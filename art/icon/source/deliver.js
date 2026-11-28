'use strict';
// Builds every Alarm Clock icon deliverable from the parametric sources in this folder:
//
//   art/icon/*.svg                 source of truth (default icon layers, full composite, notification)
//   art/icon/variants/<id>/*.svg   alternate launcher icons (midnight, daylight, neon)
//   art/icon/watch/foreground.svg  Galaxy Watch foreground (no sub-pixel details)
//   art/icon/*.png                 exports and previews
//   docs/icon.png                  README icon
//   core/designsystem/src/main/res launcher, alternate, splash and notification drawables + mipmaps
//   wear/src/main/res              watch launcher drawables + mipmaps
//
// Usage: node art/icon/source/deliver.js      (needs Node + Playwright/Chromium and python3)
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const L = require('./lib');
const S = require('./sunrise');
const { monochrome } = require('./mono');
const { VARIANTS, WATCH } = require('./variants');
const { notification } = require('./notification');

const ART = path.resolve(__dirname, '..');
const ROOT = path.resolve(ART, '..', '..');
const DS_RES = path.join(ROOT, 'core', 'designsystem', 'src', 'main', 'res');
const WEAR_RES = path.join(ROOT, 'wear', 'src', 'main', 'res');
const CONVERTER = path.join(ROOT, 'tools', 'icon', 'svg_to_vector.py');
const rel = (p) => path.relative(ROOT, p).split(path.sep).join('/');

// ------------------------------------------------------------------------------------------ layers
const SPEC = S.SPEC;
const build = (spec) => ({ spec, bg: S.background(spec), fg: S.foreground(spec) });
const DEFAULT = build(SPEC);
const ALTS = Object.fromEntries(Object.entries(VARIANTS).map(([id, over]) => [id, build(S.merge(SPEC, over))]));
const WATCH_ICON = build(S.merge(SPEC, WATCH));
const MONO = monochrome();
const NOTIF = notification();
const parts = (b) => ({ defs: b.bg.defs + b.fg.defs, bg: b.bg.body, fg: b.fg.body });

