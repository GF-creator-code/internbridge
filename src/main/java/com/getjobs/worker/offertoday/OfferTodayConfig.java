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
}
