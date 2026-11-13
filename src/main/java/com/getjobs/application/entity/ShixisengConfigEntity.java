package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shixiseng_config")
public class ShixisengConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 搜索关键词（逗号或括号列表，例如 "[行研,投研]" 或 "行研,投研"） */
    private String keywords;

    /** 城市（中文名，直接拼进搜索 URL） */
    private String city;

    /** 标题必含关键词（JSON 数组字符串，空 = 不限制标题） */
    @com.baomidou.mybatisplus.annotation.TableField("include_keywords")
    private String includeKeywords;

    /** 排除关键词（JSON 数组字符串，命中标题或公司名即跳过） */
    @com.baomidou.mybatisplus.annotation.TableField("exclude_keywords")
    private String excludeKeywords;

    /** 投递间隔（秒） */
    @com.baomidou.mybatisplus.annotation.TableField("wait_time")
    private Integer waitTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
