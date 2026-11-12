package com.getjobs.worker.shixiseng;

/**
 * 实习僧（shixiseng.com）页面选择器与 URL 常量
 * <p>
 * 勘察结论（2026-11-10，PC 端）：
 * - 搜索页为服务端渲染，每页 20 条，卡片带 data-intern-id 属性（即职位ID）
 * - 分页是 Element UI 客户端分页（.el-pagination），URL 的 p 参数无效，只能点击翻页
 * - 薪资/天数等字段使用图标字体反爬（PUA 码点），只影响这几个字段的可读性，不影响投递
 * - 列表页无投递按钮，投递入口在职位详情页
 */
public final class Locators {

    private Locators() {
    }

    /** 站点首页 */
    public static final String HOME_URL = "https://www.shixiseng.com";

    /** 搜索页模板：关键词 + 城市（均支持中文，需 URL 编码） */
    public static final String SEARCH_URL_FMT = "https://www.shixiseng.com/interns?k=%s&city=%s";

    /** 详情页模板：internId 形如 inn_xxxxxxxx */
    public static final String DETAIL_URL_FMT = "https://www.shixiseng.com/intern/%s?pcm=pc_SearchList";

    /** 搜索结果职位卡（data-intern-id 属性即职位ID） */
    public static final String CARD = "div[data-intern-id]";

    /** 卡片上的 internId 属性名 */
    public static final String CARD_INTERN_ID_ATTR = "data-intern-id";

    /** 卡片职位标题链接（含详情 URL）。注意公司名也用 a.title，
     *  职位标题额外带 .font，必须用组合选择器区分，否则 Playwright 严格模式会因多匹配抛异常 */
    public static final String CARD_TITLE_LINK = "a.title.font";

    /** 卡片城市 */
    public static final String CARD_CITY = "span.city";

    /** 卡片公司名 */
    public static final String CARD_COMPANY = "div.intern-detail__company a";

    /** Element UI 分页「下一页」按钮 */
    public static final String PAGER_NEXT = ".el-pagination .btn-next";

    /** 未登录标志：页头出现「登录/注册」 */
    public static final String LOGIN_SPAN = "span.login";

    /** 已登录标志：页头出现手机号区块 */
    public static final String LOGINED_PHONE = ".logined .phone";

    /** 详情页投递按钮（文案「投个简历」） */
    public static final String DELIVER_BTN = "div.btn-box.resume_apply";

    /** 已投递状态按钮 */
    public static final String DELIVERED_BTN = "div.btn-box.del_apply";

    /** 登录弹窗 */
    public static final String LOGIN_DIALOG = ".login-dialog";

    /** 投递弹窗容器 */
    public static final String DELIVER_DIALOG = ".common-deliver";

    /** 弹窗里的简历选项行（第一条是在线简历 My Resume，第二条是附件简历） */
    public static final String DELIVER_RESUME_OPTION = ".common-deliver .user-resume";

    /** 弹窗底部「确认投递」按钮 */
    public static final String DELIVER_CONFIRM = ".common-deliver__footer .btn";
}
