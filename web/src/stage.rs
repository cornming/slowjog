//! 運動中的「舞台」：風景、陪跑小人物、氣泡與完成時的彩帶。對應 Android 版的 MascotView／SceneryView。
//!
//! 動畫用 requestAnimationFrame 每個畫面影格算一次、直接改 DOM，不經過 Leptos 的反應式更新，
//! 才不會每秒重畫 60 次整個畫面。畫面的規則（哪一格、捲多少）都在 `mascot.rs`／`scenery.rs`，那裡有單元測試。

use crate::engine;
use crate::mascot::{self, Style};
use crate::moves::{self, Move};
use crate::plan::StageKind;
use crate::scenery::{self, Scene};
use crate::web;
use leptos::prelude::*;
use std::collections::HashMap;
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use web_sys::Element;

/// 舞台需要的所有輸入。都是讀取用的，舞台不會改動運動的狀態。
#[derive(Clone)]
pub struct Inputs {
    pub jogging: Signal<bool>,
    pub paused: Signal<bool>,
    pub done: Signal<bool>,
    /// 節拍器每響一拍加一。
    pub beat: Signal<u64>,
    /// 整段運動已經過的毫秒（每 200 毫秒更新一次，舞台自己在中間補插）。
    pub elapsed: Signal<u64>,
    /// 估算的跑步距離（公尺）。
    pub meters: Signal<f64>,
    pub bpm: Signal<u32>,
    pub stride_cm: Signal<u32>,
    pub style: Signal<Style>,
    pub scene: Signal<Option<Scene>>,
    pub scene_moving: Signal<bool>,
    /// （第幾句, 文字）；有新的一句鼓勵時氣泡就冒出來。
    pub cheer: Signal<(u32, String)>,
    /// 目前課表位置的摘要；沒有課表或沒有在運動時回傳 None。
    pub view_at: Rc<dyn Fn(u64) -> Option<engine::View>>,
}

