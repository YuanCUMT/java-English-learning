package com.dailyreading.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class SecretBoxRecoveryTest {
    @TempDir Path dir;
    @Test void emptyFileIsPreserved() throws Exception { bad(""); }
    @Test void invalidBase64IsPreserved() throws Exception { bad("not-base64!!"); }
    @Test void wrongLengthIsPreserved() throws Exception { bad(Base64.getEncoder().encodeToString(new byte[16])); }
    private void bad(String text) throws Exception {
        Path p=dir.resolve("secret.key"); Files.writeString(p,text);
        assertThrows(IllegalStateException.class,()->new SecretBox(p.toString()));
        assertEquals(text,Files.readString(p));
    }
    @Test void concurrentCreationUsesSameKey() throws Exception {
        Path p=dir.resolve("secret.key");
        try(var executor=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<SecretBox>>();
            for(int i=0;i<8;i++) futures.add(executor.submit(()->new SecretBox(p.toString())));
            SecretBox first=futures.get(0).get(); String encrypted=first.encrypt("a-private-value");
            for(var future:futures) assertEquals("a-private-value",future.get().decrypt(encrypted));
        }
    }
}
