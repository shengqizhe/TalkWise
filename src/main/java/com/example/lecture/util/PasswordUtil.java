package com.example.lecture.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 密码加密工具类（使用AES对称加密，可逆）
 */
public class PasswordUtil {
    // AES密钥，必须为16位。实际项目建议放到配置文件中
    private static final String KEY = "1234567890123456";
    private static final String ALGORITHM = "AES";

    /**
     * 加密密码
     * @param password 原始密码
     * @return 加密后的密文
     */
    public static String encode(String password) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(password.getBytes());
            // 使用Base64编码，方便存储
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 验证密码（解密后比对）
     * @param password 原始密码
     * @param encryptedPassword 加密后的密文
     * @return 是否匹配
     */
    public static boolean matches(String password, String encryptedPassword) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            String decryptedPassword = new String(decrypted);
            return password.equals(decryptedPassword);
        } catch (Exception e) {
            return false;
        }
    }
} 