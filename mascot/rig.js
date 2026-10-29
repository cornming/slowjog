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
/**
 * 小人物的外型風格。骨架與姿勢共用，只換顏色、材質與頭部配件：
 * blue 小藍（原本的 3D Q 版）、orange 小橘（卡通描邊、馬尾）、
 * pixel 小點（同小藍外型，輸出時降解析度做成像素風）、shiba 阿柴（狗狗跑者）。
 */
const STYLES = {
  blue: { skin: 0xffd6b3, hair: 0x3a2b26, shirt: 0x1e7fd6, shorts: 0x2c3a4d, shoe: 0xff7a3d, limb: 0xffd6b3, head: 'kid', toon: false },
  orange: { skin: 0xf5c9a0, hair: 0x2b2b3a, shirt: 0xf2683c, shorts: 0x2c3a4d, shoe: 0xffffff, limb: 0xf5c9a0, head: 'ponytail', toon: true },
  pixel: { skin: 0xffd6b3, hair: 0x3a2b26, shirt: 0x1e7fd6, shorts: 0x2c3a4d, shoe: 0xff7a3d, limb: 0xffd6b3, head: 'kid', toon: false, flat: true, pixel: 6 },
  // 阿柴穿運動服：T 恤、短褲、頭帶，手是白色肉球，看起來像擬人的狗狗跑者
  shiba: { skin: 0xd9772e, hair: 0xd9772e, shirt: 0x2a9d8f, shorts: 0x2c3a4d, shoe: 0xff7a3d, limb: 0xd9772e, hand: 0xfff1df, head: 'shiba', toon: true, cream: 0xfff1df, band: 0xe63946 },
};

