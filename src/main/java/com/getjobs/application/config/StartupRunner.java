package com.getjobs.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.getjobs.worker.manager.PlaywrightManager;

import java.io.IOException;

/**
 * 应用启动后自动打开管理页面
 * 优先级：前端服务 > 静态资源 > 不打开
 */
@Slf4j
@Component
public class StartupRunner implements ApplicationRunner {

    @Value("${server.port:9527}")
    private int backendPort;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Value("${internbridge.open-dashboard:true}")
    private boolean openDashboard;

    /**
     * 是否在启动时初始化共享自动化浏览器（会弹出一个 Chrome 窗口）。
     * 默认关闭：投递任务全走各平台独立的任务浏览器（PlatformTaskBrowser），
     * 共享浏览器只在需要扫码/账密登录（平台页点「登录」会自行拉起）或预检时才需要。
     */
    @Value("${internbridge.open-platforms:false}")
    private boolean openPlatforms;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (openDashboard) openBrowser("http://127.0.0.1:" + backendPort + "/");
        if (!openPlatforms) return;

        // 在尝试打开管理页面之后，初始化 Playwright（满足“先打开管理页，再实例化”）
        try {
            playwrightManager.init();
        } catch (Exception e) {
            log.error("Playwright 初始化失败: {}", e.getMessage());
            log.warn("工作台仍可使用。请安装 Google Chrome 后重启本机平台工具。");
        }
    }

    /**
     * 打开默认浏览器
     */
    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;

            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                processBuilder = new ProcessBuilder("open", url);
            } else if (os.contains("nix") || os.contains("nux")) {
                processBuilder = new ProcessBuilder("xdg-open", url);
            } else {
                log.warn("未知操作系统类型，无法自动打开浏览器，请手动访问: {}", url);
                return;
            }

            processBuilder.start();
            log.info("已打开浏览器: {}", url);
        } catch (IOException e) {
            log.error("打开浏览器失败: {}", e.getMessage());
            log.info("请手动访问管理页面: {}", url);
        }
    }
}
