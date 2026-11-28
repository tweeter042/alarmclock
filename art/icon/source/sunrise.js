// "Sunrise" alarm-clock adaptive icon: parametric builder for the background and foreground layers.
//
// Every shape is a plain path with a solid, linear or radial (userSpaceOnUse) fill and an optional
// fill-opacity. There are no filters, masks, clip-paths, patterns, text or gradient transforms, so each
// layer converts 1:1 to an Android VectorDrawable (tools/icon/svg_to_vector.py).
//
// Coordinates are adaptive-icon units: a 108 x 108 canvas, launchers show the central 72, and the
// foreground motif must stay inside the 66-unit safe circle (radius 33 around 54,54).
'use strict';
const L = require('./lib');
const { f, P, circle, ring, capsule, polar, segmentBelow, linear, radial } = L;

const SPEC = {
  cx: 54,
  cy: 55.8, // dial centre: a little low so bells + dial + feet sit optically centred
  Ro: 25.2, // bezel outer radius
  bezel: 3.5, // bezel width -> dial radius Ri = 21.7
  step: 0.75, // tan inner lip between bezel and dial
  // bells: sun-gold domes, base tucked 0.2 under the bezel so they sit on the case
  bellAngle: 38,
  bellR: 6.8,
  bellH: 1.05,
  bellGap: -0.2,
  stemW: 1.3,
  knobR: 1.1,
  rim: { ext: 0.3, y: 0.5, r: 0.62 }, // rolled bell rim in the bell's local frame
  // feet: tapered capsules behind the bezel, +-a degrees either side of 6 o'clock
  feet: { a: 38, from: -3, to: 2.8, w: [1.9, 1.55] },
  // scene (fractions of the dial radius Ri)
  hz: 0.18, // horizon below the dial centre
  sunR: 0.42,
  // hands: 7:00 -> minute hand up across the sky, hour hand down-left across the sea
  hour: 7,
  minute: 0,
  hourLen: 0.5,
  minLen: 0.78,
  hourW: [1.85, 1.4],
  minW: [1.45, 1.0],
  pivotR: 2.5,
  jewel: true, // pivot dot with a warm radial gradient and a specular point
  // indices: no 12 o'clock bar (the minute hand sits on 12), dots at 3 and 9
  bar12: false,
  dots: [90, 270],
  // fading star sparkles [dx, dy (fractions of Ri), radius, opacity]
  stars: [
    [-0.44, -0.4, 1.25, 0.9],
    [0.46, -0.5, 0.85, 0.75],
  ],
  C: {
    bgTop: '#212A6A', // lifted from #1D2560 so the tile edge separates from near-black wallpapers
    bgBot: '#43307F',
    bgGlow: '#B65C9E',
    bgGlowA: 0.5,
    bezel: ['#FFF9F1', '#F2DCC7'],
    stepC: ['#E6CBB4', '#D9B49C'],
    bell: ['#FFE6B0', '#F2A65C'],
    rim: ['#FFD9A0', '#E3894A'],
    foot: ['#F7E6D4', '#DDBB9F'],
    sky: [
      [0.0, '#20256A'],
      [0.32, '#38338A'],
      [0.58, '#8B4C9C'],
      [0.78, '#E2677F'],
      [0.91, '#FF8E62'],
      [1.0, '#FFB45F'],
    ],
    glow: ['#FFE2A0', '#FFC772', '#FF9466', '#F0697A'],
    glowA: [0.95, 0.75, 0.38, 0.12],
    sun: ['#FFF4D8', '#FFD27A', '#FFAF48'],
    sea: ['#5A2F86', '#2E2670', '#1B1A4E'],
    seaGlow: '#FF8E6E',
    seaGlowA: 0.34,
    glint: '#FFE6B4',
    glintA: 0.95,
    hand: '#FFF7EC',
    dot: '#FFF7EC',
    shadow: '#120E38',
    shadowA: 0.5,
    vignette: '#120E38',
    vignetteA: 0.28,
    handShadow: '#3B1648',
    handShadowA: 0.3,
    pivotDot: '#E8656D',
    jewel: ['#FFCB8E', '#FF8869', '#EC5A8C'],
    star: '#FFF3E2',
    // optional glow layers (used by the Neon variant): ring halo and hand halos, [colour, opacity]
    halo: null,
    handGlow: null,
  },
};

