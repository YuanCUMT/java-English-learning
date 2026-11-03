# 评测集（Eval Set）

> 每个规约条目对应一条或多条**可执行的检查**。
> 「自动化」列写 `mvn test` 里能跑的用例；`手工` 表示必须人工点一遍并记录结果。

统计口径：当前 **54 个单元测试**，覆盖下表 28 条中的 26 条；剩余 2 条（PPT 演示、移动端）为人工验收。

---

## A. 账号与权限

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| AUTH-01 | 未登录访问 `/` | 302 到 `/login` | 手工 |
| AUTH-02 | 正确密码登录 | 302 到 `/`，会话 Cookie `DR_SESSION` 存在 | 手工 |
| AUTH-03 | 错误密码 5 次 | 第 6 次提示「失败次数太多，请 N 分钟后再试」，正确密码也被挡 | `LoginAttemptServiceTest` |
| AUTH-04 | 退出 | 只接受 POST；GET `/logout` 仅跳首页不登出 | 手工 |
| AUTH-05 | 跨用户访问 `/api/marks/{id}` | 404（不是别人的数据） | 手工 |

## B. 单词导入（OCR）

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| OCR-01 | 百度 Key 未配置、tessdata 存在 | `engine` 为 tesseract，`degraded=true` | `OcrResolverTest` |
| OCR-02 | 全部引擎不可用 | 降级到 mock，前端出现「模拟数据」提示 | `OcrResolverTest` |
| OCR-03 | `provider: mock` | 链路只有 mock，且结果**不写** `ocr_cache` | `OcrResolverTest` + `OcrCacheServiceTest` |
| OCR-04 | 同一张图识别两次 | 第二次 `cached=true`，耗时显著下降 | 手工（实测 697 ms → 14 ms） |
| OCR-05 | `save=false` | 只返回 `{words,total,engine,cached}`，不入库 | 手工 |
| OCR-06 | 候选词删到 0 再保存 | 提示「还没有可保存的词」，不发请求 | 手工 |
| OCR-07 | 重复词导入 | upsert，不产生重复行，返回 `added/skipped` | 手工 |

## C. 文章生成

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| ART-01 | 段落数不足 | 抛 `IllegalStateException`，前端显示可读原因 | `ArticleValidatorTest` |
| ART-02 | 选项没有 A/B/C/D 前缀 | 拒绝 | `ArticleValidatorTest` |
| ART-03 | 答案不在 A–D | 拒绝 | `ArticleValidatorTest` |
| ART-04 | 目标词一个都没用到 | 抛「生成的文章里一个目标词都没用上」 | `ArticleValidatorTest` |
| ART-05 | 目标词词形变化 | `takes/taking/taken` 都算命中 | `ArticleValidatorTest` |
| ART-06 | 生成成功 | `articles` + `article_words` 同事务写入 | 手工 |
| ART-07 | 难度选择 | B2/C1/C2 影响句子长度（提示词内不同数值） | 手工 |
| ART-08 | 未配置 Key | 返回明确提示「点右上角设置填一个」 | 手工 |

## D. 查词

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| DICT-01 | 查 `ledger` | 命中内置词典，返回音标/词性/释义 | `DictServiceTest` |
| DICT-02 | 查 `takes` | 返回 `word=take surface=takes` | `DictServiceTest` |
| DICT-03 | 查不存在的词 | `code=404, canAskModel=true` | `DictServiceTest` |
| DICT-04 | 加 `ai=1` | 调模型并写 `lookup_cache`，`source=model` | 手工 |
| DICT-05 | 补全释义 | 词典已有的词不调模型；缺的批量补，写缓存 | `WordServiceFillTest` |
| DICT-06 | 同一词第二次查 | 命中缓存，不重复花钱 | 手工 |

## E. 单词本与生词

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| VOC-01 | 每日单词按天分组 | 当天词数正确，按日期倒序 | `WordServiceHistoryTest` |
| VOC-02 | 历史查询次数 | 只调 1 次 `listAllByUser` + 1 次 `lookupBatch`（无 N+1） | `WordServiceHistoryTest` |
| VOC-03 | 三栏搜索 | 每日/生词/短语都能搜，无匹配有对应文案 | 手工 |
| VOC-04 | 排序 | 时间新→旧 / 旧→新 / 字母，三栏都生效 | 手工 |
| VOC-05 | 生词状态 | 「已掌握」后从学习中消失，计数同步 | 手工 |

## F. 设置与安全

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| SEC-01 | Key 落库 | 数据库里是 `enc:v1:...` 密文 | `SecretBoxTest` |
| SEC-02 | 页面回显 | 只显示掩码 `sk-abcd****6789` | `SettingServiceTest` |
| SEC-03 | Key 优先级 | 用户填的 > `application-local.yml`；清空后回落到配置文件 | `SettingServiceTest` |
| SEC-04 | 请求体不是 JSON | 返回 `code=415` 可读提示，不是异常类名 | `ApiExceptionHandlerTest` |

## G. 界面与文档

| 编号 | 检查项 | 期望 | 自动化 |
|---|---|---|---|
| UI-01 | 段落序号 | 只有一处编号，无重影 | 手工（CDP 检查 `::before` 为 none） |
| UI-02 | 窄屏 390 px | 导航一行、卡片单列、无横向滚动 | 手工（截图 `docs/img/mobile-*.png`） |
| UI-03 | 文档一致性 | 表数 10、接口 21、单测 54 与实现一致 | 人工核对 |

---

## 回归方式

```powershell
cd D:\dsh-routine\java-reader
# 停掉运行中的服务（否则打包失败）
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*daily-reading*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

.\tools\apache-maven-3.9.9\bin\mvn.cmd -B test
```

最近一次结果：**Tests run: 54, Failures: 0, Errors: 0, Skipped: 0**。
