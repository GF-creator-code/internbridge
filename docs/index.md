---
layout: home
# Public project homepage using the default VitePress home layout.

hero:
  name: "InternBridge"
  text: "香港与内地职位投递管理"
  tagline: "一份求职档案管七个平台，统一查看采集、投递、进度与回复。"
  actions:
    - theme: brand
      text: 🚀 快速上手
      link: /guide/quick-start
    - theme: alt
      text: 下载本机版
      link: https://github.com/GF-creator-code/internbridge/releases

features:
  - icon: 🧭
    title: 统一设置中心
    details: 连接与通知、求职档案、全局规则、简历与匹配四个分区一次填完；每份数据只有一个编辑位置，不再多处重复填写。
  - icon: 📊
    title: 数据看板与每日日报
    details: 跨平台汇总采集、投递、打招呼与回复，列出有回复的岗位；每天固定时段自动生成日报并推送。
  - icon: 🌐
    title: 七平台覆盖
    details: Boss直聘、实习僧、智联招聘、猎聘、51job、JobsDB（香港）、OfferToday（香港），每平台独立配置页与投递分析标签页。
  - icon: 🚀
    title: 平台级并发
    details: 每平台独立 Playwright 实例 + 持久化浏览器 profile + 独立执行线程，多平台同时投递互不阻塞，Cookie 自动注入回写。
  - icon: 🧩
    title: 通用关键词过滤
    details: 标题必含 / 排除关键词双列表机制，实习僧、JobsDB 和 OfferToday 共用，规则作为配置数据存在配置页，改规则不改代码。
  - icon: 🤖
    title: AI 智能打招呼
    details: 用配置的模型提取简历原文依据，对照职位描述，保存技能介绍供平台投递使用。
  - icon: 🛡️
    title: 多层过滤闸
    details: 黑名单（公司/职位/招聘者）、HR 活跃度、公司规模、工作形式限定、单关键词投递上限，层层把关。
  - icon: 💾
    title: 本地数据持久化
    details: 基于轻量级 SQLite 存储配置、登录态与投递记录，全部保留在本机；配置接口密钥不回显。
---

## 🖥️ 看得见的求职工作台

从设置中心到平台投递，再到数据看板复盘，都在本机网页端完成。

![InternBridge 运行日志](/images/运行日志.png)

[浏览全部界面截图 →](/guide/screenshots) · [了解完整功能 →](/guide/introduction)

::: warning 使用前先了解
本仓库为 [loks666/get_jobs](https://github.com/loks666/get_jobs) 的增强 fork，协议（PolyForm Noncommercial）继承上游：非商业使用免费，AI 接口费用由所选服务商收取。各招聘平台页面结构会随时间变化，选择器失效请按平台自行修正。
:::

## 启动与第一次使用

本站是 InternBridge 的使用文档。安装 Java 21 后可运行下载版 JAR。从源码启动还需 JDK 21、Node.js 和 pnpm 或 Corepack。[完整安装与使用步骤](/guide/quick-start)

启动后打开 [本机投递工作台](http://127.0.0.1:9527/)。JobsDB 默认只采集，点岗位名称即可前往原站。

## 📑 文档说明

`docs/` 目录沿自上游项目，配置与部署流程通用；本 fork 的功能差异（统一设置中心、数据看板、工作形式、多平台与香港站）以仓库根目录 [README](https://github.com/GF-creator-code/internbridge#readme) 为准。
