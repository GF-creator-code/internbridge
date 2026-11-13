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

    /** 跳过法律岗（默认开） */
    private boolean skipLegal = true;

    /** 跳过技术难度大的岗位（默认开，优先非技术岗） */
    private boolean skipTechnical = true;

    /** 每个岗位之间的投递间隔（秒，参考 Boss wait_time） */
    private int waitTime = 2;
}
