package com.dailyreading.service.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按顺序试每个可用的 OCR 引擎，前一个失败就退到下一个。
 * 之前用 @ConditionalOnProperty 只装配一个，百度一挂就整条链路断掉；
 * 现在三个实现都装配进来，谁配好了谁就能兜底。
 */
@Slf4j
@Service
public class OcrResolver {

    private final MockOcrService mock;
    private final ObjectProvider<BaiduOcrService> baidu;
    private final ObjectProvider<TesseractOcrService> tesseract;
    private final String preferred;

    public OcrResolver(MockOcrService mock,
                       ObjectProvider<BaiduOcrService> baidu,
                       ObjectProvider<TesseractOcrService> tesseract,
                       @org.springframework.beans.factory.annotation.Value("${app.ocr.provider:mock}") String preferred) {
        this.mock = mock;
        this.baidu = baidu;
        this.tesseract = tesseract;
        this.preferred = preferred == null ? "mock" : preferred.trim().toLowerCase();
    }

    /** 当前首选引擎的名字，给页面显示用 */
    public String engine() {
        List<OcrService> chain = chain();
        return chain.isEmpty() ? "none" : chain.get(0).engine();
    }

    /** 每个引擎的状态，方便排查 */
    public List<Map<String, Object>> status() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OcrService s : chain()) {
            out.add(Map.of("engine", s.engine(), "available", s.available()));
        }
        return out;
    }

    /** 依次尝试，返回第一个成功的结果；全都失败时抛出最后一个异常 */
    public List<String> recognize(byte[] image) throws Exception {
        List<OcrService> chain = chain();
        Exception last = null;
        for (OcrService s : chain) {
            if (!s.available()) continue;
            try {
                List<String> words = s.recognize(image);
                if (words != null && !words.isEmpty()) {
                    if (s.engine() != chain.get(0).engine()) {
                        log.warn("OCR 降级：{} 失败，改用 {}", chain.get(0).engine(), s.engine());
                    }
                    return words;
                }
            } catch (Exception e) {
                last = e;
                log.warn("OCR {} 失败：{}", s.engine(), e.getMessage());
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("没有可用的 OCR 引擎，检查 app.ocr 配置。");
    }

    private List<OcrService> chain() {
        List<OcrService> list = new ArrayList<>();
        OcrService b = baidu.getIfAvailable();
        OcrService t = tesseract.getIfAvailable();
        if ("baidu".equals(preferred)) {
            if (b != null) list.add(b);
            if (t != null) list.add(t);
        } else if ("tesseract".equals(preferred)) {
            if (t != null) list.add(t);
            if (b != null) list.add(b);
        } else {
            if (b != null) list.add(b);
            if (t != null) list.add(t);
        }
        list.add(mock);
        return list;
    }
}
