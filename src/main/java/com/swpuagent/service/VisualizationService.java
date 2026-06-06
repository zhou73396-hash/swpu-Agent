package com.swpuagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class VisualizationService {

    /**
     * Auto-detect the best chart type based on data structure.
     *
     * Rules:
     *   1 text + 1 numeric → bar
     *   1 text + multiple numeric → grouped bar
     *   1 date + 1 numeric → line
     *   1 category + 1 numeric (≤10 cats) → pie
     *   2 numeric → scatter
     *   otherwise → table
     */
    public Map<String, Object> generateChart(List<Map<String, Object>> data,
                                              String chartType, String title,
                                              String xLabel, String yLabel) {

        if (data.isEmpty()) {
            return Map.of("chartType", "table", "reason", "No data");
        }

        // Detect column types
        List<String> columns = new ArrayList<>(data.get(0).keySet());
        List<String> textCols = new ArrayList<>();
        List<String> numericCols = new ArrayList<>();
        List<String> dateCols = new ArrayList<>();

        for (String col : columns) {
            Object sample = data.get(0).get(col);
            if (sample instanceof Number) {
                numericCols.add(col);
            } else if (isDateLike(sample)) {
                dateCols.add(col);
            } else {
                textCols.add(col);
            }
        }

        // Determine chart type
        String detected = "table";
        String reason = "Complex data, using table";

        if ("auto".equals(chartType)) {
            if (!dateCols.isEmpty() && !numericCols.isEmpty()) {
                detected = "line";
                reason = "1 date column + numeric data → line chart";
            } else if (textCols.size() == 1 && numericCols.size() == 1) {
                if (data.size() <= 10) {
                    detected = "pie";
                    reason = "1 category + 1 numeric (≤10 categories) → pie chart";
                } else {
                    detected = "bar";
                    reason = "1 category + 1 numeric → bar chart";
                }
            } else if (textCols.size() == 1 && numericCols.size() > 1) {
                detected = "bar";
                reason = "1 category + multiple numeric → grouped bar chart";
            } else if (textCols.isEmpty() && numericCols.size() == 2) {
                detected = "scatter";
                reason = "2 numeric columns → scatter plot";
            }
        } else {
            detected = chartType;
            reason = "User specified";
        }

        // Build ECharts option
        Map<String, Object> option = buildEChartsOption(data, columns,
                textCols, numericCols, dateCols, detected, title, xLabel, yLabel);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chartType", detected);
        result.put("reason", reason);
        result.put("option", option);
        return result;
    }

    private Map<String, Object> buildEChartsOption(List<Map<String, Object>> data,
                                                    List<String> columns,
                                                    List<String> textCols,
                                                    List<String> numericCols,
                                                    List<String> dateCols,
                                                    String chartType,
                                                    String title, String xLabel, String yLabel) {
        Map<String, Object> option = new LinkedHashMap<>();

        if (title != null) {
            option.put("title", Map.of("text", title, "left", "center"));
        }
        option.put("tooltip", Map.of("trigger", "axis"));

        String xCol = !dateCols.isEmpty() ? dateCols.get(0) :
                      !textCols.isEmpty() ? textCols.get(0) : columns.get(0);

        if (!"pie".equals(chartType) && !"table".equals(chartType)) {
            List<String> xData = data.stream()
                    .map(r -> String.valueOf(r.get(xCol))).toList();
            option.put("xAxis", Map.of(
                    "type", "category",
                    "data", xData,
                    "name", xLabel != null ? xLabel : xCol
            ));
            option.put("yAxis", Map.of(
                    "type", "value",
                    "name", yLabel != null ? yLabel : ""
            ));

            List<Map<String, Object>> series = new ArrayList<>();
            String[] colors = {"#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de"};
            int ci = 0;
            for (String nCol : numericCols) {
                List<Object> values = data.stream().map(r -> r.get(nCol)).toList();
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", nCol);
                s.put("type", chartType);
                s.put("data", values);
                s.put("itemStyle", Map.of("color", colors[ci % colors.length]));
                series.add(s);
                ci++;
            }
            option.put("series", series);

        } else if ("pie".equals(chartType)) {
            option.remove("tooltip");
            option.put("tooltip", Map.of("trigger", "item"));
            List<Map<String, Object>> pieData = new ArrayList<>();
            for (Map<String, Object> row : data) {
                pieData.add(Map.of(
                        "name", String.valueOf(row.get(xCol)),
                        "value", row.get(numericCols.get(0))
                ));
            }
            option.put("series", List.of(Map.of(
                    "type", "pie",
                    "radius", "50%",
                    "data", pieData,
                    "label", Map.of("show", true, "formatter", "{b}: {c}")
            )));
        }

        return option;
    }

    private boolean isDateLike(Object val) {
        if (val == null) return false;
        String s = val.toString();
        return s.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || s.matches(".*\\d{4}/\\d{2}/\\d{2}.*");
    }
}
