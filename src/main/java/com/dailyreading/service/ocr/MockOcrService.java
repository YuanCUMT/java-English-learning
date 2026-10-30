package com.dailyreading.service.ocr;

import com.dailyreading.config.OcrProperties;
import com.dailyreading.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 没配 Key 时用的假引擎：直接把配置里的一串词当成「识别结果」。
 * 作用是把「上传图片 -> 得到单词 -> 生成文章」整条链路先打通。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "mock", matchIfMissing = true)
public class MockOcrService implements OcrService {

    private final OcrProperties props;

    @Override
    public List<String> recognize(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("图片是空的");
        }
        return WordService.extract(props.getMockWords());
    }

    @Override
    public String engine() {
        return "mock（未配置真实 OCR）";
    }
}
