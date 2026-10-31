//! 跑步時的背景音樂：全部用 Web Audio 即時合成，不需要音檔、離線可用、也沒有版權問題。
//!
//! 跑步時音樂掛在節拍器的排程上：節拍器每排一拍，這裡就依「第幾拍」決定要發哪些聲音
//! （鼓、貝斯、和弦、琶音），所以音樂的每一拍都鎖在節拍器同一條拍子線上，節拍快慢音樂就跟著快慢。
//! 其他階段（暖身、肌力、伸展）沒有節拍，只播柔和的和弦長音。
//!
//! 這個檔案分兩部分：上半是純邏輯（風格、和弦、每一拍的音符），有單元測試；
//! 下半是把音符變成聲音的 Web Audio 合成與引擎。

use crate::web;
use serde::{Deserialize, Serialize};

// ───────────────────────── 純邏輯：風格、和弦、音符 ─────────────────────────

/// 背景音樂的風格。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Music {
    None,
    /// 輕快鼓點：踢鼓與拍手打在 2、4 拍，加上貝斯與明亮的琶音。
    #[default]
    Groove,
    /// 晨光和弦：和弦長音與清亮的琶音，鼓點很輕，最不搶注意力。
    Sunrise,
    /// 電子脈衝：每一拍都有踢鼓，八分音符的貝斯與琶音，最有衝勁。
    Pulse,
}

impl Music {
    pub const ALL: [Music; 4] = [Self::None, Self::Groove, Self::Sunrise, Self::Pulse];

    pub fn label(self) -> &'static str {
        match self {
            Self::None => "不播放",
            Self::Groove => "輕快鼓點",
            Self::Sunrise => "晨光和弦",
            Self::Pulse => "電子脈衝",
        }
    }

    pub fn desc(self) -> &'static str {
        match self {
            Self::None => "只有節拍器，不放音樂。",
            Self::Groove => "踢鼓、拍手、貝斯加明亮的琶音，節奏明確。",
            Self::Sunrise => "柔和的和弦長音配清亮的琶音，鼓點很輕。",
            Self::Pulse => "每一拍都有踢鼓，八分音符的貝斯與琶音，最有衝勁。",
        }
    }
}

/// 音樂現在該怎麼播。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    /// 沒有在運動或暫停中：安靜。
    Off,
    /// 沒有節拍的階段（暖身、肌力、伸展）：只有柔和的和弦長音。
    Ambient,
    /// 超慢跑：每一拍都鎖在節拍器上。
    Beat,
}

/// 四拍一小節，四小節換完一輪和弦。
pub const BEATS_PER_BAR: u64 = 4;
pub const BARS_PER_CYCLE: u64 = 4;

/// 一個和弦：貝斯音與上方三個和弦音（MIDI 音高）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Chord {
    pub bass: u8,
    pub tones: [u8; 3],
}

/// 和弦進行 I – V – vi – IV（C 大調：C、G、Am、F），流行歌最常見的一種。
/// 上方三個音用最近的轉位，音與音之間只動一點點，聽起來才順。
pub const PROGRESSION: [Chord; 4] = [
    Chord { bass: 48, tones: [60, 64, 67] }, // C：C3 / C4 E4 G4
    Chord { bass: 43, tones: [59, 62, 67] }, // G：G2 / B3 D4 G4
    Chord { bass: 45, tones: [57, 60, 64] }, // Am：A2 / A3 C4 E4
    Chord { bass: 41, tones: [57, 60, 65] }, // F：F2 / A3 C4 F4
];

/// 第 `beat` 拍（從 0 起）所在的和弦；一小節換一個。
pub fn chord_at(beat: u64) -> Chord {
    PROGRESSION[((beat / BEATS_PER_BAR) % BARS_PER_CYCLE) as usize]
}

/// MIDI 音高轉頻率（赫茲）；69 是 440 Hz。
pub fn mtof(midi: u8) -> f32 {
    440.0 * 2.0_f32.powf((f32::from(midi) - 69.0) / 12.0)
}

/// 合成的聲音種類。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Sound {
    Kick,
    Clap,
    Hat,
    Bass,
    Pluck,
    Pad,
}

