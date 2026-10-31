//! 跑步時的風景：天空與遠近好幾層，遠的捲得慢、近的捲得快，做出景深。
//! 對應 Android 版的 SceneryLogic.kt／SceneryView.kt，座標與顏色照著搬過來，純邏輯。
//!
//! 圖形用設計稿單位描述：一段風景寬 600、高 300，畫面依高度等比例縮放後左右接續重複。
//! 每一層是一張 SVG（會重複鋪）；草地、路面這種不捲動的色帶另外用一條色帶表示。

use serde::{Deserialize, Serialize};

/// 設計稿單位：一段風景寬 600、高 300。
pub const TILE_W: f32 = 600.0;
pub const TILE_H: f32 = 300.0;
/// 地面每前進 1 公尺捲動多少設計單位（180 BPM、步幅 50 公分約每秒 135 單位）。
pub const UNITS_PER_METER: f32 = 90.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Scene {
    #[default]
    Riverside,
    City,
    Forest,
    Seaside,
}

impl Scene {
    pub const ALL: [Scene; 4] = [Self::Riverside, Self::City, Self::Forest, Self::Seaside];

    pub fn name_zh(self) -> &'static str {
        match self {
            Self::Riverside => "河濱公園",
            Self::City => "城市清晨",
            Self::Forest => "森林步道",
            Self::Seaside => "海邊",
        }
    }
}

/// 設定裡選的風景：固定一種、每天輪換，或不顯示。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum ScenePref {
    #[default]
    Riverside,
    City,
    Forest,
    Seaside,
    Daily,
    Off,
}

impl ScenePref {
    pub const ALL: [ScenePref; 6] = [Self::Riverside, Self::City, Self::Forest, Self::Seaside, Self::Daily, Self::Off];

    pub fn name_zh(self) -> &'static str {
        match self {
            Self::Riverside => Scene::Riverside.name_zh(),
            Self::City => Scene::City.name_zh(),
            Self::Forest => Scene::Forest.name_zh(),
            Self::Seaside => Scene::Seaside.name_zh(),
            Self::Daily => "每天輪換",
            Self::Off => "不顯示",
        }
    }

    /// 今天（epoch day）要畫哪一種風景；不顯示時回傳 None。每天輪換是依日期依序換，同一天固定同一種。
    pub fn scene(self, epoch_day: i64) -> Option<Scene> {
        match self {
            Self::Riverside => Some(Scene::Riverside),
            Self::City => Some(Scene::City),
            Self::Forest => Some(Scene::Forest),
            Self::Seaside => Some(Scene::Seaside),
            Self::Daily => Some(Scene::ALL[epoch_day.rem_euclid(Scene::ALL.len() as i64) as usize]),
            Self::Off => None,
        }
    }
}

/// 某一層目前往左捲了多少設計單位（0 以上、小於一段寬）。`speed` 是相對地面的速度，
/// 遠山慢、地面 1；跟著估算距離走，所以暫停時不動、節拍越快捲得越快。
pub fn layer_offset(meters: f64, speed: f32) -> f32 {
    let raw = (meters * f64::from(UNITS_PER_METER) * f64::from(speed)) % f64::from(TILE_W);
    (if raw < 0.0 { raw + f64::from(TILE_W) } else { raw }) as f32
}

