#!/usr/bin/env python3
"""Sends a fresh, randomly generated science discussion to a Telegram group (one bot, reply chains, some charts).
Env: BOT_TOKEN, CHAT_ID; optional MSG_MIN / MSG_MAX (default 25..55), SEED.
Telegram allows ~20 messages/min per group, so the script paces itself and honours 429 retry_after.
Only stdlib; charts need matplotlib (skipped if missing)."""
import os, sys, json, time, uuid, random, io, urllib.request, urllib.error

TOKEN = os.environ["BOT_TOKEN"]
CHAT = os.environ["CHAT_ID"]
LO, HI = int(os.environ.get("MSG_MIN") or 25), int(os.environ.get("MSG_MAX") or 55)
rnd = random.Random(os.environ.get("SEED") or time.time_ns())

JOURNALS = ["Nature", "Science", "Cell", "PNAS", "Nature Physics", "The Lancet", "Physical Review Letters", "eLife", "Nature Neuroscience", "Science Advances"]
FINDINGS = [
 "logical qubits keep their coherence {x}x longer with the new surface-code decoder",
 "a base editor corrects the target allele in {p}% of liver cells with no detectable off-target edits",
 "deep sleep spindles predict next-day recall (r={r}) in a cohort of {n} adults",
 "a perovskite-silicon tandem cell reaches {e}% efficiency and holds it for 1,000 hours",
 "gut microbiome diversity drops {p}% in the weeks before a depressive episode",
 "JWST spectra show methane and CO2 in the atmosphere of a temperate sub-Neptune",
 "a solid-state battery cycles {n} times with under 5% capacity loss",
 "mRNA vaccines against a pan-flu antigen protect mice for {n} days",
 "a room-temperature superconductivity claim fails replication in {n} independent labs",
 "an AI model predicts protein-ligand binding within {e} kcal/mol on unseen targets",
 "ancient DNA from {n} individuals rewrites the timeline of early farming in Anatolia",
 "tardigrade proteins protect human cells from radiation damage by {p}%",
 "a neural implant restores {n} words per minute of speech in a paralysed patient",
 "microplastics found in {p}% of sampled placentas, linked to lower birth weight",
]
POST_FMT = ["📄 New in {j}: {f}.", "🧪 {j} just published: {f}.", "🔬 Worth reading ({j}): {f}.", "📰 {j}: {f}. Preprint was out last spring.", "🚨 {j}: {f}."]
COMMENT = [
 "Sample size looks small to me.", "The effect size is bigger than I expected.", "Did they pre-register this?",
 "Reminds me of the 2019 result that never replicated.", "The supplementary figure 3 is the real story.",
 "Funding disclosure is interesting here.", "Great, but what about long-term follow-up?",
 "I'd wait for an independent replication.", "The methods section is unusually clear.",
 "That control group choice is questionable.", "This could change the clinical guidelines.",
 "Peer review took only {n} days, impressive.", "The error bars overlap more than the abstract suggests.",
 "Agreed, and the code is public, which helps.", "Not sure the p-value tells the whole story.",
 "Cool, but the cost per sample is still huge.", "Exactly what I argued last month.", "Counterpoint: the mechanism isn't shown.",
]
QUESTION = [
 "Does anyone have access to the full text?", "How does this compare with the previous best result?",
 "Is the dataset open?", "What would an independent test look like?", "Which lab is behind this?",
 "Could this scale beyond the lab?", "Any idea about the cost?", "What was the follow-up period?",
]
ANSWER = [
 "Yes, the data is on Zenodo, DOI in the last paragraph.", "About {p}% better than the earlier benchmark.",
 "It's from a consortium of {n} labs, the corresponding author is in the first affiliation.",
 "They say follow-up was {n} weeks, so it's short.", "Roughly {p}x more expensive than the standard method.",
 "A blinded replication with {n} samples would settle it.", "Open access, the link is in the post.",
 "Scaling is the open problem, the authors admit it themselves.",
]

def fill(t):
    return (t.replace("{x}", str(rnd.randint(2, 9))).replace("{p}", str(rnd.randint(12, 94)))
             .replace("{r}", "0.%d" % rnd.randint(31, 68)).replace("{n}", str(rnd.choice([12, 40, 96, 150, 320, 1200, 5000])))
             .replace("{e}", "%.1f" % rnd.uniform(0.6, 31)).replace("{j}", rnd.choice(JOURNALS)))