function buildMascot(styleName) {
  const st = STYLES[styleName || 'blue'] || STYLES.blue;
  let gradient = null;
  if (st.toon) {
    // 三階明暗的卡通材質
    const data = new Uint8Array([100, 100, 100, 255, 190, 190, 190, 255, 255, 255, 255, 255]);
    gradient = new THREE.DataTexture(data, 3, 1, THREE.RGBAFormat);
    gradient.minFilter = THREE.NearestFilter;
    gradient.magFilter = THREE.NearestFilter;
    gradient.generateMipmaps = false;
    gradient.needsUpdate = true;
  }
  const M = (c) => {
    if (st.toon) return new THREE.MeshToonMaterial({ color: c, gradientMap: gradient });
    if (st.flat) return new THREE.MeshLambertMaterial({ color: c });
    return new THREE.MeshStandardMaterial({ color: c, roughness: 0.65, metalness: 0 });
  };
  const skin = M(st.skin), hairM = M(st.hair), shirt = M(st.shirt), shorts = M(st.shorts);
  const shoe = M(st.shoe), limb = M(st.limb), dark = M(0x1c1c1e), white = M(0xffffff), cheek = M(0xff9aa5);

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
  if (st.belly) {
    const belly = mesh(new THREE.SphereGeometry(0.22, 16, 12), M(st.cream), 0, -0.02, 0.17);
    belly.scale.set(1, 1.2, 0.55);
    torso.add(belly);
  }

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
    const fore = capsule(0.085, 0.3, limb);
    fore.position.y = -0.23;
    elbow.add(fore);
    elbow.add(mesh(new THREE.SphereGeometry(st.hand ? 0.12 : 0.1, 14, 10), st.hand ? M(st.hand) : limb, 0, -0.5, 0));
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
    const shin = capsule(0.1, 0.3, limb);
    shin.position.y = -0.25;
    knee.add(shin);
    // 腳踝：伸展動作需要讓腳掌保持貼地，所以腳掌放在自己的轉軸上（預設不轉，外觀不變）
    const ankle = new THREE.Group();
    ankle.position.y = -0.5;
    knee.add(ankle);
    const foot = mesh(new THREE.SphereGeometry(0.13, 16, 12), shoe, 0, 0, 0.08);
    foot.scale.set(1, 0.75, 1.7);
    ankle.add(foot);
    figure.add(hip);
    return { hip, knee, ankle };
  };

  // 頭：大頭 Q 版比例，臉朝 +z
  const head = new THREE.Group();
  head.position.set(0, 2.38, 0);
  figure.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.62, 32, 24), skin));
  const tuft = new THREE.Group();
  if (st.head === 'shiba') {
    // 狗狗：三角耳、白色口鼻、黑鼻頭，頸上一條藍色項圈
    [-1, 1].forEach((sx) => {
      const ear = mesh(new THREE.ConeGeometry(0.2, 0.36, 3), hairM, sx * 0.34, 0.54, -0.05);
      ear.rotation.z = -sx * 0.35;
      head.add(ear);
    });
    const muzzle = mesh(new THREE.SphereGeometry(0.28, 20, 14), M(st.cream), 0, -0.18, 0.5);
    muzzle.scale.set(1.05, 0.75, 1.05);
    head.add(muzzle);
    const nose = mesh(new THREE.SphereGeometry(0.08, 12, 8), dark, 0, -0.1, 0.8);
    nose.scale.set(1.3, 0.9, 0.9);
    head.add(nose);
    // 運動頭帶，繞在耳朵下方
    const headband = mesh(new THREE.TorusGeometry(0.6, 0.06, 8, 32), M(st.band), 0, 0.28, -0.02);
    headband.rotation.x = Math.PI / 2 - 0.25;
    head.add(headband);
    // 捲尾巴當作會晃的 tuft，掛在臀部後面
    tuft.position.set(0, 1.25, -0.3);
    const tail = mesh(new THREE.TorusGeometry(0.13, 0.065, 8, 16, Math.PI * 1.5), hairM, 0, 0.1, -0.05);
    tail.rotation.y = Math.PI / 2;
    tuft.add(tail);
    figure.add(tuft);
  } else {
    const hair = mesh(new THREE.SphereGeometry(0.655, 32, 20, 0, Math.PI * 2, 0, Math.PI * 0.5), hairM);
    hair.rotation.x = -0.5;
    head.add(hair);
    if (st.head === 'ponytail') {
      // 馬尾：掛在後腦，跑步時跟著晃
      tuft.position.set(0, 0.25, -0.55);
      const tail = mesh(new THREE.SphereGeometry(0.16, 14, 10), hairM, 0, -0.18, -0.08);
      tail.scale.set(0.8, 1.7, 0.8);
      tuft.add(tail);
      tuft.add(mesh(new THREE.SphereGeometry(0.07, 10, 8), M(st.shirt), 0, 0.02, 0));
    } else {
      tuft.position.set(0, 0.62, -0.05);
      const tuftBlob = mesh(new THREE.SphereGeometry(0.1, 12, 10), hairM, 0, 0.1, 0);
      tuftBlob.scale.set(0.8, 1.4, 0.8);
      tuft.add(tuftBlob);
    }
    head.add(tuft);
  }
  const eyeY = st.head === 'shiba' ? 0.12 : 0.02;
  [-1, 1].forEach((sx) => {
    const eye = mesh(new THREE.SphereGeometry(0.07, 12, 10), dark, sx * 0.22, eyeY, 0.555);
    eye.scale.set(1, 1.35, 0.6);
    head.add(eye);
    head.add(mesh(new THREE.SphereGeometry(0.022, 8, 6), white, sx * 0.22 + 0.02, eyeY + 0.04, 0.595));
    {
      const blush = mesh(new THREE.SphereGeometry(0.1, 12, 8), cheek, sx * 0.37, -0.1, 0.47);
      blush.scale.set(1, 0.6, 0.4);
      head.add(blush);
    }
  });
  const smile = mesh(new THREE.TorusGeometry(0.1, 0.022, 8, 20, Math.PI), dark, 0, st.head === 'shiba' ? -0.3 : -0.15, st.head === 'shiba' ? 0.72 : 0.58);
  smile.rotation.z = Math.PI;
  head.add(smile);

  const arms = { L: makeArm(1), R: makeArm(-1) };
  const legs = { L: makeLeg(1), R: makeLeg(-1) };

  if (st.toon) {
    // 卡通描邊：每個零件再疊一個稍大、只畫背面的深色外殼
    const outline = new THREE.MeshBasicMaterial({ color: 0x2b2b3a, side: THREE.BackSide });
    const meshes = [];
    root.traverse((o) => { if (o.isMesh) meshes.push(o); });
    meshes.forEach((m) => {
      if (m.geometry.type === 'TorusGeometry' && m.geometry.parameters.tube < 0.03) return; // 嘴巴不描
      if (m.geometry.parameters && m.geometry.parameters.radius !== undefined && m.geometry.parameters.radius < 0.03) return; // 眼睛反光
      const o = new THREE.Mesh(m.geometry, outline);
      const k = m.geometry.type === 'CylinderGeometry' ? 1.12 : 1.07;
      o.scale.set(m.geometry.type === 'CylinderGeometry' ? k : k, m.geometry.type === 'CylinderGeometry' ? 1 : k, m.geometry.type === 'CylinderGeometry' ? k : k);
      m.add(o);
    });
  }

  figure.rotation.x = 0.05; // 微微前傾
  return { root, figure, torso, head, tuft, arms, legs, headBaseY: 2.38, style: st };
}

