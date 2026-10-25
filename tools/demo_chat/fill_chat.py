#!/usr/bin/env python3
"""Fills a Telegram group with a scripted science discussion (long reply chains + charts).
Usage: BOT_TOKEN=123:abc CHAT_ID=-100123456 python3 fill_chat.py [BOT_TOKEN_2]
Optional 2nd token (env BOT_TOKEN2): a second bot speaks as "reviewer" so it looks like two people.
Needs: python3 + matplotlib (pip install matplotlib). No other deps."""
import os, sys, json, time, uuid, urllib.request

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
    r = json.load(urllib.request.urlopen(req)); time.sleep(1.1)
    return r["result"]

def chart(kind):
    import matplotlib; matplotlib.use("Agg")
    import matplotlib.pyplot as plt, numpy as np, io
    fig, ax = plt.subplots(figsize=(6, 3.6), dpi=130)
    x = np.linspace(0, 10, 200)
    if kind == "qubit":
        for i, c in enumerate(["#4c8bf5", "#f5a14c", "#5cc98a"]):
            ax.plot(x, np.exp(-x / (2 + 2 * i)) * np.cos(3 * x), c, label=f"T2 = {2+2*i} мкс")
        ax.set_title("Когерентность кубита"); ax.set_xlabel("время, мкс"); ax.legend()
    elif kind == "crispr":
        ax.bar(["Cas9", "Cas12", "base", "prime"], [62, 71, 88, 79], color="#4c8bf5")
        ax.set_title("Эффективность редактирования, %")
    else:
        ax.scatter(np.random.RandomState(1).randn(150) + x[:150] / 5, np.random.RandomState(2).randn(150), s=12, c="#8a6cf0")
        ax.set_title("Корреляция сна и памяти (n=150)")
    fig.tight_layout(); buf = io.BytesIO(); fig.savefig(buf, format="png"); plt.close(fig)
    return buf.getvalue()

# (key, bot, reply_to_key, text | ("photo", kind, caption))
S = [
 ("a", 1, None, "📄 Новая статья в Nature: логические кубиты с коррекцией ошибок ниже порога. https://www.nature.com/articles/s41586-024-08449-y"),
 ("b", 2, "a", "Ниже порога — это ключевое. Раньше добавление кубитов только ухудшало ситуацию."),
 ("c", 1, "b", "Да, при увеличении кода d=3→5→7 ошибка падает вдвое на каждом шаге."),
 ("d", 2, "c", ("photo", "qubit", "Вот как выглядит когерентность при разных T2")),
 ("e", 1, "d", "Кривая жёлтая — самая интересная: затухание медленнее, но шум выше."),
 ("f", 2, "e", "Это известный компромисс. Авторы пишут об этом в разделе Methods."),
 ("g", 1, "f", "Нашёл: раздел «Decoder latency», там реальное время декодирования 1.1 мкс."),
 ("h", 2, "g", "1.1 мкс при цикле 1.1 мкс — значит декодер в реальном времени. Это сильно."),
 ("i", 1, None, "🧬 Другая тема: base editing против prime editing, свежий обзор в Science. https://www.science.org/journal/science"),
 ("j", 2, "i", ("photo", "crispr", "Сводка по эффективности из обзора")),
 ("k", 1, "j", "Base editing лидирует по эффективности, но prime точнее по off-target."),
 ("l", 2, "k", "Off-target у prime меньше на порядок, если верить supplementary."),
 ("m", 1, "l", "Для терапии точность важнее. Первые клинические испытания как раз на prime."),
 ("n", 2, "m", "Согласен. А что с доставкой? LNP всё ещё упирается в печень?"),
 ("o", 1, "n", "Да, печень пока основная мишень. Есть работы по селективным LNP для лёгких."),
 ("p", 1, None, "😴 И немного про сон: связь глубокого сна и консолидации памяти."),
 ("q", 2, "p", ("photo", "sleep", "Данные из метаанализа, 150 участников")),
 ("r", 1, "q", "Корреляция умеренная, r≈0.4. Но направление причинности неясно."),
 ("s", 2, "r", "Есть эксперименты с акустической стимуляцией медленных волн — там причинность видна."),
 ("t", 1, "s", "Да, и эффект держится сутки. Расскажу подробнее завтра."),
 ("u", 2, "a", "Возвращаясь к первой статье: кто-нибудь смотрел код симуляций? Он открыт?"),
 ("v", 1, "u", "Открыт, на Zenodo. DOI в конце статьи."),
 ("w", 2, "v", "Супер, выложу воспроизведение на выходных."),
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