def api(method, data=None, files=None):
    global CHAT
    for attempt in range(8):
        data = dict(data or {})
        data["chat_id"] = CHAT
        url = f"https://api.telegram.org/bot{TOKEN}/{method}"
        if files:
            b = uuid.uuid4().hex
            body = b""
            for k, v in data.items():
                body += f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
            for k, (fn, content) in files.items():
                body += (f'--{b}\r\nContent-Disposition: form-data; name="{k}"; filename="{fn}"\r\nContent-Type: image/png\r\n\r\n').encode() + content + b"\r\n"
            body += f"--{b}--\r\n".encode()
            req = urllib.request.Request(url, body, {"Content-Type": f"multipart/form-data; boundary={b}"})
        else:
            req = urllib.request.Request(url, json.dumps(data).encode(), {"Content-Type": "application/json"})
        try:
            return json.load(urllib.request.urlopen(req, timeout=60))["result"]
        except urllib.error.HTTPError as e:
            info = json.loads(e.read().decode() or "{}")
            params = info.get("parameters", {})
            if "migrate_to_chat_id" in params:
                CHAT = str(params["migrate_to_chat_id"])
                print("chat migrated to", CHAT)
                continue
            if e.code == 429:
                wait = int(params.get("retry_after", 10)) + 1
                print("rate limited, sleeping", wait)
                time.sleep(wait)
                continue
            sys.exit(f"Telegram error {e.code}: {info}")
    sys.exit("too many retries")

def chart():
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt, numpy as np
    except Exception:
        return None
    fig, ax = plt.subplots(figsize=(6, 3.6), dpi=120)
    x = np.linspace(0, 10, 120)
    kind = rnd.choice(["line", "bar", "scatter"])
    colors = ["#4c8bf5", "#f5a14c", "#5cc98a", "#8a6cf0"]
    if kind == "line":
        for i in range(rnd.randint(1, 3)):
            ax.plot(x, np.exp(-x / rnd.uniform(2, 7)) * np.cos(rnd.uniform(1, 4) * x), colors[i])
        ax.set_title("Signal decay"); ax.set_xlabel("time (a.u.)")
    elif kind == "bar":
        k = rnd.randint(3, 6)
        ax.bar([chr(65 + i) for i in range(k)], [rnd.randint(20, 95) for _ in range(k)], color=colors[0])
        ax.set_title("Effect by condition, %")
    else:
        g = np.random.RandomState(rnd.randint(0, 9999))
        ax.scatter(g.randn(140), g.randn(140) * 0.8 + 0.4 * g.randn(140), s=10, c=colors[3])
        ax.set_title("Measured vs predicted")
    fig.tight_layout()
    buf = io.BytesIO(); fig.savefig(buf, format="png"); plt.close(fig)
    return buf.getvalue()

def main():
    n = rnd.randint(LO, HI)
    sent = []   # (message_id, kind)
    print("sending", n, "messages")
    for i in range(n):
        kind = "post" if not sent or rnd.random() < 0.14 else rnd.choice(["comment", "comment", "comment", "question", "answer"])
        reply = None
        if kind == "post":
            text = fill(rnd.choice(POST_FMT).replace("{f}", rnd.choice(FINDINGS)))
        elif kind == "question":
            text = fill(rnd.choice(QUESTION)); reply = sent[-1][0] if rnd.random() < .6 else rnd.choice(sent)[0]
        elif kind == "answer":
            qs = [m for m in sent if m[1] == "question"]
            if not qs:
                kind = "comment"; text = fill(rnd.choice(COMMENT)); reply = sent[-1][0]
            else:
                text = fill(rnd.choice(ANSWER)); reply = qs[-1][0]
        else:
            text = fill(rnd.choice(COMMENT)); reply = sent[-1][0] if rnd.random() < .55 else rnd.choice(sent)[0]
        extra = {"reply_to_message_id": reply} if reply else {}
        img = chart() if kind == "comment" and rnd.random() < 0.08 else None
        if img:
            m = api("sendPhoto", {"caption": text, **extra}, {"photo": ("chart.png", img)})
        else:
            m = api("sendMessage", {"text": text, **extra})
        sent.append((m["message_id"], kind))
        print(i + 1, "/", n, kind)
        time.sleep(3.2)   # stay under 20 messages per minute
    print("done")

main()
