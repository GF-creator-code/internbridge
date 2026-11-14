package com.getjobs.application.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Compare monthly amounts within one platform. Daily and hourly offers retain their original units in the list. */
final class MonthlySalarySummary {
    static final List<String> LABELS = List.of("低于 10K", "10–15K", "15–20K", "20–25K", "25K 及以上");
    private static final String AMOUNT = "(\\d{1,7}(?:,\\d{3})*(?:\\.\\d+)?)\\s*(k?)";
    private static final Pattern SALARY = Pattern.compile(
            "^(?:hk\\$|hkd|rmb|cny|[¥￥$])?\\s*" + AMOUNT +
            "(?:\\s*[-–—~至]\\s*(?:hk\\$|hkd|rmb|cny|[¥￥$])?\\s*" + AMOUNT + ")?" +
            "\\s*(元)?\\s*(/\\s*月|每月|/\\s*month|per\\s+month|a\\s+month|monthly|·\\d+薪)?$");
    private final long[] buckets = new long[LABELS.size()];
    private long count;
    private double sum;

    void add(String raw) {
        if (raw == null) return;
        var match = SALARY.matcher(raw.strip().toLowerCase(Locale.ROOT));
        if (!match.matches()) return;
        boolean firstK = !match.group(2).isEmpty();
        boolean secondK = "k".equals(match.group(4));
        if (match.group(6) == null && !firstK && !secondK) return;
        double low = Double.parseDouble(match.group(1).replace(",", ""));
        double high = match.group(3) == null ? low : Double.parseDouble(match.group(3).replace(",", ""));
        // A trailing K also qualifies the first amount in a range such as 12–16K.
        double minK = (firstK || (secondK && low <= high)) ? low : low / 1000;
        double maxK = (secondK || (firstK && (match.group(3) == null || high < 1000))) ? high : high / 1000;
        if (minK <= 0 || maxK < minK) return;
        double middle = (minK + maxK) / 2;
        int bucket = middle < 10 ? 0 : middle < 15 ? 1 : middle < 20 ? 2 : middle < 25 ? 3 : 4;
        buckets[bucket]++;
        sum += middle;
        count++;
    }

    Double average() { return count == 0 ? null : Math.round(sum / count * 10.0) / 10.0; }
    long bucket(int index) { return buckets[index]; }
}
