package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zhilian_config")
public class ZhilianConfigEntity {
    @TableId(type = IdType.AUTO)
    /** 主键ID */
    private Long id;

    /** 搜索关键词（逗号或括号列表，例如 "[Java,后端]" 或 "Java,后端"） */
    private String keywords;

    /** 城市（中文名或代码，单值） */
    private String cityCode;

    /** 薪资范围（中文名或代码，单值） */
    private String salary;

    /** 标题必含关键词（JSON 数组字符串，空 = 不限制标题） */
    @com.baomidou.mybatisplus.annotation.TableField("include_keywords")
    private String includeKeywords;

    /** 排除关键词（JSON 数组字符串，命中标题或公司名即跳过） */
    @com.baomidou.mybatisplus.annotation.TableField("exclude_keywords")
    private String excludeKeywords;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}