/**
 * 套用姿勢。除了跑步用的欄位，還支援暖身與伸展用的選填欄位（沒填就是 0）：
 * upperLz / upperRz（手臂往外張開，左臂為正、右臂為負）、upperLy / upperRy（手臂左右轉向）、
 * thighLz / thighRz（腿往外張開）、ankleL / ankleR（腳踝）、
 * lean（上身以髖為軸前傾）、side（上身側彎）、headTilt、headTwist、tilt（整個人前傾，預設 0.05）、dz（整個人前後平移）。
 */
function applyPose(rig, ps) {
  const v = (name) => ps[name] || 0;
  rig.legs.L.hip.rotation.order = 'YXZ';
  rig.legs.R.hip.rotation.order = 'YXZ';
  rig.arms.L.shoulder.rotation.order = 'YXZ';
  rig.arms.R.shoulder.rotation.order = 'YXZ';
  rig.legs.L.hip.rotation.set(ps.thighL, 0, v('thighLz'));
  rig.legs.R.hip.rotation.set(ps.thighR, 0, v('thighRz'));
  rig.legs.L.knee.rotation.x = ps.shinL;
  rig.legs.R.knee.rotation.x = ps.shinR;
  rig.legs.L.ankle.rotation.x = v('ankleL');
  rig.legs.R.ankle.rotation.x = v('ankleR');
  rig.arms.L.shoulder.rotation.set(ps.upperL, v('upperLy'), v('upperLz'));
  rig.arms.R.shoulder.rotation.set(ps.upperR, v('upperRy'), v('upperRz'));
  rig.arms.L.elbow.rotation.x = ps.foreL;
  rig.arms.R.elbow.rotation.x = ps.foreR;
  rig.figure.position.y = ps.bob;
  rig.figure.position.z = v('dz'); // 整個人前後平移（前彎時把重心拉回畫面中央）
  rig.figure.rotation.x = ps.tilt !== undefined ? ps.tilt : 0.05;

  // 上身以髖（高度 1.12）為軸前傾或側彎，頭與上身一起動；沒有前傾與側彎時位置和原本完全相同。
  const lean = v('lean');
  const side = v('side');
  const euler = new THREE.Euler(lean, 0, side, 'XZY');
  const torsoPos = new THREE.Vector3(0, 0.3, 0).applyEuler(euler);
  rig.torso.position.set(torsoPos.x, 1.12 + torsoPos.y, torsoPos.z);
  rig.torso.rotation.order = 'XZY'; // 先轉身、再側彎、最後前傾，轉體時才會繞著自己的軸
  rig.torso.rotation.set(lean, ps.twist, side);
  const headPos = new THREE.Vector3(0, rig.headBaseY - 1.12 + ps.headBob, 0).applyEuler(euler);
  rig.head.position.set(headPos.x, 1.12 + headPos.y, headPos.z);
  rig.head.rotation.set(lean + v('headTilt'), v('headTwist'), side);
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
