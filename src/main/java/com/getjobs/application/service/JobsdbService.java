package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.getjobs.application.entity.JobsdbConfigEntity;
import com.getjobs.application.entity.JobsdbJobDataEntity;
import com.getjobs.application.mapper.JobsdbConfigMapper;
import com.getjobs.application.mapper.JobsdbJobDataMapper;
import com.getjobs.worker.jobsdb.JobsdbConfig;
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
public class JobsdbService {
    private final JobsdbConfigMapper jobsdbConfigMapper;
    private final JobsdbJobDataMapper jobsdbJobDataMapper;
    private final DataSource dataSource;

    // ==================== 配置 ====================

    public JobsdbConfigEntity getFirstConfig() {
        QueryWrapper<JobsdbConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return jobsdbConfigMapper.selectOne(wrapper);
    }

    public JobsdbConfig loadJobsdbConfig() {
        JobsdbConfigEntity entity = getFirstConfig();
        JobsdbConfig config = new JobsdbConfig();
        if (entity == null) {
            log.warn("jobsdb_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCity("香港");
            return config;
        }
        config.setKeywords(parseListString(entity.getKeywords()));
        config.setCity(safeTrim(entity.getCity()) == null ? "香港" : safeTrim(entity.getCity()));
        config.setAccount(safeTrim(entity.getAccount()));
        config.setPassword(entity.getPassword());
        config.setResumePath(safeTrim(entity.getResumePath()));
        config.setCollectOnly(entity.getCollectOnly() != null && entity.getCollectOnly());
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

    public JobsdbConfigEntity updateConfig(JobsdbConfigEntity config) {
        if (config == null) return null;
        if (config.getId() != null) {
            jobsdbConfigMapper.updateById(config);
            return jobsdbConfigMapper.selectById(config.getId());
        }
        return saveOrUpdateFirstSelective(config);
    }

    public JobsdbConfigEntity saveOrUpdateFirstSelective(JobsdbConfigEntity incoming) {
        JobsdbConfigEntity first = getFirstConfig();
        LocalDateTime now = LocalDateTime.now();
        if (first == null) {
            JobsdbConfigEntity toInsert = new JobsdbConfigEntity();
            toInsert.setKeywords(incoming.getKeywords());
            toInsert.setCity(incoming.getCity());
            toInsert.setAccount(incoming.getAccount());
            toInsert.setPassword(incoming.getPassword());
            toInsert.setResumePath(incoming.getResumePath());
            toInsert.setCollectOnly(incoming.getCollectOnly());
            toInsert.setCreatedAt(now);
            toInsert.setUpdatedAt(now);
            jobsdbConfigMapper.insert(toInsert);
            return getFirstConfig();
        } else {
            JobsdbConfigEntity toUpdate = new JobsdbConfigEntity();
            toUpdate.setId(first.getId());
            if (incoming.getKeywords() != null) toUpdate.setKeywords(incoming.getKeywords());
            if (incoming.getCity() != null) toUpdate.setCity(incoming.getCity());
            if (incoming.getAccount() != null) toUpdate.setAccount(incoming.getAccount());
            if (incoming.getPassword() != null) toUpdate.setPassword(incoming.getPassword());
            if (incoming.getResumePath() != null) toUpdate.setResumePath(incoming.getResumePath());
            if (incoming.getCollectOnly() != null) toUpdate.setCollectOnly(incoming.getCollectOnly());
            toUpdate.setCreatedAt(first.getCreatedAt());
            toUpdate.setUpdatedAt(now);
            jobsdbConfigMapper.updateById(toUpdate);
            return jobsdbConfigMapper.selectById(first.getId());
        }
    }

    // ==================== 数据表初始化与数据操作 ====================

    @PostConstruct
    public void ensureJobsdbTablesExist() {
        String createConfig = "CREATE TABLE IF NOT EXISTS jobsdb_config (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " keywords VARCHAR(500)," +
                " city VARCHAR(100)," +
                " account VARCHAR(200)," +
                " password VARCHAR(200)," +
                " resume_path VARCHAR(500)," +
                " collect_only INTEGER DEFAULT 0," +
                " created_at DATETIME," +
                " updated_at DATETIME" +
                ")";
        String createData = "CREATE TABLE IF NOT EXISTS jobsdb_data (" +
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
            stmt.execute(createData);
            try { stmt.execute("ALTER TABLE jobsdb_config ADD COLUMN collect_only INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            log.info("确保 jobsdb_config / jobsdb_data 表已存在");
        } catch (Exception e) {
            log.warn("创建 JobsDB 表失败: {}", e.getMessage());
        }
    }

    public boolean existsByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<JobsdbJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).last("LIMIT 1");
        Long c = jobsdbJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public boolean isDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<JobsdbJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).eq("delivery_status", "已投递").last("LIMIT 1");
        Long c = jobsdbJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public void insertJob(JobsdbJobDataEntity entity) {
        if (entity == null) return;
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getDeliveryStatus() == null) entity.setDeliveryStatus("未投递");
        jobsdbJobDataMapper.insert(entity);
    }

    public void markDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return;
        JobsdbJobDataEntity upd = new JobsdbJobDataEntity();
        upd.setDeliveryStatus("已投递");
        upd.setUpdateTime(LocalDateTime.now());
        UpdateWrapper<JobsdbJobDataEntity> uw = new UpdateWrapper<>();
        uw.eq("job_id", jobId);
        jobsdbJobDataMapper.update(upd, uw);
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
        public List<JobsdbJobDataEntity> items;
        public long total;
        public int page;
        public int size;
    }

    public StatsResponse getJobsdbStats(List<String> statuses, String location,
                                            Double minK, Double maxK, String keyword) {
        QueryWrapper<JobsdbJobDataEntity> wrapper = new QueryWrapper<>();
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
        List<JobsdbJobDataEntity> filtered = jobsdbJobDataMapper.selectList(wrapper);

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

    public PagedResult listJobsdbJobs(List<String> statuses, String location,
                                          String keyword, int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;

        QueryWrapper<JobsdbJobDataEntity> wrapper = new QueryWrapper<>();
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
        List<JobsdbJobDataEntity> all = jobsdbJobDataMapper.selectList(wrapper);

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
