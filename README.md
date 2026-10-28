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
- [ ] M2 登录注册 + 截图识别（云 OCR + 词库白名单过滤）
- [ ] M3 文章生成 + 阅读查词
- [ ] M4 单词本 + 快捷键 + 文档

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

`src/main/resources/application.yml` 里的 `spring.datasource.username / password` 改成自己的。

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
