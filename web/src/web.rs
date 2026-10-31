//! 瀏覽器 API：語音（預錄檔優先、內建語音備援）、節拍器與提示音（Web Audio）、震動、螢幕常亮、localStorage、下載。
//! 這些物件不是 Send，放在 thread_local（WASM 只有單一執行緒）。

use crate::stats::MAX_BPM;
use js_sys::{Function, Promise, Reflect};
use serde::{Deserialize, Serialize};
use std::cell::{Cell, RefCell};
use std::collections::VecDeque;
use std::rc::Rc;
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::{JsFuture, spawn_local};
use web_sys::{AudioBuffer, AudioContext, HtmlAudioElement, OscillatorType, SpeechSynthesisUtterance};

thread_local! {
    static AUDIO: RefCell<Option<AudioContext>> = const { RefCell::new(None) };
    static NOISE: RefCell<Option<AudioBuffer>> = const { RefCell::new(None) };
    static WAKE: RefCell<Option<JsValue>> = const { RefCell::new(None) };
    static VOICE: RefCell<VoiceState> = RefCell::new(VoiceState::default());
    static METRO: RefCell<Option<Metro>> = const { RefCell::new(None) };
    static BPM: Cell<u32> = const { Cell::new(180) };
    static SOUND: Cell<ClickSound> = const { Cell::new(ClickSound::Click) };
}

/// 整個 App 共用一個 AudioContext。瀏覽器規定要在使用者點擊後才能啟動聲音，所以第一次需要時才建立。
pub fn audio_context() -> Option<AudioContext> {
    AUDIO.with(|a| {
        let mut a = a.borrow_mut();
        if a.is_none() {
            *a = AudioContext::new().ok();
        }
        let ctx = a.clone()?;
        let _ = ctx.resume();
        Some(ctx)
    })
}

// ───────────────────────── 語音 ─────────────────────────

/// 語音佇列：一句念完才念下一句；`flush` 會打斷正在念的並清掉佇列（讀秒才跟得上秒數）。
#[derive(Default)]
struct VoiceState {
    queue: VecDeque<(String, String)>,
    busy: bool,
    /// 每開始念一句就加一，讓晚到的回呼知道這句已經過時。
    seq: u32,
    clip: Option<HtmlAudioElement>,
}

const MAX_QUEUE: usize = 3;
/// 內建語音若一直沒有結束事件（例如瀏覽器沒有中文語音），超過這麼久就放棄這一句。
const SPEECH_TIMEOUT_MS: i32 = 9_000;

/// 念一句提示：有預錄語音檔（voice/<id>.mp3）就播檔案，沒有或播不出來就用內建語音念 `fallback`。
pub fn say(id: &str, fallback: &str, flush: bool) {
    let start_now = VOICE.with(|v| {
        let mut v = v.borrow_mut();
        if flush {
            v.queue.clear();
            v.busy = false;
            v.seq = v.seq.wrapping_add(1);
            if let Some(a) = v.clip.take() {
                let _ = a.pause();
            }
            cancel_speech();
        }
        if v.queue.len() >= MAX_QUEUE {
            v.queue.pop_front();
        }
        v.queue.push_back((id.to_string(), fallback.to_string()));
        !v.busy
    });
    if start_now {
        play_next();
    }
}

fn finish(seq: u32) {
    let cur = VOICE.with(|v| v.borrow().seq);
    if cur == seq {
        play_next();
    }
}

fn play_next() {
    let next = VOICE.with(|v| {
        let mut v = v.borrow_mut();
        match v.queue.pop_front() {
            Some(item) => {
                v.busy = true;
                v.seq = v.seq.wrapping_add(1);
                Some((item, v.seq))
            }
            None => {
                v.busy = false;
                v.clip = None;
                None
            }
        }
    });
    let Some(((id, text), seq)) = next else { return };

    let Ok(audio) = HtmlAudioElement::new_with_src(&format!("voice/{id}.mp3")) else {
        speak(&text, seq);
        return;
    };
    audio.set_volume(0.95);
    // 檔案不存在（404）、解碼失敗、播放被拒絕時只會有一個走到備援：用旗標避免念兩次
    let handled = Rc::new(Cell::new(false));

    let h = handled.clone();
    let on_end = Closure::<dyn FnMut()>::new(move || {
        if !h.replace(true) {
            finish(seq);
        }
    });
    let _ = audio.add_event_listener_with_callback("ended", on_end.as_ref().unchecked_ref());
    on_end.forget();

    let (h, t) = (handled.clone(), text.clone());
    let on_err = Closure::<dyn FnMut()>::new(move || {
        if !h.replace(true) && VOICE.with(|v| v.borrow().seq) == seq {
            speak(&t, seq);
        }
    });
    let _ = audio.add_event_listener_with_callback("error", on_err.as_ref().unchecked_ref());
    on_err.forget();

    let Ok(promise) = audio.play() else {
        handled.set(true);
        speak(&text, seq);
        return;
    };
    VOICE.with(|v| v.borrow_mut().clip = Some(audio));
    spawn_local(async move {
        // 播放被拒絕（例如瀏覽器擋自動播放、檔案壞掉）。被下一句打斷也會失敗，但那時 seq 已經變了
        if JsFuture::from(promise).await.is_err() && !handled.replace(true) && VOICE.with(|v| v.borrow().seq) == seq {
            speak(&text, seq);
        }
    });
}

