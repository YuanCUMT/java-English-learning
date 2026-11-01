package com.dailyreading.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecretBoxTest {

    @TempDir
    Path dir;

    private SecretBox box() {
        return new SecretBox(dir.resolve("secret.key").toString());
    }

    @Test
    void 加密后能解回原文() {
        SecretBox b = box();
        String key = "sk-1234567890abcdef";
        String enc = b.encrypt(key);
        assertTrue(enc.startsWith("enc:v1:"), "应当带版本前缀");
        assertFalse(enc.contains(key), "密文里不能出现明文");
        assertEquals(key, b.decrypt(enc));
    }

    @Test
    void 同一个明文两次加密结果不同() {
        SecretBox b = box();
        assertNotEquals(b.encrypt("sk-abc"), b.encrypt("sk-abc"), "每次要换 IV");
    }

    @Test
    void 密钥持久化后还能解密() {
        String f = dir.resolve("secret.key").toString();
        String enc = new SecretBox(f).encrypt("sk-persist-1");
        assertEquals("sk-persist-1", new SecretBox(f).decrypt(enc), "重启后（新实例）要能解开老数据");
    }

    @Test
    void 明文原样返回兼容老数据() {
        SecretBox b = box();
        assertEquals("sk-plain-old", b.decrypt("sk-plain-old"));
    }

    @Test
    void 密文被改坏时返回null而不是抛异常() {
        SecretBox b = box();
        String enc = b.encrypt("sk-x");
        assertNull(b.decrypt(enc.substring(0, enc.length() - 4) + "AAAA"));
    }

    @Test
    void 空值不加密() {
        SecretBox b = box();
        assertNull(b.encrypt(null));
        assertEquals("", b.encrypt(""));
    }

    @Test
    void 密钥文件会被创建() {
        SecretBox b = box();
        b.encrypt("sk-a");
        assertTrue(Files.exists(dir.resolve("secret.key")));
    }
}
