//! 一次運動的引擎：每隔一小段時間用「整段運動已過時間」呼叫 `tick`，
//! 回傳這一刻該發生的事（念語音、嗶聲、開始／停止節拍、鼓勵氣泡、運動結束）。
//! 對應 Android 版 WorkoutController 的 checkXxx 系列，但不碰任何瀏覽器 API，所以可以單元測試。

use crate::moves::{self, CueKind};
use crate::plan::{self, JogAction, WorkoutPlan};
use crate::stats::distance;
use crate::voice::{self, VoiceLine};

/// 超慢跑開始後多久說第一句鼓勵（太早會和「超慢跑開始」的語音撞在一起）。
pub const FIRST_CHEER_JOG_SEC: u64 = 20;
/// 階段快結束、要倒數的時候不說鼓勵。
pub const NO_CHEER_BEFORE_STAGE_END_MS: u64 = 12_000;

/// 沒有課表時的目標。
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Target {
    None,
    /// 整段運動的分鐘數。
    Minutes(u32),
    /// 超慢跑的估算距離（公尺）。
    Meters(u32),
}

#[derive(Debug, Clone, PartialEq)]
pub struct Options {
    pub plan: Option<WorkoutPlan>,
    pub target: Target,
    pub stride_cm: u32,
    /// 階段切換的提示音與震動。
    pub cues: bool,
    pub voice: bool,
    pub coach_voice: bool,
    pub cheer_voice: bool,
    /// 階段結束前倒數，時間到自動開始或停止超慢跑。
    pub auto_advance: bool,
    /// 運動結束時自動停止節拍器。
    pub auto_stop: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Event {
    Say { line: VoiceLine, flush: bool },
    /// 階段提示音（嗶幾聲）。
    Beeps(u32),
    /// 階段提示的震動（震幾下）。
    Vibrate(u32),
    StartJog,
    EndJog,
    /// 超慢跑鼓勵氣泡。
    Cheer(String),
    /// 這次運動算結束了（課表全部做完或達成目標）；`voice` 是結束後要念的話。
    Done { voice: Option<VoiceLine> },
}

pub struct Engine {
    opts: Options,
    jogging: bool,
    jog_ms: u64,
    last_ms: u64,
    steps: f64,
    done: bool,
    last_stage_index: Option<usize>,
    last_move_key: Option<u64>,
    last_coach_key: Option<u64>,
    last_countdown: Option<u32>,
    last_elapsed_announced: i32,
    last_cheer: Option<usize>,
    next_cheer_at_jog_sec: u64,
}

impl Engine {
    pub fn new(mut opts: Options) -> Self {
        // 沒有課表就沒有「自動接續」
        opts.auto_advance = opts.auto_advance && opts.plan.is_some();
        Self {
            opts,
            jogging: false,
            jog_ms: 0,
            last_ms: 0,
            steps: 0.0,
            done: false,
            last_stage_index: None,
            last_move_key: None,
            last_coach_key: None,
            last_countdown: None,
            last_elapsed_announced: -1,
            last_cheer: None,
            next_cheer_at_jog_sec: FIRST_CHEER_JOG_SEC,
        }
    }

    pub fn jogging(&self) -> bool {
        self.jogging
    }

    pub fn jog_sec(&self) -> u64 {
        self.jog_ms / 1000
    }

    pub fn done(&self) -> bool {
        self.done
    }

    pub fn meters(&self) -> f64 {
        distance::meters(self.steps, self.opts.stride_cm)
    }

    /// 把「上次結算到現在」的時間算進超慢跑時間與步數；任何會改變跑步狀態的操作都要先結算。
    pub fn accrue(&mut self, elapsed_ms: u64, bpm: u32) {
        let delta = elapsed_ms.saturating_sub(self.last_ms);
        if self.jogging {
            self.jog_ms += delta;
            self.steps += distance::steps(delta, bpm);
        }
        self.last_ms = elapsed_ms;
    }

