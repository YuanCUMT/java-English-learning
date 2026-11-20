package com.dailyreading.service;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Same-user paid operations are serialized; never retry automatically. */
@Service
public class RequestGuard {
    private final Set<Long> active = ConcurrentHashMap.newKeySet();
    public <T> T run(Long user, Supplier<T> work) {
        if (!active.add(user)) throw new ResponseStatusException(HttpStatus.CONFLICT,"已有 AI 请求正在处理，请稍候");
        try { return work.get(); } finally { active.remove(user); }
    }
}
