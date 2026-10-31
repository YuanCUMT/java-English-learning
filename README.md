# 每日阅读 · 考研英语

这是我 Java 课的结课作业。

起因很简单：我每天用百词斩背单词，背完就只剩一堆孤零零的词，没有语境。我想要的是——**每天读一篇用我当天背的那些词写成的文章**，顺便当考研阅读练手。

先说明：我先用 Node.js 写过一个一模一样的版本，跑起来是能用的；这学期要求用 Java，所以我把整套东西用 **Spring Boot + MyBatis + Thymeleaf + MySQL** 重做了一遍。重做的时候顺手把原来不满意的地方也改了，比如查词从"一律问模型"改成"本地词典优先"。

需求文档在 [REQUIREMENTS.md](REQUIREMENTS.md)，数据库设计在 [docs/er.md](docs/er.md)，接口文档在 [docs/api.md](docs/api.md)。

## 它现在能干什么

一条完整的链路，从截图到读完做题：

1. **导入单词** —— 截图**拖进面板**、**Ctrl+V 粘贴**、或点「选图识别」选文件，OCR 认字；也可以直接粘贴一段词表
2. **生成文章** —— 调 DeepSeek 写成考研阅读的风格，两种模式（复习 / 真题）
3. **读** —— 逐段英文，想看图中文就开；当天背的词在文里高亮
4. **查词** —— 读到生词点一下，浮层出释义，一键收藏进生词本
5. **做题** —— 五道理解题，点选项才显示答案和原文定位；键盘 `1`–`4` 就能选
6. **复习** —— 单词本分"学习中 / 已掌握"两摞，历史文章按天存着，随时能重读

## 做到哪一步了

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 工程骨架 + 9 张表 + ECDICT 本地词典导入 | ✅ |
| M2-1 | 注册 / 登录 / BCrypt / 拦截器 | ✅ |
| M2-2 | 单词导入（粘贴 + 图片上传，OCR 可插拔） | ✅ |
| M2-3 | 截图识别（百度云 / 离线 Tesseract） | ✅ |
| M3 | 文章生成 + 阅读查词 | ✅ |
| M4 | 单词本 / 历史文章 / 快捷键 / 文档 | ✅ |

几个具体数字，都是我实际跑出来的：

- 本地词典 **21,339 词条 + 27,278 个变形**，从 ECDICT 里抽的（MIT，见 [dict/README.md](dict/README.md)）
- 生成一篇 **347 词**的文章，DeepSeek 那边大概 **16 秒**
- 一张单词表截图，百度 OCR 能干净地认出 **6 个词**，没有杂字

## 怎么跑起来

### 1. 建库建表

```bash
mysql -uroot -p < sql/init.sql
```

9 张表，全 utf8mb4。表结构和设计思路在 [docs/er.md](docs/er.md)。

### 2. 导入词典

```bash
# 把 dict/ecdict.json.gz 导成 SQL（约 2.6 MB）
node tools/export-dict-sql.js
mysql -uroot -p --default-character-set=utf8mb4 < sql/dict-data.sql
```

> `sql/dict-data.sql` 是生成物，没进仓库。源数据是 `dict/ecdict.json.gz`。

### 3. 填自己的 Key

新建 `src/main/resources/application-local.yml`（这个文件在 `.gitignore` 里，不会进仓库）：

```yaml
app:
  deepseek:
    api-key: "sk-..."        # 生成文章要用，去 platform.deepseek.com 申请
  ocr:
    provider: baidu          # mock | baidu | tesseract
    baidu:
      api-key: "..."
      secret-key: "..."
      accurate: true
```

再改 `application.yml` 里的 `spring.datasource.username / password`。

> 注意：`application-local.yml` 会被打进 jar，**改完要重新 `mvn package`**，光重启不生效。我第一次就被这个坑了。

### 4. 启动

```bash
mvn spring-boot:run
```

浏览器开 <http://127.0.0.1:8080>，注册个账号就能用了。

