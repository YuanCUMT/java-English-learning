package com.dailyreading.service;

import com.dailyreading.entity.Word;
import com.dailyreading.mapper.WordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 单词本历史的查询次数：必须是「一次取全量 + 一次批量查词典」，
 * 不能退回到按天循环查（那样天数一多就又是一堆 SQL）。
 */
class WordServiceHistoryTest {

    private WordMapper wordMapper;
    private DictService dictService;
    private WordService service;

    @BeforeEach
    void setUp() {
        wordMapper = mock(WordMapper.class);
        dictService = mock(DictService.class);
        service = new WordService(wordMapper, dictService);
    }

    private static Word word(String w, LocalDate d) {
        Word x = new Word();
        x.setWord(w);
        x.setBatchDate(d);
        return x;
    }

    @Test
    void 历史只查一次数据库并按天分组() {
        LocalDate d1 = LocalDate.of(2026, 10, 31);
        LocalDate d2 = LocalDate.of(2026, 10, 30);
        when(wordMapper.listAllByUser(7L)).thenReturn(List.of(
                word("harvest", d1), word("abandon", d1), word("ledger", d2)));
        when(dictService.lookupBatch(anyList())).thenReturn(Map.of(
                "harvest", Map.of("cn", "收割", "phonetic", "/ˈhɑːvɪst/", "pos", "v.")));

        List<Map<String, Object>> days = service.history(7L);

        assertEquals(2, days.size(), "两天应该分成两组");
        assertEquals("2026-10-31", days.get(0).get("date"));
        assertEquals(2, days.get(0).get("count"));
        assertEquals("2026-10-30", days.get(1).get("date"));
        // 只有 harvest 有释义，其余两个词只留词本身
        assertTrue(days.get(0).toString().contains("收割"));

        verify(wordMapper, times(1)).listAllByUser(7L);
        verify(wordMapper, never()).listByDate(any(), any());
        verify(wordMapper, never()).listDates(any());
        verify(dictService, times(1)).lookupBatch(anyList());
    }

    @Test
    void 没有词时返回空列表且不查词典() {
        when(wordMapper.listAllByUser(7L)).thenReturn(List.of());

        List<Map<String, Object>> days = service.history(7L);

        assertTrue(days.isEmpty());
        verify(dictService, never()).lookupBatch(anyList());
    }
}
