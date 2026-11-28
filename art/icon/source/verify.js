'use strict';
// Checks the generated Android resources against their SVG sources.
//
//  1. Fidelity: every VectorDrawable under core/designsystem and wear is rendered back to SVG with
//     tools/icon/svg_to_vector.py vd2svg and compared pixel by pixel with its source SVG.
//  2. Conversion details: the sky's hard horizon stop survives (two items at the same offset) and every
//     fill-opacity became a fillAlpha.
//  3. Geometry: opaque reach / bbox / centroid of the foreground motif at 10 px per unit (safe radius 33).
//  4. Contrast (WCAG non-text, >= 3:1): minute hand vs the sky beside it, hour hand vs the sea.
//  5. The real Android renderer: when the PNGs written by LauncherIconRenderTest (Robolectric, native
//     graphics) exist, they are compared with Chrome renders of the source SVGs. Refresh them first with
//     ./gradlew :core:designsystem:testDebugUnitTest --tests '*LauncherIconRenderTest*'
//
// Usage: node art/icon/source/verify.js [outDir]   (writes side-by-side diff sheets to outDir if given)
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawnSync } = require('child_process');
const L = require('./lib');
const S = require('./sunrise');
const { VARIANTS } = require('./variants');

const ART = path.resolve(__dirname, '..');
const ROOT = path.resolve(ART, '..', '..');
const CONVERTER = path.join(ROOT, 'tools', 'icon', 'svg_to_vector.py');
const OUT = process.argv[2] ? path.resolve(process.argv[2]) : null;
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'icon-verify-'));
const RES_DIRS = ['core/designsystem/src/main/res/drawable', 'wear/src/main/res/drawable'];

function vd2svg(xml) {
  const out = path.join(TMP, path.basename(path.dirname(path.dirname(path.dirname(xml)))) + '_' + path.basename(xml, '.xml') + '.svg');
  const res = spawnSync('python3', ['-I', CONVERTER, 'vd2svg', xml, out], { encoding: 'utf8' });
  if (res.status !== 0) throw new Error(res.stderr);
  return fs.readFileSync(out, 'utf8');
}

// Draws two images (data URIs) at px x px on a transparent canvas and compares premultiplied RGBA.
async function compare(uriA, uriB, px, tol = 4) {
  return L.withPage(px * 2, px, async (page) => {
    return page.evaluate(async ({ a, b, px, tol }) => {
      const load = (uri) => new Promise((res, rej) => {
        const img = new Image();
        img.onload = () => res(img);
        img.onerror = rej;
        img.src = uri;
      });
      const draw = (img) => {
        const c = document.createElement('canvas');
        c.width = c.height = px;
        const g = c.getContext('2d');
        g.drawImage(img, 0, 0, px, px);
        return g.getImageData(0, 0, px, px).data;
      };
      const A = draw(await load(a)), B = draw(await load(b));
      // compare premultiplied values: the colour of an almost transparent pixel carries no weight
      let max = 0, sum = 0, over = 0;
      for (let i = 0; i < A.length; i += 4) {
        let m = Math.abs(A[i + 3] - B[i + 3]);
        for (let k = 0; k < 3; k++) m = Math.max(m, Math.abs((A[i + k] * A[i + 3]) / 255 - (B[i + k] * B[i + 3]) / 255));
        max = Math.max(max, m);
        sum += m;
        if (m > tol) over++;
      }
      return { max, mean: sum / (A.length / 4), over: over / (A.length / 4) };
    }, { a: uriA, b: uriB, px, tol });
  });
}

// Pixel statistics of an SVG rendered at 10 px/unit: opaque bbox, centroid, max radius from (54,54).
async function geometry(svg, alphaMin = 128) {
  const px = 1080;
  return L.withPage(px, px, (page) => page.evaluate(async ({ svg, px, alphaMin }) => {
    const img = new Image();
    await new Promise((res) => { img.onload = res; img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg))); });
    const c = document.createElement('canvas');
    c.width = c.height = px;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0, px, px);
    const d = g.getImageData(0, 0, px, px).data;
    let x0 = px, y0 = px, x1 = 0, y1 = 0, sx = 0, sy = 0, n = 0, rmax = 0;
    for (let y = 0; y < px; y++) for (let x = 0; x < px; x++) {
      const a = d[(y * px + x) * 4 + 3];
      if (a < alphaMin) continue;
      x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
      sx += x * a; sy += y * a; n += a;
      rmax = Math.max(rmax, Math.hypot(x + 0.5 - 540, y + 0.5 - 540));
    }
    return { bbox: [x0 / 10, (x1 + 1) / 10, y0 / 10, (y1 + 1) / 10], centroid: [sx / n / 10 + 0.05, sy / n / 10 + 0.05], rmax: rmax / 10 };
  }, { svg, px, alphaMin }));
}

