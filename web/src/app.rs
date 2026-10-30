use crate::engine::{self, Engine, Event, Options, Target};
use crate::plan::{self, StageKind, WorkoutPlan};
use crate::stats::{self, DEFAULT_BPM, MAX_BPM, MIN_BPM, Record, Stats, TapTempo, distance, habit};
use crate::voice;
use crate::web::{self, ClickSound};
use leptos::prelude::*;
use serde::{Deserialize, Serialize};
use std::cell::RefCell;
use std::time::Duration;

const KEY_SETTINGS: &str = "slowjog:settings";
const KEY_RECORDS: &str = "slowjog:records";
const VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
enum TargetKind {
    Time,
    Distance,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
struct Settings {
    bpm: u32,
    sound: ClickSound,
    plan_enabled: bool,
    warmup_min: u32,
    jog_min: u32,
    strength_min: u32,
    cooldown_min: u32,
    auto_advance: bool,
    cues: bool,
    voice: bool,
    coach_voice: bool,
    cheer_voice: bool,
    stride_cm: u32,
    weekly_target: u32,
    /// 沒有課表時的目標
    target_kind: TargetKind,
    target_minutes: u32,
    target_meters: u32,
    auto_stop: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            bpm: DEFAULT_BPM,
            sound: ClickSound::Click,
            plan_enabled: true,
            warmup_min: plan::DEFAULT_WARMUP,
            jog_min: plan::DEFAULT_JOG,
            strength_min: plan::DEFAULT_STRENGTH,
            cooldown_min: plan::DEFAULT_COOLDOWN,
            auto_advance: true,
            cues: true,
            voice: true,
            coach_voice: true,
            cheer_voice: true,
            stride_cm: distance::DEFAULT_STRIDE_CM,
            weekly_target: habit::DEFAULT_WEEKLY_TARGET,
            target_kind: TargetKind::Time,
            target_minutes: 30,
            target_meters: 3000,
            auto_stop: true,
        }
    }
}

impl Settings {
    fn plan(&self) -> Option<WorkoutPlan> {
        self.plan_enabled
            .then(|| WorkoutPlan::of(self.warmup_min, self.jog_min, self.strength_min, self.cooldown_min))
            .filter(|p| !p.stages.is_empty())
    }

    fn target(&self) -> Target {
        match self.target_kind {
            TargetKind::Time if self.target_minutes > 0 => Target::Minutes(self.target_minutes),
            TargetKind::Distance if self.target_meters > 0 => Target::Meters(self.target_meters),
            _ => Target::None,
        }
    }

