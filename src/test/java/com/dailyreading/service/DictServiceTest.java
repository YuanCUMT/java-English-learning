package com.dailyreading.service;

import com.dailyreading.config.DeepSeekProperties;
import com.dailyreading.entity.DictEntry;
import com.dailyreading.mapper.DictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

/** 词典词形还原：点 takes 要能查到 take。 */
class DictServiceTest {

    private DictMapper mapper;
    private DictService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(DictMapper.class);
        service = new DictService(mapper);
    }

    @Test
    void 原形直接命中() {
        DictEntry e = new DictEntry();
        e.setWord("ledger");
        Mockito.when(mapper.findByWord("ledger")).thenReturn(e);
        assertNotNull(service.lookup("Ledger"));
        assertEquals("ledger", service.lookup("Ledger").get("word"));
    }

    @Test
    void 变形能查到原形() {
        DictEntry base = new DictEntry();
        base.setWord("take");
        Mockito.when(mapper.findByWord("takes")).thenReturn(null);
        Mockito.when(mapper.findLemma("takes")).thenReturn("take");
        Mockito.when(mapper.findByWord("take")).thenReturn(base);

        var hit = service.lookup("takes");
        assertNotNull(hit);
        assertEquals("takes", hit.get("surface"));
        assertEquals("take", hit.get("word"));
    }

    @Test
    void 查不到返回null() {
        Mockito.when(mapper.findByWord(anyString())).thenReturn(null);
        Mockito.when(mapper.findLemma(anyString())).thenReturn(null);
        assertNull(service.lookup("zzzznotaword"));
    }

    @Test
    void normalize_去首尾空格并小写() {
        assertEquals("in terms of", DictService.normalize("  In Terms   Of "));
        assertEquals("", DictService.normalize(null));
    }
}