/// 不捲動的色帶：從 `top`（設計稿單位）一路鋪到底，寬度是整個畫面。
#[derive(Debug, Clone, PartialEq)]
pub struct Band {
    pub top: f32,
    pub color: &'static str,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Layer {
    /// 相對地面的捲動速度：遠山 0.05、地面 1。
    pub speed: f32,
    /// 一段風景（600 x 300）的 SVG，左右會接續重複。
    pub svg: String,
    /// 這一層畫完之後、下一層之前鋪的色帶（草地、路面）。
    pub bands: Vec<Band>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Art {
    pub sky: &'static str,
    /// 不捲動的背景小物（太陽），左對齊畫在 600 x 300 的設計稿上。
    pub back_svg: String,
    /// 天空之上、各層之下的色帶（海面）。
    pub back_bands: Vec<Band>,
    pub layers: Vec<Layer>,
}

// ───────── 小工具：把形狀寫成 SVG ─────────

fn svg_tile(shapes: &str) -> String {
    format!(r#"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {TILE_W} {TILE_H}" width="{TILE_W}" height="{TILE_H}">{shapes}</svg>"#)
}

fn rect(x: f32, y: f32, w: f32, h: f32, fill: &str) -> String {
    format!(r#"<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"/>"#)
}

fn rrect(x: f32, y: f32, w: f32, h: f32, r: f32, fill: &str) -> String {
    format!(r#"<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"/>"#)
}

fn circle(cx: f32, cy: f32, r: f32, fill: &str) -> String {
    format!(r#"<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"/>"#)
}

fn ellipse(cx: f32, cy: f32, rx: f32, ry: f32, fill: &str) -> String {
    format!(r#"<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}"/>"#)
}

/// 依序連接的折線，最後封閉到底部。
fn ridge(bottom: f32, pts: &[(f32, f32)], fill: &str) -> String {
    let mut d = format!("M{} {}", pts[0].0, bottom);
    for (x, y) in pts {
        d.push_str(&format!("L{x} {y}"));
    }
    d.push_str(&format!("L{} {}Z", pts[pts.len() - 1].0, bottom));
    format!(r#"<path d="{d}" fill="{fill}"/>"#)
}

/// 底寬 `w`、高 `h`、底邊中心在 (cx, by) 的三角形（針葉樹、帆）。
fn triangle(cx: f32, by: f32, w: f32, h: f32, fill: &str) -> String {
    format!(r#"<path d="M{} {}L{} {}L{} {}Z" fill="{}"/>"#, cx - w / 2.0, by, cx, by - h, cx + w / 2.0, by, fill)
}

fn dashes(fill: &str, y: f32, w: f32, h: f32, xs: &[f32]) -> String {
    xs.iter().map(|x| rrect(*x, y, w, h, h / 2.0, fill)).collect()
}

fn band(top: f32, color: &'static str) -> Band {
    Band { top, color }
}

pub fn art_of(scene: Scene) -> Art {
    match scene {
        Scene::Riverside => riverside(),
        Scene::City => city(),
        Scene::Forest => forest(),
        Scene::Seaside => seaside(),
    }
}

fn riverside() -> Art {
    let trunk = "#8C5B3B";
    let far = ridge(
        190.0,
        &[(0.0, 190.0), (70.0, 140.0), (150.0, 170.0), (240.0, 110.0), (330.0, 165.0), (420.0, 130.0), (510.0, 175.0), (600.0, 150.0), (600.0, 190.0)],
        "#F4BE96",
    );
    let mut mid = rect(0.0, 190.0, 600.0, 30.0, "#E9A06F");
    for (x, y, r) in [(60.0, 176.0, 26.0), (220.0, 170.0, 32.0), (460.0, 178.0, 24.0), (560.0, 172.0, 28.0_f32)] {
        mid += &rect(x - 3.0, y + r - 6.0, 6.0, 22.0, trunk);
        mid += &circle(x, y, r, if r > 27.0 { "#D0905F" } else { "#C9875C" });
    }
    mid += &rect(330.0, 144.0, 4.0, 70.0, trunk);
    mid += &rrect(322.0, 140.0, 20.0, 8.0, 3.0, trunk);
    Art {
        sky: "#FFE2C4",
        back_svg: svg_tile(&circle(470.0, 70.0, 40.0, "#FFC94D")),
        back_bands: vec![],
        layers: vec![
            Layer { speed: 0.05, svg: svg_tile(&far), bands: vec![] },
            Layer { speed: 0.3, svg: svg_tile(&mid), bands: vec![band(214.0, "#7FB77E"), band(250.0, "#E8CFA8")] },
            Layer { speed: 1.0, svg: svg_tile(&dashes("#FFF6EC", 274.0, 44.0, 6.0, &[20.0, 170.0, 320.0, 470.0])), bands: vec![] },
        ],
    }
}

fn city() -> Art {
    let far: String = [
        (0.0, 120.0, 50.0, 80.0),
        (56.0, 80.0, 40.0, 120.0),
        (102.0, 140.0, 64.0, 60.0),
        (172.0, 60.0, 36.0, 140.0),
        (214.0, 110.0, 70.0, 90.0),
        (290.0, 90.0, 46.0, 110.0),
        (342.0, 130.0, 80.0, 70.0),
        (428.0, 100.0, 60.0, 100.0),
        (494.0, 70.0, 44.0, 130.0),
        (544.0, 120.0, 56.0, 80.0_f32),
    ]
    .iter()
    .map(|(x, y, w, h)| rect(*x, *y, *w, *h, "#9DB8D0"))
    .collect();
    let window = "#FFE8A8";
    let mut mid = String::new();
    mid += &rect(0.0, 160.0, 120.0, 70.0, "#6F8AA6");
    mid += &rect(140.0, 140.0, 90.0, 90.0, "#5E7894");
    mid += &rect(280.0, 150.0, 150.0, 80.0, "#6F8AA6");
    mid += &rect(450.0, 170.0, 150.0, 60.0, "#5E7894");
    for (x, y) in [(14.0, 174.0), (46.0, 174.0), (46.0, 196.0), (156.0, 154.0), (200.0, 176.0), (296.0, 164.0), (330.0, 164.0), (364.0, 186.0), (470.0, 182.0), (520.0, 182.0_f32)] {
        mid += &rect(x, y, 12.0, 9.0, window);
    }
    mid += &rect(250.0, 180.0, 4.0, 50.0, "#3E4E60");
    mid += &circle(252.0, 178.0, 7.0, window);
    Art {
        sky: "#BFD7EA",
        back_svg: svg_tile(""),
        back_bands: vec![],
        layers: vec![
            Layer { speed: 0.05, svg: svg_tile(&far), bands: vec![] },
            Layer { speed: 0.3, svg: svg_tile(&mid), bands: vec![band(224.0, "#8C99A6"), band(254.0, "#5D6875")] },
            Layer { speed: 1.0, svg: svg_tile(&dashes("#F2F2F2", 274.0, 56.0, 6.0, &[20.0, 220.0, 420.0])), bands: vec![] },
        ],
    }
}

fn forest() -> Art {
    let far = ridge(
        190.0,
        &[(0.0, 190.0), (80.0, 130.0), (160.0, 170.0), (260.0, 110.0), (360.0, 175.0), (450.0, 135.0), (540.0, 170.0), (600.0, 150.0), (600.0, 190.0)],
        "#B9D6C0",
    );
    let trees: String = [(42.0, 70.0), (118.0, 90.0), (220.0, 62.0), (326.0, 84.0), (442.0, 70.0), (548.0, 92.0_f32)]
        .iter()
        .map(|(x, h)| triangle(*x, 230.0, h * 0.62, *h, "#7FB394"))
        .collect();
    let bushes: String = [(40.0, 10.0), (56.0, 8.0), (260.0, 11.0), (430.0, 9.0), (446.0, 10.0_f32)]
        .iter()
        .map(|(x, r)| circle(*x, 252.0 - r * 0.2, *r, "#5E9C6F"))
        .collect();
    let stones: String = [(60.0, 10.0), (250.0, 7.0), (380.0, 12.0), (520.0, 8.0_f32)]
        .iter()
        .map(|(x, w)| ellipse(x + w / 2.0, 280.0 + w * 0.225, w / 2.0, w * 0.225, "#C4B28E"))
        .collect();
    Art {
        sky: "#DCEBDF",
        back_svg: svg_tile(""),
        back_bands: vec![],
        layers: vec![
            Layer { speed: 0.05, svg: svg_tile(&far), bands: vec![] },
            Layer { speed: 0.3, svg: svg_tile(&trees), bands: vec![band(224.0, "#9BC7A6"), band(256.0, "#D9C9A6")] },
            Layer { speed: 0.6, svg: svg_tile(&bushes), bands: vec![] },
            Layer { speed: 1.0, svg: svg_tile(&stones), bands: vec![] },
        ],
    }
}

fn seaside() -> Art {
    let mut wave = String::from("M0 216");
    let mut x = 0.0_f32;
    while x < 600.0 {
        wave.push_str(&format!("Q{} 202 {} 216", x + 25.0, x + 50.0));
        x += 50.0;
    }
    wave.push('Z');
    let sails = triangle(70.0, 160.0, 18.0, 18.0, "#FFFFFF") + &triangle(390.0, 160.0, 13.0, 14.0, "#FFFFFF");
    let shells: String = [(40.0, 10.0, 4.0), (210.0, 7.0, 3.0), (400.0, 12.0, 4.0), (520.0, 6.0, 3.0_f32)]
        .iter()
        .map(|(x, rx, ry)| ellipse(*x, 264.0, *rx, *ry, "#E2C690"))
        .collect();
    Art {
        sky: "#CFEAF4",
        back_svg: svg_tile(&circle(95.0, 69.0, 35.0, "#FFE08A")),
        back_bands: vec![band(144.0, "#5AAFD0")],
        layers: vec![
            Layer { speed: 0.05, svg: svg_tile(&sails), bands: vec![] },
            Layer {
                speed: 0.3,
                svg: svg_tile(&format!(r##"<path d="{wave}" fill="#FFFFFF" fill-opacity="0.8"/>"##)),
                bands: vec![band(214.0, "#F2DDB0")],
            },
            Layer { speed: 1.0, svg: svg_tile(&shells), bands: vec![] },
        ],
    }
}

/// 畫面上要用的 CSS：`url("data:image/svg+xml;…")`。
pub fn svg_css_url(svg: &str) -> String {
    format!("url(\"data:image/svg+xml;charset=utf-8,{}\")", pct_encode(svg))
}

fn pct_encode(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn layers_wrap_and_far_layers_move_slower() {
        for m in [0.0, 1.0, 12.5, 999.9, 123_456.7] {
            for speed in [0.05, 0.3, 0.6, 1.0] {
                let o = layer_offset(m, speed);
                assert!((0.0..TILE_W).contains(&o), "{m} {speed} → {o}");
            }
        }
        assert_eq!(layer_offset(0.0, 1.0), 0.0);
        // 同樣走 2 公尺：地面 180 單位、遠山只有 9 單位
        assert!((layer_offset(2.0, 1.0) - 180.0).abs() < 1e-3);
        assert!((layer_offset(2.0, 0.05) - 9.0).abs() < 1e-3);
        assert!(layer_offset(2.0, 0.3) < layer_offset(2.0, 1.0));
    }

    #[test]
    fn a_full_tile_of_distance_brings_the_layer_back_to_the_start() {
        // 地面走完一段風景（600 / 90 公尺）剛好接回開頭，所以重複鋪不會有接縫
        let tile_m = f64::from(TILE_W / UNITS_PER_METER);
        assert!(layer_offset(tile_m, 1.0) < 0.01 || (TILE_W - layer_offset(tile_m, 1.0)) < 0.01);
    }

    #[test]
    fn negative_distance_still_lands_inside_the_tile() {
        let o = layer_offset(-1.0, 1.0);
        assert!((o - 510.0).abs() < 1e-3, "{o}");
    }

    #[test]
    fn belt_speed_matches_the_documented_pace() {
        // 180 BPM、步幅 50 公分 = 每秒 1.5 公尺 = 每秒 135 單位
        assert!((layer_offset(1.5, 1.0) - 135.0).abs() < 1e-3);
    }

    #[test]
    fn daily_scene_rotates_by_day_and_stays_put_within_a_day() {
        let day = 20_000_i64;
        let today = ScenePref::Daily.scene(day);
        assert_eq!(today, ScenePref::Daily.scene(day), "同一天固定同一種");
        let week: std::collections::BTreeSet<_> = (0..4).map(|i| ScenePref::Daily.scene(day + i).unwrap().name_zh()).collect();
        assert_eq!(week.len(), 4, "連續四天四種都輪到");
        assert_eq!(ScenePref::Daily.scene(day + 4), today, "第五天接回第一種");
        assert!(ScenePref::Daily.scene(-1).is_some(), "epoch day 為負也不會當掉");
    }

    #[test]
    fn fixed_and_off_preferences() {
        assert_eq!(ScenePref::Forest.scene(1), Some(Scene::Forest));
        assert_eq!(ScenePref::Forest.scene(99_999), Some(Scene::Forest));
        assert_eq!(ScenePref::Off.scene(1), None);
        assert_eq!(ScenePref::default(), ScenePref::Riverside);
    }

    #[test]
    fn preferences_save_as_plain_lowercase_words() {
        for p in ScenePref::ALL {
            let json = serde_json::to_string(&p).unwrap();
            assert_eq!(serde_json::from_str::<ScenePref>(&json).unwrap(), p);
            assert!(json.chars().all(|c| c == '"' || c.is_ascii_lowercase()), "{json}");
            assert!(!p.name_zh().is_empty());
        }
    }

    fn is_hex(c: &str) -> bool {
        c.len() == 7 && c.starts_with('#') && c[1..].chars().all(|d| d.is_ascii_hexdigit())
    }

    /// 只由自我封閉的形狀組成的簡單 SVG：外層一個 <svg>，裡面每個標籤都以 /> 結尾。
    fn well_formed(svg: &str) -> bool {
        let Some(inner) = svg.strip_prefix(r#"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 300" width="600" height="300">"#).and_then(|s| s.strip_suffix("</svg>")) else {
            return false;
        };
        inner.is_empty() || (inner.starts_with('<') && inner.ends_with("/>") && inner.split('>').filter(|t| !t.is_empty()).all(|t| t.ends_with('/')))
    }

    #[test]
    fn every_scene_is_complete_and_well_formed() {
        for scene in Scene::ALL {
            let art = art_of(scene);
            assert!(is_hex(art.sky), "{scene:?}");
            assert!(well_formed(&art.back_svg), "{scene:?} back");
            assert!(art.layers.len() >= 3, "{scene:?} 至少要有遠、中、近三層");
            assert!(art.layers.windows(2).all(|w| w[0].speed < w[1].speed), "{scene:?} 由遠到近，速度要遞增");
            assert!((art.layers[0].speed - 0.05).abs() < 1e-6 && (art.layers.last().unwrap().speed - 1.0).abs() < 1e-6);
            for (i, l) in art.layers.iter().enumerate() {
                assert!(well_formed(&l.svg) && l.svg.len() > 150, "{scene:?} 第 {i} 層");
                for b in &l.bands {
                    assert!(is_hex(b.color) && (0.0..TILE_H).contains(&b.top), "{scene:?} band {b:?}");
                }
            }
            for b in &art.back_bands {
                assert!(is_hex(b.color) && (0.0..TILE_H).contains(&b.top));
            }
        }
    }

    #[test]
    fn scene_colors_match_the_android_palette() {
        // 抽幾個和 SceneryView.kt 一致的顏色，避免搬運時抄錯
        assert_eq!(art_of(Scene::Riverside).sky, "#FFE2C4");
        assert_eq!(art_of(Scene::City).sky, "#BFD7EA");
        assert_eq!(art_of(Scene::Forest).sky, "#DCEBDF");
        assert_eq!(art_of(Scene::Seaside).sky, "#CFEAF4");
        assert_eq!(art_of(Scene::Seaside).back_bands, vec![band(144.0, "#5AAFD0")]);
        assert_eq!(art_of(Scene::Riverside).layers[1].bands, vec![band(214.0, "#7FB77E"), band(250.0, "#E8CFA8")]);
        assert_eq!(art_of(Scene::Forest).layers.len(), 4);
    }

    #[test]
    fn shapes_stay_inside_the_tile() {
        // 每個形狀的座標都在 0..=600 x 0..=300 內，捲動時才不會被切掉或露出空白
        for scene in Scene::ALL {
            for l in &art_of(scene).layers {
                // 先拿掉最外層 <svg …> 的宣告與所有顏色（#D0905F 裡的數字不是座標）
                let body = &l.svg[l.svg.find('>').unwrap() + 1..];
                let mut coords = String::new();
                let mut rest = body;
                while let Some(i) = rest.find("fill=\"") {
                    coords += &rest[..i];
                    rest = &rest[i + 6..];
                    rest = &rest[rest.find('"').unwrap() + 1..];
                }
                coords += rest;
                for num in coords.split(|c: char| !(c.is_ascii_digit() || c == '.' || c == '-')).filter(|s| !s.is_empty() && *s != "-" && *s != ".") {
                    let v: f32 = num.parse().unwrap();
                    assert!((-1.0..=601.0).contains(&v), "{scene:?} 有超出範圍的數字 {v}");
                }
            }
        }
    }

    #[test]
    fn css_url_is_safe_to_put_in_a_style_attribute() {
        let u = svg_css_url(&art_of(Scene::Forest).layers[0].svg);
        assert!(u.starts_with("url(\"data:image/svg+xml;charset=utf-8,%3Csvg"));
        assert!(u.ends_with("\")"));
        let body = &u[u.find(',').unwrap() + 1..u.len() - 2];
        assert!(!body.contains(['"', '<', '>', ' ', '#']), "必須全部編碼：{body}");
    }
}
