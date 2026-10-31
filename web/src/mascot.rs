//! 陪跑小人物：外型、影格表與影格挑選的規則。對應 Android 版的 MascotLogic.kt／MascotStyle.kt，純邏輯。
//!
//! 影格表放在 `public/mascot/`（Android 版用的同一批素材，由 `mascot/render.js` 算圖）：
//! 一格 384 x 480；跑步 6 欄 x 2 列 = 12 格，動作 6 欄 x 4 列 = 24 格。

use crate::moves::Move;
use serde::{Deserialize, Serialize};

/// 一個步伐循環共 12 格，對應 2 拍。
pub const FRAMES_PER_BEAT: u32 = 6;
pub const RUN_COLS: u32 = 6;
pub const RUN_ROWS: u32 = 2;
/// 動作影格表：一個循環 24 格。
pub const MOVE_FRAMES: u32 = 24;
pub const MOVE_COLS: u32 = 6;
pub const MOVE_ROWS: u32 = 4;
/// 氣泡停留秒數。
pub const BUBBLE_SHOW_SEC: u32 = 5;

/// 小人物的外型。骨架與動作都一樣，只有外型不同。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Style {
    /// 小藍：3D Q 版
    #[default]
    Blue,
    /// 小橘：卡通描邊
    Orange,
    /// 小點：像素風
    Pixel,
    /// 阿柴：狗狗跑者
    Shiba,
}

impl Style {
    pub const ALL: [Style; 4] = [Self::Blue, Self::Orange, Self::Pixel, Self::Shiba];

    pub fn id(self) -> &'static str {
        match self {
            Self::Blue => "blue",
            Self::Orange => "orange",
            Self::Pixel => "pixel",
            Self::Shiba => "shiba",
        }
    }

    pub fn name_zh(self) -> &'static str {
        match self {
            Self::Blue => "小藍",
            Self::Orange => "小橘",
            Self::Pixel => "小點",
            Self::Shiba => "阿柴",
        }
    }

    pub fn kind_zh(self) -> &'static str {
        match self {
            Self::Blue => "3D Q 版",
            Self::Orange => "卡通描邊",
            Self::Pixel => "像素風",
            Self::Shiba => "狗狗跑者",
        }
    }

    /// 像素風不做平滑，保留像素邊緣。
    pub fn pixel_art(self) -> bool {
        self == Self::Pixel
    }
}

/// 側面跑步的影格表（面向左；畫面上鏡像成面向右，風景往左退）。
pub fn run_url(style: Style) -> String {
    format!("mascot/{}_run.webp", style.id())
}

/// 正面站姿，1 格。
pub fn idle_url(style: Style) -> String {
    format!("mascot/{}_idle.webp", style.id())
}

pub fn move_url(style: Style, mv: Move) -> String {
    format!("mascot/{}_m_{}.webp", style.id(), mv.id())
}

/// 目前該畫跑步的第幾格（0 到 11）。第 0 與第 6 格是腳落地，對齊節拍：
/// 偶數拍從第 0 格開始，奇數拍從第 6 格開始，拍與拍之間走 6 格。
/// `since_beat_ms` 是距離這一拍響起已經過多久。
pub fn run_frame(beat: u64, since_beat_ms: f64, bpm: u32) -> u32 {
    let period_ms = 60_000.0 / f64::from(bpm.max(1));
    let within = ((since_beat_ms / period_ms) * f64::from(FRAMES_PER_BEAT)).floor();
    let within = within.clamp(0.0, f64::from(FRAMES_PER_BEAT - 1)) as u32;
    (beat % 2) as u32 * FRAMES_PER_BEAT + within
}

/// 影格表裡第 `frame` 格的 CSS 背景位置（百分比）。
/// 背景放大成 `cols * 100%` x `rows * 100%` 時，欄 i 的位置是 `i / (cols - 1) * 100%`。
pub fn sheet_pos(frame: u32, cols: u32, rows: u32) -> (f64, f64) {
    let col = frame % cols;
    let row = frame / cols;
    let pct = |i: u32, n: u32| if n <= 1 { 0.0 } else { f64::from(i) / f64::from(n - 1) * 100.0 };
    (pct(col, cols), pct(row, rows))
}

