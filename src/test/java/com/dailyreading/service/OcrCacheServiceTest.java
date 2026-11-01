package com.dailyreading.service;

import com.dailyreading.mapper.OcrCacheMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OCR 缓存：命中、损坏、空输入三种情况都要「不炸」，因为缓存是纯优化，
 * 坏了只该退回重新识别，不能把导入流程带崩。
 */
class OcrCacheServiceTest {

    private OcrCacheMapper mapper;
    private OcrCacheService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OcrCacheMapper.class);
        service = new OcrCacheService(mapper, new ObjectMapper());
    }

    @Test
    void 命中缓存时返回词和当时用的引擎() {
        when(mapper.findPayload("abc")).thenReturn("{\"words\":[\"abandon\",\"harvest\"],\"engine\":\"baidu 高精度版\"}");

        OcrCacheService.Cached c = service.get("abc");

        assertNotNull(c);
        assertEquals(List.of("abandon", "harvest"), c.words());
        assertEquals("baidu 高精度版", c.engine());
    }

    @Test
    void 缓存内容是坏JSON时当作没有缓存() {
        when(mapper.findPayload("bad")).thenReturn("{这不是 json");

        assertNull(service.get("bad"));
    }

    @Test
    void 缓存里词表为空时当作没有缓存() {
        when(mapper.findPayload("empty")).thenReturn("{\"words\":[],\"engine\":\"baidu\"}");

        assertNull(service.get("empty"));
    }

    @Test
    void sha1为空串时不查数据库() {
        assertNull(service.get(""));

        verify(mapper, never()).findPayload(anyString());
    }

    @Test
    void 写入时会带上引擎名() {
        service.put("abc", List.of("ledger"), "tesseract（离线，不用账号）");

        verify(mapper).save(eq("abc"), contains("tesseract"));
    }

    @Test
    void 词表为空时不写缓存() {
        service.put("abc", List.of(), "baidu");

        verify(mapper, never()).save(anyString(), any());
    }

    @Test
    void 同一张图算出的sha1稳定且为40位() {
        byte[] bytes = "hello".getBytes();

        String a = OcrCacheService.sha1(bytes);
        String b = OcrCacheService.sha1("hello".getBytes());

        assertEquals(a, b);
        assertEquals(40, a.length());
    }
}