/// 用瀏覽器內建語音合成念出提示（台灣華語），念完（或逾時、不支援）後接著念佇列的下一句。
fn speak(text: &str, seq: u32) {
    let Some(win) = web_sys::window() else {
        return finish(seq);
    };
    let (Ok(synth), Ok(u)) = (win.speech_synthesis(), SpeechSynthesisUtterance::new_with_text(text)) else {
        return finish(seq);
    };
    u.set_lang("zh-TW");
    u.set_rate(1.0);
    u.set_volume(0.95);
    if let Ok(voices) = Reflect::get(&synth, &"getVoices".into())
        .and_then(|f| f.dyn_into::<Function>().map_err(JsValue::from))
        .and_then(|f| f.call0(&synth))
    {
        let voices: js_sys::Array = voices.unchecked_into();
        let pick = voices.iter().find(|v| {
            Reflect::get(v, &"lang".into())
                .ok()
                .and_then(|l| l.as_string())
                .map(|l| l.replace('_', "-").eq_ignore_ascii_case("zh-TW"))
                .unwrap_or(false)
        });
        if let Some(v) = pick {
            let _ = Reflect::set(&u, &"voice".into(), &v);
        }
    }
    let handled = Rc::new(Cell::new(false));
    for ev in ["end", "error"] {
        let h = handled.clone();
        let cb = Closure::<dyn FnMut()>::new(move || {
            if !h.replace(true) {
                finish(seq);
            }
        });
        let _ = u.add_event_listener_with_callback(ev, cb.as_ref().unchecked_ref());
        cb.forget();
    }
    let h = handled;
    let timeout = Closure::once_into_js(move || {
        if !h.replace(true) {
            finish(seq);
        }
    });
    let _ = win.set_timeout_with_callback_and_timeout_and_arguments_0(timeout.unchecked_ref(), SPEECH_TIMEOUT_MS);
    synth.cancel();
    synth.speak(&u);
}

fn cancel_speech() {
    if let Some(Ok(s)) = web_sys::window().map(|w| w.speech_synthesis()) {
        s.cancel();
    }
}

pub fn stop_speaking() {
    VOICE.with(|v| {
        let mut v = v.borrow_mut();
        v.queue.clear();
        v.busy = false;
        v.seq = v.seq.wrapping_add(1);
        if let Some(a) = v.clip.take() {
            let _ = a.pause();
        }
    });
    cancel_speech();
}

// ───────────────────────── 聲音（Web Audio） ─────────────────────────

/// 節拍音色。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ClickSound {
    Click,
    Clap,
    Snap,
}

impl ClickSound {
    pub const ALL: [ClickSound; 3] = [Self::Click, Self::Clap, Self::Snap];

    pub fn label(self) -> &'static str {
        match self {
            Self::Click => "節拍器（答）",
            Self::Clap => "拍手",
            Self::Snap => "彈指",
        }
    }
}

fn noise_buffer(ctx: &AudioContext) -> Option<AudioBuffer> {
    NOISE.with(|n| {
        let mut n = n.borrow_mut();
        if n.is_none() {
            let len = (ctx.sample_rate() * 0.2) as u32;
            let buf = ctx.create_buffer(1, len, ctx.sample_rate()).ok()?;
            let mut seed: u32 = 12345;
            let data: Vec<f32> = (0..len)
                .map(|_| {
                    seed = seed.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                    (seed >> 8) as f32 / 8_388_608.0 - 1.0
                })
                .collect();
            buf.copy_to_channel(&data, 0).ok()?;
            *n = Some(buf);
        }
        n.clone()
    })
}

