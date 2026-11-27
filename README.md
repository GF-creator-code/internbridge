<h1 align="center">🍀 InternBridge · 本地投递工作台</h1>
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

面向同时找香港与内地职位的求职者。你可以用同一份技能介绍核对两地职位要求，在各平台查看岗位、投递进度和原站链接。InternBridge 在自己的电脑上运行。本项目基于 [loks666/get_jobs](https://github.com/loks666/get_jobs) 二次开发，沿用上游的 PolyForm Noncommercial 许可。

- **🌐 七平台覆盖**：Boss直聘、实习僧、智联招聘、猎聘、51job、JobsDB（香港）、OfferToday（香港），每平台独立配置页 + 投递分析标签页。
- **🚀 平台级并发**：每个平台独立 Playwright 实例 + 持久化浏览器 profile + 独立执行线程，多平台同时投递互不阻塞；Cookie 任务前后自动注入与回写。
- **🧩 通用关键词过滤（新增平台）**：`标题必含 / 排除关键词` 双列表机制（`KeywordFilter`），实习僧 / OfferToday / JobsDB 三个新增平台使用；规则作为配置数据存在各平台配置页，**改规则不改代码**。Boss 保持上游原版过滤逻辑。
- **🤖 AI 智能打招呼**：接入 OpenAI 兼容接口，按岗位 JD 自动生成打招呼语 / Cover Letter，可按平台开关，失败回退固定模板。
- **📷 图片简历**：Boss直聘 发送打招呼语后自动追加图片简历，先发为敬。
- **🛡️ 多层过滤闸**：**全局黑名单**（公司/职位/招聘者，Boss + 实习僧 + OfferToday + JobsDB 共用，在「设置中心 → 全局规则」维护）、HR 活跃度、公司规模、工作形式限定、单关键词投递上限，层层把关。
- **💬 可选重复沟通**：向已沟通 Boss 二次跟进的消息池，随机抽取发送，默认关闭，网页端自由编辑。
- **📊 数据看板与日报**：跨平台汇总采集、投递、进度与回复，每天固定时段生成日报；各平台另有独立的投递分析标签页，SSE 实时推送状态。
- **🔐 本地化数据**：SQLite 单文件存储，配置/登录态/投递记录全部本地；配置接口密码不回显（WRITE_ONLY）。

| 地区 | 从哪里开始 | 可完成的操作 |
| --- | --- | --- |
| 香港 | JobsDB、OfferToday | JobsDB 默认只采集，可在「投递分析」点职位前往原站。JobsDB 多步申请仍属实验功能，OfferToday 完整发送流程仍待实账验证。 |
| 内地 | 实习僧、Boss直聘 | 配置城市、关键词和简历，登录平台后运行任务。实习僧需在原站选择附件简历。另有智联、猎聘和 51job 的配置入口。 |

## 首次启动

Windows 需要 Git、JDK 21、Node.js 22.13 或更高版本，以及 Corepack 或 pnpm。配置好 `JAVA_HOME` 后，在 PowerShell 运行以下命令。

```powershell
git clone https://github.com/GF-creator-code/internbridge.git
cd internbridge
powershell -File .\start-local.ps1
```

首次启动会构建前端，并下载带有城市、职位等选项的上游数据库模板，校验后保存为 `db/getjobs.db`。已有数据库会直接使用。macOS、Linux 或需要手动准备数据库的用户可参见 [快速开始](docs/guide/quick-start.md) 和 [上游数据库模板](https://github.com/loks666/get_jobs/releases/tag/v2.0.1)。

打开 [本机投递工作台](http://127.0.0.1:9527/)。侧边栏按「工作台 / 内地 / 香港」分组，手机点击「导航」。想先试简历匹配，打开「设置中心 → 连接与通知」连接模型，再切到「简历与匹配」上传简历。想先看岗位，可在 JobsDB 保存搜索条件、登录后点击「开始采集」。

## 平台投递设置

每个平台配置页的「投递行为」区块：

| 配置 | 说明 |
|---|---|
| 标题必含关键词 | 仅实习僧 / OfferToday / JobsDB：岗位标题须包含其中之一才投递；**留空 = 不限制** |
| 排除关键词 | 标题或公司名命中即跳过（按自己的求职方向填写），与黑名单互补 |
| AI 开关 | 招呼语 / Cover Letter 按平台开关，关闭用固定模板 |
| 投递间隔 | 每两次投递的等待秒数 |
| 只采集不投递 | 仅搜索入库不投递（JobsDB），适合先观察岗位池 |

配置保存后**下次启动投递生效**。**全局黑名单**：在「设置中心 → 全局规则」维护，或 `/api/global/blacklist`（GET/POST/DELETE），类型支持公司/职位/招聘者；对 Boss / 实习僧 / OfferToday / JobsDB 同时生效。

### 各平台使用提示

| 平台 | 提示 |
|---|---|
| Boss直聘 | 扫码登录；单日打招呼上限约 150，跑满会拒绝新招呼；图片简历可在平台配置页上传 |
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

## 从简历提取技能和匹配岗位

在「设置中心 → 连接与通知」填写模型地址、密钥和模型名称。切到「简历与匹配」，上传文字版 PDF、DOCX 或 UTF-8 TXT（10 MB 以内）。简历文字会发送到当前配置的模型。每项技能附简历原文，勾选需要的项目并点击「加入并保存所选技能」，或选「先加入草稿」编辑后保存。启用 AI 的 Boss直聘、JobsDB 和 OfferToday 会读取已保存的技能介绍。

在同一页面粘贴职位描述，点击「分析岗位匹配」，即可查看有原文依据的匹配项及暂缺依据的要求。可用未保存的介绍试算。图片型 PDF 请先做 OCR 或上传有文字的版本。

## 🏗️ 架构速览

```
front/            Next.js 静态导出（设置中心 + 平台页 + 投递数据看板，构建产物进 src/main/resources/dist）
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
| 2026-11-26 | 统一设置中心与投递看板：4 个设置页合并为 `/settings` 四个分区，新增首页引导、任务进度面板、每日日报与有回复岗位看板；品牌统一为 InternBridge |
| 2026-11-26 | 修复 51job / 猎聘投递任务未注入页面导致 0 投递的问题；猎聘单关键词失败容错与分页等待放宽 |
| 2026-11-26 | 移除法律 / 猎头 / 腾讯 / 字节方向的硬编码筛选（个人定制需求），公司/岗位屏蔽统一走配置化的全局黑名单与排除关键词 |
| 2026-11-14 | 新增简历原文提取与岗位匹配，优化手机导航、香港及内地岗位筛选和首次启动 |
| 2026-11-14 | 黑名单升级为全局规则：`/api/global/blacklist` + 设置中心「全局规则」，新增平台接入；种子补 JobsDB 只采集模式 |
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
- 配置、登录状态和岗位记录保存在本机数据库。使用 AI 解析或匹配时，会将相应的简历文字、技能介绍和职位描述发送给配置的服务商。
- 各平台页面结构会随时间变化，选择器失效属正常现象，可按平台自行修正 Locators
- 本项目不支持服务器部署（招聘网站会对数据中心 IP 拒绝返回数据）
