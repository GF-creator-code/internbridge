package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.entity.OfferTodayConfigEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.TaskProgressBroadcaster;
import com.getjobs.application.service.OfferTodayService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.OfferTodayJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OfferToday（香港）控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/offertoday")
@CrossOrigin(origins = "*")
public class OfferTodayController {

    @Autowired
    private OfferTodayService offerTodayService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private OfferTodayJobService offerTodayJobService;

    @Autowired
    private TaskProgressBroadcaster taskProgressBroadcaster;

    // ==================== 配置管理 ====================

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        OfferTodayConfigEntity config = offerTodayService.getFirstConfig();
        if (config == null) {
            config = new OfferTodayConfigEntity();
        }
        result.put("config", config);
        return result;
    }

    @PutMapping("/config")
    public OfferTodayConfigEntity updateConfig(@RequestBody OfferTodayConfigEntity config) {
        return offerTodayService.updateConfig(config);
    }

    // ==================== 登录和认证 ====================

    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("offertoday");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查 OfferToday 登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 触发 OfferToday 登录：优先用配置里的账号密码自动登录，
     * 失败时退回「打开登录弹窗让用户手动完成」。
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> triggerLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.triggerOfferTodayLogin();
            response.put("success", true);
            response.put("message", "已触发 OfferToday 登录（账号密码自动登录，必要时请在浏览器窗口手动完成）");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发 OfferToday 登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.setLoginStatus("offertoday", false);
            cookieService.clearCookieByPlatform("offertoday", "manual logout");
            response.put("success", true);
            response.put("message", "OfferToday 已退出登录，数据库 Cookie 已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== Cookie 管理 ====================

    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> getCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("offertoday");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "offertoday");
                data.put("message", "未找到 OfferToday Cookie 记录");
            }
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取 Cookie 记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> saveCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveOfferTodayCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存 OfferToday Cookie 到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存 Cookie 失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 数据分析与列表 ====================

    @GetMapping("/stats")
    public OfferTodayService.StatsResponse stats(
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
        return offerTodayService.getOfferTodayStats(statusList, location, null, null, keyword);
    }

    @GetMapping("/list")
    public OfferTodayService.PagedResult list(
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
        return offerTodayService.listOfferTodayJobs(statusList, location, keyword, page, size);
    }

    // ==================== 任务管理 ====================

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!playwrightManager.isLoggedIn("offertoday")) {
                response.put("success", false);
                response.put("message", "请先登录 OfferToday");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            if (offerTodayJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "OfferToday 任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            CompletableFuture.runAsync(() -> {
                offerTodayJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                    taskProgressBroadcaster.broadcast(progressMessage);
                });
            });

            response.put("success", true);
            response.put("message", "OfferToday 任务启动成功");
            response.put("status", "started");
            log.info("通过API启动 OfferToday 任务成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动 OfferToday 任务失败", e);
            response.put("success", false);
            response.put("message", "启动任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!offerTodayJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的 OfferToday 任务");
                return ResponseEntity.badRequest().body(response);
            }

            offerTodayJobService.stopDelivery();
            response.put("success", true);
            response.put("message", "OfferToday 任务停止请求已发送");
            log.info("通过API停止 OfferToday 任务");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止 OfferToday 任务失败", e);
            response.put("success", false);
            response.put("message", "停止任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> status = offerTodayJobService.getStatus();
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
        response.put("service", "OfferTodayController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
