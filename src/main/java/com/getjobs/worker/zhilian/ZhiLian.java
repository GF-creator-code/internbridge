package com.getjobs.worker.zhilian;

import com.getjobs.application.entity.ZhilianJobDataEntity;
import com.getjobs.application.service.ZhilianService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * <p>
 * 智联招聘自动投递（适配 2026 版新站）。
 * 新站为「左侧列表 + 右侧详情面板」：点卡片 → 右侧点「立即投递」→ 弹「已向对方发送简历和打招呼语」→ 留在此页。
 * 已投过的岗位按钮文案是「继续沟通」。列表支持 URL 分页（p=N）。
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ZhiLian {

    @Setter
    private Page page;

    @Setter
    private ZhilianConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();
    private boolean isLimit = false;
    private int maxPage = 30;

    /** 新站搜索地址：jl=城市码，kw=关键词，p=页码 */
    private static final String SEARCH_URL_FMT = "https://www.zhaopin.com/jobs/?jl=%s&kw=%s&p=%d";

    private static final String CARD = ".job-card";
    private static final String CARD_TITLE = ".job-card__title-main";
    private static final String CARD_SALARY = ".job-card__salary";
    private static final String CARD_COMPANY = ".job-card__company a";
    private static final String CARD_TAGS = ".job-card__skill-tag";
    private static final String DETAIL_APPLY = ".job-detail-summary__apply";
    private static final String DELIVER_MODAL = ".deliver-greeting-modal__box";
    private static final String MODAL_STAY = ".deliver-greeting-modal__btn:has-text('留在此页')";

    private static final int PAGE_SIZE = 20;

    private final ZhilianService zhilianService;

    private static class PageJob {
        int index;
        String jobTitle;
        String companyName;
        String salary;
        String degree;
        String experience;

        PageJob(int index, String jobTitle, String companyName, String salary, String degree, String experience) {
            this.index = index;
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.salary = salary;
            this.degree = degree;
            this.experience = experience;
        }
    }

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    public void prepare() {
        log.info("智联招聘准备工作开始...");
        resultList.clear();
        isLimit = false;
        log.info("智联招聘准备工作完成");
    }

    public int execute() {
        log.info("智联招聘投递任务开始（新版页面）...");
        long startTime = System.currentTimeMillis();

        try {
            for (String keyword : config.getKeywords()) {
                if (shouldStop() || isLimit) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    break;
                }
                deliverByKeyword(keyword);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("智联招聘投递完成，共投递%d个岗位，用时%s",
                    resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);
            if (resultList.isEmpty()) {
                log.info("未投递新的岗位...");
            }
        } catch (Exception e) {
            log.error("智联招聘投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }
        return resultList.size();
    }

    private void deliverByKeyword(String keyword) {
        log.info("开始投递关键词: {}", keyword);
        sendProgress("正在搜索关键词: " + keyword, null, null);

        for (int pageNum = 1; pageNum <= maxPage; pageNum++) {
            if (shouldStop() || isLimit) {
                return;
            }
            String url = String.format(SEARCH_URL_FMT, config.getCityCode(), encode(keyword), pageNum);
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception e) {
                log.warn("打开搜索页失败（第{}页）: {}", pageNum, e.getMessage());
                return;
            }

            try {
                page.waitForSelector(CARD, new Page.WaitForSelectorOptions().setTimeout(20000));
            } catch (Exception e) {
                log.warn("等待岗位列表超时，结束关键词【{}】", keyword);
                return;
            }
            PlaywrightUtil.sleep(2);

            List<PageJob> jobs = collectCards();
            if (jobs.isEmpty()) {
                log.info("第{}页没有岗位，结束关键词【{}】", pageNum, keyword);
                return;
            }
            sendProgress(String.format("关键词【%s】第%d页，%d个岗位", keyword, pageNum, jobs.size()), null, null);

            for (PageJob pj : jobs) {
                if (shouldStop() || isLimit) {
                    return;
                }
                if (!isDeliverable(pj)) {
                    continue;
                }
                try {
                    deliverOne(pj);
                } catch (Exception e) {
                    log.warn("投递失败，继续下一个: {} | {}", pj.jobTitle, e.getMessage());
                }
                PlaywrightUtil.sleep(1);
            }

            if (jobs.size() < PAGE_SIZE) {
                log.info("关键词【{}】第{}页不足{}条，视为最后一页", keyword, pageNum, PAGE_SIZE);
                return;
            }
        }
    }

    private List<PageJob> collectCards() {
        List<PageJob> jobs = new ArrayList<>();
        Locator cards = page.locator(CARD);
        int n = cards.count();
        for (int i = 0; i < n; i++) {
            Locator card = cards.nth(i);
            String title = safeText(card, CARD_TITLE);
            String company = safeAttr(card, CARD_COMPANY, "title");
            if (company == null || company.isBlank()) {
                company = safeText(card, CARD_COMPANY);
            }
            if (title == null || title.isBlank()) {
                continue;
            }
            String salary = safeText(card, CARD_SALARY);

            String degree = null;
            String experience = null;
            try {
                List<String> tags = card.locator(CARD_TAGS).allInnerTexts();
                for (String t : tags) {
                    String v = t == null ? "" : t.trim();
                    if (v.isEmpty()) {
                        continue;
                    }
                    if (v.contains("本科") || v.contains("硕士") || v.contains("博士") || v.contains("大专") || v.contains("学历不限")) {
                        degree = v;
                    } else if (v.contains("年") || v.contains("经验") || v.contains("应届")) {
                        experience = v;
                    }
                }
            } catch (Exception ignored) {
            }
            jobs.add(new PageJob(i, title.trim(), company == null ? "" : company.trim(),
                    salary == null ? "" : salary.trim(), degree, experience));
        }
        return jobs;
    }

    /** 只投实习岗 */
    private boolean isDeliverable(PageJob pj) {
        if (pj.jobTitle == null || !pj.jobTitle.contains("实习")) {
            log.info("被过滤（非实习岗）：{} | {}", pj.companyName, pj.jobTitle);
            return false;
        }
        return true;
    }

    private void deliverOne(PageJob pj) {
        if (zhilianService.existsByTitleAndCompany(pj.jobTitle, pj.companyName)) {
            log.info("岗位已存在，跳过：{} | {}", pj.jobTitle, pj.companyName);
            return;
        }

        Locator card = page.locator(CARD).nth(pj.index);
        card.scrollIntoViewIfNeeded();
        card.click(new Locator.ClickOptions().setTimeout(10000));
        PlaywrightUtil.sleep(2);

        Locator apply = page.locator(DETAIL_APPLY).first();
        try {
            apply.waitFor(new Locator.WaitForOptions().setTimeout(10000));
        } catch (Exception e) {
            log.warn("详情面板未加载，跳过：{}", pj.jobTitle);
            return;
        }
        String btnText = apply.innerText() == null ? "" : apply.innerText().trim();

        // 公司规模过滤：详情面板摘要里带「/20-99人 · 行业」，0-20 人视为过小
        try {
            Locator summary = page.locator(".job-detail-summary").first();
            if (summary.count() > 0) {
                String st = summary.innerText() == null ? "" : summary.innerText();
                if (st.contains("0-20人") || st.contains("1-49人") || st.contains("少于15人") || st.contains("15-50人")) {
                    log.info("被过滤（公司过小）：{} | {}", pj.companyName, pj.jobTitle);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        if (btnText.contains("继续沟通")) {
            saveJob(pj);
            zhilianService.markDeliveredByTitleAndCompany(pj.jobTitle, pj.companyName);
            log.info("此前已投递，标记完成：{} | {}", pj.jobTitle, pj.companyName);
            return;
        }
        if (!btnText.contains("立即投递")) {
            log.info("按钮文案异常（{}），跳过：{}", btnText, pj.jobTitle);
            return;
        }

        apply.click(new Locator.ClickOptions().setTimeout(10000));
        Locator modal = page.locator(DELIVER_MODAL).first();
        boolean ok;
        try {
            modal.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            String modalText = modal.innerText() == null ? "" : modal.innerText();
            ok = modalText.contains("已向对方发送简历");
            if (!ok && (modalText.contains("上限") || modalText.contains("限制"))) {
                isLimit = true;
            }
        } catch (Exception e) {
            log.warn("未等到投递结果弹窗：{} | {}", pj.jobTitle, e.getMessage());
            ok = false;
        }

        try {
            Locator stay = page.locator(MODAL_STAY).first();
            if (stay.count() > 0) {
                stay.click(new Locator.ClickOptions().setTimeout(5000));
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception ignored) {
        }

        if (ok) {
            saveJob(pj);
            zhilianService.markDeliveredByTitleAndCompany(pj.jobTitle, pj.companyName);
            Job job = new Job();
            job.setJobName(pj.jobTitle);
            job.setCompanyName(pj.companyName);
            resultList.add(job);
            log.info("投递成功 | 公司：{} | 岗位：{} | 薪资：{}", pj.companyName, pj.jobTitle, pj.salary);
        } else {
            log.info("投递未确认 | 公司：{} | 岗位：{}", pj.companyName, pj.jobTitle);
        }
    }

    private void saveJob(PageJob pj) {
        try {
            if (zhilianService.existsByTitleAndCompany(pj.jobTitle, pj.companyName)) {
                return;
            }
            ZhilianJobDataEntity e = new ZhilianJobDataEntity();
            e.setJobId(pj.jobTitle + "|" + pj.companyName);
            e.setJobTitle(pj.jobTitle);
            e.setCompanyName(pj.companyName);
            e.setSalary(pj.salary);
            e.setDegree(pj.degree);
            e.setExperience(pj.experience);
            e.setLocation("");
            e.setJobLink("");
            zhilianService.insertJob(e);
        } catch (Exception ex) {
            log.debug("保存岗位失败: {}", ex.getMessage());
        }
    }

    private String safeText(Locator parent, String selector) {
        try {
            Locator l = parent.locator(selector).first();
            return l.count() > 0 ? l.innerText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator l = parent.locator(selector).first();
            return l.count() > 0 ? l.getAttribute(attr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private String formatDuration(long millis) {
        long sec = millis / 1000;
        if (sec < 60) {
            return sec + "秒";
        }
        long min = sec / 60;
        return min + "分" + (sec % 60) + "秒";
    }

    private void sendProgress(String message, Integer current, Integer total) {
        log.info(message);
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    private boolean shouldStop() {
        return shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get());
    }
}
