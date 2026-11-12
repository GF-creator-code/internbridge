package com.getjobs.application.controller;

import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 实习僧调试接口（临时）
 * 用于在已登录的自动化浏览器里执行 JS / 上传文件，
 * 绕开原生文件选择框无法被 UI 自动化驱动的问题。
 */
@Slf4j
@RestController
@RequestMapping("/api/shixiseng/debug")
@CrossOrigin(origins = "*")
public class ShixisengDebugController {

    @Autowired
    private PlaywrightManager playwrightManager;

    private Page page() {
        Page p = playwrightManager.getShixisengPage();
        if (p == null) {
            throw new IllegalStateException("shixisengPage 未初始化");
        }
        return p;
    }

    /**
     * 在实习僧页面执行 JS 表达式（需为函数形式，如 "() => document.title"）
     */
    @PostMapping("/eval")
    public Map<String, Object> eval(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = page();
            Object result = p.evaluate(body.get("script"));
            resp.put("success", true);
            resp.put("result", result == null ? null : String.valueOf(result));
            resp.put("url", p.url());
        } catch (Exception e) {
            log.warn("实习僧 debug eval 失败: {}", e.getMessage());
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /**
     * 导航到指定 URL（DOMCONTENTLOADED 即可）
     */
    @PostMapping("/goto")
    public Map<String, Object> gotoUrl(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = page();
            p.navigate(body.get("url"), new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            resp.put("success", true);
            resp.put("url", p.url());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /**
     * 通用页面动作：hover / click / dblclick / fill / type / press / wait / text / count / mouse
     * 用 Playwright 的真实输入事件，避免站点对合成事件的 isTrusted 校验
     */
    @PostMapping("/act")
    public Map<String, Object> act(@RequestBody Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = page();
            String action = String.valueOf(body.get("action"));
            String selector = body.get("selector") == null ? null : String.valueOf(body.get("selector"));
            String value = body.get("value") == null ? null : String.valueOf(body.get("value"));
            int index = body.get("index") == null ? 0 : Integer.parseInt(String.valueOf(body.get("index")));

            switch (action) {
                case "hover" -> p.locator(selector).nth(index).hover();
                case "click" -> p.locator(selector).nth(index)
                        .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(15000));
                case "forceClick" -> p.locator(selector).nth(index)
                        .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(15000).setForce(true));
                case "dblclick" -> p.locator(selector).nth(index).dblclick();
                case "fill" -> p.locator(selector).nth(index).fill(value);
                case "type" -> p.locator(selector).nth(index).type(value);
                case "press" -> p.keyboard().press(value);
                case "wait" -> p.waitForSelector(selector,
                        new Page.WaitForSelectorOptions().setTimeout(20000));
                case "text" -> {
                    resp.put("success", true);
                    resp.put("result", p.locator(selector).nth(index).innerText());
                    resp.put("url", p.url());
                    return resp;
                }
                case "count" -> {
                    resp.put("success", true);
                    resp.put("result", String.valueOf(p.locator(selector).count()));
                    resp.put("url", p.url());
                    return resp;
                }
                case "mouse" -> {
                    double x = Double.parseDouble(String.valueOf(body.get("x")));
                    double y = Double.parseDouble(String.valueOf(body.get("y")));
                    p.mouse().click(x, y);
                }
                case "sleep" -> Thread.sleep(Long.parseLong(value == null ? "1000" : value));
                default -> throw new IllegalArgumentException("未知动作: " + action);
            }
            resp.put("success", true);
            resp.put("url", p.url());
        } catch (Exception e) {
            log.warn("实习僧 debug act 失败: {}", e.getMessage());
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /**
     * 上传附件简历：点「上传」→ 处理中英文/覆盖确认弹窗 → 文件选择器交给 Playwright 设置文件
     */
    @PostMapping("/attach")
    public Map<String, Object> attach(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        StringBuilder steps = new StringBuilder();
        try {
            Page p = page();
            Path file = Paths.get(body.get("path"));

            // 先挂文件选择器处理器（只处理第一次，避免后续误触发）
            java.util.concurrent.atomic.AtomicBoolean chooserDone =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            p.onFileChooser(fc -> {
                if (chooserDone.compareAndSet(false, true)) {
                    fc.setFiles(file);
                    log.info("实习僧附件简历：已通过文件选择器设置 {}", file);
                }
            });
            steps.append("chooser-registered;");

            p.locator(".upload-btn").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions()
                            .setTimeout(15000).setForce(true));
            steps.append("clicked-upload;");
            Thread.sleep(1500);

            // 选择语言：中文 / 英文（默认中文）
            String lang = body.getOrDefault("lang", "cn");
            if ("en".equalsIgnoreCase(lang)) {
                try {
                    com.microsoft.playwright.Locator en = p.locator(
                            ".el-dialog__wrapper:visible .el-radio:has-text('英文')").last();
                    en.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(8000));
                    en.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
                    steps.append("lang-en;");
                    Thread.sleep(800);
                } catch (Exception e) {
                    steps.append("lang-en-failed;");
                    log.warn("切换英文附件失败: {}", e.getMessage());
                }
            }

            // 处理最多 3 层确认弹窗（中英文选择 / 覆盖确认 / 其他提示）
            // 注意：按钮文案是「确 定」（中间带空格），用单字匹配
            for (int i = 0; i < 3; i++) {
                com.microsoft.playwright.Locator btn = p.locator(
                        ".el-dialog__wrapper:visible button:has-text('确'), " +
                        ".el-message-box:visible button:has-text('确')").last();
                try {
                    btn.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(8000));
                } catch (Exception noDialog) {
                    steps.append("no-dialog").append(i).append(";");
                    break;
                }
                btn.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
                steps.append("confirm").append(i).append(";");
                Thread.sleep(2000);
            }

            // 等文件选择器把文件塞进去 + 上传完成
            for (int i = 0; i < 30 && !chooserDone.get(); i++) {
                Thread.sleep(500);
            }
            steps.append("chooser-fired=").append(chooserDone.get()).append(";");
            Thread.sleep(5000);

            resp.put("success", true);
            resp.put("steps", steps.toString());
            resp.put("url", p.url());
        } catch (Exception e) {
            log.warn("实习僧附件上传失败: {}", e.getMessage());
            resp.put("success", false);
            resp.put("steps", steps.toString());
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /**
     * 给页面里的 file input 设置文件
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = page();
            String selector = body.getOrDefault("selector", "input[type=file]");
            Path file = Paths.get(body.get("path"));
            p.setInputFiles(selector, file);
            resp.put("success", true);
            resp.put("message", "已设置文件: " + file);
        } catch (Exception e) {
            log.warn("实习僧 debug upload 失败: {}", e.getMessage());
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }
}
