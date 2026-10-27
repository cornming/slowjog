// 暖身與伸展動作。每個動作是一個 pose(t)，t 為 0 到 1 的循環相位，回傳 applyPose 吃的欄位。
// 和 App 的 mascot/MascotMoves.kt 要一起改（id、角度、循環秒數）。
const TAU = Math.PI * 2;
const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
/** 慢慢做到位、停住、再慢慢回來：0 → 1 → 0，中間停留。 */
const hold = (t, rise) => {
  const r = rise || 0.3;
  if (t < r) return smooth(t / r);
  if (t < 1 - r) return 1;
  return smooth((1 - t) / r);
};

function neutral() {
  return {
    thighL: 0, thighR: 0, shinL: 0.05, shinR: 0.05,
    upperL: 0, upperR: 0, foreL: -0.15, foreR: -0.15,
    bob: 0, twist: 0, headBob: 0, tuft: 0,
  };
}
const make = (o) => Object.assign(neutral(), o);

const MOVES = {
  // ---- 暖身 ----
  march: {
    angle: 60,
    pose(t) {
      const th = t * TAU, l = Math.max(0, Math.sin(th)), r = Math.max(0, -Math.sin(th));
      return make({
        thighL: -1.25 * l, shinL: 0.05 + 1.3 * l, thighR: -1.25 * r, shinR: 0.05 + 1.3 * r,
        upperL: 0.6 * Math.sin(th), upperR: -0.6 * Math.sin(th), foreL: -1.2, foreR: -1.2,
        bob: 0.02 * Math.cos(2 * th), tuft: 0.2 * Math.sin(2 * th),
      });
    },
  },
  armcircle: {
    angle: 45,
    pose(t) {
      const th = t * TAU;
      return make({
        shinL: 0.12, shinR: 0.12, thighL: -0.04, thighR: -0.04,
        upperL: -th, upperR: -th, foreL: -0.08, foreR: -0.08,
        bob: 0.015 * Math.cos(2 * th), tuft: 0.2 * Math.sin(th),
      });
    },
  },
  twist: {
    angle: 15,
    pose(t) {
      const th = t * TAU, tw = 0.95 * Math.sin(th);
      return make({
        upperLz: Math.PI / 2 - 0.45, upperRz: -(Math.PI / 2 - 0.45), foreL: -0.12, foreR: -0.12,
        shinL: 0.1, shinR: 0.1, twist: tw, headTwist: tw * 0.75, tuft: 0.15 * Math.sin(th),
      });
    },
  },
  squat: {
    angle: 90,
    pose(t) {
      const d = (1 - Math.cos(t * TAU)) / 2, a = 0.95 * d;
      return make({
        thighL: -a, thighR: -a, shinL: 0.05 + 2 * a, shinR: 0.05 + 2 * a,
        ankleL: -a, ankleR: -a, lean: 0.4 * d,
        upperL: -1.35 * d, upperR: -1.35 * d, foreL: -0.1, foreR: -0.1,
        tuft: 0.15 * Math.sin(t * TAU),
      });
    },
  },

  // ---- 伸展（慢慢到位、停住、再慢慢回來）----
  calf: {
    angle: 90,
    pose(t) {
      const d = hold(t), a = 0.8 * d, b = 0.56 * d;
      return make({
        thighL: -a, shinL: 0.05 + a, ankleL: 0,
        thighR: b, shinR: 0.05, ankleR: -b,
        lean: 0.28 * d, upperL: -1.45 * d, upperR: -1.45 * d, foreL: -0.05, foreR: -0.05,
        tuft: 0.1 * Math.sin(t * TAU),
      });
    },
  },
  quad: {
    angle: 90,
    pose(t) {
      const d = hold(t);
      return make({
        thighL: 0.35 * d, shinL: 0.05 + 2.75 * d,
        upperL: 1.35 * d, foreL: -0.15 - 1.9 * d,
        upperRz: -1.0 * d, foreR: -0.1,
        lean: -0.05 * d, tuft: 0.1 * Math.sin(t * TAU),
      });
    },
  },
  forward: {
    angle: 90,
    pose(t) {
      const d = hold(t), lean = 1.3 * d;
      return make({
        lean, headTilt: 0.25 * d, dz: -0.7 * d,
        upperL: -lean - 0.15 * d, upperR: -lean - 0.15 * d, foreL: -0.05, foreR: -0.05,
        shinL: 0.05, shinR: 0.05, tuft: 0.1 * Math.sin(t * TAU),
      });
    },
  },
  sidebend: {
    angle: 0,
    pose(t) {
      const side = 0.5 * Math.sin(t * TAU);
      return make({
        upperLz: Math.PI - 0.12, upperRz: -(Math.PI - 0.12), foreL: -0.05, foreR: -0.05,
        side, headTilt: 0, tuft: -0.2 * Math.sin(t * TAU),
      });
    },
  },
  crossarm: {
    angle: 30,
    pose(t) {
      const d = hold(t);
      return make({
        upperL: -1.5 * d, upperLy: -1.2 * d, foreL: -0.1,
        upperR: -1.2 * d, upperRy: 0.55 * d, foreR: -0.15 - 1.7 * d,
        headTwist: 0.35 * d, tuft: 0.1 * Math.sin(t * TAU),
      });
    },
  },
};

/** 所有腳底都貼地（腳踝世界座標最低點對齊 0.1，也就是鞋底在 y = 0）。 */
function groundFigure(rig) {
  rig.root.updateMatrixWorld(true);
  const v = new THREE.Vector3();
  let minY = Infinity;
  [rig.legs.L.ankle, rig.legs.R.ankle].forEach((a) => { a.getWorldPosition(v); minY = Math.min(minY, v.y); });
  rig.figure.position.y += 0.1 - minY;
}
