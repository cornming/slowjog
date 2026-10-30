//! 階段式課表：暖身、超慢跑、肌力訓練、伸展。對應 Android 版的 plan/ 套件，純邏輯、可單元測試。

/// 課表的階段種類；提示音的次數 ＝ 順序 + 1（暖身 1 聲、超慢跑 2 聲、肌力 3 聲、伸展 4 聲）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StageKind {
    Warmup,
    Jog,
    Strength,
    Cooldown,
}

impl StageKind {
    pub fn beep_count(self) -> u32 {
        match self {
            Self::Warmup => 1,
            Self::Jog => 2,
            Self::Strength => 3,
            Self::Cooldown => 4,
        }
    }

    /// 語音檔 id 的一部分（stage_<id>）。
    pub fn id(self) -> &'static str {
        match self {
            Self::Warmup => "warmup",
            Self::Jog => "jog",
            Self::Strength => "strength",
            Self::Cooldown => "cooldown",
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Warmup => "暖身",
            Self::Jog => "超慢跑",
            Self::Strength => "肌力訓練",
            Self::Cooldown => "伸展",
        }
    }

    #[cfg(test)]
    pub const ALL: [StageKind; 4] = [Self::Warmup, Self::Jog, Self::Strength, Self::Cooldown];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Stage {
    pub kind: StageKind,
    pub minutes: u32,
}

impl Stage {
    pub fn ms(&self) -> u64 {
        u64::from(self.minutes) * 60_000
    }
}

/// 目前走到課表的哪裡：`index` 等於階段數代表課表已全部完成。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PlanPosition {
    pub index: usize,
    pub stage: Option<Stage>,
    pub remaining_ms: u64,
}

/// 運動課表。時間以「整段運動已過時間」計算（暫停時不走）。0 分鐘的階段會被略過。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WorkoutPlan {
    pub stages: Vec<Stage>,
}

pub const DEFAULT_WARMUP: u32 = 5;
pub const DEFAULT_JOG: u32 = 15;
pub const DEFAULT_STRENGTH: u32 = 6;
pub const DEFAULT_COOLDOWN: u32 = 4;

impl WorkoutPlan {
    pub fn new(stages: Vec<Stage>) -> Self {
        Self {
            stages: stages.into_iter().filter(|s| s.minutes > 0).collect(),
        }
    }

    pub fn of(warmup: u32, jog: u32, strength: u32, cooldown: u32) -> Self {
        Self::new(vec![
            Stage { kind: StageKind::Warmup, minutes: warmup },
            Stage { kind: StageKind::Jog, minutes: jog },
            Stage { kind: StageKind::Strength, minutes: strength },
            Stage { kind: StageKind::Cooldown, minutes: cooldown },
        ])
    }

    pub fn total_ms(&self) -> u64 {
        self.stages.iter().map(Stage::ms).sum()
    }

    pub fn position(&self, elapsed_ms: u64) -> PlanPosition {
        let mut start = 0;
        for (index, stage) in self.stages.iter().enumerate() {
            let end = start + stage.ms();
            if elapsed_ms < end {
                return PlanPosition { index, stage: Some(*stage), remaining_ms: end - elapsed_ms };
            }
            start = end;
        }
        PlanPosition { index: self.stages.len(), stage: None, remaining_ms: 0 }
    }

    pub fn next(&self, index: usize) -> Option<Stage> {
        self.stages.get(index + 1).copied()
    }

    /// 是否剛好在某個階段交界前後 `margin_ms` 毫秒內（避免經過時間和階段播報重複念）。
    pub fn is_near_boundary(&self, elapsed_ms: u64, margin_ms: u64) -> bool {
        let mut boundary = 0;
        for stage in &self.stages {
            boundary += stage.ms();
            if elapsed_ms.abs_diff(boundary) <= margin_ms {
                return true;
            }
        }
        false
    }
}

/// 階段結束前倒數幾秒。
pub const COUNTDOWN_SEC: u64 = 5;

