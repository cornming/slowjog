//! 暖身、肌力與伸展的動作輪替與教練口令。對應 Android 版的 MascotMoves.kt／MoveCoach.kt，純邏輯。
//! 網頁版沒有 3D 小人物影格，只顯示動作名稱與要領。

use crate::plan::{Stage, StageKind};

/// 動作。`cycle_sec` 是一個循環（做一次）多久；`alternates_sides` 代表動作分左右。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Move {
    March,
    ArmCircle,
    Twist,
    Squat,
    DeepSquat,
    Lunge,
    CalfRaise,
    KneeElbow,
    SideLeg,
    Calf,
    Quad,
    Forward,
    SideBend,
    CrossArm,
}

impl Move {
    /// 依語音稿（script.csv）的順序，給語音稿對照的單元測試用。
    #[cfg(test)]
    pub const ALL: [Move; 14] = [
        Self::March,
        Self::ArmCircle,
        Self::Twist,
        Self::Squat,
        Self::DeepSquat,
        Self::Lunge,
        Self::CalfRaise,
        Self::KneeElbow,
        Self::SideLeg,
        Self::Calf,
        Self::Quad,
        Self::Forward,
        Self::SideBend,
        Self::CrossArm,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Self::March => "march",
            Self::ArmCircle => "armcircle",
            Self::Twist => "twist",
            Self::Squat => "squat",
            Self::DeepSquat => "deepsquat",
            Self::Lunge => "lunge",
            Self::CalfRaise => "calfraise",
            Self::KneeElbow => "kneeelbow",
            Self::SideLeg => "sideleg",
            Self::Calf => "calf",
            Self::Quad => "quad",
            Self::Forward => "forward",
            Self::SideBend => "sidebend",
            Self::CrossArm => "crossarm",
        }
    }

    pub fn name_zh(self) -> &'static str {
        match self {
            Self::March => "抬膝踏步",
            Self::ArmCircle => "手臂繞圈",
            Self::Twist => "轉體",
            Self::Squat => "屈膝半蹲",
            Self::DeepSquat => "深蹲",
            Self::Lunge => "原地弓箭步",
            Self::CalfRaise => "提踵",
            Self::KneeElbow => "抬膝碰肘",
            Self::SideLeg => "側抬腿",
            Self::Calf => "小腿伸展",
            Self::Quad => "大腿前側伸展",
            Self::Forward => "前彎伸展",
            Self::SideBend => "上舉側彎",
            Self::CrossArm => "肩膀橫向伸展",
        }
    }

    pub fn tip(self) -> &'static str {
        match self {
            Self::March => "膝蓋輕輕抬高，手臂前後擺動。",
            Self::ArmCircle => "雙手放鬆伸直，畫大圈活動肩膀。",
            Self::Twist => "雙手打開，腰帶著上身左右轉。",
            Self::Squat => "屁股往後坐，雙手往前伸，蹲到舒服的深度就好。",
            Self::DeepSquat => "屁股往後坐，膝蓋和腳尖同方向，雙手往前伸保持平衡。",
            Self::Lunge => "一腳在前，後膝慢慢往下沉，上身保持直立，左右輪流。",
            Self::CalfRaise => "雙腳踮起再慢慢放下，感覺小腿出力。",
            Self::KneeElbow => "雙手扶頭，抬膝時上身轉向膝蓋，左右輪流。",
            Self::SideLeg => "身體站穩，單腳往側邊抬起再放下，左右輪流。",
            Self::Calf => "後腳跟貼地，雙手往前推，感覺小腿拉長，左右都做。",
            Self::Quad => "站穩，手抓同側腳踝往臀部靠，左右都做。",
            Self::Forward => "上身慢慢往前放鬆下垂，膝蓋不要鎖死。",
            Self::SideBend => "雙手上舉，身體慢慢往兩側彎。",
            Self::CrossArm => "一手橫過胸前，另一手輕輕把手肘往身體壓，左右都做。",
        }
    }

    pub fn cycle_sec(self) -> f32 {
        match self {
            Self::March => 1.6,
            Self::ArmCircle => 2.0,
            Self::Twist => 2.4,
            Self::Squat => 4.0,
            Self::DeepSquat => 3.0,
            Self::Lunge => 3.0,
            Self::CalfRaise => 2.0,
            Self::KneeElbow => 2.4,
            Self::SideLeg => 2.4,
            Self::Calf | Self::Quad | Self::Forward | Self::CrossArm => 16.0,
            Self::SideBend => 8.0,
        }
    }

    pub fn alternates_sides(self) -> bool {
        matches!(self, Self::Lunge | Self::Calf | Self::Quad | Self::CrossArm)
    }
}

