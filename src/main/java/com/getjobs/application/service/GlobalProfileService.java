package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 求职档案：只填一次，各平台复用（设置中心「求职档案」分区）。
 * <p>
 * 背景：原先每个平台配置页都要各自填一遍关键词、城市、技能介绍，
 * 而这些内容对同一个求职者来说是同一份东西。新手在七个页面里重复输入，
 * 极易出现「Boss 填了实习、JobsDB 忘了填」这类前后不一致。
 * <p>
 * 这里把「与平台无关」的部分抽到一张全局档案里，平台页一键引用。
 * 平台特有的筛选项（关键词、城市、薪资）仍留在各平台页，因为各平台选项表不同。
 * <p>
 * 存储复用现有的 config 键值表，不新增数据库文件结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalProfileService {

    /** 全局档案在 config 表里的键前缀 */
    private static final String PREFIX = "profile.";

    public static final String KEY_INTRODUCE = PREFIX + "introduce";
    public static final String KEY_RESUME_PATH = PREFIX + "resume_path";
    public static final String KEY_WORK_TYPE = PREFIX + "work_type";
    public static final String KEY_CITY = PREFIX + "city";
    public static final String KEY_DEGREE = PREFIX + "degree";
    public static final String KEY_AVAILABLE_FROM = PREFIX + "available_from";
    public static final String KEY_EMAIL = PREFIX + "email";
    public static final String KEY_PHONE = PREFIX + "phone";
    public static final String KEY_SUMMARY = PREFIX + "summary";

    private final ConfigMapper configMapper;

    /**
     * 工作形式。
     * <p>
     * 实习/兼职/全职决定了两件事：搜索关键词要不要带这些限定词，
     * 以及岗位标题里出现相反形式时要不要跳过（例如投实习却不想看到「正式员工」）。
     */
    public enum WorkType {
        /** 不限 */
        ANY("不限", ""),
        /** 实习 */
        INTERNSHIP("实习", "实习"),
        /** 兼职 */
        PARTTIME("兼职", "兼职"),
        /** 全职 */
        FULLTIME("全职", "全职"),
        /** 校园招聘 */
        CAMPUS("校园招聘", "校园");

        private final String label;
        private final String keyword;

        WorkType(String label, String keyword) {
            this.label = label;
            this.keyword = keyword;
        }

        public String getLabel() {
            return label;
        }

        /** 该工作形式对应的搜索关键词；不限时为空 */
        public String getKeyword() {
            return keyword;
        }

        /** 解析用户传入的值，认不出来时回落到「不限」而不是抛错 */
        public static WorkType from(String raw) {
            if (raw == null || raw.isBlank()) {
                return ANY;
            }
            String v = raw.trim();
            for (WorkType t : values()) {
                if (t.name().equalsIgnoreCase(v) || t.label.equals(v)) {
                    return t;
                }
            }
            return ANY;
        }

        public static String[] labels() {
            WorkType[] all = values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                out[i] = all[i].label;
            }
            return out;
        }
    }

    /**
     * 读取整份全局档案。
     */
    public Map<String, String> getProfile() {
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("introduce", "");
        profile.put("resumePath", "");
        profile.put("workType", WorkType.ANY.getLabel());
        profile.put("city", "");
        profile.put("degree", "");
        profile.put("availableFrom", "");
        profile.put("email", "");
        profile.put("phone", "");
        profile.put("summary", "");

        var rows = configMapper.selectList(new LambdaQueryWrapper<ConfigEntity>()
                .likeRight(ConfigEntity::getConfigKey, PREFIX));
        for (var row : rows) {
            String key = row.getConfigKey();
            if (key == null) {
                continue;
            }
            switch (key) {
                case KEY_INTRODUCE -> profile.put("introduce", nz(row.getConfigValue()));
                case KEY_RESUME_PATH -> profile.put("resumePath", nz(row.getConfigValue()));
                case KEY_WORK_TYPE -> profile.put("workType", nz(row.getConfigValue()));
                case KEY_CITY -> profile.put("city", nz(row.getConfigValue()));
                case KEY_DEGREE -> profile.put("degree", nz(row.getConfigValue()));
                case KEY_AVAILABLE_FROM -> profile.put("availableFrom", nz(row.getConfigValue()));
                case KEY_EMAIL -> profile.put("email", nz(row.getConfigValue()));
                case KEY_PHONE -> profile.put("phone", nz(row.getConfigValue()));
                case KEY_SUMMARY -> profile.put("summary", nz(row.getConfigValue()));
                default -> {
                    // 未知前缀的键不参与，避免以后加键时前端拿不到
                }
            }
        }
        return profile;
    }

    /**
     * 保存全局档案。空值也会写入（用户可能是想清空某一项）。
     */
    @Transactional
    public Map<String, String> saveProfile(Map<String, String> input) {
        if (input == null) {
            throw new IllegalArgumentException("档案内容不能为空。");
        }
        Map<String, String> keyMap = new LinkedHashMap<>();
        keyMap.put("introduce", KEY_INTRODUCE);
        keyMap.put("resumePath", KEY_RESUME_PATH);
        keyMap.put("workType", KEY_WORK_TYPE);
        keyMap.put("city", KEY_CITY);
        keyMap.put("degree", KEY_DEGREE);
        keyMap.put("availableFrom", KEY_AVAILABLE_FROM);
        keyMap.put("email", KEY_EMAIL);
        keyMap.put("phone", KEY_PHONE);
        keyMap.put("summary", KEY_SUMMARY);

        for (var entry : keyMap.entrySet()) {
            String value = input.get(entry.getKey());
            if (value == null) {
                continue;
            }
            if ("workType".equals(entry.getKey())) {
                // 工作形式只接受受支持的值，避免脏数据进库
                value = WorkType.from(value).getLabel();
            }
            upsert(entry.getValue(), value);
        }
        return getProfile();
    }

    /** 当前工作形式 */
    public WorkType getWorkType() {
        return WorkType.from(getProfile().get("workType"));
    }

    /** 技能介绍（全局） */
    public String getIntroduce() {
        return getProfile().getOrDefault("introduce", "");
    }

    /** 简历文件路径（全局） */
    public String getResumePath() {
        return getProfile().getOrDefault("resumePath", "");
    }

    /**
     * 工作形式对应的关键词列表，用于拼进平台搜索词。
     * 例：选了「实习」，返回 ["实习"]；选了「不限」，返回空列表。
     */
    public java.util.List<String> getWorkTypeKeywords() {
        var type = getWorkType();
        if (type == WorkType.ANY) {
            return java.util.List.of();
        }
        return java.util.List.of(type.getKeyword());
    }

    /**
     * 工作形式对应的排除词，用于把相反形式的岗位滤掉。
     * 例：选了「实习」，「全职」「正式」这类岗位应被跳过。
     */
    public java.util.List<String> getWorkTypeExcludeWords() {
        var type = getWorkType();
        return switch (type) {
            case INTERNSHIP -> java.util.List.of("全职", "正式员工", "社招");
            case PARTTIME -> java.util.List.of("全职", "正式员工", "社招");
            case FULLTIME -> java.util.List.of("实习", "兼职", "实习生");
            case CAMPUS -> java.util.List.of("社招");
            case ANY -> java.util.List.of();
        };
    }

    private void upsert(String key, String value) {
        var existing = configMapper.selectOne(new LambdaQueryWrapper<ConfigEntity>()
                .eq(ConfigEntity::getConfigKey, key));
        if (existing != null) {
            existing.setConfigValue(value);
            existing.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(existing);
            return;
        }
        var created = new ConfigEntity();
        created.setConfigKey(key);
        created.setConfigValue(value);
        created.setConfigType("string");
        created.setCategory("profile");
        created.setDescription("求职档案");
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(created);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
