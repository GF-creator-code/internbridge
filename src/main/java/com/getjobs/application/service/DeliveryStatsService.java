package com.getjobs.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投递数据统计：跨平台汇总投递/打招呼数量与回复情况。
 * <p>
 * 七个平台的岗位表结构并不一致：
 * <ul>
 *   <li>状态列名不同（delivery_status / delivered）；</li>
 *   <li>时间列名不同（created_at / create_time）；</li>
 *   <li>Boss 额外有 recruitment_status（招聘状态），是回复情况的主要来源。</li>
 * </ul>
 * 这里用统一的查询模板把差异抹平，输出结构对前端保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStatsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 平台定义：表名、状态列、时间列。
     */
    private record PlatformSpec(String key, String label, String table, String statusColumn, String timeColumn,
                                 String titleColumn, String companyColumn) {
    }

    private static final List<PlatformSpec> PLATFORMS = List.of(
            new PlatformSpec("boss", "Boss直聘", "boss_data", "delivery_status", "created_at", "job_name", "company_name"),
            new PlatformSpec("shixiseng", "实习僧", "shixiseng_data", "delivery_status", "create_time", "job_title", "company_name"),
            new PlatformSpec("zhilian", "智联招聘", "zhilian_data", "delivery_status", "create_time", "job_title", "company_name"),
            new PlatformSpec("liepin", "猎聘", "liepin_data", "delivered", "create_time", "job_title", "comp_name"),
            new PlatformSpec("job51", "51job", "job51_data", "delivered", "create_time", "job_title", "comp_name"),
            new PlatformSpec("offertoday", "OfferToday", "offertoday_data", "delivery_status", "create_time", "job_title", "company_name"),
            new PlatformSpec("jobsdb", "JobsDB", "jobsdb_data", "delivery_status", "create_time", "job_title", "company_name")
    );

    /** 视为「已投递」的状态值 */
    private static final List<String> DELIVERED_VALUES = List.of("已投递", "投递成功", "已打招呼", "1", "true");

    /**
     * 某个平台在指定日期范围内的投递统计。
     */
    public Map<String, Object> platformStats(String platformKey, LocalDate from, LocalDate to) {
        var spec = PLATFORMS.stream()
                .filter(p -> p.key().equalsIgnoreCase(platformKey))
                .findFirst()
                .orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", platformKey);
        if (spec == null) {
            result.put("available", false);
            result.put("total", 0);
            result.put("delivered", 0);
            return result;
        }
        result.put("available", true);
        result.put("label", spec.label());

        try {
            result.put("total", scalar(
                    "SELECT COUNT(*) FROM " + spec.table() + " WHERE " + spec.timeColumn() + " >= ? AND "
                            + spec.timeColumn() + " < ?",
                    from.atStartOfDay().toString(), to.plusDays(1).atStartOfDay().toString()));

            String deliveredClause = buildDeliveredClause(spec.statusColumn());
            result.put("delivered", scalar(
                    "SELECT COUNT(*) FROM " + spec.table() + " WHERE " + spec.timeColumn() + " >= ? AND "
                            + spec.timeColumn() + " < ? AND " + deliveredClause,
                    from.atStartOfDay().toString(), to.plusDays(1).atStartOfDay().toString()));

            // 回复情况：只有 Boss 能拿到招聘状态，其余平台暂无数据源
            if ("boss".equals(spec.key())) {
                result.put("replied", scalar(
                        "SELECT COUNT(*) FROM boss_data WHERE created_at >= ? AND created_at < ? "
                                + "AND recruitment_status IS NOT NULL AND TRIM(recruitment_status) <> ''",
                        from.atStartOfDay().toString(), to.plusDays(1).atStartOfDay().toString()));
                result.put("repliedJobs", repliedJobs(from, to));
            } else {
                result.put("replied", 0);
                result.put("repliedJobs", List.of());
                result.put("replyAvailable", false);
            }
        } catch (Exception e) {
            log.warn("统计平台 [{}] 投递数据失败: {}", platformKey, e.getMessage());
            result.put("total", 0);
            result.put("delivered", 0);
            result.put("replied", 0);
            result.put("repliedJobs", List.of());
        }
        return result;
    }

    /**
     * 汇总所有平台。
     */
    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        var platforms = new ArrayList<Map<String, Object>>();
        int total = 0;
        int delivered = 0;
        int replied = 0;
        for (var spec : PLATFORMS) {
            var stat = platformStats(spec.key(), from, to);
            platforms.add(stat);
            total += intOf(stat.get("total"));
            delivered += intOf(stat.get("delivered"));
            replied += intOf(stat.get("replied"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("total", total);
        result.put("delivered", delivered);
        result.put("replied", replied);
        result.put("platforms", platforms);
        return result;
    }

    /**
     * 今天到目前为止的汇总（看板实时用）。
     */
    public Map<String, Object> today() {
        var today = LocalDate.now();
        return summary(today, today);
    }

    /**
     * 有回复的岗位明细。
     */
    public List<Map<String, Object>> repliedJobs(LocalDate from, LocalDate to) {
        var list = new ArrayList<Map<String, Object>>();
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT company_name, job_name, recruitment_status, job_url, created_at FROM boss_data "
                            + "WHERE created_at >= ? AND created_at < ? "
                            + "AND recruitment_status IS NOT NULL AND TRIM(recruitment_status) <> '' "
                            + "ORDER BY created_at DESC LIMIT 100",
                    from.atStartOfDay().toString(), to.plusDays(1).atStartOfDay().toString());
            for (var row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("company", row.get("company_name"));
                item.put("job", row.get("job_name"));
                item.put("status", row.get("recruitment_status"));
                item.put("url", row.get("job_url"));
                item.put("time", row.get("created_at"));
                list.add(item);
            }
        } catch (Exception e) {
            log.warn("读取回复岗位明细失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 生成每日总结文本（用于通知推送）。
     */
    public String buildDailyReport(LocalDate date) {
        var stat = summary(date, date);
        var sb = new StringBuilder();
        sb.append("【投递日报 · ").append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("】\n");
        sb.append("采集岗位：").append(stat.get("total")).append(" 个\n");
        sb.append("实际投递：").append(stat.get("delivered")).append(" 个\n");
        sb.append("收到回复：").append(stat.get("replied")).append(" 个\n");

        var platforms = (List<Map<String, Object>>) stat.get("platforms");
        var detail = new StringBuilder();
        for (var p : platforms) {
            if (!Boolean.TRUE.equals(p.get("available"))) {
                continue;
            }
            int delivered = intOf(p.get("delivered"));
            if (delivered == 0 && intOf(p.get("total")) == 0) {
                continue;
            }
            detail.append("· ").append(p.get("label"))
                    .append("：投 ").append(delivered)
                    .append(" / 采 ").append(p.get("total"));
            if (intOf(p.get("replied")) > 0) {
                detail.append("，回复 ").append(p.get("replied"));
            }
            detail.append("\n");
        }
        if (detail.length() > 0) {
            sb.append("\n分平台：\n").append(detail);
        }

        var replied = repliedJobs(date, date);
        if (!replied.isEmpty()) {
            sb.append("\n有回复的岗位：\n");
            for (var job : replied) {
                sb.append("· ").append(job.get("company")).append(" — ").append(job.get("job"))
                        .append("（").append(job.get("status")).append("）\n");
            }
        }
        return sb.toString();
    }

    /**
     * 生成状态列的匹配条件。不同平台的布尔列存的是 1/0，文本列存的是中文。
     */
    private String buildDeliveredClause(String statusColumn) {
        var conditions = new ArrayList<String>();
        for (var value : DELIVERED_VALUES) {
            conditions.add(statusColumn + " = '" + value + "'");
        }
        return "(" + String.join(" OR ", conditions) + ")";
    }

    private int scalar(String sql, Object... args) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        } catch (Exception e) {
            log.debug("统计查询失败 [{}]: {}", sql, e.getMessage());
            return 0;
        }
    }

    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
