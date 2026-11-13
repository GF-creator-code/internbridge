package com.getjobs.application.controller;

import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.service.GlobalBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 全局配置接口：黑名单等跨平台共享配置。
 * 数据与 Boss 原有端点同表（boss_blacklist），双向管理等效。
 */
@RestController
@RequestMapping("/api/global")
@RequiredArgsConstructor
public class GlobalConfigController {

    private final GlobalBlacklistService globalBlacklistService;

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
}
