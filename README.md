# 每日阅读

这是 Java 语言及网络编程课程项目。可以导入单词、阅读英语文章、查词收藏和做理解题。项目使用 Spring Boot、MyBatis、Thymeleaf 和 MySQL，附带独立的 H2 体验模式。

## 从 GitHub 源码启动

在 Windows 上安装 JDK 21 和 Maven 3.9 后，在仓库根目录打开 PowerShell，执行：

```powershell
mvn -B package
if ($LASTEXITCODE -ne 0) { throw '构建失败' }
New-Item -ItemType Directory -Force app | Out-Null
Copy-Item target/daily-reading-0.1.0.jar app/
.\启动.cmd
```

GitHub 源码不附带 JAR、Java 运行时、Maven 或课程报告；下面的免安装步骤仅适用于课程提交包。

## 从课程提交包快速体验

1. 完整解压压缩包，进入「项目源码」，双击 **启动.cmd**。
2. 等启动窗口显示访问地址。默认端口为 8080，被占用时会尝试 8081–8099。浏览器没有弹出时，手动打开窗口给出的地址。
3. 登录页点击「直接体验示例阅读」，进入首页后在首页使用指南点「体验示例阅读」，即可展开译文、查词收藏和答题。
4. 用完双击 **停止.cmd**。启动窗口按任意键只会关闭窗口。

随包提供 Windows x64 Java 运行时。体验模式无需另装 Java、Maven、MySQL 或配置 API Key。首次启动会自动建库、导入词典，并生成 `data/`，存放体验数据库、密钥和日志。交作业时使用原始 ZIP。

固定示例可离线试读。生成新文章、AI 查词和 Key 连通性测试需要自己的 DeepSeek Key，可能产生费用。离线 OCR 使用 Tesseract，可以在保存前校对候选词。

## 使用 MySQL

启动自己的 MySQL 8.0 服务。首次使用可在项目目录导入建表脚本和词典。

```bat
mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/dict-data.sql
```

脚本可重复执行。已有表结构不兼容时应先备份并检查字段。复制 `application-local.yml.example` 为 `application-local.yml`，填写自己的连接配置，然后双击 **启动MySQL.cmd**。两种模式的账号和学习记录分别保存。迁移时需配套备份数据库与 Key 加密密钥。

## 源码与测试

源码的目标版本为 Java 21。随包运行时用于启动，重新编译需要完整 JDK。

```bat
tools\apache-maven-3.9.9\bin\mvn.cmd -B test
tools\apache-maven-3.9.9\bin\mvn.cmd -B package
```

修改后先停止应用，再把 `target/daily-reading-0.1.0.jar` 复制到 `app/`。启动脚本从 `app/` 读取 JAR。

- 2026 年 11 月 15 日自动化回归记录见 `docs/verification/20261115-ui/`。127 项中 108 项通过，19 项隔离 MySQL 用例跳过，失败和错误均为 0。需要补跑 MySQL 用例时，仅将 `MYSQL_TEST_URL` 指向专用测试库。
- 2026 年 11 月 19 日调整界面后复跑同一套测试，结果相同。结果 XML 见 `docs/verification/20261119/`。
- 2026 年 11 月 19 日的界面验证和截图见实验报告第九、十章。首页和阅读区共用暖米白、象牙白与栗棕色主题。

实验报告在压缩包根目录。接口和数据库设计见 `docs/api.md` 与 `docs/er.md`，操作说明见 `docs/使用说明.md`。阅读排版和词汇状态的设计参考 [Readest](https://github.com/readest/readest/blob/main/apps/readest-app/DESIGN.md) 与 [LinguaCafe](https://github.com/simjanos-dev/LinguaCafe/wiki/3.-Usage-and-features)。