    fn options(&self) -> Options {
        Options {
            plan: self.plan(),
            target: self.target(),
            stride_cm: self.stride_cm,
            cues: self.cues,
            voice: self.voice,
            coach_voice: self.coach_voice,
            cheer_voice: self.cheer_voice,
            auto_advance: self.auto_advance,
            auto_stop: self.auto_stop,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Tab {
    Home,
    History,
    Settings,
}

/// 進行中這次運動的快照，給運動畫面用。
#[derive(Debug, Clone)]
struct Snapshot {
    plan: Option<WorkoutPlan>,
    auto: bool,
    target: Target,
}

struct Run {
    engine: Engine,
    /// 暫停前已經累計的時間。
    acc_ms: u64,
    /// 目前這段開始計時的時間（毫秒）；暫停中為 None。
    resumed_at: Option<f64>,
    started_wall: f64,
}

impl Run {
    fn elapsed(&self) -> u64 {
        self.acc_ms + self.resumed_at.map_or(0, |t| (web::now_ms() - t).max(0.0) as u64)
    }
}

thread_local! {
    static RUN: RefCell<Option<Run>> = const { RefCell::new(None) };
    static TAP: RefCell<TapTempo> = RefCell::new(TapTempo::new());
}

fn with_run<T>(f: impl FnOnce(&mut Run) -> T) -> Option<T> {
    RUN.with(|r| r.borrow_mut().as_mut().map(f))
}

/// 整個 App 共用的狀態，全部都是可複製的 signal。
#[derive(Clone, Copy)]
struct Session {
    settings: RwSignal<Settings>,
    records: RwSignal<Vec<Record>>,
    tab: RwSignal<Tab>,
    /// 運動中畫面
    active: RwSignal<Option<Snapshot>>,
    paused: RwSignal<bool>,
    elapsed: RwSignal<u64>,
    jogging: RwSignal<bool>,
    beat: RwSignal<u64>,
    jog_sec: RwSignal<u64>,
    meters: RwSignal<f64>,
    cheer: RwSignal<(u32, String)>,
    /// 課表做完或達成目標，等使用者選擇儲存結束或繼續
    done: RwSignal<bool>,
    ending: RwSignal<bool>,
    /// 首頁正在試聽節拍
    preview: RwSignal<bool>,
}

impl Session {
    fn new() -> Self {
        let settings: Settings = web::load(KEY_SETTINGS).and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        let records: Vec<Record> = web::load(KEY_RECORDS).and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        web::set_bpm(settings.bpm);
        web::set_click_sound(settings.sound);
        Self {
            settings: RwSignal::new(settings),
            records: RwSignal::new(records),
            tab: RwSignal::new(Tab::Home),
            active: RwSignal::new(None),
            paused: RwSignal::new(false),
            elapsed: RwSignal::new(0),
            jogging: RwSignal::new(false),
            beat: RwSignal::new(0),
            jog_sec: RwSignal::new(0),
            meters: RwSignal::new(0.0),
            cheer: RwSignal::new((0, String::new())),
            done: RwSignal::new(false),
            ending: RwSignal::new(false),
            preview: RwSignal::new(false),
        }
    }

    fn update_settings(&self, f: impl FnOnce(&mut Settings)) {
        self.settings.update(f);
        let s = self.settings.get_untracked();
        web::save(KEY_SETTINGS, &serde_json::to_string(&s).unwrap_or_default());
        web::set_click_sound(s.sound);
    }

    fn bpm(&self) -> u32 {
        self.settings.with_untracked(|s| s.bpm)
    }

    fn set_bpm(&self, bpm: u32) {
        let new = bpm.clamp(MIN_BPM, MAX_BPM);
        let old = self.bpm();
        // 先用舊的節拍結算到現在，距離才不會算錯
        with_run(|r| {
            let e = r.elapsed();
            r.engine.accrue(e, old);
        });
        self.update_settings(|s| s.bpm = new);
        web::set_bpm(new);
    }

    fn tap_tempo(&self) {
        if let Some(bpm) = TAP.with(|t| t.borrow_mut().tap(web::now_ms())) {
            self.set_bpm(bpm);
        }
        // 讓使用者點一下就聽到聲音，知道有反應
        web::preview_click(self.settings.get_untracked().sound);
    }

    fn start_metronome(&self) {
        let beat = self.beat;
        web::set_bpm(self.bpm());
        web::start_metronome(move |b| beat.set(b));
    }

    fn toggle_preview(&self) {
        if self.preview.get_untracked() {
            web::stop_metronome();
            self.preview.set(false);
        } else {
            self.start_metronome();
            self.preview.set(true);
        }
    }

    fn start(&self) {
        if self.preview.get_untracked() {
            web::stop_metronome();
            self.preview.set(false);
        }
        let st = self.settings.get_untracked();
        let opts = st.options();
        let snap = Snapshot { plan: opts.plan.clone(), auto: opts.auto_advance && opts.plan.is_some(), target: opts.target };
        web::set_bpm(st.bpm);
        web::set_click_sound(st.sound);
        // 在使用者點擊當下建立並啟動 AudioContext，瀏覽器才會允許出聲
        web::audio_context();
        web::keep_awake();
        RUN.with(|r| {
            *r.borrow_mut() = Some(Run {
                engine: Engine::new(opts),
                acc_ms: 0,
                resumed_at: Some(web::now_ms()),
                started_wall: web::now_ms(),
            });
        });
        self.elapsed.set(0);
        self.jogging.set(false);
        self.paused.set(false);
        self.done.set(false);
        self.ending.set(false);
        self.beat.set(0);
        self.jog_sec.set(0);
        self.meters.set(0.0);
        self.cheer.set((0, String::new()));
        self.active.set(Some(snap));
        web::scroll_top();
        self.tick();
    }

    fn tick(&self) {
        if self.active.with_untracked(|a| a.is_none()) || self.paused.get_untracked() {
            return;
        }
        let bpm = self.bpm();
        let mut rand = web::random;
        let out = with_run(|r| {
            let e = r.elapsed();
            let ev = r.engine.tick(e, bpm, &mut rand);
            (e, ev, r.engine.jogging(), r.engine.jog_sec(), r.engine.meters())
        });
        let Some((elapsed, events, jogging, jog_sec, meters)) = out else { return };
        self.elapsed.set(elapsed);
        self.jog_sec.set(jog_sec);
        self.meters.set(meters);
        self.apply(events);
        self.sync_jog(jogging);
    }

    /// 引擎的超慢跑狀態改變了（自動接續或手動），讓節拍器和畫面跟上。
    fn sync_jog(&self, jogging: bool) {
        if self.jogging.get_untracked() != jogging {
            self.jogging.set(jogging);
        }
    }

    fn apply(&self, events: Vec<Event>) {
        for ev in events {
            match ev {
                Event::Say { line, flush } => web::say(&line.id, &line.fallback, flush),
                Event::Beeps(n) => web::beeps(n),
                Event::Vibrate(n) => web::vibrate(n),
                Event::StartJog => {
                    self.start_metronome();
                    self.jogging.set(true);
                }
                Event::EndJog => {
                    web::stop_metronome();
                    self.jogging.set(false);
                }
                Event::Cheer(text) => self.cheer.update(|c| *c = (c.0 + 1, text)),
                Event::Done { voice } => {
                    let ms = web::chime();
                    web::vibrate(3);
                    self.done.set(true);
                    if let Some(v) = voice {
                        set_timeout(move || web::say(&v.id, &v.fallback, false), Duration::from_millis(u64::from(ms) + 300));
                    }
                }
            }
        }
    }

    fn toggle_jog(&self) {
        let bpm = self.bpm();
        let now_jogging = with_run(|r| {
            let e = r.elapsed();
            if r.engine.jogging() {
                r.engine.end_jog(e, bpm);
            } else {
                r.engine.start_jog(e, bpm);
            }
            r.engine.jogging()
        });
        match now_jogging {
            Some(true) => {
                if !self.paused.get_untracked() {
                    self.start_metronome();
                }
            }
            Some(false) => web::stop_metronome(),
            None => {}
        }
        if let Some(j) = now_jogging {
            self.jogging.set(j);
        }
    }

    fn toggle_pause(&self) {
        let bpm = self.bpm();
        let paused = !self.paused.get_untracked();
        with_run(|r| {
            if paused {
                let e = r.elapsed();
                r.engine.pause(e, bpm);
                r.acc_ms = e;
                r.resumed_at = None;
            } else {
                r.resumed_at = Some(web::now_ms());
            }
        });
        self.paused.set(paused);
        if paused {
            web::stop_metronome();
            web::stop_speaking();
        } else if self.jogging.get_untracked() {
            self.start_metronome();
        }
    }

    fn cheer_now(&self) {
        if self.paused.get_untracked() {
            return;
        }
        let mut rand = web::random;
        if let Some(ev) = with_run(|r| r.engine.cheer_now(&mut rand)) {
            self.apply(ev);
        }
    }

    /// 結束運動。`save` 為 true 時寫入一筆紀錄；整段時長不足 1 秒不寫入。
    fn finish(&self, save: bool) {
        let bpm = self.bpm();
        let run = RUN.with(|r| r.borrow_mut().take());
        web::stop_metronome();
        web::stop_speaking();
        web::release_awake();
        let mut completed = false;
        if let Some(mut run) = run {
            let elapsed = run.elapsed();
            run.engine.accrue(elapsed, bpm);
            let duration_sec = (elapsed / 1000) as u32;
            if save && duration_sec > 0 {
                // 達成這次的目標（課表走完，或時間／距離目標到了）並儲存，才算「完成」；統計在最後才回報
                if run.engine.done() {
                    completed = true;
                } else {
                    web::chime();
                }
                let rec = Record {
                    date: web::day_of(run.started_wall),
                    started_at: run.started_wall,
                    duration_sec,
                    jog_sec: run.engine.jog_sec() as u32,
                    bpm,
                };
                self.records.update(|r| r.push(rec));
                web::save(KEY_RECORDS, &self.records.with_untracked(|r| serde_json::to_string(r).unwrap_or_default()));
            }
        }
        self.active.set(None);
        self.paused.set(false);
        self.jogging.set(false);
        self.done.set(false);
        self.ending.set(false);
        self.tab.set(Tab::Home);
        web::scroll_top();
        // 匿名統計放在最後：前面該做的（紀錄、收尾）都做完了才回報，回報出什麼事都不影響使用者
        if completed {
            web::report_completion("slowjog");
        }
    }
}

fn mmss(secs: u64) -> String {
    format!("{}:{:02}", secs / 60, secs % 60)
}

fn km(meters: f64) -> String {
    format!("{:.2} 公里", meters / 1000.0)
}

/// 階段剩餘毫秒轉成顯示用秒數（無條件進位，剛好到 0 才顯示 0:00）。
fn ceil_sec(ms: u64) -> u64 {
    ms.div_ceil(1000)
}

#[component]
pub fn App() -> impl IntoView {
    let s = Session::new();
    provide_context(s);
    set_interval(move || s.tick(), Duration::from_millis(200));

    view! {
        <div class="app">
            <main class="wrap">
                {move || {
                    if s.active.with(|a| a.is_some()) {
                        view! { <Workout/> }.into_any()
                    } else {
                        match s.tab.get() {
                            Tab::Home => view! { <Home/> }.into_any(),
                            Tab::History => view! { <History/> }.into_any(),
                            Tab::Settings => view! { <SettingsView/> }.into_any(),
                        }
                    }
                }}
            </main>
            <Show when=move || s.active.with(|a| a.is_none())>
                <nav class="tabs" aria-label="主選單">
                    <TabButton tab=Tab::Home label="首頁" icon="🏠"/>
                    <TabButton tab=Tab::History label="紀錄" icon="📅"/>
                    <TabButton tab=Tab::Settings label="設定" icon="⚙️"/>
                </nav>
            </Show>
        </div>
    }
}

#[component]
fn TabButton(tab: Tab, label: &'static str, icon: &'static str) -> impl IntoView {
    let s = expect_context::<Session>();
    view! {
        <button
            class="tab"
            class:on=move || s.tab.get() == tab
            on:click=move |_| {
                s.tab.set(tab);
                web::scroll_top();
            }
        >
            <span class="ico" aria-hidden="true">{icon}</span>
            <span>{label}</span>
        </button>
    }
}

#[component]
fn Toggle(label: &'static str, #[prop(optional)] hint: &'static str, on: Signal<bool>, #[prop(into)] set: Callback<bool>) -> impl IntoView {
    view! {
        <button class="toggle" role="switch" aria-checked=move || on.get().to_string() on:click=move |_| set.run(!on.get_untracked())>
            <span class="tt">
                <span>{label}</span>
                {(!hint.is_empty()).then(|| view! { <small>{hint}</small> })}
            </span>
            <span class="sw" class:on=move || on.get()></span>
        </button>
    }
}

#[component]
fn Stepper(
    label: &'static str,
    value: Signal<u32>,
    min: u32,
    max: u32,
    step: u32,
    unit: &'static str,
    #[prop(into)] set: Callback<u32>,
) -> impl IntoView {
    view! {
        <div class="stepper">
            <span class="lbl">{label}</span>
            <button
                class="rnd"
                aria-label=format!("減少{label}")
                on:click=move |_| set.run(value.get_untracked().saturating_sub(step).max(min))
            >"−"</button>
            <span class="val">{move || value.get()}<small>{unit}</small></span>
            <button
                class="rnd"
                aria-label=format!("增加{label}")
                on:click=move |_| set.run((value.get_untracked() + step).min(max))
            >"+"</button>
        </div>
    }
}

// ───────────────────────── 首頁 ─────────────────────────

#[component]
fn Home() -> impl IntoView {
    let s = expect_context::<Session>();

    let stats = move || s.records.with(|r| Stats::compute(r, web::today()));
    let days = move || s.records.with(|r| stats::workout_days(r));
    let score = move || (habit::score(&days(), web::today(), s.settings.with(|x| x.weekly_target)) * 100.0).round() as u32;
    let week = move || habit::week_count(&days(), web::today());

    let bpm = Signal::derive(move || s.settings.with(|x| x.bpm));

    view! {
        <header class="head">
            <h1>"SlowJog"<small>"超慢跑"</small></h1>
            <p class="sub">"早晨 30 分鐘運動，跟著節拍慢慢跑"</p>
        </header>

        <section class="card hero">
            <div class="hero-main">
                <span class="badge" class:ok=move || stats().done_today>
                    {move || if stats().done_today { "今天已運動 ✓" } else { "今天還沒運動" }}
                </span>
                <div class="streak">
                    <b>{move || stats().streak_days}</b>
                    <span>"天連續"</span>
                </div>
            </div>
            <div class="mini">
                <div><b>{move || format!("{} / {}", week(), s.settings.with(|x| x.weekly_target))}</b><span>"本週天數"</span></div>
                <div><b>{move || format!("{}%", score())}</b><span>"習慣分數"</span></div>
                <div><b>{move || mmss(stats().total_jog_sec / 60 * 60).trim_end_matches(":00").to_string()}<small>" 分"</small></b><span>"累計超慢跑"</span></div>
            </div>
        </section>

        <PlanCard/>

        <section class="card">
            <h2>"節拍"</h2>
            <div class="bpm-row">
                <button class="rnd big" aria-label="減少節拍" on:click=move |_| s.set_bpm(s.bpm().saturating_sub(1))>"−"</button>
                <div class="bpm"><b>{move || bpm.get()}</b><small>"BPM"</small></div>
                <button class="rnd big" aria-label="增加節拍" on:click=move |_| s.set_bpm(s.bpm() + 1)>"+"</button>
            </div>
            <input
                class="range"
                type="range"
                min=MIN_BPM
                max=MAX_BPM
                aria-label="節拍速度"
                prop:value=move || bpm.get()
                on:input=move |ev| {
                    if let Ok(v) = event_target_value(&ev).parse::<u32>() {
                        s.set_bpm(v);
                    }
                }
            />
            <p class="hint">"超慢跑建議每分鐘 180 步，慢到還能聊天就對了。"</p>
            <div class="row">
                <button class="btn ghost" on:click=move |_| s.tap_tempo()>"點拍子定速"</button>
                <button class="btn ghost" class:on=move || s.preview.get() on:click=move |_| s.toggle_preview()>
                    {move || if s.preview.get() { "停止試聽" } else { "試聽節拍" }}
                </button>
            </div>
        </section>

        <button class="btn start" on:click=move |_| s.start()>
            "開始運動"
        </button>
        <p class="foot">{format!("SlowJog 網頁版 v{VERSION}")}</p>
    }
}

#[component]
fn PlanCard() -> impl IntoView {
    let s = expect_context::<Session>();
    view! {
        <section class="card">
            {move || {
                let st = s.settings.get();
                match st.plan() {
                    Some(p) => {
                        let total = p.total_ms() as f64;
                        let segs = p
                            .stages
                            .iter()
                            .map(|g| {
                                view! {
                                    <span class=format!("seg k-{}", g.kind.id()) style=format!("flex:{}", g.minutes)></span>
                                }
                            })
                            .collect_view();
                        let items = p
                            .stages
                            .iter()
                            .map(|g| {
                                view! {
                                    <li>
                                        <i class=format!("dot k-{}", g.kind.id())></i>
                                        <span>{g.kind.name()}</span>
                                        <b>{format!("{} 分", g.minutes)}</b>
                                    </li>
                                }
                            })
                            .collect_view();
                        view! {
                            <h2>"今天的課表"<small>{format!("共 {} 分鐘", (total / 60_000.0).round())}</small></h2>
                            <div class="bar">{segs}</div>
                            <ul class="stages">{items}</ul>
                        }
                        .into_any()
                    }
                    None => {
                        let goal = match st.target() {
                            Target::Minutes(m) => format!("目標：運動 {m} 分鐘"),
                            Target::Meters(m) => format!("目標：超慢跑約 {}（估算）", km(f64::from(m))),
                            Target::None => "沒有設定目標".to_string(),
                        };
                        view! {
                            <h2>"自由運動"</h2>
                            <p class="hint">{goal}</p>
                            <p class="hint">"沒有啟用課表，開始後請自己按「開始超慢跑」。可以在「設定」打開課表。"</p>
                        }
                        .into_any()
                    }
                }
            }}
        </section>
    }
}

// ───────────────────────── 運動中 ─────────────────────────

#[component]
fn Workout() -> impl IntoView {
    let s = expect_context::<Session>();
    let snap = move || s.active.get();

    let stage_view = move || {
        let a = snap()?;
        let p = a.plan?;
        Some((engine::view(&p, s.elapsed.get(), a.auto), p))
    };

    // 大計時：有課表顯示這一階段剩餘時間；沒課表顯示已運動時間
    let main_time = move || match stage_view() {
        Some((v, _)) if v.stage.is_some() => mmss(ceil_sec(v.stage_remaining_ms)),
        Some(_) => "0:00".to_string(),
        None => mmss(s.elapsed.get() / 1000),
    };

    let title = move || match stage_view() {
        Some((v, p)) => match v.stage {
            Some(g) => format!("{}　{}/{}", g.kind.name(), v.stage_index + 1, p.stages.len()),
            None => "課表完成".to_string(),
        },
        None => "自由運動".to_string(),
    };

    let total_pct = move || {
        let a = snap();
        let total = a.as_ref().and_then(|a| a.plan.as_ref()).map(|p| p.total_ms()).or_else(|| match a.map(|a| a.target) {
            Some(Target::Minutes(m)) => Some(u64::from(m) * 60_000),
            _ => None,
        });
        total.map_or(0.0, |t| (s.elapsed.get() as f64 / t as f64 * 100.0).min(100.0))
    };

    view! {
        <div class="wk">
            <div class="wk-top">
                <span class=move || {
                    let kind = stage_view().and_then(|(v, _)| v.stage).map(|g| g.kind.id()).unwrap_or("free");
                    format!("chip k-{kind}")
                }>{title}</span>
                <span class="total">{move || format!("已運動 {}", mmss(s.elapsed.get() / 1000))}</span>
            </div>

            <div class="clock" class:paused=move || s.paused.get()>
                <b>{main_time}</b>
                <span>{move || if stage_view().is_some() { "這個階段還剩" } else { "已運動" }}</span>
            </div>
            <div class="bar thin"><span class="fill" style:width=move || format!("{:.1}%", total_pct())></span></div>

            {move || {
                stage_view().and_then(|(v, _)| v.countdown).map(|n| view! { <div class="count" aria-live="assertive">{n}</div> })
            }}

            <section class="card main">
                {move || {
                    if s.jogging.get() {
                        view! { <JogPanel/> }.into_any()
                    } else if let Some(slot) = stage_view().and_then(|(v, _)| v.slot) {
                        let side = (slot.mv.alternates_sides() && crate::moves::is_hold(slot.mv))
                            .then(|| if slot.mirrored { "換另一側" } else { "先做第一側" });
                        view! {
                            <div class="move">
                                <small>{format!("動作 {}/{}", slot.index + 1, slot.count)}</small>
                                <h2>{slot.mv.name_zh()}</h2>
                                <p>{slot.mv.tip()}</p>
                                {side.map(|t| view! { <span class="side">{t}</span> })}
                                {slot.next.map(|n| view! { <p class="next">{format!("下一個：{}", n.name_zh())}</p> })}
                            </div>
                        }
                        .into_any()
                    } else {
                        let jog_stage = stage_view().and_then(|(v, _)| v.stage).is_some_and(|g| g.kind == StageKind::Jog);
                        let msg = if s.done.get() {
                            "太棒了，今天的運動完成了！"
                        } else if jog_stage {
                            "超慢跑階段，按下「開始超慢跑」開始節拍。"
                        } else {
                            "準備好了就按下「開始超慢跑」，節拍會響起。"
                        };
                        view! { <div class="move"><p>{msg}</p></div> }.into_any()
                    }
                }}
            </section>

            {move || {
                stage_view().and_then(|(v, _)| v.next).map(|n| {
                    view! { <p class="upnext">{format!("接下來：{}　{} 分鐘", n.kind.name(), n.minutes)}</p> }
                })
            }}

            <div class="ctrl">
                <button class="btn ghost" on:click=move |_| s.toggle_pause()>
                    {move || if s.paused.get() { "繼續" } else { "暫停" }}
                </button>
                <button class="btn primary" on:click=move |_| s.toggle_jog() disabled=move || s.paused.get()>
                    {move || if s.jogging.get() { "結束超慢跑" } else { "開始超慢跑" }}
                </button>
                <button class="btn danger" on:click=move |_| s.ending.set(true)>"結束"</button>
            </div>

            <Show when=move || s.jogging.get() || s.paused.get()>
                <div class="bpm-mini">
                    <button class="rnd" aria-label="減少節拍" on:click=move |_| s.set_bpm(s.bpm().saturating_sub(1))>"−"</button>
                    <span>{move || format!("{} BPM", s.settings.with(|x| x.bpm))}</span>
                    <button class="rnd" aria-label="增加節拍" on:click=move |_| s.set_bpm(s.bpm() + 1)>"+"</button>
                </div>
            </Show>

            <Show when=move || s.done.get()>
                <div class="modal" role="dialog" aria-modal="true">
                    <div class="sheet">
                        <h2>"完成了 🎉"</h2>
                        <p>{move || format!("總共運動 {}，超慢跑 {}。", mmss(s.elapsed.get() / 1000), mmss(s.jog_sec.get()))}</p>
                        <button class="btn primary" on:click=move |_| s.finish(true)>"儲存並結束"</button>
                        <button class="btn ghost" on:click=move |_| s.done.set(false)>"繼續運動"</button>
                    </div>
                </div>
            </Show>

            <Show when=move || s.ending.get() && !s.done.get()>
                <div class="modal" role="dialog" aria-modal="true">
                    <div class="sheet">
                        <h2>"結束這次運動？"</h2>
                        <p>{move || format!("已運動 {}。", mmss(s.elapsed.get() / 1000))}</p>
                        <button class="btn primary" on:click=move |_| s.finish(true)>"儲存並結束"</button>
                        <button class="btn danger" on:click=move |_| s.finish(false)>"放棄這次紀錄"</button>
                        <button class="btn ghost" on:click=move |_| s.ending.set(false)>"繼續運動"</button>
                    </div>
                </div>
            </Show>
        </div>
    }
}

#[component]
fn JogPanel() -> impl IntoView {
    let s = expect_context::<Session>();
    let target = move || s.active.with(|a| a.as_ref().map(|a| a.target));
    let goal_left = move || match target() {
        Some(Target::Meters(m)) => Some(format!("還差約 {}", km((f64::from(m) - s.meters.get()).max(0.0)))),
        _ => None,
    };
    view! {
        <div class="jog">
            <button
                class="orb"
                class:a=move || s.beat.get() % 2 == 0
                class:b=move || s.beat.get() % 2 == 1
                aria-label="點一下聽一句鼓勵"
                on:click=move |_| s.cheer_now()
            >
                <span>"🏃"</span>
            </button>
            <div class="bpm"><b>{move || s.settings.with(|x| x.bpm)}</b><small>"BPM"</small></div>
            <div class="mini">
                <div><b>{move || mmss(s.jog_sec.get())}</b><span>"超慢跑時間"</span></div>
                <div><b>{move || km(s.meters.get())}</b><span>"估算距離"</span></div>
            </div>
            {move || goal_left().map(|t| view! { <p class="hint">{t}</p> })}
            <Show when=move || !s.cheer.get().1.is_empty()>
                <p class="bubble" role="status" data-seq=move || s.cheer.get().0>{move || s.cheer.get().1}</p>
            </Show>
        </div>
    }
}

// ───────────────────────── 紀錄 ─────────────────────────

const WEEKDAYS: [&str; 7] = ["一", "二", "三", "四", "五", "六", "日"];

#[component]
fn History() -> impl IntoView {
    let s = expect_context::<Session>();
    let stats = move || s.records.with(|r| Stats::compute(r, web::today()));
    let days = move || s.records.with(|r| stats::workout_days(r));

    let week_cells = move || {
        let today = web::today();
        let monday = today - stats::weekday_mon0(today);
        let d = days();
        (0..7)
            .map(|i| {
                let day = monday + i;
                let (done, is_today, future) = (d.contains(&day), day == today, day > today);
                view! {
                    <div class="wd" class:done=done class:today=is_today class:future=future>
                        <span>{WEEKDAYS[i as usize]}</span>
                        <i></i>
                    </div>
                }
            })
            .collect_view()
    };

    let rows = move || {
        s.records.with(|r| {
            r.iter()
                .rev()
                .take(30)
                .map(|rec| {
                    let (y, m, d) = stats::civil_from_days(rec.date);
                    let wd = WEEKDAYS[stats::weekday_mon0(rec.date) as usize];
                    view! {
                        <li>
                            <span class="date">{format!("{y}/{m:02}/{d:02}（{wd}）")}</span>
                            <span>{format!("全程 {}", mmss(u64::from(rec.duration_sec)))}</span>
                            <span>{format!("跑 {}", mmss(u64::from(rec.jog_sec)))}</span>
                            <span class="bpmtag">{format!("{} BPM", rec.bpm)}</span>
                        </li>
                    }
                })
                .collect_view()
        })
    };

    view! {
        <header class="head"><h1>"練習紀錄"</h1></header>
        <section class="card">
            <div class="mini four">
                <div><b>{move || stats().streak_days}</b><span>"連續天數"</span></div>
                <div><b>{move || stats().count}</b><span>"運動次數"</span></div>
                <div><b>{move || stats().total_duration_sec / 60}<small>" 分"</small></b><span>"累計運動"</span></div>
                <div><b>{move || stats().total_jog_sec / 60}<small>" 分"</small></b><span>"累計超慢跑"</span></div>
            </div>
            <h2>"本週"</h2>
            <div class="week">{week_cells}</div>
        </section>
        <section class="card">
            <h2>"最近的運動"</h2>
            <Show when=move || s.records.with(|r| !r.is_empty()) fallback=|| view! { <p class="hint">"還沒有紀錄，去運動一下吧！"</p> }>
                <ul class="list">{rows}</ul>
            </Show>
        </section>
        <div class="row">
            <button
                class="btn ghost"
                disabled=move || s.records.with(|r| r.is_empty())
                on:click=move |_| web::download_text("slowjog-records.csv", "text/csv", &s.records.with(|r| stats::to_csv(r)))
            >"匯出 CSV"</button>
            <button
                class="btn ghost danger-text"
                disabled=move || s.records.with(|r| r.is_empty())
                on:click=move |_| {
                    if web::confirm("要清除全部運動紀錄嗎？這個動作無法復原。") {
                        s.records.set(Vec::new());
                        web::save(KEY_RECORDS, "[]");
                    }
                }
            >"清除全部紀錄"</button>
        </div>
        <p class="foot">"紀錄只存在這個瀏覽器裡，換裝置或清除網站資料就會消失，建議定期匯出。"</p>
    }
}

// ───────────────────────── 設定 ─────────────────────────

#[component]
fn SettingsView() -> impl IntoView {
    let s = expect_context::<Session>();
    let get = move |f: fn(&Settings) -> u32| Signal::derive(move || s.settings.with(f));
    let flag = move |f: fn(&Settings) -> bool| Signal::derive(move || s.settings.with(f));

    view! {
        <header class="head"><h1>"設定"</h1></header>

        <section class="card">
            <h2>"課表"</h2>
            <Toggle label="啟用階段式課表" hint="暖身、超慢跑、肌力訓練、伸展依序進行" on=flag(|x| x.plan_enabled) set=move |v| s.update_settings(|x| x.plan_enabled = v)/>
            <Show when=move || s.settings.with(|x| x.plan_enabled)>
                <Stepper label="暖身" value=get(|x| x.warmup_min) min=0 max=30 step=1 unit=" 分" set=move |v| s.update_settings(|x| x.warmup_min = v)/>
                <Stepper label="超慢跑" value=get(|x| x.jog_min) min=0 max=60 step=1 unit=" 分" set=move |v| s.update_settings(|x| x.jog_min = v)/>
                <Stepper label="肌力訓練" value=get(|x| x.strength_min) min=0 max=30 step=1 unit=" 分" set=move |v| s.update_settings(|x| x.strength_min = v)/>
                <Stepper label="伸展" value=get(|x| x.cooldown_min) min=0 max=30 step=1 unit=" 分" set=move |v| s.update_settings(|x| x.cooldown_min = v)/>
                <p class="hint">"0 分鐘代表略過那個階段。"</p>
                <Toggle label="自動接續" hint="階段結束前倒數 5 秒，自動開始或停止超慢跑" on=flag(|x| x.auto_advance) set=move |v| s.update_settings(|x| x.auto_advance = v)/>
            </Show>
            <Show when=move || !s.settings.with(|x| x.plan_enabled)>
                <div class="seg-btns">
                    <button class:on=move || s.settings.with(|x| x.target_kind == TargetKind::Time) on:click=move |_| s.update_settings(|x| x.target_kind = TargetKind::Time)>"目標：時間"</button>
                    <button class:on=move || s.settings.with(|x| x.target_kind == TargetKind::Distance) on:click=move |_| s.update_settings(|x| x.target_kind = TargetKind::Distance)>"目標：距離"</button>
                </div>
                <Show
                    when=move || s.settings.with(|x| x.target_kind == TargetKind::Time)
                    fallback=move || view! {
                        <Stepper label="超慢跑距離" value=get(|x| x.target_meters) min=0 max=10_000 step=250 unit=" 公尺" set=move |v| s.update_settings(|x| x.target_meters = v)/>
                    }
                >
                    <Stepper label="運動時間" value=get(|x| x.target_minutes) min=0 max=90 step=5 unit=" 分" set=move |v| s.update_settings(|x| x.target_minutes = v)/>
                </Show>
                <p class="hint">"0 代表不設目標。距離是用「超慢跑時間 × 節拍 × 步幅」估算的，不是量出來的。"</p>
            </Show>
            <Toggle label="結束時停止節拍" on=flag(|x| x.auto_stop) set=move |v| s.update_settings(|x| x.auto_stop = v)/>
        </section>

        <section class="card">
            <h2>"提示與語音"</h2>
            <Toggle label="階段提示音與震動" hint="不同階段嗶不同次數（手機瀏覽器才有震動）" on=flag(|x| x.cues) set=move |v| s.update_settings(|x| x.cues = v)/>
            <Toggle label="語音播報" hint="階段開始、動作說明、每 5 分鐘報時、讀秒" on=flag(|x| x.voice) set=move |v| s.update_settings(|x| x.voice = v)/>
            <Toggle label="動作口令" hint="換邊、還有幾秒、最後 5 秒讀秒" on=flag(|x| x.coach_voice) set=move |v| s.update_settings(|x| x.coach_voice = v)/>
            <Toggle label="超慢跑鼓勵語音" hint="每 1 到 2 分鐘一句，點跑步的小圓圈也會說" on=flag(|x| x.cheer_voice) set=move |v| s.update_settings(|x| x.cheer_voice = v)/>
            <button class="btn ghost" on:click=move |_| {
                web::audio_context();
                let l = voice::cheer((web::random() * 24.0) as usize);
                web::say(&l.id, &l.fallback, true);
            }>"試聽語音"</button>
        </section>

        <section class="card">
            <h2>"節拍音色"</h2>
            <div class="seg-btns">
                {ClickSound::ALL
                    .into_iter()
                    .map(|c| {
                        view! {
                            <button
                                class:on=move || s.settings.with(|x| x.sound == c)
                                on:click=move |_| {
                                    s.update_settings(|x| x.sound = c);
                                    web::preview_click(c);
                                }
                            >{c.label()}</button>
                        }
                    })
                    .collect_view()}
            </div>
            <p class="hint">"音色是即時合成的，點選就會試聽。"</p>
        </section>

        <section class="card">
            <h2>"距離估算與每週目標"</h2>
            <Stepper label="步幅" value=get(|x| x.stride_cm) min=distance::MIN_STRIDE_CM max=distance::MAX_STRIDE_CM step=1 unit=" 公分" set=move |v| s.update_settings(|x| x.stride_cm = v)/>
            <p class="hint">"一拍算一步，所以距離 ＝ 超慢跑時間 × 節拍 × 步幅。預設 50 公分只是起始值，可以跑一段已知距離（例如操場一圈 400 公尺）後回頭校正。"</p>
            <Stepper label="每週目標" value=get(|x| x.weekly_target) min=habit::MIN_WEEKLY_TARGET max=habit::MAX_WEEKLY_TARGET step=1 unit=" 天" set=move |v| s.update_settings(|x| x.weekly_target = v)/>
        </section>

        <p class="foot">{format!("SlowJog 網頁版 v{VERSION}　沒有廣告，運動紀錄只存在這個瀏覽器。")}</p>
        <p class="foot">
            "達成目標並儲存時，會匿名記一筆完成次數與當下的小時，不含任何個人資料；"
            <a href="https://cornming.github.io/stats/" target="_blank" rel="noopener">"看大家的統計"</a>
        </p>
    }
}