// ------------------------------------------------------------------------------------------ svg files
function pretty(xml) {
  const tokens = xml.replace(/>\s*</g, '><').split(/(?=<)/);
  let depth = 1;
  const lines = [];
  for (const t of tokens) {
    if (!t.trim()) continue;
    if (/^<\//.test(t)) depth--;
    lines.push('  '.repeat(depth) + t);
    if (/^<[^/!?]/.test(t) && !/\/>$/.test(t)) depth++;
  }
  return lines.join('\n');
}
function svgFile(title, defs, body, size = 108) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <!-- ${title} -->
${defs ? `  <defs>\n${pretty(defs).replace(/^/gm, '  ')}\n  </defs>\n` : ''}${pretty(body)}
</svg>
`;
}
function write(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
  return file;
}
function writeLayers(dir, name, b, withMono) {
  const out = {};
  out.bg = write(path.join(dir, 'background.svg'), svgFile(`Alarm Clock "Sunrise"${name} - adaptive icon BACKGROUND layer (full-bleed 108x108)`, b.bg.defs, b.bg.body));
  out.fg = write(path.join(dir, 'foreground.svg'), svgFile(`Alarm Clock "Sunrise"${name} - adaptive icon FOREGROUND layer (motif inside the 66-unit safe circle)`, b.fg.defs, b.fg.body));
  if (withMono) out.mono = write(path.join(dir, 'monochrome.svg'), svgFile('Alarm Clock "Sunrise" - adaptive icon MONOCHROME layer (themed icons; only alpha matters)', '', MONO));
  out.full = write(path.join(dir, 'full.svg'), svgFile(`Alarm Clock "Sunrise"${name} - background + foreground composited (unmasked 108x108)`, b.bg.defs + b.fg.defs, `<g>${b.bg.body}</g><g>${b.fg.body}</g>`));
  return out;
}

const SRC = writeLayers(ART, '', DEFAULT, true);
const ALT_SRC = Object.fromEntries(Object.entries(ALTS).map(([id, b]) => [id, writeLayers(path.join(ART, 'variants', id), ` (${id})`, b, false)]));
const WATCH_FG = write(path.join(ART, 'watch', 'foreground.svg'), svgFile('Alarm Clock "Sunrise" - Galaxy Watch FOREGROUND layer (no stars or 3/9 dots: sub-pixel at 40-56 px)', WATCH_ICON.fg.defs, WATCH_ICON.fg.body));
const NOTIF_SRC = write(path.join(ART, 'notification.svg'), svgFile('Alarm Clock - notification small icon (24 x 24, single colour)', '', NOTIF, 24));

const r = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, +v.toFixed(2)]));
console.log('foreground reach from centre (safe radius 33):', JSON.stringify(r(DEFAULT.fg.reach)));
if (DEFAULT.fg.reach.motif > 33) throw new Error('foreground motif leaves the 66-unit safe circle');

// ------------------------------------------------------------------------------------------ android resources
function vd(svg, out, extra = []) {
  const res = spawnSync('python3', ['-I', CONVERTER, 'svg2vd', svg, out, '--source', rel(svg), ...extra], { encoding: 'utf8' });
  if (res.status !== 0) throw new Error(`converter failed for ${svg}:\n${res.stderr}`);
  process.stdout.write('  ' + res.stdout);
}
function adaptive(file, bg, fg) {
  write(file, `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by art/icon/source/deliver.js. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/${bg}" />
    <foreground android:drawable="@drawable/${fg}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
`);
}
const hex8 = (h) => '#FF' + h.replace('#', '').toUpperCase();
const SPLASH_BG = SPEC.C.bgTop;

console.log('android resources:');
const D = path.join(DS_RES, 'drawable'), MM = path.join(DS_RES, 'mipmap-anydpi');
fs.mkdirSync(D, { recursive: true });
vd(SRC.bg, path.join(D, 'ic_launcher_background.xml'));
vd(SRC.fg, path.join(D, 'ic_launcher_foreground.xml'));
vd(SRC.mono, path.join(D, 'ic_launcher_monochrome.xml'));
adaptive(path.join(MM, 'ic_launcher.xml'), 'ic_launcher_background', 'ic_launcher_foreground');
adaptive(path.join(MM, 'ic_launcher_round.xml'), 'ic_launcher_background', 'ic_launcher_foreground');
for (const [id, src] of Object.entries(ALT_SRC)) {
  vd(src.bg, path.join(D, `ic_launcher_alt_${id}_background.xml`));
  vd(src.fg, path.join(D, `ic_launcher_alt_${id}_foreground.xml`));
  adaptive(path.join(MM, `ic_launcher_alt_${id}.xml`), `ic_launcher_alt_${id}_background`, `ic_launcher_alt_${id}_foreground`);
}
// 240 dp is the size the Android 12+ splash screen asks for (lint's VectorRaster limit is 200 dp)
vd(SRC.fg, path.join(D, 'ic_splash_foreground.xml'), ['--width', '240', '--height', '240', '--lint-ignore', 'VectorRaster']);
vd(NOTIF_SRC, path.join(D, 'ds_ic_notification.xml'));
write(path.join(DS_RES, 'values', 'ds_icon_colors.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by art/icon/source/deliver.js. -->
<resources>
    <!-- Android 12+ splash screen background: the launcher icon's base colour. -->
    <color name="ds_splash_background">${hex8(SPLASH_BG)}</color>
</resources>
`);
const WD = path.join(WEAR_RES, 'drawable'), WM = path.join(WEAR_RES, 'mipmap-anydpi');
vd(SRC.bg, path.join(WD, 'ic_launcher_background.xml'));
vd(WATCH_FG, path.join(WD, 'ic_launcher_foreground.xml'));
vd(SRC.mono, path.join(WD, 'ic_launcher_monochrome.xml'));
adaptive(path.join(WM, 'ic_launcher.xml'), 'ic_launcher_background', 'ic_launcher_foreground');
adaptive(path.join(WM, 'ic_launcher_round.xml'), 'ic_launcher_background', 'ic_launcher_foreground');

