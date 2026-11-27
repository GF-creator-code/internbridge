# 维护与发布

## 每次更新的验证

「Build and test InternBridge」在 Windows 和 Linux 上构建 JAR 并运行 Java 测试。构建完成后，测试从空目录启动 JAR，检查自动建库、首页、设置中心、数据看板和 JobsDB 的默认只采集模式。浏览器测试通过模拟的简历和职位数据验证设置、移动端导航、筛选及导出。文档单独构建。

本地运行相同的检查。Windows 将 `./gradlew` 换成 `.\gradlew.bat`。

```sh
./gradlew -PrebuildFrontend '-PreleaseVersion=0.2.0' test bootJar
python bin/smoke-package.py build/libs/internbridge-0.2.0.jar
```

若本机已经安装 Google Chrome，可安装 `playwright==1.60.0`，再给检查命令加 `--browser-tests`。

## 生成发布产物

1. 在 GitHub Actions 手动运行「Build and test InternBridge」，给 `release_version` 填写与目标标签一致的版本号，例如 `0.2.0`。
2. Windows、Linux 和文档检查通过后，从该次运行的 Artifacts 下载 JAR 和 `.sha256`。在空目录用 Java 21 复核启动，再将同一组文件作为该标签的 Release 附件。
3. 发布说明链接到[快速上手](https://gf-creator-code.github.io/internbridge/guide/quick-start.html)，写明 Java 21、Chrome 的使用场景和对应的数据备份步骤。GitHub Pages 用于浏览文档，下载的 JAR 在本机打开工作台。

## 随包数据库模板

`src/main/resources/defaults/starter.sqlite` 沿用 v0.1.0 中的干净模板，包含 SQLite 表结构和平台公开选项。原模板可从[历史标签](https://github.com/GF-creator-code/internbridge/blob/0c719fd042d51d5e880a39f6ec627ac5c3b8ed25/src/main/resources/defaults/starter.sqlite)核对。

更新模板时确认配置、Cookie、简历和投递记录均为空，随后运行首次启动和重启测试。用户运行目录中的 `db/getjobs.db` 是使用数据，发布产物应使用仓库里的模板。
