// The stress loads of packages/stress: one load = one ladder of levels, one widget per level. build.ts turns
// this table into the widgets and manifest.json. A load's widget is the shared frame (a header line and a
// 300 px field, the measuring loop of HARNESS) around its own CSS, setup and per-frame step; `N` is the level's
// value. The loads cover what makes a renderer slow (docs/decisions.md, "Runtime limits"; the limits review
// of 2026-10-28): paint effects, document size, change per frame, text, canvas, SVG, layout queries, CSS rules.
// The ladders cross the strict limits on purpose, so the data shows the cost on both sides of each one.

/** The strict limits a level can cross (widget profile 1, set from the client teams' runs of v0.3.0's kit in v0.6.0). `est` per level is rough. */
export const STRICT_LIMITS = {
  heavyBoxes: 16, // boxes with a blurred box-shadow, filter, backdrop-filter or opacity < 1 at once
  backdropBoxes: 2, // of those, with backdrop-filter
  nodes: 2000, // elements, text and comments in the document
  opsPerBatch: 10_000, // validated ops in one batch (the shim hands a flush's DOM ops over in parts of ~1000 since v0.6.0)
  cssRules: 400, // style rules in the document
  canvasPerSend: 4000, // canvas commands in one send
  layoutQueries: 128, // layout queries the host answers in one task
  docText: 49_152, // text of the document (text, styles, visible attributes)
  textValue: 32_768, // one text value (a text node, a stylesheet, an attribute's text)
  attr: 8192, // one attribute value
  complexText: 8192, // complex-script text (Hebrew, Arabic, Indic, CJK, emoji: UTF-16 units at or above U+0590)
} as const;
export type LimitKey = keyof typeof STRICT_LIMITS;

export interface Load {
  id: string;
  title: string;
  /** What it stresses, one sentence for the manifest and the report. */
  stresses: string;
  /** The level's parameter and its unit. */
  param: string;
  unit: string;
  levels: number[];
  /** Heavy boxes on screen at this level (the §3 count), for the report's fit. */
  heavy?: (n: number) => number;
  backdrop?: (n: number) => number;
  /** Rough count of what the strict limits measure at this level. */
  est: (n: number) => Partial<Record<LimitKey, number>>;
  /** Boxes that change every frame, for the report's per-box fit (the §4 cost model's "changed boxes"). */
  changed?: (n: number) => number;
  /** The report fits its cost per changed box: `heavy` boxes give the WebView cost model's A, `plain` ones its B. */
  fit?: 'heavy' | 'plain';
  css: string;
  setup: string;
  step?: string;
}

/** Moves boxes like the ARS stress test: every box moves, turns and (bg) changes colour each frame, so nothing is reused. */
const MOVE = `function move(a,t,bg){for(var i=0;i<a.length;i++){var s=a[i].style,k=t/900+i*.37;s.transform='translate('+((i*13.7+t/20)%(W-24)).toFixed(1)+'px,'+(138+Math.sin(k)*130).toFixed(1)+'px) rotate('+((k*57)%360).toFixed(0)+'deg)';if(bg)s.backgroundColor='hsl('+((i*7+t/10)%360).toFixed(0)+',70%,50%)'}}`;
/** Ten coloured stripes moving down the field: something under a backdrop-filter that changes every frame. */
const STRIPES = `var st=mk(10,'s');st.forEach(function(d,i){d.style.background='hsl('+i*36+',70%,55%)'});function stripes(t){st.forEach(function(d,i){d.style.transform='translateY('+((i*33+t/15)%330-30).toFixed(1)+'px)'})}`;
const STRIPE_CSS = '.s{position:absolute;left:0;top:0;width:100%;height:28px}';
const BOX = 'position:absolute;left:0;top:0;width:22px;height:22px;border-radius:6px;background:#4a90e2';

const words = 'the of widget chat bubble frame layout paint shadow render text glyph line wrap style rule node batch canvas path query'.split(' ');

