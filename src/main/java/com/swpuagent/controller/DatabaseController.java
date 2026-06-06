package com.swpuagent.controller;

import com.swpuagent.dto.request.DbConnectionRequest;
import com.swpuagent.dto.response.ApiResponse;
import com.swpuagent.entity.DbConnection;
import com.swpuagent.service.DatabaseConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseConnectionService dbService;

    /** List connections */
    @GetMapping("/connections")
    public ApiResponse<List<DbConnection>> list(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return ApiResponse.success(dbService.listConnections(userId));
    }

    /** Add connection */
    @PostMapping("/connections")
    public ApiResponse<DbConnection> create(@Valid @RequestBody DbConnectionRequest body,
                                             HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        DbConnection conn = dbService.create(userId, body.getName(), body.getDbType(),
                body.getHost(), body.getPort(), body.getDatabaseName(),
                body.getUsername(), body.getPassword());
        return ApiResponse.success(conn);
    }

    /** Update connection */
    @PutMapping("/connections/{id}")
    public ApiResponse<DbConnection> update(@PathVariable Long id,
                                             @RequestBody DbConnectionRequest body) {
        DbConnection conn = dbService.update(id, body.getName(), body.getHost(),
                body.getPort(), body.getDatabaseName(), body.getUsername(), body.getPassword());
        return ApiResponse.success(conn);
    }

    /** Delete connection */
    @DeleteMapping("/connections/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dbService.delete(id);
        return ApiResponse.success(null);
    }

    /** Test connection */
    @PostMapping("/connections/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        return ApiResponse.success(dbService.testConnection(id));
    }

    /** Get schema */
    @GetMapping("/connections/{id}/schema")
    public ApiResponse<Map<String, Object>> schema(@PathVariable Long id,
                                                    @RequestParam(required = false) String tableName) {
        return ApiResponse.success(dbService.getSchema(id, tableName));
    }
}
