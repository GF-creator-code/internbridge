package com.getjobs.worker.offertoday;

/**
 * OfferToday（offertoday.com）页面选择器与 URL 常量
 * <p>
 * 勘察结论（2026-11-13，PC 端）：
 * - 平台是香港招聘站，岗位全在香港，薪资 HKD
 * - 搜索入口在 /hk/scan，顶部搜索框输入关键词回车出结果（URL 参数不生效，必须走 UI）
 * - 结果卡片为 div.otdJobCard，每屏 10 条；滚动容器是卡片祖先里 overflow:auto 的 div
 * - 卡片首行会出现类型 chip：實習 / 兼職 / 全職
 * - 详情页 /hk/job/&lt;id&gt;，投递入口是「傳送投遞消息」+ 同意勾选 + 发送按钮
 * - 登录弹窗：電郵地址或電話號碼 → 開始 → 密码步骤（或「密碼登入」直达）
 */
public final class Locators {

    private Locators() {
    }

    /** 站点首页 */
    public static final String HOME_URL = "https://www.offertoday.com";

    /** 搜索/推荐页（所有关键词搜索都在这个页面内完成） */
    public static final String SCAN_URL = "https://www.offertoday.com/hk/scan";

    /** 详情页前缀 */
    public static final String JOB_URL_PREFIX = "https://www.offertoday.com";

    /** 搜索结果职位卡 */
    public static final String CARD = "div.otdJobCard";

    /** 卡片标题链接（href 含 /hk/job/） */
    public static final String CARD_TITLE_LINK = ".jobCardTitle a";

    /** 卡片公司链接（href 含 /hk/company/） */
    public static final String CARD_COMPANY_LINK = "a[href*='/hk/company/']";

    /** 顶部搜索框（ARIA name：搜索職位 / 公司 / 技能） */
    public static final String SEARCH_INPUT_ROLE_NAME = "搜索職位 / 公司 / 技能";

    /** 未登录：页头「登入」按钮 */
    public static final String LOGIN_BUTTON = "button:has-text('登入')";

    /** 登录弹窗账号输入框 */
    public static final String LOGIN_ACCOUNT_INPUT = "input[placeholder*='電郵地址'], input[placeholder*='電邮地址']";

    /** 登录弹窗「開始」按钮 */
    public static final String LOGIN_START_BUTTON = "button:has-text('開始')";

    /** 登录弹窗「密碼登入」入口 */
    public static final String LOGIN_PASSWORD_LINK = "text=密碼登入";

    /** 登录弹窗密码输入框 */
    public static final String LOGIN_PASSWORD_INPUT = "input[type='password']";

    /** 登录弹窗提交按钮（密码步骤） */
    public static final String LOGIN_SUBMIT_BUTTON = "button:has-text('登入'), button:has-text('登錄'), button:has-text('登 入')";

    /** 详情页投递入口文案 */
    public static final String APPLY_ENTRY_TEXT = "傳送投遞消息";

    /** 详情页投递同意勾选（checkbox） */
    public static final String APPLY_CONSENT_CHECKBOX = "input[type='checkbox']";

    /** 投递弹窗里的消息输入框（textarea 或 contenteditable） */
    public static final String APPLY_MESSAGE_INPUT = "textarea, [contenteditable='true']";

    /** 投递弹窗发送按钮文案候选 */
    public static final String APPLY_SEND_TEXTS = "發送,傳送,確認,提交,發送消息";
}
