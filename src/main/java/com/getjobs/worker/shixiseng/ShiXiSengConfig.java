package com.getjobs.worker.shixiseng;

import lombok.Data;

import java.util.List;

/**
 * 实习僧投递配置
 */
@Data
public class ShiXiSengConfig {

    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市（中文名，直接拼进搜索 URL）
     */
    private String city;

    /**
     * 标题必含关键词（空 = 不限制标题）
     */
    private List<String> includeKeywords;

    /**
     * 排除关键词（命中标题或公司名即跳过）
     */
    private List<String> excludeKeywords;

    /** 每个岗位之间的投递间隔（秒，参考 Boss wait_time） */
    private int waitTime = 2;
}