// Deep-merge plain objects (arrays and scalars are replaced).
function merge(a, b) {
  const o = Array.isArray(a) ? a.slice() : Object.assign({}, a);
  for (const k of Object.keys(b || {})) {
    const v = b[k];
    if (v && typeof v === 'object' && !Array.isArray(v) && a && a[k] && typeof a[k] === 'object' && !Array.isArray(a[k])) o[k] = merge(a[k], v);
    else o[k] = v;
  }
  return o;
}

function sparkle(x, y, r) {
  const k = r * 0.28;
  return `M${P(x, y - r)}Q${P(x + k, y - k)} ${P(x + r, y)}Q${P(x + k, y + k)} ${P(x, y + r)}Q${P(x - k, y + k)} ${P(x - r, y)}Q${P(x - k, y - k)} ${P(x, y - r)}Z`;
}

function geom(s = SPEC) {
  const Ri = s.Ro - s.bezel;
  const hy = s.cy + s.hz * Ri;
  const rs = s.sunR * Ri;
  const hourA = (s.hour % 12) * 30 + s.minute * 0.5;
  const minA = s.minute * 6;
  return { Ri, hy, rs, hourA, minA };
}

const dist = (p) => Math.hypot(p[0] - 54, p[1] - 54);

// One bell (stem, dome, knob, rolled rim) at angle a (degrees clockwise from 12).
// Returns path data plus gradient anchor points and the bell's reach from the canvas centre.
function bellParts(s, a) {
  const { cx, cy, Ro } = s;
  const r = s.bellR, hf = s.bellH;
  const base = Ro + s.bellGap;
  const rad = (a * Math.PI) / 180;
  const u = [Math.sin(rad), -Math.cos(rad)]; // outward
  const v = [Math.cos(rad), Math.sin(rad)]; // along the base chord
  const B0 = [cx + u[0] * base, cy + u[1] * base];
  const pt = (x, y) => [B0[0] + v[0] * x + u[0] * y, B0[1] + v[1] * x + u[1] * y];
  const S1 = [cx + u[0] * (Ro - 2.5), cy + u[1] * (Ro - 2.5)];
  const S2 = pt(0, 0.6);
  const P1 = pt(-r, 0), P2 = pt(r, 0);
  const K = pt(0, r * hf + 0.55);
  const rim = s.rim;
  const R1 = rim ? pt(-r - rim.ext, rim.y) : null, R2 = rim ? pt(r + rim.ext, rim.y) : null;
  let reach = dist(K) + s.knobR;
  for (let t = 0; t <= Math.PI + 1e-9; t += Math.PI / 360) reach = Math.max(reach, dist(pt(-r * Math.cos(t), r * hf * Math.sin(t))));
  if (rim) reach = Math.max(reach, dist(R1) + rim.r, dist(R2) + rim.r);
  return {
    stem: capsule(S1[0], S1[1], S2[0], S2[1], s.stemW, s.stemW),
    dome: `M${P(...P1)}A${f(r)} ${f(r * hf)} ${f(a)} 0 1 ${P(...P2)}Z`,
    knob: circle(K[0], K[1], s.knobR),
    rim: rim ? capsule(R1[0], R1[1], R2[0], R2[1], rim.r, rim.r) : null,
    rimGrad: rim ? [R1, R2] : null,
    domeGrad: [pt(-r, r * 0.6), pt(r, 0)],
    reach,
  };
}

