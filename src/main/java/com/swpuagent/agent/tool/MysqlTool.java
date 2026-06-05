package com.swpuagent.agent.tool;

import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent tool: query the user_info table.
 * Security: only SELECT queries are allowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlTool {

    private final UserInfoMapper userInfoMapper;

    public Map<String, Object> queryEmail(String email) {
        UserInfo user = userInfoMapper.findByEmail(email);
        Map<String, Object> result = new HashMap<>();
        result.put("exists", user != null);
        if (user != null) {
            result.put("userId", user.getId());
            result.put("userName", user.getUserName());
        }
        log.debug("MysqlTool queried email={}: exists={}", email, user != null);
        return result;
    }
}
