package com.getjobs.worker.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * 平台任务专用浏览器：每个平台一个独立的 Playwright 实例 + 持久化上下文 + 单线程，
 * 让不同平台的投递任务可以真正并发执行，而不会争抢 PlaywrightManager 的共享浏览器。
 * <p>
 * 登录态来自 cookie 表：每次任务开始前注入最新 Cookie，任务结束后回写。
 * 浏览器窗口保持常驻，后续任务复用，避免反复启动触发风控。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformTaskBrowser {

    private final CookieService cookieService;

    /**
     * 任务浏览器窗口位置：offscreen = 移到屏幕外（默认，减少桌面窗口堆积，
     * 需要人工扫码时从任务栏点 Chrome 图标调出）；normal = 正常最大化显示。
     */
    @org.springframework.beans.factory.annotation.Value("${internbridge.task-window:offscreen}")
    private String taskWindowMode;

    private final Map<String, Runner> runners = new ConcurrentHashMap<>();

    private static class Runner {
        final String platform;
        final ExecutorService executor;
        Playwright playwright;
        BrowserContext context;
        Page page;
        boolean firstRun = true;

        Runner(String platform) {
            this.platform = platform;
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "task-" + platform);
                t.setDaemon(true);
                return t;
            });
        }
    }

    /**
     * 在平台专属浏览器里执行任务。不同平台之间并发，同一平台内部串行。
     * 单个任务超过 2 小时视为挂死，抛出超时异常（避免 API 调用方永久阻塞）。
     */
    public <T> T run(String platform, Function<Page, T> task) {
        Runner runner = runners.computeIfAbsent(platform, Runner::new);
        Future<T> future = runner.executor.submit(() -> {
            ensureStarted(runner);
            // jobsdb 依赖 SEEK 的跨域 SSO，每次任务前都注入最新 Cookie；
            // 其它平台只在首次创建时注入，避免旧 Cookie 覆盖任务浏览器自己的登录态。
            if (runner.firstRun || "jobsdb".equals(runner.platform)) {
                injectCookies(runner);
                runner.firstRun = false;
            }
            try {
                return task.apply(runner.page);
            } finally {
                saveCookies(runner);
            }
        });
        try {
            return future.get(2, java.util.concurrent.TimeUnit.HOURS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("平台任务执行超时[" + platform + "]（超过 2 小时，视为挂死）", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("平台任务执行失败[" + platform + "]: " + cause.getMessage(), cause);
        }
    }

    /**
     * 服务关闭时释放所有平台任务浏览器的 Playwright 资源，
     * 避免残留 headful Chrome 与 driver 进程（对齐 PlaywrightManager.destroy()）。
     */
    @jakarta.annotation.PreDestroy
    public void destroy() {
        for (Map.Entry<String, Runner> entry : runners.entrySet()) {
            Runner runner = entry.getValue();
            try {
                runner.executor.shutdownNow();
            } catch (Exception ignored) {
            }
            closeQuietly(() -> {
                if (runner.page != null) runner.page.close();
            }, entry.getKey());
            closeQuietly(() -> {
                if (runner.context != null) runner.context.close();
            }, entry.getKey());
            closeQuietly(() -> {
                if (runner.playwright != null) runner.playwright.close();
            }, entry.getKey());
        }
        runners.clear();
        log.info("已释放全部平台任务浏览器");
    }

    private void closeQuietly(java.lang.Runnable closer, String platform) {
        try {
            closer.run();
        } catch (Exception e) {
            log.debug("关闭平台任务浏览器资源失败[{}]: {}", platform, e.getMessage());
        }
    }

    private void ensureStarted(Runner runner) {
        try {
            if (runner.context != null && runner.page != null && !runner.page.isClosed()) {
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("启动平台任务浏览器: {}", runner.platform);
        if (runner.playwright == null) {
            runner.playwright = Playwright.create();
        }
        Path userDataDir = Paths.get(System.getProperty("user.dir"), "browser-data-tasks", runner.platform);
        boolean offscreen = !"normal".equalsIgnoreCase(taskWindowMode);
        List<String> launchArgs = new ArrayList<>(List.of(
                "--test-type",
                "--disable-session-crashed-bubble",
                "--hide-crash-restore-bubble",
                "--no-first-run",
                "--no-default-browser-check",
                "--no-proxy-server",
                "--proxy-bypass-list=*",
                "--disable-blink-features=AutomationControlled"));
        if (offscreen) {
            // headful 但移出屏幕：不触发无头检测，又不在桌面堆窗口；扫码时从任务栏点图标调出
            launchArgs.add("--window-position=-32000,-32000");
            launchArgs.add("--window-size=1680,1050");
        } else {
            launchArgs.add("--start-maximized");
        }
        runner.context = runner.playwright.chromium().launchPersistentContext(userDataDir,
                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel("chrome")
                        .setHeadless(false)
                        .setSlowMo(30)
                        .setViewportSize(null)
                        .setArgs(launchArgs));
        List<Page> pages = runner.context.pages();
        runner.page = pages.isEmpty() ? runner.context.newPage() : pages.get(0);
        runner.page.setDefaultTimeout(30_000);
        boolean warmedUp = warmUp(runner);
        if (!warmedUp) {
            throw new IllegalStateException("平台 " + runner.platform + " 需要先完成登录/安全验证：任务窗口已移至屏幕外，请从任务栏点击 Chrome 图标调出窗口操作");
        }
        log.info("平台任务浏览器已启动: {} ({})", runner.platform, userDataDir);
    }

    /**
     * JobsDB 的 Cloudflare 挑战需要在页面里等它自己过；预热一下，
     * 避免 worker 一开始搜索就撞上验证页。
     */
    private boolean warmUp(Runner runner) {
        if ("boss".equals(runner.platform)) {
            return warmUpBoss(runner);
        }
        if (!"jobsdb".equals(runner.platform)) {
            return true;
        }
        try {
            runner.page.navigate("https://hk.jobsdb.com/",
                    new Page.NavigateOptions().setTimeout(45_000)
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception e) {
            log.warn("JobsDB 预热导航失败: {}", e.getMessage());
        }
        for (int i = 0; i < 12; i++) {
            PlaywrightUtil.sleep(5);
            try {
                Object text = runner.page.evaluate("() => document.body.innerText.slice(0, 160)");
                String t = text == null ? "" : text.toString();
                if (!t.contains("安全验证") && !t.contains("Just a moment")) {
                    log.info("JobsDB 任务浏览器预热完成，无安全验证");
                    return true;
                }
                log.info("JobsDB 任务浏览器仍在安全验证，等待 {}/12", i + 1);
            } catch (Exception e) {
                log.debug("JobsDB 预热检查失败: {}", e.getMessage());
            }
        }
        log.warn("JobsDB 任务浏览器 60 秒内未通过安全验证：窗口已移至屏幕外，请从任务栏点击 Chrome 图标调出窗口手动完成");
        return false;
    }

    /**
     * Boss 对登录态最敏感：任务浏览器用自己的持久化 profile，
     * 如果已经掉登录就打开扫码页，让用户在那个窗口扫一次，之后不再反复验证。
     */
    private boolean warmUpBoss(Runner runner) {
        try {
            runner.page.navigate("https://www.zhipin.com/web/geek/jobs",
                    new Page.NavigateOptions().setTimeout(45_000)
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            PlaywrightUtil.sleep(8);
            Object text = runner.page.evaluate("() => document.body.innerText");
            String t = text == null ? "" : text.toString();
            if (t.contains("登录/注册")) {
                log.warn("Boss 任务浏览器未登录：任务窗口已移至屏幕外，请从任务栏点击 Chrome 图标调出窗口扫码登录一次");
                runner.page.navigate("https://www.zhipin.com/web/user/?ka=header-login",
                        new Page.NavigateOptions().setTimeout(45_000)
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
                return false;
            }
            log.info("Boss 任务浏览器登录态正常");
            return true;
        } catch (Exception e) {
            log.warn("Boss 任务浏览器预热失败: {}", e.getMessage());
            return false;
        }
    }

    private void injectCookies(Runner runner) {
        try {
            CookieEntity entity = cookieService.getCookieByPlatform(runner.platform);
            if (entity == null || entity.getCookieValue() == null || entity.getCookieValue().isBlank()) {
                return;
            }
            List<Cookie> cookies = parseCookies(entity.getCookieValue());
            if (!cookies.isEmpty()) {
                runner.context.addCookies(cookies);
                log.debug("平台 {} 注入 Cookie {} 条", runner.platform, cookies.size());
            }
        } catch (Exception e) {
            log.warn("平台 {} 注入 Cookie 失败: {}", runner.platform, e.getMessage());
        }
    }

    private void saveCookies(Runner runner) {
        try {
            if (runner.context == null) {
                return;
            }
            String json = new ObjectMapper().writeValueAsString(runner.context.cookies());
            cookieService.saveOrUpdateCookie(runner.platform, json, "task browser save");
        } catch (Exception e) {
            log.warn("平台 {} 回写 Cookie 失败: {}", runner.platform, e.getMessage());
        }
    }

    private List<Cookie> parseCookies(String cookieJson) {
        List<Cookie> cookies = new ArrayList<>();
        try {
            JsonNode array = new ObjectMapper().readTree(cookieJson);
            for (JsonNode node : array) {
                // 逐条容错：单条脏数据（缺 name/value、sameSite 非法值）只跳过该条，不丢整批
                try {
                    if (node == null || !node.hasNonNull("name") || !node.hasNonNull("value")) {
                        log.debug("跳过缺少 name/value 的 Cookie 条目");
                        continue;
                    }
                    Cookie cookie = new Cookie(node.get("name").asText(), node.get("value").asText());
                    if (node.hasNonNull("domain")) cookie.domain = node.get("domain").asText();
                    if (node.hasNonNull("path")) cookie.path = node.get("path").asText();
                    if (node.hasNonNull("expires")) cookie.expires = node.get("expires").asDouble();
                    if (node.hasNonNull("httpOnly")) cookie.httpOnly = node.get("httpOnly").asBoolean();
                    if (node.hasNonNull("secure")) cookie.secure = node.get("secure").asBoolean();
                    if (node.hasNonNull("sameSite")) {
                        String sameSite = node.get("sameSite").asText();
                        if (sameSite != null && !sameSite.isEmpty()) {
                            try {
                                cookie.sameSite = com.microsoft.playwright.options.SameSiteAttribute.valueOf(sameSite.toUpperCase());
                            } catch (IllegalArgumentException ex) {
                                log.debug("跳过非法 sameSite 的 Cookie 条目: {}", sameSite);
                                continue;
                            }
                        }
                    }
                    cookies.add(cookie);
                } catch (Exception itemEx) {
                    log.debug("跳过无法解析的 Cookie 条目: {}", itemEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("解析 Cookie JSON 失败: {}", e.getMessage());
        }
        return cookies;
    }
}
