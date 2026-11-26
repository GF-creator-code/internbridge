package com.getjobs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.worker.dto.JobProgressMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 投递进度广播器：所有平台共用一条 SSE 通道。
 * <p>
 * 背景：原先每个平台的进度各自为政 ——
 * <ul>
 *   <li>Boss 和 51job 各自维护一份 SseEmitter 列表和推送方法；</li>
 *   <li>智联 / 实习僧 / 猎聘 / OfferToday / JobsDB 的进度只写进日志，
 *       前端连订阅的端点都没有。</li>
 * </ul>
 * 结果是新手点下「开始投递」后界面完全没有反馈：不知道跑了几个、
 * 成功几个、失败在哪一步，只能去翻后端控制台。
 * <p>
 * 这里把七个平台的进度统一收敛到一条流上，前端订阅一次即可。
 */
@Slf4j
@Service
public class TaskProgressBroadcaster {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 订阅者与各自关注的平台。platform 为 null 表示订阅全部平台。
     */
    private final Map<SseEmitter, String> platforms = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 订阅进度流。
     *
     * @param platform 平台标识；非空时只推送该平台的进度，为空则推送全部平台
     */
    public SseEmitter subscribe(String platform) {
        SseEmitter emitter = new SseEmitter(0L); // 永不超时
        emitters.add(emitter);
        platforms.put(emitter, platform);

        emitter.onCompletion(() -> remove(emitter));
        emitter.onTimeout(() -> remove(emitter));
        emitter.onError(e -> remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", platform == null
                            ? "已连接到投递进度推送"
                            : "已连接到 " + platform + " 投递进度推送")));
        } catch (IOException e) {
            log.debug("发送进度 SSE 连接消息失败: {}", e.getMessage());
        }
        return emitter;
    }

    private void remove(SseEmitter emitter) {
        emitters.remove(emitter);
        platforms.remove(emitter);
    }

    /**
     * 判断某条消息是否应该推送给关注 wanted 平台的订阅者。
     * <p>
     * wanted 为 null 表示订阅者关心全部平台；消息的 platform 为 null 表示全局消息。
     * 抽成静态方法是为了能单独测这套过滤规则。
     */
    public static boolean matches(String wanted, JobProgressMessage message) {
        if (wanted == null || message == null || message.getPlatform() == null) {
            return true;
        }
        return wanted.equalsIgnoreCase(message.getPlatform());
    }

    /**
     * 广播一条进度消息。
     *
     * @param message 进度消息；platform 为空时视为全局消息
     */
    public void broadcast(JobProgressMessage message) {
        if (message == null) {
            return;
        }
        String payload;
        try {
            payload = OBJECT_MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("序列化进度消息失败: {}", e.getMessage());
            return;
        }

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            // 按平台过滤：只关心某个平台的订阅者收不到别的平台的消息
            if (!matches(platforms.get(emitter), message)) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event().name("progress").data(payload));
            } catch (Exception e) {
                if (isClientGone(e)) {
                    log.debug("进度 SSE 客户端已断开，移除连接: {}", e.getMessage());
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 客户端已经走了，complete 失败无需处理
                    }
                } else {
                    log.error("发送进度消息失败: {}", e.getMessage());
                }
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        dead.forEach(platforms::remove);
    }

    /**
     * 心跳：某些代理会掐掉长时间无数据的连接。
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        dead.forEach(platforms::remove);
    }

    /** 当前有多少个订阅者（用于自检与测试） */
    public int subscriberCount() {
        return emitters.size();
    }

    /** 判断异常是否由客户端断开导致 */
    private boolean isClientGone(Exception e) {
        return e instanceof AsyncRequestNotUsableException
                || e instanceof ClientAbortException
                || e.getCause() instanceof ClientAbortException
                || (e instanceof IOException && String.valueOf(e.getMessage()).contains("中止了一个已建立的连接"));
    }
}