/// 現在該做哪個動作、做到循環的哪裡。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MoveSlot {
    pub mv: Move,
    /// 這個階段的第幾個動作（從 0 起）與總共幾個。
    pub index: usize,
    pub count: usize,
    /// 這個循環是否左右換邊（動作分左右時，一左一右輪流）。
    pub mirrored: bool,
    /// 下一個動作；最後一個動作為 None。
    pub next: Option<Move>,
    /// 這個動作已經做完幾個循環（從 0 起）。
    pub cycle: u64,
    pub cycle_elapsed_ms: u64,
    pub cycle_ms: u64,
    /// 這個動作還剩多久；最後一個動作做到階段結束。
    pub slot_remaining_ms: u64,
    pub slot_elapsed_ms: u64,
}

impl MoveSlot {
    pub fn cycle_remaining_ms(&self) -> u64 {
        self.cycle_ms.saturating_sub(self.cycle_elapsed_ms)
    }
}

/// 每個動作至少做這麼久，階段太短時就少做幾個動作。
pub const MIN_SLOT_MS: u64 = 30_000;

pub const WARMUP_MOVES: [Move; 4] = [Move::March, Move::ArmCircle, Move::Twist, Move::Squat];
pub const STRENGTH_MOVES: [Move; 5] =
    [Move::DeepSquat, Move::Lunge, Move::CalfRaise, Move::KneeElbow, Move::SideLeg];
pub const STRETCH_MOVES: [Move; 5] =
    [Move::Calf, Move::Quad, Move::Forward, Move::SideBend, Move::CrossArm];

/// 暖身、肌力訓練與伸展階段的動作；超慢跑階段沒有。
pub fn moves_for(kind: StageKind) -> &'static [Move] {
    match kind {
        StageKind::Warmup => &WARMUP_MOVES,
        StageKind::Strength => &STRENGTH_MOVES,
        StageKind::Cooldown => &STRETCH_MOVES,
        StageKind::Jog => &[],
    }
}

/// 這個階段會做幾個動作：每個至少 [`MIN_SLOT_MS`]，最多把清單做完。
pub fn move_count(kind: StageKind, stage_ms: u64) -> usize {
    let all = moves_for(kind).len();
    if all == 0 {
        return 0;
    }
    ((stage_ms / MIN_SLOT_MS) as usize).clamp(1, all)
}

/// 階段已過 `elapsed_ms` 時的動作；階段沒有動作或時間不合理時回傳 None。
pub fn slot_at(kind: StageKind, stage_ms: u64, elapsed_ms: u64) -> Option<MoveSlot> {
    let n = move_count(kind, stage_ms);
    if n == 0 || stage_ms == 0 {
        return None;
    }
    let slot_ms = stage_ms / n as u64;
    let index = ((elapsed_ms / slot_ms) as usize).min(n - 1);
    let within = elapsed_ms - index as u64 * slot_ms;
    let mv = moves_for(kind)[index];
    let cycle_ms = ((mv.cycle_sec() * 1000.0) as u64).max(1);
    let cycle = within / cycle_ms;
    let slot_end = if index == n - 1 { stage_ms } else { (index as u64 + 1) * slot_ms };
    Some(MoveSlot {
        mv,
        index,
        count: n,
        mirrored: mv.alternates_sides() && cycle % 2 == 1,
        next: moves_for(kind).get(index + 1).copied().filter(|_| index + 1 < n),
        cycle,
        cycle_elapsed_ms: within % cycle_ms,
        cycle_ms,
        slot_remaining_ms: slot_end.saturating_sub(elapsed_ms),
        slot_elapsed_ms: within,
    })
}