/// 目前該顯示與念出的倒數數字（5、4、3、2、1）；還沒到倒數、階段已結束、
/// 或這是最後一個階段（後面沒有下一階段）時回傳 None。
pub fn countdown(remaining_ms: u64, has_next: bool) -> Option<u32> {
    if !has_next || remaining_ms == 0 || remaining_ms > COUNTDOWN_SEC * 1000 {
        return None;
    }
    Some(remaining_ms.div_ceil(1000) as u32)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JogAction {
    None,
    Start,
    End,
}

/// 換到 `new_kind`（None 代表課表全部完成）時，超慢跑要怎麼動：
/// 進入超慢跑階段就自動開始；離開超慢跑階段而還在跑就自動停止。
pub fn jog_action(new_kind: Option<StageKind>, jogging: bool) -> JogAction {
    match (new_kind == Some(StageKind::Jog), jogging) {
        (true, false) => JogAction::Start,
        (false, true) => JogAction::End,
        _ => JogAction::None,
    }
}

/// 每隔幾分鐘念一次經過時間。
pub const ELAPSED_EVERY_MIN: u32 = 5;

/// 這一分鐘該不該念經過時間：5 分鐘的整數倍，而且和上次念的不同。
pub fn should_announce_elapsed(elapsed_ms: u64, last_announced_minute: i32) -> bool {
    let minute = (elapsed_ms / 60_000) as u32;
    minute > 0 && minute % ELAPSED_EVERY_MIN == 0 && minute as i32 != last_announced_minute
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_plan_is_30_minutes_and_skips_zero_stages() {
        let p = WorkoutPlan::of(5, 15, 6, 4);
        assert_eq!(p.total_ms(), 30 * 60_000);
        let q = WorkoutPlan::of(0, 15, 0, 4);
        assert_eq!(q.stages.len(), 2);
        assert_eq!(q.stages[0].kind, StageKind::Jog);
    }

    #[test]
    fn position_walks_through_stages_and_ends() {
        let p = WorkoutPlan::of(5, 15, 6, 4);
        let a = p.position(0);
        assert_eq!((a.index, a.remaining_ms), (0, 300_000));
        assert_eq!(p.position(300_000).stage.unwrap().kind, StageKind::Jog);
        let last = p.position(29 * 60_000 + 59_000);
        assert_eq!(last.stage.unwrap().kind, StageKind::Cooldown);
        assert_eq!(last.remaining_ms, 1_000);
        let done = p.position(30 * 60_000);
        assert_eq!((done.index, done.stage), (4, None));
    }

    #[test]
    fn boundary_margin() {
        let p = WorkoutPlan::of(5, 15, 6, 4);
        assert!(p.is_near_boundary(5 * 60_000 + 1_000, 1_500));
        assert!(!p.is_near_boundary(10 * 60_000, 1_500));
    }

    #[test]
    fn countdown_only_in_last_five_seconds_and_with_next_stage() {
        assert_eq!(countdown(5_000, true), Some(5));
        assert_eq!(countdown(4_001, true), Some(5));
        assert_eq!(countdown(1, true), Some(1));
        assert_eq!(countdown(5_001, true), None);
        assert_eq!(countdown(0, true), None);
        assert_eq!(countdown(3_000, false), None);
    }

    #[test]
    fn jog_actions() {
        assert_eq!(jog_action(Some(StageKind::Jog), false), JogAction::Start);
        assert_eq!(jog_action(Some(StageKind::Jog), true), JogAction::None);
        assert_eq!(jog_action(Some(StageKind::Strength), true), JogAction::End);
        assert_eq!(jog_action(None, true), JogAction::End);
        assert_eq!(jog_action(Some(StageKind::Warmup), false), JogAction::None);
    }

    #[test]
    fn elapsed_announce_every_five_minutes_once() {
        assert!(!should_announce_elapsed(4 * 60_000, -1));
        assert!(should_announce_elapsed(5 * 60_000, -1));
        assert!(!should_announce_elapsed(5 * 60_000 + 3_000, 5));
        assert!(should_announce_elapsed(10 * 60_000, 5));
        assert!(!should_announce_elapsed(0, -1));
    }
}
