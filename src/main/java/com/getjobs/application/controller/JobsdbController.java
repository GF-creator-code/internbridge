package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.entity.JobsdbConfigEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.TaskProgressBroadcaster;
import com.getjobs.application.service.JobsdbService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.JobsdbJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JobsDB（香港）控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/jobsdb")
@CrossOrigin(origins = "*")
public class JobsdbController {

    @Autowired
    private JobsdbService jobsdbService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private JobsdbJobService jobsdbJobService;

    @Autowired
    private TaskProgressBroadcaster taskProgressBroadcaster;

    // ==================== 配置管理 ====================

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        JobsdbConfigEntity config = jobsdbService.getFirstConfig();
        if (config == null) {
            config = new JobsdbConfigEntity();
        }
        result.put("config", config);
        return result;
    }

    @PutMapping("/config")
    public JobsdbConfigEntity updateConfig(@RequestBody JobsdbConfigEntity config) {
        return jobsdbService.updateConfig(config);
    }

    // ==================== 登录和认证 ====================

    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("jobsdb");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查 JobsDB 登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 触发 JobsDB 登录：优先用配置里的账号密码自动登录，
     * 失败时退回「打开登录弹窗让用户手动完成」。
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> triggerLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.triggerJobsdbLogin();
            response.put("success", true);
            response.put("message", "已触发 JobsDB 登录（账号密码自动登录，必要时请在浏览器窗口手动完成）");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发 JobsDB 登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.setLoginStatus("jobsdb", false);
            cookieService.clearCookieByPlatform("jobsdb", "manual logout");
            response.put("success", true);
            response.put("message", "JobsDB 已退出登录，数据库 Cookie 已清理");
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
            CookieEntity cookie = cookieService.getCookieByPlatform("jobsdb");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "jobsdb");
                data.put("message", "未找到 JobsDB Cookie 记录");
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
            playwrightManager.saveJobsdbCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存 JobsDB Cookie 到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存 Cookie 失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 数据分析与列表 ====================

    @GetMapping("/stats")
    public JobsdbService.StatsResponse stats(
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
        return jobsdbService.getJobsdbStats(statusList, location, null, null, keyword);
    }

    @GetMapping("/list")
    public JobsdbService.PagedResult list(
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
        return jobsdbService.listJobsdbJobs(statusList, location, keyword, page, size);
    }

    // ==================== 任务管理 ====================

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (jobsdbJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "JobsDB 任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 登录检查：JobsDB 的岗位列表和 Quick apply 都走 SEEK 账号，
            // 未登录时会被重定向到 login.seek.com，任务跑起来只会空转。
            // 其余六个平台都在这里拦一道，JobsDB 原先漏了，前端会显示"启动成功"
            // 但实际什么也没采到 —— 新手完全看不出原因。
            if (!playwrightManager.isLoggedIn("jobsdb")) {
                response.put("success", false);
                response.put("message", "请先登录 JobsDB（需要 SEEK 账号）");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            CompletableFuture.runAsync(() -> {
                jobsdbJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                    taskProgressBroadcaster.broadcast(progressMessage);
                });
            });

            response.put("success", true);
            response.put("message", "JobsDB 任务启动成功");
            response.put("status", "started");
            log.info("通过API启动 JobsDB 任务成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动 JobsDB 任务失败", e);
            response.put("success", false);
            response.put("message", "启动任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!jobsdbJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的 JobsDB 任务");
                return ResponseEntity.badRequest().body(response);
            }

            jobsdbJobService.stopDelivery();
            response.put("success", true);
            response.put("message", "JobsDB 任务停止请求已发送");
            log.info("通过API停止 JobsDB 任务");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止 JobsDB 任务失败", e);
            response.put("success", false);
            response.put("message", "停止任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> status = jobsdbJobService.getStatus();
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
        response.put("service", "JobsdbController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