// ------------------------------------------------------------------------------------------ previews
const fontFace = (family, file, weight) => {
  const p = path.join(DS_RES, 'font', file);
  return `@font-face{font-family:'${family}';font-weight:${weight};src:url(data:font/ttf;base64,${fs.readFileSync(p).toString('base64')}) format('truetype');}`;
};
const FONTS = fontFace('Inter', 'inter_400.ttf', 400) + fontFace('Inter', 'inter_700.ttf', 700) +
  fontFace('PlexThai', 'ibm_plex_sans_thai_400.ttf', 400) + fontFace('PlexThai', 'ibm_plex_sans_thai_700.ttf', 700);
const page = (css, body) => `<!doctype html><html><head><meta charset="utf-8"><style>${FONTS}
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:Inter,PlexThai,sans-serif;-webkit-font-smoothing:antialiased}
  img{display:block}
  ${css}</style></head><body>${body}</body></html>`;

const icon = (b, mask, px) => L.iconImg(parts(b), mask, px);
const monoIcon = (px, tile, glyph, mask = 'squircle') =>
  L.iconImg({ defs: '', bg: `<path d="M0 0H108V108H0Z" fill="${tile}"/>`, fg: MONO.replace(/#000000/g, glyph) }, mask, px);

// simple placeholder apps for the home-screen mock
const G = {
  dot: `<path d="${L.circle(54, 54, 11)}" fill="#FFFFFF" fill-opacity="0.92"/>`,
  ring: `<path d="${L.ring(54, 54, 15, 10)}" fill="#FFFFFF" fill-opacity="0.92"/>`,
  rect: `<path d="${L.roundedRect(40, 40, 28, 28, 6)}" fill="#FFFFFF" fill-opacity="0.92"/>`,
  bars: `<path d="${L.roundedRect(38, 40, 32, 6, 3)}" fill="#FFFFFF" fill-opacity="0.92"/><path d="${L.roundedRect(38, 51, 32, 6, 3)}" fill="#FFFFFF" fill-opacity="0.75"/><path d="${L.roundedRect(38, 62, 20, 6, 3)}" fill="#FFFFFF" fill-opacity="0.6"/>`,
  tri: `<path d="M47 40L68 54L47 68Z" fill="#FFFFFF" fill-opacity="0.92"/>`,
  plus: `<path d="${L.roundedRect(50, 38, 8, 32, 4)}" fill="#FFFFFF" fill-opacity="0.92"/><path d="${L.roundedRect(38, 50, 32, 8, 4)}" fill="#FFFFFF" fill-opacity="0.92"/>`,
};
const placeholder = (px, c1, c2, glyph, mask = 'squircle') =>
  L.iconImg({ defs: L.linear('pl', 54, 18, 54, 90, [[0, c1], [1, c2]]), bg: `<path d="M0 0H108V108H0Z" fill="url(#pl)"/>${glyph}`, fg: '' }, mask, px);
const APPS = [
  ['#5FD08A', '#2FA563', G.ring, 'Phone', 'โทรศัพท์'],
  ['#6AA8FF', '#3B78E7', G.bars, 'Messages', 'ข้อความ'],
  ['SELF'],
  ['#FF8A8A', '#E0505E', G.dot, 'Camera', 'กล้อง'],
  ['#FFC86B', '#F29B38', G.tri, 'Gallery', 'แกลเลอรี'],
  ['#A88BFF', '#6E5BE0', G.rect, 'Calendar', 'ปฏิทิน'],
  ['#AEB4BE', '#7D8592', G.plus, 'Settings', 'การตั้งค่า'],
];
const WALL = {
  light: 'radial-gradient(circle at 15% 20%,#FFE9DC 0,#FFE9DC 18%,transparent 55%),radial-gradient(circle at 85% 80%,#D9E6FF 0,transparent 60%),linear-gradient(135deg,#F7F1EA,#E6ECF7)',
  dark: 'radial-gradient(circle at 80% 15%,#26345C 0,transparent 55%),radial-gradient(circle at 10% 90%,#2A1C3E 0,transparent 55%),linear-gradient(160deg,#0B1020,#121A2E)',
};

async function previews() {
  const out = (n) => path.join(ART, n);
  // exports: full-bleed visible square (launchers/stores apply their own mask) and the README icon
  await L.shoot(page('', icon(DEFAULT, 'square', 1024)), out('icon-1024.png'), 1024, 1024);
  await L.shoot(page('', icon(DEFAULT, 'square', 512)), out('icon-512.png'), 512, 512);
  await L.shoot(page('body{background:transparent}', icon(DEFAULT, 'squircle', 256)), path.join(ROOT, 'docs', 'icon.png'), 256, 256, { transparent: true });

  // 1. launcher masks
  {
    const masks = [['circle', 'Circle'], ['squircle', 'One UI squircle'], ['rounded', 'Rounded square']];
    const cells = masks.map(([m, label]) => `<div class="c">${icon(DEFAULT, m, 384)}<div class="l">${label}</div></div>`).join('');
    await L.shoot(page('body{background:#E9EAEE}.w{display:flex;gap:48px;padding:48px 48px 0}.c{display:flex;flex-direction:column;align-items:center}.l{margin-top:16px;font-size:18px;color:#3A3D46;font-weight:700}',
      `<div class="w">${cells}</div>`), out('preview_masks.png'), 3 * 384 + 4 * 48, 384 + 48 + 64);
  }
  // 2. home screen, light + dark wallpaper (English and Thai labels)
  {
    const row = (thai) => APPS.map((p) => {
      if (p[0] === 'SELF') return `<div class="app">${icon(DEFAULT, 'squircle', 64)}<div class="lab">${thai ? 'นาฬิกาปลุก' : 'Alarm Clock'}</div></div>`;
      return `<div class="app">${placeholder(64, p[0], p[1], p[2])}<div class="lab">${thai ? p[4] : p[3]}</div></div>`;
    }).join('');
    const panel = (kind, thai, cap) => `<div class="panel ${kind}" style="background:${WALL[kind]}"><div class="status"><span>7:00</span><span class="dots">● ● ▮</span></div><div class="grid">${row(thai)}</div><div class="cap">${cap}</div></div>`;
    await L.shoot(page(`
      .panel{width:760px;height:250px;position:relative;overflow:hidden}
      .status{display:flex;justify-content:space-between;padding:14px 26px 0;font-size:14px;font-weight:700}
      .dots{font-size:10px;letter-spacing:3px;opacity:.8}
      .grid{display:flex;justify-content:space-between;padding:56px 30px 0}
      .app{width:92px;display:flex;flex-direction:column;align-items:center}
      .app img{filter:drop-shadow(0 2px 3px rgba(0,0,0,.18))}
      .lab{margin-top:8px;font-size:13px;line-height:16px;text-align:center;white-space:nowrap}
      .light{color:#1E2026}.dark{color:#F4F5F8}
      .dark .lab{text-shadow:0 1px 2px rgba(0,0,0,.6)}
      .cap{position:absolute;left:26px;bottom:14px;font-size:11px;opacity:.55;letter-spacing:.4px;text-transform:uppercase}`,
    panel('light', false, 'Light wallpaper · 64 px') + panel('dark', true, 'Dark wallpaper · 64 px · Thai')), out('preview_home.png'), 760, 500);
  }
  // 3. small sizes at 1:1 and a 4x pixel loupe of the same render
  {
    const cell = (bgc) => `<div style="background:${bgc};display:flex;gap:12px;align-items:center;padding:10px 12px;width:160px">${icon(DEFAULT, 'squircle', 48)}${icon(DEFAULT, 'squircle', 32)}${icon(DEFAULT, 'circle', 32)}</div>`;
    const W = 320, H = 68;
    const tmp = out('.small_1x.png');
    await L.shoot(page('', `<div style="display:flex">${cell('#F2F3F6')}${cell('#0A0A0D')}</div>`), tmp, W, H);
    const png = fs.readFileSync(tmp).toString('base64');
    fs.unlinkSync(tmp);
    await L.shoot(page('body{background:#fff}.t{font-size:12px;color:#555;padding:10px 12px 6px;font-weight:700;letter-spacing:.3px}',
      `<div class="t">1:1 · 48 px squircle, 32 px squircle, 32 px circle on light and dark</div><img src="data:image/png;base64,${png}" width="${W}" height="${H}" style="margin-bottom:6px"/>
       <div class="t">4x loupe of the same pixels</div><img src="data:image/png;base64,${png}" width="${W * 4}" height="${H * 4}" style="image-rendering:pixelated"/>`),
    out('preview_small.png'), W * 4, 34 + H + 6 + 34 + H * 4);
  }
  // 4. monochrome / themed icons
  {
    const themes = [['#CDEDEA', '#0E4F4C', 'Themed · light', '#F6F7F9'], ['#1C3433', '#BDEFEA', 'Themed · dark', '#0D1111']];
    const big = themes.map(([b, g, l]) => `<div class="c">${monoIcon(320, b, g)}<div class="l">${l}</div></div>`).join('');
    const smallRow = ([b, g, , pg]) => `<div class="sr" style="background:${pg}">${[64, 48, 32].map((px) => monoIcon(px, b, g)).join('')}</div>`;
    await L.shoot(page('body{background:#E9EAEE}.w{display:flex;gap:40px;padding:40px 40px 10px}.c{display:flex;flex-direction:column;align-items:center}.l{margin-top:12px;font-size:16px;color:#3A3D46;font-weight:700}.rows{display:flex;gap:40px;padding:6px 40px 30px}.sr{width:320px;display:flex;align-items:center;justify-content:center;gap:26px;padding:16px 0;border-radius:18px}',
      `<div class="w">${big}</div><div class="rows">${themes.map(smallRow).join('')}</div>`), out('preview_mono.png'), 2 * 320 + 3 * 40, 40 + 320 + 40 + 6 + 96 + 30);
  }
  // 5. Galaxy Watch: circle mask on black, launcher mock + sizes
  {
    const others = [['#5FD08A', '#2FA563', G.ring], ['#6AA8FF', '#3B78E7', G.bars], ['#FF8A8A', '#E0505E', G.dot], ['#FFC86B', '#F29B38', G.tri], ['#A88BFF', '#6E5BE0', G.rect], ['#AEB4BE', '#7D8592', G.plus]];
    const R = 84, C = 160, S0 = 60;
    let watch = `<div style="position:absolute;left:0;top:0;width:320px;height:320px;border-radius:50%;background:#000;box-shadow:0 0 0 10px #1B1C20,0 0 0 12px #34363C"></div>`;
    others.forEach((p, i) => {
      const a = (i / others.length) * Math.PI * 2 - Math.PI / 2;
      watch += `<div style="position:absolute;left:${C + R * Math.cos(a) - S0 / 2}px;top:${C + R * Math.sin(a) - S0 / 2}px">${placeholder(S0, p[0], p[1], p[2], 'circle')}</div>`;
    });
    watch += `<div style="position:absolute;left:${C - 36}px;top:${C - 36}px">${icon(WATCH_ICON, 'circle', 72)}</div>`;
    const sizes = [72, 56, 44].map((px) => `<div class="s">${icon(WATCH_ICON, 'circle', px)}<div>${px} px</div></div>`).join('');
    await L.shoot(page('body{background:#0B0B0E;color:#C9CBD2}.w{position:relative;width:320px;height:320px;margin:28px 0 0 28px}.row{position:absolute;left:390px;top:40px;display:flex;flex-direction:column;gap:18px}.s{display:flex;align-items:center;gap:14px;font-size:13px}.big{position:absolute;left:540px;top:50px}.cap{position:absolute;left:390px;bottom:22px;width:350px;font-size:12px;line-height:17px;opacity:.65}',
      `<div class="w">${watch}</div><div class="row">${sizes}</div><div class="big">${icon(WATCH_ICON, 'circle', 196)}</div><div class="cap">Galaxy Watch launcher: circle mask on black. The watch foreground drops the stars and the 3/9 dots.</div>`),
    out('preview_watch.png'), 760, 376);
  }
  // 6. default + alternates (Settings > Appearance > App icon), light and dark wallpaper
  {
    const list = [['Default', DEFAULT], ['Midnight', ALTS.midnight], ['Daylight', ALTS.daylight], ['Neon', ALTS.neon]];
    const panel = (kind) => `<div class="panel" style="background:${WALL[kind]};color:${kind === 'light' ? '#1E2026' : '#F4F5F8'}">${list.map(([n, b]) =>
      `<div class="it">${icon(b, 'squircle', 132)}<div class="n">${n}</div></div>`).join('')}<div class="cap">${kind === 'light' ? 'Light wallpaper' : 'Dark wallpaper'}</div></div>`;
    await L.shoot(page(`.panel{width:760px;height:262px;position:relative;display:flex;justify-content:space-around;padding:38px 26px 0}
      .it{display:flex;flex-direction:column;align-items:center}.it img{filter:drop-shadow(0 3px 6px rgba(0,0,0,.22))}
      .n{margin-top:14px;font-size:16px;font-weight:700;letter-spacing:.2px}
      .cap{position:absolute;left:24px;bottom:12px;font-size:11px;opacity:.55;letter-spacing:.4px;text-transform:uppercase}`,
    panel('light') + panel('dark')), out('preview_variants.png'), 760, 524);
  }
  // 7. notification small icon in the status bar (1:1 and 3x) and on a notification card
  {
    const n = (px, col) => `<img src="${L.dataUri(`<svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="0 0 24 24">${NOTIF.replace(/#FFFFFF/g, col)}</svg>`)}" width="${px}" height="${px}"/>`;
    const bar = (bgc, col, k) => `<div class="bar" style="background:${bgc};color:${col};height:${36 * k}px;font-size:${14 * k}px;gap:${8 * k}px;padding:0 ${14 * k}px;border-radius:${8 * k}px"><b>7:00</b>${n(24 * k, col)}</div>`;
    const card = `<div class="card"><div class="hd">${n(18, '#4A4D55')}<span>Alarm Clock · now</span></div><div class="ti">Alarm · 7:00</div><div class="tx">Good morning. Swipe to dismiss.</div></div>`;
    await L.shoot(page('body{background:#E9EAEE}.bar{display:flex;align-items:center;width:max-content}.wrap{display:flex;flex-direction:column;gap:12px;padding:14px}.card{width:330px;background:#fff;border-radius:22px;padding:14px 18px;color:#1E2026;box-shadow:0 2px 8px rgba(0,0,0,.08)}.hd{display:flex;gap:8px;align-items:center;font-size:12px;color:#5A5D66}.ti{margin-top:8px;font-weight:700;font-size:15px}.tx{margin-top:2px;font-size:13px;color:#4A4D55}.row{display:flex;gap:14px;align-items:flex-start}.col{display:flex;flex-direction:column;gap:6px}',
      `<div class="wrap"><div class="row"><div class="col">${bar('#000000', '#FFFFFF', 1)}${bar('#F5F5F7', '#1B1B1F', 1)}</div>${card}</div><div class="row">${bar('#000000', '#FFFFFF', 3)}${bar('#F5F5F7', '#1B1B1F', 3)}</div></div>`),
    out('preview_notification.png'), 560, 14 + 96 + 12 + 108 + 14);
  }
  // 8. splash screen (Android 12+): ds_splash_background + ic_splash_foreground in the 160 dp circle
  {
    const k = 0.6; // px per dp
    const fgOnly = L.iconImg({ defs: DEFAULT.fg.defs, bg: '', fg: DEFAULT.fg.body }, 'circle', 160 * k);
    const W = 412 * k, H = 892 * k;
    const phone = `<div style="width:${W + 16}px;height:${H + 16}px;border-radius:30px;background:#111;padding:8px"><div style="position:relative;width:${W}px;height:${H}px;border-radius:23px;overflow:hidden;background:${SPLASH_BG}"><div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)">${fgOnly}</div></div></div>`;
    await L.shoot(page('body{background:#E9EAEE;display:flex;gap:28px;align-items:center;padding:20px}.cap{font-size:13px;color:#3A3D46;width:250px;line-height:19px}',
      `${phone}<div class="cap"><b>Splash screen (Android 12+)</b><br>Background: ds_splash_background ${SPLASH_BG}<br>Icon: ic_splash_foreground, 240 dp; the motif stays inside the central 160 dp circle.</div>`),
    out('preview_splash.png'), 40 + W + 16 + 28 + 250, 40 + H + 16);
  }
}

previews()
  .then(() => L.closeBrowser())
  .then(() => console.log('wrote', rel(ART), 'and the Android resources'))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
