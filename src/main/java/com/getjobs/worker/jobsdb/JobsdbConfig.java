package com.getjobs.worker.jobsdb;

import lombok.Data;

import java.util.List;

/**
 * JobsDB（香港）投递配置
 */
@Data
public class JobsdbConfig {

    /**
     * 搜索关键词列表（建议英文，如 internship / finance internship）
     */
    private List<String> keywords;

    /**
     * 城市（JobsDB 香港站，默认 Hong Kong）
     */
    private String city;

    /**
     * 登录账号（SEEK 邮箱）
     */
    private String account;

    /**
     * 登录密码
     */
    private String password;

    /**
     * 投递用简历 PDF 的绝对路径
     */
    private String resumePath;

    /**
     * 标题必含关键词（空 = 不限制标题；英文站建议配 internship 等词）
     */
    private List<String> includeKeywords;

    /**
     * 排除关键词（命中标题或公司名即跳过）
     */
    private List<String> excludeKeywords;

    /** AI cover letter 开关（默认开，关闭后用固定模板） */
    private boolean enableAi = true;

    /** 每个岗位之间的投递间隔（秒，参考 Boss wait_time） */
    private int waitTime = 2;

    /** 只采集岗位、不投递 */
    private boolean collectOnly;
}
