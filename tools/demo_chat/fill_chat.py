#!/usr/bin/env python3
"""Fills a Telegram group with a scripted science discussion (long reply chains + charts).
Usage: BOT_TOKEN=123:abc CHAT_ID=-100123456 python3 fill_chat.py [BOT_TOKEN_2]
Optional 2nd token (env BOT_TOKEN2): a second bot speaks as a second person so it looks like two people.
Needs: python3 + matplotlib (pip install matplotlib). No other deps."""
import os, sys, json, time, uuid, urllib.request, urllib.error

T1 = os.environ["BOT_TOKEN"]; T2 = os.environ.get("BOT_TOKEN2", T1); CHAT = os.environ["CHAT_ID"]

def api(tok, method, data=None, files=None):
    url = f"https://api.telegram.org/bot{tok}/{method}"
    if files:
        b = uuid.uuid4().hex; body = b""
        for k, v in (data or {}).items():
            body += f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
        for k, (fn, content) in files.items():
            body += f'--{b}\r\nContent-Disposition: form-data; name="{k}"; filename="{fn}"\r\nContent-Type: image/png\r\n\r\n'.encode() + content + b"\r\n"
        body += f"--{b}--\r\n".encode()
        req = urllib.request.Request(url, body, {"Content-Type": f"multipart/form-data; boundary={b}"})
    else:
        req = urllib.request.Request(url, json.dumps(data).encode(), {"Content-Type": "application/json"})
    try:
        r = json.load(urllib.request.urlopen(req))
    except urllib.error.HTTPError as e:
        sys.exit(f"Telegram error {e.code}: {e.read().decode()}")
    time.sleep(1.1)
    return r["result"]

def chart(kind):
    import matplotlib; matplotlib.use("Agg")
    import matplotlib.pyplot as plt, numpy as np, io
    fig, ax = plt.subplots(figsize=(6, 3.6), dpi=130)
    x = np.linspace(0, 10, 200)
    if kind == "qubit":
        for i, c in enumerate(["#4c8bf5", "#f5a14c", "#5cc98a"]):
            ax.plot(x, np.exp(-x / (2 + 2 * i)) * np.cos(3 * x), c, label=f"T2 = {2+2*i} µs")
        ax.set_title("Qubit coherence"); ax.set_xlabel("time, µs"); ax.legend()
    elif kind == "crispr":
        ax.bar(["Cas9", "Cas12", "base", "prime"], [62, 71, 88, 79], color="#4c8bf5")
        ax.set_title("Editing efficiency, %")
    else:
        ax.scatter(np.random.RandomState(1).randn(150) + x[:150] / 5, np.random.RandomState(2).randn(150), s=12, c="#8a6cf0")
        ax.set_title("Sleep vs memory correlation (n=150)")
    fig.tight_layout(); buf = io.BytesIO(); fig.savefig(buf, format="png"); plt.close(fig)
    return buf.getvalue()

# (key, bot, reply_to_key, text | ("photo", kind, caption))
S = [
 ("a", 1, None, "📄 New paper in Nature: logical qubits with error correction below threshold. https://www.nature.com/articles/s41586-024-08449-y"),
 ("b", 2, "a", "Below threshold is the key point. Before, adding more qubits only made things worse."),
 ("c", 1, "b", "Yes, going from code distance d=3 to 5 to 7 the error rate halves at every step."),
 ("d", 2, "c", ("photo", "qubit", "Here is what coherence looks like for different T2 values")),
 ("e", 1, "d", "The orange curve is the interesting one: slower decay, but higher noise."),
 ("f", 2, "e", "That's a known trade-off. The authors discuss it in the Methods section."),
 ("g", 1, "f", "Found it: the 'Decoder latency' section, real-time decoding at 1.1 µs."),
 ("h", 2, "g", "1.1 µs with a 1.1 µs cycle means the decoder keeps up in real time. That's big."),
 ("i", 1, None, "🧬 Different topic: base editing vs prime editing, a fresh review in Science. https://www.science.org/journal/science"),
 ("j", 2, "i", ("photo", "crispr", "Efficiency summary from the review")),
 ("k", 1, "j", "Base editing leads on efficiency, but prime editing is more precise on off-targets."),
 ("l", 2, "k", "Prime off-target rates are an order of magnitude lower, if the supplementary data is right."),
 ("m", 1, "l", "For therapy, precision matters more. The first clinical trials are on prime editing."),
 ("n", 2, "m", "Agreed. What about delivery? Is LNP still limited to the liver?"),
 ("o", 1, "n", "Yes, the liver is still the main target. There is work on lung-selective LNPs."),
 ("p", 1, None, "😴 And a bit on sleep: deep sleep and memory consolidation."),
 ("q", 2, "p", ("photo", "sleep", "Data from a meta-analysis, 150 participants")),
 ("r", 1, "q", "Moderate correlation, r≈0.4. But the direction of causality is unclear."),
 ("s", 2, "r", "Acoustic slow-wave stimulation experiments show causality more clearly."),
 ("t", 1, "s", "Right, and the effect lasts a day. I'll post more tomorrow."),
 ("u", 2, "a", "Back to the first paper: has anyone looked at the simulation code? Is it open?"),
 ("v", 1, "u", "It's open, on Zenodo. The DOI is at the end of the paper."),
 ("w", 2, "v", "Great, I'll post a reproduction this weekend."),
]

ids = {}
for key, bot, rep, content in S:
    tok = T1 if bot == 1 else T2
    extra = {"reply_to_message_id": ids[rep]} if rep else {}
    if isinstance(content, tuple):
        m = api(tok, "sendPhoto", {"chat_id": CHAT, "caption": content[2], **extra}, {"photo": (content[1] + ".png", chart(content[1]))})
    else:
        m = api(tok, "sendMessage", {"chat_id": CHAT, "text": content, **extra})
    ids[key] = m["message_id"]; print(key, ids[key])
print("done")
