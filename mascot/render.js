// 把小人物轉成多角度、多影格的透明背景 PNG 影格表。
// 用法：cd mascot && npm install && node render.js
// 輸出：app/src/main/res/drawable-nodpi/mascot_v<角度>.png，每張 6 欄 x 2 列 = 12 格，一格 384 x 480。
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const ANGLES = [0, 15, 30, 45, 60, 75, 90]; // 另一側 45 度由 App 左右鏡像取得
const FRAMES = 12;                          // 一個步伐循環（兩步）
const FW = 384, FH = 480, COLS = 6, ROWS = 2;

(async () => {
  const three = fs.readFileSync(path.join(__dirname, 'node_modules/three/build/three.min.js'), 'utf8');
  const rig = fs.readFileSync(path.join(__dirname, 'rig.js'), 'utf8');
  const moves = fs.readFileSync(path.join(__dirname, 'moves.js'), 'utf8');
  const OUT = process.env.OUT || path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
  const ONLY = process.env.ONLY ? process.env.ONLY.split(',') : null; // 例如 ONLY=move 或 ONLY=run
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  await page.setContent('<canvas id="c" width="' + FW + '" height="' + FH + '"></canvas><canvas id="sheet" width="' + FW * COLS + '" height="' + FH * ROWS + '"></canvas>');
  await page.addScriptTag({ content: three });
  await page.addScriptTag({ content: rig });
  await page.addScriptTag({ content: moves });
  await page.evaluate(({ FW, FH }) => {
    const renderer = new THREE.WebGLRenderer({ canvas: document.getElementById('c'), antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setClearColor(0x000000, 0);
    renderer.setSize(FW, FH, false);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(19, FW / FH, 0.1, 60);
    scene.add(new THREE.HemisphereLight(0xffffff, 0x8aa39a, 0.85));
    const sun = new THREE.DirectionalLight(0xffffff, 0.9);
    sun.position.set(3, 6, 4);
    sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    Object.assign(sun.shadow.camera, { left: -4, right: 4, top: 4, bottom: -4, near: 1, far: 20 });
    scene.add(sun);
    const mascot = buildMascot();
    scene.add(mascot.root);
    const shadowPlane = new THREE.Mesh(new THREE.PlaneGeometry(16, 16), new THREE.ShadowMaterial({ opacity: 0.28 }));
    shadowPlane.rotation.x = -Math.PI / 2;
    shadowPlane.receiveShadow = true;
    scene.add(shadowPlane);
    window.renderSheet = (deg, frames, cols) => {
      const sheet = document.getElementById('sheet');
      const ctx = sheet.getContext('2d');
      ctx.clearRect(0, 0, sheet.width, sheet.height);
      const az = deg * Math.PI / 180;
      camera.position.set(11.5 * Math.sin(az), 2.2, 11.5 * Math.cos(az));
      camera.lookAt(0, 1.5, 0);
      for (let k = 0; k < frames; k++) {
        const beat = k / frames * 2;                 // 一個循環 = 2 拍
        applyPose(mascot, pose(Math.PI * beat + Math.PI / 2)); // 第 0 格 = 腳落地、對齊節拍
        renderer.render(scene, camera);
        ctx.drawImage(renderer.domElement, (k % cols) * FW, Math.floor(k / cols) * FH);
      }
      return sheet.toDataURL('image/png');
    };
    // 暖身與伸展：每個動作一張，視角固定。
    window.renderMove = (id, frames, cols) => {
      const move = MOVES[id];
      const sheet = document.getElementById('sheet');
      const ctx = sheet.getContext('2d');
      ctx.clearRect(0, 0, sheet.width, sheet.height);
      const az = move.angle * Math.PI / 180;
      camera.position.set(11.5 * Math.sin(az), 2.2, 11.5 * Math.cos(az));
      camera.lookAt(0, 1.5, 0);
      for (let k = 0; k < frames; k++) {
        applyPose(mascot, move.pose(k / frames));
        groundFigure(mascot);
        renderer.render(scene, camera);
        ctx.drawImage(renderer.domElement, (k % cols) * FW, Math.floor(k / cols) * FH);
      }
      return sheet.toDataURL('image/png');
    };
  }, { FW, FH });
  if (!ONLY || ONLY.includes('run')) {
    for (const deg of ANGLES) {
      const url = await page.evaluate(([d, f, c]) => window.renderSheet(d, f, c), [deg, FRAMES, COLS]);
      const file = path.join(OUT, 'mascot_v' + String(deg).padStart(3, '0') + '.png');
      fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
      console.log('wrote', file);
    }
  }
  if (!ONLY || ONLY.includes('move')) {
    const ids = await page.evaluate(() => Object.keys(MOVES));
    for (const id of ids) {
      const url = await page.evaluate(([i, f, c]) => window.renderMove(i, f, c), [id, FRAMES, COLS]);
      const file = path.join(OUT, 'mascot_m_' + id + '.png');
      fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
      console.log('wrote', file);
    }
  }
  await browser.close();
})();
