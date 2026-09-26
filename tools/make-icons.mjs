/* 生成应用图标（纯 Node，无依赖）。用法：node tools/make-icons.mjs */
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';

/* ---------- PNG 编码 ---------- */
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'latin1'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function filterScanlines(w, h, rgba) {
  const bpp = 4, stride = w * bpp;
  const out = Buffer.alloc(h * (stride + 1));
  const up = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const line = rgba.subarray(y * stride, (y + 1) * stride);
    const cands = [];
    const f0 = Buffer.from(line); cands.push([0, f0]);
    const f1 = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) f1[i] = (line[i] - (i >= bpp ? line[i - bpp] : 0)) & 0xff;
    cands.push([1, f1]);
    const f2 = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) f2[i] = (line[i] - up[i]) & 0xff;
    cands.push([2, f2]);
    let best = cands[0], bestSum = Infinity;
    for (const c of cands) {
      let s = 0;
      for (let i = 0; i < stride; i++) s += Math.abs((c[1][i] << 24) >> 24);
      if (s < bestSum) { bestSum = s; best = c; }
    }
    const off = y * (stride + 1);
    out[off] = best[0];
    best[1].copy(out, off + 1);
    line.copy(up);
  }
  return out;
}
function encodePNG(w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(filterScanlines(w, h, rgba), { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/* ---------- 画图标 ---------- */
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const C1 = hex('#5b5bd6');   // 左上
const C2 = hex('#a78bfa');   // 右下

// ¥ 字形，归一化坐标：x∈[-0.42,0.42] y∈[-0.60,0.62]
const GLYPH = [
  [-0.42, -0.60, 0.00, -0.14],  // 左斜
  [0.42, -0.60, 0.00, -0.14],   // 右斜
  [0.00, -0.16, 0.00, 0.62],    // 竖
  [-0.36, 0.02, 0.36, 0.02],    // 上横
  [-0.36, 0.22, 0.36, 0.22],    // 下横
];
const GLYPH_H = 1.24;
const STROKE = 0.085;

function distSeg(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1, dy = y2 - y1;
  const L = dx * dx + dy * dy;
  let t = L ? ((px - x1) * dx + (py - y1) * dy) / L : 0;
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  const ax = px - (x1 + t * dx), ay = py - (y1 + t * dy);
  return Math.sqrt(ax * ax + ay * ay);
}
function rrectSDF(x, y, w, h, r) {
  const qx = Math.abs(x - w / 2) - (w / 2 - r);
  const qy = Math.abs(y - h / 2) - (h / 2 - r);
  return Math.min(Math.max(qx, qy), 0) + Math.sqrt(Math.max(qx, 0) ** 2 + Math.max(qy, 0) ** 2) - r;
}

function render(size, { maskable = false, glyphRatio = 0.60, radiusRatio = 0.225, transparentBg = false } = {}) {
  const rgba = Buffer.alloc(size * size * 4);
  const scale = glyphRatio * size / GLYPH_H;
  const c = size / 2;
  const r = maskable ? 0 : size * radiusRatio;
  const SS = 3, inv = 1 / (SS * SS);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let inBg = 0, inGlyph = 0, sr = 0, sg = 0, sb = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = x + (sx + 0.5) / SS, py = y + (sy + 0.5) / SS;
          if (rrectSDF(px, py, size, size, r) <= 0) inBg++;
          const gx = (px - c) / scale, gy = (py - c) / scale;
          let d = Infinity;
          for (const s of GLYPH) { const dd = distSeg(gx, gy, s[0], s[1], s[2], s[3]); if (dd < d) d = dd; }
          if (d <= STROKE) inGlyph++;
          const t = Math.min(1, Math.max(0, (px / size + py / size) / 2));
          sr += C1[0] + (C2[0] - C1[0]) * t;
          sg += C1[1] + (C2[1] - C1[1]) * t;
          sb += C1[2] + (C2[2] - C1[2]) * t;
        }
      }
      const o = (y * size + x) * 4;
      const ga = Math.min(1, inGlyph * inv);
      if (transparentBg) {
        // 只留白色 ¥ 字形，背景透明（Android 自适应图标的前景层）
        rgba[o] = 255; rgba[o + 1] = 255; rgba[o + 2] = 255;
        rgba[o + 3] = Math.round(ga * 255);
      } else {
        let R = sr * inv, G = sg * inv, B = sb * inv;
        R += (255 - R) * ga; G += (255 - G) * ga; B += (255 - B) * ga;
        rgba[o] = Math.round(R); rgba[o + 1] = Math.round(G); rgba[o + 2] = Math.round(B);
        rgba[o + 3] = Math.round(Math.min(1, inBg * inv) * 255);
      }
    }
  }
  return encodePNG(size, size, rgba);
}