    /// 手動按「開始超慢跑」。已經在跑回傳 false。
    pub fn start_jog(&mut self, elapsed_ms: u64, bpm: u32) -> bool {
        self.accrue(elapsed_ms, bpm);
        if self.jogging {
            return false;
        }
        self.jogging = true;
        true
    }

    pub fn end_jog(&mut self, elapsed_ms: u64, bpm: u32) -> bool {
        self.accrue(elapsed_ms, bpm);
        if !self.jogging {
            return false;
        }
        self.jogging = false;
        true
    }

    /// 暫停時用：結算到暫停那一刻（暫停期間時間不走，所以之後 `elapsed_ms` 會接續）。
    pub fn pause(&mut self, elapsed_ms: u64, bpm: u32) {
        self.accrue(elapsed_ms, bpm);
    }

    pub fn tick(&mut self, elapsed_ms: u64, bpm: u32, rand: &mut dyn FnMut() -> f64) -> Vec<Event> {
        self.accrue(elapsed_ms, bpm);
        let mut ev = Vec::new();
        self.check_countdown(elapsed_ms, &mut ev);
        self.check_stage(elapsed_ms, &mut ev);
        self.check_move(elapsed_ms, &mut ev);
        self.check_coach(elapsed_ms, &mut ev);
        self.check_cheer(elapsed_ms, rand, &mut ev);
        self.check_elapsed(elapsed_ms, &mut ev);
        // 沒有課表時，目標（時間或距離）達成就結束；有課表時等課表全部做完（見 check_stage）。
        if self.opts.plan.is_none() && !self.done && self.target_reached(elapsed_ms) {
            self.finish_goal(None, &mut ev);
        }
        ev
    }

    fn target_reached(&self, elapsed_ms: u64) -> bool {
        match self.opts.target {
            Target::None => false,
            Target::Minutes(m) => m > 0 && elapsed_ms >= u64::from(m) * 60_000,
            Target::Meters(m) => m > 0 && self.meters() >= f64::from(m),
        }
    }

    fn finish_goal(&mut self, voice: Option<VoiceLine>, ev: &mut Vec<Event>) {
        self.done = true;
        if self.opts.auto_stop && self.jogging {
            self.jogging = false;
            ev.push(Event::EndJog);
        }
        ev.push(Event::Done { voice });
    }

    fn say(&self, ev: &mut Vec<Event>, line: VoiceLine, flush: bool) {
        ev.push(Event::Say { line, flush });
    }

    /// 階段結束前 5 秒，每秒倒數一次：語音念數字、短嗶一聲。
    fn check_countdown(&mut self, elapsed_ms: u64, ev: &mut Vec<Event>) {
        if !self.opts.auto_advance {
            return;
        }
        let Some(p) = &self.opts.plan else { return };
        let pos = p.position(elapsed_ms);
        let Some(number) = plan::countdown(pos.remaining_ms, p.next(pos.index).is_some()) else {
            self.last_countdown = None;
            return;
        };
        if self.last_countdown == Some(number) {
            return;
        }
        self.last_countdown = Some(number);
        if self.opts.cues {
            ev.push(Event::Beeps(1));
        }
        if self.opts.voice {
            self.say(ev, voice::count(number), true);
        }
    }

    /// 走到新的階段（包含剛開始的第一個階段、以及課表全部完成）就提示一次。
    fn check_stage(&mut self, elapsed_ms: u64, ev: &mut Vec<Event>) {
        let Some(p) = &self.opts.plan else { return };
        let pos = p.position(elapsed_ms);
        if self.last_stage_index == Some(pos.index) {
            return;
        }
        self.last_stage_index = Some(pos.index);
        let stage = pos.stage;
        if self.opts.auto_advance {
            match plan::jog_action(stage.map(|s| s.kind), self.jogging) {
                JogAction::Start => {
                    self.jogging = true;
                    ev.push(Event::StartJog);
                }
                JogAction::End => {
                    self.jogging = false;
                    ev.push(Event::EndJog);
                }
                JogAction::None => {}
            }
        }
        match stage {
            Some(s) => {
                if self.opts.cues {
                    ev.push(Event::Beeps(s.kind.beep_count()));
                    ev.push(Event::Vibrate(s.kind.beep_count()));
                }
                if self.opts.voice {
                    self.say(ev, voice::stage(&s), false);
                }
            }
            None => {
                if !self.done {
                    let v = self.opts.voice.then(voice::plan_done);
                    self.finish_goal(v, ev);
                }
            }
        }
    }