/// 一個要發的音。`at` 是這個音在這一拍之內的位置（0 為正拍、0.5 為反拍），單位是拍；
/// `len` 也是拍。`midi` 為 0 代表沒有音高（鼓）。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Note {
    pub sound: Sound,
    pub at: f64,
    pub midi: u8,
    pub len: f64,
    pub vel: f32,
}

const fn drum(sound: Sound, at: f64, vel: f32) -> Note {
    Note { sound, at, midi: 0, len: 0.25, vel }
}

const fn tone(sound: Sound, at: f64, midi: u8, len: f64, vel: f32) -> Note {
    Note { sound, at, midi, len, vel }
}

/// 一個小節開頭的和弦長音：三個和弦音各一個，持續整個小節。
fn pad(chord: Chord, vel: f32, out: &mut Vec<Note>) {
    for m in chord.tones {
        out.push(tone(Sound::Pad, 0.0, m, BEATS_PER_BAR as f64, vel));
    }
}

/// 第 `beat` 拍要發的所有音。`beat` 從 0 起算，跟節拍器的拍數同一條線。
pub fn notes_for_beat(style: Music, beat: u64) -> Vec<Note> {
    let bar_beat = beat % BEATS_PER_BAR;
    let chord = chord_at(beat);
    let mut n = Vec::new();
    match style {
        Music::None => {}
        Music::Groove => {
            if bar_beat == 0 {
                pad(chord, 0.5, &mut n);
            }
            // 踢鼓打在 1、3 拍，拍手打在 2、4 拍；反拍加一點點高帽
            match bar_beat {
                0 | 2 => n.push(drum(Sound::Kick, 0.0, if bar_beat == 0 { 0.85 } else { 0.75 })),
                _ => n.push(drum(Sound::Clap, 0.0, 0.7)),
            }
            n.push(drum(Sound::Hat, 0.5, 0.45));
            // 貝斯：1、3 拍各一個，第 3 拍升到五度，有點起伏
            match bar_beat {
                0 => n.push(tone(Sound::Bass, 0.0, chord.bass, 1.6, 0.8)),
                2 => n.push(tone(Sound::Bass, 0.0, chord.bass + 7, 1.5, 0.7)),
                _ => {}
            }
            // 琶音：每拍一個和弦音，來回彈
            let idx = [0, 2, 1, 2][bar_beat as usize];
            n.push(tone(Sound::Pluck, 0.0, chord.tones[idx] + 12, 0.8, 0.5));
        }
        Music::Sunrise => {
            if bar_beat == 0 {
                pad(chord, 0.75, &mut n);
                n.push(tone(Sound::Bass, 0.0, chord.bass, 3.5, 0.55));
                n.push(drum(Sound::Kick, 0.0, 0.5));
            }
            n.push(drum(Sound::Hat, 0.5, 0.22));
            let idx = [0, 1, 2, 1][bar_beat as usize];
            n.push(tone(Sound::Pluck, 0.0, chord.tones[idx] + 12, 1.2, 0.55));
            // 第 2、4 拍的反拍再補一個高八度的音，像水面的反光
            if bar_beat % 2 == 1 {
                n.push(tone(Sound::Pluck, 0.5, chord.tones[2] + 24, 0.8, 0.3));
            }
        }
        Music::Pulse => {
            if bar_beat == 0 {
                pad(chord, 0.4, &mut n);
            }
            n.push(drum(Sound::Kick, 0.0, if bar_beat == 0 { 0.95 } else { 0.8 }));
            if bar_beat % 2 == 1 {
                n.push(drum(Sound::Clap, 0.0, 0.5));
            }
            n.push(drum(Sound::Hat, 0.5, 0.5));
            // 八分音符的貝斯：正拍根音、反拍高八度
            n.push(tone(Sound::Bass, 0.0, chord.bass, 0.45, 0.8));
            n.push(tone(Sound::Bass, 0.5, chord.bass + 12, 0.4, 0.55));
            // 八分音符的琶音
            for (k, at) in [0.0, 0.5].into_iter().enumerate() {
                let idx = (bar_beat as usize * 2 + k) % 3;
                n.push(tone(Sound::Pluck, at, chord.tones[idx] + 12, 0.4, 0.4));
            }
        }
    }
    n
}

/// 沒有節拍的階段：第 `i` 個和弦長音（每個持續 [`AMBIENT_CHORD_SECS`] 秒，依序走 [`PROGRESSION`]）。
pub fn ambient_chord(i: u64) -> Chord {
    PROGRESSION[(i % BARS_PER_CYCLE) as usize]
}

