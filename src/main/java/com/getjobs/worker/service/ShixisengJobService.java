package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.manager.PlatformTaskBrowser;
import com.getjobs.worker.shixiseng.ShiXiSeng;
import com.getjobs.worker.shixiseng.ShiXiSengConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 实习僧任务服务
 * 管理实习僧平台的投递任务执行和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShixisengJobService implements JobPlatformService {
    private static final String PLATFORM = "shixiseng";

    private final PlaywrightManager playwrightManager;
    private final PlatformTaskBrowser platformTaskBrowser;
    private final ObjectProvider<ShiXiSeng> shixisengProvider;
    private final ConfigService configService;

    // 任务运行状态
    private volatile boolean isRunning = false;
    // 停止标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            Page page = playwrightManager.getShixisengPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "实习僧页面未初始化"));
                return;
            }

            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录实习僧"));
                return;
            }

            isRunning = true;
            shouldStop = false;

            playwrightManager.pauseShixisengMonitoring();

            ShiXiSengConfig config = configService.getShixisengConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            ShiXiSeng.ProgressCallback callback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            ShiXiSeng shixiseng = shixisengProvider.getObject();
            shixiseng.setPage(page);
            shixiseng.setConfig(config);
            shixiseng.setProgressCallback(callback);
            shixiseng.setShouldStopCallback(this::shouldStop);
            shixiseng.prepare();

            int deliveredCount = platformTaskBrowser.run(PLATFORM, taskPage -> {
                shixiseng.setPage(taskPage);
                return shixiseng.execute();
            });

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                    String.format("投递任务完成，共投递%d个职位", deliveredCount)));
        } catch (Exception e) {
            log.error("实习僧投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeShixisengMonitoring();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止实习僧投递任务的请求");
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
