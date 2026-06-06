package com.swpuagent.service;

import com.swpuagent.common.exception.NotFoundException;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserInfoMapper userInfoMapper;

    public Map<String, Object> getProfile(Long userId) {
        UserInfo user = userInfoMapper.findById(userId);
        if (user == null) throw new NotFoundException("用户不存在");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("userName", user.getUserName());
        profile.put("email", user.getEmail());
        profile.put("role", user.getRole());
        profile.put("age", user.getAge());
        profile.put("country", user.getCountry());
        profile.put("salary", user.getSalary());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("updatedAt", user.getUpdatedAt());
        return profile;
    }

    public Map<String, Object> updateProfile(Long userId, String email, Integer age,
                                              String country, java.math.BigDecimal salary) {
        UserInfo user = userInfoMapper.findById(userId);
        if (user == null) throw new NotFoundException("用户不存在");

        if (email != null) user.setEmail(email);
        if (age != null) user.setAge(age);
        if (country != null) user.setCountry(country);
        if (salary != null) user.setSalary(salary);
        userInfoMapper.update(user);
        return getProfile(userId);
    }
}
