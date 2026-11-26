package com.getjobs.application.controller;

import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.service.GlobalBlacklistService;
import com.getjobs.application.service.GlobalCityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局配置接口：黑名单等跨平台共享配置。
 * 数据与 Boss 原有端点同表（boss_blacklist），双向管理等效。
 */
@Slf4j
@RestController
@RequestMapping("/api/global")
@RequiredArgsConstructor
public class GlobalConfigController {

    private final GlobalBlacklistService globalBlacklistService;
    private final GlobalCityService globalCityService;

    @GetMapping("/blacklist")
    public List<BlacklistEntity> getBlacklist() {
        return globalBlacklistService.getAll();
    }

    @PostMapping("/blacklist")
    public Map<String, Object> addBlacklist(@RequestBody BlacklistEntity blacklist) {
        BlacklistEntity created = globalBlacklistService.add(blacklist.getType(), blacklist.getValue());
        if (created == null) {
            return Map.of("success", false,
                    "message", "添加失败：type 须为 company/job/recruiter，value 不能为空，且不能与已有条目重复");
        }
        return Map.of("success", true, "item", created);
    }

    @DeleteMapping("/blacklist/{id}")
    public Map<String, Object> deleteBlacklist(@PathVariable Long id) {
        boolean success = globalBlacklistService.removeById(id);
        return Map.of("success", success);
    }

    /**
     * 各平台当前城市（供设置中心「全局规则」展示同步状态）
     */
    @GetMapping("/cities")
    public Map<String, Object> cities() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", globalCityService.currentPlatformCities());
        return response;
    }

    /**
     * 把全局城市应用到所有平台。
     * 注意：会覆盖各平台已选的城市，前端调用前需二次确认。
     */
    @PostMapping("/apply-city")
    public Map<String, Object> applyCity() {
        Map<String, Object> response = new HashMap<>();
        try {
            var results = globalCityService.applyGlobalCity();
            boolean allOk = results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("success")));
            response.put("success", allOk);
            response.put("data", results);
            response.put("message", allOk ? "全局城市已应用到各平台。" : "部分平台同步失败，请查看各平台结果。");
            return ResponseEntity.ok(response).getBody();
        } catch (Exception e) {
            log.error("应用全局城市失败", e);
            response.put("success", false);
            response.put("message", "应用失败: " + e.getMessage());
            return response;
        }
    }
}
