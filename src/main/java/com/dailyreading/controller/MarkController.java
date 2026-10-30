package com.dailyreading.controller;

import com.dailyreading.common.ApiResponse;
import com.dailyreading.entity.Mark;
import com.dailyreading.service.MarkService;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marks")
@RequiredArgsConstructor
public class MarkController {

    private final MarkService markService;

    @GetMapping
    public ApiResponse list(HttpSession session) {
        Long userId = userId(session);
        List<Mark> marks = markService.list(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("marks", marks);
        data.put("learning", markService.count(userId, "learning"));
        data.put("mastered", markService.count(userId, "mastered"));
        return ApiResponse.ok(data);
    }

    @PostMapping
    public ApiResponse add(HttpSession session, @RequestBody Map<String, Object> body) {
        try {
            Mark m = markService.add(userId(session), body);
            return ApiResponse.ok(Map.of("mark", m));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PatchMapping("/{id}/status")
    public ApiResponse setStatus(
            HttpSession session, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String status = body.get("status") == null ? "" : String.valueOf(body.get("status"));
            return ApiResponse.ok(Map.of("mark", markService.setStatus(userId(session), id, status)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse remove(HttpSession session, @PathVariable Long id) {
        markService.remove(userId(session), id);
        return ApiResponse.ok();
    }

    /** 只清某一摞，或者 status=all 全清。 */
    @DeleteMapping
    public ApiResponse clear(HttpSession session, @RequestParam(defaultValue = "learning") String status) {
        int n = markService.clear(userId(session), status);
        return ApiResponse.ok(Map.of("deleted", n));
    }

    private Long userId(HttpSession session) {
        return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
    }
}
