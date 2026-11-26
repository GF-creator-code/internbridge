package com.getjobs.worker.utils;

/**
 * 统一的岗位排除规则。
 * <p>用于所有平台在投递前对「岗位标题 + 公司名」做命中判断：
 * <ul>
 *   <li>法律类：法务、律师、诉讼等</li>
 *   <li>招聘/猎头类：招聘、猎头、Recruit、Headhunt（中英繁简）</li>
 * </ul>
 * 命中任一关键词即跳过该岗位，不投递。
 */
public final class JobExcludeRules {

    private JobExcludeRules() {
    }

    /** 法律类关键词 */
    public static final String[] LEGAL_TERMS = {
            "法务", "法律", "律师", "律所", "非诉", "诉讼", "仲裁", "知识产权", "专利"
    };

    /** 招聘/猎头类关键词（中英繁简） */
    public static final String[] RECRUIT_TERMS = {
            "招聘", "猎头", "獵頭", "Recruit", "Headhunt"
    };

    /** 是否法律类岗位 */
    public static boolean isLegal(String text) {
        return containsAny(text, LEGAL_TERMS);
    }

    /** 是否招聘/猎头类岗位 */
    public static boolean isRecruiting(String text) {
        return containsAny(text, RECRUIT_TERMS);
    }

    /** 是否应排除（法律类 或 招聘/猎头类） */
    public static boolean isExcluded(String text) {
        return isLegal(text) || isRecruiting(text);
    }

    /** 返回命中的关键词（用于日志），未命中返回 null */
    public static String matchedTerm(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String t : LEGAL_TERMS) {
            if (text.contains(t)) {
                return t;
            }
        }
        for (String t : RECRUIT_TERMS) {
            if (text.contains(t)) {
                return t;
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String[] terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String t : terms) {
            if (text.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
