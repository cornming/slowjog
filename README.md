# SlowJog

陪你養成「早晨 30 分鐘運動 + 超慢跑」習慣的 Android App。第一版先做節拍器，之後陸續加入運動計時、每日提醒、桌面小工具與小人物動畫。

## 目前功能（第一版）

- **運動音樂（Spotify）**：在「設定」貼上 Spotify 歌單連結（自己選的，或請 Claude 在對話裡建立的 AI 歌單都可以）。按「開始運動」時會詢問是否播放這份歌單，也可以改成自動播放或不使用音樂。有填 Spotify Client ID 時，由 App 透過 Spotify App Remote 在背景直接播放，不離開運動中畫面；沒填或連線失敗時，改為開啟 Spotify 的歌單頁，由你按播放。設定步驟寫在 App 的設定頁。
- **小人物陪跑**：運動中畫面有 Q 版小人物，超慢跑時腳步對齊節拍；每 5 分鐘（超慢跑時間）轉換視角（正面、45 度側面、90 度側面、另一側 45 度，循環），不時冒出鼓勵氣泡。點一下小人物可以立刻換視角並冒出一則氣泡。
- **運動計時與紀錄**：按「開始運動」進入運動中畫面，整段運動與超慢跑階段分別計時；按「開始超慢跑」才啟動節拍器，結束超慢跑節拍器就停。可暫停、繼續，結束時選擇儲存或放棄。紀錄存在本機，「紀錄」分頁可查看每次的日期、整段與超慢跑時長、BPM，以及累計時間與連續天數。
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

`app/libs/` 內有 Spotify App Remote SDK 的 AAR（Apache 2.0，來源與雜湊見 `app/libs/README.md`），不需要另外下載。

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
├── workout/
│   ├── WorkoutTimer.kt         # 整段與超慢跑計時（純 Kotlin，有單元測試）
│   └── WorkoutController.kt    # 一次運動的入口：開始、暫停、結束、寫入紀錄
├── data/
│   ├── WorkoutRecord.kt        # 運動紀錄（Room）
│   ├── AppDatabase.kt          # 資料庫與 RecordRepository
│   └── Stats.kt                # 累計與連續天數（有單元測試）
├── settings/
│   └── AppSettings.kt          # 設定（歌單、音樂模式、隨機播放、Client ID），存在本機
├── spotify/
│   ├── SpotifyLink.kt          # 解析歌單連結（有單元測試）
│   └── SpotifyPlayer.kt        # 請 Spotify 播放；失敗時改開歌單頁
├── update/
│   ├── UpdateController.kt     # 檢查 GitHub 最新版、下載 APK、交給系統安裝
│   ├── ReleaseInfo.kt          # 解析 Release 資訊（有單元測試）
│   └── Versions.kt             # 版本號比較（有單元測試）
├── mascot/
│   ├── MascotLogic.kt          # 視角、影格與氣泡規則（有單元測試）
│   └── MascotSheets.kt         # 影格表載入與快取
└── ui/
    ├── HomeScreen.kt           # 首頁：今天狀態、BPM、開始運動
    ├── WorkoutScreen.kt        # 運動中畫面
    ├── MascotView.kt           # 小人物動畫與鼓勵氣泡
    ├── HistoryScreen.kt        # 紀錄分頁
    ├── SettingsScreen.kt       # 設定分頁
    └── SlowJogTheme.kt
```

## 路線圖

1. 專案與發佈流程（完成）
2. 節拍器（完成）
3. 整段運動與超慢跑階段分別計時、運動紀錄（完成，進行中的這次運動只存在記憶體，App 被系統結束就不會保留）
4. 每日運動提醒（通知上有「開始運動」按鈕、連續漏兩天補提醒）
5. 桌面小工具：累計超慢跑時間與連續天數
6. 小人物動畫與鼓勵氣泡（已選方案 A：預先算圖的多角度影格，素材在 `app/src/main/res/drawable-nodpi/`，轉檔工具在 `mascot/`）
7. 第二版：階段式課表與提示、習慣分數與每週目標、節拍音色、語音播報、匯出 CSV
