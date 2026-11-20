package com.dailyreading.controller;
import com.dailyreading.common.ApiResponse;
import com.dailyreading.service.ExampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequiredArgsConstructor
public class ExampleController {
    private final ExampleService examples;
    @PostMapping("/api/examples/load") public ApiResponse load(@RequestAttribute("userId") Long user) {
        return ApiResponse.ok(Map.of("id",examples.load(user)));
    }
}
