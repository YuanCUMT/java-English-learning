package com.dailyreading.service;

import com.dailyreading.mapper.LookupCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 判断「这串东西到底是不是个词」。
 *
 * 截图识别经常带出碎片：拦腰截断的 bl、da、kb，或者把两行糊在一起的 levv。
 * 这些词喂给模型只会让文章变歪，也让单词本里全是没释义的空卡。
 *
 * 判定：词典查得到、或者以前查过（有查词缓存）、或者看着像词（>=3 个字母且含元音）。
 * 三条都不满足的，当碎片丢掉。
 */
@Component
@RequiredArgsConstructor
public class WordFilter {

    private static final Set<Character> VOWELS =
            Set.of('a', 'e', 'i', 'o', 'u', 'y');

    private final DictService dictService;
    private final LookupCacheMapper lookupCacheMapper;

    /** kept = 能用的词；dropped = 判为碎片的词。 */
    public record Result(List<String> kept, List<String> dropped) {}

    public Result filter(Long userId, List<String> words) {
        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        if (words == null) return new Result(kept, dropped);

        for (String raw : words) {
            String w = raw == null ? "" : raw.trim();
            if (w.isEmpty()) continue;
            if (looksLikeWord(userId, w)) kept.add(w);
            else dropped.add(w);
        }
        return new Result(kept, dropped);
    }

    /** 单个词是否可信。 */
    public boolean looksLikeWord(Long userId, String raw) {
        String t = raw.trim().toLowerCase();
        if (t.isEmpty()) return false;
        if (dictService.lookup(t) != null) return true;
        if (lookupCacheMapper.findPayload(userId, t) != null) return true;
        if (t.length() < 3) return false;
        for (char c : t.toCharArray()) {
            if (VOWELS.contains(c)) return true;
        }
        return false;
    }
}
