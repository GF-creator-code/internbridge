package com.getjobs.application.controller;

import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用平台调试接口（临时）
 * 在各平台的自动化页面里执行 JS / 导航，用于排查选择器与页面结构。
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*")
public class DebugEvalController {

    @Autowired
    private PlaywrightManager playwrightManager;

    private Page pageOf(String platform) {
        Page p = switch (platform == null ? "" : platform) {
            case "boss" -> playwrightManager.getBossPage();
            case "liepin" -> playwrightManager.getLiepinPage();
            case "zhilian" -> playwrightManager.getZhilianPage();
            case "shixiseng" -> playwrightManager.getShixisengPage();
            default -> null;
        };
        if (p == null) {
            throw new IllegalStateException(platform + " 页面未初始化");
        }
        return p;
    }

    @PostMapping("/eval")
    public Map<String, Object> eval(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = pageOf(body.get("platform"));
            Object result = p.evaluate(body.get("script"));
            resp.put("success", true);
            resp.put("result", result == null ? null : String.valueOf(result));
            resp.put("url", p.url());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    @PostMapping("/goto")
    public Map<String, Object> gotoUrl(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Page p = pageOf(body.get("platform"));
            p.navigate(body.get("url"), new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            resp.put("success", true);
            resp.put("url", p.url());
            resp.put("title", p.title());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }
}
