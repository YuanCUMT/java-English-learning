package com.dailyreading.service;

import com.dailyreading.mapper.LookupCacheMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 截图识别会带出碎片（bl、da、kb，或者糊成一行 levv）。这些词不该进提示词，
 * 也不该直接塞进单词本，否则文章写歪、单词本里全是没释义的空卡。
 */
class WordFilterTest {

    private WordFilter filter(DictService dict, LookupCacheMapper cache) {
        return new WordFilter(dict, cache);
    }

    private DictService dictWith(String... words) {
        DictService dict = mock(DictService.class);
        when(dict.lookup(anyString())).thenReturn(null);
        for (String w : words) {
            when(dict.lookup(w)).thenReturn(Map.of("word", w, "cn", "释义"));
        }
        return dict;
    }

    private LookupCacheMapper emptyCache() {
        LookupCacheMapper cache = mock(LookupCacheMapper.class);
        when(cache.findPayload(anyLong(), anyString())).thenReturn(null);
        return cache;
    }

    @Test
    void 两个字母的碎片被丢掉() {
        WordFilter.Result r = filter(dictWith("abandon"), emptyCache())
                .filter(1L, List.of("abandon", "bl", "da"));

        assertEquals(List.of("abandon"), r.kept());
        assertEquals(List.of("bl", "da"), r.dropped());
    }

    @Test
    void 词典里查得到就留下哪怕短() {
        WordFilter.Result r = filter(dictWith("ob"), emptyCache())
                .filter(1L, List.of("ob"));

        assertEquals(List.of("ob"), r.kept());
        assertTrue(r.dropped().isEmpty());
    }

    @Test
    void 查词缓存里有的也留下() {
        LookupCacheMapper cache = emptyCache();
        when(cache.findPayload(anyLong(), eq("kos"))).thenReturn("{}");
        WordFilter.Result r = filter(dictWith(), cache)
                .filter(1L, List.of("kos", "bl"));

        assertEquals(List.of("kos"), r.kept());
        assertEquals(List.of("bl"), r.dropped());
    }

    @Test
    void 没有元音的长串也算碎片() {
        WordFilter.Result r = filter(dictWith(), emptyCache())
                .filter(1L, List.of("bsh", "mrkt", "ledger"));

        assertEquals(List.of("ledger"), r.kept());
        assertEquals(List.of("bsh", "mrkt"), r.dropped());
    }

    @Test
    void 空词和空白被忽略() {
        WordFilter.Result r = filter(dictWith(), emptyCache())
                .filter(1L, List.of("", "  ", "ledger"));

        assertEquals(List.of("ledger"), r.kept());
        assertTrue(r.dropped().isEmpty());
    }

    @Test
    void 看着像词的长词留下() {
        WordFilter.Result r = filter(dictWith(), emptyCache())
                .filter(1L, List.of("oceanographer", "levv"));

        assertTrue(r.kept().contains("oceanographer"));
        assertTrue(r.kept().contains("levv"), "levv 含元音，按规则保留，靠词典或用户删");
    }
}
