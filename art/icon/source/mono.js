// Monochrome (themed icon) layer for the Sunrise alarm clock. Only the alpha/shape matters: launchers
// tint it. Built for 32 px first: thick ring, big bells, feet, bold 7:00 hands, and a sunrise pictogram
// (sun arc with four short rays over a floating horizon). The sky segment above the horizon is filled at
// a low alpha, which gives the glyph a two-tone face; set skyA to 0 if a launcher flattens alpha.
'use strict';
const L = require('./lib');
const { f, P, circle, ring, capsule, polar } = L;
const S = require('./sunrise');

const MONO_OPTS = {
  k: 0.84, // uniform scale about the canvas centre (themed icons read better with some padding)
  bezel: 3.9, // ring width before scaling (3.28 units after k)
  bellScale: 1.0,
  stemW: 1.4,
  pivotR: 2.6,
  hourW: [1.95, 1.55],
  minW: [1.55, 1.15],
  arcW: 2.2, // sun arc width
  rays: [-70, -35, 35, 70], // no 0-degree ray: it would merge with the 12 o'clock minute hand
  rayLen: [2.0, 4.2],
  horizonT: 2.0,
  floatHorizon: 1.6, // horizon bars stop short of the ring
  gap: 1.7, // clearance around the pivot and hands
  feet: true,
  skyA: 0.28,
};

// Filled semicircular band (a stroked arc with round caps, expressed as a fill) over centre (cx, cy).
function arcBand(cx, cy, r, w) {
  const ro = r + w / 2, ri = r - w / 2, h = w / 2;
  return `M${P(cx - ro, cy)}A${f(ro)} ${f(ro)} 0 0 1 ${P(cx + ro, cy)}A${f(h)} ${f(h)} 0 0 1 ${P(cx + ri, cy)}A${f(ri)} ${f(ri)} 0 0 0 ${P(cx - ri, cy)}A${f(h)} ${f(h)} 0 0 1 ${P(cx - ro, cy)}Z`;
}

// Geometry of the glyph (shared with the notification icon).
function monoGeom(opts = MONO_OPTS, base = S.SPEC) {
  const o = Object.assign({}, MONO_OPTS, opts);
  const k = o.k;
  const s = Object.assign({}, base, {
    cy: 54 + k * (base.cy - 54),
    Ro: base.Ro * k,
    bezel: o.bezel * k,
    bellR: base.bellR * k * o.bellScale,
    bellGap: base.bellGap * k,
    stemW: o.stemW * k,
    knobR: base.knobR * k * 1.15,
    rim: null,
    feet: base.feet ? { a: base.feet.a, from: base.feet.from * k, to: base.feet.to * k, w: base.feet.w.map((w) => w * k) } : null,
  });
  const Ri = s.Ro - s.bezel;
  return { o, k, s, Ri, hy: s.cy + base.hz * Ri, rs: base.sunR * Ri };
}

function monochrome(opts = {}, base = S.SPEC, col = '#000000') {
  const { o, k, s, Ri, hy, rs } = monoGeom(opts, base);
  const { cx, cy, Ro } = s;
  const hourA = (base.hour % 12) * 30 + base.minute * 0.5;
  const minA = base.minute * 6;
  const out = [];

  // two-tone sky above the horizon (tucked 0.3 under the ring)
  if (o.skyA > 0) out.push(`<path d="${L.segmentAbove(cx, cy, Ri + 0.3, hy)}" fill="${col}" fill-opacity="${f(o.skyA)}"/>`);
  // bells
  for (const sg of [-1, 1]) {
    const b = S.bellParts(s, sg * base.bellAngle);
    out.push(`<path d="${b.stem}" fill="${col}"/>`, `<path d="${b.dome}" fill="${col}"/>`, `<path d="${b.knob}" fill="${col}"/>`);
  }
  // feet
  if (o.feet && s.feet) for (const ft of S.feetParts(s)) out.push(`<path d="${ft.d}" fill="${col}"/>`);
  // ring
  out.push(`<path d="${ring(cx, cy, Ro, Ri)}" fill="${col}"/>`);
  // sunrise pictogram: sun arc + rays
  const sw = o.arcW * k;
  out.push(`<path d="${arcBand(cx, hy, rs - sw / 2, sw)}" fill="${col}"/>`);
  for (const a of o.rays) {
    const A = polar(cx, hy, rs + o.rayLen[0] * k, a), B = polar(cx, hy, rs + o.rayLen[1] * k, a);
    out.push(`<path d="${capsule(A[0], A[1], B[0], B[1], sw / 2, sw / 2)}" fill="${col}"/>`);
  }
  // hands (tapered capsules from the pivot)
  const pivotR = o.pivotR * k, g = o.gap * k;
  const hands = [[hourA, base.hourLen * Ri, o.hourW], [minA, base.minLen * Ri, o.minW]].map(([a, len, w]) => ({ T: polar(cx, cy, len, a), len, w0: w[0] * k, w1: w[1] * k }));
  // floating horizon, split so each bar keeps the same clearance g from the pivot and from any hand
  const th = o.horizonT * k;
  const y1 = hy + sw * 0.5 + 0.2 * k, ym = y1 + th / 2;
  const xr = Math.sqrt(Ri * Ri - (ym - cy) ** 2) - o.floatHorizon * k - th / 2;
  const clear = (x) => {
    if (Math.hypot(x - cx, ym - cy) < pivotR + g + th / 2) return false;
    for (const h of hands) {
      const dx = h.T[0] - cx, dy = h.T[1] - cy;
      const t = Math.max(0, Math.min(1, ((x - cx) * dx + (ym - cy) * dy) / (dx * dx + dy * dy)));
      const d = Math.hypot(x - (cx + t * dx), ym - (cy + t * dy));
      if (d < h.w0 + (h.w1 - h.w0) * t + g + th / 2) return false;
    }
    return true;
  };
  for (const sg of [-1, 1]) {
    let d = 0;
    while (!clear(cx + sg * d)) d += 0.01;
    if (xr - d > th) out.push(`<path d="${capsule(cx + sg * xr, ym, cx + sg * d, ym, th / 2, th / 2)}" fill="${col}"/>`);
  }
  for (const h of hands) out.push(`<path d="${capsule(cx, cy, h.T[0], h.T[1], h.w0, h.w1)}" fill="${col}"/>`);
  out.push(`<path d="${circle(cx, cy, pivotR)}" fill="${col}"/>`);
  return out.join('');
}

module.exports = { MONO_OPTS, monochrome, monoGeom, arcBand };
