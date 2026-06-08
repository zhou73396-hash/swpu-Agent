package com.swpuagent.controller;

import com.swpuagent.dto.response.ApiResponse;
import com.swpuagent.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Get current user profile */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return ApiResponse.success(userService.getProfile(userId));
    }

    /** Update current user profile */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(HttpServletRequest req,
                                                           @RequestBody Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        String email = (String) body.get("email");
        String userName = (String) body.get("userName");
        return ApiResponse.success(userService.updateProfile(userId, email, userName));
    }
}
