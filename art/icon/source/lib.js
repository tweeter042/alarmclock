// Geometry, SVG and rendering helpers for the "Sunrise" icon generator.
'use strict';
const fs = require('fs');

// Coordinates are written with two decimals (0.01 unit = 0.04 px at 432 px), gradient offsets with four.
const f = (n, d = 2) => {
  const m = 10 ** d;
  const v = Math.round(n * m) / m;
  return (Object.is(v, -0) ? 0 : v).toString();
};
const P = (x, y) => `${f(x)} ${f(y)}`;

// Full circle as a path (two arcs), clockwise.
function circle(cx, cy, r) {
  return `M${P(cx - r, cy)}A${f(r)} ${f(r)} 0 1 0 ${P(cx + r, cy)}A${f(r)} ${f(r)} 0 1 0 ${P(cx - r, cy)}Z`;
}
// Annulus: outer and inner contours wound in opposite directions, so it works with the nonzero rule.
function ring(cx, cy, r1, r2) {
  const o = `M${P(cx - r1, cy)}A${f(r1)} ${f(r1)} 0 1 1 ${P(cx + r1, cy)}A${f(r1)} ${f(r1)} 0 1 1 ${P(cx - r1, cy)}Z`;
  const i = `M${P(cx - r2, cy)}A${f(r2)} ${f(r2)} 0 1 0 ${P(cx + r2, cy)}A${f(r2)} ${f(r2)} 0 1 0 ${P(cx - r2, cy)}Z`;
  return o + i;
}
// Capsule between two points with (possibly different) end radii and round caps.
function capsule(x1, y1, x2, y2, r1, r2 = r1) {
  const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy);
  const d = [dx / L, dy / L], n = [-d[1], d[0]];
  const s = (r1 - r2) / L, c = Math.sqrt(1 - s * s);
  const m = [n[0] * c - d[0] * s, n[1] * c - d[1] * s];
  const m2 = [-n[0] * c - d[0] * s, -n[1] * c - d[1] * s];
  const T1 = [x1 + r1 * m[0], y1 + r1 * m[1]], T2 = [x2 + r2 * m[0], y2 + r2 * m[1]];
  const T2b = [x2 + r2 * m2[0], y2 + r2 * m2[1]], T1b = [x1 + r1 * m2[0], y1 + r1 * m2[1]];
  return `M${P(...T1)}L${P(...T2)}A${f(r2)} ${f(r2)} 0 0 0 ${P(...T2b)}L${P(...T1b)}A${f(r1)} ${f(r1)} 0 1 0 ${P(...T1)}Z`;
}
// Polar helper: angle in degrees clockwise from 12 o'clock.
function polar(cx, cy, r, deg) {
  const a = (deg * Math.PI) / 180;
  return [cx + r * Math.sin(a), cy - r * Math.cos(a)];
}
// Circular segment below the horizontal chord y = hy inside circle (cx, cy, r).
function segmentBelow(cx, cy, r, hy) {
  const h = hy - cy;
  const half = Math.sqrt(r * r - h * h);
  return `M${P(cx - half, hy)}L${P(cx + half, hy)}A${f(r)} ${f(r)} 0 0 1 ${P(cx - half, hy)}Z`;
}
// Circular segment above the horizontal chord y = hy inside circle (cx, cy, r).
function segmentAbove(cx, cy, r, hy) {
  const h = hy - cy;
  const half = Math.sqrt(r * r - h * h);
  const large = hy > cy ? 1 : 0;
  return `M${P(cx - half, hy)}A${f(r)} ${f(r)} 0 ${large} 1 ${P(cx + half, hy)}Z`;
}
// Superellipse (One UI squircle) path.
function superellipse(cx, cy, rx, ry, n = 4.5, steps = 720) {
  let d = '';
  for (let i = 0; i < steps; i++) {
    const t = (i / steps) * Math.PI * 2;
    const c = Math.cos(t), s = Math.sin(t);
    const x = cx + rx * Math.sign(c) * Math.pow(Math.abs(c), 2 / n);
    const y = cy + ry * Math.sign(s) * Math.pow(Math.abs(s), 2 / n);
    d += (i === 0 ? 'M' : 'L') + `${f(x, 3)} ${f(y, 3)}`;
  }
  return d + 'Z';
}
function roundedRect(x, y, w, h, r) {
  return `M${P(x + r, y)}H${f(x + w - r)}A${f(r)} ${f(r)} 0 0 1 ${P(x + w, y + r)}V${f(y + h - r)}A${f(r)} ${f(r)} 0 0 1 ${P(x + w - r, y + h)}H${f(x + r)}A${f(r)} ${f(r)} 0 0 1 ${P(x, y + h - r)}V${f(y + r)}A${f(r)} ${f(r)} 0 0 1 ${P(x + r, y)}Z`;
}

