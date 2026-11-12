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
}