/// 一個 CSS 屬性只有值變了才真的寫進 DOM。
#[derive(Default)]
struct Painter {
    last: HashMap<(u8, &'static str), String>,
}

impl Painter {
    fn set(&mut self, slot: u8, el: &Element, name: &'static str, value: String) {
        if self.last.get(&(slot, name)) != Some(&value) {
            web::set_css(el, name, &value);
            self.last.insert((slot, name), value);
        }
    }
}

type Bubble = RwSignal<(u32, Option<String>)>;

/// 冒出氣泡，`BUBBLE_SHOW_SEC` 秒後收起；期間又有新的一句就以新的為準。
fn show_bubble(bubble: Bubble, text: String) {
    let id = bubble.with_untracked(|b| b.0) + 1;
    bubble.set((id, Some(text)));
    set_timeout(
        move || {
            if bubble.with_untracked(|b| b.0) == id {
                bubble.set((id, None));
            }
        },
        Duration::from_secs(u64::from(mascot::BUBBLE_SHOW_SEC)),
    );
}

const SLOT_M1: u8 = 0;
const SLOT_M2: u8 = 1;
const SLOT_FLIP: u8 = 2;
const SLOT_LAYER: u8 = 10;

struct Anim {
    inputs: Inputs,
    m1: NodeRef<leptos::html::Div>,
    m2: NodeRef<leptos::html::Div>,
    flip: NodeRef<leptos::html::Div>,
    layers: Vec<(NodeRef<leptos::html::Div>, f32)>,
    bubble: Bubble,
    reduced: bool,
    painter: Painter,
    last_t: f64,
    beat_seen: u64,
    beat_t: f64,
    elapsed_seen: u64,
    elapsed_t: f64,
    local_m: f64,
    move_key: Option<(Move, usize)>,
}

impl Anim {
    fn frame(&mut self, t: f64) {
        let (Some(m1), Some(m2), Some(flip)) = (self.m1.get_untracked(), self.m2.get_untracked(), self.flip.get_untracked()) else {
            return;
        };
        let i = self.inputs.clone();
        let jogging = i.jogging.get_untracked();
        let paused = i.paused.get_untracked();
        let done = i.done.get_untracked();
        let bpm = i.bpm.get_untracked().max(1);
        let style = i.style.get_untracked();
        let dt = ((t - self.last_t) / 1000.0).clamp(0.0, 0.1);
        self.last_t = t;

        // 記下每一拍響起的時間，跑步影格依它對齊腳落地的瞬間
        let beat = i.beat.get_untracked();
        if beat != self.beat_seen {
            self.beat_seen = beat;
            self.beat_t = t;
        }
        // 計時每 200 毫秒才更新一次，中間補插，動作的循環才會順
        let e = i.elapsed.get_untracked();
        if e != self.elapsed_seen {
            self.elapsed_seen = e;
            self.elapsed_t = t;
        }
        let est = if paused { e } else { e + (t - self.elapsed_t).clamp(0.0, 400.0) as u64 };

        // 風景：跟著估算距離捲動，暫停就停在原地
        let moving = jogging && !paused;
        let m = i.meters.get_untracked();
        if moving {
            self.local_m += dt * (f64::from(bpm) / 60.0) * (f64::from(i.stride_cm.get_untracked()) / 100.0);
            if (self.local_m - m).abs() > 0.75 {
                self.local_m = m;
            }
        } else {
            self.local_m = m;
        }
        let scroll_m = if i.scene_moving.get_untracked() && !self.reduced { self.local_m } else { 0.0 };
        for (n, (layer, speed)) in self.layers.iter().enumerate() {
            if let Some(el) = layer.get_untracked() {
                let frac = scenery::layer_offset(scroll_m, *speed) / scenery::TILE_W;
                self.painter.set(SLOT_LAYER + n as u8, &el, "--f", format!("{frac:.4}"));
            }
        }

        // 小人物：做動作 → 動作影格；超慢跑 → 跑步影格；其餘 → 站姿
        let slot = if jogging || done {
            None
        } else {
            (i.view_at)(est).and_then(|v| v.slot)
        };
        if let Some(sl) = slot {
            if self.move_key != Some((sl.mv, sl.index)) {
                self.move_key = Some((sl.mv, sl.index));
                show_bubble(self.bubble, format!("{}：{}", sl.mv.name_zh(), sl.mv.tip()));
                if let Some(next) = sl.next {
                    web::preload_image(&mascot::move_url(style, next));
                }
            }
            let phase = if sl.cycle_ms == 0 { 0.0 } else { sl.cycle_elapsed_ms as f32 / sl.cycle_ms as f32 };
            let (f1, f2, blend) = mascot::move_frames(phase);
            let url = format!("url(\"{}\")", mascot::move_url(style, sl.mv));
            self.sheet(&m1, &m2, &url, "600% 400%", (mascot::MOVE_COLS, mascot::MOVE_ROWS), f1, Some((f2, blend)));
            self.painter.set(SLOT_FLIP, &flip, "transform", if sl.mirrored { "scaleX(-1)" } else { "scaleX(1)" }.to_string());
        } else {
            self.move_key = None;
            if jogging {
                let frame = if paused { 0 } else { mascot::run_frame(beat, t - self.beat_t, bpm) };
                let url = format!("url(\"{}\")", mascot::run_url(style));
                self.sheet(&m1, &m2, &url, "600% 200%", (mascot::RUN_COLS, mascot::RUN_ROWS), frame, None);
                // 影格表的側面是面向左；鏡像成面向右，風景往左退
                self.painter.set(SLOT_FLIP, &flip, "transform", "scaleX(-1)".to_string());
            } else {
                let url = format!("url(\"{}\")", mascot::idle_url(style));
                self.sheet(&m1, &m2, &url, "100% 100%", (1, 1), 0, None);
                self.painter.set(SLOT_FLIP, &flip, "transform", "scaleX(1)".to_string());
            }
        }
    }

