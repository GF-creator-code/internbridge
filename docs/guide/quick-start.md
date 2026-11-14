# 香港与内地实习申请入门

在自己的电脑上启动 Get Jobs 后，可采集职位、整理两地申请用的简历技能，也可在投递前粘贴一份职位描述试算。

## 启动本机管理端

Windows 需要 Git、JDK 21、Node.js 22.13 或更高版本，以及 Corepack 或 pnpm。将 JDK 安装目录设为 `JAVA_HOME`。第一次启动需要联网获取构建依赖。

```powershell
git clone https://github.com/GF-creator-code/internbridge.git
cd internbridge
powershell -File .\start-local.ps1
```

脚本首次运行时从上游 v2.0.1 下载数据库模板，校验后放到 `db/getjobs.db`。已有数据库会直接使用。Gradle 会构建管理页面并启动服务，打开本机的 [配置管理中心](http://127.0.0.1:9527/)。

macOS 或 Linux 用户可在项目根目录创建 `db` 目录，将上游的 `getjobs.db.template` 保存为 `db/getjobs.db`，然后运行 `./gradlew bootRun`。Windows 无法连接下载站时也可手动放置同一模板。[上游 v2.0.1 发布页](https://github.com/loks666/get_jobs/releases/tag/v2.0.1)

## 第一次申请

1. 在导航中选地区。在香港可先进入 JobsDB，填写搜索词（例如 `summer intern`）并保存。默认任务只采集，职位名称可从「投递分析」打开。内地可从实习僧或 BOSS 直聘开始，先检查城市、搜索词、附件简历和登录状态。
2. 使用 AI 时，在「环境配置」保存服务商的模型地址、密钥和模型名称，再打开「简历与 AI 匹配」。上传文字版 PDF、DOCX 或 UTF-8 TXT，最大 10 MB。简历文字会发送到这个模型。
3. 核对技能旁的简历原文。「加入并保存所选技能」会把勾选项追加到已有介绍。「先加入草稿」可以在保存前调整文字、试算匹配。
4. 在「职位描述」粘贴繁中、简中或英文的岗位要求。页面列出有简历原文支持的要求，以及现有介绍中暂缺依据的要求。计算匹配本身不会投递。
5. 需要跟踪岗位时，进入对应平台的「投递分析」，应用筛选后可打开原站或导出 CSV。JobsDB 多步骤申请为实验功能，使用 SEEK 账户中已有的身份和联系信息，OfferToday 的完整发送流程仍待实账验证。

## 更新已有安装

保留 `db/getjobs.db`，在运行服务的终端按 Ctrl+C，拉取最新代码后执行以下命令，重新生成页面并启动配套后端。

```powershell
git pull --ff-only
powershell -File .\start-local.ps1 -PrebuildFrontend
```
