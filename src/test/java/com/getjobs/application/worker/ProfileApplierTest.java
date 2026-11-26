package com.getjobs.application.worker;

import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import com.getjobs.application.service.GlobalProfileService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证「工作形式」是否真正作用到平台配置上。
 * 这是需求的核心：选了实习，搜索词要带上限定，相反形式的岗位要被排除。
 */
class ProfileApplierTest {

    private GlobalProfileService profile(String workType) {
        var mapper = mock(ConfigMapper.class);
        // getProfile 会按前缀查；返回一个 work_type 行即可
        var row = new ConfigEntity();
        row.setConfigKey(GlobalProfileService.KEY_WORK_TYPE);
        row.setConfigValue(workType);
        when(mapper.selectList(any())).thenReturn(List.of(row));
        return new GlobalProfileService(mapper);
    }

    @Test void internshipExpandsKeywordsAndExcludesFullTime() {
        var applier = new ProfileApplier(profile("实习"));

        var expanded = applier.applyKeywords(List.of("后端", "数据"));
        assertTrue(expanded.contains("后端"), "原关键词必须保留");
        assertTrue(expanded.contains("后端 实习"), "应补上工作形式限定词");
        assertTrue(expanded.contains("数据 实习"));

        var excluded = applier.applyExcludeKeywords(List.of("外包"));
        assertTrue(excluded.contains("外包"), "平台自己的排除词要保留");
        assertTrue(excluded.contains("全职"), "工作形式的排除词要并入");
        assertTrue(excluded.contains("正式员工"));
    }

    @Test void fulltimeExcludesInternships() {
        var applier = new ProfileApplier(profile("全职"));
        var excluded = applier.applyExcludeKeywords(List.of());
        assertTrue(excluded.contains("实习"));
        assertTrue(excluded.contains("兼职"));
        assertFalse(excluded.contains("全职"), "不能排除自己");
    }

    @Test void anyWorkTypeKeepsKeywordsUnchanged() {
        var applier = new ProfileApplier(profile("不限"));
        assertEquals(List.of("后端", "数据"), applier.applyKeywords(List.of("后端", "数据")));
        assertTrue(applier.applyExcludeKeywords(List.of()).isEmpty());
    }

    @Test void doesNotDuplicateExistingSuffix() {
        var applier = new ProfileApplier(profile("实习"));
        var expanded = applier.applyKeywords(List.of("后端 实习"));
        assertEquals(1, expanded.size(), "已经带限定词的关键词不应重复追加");
        assertEquals("后端 实习", expanded.get(0));
    }

    @Test void resolvesGlobalResumePathAsFallback() {
        var mapper = mock(ConfigMapper.class);
        var row = new ConfigEntity();
        row.setConfigKey(GlobalProfileService.KEY_RESUME_PATH);
        row.setConfigValue("C:/global/cv.pdf");
        when(mapper.selectList(any())).thenReturn(List.of(row));
        var applier = new ProfileApplier(new GlobalProfileService(mapper));

        assertEquals("C:/platform/cv.pdf", applier.resolveResumePath("C:/platform/cv.pdf"));
        assertEquals("C:/global/cv.pdf", applier.resolveResumePath(""));
        assertEquals("C:/global/cv.pdf", applier.resolveResumePath(null));
    }

    @Test void unknownWorkTypeFallsBackToAny() {
        var applier = new ProfileApplier(profile("随便什么"));
        assertEquals(List.of("Java"), applier.applyKeywords(List.of("Java")));
    }
}