/// 方便用課表階段與階段內已過的時間查詢。
pub fn slot_in_stage(stage: &Stage, elapsed_in_stage_ms: u64) -> Option<MoveSlot> {
    slot_at(stage.kind, stage.ms(), elapsed_in_stage_ms)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CueKind {
    SwitchSide,
    Left30,
    Left10,
    Count,
}

/// 做動作時的教練口令。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CoachCue {
    pub kind: CueKind,
    pub number: u32,
}

impl CoachCue {
    /// 同一個循環裡，同一句口令的識別碼（用來避免重複念）。
    pub fn key(&self) -> u64 {
        self.kind as u64 * 10 + u64::from(self.number)
    }
}

/// 一個循環達到這麼久，就當作停留型動作（伸展）。
pub const HOLD_CYCLE_SEC: f32 = 8.0;
/// 最後幾秒讀秒。
pub const COUNT_FROM: u32 = 5;
/// 「換邊」只在新循環開始後這麼短的時間內喊，過了就不喊（避免延遲才補喊）。
const SWITCH_WINDOW_MS: u64 = 1_500;
/// 反覆型動作的「剩 30 秒」，動作至少要做了這麼久才喊，避免和剛念完的動作名稱撞在一起。
const LEFT_30_MIN_ELAPSED_MS: u64 = 5_000;

pub fn is_hold(mv: Move) -> bool {
    mv.cycle_sec() >= HOLD_CYCLE_SEC
}