/// 柔和長音階段，每個和弦持續幾秒。
pub const AMBIENT_CHORD_SECS: f64 = 8.0;

// ───────────────────────── Web Audio：把音符變成聲音 ─────────────────────────

use std::cell::RefCell;
use wasm_bindgen::prelude::*;
use web_sys::{AudioBuffer, AudioContext, AudioNode, BiquadFilterType, GainNode, OscillatorType};

/// 進限幅器之前的固定增益：讓限幅器每一種風格都在差不多的狀態下工作。
const DRIVE: f32 = 0.3;
/// 音量 100% 時，限幅器之後的增益。節拍器的「答」峰值是 0.8，音樂的尖峰加上去要留在 1.0 以內，
/// 所以這個值不能太大（用探針量過：三種風格 100% 時疊上節拍器都沒有超過 1.0）。
const MASTER_MAX: f32 = 0.8;
/// 說話時，音樂壓到平常的幾倍。
const DUCK_GAIN: f32 = 0.35;

struct Engine {
    ctx: AudioContext,
    /// 限幅器之後的音量與說話時壓低。
    master: GainNode,
    /// 開關：安靜時快速淡出，有音樂時淡入。
    mix: GainNode,
    /// 柔和長音階段專用，進入超慢跑時淡出。
    amb: GainNode,
    /// 送進殘響的入口。
    verb: GainNode,
    noise: Option<AudioBuffer>,
    mode: Mode,
    style: Music,
    volume: u32,
    /// 下一個長音和弦要在什麼時間（Web Audio 時間）響，以及它是第幾個。
    ambient_next: f64,
    ambient_idx: u64,
    ambient_handle: Option<i32>,
    _ambient_tick: Option<Closure<dyn FnMut()>>,
}

thread_local! {
    static ENGINE: RefCell<Option<Engine>> = const { RefCell::new(None) };
}

/// 音量（0 到 100）對應的增益（限幅器之後）：用 1.2 次方，小聲時調整的幅度比較細。
fn gain_for(volume: u32) -> f32 {
    MASTER_MAX * (volume.min(100) as f32 / 100.0).powf(1.2)
}

/// 簡單的偽亂數（雜訊與殘響用），不需要高品質，要的是每次一樣。
struct Lcg(u32);

impl Lcg {
    fn next(&mut self) -> f32 {
        self.0 = self.0.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
        (self.0 >> 8) as f32 / 8_388_608.0 - 1.0
    }
}

fn noise_buffer(ctx: &AudioContext) -> Option<AudioBuffer> {
    let len = (ctx.sample_rate() * 0.3) as u32;
    let buf = ctx.create_buffer(1, len, ctx.sample_rate()).ok()?;
    let mut r = Lcg(777);
    let data: Vec<f32> = (0..len).map(|_| r.next()).collect();
    buf.copy_to_channel(&data, 0).ok()?;
    Some(buf)
}

/// 殘響用的脈衝響應：兩秒內漸漸消失的雜訊。
fn impulse(ctx: &AudioContext) -> Option<AudioBuffer> {
    let rate = ctx.sample_rate();
    let secs = 1.8_f32;
    let len = (rate * secs) as u32;
    let buf = ctx.create_buffer(2, len, rate).ok()?;
    for ch in 0..2_i32 {
        let mut r = Lcg(4242 + ch as u32 * 91);
        let data: Vec<f32> = (0..len)
            .map(|i| {
                let t = i as f32 / rate;
                r.next() * (-t * 2.6).exp() * (1.0 - t / secs)
            })
            .collect();
        let _ = buf.copy_to_channel(&data, ch);
    }
    Some(buf)
}

fn connect(a: &AudioNode, b: &AudioNode) {
    let _ = a.connect_with_audio_node(b);
}

