package com.dailyreading.service;

import com.dailyreading.mapper.LookupCacheMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 截图识别会带出碎片（bl、da、bsh…）。这些词不该进提示词，
 * 否则模型要么绕开它们，要么硬塞进句子里把文章写歪。
 */
class ArticleServiceTargetsTest {

    /** 只测 pickTargets，其它依赖传 null 即可。 */
    private ArticleService service(DictService dict, LookupCacheMapper cache) {
        return new ArticleService(null, null, null, null, null, dict, cache, null);
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
        ArticleService.Targets t = service(dictWith("abandon"), emptyCache())
                .pickTargets(1L, List.of("abandon", "bl", "da"));

        assertEquals(List.of("abandon"), t.kept());
        assertEquals(List.of("bl", "da"), t.dropped());
    }

    @Test
    void 词典里查得到就留下哪怕短() {
        ArticleService.Targets t = service(dictWith("ob"), emptyCache())
                .pickTargets(1L, List.of("ob"));

        assertEquals(List.of("ob"), t.kept());
        assertTrue(t.dropped().isEmpty());
    }

    @Test
    void 查词缓存里有的也留下() {
        LookupCacheMapper cache = emptyCache();
        when(cache.findPayload(anyLong(), eq("kos"))).thenReturn("{}");
        ArticleService.Targets t = service(dictWith(), cache)
                .pickTargets(1L, List.of("kos", "bl"));

        assertEquals(List.of("kos"), t.kept());
        assertEquals(List.of("bl"), t.dropped());
    }

    @Test
    void 没有元音的长串也算碎片() {
        ArticleService.Targets t = service(dictWith(), emptyCache())
                .pickTargets(1L, List.of("bsh", "mrkt", "ledger"));

        assertEquals(List.of("ledger"), t.kept());
        assertEquals(List.of("bsh", "mrkt"), t.dropped());
    }

    @Test
    void 空词和空白被忽略() {
        ArticleService.Targets t = service(dictWith(), emptyCache())
                .pickTargets(1L, List.of("", "  ", "ledger"));

        assertEquals(List.of("ledger"), t.kept());
        assertTrue(t.dropped().isEmpty());
    }
}
