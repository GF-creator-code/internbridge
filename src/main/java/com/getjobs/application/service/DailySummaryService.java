package com.getjobs.application.service;

import com.getjobs.worker.utils.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每日投递总结。
 * <p>
 * 每天在固定时段（默认 21:00）汇总前一天的投递与回复情况，
 * 输出到日志、推送到企业微信机器人（若已配置），并写入历史记录供看板查看。
 * <p>
 * 定时时间可在设置中心用 daily_summary_time 调整，格式 HH:mm。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** 每天几点执行（默认 21:00） */
    static final String CONFIG_KEY = "daily_summary_time";
    /** 是否启用每日总结 */
    static final String CONFIG_ENABLED = "daily_summary_enabled";

    private final DeliveryStatsService deliveryStatsService;
    private final ConfigService configService;

    /** 历史总结（进程内保留，重启后从数据库重新拉取） */
    private final List<Map<String, Object>> history = new ArrayList<>();

    /**
     * 每天定时执行。
     * <p>
     * 用 cron 表达式固定在整点附近触发，再在方法内判断当前时间是否已到设定时刻，
     * 这样用户改配置不必重启服务。
     */
    @Scheduled(cron = "0 * * * * *")
    public void scheduledSummary() {
        try {
            if (!isEnabled()) {
                return;
            }
            LocalTime target = getSummaryTime();
            LocalDateTime now = LocalDateTime.now();
            // 只在设定时刻之后的这一分钟内执行，避免每分钟重复
            if (now.toLocalTime().isBefore(target) || now.toLocalTime().isAfter(target.plusMinutes(1))) {
                return;
            }
            // 同一分钟只跑一次
            if (now.getMinute() != target.getMinute()) {
                return;
            }
            summarize(LocalDate.now().minusDays(1));
        } catch (Exception e) {
            log.error("每日投递总结执行失败", e);
        }
    }

    /**
     * 生成并记录一次总结。
     *
     * @param date 统计哪一天
     */
    public Map<String, Object> summarize(LocalDate date) {
        var stat = deliveryStatsService.summary(date, date);
        var report = deliveryStatsService.buildDailyReport(date);

        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("date", date.toString());
        entry.put("generatedAt", LocalDateTime.now().toString());
        entry.put("total", stat.get("total"));
        entry.put("delivered", stat.get("delivered"));
        entry.put("replied", stat.get("replied"));
        entry.put("report", report);
        entry.put("platforms", stat.get("platforms"));

        synchronized (history) {
            history.removeIf(e -> date.toString().equals(e.get("date")));
            history.add(0, entry);
            while (history.size() > 30) {
                history.remove(history.size() - 1);
            }
        }

        log.info("每日投递总结:\n{}", report);

        // 推送到企业微信机器人（未配置时 Bot 内部会跳过）
        try {
            Bot.sendMessageByTime(report);
        } catch (Exception e) {
            log.warn("推送每日总结失败: {}", e.getMessage());
        }

        return entry;
    }

    /**
     * 历史总结列表（最近的在前）。
     */
    public List<Map<String, Object>> history(int limit) {
        synchronized (history) {
            return new ArrayList<>(history.subList(0, Math.min(limit, history.size())));
        }
    }

    /** 是否启用 */
    public boolean isEnabled() {
        String flag = configService.getConfigValue(CONFIG_ENABLED);
        // 默认开启：用户没配过就按开启处理，配了才按配的来
        if (flag == null || flag.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(flag) || "1".equals(flag);
    }

    /** 设定每天几点执行 */
    public LocalTime getSummaryTime() {
        String raw = configService.getConfigValue(CONFIG_KEY);
        if (raw == null || raw.isBlank()) {
            return LocalTime.of(21, 0);
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMAT);
        } catch (Exception e) {
            log.warn("每日总结时间格式不正确 [{}]，回落到 21:00", raw);
            return LocalTime.of(21, 0);
        }
    }
}
