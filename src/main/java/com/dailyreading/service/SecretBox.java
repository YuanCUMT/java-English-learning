package com.dailyreading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据库里的 API Key 加密存放。
 *
 * 严格说这台机器上「加密」挡不住能读文件的人（密钥也在这台机器上），
 * 但它至少让 mysqldump / 截图 / 误提交导出的数据里不出现明文 Key。
 * 密钥放 data/secret.key（第一次运行自动生成，POSIX 600；Windows 使用当前用户 ACL）。
 */
@Service
public class SecretBox {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${app.crypto.key-file:data/secret.key}") String keyFile) {
        this.key = new SecretKeySpec(loadOrCreate(Path.of(keyFile)), "AES");
    }

    private static byte[] readKey(Path path) throws IOException {
        try {
            byte[] raw=Base64.getDecoder().decode(Files.readString(path,StandardCharsets.UTF_8).trim());
            if(raw.length!=32) throw new IllegalArgumentException();
            return raw;
        } catch(IllegalArgumentException e) {
            throw new IllegalStateException("密钥文件已损坏，请从配套备份恢复，原文件未修改："+path);
        }
    }

    private static byte[] loadOrCreate(Path path) {
        path=path.toAbsolutePath();
        try {
            if(Files.exists(path)) return readKey(path);
            Files.createDirectories(path.getParent());
            byte[] fresh=new byte[32]; new SecureRandom().nextBytes(fresh);
            Path temp=Files.createTempFile(path.getParent(),"key-",".tmp");
            try {
                try { Files.setPosixFilePermissions(temp,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
                catch(UnsupportedOperationException e) {
                    var view=Files.getFileAttributeView(temp,java.nio.file.attribute.AclFileAttributeView.class);
                    if(view!=null) {
                        var entry=java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW)
                            .setPrincipal(Files.getOwner(temp)).setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build();
                        view.setAcl(java.util.List.of(entry));
                    }
                }
                Files.writeString(temp,Base64.getEncoder().encodeToString(fresh),StandardCharsets.UTF_8);
                // Hard-link installation publishes complete bytes without replacing an existing key.
                try { Files.createLink(path,temp); }
                catch(java.nio.file.FileAlreadyExistsException race) { return readKey(path); }
                return fresh;
            } finally { Files.deleteIfExists(temp); }
        } catch(IOException e) { throw new IllegalStateException("无法安全创建或读取密钥文件："+path,e); }
    }

    /** 已经是密文就原样返回，方便重复调用 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        if (plain.startsWith(PREFIX)) return plain;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 不是密文（老数据）就原样返回，保证升级前存进去的 Key 还能用 */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 密钥文件被换过 / 数据被改过，都当解密失败处理，不要 500
            return null;
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