export const LOADS: Load[] = [
  {
    id: 'shadow-static', title: 'Blurred shadows, static', stresses: 'boxes with a blurred box-shadow that never change: what an idle heavy widget costs per frame',
    param: 'boxes', unit: 'boxes', levels: [0, 8, 16, 32, 64, 128, 256],
    heavy: (n) => n, est: (n) => ({ heavyBoxes: n, nodes: n + 8 }),
    css: `.b{${BOX};box-shadow:0 2px 6px rgba(0,0,0,.45)}`,
    setup: `mk(N,'b').forEach(function(d,i){d.style.transform='translate('+((i%12)*26+4)+'px,'+((Math.floor(i/12)*26)%276+4)+'px)'})`,
  },
  {
    id: 'shadow-moving', fit: 'heavy', title: 'Blurred shadows, moving', stresses: 'boxes with a blurred box-shadow that move and change colour every frame',
    param: 'boxes', unit: 'boxes', levels: [0, 4, 8, 16, 32, 64, 128, 256, 512],
    heavy: (n) => n, changed: (n) => n, est: (n) => ({ heavyBoxes: n, nodes: n + 8, opsPerBatch: 2 * n }),
    css: `.b{${BOX};box-shadow:0 1px 4px rgba(0,0,0,.5)}`,
    setup: `${MOVE}var a=mk(N,'b')`, step: 'move(a,t,1)',
  },
  {
    id: 'ars-cell', fit: 'heavy', title: 'The ARS cell, moving', stresses: 'the ARS stress test: each cell has a gradient, a blurred and an inset shadow, a border, opacity .85 and a label, and moves every frame',
    param: 'cells', unit: 'cells', levels: [0, 16, 64, 128, 256, 512, 1000],
    heavy: (n) => n, changed: (n) => n, est: (n) => ({ heavyBoxes: n, nodes: 2 * n + 8, opsPerBatch: 4 * n }),
    css: `.c{${BOX};background:linear-gradient(135deg,rgba(255,80,80,.8),rgba(80,80,255,.6));box-shadow:0 1px 3px rgba(0,0,0,.4),inset 0 0 0 1px rgba(255,255,255,.3);border:1px solid rgba(0,0,0,.2);opacity:.85;font-size:8px;line-height:22px;text-align:center;color:#fff}`,
    setup: `${MOVE}var a=mk(N,'c');a.forEach(function(d,i){d.textContent=i%100})`, step: 'move(a,t,1)',
  },
  {
    id: 'opacity-moving', fit: 'heavy', title: 'Opacity, moving', stresses: 'boxes with opacity .85 (and nothing else heavy) that move and change colour every frame',
    param: 'boxes', unit: 'boxes', levels: [0, 8, 16, 32, 64, 128, 256, 512],
    heavy: (n) => n, changed: (n) => n, est: (n) => ({ heavyBoxes: n, nodes: n + 8, opsPerBatch: 2 * n }),
    css: `.b{${BOX};opacity:.85}`,
    setup: `${MOVE}var a=mk(N,'b')`, step: 'move(a,t,1)',
  },
  {
    id: 'filter-moving', fit: 'heavy', title: 'Filter, moving', stresses: 'boxes with filter: blur(1px) brightness(1.15) that move and change colour every frame',
    param: 'boxes', unit: 'boxes', levels: [0, 4, 8, 16, 32, 64, 128, 256],
    heavy: (n) => n, changed: (n) => n, est: (n) => ({ heavyBoxes: n, nodes: n + 8, opsPerBatch: 2 * n }),
    css: `.b{${BOX};filter:blur(1px) brightness(1.15)}`,
    setup: `${MOVE}var a=mk(N,'b')`, step: 'move(a,t,1)',
  },
  {
    id: 'backdrop-count', title: 'Backdrop filter, count', stresses: 'fixed 64×44 px panels with backdrop-filter: blur(6px) over stripes that move every frame',
    param: 'panels', unit: 'panels', levels: [0, 1, 2, 3, 4, 8, 16],
    heavy: (n) => n, backdrop: (n) => n, changed: () => 10, est: (n) => ({ heavyBoxes: n, backdropBoxes: n, nodes: n + 18 }),
    css: `${STRIPE_CSS}.p{position:absolute;width:64px;height:44px;border-radius:10px;backdrop-filter:blur(6px);background:rgba(255,255,255,.15)}`,
    setup: `${STRIPES}mk(N,'p').forEach(function(d,i){d.style.left=(i%4)*72+6+'px';d.style.top=Math.floor(i/4)*52+6+'px'})`, step: 'stripes(t)',
  },
  {
    id: 'backdrop-area', title: 'Backdrop filter, area', stresses: 'one panel with backdrop-filter: blur(8px) covering a share of the 300 px field, over stripes that move every frame',
    param: 'area', unit: '% of the field', levels: [0, 10, 25, 50, 100],
    heavy: (n) => (n ? 1 : 0), backdrop: (n) => (n ? 1 : 0), changed: () => 10, est: (n) => ({ heavyBoxes: n ? 1 : 0, backdropBoxes: n ? 1 : 0, nodes: 19 }),
    css: `${STRIPE_CSS}.p{position:absolute;left:0;top:0;width:100%;backdrop-filter:blur(8px);background:rgba(255,255,255,.12)}`,
    setup: `${STRIPES}if(N)mk(1,'p')[0].style.height=N*3+'px'`, step: 'stripes(t)',
  },
  {
    id: 'gradient-moving', fit: 'plain', title: 'Gradients, moving', stresses: 'boxes whose linear-gradient background turns and changes colour every frame while they move',
    param: 'boxes', unit: 'boxes', levels: [0, 16, 64, 128, 256, 512, 1000],
    changed: (n) => n, est: (n) => ({ nodes: n + 8, opsPerBatch: 2 * n }),
    css: `.b{${BOX};border-radius:4px}`,
    setup: `${MOVE}var a=mk(N,'b')`,
    step: `move(a,t,0);for(var i=0;i<a.length;i++){var h=(i*7+t/10)%360|0;a[i].style.backgroundImage='linear-gradient('+((t/5+i*20)%360|0)+'deg,hsl('+h+',80%,60%),hsl('+((h+120)%360)+',80%,40%))'}`,
  },
  {
    id: 'clip-moving', fit: 'plain', title: 'Rounded clip, moving', stresses: 'boxes with overflow: hidden and border-radius clipping a child, moving and changing colour every frame',
    param: 'boxes', unit: 'boxes', levels: [0, 16, 64, 128, 256, 512, 1000],
    changed: (n) => n, est: (n) => ({ nodes: 2 * n + 8, opsPerBatch: 2 * n }),
    css: `.b{${BOX};border-radius:9px;overflow:hidden}.b i{display:block;width:30px;height:12px;margin:-3px;background:#fff8}`,
    setup: `${MOVE}var a=mk(N,'b');a.forEach(function(d){d.appendChild(document.createElement('i'))})`, step: 'move(a,t,1)',
  },
  {
    id: 'dom-static', title: 'Document size, static', stresses: 'a static document of N small elements: the boot batch, the idle cost of a large document',
    param: 'elements', unit: 'elements', levels: [100, 250, 500, 1000, 2000, 3000, 4000],
    est: (n) => ({ nodes: n + 8, opsPerBatch: 2 * n }),
    css: '.d{display:inline-block;width:5px;height:5px;margin:1px;background:#4a90e2;vertical-align:top}',
    setup: `mk(N,'d')`,
  },
  {
    id: 'dom-tick', title: 'Document size, a few changing', stresses: 'N small static elements and 8 labels whose text changes every frame: the cost of a small change in a large document (a renderer that rebuilds the whole document pays for all N)',
    param: 'elements', unit: 'elements', levels: [100, 500, 1000, 2000, 3000, 4000],
    changed: () => 8, est: (n) => ({ nodes: n + 24, opsPerBatch: 2 * n }),
    css: '.d{display:inline-block;width:5px;height:5px;margin:1px;background:#4a90e2;vertical-align:top}.t{position:absolute;width:60px;font:11px monospace;background:#fffc;color:#000}',
    setup: `mk(N,'d');var a=mk(8,'t');a.forEach(function(d,i){d.style.left=(i%4)*70+4+'px';d.style.top=Math.floor(i/4)*20+4+'px';d.textContent='0'})`,
    step: `for(var i=0;i<8;i++)a[i].firstChild.data=String((t/10|0)+i)`,
  },
  {
    id: 'style-churn', fit: 'plain', title: 'Style and text churn', stresses: 'plain boxes (nothing heavy) whose position, colour and label change every frame: three ops per box per frame',
    param: 'boxes', unit: 'boxes', levels: [16, 64, 256, 1000, 2000, 3000, 4000],
    changed: (n) => n, est: (n) => ({ nodes: 2 * n + 8, opsPerBatch: 3 * n }),
    css: `.b{${BOX};width:18px;height:18px;border-radius:0;font:8px/18px system-ui,sans-serif;text-align:center;color:#fff}`,
    setup: `${MOVE}var a=mk(N,'b');a.forEach(function(d){d.textContent='0'})`,
    step: `move(a,t,1);for(var i=0;i<a.length;i++)a[i].firstChild.data=String((t/100+i)%100|0)`,
  },
  {
    id: 'text-volume', title: 'Text volume', stresses: 'N characters of Latin text in paragraphs, one counter changing every frame',
    param: 'characters', unit: 'chars', levels: [1024, 4096, 16_384, 49_152, 65_536, 131_072],
    changed: () => 1, est: (n) => ({ docText: n, nodes: Math.ceil(n / 480) * 2 + 10 }),
    css: 'p{margin:0 0 3px;font:10px/1.25 system-ui,sans-serif}#n{position:absolute;right:4px;top:4px;background:#fffc;color:#000;font:11px monospace}',
    setup: `var w=${JSON.stringify(words.join(' '))}.split(' '),left=N,k=0;while(left>0){var s='';while(s.length<480&&s.length<left){s+=w[k%w.length]+' ';k=(k*7+3)%101}var p=document.createElement('p');p.textContent=s.slice(0,Math.min(480,left));F.appendChild(p);left-=480}var c=document.createElement('b');c.id='n';c.textContent='0';F.appendChild(c)`,
    step: 'c.firstChild.data=String(t/10|0)',
  },
  {
    id: 'complex-text', title: 'Complex scripts', stresses: 'N characters of Arabic, Hebrew, Devanagari, Thai and CJK text; one 128-character paragraph is rewritten every frame (shaping on change)',
    param: 'characters', unit: 'chars', levels: [256, 1024, 2048, 4096, 8192, 16_384],
    changed: () => 1, est: (n) => ({ complexText: n, docText: n, nodes: Math.ceil(n / 128) * 2 + 8 }),
    css: 'p{margin:0 0 2px;font:11px/1.3 system-ui,sans-serif}',
    setup: `var S=['مرحبا بكم في الدردشة ','שלום לכולם בצ׳אט ','नमस्ते चैट में आपका स्वागत है ','สวัสดีทุกคนในแชท ','欢迎来到聊天窗口文字排版测试'].join(''),ps=[];for(var j=0;j*128<N;j++){var p=document.createElement('p');p.textContent=(S+S+S).substr((j*37)%S.length,Math.min(128,N-j*128));F.appendChild(p);ps.push(p)}`,
    step: 'var k=(t/50|0)%S.length;ps[0].firstChild.data=(S+S+S).substr(k,Math.min(128,N))',
  },
  {
    id: 'emoji', title: 'Emoji', stresses: 'N colour emoji in rows of 32, eight of them cycling every frame',
    param: 'emoji', unit: 'emoji', levels: [50, 200, 500, 1000, 2000, 4000],
    changed: () => 8, est: (n) => ({ complexText: 2 * n, docText: 2 * n, nodes: Math.ceil(n / 32) * 2 + 24 }),
    css: 'div.r{font:13px/1.2 system-ui,sans-serif;white-space:nowrap}.e{position:absolute;font:20px system-ui;background:#fffc}',
    setup: `var E=Array.from('😀😂🥰😎🤖👾🎉🔥⭐🍕🚀🌈🐱🐶🦊🍀⚽🎵💡📦');for(var j=0;j*32<N;j++){var s='';for(var i=0;i<32&&j*32+i<N;i++)s+=E[(j*7+i)%E.length];mk(1,'r')[0].textContent=s}var a=mk(8,'e');a.forEach(function(d,i){d.style.left=i*34+4+'px';d.style.top='250px';d.textContent=E[i]})`,
    step: 'for(var i=0;i<8;i++)a[i].firstChild.data=E[((t/80|0)+i*3)%E.length]',
  },
  {
    id: 'canvas-cmds', title: 'Canvas commands per frame', stresses: 'a 600×600 canvas (300 px on screen) redrawn every frame with N commands (fillStyle + fillRect pairs)',
    param: 'commands', unit: 'commands per frame', levels: [200, 1000, 2000, 4000, 8000, 16_000],
    est: (n) => ({ canvasPerSend: n + 1, nodes: 10 }),
    css: 'canvas{display:block;width:300px;height:300px}',
    setup: `var cv=document.createElement('canvas');cv.width=600;cv.height=600;F.appendChild(cv);var x=cv.getContext('2d')`,
    step: `x.clearRect(0,0,600,600);for(var i=0;i<N/2;i++){x.fillStyle='hsl('+((i*7+t/10)%360|0)+',70%,50%)';x.fillRect((i*37+t/8)%580,(i*53)%580,16,16)}`,
  },
  {
    id: 'svg-path', title: 'SVG path, morphing', stresses: 'one SVG path of N line segments whose d attribute is rewritten every frame',
    param: 'segments', unit: 'segments', levels: [50, 200, 500, 1000, 2000, 4000],
    changed: () => 1, est: (n) => ({ attr: 12 * n, nodes: 12 }),
    css: 'svg{display:block;width:300px;height:300px}path{fill:none;stroke:#4a90e2;stroke-width:1.5}',
    setup: `var ns='http://www.w3.org/2000/svg',sv=document.createElementNS(ns,'svg');sv.setAttribute('viewBox','0 0 300 300');var pa=document.createElementNS(ns,'path');sv.appendChild(pa);F.appendChild(sv)`,
    step: `var d='M0 150';for(var i=1;i<=N;i++)d+='L'+(i*300/N).toFixed(1)+' '+(150+Math.sin(i*.3+t/300)*120).toFixed(1);pa.setAttribute('d',d)`,
  },
  {
    id: 'layout-queries', title: 'Layout queries per frame', stresses: 'N pairs of a style write and a getBoundingClientRect read per frame on 40 bars: each read forces a layout, answered synchronously by the renderer',
    param: 'queries', unit: 'queries per frame', levels: [0, 1, 4, 16, 64, 256],
    changed: (n) => Math.min(n, 40), est: (n) => ({ nodes: 50, layoutQueries: n }),
    css: '.q{height:5px;margin:1px 0;background:#4a90e2}',
    setup: `var a=mk(40,'q'),sum=0`,
    step: `for(var k=0;k<N;k++){a[k%40].style.width=((t/10+k*7)%280|0)+'px';sum+=a[(k+1)%40].getBoundingClientRect().width}`,
  },
  {
    id: 'css-rules', title: 'CSS rules', stresses: 'N descendant style rules in stylesheets of 200 rules each; 8 containers change class every frame, so 64 boxes match against all N again',
    param: 'rules', unit: 'rules', levels: [50, 200, 400, 800, 1600, 3200],
    changed: () => 72, est: (n) => ({ cssRules: n, nodes: 80, opsPerBatch: 80 * 3 }),
    css: '.g{display:flex;gap:2px;margin:2px}.g i{display:block;width:30px;height:30px;background:#ccc}',
    // The frame's three rules and the load's two count too: N rules in all, so the 400 level is at the cap, not over it.
    setup: `var css='';for(var i=0;i<N-5;i++){css+='.k'+(i%8)+' .r'+(i%64)+'{background:hsl('+(i*7%360)+',60%,'+(35+i%40)+'%)}';if(i%200==199||i==N-6){var y=document.createElement('style');y.textContent=css;document.head.appendChild(y);css=''}}var g=mk(8,'g');g.forEach(function(e,j){for(var i=0;i<8;i++){var b=document.createElement('i');b.className='r'+(j*8+i);e.appendChild(b)}});var fr=0`,
    step: `fr++;for(var j=0;j<8;j++)g[j].className='g k'+((j+fr)%8)`,
  },
];
