let bubbleCSS = #"""
html, body { margin: 0; padding: 0; }
body { font-family: -apple-system; font-size: 13px; line-height: 1.3; color: rgba(0,0,0,1.000); }
p { margin: 0 0 8px 0; }
h1, h2, h3, h4 { margin: 4px 0 6px 0; line-height: 1.2; }
h1 { font-size: 22px; } h2 { font-size: 19px; } h3 { font-size: 17px; } h4 { font-size: 15px; }
ul, ol { margin: 4px 0 8px 0; padding-left: 22px; }
li { margin: 2px 0; }
blockquote { margin: 4px 0 8px 0; padding: 2px 0 2px 10px; border-left: 3px solid rgba(0,122,255,1.000); color: rgba(142,142,147,1.000); }
code { font-family: ui-monospace; font-size: 13px; background-color: rgba(0,0,0,0.080); padding: 1px 4px; border-radius: 4px; }
pre { font-family: ui-monospace; font-size: 12.5px; line-height: 1.45; background-color: rgba(0,0,0,0.070); padding: 8px 10px; border-radius: 8px; margin: 4px 0 8px 0; white-space: pre-wrap; }
pre code { background-color: transparent; padding: 0; font-size: 12.5px; }
a { color: rgba(0,122,255,1.000); text-decoration: none; }
a:hover { text-decoration: underline; }
hr { border: 0; border-top: 1px solid rgba(142,142,147,0.400); margin: 8px 0; height: 0; }
table { border-collapse: collapse; }
img { vertical-align: middle; }
mark { background-color: #ffe066; color: #1c1c1e; padding: 0 2px; border-radius: 3px; }
small { font-size: 12px; }
body > *:first-child { margin-top: 0; }
body > *:last-child { margin-bottom: 0; }
.widget > *:first-child { margin-top: 0; }
.widget > *:last-child { margin-bottom: 0; }
.card { background-color: rgba(255,255,255,1.000); border: 1px solid rgba(229,229,234,1.000); border-radius: 14px; color: rgba(0,0,0,1.000); }
.pad { padding: 12px 14px; }
.panel { background-color: rgba(127,127,127,0.12); border-radius: 10px; padding: 8px 10px; }
.fg { color: rgba(0,0,0,1.000); } .muted { color: rgba(142,142,147,1.000); } .small { font-size: 12px; } .tiny { font-size: 11px; }
.caps { font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(142,142,147,1.000); }
.row { display: flex; align-items: center; } .between { justify-content: space-between; }
.grow { flex-grow: 1; } .center { text-align: center; } .right { text-align: right; }
.sep { border-top: 1px solid rgba(229,229,234,1.000); } .nowrap { white-space: nowrap; }
.t-blue { color: rgba(0,122,255,1.000); } .t-green { color: rgba(52,199,89,1.000); } .t-red { color: rgba(255,59,48,1.000); } .t-orange { color: rgba(255,159,10,1.000); } .t-purple { color: rgba(175,82,222,1.000); } .t-teal { color: rgba(90,200,250,1.000); }
.badge { display: inline-block; padding: 1px 8px; border-radius: 9px; font-size: 11px; font-weight: 700; background-color: rgba(127,127,127,0.18); }
.badge.blue { background-color: rgba(0,122,255,0.16); color: rgba(0,122,255,1.000); } .badge.green { background-color: rgba(52,199,89,0.18); color: rgba(52,199,89,1.000); }
.badge.red { background-color: rgba(255,59,48,0.16); color: rgba(255,59,48,1.000); } .badge.orange { background-color: rgba(255,149,0,0.18); color: rgba(255,159,10,1.000); }
.badge.purple { background-color: rgba(175,82,222,0.16); color: rgba(175,82,222,1.000); }
.btn { display: inline-block; padding: 6px 14px; border-radius: 9px; font-weight: 600; text-align: center; text-decoration: none; background-color: rgba(127,127,127,0.16); color: rgba(0,0,0,1.000); }
.btn:hover { background-color: rgba(127,127,127,0.28); text-decoration: none; }
.btn.primary { background-color: rgba(0,122,255,1.000); color: #ffffff; } .btn.primary:hover { background-color: rgba(0,104,217,1.000); }
.btn.success { background-color: rgba(52,199,89,1.000); color: #ffffff; } .btn.danger { background-color: rgba(255,59,48,0.14); color: rgba(255,59,48,1.000); }
.btn.success:hover { background-color: rgba(52,199,89,0.85); } .btn.danger:hover { background-color: rgba(255,59,48,0.24); }
.chip { display: inline-block; padding: 3px 12px; border-radius: 14px; border: 1px solid rgba(0,122,255,1.000); color: rgba(0,122,255,1.000); margin: 2px 4px 2px 0; text-decoration: none; }
.chip:hover { background-color: rgba(127,127,127,0.12); text-decoration: none; }
.av { display: inline-block; width: 24px; height: 24px; border-radius: 12px; text-align: center; line-height: 24px; color: #ffffff; font-size: 10px; font-weight: 700; background-color: #8e8e93; }
.bar { height: 8px; border-radius: 4px; background-color: rgba(127,127,127,0.22); }
.bar > div { height: 8px; border-radius: 4px; background-color: rgba(0,122,255,1.000); }
.card pre, .panel pre { background-color: rgba(127,127,127,0.13); } .card code, .panel code { background-color: rgba(127,127,127,0.15); }
.card pre code, .panel pre code { background-color: transparent; }
[data-action] { cursor: pointer; }
button { font-size: 14px; font-weight: 600; padding: 5px 13px; border: 0; border-radius: 9px; background-color: rgba(127,127,127,0.16); color: rgba(0,0,0,1.000); text-align: center; }
button:hover { background-color: rgba(127,127,127,0.28); }
button.primary { background-color: rgba(0,122,255,1.000); color: #ffffff; } button.primary:hover { background-color: rgba(0,104,217,1.000); }
button:active, .btn:active { background-color: rgba(127,127,127,0.40); } button.primary:active, .btn.primary:active { background-color: rgba(0,104,217,1.000); }
.lc-script-error { margin-top: 6px; padding: 3px 8px; border-radius: 7px; font-size: 11.5px; line-height: 1.3; background-color: rgba(255,149,0,0.16); color: rgba(255,159,10,1.000); }
kbd { font-family: ui-monospace; font-size: 12px; padding: 0 6px; border-radius: 5px; border: 1px solid rgba(229,229,234,1.000); border-bottom-width: 2px; background-color: rgba(255,255,255,1.000); color: rgba(0,0,0,1.000); }
"""#
