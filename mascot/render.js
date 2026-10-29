// 把小人物轉成透明背景的影格表（PNG），再由 to-webp.py 轉成 App 用的 WebP。
// 用法：cd mascot && npm install && node render.js && python3 to-webp.py
// 每種風格輸出（OUT 預設為 mascot/out/）：
//   mascot_<風格>_run.png      側面跑步，6 欄 x 2 列 = 12 格（一個步伐循環 = 2 拍）
//   mascot_<風格>_idle.png     正面站姿，1 格
//   mascot_<風格>_m_<動作>.png 暖身與伸展動作，6 欄 x 4 列 = 24 格
// 一格 384 x 480。STYLE=blue,shiba 可以只算部分風格，ONLY=run|idle|move 只算部分種類。
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const STYLE_IDS = ['blue', 'orange', 'pixel', 'shiba'];
const RUN_FRAMES = 12;
const MOVE_FRAMES = 24;
const FW = 384, FH = 480, COLS = 6;

(async () => {
  const three = fs.readFileSync(path.join(__dirname, 'node_modules/three/build/three.min.js'), 'utf8');
  const rig = fs.readFileSync(path.join(__dirname, 'rig.js'), 'utf8');
  const moves = fs.readFileSync(path.join(__dirname, 'moves.js'), 'utf8');
  const OUT = process.env.OUT || path.join(__dirname, 'out');
  const ONLY = process.env.ONLY ? process.env.ONLY.split(',') : null;
  const styles = process.env.STYLE ? process.env.STYLE.split(',') : STYLE_IDS;
  fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  await page.setContent('<canvas id="c" width="' + FW + '" height="' + FH + '"></canvas><canvas id="sheet"></canvas>');
  await page.addScriptTag({ content: three });
  await page.addScriptTag({ content: rig });
  await page.addScriptTag({ content: moves });
  await page.evaluate(({ FW, FH }) => {
    const renderer = new THREE.WebGLRenderer({ canvas: document.getElementById('c'), antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setClearColor(0x000000, 0);
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
    const shadowPlane = new THREE.Mesh(new THREE.PlaneGeometry(16, 16), new THREE.ShadowMaterial({ opacity: 0.28 }));
    shadowPlane.rotation.x = -Math.PI / 2;
    shadowPlane.receiveShadow = true;
    scene.add(shadowPlane);

    let mascot = null;
    const hemi = scene.children.find((o) => o.isHemisphereLight);
    window.useStyle = (id) => {
      if (mascot) scene.remove(mascot.root);
      mascot = buildMascot(id);
      scene.add(mascot.root);
      // 卡通材質的亮部不會自然衰減，燈光調暗一點才不會整片過曝。
      hemi.intensity = mascot.style.toon ? 0.5 : 0.85;
      sun.intensity = mascot.style.toon ? 0.6 : 0.9;
    };

    const aim = (deg) => {
      const az = deg * Math.PI / 180;
      camera.position.set(11.5 * Math.sin(az), 2.2, 11.5 * Math.cos(az));
      camera.lookAt(0, 1.5, 0);
    };

    // 畫一格到影格表：像素風先用很小的解析度算，再用最近鄰放大，做出一格一格的像素。
    const shot = (ctx, k) => {
      const px = mascot.style.pixel;
      if (px) {
        renderer.setSize(FW / px, FH / px, false);
        renderer.render(scene, camera);
        ctx.imageSmoothingEnabled = false;
        ctx.drawImage(renderer.domElement, 0, 0, FW / px, FH / px, (k % 6) * FW, Math.floor(k / 6) * FH, FW, FH);
      } else {
        renderer.setSize(FW, FH, false);
        renderer.render(scene, camera);
        ctx.drawImage(renderer.domElement, 0, 0, FW, FH, (k % 6) * FW, Math.floor(k / 6) * FH, FW, FH);
      }
    };

    const sheetFor = (frames, cols) => {
      const sheet = document.getElementById('sheet');
      sheet.width = FW * Math.min(cols, frames);
      sheet.height = FH * Math.ceil(frames / cols);
      const ctx = sheet.getContext('2d');
      ctx.clearRect(0, 0, sheet.width, sheet.height);
      return { sheet, ctx };
    };

    window.renderRun = (frames, cols) => {
      const { sheet, ctx } = sheetFor(frames, cols);
      aim(90);
      for (let k = 0; k < frames; k++) {
        const beat = k / frames * 2; // 一個循環 = 2 拍
        applyPose(mascot, pose(Math.PI * beat + Math.PI / 2)); // 第 0 格 = 腳落地、對齊節拍
        shot(ctx, k);
      }
      return sheet.toDataURL('image/png');
    };

    window.renderIdle = () => {
      const { sheet, ctx } = sheetFor(1, 1);
      aim(0);
      applyPose(mascot, Object.assign(neutral(), { foreL: -0.25, foreR: -0.25, upperLz: 0.12, upperRz: -0.12 }));
      groundFigure(mascot);
      shot(ctx, 0);
      return sheet.toDataURL('image/png');
    };

    window.renderMove = (id, frames, cols) => {
      const move = MOVES[id];
      const { sheet, ctx } = sheetFor(frames, cols);
      aim(move.angle);
      for (let k = 0; k < frames; k++) {
        const ps = move.pose(k / frames);
        applyPose(mascot, ps);
        groundFigure(mascot, ps.lift || 0);
        shot(ctx, k);
      }
      return sheet.toDataURL('image/png');
    };
  }, { FW, FH });

  const save = (name, url) => {
    const file = path.join(OUT, name);
    fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
    console.log('wrote', file);
  };

  const moveIds = await page.evaluate(() => Object.keys(MOVES));
  for (const style of styles) {
    await page.evaluate((s) => window.useStyle(s), style);
    if (!ONLY || ONLY.includes('run')) save('mascot_' + style + '_run.png', await page.evaluate(([f, c]) => window.renderRun(f, c), [RUN_FRAMES, COLS]));
    if (!ONLY || ONLY.includes('idle')) save('mascot_' + style + '_idle.png', await page.evaluate(() => window.renderIdle()));
    if (!ONLY || ONLY.includes('move')) {
      for (const id of moveIds) {
        save('mascot_' + style + '_m_' + id + '.png', await page.evaluate(([i, f, c]) => window.renderMove(i, f, c), [id, MOVE_FRAMES, COLS]));
      }
    }
  }
  await browser.close();
})();
