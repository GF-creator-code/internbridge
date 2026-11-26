package com.getjobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * InternBridge 应用程序启动类
 * 本地运行的跨平台实习投递管理端主入口
 *
 * 注：类名沿用历史命名，改动会牵连构建配置与启动脚本，故保留。
 */
@SpringBootApplication(scanBasePackages = "com.getjobs")
@EnableScheduling
@EnableAsync
public class GetJobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GetJobsApplication.class, args);
    }
}