// Samples colours (in viewport units) from an SVG rendered at 10 px/unit.
async function sample(svg, points) {
  const px = 1080;
  return L.withPage(px, px, (page) => page.evaluate(async ({ svg, px, points }) => {
    const img = new Image();
    await new Promise((res) => { img.onload = res; img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg))); });
    const c = document.createElement('canvas');
    c.width = c.height = px;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0, px, px);
    return points.map(([x, y]) => Array.from(g.getImageData(Math.round(x * 10), Math.round(y * 10), 1, 1).data));
  }, { svg, px, points }));
}
const lum = ([r, g, b]) => {
  const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
};
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

async function main() {
  let failed = false;
  const rows = [];
  const sheets = [];
  // 1. fidelity of every generated drawable
  for (const dir of RES_DIRS) {
    for (const f of fs.readdirSync(path.join(ROOT, dir)).filter((n) => n.endsWith('.xml')).sort()) {
      const xml = path.join(ROOT, dir, f);
      const text = fs.readFileSync(xml, 'utf8');
      const m = text.match(/Generated by tools\/icon\/svg_to_vector\.py from (\S+)\./);
      if (!m) continue;
      const src = fs.readFileSync(path.join(ROOT, m[1]), 'utf8');
      const back = vd2svg(xml);
      const px = /viewBox="0 0 24 24"/.test(src) ? 96 : 432;
      const d = await compare(L.dataUri(src), L.dataUri(back), px);
      const ok = d.max <= 3 && d.over === 0; // at most 8-bit rounding of stop and fill alphas
      if (!ok) failed = true;
      rows.push(`${ok ? 'ok  ' : 'FAIL'} ${path.join(dir, f).padEnd(72)} max ${d.max.toFixed(1).padStart(5)}  mean ${d.mean.toFixed(4)}  >4: ${(d.over * 100).toFixed(3)}%`);
      if (OUT) sheets.push({ name: path.join(dir, f), src, back });
    }
  }
  console.log('1. VectorDrawable -> SVG round trip vs source (432 px; notification 96 px)');
  console.log(rows.map((r) => '   ' + r).join('\n'));

  // 2. conversion details
  const fgXml = fs.readFileSync(path.join(ROOT, 'core/designsystem/src/main/res/drawable/ic_launcher_foreground.xml'), 'utf8');
  const fgSvg = fs.readFileSync(path.join(ART, 'foreground.svg'), 'utf8');
  const { Ri } = S.geom(S.SPEC);
  const skyStart = `android:startY="${+(S.SPEC.cy - Ri).toFixed(2)}"`;
  const skyBlock = fgXml.split('<gradient').find((g) => g.includes(skyStart) && (g.match(/<item/g) || []).length > 6);
  const offsets = skyBlock ? [...skyBlock.matchAll(/android:offset="([^"]+)"/g)].map((x) => x[1]) : [];
  const hard = offsets.some((o, i) => i > 0 && o === offsets[i - 1]);
  const alphaSrc = (fgSvg.match(/fill-opacity=/g) || []).length, alphaXml = (fgXml.match(/android:fillAlpha=/g) || []).length;
  console.log(`2. sky gradient offsets [${offsets.join(', ')}] -> hard horizon stop ${hard ? 'kept' : 'MISSING'}`);
  console.log(`   fill-opacity in foreground.svg: ${alphaSrc}, fillAlpha in ic_launcher_foreground.xml: ${alphaXml} ${alphaSrc === alphaXml ? '(all converted)' : 'MISMATCH'}`);
  if (!hard || alphaSrc !== alphaXml) failed = true;

  // 3. geometry of the rendered foreground (alpha >= 50 %): must stay inside the 33-unit safe circle
  const fgBack = vd2svg(path.join(ROOT, 'core/designsystem/src/main/res/drawable/ic_launcher_foreground.xml'));
  // the drop shadow is soft and semi-transparent; measure the motif without it
  const motifOnly = fgBack.replace(/<path d="[^"]*" fill="url\(#g1\)"\/>/, '');
  const gm = await geometry(motifOnly);
  console.log(`3. motif (alpha >= 50%): max radius ${gm.rmax.toFixed(2)} (safe 33), bbox x ${gm.bbox[0]}-${gm.bbox[1]} y ${gm.bbox[2]}-${gm.bbox[3]}, centroid (${gm.centroid.map((v) => v.toFixed(2)).join(', ')})`);
  if (gm.rmax > 33) failed = true;
  const monoBack = vd2svg(path.join(ROOT, 'core/designsystem/src/main/res/drawable/ic_launcher_monochrome.xml'));
  const mg = await geometry(monoBack, 200);
  console.log(`   monochrome glyph: max radius ${mg.rmax.toFixed(2)}, bbox x ${mg.bbox[0]}-${mg.bbox[1]} y ${mg.bbox[2]}-${mg.bbox[3]}, centroid (${mg.centroid.map((v) => v.toFixed(2)).join(', ')})`);

  // 4. hand contrast for the default icon and each variant
  console.log('4. hand contrast (WCAG, >= 3:1 required, >= 4.5:1 ideal)');
  const specs = Object.assign({ default: S.SPEC }, Object.fromEntries(Object.entries(VARIANTS).map(([k, v]) => [k, S.merge(S.SPEC, v)])));
  for (const [name, s] of Object.entries(specs)) {
    const g = S.geom(s);
    const bg = S.background(s), fg = S.foreground(s);
    const svg = L.svgDoc(bg.body + fg.body, bg.defs + fg.defs);
    const minLen = s.minLen * g.Ri, hourLen = s.hourLen * g.Ri;
    // minute hand: on the axis 75 % up its length (sky above the sun disc) vs 2.2 units to its left
    const mY = s.cy - minLen * 0.75;
    const mHand = [s.cx, mY], mSky = [s.cx - s.minW[1] - 1.6, mY];
    // hour hand: 70 % along its axis (over the sea) vs 2.4 units to the side of the axis
    const ha = (g.hourA * Math.PI) / 180, dir = [Math.sin(ha), -Math.cos(ha)], nrm = [Math.cos(ha), Math.sin(ha)];
    const hp = [s.cx + dir[0] * hourLen * 0.7, s.cy + dir[1] * hourLen * 0.7];
    const hSea = [hp[0] - nrm[0] * 2.6, hp[1] - nrm[1] * 2.6];
    const [c1, c2, c3, c4] = await sample(svg, [mHand, mSky, hp, hSea]);
    const k1 = contrast(c1, c2), k2 = contrast(c3, c4);
    if (k1 < 3 || k2 < 3) failed = true;
    console.log(`   ${name.padEnd(9)} minute hand vs sky ${k1.toFixed(2)}:1   hour hand vs sea ${k2.toFixed(2)}:1`);
  }

  // 5. the real Android VectorDrawable renderer (PNGs from LauncherIconRenderTest) vs the sources
  const RB = path.join(ROOT, 'core/designsystem/build/screenshots-out/launcher-icons');
  if (fs.existsSync(RB)) {
    console.log('5. Android renderer (LauncherIconRenderTest PNGs) vs Chrome render of the source SVG');
    const ids = Object.keys(VARIANTS);
    const pairs = [
      ['ic_launcher_background', 'background.svg', 432],
      ['ic_launcher_foreground', 'foreground.svg', 432],
      ['ic_launcher_monochrome', 'monochrome.svg', 432],
      ['ic_splash_foreground', 'foreground.svg', 432],
      ['ds_ic_notification', 'notification.svg', 96],
      ...ids.flatMap((id) => [[`ic_launcher_alt_${id}_background`, `variants/${id}/background.svg`, 432], [`ic_launcher_alt_${id}_foreground`, `variants/${id}/foreground.svg`, 432]]),
    ];
    for (const [png, svg, px] of pairs) {
      const file = path.join(RB, png + '.png');
      if (!fs.existsSync(file)) continue;
      const d = await compare('data:image/png;base64,' + fs.readFileSync(file).toString('base64'), L.dataUri(fs.readFileSync(path.join(ART, svg), 'utf8')), px, 8);
      // gradients are interpolated and dithered a little differently by Android; no pixel may drift far
      const ok = d.max <= 16 && d.over < 0.0005;
      if (!ok) failed = true;
      console.log(`   ${ok ? 'ok  ' : 'FAIL'} ${png.padEnd(38)} max ${d.max.toFixed(1).padStart(5)}  mean ${d.mean.toFixed(3)}  >8: ${(d.over * 100).toFixed(3)}%${ok ? '' : '  (stale PNG? re-run LauncherIconRenderTest)'}`);
    }
  } else {
    console.log('5. skipped: run LauncherIconRenderTest to compare with the Android renderer');
  }

  if (OUT) {
    fs.mkdirSync(OUT, { recursive: true });
    const uri = (s) => L.dataUri(s);
    const cells = sheets.map((x) => `<div style="display:flex;gap:6px;align-items:center;font:11px sans-serif;margin:4px">
      <img src="${uri(x.src)}" width="160" height="160" style="background:#888"/><img src="${uri(x.back)}" width="160" height="160" style="background:#888"/><span>${x.name}</span></div>`).join('');
    await L.shoot(`<body style="margin:0;background:#fff">${cells}</body>`, path.join(OUT, 'roundtrip.png'), 720, sheets.length * 168 + 8);
  }
  await L.closeBrowser();
  fs.rmSync(TMP, { recursive: true, force: true });
  if (failed) {
    console.error('verification FAILED');
    process.exit(1);
  }
  console.log('verification passed');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
