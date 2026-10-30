# 預錄語音（自然語音）

App 預設用手機內建的中文語音念，聽起來機械。App 也支援「預錄語音檔」：
`app/src/main/assets/voice/<id>.wav` 有的句子就播檔案，沒有的句子才用手機語音，所以可以一句一句慢慢換，不會出錯。

## 語音稿

`script.csv` 是所有會念的話（目前 63 句，id 對應檔名）。它由程式裡的 `VoiceLines.catalog()` 決定，
單元測試 `VoiceLinesTest.scriptFileMatchesTheCatalog` 會檢查兩邊一致：改了 App 裡的話，要同步改這個檔案。

## 用 Google AI Studio 產生

1. 到 <https://aistudio.google.com> 的 Get API key 申請金鑰（不要放進程式或 repo）。
2. 在 repo 根目錄執行：

   ```
   export GEMINI_API_KEY=你的金鑰
   python3 tools/voice/generate.py
   ```

3. 產生的檔案在 `app/src/main/assets/voice/`。逐句聽過，不滿意的刪掉再跑一次（已經有的檔案預設略過，`--force` 才重做）。
4. 想換聲音：`--voice Zephyr`（文件標示明亮）、`Sadachbia`（活潑）、`Laomedeia`（輕快），預設是 `Puck`（輕快）。
5. 提交並推送，CI 會把語音檔打包進 App；設定畫面會顯示「目前內附的語音檔：N / 63 句」。

如果預設的 Interactions 格式回應不對，加 `--api classic` 改用較早公開的 generateContent 格式（預設模型 `gemini-2.5-flash-preview-tts`，可用 `--model` 換）。

腳本會切掉每句前後的靜音（讀秒才不會慢半拍）並把音量統一。

> 這支腳本照 Google 的文件（Interactions API）寫成，寫的時候沒有實際連到 Google 測過。
> 如果執行失敗，錯誤訊息會印出 API 的回應，照著調整 `generate.py` 裡的 `build_body()` 與 `find_audio()`。

檔案大小：24 kHz 單聲道 16 位元 wav，每秒約 48 KB，63 句約 7 到 10 MB。

## 驗收語音品質

```
python tools/voice/verify.py            # 訊號檢查：格式、語速、靜音、停頓、音量、爆音、音高起伏
python tools/voice/verify.py --listen   # 再請 Gemini 聽寫並評分（需要 GEMINI_API_KEY，結果會快取）
```

結果在 `tools/voice/report/`（不進版控）：`review.html` 可以直接聽、標「過／重做」，按「匯出重做清單」會給出重跑的指令；`report.csv` 可用 Excel 看。
Gemini 的評分（台灣腔、風格、自然度）只是篩選用，不是標準答案，請以耳朵為準。

## 網頁版（web/）共用這些語音

網頁版用同一份語音稿，聲音檔則是這裡 wav 轉成的 mp3（`web/public/voice/<id>.mp3`）。重做或新增 wav 之後，執行

```
python3 tools/voice/to_web.py
```

即可同步（需要 ffmpeg；已經比 wav 新的 mp3 會略過，`--force` 全部重做），並把 `web/public/sw.js` 的 `CACHE` 版本號加一。
