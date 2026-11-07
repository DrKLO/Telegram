# Prints bench reports (reports/android-bench/<name>.json) as text; `--table` prints one row per report
# (the table in docs/android-renderer-bench.md). Usage: python3 -I scripts/android-bench-print.py [--table] <name>...
import json, sys, statistics as st, re
def renderer_pss_mb(name):
    # dumpsys lists the process under "Total RSS by process" and "Total PSS by process"; PSS is the smaller.
    vals = [int(m.group(1).replace(',', '')) // 1024 for line in open('reports/android-bench/%s.meminfo.txt' % name)
            if (m := re.match(r'^ +([0-9,]+)K: com.google.android.webview:sandboxed', line))]
    return min(vals) if vals else 0
if sys.argv[1:2] == ['--table']:
    def med(ws, k):
        xs = [w[k] for w in ws[1:] if w.get(k) is not None]
        return '%3.0f/%3.0f' % (st.median(xs), sorted(xs)[max(0, int(len(xs) * 0.9) - 1)]) if xs else '  -  '
    print('%-24s %9s %8s | %-7s %-7s %-7s %-7s | %5s %5s %5s | %4s %5s %3s' % ('run', 'w1 create', 'w1 cont', 'create', 'ready', 'content', 'loaded', 'app', 'rend', 'destr', 'late', 'worst', 'frz'))
    for n in sys.argv[2:]:
        r = json.load(open('reports/android-bench/%s.json' % n)); ws = r['widgets']; ph = {p['label']: p for p in r['phases']}
        print('%-24s %9.0f %8.0f | %-7s %-7s %-7s %-7s | %5d %5d %5d | %4.0f %5.0f %3d' % (n, ws[0]['createMs'], ws[0]['firstContentMs'] or ws[0]['loadedMs'] or 0, med(ws, 'createMs'), med(ws, 'rendererReadyMs'), med(ws, 'firstContentMs'), med(ws, 'loadedMs'), ph['live']['totalPssKb'] // 1024, renderer_pss_mb(n), ph['destroyed']['totalPssKb'] // 1024, r['createFrames']['lateMs'], r['createFrames']['worstFrameMs'], r['createFrames']['freezes']))
    sys.exit(0)
def ms(v): return '-' if v is None else '%.0f' % v
for name in sys.argv[1:]:
    r = json.load(open('reports/android-bench/%s.json' % name))
    print('==', name, r['device'], 'prewarmConstructorMs', ms(r.get('prewarmConstructorMs')))
    for w in r['widgets']:
        print('%2d %-30s create %4s ready %4s content %4s loaded %5s worst %4.0f late %4.0f %s%s' % (w['i'], w['file'][:30], ms(w['createMs']), ms(w['rendererReadyMs']), ms(w['firstContentMs']), ms(w['loadedMs']), w['frames']['worstFrameMs'], w['frames']['lateMs'], 'DEAD ' if w['dead'] else '', ('err%d' % w['errors']) if w['errors'] else ''))
    ws = r['widgets']
    def med(k, sub=None):
        xs = [w[k] for w in ws[1:] if w.get(k) is not None] if sub is None else [w[k] for w in ws[1:] if w.get(k) is not None]
        return '%.0f / p90 %.0f' % (st.median(xs), sorted(xs)[max(0, int(len(xs) * 0.9) - 1)]) if xs else '-'
    print('first widget: create %s ready %s content %s loaded %s' % (ms(ws[0]['createMs']), ms(ws[0]['rendererReadyMs']), ms(ws[0]['firstContentMs']), ms(ws[0]['loadedMs'])))
    print('widgets 2..N median: create %s | ready %s | content %s | loaded %s' % (med('createMs'), med('rendererReadyMs'), med('firstContentMs'), med('loadedMs')))
    for p in r['phases']:
        print('%-10s pss %4d MB  java %3d native %3d code %3d graphics %3d other %3d' % (p['label'], p['totalPssKb'] // 1024, p.get('java_heapKb', 0) // 1024, p.get('native_heapKb', 0) // 1024, p.get('codeKb', 0) // 1024, p.get('graphicsKb', 0) // 1024, p.get('private_otherKb', 0) // 1024))
    print('createFrames', r['createFrames'], 'destroyAllMs %.0f' % r['destroyAllMs'])
