package com.getjobs.application.controller;

import com.getjobs.application.entity.BossOptionEntity;
import com.getjobs.application.service.BossService;
import com.getjobs.application.service.GlobalProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职档案接口（设置中心「求职档案」分区）。
 * <p>
 * 只填一次，各平台页引用：技能介绍、简历路径、工作形式、城市、学历、
 * 可到岗时间、联系方式、个人摘要。
 */
@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class GlobalProfileController {

    private final GlobalProfileService globalProfileService;
    private final BossService bossService;

    /** 读取全局档案 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", globalProfileService.getProfile());
            response.put("message", "获取成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取全局档案失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /** 保存全局档案 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveProfile(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            var saved = globalProfileService.saveProfile(body);
            response.put("success", true);
            response.put("data", saved);
            response.put("message", "全局档案已保存，各平台配置页可一键引用。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("保存全局档案失败", e);
            response.put("success", false);
            response.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /** 工作形式选项，供前端渲染单选按钮 */
    @GetMapping("/work-types")
    public ResponseEntity<Map<String, Object>> workTypes() {
        Map<String, Object> response = new HashMap<>();
        var options = new java.util.ArrayList<Map<String, String>>();
        for (var type : GlobalProfileService.WorkType.values()) {
            options.add(Map.of("value", type.getLabel(), "label", type.getLabel(),
                    "keyword", type.getKeyword()));
        }
        response.put("success", true);
        response.put("data", options);
        return ResponseEntity.ok(response);
    }

    /**
     * 全局档案选项：城市、学历、到岗时间。
     * <p>
     * 城市复用 Boss 平台的城市字典（375 个，覆盖内地与港澳），
     * 避免让用户在全局档案里手打城市名 —— 手打的名称和各平台选项表对不上，
     * 保存了也用不了。
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options() {
        Map<String, Object> response = new HashMap<>();
        try {
            var data = new HashMap<String, Object>();
            data.put("city", profileOptions("city"));
            data.put("degree", profileOptions("degree"));
            data.put("availableFrom", AVAILABILITY_OPTIONS);

            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取全局档案选项失败", e);
            response.put("success", false);
            response.put("message", "获取选项失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private static final java.util.List<String> AVAILABILITY_OPTIONS = List.of(
            "随时到岗", "1 个月内", "2 个月内", "3 个月内", "6 个月后", "待定");

    /** 从 Boss 选项表取某类的名称列表 */
    private java.util.List<String> profileOptions(String type) {
        return bossService.getOptionsByType(type).stream()
                .map(option -> option.getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }
}
