package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * jobsdb_data 表实体
 */
@Data
@TableName("jobsdb_data")
public class JobsdbJobDataEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("job_id")
    private String jobId;

    @TableField("job_title")
    private String jobTitle;

    @TableField("job_link")
    private String jobLink;

    @TableField("salary")
    private String salary;

    @TableField("location")
    private String location;

    @TableField("company_name")
    private String companyName;

    @TableField("delivery_status")
    private String deliveryStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