/// 在 `when`（AudioContext 時間）響一下指定音色。
fn click_at(ctx: &AudioContext, sound: ClickSound, when: f64) {
    let Ok(master) = ctx.create_gain() else { return };
    let _ = master.connect_with_audio_node(&ctx.destination());
    let g = master.gain();
    match sound {
        ClickSound::Click => {
            let Ok(osc) = ctx.create_oscillator() else { return };
            osc.set_type(OscillatorType::Sine);
            osc.frequency().set_value(1000.0);
            let _ = g.set_value_at_time(0.8, when);
            let _ = g.exponential_ramp_to_value_at_time(0.001, when + 0.04);
            let _ = osc.connect_with_audio_node(&master);
            let _ = osc.start_with_when(when);
            let _ = osc.stop_with_when(when + 0.05);
        }
        ClickSound::Clap | ClickSound::Snap => {
            let (freq, q, burst_offsets, decay): (f32, f32, &[f64], f64) = if sound == ClickSound::Clap {
                (1500.0, 0.8, &[0.0, 0.008, 0.016], 0.06)
            } else {
                (2800.0, 2.0, &[0.0], 0.035)
            };
            let Some(buf) = noise_buffer(ctx) else { return };
            let Ok(filter) = ctx.create_biquad_filter() else { return };
            filter.set_type(web_sys::BiquadFilterType::Bandpass);
            filter.frequency().set_value(freq);
            filter.q().set_value(q);
            let _ = filter.connect_with_audio_node(&master);
            let _ = g.set_value_at_time(1.0, when);
            let _ = g.exponential_ramp_to_value_at_time(0.001, when + decay);
            for off in burst_offsets {
                let Ok(src) = ctx.create_buffer_source() else { return };
                src.set_buffer(Some(&buf));
                let _ = src.connect_with_audio_node(&filter);
                let _ = src.start_with_when(when + off);
            }
        }
    }
}

/// 試聽一下節拍音色。
pub fn preview_click(sound: ClickSound) {
    if let Some(ctx) = audio_context() {
        click_at(&ctx, sound, ctx.current_time());
    }
}

/// 一個短音：正弦波，快速淡入、指數淡出。
fn tone(ctx: &AudioContext, freq: f32, when: f64, dur: f64, vol: f32) {
    let (Ok(osc), Ok(gain)) = (ctx.create_oscillator(), ctx.create_gain()) else { return };
    osc.set_type(OscillatorType::Sine);
    osc.frequency().set_value(freq);
    let g = gain.gain();
    let _ = g.set_value_at_time(0.0, when);
    let _ = g.linear_ramp_to_value_at_time(vol, when + 0.01);
    let _ = g.exponential_ramp_to_value_at_time(0.0001, when + dur);
    let _ = osc.connect_with_audio_node(&gain);
    let _ = gain.connect_with_audio_node(&ctx.destination());
    let _ = osc.start_with_when(when);
    let _ = osc.stop_with_when(when + dur + 0.02);
}

/// 階段提示：`n` 聲短嗶（暖身 1、超慢跑 2、肌力 3、伸展 4）。
pub fn beeps(n: u32) {
    if let Some(ctx) = audio_context() {
        let t = ctx.current_time() + 0.02;
        for i in 0..n {
            tone(&ctx, 880.0, t + f64::from(i) * 0.2, 0.12, 0.35);
        }
    }
}

/// 完成音效：由低到高的四個音加最後的和弦，約 1.6 秒。回傳大約持續幾毫秒。
pub fn chime() -> u32 {
    let Some(ctx) = audio_context() else { return 0 };
    let t = ctx.current_time() + 0.02;
    for (i, f) in [523.25_f32, 659.25, 783.99, 1046.5].into_iter().enumerate() {
        tone(&ctx, f, t + i as f64 * 0.16, 0.6, 0.3);
    }
    for f in [523.25_f32, 659.25, 783.99, 1046.5] {
        tone(&ctx, f, t + 0.7, 0.9, 0.18);
    }
    1_700
}

pub fn vibrate(count: u32) {
    let Some(win) = web_sys::window() else { return };
    let nav = win.navigator();
    let Ok(f) = Reflect::get(&nav, &"vibrate".into()) else { return };
    let Ok(f) = f.dyn_into::<Function>() else { return };
    let pattern = js_sys::Array::new();
    for i in 0..count {
        if i > 0 {
            pattern.push(&JsValue::from(110));
        }
        pattern.push(&JsValue::from(130));
    }
    let _ = f.call1(&nav, &pattern);
}

// ───────────────────────── 節拍器 ─────────────────────────

struct Metro {
    handle: i32,
    next_time: f64,
    beat: u64,
    /// 已排進 Web Audio、還沒響的拍子（時間, 第幾拍），響了才通知畫面閃動。
    pending: VecDeque<(f64, u64)>,
    on_beat: Rc<dyn Fn(u64)>,
    _tick: Closure<dyn FnMut()>,
}

