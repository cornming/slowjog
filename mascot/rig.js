// 小人物角色與跑步姿勢，從網頁原型擷取（需要全域的 THREE，three.js r128）。
// 網頁原型與影格輸出共用這份程式，確保外觀一致。
// ===== <logic> 純邏輯：不依賴畫面，可單獨測試 =====
const VIEWS = [
  { deg: 0, name: '正面' },
  { deg: 45, name: '45 度側面' },
  { deg: 90, name: '90 度側面' },
  { deg: -45, name: '另一側 45 度' },
];

const MODES = {
  demo: { view: 10, bubbleMin: 5, bubbleMax: 9, hint: '視角每 10 秒轉一次，氣泡每 5 到 9 秒出現一次，方便在這裡看完整效果。' },
  real: { view: 300, bubbleMin: 60, bubbleMax: 120, hint: '視角每 5 分鐘轉一次，氣泡每 1 到 2 分鐘出現一次，和實際運動時相同。' },
};

const MESSAGES = [
  '今天也出門了，很棒！',
  '步子小小的，剛剛好。',
  '慢到還能聊天，就是對的速度。',
  '肩膀放鬆，嘴角上揚～',
  '呼吸順順的，繼續保持。',
  '跟著我的腳步，一二、一二～',
  '不用追求快，穩穩地跑就好。',
  '腳掌輕輕落地，像踩在雲上。',
  '抬頭挺胸，看向遠方。',
  '每一步都算數！',
  '節拍 {bpm}，我們同步囉！',
];

/** 一個完整步伐循環（兩步）的各關節角度，單位為弧度。phi 為循環相位。 */
function pose(phi) {
  const A = 0.4;      // 大腿前後擺幅：小步幅
  const K = 0.9;      // 膝蓋在擺腿期的彎曲量
  const FLEX = 0.12;  // 膝蓋平常微彎
  const B = 0.45;     // 上臂擺幅
  const leg = (p) => ({ thigh: -A * Math.sin(p), shin: FLEX + K * Math.max(0, Math.cos(p)) });
  const arm = (p) => ({ upper: B * Math.sin(p), fore: -(1.15 + 0.15 * Math.sin(p)) });
  const L = leg(phi);
  const R = leg(phi + Math.PI);
  const aL = arm(phi);
  const aR = arm(phi + Math.PI);
  return {
    thighL: L.thigh, thighR: R.thigh,
    shinL: L.shin, shinR: R.shin,
    upperL: aL.upper, upperR: aR.upper,
    foreL: aL.fore, foreR: aR.fore,
    bob: 0.05 * Math.cos(2 * phi),
    twist: 0.12 * Math.sin(phi),
    headBob: 0.02 * Math.cos(2 * phi - 0.6),
    tuft: 0.3 * Math.sin(2 * phi + 1),
  };
}

function fmtTime(sec) {
  const s = Math.max(0, Math.ceil(sec));
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
}

/** 挑一則和上一則不同的鼓勵文字。rand 為 0 到 1 的亂數函式。 */
function nextMessage(prevIndex, rand, bpm) {
  let i = Math.floor(rand() * MESSAGES.length);
  if (i === prevIndex) i = (i + 1) % MESSAGES.length;
  return { index: i, text: MESSAGES[i].replace('{bpm}', String(bpm)) };
}
// ===== </logic> =====

