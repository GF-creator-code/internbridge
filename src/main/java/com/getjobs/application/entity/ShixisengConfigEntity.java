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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
