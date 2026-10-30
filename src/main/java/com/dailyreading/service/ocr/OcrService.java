package com.dailyreading.service.ocr;

import java.util.List;

/**
 * 图片 -> 英文单词。实现可替换（mock / 百度云），上层不关心用的哪个。
 */
public interface OcrService {

    /**
     * @param image 图片字节
     * @return 识别到的词（已去重、小写），保持识别顺序
     */
    List<String> recognize(byte[] image);

    /** 给前端显示当前用的是哪个引擎 */
    String engine();
}
