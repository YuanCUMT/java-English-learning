package com.dailyreading.service.ocr;

import com.dailyreading.config.OcrProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 这一组测试盯的是「Bean 装配」本身：
 * 之前百度 / Tesseract 上挂着 @ConditionalOnProperty，provider=auto 时两个 Bean 都不会被创建，
 * ObjectProvider 永远拿到 null，所谓自动降级其实只剩 mock。
 * 所以这里用真实的组件扫描（@ComponentScan）而不是手动 new，一旦注解被加回去就会失败。
 */
class OcrResolverTest {

    @Configuration
    @ComponentScan(basePackages = "com.dailyreading.service.ocr")
    static class ScanConfig {
        @Bean
        OcrProperties ocrProperties() {
            return new OcrProperties();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(ScanConfig.class);

    @Test
    void 默认配置下三个引擎都会注册进来() {
        runner.withPropertyValues("app.ocr.provider=auto").run(ctx -> {
            assertNotNull(ctx.getBean(BaiduOcrService.class), "百度 Bean 没注册，自动降级无从谈起");
            assertNotNull(ctx.getBean(TesseractOcrService.class), "Tesseract Bean 没注册");
            assertNotNull(ctx.getBean(MockOcrService.class), "mock 兜底 Bean 没注册");

            OcrResolver resolver = ctx.getBean(OcrResolver.class);
            List<OcrService> chain = resolver.chain();
            assertEquals(3, chain.size(), "链路应该是 百度 -> Tesseract -> mock");
        });
    }

    @Test
    void 状态列表能反映每个引擎是否可用() {
        runner.withPropertyValues("app.ocr.provider=auto").run(ctx -> {
            OcrResolver resolver = ctx.getBean(OcrResolver.class);
            List<Map<String, Object>> status = resolver.status();
            assertEquals(3, status.size());
            for (Map<String, Object> row : status) {
                assertNotNull(row.get("engine"));
                assertNotNull(row.get("available"), "每个引擎都要报 available，页面显示的引擎才不会骗人");
            }
            // 没配 Key、也没放 tessdata 时，说得出话的只有 mock
            boolean baiduAvailable = (Boolean) status.get(0).get("available");
            String engine = resolver.engine();
            assertNotNull(engine);
            if (!baiduAvailable) {
                assertFalse("baidu 高精度版".equals(engine), "百度不可用时报出来的引擎不能是百度");
            }
        });
    }

    @Test
    void provider_tesseract_时链路会把离线引擎排前面() {
        runner.withPropertyValues("app.ocr.provider=tesseract").run(ctx -> {
            List<OcrService> chain = ctx.getBean(OcrResolver.class).chain();
            assertEquals("tesseract（离线，不用账号）", chain.get(0).engine());
            assertEquals(3, chain.size());
        });
    }

    @Test
    void mock_永远排在最后当兜底() {
        runner.withPropertyValues("app.ocr.provider=auto").run(ctx -> {
            List<OcrService> chain = ctx.getBean(OcrResolver.class).chain();
            OcrService last = chain.get(chain.size() - 1);
            assertTrue(last instanceof MockOcrService, "最后一道兜底必须是 mock");
            assertTrue(last.available(), "mock 必须永远可用");
        });
    }

    @Test
    void provider为mock时链路只有mock() {
        runner.withPropertyValues("app.ocr.provider=mock").run(ctx -> {
            OcrResolver resolver = ctx.getBean(OcrResolver.class);
            List<OcrService> chain = resolver.chain();
            assertEquals(1, chain.size(), "显式配成 mock 就该只跑 mock，不能先去试百度");
            assertEquals(resolver.engine(), chain.get(0).engine());
            assertTrue(chain.get(0).engine().toLowerCase().contains("mock"));
        });
    }

    @Test
    void 只有显式配置成mock才算explicitlyMock() {
        runner.withPropertyValues("app.ocr.provider=mock").run(ctx ->
                assertTrue(ctx.getBean(OcrResolver.class).explicitlyMock(), "显式 mock 要能被识别，否则还会去读缓存"));
        runner.withPropertyValues("app.ocr.provider=auto").run(ctx ->
                assertFalse(ctx.getBean(OcrResolver.class).explicitlyMock(), "auto 不算显式 mock"));
    }
}
