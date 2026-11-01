package com.dailyreading.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流是纯内存 + 可注入时钟，所以能测「过了 5 分钟自动解封」这种时间相关行为。
 */
class LoginAttemptServiceTest {

    /** 测试用的可拨动时钟 */
    static class TickClock extends Clock {
        Instant now = Instant.parse("2026-11-01T10:00:00Z");

        void plus(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TickClock clock = new TickClock();
    private final LoginAttemptService svc = new LoginAttemptService(clock);
    private static final String KEY = LoginAttemptService.key("Alice", "127.0.0.1");

    @Test
    void key大小写和空格归一() {
        assertEquals(KEY, LoginAttemptService.key("  alice ", "127.0.0.1"));
        assertEquals(LoginAttemptService.key("x", null), LoginAttemptService.key("x", " "));
    }

    @Test
    void 失败四次还能试第五次() {
        for (int i = 0; i < 4; i++) svc.onFailure(KEY);
        assertEquals(0, svc.remainingBlockSeconds(KEY));
    }

    @Test
    void 连续失败五次会被挡住() {
        for (int i = 0; i < 5; i++) svc.onFailure(KEY);
        assertTrue(svc.remainingBlockSeconds(KEY) > 0);
    }

    @Test
    void 封禁五分钟后自动解除() {
        for (int i = 0; i < 5; i++) svc.onFailure(KEY);
        assertTrue(svc.remainingBlockSeconds(KEY) > 0);
        clock.plus(Duration.ofMinutes(5).plusSeconds(1));
        assertEquals(0, svc.remainingBlockSeconds(KEY));
    }

    @Test
    void 登录成功会清掉计数() {
        for (int i = 0; i < 4; i++) svc.onFailure(KEY);
        svc.onSuccess(KEY);
        for (int i = 0; i < 4; i++) svc.onFailure(KEY);
        assertEquals(0, svc.remainingBlockSeconds(KEY), "成功后应当重新计数");
    }

    @Test
    void 不同用户名互不影响() {
        for (int i = 0; i < 5; i++) svc.onFailure(KEY);
        assertEquals(0, svc.remainingBlockSeconds(LoginAttemptService.key("bob", "127.0.0.1")));
    }

    @Test
    void 窗口过期后失败次数重新算() {
        for (int i = 0; i < 4; i++) svc.onFailure(KEY);
        clock.plus(Duration.ofMinutes(11));
        svc.onFailure(KEY);
        assertEquals(0, svc.remainingBlockSeconds(KEY), "间隔超过窗口，不该累计");
    }
}
