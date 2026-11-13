package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.ShixisengJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 实习僧控制器
 * 提供实习僧平台配置管理、Cookie管理和登录状态控制的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/shixiseng")
@CrossOrigin(origins = "*")
public class ShixisengController {

    @Autowired
    private ShixisengService shixisengService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private ShixisengJobService shixisengJobService;

    // ==================== 配置管理相关接口 ====================

    @GetMapping("/config")
    public Map<String, Object> getAllShixisengConfig() {
        Map<String, Object> result = new HashMap<>();
        ShixisengConfigEntity config = shixisengService.getFirstConfig();
        if (config == null) {
            config = new ShixisengConfigEntity();
        }
        result.put("config", config);
        return result;
    }

    @PutMapping("/config")
    public ShixisengConfigEntity updateConfig(@RequestBody ShixisengConfigEntity config) {
        return shixisengService.updateConfig(config);
    }

    // ==================== 登录和认证相关接口 ====================

    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("shixiseng");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 触发实习僧登录：打开实习僧首页并点击「登录/注册」，
     * 用户在自动化浏览器里完成登录后，监控会自动检测并保存 Cookie
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> triggerShixisengLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.triggerShixisengLogin();
            response.put("success", true);
            response.put("message", "已打开实习僧登录入口，请在弹出的浏览器窗口完成登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发实习僧登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logoutShixiseng() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.setLoginStatus("shixiseng", false);
            cookieService.clearCookieByPlatform("shixiseng", "manual logout");
            response.put("success", true);
            response.put("message", "实习僧已退出登录，数据库Cookie已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== Cookie管理相关接口 ====================

    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> getShixisengCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("shixiseng");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "shixiseng");
                data.put("message", "未找到实习僧Cookie记录");
            }
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取Cookie记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> saveShixisengCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveShixisengCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存实习僧Cookie到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存实习僧Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 数据分析与列表 ====================

    @GetMapping("/stats")
    public ShixisengService.StatsResponse stats(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return shixisengService.getShixisengStats(statusList, location, null, null, keyword);
    }

    @GetMapping("/list")
    public ShixisengService.PagedResult list(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return shixisengService.listShixisengJobs(statusList, location, keyword, page, size);
    }

    // ==================== 任务管理相关接口 ====================

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startShixisengJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!playwrightManager.isLoggedIn("shixiseng")) {
                response.put("success", false);
                response.put("message", "请先登录实习僧");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            if (shixisengJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "实习僧任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            CompletableFuture.runAsync(() -> {
                shixisengJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "实习僧任务启动成功");
            response.put("status", "started");

            log.info("通过API启动实习僧任务成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动实习僧任务失败", e);
            response.put("success", false);
            response.put("message", "启动实习僧任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopShixisengJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!shixisengJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的实习僧任务");
                return ResponseEntity.badRequest().body(response);
            }

            shixisengJobService.stopDelivery();
            response.put("success", true);
            response.put("message", "实习僧任务停止请求已发送");
            log.info("通过API停止实习僧任务");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止实习僧任务失败", e);
            response.put("success", false);
            response.put("message", "停止实习僧任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> status = shixisengJobService.getStatus();
            response.put("success", true);
            response.putAll(status);
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取当前状态失败", e);
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "ShixisengController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
