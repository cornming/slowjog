//! 練習紀錄、累計、連續天數、習慣分數、距離估算、點拍子定速、CSV 匯出。對應 Android 版的 data/ 與 workout/Distance.kt，純邏輯。

use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;

/// 一次運動的紀錄。`date` 是本地日期的 epoch day（1970-01-01 ＝ 0）。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Record {
    pub date: i64,
    /// 開始時間（毫秒，Unix epoch）。
    pub started_at: f64,
    pub duration_sec: u32,
    pub jog_sec: u32,
    pub bpm: u32,
}

/// 由年月日算 epoch day（公曆）。
pub fn days_from_civil(y: i64, m: i64, d: i64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = y.div_euclid(400);
    let yoe = y - era * 400;
    let mp = (m + 9) % 12;
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

/// epoch day 轉年月日。
pub fn civil_from_days(z: i64) -> (i64, i64, i64) {
    let z = z + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    (if m <= 2 { y + 1 } else { y }, m, d)
}

/// 星期幾，週一 ＝ 0 … 週日 ＝ 6。
pub fn weekday_mon0(day: i64) -> i64 {
    (day + 3).rem_euclid(7)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Stats {
    pub count: usize,
    pub total_jog_sec: u64,
    pub total_duration_sec: u64,
    /// 從今天（今天還沒運動則從昨天）往前，每天都有紀錄的天數。
    pub streak_days: u32,
    pub done_today: bool,
}

pub fn workout_days(records: &[Record]) -> BTreeSet<i64> {
    records.iter().map(|r| r.date).collect()
}

impl Stats {
    pub fn compute(records: &[Record], today: i64) -> Self {
        let days = workout_days(records);
        let done_today = days.contains(&today);
        let mut day = if done_today { today } else { today - 1 };
        let mut streak = 0;
        while days.contains(&day) {
            streak += 1;
            day -= 1;
        }
        Self {
            count: records.len(),
            total_jog_sec: records.iter().map(|r| u64::from(r.jog_sec)).sum(),
            total_duration_sec: records.iter().map(|r| u64::from(r.duration_sec)).sum(),
            streak_days: streak,
            done_today,
        }
    }
}

/// 習慣分數與每週目標（指數平滑，做法和 Android 版相同）。
pub mod habit {
    use std::collections::BTreeSet;

    pub const MIN_WEEKLY_TARGET: u32 = 1;
    pub const MAX_WEEKLY_TARGET: u32 = 7;
    pub const DEFAULT_WEEKLY_TARGET: u32 = 5;

    fn target(weekly: u32) -> u32 {
        weekly.clamp(MIN_WEEKLY_TARGET, MAX_WEEKLY_TARGET)
    }

    /// 每過一天，舊分數保留的比例；目標天數越少，衰減越慢。
    pub fn decay(weekly: u32) -> f64 {
        0.5_f64.powf((f64::from(target(weekly)) / 7.0).sqrt() / 13.0)
    }

    /// 回傳 0.0 到 1.0。
    pub fn score(days: &BTreeSet<i64>, today: i64, weekly: u32) -> f64 {
        let Some(&first) = days.iter().next() else { return 0.0 };
        if first > today {
            return 0.0;
        }
        let goal = target(weekly);
        let keep = decay(goal);
        let mut score = 0.0;
        for day in first..=today {
            let count = (day - 6..=day).filter(|d| days.contains(d)).count();
            let completion = (count as f64 / f64::from(goal)).min(1.0);
            score = score * keep + completion * (1.0 - keep);
        }
        score
    }

    /// 本週（週一到今天）運動了幾天。
    pub fn week_count(days: &BTreeSet<i64>, today: i64) -> u32 {
        let monday = today - super::weekday_mon0(today);
        (monday..=today).filter(|d| days.contains(d)).count() as u32
    }
}

/// 超慢跑距離的「估算」：節拍 × 時間 ＝ 步數，再乘上使用者設定的步幅（一拍算一步）。
pub mod distance {
    pub const DEFAULT_STRIDE_CM: u32 = 50;
    pub const MIN_STRIDE_CM: u32 = 30;
    pub const MAX_STRIDE_CM: u32 = 100;

    pub fn steps(duration_ms: u64, bpm: u32) -> f64 {
        if duration_ms == 0 || bpm == 0 { 0.0 } else { duration_ms as f64 / 60_000.0 * f64::from(bpm) }
    }

    pub fn meters(steps: f64, stride_cm: u32) -> f64 {
        steps * f64::from(stride_cm.clamp(MIN_STRIDE_CM, MAX_STRIDE_CM)) / 100.0
    }
}

/// 點拍子定速：跟著腳步連續點擊，換算成 BPM。
pub struct TapTempo {
    taps: Vec<f64>,
}

pub const MIN_BPM: u32 = 40;
pub const MAX_BPM: u32 = 240;
pub const DEFAULT_BPM: u32 = 180;

impl TapTempo {
    const MAX_GAP_MS: f64 = 2000.0;
    const WINDOW: usize = 6;

    pub fn new() -> Self {
        Self { taps: Vec::new() }
    }

    /// 記錄一次點擊；累積至少兩次點擊後回傳換算後的 BPM。兩次間隔超過 2 秒視為重新開始。
    pub fn tap(&mut self, now_ms: f64) -> Option<u32> {
        if self.taps.last().is_some_and(|&t| now_ms - t > Self::MAX_GAP_MS) {
            self.taps.clear();
        }
        self.taps.push(now_ms);
        while self.taps.len() > Self::WINDOW + 1 {
            self.taps.remove(0);
        }
        if self.taps.len() < 2 {
            return None;
        }
        let avg = (self.taps[self.taps.len() - 1] - self.taps[0]) / (self.taps.len() - 1) as f64;
        if avg <= 0.0 {
            return None;
        }
        Some(((60_000.0 / avg).round() as u32).clamp(MIN_BPM, MAX_BPM))
    }
}

impl Default for TapTempo {
    fn default() -> Self {
        Self::new()
    }
}

/// 匯出 CSV，欄位和 Android 版相同：id、date、startedAt、durationSec、jogSec、bpm。
pub fn to_csv(records: &[Record]) -> String {
    let mut out = String::from("id,date,startedAt,durationSec,jogSec,bpm\n");
    for (i, r) in records.iter().enumerate() {
        let (y, m, d) = civil_from_days(r.date);
        out.push_str(&format!(
            "{},{y:04}-{m:02}-{d:02},{},{},{},{}\n",
            i + 1,
            r.started_at as u64,
            r.duration_sec,
            r.jog_sec,
            r.bpm
        ));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rec(date: i64, duration_sec: u32, jog_sec: u32) -> Record {
        Record { date, started_at: 0.0, duration_sec, jog_sec, bpm: 180 }
    }

    #[test]
    fn civil_date_round_trip() {
        assert_eq!(days_from_civil(1970, 1, 1), 0);
        assert_eq!(days_from_civil(2026, 10, 30), 20_756);
        for day in [-1000, 0, 59, 60, 11_016, 20_756, 30_000] {
            let (y, m, d) = civil_from_days(day);
            assert_eq!(days_from_civil(y, m, d), day);
        }
        assert_eq!(civil_from_days(20_756), (2026, 10, 30));
    }

    #[test]
    fn weekday_is_monday_based() {
        assert_eq!(weekday_mon0(0), 3); // 1970-01-01 是星期四
        assert_eq!(weekday_mon0(days_from_civil(2026, 10, 30)), 4); // 星期五
        assert_eq!(weekday_mon0(days_from_civil(2026, 11, 2)), 0); // 星期一
    }

    #[test]
    fn streak_counts_from_today_or_yesterday() {
        let today = 100;
        let r = [rec(100, 60, 30), rec(99, 60, 30), rec(98, 60, 30), rec(96, 60, 30)];
        let s = Stats::compute(&r, today);
        assert_eq!((s.streak_days, s.done_today, s.count), (3, true, 4));
        let s2 = Stats::compute(&r[1..], today);
        assert_eq!((s2.streak_days, s2.done_today), (2, false));
        assert_eq!(Stats::compute(&[], today).streak_days, 0);
        assert_eq!(s.total_jog_sec, 120);
    }

    #[test]
    fn habit_score_rises_slowly_and_week_count() {
        let days: BTreeSet<i64> = (0..30).collect();
        let s = habit::score(&days, 29, 5);
        assert!(s > 0.6 && s < 0.95, "{s}");
        assert_eq!(habit::score(&BTreeSet::new(), 10, 5), 0.0);
        // 2026-11-04 星期三，週一 11-02 起
        let wed = days_from_civil(2026, 11, 4);
        let set: BTreeSet<i64> = [wed - 2, wed - 1, wed - 5].into_iter().collect();
        assert_eq!(habit::week_count(&set, wed), 2);
    }

    #[test]
    fn distance_estimate() {
        let steps = distance::steps(60_000, 180);
        assert_eq!(steps, 180.0);
        assert_eq!(distance::meters(steps, 50), 90.0);
        assert_eq!(distance::meters(steps, 10), 54.0); // 步幅最低 30 公分
        assert_eq!(distance::steps(0, 180), 0.0);
    }

    #[test]
    fn tap_tempo() {
        let mut t = TapTempo::new();
        assert_eq!(t.tap(0.0), None);
        // 每 333.33 毫秒一下 ≈ 180 BPM
        assert_eq!(t.tap(333.0), Some(180));
        assert_eq!(t.tap(667.0), Some(180));
        // 超過 2 秒重新開始
        assert_eq!(t.tap(5000.0), None);
        // 太慢會被限制在最低值
        let mut slow = TapTempo::new();
        slow.tap(0.0);
        assert_eq!(slow.tap(1900.0), Some(MIN_BPM));
    }

    #[test]
    fn csv_header_and_row() {
        let csv = to_csv(&[Record { date: days_from_civil(2026, 10, 30), started_at: 1_000.0, duration_sec: 1800, jog_sec: 900, bpm: 180 }]);
        assert_eq!(csv, "id,date,startedAt,durationSec,jogSec,bpm\n1,2026-10-30,1000,1800,900,180\n");
    }
}
