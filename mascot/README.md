# 小人物影格

預先算圖的小人物素材與轉檔工具。影格表放在 `app/src/main/res/drawable-nodpi/`（WebP），App 的首頁、設定與運動中畫面直接使用。

## 內容

- `rig.js`：角色骨架、外型風格（`STYLES`）與跑步姿勢。四種風格共用同一套骨架與動作：
  - `blue` 小藍：原本的 3D Q 版。
  - `orange` 小橘：卡通材質加深色描邊、馬尾。
  - `pixel` 小點：外型同小藍，輸出時先用 1/6 解析度算圖再最近鄰放大，做成像素風。
  - `shiba` 阿柴：狗頭（三角耳、口鼻）、捲尾巴、項圈。
- `moves.js`：暖身與伸展 9 個動作的姿勢（`MOVES`，每個動作一個 `pose(t)`，t 為 0 到 1 的循環相位）。App 端對應 `mascot/MascotMoves.kt`，id、循環秒數要一起改。
- `render.js`：用無頭瀏覽器把每種風格輸出成透明背景的 PNG 影格表。
- `to-webp.py`：把 PNG 轉成 WebP 放進 App（像素風無損，其他有損品質 88），體積約為 PNG 的三分之一。

## 每種風格的檔案

一格 384 x 480。

- `mascot_<風格>_run`：側面跑步（面向左，App 鏡像成面向右），6 欄 x 2 列 = 12 格，一個步伐循環 = 2 拍。第 0 格是腳落地的瞬間，對齊節拍；播放速率 = BPM / 10 格每秒。
- `mascot_<風格>_idle`：正面站姿，1 格。
- `mascot_<風格>_m_<動作>`：暖身與伸展動作，6 欄 x 4 列 = 24 格一個循環，視角見 `moves.js` 的 `angle`。App 播放時相鄰兩格疊合淡入，慢動作才順。

## 重新輸出

```bash
cd mascot
npm install
node render.js                 # 全部風格，輸出到 mascot/out/
STYLE=shiba node render.js     # 只算某些風格
ONLY=move node render.js       # 只算動作（run、idle、move 可組合）
python3 to-webp.py             # 轉成 WebP 放進 App
```

需要先安裝過 Playwright 的 Chromium（`npx playwright install chromium`）。