// Gradient definitions (userSpaceOnUse only, so they map 1:1 to VectorDrawable gradients).
function linear(id, x1, y1, x2, y2, stops) {
  return `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${f(x1)}" y1="${f(y1)}" x2="${f(x2)}" y2="${f(y2)}">${stopsXml(stops)}</linearGradient>`;
}
function radial(id, cx, cy, r, stops) {
  return `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${f(cx)}" cy="${f(cy)}" r="${f(r)}">${stopsXml(stops)}</radialGradient>`;
}
function stopsXml(stops) {
  return stops
    .map(([o, c, a = 1]) => `<stop offset="${f(Math.min(1, Math.max(0, o)), 4)}" stop-color="${c}"${a !== 1 ? ` stop-opacity="${f(a, 3)}"` : ''}/>`)
    .join('');
}

function svgDoc(inner, defs = '', vb = '0 0 108 108', w = 108, h = 108) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="${vb}">${defs ? `<defs>${defs}</defs>` : ''}${inner}</svg>`;
}

const dataUri = (svg) => 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');

// Launcher masks over the visible 72-unit square (18..90) of the 108 canvas.
const MASKS = {
  circle: circle(54, 54, 36),
  squircle: superellipse(54, 54, 36, 36, 4.5),
  rounded: roundedRect(18, 18, 72, 72, 72 * 0.22),
  square: 'M18 18H90V90H18Z',
};

// A masked icon (preview only; clip-paths never reach the Android layers).
// layers = {defs, bg, fg}; the result shows the visible 72 units at px x px.
let maskSeq = 0;
function maskedIcon(layers, mask = 'squircle', px = 384) {
  const id = `mk${maskSeq++}`;
  const clip = `<clipPath id="${id}"><path d="${MASKS[mask]}"/></clipPath>`;
  return svgDoc(`<g clip-path="url(#${id})">${layers.bg}${layers.fg}</g>`, clip + layers.defs, '18 18 72 72', px, px);
}

// The same icon as an <img> (its own document, so gradient ids never collide with other icons on a page).
function iconImg(layers, mask = 'squircle', px = 384, style = '') {
  return `<img src="${dataUri(maskedIcon(layers, mask, px))}" width="${px}" height="${px}" style="display:block;${style}"/>`;
}

// Shared headless browser for screenshots.
let browserP = null;
function browser() {
  if (!browserP) browserP = require('playwright').chromium.launch();
  return browserP;
}
async function closeBrowser() {
  if (browserP) (await browserP).close();
  browserP = null;
}
async function shoot(html, outPng, w, h, opts = {}) {
  w = Math.round(w);
  h = Math.round(h);
  const b = await browser();
  const page = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts && document.fonts.ready);
  await page.waitForTimeout(100);
  await page.screenshot({ path: outPng, clip: { x: 0, y: 0, width: w, height: h }, omitBackground: !!opts.transparent });
  await page.close();
}
// Runs fn(page) in a fresh page (used for pixel measurements in the browser).
async function withPage(w, h, fn) {
  const b = await browser();
  const page = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  try {
    return await fn(page);
  } finally {
    await page.close();
  }
}

module.exports = {
  f, P, circle, ring, capsule, polar, segmentBelow, segmentAbove, superellipse, roundedRect,
  linear, radial, svgDoc, dataUri, MASKS, maskedIcon, iconImg, shoot, withPage, closeBrowser, fs,
};
