package com.dailyreading.service.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按顺序试每个可用的 OCR 引擎，前一个失败就退到下一个。
 *
 * <p>三个实现都是无条件的 Spring Bean（不再用 @ConditionalOnProperty），
 * 否则 {@code provider: auto} 时百度和 Tesseract 根本不会被创建，ObjectProvider 永远是空的，
 * 自动降级就成了摆设。谁配好了（{@link OcrService#available()}）就由谁来干活。
 */
@Slf4j
@Service
public class OcrResolver {

    /** 一次识别的结果：单词 + 实际干活的引擎 + 是否发生了降级 */
    public record Result(List<String> words, String engine, boolean degraded) {}

    private final MockOcrService mock;
    private final BaiduOcrService baidu;
    private final TesseractOcrService tesseract;
    private final String preferred;

    public OcrResolver(MockOcrService mock,
                       BaiduOcrService baidu,
                       TesseractOcrService tesseract,
                       @Value("${app.ocr.provider:auto}") String preferred) {
        this.mock = mock;
        this.baidu = baidu;
        this.tesseract = tesseract;
        this.preferred = preferred == null ? "auto" : preferred.trim().toLowerCase();
    }

    /** 页面显示用：真正会先用到的那个引擎（跳过不可用的） */
    public String engine() {
        return firstAvailable(chain()).engine();
    }

    /** 每个引擎的状态，方便排查和写测试 */
    public List<Map<String, Object>> status() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OcrService s : chain()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("engine", s.engine());
            row.put("available", s.available());
            out.add(row);
        }
        return out;
    }

    /** 按当前配置的偏好排出来的完整链路（包含不可用的，用于展示） */
    public List<OcrService> chain() {
        List<OcrService> list = new ArrayList<>();
        if ("tesseract".equals(preferred)) {
            list.add(tesseract);
            list.add(baidu);
        } else if ("baidu".equals(preferred)) {
            list.add(baidu);
            list.add(tesseract);
        } else {
            // auto 及任何未知值：百度优先（识别质量最好），再本地离线，最后 mock
            list.add(baidu);
            list.add(tesseract);
        }
        list.add(mock);
        return list;
    }

    /** 依次尝试，返回第一个成功的结果；全都失败时抛出最后一个异常 */
    public Result recognize(byte[] image) throws Exception {
        List<OcrService> chain = chain();
        OcrService first = firstAvailable(chain);
        Exception last = null;
        for (OcrService s : chain) {
            if (!s.available()) continue;
            try {
                List<String> words = s.recognize(image);
                if (words != null && !words.isEmpty()) {
                    boolean degraded = s != first;
                    if (degraded) {
                        log.warn("OCR 降级：{} 没能出结果，改用 {}", first.engine(), s.engine());
                    }
                    return new Result(words, s.engine(), degraded);
                }
                last = new IllegalStateException(s.engine() + " 没认出任何单词");
                log.warn("OCR {} 没认出单词，试下一个", s.engine());
            } catch (Exception e) {
                last = e;
                log.warn("OCR {} 失败：{}", s.engine(), e.getMessage());
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("没有可用的 OCR 引擎，检查 app.ocr 配置。");
    }

    /** 链路里第一个 available 的引擎；全都不行时退到 mock（mock 永远可用） */
    private OcrService firstAvailable(List<OcrService> chain) {
        for (OcrService s : chain) {
            if (s.available()) return s;
        }
        return mock;
    }
}
