package com.dailyreading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 配置。provider 只决定「优先试哪个」，失败会自动降级到下一个能用的引擎。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    /** auto | mock | baidu | tesseract（auto = 百度 > tesseract > mock 自动降级） */
    private String provider = "auto";

    /** mock 模式下返回的词 */
    private String mockWords = "abandon,procedure,reluctant,cautious,vivid,witness,harvest,modest";

    /** tesseract 模式下 tessdata 目录（放 eng.traineddata） */
    private String tessdata = "tessdata";

    /** tesseract 语言包，可写 eng 或 eng+chi_sim */
    private String lang = "eng";

    private Baidu baidu = new Baidu();

    /** 百度这对 Key 有没有填 */
    public boolean configured() {
        return baidu != null
                && baidu.getApiKey() != null && !baidu.getApiKey().isBlank()
                && baidu.getSecretKey() != null && !baidu.getSecretKey().isBlank();
    }

    @Data
    public static class Baidu {
        private String apiKey = "";
        private String secretKey = "";
        /** true=高精度版（accurate_basic），false=标准版（general_basic） */
        private boolean accurate = true;
    }
}
