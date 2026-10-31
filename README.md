# 每日阅读 · 考研英语（Java 结课作业）

把今天背的单词，写成一篇考研真题风格的阅读文章。

原版是 Node.js 写的（[bcz-reader](../bcz-reader)），这里是按结课作业要求重做的 **Java + Spring Boot** 版本。
需求定稿见 [REQUIREMENTS.md](REQUIREMENTS.md)。

## 当前进度

- [x] **M1** 工程骨架 + 数据库 + 本地词典
  - Spring Boot 3.3.5 + MyBatis + MySQL 8 + Thymeleaf
  - 9 张表（用户 / 单词 / 生词本 / 文章 / 用词 / 词典 / 词形 / 查词缓存 / OCR 缓存）
  - ECDICT 子集导入 MySQL：**21,339 词条 + 27,278 变形**
  - 词典查询接口跑通（含词形还原：`takes → take`）
- [x] **M2-1** 用户系统：注册 / 登录 / BCrypt / 拦截器 / 会话固定防护
- [x] **M2-2** 单词导入：粘贴导入 + 图片上传，OCR 可插拔（`mock` / `baidu` / `tesseract`）
- [x] **M2-3** 截图识别：**离线 Tesseract 已实测可用**（按字号筛词）
  - 百度云高精度版代码已写好、`access_token` 也能换到，但这个账号**实名认证没通过**，
    接口一直返回 `错误 18: Open api qps request limit reached`（配额是 0），所以默认走离线引擎。
  - 实名过了以后把 `app.ocr.provider` 改成 `baidu` 即可，代码不用动。
- [x] **M3** 文章生成 + 阅读查词
  - `POST /api/articles/generate` 调 DeepSeek（`response_format: json_object`），实测一篇 347 词约 16 秒
  - 两套提示词：复习模式（句子短、少从句）/ 真题模式（社科议论文、带让步—反驳、五类题型）
  - 阅读页客户端渲染：逐段英文 + 中文对照开关、目标词高亮、今日词汇卡、长难句、理解题（点选项才显示答案与原文定位）
  - 点任意单词查词（本地词典优先，查不到才问模型），浮层里可「朗读」「☆ 收藏」
- [x] **M4** 单词本 + 历史文章 + 快捷键 + 文档
  - `/vocab` 单词本：学习中 / 已掌握两摞，可朗读、标记已掌握、退回学习、删除、一键清空
  - `/archive` 历史文章：按日期倒序，点开任意一篇可重读（`/article/{id}`）
  - 阅读页快捷键：`1`–`4` 选答案、`c` 切中文、`Esc` 关浮层

## 文档

| 文档 | 内容 |
|---|---|
| [REQUIREMENTS.md](REQUIREMENTS.md) | 需求定稿：功能清单、数据库设计、接口设计、页面设计、里程碑 |
| [docs/er.md](docs/er.md) | ER 图（Mermaid，9 张表 + 关系 + 设计取舍） |
| [docs/api.md](docs/api.md) | 接口文档（16 个 API + 9 个页面路由，含真实返回示例） |

## 怎么跑

### 1. 准备数据库

```bash
# 建库建表（9 张表）
mysql -uroot -p < sql/init.sql

# 导入本地词典（会生成 2.6 MB 的 sql/dict-data.sql 再执行）
node tools/export-dict-sql.js
mysql -uroot -p --default-character-set=utf8mb4 < sql/dict-data.sql
```

> `sql/dict-data.sql` 是生成物，已在 `.gitignore` 里排除。
> 源数据是 `dict/ecdict.json.gz`（ECDICT 子集，MIT，见 [dict/README.md](dict/README.md)）。

### 2. 改配置

`src/main/resources/application-local.yml` 放自己真实的 Key（这个文件在 `.gitignore` 里，不进仓库）：

```yaml
app:
  deepseek:
    api-key: "sk-..."        # 文章生成要用
  ocr:
    provider: tesseract      # mock | baidu | tesseract
    baidu:
      api-key: "..."
      secret-key: "..."
      accurate: true
```

> `application-local.yml` 会被打进 jar，**改它要重新 `mvn package`** 才生效（不是只重启就行）。

`application.yml` 里的 `spring.datasource.username / password` 也改成自己的。

### 2.1 截图识别选哪个引擎

| provider | 要账号吗 | 效果 | 说明 |
|---|---|---|---|
| `tesseract` | 不要 | 够用 | 默认。只依赖 `tessdata/eng.traineddata`（3.9 MB，已随仓库提供） |
| `baidu` | 要 | 更好 | 通用文字识别·高精度版。**账号必须实名**，否则接口返回 `错误 18`（QPS 配额 0） |
| `mock` | 不要 | 假数据 | 单测 / 演示用，固定返回 abandon / harvest 之类 |

Tesseract 的做法是**按字号筛词**：单词卡上目标词最大，音标 / 释义 / 例句 / 水印都小一号，
所以按识别出的包围盒高度只留接近最大字号的那批，杂字自然被滤掉；字都差不多大时退回「按行挑短行」。
识别结果会进候选列表，可以自己勾选和改拼写，不用指望它 100% 干净。

### 3. 启动

```bash
mvn spring-boot:run
```

浏览器打开 <http://127.0.0.1:8080/api/health> 能看到 `{"code":0,...}` 就说明起来了。

## 目录结构

```
java-reader/
├─ pom.xml
├─ sql/
│  └─ init.sql                 9 张表的建表语句
├─ dict/
│  ├─ ecdict.json.gz           词典源数据（21,699 词 + 27,278 变形，0.8 MB）
│  └─ README.md                来源与抽取规则
├─ tools/
│  └─ export-dict-sql.js       把 gz 导成 SQL
└─ src/main/java/com/dailyreading/
   ├─ DailyReadingApplication.java
   ├─ controller/              接口层
   ├─ service/                 业务层
   ├─ mapper/                  MyBatis Mapper
   └─ entity/                  实体
```

## 已验证的接口

| 接口 | 说明 | 示例 |
|---|---|---|
| `GET /api/health` | 健康检查 | `{"code":0,"message":"ok"}` |
| `GET /api/dict/size` | 词条总数 | `{"data":{"entries":21339}}` |
| `GET /api/lookup?word=takes` | 查词（含词形还原） | 返回 `word=take, surface=takes, pos=vt., tags=[zk,gk,ielts]` |

## 环境

- JDK 21（`JAVA_HOME` 指过去；Spring Boot 3.x 对 JDK 25 支持还不完整，别用 PATH 上那个 25）
- Maven 3.9+
- MySQL 8.0（本项目在 8.0.29 上验证）
