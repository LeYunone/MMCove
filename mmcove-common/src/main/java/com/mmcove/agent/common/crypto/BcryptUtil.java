package com.mmcove.agent.common.crypto;

import org.mindrot.jbcrypt.BCrypt;

/**
 * bcrypt 哈希工具，兼容 Go 版本 encryption/bcrypt.go。
 * - KeyText 格式: 时间戳_bcrypt(时间戳_tokenKey)
 * - 验证 60 秒时效
 */
public class BcryptUtil {

    /**
     * bcrypt 哈希（cost=10，与 Go 的 bcrypt.DefaultCost 一致）。
     */
    public static String hashText(String text) {
        return BCrypt.hashpw(text, BCrypt.gensalt(10));
    }

    /**
     * 验证明文与哈希是否匹配。
     * 非 BCrypt 哈希（如旧版 SHA-256 密文）或格式异常时返回 false（视为校验失败），
     * 不抛异常——避免上层把"密码格式不对"误报成 500。
     */
    public static boolean verifyText(String hashedText, String text) {
        if (hashedText == null || !hashedText.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(text, hashedText);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 KeyText 是否有效（兼容 Go 版本 IsTextValid）。
     * KeyText 格式: 时间戳_bcrypt(时间戳_tokenKey)
     * 验证条件: 时间戳距当前不超过 60 秒，且 bcrypt 哈希匹配。
     */
    public static boolean isTextValid(String tokenKey, String keyText) {
        if (tokenKey == null || keyText == null) {
            return false;
        }

        String[] parts = keyText.split("_");
        if (parts.length != 2) {
            return false;
        }

        long clientTime;
        try {
            clientTime = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - clientTime;

        if (timeDifference > 60_000) {
            return false;
        }

        String textVal = parts[0] + "_" + tokenKey;
        return verifyText(parts[1], textVal);
    }
}
