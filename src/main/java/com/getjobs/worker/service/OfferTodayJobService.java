package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.manager.PlatformTaskBrowser;
import com.getjobs.worker.offertoday.OfferToday;
import com.getjobs.worker.offertoday.OfferTodayConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OfferToday 任务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferTodayJobService implements JobPlatformService {
    private static final String PLATFORM = "offertoday";

    private final PlaywrightManager playwrightManager;
    private final PlatformTaskBrowser platformTaskBrowser;
    private final ObjectProvider<OfferToday> offerTodayProvider;
    private final ConfigService configService;
    private final CookieService cookieService;

    private volatile boolean isRunning = false;
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查登录态（Cookie 表）..."));

            // 投递任务全走任务浏览器（PlatformTaskBrowser），共享浏览器不再为此拉起。
            // Cookie 表有记录就放行，真正登录验证由任务浏览器的预热 / worker 内部处理。
            boolean hasLoginCookie = false;
            try {
                var loginCookie = cookieService.getCookieByPlatform(PLATFORM);
                hasLoginCookie = loginCookie != null && loginCookie.getCookieValue() != null
                        && !loginCookie.getCookieValue().isBlank();
            } catch (Exception ignored) {
            }
            if (!hasLoginCookie) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录OfferToday（Cookie 表无登录态）"));
                return;
            }
            isRunning = true;
            shouldStop = false;

            playwrightManager.pauseOfferTodayMonitoring();

            OfferTodayConfig config = configService.getOfferTodayConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            OfferToday.ProgressCallback callback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            OfferToday offerToday = offerTodayProvider.getObject();
            offerToday.setConfig(config);
            offerToday.setProgressCallback(callback);
            offerToday.setShouldStopCallback(this::shouldStop);
            offerToday.prepare();

            int deliveredCount = platformTaskBrowser.run(PLATFORM, taskPage -> {
                offerToday.setPage(taskPage);
                return offerToday.execute();
            });

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                    String.format("投递任务完成，共投递%d个职位", deliveredCount)));
        } catch (Exception e) {
            log.error("OfferToday 投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeOfferTodayMonitoring();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止 OfferToday 投递任务的请求");
            shouldStop = true;
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("platform", PLATFORM);
        status.put("isRunning", isRunning);
        status.put("isLoggedIn", playwrightManager.isLoggedIn(PLATFORM));
        return status;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public boolean shouldStop() {
        return shouldStop;
    }
}
