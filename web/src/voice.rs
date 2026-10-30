//! 要念的話。每一句有固定的 id，對應預錄語音檔 `voice/<id>.mp3`（由 Android 版的 `assets/voice/<id>.wav` 轉檔而來）；
//! 沒有那個檔案時，改用瀏覽器內建語音合成念 `fallback`。
//! 語音稿 `tools/voice/script.csv` 與 Android 版共用，單元測試會檢查兩邊一致。
//!
//! 語氣分類看 id 前綴：`count_`（讀秒）、`coach_`（動作口令）、`cheer_`（鼓勵）、
//! `move_`（動作說明）、`stage_`（階段開始）、`elapsed_`（經過時間）、`plan_done`（課表完成）。

use crate::moves::{CoachCue, CueKind, Move};
use crate::plan::{Stage, StageKind};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VoiceLine {
    pub id: String,
    /// 預錄語音的稿子（script.csv 的內容）。
    pub text: String,
    /// 沒有語音檔時給瀏覽器內建語音念的話；通常和 `text` 相同，階段開始與經過時間會帶上分鐘數。
    pub fallback: String,
}

impl VoiceLine {
    fn same(id: String, text: String) -> Self {
        Self { id, fallback: text.clone(), text }
    }
}

/// 鼓勵的話。氣泡顯示文字，語音念同一句；不放會變動的數字，才能事先錄成語音檔。
pub const CHEERS: [&str; 24] = [
    "今天也出門了，你真的好棒！",
    "步子小小的，剛剛好，繼續保持喔！",
    "慢到還能聊天，就是對的速度！",
    "肩膀放鬆，嘴角上揚，笑一個～",
    "呼吸順順的，你做得超好！",
    "跟著節拍，一二、一二，我們同步囉！",
    "不用追求快，穩穩地跑就很厲害了！",
    "腳掌輕輕落地，像踩在雲上一樣～",
    "抬頭挺胸，看向遠方，帥呆了！",
    "每一步都算數，你超讚的！",
    "太棒了！你已經打敗賴床的自己了！",
    "加油加油，再撐一下下，你可以的！",
    "哇，你的節奏好穩，我都想跟著你跑了！",
    "感覺到風了嗎？今天的早晨好舒服～",
    "身體正在謝謝你喔，繼續保持！",
    "慢慢來比較快，我們不急！",
    "好帥好帥，這個姿勢滿分！",
    "堅持下去，未來的你會謝謝現在的你！",
    "手臂放鬆，輕輕擺動，跑起來好輕鬆～",
    "你不是一個人，我陪你一起跑！",
    "這就是自律的樣子，超酷的！",
    "深呼吸，吸～吐～，節奏剛剛好！",
    "再堅持一下，好心情就在前面等你！",
    "今天的你，比昨天更棒一點點！",
];

/// 每 5 分鐘念一次經過時間，語音檔涵蓋 5 到 60 分鐘。
#[cfg(test)]
pub fn elapsed_minutes() -> Vec<u32> {
    (5..=60).step_by(5).collect()
}

const NUMERALS: [&str; 10] = ["零", "一", "二", "三", "四", "五", "六", "七", "八", "九"];

/// 0 到 99 的中文念法：5 → 五、10 → 十、15 → 十五、30 → 三十、45 → 四十五。
pub fn chinese_number(n: u32) -> String {
    let v = n.min(99) as usize;
    match v {
        0..=9 => NUMERALS[v].to_string(),
        10..=19 => format!("十{}", if v % 10 == 0 { "" } else { NUMERALS[v % 10] }),
        _ => format!("{}十{}", NUMERALS[v / 10], if v % 10 == 0 { "" } else { NUMERALS[v % 10] }),
    }
}

fn stage_script(kind: StageKind) -> &'static str {
    match kind {
        StageKind::Warmup => "暖身開始，一起動一動吧！",
        StageKind::Jog => "超慢跑開始，跟著節拍出發囉！",
        StageKind::Strength => "肌力訓練開始，動作做漂亮一點喔！",
        StageKind::Cooldown => "伸展開始，慢慢放鬆，辛苦了～",
    }
}

/// 階段開始。語音檔不念分鐘數（畫面上有），內建語音則會念。
pub fn stage(stage: &Stage) -> VoiceLine {
    VoiceLine {
        id: format!("stage_{}", stage.kind.id()),
        text: stage_script(stage.kind).to_string(),
        fallback: format!("{}開始，{} 分鐘", stage.kind.name(), stage.minutes),
    }
}

pub fn plan_done() -> VoiceLine {
    VoiceLine {
        id: "plan_done".to_string(),
        text: "課表全部完成，今天辛苦了，你超棒的！".to_string(),
        fallback: "課表完成".to_string(),
    }
}

