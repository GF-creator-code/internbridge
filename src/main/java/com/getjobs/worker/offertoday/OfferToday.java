package com.getjobs.worker.offertoday;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.entity.OfferTodayJobDataEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.OfferTodayService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OfferToday（香港）自动投递 - Playwright 版本
 * <p>
 * 流程：/hk/scan 输入关键词 → 解析 div.otdJobCard → 只保留「實習」类型 →
 * 滚动列表加载更多 → 逐个在新标签页打开详情页 → 点「傳送投遞消息」→ 填 AI 招呼语 → 发送。
 * <p>
 * 注意：OfferToday 的搜索状态存在前端内存里，URL 参数不生效，必须走页面上的搜索框。
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class OfferToday {

    @Setter
    private Page page;

    @Setter
    private OfferTodayConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();

    /** 出现登录弹窗时置 true，中断本次投递 */
    private volatile boolean loginRequired = false;

    /** 每个关键词最多滚动的屏数（每屏 10 条） */
    private static final int MAX_SCROLLS = 6;

    /** 每个关键词最多投递的岗位数，防止单关键词刷爆 */
    private static final int MAX_DELIVER_PER_KEYWORD = 30;

    private static final String FALLBACK_GREETING =
            "您好，我是北京工商大学计算机科学与技术专业 2029 届本科生梁家杰，"
                    + "可每周到岗三天、连续实习三个月以上，熟悉 Python/SQL 与数据分析，"
                    + "希望有机会进一步沟通，谢谢！";

    private final OfferTodayService offerTodayService;
    private final AiService aiService;

    private static class PageJob {
        String jobId;
        String jobTitle;
        String companyName;
        String salary;
        String location;
        String detailUrl;
        String cardText;
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    public void prepare() {
        log.info("OfferToday 准备工作开始...");
        resultList.clear();
        loginRequired = false;
        log.info("OfferToday 准备工作完成");
    }

    public int execute() {
        log.info("OfferToday 投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            List<String> keywords = config.getKeywords();
            if (keywords == null || keywords.isEmpty()) {
                sendProgress("OfferToday 关键词为空，任务结束", null, null);
                return 0;
            }

            for (String keyword : keywords) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    break;
                }
                deliverByKeyword(keyword);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("OfferToday 投递完成，共投递 %d 个岗位，用时 %s",
                    resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);

            if (!resultList.isEmpty()) {
                log.info("新投递公司如下:");
                resultList.forEach(job -> log.info(job.toString()));
            } else {
                log.info("未投递新的岗位...");
            }
        } catch (Exception e) {
            log.error("OfferToday 投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    private void deliverByKeyword(String keyword) {
        if (loginRequired) {
            return;
        }

        try {
            log.info("开始搜索关键词: {}", keyword);
            sendProgress("正在搜索关键词: " + keyword, null, null);

            navigate(page, Locators.SCAN_URL);
            PlaywrightUtil.sleep(2);

            Locator searchBox = page.locator("input[placeholder='" + Locators.SEARCH_INPUT_ROLE_NAME + "']");
            if (searchBox.count() == 0) {
                log.warn("未找到搜索框，跳过关键词: {}", keyword);
                return;
            }
            searchBox.first().click();
            searchBox.first().fill(keyword);
            searchBox.first().press("Enter");
            PlaywrightUtil.sleep(3);

            try {
                page.waitForSelector(Locators.CARD,
                        new Page.WaitForSelectorOptions().setTimeout(15_000));
            } catch (Exception e) {
                log.warn("等待岗位列表超时，跳过关键词: {}", keyword);
                return;
            }

            List<PageJob> jobs = collectJobs(keyword);
            log.info("关键词【{}】采集到 {} 个岗位（已按實習过滤）", keyword, jobs.size());

            int delivered = 0;
            for (PageJob pj : jobs) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    return;
                }
                if (delivered >= MAX_DELIVER_PER_KEYWORD) {
                    log.info("关键词【{}】达到单关键词投递上限 {}", keyword, MAX_DELIVER_PER_KEYWORD);
                    break;
                }

                try {
                    if (isExcludedCompany(pj.companyName)) {
                        log.info("命中硬性排除（腾讯/字节跳动），跳过：{} | {}", pj.companyName, pj.jobTitle);
                        continue;
                    }
                    if (config.isSkipLegal() && (isLegalJob(pj.jobTitle) || isLegalJob(pj.companyName))) {
                        log.info("被过滤（法律岗）：{} | {}", pj.companyName, pj.jobTitle);
                        continue;
                    }
                    if (deliverOne(pj, keyword)) {
                        resultList.add(buildJob(pj));
                        delivered++;
                    }
                } catch (Exception ex) {
                    log.warn("投递失败，继续下一个岗位: {}", ex.getMessage());
                }

                PlaywrightUtil.sleep(Math.max(1, config.getWaitTime()));
            }

            log.info("关键词【{}】投递完成，本次 {} 个", keyword, delivered);
        } catch (Exception e) {
            log.error("处理关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
     * 采集当前搜索结果里的實習岗位，滚动加载更多。
     */
    private List<PageJob> collectJobs(String keyword) {
        List<PageJob> jobs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        int lastCount = -1;
        for (int scroll = 0; scroll <= MAX_SCROLLS; scroll++) {
            if (shouldStop()) {
                break;
            }

            Locator cards = page.locator(Locators.CARD);
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                try {
                    Locator card = cards.nth(i);
                    String cardText = safeInnerText(card);
                    if (!isInternCard(cardText)) {
                        continue;
                    }

                    Locator titleLink = card.locator(Locators.CARD_TITLE_LINK).first();
                    if (titleLink.count() == 0) {
                        continue;
                    }
                    String href = titleLink.getAttribute("href");
                    String title = titleLink.innerText();
                    if (href == null || href.isBlank() || title == null || title.isBlank()) {
                        continue;
                    }

                    String jobId = extractJobId(href);
                    if (jobId.isEmpty() || !seen.add(jobId)) {
                        continue;
                    }

                    PageJob pj = new PageJob();
                    pj.jobId = jobId;
                    pj.jobTitle = title.trim();
                    pj.companyName = safeText(card.locator(Locators.CARD_COMPANY_LINK).first());
                    pj.salary = extractSalary(cardText);
                    pj.location = "";
                    pj.detailUrl = href.startsWith("http") ? href : Locators.JOB_URL_PREFIX + href;
                    pj.cardText = cardText;
                    jobs.add(pj);

                    persistIfAbsent(pj);
                } catch (Exception e) {
                    log.debug("解析卡片失败: {}", e.getMessage());
                }
            }

            if (count == lastCount && scroll > 0) {
                break;
            }
            lastCount = count;

            scrollListToBottom();
            PlaywrightUtil.sleep(2);
        }

        return jobs;
    }

    private void persistIfAbsent(PageJob pj) {
        try {
            if (offerTodayService.existsByJobId(pj.jobId)) {
                return;
            }
            OfferTodayJobDataEntity entity = new OfferTodayJobDataEntity();
            entity.setJobId(pj.jobId);
            entity.setJobTitle(pj.jobTitle);
            entity.setJobLink(pj.detailUrl);
            entity.setSalary(pj.salary);
            entity.setLocation(pj.location);
            entity.setCompanyName(pj.companyName);
            entity.setDeliveryStatus("未投递");
            offerTodayService.insertJob(entity);
        } catch (Exception e) {
            log.warn("保存岗位数据失败: {}", e.getMessage());
        }
    }

    private boolean deliverOne(PageJob pj, String keyword) {
        if (pj.detailUrl == null || pj.detailUrl.isEmpty()) {
            return false;
        }
        try {
            if (offerTodayService.isDeliveredByJobId(pj.jobId)) {
                return false;
            }
        } catch (Exception ignored) {
        }

        Page detail = page.context().newPage();
        try {
            navigate(detail, pj.detailUrl);
            PlaywrightUtil.sleep(2);

            Locator entry = detail.locator("text=" + Locators.APPLY_ENTRY_TEXT).first();
            if (entry.count() == 0) {
                log.info("岗位【{}】未找到投递入口，跳过", pj.jobTitle);
                return false;
            }

            // 勾选投递同意（页面上的 checkbox）
            checkConsent(detail);

            entry.click();
            PlaywrightUtil.sleep(2);

            if (isLoginDialogVisible(detail)) {
                log.warn("OfferToday 登录态失效，中断本次投递");
                loginRequired = true;
                return false;
            }

            String jd = safeInnerText(detail.locator("body").first());
            if (jd.length() > 1800) {
                jd = jd.substring(0, 1800);
            }

            String greeting = config.isEnableAi() ? generateGreeting(keyword, pj.jobTitle, jd) : FALLBACK_GREETING;
            if (greeting == null) {
                log.info("AI 判定不适合投递，跳过：{} | {}", pj.companyName, pj.jobTitle);
                return false;
            }

            // 参考 Boss 流程：点「傳送投遞消息」后进入聊天/消息界面，在输入框里发招呼语。
            // OfferToday 有时是弹层、有时跳到独立聊天页，这里轮询等输入框出现。
            Locator messageInput = waitForChatInput(detail);
            boolean sent = false;
            if (messageInput != null && messageInput.count() > 0) {
                try {
                    messageInput.click();
                    messageInput.fill(greeting);
                } catch (Exception e) {
                    try {
                        messageInput.click();
                        messageInput.pressSequentially(greeting);
                    } catch (Exception ignored) {
                    }
                }
                PlaywrightUtil.sleep(1);

                // 优先点发送按钮；没有发送按钮就回车发送（Boss 的聊天输入框同样支持回车）
                boolean clicked = clickSendButton(detail);
                if (!clicked) {
                    try {
                        messageInput.press("Enter");
                        clicked = true;
                    } catch (Exception ignored) {
                    }
                }
                PlaywrightUtil.sleep(2);
                sent = clicked && (containsText(detail, greeting.substring(0, Math.min(12, greeting.length())))
                        || containsText(detail, "已傳送") || containsText(detail, "已发送"));
            } else {
                // 没有输入框：可能是直接一键投递，点完入口即完成
                sent = containsText(detail, "已傳送") || containsText(detail, "已发送")
                        || containsText(detail, "已投遞") || containsText(detail, "投递成功");
            }
            PlaywrightUtil.sleep(1);

            boolean success = sent || containsText(detail, "已傳送")
                    || containsText(detail, "已发送") || containsText(detail, "已投遞")
                    || containsText(detail, "發送成功") || containsText(detail, "发送成功");

            if (success) {
                try {
                    offerTodayService.markDeliveredByJobId(pj.jobId);
                } catch (Exception ignored) {
                }
                log.info("已投递：{} | {} | {}", pj.companyName, pj.jobTitle, pj.detailUrl);
            } else {
                log.info("岗位【{}】未确认到发送成功", pj.jobTitle);
            }
            return success;
        } catch (Exception e) {
            log.warn("打开详情页或投递失败: {}", e.getMessage());
            return false;
        } finally {
            try {
                detail.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 点「傳送投遞消息」后等聊天输入框出现（弹层或独立聊天页都可能）。
     */
    private Locator waitForChatInput(Page detail) {
        for (int i = 0; i < 16; i++) {
            try {
                Locator input = detail.locator("textarea:visible, [contenteditable='true']:visible").first();
                if (input.count() > 0) {
                    return input;
                }
                Locator placeholderInput = detail.locator(
                        "input[placeholder*='輸入']:visible, input[placeholder*='输入']:visible").first();
                if (placeholderInput.count() > 0) {
                    return placeholderInput;
                }
            } catch (Exception ignored) {
            }
            PlaywrightUtil.sleep(1);
        }
        return null;
    }

    private boolean clickSendButton(Page detail) {
        try {
            Locator buttons = detail.locator("button:visible");
            int n = buttons.count();
            for (int i = 0; i < n; i++) {
                String text = safeText(buttons.nth(i));
                if (text == null) {
                    continue;
                }
                String t = text.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.equals("發送") || t.equals("傳送") || t.equals("確認") || t.equals("提交")
                        || t.equals("發送消息") || t.equals("发送")) {
                    buttons.nth(i).click();
                    return true;
                }
            }
            // 兜底：投递入口旁边的按钮（详情页底部固定操作条）
            Locator fallback = detail.locator("button:visible").last();
            if (fallback.count() > 0) {
                fallback.click();
                return true;
            }
        } catch (Exception e) {
            log.debug("点击发送按钮失败: {}", e.getMessage());
        }
        return false;
    }

    private void checkConsent(Page detail) {
        try {
            Locator boxes = detail.locator(Locators.APPLY_CONSENT_CHECKBOX);
            int n = boxes.count();
            for (int i = 0; i < n; i++) {
                Locator box = boxes.nth(i);
                try {
                    if (!box.isChecked()) {
                        box.check();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isLoginDialogVisible(Page detail) {
        try {
            Locator pwd = detail.locator("input[type='password']:visible");
            if (pwd.count() > 0) {
                return true;
            }
            return containsText(detail, "10s 註冊") || containsText(detail, "10s 注册");
        } catch (Exception e) {
            return false;
        }
    }

    private String generateGreeting(String keyword, String jobName, String jd) {
        try {
            AiEntity aiConfig = aiService.getAiConfig();
            String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
            String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;

            String request = (prompt != null)
                    ? String.format(prompt, introduce, keyword, jobName, jd, FALLBACK_GREETING)
                    : "请基于以下信息生成简洁友好的中文打招呼语，不超过80字：\n"
                    + "个人介绍：" + introduce + "\n"
                    + "关键词：" + keyword + "\n"
                    + "职位名称：" + jobName + "\n"
                    + "职位描述：" + jd + "\n"
                    + "参考语：" + FALLBACK_GREETING;

            String result = aiService.sendRequest(request);
            if (result == null || result.isBlank()) {
                return FALLBACK_GREETING;
            }
            if (isAiRejectResponse(result)) {
                return null;
            }
            return result.trim();
        } catch (Exception e) {
            log.warn("AI 生成招呼语失败，使用固定模板: {}", e.getMessage());
            return FALLBACK_GREETING;
        }
    }

    private boolean isAiRejectResponse(String result) {
        return result != null
                && result.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$");
    }

    /**
     * 卡片是否是「實習」类型：卡片文本里有一行恰好是 實習。
     */
    private boolean isInternCard(String cardText) {
        if (cardText == null || cardText.isBlank()) {
            return false;
        }
        for (String line : cardText.split("\n")) {
            if ("實習".equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private String extractJobId(String href) {
        int idx = href.indexOf("/hk/job/");
        if (idx < 0) {
            return "";
        }
        String id = href.substring(idx + "/hk/job/".length());
        int q = id.indexOf('?');
        if (q >= 0) {
            id = id.substring(0, q);
        }
        int h = id.indexOf('#');
        if (h >= 0) {
            id = id.substring(0, h);
        }
        return id.trim();
    }

    private String extractSalary(String cardText) {
        if (cardText == null) {
            return "";
        }
        Matcher m = Pattern.compile("(HK\\$\\s*)?[\\d,.]+\\s*[Kk]?\\s*[-~至]\\s*[\\d,.]+\\s*[Kk]?\\s*/\\s*(月|小時|小时|天|週|周)")
                .matcher(cardText);
        if (m.find()) {
            return m.group().trim();
        }
        if (cardText.contains("薪資面議") || cardText.contains("薪资面议")) {
            return "薪資面議";
        }
        return "";
    }

    private void scrollListToBottom() {
        try {
            page.evaluate("() => {\n"
                    + "  const cards = [...document.querySelectorAll('div.otdJobCard')];\n"
                    + "  if (!cards.length) return;\n"
                    + "  let n = cards[0];\n"
                    + "  while (n && getComputedStyle(n).overflowY !== 'auto' && getComputedStyle(n).overflowY !== 'scroll') {\n"
                    + "    n = n.parentElement;\n"
                    + "  }\n"
                    + "  if (n) { n.scrollTop = n.scrollHeight; } else { window.scrollTo(0, document.body.scrollHeight); }\n"
                    + "}");
        } catch (Exception e) {
            log.debug("滚动列表失败: {}", e.getMessage());
        }
    }

    private void navigate(Page p, String url) {
        try {
            p.navigate(url, new Page.NavigateOptions()
                    .setTimeout(45_000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception navEx) {
            String u = null;
            try {
                u = p.url();
            } catch (Exception ignored) {
            }
            if (u == null || !u.contains("offertoday.com")) {
                throw navEx;
            }
        }
    }

    private Job buildJob(PageJob pj) {
        Job job = new Job();
        job.setJobName(pj.jobTitle);
        job.setCompanyName(pj.companyName);
        return job;
    }

    private boolean containsText(Page p, String text) {
        try {
            return p.locator("text=" + text).count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String safeText(Locator locator) {
        try {
            if (locator == null || locator.count() == 0) {
                return "";
            }
            String t = locator.innerText();
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeInnerText(Locator locator) {
        try {
            if (locator == null || locator.count() == 0) {
                return "";
            }
            String t = locator.innerText();
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isExcludedCompany(String company) {
        if (company == null || company.isBlank()) {
            return false;
        }
        return company.contains("腾讯") || company.contains("字節") || company.contains("字节");
    }

    private boolean isLegalJob(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] terms = {"法务", "法務", "法律", "律师", "律師", "律所", "非诉", "非訴",
                "诉讼", "訴訟", "仲裁", "知识产权", "知識產權", "专利", "專利", "法證", "法证", "Forensic"};
        for (String t : terms) {
            if (text.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    private void sendProgress(String message, Integer current, Integer total) {
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }
}
