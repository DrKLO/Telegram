/*
 *  Telegram-Android browser extension
 *
 *  # Gestures
 *  This script captures whether touch event is consumed by a website, to otherwise apply
 *  down or right gesture. Use `event.preventDefault()` at `touchstart` to prevent those gestures.
 *  It is recommended to do `event.preventDefault()` when dragging or swiping is expected to be
 *  handled by a website.
 *
 *  You can also globally disable swipes for X and/or Y with <meta> tags:
 *   - <meta name="tg:swipes:x" content="none">
 *   - <meta name="tg:swipes:y" content="allow">
 *  Please, use these <meta> tags as the last resort, as it disables convenient back and close
 *  gestures, degrading user experience.
 *
 *  Since some websites don't do that, the script also captures `style` and `class` changes to
 *  hierarchy of a touch element, and does equivalent of `preventDefault` if those changes happen
 *  while `touchstart` or `touchmove` events.
 *
 *  === feature is hidden under debug button ===
 *  # Action Bar and Navigation Bar colors
 *  Top action bar and bottom navigation bar colors are defined with:
 *    - <meta name="tg:theme-accent" content="#FFFFFF" /> — action bar, usually an accent color
 *    - <meta name="theme-color" content="#FFFFFF" /> — action bar, usually an accent color
 *    - <meta name="tg:theme-background" content="#FFFFFF" /> — navigation bar
 *    - <meta name="theme-background-color" content="#FFFFFF" /> — navigation bar
 *    - <body> `background-color` css style — fallback
 *  `media` attribute on <meta> is also supported, feel free to use `prefers-color-scheme`
 */

