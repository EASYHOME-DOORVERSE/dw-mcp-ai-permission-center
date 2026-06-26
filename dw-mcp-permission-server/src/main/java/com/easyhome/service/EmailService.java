package com.easyhome.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮件验证码服务
 * <p>
 * 用于忘记密码场景：生成6位随机验证码 → 存入 Redis（5分钟过期） → 发送至用户邮箱。
 * 同一用户60秒内只能发送一次（Redis TTL 控制频率）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedissonClient redissonClient;

    /** 发件人地址，默认读取 spring.mail.username */
    @Value("${spring.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    /** Redis Key 前缀 */
    private static final String RESET_CODE_PREFIX = "forgot_pwd:code:";
    /** 发送冷却时间 Key 前缀（防刷） */
    private static final String RESET_COOLDOWN_PREFIX = "forgot_pwd:cd:";
    /** 每日发送次数 Key 前缀 */
    private static final String DAILY_COUNT_PREFIX = "forgot_pwd:daily:";

    /** 验证码有效期（分钟） */
    private static final int CODE_TTL_MINUTES = 5;
    /** 发送冷却时间（秒） */
    private static final int COOLDOWN_SECONDS = 60;
    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;
    /** 每用户每日最大发送次数 */
    private static final int MAX_DAILY_SENDS = 5;

    private final SecureRandom random = new SecureRandom();

    /**
     * 发送密码重置验证码
     *
     * @param username 用户名（用于 Redis Key）
     * @param email    收件人邮箱
     * @throws IllegalStateException 冷却时间内重复发送时抛出
     */
    public void sendResetCode(String username, String email) {
        // 检查冷却时间
        RBucket<String> cooldownBucket = redissonClient.getBucket(RESET_COOLDOWN_PREFIX + username);
        if (cooldownBucket.isExists()) {
            throw new IllegalStateException("验证码已发送，请60秒后再试");
        }

        // 检查每日发送次数
        RAtomicLong dailyCount = redissonClient.getAtomicLong(DAILY_COUNT_PREFIX + username);
        long count = dailyCount.incrementAndGet();
        // 每次检查 TTL，防止 expire 丢失导致 key 永不过期
        if (dailyCount.remainTimeToLive() <= 0) {
            dailyCount.expire(24, TimeUnit.HOURS);
        }
        if (count > MAX_DAILY_SENDS) {
            throw new IllegalStateException("今日验证码发送次数已达上限，请明天再试");
        }

        // 生成6位随机数字验证码
        String code = generateCode();

        // 存入 Redis，5分钟过期
        RBucket<String> codeBucket = redissonClient.getBucket(RESET_CODE_PREFIX + username);
        codeBucket.set(code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        // 设置冷却时间标记，60秒内不可重复发送
        cooldownBucket.set("1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // 发送邮件
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(email);
            msg.setSubject("MCP 权限中心 - 密码重置验证码");
            msg.setText(String.format(
                    "您的密码重置验证码为：%s\n\n验证码 %d 分钟内有效，请勿泄露给他人。\n如非本人操作，请忽略此邮件。",
                    code, CODE_TTL_MINUTES));
            mailSender.send(msg);
            log.info("密码重置验证码已发送: username={}, email={}", username, maskEmail(email));
        } catch (Exception e) {
            // 发送失败，清除 Redis 中的验证码和冷却标记，回退每日计数
            codeBucket.delete();
            cooldownBucket.delete();
            dailyCount.decrementAndGet();
            log.error("验证码邮件发送失败: username={}, email={}", username, maskEmail(email), e);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 验证验证码是否正确
     *
     * @param username 用户名
     * @param code     用户输入的验证码
     * @return 验证码正确返回 true
     */
    public boolean verifyResetCode(String username, String code) {
        RBucket<String> codeBucket = redissonClient.getBucket(RESET_CODE_PREFIX + username);
        String stored = codeBucket.get();
        return stored != null && stored.equals(code);
    }

    /**
     * 验证码使用后立即失效
     */
    public void consumeResetCode(String username) {
        redissonClient.getBucket(RESET_CODE_PREFIX + username).delete();
    }

    /**
     * 生成 N 位随机数字验证码
     */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 脱敏邮箱地址：te***@qq.com
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.charAt(0) + email.charAt(1) + "***" + email.substring(atIdx);
    }
}
