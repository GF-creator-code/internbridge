package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("offertoday_config")
public class OfferTodayConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 搜索关键词（JSON 数组字符串或逗号分隔） */
    private String keywords;

    /** 城市（OfferToday 只有香港岗位，默认「香港」） */
    private String city;

    /** 登录账号（邮箱或手机号） */
    private String account;

    /** 登录密码 */
    private String password;

    /** 跳过法律岗 */
    @com.baomidou.mybatisplus.annotation.TableField("skip_legal")
    private Boolean skipLegal;

    /** AI 招呼语开关 */
    @com.baomidou.mybatisplus.annotation.TableField("enable_ai")
    private Boolean enableAi;

    /** 投递间隔（秒） */
    @com.baomidou.mybatisplus.annotation.TableField("wait_time")
    private Integer waitTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
