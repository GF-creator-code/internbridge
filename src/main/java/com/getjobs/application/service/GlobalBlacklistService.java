package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.mapper.BlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局黑名单服务：boss_blacklist 表为全平台共享的黑名单（表名沿用历史命名）。
 * <p>
 * Boss worker 仍走 BossService 的同名方法（同一张表）；
 * 新增平台（实习僧 / OfferToday / JobsDB）与本服务统一读取。
 * 管理入口：前端「全局配置」页 + /api/global/blacklist；Boss 原有端点继续可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalBlacklistService {

    private final BlacklistMapper blacklistMapper;

    public List<BlacklistEntity> getAll() {
        return blacklistMapper.selectList(null);
    }

    public Set<String> getByType(String type) {
        return blacklistMapper.selectList(new LambdaQueryWrapper<BlacklistEntity>()
                        .eq(BlacklistEntity::getType, type)).stream()
                .map(BlacklistEntity::getValue)
                .collect(Collectors.toSet());
    }

    public Set<String> getBlackCompanies() {
        return getByType("company");
    }

    public Set<String> getBlackJobs() {
        return getByType("job");
    }

    /**
     * 新增黑名单条目；type 限 company/job/recruiter，value 非空。
     *
     * @return 新建的实体；已存在或参数非法时返回 null
     */
    public BlacklistEntity add(String type, String value) {
        if (type == null || value == null || value.isBlank()) return null;
        if (!Set.of("company", "job", "recruiter").contains(type)) return null;
        String trimmed = value.trim();
        LambdaQueryWrapper<BlacklistEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlacklistEntity::getType, type).eq(BlacklistEntity::getValue, trimmed);
        if (blacklistMapper.selectCount(wrapper) > 0) {
            return null; // 已存在
        }
        BlacklistEntity entity = new BlacklistEntity();
        entity.setType(type);
        entity.setValue(trimmed);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        blacklistMapper.insert(entity);
        return entity;
    }

    public boolean removeById(Long id) {
        return blacklistMapper.deleteById(id) > 0;
    }
}
