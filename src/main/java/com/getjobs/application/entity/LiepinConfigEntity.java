package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("liepin_config")
public class LiepinConfigEntity {
    @TableId(type = IdType.AUTO)
    /** 主键ID */
    private Long id;

    /** 搜索关键词 */
    private String keywords;

    /** 城市（名称或代码） */
    private String city;

    /** 薪资代码或范围 */
    private String salaryCode;

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
