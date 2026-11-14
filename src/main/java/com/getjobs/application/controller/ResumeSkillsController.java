package com.getjobs.application.controller;

import com.getjobs.application.service.ResumeSkillsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/resume")
@RequiredArgsConstructor
public class ResumeSkillsController {
    private final ResumeSkillsService service;
    public record CompareRequest(String profile, String job) {}

    @PostMapping("/skills")
    public List<ResumeSkillsService.Skill> skills(@RequestParam("file") MultipartFile file) {
        return service.extract(file);
    }

    @PostMapping("/match")
    public ResumeSkillsService.Comparison match(@RequestBody CompareRequest input) {
        return service.match(input.profile(), input.job());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> modelError(IllegalStateException error) {
        return ResponseEntity.status(502).body(Map.of("message", error.getMessage()));
    }
}
