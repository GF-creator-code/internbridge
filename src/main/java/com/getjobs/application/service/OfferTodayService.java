package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.getjobs.application.entity.OfferTodayConfigEntity;
import com.getjobs.application.entity.OfferTodayJobDataEntity;
import com.getjobs.application.mapper.OfferTodayConfigMapper;
import com.getjobs.application.mapper.OfferTodayJobDataMapper;
import com.getjobs.worker.offertoday.OfferTodayConfig;
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
public class OfferTodayService {
    private final OfferTodayConfigMapper offerTodayConfigMapper;
    private final OfferTodayJobDataMapper offerTodayJobDataMapper;
    private final DataSource dataSource;

    // ==================== 配置 ====================

    public OfferTodayConfigEntity getFirstConfig() {
        QueryWrapper<OfferTodayConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return offerTodayConfigMapper.selectOne(wrapper);
    }

    public OfferTodayConfig loadOfferTodayConfig() {
        OfferTodayConfigEntity entity = getFirstConfig();
        OfferTodayConfig config = new OfferTodayConfig();
        if (entity == null) {
            log.warn("offertoday_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCity("香港");
            return config;
        }
        config.setKeywords(parseListString(entity.getKeywords()));
        config.setCity(safeTrim(entity.getCity()) == null ? "香港" : safeTrim(entity.getCity()));
        config.setAccount(safeTrim(entity.getAccount()));
        config.setPassword(entity.getPassword());
        config.setSkipLegal(entity.getSkipLegal() == null || entity.getSkipLegal());
        config.setEnableAi(entity.getEnableAi() == null || entity.getEnableAi());
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

    public OfferTodayConfigEntity updateConfig(OfferTodayConfigEntity config) {
        if (config == null) return null;
        if (config.getId() != null) {
            offerTodayConfigMapper.updateById(config);
            return offerTodayConfigMapper.selectById(config.getId());
        }
        return saveOrUpdateFirstSelective(config);
    }

    public OfferTodayConfigEntity saveOrUpdateFirstSelective(OfferTodayConfigEntity incoming) {
        OfferTodayConfigEntity first = getFirstConfig();
        LocalDateTime now = LocalDateTime.now();
        if (first == null) {
            OfferTodayConfigEntity toInsert = new OfferTodayConfigEntity();
            toInsert.setKeywords(incoming.getKeywords());
            toInsert.setCity(incoming.getCity());
            toInsert.setAccount(incoming.getAccount());
            toInsert.setPassword(incoming.getPassword());
            toInsert.setSkipLegal(incoming.getSkipLegal());
            toInsert.setEnableAi(incoming.getEnableAi());
            toInsert.setWaitTime(incoming.getWaitTime());
            toInsert.setCreatedAt(now);
            toInsert.setUpdatedAt(now);
            offerTodayConfigMapper.insert(toInsert);
            return getFirstConfig();
        } else {
            OfferTodayConfigEntity toUpdate = new OfferTodayConfigEntity();
            toUpdate.setId(first.getId());
            if (incoming.getKeywords() != null) toUpdate.setKeywords(incoming.getKeywords());
            if (incoming.getCity() != null) toUpdate.setCity(incoming.getCity());
            if (incoming.getAccount() != null) toUpdate.setAccount(incoming.getAccount());
            if (incoming.getPassword() != null) toUpdate.setPassword(incoming.getPassword());
            if (incoming.getSkipLegal() != null) toUpdate.setSkipLegal(incoming.getSkipLegal());
            if (incoming.getEnableAi() != null) toUpdate.setEnableAi(incoming.getEnableAi());
            if (incoming.getWaitTime() != null) toUpdate.setWaitTime(incoming.getWaitTime());
            toUpdate.setCreatedAt(first.getCreatedAt());
            toUpdate.setUpdatedAt(now);
            offerTodayConfigMapper.updateById(toUpdate);
            return offerTodayConfigMapper.selectById(first.getId());
        }
    }

    // ==================== 数据表初始化与数据操作 ====================

    @PostConstruct
    public void ensureOfferTodayTablesExist() {
        String createConfig = "CREATE TABLE IF NOT EXISTS offertoday_config (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " keywords VARCHAR(500)," +
                " city VARCHAR(100)," +
                " account VARCHAR(200)," +
                " password VARCHAR(200)," +
                " skip_legal INTEGER DEFAULT 1," +
                " enable_ai INTEGER DEFAULT 1," +
                " wait_time INTEGER DEFAULT 2," +
                " created_at DATETIME," +
                " updated_at DATETIME" +
                ")";
        String createData = "CREATE TABLE IF NOT EXISTS offertoday_data (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " job_id VARCHAR(128)," +
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
            for (String col : new String[]{"skip_legal INTEGER DEFAULT 1", "enable_ai INTEGER DEFAULT 1", "wait_time INTEGER DEFAULT 2"}) {
                try { stmt.execute("ALTER TABLE offertoday_config ADD COLUMN " + col); } catch (Exception ignored) {}
            }
            stmt.execute(createData);
            log.info("确保 offertoday_config / offertoday_data 表已存在");
        } catch (Exception e) {
            log.warn("创建 OfferToday 表失败: {}", e.getMessage());
        }
    }

    public boolean existsByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<OfferTodayJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).last("LIMIT 1");
        Long c = offerTodayJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public boolean isDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<OfferTodayJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).eq("delivery_status", "已投递").last("LIMIT 1");
        Long c = offerTodayJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public void insertJob(OfferTodayJobDataEntity entity) {
        if (entity == null) return;
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getDeliveryStatus() == null) entity.setDeliveryStatus("未投递");
        offerTodayJobDataMapper.insert(entity);
    }

    public void markDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return;
        OfferTodayJobDataEntity upd = new OfferTodayJobDataEntity();
        upd.setDeliveryStatus("已投递");
        upd.setUpdateTime(LocalDateTime.now());
        UpdateWrapper<OfferTodayJobDataEntity> uw = new UpdateWrapper<>();
        uw.eq("job_id", jobId);
        offerTodayJobDataMapper.update(upd, uw);
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
        public List<OfferTodayJobDataEntity> items;
        public long total;
        public int page;
        public int size;
    }

    public StatsResponse getOfferTodayStats(List<String> statuses, String location,
                                            Double minK, Double maxK, String keyword) {
        QueryWrapper<OfferTodayJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream()
                    .filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw).or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time");
        List<OfferTodayJobDataEntity> filtered = offerTodayJobDataMapper.selectList(wrapper);

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
        charts.salaryBuckets = new ArrayList<>();
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

    public PagedResult listOfferTodayJobs(List<String> statuses, String location,
                                          String keyword, int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;

        QueryWrapper<OfferTodayJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream()
                    .filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw).or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time");
        List<OfferTodayJobDataEntity> all = offerTodayJobDataMapper.selectList(wrapper);

        int total = all.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(total, from + size);

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