pub fn elapsed(minutes: u32) -> VoiceLine {
    VoiceLine {
        id: format!("elapsed_{minutes}"),
        text: format!("已經{}分鐘囉，繼續保持！", chinese_number(minutes)),
        fallback: format!("已經 {minutes} 分鐘"),
    }
}

/// 讀秒（1 到 5）。
pub fn count(number: u32) -> VoiceLine {
    let n = number.clamp(1, 5);
    let text = format!("{}！", ["", "一", "二", "三", "四", "五"][n as usize]);
    VoiceLine::same(format!("count_{n}"), text)
}

pub fn coach(cue: &CoachCue) -> VoiceLine {
    match cue.kind {
        CueKind::SwitchSide => VoiceLine::same("coach_switch".into(), "換邊！".into()),
        CueKind::Left30 => VoiceLine::same("coach_30s".into(), "還有三十秒，加油！".into()),
        CueKind::Left10 => VoiceLine::same("coach_10s".into(), "還有十秒！".into()),
        CueKind::Count => count(cue.number),
    }
}

pub fn mv(m: Move) -> VoiceLine {
    VoiceLine::same(format!("move_{}", m.id()), format!("{}。{}", m.name_zh(), m.tip()))
}

/// 鼓勵的話：`index` 是 [`CHEERS`] 的索引。
pub fn cheer(index: usize) -> VoiceLine {
    let i = index.min(CHEERS.len() - 1);
    VoiceLine::same(format!("cheer_{:02}", i + 1), CHEERS[i].to_string())
}

/// 全部語音稿，依產生語音檔的順序（和 tools/voice/script.csv 一致）。
#[cfg(test)]
pub fn catalog() -> Vec<VoiceLine> {
    let mut out = Vec::new();
    for kind in StageKind::ALL {
        out.push(stage(&Stage { kind, minutes: 0 }));
    }
    out.push(plan_done());
    for m in elapsed_minutes() {
        out.push(elapsed(m));
    }
    for n in (1..=5).rev() {
        out.push(count(n));
    }
    for kind in [CueKind::SwitchSide, CueKind::Left30, CueKind::Left10] {
        out.push(coach(&CoachCue { kind, number: 0 }));
    }
    for m in Move::ALL {
        out.push(mv(m));
    }
    for i in 0..CHEERS.len() {
        out.push(cheer(i));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 解析 tools/voice/script.csv：每行 `id,"text"`。
    fn script() -> Vec<(String, String)> {
        include_str!("../../tools/voice/script.csv")
            .lines()
            .skip(1)
            .filter(|l| !l.trim().is_empty())
            .map(|l| {
                let (id, text) = l.split_once(',').expect("script.csv 每行要有逗號");
                (id.to_string(), text.trim().trim_matches('"').to_string())
            })
            .collect()
    }

    #[test]
    fn script_file_matches_the_catalog() {
        let expected: Vec<(String, String)> = catalog().into_iter().map(|l| (l.id, l.text)).collect();
        assert_eq!(script(), expected, "語音稿要和 tools/voice/script.csv 一致（Android 版共用）");
        assert_eq!(expected.len(), 63);
    }

    #[test]
    fn every_line_has_a_prerecorded_clip() {
        let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/public/voice");
        for l in catalog() {
            let path = format!("{dir}/{}.mp3", l.id);
            let len = std::fs::metadata(&path).unwrap_or_else(|_| panic!("缺語音檔 {path}")).len();
            assert!(len > 1_000, "{path} 太小，可能是壞檔");
        }
    }

    #[test]
    fn ids_are_unique_and_file_safe_and_have_a_known_prefix() {
        let cat = catalog();
        for l in &cat {
            assert!(l.id.chars().all(|c| c.is_ascii_alphanumeric() || c == '_'), "{}", l.id);
            assert_eq!(cat.iter().filter(|x| x.id == l.id).count(), 1);
            assert!(
                ["count_", "coach_", "cheer_", "move_", "stage_", "elapsed_", "plan_done"]
                    .iter()
                    .any(|p| l.id.starts_with(p)),
                "{} 沒有已知的語氣前綴",
                l.id
            );
        }
    }

    #[test]
    fn chinese_numbers() {
        assert_eq!(chinese_number(5), "五");
        assert_eq!(chinese_number(10), "十");
        assert_eq!(chinese_number(15), "十五");
        assert_eq!(chinese_number(30), "三十");
        assert_eq!(chinese_number(45), "四十五");
    }

    #[test]
    fn fallback_keeps_minutes_but_clip_text_does_not() {
        let s = stage(&Stage { kind: StageKind::Warmup, minutes: 5 });
        assert_eq!(s.fallback, "暖身開始，5 分鐘");
        assert_eq!(s.text, "暖身開始，一起動一動吧！");
        assert_eq!(elapsed(25).fallback, "已經 25 分鐘");
    }
}