/* ---------- 输出 1：网页图标（根目录） ---------- */
const jobs = [
  ['icon-192.png', 192, { glyphRatio: 0.60 }],
  ['icon-512.png', 512, { glyphRatio: 0.60 }],
  ['icon-maskable-512.png', 512, { maskable: true, glyphRatio: 0.46 }],
  ['apple-touch-icon.png', 180, { radiusRatio: 0, glyphRatio: 0.62 }],  // iOS 自己会切圆角，这里不留边
  ['favicon-32.png', 32, { radiusRatio: 0, glyphRatio: 0.66 }],
];
for (const [name, size, opt] of jobs) {
  const buf = render(size, opt);
  writeFileSync(name, buf);
  console.log('  ' + name.padEnd(26), size + 'x' + size, (buf.length / 1024).toFixed(1) + ' KB');
}

/* ---------- 输出 2：Android 图标（各屏幕密度） ---------- */
//   ic_launcher.png            48dp 传统图标（Android 8 以下用）
//   ic_launcher_foreground.png 108dp 自适应图标前景层（字形要落在中间 72dp 安全区内）
const RES = 'android/app/src/main/res';
const DENSITIES = [['mdpi', 1], ['hdpi', 1.5], ['xhdpi', 2], ['xxhdpi', 3], ['xxxhdpi', 4]];
console.log('\n  Android:');
for (const [dpi, k] of DENSITIES) {
  const dir = `${RES}/mipmap-${dpi}`;
  mkdirSync(dir, { recursive: true });

  const legacy = render(Math.round(48 * k), { glyphRatio: 0.62 });
  writeFileSync(`${dir}/ic_launcher.png`, legacy);

  const fg = render(Math.round(108 * k), { transparentBg: true, glyphRatio: 0.50 });
  writeFileSync(`${dir}/ic_launcher_foreground.png`, fg);

  console.log('  ' + ('mipmap-' + dpi).padEnd(26),
    `${Math.round(48 * k)}px / ${Math.round(108 * k)}px`,
    (legacy.length / 1024).toFixed(1) + ' KB / ' + (fg.length / 1024).toFixed(1) + ' KB');
}

/* ---------- 输出 3：Windows 快捷方式图标 ---------- */
// ICO 里直接塞一段 PNG（Vista 以后都支持这种写法），省得自己拼 BMP
{
  const png = render(256, { glyphRatio: 0.60 });
  const icondir = Buffer.alloc(6);
  icondir.writeUInt16LE(0, 0);   // reserved
  icondir.writeUInt16LE(1, 2);   // type: 1 = icon
  icondir.writeUInt16LE(1, 4);   // 只放一张图
  const ent = Buffer.alloc(16);
  ent[0] = 0;                    // 宽 256（写 0 表示 256）
  ent[1] = 0;                    // 高 256
  ent[2] = 0;                    // 调色板数
  ent[3] = 0;                    // reserved
  ent.writeUInt16LE(1, 4);       // color planes
  ent.writeUInt16LE(32, 6);      // 每像素位数
  ent.writeUInt32LE(png.length, 8);
  ent.writeUInt32LE(22, 12);     // 数据偏移 = 6 + 16
  const ico = Buffer.concat([icondir, ent, png]);
  writeFileSync('预算本.ico', ico);
  console.log('\n  预算本.ico'.padEnd(28) + '256x256  ' + (ico.length / 1024).toFixed(1) + ' KB');
}