// ===== <scene> 角色與地面：只用 three.js，不碰 DOM =====
function buildMascot() {
  const M = (c) => new THREE.MeshStandardMaterial({ color: c, roughness: 0.65, metalness: 0 });
  const skin = M(0xffd6b3), hairM = M(0x3a2b26), shirt = M(0x1e7fd6), shorts = M(0x2c3a4d);
  const shoe = M(0xff7a3d), dark = M(0x1c1c1e), white = M(0xffffff), cheek = M(0xff9aa5);

  const mesh = (geo, mat, x, y, z) => {
    const m = new THREE.Mesh(geo, mat);
    m.position.set(x || 0, y || 0, z || 0);
    m.castShadow = true;
    return m;
  };
  const capsule = (r, len, mat) => {
    const g = new THREE.Group();
    g.add(mesh(new THREE.CylinderGeometry(r, r, len, 16), mat));
    g.add(mesh(new THREE.SphereGeometry(r, 16, 12), mat, 0, len / 2, 0));
    g.add(mesh(new THREE.SphereGeometry(r, 16, 12), mat, 0, -len / 2, 0));
    return g;
  };

  const root = new THREE.Group();
  const figure = new THREE.Group();
  root.add(figure);

  // 髖部與軀幹
  const hips = mesh(new THREE.SphereGeometry(0.3, 20, 14), shorts, 0, 1.12, 0);
  hips.scale.set(1.15, 0.72, 0.95);
  figure.add(hips);
  const torso = new THREE.Group();
  torso.position.set(0, 1.42, 0);
  figure.add(torso);
  const body = capsule(0.3, 0.3, shirt);
  body.scale.set(1.08, 1, 0.9);
  torso.add(body);

  // 手臂：肩膀為轉軸，手肘彎曲
  const makeArm = (side) => {
    const shoulder = new THREE.Group();
    shoulder.position.set(side * 0.4, 0.2, 0);
    const upper = capsule(0.1, 0.3, shirt);
    upper.position.y = -0.25;
    shoulder.add(upper);
    const elbow = new THREE.Group();
    elbow.position.y = -0.5;
    shoulder.add(elbow);
    const fore = capsule(0.085, 0.3, skin);
    fore.position.y = -0.23;
    elbow.add(fore);
    elbow.add(mesh(new THREE.SphereGeometry(0.1, 14, 10), skin, 0, -0.5, 0));
    torso.add(shoulder);
    return { shoulder, elbow };
  };

  // 腿：髖為轉軸，膝蓋彎曲；鞋底剛好落在 y = 0
  const makeLeg = (side) => {
    const hip = new THREE.Group();
    hip.position.set(side * 0.2, 1.1, 0);
    const thigh = capsule(0.12, 0.26, shorts);
    thigh.position.y = -0.25;
    hip.add(thigh);
    const knee = new THREE.Group();
    knee.position.y = -0.5;
    hip.add(knee);
    const shin = capsule(0.1, 0.3, skin);
    shin.position.y = -0.25;
    knee.add(shin);
    const foot = mesh(new THREE.SphereGeometry(0.13, 16, 12), shoe, 0, -0.5, 0.08);
    foot.scale.set(1, 0.75, 1.7);
    knee.add(foot);
    figure.add(hip);
    return { hip, knee };
  };

  // 頭：大頭 Q 版比例，臉朝 +z
  const head = new THREE.Group();
  head.position.set(0, 2.38, 0);
  figure.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.62, 32, 24), skin));
  const hair = mesh(new THREE.SphereGeometry(0.655, 32, 20, 0, Math.PI * 2, 0, Math.PI * 0.5), hairM);
  hair.rotation.x = -0.5;
  head.add(hair);
  const tuft = new THREE.Group();
  tuft.position.set(0, 0.62, -0.05);
  const tuftBlob = mesh(new THREE.SphereGeometry(0.1, 12, 10), hairM, 0, 0.1, 0);
  tuftBlob.scale.set(0.8, 1.4, 0.8);
  tuft.add(tuftBlob);
  head.add(tuft);
  [-1, 1].forEach((sx) => {
    const eye = mesh(new THREE.SphereGeometry(0.07, 12, 10), dark, sx * 0.22, 0.02, 0.555);
    eye.scale.set(1, 1.35, 0.6);
    head.add(eye);
    head.add(mesh(new THREE.SphereGeometry(0.022, 8, 6), white, sx * 0.22 + 0.02, 0.06, 0.595));
    const blush = mesh(new THREE.SphereGeometry(0.1, 12, 8), cheek, sx * 0.37, -0.1, 0.47);
    blush.scale.set(1, 0.6, 0.4);
    head.add(blush);
  });
  const smile = mesh(new THREE.TorusGeometry(0.1, 0.022, 8, 20, Math.PI), dark, 0, -0.15, 0.58);
  smile.rotation.z = Math.PI;
  head.add(smile);

  const arms = { L: makeArm(1), R: makeArm(-1) };
  const legs = { L: makeLeg(1), R: makeLeg(-1) };
  figure.rotation.x = 0.05; // 微微前傾
  return { root, figure, torso, head, tuft, arms, legs, headBaseY: 2.38 };
}

function applyPose(rig, ps) {
  rig.legs.L.hip.rotation.x = ps.thighL;
  rig.legs.R.hip.rotation.x = ps.thighR;
  rig.legs.L.knee.rotation.x = ps.shinL;
  rig.legs.R.knee.rotation.x = ps.shinR;
  rig.arms.L.shoulder.rotation.x = ps.upperL;
  rig.arms.R.shoulder.rotation.x = ps.upperR;
  rig.arms.L.elbow.rotation.x = ps.foreL;
  rig.arms.R.elbow.rotation.x = ps.foreR;
  rig.figure.position.y = ps.bob;
  rig.torso.rotation.y = ps.twist;
  rig.head.position.y = rig.headBaseY + ps.headBob;
  rig.tuft.rotation.z = ps.tuft;
}

function buildGround() {
  const group = new THREE.Group();
  const shadowMat = new THREE.ShadowMaterial({ opacity: 0.28 });
  const shadowPlane = new THREE.Mesh(new THREE.PlaneGeometry(16, 16), shadowMat);
  shadowPlane.rotation.x = -Math.PI / 2;
  shadowPlane.receiveShadow = true;
  group.add(shadowPlane);

  // 兩排地面標線，往後退以呈現前進
  const dashMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.85 });
  const count = 9, spacing = 1.6, span = count * spacing;
  const dashes = [];
  [-1.7, 1.7].forEach((x) => {
    for (let i = 0; i < count; i++) {
      const d = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.01, 0.7), dashMat);
      d.position.set(x, 0.005, 0);
      group.add(d);
      dashes.push({ mesh: d, base: i * spacing });
    }
  });

  // 每一拍亮起的圓圈
  const ringMat = new THREE.MeshBasicMaterial({ color: 0x1e7fd6, transparent: true, opacity: 0, depthWrite: false });
  const ring = new THREE.Mesh(new THREE.RingGeometry(0.95, 1.05, 56), ringMat);
  ring.rotation.x = -Math.PI / 2;
  ring.position.y = 0.012;
  group.add(ring);

  return { group, shadowMat, dashMat, ringMat, ring, dashes, span };
}

function updateGround(g, travel, beatFrac) {
  g.dashes.forEach((d) => {
    const z = ((((d.base - travel) % g.span) + g.span) % g.span) - g.span / 2;
    d.mesh.position.z = z;
  });
  g.ringMat.opacity = 0.4 * Math.exp(-7 * beatFrac);
  const s = 0.9 + beatFrac * 0.5;
  g.ring.scale.set(s, s, s);
}
// ===== </scene> =====