pub fn set_bpm(bpm: u32) {
    BPM.set(bpm.clamp(1, MAX_BPM));
}

pub fn set_click_sound(s: ClickSound) {
    SOUND.set(s);
}

/// 排程間隔與預排時間：分頁在前景時預排 0.2 秒；被背景化時瀏覽器會把計時器降到每秒一次，所以預排 5 秒。
const SCHEDULE_EVERY_MS: i32 = 25;

fn metro_tick() {
    let Some(ctx) = audio_context() else { return };
    let now = ctx.current_time();
    let hidden = web_sys::window()
        .and_then(|w| w.document())
        .map(|d| d.hidden())
        .unwrap_or(false);
    let ahead = if hidden { 5.0 } else { 0.2 };
    let mut fire: Option<(u64, Rc<dyn Fn(u64)>)> = None;
    METRO.with(|m| {
        let mut m = m.borrow_mut();
        let Some(m) = m.as_mut() else { return };
        let period = 60.0 / f64::from(BPM.get());
        let sound = SOUND.get();
        while m.next_time < now + ahead {
            let t = m.next_time.max(now);
            click_at(&ctx, sound, t);
            m.pending.push_back((t, m.beat));
            m.beat += 1;
            m.next_time += period;
        }
        let mut last = None;
        while m.pending.front().is_some_and(|(t, _)| *t <= now) {
            last = m.pending.pop_front().map(|(_, b)| b);
        }
        if let Some(b) = last {
            fire = Some((b, m.on_beat.clone()));
        }
    });
    if let Some((b, f)) = fire {
        f(b);
    }
}

/// 開始節拍。`on_beat` 在每一拍真正響起時被呼叫（畫面閃動用）。已經在跑則略過。
pub fn start_metronome(on_beat: impl Fn(u64) + 'static) {
    if METRO.with(|m| m.borrow().is_some()) {
        return;
    }
    let Some(ctx) = audio_context() else { return };
    let Some(win) = web_sys::window() else { return };
    let tick = Closure::<dyn FnMut()>::new(metro_tick);
    let Ok(handle) = win.set_interval_with_callback_and_timeout_and_arguments_0(tick.as_ref().unchecked_ref(), SCHEDULE_EVERY_MS)
    else {
        return;
    };
    METRO.with(|m| {
        *m.borrow_mut() = Some(Metro {
            handle,
            next_time: ctx.current_time() + 0.05,
            beat: 0,
            pending: VecDeque::new(),
            on_beat: Rc::new(on_beat),
            _tick: tick,
        });
    });
}

pub fn stop_metronome() {
    let m = METRO.with(|m| m.borrow_mut().take());
    if let (Some(m), Some(win)) = (m, web_sys::window()) {
        win.clear_interval_with_handle(m.handle);
    }
}

// ───────────────────────── 螢幕、儲存、下載 ─────────────────────────

/// 運動時讓螢幕保持亮著（Screen Wake Lock API，不支援的瀏覽器會略過）。
pub fn keep_awake() {
    let Some(win) = web_sys::window() else { return };
    let nav = win.navigator();
    let Ok(wl) = Reflect::get(&nav, &"wakeLock".into()) else { return };
    if wl.is_undefined() || wl.is_null() {
        return;
    }
    let Ok(req) = Reflect::get(&wl, &"request".into()) else { return };
    let Ok(req) = req.dyn_into::<Function>() else { return };
    let Ok(p) = req.call1(&wl, &"screen".into()) else { return };
    let Ok(p) = p.dyn_into::<Promise>() else { return };
    spawn_local(async move {
        if let Ok(sentinel) = JsFuture::from(p).await {
            WAKE.with(|w| *w.borrow_mut() = Some(sentinel));
        }
    });
}

pub fn release_awake() {
    WAKE.with(|w| {
        if let Some(s) = w.borrow_mut().take() {
            if let Ok(f) = Reflect::get(&s, &"release".into()).and_then(|f| f.dyn_into::<Function>().map_err(JsValue::from)) {
                let _ = f.call0(&s);
            }
        }
    });
}

pub fn load(key: &str) -> Option<String> {
    web_sys::window()?.local_storage().ok()??.get_item(key).ok()?
}

pub fn save(key: &str, value: &str) {
    if let Some(Ok(Some(s))) = web_sys::window().map(|w| w.local_storage()) {
        let _ = s.set_item(key, value);
    }
}

/// 現在的毫秒時間（Unix epoch）。
pub fn now_ms() -> f64 {
    js_sys::Date::now()
}