impl Engine {
    fn new() -> Option<Engine> {
        let ctx = web::audio_context()?;
        let (master, mix, amb, verb) = (ctx.create_gain().ok()?, ctx.create_gain().ok()?, ctx.create_gain().ok()?, ctx.create_gain().ok()?);
        master.gain().set_value(0.0);
        mix.gain().set_value(0.0);
        amb.gain().set_value(0.0);
        verb.gain().set_value(1.0);
        // 路徑：各聲音 → mix（開關）→ drive（固定增益）→ 限幅器 → master（音量、說話時壓低）→ 喇叭。
        // 限幅器在音量旋鈕「前面」：先把尖峰擋住、整體音量穩定，再由旋鈕決定大小，
        // 滑桿才是可預測的，而且 100% 時踢鼓、貝斯、和弦疊在一起、又剛好和節拍器的「答」同時響，也不會爆音。
        // （節拍器和語音不經過這條路徑，所以不受影響）
        let drive = ctx.create_gain().ok()?;
        drive.gain().set_value(DRIVE);
        let limiter = ctx.create_dynamics_compressor().ok()?;
        limiter.threshold().set_value(-30.0);
        limiter.knee().set_value(12.0);
        limiter.ratio().set_value(20.0);
        limiter.attack().set_value(0.001);
        limiter.release().set_value(0.1);
        connect(&amb, &mix);
        connect(&mix, &drive);
        connect(&drive, &limiter);
        connect(&limiter, &master);
        connect(&master, &ctx.destination());
        // 殘響：只送進去的那一份經過它，再混回總線
        let conv = ctx.create_convolver().ok()?;
        conv.set_buffer(impulse(&ctx).as_ref());
        let wet = ctx.create_gain().ok()?;
        wet.gain().set_value(0.45);
        connect(&verb, &conv);
        connect(&conv, &wet);
        connect(&wet, &mix);
        Some(Engine {
            noise: noise_buffer(&ctx),
            ctx,
            master,
            mix,
            amb,
            verb,
            mode: Mode::Off,
            style: Music::None,
            volume: 0,
            ambient_next: 0.0,
            ambient_idx: 0,
            ambient_handle: None,
            _ambient_tick: None,
        })
    }
}

fn with_engine<R>(f: impl FnOnce(&mut Engine) -> R) -> Option<R> {
    ENGINE.with(|e| {
        let mut e = e.borrow_mut();
        if e.is_none() {
            *e = Engine::new();
        }
        e.as_mut().map(f)
    })
}

/// 一個短促的包絡：`t` 起 `attack` 秒升到 `peak`，到 `t + len` 指數衰減到幾乎聽不到。
fn pluck_env(g: &web_sys::AudioParam, t: f64, attack: f64, peak: f32, len: f64) {
    let _ = g.set_value_at_time(0.0, t);
    let _ = g.linear_ramp_to_value_at_time(peak, t + attack);
    let _ = g.exponential_ramp_to_value_at_time(0.0008, t + len);
}

fn osc(e: &Engine, kind: OscillatorType, freq: f32) -> Option<web_sys::OscillatorNode> {
    let o = e.ctx.create_oscillator().ok()?;
    o.set_type(kind);
    o.frequency().set_value(freq);
    Some(o)
}

/// 把聲音另外送一份進殘響。
fn send(e: &Engine, from: &AudioNode, amount: f32) {
    if let Ok(g) = e.ctx.create_gain() {
        g.gain().set_value(amount);
        connect(from, &g);
        connect(&g, &e.verb);
    }
}

fn filter(e: &Engine, kind: BiquadFilterType, freq: f32, q: f32) -> Option<web_sys::BiquadFilterNode> {
    let f = e.ctx.create_biquad_filter().ok()?;
    f.set_type(kind);
    f.frequency().set_value(freq);
    f.q().set_value(q);
    Some(f)
}

fn kick(e: &Engine, out: &AudioNode, t: f64, vel: f32) -> Option<()> {
    let (o, g) = (osc(e, OscillatorType::Sine, 140.0)?, e.ctx.create_gain().ok()?);
    let _ = o.frequency().set_value_at_time(140.0, t);
    let _ = o.frequency().exponential_ramp_to_value_at_time(46.0, t + 0.12);
    let _ = g.gain().set_value_at_time(0.9 * vel, t);
    let _ = g.gain().exponential_ramp_to_value_at_time(0.001, t + 0.28);
    connect(&o, &g);
    connect(&g, out);
    let _ = o.start_with_when(t);
    let _ = o.stop_with_when(t + 0.3);
    Some(())
}

