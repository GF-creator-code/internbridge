package com.getjobs.worker.jobsdb;

/**
 * JobsDB（hk.jobsdb.com，SEEK 系）页面选择器与 URL 常量
 * <p>
 * 勘察结论（2026-11-13）：
 * - 搜索是 SEO 落地页：关键词提交后跳 /&lt;keyword-slug&gt;-jobs，如 /finance-internship-jobs
 * - 列表每页 30 条，分页用 ?page=N
 * - 卡片是 article，标题链接 /job/&lt;id&gt;，公司链接 /companies/...
 * - 详情页「Quick apply」链接 /job/&lt;id&gt;/apply
 * - 投递是 SEEK 多步表单：Choose documents（个人信息 + 简历 + cover letter）→ Update Profile → Review and submit
 * - 未登录会被重定向到 login.seek.com（Auth0）
 */
public final class Locators {

    private Locators() {
    }

    /** 站点首页 */
    public static final String HOME_URL = "https://hk.jobsdb.com";

    /** 实习搜索落地页（未登录也能用） */
    public static final String SEARCH_LANDING_URL = "https://hk.jobsdb.com/internship-jobs";

    /** 关键词输入框 */
    public static final String KEYWORDS_INPUT = "#keywords-input";

    /** 提交搜索按钮 */
    public static final String SUBMIT_SEARCH = "button[aria-label='Submit search']";

    /** 职位卡片 */
    public static final String CARD = "article";

    /** 卡片标题/详情链接 */
    public static final String CARD_JOB_LINK = "a[href*='/job/']";

    /** 卡片公司链接 */
    public static final String CARD_COMPANY_LINK = "a[href*='/companies/']";

    /** 详情页 Quick apply 链接 */
    public static final String APPLY_LINK = "a[href$='/apply']";

    /** 登录页邮箱输入框（Auth0 Universal Login） */
    public static final String LOGIN_EMAIL = "#username, input[name='email'], input[type='email']";

    /** 登录页密码输入框 */
    public static final String LOGIN_PASSWORD = "#password, input[type='password']";

    /** 登录页提交按钮 */
    public static final String LOGIN_SUBMIT = "button[type='submit']";

    /** 申请表单里的简历文件输入框 */
    public static final String APPLY_FILE_INPUT = "input[type='file']";

    /** 申请表单里的 cover letter 文本域 */
    public static final String APPLY_COVER_LETTER = "textarea";

    /** 申请表单继续按钮文案 */
    public static final String APPLY_CONTINUE_TEXT = "Continue";

    /** 申请表单提交按钮文案候选 */
    public static final String APPLY_SUBMIT_TEXTS = "Submit application,Submit,Submit application and continue";
}
