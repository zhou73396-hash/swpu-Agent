package com.swpuagent.service;

public interface RedisService {

    /**
     * Store verification code in Redis.
     * Key: prefix + email, Value: code, TTL: 60s
     */
    void storeCode(String prefix, String email, String code);

    /**
     * Get and verify the stored code.
     * Returns the stored code or null if expired/not found.
     */
    String getCode(String prefix, String email);

    /**
     * Delete a stored code.
     */
    void deleteCode(String prefix, String email);
}
