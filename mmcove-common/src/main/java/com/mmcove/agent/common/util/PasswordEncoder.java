package com.mmcove.agent.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 密码加密工具类。
 */
public class PasswordEncoder {

    private static final String ALGORITHM = "SHA-256";

    /**
     * 加密密码。
     */
    public static String encode(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证密码。
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        // 兼容旧密码（BCrypt格式，$2a$开头）
        if (encodedPassword.startsWith("$2")) {
            return false; // 需要重新设置密码
        }
        return encode(rawPassword).equals(encodedPassword);
    }
}