## 截图识别用哪个引擎

我做了两个引擎，各有各的用武之地，改一行配置就能换：

| provider | 要账号吗 | 我用它识别什么 |
|---|---|---|
| `baidu` | 要 | 默认。**单词表截图**（一屏好几个词）——逐行识别，字准、快 |
| `tesseract` | 不要 | **单词卡截图**（一个大词 + 例句）——按字号筛词，不会把例句里的 the / this 捞进来 |
| `mock` | 不要 | 单测和演示用，固定返回几个词 |

为什么不能只留一个？因为我两种截图都会传：

- 百度是**整张图逐行识别、把所有英文词都抽出来**。传单词表很干净；传单词卡就糟了，例句里的 `the`、`this`、`to`、`was` 全被当成生词捞进来。
- Tesseract 是**按框的高度筛**：单词卡上目标词字号最大，音标、释义、例句都小一号，只留接近最大字号的那批，杂字自然被滤掉。

所以我的用法是：**单词表用百度，单词卡用 Tesseract**。两边识别结果都会进候选列表，可以自己勾选、改拼写，不用指望 100% 干净。

## 踩过的坑

写这份作业踩了不少坑，记下来免得以后忘：

**1. `${DB_USERNAME:root}` 里的中文密码炸了**
早期用 `characterEncoding=utf8mb4` 连 MySQL，启动直接报 `UnsupportedEncodingException: utf8mb4` —— JDBC 要的是 `utf8`，`utf8mb4` 是 MySQL 的字符集名，不是 Java 的编码名。

**2. Lombok 突然不生效**
编译报一堆"找不到 getXxx/setXxx"。不是注解没写，是**这台机器的 JDK 21 默认不跑 classpath 上的注解处理器**了。要在 `maven-compiler-plugin` 里显式写 `<proc>full</proc>`，再加上 lombok 的 `annotationProcessorPaths`。

**3. application.yml 重复的顶层键**
我用脚本往文件末尾追配置，结果 `app:` 出现了两次，SnakeYAML 直接拒绝启动。改完才知道 YAML 不允许重复键，报错信息还很不明显。

**4. 百度 OCR 一直返回错误 18**
`Open api qps request limit reached`。以为是调用太快，间隔 10 秒重试还是一样。最后才想明白：**账号没实名，配额就是 0**，跟速度无关。实名通过当天就正常了。

**5. 阅读页整页空白**
`article.html` 里 `#articleBox` 把 `#articleBody` 整个包住了，脚本里 `box.hidden = true` 一执行，标题和正文一起消失。查了半小时才发现是嵌套关系写错。

**6. 快捷键按了没反应**
`1`–`4` 选答案的代码写的是 `querySelectorAll('.opt')`，而模板里选项的类名是 `.q-opt`。一个类名对不上，功能就整个是死的。

## 目录结构

```
java-reader/
├─ sql/init.sql                9 张表
├─ dict/ecdict.json.gz         词典源数据（21,699 词 + 27,278 变形，0.8 MB）
├─ tessdata/eng.traineddata    离线 OCR 的英文模型（3.9 MB）
├─ tools/export-dict-sql.js    把 gz 导成 SQL
└─ src/main/
   ├─ java/com/dailyreading/
   │  ├─ controller/           接口层（8 个 Controller）
   │  ├─ service/              业务层（含 ocr/ 下三个引擎实现）
   │  ├─ mapper/               MyBatis Mapper
   │  ├─ entity/               实体
   │  └─ config/               配置类 + 登录拦截器
   └─ resources/
      ├─ templates/            Thymeleaf 页面（首页/登录/注册/单词本/历史/阅读）
      └─ static/               CSS + JS
```

## 环境

- **JDK 21**（`JAVA_HOME` 要指过去。我机器上 PATH 里的 java 是 25，Spring Boot 3.x 对它支持还不完整，踩过）
- Maven 3.9+
- MySQL 8.0（我在 8.0.29 上测的）
