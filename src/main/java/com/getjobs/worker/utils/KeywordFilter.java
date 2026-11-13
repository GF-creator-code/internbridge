package com.getjobs.worker.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通用岗位关键词过滤：include/exclude 双列表机制，所有平台共用。
 * <p>
 * include（标题必含）：列表为空时不限制；非空时岗位标题必须包含其中任意一个词。
 * exclude（排除）：岗位标题或公司名命中其中任意一个词即跳过。
 * <p>
 * 规则本身不写在代码里——各平台的默认种子值
 * 由各平台 Service 在配置为空时作为数据写入，前端配置页可直接增删。
 */
public final class KeywordFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 全平台共享的默认种子：岗位标题必含「实习」。 */
    public static final List<String> DEFAULT_INCLUDE = List.of("实习");

    /** Exclusions are chosen by the applicant. */
    public static final List<String> DEFAULT_EXCLUDE = List.of();

    private KeywordFilter() {
    }

    /**
     * 解析存储值：支持 JSON 数组（["a","b"]）、逗号分隔（中英文逗号皆可）。
     * 空白/null 返回空列表。
     */
    public static List<String> parse(String raw) {
        if (raw == null) return new ArrayList<>();
        String s = raw.trim();
        if (s.isEmpty() || s.equals("[]")) return new ArrayList<>();
        if (s.startsWith("[") && s.endsWith("]")) {
            try {
                JsonNode arr = MAPPER.readTree(s);
                List<String> out = new ArrayList<>();
                for (JsonNode n : arr) {
                    String v = n.asText();
                    if (v != null && !v.trim().isEmpty()) out.add(v.trim());
                }
                return out;
            } catch (Exception ignored) {
            }
        }
        return Arrays.stream(s.replace('，', ',').split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
    }

    /** 序列化为 JSON 数组字符串存库。 */
    public static String serialize(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(list.stream()
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 标题必含判断：includes 为空 → 不限制（true）；否则标题须包含其中任意一个词。
     */
    public static boolean matchesInclude(String title, List<String> includes) {
        if (includes == null || includes.isEmpty()) return true;
        if (title == null) return false;
        String t = title;
        for (String kw : includes) {
            if (kw != null && !kw.isEmpty() && t.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 排除判断：标题或公司命中任意一个排除词 → true（应跳过）。
     */
    public static boolean matchesExclude(String title, String company, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) return false;
        if (title != null) {
            for (String kw : excludes) {
                if (kw != null && !kw.isEmpty() && title.contains(kw)) return true;
            }
        }
        if (company != null) {
            for (String kw : excludes) {
                if (kw != null && !kw.isEmpty() && company.contains(kw)) return true;
            }
        }
        return false;
    }

    /** 返回命中的第一个排除词（用于日志说明原因），未命中返回 null。 */
    public static String hitExcludeWord(String title, String company, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) return null;
        List<String> texts = new ArrayList<>();
        if (title != null && !title.isEmpty()) texts.add(title);
        if (company != null && !company.isEmpty()) texts.add(company);
        for (String kw : excludes) {
            if (kw == null || kw.isEmpty()) continue;
            for (String t : texts) {
                if (t.contains(kw)) return kw;
            }
        }
        return null;
    }

    /** 合并去重（用于种子迁移时叠加平台特有词）。 */
    public static List<String> merge(List<String> base, List<String> extra) {
        List<String> out = new ArrayList<>();
        if (base != null) out.addAll(base);
        if (extra != null) {
            for (String v : extra) {
                if (v != null && !v.trim().isEmpty() && !out.contains(v.trim())) out.add(v.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }
}
