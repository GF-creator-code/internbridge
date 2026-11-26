package com.getjobs.application.controller;

import com.getjobs.application.service.DailySummaryService;
import com.getjobs.application.service.DeliveryStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 投递数据看板接口：统计投递/打招呼数量与回复情况。
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final DeliveryStatsService deliveryStatsService;
    private final DailySummaryService dailySummaryService;

    /**
     * 今日汇总（看板默认视图）。
     */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> today() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", deliveryStatsService.today());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取今日统计失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 指定日期范围的汇总。
     *
     * @param from 起始日期，默认今天
     * @param to   结束日期，默认今天
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalDate start = parseDate(from, LocalDate.now());
            LocalDate end = parseDate(to, LocalDate.now());
            if (end.isBefore(start)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            response.put("success", true);
            response.put("data", deliveryStatsService.summary(start, end));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取统计失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 有回复的岗位明细。
     */
    @GetMapping("/replies")
    public ResponseEntity<Map<String, Object>> replies(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalDate start = parseDate(from, LocalDate.now());
            LocalDate end = parseDate(to, LocalDate.now());
            response.put("success", true);
            response.put("data", deliveryStatsService.repliedJobs(start, end));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取回复明细失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 立即生成一次总结（不想等到定时点时手动触发）。
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, Object>> summarize(@RequestParam(required = false) String date) {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalDate target = parseDate(date, LocalDate.now().minusDays(1));
            response.put("success", true);
            response.put("data", dailySummaryService.summarize(target));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("生成总结失败", e);
            response.put("success", false);
            response.put("message", "生成失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 历史总结列表。
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(@RequestParam(defaultValue = "14") int limit) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", dailySummaryService.history(Math.max(1, Math.min(limit, 60))));
        response.put("settings", Map.of(
                "enabled", dailySummaryService.isEnabled(),
                "time", dailySummaryService.getSummaryTime().toString()));
        return ResponseEntity.ok(response);
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
