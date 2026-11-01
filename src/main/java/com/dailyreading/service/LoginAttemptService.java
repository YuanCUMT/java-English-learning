package com.dailyreading.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一个「用户名 + IP」在窗口期内失败太多次，就先挡住。
 *
 * 作业是本地单机，本来不太需要；但接口是公开的，加一层很便宜，
 * 答辩被问到「有没有防爆破」时能拿出代码而不是只靠嘴说。
 *
 * 存在内存里足够：单机、单进程，重启就清空，也不需要持久化。
 */
@Service
public class LoginAttemptService {

    /** 连续失败多少次开始挡 */
    static final int MAX_FAILS = 5;
    /** 失败计数的时间窗口 */
    static final Duration WINDOW = Duration.ofMinutes(10);
    /** 挡多久 */
    static final Duration BLOCK = Duration.ofMinutes(5);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemDefaultZone());
    }

    /** 测试用：可以塞一个固定的时钟进来 */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public static String key(String username, String ip) {
        String u = username == null ? "" : username.trim().toLowerCase();
        String who = ip == null || ip.isBlank() ? "unknown" : ip;
        return u + "|" + who;
    }

    /** 还要等多少秒才能再试；返回 0 表示可以试 */
    public long remainingBlockSeconds(String key) {
        Bucket b = buckets.get(key);
        if (b == null) return 0;
        Instant now = clock.instant();
        if (b.blockedUntil != null && now.isBefore(b.blockedUntil)) {
            return Math.max(1, Duration.between(now, b.blockedUntil).toSeconds() + 1);
        }
        return 0;
    }

    /** 记一次失败，必要时升级为封禁 */
    public void onFailure(String key) {
        Instant now = clock.instant();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (b) {
            if (b.windowStart == null || now.isAfter(b.windowStart.plus(WINDOW))) {
                b.windowStart = now;
                b.fails = 0;
            }
            b.fails++;
            if (b.fails >= MAX_FAILS) {
                b.blockedUntil = now.plus(BLOCK);
                b.windowStart = now;
                b.fails = 0;
            }
        }
    }

    /** 登录成功就清掉这个 key 的历史 */
    public void onSuccess(String key) {
        buckets.remove(key);
    }

    private static final class Bucket {
        Instant windowStart;
        int fails;
        Instant blockedUntil;
    }
}