if (!window.__tg__webview_set) {
    window.__tg__webview_set = true;
    (function () {
        const DEBUG = $DEBUG$;

        // Touch gestures hacks
        const isImageViewer = () => {
            if (!document.body.children || document.body.children.length != 1) return false;
            const img = document.querySelector('body > img');
            return img && img.tagName && img.tagName.toLowerCase() === 'img' && img.src === window.location.href;
        }
        const swipesDisabled = axis =>
            (document.querySelector(`meta[name="tg:swipes:${axis}"]`)||{}).content === 'none';
        let prevented = false;
        let awaitingResponse = false;
        let touchElement = null;
        let mutatedWhileTouch = false;
        let whiletouchstart = false, whiletouchmove = false;
        document.addEventListener('touchstart', e => {
            touchElement = e.target;
            awaitingResponse = true;
            whiletouchstart = true;
            if (isImageViewer()) {
                if (window.TelegramWebview) {
                    const allowScrollX = window.visualViewport && window.visualViewport.offsetLeft == 0 && !swipesDisabled('x');
                    const allowScrollY = window.visualViewport && window.visualViewport.offsetTop  == 0 && !swipesDisabled('y');
                    if (DEBUG) {
                        console.log('tgbrowser allowScroll sent after "touchstart": x=' + allowScrollX + ' y=' + allowScrollY + ' inside image viewer');
                    }
                    window.TelegramWebview.post('allowScroll', JSON.stringify([ allowScrollX, allowScrollY ]));
                }
                awaitingResponse = false;
            }
        }, true);
        document.addEventListener('touchstart', e => {
            whiletouchstart = false;
        }, false);
        document.addEventListener('touchmove', e => {
            whiletouchstart = false;
            whiletouchmove = true;
            if (awaitingResponse) {
                setTimeout(() => {
                    if (awaitingResponse) {
                        if (window.TelegramWebview) {
                            const allowScrollX = !prevented && (!window.visualViewport || window.visualViewport.offsetLeft == 0) && !mutatedWhileTouch && !swipesDisabled('x');
                            const allowScrollY = !prevented && (!window.visualViewport || window.visualViewport.offsetTop == 0)  && !mutatedWhileTouch && !swipesDisabled('y');
                            if (DEBUG) {
                                console.log('tgbrowser allowScroll sent after "touchmove": x=' + allowScrollX + ' y=' + allowScrollY, { prevented, mutatedWhileTouch });
                            }
                            window.TelegramWebview.post('allowScroll', JSON.stringify([ allowScrollX, allowScrollY ]));
                        }
                        prevented = false;
                        awaitingResponse = false;
                    }
                    mutatedWhileTouch = false;
                }, 16);
            }
        }, true);
        document.addEventListener('touchmove', e => {
            whiletouchmove = false;
        }, false);
        document.addEventListener('scroll', e => {
            if (!e.target) return;
            const allowScrollX = e.target.scrollLeft == 0 && (!window.visualViewport || window.visualViewport.offsetLeft == 0) && !prevented && !mutatedWhileTouch && !swipesDisabled('x');
            const allowScrollY = e.target.scrollTop == 0  && (!window.visualViewport || window.visualViewport.offsetTop == 0)  && !prevented && !mutatedWhileTouch && !swipesDisabled('y');
            if (DEBUG) {
                console.log('tgbrowser scroll on' + e.target + ' scrollLeft=' + e.target.scrollLeft + ' scrollTop=' + e.target.scrollTop);
            }
            if (awaitingResponse) {
                if (window.TelegramWebview) {
                    if (DEBUG) {
                        console.log('tgbrowser allowScroll sent after "scroll": x=' + allowScrollX + ' y=' + allowScrollY, { prevented, mutatedWhileTouch, scrollLeft: e.target.scrollLeft, scrollTop: e.target.scrollTop });
                    }
                    window.TelegramWebview.post('allowScroll', JSON.stringify([allowScrollX, allowScrollY]));
                }
                awaitingResponse = false;
            }
            prevented = false;
            mutatedWhileTouch = false;
        }, true);
        if (TouchEvent) {
            const originalPreventDefault = TouchEvent.prototype.preventDefault;
            TouchEvent.prototype.preventDefault = function () {
                prevented = true;
                originalPreventDefault.call(this);
            };
            const originalStopPropagation = TouchEvent.prototype.stopPropagation;
            TouchEvent.prototype.stopPropagation = function () {
                if (this.type === 'touchmove') {
                    whiletouchmove = false;
                } else if (this.type === 'touchstart') {
                    whiletouchstart = false;
                }
                originalStopPropagation.call(this);
            };
        }
        const isParentOf = (e, p) => {
            if (!e || !p) return false;
            if (e == p) return true;
            return isParentOf(e.parentElement, p);
        }
        new MutationObserver(mutationList => {
            const isTouchElement = touchElement && !![...(mutationList||[])]
                .filter(r => r && (r.attributeName === 'style' || r.attributeName === 'class'))
                .map(r => r.target)
                .filter(e => !!e && e != document.body && e != document.documentElement)
                .find(e => isParentOf(touchElement, e));
            if (isTouchElement) { // && (whiletouchstart || whiletouchmove)) {
                if (DEBUG) {
                    console.log('tgbrowser mutation detected', mutationList);
                }
                mutatedWhileTouch = true;
            }
        }).observe(document, { attributes: true, childList: true, subtree: true });

        // Retrieving colors
        const __tg__backgroundColor = () => {
            try {
                return window.getComputedStyle(document.body, null).getPropertyValue('background-color');
            } catch (e) {
                return null;
            }
        }
        const __tg__metaColor = name =>
            [...document.querySelectorAll(`meta[name="${name}"]`)]
                .filter(meta => !meta.media || window.matchMedia && window.matchMedia(meta.media).matches)
                .map(meta => meta.content)[0];
        const __tg__cssColorToRGBA = color => {
            if (!color) return null;
            if (color[0] === '#') {
                let hex = color.slice(1);
                if (hex.length === 3 || hex.length === 4) {
                    hex = hex.split('').map(char => char + char).join('');
                }
                return [parseInt(hex.slice(0,2), 16), parseInt(hex.slice(2,4), 16), parseInt(hex.slice(4,6), 16), hex.length <= 6 ? 1 : parseInt(hex.slice(6,8), 16) / 255];
            }
            const colorMatch = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*(\d+(?:\.\d+)?))?\)$/);
            if (colorMatch) {
                return [parseInt(colorMatch[1]), parseInt(colorMatch[2]), parseInt(colorMatch[3]), colorMatch[4] ? parseFloat(colorMatch[4]) : 1];
            }
            return null;
        };
        let __tg__lastActionBarColor, __tg__lastNavigationBarColor;
        window.__tg__postColorsChange = () => {
            const actionBarColor = JSON.stringify(__tg__cssColorToRGBA(
                __tg__metaColor("tg:theme-accent") ||
                __tg__metaColor("theme-color") ||
                __tg__backgroundColor()
            ));
            const navigationBarColor = JSON.stringify(__tg__cssColorToRGBA(
                __tg__metaColor("tg:theme-background") ||
                __tg__metaColor("theme-background-color") ||
                __tg__backgroundColor()
            ));
            if (window.TelegramWebview) {
                if (actionBarColor != __tg__lastActionBarColor) {
                    if (DEBUG) {
                        console.log('tgbrowser actionbar color', actionBarColor);
                    }
                    window.TelegramWebview.post("actionBarColor", __tg__lastActionBarColor = actionBarColor);
                }
                if (navigationBarColor != __tg__lastNavigationBarColor) {
                    if (DEBUG) {
                        console.log('tgbrowser navbar color', navigationBarColor);
                    }
                    window.TelegramWebview.post("navigationBarColor", __tg__lastNavigationBarColor = navigationBarColor);
                }
            }
        };
        const __tg__colorsObserver = new MutationObserver(() => {
            window.__tg__postColorsChange();
            setTimeout(window.__tg__postColorsChange, 500);
        });
        window.__tg__listenColors = () => {
            [
                document,
                document.body,
                ...document.querySelectorAll('meta[name="tg:theme-accent"]'),
                ...document.querySelectorAll('meta[name="tg:theme-background"]'),
                ...document.querySelectorAll('meta[name="theme-color"]'),
                ...document.querySelectorAll('meta[name="theme-background-color"]')
            ].filter(e => !!e).map(e => __tg__colorsObserver.observe(e, { attributes: true }));
            if (window.matchMedia) {
                window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => window.__tg__postColorsChange());
                window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => window.__tg__postColorsChange());
            }
        };
        window.__tg__listenColors();
        window.addEventListener('ready', __tg__listenColors, true);
        window.__tg__postColorsChange();
    })();
};

setTimeout(function () {
    const site_name = (
        (document.querySelector('meta[property="og:site_name"]') || {}).content ||
        (document.querySelector('meta[property="og:title"]') || {}).content
    );
    if (window.TelegramWebview && window.TelegramWebview.post) {
        if (site_name) {
            window.TelegramWebview.post('siteName', site_name);
        } else {
            window.TelegramWebview.post('siteNameEmpty');
        }
    }
    if (window.__tg__listenColors) {
        window.__tg__listenColors();
    }
    if (window.__tg__postColorsChange) {
        window.__tg__postColorsChange();
    }
}, 10);