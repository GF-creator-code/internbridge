package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.manager.PlatformTaskBrowser;
import com.getjobs.worker.jobsdb.Jobsdb;
import com.getjobs.worker.jobsdb.JobsdbConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JobsDB 任务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobsdbJobService implements JobPlatformService {
    private static final String PLATFORM = "jobsdb";

    private final PlaywrightManager playwrightManager;
    private final PlatformTaskBrowser platformTaskBrowser;
    private final ObjectProvider<Jobsdb> jobsdbProvider;
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
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            Page page = playwrightManager.getJobsdbPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "JobsDB 页面未初始化"));
                return;
            }

            // 共享浏览器的页面可能被 Cloudflare 拦住，但任务浏览器有自己的持久化 profile；
            // 只要 cookie 表里有 JobsDB 登录态就继续尝试，让任务浏览器自己去验证。
            boolean hasCookie = false;
            try {
                var cookie = cookieService.getCookieByPlatform(PLATFORM);
                hasCookie = cookie != null && cookie.getCookieValue() != null && !cookie.getCookieValue().isBlank();
            } catch (Exception ignored) {
            }
            if (!playwrightManager.isLoggedIn(PLATFORM) && !hasCookie) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录 JobsDB"));
                return;
            }

            isRunning = true;
            shouldStop = false;

            playwrightManager.pauseJobsdbMonitoring();

            JobsdbConfig config = configService.getJobsdbConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            Jobsdb.ProgressCallback callback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Jobsdb jobsdb = jobsdbProvider.getObject();
            jobsdb.setPage(page);
            jobsdb.setConfig(config);
            jobsdb.setProgressCallback(callback);
            jobsdb.setShouldStopCallback(this::shouldStop);
            jobsdb.prepare();

            int deliveredCount;
            try {
                deliveredCount = platformTaskBrowser.run(PLATFORM, taskPage -> {
                    jobsdb.setPage(taskPage);
                    return jobsdb.execute();
                });
            } catch (Exception e) {
                // 任务浏览器被 Cloudflare 拦住时，回退到共享浏览器（已验证、登录态稳定）
                log.warn("JobsDB 任务浏览器不可用，回退共享浏览器: {}", e.getMessage());
                Page shared = playwrightManager.getJobsdbPage();
                if (shared == null) {
                    throw e;
                }
                jobsdb.setPage(shared);
                deliveredCount = playwrightManager.callOnPlaywright(jobsdb::execute);
            }

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                    String.format("投递任务完成，共投递%d个职位", deliveredCount)));
        } catch (Exception e) {
            log.error("JobsDB 投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeJobsdbMonitoring();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止 JobsDB 投递任务的请求");
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