/// 雜訊爆一下：拍手與高帽共用，差在濾波與衰減。
fn noise_hit(e: &Engine, out: &AudioNode, t: f64, kind: BiquadFilterType, freq: f32, q: f32, vol: f32, decay: f64, bursts: &[f64]) -> Option<()> {
    let buf = e.noise.as_ref()?;
    let (f, g) = (filter(e, kind, freq, q)?, e.ctx.create_gain().ok()?);
    let _ = g.gain().set_value_at_time(vol, t);
    let _ = g.gain().exponential_ramp_to_value_at_time(0.001, t + decay);
    connect(&f, &g);
    connect(&g, out);
    for off in bursts {
        let src = e.ctx.create_buffer_source().ok()?;
        src.set_buffer(Some(buf));
        connect(&src, &f);
        let _ = src.start_with_when(t + off);
    }
    Some(())
}

fn bass(e: &Engine, out: &AudioNode, t: f64, dur: f64, freq: f32, vel: f32) -> Option<()> {
    let (o, g) = (osc(e, OscillatorType::Sawtooth, freq)?, e.ctx.create_gain().ok()?);
    let f = filter(e, BiquadFilterType::Lowpass, 900.0, 0.7)?;
    let _ = f.frequency().set_value_at_time(900.0, t);
    let _ = f.frequency().exponential_ramp_to_value_at_time(280.0, t + 0.18);
    let end = t + dur.max(0.08);
    let _ = g.gain().set_value_at_time(0.0, t);
    let _ = g.gain().linear_ramp_to_value_at_time(0.5 * vel, t + 0.012);
    let _ = g.gain().set_value_at_time(0.5 * vel, (end - 0.06).max(t + 0.02));
    let _ = g.gain().exponential_ramp_to_value_at_time(0.001, end);
    connect(&o, &f);
    connect(&f, &g);
    connect(&g, out);
    let _ = o.start_with_when(t);
    let _ = o.stop_with_when(end + 0.02);
    Some(())
}

fn pluck(e: &Engine, out: &AudioNode, t: f64, dur: f64, freq: f32, vel: f32) -> Option<()> {
    let g = e.ctx.create_gain().ok()?;
    let f = filter(e, BiquadFilterType::Lowpass, 3600.0, 0.5)?;
    let len = dur.clamp(0.15, 0.7) + 0.1;
    pluck_env(&g.gain(), t, 0.006, 0.3 * vel, len);
    connect(&f, &g);
    connect(&g, out);
    send(e, &g, 0.35);
    for (kind, mult, level) in [(OscillatorType::Triangle, 1.0_f32, 1.0_f32), (OscillatorType::Sine, 2.0, 0.3)] {
        let (o, lv) = (osc(e, kind, freq * mult)?, e.ctx.create_gain().ok()?);
        lv.gain().set_value(level);
        connect(&o, &lv);
        connect(&lv, &f);
        let _ = o.start_with_when(t);
        let _ = o.stop_with_when(t + len + 0.05);
    }
    Some(())
}

/// 和弦長音：兩支略微走音的三角波疊在一起，慢慢淡入、慢慢淡出。
fn pad_voice(e: &Engine, out: &AudioNode, t: f64, dur: f64, freq: f32, vel: f32) -> Option<()> {
    let g = e.ctx.create_gain().ok()?;
    let f = filter(e, BiquadFilterType::Lowpass, 900.0, 0.4)?;
    let attack = (dur * 0.12).clamp(0.25, 0.8);
    let end = t + dur;
    let _ = g.gain().set_value_at_time(0.0, t);
    let _ = g.gain().linear_ramp_to_value_at_time(0.08 * vel, t + attack);
    let _ = g.gain().set_value_at_time(0.08 * vel, (end - 0.5).max(t + attack));
    let _ = g.gain().linear_ramp_to_value_at_time(0.0001, end + 0.9);
    connect(&f, &g);
    connect(&g, out);
    send(e, &g, 0.5);
    for cents in [-7.0_f32, 7.0] {
        let o = osc(e, OscillatorType::Triangle, freq)?;
        o.detune().set_value(cents);
        connect(&o, &f);
        let _ = o.start_with_when(t);
        let _ = o.stop_with_when(end + 1.0);
    }
    Some(())
}