/// 今天的日期（本地時間）轉成 epoch day。
pub fn today() -> i64 {
    day_of(js_sys::Date::now())
}

/// 某個時間點（毫秒）所在的本地日期，轉成 epoch day。
pub fn day_of(ms: f64) -> i64 {
    let d = js_sys::Date::new(&JsValue::from_f64(ms));
    crate::stats::days_from_civil(i64::from(d.get_full_year()), i64::from(d.get_month()) + 1, i64::from(d.get_date()))
}

pub fn random() -> f64 {
    js_sys::Math::random()
}

pub fn confirm(msg: &str) -> bool {
    web_sys::window().and_then(|w| w.confirm_with_message(msg).ok()).unwrap_or(false)
}

/// 把文字存成檔案讓使用者下載（用 data: 網址，不需要額外的 Blob API）。
pub fn download_text(filename: &str, mime: &str, text: &str) {
    let Some(doc) = web_sys::window().and_then(|w| w.document()) else { return };
    let Ok(a) = doc.create_element("a") else { return };
    let enc: String = js_sys::encode_uri_component(text).into();
    let _ = a.set_attribute("href", &format!("data:{mime};charset=utf-8,{enc}"));
    let _ = a.set_attribute("download", filename);
    if let Ok(a) = a.dyn_into::<web_sys::HtmlElement>() {
        a.click();
    }
}

pub fn scroll_top() {
    if let Some(w) = web_sys::window() {
        w.scroll_to_with_x_and_y(0.0, 0.0);
    }
}

/// 匿名完成統計的收集網址（Google Apps Script，見 cornming.github.io 的 /stats/）。
const STATS_URL: &str = "https://script.google.com/macros/s/AKfycbzN1cM7oU-eeatBeISxnm3n8CbYiQej-aOfmIGljFUAAFmRtcP-9NruWs8_RMkhsSpkVA/exec";

/// 回報「完成一次」。只送作品名稱、當下的小時（本地時間 0 到 23）、這個瀏覽器第幾次完成的分級
/// （1、2-3、4+），不送任何能識別個人的資料。SlowJog 沒有固定課程，所以課程欄留空。
/// 統計只是附加功能：任何一步出錯都靜靜放棄，絕不能影響使用者繼續使用。
pub fn report_completion(app: &str, nth: &str) {
    let _ = try_report(app, nth);
}

/// 每一步都用 `?` 往外丟：沒有 window、沒有 fetch、fetch 同步丟出例外時只會得到 None。
/// （WASM 一旦 panic 整個頁面就停了，所以這裡不能有 unwrap，也不能用會丟例外的綁定。）
fn try_report(app: &str, nth: &str) -> Option<()> {
    let window = web_sys::window()?;
    let fetch = Reflect::get(&window, &JsValue::from_str("fetch")).ok()?.dyn_into::<Function>().ok()?;
    let hour = js_sys::Date::new_0().get_hours();
    let body = crate::comeback::completion_body(app, "", hour, nth);
    let init = js_sys::Object::new();
    // Apps Script 不支援跨網域預檢，所以用 no-cors 的簡單請求；回應讀不到，也不需要。
    // keepalive：使用者回報後馬上關掉分頁，請求也能送完。
    for (key, value) in [
        ("method", JsValue::from_str("POST")),
        ("mode", JsValue::from_str("no-cors")),
        ("body", JsValue::from_str(&body)),
        ("keepalive", JsValue::TRUE),
    ] {
        Reflect::set(&init, &JsValue::from_str(key), &value).ok()?;
    }
    let promise = fetch.call2(&window, &JsValue::from_str(STATS_URL), &init).ok()?.dyn_into::<Promise>().ok()?;
    spawn_local(async move {
        // 網路不通、被擋、逾時都會到這裡，直接忽略
        let _ = JsFuture::from(promise).await;
    });
    Some(())
}

/// 這個瀏覽器的累計次數加一，回傳新的次數。儲存空間不能用時回傳 1，不影響其他功能。
pub fn bump_counter(key: &str) -> u32 {
    let n = load(key)
        .and_then(|s| s.trim().parse::<u32>().ok())
        .unwrap_or(0)
        .saturating_add(1);
    save(key, &n.to_string());
    n
}

/// 明天的日期（本地時間），格式 YYYYMMDD。
pub fn tomorrow_ymd() -> String {
    let d = js_sys::Date::new(&JsValue::from_f64(js_sys::Date::now() + 86_400_000.0));
    format!("{:04}{:02}{:02}", d.get_full_year(), d.get_month() + 1, d.get_date())
}
