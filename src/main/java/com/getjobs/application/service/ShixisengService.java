package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.entity.ShixisengJobDataEntity;
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
    private final ShixisengJobDataMapper shixisengJobDataMapper;
    private final DataSource dataSource;

    /**
     * 实习僧平台种子：整站都是实习岗（必含词为空）；排除词叠加开发/算法/硬件等
     * 纯技术方向（历史「跳过技术岗」规则的数字化迁移，可在配置页直接增删）。
     */
    private static final List<String> SEED_INCLUDE = new ArrayList<>();
    private static final List<String> SEED_EXCLUDE_EXTRA = List.of(
            "开发", "開發", "研发", "研發", "后端", "後端", "前端", "全栈", "全端",
            "客户端", "客戶端", "服务端", "服務端", "算法工程师", "算法研究",
            "机器学习", "機器學習", "深度学习", "深度學習", "大模型", "NLP", "自然语言", "自然語言",
            "计算机视觉", "計算機視覺", "嵌入式", "硬件", "硬體", "芯片", "晶片", "FPGA",
            "驱动", "驅動", "测试开发", "測試開發", "自动化测试", "自動化測試",
            "运维", "運維", "网络工程", "網絡工程", "安全工程师", "安全工程師", "渗透", "滲透",
            "架构师", "架構師", "Java", "C++", "Golang", "Go开发", "Python开发",
            "数据开发", "數據開發", "大数据开发", "大數據開發", "爬虫", "爬蟲",
            "游戏开发", "遊戲開發", "Unity", "安卓", "iOS");

    // ==================== 配置 ====================

    /** 获取第一条配置（通常只有一条） */
    public ShixisengConfigEntity getFirstConfig() {
        QueryWrapper<ShixisengConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return shixisengConfigMapper.selectOne(wrapper);
    }

    /** 关键词列首次为空时写入种子值（一次性迁移，之后由用户在配置页维护） */
    private void seedKeywordsIfBlank(ShixisengConfigEntity entity) {
        boolean changed = false;
        if (entity.getIncludeKeywords() == null) {
            entity.setIncludeKeywords(com.getjobs.worker.utils.KeywordFilter.serialize(SEED_INCLUDE));
            changed = true;
        }
        if (entity.getExcludeKeywords() == null) {
            entity.setExcludeKeywords(com.getjobs.worker.utils.KeywordFilter.serialize(
                    com.getjobs.worker.utils.KeywordFilter.merge(
                            com.getjobs.worker.utils.KeywordFilter.DEFAULT_EXCLUDE, SEED_EXCLUDE_EXTRA)));
            changed = true;
        }
        if (changed && entity.getId() != null) {
            ShixisengConfigEntity patch = new ShixisengConfigEntity();
            patch.setId(entity.getId());
            patch.setIncludeKeywords(entity.getIncludeKeywords());
            patch.setExcludeKeywords(entity.getExcludeKeywords());
            try {
                shixisengConfigMapper.updateById(patch);
                log.info("实习僧关键词种子已写入（include {} 项 / exclude {} 项）",
                        KeywordFilter.parse(entity.getIncludeKeywords()).size(),
                        KeywordFilter.parse(entity.getExcludeKeywords()).size());
            } catch (Exception e) {
                log.warn("写入实习僧关键词种子失败: {}", e.getMessage());
            }
        }
    }

    /** 从专表构建 ShiXiSengConfig */
    public ShiXiSengConfig loadShixisengConfig() {
        ShixisengConfigEntity entity = getFirstConfig();
        ShiXiSengConfig config = new ShiXiSengConfig();
        if (entity == null) {
            log.warn("shixiseng_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCity("北京");
            return config;
        }
        config.setKeywords(parseListString(entity.getKeywords()));
        config.setCity(safeTrim(entity.getCity()) == null ? "北京" : safeTrim(entity.getCity()));
        seedKeywordsIfBlank(entity);
        config.setIncludeKeywords(KeywordFilter.parse(entity.getIncludeKeywords()));
        config.setExcludeKeywords(KeywordFilter.parse(entity.getExcludeKeywords()));
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
            ShixisengConfigEntity first = getFirstConfig();
            if (first != null) {
                seedKeywordsIfBlank(first);
            }
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

        wrapper.orderByDesc("create_time");
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

        wrapper.orderByDesc("create_time");
        List<ShixisengJobDataEntity> all = shixisengJobDataMapper.selectList(wrapper);

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