// Feet: two tapered capsules at +-feet.a degrees either side of 6 o'clock.
function feetParts(s) {
  const { cx, cy, Ro } = s;
  const ft = s.feet;
  return [-1, 1].map((sg) => {
    const a = 180 - sg * ft.a;
    const A = polar(cx, cy, Ro + ft.from, a), B = polar(cx, cy, Ro + ft.to, a);
    return { d: capsule(A[0], A[1], B[0], B[1], ft.w[0], ft.w[1]), reach: Math.max(dist(A) + ft.w[0], dist(B) + ft.w[1]) };
  });
}

function background(s = SPEC) {
  const C = s.C;
  let defs = '';
  let body = '';
  defs += linear('bgL', 54, 0, 54, 108, [[0, C.bgTop], [1, C.bgBot]]);
  body += `<path d="M0 0H108V108H0Z" fill="url(#bgL)"/>`;
  if (C.bgGlowA > 0) {
    defs += radial('bgG', 54, 100, 58, [[0, C.bgGlow, C.bgGlowA], [0.55, C.bgGlow, C.bgGlowA * 0.36], [1, C.bgGlow, 0]]);
    body += `<path d="${circle(54, 100, 58)}" fill="url(#bgG)"/>`;
  }
  return { defs, body };
}

function foreground(s = SPEC) {
  const C = s.C;
  const { cx, cy, Ro } = s;
  const { Ri, hy, rs, hourA, minA } = geom(s);
  const ov = 0.35; // overhang of the dial fills, hidden under the inner lip ring
  let defs = '';
  let body = '';
  const fo = (a) => (a < 1 ? ` fill-opacity="${f(a)}"` : '');

  // 1. soft drop shadow under the clock
  const shR = Ro + 5.5, shY = cy + 2.6;
  const sa = C.shadowA;
  defs += radial('fShadow', cx, shY, shR, [[0, C.shadow, sa], [(Ro - 1.5) / shR, C.shadow, sa * 0.84], [(Ro + 1.5) / shR, C.shadow, sa * 0.36], [1, C.shadow, 0]]);
  body += `<path d="${circle(cx, shY, shR)}" fill="url(#fShadow)"/>`;

  // 2. optional neon halo around the case
  if (C.halo) {
    const hR = Ro + 6;
    defs += radial('fHalo', cx, cy, hR, [[0, C.halo[0], 0], [(Ro - 2) / hR, C.halo[0], 0], [Ro / hR, C.halo[0], C.halo[1]], [(Ro + 2.4) / hR, C.halo[0], C.halo[1] * 0.35], [1, C.halo[0], 0]]);
    body += `<path d="${circle(cx, cy, hR)}" fill="url(#fHalo)"/>`;
  }

  // 3. feet, behind the bezel
  const feet = s.feet ? feetParts(s) : [];
  if (feet.length) {
    defs += linear('fFoot', 0, cy + Ro - 6, 0, cy + Ro + 4, [[0, C.foot[0]], [1, C.foot[1]]]);
    for (const ft of feet) body += `<path d="${ft.d}" fill="url(#fFoot)"/>`;
  }

  // 4. bells (stem, dome, knob), then their rolled rims, all behind the bezel
  defs += linear('fBell', 0, cy - Ro - 12, 0, cy - Ro + 8, [[0, C.bell[0]], [1, C.bell[1]]]);
  const bells = [-1, 1].map((sg) => bellParts(s, sg * s.bellAngle));
  bells.forEach((b, i) => {
    const id = i === 0 ? 'fBellL' : 'fBellR';
    defs += linear(id, b.domeGrad[0][0], b.domeGrad[0][1], b.domeGrad[1][0], b.domeGrad[1][1], [[0, C.bell[0]], [1, C.bell[1]]]);
    body += `<path d="${b.stem}" fill="url(#fBell)"/><path d="${b.dome}" fill="url(#${id})"/><path d="${b.knob}" fill="url(#fBell)"/>`;
  });
  bells.forEach((b, i) => {
    if (!b.rim) return;
    const id = i === 0 ? 'fRimL' : 'fRimR';
    defs += linear(id, b.rimGrad[0][0], b.rimGrad[0][1], b.rimGrad[1][0], b.rimGrad[1][1], [[0, C.rim[0]], [1, C.rim[1]]]);
    body += `<path d="${b.rim}" fill="url(#${id})"/>`;
  });

  // 5. bezel
  defs += linear('fBezel', cx - Ro * 0.5, cy - Ro, cx + Ro * 0.5, cy + Ro, [[0, C.bezel[0]], [1, C.bezel[1]]]);
  body += `<path d="${circle(cx, cy, Ro)}" fill="url(#fBezel)"/>`;

  // 6. sky: top -> horizon, then a hard stop (two stops at the same offset) into the sea colours
  const t = (hy - (cy - Ri)) / (2 * Ri);
  const skyStops = C.sky.map(([o, c]) => [o * t, c]).concat([[t, C.sea[0]], [t + 0.35 * (1 - t), C.sea[1]], [1, C.sea[2]]]);
  defs += linear('fSky', cx, cy - Ri, cx, cy + Ri, skyStops);
  body += `<path d="${circle(cx, cy, Ri + ov)}" fill="url(#fSky)"/>`;

  // 7. dawn glow centred on the sun
  const gr = Ri * 1.15, ga = C.glowA;
  defs += radial('fGlow', cx, hy, gr, [[0, C.glow[0], ga[0]], [0.18, C.glow[1], ga[1]], [0.4, C.glow[2], ga[2]], [0.7, C.glow[3], ga[3]], [1, C.glow[3], 0]]);
  body += `<path d="${circle(cx, cy, Ri + ov)}" fill="url(#fGlow)"/>`;

  // 8. fading stars
  for (const [dx, dy, r, a] of s.stars) body += `<path d="${sparkle(cx + dx * Ri, cy + dy * Ri, r)}" fill="${C.star}"${fo(a)}/>`;

  // 9. half-risen sun (the sea covers its lower half)
  defs += radial('fSun', cx, hy - rs * 0.25, rs * 1.15, [[0, C.sun[0]], [0.55, C.sun[1]], [1, C.sun[2]]]);
  body += `<path d="${circle(cx, hy, rs)}" fill="url(#fSun)"/>`;

  // 10. sea + warm reflected light
  defs += linear('fSea', cx, hy, cx, cy + Ri, [[0, C.sea[0]], [0.35, C.sea[1]], [1, C.sea[2]]]);
  body += `<path d="${segmentBelow(cx, cy, Ri + ov, hy)}" fill="url(#fSea)"/>`;
  defs += radial('fSeaGlow', cx, hy, Ri * 0.62, [[0, C.seaGlow, C.seaGlowA], [0.45, C.seaGlow, C.seaGlowA * 0.44], [1, C.seaGlow, 0]]);
  body += `<path d="${segmentBelow(cx, cy, Ri + ov, hy)}" fill="url(#fSeaGlow)"/>`;

  // 11. glint on the horizon under the sun
  const gw = rs * 1.75, gt = 0.62;
  defs += linear('fGlint', cx - gw, hy, cx + gw, hy, [[0, C.glint, 0], [0.5, C.glint, C.glintA], [1, C.glint, 0]]);
  body += `<path d="${capsule(cx - gw, hy + gt * 0.5, cx + gw, hy + gt * 0.5, gt * 0.5, gt * 0.5)}" fill="url(#fGlint)"/>`;

  // 12. rim vignette (depth)
  defs += radial('fVig', cx, cy, Ri, [[0, C.vignette, 0], [0.86, C.vignette, 0], [1, C.vignette, C.vignetteA]]);
  body += `<path d="${circle(cx, cy, Ri)}" fill="url(#fVig)"/>`;

  // 13. inner lip ring (also covers the fill overhang -> no seams)
  defs += linear('fStep', cx, cy - Ri, cx, cy + Ri, [[0, C.stepC[0]], [1, C.stepC[1]]]);
  body += `<path d="${ring(cx, cy, Ri + s.step, Ri)}" fill="url(#fStep)"/>`;

  // 14. indices
  if (s.bar12) {
    const A = polar(cx, cy, Ri * 0.72, 0), B = polar(cx, cy, Ri * 0.87, 0);
    body += `<path d="${capsule(A[0], A[1], B[0], B[1], 1.0, 1.0)}" fill="${C.dot}" fill-opacity="0.95"/>`;
  }
  for (const a of s.dots) {
    const D = polar(cx, cy, Ri * 0.82, a);
    body += `<path d="${circle(D[0], D[1], 0.92)}" fill="${C.dot}" fill-opacity="0.85"/>`;
  }

  // 15. hands with a soft plum shadow (and an optional glow), pivot, pivot dot
  const hands = [
    [hourA, s.hourLen * Ri, s.hourW],
    [minA, s.minLen * Ri, s.minW],
  ];
  const [sdx, sdy, sg] = [0.45, 0.95, 0.2];
  if (C.handGlow) {
    // stacked translucent halos approximate a soft glow without filters
    for (const [grow, k] of [[2.3, 0.22], [1.7, 0.3], [1.15, 0.42], [0.6, 0.6]]) {
      for (const [a, len, w] of hands) {
        const T = polar(cx, cy, len, a);
        body += `<path d="${capsule(cx, cy, T[0], T[1], w[0] + grow, w[1] + grow)}" fill="${C.handGlow[0]}"${fo(C.handGlow[1] * k)}/>`;
      }
    }
  }
  for (const [a, len, w] of hands) {
    const T = polar(cx, cy, len, a);
    body += `<path d="${capsule(cx + sdx, cy + sdy, T[0] + sdx, T[1] + sdy, w[0] + sg, w[1] + sg)}" fill="${C.handShadow}"${fo(C.handShadowA)}/>`;
  }
  body += `<path d="${circle(cx + sdx, cy + sdy, s.pivotR + sg)}" fill="${C.handShadow}"${fo(C.handShadowA)}/>`;
  for (const [a, len, w] of hands) {
    const T = polar(cx, cy, len, a);
    body += `<path d="${capsule(cx, cy, T[0], T[1], w[0], w[1])}" fill="${C.hand}"/>`;
  }
  body += `<path d="${circle(cx, cy, s.pivotR)}" fill="${C.hand}"/>`;
  if (s.jewel) {
    defs += radial('fJewel', cx - 0.3, cy - 0.35, 1.25, [[0, C.jewel[0]], [0.5, C.jewel[1]], [1, C.jewel[2]]]);
    body += `<path d="${circle(cx, cy, 1.0)}" fill="url(#fJewel)"/>`;
    body += `<path d="${circle(cx - 0.34, cy - 0.36, 0.34)}" fill="#FFFFFF" fill-opacity="0.9"/>`;
  } else {
    body += `<path d="${circle(cx, cy, 1.0)}" fill="${C.pivotDot}"/>`;
  }

  // safe-zone report: everything opaque must stay inside radius 33
  const reach = {
    bells: Math.max(...bells.map((b) => b.reach)),
    feet: feet.length ? Math.max(...feet.map((x) => x.reach)) : 0,
    ring: Math.max(Math.hypot(0, cy + Ro - 54), Math.hypot(0, cy - Ro - 54)),
    shadow: Math.hypot(0, shY - 54) + shR,
  };
  reach.motif = Math.max(reach.bells, reach.feet, reach.ring);
  return { defs, body, reach };
}

module.exports = { SPEC, merge, geom, background, foreground, bellParts, feetParts, sparkle };
