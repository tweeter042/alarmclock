// Notification small icon (status bar): a 24-unit, single-colour silhouette derived from the icon.
// At 24 px the sunrise pictogram and rays would be sub-pixel, so it keeps what reads at that size:
// ring, gold-bell domes, feet and the same 7:00 hands.
'use strict';
const L = require('./lib');
const { circle, ring, capsule, polar } = L;
const S = require('./sunrise');

const NOTIF = {
  cx: 12,
  cy: 12.9,
  Ro: 7.6,
  ringW: 1.9,
  bellR: 3.3,
  bellGap: -0.1,
  stemW: 0.7,
  knobR: 0.75,
  feet: { a: 38, from: -1.2, to: 1.7, w: [1.05, 0.9] },
  handW: 0.9,
  hourLen: 0.56,
  minLen: 0.8,
  pivotR: 1.2,
};

function notification(base = S.SPEC, n = NOTIF, col = '#FFFFFF') {
  const { cx, cy, Ro } = n;
  const Ri = Ro - n.ringW;
  const sp = { cx, cy, Ro, bellGap: n.bellGap, bellR: n.bellR, bellH: base.bellH, stemW: n.stemW, knobR: n.knobR, rim: null, feet: n.feet };
  const d = [];
  for (const sg of [-1, 1]) {
    const b = S.bellParts(sp, sg * base.bellAngle);
    d.push(b.stem, b.dome, b.knob);
  }
  for (const ft of S.feetParts(sp)) d.push(ft.d);
  d.push(ring(cx, cy, Ro, Ri));
  const hourA = (base.hour % 12) * 30 + base.minute * 0.5, minA = base.minute * 6;
  const M = polar(cx, cy, Ri * n.minLen, minA), H = polar(cx, cy, Ri * n.hourLen, hourA);
  d.push(capsule(cx, cy, M[0], M[1], n.handW, n.handW * 0.85), capsule(cx, cy, H[0], H[1], n.handW, n.handW * 0.85), circle(cx, cy, n.pivotR));
  return d.map((p) => `<path d="${p}" fill="${col}"/>`).join('');
}

module.exports = { NOTIF, notification };
