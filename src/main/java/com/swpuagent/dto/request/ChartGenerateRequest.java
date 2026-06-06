package com.swpuagent.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChartGenerateRequest {
    @NotEmpty(message = "数据不能为空")
    private List<Map<String, Object>> data;

    private String chartType = "auto";  // auto, bar, line, pie, scatter, table
    private String title;
    private String xAxisLabel;
    private String yAxisLabel;
}
