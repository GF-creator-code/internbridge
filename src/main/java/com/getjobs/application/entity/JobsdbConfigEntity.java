package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("jobsdb_config")
public class JobsdbConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 搜索关键词（JSON 数组字符串或逗号分隔） */
    private String keywords;

    /** 城市（默认 Hong Kong） */
    private String city;

    /** 登录账号（SEEK 邮箱） */
    private String account;

    /** 登录密码（WRITE_ONLY：接口不回显，保存时仍可写入） */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 投递用简历 PDF 绝对路径 */
    private String resumePath;

    /** 标题必含关键词（JSON 数组字符串，空 = 不限制标题） */
    @com.baomidou.mybatisplus.annotation.TableField("include_keywords")
    private String includeKeywords;

    /** 排除关键词（JSON 数组字符串，命中标题或公司名即跳过） */
    @com.baomidou.mybatisplus.annotation.TableField("exclude_keywords")
    private String excludeKeywords;

    /** AI cover letter 开关 */
    @com.baomidou.mybatisplus.annotation.TableField("enable_ai")
    private Boolean enableAi;

    /** 投递间隔（秒） */
    @com.baomidou.mybatisplus.annotation.TableField("wait_time")
    private Integer waitTime;

    /** 只采集不投递 */
    @com.baomidou.mybatisplus.annotation.TableField("collect_only")
    private Boolean collectOnly;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
