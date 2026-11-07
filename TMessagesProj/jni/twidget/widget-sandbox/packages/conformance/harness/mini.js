// Mini harness for the client-SDK group (visual/sdk): the same data-conformance
// result as harness.js in ~0.5 KB compressed, so a widget fits a v1 mini-widget
// (4 KB compressed). S.check(id, name, ok, detail) records a check and prints a line in
// #log; S.log(text) prints a note; S.target(name, elementId) publishes a tap
// target; S.done() / S.start() set the status (done / awaiting input).
var S = (function () {
  var r = document.documentElement, c = [], tg = {}, st = 'running';
  function rc(e) { var b = e.getBoundingClientRect(); return [b.left, b.top, b.width, b.height].map(function (v) { return Math.round(v * 10) / 10; }); }
  function pub() {
    var t = {};
    for (var k in tg) { var e = document.getElementById(tg[k]); if (e) t[k] = rc(e); }
    r.setAttribute('data-conformance', JSON.stringify({ v: 1, id: r.getAttribute('data-test'), status: st, checks: c, targets: t }));
  }
  function log(m, ok) {
    var l = document.getElementById('log');
    if (!l) return;
    var d = document.createElement('div');
    d.textContent = (ok === true ? '✓ ' : ok === false ? '✗ ' : '· ') + m;
    l.appendChild(d);
  }
  return {
    TW: typeof TelegramWidget !== 'undefined' ? TelegramWidget : null,
    check: function (id, name, ok, d) { c.push([id, name, ok ? 'pass' : 'fail', d || '']); log(name + (d ? ' (' + d + ')' : ''), !!ok); pub(); },
    log: log,
    target: function (n, id) { tg[n] = id; },
    start: function () { st = 'awaiting'; pub(); },
    done: function () { st = 'done'; pub(); },
  };
})();