/// 此刻該喊的口令；沒有則 None。
///
/// - 停留型（伸展）：以「這一側」為單位，剩 10 秒提醒、最後 5 秒讀秒；左右分邊的從第二個循環起喊「換邊」。
/// - 反覆型（深蹲、抬膝）：以「這個動作」為單位，剩 30 秒、10 秒提醒，最後 5 秒讀秒；不喊換邊。
pub fn cue_at(slot: &MoveSlot) -> Option<CoachCue> {
    let hold = is_hold(slot.mv);
    if hold && slot.mv.alternates_sides() && slot.cycle > 0 && slot.cycle_elapsed_ms < SWITCH_WINDOW_MS {
        return Some(CoachCue { kind: CueKind::SwitchSide, number: 0 });
    }
    // 停留型看這一側剩多久；但最後一個循環可能被動作時間截短，所以不能超過整個動作剩下的時間，
    // 否則最後幾秒的讀秒會停在 3 就換動作了（Android 版有這個小毛病，網頁版修正）。
    let remaining_ms = if hold { slot.cycle_remaining_ms().min(slot.slot_remaining_ms) } else { slot.slot_remaining_ms };
    if remaining_ms == 0 {
        return None;
    }
    let seconds = remaining_ms.div_ceil(1000) as u32;
    if (1..=COUNT_FROM).contains(&seconds) {
        Some(CoachCue { kind: CueKind::Count, number: seconds })
    } else if seconds == 10 {
        Some(CoachCue { kind: CueKind::Left10, number: 0 })
    } else if !hold && seconds == 30 && slot.slot_elapsed_ms >= LEFT_30_MIN_ELAPSED_MS {
        Some(CoachCue { kind: CueKind::Left30, number: 0 })
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const MIN: u64 = 60_000;

    #[test]
    fn move_count_depends_on_stage_length() {
        assert_eq!(move_count(StageKind::Warmup, 5 * MIN), 4);
        assert_eq!(move_count(StageKind::Warmup, 90_000), 3);
        assert_eq!(move_count(StageKind::Warmup, 10_000), 1);
        assert_eq!(move_count(StageKind::Strength, 6 * MIN), 5);
        assert_eq!(move_count(StageKind::Jog, 15 * MIN), 0);
    }

    #[test]
    fn slots_split_the_stage_evenly_and_last_one_runs_to_the_end() {
        let s = slot_at(StageKind::Cooldown, 4 * MIN, 0).unwrap();
        assert_eq!((s.mv, s.index, s.count), (Move::Calf, 0, 5));
        assert_eq!(s.next, Some(Move::Quad));
        assert_eq!(s.slot_remaining_ms, 48_000); // 4 分鐘 ÷ 5 個動作
        let last = slot_at(StageKind::Cooldown, 4 * MIN, 4 * MIN - 1).unwrap();
        assert_eq!(last.mv, Move::CrossArm);
        assert_eq!(last.next, None);
        assert_eq!(last.slot_remaining_ms, 1);
    }

    #[test]
    fn side_moves_mirror_every_cycle() {
        let a = slot_at(StageKind::Cooldown, 4 * MIN, 1_000).unwrap();
        let b = slot_at(StageKind::Cooldown, 4 * MIN, 17_000).unwrap();
        assert!(!a.mirrored);
        assert!(b.mirrored);
        assert_eq!(b.cycle, 1);
    }

    #[test]
    fn hold_moves_call_switch_then_ten_then_countdown() {
        let at = |ms| slot_at(StageKind::Cooldown, 4 * MIN, ms).unwrap();
        // 第二個循環剛開始：換邊
        assert_eq!(cue_at(&at(16_200)).unwrap().kind, CueKind::SwitchSide);
        // 第一個循環剩 10 秒
        assert_eq!(cue_at(&at(6_000)).unwrap().kind, CueKind::Left10);
        // 第一個循環剩 3 秒
        let c = cue_at(&at(13_000)).unwrap();
        assert_eq!((c.kind, c.number), (CueKind::Count, 3));
        // 第一個循環剛開始不喊
        assert_eq!(cue_at(&at(200)), None);
    }

    #[test]
    fn hold_move_countdown_reaches_one_even_when_the_last_cycle_is_cut_short() {
        // 伸展 2 分鐘 ÷ 4 個動作（每個至少 30 秒），小腿伸展做 30 秒：第二個循環（16 到 32 秒）會被截在 30 秒
        let at = |ms| slot_at(StageKind::Cooldown, 150_000, ms).unwrap();
        assert_eq!(at(0).slot_remaining_ms, 30_000);
        let c = cue_at(&at(29_000)).unwrap();
        assert_eq!((c.kind, c.number), (CueKind::Count, 1));
        assert_eq!(cue_at(&at(20_000)).unwrap().kind, CueKind::Left10);
    }

    #[test]
    fn repeat_moves_call_30s_only_after_a_while() {
        let at = |ms| slot_at(StageKind::Strength, 6 * MIN, ms).unwrap();
        // 每個動作 72 秒：剩 30 秒 ＝ 做了 42 秒
        assert_eq!(cue_at(&at(42_000)).unwrap().kind, CueKind::Left30);
        assert_eq!(cue_at(&at(62_000)).unwrap().kind, CueKind::Left10);
        assert_eq!(cue_at(&at(67_000)).unwrap().number, 5);
        // 短階段：剛開始 5 秒內剩 30 秒以內也不喊
        let short = slot_at(StageKind::Warmup, 30_000, 1_000).unwrap();
        assert_eq!(cue_at(&short), None);
        let short = slot_at(StageKind::Warmup, 30_000, 5_500).unwrap();
        assert_eq!(cue_at(&short), None);
    }

    #[test]
    fn lunge_never_calls_switch() {
        // 弓箭步是反覆型，循環 3 秒，不喊換邊
        for ms in (72_000..144_000).step_by(100) {
            let s = slot_at(StageKind::Strength, 6 * MIN, ms).unwrap();
            if s.mv == Move::Lunge {
                assert_ne!(cue_at(&s).map(|c| c.kind), Some(CueKind::SwitchSide));
            }
        }
    }
}
