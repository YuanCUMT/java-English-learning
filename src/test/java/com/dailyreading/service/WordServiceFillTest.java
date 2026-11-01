package com.dailyreading.service;

import com.dailyreading.mapper.LookupCacheMapper;
import com.dailyreading.mapper.WordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 「补全释义」：词典里有的不花钱，模型回来的写进缓存。 */
class WordServiceFillTest {

    private WordMapper wordMapper;
    private DictService dictService;
    private DeepSeekClient deepSeek;
    private SettingService settingService;
    private LookupCacheMapper lookupCacheMapper;
    private WordService service;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wordMapper = mock(WordMapper.class);
        dictService = mock(DictService.class);
        deepSeek = mock(DeepSeekClient.class);
        settingService = mock(SettingService.class);
        lookupCacheMapper = mock(LookupCacheMapper.class);
        service = new WordService(wordMapper, dictService, deepSeek, settingService, lookupCacheMapper);
    }

    @Test
    void 词典里有的词不调模型() throws Exception {
        when(dictService.lookup("abandon")).thenReturn(Map.of("cn", "放弃"));

        Map<String, Object> out = service.fillMeanings(1L, List.of("abandon"));

        assertEquals(0, out.get("filled"));
        verify(deepSeek, never()).completeJson(anyString(), anyString(), any(Double.class), any());
    }

    @Test
    void 模型补的释义写进缓存() throws Exception {
        when(dictService.lookup(eq("hardline"))).thenReturn(null);
        when(settingService.effectiveDeepSeekKey(1L)).thenReturn("sk-test");
        JsonNode reply = json.readTree("[{\"word\":\"hardline\",\"phonetic\":\"\",\"pos\":\"adj.\",\"cn\":\"强硬的\"}]");
        when(deepSeek.completeJson(anyString(), anyString(), any(Double.class), any())).thenReturn(reply);

        Map<String, Object> out = service.fillMeanings(1L, List.of("hardline"));

        assertEquals(1, out.get("filled"));
        verify(lookupCacheMapper).save(eq(1L), eq("hardline"), eq("model"), anyString());
    }
}