    /// 動作階段換到下一個動作時，念動作名稱與要領。已經按了「開始超慢跑」就不念。
    fn check_move(&mut self, elapsed_ms: u64, ev: &mut Vec<Event>) {
        if !self.opts.voice {
            return;
        }
        let Some(p) = &self.opts.plan else { return };
        let pos = p.position(elapsed_ms);
        let Some(stage) = pos.stage else { return };
        let Some(slot) = moves::slot_in_stage(&stage, stage.ms() - pos.remaining_ms) else { return };
        let key = pos.index as u64 * 100 + slot.index as u64;
        if self.last_move_key == Some(key) {
            return;
        }
        self.last_move_key = Some(key);
        if self.jogging {
            return;
        }
        self.say(ev, voice::mv(slot.mv), false);
    }

    /// 做動作時的口令：換邊、剩 30／10 秒、最後 5 秒讀秒。階段結束前已經有倒數時不重複喊。
    fn check_coach(&mut self, elapsed_ms: u64, ev: &mut Vec<Event>) {
        if !self.opts.voice || !self.opts.coach_voice || self.jogging {
            return;
        }
        let Some(p) = &self.opts.plan else { return };
        let pos = p.position(elapsed_ms);
        let Some(stage) = pos.stage else { return };
        if self.opts.auto_advance && plan::countdown(pos.remaining_ms, p.next(pos.index).is_some()).is_some() {
            return;
        }
        let Some(slot) = moves::slot_in_stage(&stage, stage.ms() - pos.remaining_ms) else { return };
        let Some(cue) = moves::cue_at(&slot) else { return };
        // 停留型動作的口令以「循環」為單位（每一側各喊一次）；反覆型以「整個動作」為單位。
        // 反覆型的循環只有一兩秒，key 帶上循環編號的話，同一個讀秒會因為跨過循環邊界而重複喊。
        let cycle = if moves::is_hold(slot.mv) { slot.cycle } else { 0 };
        let key = ((pos.index as u64 * 100 + slot.index as u64) * 1000 + cycle) * 100 + cue.key();
        if self.last_coach_key == Some(key) {
            return;
        }
        self.last_coach_key = Some(key);
        self.say(ev, voice::coach(&cue), cue.kind == CueKind::Count);
    }

    /// 超慢跑時每 1 到 2 分鐘說一句鼓勵：氣泡一定會出現，語音依設定。快到階段結束要倒數時先不說。
    fn check_cheer(&mut self, elapsed_ms: u64, rand: &mut dyn FnMut() -> f64, ev: &mut Vec<Event>) {
        if !self.jogging || self.jog_sec() < self.next_cheer_at_jog_sec {
            return;
        }
        if self.opts.auto_advance {
            let remaining = self.opts.plan.as_ref().map(|p| p.position(elapsed_ms).remaining_ms);
            if remaining.is_some_and(|r| r > 0 && r < NO_CHEER_BEFORE_STAGE_END_MS) {
                return;
            }
        }
        self.cheer(rand, ev);
        // 下一則間隔 60 到 119 秒
        self.next_cheer_at_jog_sec = self.jog_sec() + 60 + ((rand() * 60.0) as u64).min(59);
    }