fn render(e: &Engine, out: &AudioNode, n: Note, t: f64, dur: f64) {
    let _ = match n.sound {
        Sound::Kick => kick(e, out, t, n.vel),
        Sound::Clap => noise_hit(e, out, t, BiquadFilterType::Bandpass, 1800.0, 0.9, 0.42 * n.vel, 0.12, &[0.0, 0.011, 0.022]),
        Sound::Hat => noise_hit(e, out, t, BiquadFilterType::Highpass, 7000.0, 0.7, 0.2 * n.vel, 0.045, &[0.0]),
        Sound::Bass => bass(e, out, t, dur, mtof(n.midi), n.vel),
        Sound::Pluck => pluck(e, out, t, dur, mtof(n.midi), n.vel),
        Sound::Pad => pad_voice(e, out, t, dur, mtof(n.midi), n.vel),
    };
}

/// 節拍器每排一拍就呼叫一次：`t` 是這一拍響起的 Web Audio 時間，`spb` 是一拍多少秒。
/// 只有「超慢跑」模式才會發聲。
pub fn on_beat(t: f64, beat: u64, spb: f64) {
    ENGINE.with(|e| {
        let e = e.borrow();
        let Some(e) = e.as_ref() else { return };
        if e.mode != Mode::Beat || e.style == Music::None {
            return;
        }
        for n in notes_for_beat(e.style, beat) {
            render(e, &e.mix, n, t + n.at * spb, n.len * spb);
        }
    });
}

/// 柔和長音階段：每 0.25 秒看一次，把接下來一秒內該響的和弦排進去。
fn ambient_tick() {
    ENGINE.with(|e| {
        let mut e = e.borrow_mut();
        let Some(e) = e.as_mut() else { return };
        if e.mode != Mode::Ambient {
            return;
        }
        let now = e.ctx.current_time();
        while e.ambient_next < now + 1.0 {
            let t = e.ambient_next.max(now);
            for m in ambient_chord(e.ambient_idx).tones {
                // 比一個和弦的長度多 1.5 秒，前後兩個和弦疊在一起淡入淡出，換和弦沒有縫
                let _ = pad_voice(e, &e.amb, t, AMBIENT_CHORD_SECS + 1.5, mtof(m), 0.9);
            }
            e.ambient_next += AMBIENT_CHORD_SECS;
            e.ambient_idx += 1;
        }
    });
}

fn stop_ambient_timer(e: &mut Engine) {
    if let (Some(h), Some(win)) = (e.ambient_handle.take(), web_sys::window()) {
        win.clear_interval_with_handle(h);
    }
    e._ambient_tick = None;
}

/// 切換音樂的播法。`style` 為「不播放」時一律當成安靜。可以重複呼叫，狀態沒變就什麼都不會發生。
pub fn set_mode(mode: Mode, style: Music, volume: u32) {
    let mode = if style == Music::None { Mode::Off } else { mode };
    if mode == Mode::Off && ENGINE.with(|e| e.borrow().is_none()) {
        return;
    }
    with_engine(|e| {
        let now = e.ctx.current_time();
        // 沒有變就什麼都不做：運動中常被呼叫，不能每次都重排音量（會蓋掉說話時的壓低）
        if e.mode == mode && e.style == style && e.volume == volume {
            return;
        }
        e.style = style;
        if e.volume != volume {
            e.volume = volume;
            let _ = e.master.gain().set_target_at_time(gain_for(volume), now, 0.05);
        }
        if e.mode == mode {
            return;
        }
        match mode {
            Mode::Off => {
                let _ = e.mix.gain().set_target_at_time(0.0, now, 0.08);
                stop_ambient_timer(e);
            }
            Mode::Ambient => {
                let _ = e.mix.gain().set_target_at_time(1.0, now, 0.05);
                let _ = e.amb.gain().set_target_at_time(1.0, now, 0.3);
                if e.ambient_handle.is_none() {
                    e.ambient_next = now + 0.05;
                    let tick = Closure::<dyn FnMut()>::new(ambient_tick);
                    if let Some(win) = web_sys::window() {
                        e.ambient_handle = win.set_interval_with_callback_and_timeout_and_arguments_0(tick.as_ref().unchecked_ref(), 250).ok();
                    }
                    e._ambient_tick = Some(tick);
                }
            }
            Mode::Beat => {
                let _ = e.mix.gain().set_target_at_time(1.0, now, 0.05);
                // 柔和長音慢慢退場，換成跟著節拍的音樂
                let _ = e.amb.gain().set_target_at_time(0.0, now, 0.6);
                stop_ambient_timer(e);
            }
        }
        e.mode = mode;
    });
}

