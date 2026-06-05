package com.swpuagent.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent tool: send verification code email.
 * Currently a placeholder — integrate with JavaMailSender for real SMTP delivery.
 */
@Slf4j
@Component
public class SendEmailTool {

    /**
     * Send a verification code to the specified email address.
     *
     * @param email recipient email address
     * @param code  6-digit verification code
     * @return delivery result
     */
    public Map<String, Object> send(String email, String code) {
        // TODO: Integrate with Spring JavaMailSender for real SMTP delivery
        log.info("============================================");
        log.info("[SendEmailTool] Verification Code Email");
        log.info("To: {}", email);
        log.info("Code: {}", code);
        log.info("============================================");

        return Map.of(
                "success", true,
                "message", "Verification code sent to " + email
        );
    }
}
