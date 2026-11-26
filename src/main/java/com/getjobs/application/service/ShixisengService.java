package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.entity.ShixisengJobDataEntity;
import com.getjobs.application.worker.ProfileApplier;
import com.getjobs.application.mapper.ShixisengConfigMapper;
import com.getjobs.application.mapper.ShixisengJobDataMapper;
import com.getjobs.worker.shixiseng.ShiXiSengConfig;
import com.getjobs.worker.utils.KeywordFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShixisengService {
    private final ShixisengConfigMapper shixisengConfigMapper;
    private final ProfileApplier profileApplier;
    private final ShixisengJobDataMapper shixisengJobDataMapper;
    private final DataSource dataSource;

    // ==================== 配置 ====================

    /** 获取第一条配置（通常只有一条） */
    public ShixisengConfigEntity getFirstConfig() {
        QueryWrapper<ShixisengConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return shixisengConfigMapper.selectOne(wrapper);
    }


    /** 从专表构建 ShiXiSengConfig */
    public ShiXiSengConfig loadShixisengConfig() {
        ShixisengConfigEntity entity = getFirstConfig();
        ShiXiSengConfig config = new ShiXiSengConfig();
        if (entity == null) {
            log.warn("shixiseng_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCity("北京");
            config.setIncludeKeywords(List.of());
            config.setExcludeKeywords(List.of());
            return config;
        }
        config.setKeywords(parseListString(entity.getKeywords()));
        config.setKeywords(profileApplier.applyKeywords(config.getKeywords()));
        config.setCity(safeTrim(entity.getCity()) == null ? "北京" : safeTrim(entity.getCity()));
        config.setIncludeKeywords(KeywordFilter.parse(entity.getIncludeKeywords()));
        config.setExcludeKeywords(KeywordFilter.parse(entity.getExcludeKeywords()));
        config.setExcludeKeywords(profileApplier.applyExcludeKeywords(config.getExcludeKeywords()));
        config.setWaitTime(entity.getWaitTime() == null ? 2 : entity.getWaitTime());
        return config;
    }

    public List<String> parseListString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        String s = raw.trim().replace('，', ',');
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (s.trim().isEmpty()) return new ArrayList<>();
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .map(this::stripWrapperQuotes)
                .filter(str -> !str.isEmpty())
                .collect(Collectors.toList());
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private String stripWrapperQuotes(String value) {
        if (value == null || value.length() < 2) return value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * 选择性更新：若传入 ID 则按 ID 更新；否则更新第一条记录（不存在则插入）
     */
    public ShixisengConfigEntity updateConfig(ShixisengConfigEntity config) {
        if (config == null) return null;
        if (config.getId() != null) {
            shixisengConfigMapper.updateById(config);
            return shixisengConfigMapper.selectById(config.getId());
        }
        return saveOrUpdateFirstSelective(config);
    }

    /**
     * 保存或选择性更新第一条记录（仅覆盖非空字段）
     */
    public ShixisengConfigEntity saveOrUpdateFirstSelective(ShixisengConfigEntity incoming) {
        ShixisengConfigEntity first = getFirstConfig();
        LocalDateTime now = LocalDateTime.now();
        if (first == null) {
            ShixisengConfigEntity toInsert = new ShixisengConfigEntity();
            toInsert.setKeywords(incoming.getKeywords());
            toInsert.setCity(incoming.getCity());
            toInsert.setIncludeKeywords(incoming.getIncludeKeywords());
            toInsert.setExcludeKeywords(incoming.getExcludeKeywords());
            toInsert.setWaitTime(incoming.getWaitTime());
            toInsert.setCreatedAt(now);
            toInsert.setUpdatedAt(now);
            shixisengConfigMapper.insert(toInsert);
            return getFirstConfig();
        } else {
            ShixisengConfigEntity toUpdate = new ShixisengConfigEntity();
            toUpdate.setId(first.getId());
            if (incoming.getKeywords() != null) toUpdate.setKeywords(incoming.getKeywords());
            if (incoming.getCity() != null) toUpdate.setCity(incoming.getCity());
            if (incoming.getIncludeKeywords() != null) toUpdate.setIncludeKeywords(incoming.getIncludeKeywords());
            if (incoming.getExcludeKeywords() != null) toUpdate.setExcludeKeywords(incoming.getExcludeKeywords());
            if (incoming.getWaitTime() != null) toUpdate.setWaitTime(incoming.getWaitTime());
            toUpdate.setCreatedAt(first.getCreatedAt());
            toUpdate.setUpdatedAt(now);
            shixisengConfigMapper.updateById(toUpdate);
            return shixisengConfigMapper.selectById(first.getId());
        }
    }

    // ==================== 数据表初始化与数据操作 ====================

    @PostConstruct
    public void ensureShixisengTablesExist() {
        String createConfig = "CREATE TABLE IF NOT EXISTS shixiseng_config (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " keywords VARCHAR(500)," +
                " city VARCHAR(100)," +
                " include_keywords TEXT," +
                " exclude_keywords TEXT," +
                " wait_time INTEGER DEFAULT 2," +
                " created_at DATETIME," +
                " updated_at DATETIME" +
                ")";
        String createData = "CREATE TABLE IF NOT EXISTS shixiseng_data (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " job_id VARCHAR(64)," +
                " job_title VARCHAR(200)," +
                " job_link VARCHAR(300)," +
                " salary VARCHAR(100)," +
                " location VARCHAR(100)," +
                " company_name VARCHAR(200)," +
                " delivery_status VARCHAR(20) DEFAULT '未投递'," +
                " create_time DATETIME," +
                " update_time DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createConfig);
            for (String col : new String[]{"include_keywords TEXT", "exclude_keywords TEXT", "wait_time INTEGER DEFAULT 2"}) {
                try { stmt.execute("ALTER TABLE shixiseng_config ADD COLUMN " + col); } catch (Exception ignored) {}
            }
            stmt.execute(createData);
            log.info("确保 shixiseng_config / shixiseng_data 表已存在");
        } catch (Exception e) {
            log.warn("创建实习僧表失败: {}", e.getMessage());
        }
    }

    public boolean existsByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<ShixisengJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).last("LIMIT 1");
        Long c = shixisengJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    /** 该岗位是否已投递成功（用于跳过重复打开详情页） */
    public boolean isDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<ShixisengJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).eq("delivery_status", "已投递").last("LIMIT 1");
        Long c = shixisengJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public void insertJob(ShixisengJobDataEntity entity) {
        if (entity == null) return;
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getDeliveryStatus() == null) entity.setDeliveryStatus("未投递");
        shixisengJobDataMapper.insert(entity);
    }

    public void markDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return;
        ShixisengJobDataEntity upd = new ShixisengJobDataEntity();
        upd.setDeliveryStatus("已投递");
        upd.setUpdateTime(LocalDateTime.now());
        UpdateWrapper<ShixisengJobDataEntity> uw = new UpdateWrapper<>();
        uw.eq("job_id", jobId);
        shixisengJobDataMapper.update(upd, uw);
    }

    // ==================== 投递分析（Dashboard）与列表 ====================

    public static class Kpi {
        public long total;
        public long delivered;
        public long pending;
        public long failed;
        public Double avgMonthlyK;
    }

    public static class NameValue {
        public String name;
        public long value;

        public NameValue() {
        }

        public NameValue(String n, long v) {
            name = n;
            value = v;
        }
    }

    public static class BucketValue {
        public String bucket;
        public long value;

        public BucketValue() {
        }

        public BucketValue(String b, long v) {
            bucket = b;
            value = v;
        }
    }

    public static class Charts {
        public List<NameValue> byStatus;
        public List<NameValue> byCity;
        public List<NameValue> byCompany;
        public List<BucketValue> salaryBuckets;
        public List<NameValue> dailyTrend;
    }

    public static class StatsResponse {
        public Kpi kpi;
        public Charts charts;
    }

    public static class PagedResult {
        public List<ShixisengJobDataEntity> items;
        public long total;
        public int page;
        public int size;
    }

    /** 获取实习僧投递统计（带筛选） */
    public StatsResponse getShixisengStats(List<String> statuses, String location,
                                           Double minK, Double maxK, String keyword) {
        QueryWrapper<ShixisengJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream()
                    .filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw).or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time", "id");
        List<ShixisengJobDataEntity> filtered = shixisengJobDataMapper.selectList(wrapper);

        Kpi kpi = new Kpi();
        kpi.total = filtered.size();
        kpi.delivered = filtered.stream()
                .filter(e -> "已投递".equals(nullSafe(e.getDeliveryStatus()))).count();
        kpi.pending = filtered.stream()
                .filter(e -> "未投递".equals(nullSafe(e.getDeliveryStatus()))).count();
        kpi.failed = filtered.stream()
                .filter(e -> "投递失败".equals(nullSafe(e.getDeliveryStatus()))).count();

        Charts charts = new Charts();
        charts.byStatus = new ArrayList<>();
        charts.byCity = new ArrayList<>();
        charts.byCompany = new ArrayList<>();
        var salary = new MonthlySalarySummary();
        filtered.forEach(job -> salary.add(job.getSalary()));
        kpi.avgMonthlyK = salary.average();
        charts.salaryBuckets = new ArrayList<>();
        if (kpi.avgMonthlyK != null) {
            for (int i = 0; i < MonthlySalarySummary.LABELS.size(); i++) {
                charts.salaryBuckets.add(new BucketValue(MonthlySalarySummary.LABELS.get(i), salary.bucket(i)));
            }
        }
        charts.dailyTrend = new ArrayList<>();

        Map<String, Long> byStatus = filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getDeliveryStatus()), Collectors.counting()));
        byStatus.forEach((k, v) -> charts.byStatus.add(new NameValue(k, v)));

        Map<String, Long> byCity = filtered.stream()
                .filter(e -> e.getLocation() != null && !e.getLocation().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getLocation()), Collectors.counting()));
        byCity.forEach((k, v) -> charts.byCity.add(new NameValue(k, v)));

        Map<String, Long> byCompany = filtered.stream()
                .filter(e -> e.getCompanyName() != null && !e.getCompanyName().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getCompanyName()), Collectors.counting()));
        byCompany.forEach((k, v) -> charts.byCompany.add(new NameValue(k, v)));

        Map<String, Long> byDay = filtered.stream()
                .filter(e -> e.getCreateTime() != null)
                .collect(Collectors.groupingBy(e -> e.getCreateTime().toLocalDate().toString(), Collectors.counting()));
        byDay.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(en -> charts.dailyTrend.add(new NameValue(en.getKey(), en.getValue())));

        StatsResponse resp = new StatsResponse();
        resp.kpi = kpi;
        resp.charts = charts;
        return resp;
    }

    /** 列表查询（分页 + 筛选 + 关键词） */
    public PagedResult listShixisengJobs(List<String> statuses, String location,
                                         String keyword, int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;

        QueryWrapper<ShixisengJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream()
                    .filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw).or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time", "id");
        List<ShixisengJobDataEntity> all = shixisengJobDataMapper.selectList(wrapper);

        int total = all.size();
        int from = (int) Math.min(total, (long) (page - 1) * size);
        int to = (int) Math.min(total, (long) from + size);

        PagedResult pr = new PagedResult();
        pr.items = all.subList(from, to);
        pr.total = total;
        pr.page = page;
        pr.size = size;
        return pr;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }
}