/// 說話的時候把音樂壓低 `secs` 秒，講完再慢慢回來，口令才聽得清楚。
pub fn duck(secs: f64) {
    ENGINE.with(|e| {
        let e = e.borrow();
        let Some(e) = e.as_ref() else { return };
        if e.mode == Mode::Off {
            return;
        }
        let now = e.ctx.current_time();
        let base = gain_for(e.volume);
        let g = e.master.gain();
        // 清掉前一次排好的「恢復」，連續說話時才會一直壓低到最後一句講完
        let _ = g.cancel_scheduled_values(now);
        let _ = g.set_target_at_time(base * DUCK_GAIN, now, 0.04);
        let _ = g.set_target_at_time(base, now + secs, 0.35);
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeSet;

    #[test]
    fn midi_to_frequency() {
        assert!((mtof(69) - 440.0).abs() < 1e-3);
        assert!((mtof(60) - 261.63).abs() < 0.01, "中央 C");
        assert!((mtof(81) - 880.0).abs() < 1e-2, "高八度剛好兩倍");
        assert!((mtof(48) * 2.0 - mtof(60)).abs() < 1e-2);
    }

    #[test]
    fn none_is_silent() {
        for beat in 0..32 {
            assert!(notes_for_beat(Music::None, beat).is_empty());
        }
    }

    #[test]
    fn progression_changes_each_bar_and_repeats_every_sixteen_beats() {
        for beat in 0..64_u64 {
            assert_eq!(chord_at(beat), chord_at(beat + 16), "第 {beat} 拍與 16 拍後應該同一個和弦");
        }
        let per_bar: Vec<Chord> = (0..4).map(|bar| chord_at(bar * 4)).collect();
        assert_eq!(per_bar, PROGRESSION);
        assert_eq!(chord_at(0), chord_at(3), "同一小節內不換和弦");
        assert_ne!(chord_at(3), chord_at(4));
    }

    #[test]
    fn every_pitch_is_in_the_c_major_scale() {
        // 全部音都在 C 大調內，所以怎麼疊都不會刺耳
        let scale: BTreeSet<u8> = [0, 2, 4, 5, 7, 9, 11].into_iter().collect();
        for c in PROGRESSION {
            for m in std::iter::once(c.bass).chain(c.tones) {
                assert!(scale.contains(&(m % 12)), "{m} 不在 C 大調");
            }
        }
        for style in Music::ALL {
            for beat in 0..16 {
                for note in notes_for_beat(style, beat) {
                    if note.midi != 0 {
                        assert!(scale.contains(&(note.midi % 12)), "{style:?} 第 {beat} 拍的 {} 不在 C 大調", note.midi);
                    }
                }
            }
        }
    }

    #[test]
    fn notes_have_sane_ranges() {
        for style in Music::ALL {
            for beat in 0..16 {
                let notes = notes_for_beat(style, beat);
                assert!(notes.len() <= 12, "{style:?} 第 {beat} 拍音符太多（{}），手機會吃力", notes.len());
                for n in notes {
                    assert!(n.at == 0.0 || n.at == 0.5, "只排在正拍與反拍：{n:?}");
                    assert!(n.len > 0.0 && n.len <= 4.0, "{n:?}");
                    assert!(n.vel > 0.0 && n.vel <= 1.0, "{n:?}");
                    match n.sound {
                        Sound::Kick | Sound::Clap | Sound::Hat => assert_eq!(n.midi, 0, "鼓沒有音高"),
                        Sound::Bass => assert!((36..=65).contains(&n.midi), "貝斯音域 {n:?}"),
                        Sound::Pluck => assert!((60..=96).contains(&n.midi), "琶音音域 {n:?}"),
                        Sound::Pad => assert!((55..=70).contains(&n.midi), "和弦音域 {n:?}"),
                    }
                }
            }
        }
    }

    fn count(style: Music, sound: Sound, bar_beat: u64) -> usize {
        // 取第 5 小節（和弦已經輪過一次）以確認不是只有第一個小節才對
        notes_for_beat(style, 16 + bar_beat).into_iter().filter(|n| n.sound == sound).count()
    }

    #[test]
    fn groove_has_a_backbeat() {
        for (bar_beat, kick, clap) in [(0, 1, 0), (1, 0, 1), (2, 1, 0), (3, 0, 1)] {
            assert_eq!(count(Music::Groove, Sound::Kick, bar_beat), kick, "拍 {bar_beat} 踢鼓");
            assert_eq!(count(Music::Groove, Sound::Clap, bar_beat), clap, "拍 {bar_beat} 拍手");
            assert_eq!(count(Music::Groove, Sound::Hat, bar_beat), 1, "每拍的反拍都有高帽");
        }
    }

    #[test]
    fn pulse_kicks_on_every_beat() {
        for bar_beat in 0..4 {
            assert_eq!(count(Music::Pulse, Sound::Kick, bar_beat), 1);
            assert_eq!(count(Music::Pulse, Sound::Bass, bar_beat), 2, "八分音符的貝斯");
            assert_eq!(count(Music::Pulse, Sound::Pluck, bar_beat), 2, "八分音符的琶音");
        }
    }

    #[test]
    fn sunrise_is_gentle() {
        let kicks: usize = (0..4).map(|b| count(Music::Sunrise, Sound::Kick, b)).sum();
        assert_eq!(kicks, 1, "一個小節只有一下很輕的踢鼓");
        for b in 0..4 {
            for n in notes_for_beat(Music::Sunrise, 16 + b) {
                if n.sound == Sound::Kick {
                    assert!(n.vel <= 0.5);
                }
            }
        }
    }

    #[test]
    fn every_style_plays_one_pad_chord_per_bar() {
        for style in [Music::Groove, Music::Sunrise, Music::Pulse] {
            for bar in 0..8_u64 {
                let pads: Vec<Note> = (0..4).flat_map(|b| notes_for_beat(style, bar * 4 + b)).filter(|n| n.sound == Sound::Pad).collect();
                assert_eq!(pads.len(), 3, "{style:?} 小節 {bar}：一個三和弦");
                assert!(pads.iter().all(|n| n.at == 0.0 && n.len == 4.0), "長音從小節開頭響滿四拍");
                let mut got: Vec<u8> = pads.iter().map(|n| n.midi).collect();
                got.sort_unstable();
                let mut want = chord_at(bar * 4).tones.to_vec();
                want.sort_unstable();
                assert_eq!(got, want, "長音要是這個小節的和弦");
            }
        }
    }

    #[test]
    fn bass_follows_the_chord_root() {
        for style in [Music::Groove, Music::Sunrise, Music::Pulse] {
            for beat in 0..16_u64 {
                for n in notes_for_beat(style, beat).into_iter().filter(|n| n.sound == Sound::Bass) {
                    let root = chord_at(beat).bass;
                    assert!(n.midi == root || n.midi == root + 7 || n.midi == root + 12, "{style:?} 第 {beat} 拍貝斯 {} 不是 {root} 的根音／五度／八度", n.midi);
                }
            }
        }
    }

    #[test]
    fn same_beat_always_gives_the_same_notes() {
        for style in Music::ALL {
            for beat in [0_u64, 1, 7, 100, 1_000_003] {
                assert_eq!(notes_for_beat(style, beat), notes_for_beat(style, beat));
            }
        }
    }

    #[test]
    fn never_overflows_on_huge_beat_counts() {
        for style in Music::ALL {
            let _ = notes_for_beat(style, u64::MAX);
            let _ = chord_at(u64::MAX);
        }
    }

    #[test]
    fn ambient_walks_the_same_progression() {
        assert_eq!(ambient_chord(0), PROGRESSION[0]);
        assert_eq!(ambient_chord(5), PROGRESSION[1]);
        assert!(AMBIENT_CHORD_SECS >= 4.0, "長音要夠長，才不會變成吵鬧的和弦切換");
    }

    #[test]
    fn styles_save_as_plain_lowercase_words() {
        for s in Music::ALL {
            let json = serde_json::to_string(&s).unwrap();
            assert_eq!(serde_json::from_str::<Music>(&json).unwrap(), s);
            assert!(json.chars().all(|c| c == '"' || c.is_ascii_lowercase()), "{json}");
            assert!(!s.label().is_empty() && !s.desc().is_empty());
        }
        assert_eq!(Music::default(), Music::Groove);
    }
}
