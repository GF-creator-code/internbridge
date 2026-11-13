---
layout: home
# Public project homepage using the default VitePress home layout.

hero:
  name: "Get Jobs · Multi-Platform"
  text: "七平台统一的全自动求职投递"
  tagline: "把时间留给面试准备 · 平台级并发投递<br>通用关键词过滤 · AI 智能定制招呼语 · 精准企业黑名单"
  actions:
    - theme: brand
      text: 🚀 快速上手
      link: /guide/quick-start
    - theme: alt
      text: 🌱 上游原版项目
      link: https://github.com/loks666/get_jobs

features:
  - icon: 🌐
    title: 七平台覆盖
    details: Boss直聘、实习僧、智联招聘、猎聘、51job、JobsDB（香港）、OfferToday（香港），每平台独立配置页与投递分析看板。
  - icon: 🚀
    title: 平台级并发
    details: 每平台独立 Playwright 实例 + 持久化浏览器 profile + 独立执行线程，多平台同时投递互不阻塞，Cookie 自动注入回写。
  - icon: 🧩
    title: 通用关键词过滤
    details: 标题必含 / 排除关键词双列表机制，全平台共用一套代码，规则作为配置数据存在配置页，改规则不改代码。
  - icon: 🤖
    title: AI 智能打招呼
    details: 接入兼容 OpenAI 格式的大模型，基于个人简历与岗位 JD 动态撰写招呼语 / Cover Letter，可按平台开关。
  - icon: 🛡️
    title: 多层过滤闸
    details: 黑名单（公司/职位/招聘者）、HR 活跃度、公司规模、实习岗限定、单关键词投递上限，层层把关。
  - icon: 💾
    title: 本地数据持久化
    details: 基于轻量级 SQLite 存储配置、登录态与投递记录，全部保留在本机；配置接口密码不回显。
---

## 🖥️ 看得见的求职工作台

从配置到岗位分析，再到投递日志，在网页端完成日常操作。

![Get Jobs 运行日志](/images/运行日志.png)

[浏览全部界面截图 →](/guide/screenshots) · [了解完整功能 →](/guide/introduction)

::: warning 使用前先了解
本仓库为 [loks666/get_jobs](https://github.com/loks666/get_jobs) 的增强 fork，协议（PolyForm Noncommercial）继承上游：非商业使用免费，AI 接口费用由所选服务商收取。各招聘平台页面结构会随时间变化，选择器失效请按平台自行修正。
:::

## ⚡ 三步极简启动

```bash
# 1. 克隆项目仓库
git clone https://github.com/GF-creator-code/internbridge.git
cd get_jobs

# 2. 放置空数据库文件（从上游 Release 下载 getjobs.db.template 重命名为 getjobs.db）
mkdir -p db
# 将下载的 getjobs.db 放入 db/ 目录

# 3. 构建前端并启动（Windows）
cd front && npm install && npm run build:prod && cd ..
powershell -File start-local.ps1
```

::: tip 💡 提示
启动后访问 <http://127.0.0.1:9527>，在各平台配置页填关键词与投递行为、完成登录，即可开启全自动投递。详细步骤请查看 [快速上手教程](/guide/quick-start)。
:::

---

## 📑 文档说明

`docs/` 目录沿自上游项目，配置与部署流程通用；本 fork 的功能差异（多平台、并发、关键词过滤、香港站）以仓库根目录 [README](../README.md) 为准。
