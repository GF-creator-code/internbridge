package com.getjobs.application.service;

import com.getjobs.application.entity.*;
import com.getjobs.application.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局城市分发：把「全局配置」里选的城市同步到各平台配置。
 * <p>
 * 各平台对城市的存储格式不一样：
 * <ul>
 *   <li>实习僧存城市名（"北京"）；</li>
 *   <li>Boss / 智联存城市编码（"101010100"）；</li>
 *   <li>猎聘 / 51job 存城市名或编码；</li>
 *   <li>JobsDB / OfferToday 固定是香港，不参与同步。</li>
 * </ul>
 * 这里负责做格式转换，让用户在全局配置里选一次就够了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalCityService {

    private final GlobalProfileService globalProfileService;
    private final BossService bossService;
    private final ZhilianService zhilianService;
    private final LiepinService liepinService;

    private final ShixisengConfigMapper shixisengConfigMapper;
    private final JobsdbConfigMapper jobsdbConfigMapper;
    private final OfferTodayConfigMapper offerTodayConfigMapper;
    private final ZhilianConfigMapper zhilianConfigMapper;
    private final Job51ConfigMapper job51ConfigMapper;
    private final BossConfigMapper bossConfigMapper;
    private final LiepinConfigMapper liepinConfigMapper;

    /**
     * 把全局城市应用到各平台。
     *
     * @return 每个平台的应用结果
     */
    @Transactional
    public List<Map<String, Object>> applyGlobalCity() {
        String cityName = globalProfileService.getProfile().getOrDefault("city", "");
        var results = new ArrayList<Map<String, Object>>();

        if (cityName.isBlank()) {
            var r = new LinkedHashMap<String, Object>();
            r.put("platform", "all");
            r.put("success", false);
            r.put("detail", "尚未设置全局城市");
            results.add(r);
            return results;
        }

        results.add(apply("实习僧", () -> {
            var entity = firstOrNew(shixisengConfigMapper.selectList(null), ShixisengConfigEntity::new);
            entity.setCity(cityName);
            if (entity.getId() == null) {
                shixisengConfigMapper.insert(entity);
            } else {
                shixisengConfigMapper.updateById(entity);
            }
        }, cityName));

        results.add(apply("Boss直聘", () -> {
            String code = bossService.getCodeByTypeAndName("city", cityName);
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Boss 城市字典里找不到「" + cityName + "」");
            }
            var entity = firstOrNew(bossConfigMapper.selectList(null), BossConfigEntity::new);
            entity.setCityCode(code);
            if (entity.getId() == null) {
                bossConfigMapper.insert(entity);
            } else {
                bossConfigMapper.updateById(entity);
            }
        }, cityName));

        results.add(apply("智联招聘", () -> {
            String code = zhilianService.getCodeByTypeAndName("city", cityName);
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("智联城市字典里找不到「" + cityName + "」");
            }
            var entity = firstOrNew(zhilianConfigMapper.selectList(null), ZhilianConfigEntity::new);
            entity.setCityCode(code);
            if (entity.getId() == null) {
                zhilianConfigMapper.insert(entity);
            } else {
                zhilianConfigMapper.updateById(entity);
            }
        }, cityName));

        results.add(apply("猎聘", () -> {
            String code = liepinService.getCodeByTypeAndName("city", cityName);
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("猎聘城市字典里找不到「" + cityName + "」");
            }
            var entity = firstOrNew(liepinConfigMapper.selectList(null), LiepinConfigEntity::new);
            entity.setCity(code);
            if (entity.getId() == null) {
                liepinConfigMapper.insert(entity);
            } else {
                liepinConfigMapper.updateById(entity);
            }
        }, cityName));

        results.add(apply("51job", () -> {
            // 51job 的城市字段存编码，但它的字典在 job51_option 里，
            // 这里用城市名直写；平台加载时会再做一次 name→code 归一化。
            var entity = firstOrNew(job51ConfigMapper.selectList(null), Job51ConfigEntity::new);
            entity.setJobArea(cityName);
            if (entity.getId() == null) {
                job51ConfigMapper.insert(entity);
            } else {
                job51ConfigMapper.updateById(entity);
            }
        }, cityName));

        // 香港平台固定，不参与全局城市同步
        for (String platform : List.of("JobsDB", "OfferToday")) {
            var skipped = new LinkedHashMap<String, Object>();
            skipped.put("platform", platform);
            skipped.put("success", true);
            skipped.put("detail", "香港平台，城市固定，已跳过");
            results.add(skipped);
        }

        return results;
    }

    /** 当前各平台已配置的城市，供设置中心「全局规则」展示 */
    public Map<String, String> currentPlatformCities() {
        var result = new LinkedHashMap<String, String>();
        try {
            var sxs = shixisengConfigMapper.selectList(null);
            result.put("实习僧", sxs.isEmpty() ? "" : nz(sxs.get(0).getCity()));
        } catch (Exception e) {
            result.put("实习僧", "");
        }
        try {
            var boss = bossConfigMapper.selectList(null);
            String code = boss.isEmpty() ? "" : nz(boss.get(0).getCityCode());
            result.put("Boss直聘", code.isBlank() ? "" : nz(bossService.getOptionByTypeAndCode("city", code) != null
                    ? bossService.getOptionByTypeAndCode("city", code).getName() : code));
        } catch (Exception e) {
            result.put("Boss直聘", "");
        }
        try {
            var zl = zhilianConfigMapper.selectList(null);
            String code = zl.isEmpty() ? "" : nz(zl.get(0).getCityCode());
            result.put("智联招聘", code.isBlank() ? "" : nz(zhilianService.getNameByTypeAndCode("city", code)));
        } catch (Exception e) {
            result.put("智联招聘", "");
        }
        try {
            var lp = liepinConfigMapper.selectList(null);
            String code = lp.isEmpty() ? "" : nz(lp.get(0).getCity());
            result.put("猎聘", code.isBlank() ? "" : nz(liepinService.getNameByTypeAndCode("city", code)));
        } catch (Exception e) {
            result.put("猎聘", "");
        }
        try {
            var j51 = job51ConfigMapper.selectList(null);
            result.put("51job", j51.isEmpty() ? "" : nz(j51.get(0).getJobArea()));
        } catch (Exception e) {
            result.put("51job", "");
        }
        result.put("JobsDB", "香港（固定）");
        result.put("OfferToday", "香港（固定）");
        return result;
    }

    private Map<String, Object> apply(String platform, Runnable action, String city) {
        var result = new LinkedHashMap<String, Object>();
        result.put("platform", platform);
        try {
            action.run();
            result.put("success", true);
            result.put("detail", city);
        } catch (Exception e) {
            log.warn("同步全局城市到 [{}] 失败: {}", platform, e.getMessage());
            result.put("success", false);
            result.put("detail", e.getMessage());
        }
        return result;
    }

    private <T> T firstOrNew(List<T> list, java.util.function.Supplier<T> factory) {
        return list == null || list.isEmpty() ? factory.get() : list.get(0);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
