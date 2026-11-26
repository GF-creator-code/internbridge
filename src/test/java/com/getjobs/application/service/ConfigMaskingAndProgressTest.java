package com.getjobs.application.service;

import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import com.getjobs.worker.dto.JobProgressMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖本次改进的两个关键点：
 * 1. 凭据脱敏后，掩码不能被当成真实值写回数据库；
 * 2. 统一进度广播按平台正确过滤。
 */
class ConfigMaskingAndProgressTest {

    private ConfigService serviceWith(ConfigMapper mapper) {
        return new ConfigService(mapper, mock(LiepinService.class), mock(BossService.class),
                mock(ZhilianService.class), mock(ShixisengService.class), mock(Job51Service.class),
                mock(OfferTodayService.class), mock(JobsdbService.class),
                mock(com.getjobs.application.worker.ProfileApplier.class));
    }

    private ConfigEntity entity(String key, String value) {
        var entity = new ConfigEntity();
        entity.setId(1L);
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }

    // ---------- 脱敏 ----------

    @Test void masksSecretKeysButKeepsPlainOnes() {
        var mapper = mock(ConfigMapper.class);
        var service = serviceWith(mapper);

        var secret = entity("API_KEY", "sk-super-secret-value");
        var plain = entity("MODEL", "gpt-4o-mini");
        when(mapper.selectList(null)).thenReturn(List.of(secret, plain));

        var result = service.getAllConfigsAsMap();
        assertEquals("••••alue", result.get("API_KEY"));
        assertFalse(result.get("API_KEY").contains("super-secret"), "密钥主体不应出现在返回值里");
        assertEquals("gpt-4o-mini", result.get("MODEL"), "非敏感配置不应被脱敏");
    }

    @Test void doesNotWriteMaskedValueBackToDatabase() {
        var mapper = mock(ConfigMapper.class);
        var service = serviceWith(mapper);

        var existing = entity("API_KEY", "sk-real-value");
        when(mapper.selectOne(any())).thenReturn(existing);

        // 前端回显掩码后提交：必须跳过写入，保留原值
        int count = service.batchUpdateConfigs(Map.of("API_KEY", "••••alue"));

        assertEquals(0, count, "掩码不应计入更新数量");
        verify(mapper, never()).updateById(any(ConfigEntity.class));
        assertEquals("sk-real-value", existing.getConfigValue(), "原密钥必须保持不变");
    }

    @Test void writesRealValueNormally() {
        var mapper = mock(ConfigMapper.class);
        var service = serviceWith(mapper);

        var existing = entity("API_KEY", "sk-old");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.updateById(any(ConfigEntity.class))).thenReturn(1);

        int count = service.batchUpdateConfigs(Map.of("API_KEY", "sk-brand-new"));

        assertEquals(1, count);
        assertEquals("sk-brand-new", existing.getConfigValue());
    }

    @Test void maskedValueDoesNotBlockOtherKeysInSameRequest() {
        var mapper = mock(ConfigMapper.class);
        var service = serviceWith(mapper);

        var apiKey = entity("API_KEY", "sk-real-value");
        var model = entity("MODEL", "gpt-old");
        // 用 LinkedHashMap 固定迭代顺序。掩码键会在查库前就被跳过，
        // 所以 selectOne 只会被调用一次（MODEL 那次），返回 model。
        var changes = new java.util.LinkedHashMap<String, String>();
        changes.put("API_KEY", "••••alue");
        changes.put("MODEL", "gpt-new");
        when(mapper.selectOne(any())).thenReturn(model);
        when(mapper.updateById(any(ConfigEntity.class))).thenReturn(1);

        int count = service.batchUpdateConfigs(changes);

        assertEquals(1, count, "只有被掩码的键被跳过，其余键照常写入");
        verify(mapper, never()).updateById(argThat((ConfigEntity e) -> "sk-real-value".equals(e.getConfigValue())));
        assertEquals("gpt-new", model.getConfigValue());
    }

    @Test void shortSecretsAreFullyMasked() {
        assertEquals("••••", ConfigService.maskSecret("abcd"));
        assertEquals("", ConfigService.maskSecret(""));
        assertEquals("", ConfigService.maskSecret(null));
    }

    @Test void identifiesSecretKeys() {
        assertTrue(ConfigService.isSecretKey("API_KEY"));
        assertTrue(ConfigService.isSecretKey("api_key"));
        assertTrue(ConfigService.isSecretKey("HOOK_URL"));
        assertFalse(ConfigService.isSecretKey("MODEL"));
        assertFalse(ConfigService.isSecretKey("BASE_URL"));
    }

    // ---------- 进度广播 ----------

    @Test void subscriberWithoutPlatformFilterReceivesEverything() {
        assertTrue(TaskProgressBroadcaster.matches(null, JobProgressMessage.info("zhilian", "x")));
    }

    @Test void subscriberReceivesOwnPlatform() {
        assertTrue(TaskProgressBroadcaster.matches("zhilian", JobProgressMessage.info("zhilian", "x")));
    }

    @Test void subscriberDoesNotReceiveOtherPlatform() {
        assertFalse(TaskProgressBroadcaster.matches("boss", JobProgressMessage.info("zhilian", "x")));
    }

    @Test void globalMessageReachesEveryone() {
        assertTrue(TaskProgressBroadcaster.matches("boss",
                new JobProgressMessage(null, "info", "全局消息", null, null, 1L)));
    }

    @Test void platformFilterIsCaseInsensitive() {
        assertTrue(TaskProgressBroadcaster.matches("ZHILIAN", JobProgressMessage.info("zhilian", "x")));
    }

    @Test void broadcasterTracksSubscriberCount() {
        var broadcaster = new TaskProgressBroadcaster();
        assertEquals(0, broadcaster.subscriberCount());
        broadcaster.subscribe("zhilian");
        assertEquals(1, broadcaster.subscriberCount());
    }
}
