package com.getjobs.worker.shixiseng;

import com.getjobs.application.entity.ShixisengJobDataEntity;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.KeywordFilter;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 实习僧自动投递 - Playwright版本
 * <p>
 * 流程：按关键词打开搜索页 → 逐页解析职位卡（data-intern-id 去重入库）→
 * 逐个在**新标签页**打开详情页点击「投个简历」→ 关闭详情页 → 翻页继续。
 * <p>
 * 注意：列表页没有投递按钮，投递入口在详情页；因此用「新标签页打开详情」的方式
 * 保住列表页的翻页状态，避免每投一岗都要重新翻页。
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ShiXiSeng {

    @Setter
    private Page page;

    @Setter
    private ShiXiSengConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();
    /** 出现登录弹窗时置 true，中断本次投递 */
    private volatile boolean loginRequired = false;

    private static final int MAX_PAGE = 20;

    private final ShixisengService shixisengService;

    private static class PageJob {
        int index;
        String internId;
        String jobTitle;
        String companyName;
        String detailUrl;

        PageJob(int index, String internId, String jobTitle, String companyName, String detailUrl) {
            this.index = index;
            this.internId = internId;
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.detailUrl = detailUrl;
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
        log.info("实习僧准备工作开始...");
        resultList.clear();
        loginRequired = false;
        log.info("实习僧准备工作完成");
    }

    /**
     * 执行投递任务
     *
     * @return 投递数量
     */
    public int execute() {
        log.info("实习僧投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            for (String keyword : config.getKeywords()) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    break;
                }
                deliverByKeyword(keyword);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("实习僧投递完成，共投递%d个岗位，用时%s",
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
            log.error("实习僧投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    /**
     * 按关键词投递
     */
    private void deliverByKeyword(String keyword) {
        if (loginRequired) {
            return;
        }

        try {
            log.info("开始投递关键词: {}", keyword);
            sendProgress("正在搜索关键词: " + keyword, null, null);

            String url = buildSearchUrl(keyword);
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(45_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception navEx) {
                String u = null;
                try { u = page.url(); } catch (Exception ignored) {}
                if (u == null || !u.contains("shixiseng.com")) {
                    throw navEx;
                }
            }
            PlaywrightUtil.sleep(2);

            try {
                page.waitForSelector(Locators.CARD,
                        new Page.WaitForSelectorOptions().setTimeout(15_000));
            } catch (Exception e) {
                log.warn("等待岗位列表超时，跳过当前关键词");
                return;
            }

            int pageNum = 1;
            while (pageNum <= MAX_PAGE) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    return;
                }

                log.info("开始投递【{}】关键词，第【{}】页...", keyword, pageNum);
                sendProgress(String.format("正在投递第%d页", pageNum), pageNum, MAX_PAGE);

                if (!deliverCurrentPage(keyword)) {
                    return;
                }

                PlaywrightUtil.sleep(2);

                if (isNextDisabled()) {
                    log.info("下一页按钮不可点击，结束翻页");
                    break;
                }

                Locator nextBtn = page.locator(Locators.PAGER_NEXT);
                if (nextBtn.count() > 0) {
                    try {
                        nextBtn.first().scrollIntoViewIfNeeded();
                    } catch (Exception ignored) {
                    }
                    nextBtn.first().click();
                    PlaywrightUtil.sleep(3);
                    pageNum++;
                } else {
                    log.info("未找到下一页按钮，结束翻页");
                    break;
                }
            }

            log.info("关键词【{}】投递完成", keyword);
        } catch (Exception e) {
            log.error("投递关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
     * 投递当前页面的所有职位
     *
     * @return 是否继续投递下一页
     */
    private boolean deliverCurrentPage(String keyword) {
        try {
            page.waitForSelector(Locators.CARD,
                    new Page.WaitForSelectorOptions().setTimeout(15_000));

            Locator cards = page.locator(Locators.CARD);
            int count = cards.count();
            log.info("检测到当前页岗位数量: {}", count);

            List<PageJob> jobs = new ArrayList<>();
            List<ShixisengJobDataEntity> toInsert = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    return false;
                }

                Locator card = cards.nth(i);
                String internId = safeGetAttr(card, Locators.CARD_INTERN_ID_ATTR);
                String jobTitle = safeGetText(card, Locators.CARD_TITLE_LINK);
                String jobLink = null;
                try {
                    jobLink = card.locator(Locators.CARD_TITLE_LINK).getAttribute("href");
                } catch (Exception ignored) {
                }
                String city = safeGetText(card, Locators.CARD_CITY);
                String companyName = safeGetText(card, Locators.CARD_COMPANY);

                try {
                    String jid = internId == null ? "" : internId.trim();
                    String jtitle = jobTitle == null ? "" : jobTitle.trim();
                    if (jid.isEmpty() || jtitle.isEmpty()) {
                        log.info("岗位缺少internId或title，跳过采集：title={}，company={}", jtitle, companyName);
                    } else {
                        boolean exists = false;
                        try {
                            exists = shixisengService.existsByJobId(jid);
                        } catch (Exception checkEx) {
                            log.warn("查询internId是否已存在失败: {}", checkEx.getMessage());
                        }
                        if (exists) {
                            log.info("internId已存在，跳过采集：internId={}，title={}", jid, jtitle);
                        } else {
                            ShixisengJobDataEntity entity = new ShixisengJobDataEntity();
                            entity.setJobId(jid);
                            entity.setJobTitle(jtitle);
                            entity.setJobLink(jobLink);
                            entity.setLocation(city);
                            entity.setCompanyName(companyName);
                            entity.setDeliveryStatus("未投递");
                            toInsert.add(entity);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("采集岗位数据失败: {}", ex.getMessage());
                }

                String detailUrl = buildDetailUrl(jid2(internId), jobLink);
                jobs.add(new PageJob(i, internId, jobTitle, companyName, detailUrl));
            }

            if (!toInsert.isEmpty()) {
                for (ShixisengJobDataEntity entity : toInsert) {
                    try {
                        shixisengService.insertJob(entity);
                        log.info("已保存岗位数据：internId={}，title={}，company={}",
                                entity.getJobId(), entity.getJobTitle(), entity.getCompanyName());
                    } catch (Exception ex) {
                        log.warn("保存岗位数据失败: {}", ex.getMessage());
                    }
                }
            }

            // 逐个在新标签页打开详情并投递（保持列表页翻页状态）
            for (PageJob pj : jobs) {
                if (shouldStop() || loginRequired) {
                    sendProgress("用户取消投递或需要登录", null, null);
                    return false;
                }

                try {
                    // 通用关键词过滤：排除词命中标题/公司即跳过；必含词为空则不限标题
                    String excludeHit = KeywordFilter.hitExcludeWord(pj.jobTitle, pj.companyName, config.getExcludeKeywords());
                    if (excludeHit != null) {
                        log.info("命中排除关键词[{}]，跳过：{} | {}", excludeHit, pj.companyName, pj.jobTitle);
                        continue;
                    }
                    if (!KeywordFilter.matchesInclude(pj.jobTitle, config.getIncludeKeywords())) {
                        log.info("标题未命中必含关键词，跳过：{} | {}", pj.companyName, pj.jobTitle);
                        continue;
                    }
                    // 实习僧整站都是实习岗，必含词种子为空，不按标题里的「实习」二字过滤
                    if (deliverOne(pj)) {
                        resultList.add(buildJob(pj));
                    }
                } catch (Exception clickEx) {
                    log.warn("投递失败，继续下一个岗位: {}", clickEx.getMessage());
                }

                PlaywrightUtil.sleep(Math.max(1, config.getWaitTime()));
            }

            return true;
        } catch (Exception e) {
            log.error("投递当前页面失败", e);
            saveCurrentPageHtml();
            return false;
        }
    }

    /**
     * 在新标签页打开详情页并点击投递
     *
     * @return 是否投递成功
     */
    private boolean deliverOne(PageJob pj) {
        if (pj.detailUrl == null || pj.detailUrl.isEmpty()) {
            log.info("岗位【{}】缺少详情链接，跳过", pj.jobTitle);
            return false;
        }

        // 库里已标记投递的直接跳过，省掉一次详情页打开（重扫时能把 90 分钟压到几分钟）
        try {
            if (shixisengService.isDeliveredByJobId(pj.internId)) {
                return false;
            }
        } catch (Exception ignored) {
        }

        Page detail = page.context().newPage();
        try {
            // 详情页的 load 事件常被广告/埋点脚本拖住，等到 DOMCONTENTLOADED 就够用
            try {
                detail.navigate(pj.detailUrl, new Page.NavigateOptions()
                        .setTimeout(45_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception navEx) {
                String u = null;
                try { u = detail.url(); } catch (Exception ignored) {}
                if (u == null || !u.contains("shixiseng.com")) {
                    throw navEx;
                }
            }
            PlaywrightUtil.sleep(2);

            // 已投递状态：按钮变为 del_apply
            Locator delivered = detail.locator(Locators.DELIVERED_BTN);
            if (delivered.count() > 0) {
                log.info("岗位【{}】已投递过，跳过", pj.jobTitle);
                shixisengService.markDeliveredByJobId(pj.internId);
                return false;
            }

            Locator applyBtn = detail.locator(Locators.DELIVER_BTN);
            if (applyBtn.count() == 0) {
                log.info("岗位【{}】未找到投递按钮，跳过", pj.jobTitle);
                return false;
            }

            applyBtn.click();
            PlaywrightUtil.sleep(3);

            // 投递弹窗：选择「附件简历」并确认投递。
            // 该函数内部已完成「选附件简历 → 确认投递 → 标记入库」全流程，
            // 成功即可直接返回，不要再走后面的通用成功判定（那会覆盖结果）
            if (confirmDeliveryWithAttachment(detail, pj)) {
                return true;
            }
            log.info("岗位【{}】投递弹窗处理失败", pj.jobTitle);
            return false;
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

    private Job buildJob(PageJob pj) {
        Job job = new Job();
        job.setJobName(pj.jobTitle);
        job.setCompanyName(pj.companyName);
        return job;
    }

    private boolean isLoggedIn(Page checkPage) {
        try {
            return checkPage.locator(Locators.LOGINED_PHONE).count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsText(Page p, String text) {
        try {
            return p.locator(String.format("text=%s", text)).count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String jid2(String internId) {
        return internId == null ? "" : internId.trim();
    }

    /**
     * 构建搜索 URL（关键词与城市均需 URL 编码）
     */
    private String buildSearchUrl(String keyword) {
        String kw = urlEncode(keyword);
        String city = urlEncode(config.getCity() == null ? "" : config.getCity());
        return String.format(Locators.SEARCH_URL_FMT, kw, city);
    }

    private String buildDetailUrl(String internId, String fallbackLink) {
        if (internId != null && !internId.isEmpty()) {
            return String.format(Locators.DETAIL_URL_FMT, internId);
        }
        return fallbackLink;
    }

    private String urlEncode(String raw) {
        if (raw == null) return "";
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    /**
     * 判断「下一页」按钮是否不可点击
     */
    private boolean isNextDisabled() {
        try {
            Locator nextBtn = page.locator(Locators.PAGER_NEXT);
            if (nextBtn.count() == 0) return true;
            String cls = null;
            try {
                cls = nextBtn.first().getAttribute("class");
            } catch (Exception ignored) {
            }
            String disabledAttr = null;
            try {
                disabledAttr = nextBtn.first().getAttribute("disabled");
            } catch (Exception ignored) {
            }
            if (cls != null && cls.contains("disabled")) return true;
            return disabledAttr != null && ("disabled".equalsIgnoreCase(disabledAttr)
                    || "true".equalsIgnoreCase(disabledAttr));
        } catch (Exception e) {
            return false;
        }
    }

    private String safeGetText(Locator parent, String selector) {
        try {
            Locator element = parent.locator(selector);
            if (element.count() > 0) {
                // 严格模式下多匹配会抛异常，统一取第一个
                return element.first().textContent();
            }
        } catch (Exception e) {
            log.debug("获取文本失败: {}", e.getMessage());
        }
        return "";
    }

    private String safeGetAttr(Locator parent, String attr) {
        try {
            return parent.getAttribute(attr);
        } catch (Exception e) {
            return null;
        }
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

    private void saveCurrentPageHtml() {
        try {
            String html = page.content();
            java.nio.file.Path path = java.nio.file.Paths.get(
                    "src/main/java/com/getjobs/worker/shixiseng/page.html");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("保存当前页面HTML失败: {}", e.getMessage());
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

    /**
     * 在投递弹窗中选择「附件简历」并点确认投递。
     * <p>
     * 实习僧点「投个简历」后弹出「选择投递简历」对话框，默认勾选的是在线简历（My Resume），
     * 需要手动改选「附件简历」再确认 —— 按用户要求只投附件简历。
     */
    private boolean confirmDeliveryWithAttachment(Page detail, PageJob pj) {
        try {
            // 等弹窗出现（容器 class 为 common-deliver）
            Locator dialog = detail.locator(Locators.DELIVER_DIALOG).first();
            boolean hasDialog = false;
            try {
                dialog.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
                hasDialog = dialog.isVisible();
            } catch (Exception ignored) {
            }

            if (!hasDialog) {
                // 部分岗位要求中英文简历齐全，会弹「请完善中&英文简历后投递」
                if (containsText(detail, "请完善中") || containsText(detail, "英文简历")) {
                    log.info("岗位要求中英文简历齐全，跳过：internId={}", pj.internId);
                    return false;
                }
                boolean done = detail.locator(Locators.DELIVERED_BTN).count() > 0
                        || containsText(detail, "投递成功") || containsText(detail, "已投递");
                if (done) {
                    try { shixisengService.markDeliveredByJobId(pj.internId); } catch (Exception ignored) {}
                    log.info("已投递（无弹窗，直接成功）：internId={}", pj.internId);
                }
                return done;
            }

            // 弹窗里第 2 个 user-resume 行是「附件简历」，选中标记是 .right-item__img 里的 radio 图
            boolean picked = false;
            try {
                Locator rows = detail.locator(Locators.DELIVER_RESUME_OPTION);
                int n = rows.count();
                if (n >= 2) {
                    Locator attachRow = rows.nth(1);
                    // 依次尝试：整行 → 单选图 → 左侧名称，直到 radio 图变成 radio-choosed
                    String[] targets = {null, ".right-item__img", ".user-resume__left"};
                    for (int attempt = 0; attempt < targets.length && !picked; attempt++) {
                        try {
                            if (targets[attempt] == null) {
                                attachRow.click(new Locator.ClickOptions().setTimeout(8000));
                            } else {
                                Locator t = attachRow.locator(targets[attempt]).first();
                                if (t.count() > 0) {
                                    t.click(new Locator.ClickOptions().setTimeout(8000));
                                } else {
                                    attachRow.click(new Locator.ClickOptions().setTimeout(8000));
                                }
                            }
                        } catch (Exception clickErr) {
                            log.debug("附件简历选择第 {} 次点击失败: {}", attempt + 1, clickErr.getMessage());
                        }
                        PlaywrightUtil.sleep(1);
                        picked = isAttachmentRowSelected(attachRow);
                        if (picked) {
                            log.info("已选择附件简历（第 {} 种点法）：internId={}", attempt + 1, pj.internId);
                        }
                    }
                    if (!picked) {
                        log.warn("附件简历未被选中，放弃本次投递以免误发在线简历：internId={}", pj.internId);
                        return false;
                    }
                } else {
                    log.warn("弹窗内简历选项不足 2 条（实际 {}），可能未上传附件简历", n);
                    return false;
                }
            } catch (Exception e) {
                log.warn("选择附件简历失败: {}", e.getMessage());
                return false;
            }

            // 点「确认投递」前再确认一次选中状态，防止弹窗重渲染把选择重置回在线简历
            if (!isAttachmentRowSelected(detail.locator(Locators.DELIVER_RESUME_OPTION).nth(1))) {
                log.warn("确认前发现附件简历被取消选中，放弃本次投递：internId={}", pj.internId);
                return false;
            }

            // 点「确认投递」
            boolean confirmed = false;
            try {
                Locator confirm = detail.locator(Locators.DELIVER_CONFIRM).first();
                if (confirm.count() > 0) {
                    confirm.click();
                    confirmed = true;
                    PlaywrightUtil.sleep(2);
                }
            } catch (Exception e) {
                log.warn("点击确认投递失败: {}", e.getMessage());
            }

            boolean success = detail.locator(Locators.DELIVERED_BTN).count() > 0
                    || containsText(detail, "投递成功") || containsText(detail, "已投递")
                    || confirmed;
            if (success) {
                try { shixisengService.markDeliveredByJobId(pj.internId); } catch (Exception ignored) {}
                log.info("已投递（{}）：internId={}，title={}",
                        picked ? "附件简历" : "默认简历", pj.internId, pj.jobTitle);
            }
            return success;
        } catch (Exception e) {
            log.warn("投递弹窗处理异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断某一行简历是否处于选中态（radio 图为 radio-choosed.png）
     */
    private boolean isAttachmentRowSelected(Locator row) {
        try {
            if (row == null || row.count() == 0) {
                return false;
            }
            String src = row.locator(".right-item__img img").first().getAttribute("src");
            return src != null && src.contains("radio-choosed");
        } catch (Exception e) {
            return false;
        }
    }

}
