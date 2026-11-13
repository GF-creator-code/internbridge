# InternBridge

**香港學生的兩地實習工作台** · Hong Kong & mainland China internship tracker

一次過整理多個職位，記低每份申請所用的履歷、截止日期和面試進度。香港的 JobsDB、OfferToday 與內地的實習僧、BOSS 直聘共用一張清單。工作台支援繁體中文及 English。

**[開啟工作台 / Open workspace](https://gf-creator-code.github.io/internbridge/)**　·　[下載發行版](https://github.com/GF-creator-code/internbridge/releases/latest)

## 第一次使用

1. 開啟工作台，從「香港」或「中國內地」進入招聘網站。亦可加入公司校招官網或學校就業中心的職位連結。
2. 按「加入職位」，選地區，每行貼一個連結。要一併記錄公司和職位，可以用以下格式。重複加入同一網址會保留原有進度。

   ```text
   Finance Intern | Example HK | https://example.org/jobs/finance-intern
   Data Intern | Example HK | https://example.org/jobs/data-intern
   ```

3. 按「編輯職位」記下履歷檔名、截止日期或跟進事項。按職位名稱到招聘網站選擇附件並提交，收到確認後把進度改為「已申請」。面試邀請和聘書亦可記錄。
4. 清單保存在目前瀏覽器、目前網址的本機儲存空間。換電腦、轉用本機版或清理瀏覽器前，先按「匯出備份」，再在新環境「匯入備份」。匯入會合併職位並保留現有同一網址的紀錄。履歷欄儲存檔名；附件在招聘網站上傳。

**English quick start.** Open the workspace, choose **English**, and use either market's job sites. Choose **Add jobs**, select a market and paste one listing URL per line. Edit a role to record the CV version, deadline and notes. Submit on the employer's site, then update the status. Use **Export backup** and **Import backup** to move between browsers or devices. The web workspace needs only a browser and stores records on your device.

## 本機平台工具

需要從平台採集職位時，可安裝本機版。網頁版可獨立使用。兩者的清單可用同一種 JSON 備份轉移。

1. 安裝 Java 21 及 Google Chrome。從 Releases 下載 `internbridge-0.1.0.jar`，放到專用資料夾。
2. 在該資料夾開啟終端機，執行以下指令。首次啟動會自動建立空白資料庫及公共選項字典。

   ```sh
   java -jar internbridge-0.1.0.jar
   ```

3. 開啟 [本機工作台](http://127.0.0.1:9527/)，按「本機平台工具」選擇平台，登入自己的帳戶。先核對城市、關鍵詞、履歷及該平台的任務模式，再啟動任務。香港從 JobsDB 或 OfferToday 開始；內地從實習僧或 BOSS 直聘開始。內地新安裝的地點預設為深圳，可在各平台更改。
4. 返回工作台，在「從本機採集記錄加入職位」選平台。每次加入最近 50 個有連結的記錄並按網址去重。清單中的人工跟進狀態與平台任務紀錄分開儲存。

若只想使用本機清單及查看已有記錄，可加上 `--internbridge.open-platforms=false` 啟動。變更連接埠可加 `--server.port=9537`。使用專用資料夾存放本機版可獨立管理求職資料；重新啟動時請使用同一資料夾。

| 地區 | 平台 | 首版支援情況 |
| --- | --- | --- |
| 香港 | JobsDB | 職位搜尋、已有採集記錄匯入。預設只採集。SEEK 多步申請仍屬實驗功能，建議在原站完成。 |
| 香港 | OfferToday | 職位入口、已有採集記錄匯入。自動登入與發送尚待實帳驗證。 |
| 內地 | 實習僧、BOSS 直聘 | 職位入口、記錄匯入及原有本機任務。實習僧投遞需選中附件；BOSS 直聘需登入並受帳戶當日額度限制。 |
| 內地 | 智聯、獵聘、前程無憂 | 保留原有平台設定。首版未重新驗證這三個平台的完整投遞流程。 |

本機版使用 SQLite 儲存平台設定及登入資料，平台瀏覽器另有獨立資料夾。請只在自己的電腦執行；分享問題報告時使用虛構的學生資料。AI 為選用功能；啟用後，相應職位及自填的個人介紹會提交至所設定的模型服務。公開工作台並無登入、上傳履歷或 AI 服務。

## 從原始碼運行

需要 JDK 21、Node.js 22 或更新版本、pnpm 10 及 Google Chrome。在專案根目錄執行：

```sh
pnpm --dir front install --frozen-lockfile
```

Windows 執行 `powershell -ExecutionPolicy Bypass -File .\start-local.ps1`。macOS / Linux 執行 `sh gradlew bootRun`。原始碼模式會編譯 Next.js 平台面板及安裝瀏覽器驅動。開發者可用 `node --test tests/workspace.test.cjs` 測工作台資料處理，並用 `sh gradlew build` 或 `.\gradlew.bat build` 執行後端測試及打包。

## 來源與授權

InternBridge 由 [GF-creator-code](https://github.com/GF-creator-code) 維護，基於 [loks666/get_jobs](https://github.com/loks666/get_jobs/tree/8f5c48538f68ffebbcfe1c9fb57a12a0bc699b50) 二次開發。上游提供本機自動化架構及多個內地平台工具。本分支加入香港平台、實習僧、跨平台任務瀏覽器、繁中／英文兩地申請清單，以及學生用首次安裝設定。空白資料庫由[上游 v2.0.1 公開模板](https://github.com/loks666/get_jobs/releases/tag/v2.0.1)的表結構和選項字典重建。

軟件沿用 **PolyForm Noncommercial 1.0.0**。完整授權條款、上游版權及 Required Notice 見 [LICENSE](LICENSE)。
