package com.getjobs.application.worker;

import com.getjobs.application.service.GlobalProfileService;
import com.getjobs.worker.utils.KeywordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 全局档案 → 各平台配置的适配器。
 * <p>
 * 在各平台的 loadXxxConfig() 之后调用，把「与平台无关」的全局设置
 * （工作形式、技能介绍、简历路径）翻译成平台能用的字段，
 * 这样 worker 层完全不需要改动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileApplier {

    private final GlobalProfileService globalProfileService;

    /**
     * 给关键词补上工作形式限定词。
     * 例：关键词「后端」+ 工作形式「实习」→ ["后端", "后端 实习"]
     */
    public List<String> applyKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        var limit = globalProfileService.getWorkTypeKeywords();
        if (limit.isEmpty()) {
            return new ArrayList<>(keywords);
        }
        String suffix = limit.get(0);
        var result = new LinkedHashSet<String>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String k = keyword.trim();
            result.add(k);
            if (!k.contains(suffix)) {
                result.add(k + " " + suffix);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 把工作形式的排除词并进平台自己的排除关键词。
     * 例：投实习时，「全职」「正式员工」这类岗位标题会被跳过。
     */
    public List<String> applyExcludeKeywords(List<String> excludeKeywords) {
        var base = excludeKeywords == null ? new ArrayList<String>() : new ArrayList<>(excludeKeywords);
        var workTypeWords = globalProfileService.getWorkTypeExcludeWords();
        if (!workTypeWords.isEmpty()) {
            base.addAll(workTypeWords);
        }
        return KeywordFilter.parse(KeywordFilter.serialize(base));
    }

    /**
     * 全局简历路径。平台没单独配简历时用它兜底。
     */
    public String resolveResumePath(String platformResumePath) {
        if (platformResumePath != null && !platformResumePath.isBlank()) {
            return platformResumePath;
        }
        return globalProfileService.getResumePath();
    }

    /**
     * 全局技能介绍。平台没有单独配时用它兜底。
     */
    public String resolveIntroduce(String platformIntroduce) {
        if (platformIntroduce != null && !platformIntroduce.isBlank()) {
            return platformIntroduce;
        }
        return globalProfileService.getIntroduce();
    }
}
