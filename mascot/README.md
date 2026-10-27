# 小人物影格

方案 A（預先算圖的多角度影格）的素材與轉檔工具。影格表放在 `app/src/main/res/drawable-nodpi/`，App 的運動中畫面直接使用。

## 內容

- `rig.js`：角色造型與跑步姿勢。網頁原型也是用同一份，外觀才會一致。
- `moves.js`：暖身與伸展 9 個動作的姿勢（`MOVES`，每個動作一個 `pose(t)`，t 為 0 到 1 的循環相位）。App 端對應 `mascot/MascotMoves.kt`，id、循環秒數要一起改。
- `render.js`：用無頭瀏覽器把角色轉成透明背景的影格表。
- `../app/src/main/res/drawable-nodpi/mascot_v<角度>.png`：視角 0、15、30、45、60、75、90 度，共 7 張。每張 6 欄 x 2 列 = 12 格，一格 384 x 480，涵蓋一個步伐循環（兩步）。

- `../app/src/main/res/drawable-nodpi/mascot_m_<動作>.png`：9 個動作各一張，視角固定（見 `moves.js` 的 `angle`），格式同上（12 格一個循環）。App 播放時相鄰兩格疊合淡入，慢動作才順。

## 使用方式

- 第 0 格是腳落地的瞬間，對齊節拍；12 格對應 2 拍，播放速率 = BPM / 10 格每秒（180 BPM 約 18 格每秒）。
- 另一側 45 度（-45 度）用 45 度那張左右鏡像。
- 視角轉換時依序經過中間角度，例如 0、15、30、45 度。

## 重新輸出

```bash
cd mascot
npm install
node render.js            # 全部
ONLY=move node render.js  # 只重算暖身與伸展
ONLY=run node render.js   # 只重算跑步
OUT=/some/dir node render.js  # 輸出到別的資料夾預覽
```

需要先安裝過 Playwright 的 Chromium（`npx playwright install chromium`）。
