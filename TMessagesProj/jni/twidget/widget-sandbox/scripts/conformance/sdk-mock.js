// A stand-in for TelegramWidget per docs/sdk-v1.md, injected only when build.ts
// renders the client-SDK group (visual/sdk) natively for its reference pictures:
// the widgets then show their "SDK present" state. It checks arguments as the
// contract says and does nothing else (no dialogs, no haptics). The palette is a
// reference palette for the pictures, not any client's. With
// window.__conformanceNoSdk (the visual set's pictures) it sets only the palette
// variables, and no TelegramWidget. The bubble's colour is the page's canvas, which
// build.ts sets outside the document, so a widget's body background still reaches
// the whole picture as on every client.
(function () {
  var P = {
    light: { bg_color: '#ffffff', text_color: '#000000', hint_color: '#707579', link_color: '#3390ec', button_color: '#3390ec', button_text_color: '#ffffff', secondary_bg_color: '#f4f4f5', accent_text_color: '#3390ec', subtitle_text_color: '#707579', destructive_text_color: '#e53935', section_bg_color: '#ffffff', section_header_text_color: '#3390ec', section_separator_color: '#e7e7e7', header_bg_color: '#ffffff', bottom_bar_bg_color: '#ffffff' },
    dark: { bg_color: '#212121', text_color: '#ffffff', hint_color: '#aaaaaa', link_color: '#8774e1', button_color: '#8774e1', button_text_color: '#ffffff', secondary_bg_color: '#181818', accent_text_color: '#8774e1', subtitle_text_color: '#aaaaaa', destructive_text_color: '#ff595a', section_bg_color: '#212121', section_header_text_color: '#8774e1', section_separator_color: '#3d3d3d', header_bg_color: '#212121', bottom_bar_bg_color: '#212121' },
  };
  var scheme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  var params = Object.freeze(P[scheme]);
  for (var k in params) document.documentElement.style.setProperty('--tg-theme-' + k.replace(/_/g, '-'), params[k]);
  document.documentElement.style.setProperty('--tg-color-scheme', scheme);
  if (window.__conformanceNoSdk) return;
  // Subdomains count too (durov.t.me), as in the framework.
  var TG = /^([a-z0-9-]+\.)*(t\.me|telegram\.me|telegram\.dog)$/i;
  function url(u, telegram) {
    if (typeof u !== 'string' || u.length > 256) throw new TypeError('URL must be a string of at most 256 characters');
    var m = /^https:\/\/([^/?#@]+)(?:[/?#]|$)/i.exec(u);
    if (!m || /@/.test(u.split(/[/?#]/)[2] || '')) throw new TypeError('URL must be an absolute https URL without user info');
    if (TG.test(m[1]) !== telegram) throw new TypeError(telegram ? 'openTelegramLink takes t.me links' : 'openLink does not take t.me links');
  }
  var M = window.__conformanceMessage || {};
  var H = {};
  var check = function (v, list) { if (list.indexOf(v) < 0) throw new TypeError('unknown haptic ' + v); return H; };
  H.impactOccurred = function (s) { return check(s, ['light', 'medium', 'heavy', 'rigid', 'soft']); };
  H.notificationOccurred = function (t) { return check(t, ['error', 'success', 'warning']); };
  H.selectionChanged = function () { return H; };
  window.TelegramWidget = {
    profile: 1, isProfileAtLeast: function (n) { return typeof n === 'number' && n <= 1; },
    colorScheme: scheme, theme: scheme, themeParams: params, isActive: true,
    onEvent: function () {}, offEvent: function () {}, onTheme: function (cb) { if (typeof cb === 'function') cb(scheme); }, ready: function () {},
    openLink: function (u) { url(u, false); }, openTelegramLink: function (u) { url(u, true); },
    HapticFeedback: H,
    setResizeAnchor: function (e) { if (e !== 'top' && e !== 'bottom') throw new TypeError("setResizeAnchor: edge must be 'top' or 'bottom'"); },
    // The widget's state and the message context, from the header's first message spec (build.ts sets
    // __conformanceMessage): the original as the tester first sees it, a viewer when the message may ask.
    chatData: M.forward ? null : M.chat_data !== undefined ? M.chat_data : null,
    publicData: M.public_data !== undefined ? M.public_data : null,
    isForward: !!M.forward,
    isPreview: !!M.inline,
    chatType: M.forward || M.inline ? null : M.chat === 'group' ? 'supergroup' : 'private',
    viewer: M.allow_queries && !M.inline ? Object.freeze({ id: 1001, first_name: 'Ann', last_name: 'Lee', username: 'ann_lee' }) : null,
    // Checked as the contract says; a request never settles here, so the pictures show the state before any answer.
    request: function (d) { if (typeof d !== 'string' || !d || unescape(encodeURIComponent(d)).length > 64) throw new TypeError('request: data must be 1-64 bytes of text'); return new Promise(function () {}); },
    copyText: function (t) { if (typeof t !== 'string' || !t || t.length > 256) throw new TypeError('copyText: text must be 1-256 characters'); },
    watchPublicData: function () {}, unwatchPublicData: function () {},
  };
})();