    fn cheer(&mut self, rand: &mut dyn FnMut() -> f64, ev: &mut Vec<Event>) {
        let n = voice::CHEERS.len();
        let mut i = ((rand() * n as f64) as usize).min(n - 1);
        if self.last_cheer == Some(i) {
            i = (i + 1) % n;
        }
        self.last_cheer = Some(i);
        ev.push(Event::Cheer(voice::CHEERS[i].to_string()));
        if self.opts.voice && self.opts.cheer_voice {
            self.say(ev, voice::cheer(i), false);
        }
    }

    /// 點氣泡：馬上說一句鼓勵。
    pub fn cheer_now(&mut self, rand: &mut dyn FnMut() -> f64) -> Vec<Event> {
        let mut ev = Vec::new();
        self.cheer(rand, &mut ev);
        ev
    }

    /// 每 5 分鐘念一次經過時間；剛好在階段交界附近就略過，因為階段播報已經說了。
    fn check_elapsed(&mut self, elapsed_ms: u64, ev: &mut Vec<Event>) {
        if !self.opts.voice || !plan::should_announce_elapsed(elapsed_ms, self.last_elapsed_announced) {
            return;
        }
        self.last_elapsed_announced = (elapsed_ms / 60_000) as i32;
        if self.opts.plan.as_ref().is_some_and(|p| p.is_near_boundary(elapsed_ms, 1_500)) {
            return;
        }
        self.say(ev, voice::elapsed(self.last_elapsed_announced as u32), false);
    }
}

/// 給畫面用：目前課表位置的摘要。
pub struct View {
    pub stage_index: usize,
    pub stage: Option<plan::Stage>,
    pub stage_remaining_ms: u64,
    pub next: Option<plan::Stage>,
    pub slot: Option<moves::MoveSlot>,
    pub countdown: Option<u32>,
}

pub fn view(plan: &WorkoutPlan, elapsed_ms: u64, auto_advance: bool) -> View {
    let pos = plan.position(elapsed_ms);
    let slot = pos
        .stage
        .and_then(|s| moves::slot_in_stage(&s, s.ms() - pos.remaining_ms));
    let countdown = if auto_advance { plan::countdown(pos.remaining_ms, plan.next(pos.index).is_some()) } else { None };
    View {
        stage_index: pos.index,
        stage: pos.stage,
        stage_remaining_ms: pos.remaining_ms,
        next: plan.next(pos.index),
        slot,
        countdown,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn opts() -> Options {
        Options {
            plan: Some(WorkoutPlan::of(5, 15, 6, 4)),
            target: Target::None,
            stride_cm: 50,
            cues: true,
            voice: true,
            coach_voice: true,
            cheer_voice: true,
            auto_advance: true,
            auto_stop: true,
        }
    }

    /// 模擬整段運動，每 200 毫秒呼叫一次 tick，收集所有事件（附時間）。
    fn run(o: Options, until_ms: u64) -> Vec<(u64, Event)> {
        let mut e = Engine::new(o);
        let mut r = 0.0_f64;
        let mut rand = move || {
            r = (r + 0.37) % 1.0;
            r
        };
        let mut out = Vec::new();
        let mut t = 0;
        while t <= until_ms {
            for ev in e.tick(t, 180, &mut rand) {
                out.push((t, ev));
            }
            t += 200;
        }
        out
    }

    fn says(ev: &[(u64, Event)]) -> Vec<(u64, String)> {
        ev.iter()
            .filter_map(|(t, e)| match e {
                Event::Say { line, .. } => Some((*t, line.id.clone())),
                _ => None,
            })
            .collect()
    }

    #[test]
    fn starts_with_stage_cue_and_announcement() {
        let ev = run(opts(), 1_000);
        assert_eq!(ev[0].1, Event::Beeps(1));
        assert_eq!(ev[1].1, Event::Vibrate(1));
        let s = says(&ev);
        assert_eq!(s[0], (0, "stage_warmup".to_string()));
        assert_eq!(s[1], (0, "move_march".to_string()));
    }

    /// 階段倒數的讀秒（階段結束前 5 秒內的 count_5）出現幾次。
    fn stage_countdowns(ev: &[(u64, Event)], boundary_ms: u64) -> usize {
        says(ev)
            .iter()
            .filter(|(t, id)| id == "count_5" && (boundary_ms - 5_000..boundary_ms).contains(t))
            .count()
    }

    #[test]
    fn countdown_five_to_one_before_each_stage() {
        let ev = run(opts(), 31 * 60_000);
        // 暖身、超慢跑、肌力訓練結束前各一組倒數，數字 5 到 1 都有
        for b in [300_000, 1_200_000, 1_560_000] {
            assert_eq!(stage_countdowns(&ev, b), 1, "{b}");
        }
        for n in 1..=5 {
            assert!(says(&ev).iter().any(|(t, id)| *id == format!("count_{n}") && *t < 300_000));
        }
        // 倒數時第一個讀秒會打斷前一句（flush）
        assert!(ev.iter().any(|(_, e)| matches!(e, Event::Say { line, flush: true } if line.id == "count_5")));
        // 每組倒數都有短嗶聲
        assert!(ev.iter().any(|(t, e)| *e == Event::Beeps(1) && *t == 295_000));
    }

    #[test]
    fn jog_starts_and_ends_automatically_with_the_stage() {
        let ev = run(opts(), 31 * 60_000);
        let start = ev.iter().find(|(_, e)| *e == Event::StartJog).unwrap().0;
        let end = ev.iter().find(|(_, e)| *e == Event::EndJog).unwrap().0;
        assert_eq!(start, 300_000);
        assert_eq!(end, 20 * 60_000);
    }

    #[test]
    fn plan_end_is_done_with_voice_once() {
        let ev = run(opts(), 32 * 60_000);
        let done: Vec<_> = ev.iter().filter(|(_, e)| matches!(e, Event::Done { .. })).collect();
        assert_eq!(done.len(), 1);
        assert_eq!(done[0].0, 30 * 60_000);
        match &done[0].1 {
            Event::Done { voice: Some(v) } => assert_eq!(v.id, "plan_done"),
            other => panic!("{other:?}"),
        }
    }

    #[test]
    fn no_move_or_coach_voice_while_jogging_and_no_coach_during_countdown() {
        let ev = run(opts(), 20 * 60_000);
        let s = says(&ev);
        // 超慢跑階段（5–20 分鐘）沒有動作說明與口令
        assert!(!s.iter().any(|(t, id)| (300_000..1_200_000).contains(t) && (id.starts_with("move_") || id.starts_with("coach_"))));
        // 暖身最後 5 秒只有階段倒數那一組讀秒，教練口令不重複
        assert_eq!(stage_countdowns(&ev, 300_000), 1);
    }

    #[test]
    fn cheers_start_after_20_jog_seconds_and_are_spaced() {
        let ev = run(opts(), 12 * 60_000);
        let cheers: Vec<u64> = ev.iter().filter(|(_, e)| matches!(e, Event::Cheer(_))).map(|(t, _)| *t).collect();
        assert!(cheers.len() >= 4, "{cheers:?}");
        assert!(cheers[0] >= 320_000 && cheers[0] < 322_000, "{cheers:?}");
        for w in cheers.windows(2) {
            assert!(w[1] - w[0] >= 59_000 && w[1] - w[0] <= 121_000, "{cheers:?}");
        }
        // 每句鼓勵都有對應語音
        assert!(says(&ev).iter().any(|(_, id)| id.starts_with("cheer_")));
    }

    #[test]
    fn repeat_move_countdown_is_not_repeated_across_cycle_boundaries() {
        // 抬膝踏步每個循環 1.6 秒，讀秒的一秒常常會跨過循環邊界
        let mut o = opts();
        o.auto_advance = false;
        let ev = run(o, 300_000);
        let s = says(&ev);
        for id in ["coach_10s", "count_5", "count_4", "count_3", "count_2", "count_1"] {
            // 暖身 5 分鐘 ÷ 4 個動作，每個動作各喊一次（最後一個動作的倒數也各一次）
            let n = s.iter().filter(|(_, x)| x == id).count();
            assert!(n <= 4, "{id} 喊了 {n} 次");
            if id.starts_with("count_") {
                assert_eq!(n, 4, "{id} 應該每個動作各一次");
            }
        }
    }

    #[test]
    fn elapsed_minutes_are_announced_but_not_next_to_a_stage_boundary() {
        let ev = run(opts(), 29 * 60_000);
        let ids: Vec<String> = says(&ev).into_iter().map(|(_, id)| id).collect();
        // 5 分鐘與 20 分鐘剛好是階段交界，不念；10、15、25 要念
        assert!(!ids.contains(&"elapsed_5".to_string()));
        assert!(!ids.contains(&"elapsed_20".to_string()));
        for m in [10, 15, 25] {
            assert!(ids.contains(&format!("elapsed_{m}")), "elapsed_{m}");
        }
    }

    #[test]
    fn voice_off_means_no_say_events_but_beeps_remain() {
        let mut o = opts();
        o.voice = false;
        let ev = run(o, 400_000);
        assert!(says(&ev).is_empty());
        assert!(ev.iter().any(|(_, e)| *e == Event::Beeps(2)));
    }

    #[test]
    fn manual_mode_without_auto_advance_never_starts_jog_by_itself() {
        let mut o = opts();
        o.auto_advance = false;
        let ev = run(o, 31 * 60_000);
        assert!(!ev.iter().any(|(_, e)| matches!(e, Event::StartJog | Event::EndJog)));
        // 沒有階段倒數的短嗶聲（只剩階段切換的提示音）
        assert_eq!(ev.iter().filter(|(_, e)| *e == Event::Beeps(1)).count(), 1);
    }

    #[test]
    fn without_plan_time_target_ends_the_workout_and_stops_the_jog() {
        let mut o = opts();
        o.plan = None;
        o.target = Target::Minutes(1);
        let mut e = Engine::new(o);
        let mut rand = || 0.5;
        assert!(e.start_jog(0, 180));
        assert!(!e.tick(30_000, 180, &mut rand).iter().any(|x| matches!(x, Event::Done { .. })));
        let ev = e.tick(60_000, 180, &mut rand);
        assert!(ev.ends_with(&[Event::EndJog, Event::Done { voice: None }]), "{ev:?}");
        assert!(e.done());
        assert!(e.tick(61_000, 180, &mut rand).is_empty());
    }

    #[test]
    fn distance_target_uses_estimated_meters_from_jog_time_only() {
        let mut o = opts();
        o.plan = None;
        o.target = Target::Meters(90);
        let mut e = Engine::new(o);
        let mut rand = || 0.5;
        // 沒在跑：時間再久也不會增加距離
        assert!(e.tick(120_000, 180, &mut rand).is_empty());
        assert_eq!(e.meters(), 0.0);
        assert!(e.start_jog(120_000, 180));
        // 180 BPM × 50 公分 ＝ 90 公尺／分鐘
        assert!(!e.tick(150_000, 180, &mut rand).iter().any(|x| matches!(x, Event::Done { .. })));
        let ev = e.tick(180_000, 180, &mut rand);
        assert!(ev.contains(&Event::Done { voice: None }));
        assert!((e.meters() - 90.0).abs() < 0.01);
    }

    #[test]
    fn view_reports_countdown_and_cue() {
        let p = WorkoutPlan::of(5, 15, 6, 4);
        let v = view(&p, 297_000, true);
        assert_eq!(v.stage.unwrap().kind, crate::plan::StageKind::Warmup);
        assert_eq!(v.countdown, Some(3));
        assert_eq!(v.next.unwrap().kind, crate::plan::StageKind::Jog);
        let v = view(&p, 297_000, false);
        assert_eq!(v.countdown, None);
        let done = view(&p, 31 * 60_000, true);
        assert!(done.stage.is_none());
    }
}