    /// 把影格表的第 `frame` 格畫在 m1；`blend` 有值時，下一格疊在 m2 上淡入。
    #[allow(clippy::too_many_arguments)]
    fn sheet(&mut self, m1: &Element, m2: &Element, url: &str, size: &str, grid: (u32, u32), frame: u32, blend: Option<(u32, f32)>) {
        let (x, y) = mascot::sheet_pos(frame, grid.0, grid.1);
        self.painter.set(SLOT_M1, m1, "background-image", url.to_string());
        self.painter.set(SLOT_M1, m1, "background-size", size.to_string());
        self.painter.set(SLOT_M1, m1, "background-position", format!("{x:.3}% {y:.3}%"));
        match blend {
            // 相鄰兩格疊在一起淡入，慢動作才不會一格一格跳
            Some((next, alpha)) if alpha > 0.02 => {
                let (x2, y2) = mascot::sheet_pos(next, grid.0, grid.1);
                self.painter.set(SLOT_M2, m2, "background-image", url.to_string());
                self.painter.set(SLOT_M2, m2, "background-size", size.to_string());
                self.painter.set(SLOT_M2, m2, "background-position", format!("{x2:.3}% {y2:.3}%"));
                self.painter.set(SLOT_M2, m2, "opacity", format!("{alpha:.3}"));
            }
            _ => self.painter.set(SLOT_M2, m2, "opacity", "0".to_string()),
        }
    }
}

/// 彩帶用的小圖案。
const CONFETTI: [&str; 4] = ["🎉", "✨", "⭐", "🎊"];

#[component]
pub fn Stage(inputs: Inputs, #[prop(into)] on_tap: Callback<()>) -> impl IntoView {
    let reduced = web::prefers_reduced_motion();
    let art = inputs.scene.get_untracked().map(scenery::art_of);
    let sky = art.as_ref().map_or("var(--accent-soft)", |a| a.sky);
    let style0 = inputs.style.get_untracked();

    // 先把用得到的圖載進快取：跑步、站姿，以及每個階段的第一個動作
    web::preload_image(&mascot::run_url(style0));
    web::preload_image(&mascot::idle_url(style0));
    for kind in [StageKind::Warmup, StageKind::Strength, StageKind::Cooldown] {
        if let Some(first) = moves::moves_for(kind).first() {
            web::preload_image(&mascot::move_url(style0, *first));
        }
    }

    let m1 = NodeRef::<leptos::html::Div>::new();
    let m2 = NodeRef::<leptos::html::Div>::new();
    let flip = NodeRef::<leptos::html::Div>::new();
    let layer_refs: Vec<NodeRef<leptos::html::Div>> = art.iter().flat_map(|a| a.layers.iter().map(|_| NodeRef::new())).collect();
    let bubble: Bubble = RwSignal::new((0, None));
    let hop = RwSignal::new(0_u32);

    // 有新的一句鼓勵就冒出氣泡。畫面剛出現時就存在的那一句是舊的，不要再冒一次。
    let seen = RwSignal::new(inputs.cheer.get_untracked().0);
    {
        let cheer = inputs.cheer;
        Effect::new(move |_| {
            let (seq, text) = cheer.get();
            if seq != seen.get_untracked() {
                seen.set(seq);
                if !text.is_empty() {
                    show_bubble(bubble, text);
                }
            }
        });
    }

    // 動畫迴圈：畫面離開（運動結束）時停掉
    let stop = Arc::new(AtomicBool::new(false));
    {
        let stop = stop.clone();
        on_cleanup(move || stop.store(true, Ordering::Relaxed));
    }
    let layers = art
        .iter()
        .flat_map(|a| a.layers.iter().map(|l| l.speed))
        .zip(layer_refs.iter().copied())
        .map(|(speed, r)| (r, speed))
        .collect();
    let mut anim = Anim {
        inputs: inputs.clone(),
        m1,
        m2,
        flip,
        layers,
        bubble,
        reduced,
        painter: Painter::default(),
        last_t: 0.0,
        beat_seen: 0,
        beat_t: 0.0,
        elapsed_seen: 0,
        elapsed_t: 0.0,
        local_m: 0.0,
        move_key: None,
    };
    web::raf_loop(stop, move |t| anim.frame(t));

    let scenery_view = art.map(|a| {
        let back = format!("background-image:{}", scenery::svg_css_url(&a.back_svg));
        let band = |b: &scenery::Band| {
            view! { <div class="scene-band" style=format!("top:{:.3}%;background:{}", b.top / scenery::TILE_H * 100.0, b.color)></div> }
        };
        let back_bands = a.back_bands.iter().map(band).collect_view();
        let layer_views = a
            .layers
            .iter()
            .zip(layer_refs.iter().copied())
            .map(|(l, r)| {
                let bg = format!("background-image:{}", scenery::svg_css_url(&l.svg));
                let bands = l.bands.iter().map(band).collect_view();
                view! {
                    <div class="scene-layer" node_ref=r style=bg></div>
                    {bands}
                }
            })
            .collect_view();
        view! {
            <div class="scene-back" style=back></div>
            {back_bands}
            {layer_views}
        }
    });

    let confetti = move || {
        (0..18_usize)
            .map(|n| {
                view! {
                    <i style=format!("--x:{}%;--d:{:.2}s;--t:{:.2}s;--r:{}deg", (n * 37 + 11) % 97, (n % 6) as f64 * 0.12, 1.8 + (n % 5) as f64 * 0.25, ((n * 53) % 90) as i32 - 45)>
                        {CONFETTI[n % CONFETTI.len()]}
                    </i>
                }
            })
            .collect_view()
    };

    view! {
        <div class="stage" style=format!("background:{sky}")>
            {scenery_view}
            <button
                class="mascot-btn"
                class:hop-a=move || !reduced && hop.get() % 2 == 1
                class:hop-b=move || !reduced && hop.get() != 0 && hop.get() % 2 == 0
                class:pixel=move || inputs.style.get().pixel_art()
                aria-label="點一下聽一句鼓勵"
                on:click=move |_| {
                    on_tap.run(());
                    hop.update(|n| *n += 1);
                }
            >
                <div class="mflip" node_ref=flip>
                    <div class="m" node_ref=m1></div>
                    <div class="m" node_ref=m2></div>
                </div>
            </button>
            <Show when=move || bubble.with(|b| b.1.is_some())>
                <p class="bubble stage-bubble" role="status" data-id=move || bubble.with(|b| b.0)>
                    {move || bubble.with(|b| b.1.clone().unwrap_or_default())}
                </p>
            </Show>
            <Show when=move || inputs.done.get() && !reduced>
                <div class="confetti" aria-hidden="true">{confetti()}</div>
            </Show>
        </div>
    }
}
