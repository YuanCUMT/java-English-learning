# 评测与测试对应

2026-11-13 最终执行：121 项，失败 0、错误 0、跳过 0。下面按实际 JUnit XML 汇总，不把手工项算进单元测试覆盖。

| 测试套件 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| ApiExceptionHandlerTest | 3 | 0 | 0 | 0 |
| DeliveryIntegrationTest | 17 | 0 | 0 | 0 |
| MysqlDeliveryIntegrationTest | 17 | 0 | 0 | 0 |
| ArticleGenerationRegressionTest | 7 | 0 | 0 | 0 |
| ArticleValidatorTest | 10 | 0 | 0 | 0 |
| DictServiceTest | 4 | 0 | 0 | 0 |
| LoginAttemptServiceTest | 7 | 0 | 0 | 0 |
| OcrResolverTest | 6 | 0 | 0 | 0 |
| OcrCacheServiceTest | 7 | 0 | 0 | 0 |
| SecretBoxRecoveryTest | 4 | 0 | 0 | 0 |
| SecretBoxTest | 7 | 0 | 0 | 0 |
| SettingServiceTest | 9 | 0 | 0 | 0 |
| TargetWordMatcherTest | 13 | 0 | 0 | 0 |
| WordFilterTest | 6 | 0 | 0 | 0 |
| WordServiceFillTest | 2 | 0 | 0 | 0 |
| WordServiceHistoryTest | 2 | 0 | 0 | 0 |

## 可判定验收

| 要求 | 自动化证据 |
|---|---|
| 覆盖率向上取整、去重、真题短词表 | TargetWordMatcherTest |
| 独立词形、takeover 反例、短语边界、批量查询 | TargetWordMatcherTest；两库 DeliveryIntegrationTest |
| 结构缺项、不合法选项、引用不在正文、失败不触碰存储 | ArticleValidatorTest、ArticleGenerationRegressionTest |
| 密钥损坏不覆盖、并发初始化共用密钥 | SecretBoxRecoveryTest |
| CSRF、JSON/日期/图片、登录会话更换 | 两库 DeliveryIntegrationTest |
| 跨用户读写、重复初始化保留数据、替换事务回滚 | 两库 DeliveryIntegrationTest，真实数据库 |
| 示例载入幂等、无账号体验入口 | 两库 DeliveryIntegrationTest |

## 人工与尚未执行项目

人工浏览器已检查：免安装入口、阅读、译文、本地查词、收藏、答题解析、单词本、设置页；记录见 acceptance。
未执行：真实 DeepSeek 文章质量、百度云 OCR、Key 连通性、真实外部服务耗时、多用户并发压测。不能从自动化桩测试推断这些项目通过。

常规：项目根目录 tools/apache-maven-3.9.9/bin/mvn.cmd -B test。MySQL 套件需 MYSQL_TEST_URL 指向专用隔离实例，未设置则跳过。不得把测试变量指向已有个人业务库。

离线 OCR 补充：用合成清晰词表图实际识别出六个词，返回 tesseract 且未降级。仅证明该样本可识别，不代表截图总体准确率。输入与返回见 ../verification/ocr-input.png、offline-ocr.json；未调用云服务。

### UI 增补评测（2026-11-15）

手工界面条目：浅色登录、浅色首页、浅／深色阅读、390×844 首页／阅读／注册、密码显示、译文展开、正确／错误答题反馈、示例载入。布局与交互结论仅适用于本次浏览器检查的视口，不能视为全部设备兼容性保证。新增 `CsrfProtectionTest` 的 2 项：过期 HTML 表单可恢复且不回显输入；API 即使接受 HTML 仍返回 JSON。证据目录 `../verification/20261115-ui/`，汇总 `../verification/ui-20261115.json`。
