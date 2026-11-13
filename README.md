<h1 align="center">🍀 Get Jobs · Multi-Platform 增强版</h1>
<div align="center">

[![License](https://img.shields.io/badge/📑license-PolyForm--NC-34D058?labelColor=22863A&style=flat)](LICENSE)
[![Upstream](https://img.shields.io/badge/🌱fork_from-loks666%2Fget_jobs-0969DA?labelColor=24292F&style=flat)](https://github.com/loks666/get_jobs)

[![JDK](https://img.shields.io/badge/☕JDK-21-FF8C00?labelColor=C04000&style=flat)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot](https://img.shields.io/badge/🍃Spring%20Boot-3.5-6DB33F?labelColor=2E7D32&style=flat)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/🐘Gradle-9.7-26A69A?labelColor=00695C&style=flat)](https://gradle.org/)
[![Playwright](https://img.shields.io/badge/🎭Playwright-Java-E2574C?labelColor=A93226&style=flat)](https://playwright.dev/java/)
[![SQLite](https://img.shields.io/badge/🗃️SQLite-3-4FC3F7?labelColor=0277BD&style=flat)](https://www.sqlite.org/)
[![Next.js](https://img.shields.io/badge/⚡Next.js-16-B39DDB?labelColor=5E35B1&style=flat)](https://nextjs.org/)
[![React](https://img.shields.io/badge/⚛️React-19-61DAFB?labelColor=1E88E5&style=flat)](https://react.dev/)

</div>

> 本项目是 [loks666/get_jobs](https://github.com/loks666/get_jobs) 的增强 fork：在上游「Boss/猎聘 + AI 招呼语」的基础上，扩展为**七平台统一接入、平台级并发、全局黑名单**的全自动求职投递系统，并为新增平台提供通用关键词过滤。协议（PolyForm Noncommercial）与上游一脉相承，感谢原作者的开创工作。

需要從平台採集職位時，可安裝本機版。網頁版可獨立使用。兩者的清單可用同一種 JSON 備份轉移。

- **🌐 七平台覆盖**：Boss直聘、实习僧、智联招聘、猎聘、51job、JobsDB（香港）、OfferToday（香港），每平台独立配置页 + 投递分析看板。
- **🚀 平台级并发**：每个平台独立 Playwright 实例 + 持久化浏览器 profile + 独立执行线程，多平台同时投递互不阻塞；Cookie 任务前后自动注入与回写。
- **🧩 通用关键词过滤（新增平台）**：`标题必含 / 排除关键词` 双列表机制（`KeywordFilter`），实习僧 / OfferToday / JobsDB 三个新增平台使用；规则作为配置数据存在各平台配置页，**改规则不改代码**。Boss / 智联 / 猎聘保持上游原版过滤逻辑。
- **🤖 AI 智能打招呼**：接入 OpenAI 兼容接口，按岗位 JD 自动生成招呼语 / Cover Letter，可按平台开关，失败回退固定模板。
- **📷 图片简历**：Boss 直聘发送招呼语后自动追加图片简历，先发为敬。
- **🛡️ 多层过滤闸**：**全局黑名单**（公司/职位/招聘者，Boss + 实习僧 + OfferToday + JobsDB 共用，独立「全局配置」页管理）、HR 活跃度、公司规模、实习岗限定、单关键词投递上限，层层把关。
- **💬 可选重复沟通**：向已沟通 Boss 二次跟进的消息池，随机抽取发送，默认关闭，网页端自由编辑。
- **📊 投递分析看板**：每平台独立的采集/投递统计与图表；SSE 实时推送登录状态。
- **🔐 本地化数据**：SQLite 单文件存储，配置/登录态/投递记录全部本地；配置接口密码不回显（WRITE_ONLY）。

| 地區 | 平台 | 首版支援情況 |
| --- | --- | --- |
| 香港 | JobsDB | 職位搜尋、已有採集記錄匯入。預設只採集。SEEK 多步申請仍屬實驗功能，建議在原站完成。 |
| 香港 | OfferToday | 職位入口、已有採集記錄匯入。自動登入與發送尚待實帳驗證。 |
| 內地 | 實習僧、BOSS 直聘 | 職位入口、記錄匯入及原有本機任務。實習僧投遞需選中附件；BOSS 直聘需登入並受帳戶當日額度限制。 |
| 內地 | 智聯、獵聘、前程無憂 | 保留原有平台設定。首版未重新驗證這三個平台的完整投遞流程。 |

### 1️⃣ 拉取代码

```bash
git clone https://github.com/GF-creator-code/internbridge.git
cd get_jobs
```

### 2️⃣ 环境配置

- **JDK 21**（Windows 下 `start-local.ps1` 会自动在 `~/.gradle/jdks` 查找）
- 以 **Windows** 为主（启动脚本为 PowerShell；其他平台可 `gradlew bootRun` 自行启动）
- ⚠️ 投递国内平台时**关闭全局代理**，避免页面加载异常；JobsDB/OfferToday 为香港站，如遇 Cloudflare 验证请在弹出的浏览器窗口手动通过一次

### 3️⃣ 下载数据库文件（新用户必做）

本项目**没有数据库文件无法启动**（仓库不包含 db，避免把配置和登录态一起发布）：

1. 到上游 [release 页面](https://github.com/loks666/get_jobs/releases/latest) 下载 `getjobs.db.template`
2. 重命名为 `getjobs.db`，放到项目根目录 `db/` 下（最终路径 `db/getjobs.db`）
3. 首次启动会自动建表、补列，并从 `src/main/resources/defaults/page-config.json` 加载默认配置与过滤关键词种子

> ⚠️ 升级时不要覆盖已有的 `db/getjobs.db`，否则配置和登录态全部丢失。

### 4️⃣ 前端构建（首次）

```bash
cd front && npm install && npm run build:prod   # 产物自动复制到 src/main/resources/dist
```

### 5️⃣ 启动与配置

```powershell
powershell -File start-local.ps1     # 启动，端口 9527
```

浏览器打开 <http://127.0.0.1:9527>：

1. 各平台配置页填**关键词 / 城市 / 投递行为**
2. 登录：Boss / 实习僧 / 智联扫码或手机验证；JobsDB / OfferToday 为账号密码（存库，接口不回显）
3. 点「开始投递」；停止用「停止投递」按钮或 `POST /api/<platform>/stop`
4. AI 配置在网页端「AI 配置」页：OpenAI 兼容的 `BASE_URL / API_KEY / MODEL`，以及个人简历介绍（生成招呼语时参考）

### 6️⃣ 投递行为配置（重点）

每个平台配置页的「投递行为」区块：

| 配置 | 说明 |
|---|---|
| 标题必含关键词 | 仅实习僧 / OfferToday / JobsDB：岗位标题须包含其中之一才投递；**留空 = 不限制** |
| 排除关键词 | 标题或公司名命中即跳过（如 `法务, 律师, 腾讯, 字节`），与黑名单互补 |
| AI 开关 | 招呼语 / Cover Letter 按平台开关，关闭用固定模板 |
| 投递间隔 | 每两次投递的等待秒数 |
| 只采集不投递 | 仅搜索入库不投递（JobsDB），适合先观察岗位池 |

配置保存后**下次启动投递生效**。**全局黑名单**：侧边栏「全局配置」页管理，或 `/api/global/blacklist`（GET/POST/DELETE），类型支持公司/职位/招聘者；对 Boss / 实习僧 / OfferToday / JobsDB 同时生效。

### 各平台使用提示

| 平台 | 提示 |
|---|---|
| Boss直聘 | 扫码登录；单日打招呼上限约 150，跑满会拒绝新招呼；图片简历放 `resources` 下配置 |
| 实习僧 | 需在页面登录一次；投递必须选**附件简历**（网页可上传） |
| 智联招聘 | 需在 App/网页设置默认投递简历，否则投递失败；有投递上限 |
| 猎聘 | 默认打招呼无上限、主动发消息有上限；需绑定微信扫码 |
| 51job | 岗位与投递限制较多，谨慎使用 |
| JobsDB / OfferToday | 香港站；关键词建议英文（internship 等）；Quick apply 为多步表单 |

---

### 运行截图

> 截图为上游版本界面，本 fork 增加了实习僧 / JobsDB / OfferToday 等平台页，布局一致。

![运行日志.png](doc/imgs/%E8%BF%90%E8%A1%8C%E6%97%A5%E5%BF%97.png)
![环境变量配置.png](doc/imgs/%E7%8E%AF%E5%A2%83%E5%8F%98%E9%87%8F%E9%85%8D%E7%BD%AE.png)
![AI配置.png](doc/imgs/AI%E9%85%8D%E7%BD%AE.png)
![boss配置.png](doc/imgs/boss%E9%85%8D%E7%BD%AE.png)
![boss岗位分析.png](doc/imgs/boss%E5%B2%97%E4%BD%8D%E5%88%86%E6%9E%90.png)

## 🏗️ 架构速览

```
front/            Next.js 静态导出（配置页 + 分析看板，构建产物进 src/main/resources/dist）
src/main/java/com/getjobs/
├── application/  controller · service · entity · mapper（每平台同构：config + data 两表）
├── worker/       各平台投递 worker（boss/shixiseng/zhilian/liepin/51job/offertoday/jobsdb）
│   └── utils/    KeywordFilter（通用过滤）、PlaywrightUtil 等
└── worker/manager/
    ├── PlaywrightManager.java      共享浏览器：登录检测 / 扫码 / Cookie 生命周期
    └── PlatformTaskBrowser.java    任务浏览器：每平台独立实例 + 持久化 profile + 单线程，平台间并发
```

## 📑 更新日志（本 fork）

| 日期 | 内容 |
|---|---|
| 2026-11-14 | 黑名单升级为全局配置：`/api/global/blacklist` + 「全局配置」页，新增平台接入；种子补 JobsDB 只采集模式 |
| 2026-11-14 | 与上游原版全面对齐：fork 仅保留新增平台及适配规则，旧平台代码还原上游 |
| 2026-11-14 | 全面代码审计：修复实习僧分析页/cookie 平台参数两处 P0、配置接口密码脱敏、任务浏览器生命周期与容错 |
| 2026-11-14 | 岗位过滤通用化：include/exclude 关键词双列表替代全部硬编码规则，六平台统一接入 + 种子迁移 |
| 2026-11-13 | 新增 OfferToday/JobsDB 香港平台；平台级并发（PlatformTaskBrowser）；实习僧技术岗过滤与只采集模式 |
| 2026-11-10 | 新增实习僧平台、智联新站适配 |
| 上游 | [loks666/get_jobs](https://github.com/loks666/get_jobs) 的完整更新日志见原仓库 |

## 📑 开源协议

本项目继承上游 [PolyForm Noncommercial License 1.0.0](LICENSE)：允许个人学习与求职等**非商业**使用，禁止任何形式的商业使用；上游版权与商业授权归原作者（[联系原作者](https://github.com/loks666/get_jobs)）。

## ⚠️ 使用须知

- 请遵守各招聘平台的使用条款，控制投递频率（本项目内置投递间隔与单关键词上限），仅供学习交流
- 登录 Cookie、简历、数据库均存本地且已加入 `.gitignore`，请勿外传
- 各平台页面结构会随时间变化，选择器失效属正常现象，可按平台自行修正 Locators
- 本项目不支持服务器部署（招聘网站会对数据中心 IP 拒绝返回数据）
