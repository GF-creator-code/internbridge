package com.getjobs.application.controller;

import com.getjobs.application.service.TaskProgressBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 投递进度 SSE（统一入口）。
 * <p>
 * 七个平台的投递进度都汇总到这一条流上，前端订阅一次就能看到全部平台的进度。
 * 平台专属的旧端点（/api/boss/stream、/api/51job/stream）仍然保留，向后兼容。
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs/progress")
@RequiredArgsConstructor
public class TaskProgressController {

    private final TaskProgressBroadcaster taskProgressBroadcaster;

    /**
     * 订阅投递进度。
     *
     * @param platform 可选。只传该平台时只收该平台的进度；不传则收全部平台。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String platform) {
        String normalized = platform == null || platform.isBlank() ? null : platform.trim().toLowerCase();
        log.debug("新增投递进度订阅: {}", normalized == null ? "全部平台" : normalized);
        return taskProgressBroadcaster.subscribe(normalized);
    }
}
