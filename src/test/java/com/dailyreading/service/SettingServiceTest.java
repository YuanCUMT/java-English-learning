package com.dailyreading.service;

import com.dailyreading.config.DeepSeekProperties;
import com.dailyreading.mapper.SettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 关注两件事：存进库的一定是密文；读出来要能还原成原来的 Key（含老数据的明文兼容 + 自动迁移）。
 */
class SettingServiceTest {

    @TempDir
    Path dir;

    private SettingMapper mapper;
    private SecretBox box;
    private SettingService svc;

    private static final long UID = 7L;
    private static final String KEY = "deepseek.apiKey";

    @BeforeEach
    void setUp() {
        mapper = mock(SettingMapper.class);
        box = new SecretBox(dir.resolve("secret.key").toString());
        DeepSeekProperties props = new DeepSeekProperties();
        props.setApiKey("");
        svc = new SettingService(mapper, props, box);
    }

    @Test
    void 保存时会加密再写库() {
        svc.saveDeepSeekKey(UID, "sk-1234567890abcdef");
        verify(mapper).put(eq(UID), eq(KEY), argThat(v -> v.startsWith("enc:v1:") && !v.contains("sk-1234567890abcdef")));
    }

    @Test
    void 读出来是解密后的明文() {
        String enc = box.encrypt("sk-stored-secret-123456");
        when(mapper.get(UID, KEY)).thenReturn(enc);
        assertEquals("sk-stored-secret-123456", svc.userDeepSeekKey(UID));
        verify(mapper, never()).put(anyLong(), anyString(), anyString());
    }

    @Test
    void 老数据的明文能读并顺手迁移成密文() {
        when(mapper.get(UID, KEY)).thenReturn("sk-legacy-plain-123456");
        assertEquals("sk-legacy-plain-123456", svc.userDeepSeekKey(UID));
        verify(mapper).put(eq(UID), eq(KEY), argThat(v -> v.startsWith("enc:v1:")));
    }

    @Test
    void 空值会删除记录() {
        svc.saveDeepSeekKey(UID, "   ");
        verify(mapper).remove(UID, KEY);
        verify(mapper, never()).put(anyLong(), anyString(), anyString());
    }

    @Test
    void 用户没填时回落到配置文件() {
        when(mapper.get(UID, KEY)).thenReturn(null);
        DeepSeekProperties props = new DeepSeekProperties();
        props.setApiKey("sk-from-config-1234567890");
        SettingService s2 = new SettingService(mapper, props, box);
        assertEquals("sk-from-config-1234567890", s2.effectiveDeepSeekKey(UID));
        assertTrue(s2.deepSeekConfigured(UID));
    }

    @Test
    void 掩码只露头尾() {
        when(mapper.get(UID, KEY)).thenReturn(box.encrypt("sk-abcdefghijklmnopqrstuvwxyz"));
        String masked = svc.maskedDeepSeekKey(UID);
        assertTrue(masked.startsWith("sk-abcd"), masked);
        assertTrue(masked.endsWith("wxyz"), masked);
        assertFalse(masked.contains("efghij"), masked);
    }
}
