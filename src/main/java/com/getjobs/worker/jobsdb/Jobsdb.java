package com.getjobs.worker.jobsdb;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.entity.JobsdbJobDataEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.JobsdbService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * JobsDB（香港）自动投递 - Playwright 版本
 * <p>
 * 流程：在 /internship-jobs 落地页输入关键词 → 跳到 /&lt;kw&gt;-jobs → 逐页（?page=N）解析 article 卡片 →
 * 逐个打开详情页点「Quick apply」→ 填写 SEEK 申请表单（简历 + cover letter）→ 提交。
 * <p>
 * 注意：投递需要登录 SEEK 账号；未登录会被重定向到 login.seek.com，此时中断任务。
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Jobsdb {

    @Setter
    private Page page;

    @Setter
    private JobsdbConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();
    private volatile boolean loginRequired = false;

    /** 每个关键词最多翻页数（每页 30 条） */
    private static final int MAX_PAGE = 5;

    /** 每个关键词最多投递数 */
    private static final int MAX_DELIVER_PER_KEYWORD = 20;

    private static final String FALLBACK_COVER_LETTER =
            "Dear Hiring Manager,\n\n"
                    + "I am Jiajie Liang, a Computer Science undergraduate at Beijing Technology and Business University "
                    + "(Class of 2029). I have hands-on finance and data experience: as a finance intern I processed 82 ERP "
                    + "transactions monthly, reconciled two bank accounts, and built cost reports; I also build Python/SQL "
                    + "financial analysis tools and a quantitative research pipeline.\n\n"
                    + "I am available three days per week for at least three months and can start immediately. "
                    + "Thank you for your consideration.\n\nBest regards,\nJiajie Liang";

    private final JobsdbService jobsdbService;
    private final AiService aiService;

    private static class PageJob {
        String jobId;
        String jobTitle;
        String companyName;
        String location;
        String detailUrl;
        String cardText;
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    public void prepare() {
        log.info("JobsDB 准备工作开始...");
        resultList.clear();
        loginRequired = false;
        log.info("JobsDB 准备工作完成");
    }

    public int execute() {
        log.info("JobsDB 投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            List<String> keywords = config.getKeywords();
            if (keywords == null || keywords.isEmpty()) {
                sendProgress("JobsDB 关键词为空，任务结束", null, null);
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
            String message = String.format("JobsDB 投递完成，共投递 %d 个岗位，用时 %s",
                    resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);
        } catch (Exception e) {
            log.error("JobsDB 投递过程出现异常", e);
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

            if (!searchKeyword(keyword)) {
                return;
            }

            int delivered = 0;
            String baseUrl = stripPageParam(page.url());

            for (int pageNum = 1; pageNum <= MAX_PAGE; pageNum++) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    return;
                }

                if (pageNum > 1) {
                    navigate(page, baseUrl + "?page=" + pageNum);
                    PlaywrightUtil.sleep(2);
                }

                List<PageJob> jobs = collectCurrentPage();
                log.info("关键词【{}】第 {} 页采集到 {} 个岗位", keyword, pageNum, jobs.size());
                if (jobs.isEmpty()) {
                    break;
                }

                for (PageJob pj : jobs) {
                    if (shouldStop() || loginRequired) {
                        sendProgress("用户取消投递或需要登录", null, null);
                        return;
                    }
                    if (delivered >= MAX_DELIVER_PER_KEYWORD) {
                        log.info("关键词【{}】达到单关键词投递上限 {}", keyword, MAX_DELIVER_PER_KEYWORD);
                        break;
                    }

                    if (config.isCollectOnly()) {
                        continue;
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

                if (delivered >= MAX_DELIVER_PER_KEYWORD) {
                    break;
                }
            }

            log.info("关键词【{}】投递完成，本次 {} 个", keyword, delivered);
        } catch (Exception e) {
            log.error("处理关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
     * 在落地页输入关键词并提交，等待跳转到结果页。
     */
    private boolean searchKeyword(String keyword) {
        log.info("JobsDB 打开搜索落地页...");
        navigate(page, Locators.SEARCH_LANDING_URL);
        PlaywrightUtil.sleep(2);
        log.info("JobsDB 落地页已打开: {}", page.url());

        // Cloudflare 挑战可能在搜索页再出现一次，轮询等输入框渲染出来
        Locator input = null;
        for (int i = 0; i < 15; i++) {
            input = page.locator(Locators.KEYWORDS_INPUT).first();
            if (input.count() > 0) {
                break;
            }
            PlaywrightUtil.sleep(2);
        }
        if (input == null || input.count() == 0) {
            log.warn("未找到 JobsDB 关键词输入框（可能仍在安全验证）");
            return false;
        }
        input.click(new Locator.ClickOptions().setTimeout(10_000));
        log.info("JobsDB 关键词框已聚焦");
        jsFill(input, keyword);
        PlaywrightUtil.sleep(1);
        log.info("JobsDB 关键词已填入: {}", keyword);

        Locator submit = page.locator(Locators.SUBMIT_SEARCH).first();
        if (submit.count() == 0) {
            log.warn("未找到 JobsDB 搜索提交按钮");
            return false;
        }
        try {
            jsClick(submit);
            log.info("JobsDB 搜索按钮已点击");
        } catch (Exception e) {
            log.warn("点击搜索按钮失败，改回车: {}", e.getMessage());
            input.press("Enter");
        }
        PlaywrightUtil.sleep(4);
        log.info("JobsDB 搜索后 URL: {}", page.url());

        try {
            page.waitForSelector(Locators.CARD,
                    new Page.WaitForSelectorOptions().setTimeout(15_000));
        } catch (Exception e) {
            log.warn("等待 JobsDB 岗位列表超时: {}", keyword);
            return false;
        }

        String url = page.url();
        if (url.contains("/oauth/login") || url.contains("login.seek.com")) {
            log.warn("JobsDB 登录态失效，中断任务");
            loginRequired = true;
            return false;
        }
        log.info("JobsDB 搜索跳转到: {}", url);
        return true;
    }

    private List<PageJob> collectCurrentPage() {
        List<PageJob> jobs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Locator cards = page.locator(Locators.CARD);
        int count = cards.count();
        for (int i = 0; i < count; i++) {
            try {
                Locator card = cards.nth(i);
                String cardText = safeInnerText(card);
                if (cardText == null || cardText.isBlank()) {
                    continue;
                }

                Locator jobLinks = card.locator(Locators.CARD_JOB_LINK);
                String href = null;
                String title = null;
                int n = jobLinks.count();
                for (int j = 0; j < n; j++) {
                    String h = jobLinks.nth(j).getAttribute("href");
                    String t = safeInnerText(jobLinks.nth(j));
                    if (h != null && h.contains("/job/") && t != null && !t.isBlank()) {
                        href = h;
                        title = t.trim();
                        break;
                    }
                }
                if (href == null || title == null) {
                    continue;
                }

                String jobId = extractJobId(href);
                if (jobId.isEmpty() || !seen.add(jobId)) {
                    continue;
                }

                PageJob pj = new PageJob();
                pj.jobId = jobId;
                pj.jobTitle = title;
                pj.companyName = safeInnerText(card.locator(Locators.CARD_COMPANY_LINK).first());
                pj.location = extractLocation(card);
                pj.detailUrl = href.startsWith("http") ? href : Locators.HOME_URL + href;
                pj.cardText = cardText;
                jobs.add(pj);

                persistIfAbsent(pj);
            } catch (Exception e) {
                log.debug("解析 JobsDB 卡片失败: {}", e.getMessage());
            }
        }
        return jobs;
    }

    private String extractLocation(Locator card) {
        try {
            Locator links = card.locator("a[href*='-jobs/in-']");
            if (links.count() > 0) {
                return safeInnerText(links.first()).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void persistIfAbsent(PageJob pj) {
        try {
            if (jobsdbService.existsByJobId(pj.jobId)) {
                return;
            }
            JobsdbJobDataEntity entity = new JobsdbJobDataEntity();
            entity.setJobId(pj.jobId);
            entity.setJobTitle(pj.jobTitle);
            entity.setJobLink(pj.detailUrl);
            entity.setSalary("");
            entity.setLocation(pj.location);
            entity.setCompanyName(pj.companyName);
            entity.setDeliveryStatus("未投递");
            jobsdbService.insertJob(entity);
        } catch (Exception e) {
            log.warn("保存 JobsDB 岗位失败: {}", e.getMessage());
        }
    }

    private boolean deliverOne(PageJob pj, String keyword) {
        if (pj.detailUrl == null || pj.detailUrl.isEmpty()) {
            return false;
        }
        try {
            if (jobsdbService.isDeliveredByJobId(pj.jobId)) {
                return false;
            }
        } catch (Exception ignored) {
        }

        Page detail = page.context().newPage();
        try {
            navigate(detail, pj.detailUrl);
            PlaywrightUtil.sleep(2);

            Locator apply = detail.locator(Locators.APPLY_LINK).first();
            if (apply.count() == 0) {
                log.info("岗位【{}】没有 Quick apply 入口，跳过", pj.jobTitle);
                return false;
            }

            String jd = safeInnerText(detail.locator("body").first());
            if (jd.length() > 1800) {
                jd = jd.substring(0, 1800);
            }

            jsClick(apply);
            PlaywrightUtil.sleep(3);
            String url = detail.url();
            if (url.contains("login.seek.com") || url.contains("/oauth/login")) {
                log.warn("JobsDB 投递需要登录，中断本次任务");
                loginRequired = true;
                return false;
            }

            String coverLetter = config.isEnableAi() ? generateCoverLetter(keyword, pj.jobTitle, jd) : FALLBACK_COVER_LETTER;
            if (coverLetter == null) {
                log.info("AI 判定不适合投递，跳过：{} | {}", pj.companyName, pj.jobTitle);
                return false;
            }

            boolean submitted = fillAndSubmitApplication(detail, coverLetter);
            if (submitted) {
                try {
                    jobsdbService.markDeliveredByJobId(pj.jobId);
                } catch (Exception ignored) {
                }
                log.info("已投递：{} | {} | {}", pj.companyName, pj.jobTitle, pj.detailUrl);
            } else {
                log.info("岗位【{}】申请未确认提交成功", pj.jobTitle);
            }
            return submitted;
        } catch (Exception e) {
            log.warn("JobsDB 打开详情或投递失败: {}", e.getMessage());
            return false;
        } finally {
            try {
                detail.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * SEEK 申请表单：Choose documents → Update profile → Review and submit。
     */
    private boolean fillAndSubmitApplication(Page detail, String coverLetter) {
        try {
            // 等表单渲染（SEEK 的申请页是重客户端，加载慢）
            for (int i = 0; i < 25; i++) {
                if (containsText(detail, "Choose documents") || containsText(detail, "Personal details")) {
                    break;
                }
                PlaywrightUtil.sleep(1);
            }
            log.info("JobsDB 申请表单已加载: {}", detail.url());

            fillByLabel(detail, "First name", "Jiajie");
            fillByLabel(detail, "Last name", "Liang");
            fillHomeLocation(detail, "Hong Kong");
            fillPhone(detail, "China (+86)", "13426841122");

            // 简历：如果表单已默认选中一份简历，直接跳过上传；否则上传 PDF
            try {
                Locator checkedResume = detail.locator("input[type='radio'][name^='document-select']:checked").first();
                if (checkedResume.count() > 0) {
                    log.info("申请表单已默认选中简历，跳过上传");
                } else {
                    String resumePath = config.getResumePath();
                    if (resumePath != null && !resumePath.isBlank()) {
                        Path p = Paths.get(resumePath);
                        if (Files.exists(p)) {
                            Locator fileInput = detail.locator(Locators.APPLY_FILE_INPUT).first();
                            if (fileInput.count() > 0) {
                                fileInput.setInputFiles(p);
                            } else {
                                Locator upload = detail.locator("button:has-text('Upload'), label:has-text('Upload')").first();
                                if (upload.count() > 0) {
                                    com.microsoft.playwright.FileChooser chooser =
                                            detail.waitForFileChooser(() -> upload.click());
                                    chooser.setFiles(p);
                                } else {
                                    log.warn("未找到简历上传入口");
                                }
                            }
                            log.info("已上传简历: {}", p.getFileName());
                            PlaywrightUtil.sleep(2);
                        } else {
                            log.warn("简历文件不存在: {}", resumePath);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("上传简历失败: {}", e.getMessage());
            }

            // Cover letter：必须选中「Write a cover letter」这个 radio（value=change），
            // 只点文字不会切换选项，导致 Continue 校验不过。
            try {
                Locator changeRadio = detail.locator("input[type='radio'][value='change']").first();
                if (changeRadio.count() > 0 && !changeRadio.isChecked()) {
                    try {
                        changeRadio.check(new Locator.CheckOptions().setForce(true));
                    } catch (Exception e) {
                        jsClick(changeRadio);
                    }
                    PlaywrightUtil.sleep(1);
                }
                log.info("Cover letter 选项已选中: {}", changeRadio.count() > 0 && changeRadio.isChecked());
                Locator textarea = detail.locator("textarea:visible").first();
                if (textarea.count() > 0) {
                    // 必须用真实输入，合成 input 事件不会更新 SEEK 的受控组件
                    try {
                        textarea.click();
                        textarea.fill(coverLetter);
                    } catch (Exception e) {
                        try {
                            textarea.click();
                            textarea.type(coverLetter);
                        } catch (Exception ignored) {
                        }
                    }
                    PlaywrightUtil.sleep(1);
                    log.info("Cover letter 已填写，输入框长度: {}", textarea.inputValue().length());
                } else {
                    log.warn("未找到 Cover letter 输入框");
                }
            } catch (Exception e) {
                log.warn("填写 cover letter 失败: {}", e.getMessage());
            }

            // 个人信息区域填完后会进入编辑态（出现 Save/Cancel），必须先 Save 再 Continue
            try {
                Locator saveBtn = detail.locator("button:has-text('Save')").first();
                if (saveBtn.count() > 0 && saveBtn.isVisible()) {
                    saveBtn.click(new Locator.ClickOptions().setTimeout(8000));
                    PlaywrightUtil.sleep(2);
                    log.info("JobsDB 个人信息已保存");
                }
            } catch (Exception e) {
                log.debug("点击 Save 失败: {}", e.getMessage());
            }

            // 逐步 Continue
            for (int step = 0; step < 2; step++) {
                if (!clickTextButton(detail, Locators.APPLY_CONTINUE_TEXT)) {
                    log.warn("JobsDB 未找到 Continue 按钮（第 {} 步）", step + 1);
                    break;
                }
                PlaywrightUtil.sleep(3);
                String err = visibleValidationErrors(detail);
                if (!err.isBlank()) {
                    log.warn("JobsDB Continue 后校验提示: {}", err);
                }
            }

            // 最后一步提交
            boolean clicked = false;
            for (String t : Locators.APPLY_SUBMIT_TEXTS.split(",")) {
                if (clickTextButton(detail, t.trim())) {
                    clicked = true;
                    break;
                }
            }
            PlaywrightUtil.sleep(3);
            if (!clicked) {
                Object btns = detail.evaluate("() => [...document.querySelectorAll('button')].filter(b => b.offsetParent).map(b => (b.innerText || '').trim()).filter(Boolean).join(' | ')");
                log.warn("JobsDB 未找到提交按钮，当前按钮: {}；校验提示: {}", btns, visibleValidationErrors(detail));
            }

            boolean success = containsText(detail, "Application submitted")
                    || containsText(detail, "applied for this job")
                    || containsText(detail, "successfully applied")
                    || containsText(detail, "Thank you for applying")
                    || clicked;
            return success;
        } catch (Exception e) {
            log.warn("JobsDB 申请表单处理异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SEEK 的姓名输入框没有任何 name/aria/placeholder 属性，只能通过前面的 strong 标签定位。
     */
    private void fillByLabel(Page p, String label, String value) {
        try {
            Locator inp = p.locator("xpath=//strong[normalize-space()='" + label + "']/following::input[1]").first();
            if (inp.count() == 0) {
                return;
            }
            String cur = inp.inputValue();
            if (cur != null && !cur.isBlank()) {
                return;
            }
            inp.click();
            inp.type(value);
            PlaywrightUtil.sleep(1);
            log.info("{} 已填写: {}", label, inp.inputValue());
        } catch (Exception e) {
            log.warn("填写 {} 失败: {}", label, e.getMessage());
        }
    }

    private void fillHomeLocation(Page p, String value) {
        try {
            Locator loc = p.locator("input[aria-label='Home location']").first();
            if (loc.count() == 0) {
                loc = p.locator("input[placeholder*='district or area']").first();
            }
            if (loc.count() == 0) {
                return;
            }
            loc.click();
            loc.press("Control+a");
            loc.press("Backspace");
            // 必须用真实键盘输入，合成的 input 事件不会触发地点联想
            loc.type(value);
            PlaywrightUtil.sleep(2);
            // 联想列表渲染有延迟，轮询等它出现再点
            boolean picked = false;
            for (int i = 0; i < 10 && !picked; i++) {
                picked = jsClickExactText(p, "Hong Kong SAR")
                        || jsClickExactText(p, "Hong Kong Island");
                if (!picked) {
                    PlaywrightUtil.sleep(1);
                }
            }
            if (!picked) {
                loc.press("ArrowDown");
                loc.press("Enter");
            }
            PlaywrightUtil.sleep(1);
            log.info("Home location 已填写: {}（联想点选: {}）", loc.inputValue(), picked);
        } catch (Exception e) {
            log.warn("填写 Home location 失败: {}", e.getMessage());
        }
    }

    private boolean jsClickExactText(Page p, String text) {
        try {
            Object r = p.evaluate("(t) => {\n"
                    + "  const els = [...document.querySelectorAll('li,div,span')]\n"
                    + "    .filter(e => e.children.length === 0 && e.textContent && e.textContent.trim() === t && e.offsetParent);\n"
                    + "  if (!els.length) return false;\n"
                    + "  els[0].click();\n"
                    + "  return true;\n"
                    + "}", text);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    private String visibleValidationErrors(Page p) {
        try {
            Object r = p.evaluate("() => {\n"
                    + "  const bad = [...document.querySelectorAll('*')]\n"
                    + "    .filter(e => e.children.length === 0 && e.offsetParent && e.textContent\n"
                    + "      && /Please select|required|Enter a|must be|is required/i.test(e.textContent))\n"
                    + "    .map(e => e.textContent.trim());\n"
                    + "  return [...new Set(bad)].slice(0, 5).join(' | ');\n"
                    + "}");
            return r == null ? "" : r.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void fillPhone(Page p, String countryLabel, String number) {
        try {
            Locator phone = p.locator("input[type='tel'], input[aria-label='Phone number']").first();
            if (phone.count() == 0) {
                return;
            }
            String cur = phone.inputValue();
            if (cur != null && !cur.isBlank()) {
                return;
            }
            Locator select = p.locator("select").first();
            if (select.count() > 0) {
                try {
                    select.selectOption(countryLabel);
                } catch (Exception ignored) {
                }
            }
            jsFill(phone, number);
        } catch (Exception e) {
            log.warn("填写手机号失败: {}", e.getMessage());
        }
    }

    /**
     * 用页面内 JS 赋值 + 触发 input/change 事件。
     * JobsDB 的 MUI 输入框在 Playwright fill 下会卡住，这里绕开原生输入链路。
     */
    private boolean jsFill(Locator loc, String value) {
        try {
            if (loc == null || loc.count() == 0) {
                return false;
            }
            loc.first().evaluate("(el, v) => {\n"
                    + "  el.focus();\n"
                    + "  const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;\n"
                    + "  s.call(el, v);\n"
                    + "  el.dispatchEvent(new Event('input', { bubbles: true }));\n"
                    + "  el.dispatchEvent(new Event('change', { bubbles: true }));\n"
                    + "}", value);
            return true;
        } catch (Exception e) {
            log.debug("jsFill 失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean jsClick(Locator loc) {
        try {
            if (loc == null || loc.count() == 0) {
                return false;
            }
            loc.first().evaluate("el => { el.scrollIntoView({ block: 'center' }); el.click(); }");
            return true;
        } catch (Exception e) {
            log.debug("jsClick 失败: {}", e.getMessage());
            return false;
        }
    }

    private void fillIfEmpty(Page p, String selector, String value) {
        try {
            Locator input = p.locator(selector).first();
            if (input.count() > 0) {
                String v = input.inputValue();
                if (v == null || v.isBlank()) {
                    jsFill(input, value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean clickTextButton(Page p, String text) {
        try {
            Locator buttons = p.locator("button:visible");
            int n = buttons.count();
            for (int i = 0; i < n; i++) {
                String t = safeInnerText(buttons.nth(i));
                if (t == null) {
                    continue;
                }
                // SEEK 的按钮文案里带零宽字符（Continue⁠），先清洗再比较
                String norm = t.replace("\u2060", "").replace("\u200B", "").trim();
                if (norm.equalsIgnoreCase(text)) {
                    jsClick(buttons.nth(i));
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("点击按钮【{}】失败: {}", text, e.getMessage());
        }
        return false;
    }

    private String generateCoverLetter(String keyword, String jobName, String jd) {
        try {
            AiEntity aiConfig = aiService.getAiConfig();
            String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
            String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;

            String request = (prompt != null)
                    ? String.format(prompt, introduce, keyword, jobName, jd, FALLBACK_COVER_LETTER)
                    : "请基于以下信息生成一段简洁的英文 cover letter（120词以内）：\n"
                    + "个人介绍：" + introduce + "\n"
                    + "关键词：" + keyword + "\n"
                    + "职位名称：" + jobName + "\n"
                    + "职位描述：" + jd + "\n"
                    + "参考：" + FALLBACK_COVER_LETTER;

            String result = aiService.sendRequest(request);
            if (result == null || result.isBlank()) {
                return FALLBACK_COVER_LETTER;
            }
            if (result.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$")) {
                return null;
            }
            return result.trim();
        } catch (Exception e) {
            log.warn("AI 生成 cover letter 失败，使用固定模板: {}", e.getMessage());
            return FALLBACK_COVER_LETTER;
        }
    }

    private String extractJobId(String href) {
        int idx = href.indexOf("/job/");
        if (idx < 0) {
            return "";
        }
        String rest = href.substring(idx + "/job/".length());
        StringBuilder sb = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private String stripPageParam(String url) {
        if (url == null) {
            return Locators.SEARCH_LANDING_URL;
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
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
            if (u == null || !u.contains("jobsdb.com")) {
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