/// 動作的循環相位（0 以上、小於 1）對應的影格：（這一格、下一格、下一格要疊多少）。
/// 相鄰兩格疊在一起淡入，慢動作才不會一格一格跳。
pub fn move_frames(phase: f32) -> (u32, u32, f32) {
    let phase = phase.rem_euclid(1.0);
    let pos = phase * MOVE_FRAMES as f32;
    let i = (pos as u32).min(MOVE_FRAMES - 1);
    (i, (i + 1) % MOVE_FRAMES, pos - i as f32)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn run_frames_land_on_the_beat() {
        // 偶數拍從第 0 格、奇數拍從第 6 格開始：腳落地的瞬間就是拍子
        assert_eq!(run_frame(0, 0.0, 180), 0);
        assert_eq!(run_frame(1, 0.0, 180), 6);
        assert_eq!(run_frame(2, 0.0, 180), 0);
        assert_eq!(run_frame(7, 0.0, 180), 6);
    }

    #[test]
    fn run_frames_walk_six_frames_per_beat() {
        // 180 BPM：一拍 333.33 毫秒，6 格各約 55.6 毫秒
        let seen: Vec<u32> = (0..6).map(|i| run_frame(0, f64::from(i) * 55.6 + 1.0, 180)).collect();
        assert_eq!(seen, [0, 1, 2, 3, 4, 5]);
        let odd: Vec<u32> = (0..6).map(|i| run_frame(1, f64::from(i) * 55.6 + 1.0, 180)).collect();
        assert_eq!(odd, [6, 7, 8, 9, 10, 11]);
    }

    #[test]
    fn run_frames_stay_in_range_even_when_the_beat_is_late() {
        // 下一拍還沒來（暫停、卡頓）時停在這一拍的最後一格，不會跳到別拍的範圍
        assert_eq!(run_frame(0, 10_000.0, 180), 5);
        assert_eq!(run_frame(1, 10_000.0, 180), 11);
        assert_eq!(run_frame(0, -50.0, 180), 0, "負的經過時間當成 0");
        assert_eq!(run_frame(3, 100.0, 0), 6, "BPM 為 0 不能除以零");
    }

    #[test]
    fn faster_bpm_walks_the_frames_faster() {
        // 同樣過了 100 毫秒：160 BPM 比 200 BPM 走得慢
        assert!(run_frame(0, 100.0, 200) > run_frame(0, 100.0, 160));
    }

    #[test]
    fn sheet_positions_cover_the_grid_corners() {
        assert_eq!(sheet_pos(0, RUN_COLS, RUN_ROWS), (0.0, 0.0));
        assert_eq!(sheet_pos(5, RUN_COLS, RUN_ROWS), (100.0, 0.0));
        assert_eq!(sheet_pos(6, RUN_COLS, RUN_ROWS), (0.0, 100.0));
        assert_eq!(sheet_pos(11, RUN_COLS, RUN_ROWS), (100.0, 100.0));
        assert_eq!(sheet_pos(23, MOVE_COLS, MOVE_ROWS), (100.0, 100.0));
        let (x, y) = sheet_pos(6, MOVE_COLS, MOVE_ROWS);
        assert_eq!(x, 0.0);
        assert!((y - 100.0 / 3.0).abs() < 1e-9);
        assert_eq!(sheet_pos(0, 1, 1), (0.0, 0.0), "單格的影格表不除以零");
    }

    #[test]
    fn every_frame_maps_to_a_distinct_cell() {
        let cells: std::collections::BTreeSet<(i64, i64)> = (0..MOVE_FRAMES)
            .map(|f| {
                let (x, y) = sheet_pos(f, MOVE_COLS, MOVE_ROWS);
                ((x * 1000.0) as i64, (y * 1000.0) as i64)
            })
            .collect();
        assert_eq!(cells.len(), MOVE_FRAMES as usize);
    }

    #[test]
    fn move_frames_blend_adjacent_frames() {
        assert_eq!(move_frames(0.0), (0, 1, 0.0));
        let (i, next, frac) = move_frames(0.5);
        assert_eq!((i, next), (12, 13));
        assert!(frac.abs() < 1e-4);
        let (i, next, frac) = move_frames(0.5 + 0.25 / 24.0);
        assert_eq!((i, next), (12, 13));
        assert!((frac - 0.25).abs() < 1e-3);
    }

    #[test]
    fn move_frames_wrap_around_the_loop() {
        let (i, next, _) = move_frames(0.999);
        assert_eq!((i, next), (23, 0), "最後一格接回第 0 格");
        assert_eq!(move_frames(1.0).0, 0, "相位 1 等於回到循環開頭");
        assert_eq!(move_frames(-0.25).0, 18, "負的相位也往回繞");
        for step in 0..1000 {
            let (i, next, frac) = move_frames(step as f32 / 1000.0);
            assert!(i < MOVE_FRAMES && next < MOVE_FRAMES && (0.0..1.0).contains(&frac));
        }
    }

    #[test]
    fn style_ids_names_and_serde_agree() {
        for s in Style::ALL {
            let json = serde_json::to_string(&s).unwrap();
            assert_eq!(json, format!("\"{}\"", s.id()), "存檔用的名稱要和檔名一致");
            assert_eq!(serde_json::from_str::<Style>(&json).unwrap(), s);
            assert!(!s.name_zh().is_empty() && !s.kind_zh().is_empty());
        }
        assert_eq!(Style::default(), Style::Blue);
        assert!(Style::Pixel.pixel_art() && !Style::Shiba.pixel_art());
    }

    /// 網頁用到的每一張影格表都真的在 public/mascot/ 裡，檔名對得上。
    #[test]
    fn every_sheet_the_page_asks_for_exists() {
        let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("public");
        let mut count = 0;
        for s in Style::ALL {
            let mut urls = vec![run_url(s), idle_url(s)];
            urls.extend(Move::ALL.iter().map(|m| move_url(s, *m)));
            for u in urls {
                assert!(dir.join(&u).is_file(), "缺少 {u}");
                count += 1;
            }
        }
        assert_eq!(count, 4 * (2 + 14));
    }

    /// 網頁版的影格表是從 Android 版複製來的（去掉 `mascot_` 前綴）。重新輸出素材後兩邊要一起更新，
    /// 這個測試確保它們逐位元組相同，不會悄悄不一致。
    #[test]
    fn web_sheets_are_byte_identical_to_the_android_ones() {
        let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR"));
        let android = root.join("../app/src/main/res/drawable-nodpi");
        if !android.is_dir() {
            return; // 只拿到 web/ 資料夾時沒有 Android 版可比對
        }
        let mut compared = 0;
        for s in Style::ALL {
            let mut urls = vec![run_url(s), idle_url(s)];
            urls.extend(Move::ALL.iter().map(|m| move_url(s, *m)));
            for u in urls {
                let name = u.trim_start_matches("mascot/");
                let web = std::fs::read(root.join("public").join(&u)).unwrap();
                let droid = std::fs::read(android.join(format!("mascot_{name}"))).unwrap_or_else(|_| panic!("Android 版缺少 mascot_{name}"));
                assert!(web == droid, "{name} 和 Android 版不一樣，請重新複製");
                compared += 1;
            }
        }
        assert_eq!(compared, 64);
    }

    #[test]
    fn sheet_files_have_the_expected_pixel_sizes() {
        // WebP 檔頭：VP8 / VP8L / VP8X 都在固定位置放寬高，這裡只檢查最常見的 VP8（有損）與 VP8L（無損）
        fn size(path: &std::path::Path) -> (u32, u32) {
            let b = std::fs::read(path).unwrap();
            assert_eq!(&b[0..4], b"RIFF");
            assert_eq!(&b[8..12], b"WEBP");
            match &b[12..16] {
                b"VP8 " => (u32::from(u16::from_le_bytes([b[26], b[27]]) & 0x3fff), u32::from(u16::from_le_bytes([b[28], b[29]]) & 0x3fff)),
                b"VP8L" => {
                    let v = u32::from_le_bytes([b[21], b[22], b[23], b[24]]);
                    ((v & 0x3fff) + 1, ((v >> 14) & 0x3fff) + 1)
                }
                b"VP8X" => (
                    u32::from_le_bytes([b[24], b[25], b[26], 0]) + 1,
                    u32::from_le_bytes([b[27], b[28], b[29], 0]) + 1,
                ),
                other => panic!("不認得的 WebP 格式 {other:?}"),
            }
        }
        let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("public");
        for s in Style::ALL {
            assert_eq!(size(&dir.join(run_url(s))), (384 * RUN_COLS, 480 * RUN_ROWS), "{}", s.id());
            assert_eq!(size(&dir.join(idle_url(s))), (384, 480), "{}", s.id());
            for m in Move::ALL {
                assert_eq!(size(&dir.join(move_url(s, m))), (384 * MOVE_COLS, 480 * MOVE_ROWS), "{} {}", s.id(), m.id());
            }
        }
    }
}
