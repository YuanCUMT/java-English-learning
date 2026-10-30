package com.dailyreading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 配置。provider=mock 时用内置假数据，方便没 Key 时跑通整条链路。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    /** mock | baidu */
    private String provider = "mock";

    /** mock 模式下返回的词 */
    private String mockWords = "abandon,procedure,reluctant,cautious,vivid,witness,harvest,modest";

    private Baidu baidu = new Baidu();

    @Data
    public static class Baidu {
        private String apiKey = "";
        private String secretKey = "";
        /** true=高精度版（accurate_basic），false=标准版（general_basic） */
        private boolean accurate = true;
    }
}
