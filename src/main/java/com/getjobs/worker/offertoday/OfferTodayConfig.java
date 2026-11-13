package com.getjobs.worker.offertoday;

import lombok.Data;

import java.util.List;

/**
 * OfferToday（香港）投递配置
 */
@Data
public class OfferTodayConfig {

    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市（OfferToday 只有香港岗位，保留字段用于展示/兼容）
     */
    private String city;

    /**
     * 登录账号（邮箱或手机号）
     */
    private String account;

    /**
     * 登录密码
     */
    private String password;

    /**
     * 标题必含关键词（空 = 不限制标题；實習类型卡片已在采集层按平台特性过滤）
     */
    private List<String> includeKeywords;

    /**
     * 排除关键词（命中标题或公司名即跳过）
     */
    private List<String> excludeKeywords;

    /** AI 招呼语开关（默认开，关闭后用固定模板） */
    private boolean enableAi = true;

    /** 每个岗位之间的投递间隔（秒，参考 Boss wait_time） */
    private int waitTime = 2;
}
