# SlowJog

陪你養成「早晨 30 分鐘運動 + 超慢跑」習慣的 Android App。第一版先做節拍器，之後陸續加入運動計時、每日提醒、桌面小工具與小人物動畫。

## 目前功能（第一版）

- **節拍器**：BPM 可調（40 到 240，預設 180，對應超慢跑建議的每分鐘 180 步）。
- **點拍子定速**：跟著腳步連續點幾下，自動換算成 BPM。
- **節拍視覺閃動**：每一拍畫面亮一下，環境吵或音量小時也能對拍。
- **背景持續運作**：以前景服務執行，鎖定螢幕或切到其他 App 後仍會繼續，通知上有「停止」按鈕。
- **可與音樂同時播放**：節拍器不搶音訊焦點，可以邊聽 Spotify 邊用。
- **運動中螢幕恆亮**，停止後恢復系統設定。
- **檢查更新**：開啟 App 時自動檢查 GitHub 上的最新版，有新版就在首頁提示；按「下載並更新」會在 App 內下載 APK 並開啟系統安裝畫面。首頁也有「檢查更新」按鈕可手動檢查。第一次更新時，系統會要求允許 SlowJog 安裝應用程式。
- 沒有廣告、不追蹤。網路權限只用來檢查與下載更新，只連到 GitHub，不送出任何使用者資料。

## 下載與安裝

到 [Releases](../../releases) 下載最新的 `slowjog-v*.apk` 安裝。每次推送到 `main` 都會自動建置並發佈新版本，Release 頁面附有這個版本的更新日誌。

新版也可以在 App 內更新（見上方「檢查更新」）。安裝新版時可以直接覆蓋舊版，不需要先移除：所有版本都使用 repo 內固定的 debug keystore（`keystore/debug.keystore`）簽章，簽章一致才能覆蓋安裝。

## 版本與更新日誌

- 版本號自動遞增：`versionCode` 與 `versionName`（`0.1.<建置編號>`）由 CI 在建置時帶入，不需要手動修改。
- 更新日誌由兩次發佈之間的 commit 標題自動產生，因此 commit 標題請寫成人看得懂的一句話。
- App 首頁底部會顯示目前版本，方便確認有沒有更新成功。

## 本機建置

需要 JDK 17 與 Android SDK。

```bash
./gradlew test assembleDebug
```

APK 會產生在 `app/build/outputs/apk/debug/`。

## 專案結構

`mascot/` 放小人物的影格表與轉檔工具，詳見 [mascot/README.md](mascot/README.md)。

```
app/src/main/java/com/cornming/slowjog/
├── MainActivity.kt
├── metronome/
│   ├── MetronomeEngine.kt      # AudioTrack 串流節拍，每拍由取樣點數決定長度
│   ├── MetronomeService.kt     # 前景服務與通知
│   ├── MetronomeController.kt  # 畫面與引擎之間的單一入口與狀態
│   └── TapTempo.kt             # 點拍子換算 BPM（純 Kotlin，有單元測試）
├── update/
│   ├── UpdateController.kt     # 檢查 GitHub 最新版、下載 APK、交給系統安裝
│   ├── ReleaseInfo.kt          # 解析 Release 資訊（有單元測試）
│   └── Versions.kt             # 版本號比較（有單元測試）
└── ui/
    ├── HomeScreen.kt
    └── SlowJogTheme.kt
```

## 路線圖

1. 專案與發佈流程（完成）
2. 節拍器（完成）
3. 整段運動與超慢跑階段分別計時、運動紀錄
4. 每日運動提醒（通知上有「開始運動」按鈕、連續漏兩天補提醒）
5. 桌面小工具：累計超慢跑時間與連續天數
6. 小人物動畫與鼓勵氣泡（已選方案 A：預先算圖的多角度影格，素材在 `mascot/`）
7. 第二版：階段式課表與提示、習慣分數與每週目標、節拍音色、語音播報、匯出 CSV
