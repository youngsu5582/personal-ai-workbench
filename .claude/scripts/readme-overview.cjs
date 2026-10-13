// README 첫머리 그림 docs/images/overview-{light,dark}.svg 를 만든다.
//
//   npm i roughjs@4.6.6 opentype.js@1.3.4           # 저장소 밖 아무 폴더에서
//   gh api repos/google/fonts/contents/ofl/gaegu/Gaegu-Bold.ttf -H "Accept: application/vnd.github.raw" > <글꼴 폴더>/Gaegu-Bold.ttf
//   (Gaegu-Regular.ttf 도 같은 방식. 글꼴은 SIL OFL 1.1)
//   NODE_PATH=<그 폴더>/node_modules node .claude/scripts/readme-overview.cjs <글꼴 폴더> docs/images
//
// 손그림 선은 rough.js, 글자는 Gaegu 를 경로로 바꿔 넣는다 — GitHub 에서 글꼴 없이도 같은 모양으로 보이게.
const fs = require('fs')
const path = require('path')
const roughMod = require('roughjs/bundled/rough.cjs.js')
const rough = roughMod.default || roughMod
const opentype = require('opentype.js')

const [fontDir, outDir] = process.argv.slice(2)
const fonts = {
  regular: opentype.loadSync(path.join(fontDir, 'Gaegu-Regular.ttf')),
  bold: opentype.loadSync(path.join(fontDir, 'Gaegu-Bold.ttf')),
}

const W = 1280, H = 510

const PALETTES = {
  light: {
    ink: '#1e1e1e', muted: '#868e96', bad: '#e03131', accent: '#1971c2',
    service: '#b2f2bb', provider: '#ffec99', hub: '#a5d8ff', fillStyle: 'hachure',
  },
  dark: {
    ink: '#e9ecef', muted: '#adb5bd', bad: '#ff8787', accent: '#74c0fc',
    service: '#234a2f', provider: '#4a3412', hub: '#173a5e', fillStyle: 'solid',
  },
}

function render(p) {
  const gen = rough.generator()
  const out = []
  let seed = 1
  const opts = (o = {}) => ({ seed: seed++, roughness: 1.1, bowing: 1, strokeWidth: 2, stroke: p.ink, ...o })

  const draw = (drawable) => {
    for (const s of gen.toPaths(drawable)) {
      const d = s.d.replace(/-?\d+\.\d+/g, (m) => String(Math.round(+m * 10) / 10))
      out.push(`<path d="${d}" stroke="${s.stroke}" stroke-width="${s.strokeWidth}" fill="${s.fill || 'none'}" stroke-linecap="round" stroke-linejoin="round"/>`)
    }
  }

  const missing = new Set()
  const text = (str, cx, baseline, size, { bold = true, color = p.ink, align = 'center' } = {}) => {
    const f = bold ? fonts.bold : fonts.regular
    for (const ch of str) if (ch.trim() && f.charToGlyphIndex(ch) === 0) missing.add(ch)
    const w = f.getAdvanceWidth(str, size)
    const x = align === 'center' ? cx - w / 2 : cx
    out.push(`<path d="${f.getPath(str, x, baseline, size).toPathData(0)}" fill="${color}" stroke="${color}" stroke-width="0.7" stroke-linejoin="round"/>`)
  }
  // 상자 안 세로 가운데에 글자를 놓는다
  const centeredBaseline = (cy, size, bold = true) => {
    const f = bold ? fonts.bold : fonts.regular
    const s = size / f.unitsPerEm
    return cy + ((f.ascender + f.descender) / 2) * s
  }

  const roundedRect = (x, y, w, h, r) =>
    `M${x + r},${y} L${x + w - r},${y} Q${x + w},${y} ${x + w},${y + r} L${x + w},${y + h - r} ` +
    `Q${x + w},${y + h} ${x + w - r},${y + h} L${x + r},${y + h} Q${x},${y + h} ${x},${y + h - r} ` +
    `L${x},${y + r} Q${x},${y} ${x + r},${y} Z`

  const box = (x, y, w, h, fill, label, size = 29) => {
    draw(gen.path(roundedRect(x, y, w, h, 12), opts({ fill, fillStyle: p.fillStyle, hachureGap: 8, fillWeight: 1.4, disableMultiStrokeFill: true })))
    if (label) text(label, x + w / 2, centeredBaseline(y + h / 2, size), size)
  }

  const arrow = (x1, y1, x2, y2, o = {}) => {
    draw(gen.line(x1, y1, x2, y2, opts(o)))
    const a = Math.atan2(y2 - y1, x2 - x1), len = 13, spread = 0.5
    for (const s of [-spread, spread]) {
      draw(gen.line(x2, y2, x2 - len * Math.cos(a + s), y2 - len * Math.sin(a + s), opts({ ...o, roughness: 0.6 })))
    }
  }

  // ── 왼쪽: 없으면 ──────────────────────────────
  const rows = [150, 260, 370]
  text('Workbench 가 없으면', 300, 62, 38)
  const leftServices = ['서비스 A', '서비스 B', '서비스 C']
  const leftProviders = ['이미지 API', '비디오 API', '음성 API']
  rows.forEach((cy, i) => {
    box(40, cy - 32, 170, 64, p.service, leftServices[i])
    box(390, cy - 32, 170, 64, p.provider, leftProviders[i])
  })
  // 서비스마다 Provider 를 따로 붙인다 — 3 × 3 개의 선이 엉킨다
  for (const a of rows) for (const b of rows) {
    draw(gen.line(212, a, 388, b, opts({ stroke: p.bad, strokeWidth: 1.6, roughness: 1.8 })))
  }
  text('키, 재시도, 과금이 서비스마다 제각각', 300, 470, 30, { color: p.bad })

  // ── 가운데 ────────────────────────────────────
  arrow(594, 260, 664, 260, { stroke: p.muted, strokeWidth: 3 })

  // ── 오른쪽: 있으면 ────────────────────────────
  text('Workbench 가 있으면', 985, 62, 38)
  const rightServices = ['서비스 A', '서비스 B', 'AI 클라이언트']
  const rightProviders = ['이미지', '비디오', '음성']
  const rRows = [160, 260, 360]
  rRows.forEach((cy, i) => {
    box(692, cy - 28, 194, 56, p.service, rightServices[i])
    box(1140, cy - 28, 110, 56, p.provider, rightProviders[i])
    arrow(890, cy, 924, cy)
    arrow(1094, cy, 1134, cy)
  })
  box(930, 112, 160, 296, p.hub)
  text('Workbench', 1010, 160, 32)
  ;['인증', '작업 큐', '재시도', '비용 계산', '템플릿'].forEach((t, i) => text(t, 1010, 214 + i * 43, 28))
  text('연결은 한 번, 비용은 정확히', 985, 472, 35, { color: p.accent })

  if (missing.size) console.error('글꼴에 없는 글자:', [...missing].join(' '))
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}">\n` +
    `<title>Workbench 가 없으면 서비스마다 AI Provider 를 따로 붙이고, 있으면 한 곳으로 모인다</title>\n` +
    out.join('\n') + '\n</svg>\n'
}

for (const [name, p] of Object.entries(PALETTES)) {
  const file = path.join(outDir, `overview-${name}.svg`)
  fs.writeFileSync(file, render(p))
  console.log(file, (fs.statSync(file).size / 1024).toFixed(0) + 'KB')
}
