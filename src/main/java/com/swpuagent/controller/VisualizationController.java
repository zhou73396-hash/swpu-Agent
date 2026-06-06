package com.swpuagent.controller;

import com.swpuagent.dto.request.ChartGenerateRequest;
import com.swpuagent.dto.response.ApiResponse;
import com.swpuagent.service.VisualizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/viz")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService vizService;

    /** Generate chart configuration — POST /api/viz/generate */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@Valid @RequestBody ChartGenerateRequest request) {
        Map<String, Object> result = vizService.generateChart(
                request.getData(), request.getChartType(),
                request.getTitle(), request.getXAxisLabel(), request.getYAxisLabel());
        return ApiResponse.success(result);
    }
}
