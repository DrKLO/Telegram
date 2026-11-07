// Tests for the desktop core: the XCTest suite of ios/Tests/WidgetSandboxTests
// ported one for one (engine boot, network stubs, CPU / memory kills, layout
// query round trip, pre-boot buffering, timer cap, CPU quota, off-screen pause,
// bridge, pinned-thread recursion, validator diagnostics and sanitising,
// create/teardown churn) plus the shared CpuBudget, SDK v1 and gesture policy fixtures,
// SDK v1 through the real engine and WidgetHost, and the JSON helper.
// No test framework dependency: `twidget-tests [filter]`, exit code = failures.

#include "../harness/recording_host.h"
#include "twidget/bundle_stamp.h"
#include "twidget/cpu_budget.h"
#include "twidget/gesture_policy.h"
#ifdef _WIN32
#include "twidget/icu_intl.h"
#endif
#include "twidget/intl.h"
#include "twidget/json.h"
#include "twidget/sdk_policy.h"
#ifdef TW_HAVE_BLITZ
#include "twidget/blitz_renderer.h"
#endif
#ifdef TW_HAVE_LITEHTML
#include "twidget/litehtml_renderer.h"
#endif
#include "twidget/widget_host.h"

#include <algorithm>
#include <chrono>
#include <clocale>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <future>
#include <iterator>
#include <optional>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

using namespace twidget;

static int g_failures = 0;
static int g_checks = 0;
#define CHECK(cond) do { g_checks++; if (!(cond)) { g_failures++; fprintf(stderr, "  FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); } } while (0)
#define CHECK_MSG(cond, msg) do { g_checks++; if (!(cond)) { g_failures++; fprintf(stderr, "  FAIL %s:%d: %s — %s\n", __FILE__, __LINE__, #cond, std::string(msg).c_str()); } } while (0)

struct TestCase { const char* name; void (*fn)(); };
static std::vector<TestCase>& registry() { static std::vector<TestCase> r; return r; }
#define TEST(name) static void name(); static const bool name##_registered = (registry().push_back({#name, name}), true); static void name()

static std::shared_ptr<SandboxResources> RES;
static const std::string CANARY = "http://127.0.0.1:9999/hit/desktop-unit";

static std::string readFile(const std::string& p) {
    std::ifstream in(p, std::ios::binary);
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

static void sleepMs(int ms) { std::this_thread::sleep_for(std::chrono::milliseconds(ms)); }

static SandboxConfig testConfig() {
    SandboxConfig c;
    c.cpuLimitMs = 2000;
    c.innerWidth = 400;
    c.innerHeight = 300;
    c.devicePixelRatio = 2;
    c.theme = "light";
    return c;
}

/// One engine with a recording host; the host outlives the engine (declaration order).
struct Run {
    RecordingHost host;
    SandboxEngine engine;
    Run(const std::string& html, SandboxConfig cfg = testConfig(), bool start = true) : host("light", RES, false, cfg.policy), engine(html, cfg, RES) {
        engine.setDelegate(&host);
        if (start) { engine.start(); host.waitLoaded(20); }
    }
    /// Like `engine.queue.sync {}` on iOS: everything queued so far has run.
    void barrier() { engine.thread().sync([] {}); }
    std::string logsText() { std::string s; for (auto& l : host.logs) s += "[" + l.first + "] " + l.second + "\n"; return s; }
    std::string diagsText() { std::string s; for (auto& d : host.diags) s += d.first + ": " + d.second + "\n"; return s; }
};

// ───────────── helpers ─────────────

TEST(json_fuzz_never_crashes) {
    uint64_t state = 0x9E3779B97F4A7C15ull;
    auto next = [&]() { state = state * 6364136223846793005ull + 1442695040888963407ull; return uint32_t(state >> 33); };
    const std::string seeds[] = {
        "{\"type\":\"result\",\"qid\":1,\"result\":{\"w\":[1,2.5e300,-0.0,\"\\ud83d\\ude00\"]}}",
        "[\"\\u0000\",{\"a\":{\"b\":[null,true,false]}},-1e-400,1E+999]",
        "{\"ops\":[{\"op\":\"el\",\"id\":2,\"tag\":\"div\",\"attrs\":{\"class\":\"x\"}}]}",
    };
    const char alphabet[] = "{}[]\":,.-+eE0123456789truefalsn \\u/abcdef\x00\x80\xff";
    int parsed = 0;
    for (int t = 0; t < 60000; t++) {
        std::string text;
        if (t % 2 == 0) {
            text = seeds[next() % 3];
            int edits = 1 + int(next() % 6);
            for (int e = 0; e < edits && !text.empty(); e++) {
                size_t at = next() % text.size();
                switch (next() % 4) {
                case 0: text[at] = alphabet[next() % (sizeof(alphabet) - 1)]; break;
                case 1: text.erase(at, 1 + next() % 4); break;
                case 2: text.insert(at, 1, alphabet[next() % (sizeof(alphabet) - 1)]); break;
                default: text.resize(at); break;
                }
            }
        } else {
            size_t n = next() % 48;
            for (size_t k = 0; k < n; k++) text.push_back(alphabet[next() % (sizeof(alphabet) - 1)]);
        }
        auto v = Json::parse(text);
        if (v) {
            parsed++;
            std::string dumped = v->dump();
            auto again = Json::parse(dumped);
            CHECK(again.has_value());
            if (again) CHECK(again->dump() == dumped);
        }
    }
    CHECK(parsed > 0);
    CHECK(!Json::parse(std::string(200000, '[')).has_value());
    CHECK(!Json::parse(std::string("\"\\ud800")).has_value() || true);
}

TEST(json_parse_and_dump) {
    auto v = Json::parse("{\"a\":[1,2.5,\"x\\u00e9\\ud83d\\ude00\",true,null],\"b\":{}, \"c\":-1e2}");
    CHECK(v.has_value());
    CHECK(v->dump() == "{\"a\":[1,2.5,\"xé\U0001F600\",true,null],\"b\":{},\"c\":-100}");
    CHECK((*v)["a"][2].asString() == "xé\U0001F600");
    CHECK((*v)["c"].asNumber() == -100);
    CHECK(!Json::parse("[1,]"));
    CHECK(!Json::parse("{\"a\":1} x"));
    CHECK(!Json::parse("{a:1}"));
    CHECK(!Json::parse(std::string(300, '[')));
    CHECK(Json::parse(std::string(100, '[') + std::string(100, ']')).has_value());
    CHECK(Json::number(1234.5) == "1234.5");
    CHECK(Json::number(3) == "3");
    CHECK(Json::number(1e21) == "1e+21");
    // Shortest round trip on every target (Apple below macOS 13.3 has no floating to_chars).
    CHECK(Json::number(0.1) == "0.1");
    CHECK(Json::number(16.7) == "16.7");
    CHECK(Json::number(-0.25) == "-0.25");
    for (double d : {1.0 / 3, 2.0 / 3, 0.1 + 0.2, 1e-7, 123456.789e10, 5e-324, 1.7976931348623157e308}) {
        auto text = Json::number(d);
        CHECK_MSG(Json::parse(text)->asNumber() == d, text);
        CHECK_MSG(text.size() <= 24, text);
    }
    CHECK(Json::quote("a\"b\\c\n\x01") == "\"a\\\"b\\\\c\\n\\u0001\"");
    Json o = Json::object();
    o.set("k", "v").set("n", 2);
    CHECK(o.dump() == "{\"k\":\"v\",\"n\":2}");
}

/// Numbers parse and print the same under a locale whose decimal separator is a
/// comma (Qt hosts call setlocale from the environment).
TEST(json_numbers_ignore_lc_numeric) {
    std::string saved = setlocale(LC_NUMERIC, nullptr);
    const char* comma = nullptr;
    for (const char* name : {"ru_RU.UTF-8", "ru_RU.utf8", "de_DE.UTF-8", "de_DE.utf8", "fr_FR.UTF-8"}) {
        if (setlocale(LC_NUMERIC, name)) { comma = name; break; }
    }
    if (!comma) { fprintf(stderr, "  (no comma-decimal locale installed; skipped)\n"); return; }
    CHECK((*Json::parse("{\"h\":16.5}"))["h"].asNumber() == 16.5);
    CHECK(Json::parse("[1e-3,2.25E2]")->dump() == "[0.001,225]");
    CHECK(Json::number(16.5) == "16.5");
    CHECK(Json::number(0.1) == "0.1");
    setlocale(LC_NUMERIC, saved.c_str());
}

/// CpuBudget against the fixture shared with cpu-budget.ts, CpuBudget.swift and CpuBudget.kt (the JS quota and the
/// main-thread window are two budgets of the same policy; opsCostMs is the window's model of the ops).
TEST(cpu_budget_shared_fixture) {
    auto root = Json::parse(readFile(std::string(TW_FIXTURES) + "/cpu-budget.json"));
    CHECK_MSG(root.has_value(), "fixture missing — run pnpm build:desktop");
    if (!root) return;
    const auto& cases = (*root)["cases"].arr();
    CHECK(cases.size() > 5);
    int costSteps = 0;
    for (const auto& c : cases) {
        const std::string& name = c["name"].asString();
        const Json& cfg = c["config"];
        CpuBudget b(cfg["cpuLimitMs"].asNumber(), cfg["cpuQuotaMs"].asNumber(), cfg["cpuQuotaWindowMs"].asNumber(), cfg["cpuKillAfterMs"].asNumber());
        int i = 0;
        for (const auto& step : c["steps"].arr()) {
            double at = step["at"].asNumber();
            std::string where = name + ": step " + std::to_string(i++) + " @" + std::to_string(int(at));
            if (const Json* kind = step.find("decide")) {
                const Json& expect = step["expect"];
                CpuDecision want = expect["run"].asBool() ? CpuDecision{true, expect["sliceMs"].asNumber(), 0} : CpuDecision{false, 0, expect["retryAt"].asNumber()};
                CpuDecision got = b.decide(kind->asString(), at);
                CHECK_MSG(got == want, where + " got run=" + std::to_string(got.run) + " slice=" + std::to_string(got.sliceMs) + " retryAt=" + std::to_string(got.retryAt));
            } else if (const Json* kind = step.find("record")) {
                b.record(kind->asString(), step["cpuMs"].asNumber(), at);
            } else if (const Json* kill = step.find("kill")) {
                CHECK_MSG(b.shouldKill(at) == kill->asBool(), where);
            } else if (const Json* used = step.find("used")) {
                double got = b.used(at);
                CHECK_MSG(std::fabs(got - used->asNumber()) < 1e-9, where + " used " + std::to_string(got));
            } else if (const Json* q = step.find("queries")) {
                std::string want = step["expect"].isNull() ? "" : step["expect"].asString();
                CHECK_MSG(std::string(CpuBudget::queryLimitHit(int((*q)[0].asNumber()), (*q)[1].asNumber())) == want, where);
            } else if (const Json* cost = step.find("opsCost")) {
                costSteps++;
                double got = CpuBudget::opsCostMs((*cost)[0].asNumber(), (*cost)[1].asNumber());
                CHECK_MSG(std::fabs(got - step["expect"].asNumber()) < 1e-9, where + " got " + std::to_string(got));
            } else {
                CHECK_MSG(false, where + ": unknown step");
            }
        }
    }
    // The strict-limits fixture (the main-thread window) or later: a stale copy fails here.
    CHECK_MSG(costSteps >= 6, std::to_string(costSteps) + " opsCost steps — run pnpm build:desktop");
}

/// Deep equality of JSON values, object members in any order (what node:assert's deepEqual compares).
static bool jsonEqual(const Json& a, const Json& b) {
    if (a.type() != b.type()) return false;
    switch (a.type()) {
    case Json::Type::Null: return true;
    case Json::Type::Bool: return a.asBool() == b.asBool();
    case Json::Type::Number: return a.asNumber() == b.asNumber();
    case Json::Type::String: return a.asString() == b.asString();
    case Json::Type::Array:
        if (a.size() != b.size()) return false;
        for (size_t i = 0; i < a.size(); i++) if (!jsonEqual(a[i], b[i])) return false;
        return true;
    case Json::Type::Object:
        if (a.size() != b.size()) return false;
        for (const auto& [k, v] : a.obj()) { const Json* w = b.find(k); if (!w || !jsonEqual(v, *w)) return false; }
        return true;
    }
    return false;
}

/// `{ "repeat": [s, n] }` in the fixture stands for s repeated n times.
static Json expandRepeat(const Json& v) {
    if (v.isArray()) {
        Json out = Json::array();
        for (const auto& x : v.arr()) out.push(expandRepeat(x));
        return out;
    }
    if (v.isObject()) {
        if (v.size() == 1 && v["repeat"].isArray()) {
            std::string s;
            for (int i = 0; i < v["repeat"][1].asInt(); i++) s += v["repeat"][0].asString();
            return Json(s);
        }
        Json out = Json::object();
        for (const auto& [k, x] : v.obj()) out.set(k, expandRepeat(x));
        return out;
    }
    return v;
}

static Json orNull(const std::optional<std::string>& s) { return s ? Json(*s) : Json(); }

/// A fixture's data input as a native host passes it: each field a string, or none for null / absent.
static WidgetData widgetDataOf(const Json& input) {
    WidgetData d;
    if (input["chatData"].isString()) d.chatData = input["chatData"].asString();
    if (input["publicData"].isString()) d.publicData = input["publicData"].asString();
    return d;
}

/// sdk_policy against the fixture shared with sdk-policy.ts, SdkPolicy.swift and SdkPolicy.kt: every section.
TEST(sdk_policy_shared_fixture) {
    auto root = Json::parse(readFile(std::string(TW_FIXTURES) + "/sdk-policy.json"));
    CHECK_MSG(root.has_value(), "fixture missing — run pnpm build:desktop");
    if (!root) return;
    int cases = 0;
    for (const auto& c : (*root)["links"].arr()) {
        cases++;
        Json got = orNull(sdk::checkLinkValue(c["method"].asString(), c["url"]));
        CHECK_MSG(jsonEqual(got, c["expect"]), c["method"].asString() + " " + c["url"].dump().substr(0, 100) + " → " + got.dump().substr(0, 100));
    }
    for (const auto& c : (*root)["haptics"].arr()) {
        cases++;
        auto h = sdk::checkHaptic(c["args"]);
        Json got;
        if (h) { got = Json::object(); got.set("haptic", h->haptic); if (h->value) got.set("value", *h->value); }
        CHECK_MSG(jsonEqual(got, c["expect"]), c["args"].dump() + " → " + got.dump());
    }
    int anchors = 0, requests = 0, copies = 0, watches = 0, wallets = 0;
    for (const auto& c : (*root)["bridge"].arr()) {
        cases++;
        auto a = sdk::checkBridgeCall(c["method"], c["args"]);
        Json got = a ? a->toJson() : Json();
        CHECK_MSG(jsonEqual(got, c["expect"]), c["method"].dump() + " " + c["args"].dump() + " → " + got.dump());
        // Exactly one of the five: the action, setResizeAnchor's edge, the request, the text to copy, the watch flag.
        if (a) CHECK_MSG(int(a->action.has_value()) + int(a->resizeAnchor.has_value()) + int(a->request.has_value()) + int(a->copyText.has_value()) +
                                 int(a->watchPublicData.has_value()) + int(a->wallet.has_value()) == 1,
                         c["method"].dump());
        wallets += a && a->wallet;
        const std::string method = c["method"].isString() ? c["method"].asString() : "";
        anchors += method == "setResizeAnchor";
        requests += method == "request";
        copies += method == "copyText";
        watches += method == "watchPublicData";
    }
    // v0.1.3's fixture or later: a stale copy without the setResizeAnchor entries fails here; the bot's requests: request and copyText.
    CHECK_MSG(anchors >= 15, std::to_string(anchors) + " setResizeAnchor entries");
    CHECK_MSG(requests >= 19 && copies >= 12, std::to_string(requests) + " request, " + std::to_string(copies) + " copyText entries");
    CHECK_MSG(watches >= 6, std::to_string(watches) + " watchPublicData entries"); // round 7 or later
    int touchSteps = 0;
    for (const auto& c : (*root)["hapticGate"].arr()) {
        cases++;
        sdk::HapticGate gate;
        int i = 0;
        for (const auto& s : c["steps"].arr()) {
            // v0.2.2: {at, touch: "down" | "up"} is the host's press / release; else {at, visible, expect} is a decision.
            if (s["touch"].isString()) {
                touchSteps++;
                if (s["touch"].asString() == "down") gate.press(s["at"].asNumber());
                else gate.release(s["at"].asNumber());
            } else {
                std::string got(sdk::HapticGate::name(gate.decide(s["at"].asNumber(), s["visible"].asBool())));
                CHECK_MSG(got == s["expect"].asString(), c["name"].asString() + ", step " + std::to_string(i) + ": " + got);
            }
            i++;
        }
    }
    CHECK_MSG(touchSteps >= 6, std::to_string(touchSteps) + " touch steps"); // v0.2.2's fixture or later
    for (const auto& c : (*root)["themeParams"].arr()) {
        cases++;
        Json got = sdk::themeParamsJson(sdk::checkThemeParams(c["input"]));
        CHECK_MSG(jsonEqual(got, c["expect"]), c["input"].dump() + " → " + got.dump());
    }
    for (const auto& c : (*root)["locales"].arr()) {
        cases++;
        Json got = orNull(sdk::checkLocaleValue(c["input"]));
        CHECK_MSG(jsonEqual(got, c["expect"]), c["input"].dump() + " → " + got.dump());
    }
    for (const auto& c : (*root)["storage"].arr()) {
        cases++;
        auto commit = sdk::checkStorageCommit(expandRepeat(c["input"]));
        Json got = commit ? commit->toJson() : Json();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), c["note"].isString() ? c["note"].asString() : c["input"].dump());
    }
    for (const auto& c : (*root)["utf8"].arr()) {
        cases++;
        CHECK_MSG(sdk::utf8Length(c["s"].asString()) == size_t(c["bytes"].asInt()), c["s"].asString());
    }
    // Bot data and requests.
    for (const auto& c : (*root)["widgetData"].arr()) {
        cases++;
        Json got = sdk::checkWidgetData(widgetDataOf(expandRepeat(c["input"])), c["copy"].asBool()).toJson();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), (c["note"].isString() ? c["note"].asString() : c["input"].dump().substr(0, 80)) + " → " + got.dump().substr(0, 120));
    }
    int viewerShapes = 0;
    for (const auto& c : (*root)["viewer"].arr()) {
        cases++;
        // The input in the widget's field names, mapped onto the typed struct (round 6: Mini Apps' WebAppUser).
        const Json in = expandRepeat(c["input"]);
        std::optional<WidgetViewer> input;
        if (in["id"].isNumber()) {
            WidgetViewer v;
            v.id = int64_t(in["id"].asNumber());
            if (in["first_name"].isString()) v.firstName = in["first_name"].asString();
            if (in["last_name"].isString()) v.lastName = in["last_name"].asString();
            if (in["username"].isString()) v.username = in["username"].asString();
            v.isPremium = in["is_premium"].asBool();
            input = v;
        }
        Json got = sdk::checkViewer(input).toJson();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), (c["note"].isString() ? c["note"].asString() : in.dump().substr(0, 80)) + " → " + got.dump().substr(0, 160));
        viewerShapes += c["expect"].find("rejected") != nullptr;
    }
    // Round 6 or later: {viewer, rejected}; a stale copy fails here.
    CHECK_MSG(viewerShapes >= 20, std::to_string(viewerShapes) + " viewer cases with a rejected list");
    for (const auto& c : (*root)["chatType"].arr()) {
        cases++;
        Json got = orNull(sdk::checkChatType(c["input"].asString())); // null is "" (none) for a native host
        CHECK_MSG(jsonEqual(got, c["expect"]), c["input"].dump() + " → " + got.dump());
    }
    for (const auto& c : (*root)["answers"].arr()) {
        cases++;
        const Json input = expandRepeat(c["input"]);
        // The server's answer is only the result.
        WidgetRequestAnswer answer;
        if (input["result"].isString()) answer.result = input["result"].asString();
        std::optional<sdk::CheckedAnswer> checked = sdk::checkRequestAnswer(answer);
        Json got = checked ? checked->toJson() : Json();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), (c["note"].isString() ? c["note"].asString() : input.dump().substr(0, 80)) + " → " + got.dump().substr(0, 120));
    }
    for (const auto& c : (*root)["requestGate"].arr()) {
        cases++;
        sdk::RequestGate gate;
        int i = 0;
        for (const auto& s : c["steps"].arr()) {
            if (s["op"].asString() == "finish") gate.finish(s["answeredAt"].isNumber() ? std::optional<double>(s["answeredAt"].asNumber()) : std::nullopt);
            else if (s["op"].asString() == "decideWallet") {
                sdk::RequestGate::WalletConditions at{s["gesture"].asBool(), s["visible"].asBool(), s["canQuery"].asBool(), s["wallet"].asBool()};
                std::string got(sdk::requestVerdictName(gate.decideWallet(s["at"].asNumber(), at)));
                CHECK_MSG(got == s["expect"].asString(), c["name"].asString() + ", step " + std::to_string(i) + ": " + got);
            }
            else if (s["op"].asString() == "show") {
                bool busy = gate.busy();
                CHECK_MSG(gate.show(s["at"].asNumber()) == s["expect"].asBool(), c["name"].asString() + ", step " + std::to_string(i));
                CHECK_MSG(gate.busy() == busy, c["name"].asString() + ", step " + std::to_string(i) + ": show changes nothing in flight");
            } else {
                sdk::RequestGate::Conditions at{s["gesture"].asBool(), s["visible"].asBool(), s["canQuery"].asBool(), s["backgroundQuery"].asBool(), s["withWallet"].asBool(), s["wallet"].asBool()};
                std::string got(sdk::requestVerdictName(gate.decide(s["at"].asNumber(), at)));
                CHECK_MSG(got == s["expect"].asString(), c["name"].asString() + ", step " + std::to_string(i) + ": " + got);
            }
            i++;
        }
        CHECK_MSG(!c["steps"].arr().empty(), c["name"].asString());
    }
    std::vector<std::string> verdicts;
    for (const auto& c : (*root)["requestRefusals"].arr()) {
        cases++;
        sdk::RequestVerdict all[] = {sdk::RequestVerdict::Foreground, sdk::RequestVerdict::Background, sdk::RequestVerdict::Held, sdk::RequestVerdict::Granted,
                                     sdk::RequestVerdict::Disabled, sdk::RequestVerdict::NoWallet, sdk::RequestVerdict::Busy, sdk::RequestVerdict::NoActivation,
                                     sdk::RequestVerdict::RateLimited};
        const sdk::RequestVerdict* v = std::find_if(std::begin(all), std::end(all), [&](sdk::RequestVerdict x) { return sdk::requestVerdictName(x) == c["verdict"].asString(); });
        CHECK_MSG(v != std::end(all), c["verdict"].asString());
        if (v == std::end(all)) continue;
        verdicts.push_back(c["verdict"].asString());
        std::optional<std::string_view> refusal = sdk::requestRefusal(*v);
        Json got = refusal ? Json(*refusal) : Json();
        CHECK_MSG(jsonEqual(got, c["expect"]), c["verdict"].asString() + " → " + got.dump());
    }
    CHECK_MSG(verdicts.size() == 9, std::to_string(verdicts.size()) + " verdicts"); // every verdict once
    CHECK_MSG(wallets == 3, std::to_string(wallets) + " valid wallet calls");
    // The wallet (docs/sdk-v1.md §2.18): TON Connect's transaction and signData payload, checked.
    for (const auto& c : (*root)["walletTransactions"].arr()) {
        cases++;
        std::optional<WalletTransaction> tx = sdk::checkWalletTransaction(expandRepeat(c["input"]));
        Json got = tx ? tx->toJson() : Json();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), (c["note"].isString() ? c["note"].asString() : c["input"].dump().substr(0, 100)) + " → " + got.dump().substr(0, 120));
    }
    for (const auto& c : (*root)["signDataPayloads"].arr()) {
        cases++;
        std::optional<SignDataPayload> p = sdk::checkSignDataPayload(expandRepeat(c["input"]));
        Json got = p ? p->toJson() : Json();
        CHECK_MSG(jsonEqual(got, expandRepeat(c["expect"])), (c["note"].isString() ? c["note"].asString() : c["input"].dump().substr(0, 100)) + " → " + got.dump().substr(0, 120));
    }
    // The server's spec or later (the server's flags, answers of a result only, no NUL in data); a stale copy fails here.
    int flagged = 0, shows = 0, nulData = 0, edits = 0;
    for (const auto& c : (*root)["requestGate"].arr())
        for (const auto& st : c["steps"].arr()) { flagged += st.find("canQuery") != nullptr; shows += st["op"].asString() == "show"; }
    for (const auto& c : (*root)["widgetData"].arr()) nulData += c.find("copy") != nullptr && c["input"].dump().find("\\u0000") != std::string::npos;
    for (const auto& c : (*root)["answers"].arr()) edits += c["expect"].find("edit") != nullptr;
    CHECK_MSG(flagged >= 10 && shows >= 2 && nulData >= 1 && edits == 0,
              std::to_string(flagged) + " gate steps with canQuery, " + std::to_string(shows) + " show steps, " + std::to_string(nulData) + " NUL data cases, " + std::to_string(edits) + " answers with an edit");
    // Round 4 (previews): the methods a preview may call.
    for (const auto& c : (*root)["previewMethods"].arr()) {
        cases++;
        CHECK_MSG(sdk::allowedInPreview(c["method"].asString()) == c["allowed"].asBool(), c["method"].dump());
    }
    printf("    %d fixture cases\n", cases);
    CHECK(cases > 150);
    for (const char* section : {"links", "haptics", "bridge", "hapticGate", "themeParams", "locales", "storage", "utf8",
                                "widgetData", "viewer", "chatType", "answers", "requestGate", "requestRefusals", "previewMethods", "walletTransactions", "signDataPayloads"})
        CHECK_MSG((*root)[section].size() > 0, section);
}

/// What the fixture cannot carry: the strings of the bot's data and requests refuse a lone surrogate (its WTF-8 form, as QuickJS and the
/// engine's `Surrogates::Keep` parse give it) and invalid UTF-8 everywhere a string leaves the widget or enters it;
/// lengths are UTF-8 bytes for the data and UTF-16 code units for copyText; out-of-range ids and viewers are none.
TEST(sdk_request_strings_refuse_surrogates_and_invalid_utf8) {
    const std::string lone = "\xED\xA0\x80", loneLow = "\xED\xB0\x80", emoji = "\xF0\x9F\x98\x80";
    for (const std::string& bad : {"a" + lone, loneLow + "b", lone + loneLow /* CESU-8, not a pair in UTF-8 */, std::string("\xFF"), std::string("x\xC3"), std::string("\xC0\xAF")}) {
        CHECK_MSG(!sdk::checkRequestData(bad), bad);
        CHECK_MSG(!sdk::checkCopyText(bad), bad);
        CHECK_MSG(!sdk::checkWidgetDataValue(bad), bad);
        CHECK(!sdk::checkRequestAnswer(WidgetRequestAnswer{bad}));
        sdk::CheckedWidgetData d = sdk::checkWidgetData(WidgetData{bad, std::string("ok")}, false);
        CHECK(!d.chatData && d.publicData == std::string("ok") && d.rejected == std::vector<std::string>{"chatData"});
    }
    CHECK(sdk::checkRequestData(emoji) == emoji);
    CHECK(sdk::checkRequestData(std::string("a\0b", 3)).has_value()); // a NUL is a character like any other
    auto call = Json::parse("[\"request\",[1,\"a\\ud800\"]]", Json::Surrogates::Keep);
    CHECK(call && !sdk::checkBridgeCall((*call)[0], (*call)[1]));
    call = Json::parse("[\"copyText\",[\"\\udc00\"]]", Json::Surrogates::Keep);
    CHECK(call && !sdk::checkBridgeCall((*call)[0], (*call)[1]));
    // UTF-16 code units: an emoji is 2, so 128 fit and 129 do not; 256 Cyrillic letters (512 bytes) fit.
    CHECK(sdk::utf16Length(emoji) == 2 && sdk::utf16Length("\xD0\xB6") == 1 && sdk::utf16Length("a" + emoji + "\xE2\x82\xAC") == 4);
    std::string emojis, cyrillic;
    for (int i = 0; i < 128; i++) emojis += emoji;
    for (int i = 0; i < 256; i++) cyrillic += "\xD0\xB6";
    CHECK(sdk::checkCopyText(emojis).has_value() && !sdk::checkCopyText(emojis + "a"));
    CHECK(sdk::checkCopyText(cyrillic).has_value() && !sdk::checkCopyText(cyrillic + "a"));
    // Ids: integers 1 … 2^31 − 1, as numbers only.
    for (const char* args : {"[1e0,\"x\"]", "[2147483647.0,\"x\"]"}) CHECK_MSG(sdk::checkBridgeCall(Json("request"), *Json::parse(args)).has_value(), args);
    for (const char* args : {"[0.5,\"x\"]", "[1e10,\"x\"]", "[true,\"x\"]", "[null,\"x\"]", "[[1],\"x\"]", "[-0,\"x\"]"})
        CHECK_MSG(!sdk::checkBridgeCall(Json("request"), *Json::parse(args)), args);
    // Viewers outside 1 … 2^53 − 1.
    CHECK(!sdk::checkViewer(WidgetViewer{INT64_MIN}).viewer && !sdk::checkViewer(WidgetViewer{INT64_MAX}).viewer && !sdk::checkViewer(std::nullopt).viewer);
    CHECK(sdk::checkViewer(WidgetViewer{sdk::MAX_VIEWER_ID}).viewer->id == sdk::MAX_VIEWER_ID);
    // The viewer's names refuse WTF-8 surrogates and invalid UTF-8; the username is ASCII only (a Cyrillic letter is 2
    // bytes, so a byte count would not catch "аннаx" either way).
    for (const std::string& bad : {"A" + lone, std::string("\xFF"), std::string("x\xC3")}) {
        WidgetViewer v{7};
        v.firstName = bad;
        v.lastName = "Lee";
        sdk::CheckedViewer c = sdk::checkViewer(v);
        CHECK_MSG(c.viewer && !c.viewer->firstName && c.viewer->lastName == std::string("Lee") && c.rejected == std::vector<std::string>{"first_name"}, bad);
    }
    {
        WidgetViewer v{7};
        v.username = "\xD0\xB0nnax";
        v.isPremium = false;
        sdk::CheckedViewer c = sdk::checkViewer(v);
        CHECK(c.viewer && !c.viewer->username && c.rejected == std::vector<std::string>{"username"});
        CHECK(sdk::viewerJson(*c.viewer).dump() == "{\"id\":7}");
        std::string names;
        for (int i = 0; i < 64; i++) names += "\xD0\xB6"; // 64 Cyrillic letters: 128 bytes, 64 UTF-16 code units
        v = WidgetViewer{7};
        v.firstName = names;
        CHECK(sdk::checkViewer(v).viewer->firstName == names);
        v.firstName = names + "a";
        CHECK(sdk::checkViewer(v).rejected == std::vector<std::string>{"first_name"});
    }
    CHECK(sdk::viewerFieldRule("first_name") == "not text of 1–64 UTF-16 code units" && sdk::viewerFieldRule("username") == "not 5–32 characters of A–Z, a–z, 0–9 and _" &&
          sdk::viewerFieldRule("is_premium") == "not a boolean");
    // A client failure outside the three is FAILED; the privileged methods now include copyText, not request.
    CHECK(sdk::requestFailureCode(WidgetRequestFailure::Timeout) == "TIMEOUT" && sdk::requestFailureCode(WidgetRequestFailure::RateLimited) == "RATE_LIMITED");
    CHECK(sdk::requestFailureCode(WidgetRequestFailure::Failed) == "FAILED" && sdk::requestFailureCode(WidgetRequestFailure(42)) == "FAILED");
    CHECK(sdk::isPrivilegedMethod("copyText") && !sdk::isPrivilegedMethod("request"));
}

/// What the fixture cannot carry (no lone surrogates cross every platform's string type): the C++ strings
/// are UTF-8, with a lone surrogate as WTF-8, and checkLink refuses both that and invalid UTF-8.
TEST(sdk_link_refuses_surrogates_and_invalid_utf8) {
    auto link = [](const std::string& url) { return sdk::checkLink("openLink", url).value_or("null"); };
    CHECK(link("https://example.com/\xC3\xA9") == "https://example.com/%C3%A9");
    CHECK(link("https://example.com/\xF0\x9F\x98\x80") == "https://example.com/%F0%9F%98%80");
    CHECK(link("https://example.com/\xED\x9F\xBF") == "https://example.com/%ED%9F%BF"); // U+D7FF, the last before the surrogates
    CHECK(link("https://example.com/\xEE\x80\x80") == "https://example.com/%EE%80%80"); // U+E000, the first after
    for (const char* bad : {"https://example.com/\xED\xA0\x80", "https://example.com/\xED\xBF\xBF", "https://example.com/\xED\xA0\xBD\xED\xB8\x80", // lone / CESU-8 pair
                            "https://example.com/\xC3", "https://example.com/\xC3x", "https://example.com/\x80", "https://example.com/\xFF",
                            "https://example.com/\xC0\xAF", "https://example.com/\xE0\x80\xAF", "https://example.com/\xF0\x80\x80\xAF", // overlong
                            "https://example.com/\xF4\x90\x80\x80", "https://example.com/\xF8\x88\x80\x80\x80"})                       // above U+10FFFF
        CHECK_MSG(link(bad) == "null", bad);
    CHECK(link(std::string("https://example.com/a\0b", 23)) == "null");
    // The JSON route: a `\ud800` escape stays a surrogate when asked to, so the bridge check refuses it.
    CHECK(Json::parse("\"\\ud800\"", Json::Surrogates::Keep)->asString() == "\xED\xA0\x80");
    CHECK(Json::parse("\"\\udc00x\"", Json::Surrogates::Keep)->asString() == "\xED\xB0\x80x");
    CHECK(Json::parse("\"\\ud83d\\ude00\"", Json::Surrogates::Keep)->asString() == "\xF0\x9F\x98\x80");
    CHECK(Json::parse("\"\\ud800\"")->asString() == "\xEF\xBF\xBD");
    // A lone high surrogate does not swallow the escape after it.
    CHECK(Json::parse("\"\\ud800\\u0041\"")->asString() == "\xEF\xBF\xBD" "A");
    CHECK(Json::parse("\"\\ud800\\u0041\"", Json::Surrogates::Keep)->asString() == "\xED\xA0\x80" "A");
    auto call = Json::parse("[\"openLink\",[\"https://example.com/\\ud800\"]]", Json::Surrogates::Keep);
    CHECK(call && !sdk::checkBridgeCall((*call)[0], (*call)[1]));
    CHECK(sdk::utf8Length(Json::parse("\"\\udc00\"", Json::Surrogates::Keep)->asString()) == 3);
    // Lowercasing is A–Z only: U+212A KELVIN SIGN stays non-ASCII and fails the host check.
    CHECK(link("https://\xE2\x84\xAA" "elvin.com/") == "null");
}

static Json claimJson(const WidgetGestureClaim& c) {
    Json o = Json::object();
    o.set("x", c.x).set("y", c.y).set("hold", c.hold);
    return o;
}

/// The TS `GestureVerdict` shape (owner, gesture, cancel always; action, event, edge only when set), or null.
static Json verdictJson(const std::optional<GestureVerdict>& v) {
    if (!v) return Json();
    Json o = Json::object();
    o.set("owner", gesture::name(v->owner)).set("gesture", gesture::name(v->gesture)).set("cancel", v->cancel);
    if (v->action) o.set("action", gesture::name(*v->action));
    if (v->event) o.set("event", gesture::name(*v->event));
    if (v->edge) o.set("edge", true);
    return o;
}

/// gesture_policy against the fixture shared with gesture-policy.ts, GesturePolicy.swift and GesturePolicy.kt: every
/// section. A `down` step's claim is the `claimed` object {x, y, hold} as given (a claim a client builds by hand),
/// else claimOf([claim]) (default "auto"), as gesture-policy.test.ts reads it.
TEST(gesture_policy_shared_fixture) {
    auto root = Json::parse(readFile(std::string(TW_FIXTURES) + "/gesture-policy.json"));
    CHECK_MSG(root.has_value(), "fixture missing — run pnpm build:desktop");
    if (!root) return;
    const std::pair<std::string_view, double> constants[] = {
        {"TOUCH_SLOP_PX", gesture::TOUCH_SLOP_PX}, {"HOLD_MS", gesture::HOLD_MS}, {"DOUBLE_TAP_MS", gesture::DOUBLE_TAP_MS},
        {"DOUBLE_TAP_SLOP_PX", gesture::DOUBLE_TAP_SLOP_PX}, {"EDGE_STRIP_PX", gesture::EDGE_STRIP_PX}};
    CHECK((*root)["constants"].size() == std::size(constants));
    for (const auto& [name, value] : (*root)["constants"].obj()) {
        auto it = std::find_if(std::begin(constants), std::end(constants), [&](const auto& c) { return c.first == name; });
        CHECK_MSG(it != std::end(constants) && it->second == value.asNumber(), name);
    }
    int claims = 0;
    for (const auto& c : (*root)["claims"].arr()) {
        claims++;
        std::vector<std::string> touchActions;
        for (const auto& ta : c["touchActions"].arr()) touchActions.push_back(ta.asString());
        Json got = claimJson(gesture::claimOf(touchActions, c["prevented"].asBool()));
        CHECK_MSG(jsonEqual(got, c["expect"]), c["touchActions"].dump() + " prevented " + c["prevented"].dump() + " → " + got.dump());
    }
    int cases = 0, steps = 0, claimed = 0;
    for (const auto& c : (*root)["cases"].arr()) {
        cases++;
        GestureArbiter a;
        int i = 0;
        for (const auto& s : c["steps"].arr()) {
            steps++;
            std::string where = c["name"].asString() + ": step " + std::to_string(i++) + " " + s.dump().substr(0, 80);
            std::optional<GestureVerdict> got;
            if (const Json* p = s.find("down")) {
                std::string pointer = s["pointer"].isString() ? s["pointer"].asString() : "touch";
                std::optional<PointerKind> kind;
                for (PointerKind k : {PointerKind::Touch, PointerKind::Pen, PointerKind::Mouse}) if (gesture::name(k) == pointer) kind = k;
                CHECK_MSG(kind.has_value(), where + ": unknown pointer");
                WidgetGestureClaim claim;
                if (const Json* given = s.find("claimed")) {
                    claimed++;
                    CHECK_MSG(given->isObject() && (*given)["x"].isBool() && (*given)["y"].isBool() && (*given)["hold"].isBool(), where + ": claimed is not {x, y, hold}");
                    claim = {(*given)["x"].asBool(), (*given)["y"].asBool(), (*given)["hold"].asBool()};
                } else {
                    claim = gesture::claimOf({s["claim"].isString() ? s["claim"].asString() : "auto"});
                }
                got = a.down((*p)[0].asNumber(), (*p)[1].asNumber(), (*p)[2].asNumber(), claim, kind.value_or(PointerKind::Touch), s["button"].asInt(0));
            } else if (const Json* p = s.find("move")) {
                got = a.move((*p)[0].asNumber(), (*p)[1].asNumber(), (*p)[2].asNumber());
            } else if (const Json* p = s.find("up")) {
                got = a.up((*p)[0].asNumber(), (*p)[1].asNumber(), (*p)[2].asNumber());
            } else if (const Json* t = s.find("tick")) {
                got = a.tick(t->asNumber());
            } else if (s["cancel"].asBool()) {
                a.cancel();
                continue;
            } else if (const Json* deadline = s.find("holdDeadline")) {
                std::optional<double> d = a.holdDeadline();
                Json dj = d ? Json(*d) : Json();
                CHECK_MSG(jsonEqual(dj, *deadline), where + " → " + dj.dump());
                continue;
            } else {
                CHECK_MSG(false, where + ": unknown step");
                continue;
            }
            Json gj = verdictJson(got);
            CHECK_MSG(s.find("expect") && jsonEqual(gj, s["expect"]), where + " → " + gj.dump());
        }
    }
    printf("    %d claims, %d cases, %d steps (%d with a claim given as is)\n", claims, cases, steps, claimed);
    // v0.1.3's fixture (vertical drags are the chat's) or later: a stale copy fails here.
    CHECK(claims >= 21 && cases >= 28 && steps >= 174 && claimed >= 3);
    // A verdict and NO_CLAIM are values: a caller may keep one, and nothing changes the other.
    GestureArbiter a;
    GestureVerdict v = a.down(0, 100, 50, gesture::claimOf({"auto"}));
    a.tick(500);
    CHECK(verdictJson(v).dump() == "{\"owner\":\"pending\",\"gesture\":\"press\",\"cancel\":false}");
    static_assert(gesture::NO_CLAIM == WidgetGestureClaim{false, false, false});
}

static const char* kDepthProbe ="(function(){ var n = 0; function r(){ n++; r(); } try { r(); } catch (e) { if (!(e instanceof RangeError)) throw e; } return n; })()";

/// Product contexts live on a JSThread (8 MB stack, 6 MB QuickJS limit): a release
/// quickjs-ng needs a few hundred bytes per JS call, so this must comfortably exceed
/// what css-tree and chart libraries recurse to.
TEST(pinned_thread_gives_deep_recursion) {
    JSThread t("test.js");
    double depth = t.sync([&] {
        QuickJSContext js(size_t(16) << 20, JSThread::kJSStackLimit);
        return js.evaluate(kDepthProbe, "t.js").number.value_or(-1);
    });
    printf("    quickjs recursion depth on JSThread: %.0f\n", depth);
    CHECK(depth > 300);
    // Timers and ordering: `after` fires on the thread, `cancel` prevents it.
    std::promise<bool> fired;
    bool cancelledRan = false;
    auto c = t.after(0.01, [&] { cancelledRan = true; });
    c->cancel();
    t.after(0.03, [&] { fired.set_value(t.isCurrent()); });
    auto f = fired.get_future();
    CHECK(f.wait_for(std::chrono::seconds(2)) == std::future_status::ready);
    CHECK(f.get());
    CHECK(!cancelledRan);
    // A cancelled timer leaves the queue before its time: 10 000 timers of 24 h set and cancelled (a widget's
    // setTimeout / clearTimeout loop) leave at most twice the live ones, not 10 000.
    auto live = t.after(86400, [] {});
    for (int i = 0; i < 10000; i++) t.after(86400, [] {})->cancel();
    CHECK_MSG(t.queuedTimers() <= 128, std::to_string(t.queuedTimers()) + " timers queued");
    live->cancel();
    t.stop();
}

/// The runtime re-reads the stack top on every entry, so a call from another thread
/// (whose stack lives elsewhere) is measured correctly, not as a bogus overflow.
TEST(stack_check_follows_the_calling_thread) {
    JSThread t("creator");
    std::unique_ptr<QuickJSContext> js;
    double depthOnCreator = t.sync([&] {
        js = std::make_unique<QuickJSContext>(size_t(16) << 20);
        return js->evaluate(kDepthProbe, "t.js").number.value_or(-1);
    });
    double depthHere = js->evaluate(kDepthProbe, "t.js").number.value_or(-1);
    printf("    quickjs recursion depth at default stack limit: creator %.0f, here %.0f\n", depthOnCreator, depthHere);
    CHECK(depthOnCreator > 20);
    CHECK(depthHere > 20);
    CHECK(std::fabs(depthHere - depthOnCreator) / depthOnCreator < 0.1);
    t.sync([&] { js.reset(); });
}

// ───────────── engine ─────────────

TEST(boots_and_emits_initial_tree) {
    Run r("<p id=\"a\">hi</p><script>document.getElementById('a').textContent = 'changed by script'; console.log('hello from quickjs');</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK(!r.host.died);
    std::string all = r.host.allOps();
    CHECK_MSG(all.find("\"tag\":\"p\"") != std::string::npos, "initial tree should be sent");
    CHECK_MSG(all.find("changed by script") != std::string::npos, "script mutation should be sent");
    CHECK_MSG(r.host.allRenderOps().find("changed by script") != std::string::npos, "validated ops carry the mutation");
    CHECK(r.host.hasLog("hello from quickjs"));
    bool errors = false;
    for (auto& lg : r.host.logs) if (lg.first == "error") errors = true;
    CHECK_MSG(!errors, r.logsText());
}

TEST(network_apis_are_stubbed) {
    Run r("<script>var r = []; try { new WebSocket('ws://x') } catch (e) { r.push(e.name) } fetch('" + CANARY + "').catch(e => r.push(e.name)).then(() => console.log(r.join(',')));</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("NetworkDisabledError,NetworkDisabledError"), r.logsText());
    CHECK(r.host.hasDiag("network-blocked"));
}

TEST(cpu_limit_kills_infinite_loop) {
    auto cfg = testConfig();
    cfg.cpuLimitMs = 200;
    Run r("<script>for(;;){}</script>", cfg);
    r.barrier();
    auto l = r.host.lock();
    CHECK(r.host.died.has_value());
    CHECK_MSG(r.host.died && r.host.died->find("CPU limit") != std::string::npos, r.host.died.value_or(""));
}

TEST(memory_limit_kills_allocation_flood) {
    auto cfg = testConfig();
    cfg.memoryLimitBytes = size_t(16) << 20;
    cfg.cpuLimitMs = 10000;
    Run r("<script>var a = []; for (;;) a.push(new Array(100000).fill(1.5));</script>", cfg);
    r.barrier();
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.died && r.host.died->find("Memory limit exceeded (16 MB)") != std::string::npos, r.host.died.value_or("alive: " + r.logsText()));
    }
    // The default is 8 MB of QuickJS heap (docs/decisions.md, "Strict limits from the start").
    auto byDefault = testConfig();
    byDefault.cpuLimitMs = 10000;
    Run d("<script>var a = []; for (;;) a.push(new Array(100000).fill(1.5));</script>", byDefault);
    d.barrier();
    auto l = d.host.lock();
    CHECK_MSG(d.host.died && d.host.died->find("Memory limit exceeded (8 MB)") != std::string::npos, d.host.died.value_or("alive: " + d.logsText()));
}

TEST(layout_query_round_trip) {
    Run r("<div id=\"d\">x</div><script>console.log('w=' + document.getElementById('d').getBoundingClientRect().width);</script>");
    r.barrier();
    auto l = r.host.lock();
    bool rect = false;
    for (auto& q : r.host.queries) if (q.find("\"rect\"") != std::string::npos) rect = true;
    CHECK(rect);
    CHECK_MSG(r.host.hasLog("w=100"), r.logsText());
}

/// The renderer reports its size before `ready`; the host forwards it before start().
TEST(inputs_before_start_are_buffered_not_crashing) {
    Run r("<script>console.log('w=' + innerWidth + ' theme=' + TelegramWidget.theme);</script>", testConfig(), false);
    r.engine.viewport(360, 200, 3);
    r.engine.theme("dark");
    r.engine.frame(1);
    r.engine.start();
    r.host.waitLoaded(20);
    r.barrier();
    auto l = r.host.lock();
    CHECK(!r.host.died);
    CHECK_MSG(r.host.hasLog("w=400 theme=light"), "boot sees the initial config: " + r.logsText());
}

/// `maxTimers` reaches the shim through the boot config: the 9th timer throws
/// RangeError inside the widget, the first 8 still fire, and the engine's own
/// backstop is never needed.
TEST(timer_limit_is_enforced) {
    auto cfg = testConfig();
    cfg.maxTimers = 8;
    Run r("<script>var fired = 0, err = ''; for (var i = 0; i < 8; i++) setTimeout(function () { if (++fired === 8) console.log('fired=' + fired); }, 5);"
          "try { setTimeout(function () {}, 5); } catch (e) { err = e.name; } try { setInterval(function () {}, 10); } catch (e) { err += ',' + e.name; } console.log('err=' + err);</script>", cfg);
    r.host.waitUntil([&] { return r.host.hasLog("fired=8"); }, 3);
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("err=RangeError,RangeError"), r.logsText());
    CHECK_MSG(r.host.hasLog("fired=8"), r.logsText());
    CHECK_MSG(r.host.countDiag("timer-limit") == 1, r.diagsText());
    CHECK_MSG(!r.host.hasDiag("timer-limit-enforced"), "the engine backstop must not be needed: " + r.diagsText());
    CHECK(!r.host.died);
}

/// A timer id the shim asks for again replaces its timer: a widget that replaces JSON.stringify makes the shim's 200
/// timer calls arrive as one long interval's id, due in 20 ms. The engine kept every handle's timer running (dropping
/// the shared_ptr did not cancel it) and each fired the interval's callback; now the old one is cancelled, once.
TEST(a_timer_id_asked_for_again_replaces_its_timer) {
    Run r("<script>var fired = 0; var id = setInterval(function () { fired++; }, 10000000); var orig = JSON.stringify;"
          "JSON.stringify = function (v) { return Array.isArray(v) && v.length === 2 ? '[' + id + ',20]' : orig(v); };"
          "for (var i = 0; i < 200; i++) clearTimeout(setTimeout(function () {}, 1)); JSON.stringify = orig;"
          "setTimeout(function () { console.log('fired=' + fired); console.log('done'); }, 500);</script>", testConfig());
    r.host.waitUntil([&] { return r.host.hasLog("done"); }, 5);
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("fired=1"), r.logsText());
    CHECK_MSG(!r.host.hasDiag("timer-limit-enforced"), r.diagsText());
    CHECK(!r.host.died);
}

/// A 100 ms interval burning 40 ms per tick is legal under cpuLimitMs alone; the
/// quota (100 ms/s here) defers ticks instead of killing, reports `cpu-throttled`,
/// and a click still gets through.
TEST(cpu_quota_throttles_greedy_timer) {
    auto cfg = testConfig();
    cfg.cpuQuotaMs = 100;
    Run r("<p id=\"p\">x</p><script>var ticks = 0; setInterval(function () { var t = performance.now(); while (performance.now() - t < 40) {} ticks++; }, 100);"
          "document.addEventListener('click', function () { console.log('click at tick ' + ticks); });</script>", cfg);
    sleepMs(2500);
    r.engine.event("{\"type\":\"click\",\"targetId\":2}");
    r.barrier();
    auto l = r.host.lock();
    CHECK(!r.host.died);
    CHECK_MSG(r.host.hasDiag("cpu-throttled"), r.diagsText());
    CHECK_MSG(r.host.hasLogPrefix("click at tick"), "events run under throttling: " + r.logsText());
    int ticks = -1;
    for (auto& lg : r.host.logs) if (lg.second.rfind("click at tick ", 0) == 0) ticks = std::atoi(lg.second.c_str() + 14);
    // Unthrottled the interval would tick ~25 times in 2.5 s; 100 ms/s allows 2–3 per second.
    CHECK_MSG(ticks > 1, std::to_string(ticks));
    CHECK_MSG(ticks < 12, std::to_string(ticks) + " ticks: " + r.logsText());
}

/// The layout-query limit (CpuBudget::queryLimitHit): a forced-layout loop burns no JS
/// CPU of its own but keeps the host answering queries; with a slow renderer (5 ms per
/// answer) it is killed past 128 answered queries instead of holding the renderer.
/// The twin of QueryBudgetTests.swift and QueryBudgetTest.kt.
TEST(layout_query_loop_is_killed_instead_of_blocking_the_host) {
    auto cfg = testConfig();
    cfg.cpuLimitMs = 200;
    Run r("<div id=a></div><script>const a = document.getElementById('a'); let i = 0; for (;;) { a.style.width = (i++ % 300) + 'px'; a.offsetHeight; }</script>", cfg, false);
    r.host.answer = [](const Json&) -> std::optional<std::string> { std::this_thread::sleep_for(std::chrono::milliseconds(5)); return std::nullopt; };
    auto t0 = std::chrono::steady_clock::now();
    r.engine.start();
    r.host.waitLoaded(10);
    double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    auto l = r.host.lock();
    CHECK_MSG(r.host.died.has_value(), "alive after " + std::to_string(seconds) + " s, " + std::to_string(r.host.queries.size()) + " queries");
    CHECK_MSG(r.host.died && r.host.died->rfind("Layout query limit exceeded", 0) == 0, r.host.died.value_or("alive"));
    // 128 answered queries whatever the slice (CpuBudget::kQueryLimit), the boot task's included.
    CHECK_MSG(r.host.died && r.host.died->find("more than 128 layout queries in one boot task") != std::string::npos, r.host.died.value_or("alive"));
    CHECK_MSG(seconds < 5, std::to_string(seconds) + " s");
    CHECK_MSG(int(r.host.queries.size()) == CpuBudget::kQueryLimit + 1, std::to_string(r.host.queries.size()) + " queries");
}

/// 30 ms per answer: the 1000 ms guard (CpuBudget::kQueryBudgetMs) fires at the 34th query, and a loop that ends on the
/// error answers is stopped at the task's end all the same.
TEST(layout_queries_holding_the_host_past_the_guard_stop_the_widget) {
    auto cfg = testConfig();
    cfg.cpuLimitMs = 200;
    Run r("<div id=a></div><script>const a = document.getElementById('a'); for (let i = 0; i < 60; i++) { a.style.width = i + 'px'; a.offsetHeight; }</script>", cfg, false);
    r.host.answer = [](const Json&) -> std::optional<std::string> { std::this_thread::sleep_for(std::chrono::milliseconds(30)); return std::nullopt; };
    r.engine.start();
    r.host.waitLoaded(10);
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.died && r.host.died->rfind("Layout query limit exceeded: host queries blocked the renderer for more than 1000 ms in one boot task", 0) == 0, r.host.died.value_or("alive"));
    CHECK_MSG(r.host.queries.size() < 40, std::to_string(r.host.queries.size()) + " queries");
}

TEST(ordinary_layout_queries_keep_the_widget_alive) {
    auto cfg = testConfig();
    cfg.cpuLimitMs = 200;
    Run r("<div id=a></div><script>const a = document.getElementById('a'); for (let i = 0; i < 10; i++) { a.style.width = i + 'px'; a.offsetHeight; }</script>", cfg, false);
    r.host.answer = [](const Json&) -> std::optional<std::string> { std::this_thread::sleep_for(std::chrono::milliseconds(2)); return std::nullopt; };
    r.engine.start();
    r.host.waitLoaded(10);
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(!r.host.died, r.host.died.value_or(""));
    CHECK(!r.host.queries.empty());
}

/// Hidden: frames are neither requested nor delivered; on return the one
/// pending request is made and the shim saw `visibilitychange` both ways.
TEST(hidden_engine_delivers_no_frames) {
    Run r("<script>var n = 0; function step() { n++; console.log('frame ' + n); requestAnimationFrame(step); } requestAnimationFrame(step);"
          "document.addEventListener('visibilitychange', function () { console.log('vis=' + document.visibilityState); });</script>");
    r.barrier();
    { auto l = r.host.lock(); CHECK(r.host.frameRequests == 1); }
    r.engine.frame(1);
    r.barrier();
    { auto l = r.host.lock(); CHECK(r.host.frameRequests == 2); CHECK(r.host.hasLog("frame 1")); }
    r.engine.setVisible(false);
    r.engine.frame(2); // in flight when the widget was hidden: dropped
    r.barrier();
    { auto l = r.host.lock(); CHECK(r.host.frameRequests == 2); CHECK(!r.host.hasLog("frame 2")); CHECK_MSG(r.host.hasLog("vis=hidden"), r.logsText()); }
    r.engine.setVisible(true);
    r.barrier();
    { auto l = r.host.lock(); CHECK_MSG(r.host.frameRequests == 3, "the pending request is made on return"); CHECK_MSG(r.host.hasLog("vis=visible"), r.logsText()); }
    r.engine.frame(3);
    r.barrier();
    auto l = r.host.lock();
    CHECK(r.host.hasLog("frame 2"));
    CHECK(!r.host.died);
}

/// Hidden: a 50 ms interval ticks at most once per second; visible again it runs at full rate.
TEST(hidden_timers_are_coalesced_to_one_hertz) {
    Run r("<script>var t = 0; setInterval(function () { t++; console.log('tick ' + t); }, 50);</script>");
    r.engine.setVisible(false);
    sleepMs(1600);
    r.barrier();
    int hiddenTicks;
    { auto l = r.host.lock(); hiddenTicks = r.host.countLogPrefix("tick"); }
    CHECK(hiddenTicks >= 1);
    CHECK_MSG(hiddenTicks <= 2, std::to_string(hiddenTicks) + " ticks while hidden");
    r.engine.setVisible(true);
    sleepMs(1600);
    r.barrier();
    auto l = r.host.lock();
    int visibleTicks = r.host.countLogPrefix("tick") - hiddenTicks;
    CHECK_MSG(visibleTicks >= 5, std::to_string(visibleTicks) + " ticks while visible");
    CHECK(!r.host.died);
}

/// Hidden: the timers still run in the order of their delays (all at about 1 s; setTimeout 20, 10, 0 → 0, 10, 20).
TEST(hidden_timers_keep_their_order) {
    Run r("<script>document.addEventListener('visibilitychange', function () { if (!document.hidden) return; [20, 10, 0].forEach(function (d) { setTimeout(function () { console.log('order ' + d); }, d); }); });</script>");
    r.engine.setVisible(false);
    // Hidden timers fire at the next 1 Hz wake-up after their delay: up to 2 s.
    r.host.waitUntil([&] { return r.host.countLogPrefix("order") >= 3; }, 4);
    auto l = r.host.lock();
    std::string order;
    for (auto& lg : r.host.logs) if (lg.second.rfind("order ", 0) == 0) order += lg.second.substr(6) + ",";
    CHECK_MSG(order == "0,10,20,", order + "\n" + r.logsText());
    CHECK(!r.host.died);
}

static int countActions(const std::vector<WidgetAction>& actions, WidgetAction::Kind kind) {
    int n = 0;
    for (auto& a : actions) if (a.kind == kind) n++;
    return n;
}

static std::string actionsText(const std::vector<WidgetAction>& actions) {
    std::string s;
    for (auto& a : actions) s += a.toJson().dump() + " ";
    return s;
}

TEST(compat_policy_forwards_every_bridge_call) {
    SandboxConfig compat = testConfig();
    compat.policy = "compat";
    Run r("<script>TelegramWidget.ready(); setTimeout(() => TelegramWidget.openLink('https://example.com/later'), 30);</script>", compat);
    r.host.waitUntil([&] { return countActions(r.host.actions, WidgetAction::OpenLink) > 0; }, 3);
    auto l = r.host.lock();
    CHECK_MSG(countActions(r.host.actions, WidgetAction::OpenLink) == 1, actionsText(r.host.actions));
    CHECK(countActions(r.host.actions, WidgetAction::Ready) == 1);
    CHECK(!r.host.hasDiag("activation-blocked"));
    // The deprecated raw call still follows each action.
    CHECK(r.host.bridge.size() == 2 && r.host.bridge[1].first == "openLink" && r.host.bridge[1].second == "[\"https://example.com/later\"]");
}

/// The default policy is strict, and so is any value but exactly "compat".
TEST(policy_is_strict_by_default_and_fails_closed) {
    CHECK(SandboxConfig().policy == "strict");
    CHECK(WidgetHostOptions().policy == "strict");
    CHECK(isStrictPolicy("strict"));
    CHECK(isStrictPolicy("Strict"));
    CHECK(isStrictPolicy("Compat"));
    CHECK(isStrictPolicy(""));
    CHECK(!isStrictPolicy("compat"));
    SandboxConfig typo = testConfig();
    typo.policy = "compat ";
    Run r("<script>setTimeout(() => TelegramWidget.openLink('https://example.com/later'), 30);</script>", typo);
    r.host.waitUntil([&] { return r.host.hasDiag("activation-blocked"); }, 3);
    auto l = r.host.lock();
    CHECK(r.host.hasDiag("activation-blocked"));
    CHECK_MSG(r.host.actions.empty(), actionsText(r.host.actions));
    CHECK(r.host.bridge.empty());
}

/// `ready` needs no gesture; a link call from a timer is refused and reported, the same call from the
/// handler of a trusted click goes through once (normalised), the second call of that tap is refused.
TEST(bridge_and_timers) {
    SandboxConfig strict = testConfig();
    Run r("<button id='b'>go</button><script>TelegramWidget.ready(); setTimeout(() => TelegramWidget.openLink('https://example.com/later'), 30);"
          "document.getElementById('b').addEventListener('click', () => { TelegramWidget.openLink('HTTPS://Example.COM/clicked'); TelegramWidget.openTelegramLink('https://t.me/again'); });</script>", strict);
    r.host.waitUntil([&] { return r.host.hasDiag("activation-blocked"); }, 3);
    {
        auto l = r.host.lock();
        CHECK(countActions(r.host.actions, WidgetAction::Ready) == 1);
        CHECK(countActions(r.host.actions, WidgetAction::OpenLink) == 0);
        CHECK(r.host.countDiag("activation-blocked") == 1);
    }
    r.engine.event("{\"type\":\"click\",\"targetId\":5}");
    r.barrier();
    r.engine.activationEvent("{\"type\":\"click\",\"targetId\":5}", 1);
    r.host.waitUntil([&] { return countActions(r.host.actions, WidgetAction::OpenLink) > 0; }, 3);
    r.barrier();
    r.engine.activationEvent("{\"type\":\"click\",\"targetId\":5}", 1);
    r.barrier();
    auto l = r.host.lock();
    std::vector<std::string> opened;
    for (auto& a : r.host.actions) if (a.kind == WidgetAction::OpenLink || a.kind == WidgetAction::OpenTelegramLink) opened.push_back(a.url);
    CHECK_MSG(opened == std::vector<std::string>({"https://example.com/clicked"}), actionsText(r.host.actions));
    // The timer, the untrusted click (×2 calls), the second call of the tap, the replayed gesture (×2 calls).
    CHECK_MSG(r.host.countDiag("activation-blocked") == 6, r.diagsText());
}

TEST(user_gestures_are_numbered_per_physical_press) {
    auto ev = [](const char* json) { return *Json::parse(json); };
    CHECK(WidgetHost::isUserActivation(ev("{\"type\":\"click\",\"detail\":1}")));
    CHECK(WidgetHost::isUserActivation(ev("{\"type\":\"pointerdown\"}")));
    CHECK(!WidgetHost::isUserActivation(ev("{\"type\":\"click\",\"detail\":0}")));
    CHECK(!WidgetHost::isUserActivation(ev("{\"type\":\"click\"}")));
    for (const char* key : {"Enter", " ", "a", "Escape", "Tab", "ArrowDown"})
        CHECK_MSG(!WidgetHost::isUserActivation(ev(("{\"type\":\"keydown\",\"key\":\"" + std::string(key) + "\"}").c_str())), key);
    CHECK(!WidgetHost::isUserActivation(ev("{\"type\":\"mousemove\"}")));
    CHECK(!WidgetHost::isUserActivation(ev("{\"type\":\"scroll\"}")));

    GestureCounter c;
    std::vector<int> press;
    for (const char* t : {"pointerdown", "mousedown", "pointerup", "mouseup", "click", "dblclick"}) press.push_back(c.gestureFor(ev(("{\"type\":\"" + std::string(t) + "\",\"detail\":1}").c_str())));
    CHECK(press == std::vector<int>({1, 1, 1, 1, 1, 1}));
    CHECK(c.gestureFor(ev("{\"type\":\"pointerdown\"}")) == 2);
    CHECK(c.gestureFor(ev("{\"type\":\"keydown\",\"key\":\"Enter\"}")) == 0);
    CHECK(c.gestureFor(ev("{\"type\":\"click\",\"detail\":0}")) == 2); // the end of the open press
    // No keyboard: neither a key nor the click Enter or Space activates (detail 0) starts a gesture.
    CHECK(c.gestureFor(ev("{\"type\":\"click\",\"detail\":0}")) == 0);
    CHECK(c.gestureFor(ev("{\"type\":\"dblclick\",\"detail\":0}")) == 0);
    CHECK(c.gestureFor(ev("{\"type\":\"click\",\"detail\":1}")) == 3);
    CHECK(c.gestureFor(ev("{\"type\":\"keydown\",\"key\":\" \"}")) == 0);
    CHECK(c.gestureFor(ev("{\"type\":\"mousemove\"}")) == 0);
    // A press the renderer reports (its `gesture` claim) begins a new gesture: a finger's release after a drag, whose
    // pointerdown is not trusted, is not the end of the drag's press.
    GestureCounter d;
    CHECK(d.gestureFor(ev("{\"type\":\"pointerup\"}")) == 1);
    d.pressBegan();
    CHECK(d.gestureFor(ev("{\"type\":\"pointerup\"}")) == 2);
    CHECK(d.gestureFor(ev("{\"type\":\"click\",\"detail\":1}")) == 2);
}

/// A renderer without a document: announces itself, answers every query with null, keeps what it was sent; tests inject its events.
struct ScriptedRenderer final : Renderer {
    void load() override { onMessage("{\"type\":\"ready\"}"); }
    void send(const std::string& json) override {
        {
            std::lock_guard<std::mutex> l(mutex);
            messages.push_back(json);
        }
        auto m = Json::parse(json);
        if (m && (*m)["type"].asString() == "query") onMessage("{\"type\":\"result\",\"qid\":" + std::to_string((*m)["qid"].asInt()) + ",\"result\":null}");
    }
    void setTheme(const std::string& t) override { std::lock_guard<std::mutex> l(mutex); themes.push_back(t); }
    void setWidth(double) override {}
    void destroy() override {}
    /// The messages of one type, in order.
    std::vector<Json> sent(const std::string& type) {
        std::lock_guard<std::mutex> l(mutex);
        std::vector<Json> out;
        for (auto& m : messages) if (auto j = Json::parse(m); j && (*j)["type"].asString() == type) out.push_back(*j);
        return out;
    }
    std::mutex mutex;
    std::vector<std::string> messages;
    std::vector<std::string> themes;
};

static RendererFactory scripted(ScriptedRenderer*& out) {
    return [&out](std::shared_ptr<SandboxResources>, const std::string&, double) -> std::unique_ptr<Renderer> {
        auto r = std::make_unique<ScriptedRenderer>();
        out = r.get();
        return r;
    };
}

/// Only renderer events marked `trusted` (the DOM event's isTrusted; every input event of a native renderer) are gestures,
/// and only pointer ones: keys and the click Enter activates unlock nothing.
TEST(gestures_need_the_renderers_trusted_flag) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<button id='b'>go</button><script>TelegramWidget.ready(); var b = document.getElementById('b');"
                    "b.addEventListener('keydown', function (e) { TelegramWidget.openLink('https://example.com/key-' + e.key); });"
                    "b.addEventListener('click', function (e) { TelegramWidget.openLink('https://example.com/click-' + e.detail); });</script>",
                    WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
    std::mutex m;
    std::vector<std::string> sent;
    host.onAction = [&](const WidgetAction& a) { std::lock_guard<std::mutex> l(m); if (a.kind != WidgetAction::Ready) sent.push_back(a.url); };
    host.start();
    CHECK(host.waitLoaded(20));
    auto event = [&](const std::string& ev, const std::string& trusted) { renderer->onMessage("{\"type\":\"event\",\"ev\":" + ev + trusted + "}"); };
    const std::string click = "{\"type\":\"click\",\"targetId\":5,\"detail\":1}";
    event(click, "");
    event(click, ",\"trusted\":false");
    event(click, ",\"trusted\":1");
    CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("activation-blocked") >= 3; }, 5));
    {
        std::lock_guard<std::mutex> l(m);
        CHECK_MSG(sent.empty(), std::to_string(sent.size()) + " calls from untrusted events");
    }
    const std::string yes = ",\"trusted\":true";
    event("{\"type\":\"keydown\",\"targetId\":5,\"key\":\"Enter\",\"code\":\"Enter\",\"repeat\":false,\"modifiers\":0}", yes);
    event("{\"type\":\"click\",\"targetId\":5,\"detail\":0}", yes);
    event("{\"type\":\"keydown\",\"targetId\":5,\"key\":\"Enter\",\"code\":\"Enter\",\"repeat\":true,\"modifiers\":0}", yes);
    event("{\"type\":\"click\",\"targetId\":5,\"detail\":0}", yes);
    event("{\"type\":\"keydown\",\"targetId\":5,\"key\":\"Tab\",\"code\":\"Tab\",\"repeat\":false,\"modifiers\":0}", yes);
    event(click, yes);
    CHECK(host.waitUntil([&](const WidgetDiagnostics& d) { std::lock_guard<std::mutex> l(m); return sent.size() >= 1 && d.countDiag("activation-blocked") >= 8; }, 5));
    sleepMs(100);
    std::lock_guard<std::mutex> l(m);
    CHECK_MSG(sent == std::vector<std::string>({"https://example.com/click-1"}), [&] { std::string s; for (auto& x : sent) s += x + " "; return s; }());
    CHECK(host.diagnostics().countDiag("activation-blocked") == 8);
}

// ───────────── strict limits (docs/decisions.md, "Strict limits from the start") ─────────────

/// The defaults every host shares (LIMIT_DEFAULTS in protocol.ts, STORAGE_QUOTA_BYTES in sdk-policy.ts).
TEST(strict_limit_defaults) {
    SandboxConfig c;
    WidgetHostOptions o;
    CHECK(c.memoryLimitBytes == size_t(8) * 1024 * 1024 && o.memoryLimitBytes == c.memoryLimitBytes);
    CHECK(c.maxTimers == 64 && o.maxTimers == 64);
    CHECK(c.mainThreadQuotaMs == 100 && o.mainThreadQuotaMs == 100);
    CHECK(c.cpuQuotaMs == 300 && o.cpuQuotaMs == 300 && c.cpuQuotaWindowMs == 1000);
    CHECK(CpuBudget::kQueryLimit == 128 && CpuBudget::kQueryBudgetMs == 1000);
    CHECK(sdk::STORAGE_QUOTA_BYTES == 4096);
}

/// The default timer cap reaches the shim: the 65th live timer throws RangeError in the widget.
TEST(default_timer_cap_throws_at_the_65th) {
    Run r("<script>var n = 0; try { for (; n < 100; n++) setTimeout(function () {}, 60000); } catch (e) { console.log(e.name + ' at ' + n); }</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("RangeError at 64"), r.logsText());
    CHECK_MSG(!r.host.hasDiag("timer-limit-enforced"), r.diagsText());
    CHECK(!r.host.died);
}

/// The policy reaches the shim in the boot config: under strict a canvas takes 4000 commands in one task and the next
/// throws RangeError in the widget (the validator's per-send cap, so the host does not have to stop it); compat draws all.
TEST(shim_refuses_canvas_commands_at_the_validators_cap) {
    const std::string html = "<canvas id=c></canvas><script>var x = document.getElementById('c').getContext('2d');"
                             "try { for (var i = 0; i < 4001; i++) x.fillRect(i, 0, 1, 1); console.log('drew all'); } catch (e) { console.log(e.name + ': ' + e.message); }</script>";
    Run strict(html);
    strict.barrier();
    {
        auto l = strict.host.lock();
        CHECK_MSG(strict.host.hasLog("RangeError: Canvas limit exceeded: more than 4000 commands for one canvas in one task (max 4000)"), strict.logsText());
        CHECK_MSG(!strict.host.died, strict.host.died.value_or(""));
        CHECK_MSG(strict.host.allRenderOps().find("[\"c\",\"fillRect\",3999,") != std::string::npos, "the 4000 commands before it are drawn");
    }
    SandboxConfig cfg = testConfig();
    cfg.policy = "compat";
    Run compat(html, cfg);
    compat.barrier();
    auto l = compat.host.lock();
    CHECK_MSG(compat.host.hasLog("drew all"), compat.logsText());
    CHECK(!compat.host.died);
}

/// A text node over 32 KB made by the widget stops it under strict, through the real engine and WidgetHost: the
/// validator's `limit` is fatal (`dead` with its reason, the runtime gone, the renderer frozen on its last render) and
/// nothing after it reaches the renderer. compat draws it (its cap is 2 MB) and never stops.
TEST(text_over_the_cap_stops_the_widget) {
    const std::string html = "<p id=p>start</p><script>"
                             "setTimeout(function () { document.getElementById('p').textContent = 'x'.repeat(40000); }, 10);"
                             "setTimeout(function () { document.getElementById('p').textContent = 'never'; }, 60);</script>";
    const std::string x64(64, 'x');
    {
        ScriptedRenderer* renderer = nullptr;
        WidgetHost host(html, WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
        std::mutex m;
        std::string rendered;
        host.onRenderOps = [&](const std::string& ops) { std::lock_guard<std::mutex> l(m); rendered += ops; };
        host.start();
        CHECK(host.waitLoaded(20));
        CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.dead.has_value(); }, 5));
        sleepMs(150); // the second timer would have fired by now
        WidgetDiagnostics d = host.diagnostics();
        CHECK_MSG(d.dead == std::optional<std::string>("Text limit exceeded: a text value of 40000 characters (max 32768)"), d.dead.value_or("alive"));
        CHECK_MSG(d.countDiag("limit") == 1, std::to_string(d.countDiag("limit")));
        CHECK(host.probe("1") == "ERROR no runtime");
        std::vector<Json> frozen = renderer->sent("frozen");
        CHECK_MSG(frozen.size() == 1 && frozen[0]["fatal"].asBool() && frozen[0]["reason"].asString() == host.stoppedLabel, std::to_string(frozen.size()) + " frozen messages");
        std::lock_guard<std::mutex> l(m);
        CHECK_MSG(rendered.find("start") != std::string::npos, rendered);
        CHECK(rendered.find(x64) == std::string::npos);
        CHECK(rendered.find("never") == std::string::npos);
    }
    WidgetHostOptions compat;
    compat.policy = "compat";
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host(html, WidgetTheme{}, 300, compat, RES, scripted(renderer));
    std::mutex m;
    std::string rendered;
    host.onRenderOps = [&](const std::string& ops) { std::lock_guard<std::mutex> l(m); rendered += ops; };
    host.start();
    CHECK(host.waitLoaded(20));
    for (int i = 0; i < 100; i++) {
        { std::lock_guard<std::mutex> l(m); if (rendered.find("never") != std::string::npos) break; }
        sleepMs(20);
    }
    WidgetDiagnostics d = host.diagnostics();
    CHECK_MSG(!d.dead, d.dead.value_or(""));
    CHECK(d.countDiag("limit") == 0);
    std::lock_guard<std::mutex> l(m);
    CHECK(rendered.find(x64) != std::string::npos);
    CHECK(rendered.find("never") != std::string::npos);
}

/// The paint limit (docs/decisions.md "Heavy boxes"): a renderer that counts reports it with Renderer::limitExceeded,
/// the host stops the widget like a counted cap (`dead` with the reason, the diagnostic, the renderer frozen); the
/// renderer learns the policy first; compat ignores the report, and a malformed one is a renderer violation.
TEST(paint_limit_stops_the_widget) {
    CHECK(paintLimitMessage(WidgetPaintLimit::Paint, 17) == "Paint limit exceeded: 17 boxes with shadows, filters or transparency (max 16)");
    CHECK(paintLimitMessage(WidgetPaintLimit::Backdrop, 3) == "Paint limit exceeded: 3 boxes with backdrop-filter (max 2)");
    CHECK(paintLimitFromCode("backdrop-limit") == WidgetPaintLimit::Backdrop && !paintLimitFromCode("render-budget"));
    const std::string html = "<p id=p>x</p><script>setInterval(function () { document.getElementById('p').textContent += '.'; }, 20);</script>";
    for (WidgetPaintLimit code : {WidgetPaintLimit::Paint, WidgetPaintLimit::Backdrop}) {
        ScriptedRenderer* renderer = nullptr;
        WidgetHost host(html, WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
        host.start();
        CHECK(host.waitLoaded(20));
        std::vector<Json> policy = renderer->sent("policy");
        CHECK_MSG(policy.size() == 1 && policy[0]["policy"].asString() == "strict", std::to_string(policy.size()) + " policy messages");
        int count = code == WidgetPaintLimit::Paint ? 17 : 3;
        renderer->limitExceeded(code, count);
        CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.dead.has_value(); }, 5));
        WidgetDiagnostics d = host.diagnostics();
        CHECK_MSG(d.dead == std::optional<std::string>(paintLimitMessage(code, count)), d.dead.value_or("alive"));
        CHECK(d.countDiag(paintLimitCode(code)) == 1);
        CHECK(renderer->sent("frozen").size() == 1);
        CHECK(host.probe("1") == "ERROR no runtime");
    }
    WidgetHostOptions compat;
    compat.policy = "compat";
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host(html, WidgetTheme{}, 300, compat, RES, scripted(renderer));
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK(renderer->sent("policy").size() == 1 && renderer->sent("policy")[0]["policy"].asString() == "compat");
    renderer->limitExceeded(WidgetPaintLimit::Paint, 40);
    renderer->onMessage("{\"type\":\"limit\",\"code\":\"paint-limit\"}");
    sleepMs(150);
    WidgetDiagnostics d = host.diagnostics();
    CHECK_MSG(!d.dead, d.dead.value_or(""));
    CHECK(d.countDiag("renderer-violation") == 1);
}

/// The main-thread window (mainThreadQuotaMs): the modelled cost of what a task sent (25 µs per op, 2 µs per canvas
/// command) plus its layout queries, apart from the JS quota. Past it frames are deferred and `cpu-throttled` names the
/// main-thread budget; with the default 100 ms the same frames all run. The canvas widget sends one op per frame, so
/// only its commands (counted in the JSON's `cmds` arrays) can spend the window.
TEST(main_thread_window_defers_frames) {
    const std::string dom = "<div id=list></div><script>var list = document.getElementById('list'), drawn = 0;"
                            "for (var i = 0; i < 50; i++) list.appendChild(document.createElement('span'));"
                            "function frame() { drawn++; for (var s of list.children) s.textContent = String(Math.random()); requestAnimationFrame(frame); }"
                            "requestAnimationFrame(frame);</script>";
    const std::string canvas = "<canvas id=c></canvas><script>var x = document.getElementById('c').getContext('2d'), drawn = 0;"
                               "function frame() { drawn++; for (var i = 0; i < 1500; i++) x.fillRect(i % 300, 0, 1, 1); requestAnimationFrame(frame); }"
                               "requestAnimationFrame(frame);</script>";
    auto run = [](const std::string& html, double quota, int& frames, std::string& throttled) {
        SandboxConfig cfg = testConfig();
        cfg.mainThreadQuotaMs = quota;
        Run r(html, cfg);
        for (int i = 1; i <= 10; i++) {
            r.engine.frame(i * 16);
            r.barrier();
        }
        frames = std::atoi(r.engine.probe("drawn").c_str());
        auto l = r.host.lock();
        CHECK_MSG(!r.host.died, r.host.died.value_or(""));
        throttled.clear();
        for (auto& [k, d] : r.host.diags) if (k == "cpu-throttled") throttled += d + "\n";
    };
    for (const std::string* html : {&dom, &canvas}) {
        const char* which = html == &dom ? "dom" : "canvas";
        int frames = 0;
        std::string throttled;
        run(*html, 100, frames, throttled);
        CHECK_MSG(frames == 10 && throttled.empty(), std::string(which) + ": " + std::to_string(frames) + " frames; " + throttled);
        run(*html, 2, frames, throttled);
        CHECK_MSG(frames < 3, std::string(which) + ": " + std::to_string(frames) + " frames under a 2 ms window");
        CHECK_MSG(throttled.find("main-thread budget used by layout queries and ops") != std::string::npos, std::string(which) + ": " + throttled);
    }
}

/// The validator's time counts against the JS quota (docs/decisions.md, "The validator's time counts"): a charge from
/// another thread is recorded on the engine thread, shows in the stats and defers the widget's timers like its own JS
/// time would. The twin of ValidatorTimeTests.swift and ValidatorCpuTest.kt.
TEST(validator_time_counts_against_the_js_quota) {
    auto ticksIn = [](double charge, double& validatorMs, std::string& throttled) {
        SandboxConfig cfg = testConfig();
        cfg.cpuQuotaMs = 100;
        Run r("<script>var ticks = 0; setInterval(function () { ticks++; }, 20);</script>", cfg);
        int before = std::atoi(r.engine.probe("ticks").c_str());
        if (charge > 0) std::thread([&] { r.engine.chargeValidator(charge); }).join();
        sleepMs(600);
        r.barrier();
        int ticks = std::atoi(r.engine.probe("ticks").c_str()) - before;
        sleepMs(250); // the trailing stats report
        auto l = r.host.lock();
        CHECK_MSG(!r.host.died, r.host.died.value_or(""));
        validatorMs = r.host.stats.validatorMs;
        throttled.clear();
        for (auto& [k, d] : r.host.diags) if (k == "cpu-throttled") throttled += d + "\n";
        return ticks;
    };
    double validatorMs = 0;
    std::string throttled;
    int free = ticksIn(0, validatorMs, throttled);
    CHECK_MSG(free > 10 && validatorMs == 0 && throttled.empty(), std::to_string(free) + " ticks, " + std::to_string(validatorMs) + " ms; " + throttled);
    int charged = ticksIn(300, validatorMs, throttled);
    CHECK_MSG(charged < 3, std::to_string(charged) + " ticks after 300 ms of validation against a 100 ms quota");
    CHECK_MSG(validatorMs == 300, std::to_string(validatorMs));
    CHECK_MSG(throttled.find("ms per 1000 ms budget used") != std::string::npos, throttled);
}

/// WidgetHost measures the validator's CPU time on its thread and charges it to the widget: a batch of style ops that
/// css-tree has to parse shows up in `stats.validatorMs`.
TEST(widget_host_charges_the_validators_time) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<div id=d></div><script>var d = document.getElementById('d');"
                    "for (var i = 0; i < 300; i++) d.style.setProperty('--v' + (i % 7), 'var(--x, ' + i + 'px) calc(' + i + 'px + 1em)');"
                    "TelegramWidget.ready();</script>",
                    WidgetTheme(), 300, WidgetHostOptions(), RES, scripted(renderer));
    host.start();
    CHECK(host.waitLoaded(20));
    double validatorMs = 0;
    for (int i = 0; i < 100 && validatorMs <= 0; i++) {
        sleepMs(20);
        validatorMs = host.diagnostics().stats.validatorMs;
    }
    CHECK_MSG(validatorMs > 0, std::to_string(validatorMs));
    CHECK_MSG(validatorMs < 2000, std::to_string(validatorMs));
}

/// Any pointerdown begins a press, trusted or not: a second finger's is neither trusted (it may become the chat's scroll)
/// nor claimed (`gesture` is the primary pointer's), so its release is a new gesture, not the one before it, spent.
TEST(an_untrusted_pointerdown_begins_a_new_gesture) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<button id='b'>go</button><script>TelegramWidget.ready(); var n = 0;"
                    "document.getElementById('b').addEventListener('pointerup', function () { TelegramWidget.openLink('https://example.com/up-' + (++n)); });</script>",
                    WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
    std::mutex m;
    std::vector<std::string> sent;
    host.onAction = [&](const WidgetAction& a) { std::lock_guard<std::mutex> l(m); if (a.kind != WidgetAction::Ready) sent.push_back(a.url); };
    host.start();
    CHECK(host.waitLoaded(20));
    auto event = [&](const std::string& type, bool trusted) {
        renderer->onMessage("{\"type\":\"event\",\"ev\":{\"type\":\"" + type + "\",\"targetId\":5,\"pointerId\":2,\"pointerType\":\"touch\",\"isPrimary\":false},\"trusted\":" + (trusted ? "true" : "false") + "}");
    };
    // a second finger's tap, its gesture spent in its pointerup handler; then another tap of it
    event("pointerdown", false);
    event("pointerup", true);
    event("pointerdown", false);
    event("pointerup", true);
    CHECK(host.waitUntil([&](const WidgetDiagnostics&) { std::lock_guard<std::mutex> l(m); return sent.size() >= 2; }, 5));
    sleepMs(100);
    std::lock_guard<std::mutex> l(m);
    CHECK_MSG(sent == std::vector<std::string>({"https://example.com/up-1", "https://example.com/up-2"}), [&] { std::string s; for (auto& x : sent) s += x + " "; return s; }());
}

// ───────────── SDK v1 through the real engine (docs/sdk-v1.md §2–§4) ─────────────

TEST(sdk_members_and_removed_apis) {
    Run r("<button>c</button><script>var out = [alert('a'), confirm('c'), prompt('p')];"
          "document.querySelector('button').onclick = function () {"
          "  navigator.clipboard.writeText('x').catch(function (e) { out.push(e.name); });"
          "  navigator.share({ text: 'x' }).catch(function (e) { out.push(e.name); }); };</script>");
    r.engine.activationEvent("{\"type\":\"click\",\"targetId\":5,\"detail\":1}", 1);
    r.barrier();
    CHECK_MSG(r.engine.probe("JSON.stringify(out)") == "[null,false,null,\"NotAllowedError\",\"NotAllowedError\"]", r.engine.probe("JSON.stringify(out)"));
    std::string members = r.engine.probe("[TelegramWidget.profile, TelegramWidget.isProfileAtLeast(1), TelegramWidget.isProfileAtLeast(2), TelegramWidget.colorScheme,"
                                         " TelegramWidget.isActive, typeof TelegramWidget.sendMessage, typeof TelegramWidget.on, Object.keys(TelegramWidget.themeParams).length].join()");
    CHECK_MSG(members == "1,true,false,light,true,undefined,undefined,15", members);
    auto l = r.host.lock();
    CHECK_MSG(r.host.bridge.empty() && r.host.actions.empty(), actionsText(r.host.actions));
    CHECK(!r.host.hasDiag("profile-mismatch"));
}

/// A tap on a button that calls openLink reaches onAction normalised; the second call in the same tap is
/// `activation-blocked`; invalid URLs throw TypeError in the widget and never reach the host.
TEST(sdk_link_tap_reaches_on_action) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<button>go</button><script>var errors = [];"
                    "function tryOpen(m, u) { try { TelegramWidget[m](u); } catch (e) { errors.push(m + ':' + e.name); } }"
                    "tryOpen('openLink', 'http://example.com/'); tryOpen('openLink', 'https://t.me/x'); tryOpen('openTelegramLink', 'tg://resolve?domain=x'); tryOpen('openLink', 42);"
                    "document.querySelector('button').addEventListener('click', function () { TelegramWidget.openLink('HTTPS://Example.com/\xD0\xBF\xD0\xBE\xD0\xB8\xD1\x81\xD0\xBA');"
                    " TelegramWidget.openLink('https://example.com/second'); });</script>",
                    WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
    std::mutex m;
    std::vector<WidgetAction> actions;
    host.onAction = [&](const WidgetAction& a) { std::lock_guard<std::mutex> l(m); actions.push_back(a); };
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK(host.probe("errors.join()") == "openLink:TypeError,openLink:TypeError,openTelegramLink:TypeError,openLink:TypeError");
    renderer->onMessage("{\"type\":\"event\",\"ev\":{\"type\":\"click\",\"targetId\":5,\"detail\":1},\"trusted\":true}");
    CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("activation-blocked") >= 1; }, 5));
    host.probe("0");            // the click task has run
    host.onHostThread([] {});   // and its actions reached the host thread
    std::lock_guard<std::mutex> l(m);
    CHECK_MSG(actions.size() == 1 && actions[0].kind == WidgetAction::OpenLink && actions[0].url == "https://example.com/%D0%BF%D0%BE%D0%B8%D1%81%D0%BA", actionsText(actions));
    CHECK(host.diagnostics().countDiag("activation-blocked") == 1);
    CHECK(host.diagnostics().countDiag("bridge-rejected") == 0);
}

/// `<a href>` taps and window.open go to openLink or openTelegramLink by host; http: and javascript: are ignored.
TEST(sdk_anchor_and_window_open_route_by_host) {
    Run r("<a href='https://example.com/a'>a</a><a href='https://t.me/durov'>t</a><a href='http://example.com/'>h</a><a href='javascript:void 0'>j</a>"
          "<button>o</button><script>var w = 1; document.querySelector('button').onclick = function () { w = window.open('https://telegram.me/x'); };</script>");
    r.barrier();
    std::vector<int> targets;
    {
        auto l = r.host.lock();
        for (const std::string tag : {"a", "button"})
            for (auto& batch : r.host.renderOps)
                if (auto ops = Json::parse(batch))
                    for (auto& op : ops->arr()) if (op["op"].asString() == "el" && op["tag"].asString() == tag) targets.push_back(op["id"].asInt());
    }
    CHECK(targets.size() == 5);
    int gesture = 0;
    for (int id : targets) r.engine.activationEvent("{\"type\":\"click\",\"targetId\":" + std::to_string(id) + ",\"detail\":1}", ++gesture);
    r.barrier();
    CHECK(r.engine.probe("w") == "null");
    auto l = r.host.lock();
    std::string got = actionsText(r.host.actions);
    CHECK_MSG(got == "{\"kind\":\"openLink\",\"url\":\"https://example.com/a\"} {\"kind\":\"openTelegramLink\",\"url\":\"https://t.me/durov\"} "
                     "{\"kind\":\"openTelegramLink\",\"url\":\"https://telegram.me/x\"} ", got);
    CHECK_MSG(r.host.countDiag("navigation-blocked") == 2, r.diagsText());
}

/// Haptics without a touch get one 1 s series (v0.2.2): seven impactOccurred in one task give 5 actions (the burst) and one
/// `haptic-limit`, a call 250 ms later plays (the bucket refilled, still in the series); a hidden widget plays nothing.
TEST(sdk_haptics_burst_and_limit) {
    Run r("<script>for (var i = 0; i < 7; i++) TelegramWidget.HapticFeedback.impactOccurred('medium');"
          "TelegramWidget.HapticFeedback.notificationOccurred('success').selectionChanged();</script>");
    r.barrier();
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.actions.size() == 5, actionsText(r.host.actions));
        if (!r.host.actions.empty()) CHECK(r.host.actions[0] == (WidgetAction{WidgetAction::Haptic, "", "impact", "medium"}));
        CHECK_MSG(r.host.countDiag("haptic-limit") == 1, r.diagsText());
    }
    sleepMs(250); // 2.5 tokens back
    CHECK(r.engine.probe("TelegramWidget.HapticFeedback.selectionChanged() === TelegramWidget.HapticFeedback") == "true");
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.actions.size() == 6 && r.host.actions[5] == (WidgetAction{WidgetAction::Haptic, "", "selection", ""}), actionsText(r.host.actions));
    }
    SandboxConfig hidden = testConfig();
    hidden.visible = false;
    Run h("<script>TelegramWidget.HapticFeedback.selectionChanged(); TelegramWidget.HapticFeedback.selectionChanged();</script>", hidden);
    h.barrier();
    auto l = h.host.lock();
    CHECK_MSG(h.host.actions.empty(), actionsText(h.host.actions));
    CHECK_MSG(h.host.countDiag("haptic-limit") == 1, h.diagsText());
}

/// The preload is readable from the first script line; each task commits the areas it changed, in full;
/// a commit near the quota passes the glue; lone surrogates round-trip as WTF-8.
TEST(sdk_storage_preload_and_commits) {
    SandboxConfig cfg = testConfig();
    cfg.storage.local = {{"count", "41"}};
    cfg.storage.session = {{"s", "kept"}};
    Run r("<button>b</button><script>"
          "var seen = localStorage.getItem('count') + '/' + sessionStorage.getItem('s') + '/' + localStorage.length;"
          "localStorage.setItem('count', String(Number(localStorage.getItem('count')) + 1)); localStorage.extra = 7;"
          "document.querySelector('button').onclick = function () { sessionStorage.setItem('s', 'tapped'); localStorage.setItem('count', localStorage.count); };</script>", cfg);
    CHECK(r.engine.probe("seen") == "41/kept/1");
    r.engine.event("{\"type\":\"click\",\"targetId\":5}");
    r.barrier();
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.storageCommits.size() == 2, std::to_string(r.host.storageCommits.size()) + " commits");
        if (r.host.storageCommits.size() == 2) {
            CHECK(r.host.storageCommits[0].first == "local" && r.host.storageCommits[0].second == StorageArea({{"count", "42"}, {"extra", "7"}}));
            CHECK(r.host.storageCommits[1].first == "session" && r.host.storageCommits[1].second == StorageArea({{"s", "tapped"}}));
        }
        CHECK(!r.host.hasDiag("storage-rejected"));
    }
    // 4 KB of UTF-8 per area: the shim throws past it, what it commits is within the glue's quota.
    Run q("<script>var out = []; localStorage.setItem('k', 'x'.repeat(4095));"
          "try { localStorage.setItem('k2', 'y'); } catch (e) { out.push(e.name, e.code, e instanceof DOMException); }"
          "sessionStorage.setItem('w', '\xD0\xB6'.repeat(2000)); sessionStorage.setItem('\\ud800', 'x\\udc00');</script>");
    CHECK(q.engine.probe("out.join()") == "QuotaExceededError,22,true");
    q.barrier();
    StorageArea session;
    {
        auto l = q.host.lock();
        CHECK_MSG(q.host.storageCommits.size() == 2 && !q.host.hasDiag("storage-rejected"), q.diagsText());
        if (q.host.storageCommits.size() == 2) {
            CHECK(sdk::storageBytes(q.host.storageCommits[0].second) == 4096);
            session = q.host.storageCommits[1].second;
            CHECK(session.count("\xED\xA0\x80") && session["\xED\xA0\x80"] == "x\xED\xB0\x80");
            CHECK(sdk::storageBytes(session) == 1 + 2 * 2000 + 3 + 4);
        }
    }
    SandboxConfig again = testConfig();
    again.storage.session = session;
    Run back("<p>x</p>", again);
    CHECK_MSG(back.engine.probe("sessionStorage.getItem('\\ud800') === 'x\\udc00' && sessionStorage.getItem('w').length") == "2000", back.diagsText());
}

TEST(sdk_storage_preload_over_quota_is_rejected) {
    SandboxConfig cfg = testConfig();
    cfg.storage.local = {{"a", std::string(20000, 'x')}};
    cfg.storage.session = {{"b", "1"}};
    Run r("<p>x</p>", cfg);
    CHECK(r.engine.probe("localStorage.length + sessionStorage.length") == "0");
    auto l = r.host.lock();
    CHECK_MSG(r.host.countDiag("storage-rejected") == 1, r.diagsText());
    CHECK(r.host.storageCommits.empty());
}

/// themeParams from the host reach the widget checked (the rest from the scheme's defaults); a theme
/// change updates colorScheme and themeParams and fires themeChanged and the legacy onTheme once.
TEST(sdk_theme_params_reach_the_widget) {
    SandboxConfig cfg = testConfig();
    cfg.themeParams = {{"bg_color", "#ABCDEF"}, {"text_color", "red"}, {"made_up", "#123456"}};
    Run r("<script>var log = [], first = TelegramWidget.themeParams;"
          "TelegramWidget.onEvent('themeChanged', function () { log.push('changed:' + TelegramWidget.colorScheme + ':' + TelegramWidget.themeParams.bg_color + ':' + (this === TelegramWidget)); });"
          "TelegramWidget.onTheme(function (t) { log.push('onTheme:' + t); });</script>", cfg);
    CHECK(r.engine.probe("TelegramWidget.themeParams.bg_color + ',' + TelegramWidget.themeParams.text_color + ',' + ('made_up' in TelegramWidget.themeParams)") == "#abcdef,#000000,false");
    r.engine.theme("light", {{"bg_color", "#abcdef"}});
    r.engine.theme("dark", {{"bg_color", "#101010"}});
    r.barrier();
    CHECK_MSG(r.engine.probe("log.join('|')") == "onTheme:light|changed:dark:#101010:true|onTheme:dark", r.engine.probe("log.join('|')"));
    CHECK(r.engine.probe("first === TelegramWidget.themeParams") == "false");
    CHECK(r.engine.probe("TelegramWidget.themeParams.text_color") == "#ffffff");
}

TEST(sdk_locale_reaches_the_widget) {
    SandboxConfig cfg = testConfig();
    cfg.locale = "pt-BR";
    Run r("<p>x</p>", cfg);
    CHECK(r.engine.probe("navigator.language + ',' + navigator.languages.join()") == "pt-BR,pt-BR");
    SandboxConfig bad = testConfig();
    bad.locale = "ru_RU";
    Run b("<p>x</p>", bad);
    CHECK(b.engine.probe("navigator.language") == "en-US");
}

/// WidgetHost: the renderer's first message is the theme (scheme, checked params, button and text tokens as given),
/// and every setTheme sends another; the widget gets the scheme, the palette and the locale; the renderer's
/// gesture report becomes onGesture with its x and hold (missing or not a boolean: false); `y` is false whatever the
/// renderer sends (v0.1.3: vertical drags are the chat's), and a report with only `wants` claims nothing.
TEST(sdk_widget_host_theme_locale_and_gestures) {
    ScriptedRenderer* renderer = nullptr;
    WidgetTheme theme;
    theme.colorScheme = "dark";
    theme.params = {{"bg_color", "#ABCDEF"}, {"link_color", "blue"}};
    theme.button.radius = "10px";
    theme.button.pressedOpacity = 0.7;
    theme.text.fontSize = 17;
    theme.text.padding = "6px 10px";
    WidgetHostOptions options;
    options.locale = "ru";
    options.storage.local = {{"k", "v"}};
    WidgetHost host("<button>b</button><script>document.querySelector('button').onclick = function () { localStorage.k = 'w'; };</script>", theme, 300, options, RES, scripted(renderer));
    std::mutex m;
    std::vector<WidgetGestureClaim> claims;
    std::vector<std::pair<std::string, StorageArea>> commits;
    host.onGesture = [&](const WidgetGestureClaim& claim) { std::lock_guard<std::mutex> l(m); claims.push_back(claim); };
    host.onStorageCommit = [&](const std::string& area, const StorageArea& values) { std::lock_guard<std::mutex> l(m); commits.emplace_back(area, values); };
    host.start();
    CHECK(host.waitLoaded(20));
    {
        std::lock_guard<std::mutex> l(renderer->mutex);
        CHECK_MSG(!renderer->messages.empty() && renderer->messages[0] == "{\"type\":\"theme\",\"theme\":{\"colorScheme\":\"dark\",\"params\":{\"bg_color\":\"#abcdef\"},\"button\":{\"radius\":\"10px\",\"pressedOpacity\":0.7},\"text\":{\"fontSize\":17,\"padding\":\"6px 10px\"}}}",
                  renderer->messages.empty() ? "nothing sent" : renderer->messages[0]);
    }
    CHECK(host.probe("TelegramWidget.colorScheme + ',' + TelegramWidget.themeParams.bg_color + ',' + TelegramWidget.themeParams.link_color + ',' + navigator.language + ',' + localStorage.k") ==
          "dark,#abcdef,#8774e1,ru,v");
    WidgetTheme light;
    light.colorScheme = "Dark"; // anything but exactly "dark" is light
    light.params = {{"bg_color", "#FFFFFF"}};
    host.setTheme(light);
    host.onHostThread([] {});
    CHECK(host.probe("TelegramWidget.colorScheme + ',' + TelegramWidget.themeParams.bg_color") == "light,#ffffff");
    auto themes = renderer->sent("theme");
    CHECK_MSG(themes.size() == 2 && themes[1].dump() == "{\"type\":\"theme\",\"theme\":{\"colorScheme\":\"light\",\"params\":{\"bg_color\":\"#ffffff\"},\"button\":{},\"text\":{}}}",
              themes.empty() ? "none" : themes.back().dump());
    {
        std::lock_guard<std::mutex> l(renderer->mutex);
        CHECK(renderer->themes == std::vector<std::string>({"light"}));
    }
    // What the renderer core reports for a press on `touch-action: none`, on `auto` (and `pan-x`) and on `pan-y`.
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false,\"hold\":true}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":false,\"y\":false,\"hold\":false}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false,\"hold\":false}");
    // What a v0.1.2 renderer core reported for `none` and for `pan-x`: `y` no longer reaches the client.
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":true,\"x\":true,\"y\":true,\"hold\":true}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":true,\"x\":false,\"y\":true,\"hold\":false}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false}"); // a renderer before hold
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":true}"); // a renderer that only knows `wants` (the native prototypes)
    renderer->onMessage("{\"type\":\"gesture\",\"x\":1,\"y\":\"true\",\"hold\":1}");
    renderer->onMessage("{\"type\":\"event\",\"ev\":{\"type\":\"click\",\"targetId\":5,\"detail\":1},\"trusted\":true}");
    CHECK(host.waitUntil([&](const WidgetDiagnostics& d) { return d.storageCommits > 0; }, 5));
    host.destroy();
    std::lock_guard<std::mutex> l(m);
    CHECK((claims == std::vector<WidgetGestureClaim>{
               {true, false, true}, {false, false, false}, {true, false, false}, {true, false, true}, {}, {true, false, false}, {}, {}}));
    CHECK(commits.size() == 1 && commits[0].first == "local" && commits[0].second == StorageArea({{"k", "w"}}));
}

/// The client's chrome over the widget (docs/sdk-v1.md §2.17): the option reaches the renderer right after the theme and
/// the widget from its first line (checked by the shim: out of range is 0); setContentSafeArea sends the variables and
/// one contentSafeAreaChanged with a fresh frozen object; the same value fires nothing; NaN reaches nobody as NaN.
TEST(sdk_content_safe_area_reaches_the_renderer_and_the_widget) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHostOptions options;
    options.contentSafeArea.bottom = 24;
    options.contentSafeArea.cornerRight = 72;
    options.contentSafeArea.top = 99; // over 64: 0 for the widget
    WidgetHost host("<script>var log = [], first = TelegramWidget.contentSafeAreaInset;"
                    "TelegramWidget.onEvent('contentSafeAreaChanged', function () { var a = TelegramWidget.contentSafeAreaInset;"
                    " log.push([a.bottom, a.cornerLeft, a.cornerRight, Object.isFrozen(a), a !== first, this === TelegramWidget].join(':')); });</script>",
                    WidgetTheme{}, 300, options, RES, scripted(renderer));
    host.start();
    CHECK(host.waitLoaded(20));
    {
        std::lock_guard<std::mutex> l(renderer->mutex);
        CHECK_MSG(renderer->messages.size() > 1 && renderer->messages[1] == "{\"type\":\"safeArea\",\"inset\":{\"top\":99,\"right\":0,\"bottom\":24,\"left\":0,\"cornerLeft\":0,\"cornerRight\":72}}",
                  renderer->messages.size() > 1 ? renderer->messages[1] : "nothing sent");
    }
    CHECK_MSG(host.probe("JSON.stringify(first)") == "{\"top\":0,\"right\":0,\"bottom\":24,\"left\":0,\"cornerLeft\":0,\"cornerRight\":72}", host.probe("JSON.stringify(first)"));
    WidgetContentSafeArea rtl;
    rtl.bottom = 26.5;
    rtl.cornerLeft = 80;
    host.setContentSafeArea(rtl);
    host.setContentSafeArea(rtl);
    WidgetContentSafeArea broken;
    broken.bottom = std::nan("");
    host.setContentSafeArea(broken);
    host.onHostThread([] {});
    CHECK_MSG(host.probe("log.join('|')") == "26.5:80:0:true:true:true|0:0:0:true:true:true", host.probe("log.join('|')"));
    auto sent = renderer->sent("safeArea");
    CHECK_MSG(sent.size() == 4 && sent[3].dump() == "{\"type\":\"safeArea\",\"inset\":{\"top\":0,\"right\":0,\"bottom\":0,\"left\":0,\"cornerLeft\":0,\"cornerRight\":0}}",
              sent.empty() ? "none" : sent.back().dump());
    host.destroy();
}

/// The API before SDK v1 still compiles and forwards: a string theme, setTheme(string), the raw onBridgeCall; and the
/// one before v0.1.2: onGestureClaim gets the claim's x and y right after onGesture.
TEST(sdk_deprecated_host_api_forwards) {
#if defined(_MSC_VER)
#pragma warning(push)
#pragma warning(disable : 4996)
#else
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#endif
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<script>TelegramWidget.ready(); TelegramWidget.HapticFeedback.impactOccurred('soft');</script>", "dark", 300, WidgetHostOptions(), RES, scripted(renderer));
    std::mutex m;
    std::vector<std::pair<std::string, std::string>> raw;
    std::vector<std::string> gestures;
    host.onBridgeCall = [&](const std::string& method, const std::string& args) { std::lock_guard<std::mutex> l(m); raw.emplace_back(method, args); };
    host.onGesture = [&](const WidgetGestureClaim& c) {
        std::lock_guard<std::mutex> l(m);
        gestures.push_back("onGesture " + std::to_string(c.x) + std::to_string(c.y) + std::to_string(c.hold));
    };
    host.onGestureClaim = [&](bool x, bool y) { std::lock_guard<std::mutex> l(m); gestures.push_back("onGestureClaim " + std::to_string(x) + std::to_string(y)); };
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK(host.probe("TelegramWidget.theme + ',' + TelegramWidget.themeParams.bg_color") == "dark,#212121");
    host.setTheme("light");
    host.onHostThread([] {});
    CHECK(host.probe("TelegramWidget.colorScheme") == "light");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false,\"hold\":true}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":true,\"x\":true,\"y\":true,\"hold\":false}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":true}");
    host.onHostThread([] {});
    std::lock_guard<std::mutex> l(m);
    CHECK_MSG((raw == std::vector<std::pair<std::string, std::string>>{{"ready", "[]"}, {"haptic", "[\"impact\",\"soft\"]"}}), std::to_string(raw.size()));
    // `y` is always false since v0.1.3, for the deprecated callback too.
    CHECK((gestures == std::vector<std::string>{"onGesture 101", "onGestureClaim 10", "onGesture 100", "onGestureClaim 10", "onGesture 000", "onGestureClaim 00"}));
    CHECK(host.diagnostics().actions.size() == 2);
#if defined(_MSC_VER)
#pragma warning(pop)
#else
#pragma GCC diagnostic pop
#endif
}

/// TelegramWidget.setResizeAnchor (docs/sdk-v1.md §2.12, v0.1.3) through the real engine: anything but exactly 'top' or
/// 'bottom' throws TypeError and sends nothing; only a change reaches the delegate (`onResizeAnchor`), with no gesture
/// and on a hidden widget, and never as an action or a raw bridge call.
TEST(sdk_resize_anchor_reaches_the_delegate) {
    {
        Run r("<script>var errs = [];"
              "[undefined, null, 'left', 'Bottom', ' top', '', 1, ['top'], { edge: 'top' }].forEach(function (v) {"
              "  try { TelegramWidget.setResizeAnchor(v); errs.push('ok'); } catch (e) { errs.push(e instanceof TypeError ? 'TypeError' : String(e)); } });</script>");
        CHECK(r.engine.probe("typeof TelegramWidget.setResizeAnchor") == "function");
        CHECK_MSG(r.engine.probe("errs.join()") == "TypeError,TypeError,TypeError,TypeError,TypeError,TypeError,TypeError,TypeError,TypeError", r.engine.probe("errs.join()"));
        CHECK(r.engine.probe("String(TelegramWidget.setResizeAnchor('top'))") == "undefined");
        r.barrier();
        auto l = r.host.lock();
        CHECK(r.host.resizeAnchors.empty() && r.host.bridge.empty() && r.host.actions.empty());
        CHECK_MSG(!r.host.hasDiag("bridge-rejected"), r.diagsText());
    }
    SandboxConfig hidden = testConfig();
    hidden.visible = false;
    Run r("<script>TelegramWidget.setResizeAnchor('top'); TelegramWidget.setResizeAnchor('bottom');"
          "for (var i = 0; i < 1000; i++) TelegramWidget.setResizeAnchor('bottom');"
          "TelegramWidget.setResizeAnchor('top'); TelegramWidget.setResizeAnchor('bottom');</script>", hidden);
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG((r.host.resizeAnchors == std::vector<std::string>{"bottom", "top", "bottom"}), std::to_string(r.host.resizeAnchors.size()));
    CHECK_MSG(r.host.actions.empty() && r.host.bridge.empty(), actionsText(r.host.actions));
    CHECK_MSG(!r.host.hasDiag("activation-blocked") && !r.host.hasDiag("bridge-rejected"), r.diagsText());
}

/// A renderer that answers every ops batch with a size report 40 px taller than the last, as a document growing by a row.
struct GrowingRenderer final : Renderer {
    void load() override { onMessage("{\"type\":\"ready\"}"); }
    void send(const std::string& json) override {
        auto m = Json::parse(json);
        const std::string type = m ? (*m)["type"].asString() : "";
        if (type == "query") onMessage("{\"type\":\"result\",\"qid\":" + std::to_string((*m)["qid"].asInt()) + ",\"result\":null}");
        if (type == "ops") {
            height += 40;
            onMessage("{\"type\":\"size\",\"height\":" + std::to_string(height) + ",\"innerWidth\":300,\"innerHeight\":300,\"dpr\":1}");
        }
    }
    void setTheme(const std::string&) override {}
    void setWidth(double) override {}
    void destroy() override {}
    int height = 60;
};

/// WidgetHost keeps the widget's latest resize anchor in its diagnostics, next to the height: `top` until the widget calls
/// setResizeAnchor; a tap that sets `bottom` and adds a row shows the new height with `bottom` already in place (the
/// bridge call reaches the host state before the size report of the same task's ops); `top` again goes back; no action,
/// no raw bridge call, no gesture needed, on a hidden widget too. An equal value changes nothing.
TEST(sdk_resize_anchor_is_host_state_next_to_the_height) {
    std::mutex m;
    std::vector<std::pair<double, std::string>> heights; // each new height with the anchor of that moment
    int anchorChanges = 0;
    std::string lastAnchor = "top";
    double lastHeight = 0;
    WidgetHost host("<button>grow</button><script>var n = 0; document.querySelector('button').onclick = function () {"
                    "  n++; TelegramWidget.setResizeAnchor(n % 2 ? 'bottom' : 'top');"
                    "  var p = document.createElement('p'); p.textContent = 'row ' + n; document.body.appendChild(p); };</script>",
                    WidgetTheme{}, 300, WidgetHostOptions(), RES,
                    [](std::shared_ptr<SandboxResources>, const std::string&, double) -> std::unique_ptr<Renderer> { return std::make_unique<GrowingRenderer>(); });
    host.onChange = [&](const WidgetDiagnostics& d) {
        std::lock_guard<std::mutex> l(m);
        if (d.height != lastHeight) heights.emplace_back(lastHeight = d.height, d.resizeAnchor);
        if (d.resizeAnchor != lastAnchor) { lastAnchor = d.resizeAnchor; anchorChanges++; }
    };
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK(host.waitRendered(5));
    const double base = host.diagnostics().height; // after the boot's batches
    CHECK(base >= 100 && host.diagnostics().resizeAnchor == "top");
    // Untrusted clicks: no gesture. The third one on a hidden widget.
    auto tap = [&] { host.renderer().onMessage("{\"type\":\"event\",\"ev\":{\"type\":\"click\",\"targetId\":5,\"detail\":1}}"); };
    tap();
    CHECK(host.waitUntil([&](const WidgetDiagnostics& d) { return d.height >= base + 40; }, 5));
    CHECK(host.diagnostics().resizeAnchor == "bottom");
    tap();
    CHECK(host.waitUntil([&](const WidgetDiagnostics& d) { return d.height >= base + 80; }, 5));
    CHECK(host.diagnostics().resizeAnchor == "top");
    host.setVisible(false);
    tap();
    CHECK(host.waitUntil([&](const WidgetDiagnostics& d) { return d.height >= base + 120; }, 5));
    WidgetDiagnostics d = host.diagnostics();
    CHECK(d.resizeAnchor == "bottom");
    CHECK_MSG(d.actions.empty() && d.bridge.empty(), actionsText(d.actions));
    CHECK(d.countDiag("bridge-rejected") == 0 && d.countDiag("activation-blocked") == 0);
    // The host's own guard (the shim already sends only changes): an equal value is no change.
    SandboxEngine unused("", testConfig(), RES);
    host.onResizeAnchor(unused, "bottom");
    host.onHostThread([] {});
    {
        std::lock_guard<std::mutex> l(m);
        // The boot's heights with `top`, then one per tap, each with its tap's anchor already in place.
        std::vector<std::pair<double, std::string>> taps(heights.end() - std::min<size_t>(heights.size(), 3), heights.end());
        CHECK_MSG(heights.size() >= 4 && heights[heights.size() - 4] == std::make_pair(base, std::string("top")) &&
                      (taps == std::vector<std::pair<double, std::string>>{{base + 40, "bottom"}, {base + 80, "top"}, {base + 120, "bottom"}}),
                  [&] { std::string s; for (auto& [h, a] : heights) s += std::to_string(int(h)) + ":" + a + " "; return s; }());
        CHECK_MSG(anchorChanges == 3, std::to_string(anchorChanges));
    }
    host.destroy();
    // A widget that never calls it: top.
    WidgetHost plain("<p>x</p>", WidgetTheme{}, 300, WidgetHostOptions(), RES,
                     [](std::shared_ptr<SandboxResources>, const std::string&, double) -> std::unique_ptr<Renderer> { return std::make_unique<GrowingRenderer>(); });
    plain.start();
    CHECK(plain.waitLoaded(20));
    CHECK(plain.waitRendered(5));
    CHECK(plain.diagnostics().resizeAnchor == "top" && plain.diagnostics().height > 0);
}

// ───────────── The bot's data and requests through the real engine and WidgetHost (docs/sdk-v1.md §2.13–§2.15) ─────────────

static const char* kShowContext = "JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.isForward, TelegramWidget.chatType, TelegramWidget.viewer])";

/// A button whose tap asks twice (the shim forwards both, the glue's gate refuses the second as BUSY); `mode = 'copy'` makes it copy twice instead. The log
/// shows each settled request and each `chatDataChanged` / `publicDataChanged`.
static const char* kRequester =
    "<button id='b'>go</button><script>var log = [], mode = 'ask';"
    "function ask(d) { TelegramWidget.request(d).then(function (a) { log.push('ok:' + d + ':' + JSON.stringify(a) + ':' + Object.isFrozen(a)); },"
    "  function (e) { log.push('err:' + d + ':' + e.name + ':' + e.code); }); }"
    "TelegramWidget.onEvent('chatDataChanged', function () { log.push('chat:' + TelegramWidget.chatData); });"
    "TelegramWidget.onEvent('publicDataChanged', function () { log.push('public:' + TelegramWidget.publicData); });"
    "document.getElementById('b').addEventListener('click', function () {"
    "  if (mode === 'ask') { ask('tap'); ask('tap2'); } else { TelegramWidget.copyText('PROMO-2026 \\u2713'); TelegramWidget.copyText('second'); } });</script>";
static const std::string kTapOnButton = "{\"type\":\"click\",\"targetId\":5,\"detail\":1}";

using Respond = std::function<void(const WidgetRequestOutcome&)>;

/// The server's answer: only the result.
static WidgetRequestOutcome answered(std::optional<std::string> result) {
    WidgetRequestOutcome o;
    o.answer.result = std::move(result);
    return o;
}

/// The server's flags for a kind of message (WIDGET_MESSAGE_KINDS in sdk-policy.ts).
static WidgetContext messageContext(bool isCopy, bool canQuery, bool backgroundQuery, std::string chatType = "", std::optional<WidgetViewer> viewer = std::nullopt) {
    WidgetContext c;
    c.isCopy = isCopy;
    c.canQuery = canQuery;
    c.backgroundQuery = backgroundQuery;
    c.chatType = std::move(chatType);
    c.viewer = viewer;
    return c;
}

/// Polls a probe until it gives `expected` (an answer may come from another thread); returns the last value.
template <typename Engine>
static std::string probeUntil(Engine& engine, const std::string& code, const std::string& expected, double timeoutSeconds = 5) {
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(int(timeoutSeconds * 1000));
    std::string got = engine.probe(code);
    while (got != expected && std::chrono::steady_clock::now() < deadline) {
        sleepMs(10);
        got = engine.probe(code);
    }
    return got;
}

/// Polls state a test's callbacks keep under their own lock.
static bool eventually(const std::function<bool()>& pred, double timeoutSeconds = 5) {
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(int(timeoutSeconds * 1000));
    while (!pred()) {
        if (std::chrono::steady_clock::now() > deadline) return false;
        sleepMs(10);
    }
    return true;
}

static std::vector<std::string> diagsOf(RecordingHost& host, const std::string& kind) {
    auto l = host.lock();
    std::vector<std::string> out;
    for (auto& d : host.diags) if (d.first == kind) out.push_back(d.second);
    return out;
}

static std::vector<WidgetRequest> requestsOf(RecordingHost& host) {
    auto l = host.lock();
    return host.requests;
}

/// chatData, publicData, chatType and viewer come from the client, checked, with real JSON nulls for none: an original
/// sees them all; a live copy (`isCopy`, `canQuery`) sees the viewer but no chatData and no chat type (round 5: nothing
/// about where it was forwarded); a plain copy (`isCopy` only) only publicData; an original without `canQuery` (sent
/// without allow_queries) its data and chat but no viewer. What breaks the rules (over 1024 bytes, a NUL) is none and
/// reported.
TEST(sdk_bot_data_and_context_at_boot) {
    SandboxConfig cfg = testConfig();
    cfg.data = {std::string("{\"turn\":1}"), std::string("score 3:1")};
    cfg.context = messageContext(false, true, false, "group", WidgetViewer{42});
    Run r("<p>x</p>", cfg);
    CHECK_MSG(r.engine.probe(kShowContext) == "[\"{\\\"turn\\\":1}\",\"score 3:1\",false,\"group\",{\"id\":42}]", r.engine.probe(kShowContext));
    CHECK(r.engine.probe("[Object.isFrozen(TelegramWidget.viewer), TelegramWidget.viewer === TelegramWidget.viewer, typeof TelegramWidget.request, typeof TelegramWidget.copyText].join()") ==
          "true,true,function,function");
    CHECK(diagsOf(r.host, "data-rejected").empty());
    SandboxConfig live = cfg;
    live.data.chatData = "secret";
    live.context = messageContext(true, true, false, "supergroup", WidgetViewer{42});
    Run l("<p>x</p>", live);
    CHECK_MSG(l.engine.probe(kShowContext) == "[null,\"score 3:1\",true,null,{\"id\":42}]", l.engine.probe(kShowContext));
    CHECK(diagsOf(l.host, "data-rejected").empty()); // a copy's chatData is dropped, not rejected
    SandboxConfig copy = live;
    copy.context = messageContext(true, false, false, "supergroup", WidgetViewer{42});
    Run c("<p>x</p>", copy);
    CHECK_MSG(c.engine.probe(kShowContext) == "[null,\"score 3:1\",true,null,null]", c.engine.probe(kShowContext));
    SandboxConfig quiet = cfg;
    quiet.context = messageContext(false, false, false, "private", WidgetViewer{42});
    Run q("<p>x</p>", quiet);
    CHECK_MSG(q.engine.probe(kShowContext) == "[\"{\\\"turn\\\":1}\",\"score 3:1\",false,\"private\",null]", q.engine.probe(kShowContext));
    SandboxConfig none = testConfig();
    none.data = {std::string(""), std::string(1025, 'x')};
    none.context = messageContext(false, false, false, "sender", WidgetViewer{0});
    Run n("<p>x</p>", none);
    CHECK_MSG(n.engine.probe(kShowContext) == "[null,null,false,null,null]", n.engine.probe(kShowContext));
    CHECK((diagsOf(n.host, "data-rejected") == std::vector<std::string>{"publicData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"}));
    SandboxConfig nul = testConfig();
    nul.data = {std::string("a\0b", 3), std::string("tab\tok")};
    Run z("<p>x</p>", nul);
    CHECK_MSG(z.engine.probe("JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData])") == "[null,\"tab\\tok\"]", z.engine.probe("JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData])"));
    CHECK((diagsOf(z.host, "data-rejected") == std::vector<std::string>{"chatData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"}));
    SandboxConfig big = testConfig();
    big.context = messageContext(false, true, false, "private", WidgetViewer{sdk::MAX_VIEWER_ID});
    Run b("<p>x</p>", big);
    CHECK(b.engine.probe("TelegramWidget.viewer.id === 9007199254740991") == "true");
}

/// The viewer (round 6) reaches the widget as Mini Apps' `WebAppUser`, only with canQuery: a private original with every
/// field boots with exactly {id, first_name, last_name, username, is_premium}, frozen, nothing rejected; a group
/// original with a broken username and no Premium boots with {id, first_name} and one `data-rejected` for the username;
/// an original without canQuery boots with null (and reports nothing).
TEST(sdk_viewer_is_a_web_app_user) {
    const std::string show = "JSON.stringify(TelegramWidget.viewer) + ' ' + Object.isFrozen(TelegramWidget.viewer)";
    WidgetViewer ann{42};
    ann.firstName = "Ann";
    ann.lastName = "Lee";
    ann.username = "ann_lee";
    ann.isPremium = true;
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, true, "private", ann);
    Run p("<p>x</p>", cfg);
    const std::string full = "{\"id\":42,\"first_name\":\"Ann\",\"last_name\":\"Lee\",\"username\":\"ann_lee\",\"is_premium\":true} true";
    CHECK_MSG(p.engine.probe(show) == full, p.engine.probe(show));
    CHECK(diagsOf(p.host, "data-rejected").empty());
    WidgetViewer broken{42};
    broken.firstName = "Ann";
    broken.username = "a-b";
    broken.isPremium = false;
    cfg.context = messageContext(false, true, false, "group", broken);
    Run g("<p>x</p>", cfg);
    CHECK_MSG(g.engine.probe(show) == "{\"id\":42,\"first_name\":\"Ann\"} true", g.engine.probe(show));
    CHECK((diagsOf(g.host, "data-rejected") == std::vector<std::string>{"viewer.username: not 5–32 characters of A–Z, a–z, 0–9 and _; the widget does not see it"}));
    cfg.context = messageContext(false, false, false, "private", ann);
    Run q("<p>x</p>", cfg);
    CHECK_MSG(q.engine.probe("String(TelegramWidget.viewer)") == "null", q.engine.probe("String(TelegramWidget.viewer)"));
    CHECK(diagsOf(q.host, "data-rejected").empty());
    // The same through WidgetHost.
    ScriptedRenderer* renderer = nullptr;
    WidgetHostOptions options;
    options.context = messageContext(false, true, false, "group", broken);
    WidgetHost host("<p>x</p>", WidgetTheme{}, 300, options, RES, scripted(renderer));
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK_MSG(host.probe(show) == "{\"id\":42,\"first_name\":\"Ann\"} true", host.probe(show));
    CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("data-rejected") == 1; }, 5));
}

/// watchPublicData (round 7) through the real engine: `watchPublicData(); watchPublicData();` then later
/// `unwatchPublicData(); watchPublicData();` reaches the delegate as true, false, true (the shim sends only changes), with
/// no gesture, on a hidden widget too, never as an action or a raw call; a widget that never calls it sends nothing; a
/// preview's call is `preview-blocked`.
TEST(sdk_watch_public_data_reaches_the_delegate) {
    SandboxConfig hidden = testConfig();
    hidden.visible = false;
    Run r("<script>TelegramWidget.watchPublicData(); TelegramWidget.watchPublicData();"
          "setTimeout(function () { TelegramWidget.unwatchPublicData(); TelegramWidget.watchPublicData(); }, 10);</script>", hidden);
    CHECK(r.host.waitUntil([&] { return r.host.publicDataWatches.size() >= 3; }, 5));
    r.barrier();
    {
        auto l = r.host.lock();
        CHECK((r.host.publicDataWatches == std::vector<bool>{true, false, true}));
        CHECK_MSG(r.host.actions.empty() && r.host.bridge.empty(), actionsText(r.host.actions));
        CHECK_MSG(!r.host.hasDiag("bridge-rejected") && !r.host.hasDiag("activation-blocked"), r.diagsText());
    }
    Run n("<p>x</p>");
    n.barrier();
    {
        auto l = n.host.lock();
        CHECK(n.host.publicDataWatches.empty());
    }
    SandboxConfig preview = testConfig();
    preview.context.preview = true;
    Run p("<script>TelegramWidget.watchPublicData();</script>", preview);
    p.barrier();
    auto l = p.host.lock();
    CHECK(p.host.publicDataWatches.empty());
    CHECK_MSG(p.host.countDiag("preview-blocked") == 1, p.diagsText());
}

/// WidgetHost keeps whether the widget watches its public_data in its diagnostics (false at start) and calls
/// onWatchPublicData only on a change: true, false, true, ending with true; a widget that never calls it: false, no
/// callback; a preview: false, one `preview-blocked`.
TEST(sdk_widget_host_watches_public_data) {
    auto make = [](const std::string& html, WidgetHostOptions options) {
        ScriptedRenderer* renderer = nullptr;
        return std::make_unique<WidgetHost>(html, WidgetTheme{}, 300, options, RES, scripted(renderer));
    };
    std::mutex m;
    std::vector<bool> changes;
    auto host = make("<button>b</button><script>TelegramWidget.watchPublicData(); TelegramWidget.watchPublicData();"
                     "document.querySelector('button').onclick = function () { TelegramWidget.unwatchPublicData(); TelegramWidget.watchPublicData(); };</script>",
                     WidgetHostOptions());
    host->onWatchPublicData = [&](bool on) { std::lock_guard<std::mutex> l(m); changes.push_back(on); };
    host->start();
    CHECK(host->waitLoaded(20));
    CHECK(host->waitUntil([](const WidgetDiagnostics& d) { return d.watchesPublicData; }, 5));
    host->event(kTapOnButton);
    CHECK(eventually([&] { std::lock_guard<std::mutex> l(m); return changes.size() >= 3; }));
    host->probe("0");
    host->onHostThread([] {});
    {
        std::lock_guard<std::mutex> l(m);
        CHECK((changes == std::vector<bool>{true, false, true}));
    }
    WidgetDiagnostics d = host->diagnostics();
    CHECK(d.watchesPublicData && d.countDiag("bridge-rejected") == 0);
    CHECK_MSG(d.actions.empty() && d.bridge.empty(), actionsText(d.actions));
    // The host's own guard: an equal value is no change.
    SandboxEngine unused("", testConfig(), RES);
    host->onPublicDataWatch(unused, true);
    host->onHostThread([] {});
    {
        std::lock_guard<std::mutex> l(m);
        CHECK(changes.size() == 3);
    }
    host.reset();
    bool called = false;
    auto plain = make("<p>x</p>", WidgetHostOptions());
    plain->onWatchPublicData = [&](bool) { called = true; };
    plain->start();
    CHECK(plain->waitLoaded(20));
    plain->probe("0");
    plain->onHostThread([] {});
    CHECK(!plain->diagnostics().watchesPublicData && !called);
    WidgetHostOptions previewOptions;
    previewOptions.context.preview = true;
    auto preview = make("<script>TelegramWidget.watchPublicData();</script>", previewOptions);
    preview->onWatchPublicData = [&](bool) { called = true; };
    preview->start();
    CHECK(preview->waitLoaded(20));
    CHECK(preview->waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("preview-blocked") == 1; }, 5));
    CHECK(!preview->diagnostics().watchesPublicData && !called);
}

/// setData: the full state; `chatDataChanged` then `publicDataChanged` for each field that changed (a missing field is
/// none), both after both fields are set; equal data fires nothing; a field over the limit is none and reported. A copy
/// keeps no chatData and its publicData can change.
TEST(sdk_set_data_fires_chat_then_public_data_changed) {
    SandboxConfig cfg = testConfig();
    cfg.data = {std::string("a"), std::string("p")};
    const char* html = "<script>var log = []; ['chatDataChanged', 'publicDataChanged'].forEach(function (t) { TelegramWidget.onEvent(t, function () {"
                       "  log.push(t + ':' + TelegramWidget.chatData + '|' + TelegramWidget.publicData + ':' + (this === TelegramWidget)); }); });</script>";
    Run r(html, cfg);
    r.engine.setData({std::string("a"), std::string("p")});
    r.engine.setData({std::string("b"), std::string("q")});
    r.engine.setData({std::nullopt, std::string("q")});
    r.engine.setData({std::string(1025, 'x'), std::string("q")});
    r.barrier();
    const std::string expected = "chatDataChanged:b|q:true publicDataChanged:b|q:true chatDataChanged:null|q:true";
    CHECK_MSG(r.engine.probe("log.join(' ')") == expected, r.engine.probe("log.join(' ')"));
    CHECK((diagsOf(r.host, "data-rejected") == std::vector<std::string>{"chatData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"}));
    SandboxConfig copy = testConfig();
    copy.data.publicData = "p";
    copy.context.isCopy = true;
    Run c(html, copy);
    c.engine.setData({std::string("c"), std::string("p2")});
    c.barrier();
    CHECK_MSG(c.engine.probe("log.join(' ')") == "publicDataChanged:null|p2:true", c.engine.probe("log.join(' ')"));
    CHECK(diagsOf(c.host, "data-rejected").empty());
}

/// A tap sends one request (the client gets only its data) where the message allows requests: an original in a group
/// (`canQuery`) and a live copy (`isCopy`, `canQuery`); the answer resolves with { result }; the second call of the tap
/// is BUSY in the glue's gate and never reaches the client; after the answer the next tap can ask again. The recording
/// host answers on the engine thread inside the delivery, so the answer is queued before the gate's refusal.
TEST(sdk_request_tap_answer_and_busy) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, false, "group");
    Run r(kRequester, cfg, false);
    int n = 0;
    r.host.onRequest = [&n](const WidgetRequest&, Respond respond) { respond(answered(++n == 1 ? "thanks" : "again")); };
    r.engine.start();
    r.host.waitLoaded(20);
    r.engine.activationEvent(kTapOnButton, 1);
    const std::string first = "ok:tap:{\"result\":\"thanks\"}:true err:tap2:WidgetRequestError:BUSY";
    CHECK_MSG(probeUntil(r.engine, "log.join(' ')", first) == first, r.engine.probe("log.join(' ')"));
    CHECK((requestsOf(r.host) == std::vector<WidgetRequest>{{"tap"}}));
    r.engine.activationEvent(kTapOnButton, 2);
    const std::string second = first + " ok:tap:{\"result\":\"again\"}:true err:tap2:WidgetRequestError:BUSY";
    CHECK_MSG(probeUntil(r.engine, "log.join(' ')", second) == second, r.engine.probe("log.join(' ')"));
    CHECK((requestsOf(r.host) == std::vector<WidgetRequest>{{"tap"}, {"tap"}}));
    const std::string busy = "request refused (busy): a request is already in flight";
    CHECK((diagsOf(r.host, "request-blocked") == std::vector<std::string>{busy, busy}));
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.actions.empty() && r.host.bridge.empty(), actionsText(r.host.actions)); // not an action, not a raw call
    }
    SandboxConfig live = testConfig();
    live.context = messageContext(true, true, false, "private", WidgetViewer{42});
    Run c(kRequester, live, false);
    c.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
    c.engine.start();
    c.host.waitLoaded(20);
    c.engine.activationEvent(kTapOnButton, 1);
    const std::string asked = "ok:tap:{\"result\":null}:true err:tap2:WidgetRequestError:BUSY";
    CHECK_MSG(probeUntil(c.engine, "log.join(' ')", asked) == asked, c.engine.probe("log.join(' ')"));
    CHECK((requestsOf(c.host) == std::vector<WidgetRequest>{{"tap"}}));
}

/// Refused requests never reach the client: a message with `canQuery` only, from a timer (no-activation); with
/// `backgroundQuery` the first goes out and the next within 5 s is RATE_LIMITED (a hidden widget's waits: the next test);
/// a plain copy and a message without flags never ask, even with a tap (the shim refuses; a forged call on the wire is
/// the glue's `disabled`). Without onRequest the gate lets the request through and the delivery refuses it
/// (`unsupported`): the tap is used, the gate frees, the next tap is refused the same way, not BUSY.
TEST(sdk_request_refusals) {
    auto never = [](int& calls) { return [&calls](const WidgetRequest&, Respond respond) { calls++; respond(answered("never")); }; };
    int calls = 0;
    {
        SandboxConfig group = testConfig();
        group.context = messageContext(false, true, false, "group");
        Run t("<script>var log = []; setTimeout(function () { TelegramWidget.request('t').catch(function (e) { log.push(e.code + ':' + /tap/.test(e.message)); }); }, 0);</script>", group, false);
        t.host.onRequest = never(calls);
        t.engine.start();
        t.host.waitLoaded(20);
        CHECK_MSG(probeUntil(t.engine, "log.join()", "NOT_ALLOWED:true") == "NOT_ALLOWED:true", t.engine.probe("log.join()"));
        CHECK((diagsOf(t.host, "request-blocked") ==
               std::vector<std::string>{"request refused (no-activation): it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)"}));
    }
    const char* background = "<script>var log = []; function ask() { TelegramWidget.request('bg').then(function () { log.push('ok'); }, function (e) { log.push(e.code); }); }"
                             "ask(); setTimeout(ask, 10);</script>";
    SandboxConfig bg = testConfig();
    bg.context = messageContext(false, true, true, "private");
    {
        Run b(background, bg, false);
        b.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
        b.engine.start();
        b.host.waitLoaded(20);
        CHECK_MSG(probeUntil(b.engine, "log.join()", "ok,RATE_LIMITED") == "ok,RATE_LIMITED", b.engine.probe("log.join()"));
        CHECK((requestsOf(b.host) == std::vector<WidgetRequest>{{"bg"}}));
        CHECK((diagsOf(b.host, "request-blocked") == std::vector<std::string>{"request refused (rate-limited): background requests at most once per 5 s"}));
    }
    for (WidgetContext context : {messageContext(true, false, false, "private"), messageContext(true, false, true), WidgetContext{}}) {
        SandboxConfig cfg = testConfig();
        cfg.context = context;
        Run c(kRequester, cfg, false);
        c.host.onRequest = never(calls);
        c.engine.start();
        c.host.waitLoaded(20);
        c.engine.activationEvent(kTapOnButton, 1);
        const std::string refused = "err:tap:WidgetRequestError:NOT_ALLOWED err:tap2:WidgetRequestError:NOT_ALLOWED";
        CHECK_MSG(probeUntil(c.engine, "log.join(' ')", refused) == refused, c.engine.probe("log.join(' ')"));
        CHECK(requestsOf(c.host).empty() && diagsOf(c.host, "request-blocked").empty()); // the shim refuses before the glue
    }
    {
        // A widget that rewrites what the shim sends forges a request in place of ready(): the glue's own gate refuses it.
        Run f("<script>var s = JSON.stringify; JSON.stringify = function (v) { return s(Array.isArray(v) && v[0] === 'ready' ? ['request', [1, 'forged']] : v); };"
              "TelegramWidget.ready();</script>", testConfig(), false);
        f.host.onRequest = never(calls);
        f.engine.start();
        f.host.waitLoaded(20);
        f.barrier();
        CHECK((diagsOf(f.host, "request-blocked") ==
               std::vector<std::string>{"request refused (disabled): the server allows no requests for this message (no can_query: a widget sent without allow_queries, or a forwarded copy of one sent without fwd_allow_queries)"}));
        CHECK(requestsOf(f.host).empty());
    }
    {
        SandboxConfig group = testConfig();
        group.context = messageContext(false, true, false, "group");
        Run u(kRequester, group); // no onRequest
        u.engine.activationEvent(kTapOnButton, 1);
        // The delivery refuses on the engine thread, queued before the gate's BUSY for the second call.
        const std::string refused = "err:tap:WidgetRequestError:NOT_ALLOWED err:tap2:WidgetRequestError:BUSY";
        CHECK_MSG(probeUntil(u.engine, "log.join(' ')", refused) == refused, u.engine.probe("log.join(' ')"));
        u.engine.activationEvent(kTapOnButton, 2);
        CHECK_MSG(probeUntil(u.engine, "log.join(' ')", refused + " " + refused) == refused + " " + refused, u.engine.probe("log.join(' ')"));
        const std::string unsupported = "request refused: the client handles no requests (onRequest)", busy = "request refused (busy): a request is already in flight";
        CHECK((diagsOf(u.host, "request-blocked") == std::vector<std::string>{busy, unsupported, busy, unsupported}));
        CHECK(requestsOf(u.host).size() == 2); // delivered, then refused there
    }
    CHECK_MSG(calls == 0, std::to_string(calls) + " refused requests reached the client");
}

/// The client's failures become their codes; the answer is only `result`: an empty one is a result, none is null; a
/// result over 1024 bytes or with a NUL is INVALID_ANSWER (`answer-rejected`).
TEST(sdk_request_failures_and_invalid_answers) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, false, "group");
    auto tapWith = [&](WidgetRequestOutcome outcome, const std::string& code, const std::string& expected) {
        Run r(kRequester, cfg, false);
        r.host.onRequest = [outcome](const WidgetRequest&, Respond respond) { respond(outcome); };
        r.engine.start();
        r.host.waitLoaded(20);
        r.engine.activationEvent(kTapOnButton, 1);
        CHECK_MSG(probeUntil(r.engine, code, expected) == expected, r.engine.probe("log.join(' ')"));
        return diagsOf(r.host, "answer-rejected");
    };
    for (auto [failure, code] : {std::pair{WidgetRequestFailure::Timeout, "TIMEOUT"}, {WidgetRequestFailure::RateLimited, "RATE_LIMITED"}, {WidgetRequestFailure::Failed, "FAILED"}})
        tapWith({failure, {}}, "log[0]", std::string("err:tap:WidgetRequestError:") + code);
    tapWith(answered(""), "log[0]", "ok:tap:{\"result\":\"\"}:true");
    const std::vector<std::string> rejected = {"the answer's result is not text of at most 1024 bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER"};
    CHECK(tapWith(answered(std::string(1025, 'x')), "log[0]", "err:tap:WidgetRequestError:INVALID_ANSWER") == rejected);
    CHECK(tapWith(answered(std::string("a\0b", 3)), "log[0]", "err:tap:WidgetRequestError:INVALID_ANSWER") == rejected);
    CHECK(tapWith(answered("{\"v\":[1,2]}\n"), "log[0]", "ok:tap:{\"result\":\"{\\\"v\\\":[1,2]}\\n\"}:true").empty());
}

/// Background requests: one when the widget appears on screen, then one per 5 s. A background request at boot goes
/// out; after hide and show the next goes out at once; another at once is RATE_LIMITED. The client gets only the data.
TEST(sdk_background_request_after_the_widget_appears) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, true, "private");
    const char* html = "<script>var log = []; function ask() { TelegramWidget.request('bg').then(function () { log.push('ok'); }, function (e) { log.push(e.code); }); } ask();</script>";
    Run r(html, cfg, false);
    r.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
    r.engine.start();
    r.host.waitLoaded(20);
    CHECK_MSG(probeUntil(r.engine, "log.join()", "ok") == "ok", r.engine.probe("log.join()"));
    r.engine.setVisible(false);
    r.engine.setVisible(true);
    r.engine.probe("ask()");
    CHECK_MSG(probeUntil(r.engine, "log.join()", "ok,ok") == "ok,ok", r.engine.probe("log.join()"));
    r.engine.probe("ask()");
    CHECK_MSG(probeUntil(r.engine, "log.join()", "ok,ok,RATE_LIMITED") == "ok,ok,RATE_LIMITED", r.engine.probe("log.join()"));
    CHECK((requestsOf(r.host) == std::vector<WidgetRequest>{{"bg"}, {"bg"}}));
    // Visible all along: no reset.
    Run v(html, cfg, false);
    v.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
    v.engine.start();
    v.host.waitLoaded(20);
    CHECK_MSG(probeUntil(v.engine, "log.join()", "ok") == "ok", v.engine.probe("log.join()"));
    v.engine.setVisible(true); // no transition
    v.engine.probe("ask()");
    CHECK_MSG(probeUntil(v.engine, "log.join()", "ok,RATE_LIMITED") == "ok,RATE_LIMITED", v.engine.probe("log.join()"));
}

/// Telegram's clients create the host off screen: a background request at boot waits in the glue (no answer, nothing
/// refused, nothing delivered) and goes to the client when the widget appears, as that appearance's; one on `activated`
/// meanwhile is BUSY, the first being in flight. A widget terminated before it appears never sends its held request.
TEST(sdk_background_request_off_screen_waits_for_the_widget) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, true, "private");
    cfg.visible = false;
    const char* html = "<script>var log = []; function ask(t) { TelegramWidget.request(t).then(function (a) { log.push(t + ':' + a.result); }, function (e) { log.push(t + ':' + e.code); }); }"
                       "TelegramWidget.onEvent('activated', function () { ask('activated'); }); ask('boot');</script>";
    Run r(html, cfg, false);
    r.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered("r")); };
    r.engine.start();
    r.host.waitLoaded(20);
    r.barrier();
    CHECK_MSG(r.engine.probe("log.length + ':' + TelegramWidget.isActive") == "0:false", r.engine.probe("log.join()"));
    CHECK(requestsOf(r.host).empty() && diagsOf(r.host, "request-blocked").empty());
    r.engine.setVisible(true);
    const std::string both = "activated:BUSY boot:r";
    CHECK_MSG(probeUntil(r.engine, "log.slice().sort().join(' ')", both) == both, r.engine.probe("log.join(' ')"));
    CHECK((requestsOf(r.host) == std::vector<WidgetRequest>{{"boot"}}));
    CHECK((diagsOf(r.host, "request-blocked") == std::vector<std::string>{"request refused (busy): a request is already in flight"}));

    int calls = 0;
    Run d(html, cfg, false);
    d.host.onRequest = [&calls](const WidgetRequest&, Respond respond) { calls++; respond(answered("never")); };
    d.engine.start();
    d.host.waitLoaded(20);
    d.barrier();
    d.engine.terminate();
    d.engine.setVisible(true);
    d.barrier();
    CHECK(calls == 0 && requestsOf(d.host).empty());
}

/// copyText: one per tap, to the client as given (onBridgeCopyText), never an action or a raw call; the second call of
/// the tap and one from a timer are `activation-blocked`. A request takes the tap: a copyText or a link after it in the
/// same tap is blocked.
TEST(sdk_copy_text_needs_a_tap_and_shares_it_with_requests) {
    {
        Run r("<button>c</button><script>setTimeout(function () { TelegramWidget.copyText('timer'); }, 0);"
              "document.querySelector('button').onclick = function () { TelegramWidget.copyText('PROMO-2026 \\u2713'); TelegramWidget.copyText('second'); };</script>");
        r.host.waitUntil([&] { return r.host.hasDiag("activation-blocked"); }, 3);
        r.engine.activationEvent(kTapOnButton, 1);
        r.barrier();
        auto l = r.host.lock();
        CHECK_MSG((r.host.copiedTexts == std::vector<std::string>{"PROMO-2026 \xE2\x9C\x93"}), std::to_string(r.host.copiedTexts.size()));
        CHECK_MSG(r.host.countDiag("activation-blocked") == 2, r.diagsText());
        CHECK_MSG(r.host.actions.empty() && r.host.bridge.empty(), actionsText(r.host.actions));
    }
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, false, "group");
    Run r("<button>x</button><script>document.querySelector('button').onclick = function () {"
          "  TelegramWidget.request('r'); TelegramWidget.copyText('c'); TelegramWidget.openLink('https://example.com/'); };</script>", cfg, false);
    r.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
    r.engine.start();
    r.host.waitLoaded(20);
    r.engine.activationEvent(kTapOnButton, 1);
    r.barrier();
    auto l = r.host.lock();
    CHECK(r.host.requests.size() == 1);
    CHECK(r.host.copiedTexts.empty());
    CHECK_MSG(r.host.actions.empty(), actionsText(r.host.actions));
    CHECK_MSG(r.host.countDiag("activation-blocked") == 2, r.diagsText());
}

/// A preview (an inline result not sent yet, docs/sdk-v1.md §2.16) with data, a viewer, the private chat's flags and a
/// storage preload: it sees `isPreview`, its data, no chat type or viewer and an empty localStorage; a tap reaches
/// nothing; `request` is NOT_ALLOWED in the shim and never reaches the client; openLink, copyText and a haptic are
/// `preview-blocked` (×3) and reach nothing; ready and setResizeAnchor work; its storage is never committed; a timer
/// still fires. A request forged onto the wire is the glue's `preview-blocked` too.
/// The wallet (docs/sdk-v1.md §2.18; sdk.test.ts, SdkTests.swift): a tap asks the bot with the wallet, the answer's
/// transaction goes to `sendTransaction` on the grant (no new tap), the bot's answer to the payment resolves it; a
/// refusal in the window brings no grant; a timer gets nothing; `TelegramWidget.wallet` only with the wallet and canQuery.
static const char* kPayer =
    "<button id='b'>pay</button><script>var log = [];"
    "document.getElementById('b').onclick = function () { TelegramWidget.request('pay:42', { wallet: true })"
    "  .then(function (a) { log.push('tx'); return TelegramWidget.wallet.sendTransaction(JSON.parse(a.result), 'paid:42'); })"
    "  .then(function (b) { log.push('ok:' + b.result); }, function (e) { log.push('err:' + e.code);"
    "    return TelegramWidget.wallet.sendTransaction({ messages: [{ address: 'EQ' + 'ab'.repeat(23), amount: '1' }] }, 'again').catch(function (e2) { log.push('again:' + e2.code); }); }); };</script>";
static const std::string kTxJson = "{\"valid_until\":1900000000,\"messages\":[{\"address\":\"EQ" + std::string(46, 'a') + "\",\"amount\":\"1500000000\"}]}";

TEST(sdk_wallet_a_tap_pays_on_the_answer_to_its_request) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, true, "private");
    cfg.context.wallet = true;
    Run r(kPayer, cfg, false);
    r.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(kTxJson)); };
    r.host.onWallet = [](const WalletCall&, Respond respond) { respond(answered(std::string("thanks"))); };
    r.engine.start();
    r.host.waitLoaded(20);
    r.engine.activationEvent(kTapOnButton, 1);
    CHECK_MSG(probeUntil(r.engine, "log.join(' ')", "tx ok:thanks") == "tx ok:thanks", r.engine.probe("log.join(' ')"));
    CHECK((requestsOf(r.host) == std::vector<WidgetRequest>{{"pay:42", true}}));
    auto l = r.host.lock();
    CHECK_MSG(r.host.walletCalls.size() == 1, std::to_string(r.host.walletCalls.size()));
    if (!r.host.walletCalls.empty()) {
        const WalletCall& c = r.host.walletCalls[0];
        CHECK(c.method == WalletCall::Method::SendTransaction && c.data == "paid:42");
        CHECK_MSG(c.transaction.toJson().dump() == kTxJson, c.transaction.toJson().dump());
    }
    CHECK(r.host.actions.empty());
}

TEST(sdk_wallet_refusals_and_the_background) {
    SandboxConfig cfg = testConfig();
    cfg.context = messageContext(false, true, false, "group");
    cfg.context.wallet = true;
    Run r(kPayer, cfg, false);
    r.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(kTxJson)); };
    r.host.onWallet = [](const WalletCall&, Respond respond) { WidgetRequestOutcome o; o.failure = WidgetRequestFailure::Rejected; respond(o); };
    r.engine.start();
    r.host.waitLoaded(20);
    r.engine.activationEvent(kTapOnButton, 1);
    const std::string expected = "tx err:REJECTED again:NOT_ALLOWED";
    CHECK_MSG(probeUntil(r.engine, "log.join(' ')", expected) == expected, r.engine.probe("log.join(' ')"));
    CHECK((diagsOf(r.host, "request-blocked") == std::vector<std::string>{"wallet.sendTransaction refused (no-activation): it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each"}));

    // A request's failure Rejected is FAILED there.
    SandboxConfig group = testConfig();
    group.context = messageContext(false, true, false, "group");
    Run p(kRequester, group, false);
    p.host.onRequest = [](const WidgetRequest&, Respond respond) { WidgetRequestOutcome o; o.failure = WidgetRequestFailure::Rejected; respond(o); };
    p.engine.start();
    p.host.waitLoaded(20);
    p.engine.activationEvent(kTapOnButton, 1);
    const std::string failed = "err:tap:WidgetRequestError:FAILED err:tap2:WidgetRequestError:BUSY";
    CHECK_MSG(probeUntil(p.engine, "log.join(' ')", failed) == failed, p.engine.probe("log.join(' ')"));

    // From a timer neither a wallet call nor a request for the wallet goes out, also in the private chat with the bot;
    // TelegramWidget.wallet is null without the wallet or without canQuery.
    SandboxConfig priv = testConfig();
    priv.context = messageContext(false, true, true, "private");
    priv.context.wallet = true;
    Run t("<script>var log = []; setTimeout(function () {"
          "TelegramWidget.wallet.signData({ type: 'text', text: 'x' }, 's').catch(function (e) { log.push('sign:' + e.code); });"
          "TelegramWidget.request('w', { wallet: true }).catch(function (e) { log.push('rq:' + e.code); }); }, 0);</script>", priv, false);
    t.host.onRequest = [](const WidgetRequest&, Respond respond) { respond(answered(std::nullopt)); };
    t.host.onWallet = [](const WalletCall&, Respond respond) { respond(answered(std::nullopt)); };
    t.engine.start();
    t.host.waitLoaded(20);
    CHECK_MSG(probeUntil(t.engine, "log.slice().sort().join(' ')", "rq:NOT_ALLOWED sign:NOT_ALLOWED") == "rq:NOT_ALLOWED sign:NOT_ALLOWED", t.engine.probe("log.join(' ')"));
    CHECK(requestsOf(t.host).empty());
    {
        auto l = t.host.lock();
        CHECK(t.host.walletCalls.empty());
    }
    for (bool canQuery : {false, true}) {
        SandboxConfig none = testConfig();
        none.context = messageContext(false, canQuery, false, "private");
        none.context.wallet = !canQuery;
        Run n("<p>x</p>", none);
        CHECK_MSG(n.engine.probe("String(TelegramWidget.wallet)") == "null", n.engine.probe("String(TelegramWidget.wallet)"));
    }
}

TEST(sdk_preview_takes_no_input_and_no_actions) {
    SandboxConfig cfg = testConfig();
    cfg.data = {std::string("c"), std::string("p")};
    cfg.context = messageContext(false, true, true, "private", WidgetViewer{42});
    cfg.context.preview = true;
    cfg.storage.local = {{"k", "kept"}};
    Run r("<button>b</button><script>var log = [];"
          "document.querySelector('button').onclick = function () { log.push('tapped'); TelegramWidget.openLink('https://example.com/tap'); };"
          "TelegramWidget.ready(); TelegramWidget.setResizeAnchor('bottom');"
          "TelegramWidget.request('x').catch(function (e) { log.push(e.code + ':' + /preview|inline/.test(e.message)); });"
          "TelegramWidget.openLink('https://example.com/'); TelegramWidget.copyText('c'); TelegramWidget.HapticFeedback.selectionChanged();"
          "localStorage.setItem('n', '1'); setTimeout(function () { log.push('timer'); }, 20);</script>", cfg, false);
    int calls = 0;
    r.host.onRequest = [&calls](const WidgetRequest&, Respond respond) { calls++; respond(answered("no")); };
    r.engine.start();
    r.host.waitLoaded(20);
    const std::string seen = "JSON.stringify([TelegramWidget.isPreview, TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.chatType, TelegramWidget.viewer, TelegramWidget.isForward])";
    CHECK_MSG(r.engine.probe(seen) == "[true,\"c\",\"p\",null,null,false]", r.engine.probe(seen));
    CHECK(r.engine.probe("localStorage.getItem('k') + ',' + localStorage.getItem('n')") == "null,1"); // no preload; its own writes stay for this run
    r.engine.activationEvent(kTapOnButton, 1);
    r.engine.event(kTapOnButton);
    CHECK_MSG(probeUntil(r.engine, "log.join()", "NOT_ALLOWED:true,timer") == "NOT_ALLOWED:true,timer", r.engine.probe("log.join()"));
    r.barrier();
    {
        auto l = r.host.lock();
        CHECK_MSG(r.host.actions.size() == 1 && r.host.actions[0].kind == WidgetAction::Ready, actionsText(r.host.actions));
        CHECK((r.host.resizeAnchors == std::vector<std::string>{"bottom"}));
        CHECK(r.host.copiedTexts.empty() && r.host.requests.empty() && r.host.storageCommits.empty());
        CHECK_MSG(r.host.countDiag("preview-blocked") == 3 && !r.host.hasDiag("activation-blocked") && !r.host.hasDiag("storage-rejected"), r.diagsText());
    }
    CHECK((diagsOf(r.host, "preview-blocked") == std::vector<std::string>{"openLink ignored: a preview (an inline result not sent yet) takes no actions",
                                                                          "copyText ignored: a preview (an inline result not sent yet) takes no actions",
                                                                          "haptic ignored: a preview (an inline result not sent yet) takes no actions"}));
    // A widget that rewrites what the shim sends forges a request in place of ready(): the glue drops it too.
    SandboxConfig forged = cfg;
    Run f("<script>var s = JSON.stringify; JSON.stringify = function (v) { return s(Array.isArray(v) && v[0] === 'ready' ? ['request', [1, 'forged']] : v); };"
          "TelegramWidget.ready();</script>", forged, false);
    f.host.onRequest = [&calls](const WidgetRequest&, Respond respond) { calls++; respond(answered("no")); };
    f.engine.start();
    f.host.waitLoaded(20);
    f.barrier();
    CHECK((diagsOf(f.host, "preview-blocked") == std::vector<std::string>{"request ignored: a preview (an inline result not sent yet) takes no actions"}));
    CHECK(diagsOf(f.host, "request-blocked").empty() && requestsOf(f.host).empty());
    CHECK_MSG(calls == 0, std::to_string(calls) + " requests reached the client");
}

/// WidgetHost, preview: the renderer's events (trusted or not) and gesture claims are dropped; only `ready` reaches
/// onAction; nothing reaches onRequest, onCopyText or onStorageCommit.
TEST(sdk_widget_host_preview_drops_input) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHostOptions options;
    options.context = messageContext(false, true, true, "private", WidgetViewer{7});
    options.context.preview = true;
    options.storage.local = {{"k", "v"}};
    WidgetHost host("<button>b</button><script>var taps = 0; TelegramWidget.ready();"
                    "document.querySelector('button').onclick = function () { taps++; TelegramWidget.copyText('x'); TelegramWidget.request('r').catch(function () {}); localStorage.k = 'w'; };"
                    "localStorage.setItem('mine', '1');</script>",
                    WidgetTheme{}, 300, options, RES, scripted(renderer));
    std::mutex m;
    int requests = 0, copies = 0, commits = 0, claims = 0;
    std::vector<WidgetAction> actions;
    host.onRequest = [&](const WidgetRequest&, Respond respond) { std::lock_guard<std::mutex> l(m); requests++; respond(answered("no")); };
    host.onCopyText = [&](const std::string&) { std::lock_guard<std::mutex> l(m); copies++; };
    host.onStorageCommit = [&](const std::string&, const StorageArea&) { std::lock_guard<std::mutex> l(m); commits++; };
    host.onGesture = [&](const WidgetGestureClaim&) { std::lock_guard<std::mutex> l(m); claims++; };
    host.onAction = [&](const WidgetAction& a) { std::lock_guard<std::mutex> l(m); actions.push_back(a); };
    host.start();
    CHECK(host.waitLoaded(20));
    CHECK(host.probe("TelegramWidget.isPreview + ',' + TelegramWidget.chatType + ',' + TelegramWidget.viewer + ',' + localStorage.getItem('k')") == "true,null,null,null");
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + ",\"trusted\":true}");
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + "}");
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false,\"hold\":true}");
    host.onHostThread([] {});
    host.probe("0");
    host.onHostThread([] {});
    CHECK(host.probe("taps") == "0");
    host.destroy();
    std::lock_guard<std::mutex> l(m);
    CHECK(requests == 0 && copies == 0 && commits == 0 && claims == 0);
    CHECK_MSG(actions.size() == 1 && actions[0].kind == WidgetAction::Ready, actionsText(actions));
    CHECK(host.diagnostics().countDiag("activation-blocked") == 0 && host.diagnostics().countDiag("storage-rejected") == 0);
}

/// WidgetHost: data and the message's flags from WidgetHostOptions; onRequest on the host thread, answered
/// from another thread; a second respond is ignored; onCopyText on the host thread; setData (chatDataChanged and
/// publicDataChanged); a respond after destroy() and after the host is gone is ignored; without onRequest at delivery a
/// request is refused, and one set later on the host thread gets the next.
TEST(sdk_widget_host_requests_data_and_copy_text) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHostOptions options;
    options.data = {std::string("v1"), std::string("pub")};
    options.context = messageContext(false, true, false, "group", WidgetViewer{7});
    auto host = std::make_unique<WidgetHost>(kRequester, WidgetTheme{}, 300, options, RES, scripted(renderer));
    std::thread::id hostThread;
    host->onHostThread([&] { hostThread = std::this_thread::get_id(); });
    std::mutex m;
    std::vector<WidgetRequest> asked;
    std::vector<std::string> copied;
    std::vector<Respond> kept;
    bool onHost = true;
    host->onRequest = [&](const WidgetRequest& q, Respond respond) {
        std::lock_guard<std::mutex> l(m);
        onHost = onHost && std::this_thread::get_id() == hostThread;
        asked.push_back(q);
        kept.push_back(std::move(respond));
    };
    host->onCopyText = [&](const std::string& text) {
        std::lock_guard<std::mutex> l(m);
        onHost = onHost && std::this_thread::get_id() == hostThread;
        copied.push_back(text);
    };
    host->start();
    CHECK(host->waitLoaded(20));
    CHECK_MSG(host->probe(kShowContext) == "[\"v1\",\"pub\",false,\"group\",{\"id\":7}]", host->probe(kShowContext));
    auto tap = [&] { renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + ",\"trusted\":true}"); };
    tap();
    CHECK(eventually([&] { std::lock_guard<std::mutex> l(m); return kept.size() == 1; }));
    Respond respond;
    {
        std::lock_guard<std::mutex> l(m);
        respond = kept[0];
    }
    std::thread([respond] {
        respond(answered("thanks"));
        respond({WidgetRequestFailure::Timeout, {}}); // once only
    }).join();
    std::string log = "err:tap2:WidgetRequestError:BUSY ok:tap:{\"result\":\"thanks\"}:true";
    CHECK_MSG(probeUntil(*host, "log.join(' ')", log) == log, host->probe("log.join(' ')"));
    // The bot's data through setData: one event per changed field.
    host->setData({std::string("v3"), std::string("pub")});
    host->setData({std::string("v3"), std::string("pub")});
    host->setData({std::string("v3"), std::string("pub2")});
    log += " chat:v3 public:pub2";
    CHECK_MSG(probeUntil(*host, "log.join(' ')", log) == log, host->probe("log.join(' ')"));
    // copyText in a tap; untrusted, it is blocked.
    host->probe("mode = 'copy'");
    tap();
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + "}");
    CHECK(host->waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("activation-blocked") >= 3; }, 5)); // the tap's second call, the untrusted click's two
    host->onHostThread([] {});
    {
        std::lock_guard<std::mutex> l(m);
        CHECK_MSG((copied == std::vector<std::string>{"PROMO-2026 \xE2\x9C\x93"}), std::to_string(copied.size()));
        CHECK((asked == std::vector<WidgetRequest>{{"tap"}}));
        CHECK(onHost);
    }
    WidgetDiagnostics d = host->diagnostics();
    CHECK_MSG(d.actions.empty() && d.bridge.empty(), actionsText(d.actions));
    CHECK(d.countDiag("request-blocked") == 1 && d.countDiag("answer-rejected") == 0); // the tap's second call, BUSY
    // A request in flight when the host goes: its respond is ignored after destroy() and after the host is freed.
    host->probe("mode = 'ask'");
    tap();
    CHECK(eventually([&] { std::lock_guard<std::mutex> l(m); return kept.size() == 2; }));
    {
        std::lock_guard<std::mutex> l(m);
        respond = kept[1];
    }
    host->destroy();
    respond(answered("late"));
    host.reset();
    respond(answered("later"));
    // Without onRequest at delivery: refused, `unsupported`.
    WidgetHostOptions plainOptions;
    plainOptions.context = messageContext(false, true, false, "group");
    WidgetHost plain(kRequester, WidgetTheme{}, 300, plainOptions, RES, scripted(renderer));
    plain.start();
    CHECK(plain.waitLoaded(20));
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + ",\"trusted\":true}");
    // The refusal at delivery comes from the host thread, the gate's BUSY from the engine's: their order is the threads'.
    const std::string refused = "err:tap2:WidgetRequestError:BUSY err:tap:WidgetRequestError:NOT_ALLOWED", sorted = "log.slice().sort().join(' ')";
    CHECK_MSG(probeUntil(plain, sorted, refused) == refused, plain.probe("log.join(' ')"));
    CHECK(plain.waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("request-blocked") == 2; }, 5));
    CHECK((plain.diagnostics().diags["request-blocked"].second ==
           std::vector<std::string>{"request refused (busy): a request is already in flight", "request refused: the client handles no requests (onRequest)"}));
    // Decided at delivery: set on the host thread after start, the next tap's request reaches it.
    plain.onHostThread([&] { plain.onRequest = [](const WidgetRequest& q, Respond respond) { respond(answered("late " + q.data)); }; });
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + ",\"trusted\":true}");
    const std::string answeredLog = "err:tap2:WidgetRequestError:BUSY err:tap2:WidgetRequestError:BUSY err:tap:WidgetRequestError:NOT_ALLOWED ok:tap:{\"result\":\"late tap\"}:true";
    CHECK_MSG(probeUntil(plain, sorted, answeredLog) == answeredLog, plain.probe("log.join(' ')"));
}

static size_t countHaptics(RecordingHost& host) {
    auto l = host.lock();
    return size_t(countActions(host.actions, WidgetAction::Haptic));
}

/// v0.2.2 (the Telegram Android 13.2.0 report): a timer that asks for a haptic every 10 ms with no touch gets one series,
/// at most 16 played in its first second (a burst of 5, then 10 per second) and none in the next second, reported as
/// `haptic-limit`; a press (the renderer's, `pressBegan`) lets them through again. After a spent series a haptic outside
/// a touch is dropped and one in a tap's handler (press, click, release) plays.
TEST(sdk_haptics_need_a_touch) {
    Run r("<script>var H = TelegramWidget.HapticFeedback; setInterval(function () { H.notificationOccurred('error'); }, 10);</script>");
    sleepMs(1300);
    const size_t first = countHaptics(r.host);
    CHECK_MSG(first >= 5 && first <= 16, std::to_string(first) + " haptics in the series");
    sleepMs(1000);
    CHECK_MSG(countHaptics(r.host) == first, std::to_string(countHaptics(r.host)) + " after the series, " + std::to_string(first) + " in it");
    {
        auto l = r.host.lock();
        bool detail = false;
        for (auto& d : r.host.diags)
            detail = detail || (d.first == "haptic-limit" && d.second == "haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped");
        CHECK_MSG(detail, r.diagsText());
    }
    r.engine.pressBegan();
    CHECK(r.host.waitUntil([&] { return size_t(countActions(r.host.actions, WidgetAction::Haptic)) > first; }, 3));
    r.engine.pressEnded();

    Run t("<button>b</button><script>var H = TelegramWidget.HapticFeedback; H.selectionChanged();"
          "document.querySelector('button').onclick = function () { H.impactOccurred('light'); };</script>");
    sleepMs(1100); // the boot's call opened a series and played; it is over now
    t.engine.probe("TelegramWidget.HapticFeedback.selectionChanged(), 1");
    t.engine.event(kTapOnButton); // an untrusted click is no press
    t.barrier();
    CHECK(countHaptics(t.host) == 1);
    t.engine.pressBegan();
    t.engine.activationEvent(kTapOnButton, 1);
    t.engine.pressEnded();
    t.barrier();
    auto l = t.host.lock();
    CHECK_MSG(actionsText(t.host.actions) == "{\"kind\":\"haptic\",\"haptic\":\"selection\"} {\"kind\":\"haptic\",\"haptic\":\"impact\",\"value\":\"light\"} ", actionsText(t.host.actions));
}

/// WidgetHost: presses come from the renderer's messages (v0.2.2): after a spent series an untrusted click's haptic is
/// dropped (`haptic-limit`), and one in a tap the renderer frames with `gesture` and `release` plays.
TEST(sdk_widget_host_haptics_follow_the_renderers_presses) {
    ScriptedRenderer* renderer = nullptr;
    WidgetHost host("<button>b</button><script>var H = TelegramWidget.HapticFeedback; H.selectionChanged();"
                    "document.querySelector('button').onclick = function () { H.impactOccurred('light'); };</script>",
                    WidgetTheme{}, 300, WidgetHostOptions(), RES, scripted(renderer));
    std::mutex m;
    std::vector<WidgetAction> actions;
    host.onAction = [&](const WidgetAction& a) { std::lock_guard<std::mutex> l(m); actions.push_back(a); };
    host.start();
    CHECK(host.waitLoaded(20));
    sleepMs(1100);
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + "}");
    CHECK(host.waitUntil([](const WidgetDiagnostics& d) { return d.countDiag("haptic-limit") == 1; }, 5));
    renderer->onMessage("{\"type\":\"gesture\",\"wants\":false,\"x\":false,\"y\":false,\"hold\":false}");
    renderer->onMessage("{\"type\":\"event\",\"ev\":" + kTapOnButton + ",\"trusted\":true}");
    renderer->onMessage("{\"type\":\"release\"}");
    CHECK(eventually([&] { std::lock_guard<std::mutex> l(m); return actions.size() >= 2; }));
    host.probe("0");
    host.onHostThread([] {});
    std::lock_guard<std::mutex> l(m);
    CHECK_MSG(actionsText(actions) == "{\"kind\":\"haptic\",\"haptic\":\"selection\"} {\"kind\":\"haptic\",\"haptic\":\"impact\",\"value\":\"light\"} ", actionsText(actions));
    CHECK(host.diagnostics().diags["haptic-limit"].second == std::vector<std::string>{"haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped"});
}

TEST(widget_code_cannot_reach_host_globals) {
    Run r("<script>var names=['__host','__hostBinary','__hostCanvas','__hostOps','__twDispatch'];console.log('globals '+names.map(function(n){return n+'='+typeof globalThis[n]+'/'+(n in globalThis);}).join(','));"
          "try{Object.defineProperty(globalThis,'__twDispatch',{value:function(){console.log('pwned');}});}catch(e){}setTimeout(function(){console.log('tick');},1);</script>");
    r.host.waitUntil([&] { return r.host.countLogPrefix("tick") > 0; }, 3);
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.countLogPrefix("globals __host=undefined/false,__hostBinary=undefined/false,__hostCanvas=undefined/false,__hostOps=undefined/false,__twDispatch=undefined/false") == 1, r.logsText());
    CHECK(r.host.countLogPrefix("tick") == 1);
    CHECK(r.host.countLogPrefix("pwned") == 0);
}

TEST(widget_clock_is_coarse) {
    Run r("<script>var seen={},distinct=0,t0=performance.now();for(var i=0;i<200000;i++){var t=performance.now();if(!seen[t]){seen[t]=1;distinct++;}}console.log('CLOCK '+distinct+' '+(performance.now()-t0));</script>");
    r.barrier();
    auto l = r.host.lock();
    double distinct = -1, elapsed = -1;
    for (auto& log : r.host.logs) if (log.second.rfind("CLOCK ", 0) == 0) sscanf(log.second.c_str(), "CLOCK %lf %lf", &distinct, &elapsed);
    CHECK_MSG(distinct >= 1 && distinct <= elapsed / 0.1 + 2, r.logsText());
}

TEST(intl_fallback_formats_through_the_host) {
    Run r("<script>console.log((1234.5).toLocaleString() + '|' + new Intl.PluralRules('en').select(1) + '|' + new Intl.NumberFormat('en', {style:'percent'}).format(0.25));</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("1,234.5|one|25%"), r.logsText());
}

// ECMA-402's date defaults come from the shim: a format without fields reaches the provider with numeric ones.
TEST(intl_fallback_date_defaults_from_the_shim) {
    Run r("<script>var u={timeZone:'UTC'},d=new Date(0);"
          "console.log(['D',new Intl.DateTimeFormat('en-US',u).format(d),d.toLocaleDateString('en-US',u),d.toLocaleString('en-US',u),d.toLocaleTimeString('en-US',u)].join('|'));</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("D|1/1/1970|1/1/1970|1/1/1970, 12:00:00\xE2\x80\xAF" "AM|12:00:00\xE2\x80\xAF" "AM"), r.logsText());
}

/// The three spaces V8 uses (U+00A0, U+202F, U+2009) as one, as the fixture says to compare.
static std::string intlSpaces(std::string s) {
    for (const char* sp : {"\xC2\xA0", "\xE2\x80\xAF", "\xE2\x80\x89"}) {
        for (size_t at; (at = s.find(sp)) != std::string::npos;) s.replace(at, std::strlen(sp), " ");
    }
    return s;
}

/// The cases of the fixture shared with intl.ts, IntlBridge.swift and IntlBridge.kt (tests/fixtures/intl-cases.json,
/// V8's answers) through `intl`: every case, or only those marked `basic`. Returns one line per mismatch.
static std::vector<std::string> intlMismatches(IntlProvider& intl, bool basicOnly, int& checked) {
    std::vector<std::string> bad;
    auto root = Json::parse(readFile(std::string(TW_FIXTURES) + "/intl-cases.json"));
    if (!root) return {"fixture missing — run pnpm build:desktop"};
    for (const auto& c : (*root)["cases"].arr()) {
        if (basicOnly && !c["basic"].asBool()) continue;
        checked++;
        std::string got = intl.format(c["request"]);
        const Json& want = c["expected"];
        bool ok = false;
        if (want.isString()) ok = intlSpaces(got) == intlSpaces(want.asString());
        else if (auto g = Json::parse(got)) {
            if (want.isArray()) {
                ok = g->isArray() && g->size() == want.size();
                for (size_t i = 0; ok && i < want.size(); i++)
                    ok = (*g)[i]["type"].asString() == want[i]["type"].asString() && intlSpaces((*g)[i]["value"].asString()) == intlSpaces(want[i]["value"].asString());
            } else {
                ok = g->isObject();
                for (const auto& kv : want.obj()) ok = ok && (*g)[kv.first].dump() == kv.second.dump();
            }
        }
        if (!ok) bad.push_back(c["request"].dump() + " → " + got + ", want " + want.dump());
    }
    return bad;
}

/// BasicIntl, the provider without locale data, on every case the fixture marks `basic`.
TEST(intl_fallback_matches_v8_on_the_basic_cases) {
    BasicIntl intl;
    int checked = 0;
    for (const auto& m : intlMismatches(intl, true, checked)) CHECK_MSG(false, m);
    CHECK(checked > 100);
}

#ifdef _WIN32
/// IcuIntl, on the system's ICU, on every case of the fixture.
TEST(icu_intl_matches_v8) {
    IcuIntl intl;
    int checked = 0;
    for (const auto& m : intlMismatches(intl, false, checked)) CHECK_MSG(false, m);
    CHECK(checked > 100);
}
#endif

// formatToParts is in the widget profile: the basic provider splits what it formats.
TEST(intl_fallback_format_to_parts) {
    Run r("<script>var j=function(p){return p.map(function(x){return x.type+'='+x.value}).join(';')};"
          "console.log(j(new Intl.NumberFormat('en').formatToParts(-1234.5)));"
          "console.log(j(new Intl.DateTimeFormat('en',{timeZone:'UTC',year:'numeric',month:'long',day:'numeric'}).formatToParts(new Date(0))));</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK_MSG(r.host.hasLog("minusSign=-;integer=1;group=,;integer=234;decimal=.;fraction=5"), r.logsText());
    CHECK_MSG(r.host.hasLog("month=January;literal= ;day=1;literal=, ;year=1970"), r.logsText());
}

#ifdef _WIN32
// The system's ICU: CLDR data for the locale a widget names, where the basic provider writes English.
TEST(icu_intl_formats_with_cldr) {
    RecordingHost host("light", RES);
    SandboxEngine engine("<script>console.log(new Intl.NumberFormat('ru-RU').format(1234.5).replace(/\\s/g, ' ') + '|' + new Intl.PluralRules('ru-RU').select(3) + '|' +"
                         " new Intl.RelativeTimeFormat('en', {numeric: 'auto'}).format(-1, 'day') + '|' + new Intl.DateTimeFormat('en-US', {timeZone: 'UTC'}).format(new Date(0)) + '|' +"
                         " new Intl.ListFormat('en', {type: 'conjunction'}).format(['a', 'b', 'c']));</script>",
                         testConfig(), RES, std::make_shared<IcuIntl>());
    engine.setDelegate(&host);
    engine.start();
    host.waitLoaded(20);
    engine.thread().sync([] {});
    auto l = host.lock();
    std::string logs;
    for (auto& log : host.logs) logs += log.second + "\n";
    CHECK_MSG(host.hasLog("1 234,5|few|yesterday|1/1/1970|a, b, and c"), logs);
}
#endif

/// Engines created and torn down quickly, some before they finished booting.
TEST(engine_create_and_teardown_churn) {
    for (int i = 0; i < 10; i++) {
        RecordingHost host("light", RES);
        SandboxEngine engine("<p>x</p><script>setInterval(function(){}, 10); requestAnimationFrame(function(){});</script>", testConfig(), RES);
        engine.setDelegate(&host);
        engine.start();
        if (i % 2) host.waitLoaded(10);
        if (i % 3 == 0) engine.terminate();
    }
    CHECK(true);
}

// ───────────── validator ─────────────

// ───────────── precompiled bundles ─────────────

TEST(bytecode_round_trip_and_stale_blob_refused) {
    QuickJSContext js(size_t(16) << 20);
    auto blob = js.compileBytecode("globalThis.answer = 6 * 7;", "t.js");
    CHECK(blob.size() > 24);
    js.evaluateBytecode(blob, "globalThis.answer = 6 * 7;");
    CHECK(js.evaluate("answer", "t.js").number == 42);
    bool threw = false;
    try { js.evaluateBytecode(blob, "globalThis.answer = 6 * 8;"); } catch (const JSError& e) { threw = std::string(e.what()).find("different source") != std::string::npos; }
    CHECK_MSG(threw, "a blob compiled from another source must be refused");
    auto bad = blob;
    bad[0] = 'X';
    threw = false;
    try { js.evaluateBytecode(bad, "globalThis.answer = 6 * 7;"); } catch (const JSError&) { threw = true; }
    CHECK(threw);
    threw = false;
    try { js.evaluateBytecode({}, ""); } catch (const JSError&) { threw = true; }
    CHECK(threw);
}

// A CDN script is an external script like any other: profile 1 bundles no libraries.
TEST(shipped_bytecode_is_used_and_no_library_is_bundled) {
    CHECK_MSG(!RES->sandboxBytecode.empty() && !RES->validatorBytecode.empty(), "pnpm build:desktop ships .qbc next to the bundles");
    Run r("<canvas id=\"c\"></canvas><script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script><script>console.log(typeof Chart);</script>");
    r.barrier();
    auto l = r.host.lock();
    CHECK(!r.host.died);
    bool bootFromBytecode = false, skipped = false, fallback = false;
    for (auto& d : r.host.diags) {
        if (d.first == "boot" && d.second.find("(bytecode)") != std::string::npos) bootFromBytecode = true;
        if (d.first == "script-skipped" && d.second.find("chart.js") != std::string::npos) skipped = true;
        if (d.first == "bytecode") fallback = true;
    }
    CHECK_MSG(bootFromBytecode && skipped && !fallback, r.diagsText());
    CHECK(r.host.hasLog("undefined"));
    std::vector<std::pair<std::string, std::string>> diags;
    TrustedValidator v("light", RES, [&](const std::string& k, const std::string& d) { diags.emplace_back(k, d); });
    for (auto& d : diags) CHECK_MSG(d.first != "bytecode", d.second);
}

// ───────────── VERSION.json's stamp on the bundles (bundle_stamp.h) ─────────────

/// `source` with its first line replaced by `line` (empty: removed).
static std::string restamped(const std::string& source, const std::string& line) {
    size_t nl = source.find('\n');
    std::string rest = nl == std::string::npos ? "" : source.substr(nl + 1);
    return line.empty() ? rest : line + "\n" + rest;
}

static std::shared_ptr<SandboxResources> resourcesWith(const char* which, const std::string& line) {
    auto res = std::make_shared<SandboxResources>(*RES);
    std::string& bundle = std::string(which) == "sandbox" ? res->sandboxBundle : std::string(which) == "validator" ? res->validatorBundle : res->rendererBundle;
    bundle = restamped(bundle, line);
    return res;
}

TEST(bundle_stamp_shipped_and_parsed) {
    auto mine = BundleStamp::glue();
    CHECK_MSG(!BundleStamp::refusal(*RES), BundleStamp::refusal(*RES).value_or(""));
    for (const std::string* source : {&RES->sandboxBundle, &RES->validatorBundle, &RES->rendererBundle}) {
        auto stamp = BundleStamp::parse(*source);
        CHECK_MSG(stamp && *stamp == mine, "a shipped bundle is not this release's; run pnpm build:desktop: " + source->substr(0, 80));
    }
    CHECK(sdk::PROFILE_VERSION == version::PROFILE);
    auto parsed = BundleStamp::parse("/* widget-sandbox version=1.2.3-rc.1 protocol=7 profile=2 sdk=3 later=x */\n\"use strict\";");
    CHECK(parsed && *parsed == (BundleStamp{"1.2.3-rc.1", 7, 2, 3}));
    CHECK(BundleStamp::parse(mine.line()) == mine);
    for (const char* line : {
             "\"use strict\";(()=>{", "/* widget-sandbox */", "/* widget-sandbox version=0.6.0 protocol=1 profile=1 */",
             "/* widget-sandbox version=0.6.0 protocol=x profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0 protocol=-1 profile=1 sdk=1 */",
             "/* widget-sandbox version= protocol=1 profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0  protocol=1 profile=1 sdk=1 */",
             "/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1", "\n/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */",
             "/* widget-sandbox version=0.6.0 protocol=1234567 profile=1 sdk=1 */"}) {
        CHECK_MSG(!BundleStamp::parse(line), line);
    }
    CHECK_MSG(!BundleStamp::refusal(BundleStamp{"0.0.1", mine.protocol, mine.profile, mine.sdk}.line(), "x.js"), "another release with the same numbers runs");
    auto other = BundleStamp::refusal(BundleStamp{"9.9.9", mine.protocol + 1, mine.profile, mine.sdk}.line(), "sandbox-bundle.js").value_or("");
    CHECK_MSG(other.rfind("sandbox-bundle.js is widget-sandbox 9.9.9 (protocol " + std::to_string(mine.protocol + 1) + ",", 0) == 0, other);
    CHECK_MSG(other.find("this glue is " + mine.describe()) != std::string::npos, other);
    CHECK(BundleStamp::refusal(BundleStamp{"9.9.9", mine.protocol, mine.profile + 1, mine.sdk}.line(), "x.js"));
    CHECK(BundleStamp::refusal(BundleStamp{"9.9.9", mine.protocol, mine.profile, mine.sdk + 1}.line(), "x.js"));
    auto none = BundleStamp::refusal("\"use strict\";", "validator-bundle.js").value_or("");
    CHECK_MSG(none.rfind("validator-bundle.js has no version stamp", 0) == 0, none);
    CHECK(BundleStamp::refusal(*resourcesWith("renderer", BundleStamp{"9.9.9", mine.protocol, mine.profile, 99}.line())).value_or("").rfind("renderer-bundle.js is ", 0) == 0);
}

/// Bundles of another release: the engine dies at boot with `bundle-mismatch` before any widget code runs, and the
/// validator loads no bundle (every call answers empty).
TEST(bundle_stamp_engine_and_validator_refuse_another_release) {
    auto mine = BundleStamp::glue();
    std::vector<std::pair<const char*, std::string>> cases = {
        {"sandbox", BundleStamp{"9.9.9", mine.protocol + 1, mine.profile, mine.sdk}.line()},
        {"sandbox", ""},
        {"validator", BundleStamp{"9.9.9", mine.protocol, mine.profile + 1, mine.sdk}.line()},
        {"renderer", BundleStamp{"9.9.9", mine.protocol, mine.profile, mine.sdk + 1}.line()},
    };
    for (auto& [which, line] : cases) {
        auto res = resourcesWith(which, line);
        std::string refusal = BundleStamp::refusal(*res).value_or("");
        CHECK(!refusal.empty());
        RecordingHost host("light", res, false);
        SandboxConfig cfg = testConfig();
        SandboxEngine engine("<p>x</p><script>TelegramWidget.ready(); console.log('widget ran');</script>", cfg, res);
        engine.setDelegate(&host);
        engine.start();
        CHECK(host.waitLoaded(20));
        engine.thread().sync([] {});
        auto l = host.lock();
        CHECK_MSG(host.died == std::optional<std::string>("sandbox bundle failed to load"), std::string(which) + ": " + host.died.value_or("alive"));
        int mismatches = 0;
        for (auto& d : host.diags) if (d.first == "bundle-mismatch") { mismatches++; CHECK_MSG(d.second == refusal, d.second); }
        CHECK_MSG(mismatches == 1, which);
        CHECK_MSG(host.countDiag("validator:bundle-mismatch") == 1, which);
        CHECK(!host.hasLog("widget ran"));
        CHECK(host.ops.empty());
    }
    std::vector<std::pair<std::string, std::string>> diags;
    TrustedValidator v("light", resourcesWith("validator", ""), [&](const std::string& k, const std::string& d) { diags.emplace_back(k, d); });
    CHECK(v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"p\"}]") == "[]");
    CHECK_MSG(!diags.empty() && diags[0].first == "bundle-mismatch", diags.empty() ? "none" : diags[0].first);
}

TEST(validator_diag_bridge) {
    std::vector<std::pair<std::string, std::string>> diags;
    TrustedValidator v("light", RES, [&](const std::string& k, const std::string& d) { diags.emplace_back(k, d); });
    // An unknown element is reported through __diag (tag-replaced).
    v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"x-foo\"}]");
    bool replaced = false;
    for (auto& d : diags) if (d.first == "tag-replaced") replaced = true;
    CHECK(replaced);
}

TEST(validator_validates_anim_records) {
    std::vector<std::pair<std::string, std::string>> diags;
    TrustedValidator v("light", RES, [&](const std::string& k, const std::string& d) { diags.emplace_back(k, d); });
    v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"el\",\"id\":7,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":7,\"before\":null}]");
    auto record = [](const std::string& values, const std::string& duration, const std::string& easing) {
        return "[{\"aid\":1,\"target\":7,\"keyframes\":[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":" + values + "}],\"timing\":{\"duration\":" + duration + ",\"delay\":0,\"endDelay\":0,\"iterations\":null,\"iterationStart\":0,\"direction\":\"normal\",\"fill\":\"none\",\"easing\":\"" + easing + "\"},\"composite\":\"replace\",\"iterationComposite\":\"replace\",\"startTime\":1000,\"holdTime\":null,\"playbackRate\":1}]";
    };
    for (const std::string& bad : {record("{}", "1e999", "linear"), record("{}", "1e12", "linear"), record("{}", "1000", "evil(1)"), std::string("{\"not\":\"a list\"}"), std::string("[{\"aid\":-1}]")}) {
        diags.clear();
        CHECK_MSG(v.validateAnim(bad) == "[]", bad);
        bool violation = false;
        for (auto& d : diags) if (d.first == "protocol-violation") violation = true;
        CHECK_MSG(violation, bad);
    }
    std::string clean = v.validateAnim(record("{\"opacity\":\"0.5\",\"background-image\":\"url(https://evil.example/x)\",\"width\":\"expression(alert(1))\",\"behavior\":\"url(x.htc)\",\"animation-name\":\"x\"}", "1000", "ease-in"));
    CHECK_MSG(clean.find("\"opacity\":\"0.5\"") != std::string::npos, clean);
    CHECK_MSG(clean.find("url(") == std::string::npos && clean.find("expression") == std::string::npos && clean.find("animation-name") == std::string::npos, clean);
}

TEST(validator_strips_external_urls_and_handlers) {
    std::vector<std::pair<std::string, std::string>> diags;
    TrustedValidator v("light", RES, [&](const std::string& k, const std::string& d) { diags.emplace_back(k, d); });
    std::string ops =
        "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},"
        "{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},"
        "{\"op\":\"el\",\"id\":4,\"tag\":\"img\",\"attrs\":{\"src\":\"http://127.0.0.1:9999/hit/x\",\"onerror\":\"fetch('http://127.0.0.1:9999/e')\"}},{\"op\":\"ins\",\"parent\":3,\"id\":4,\"before\":null},"
        "{\"op\":\"el\",\"id\":5,\"tag\":\"iframe\",\"attrs\":{\"src\":\"http://127.0.0.1:9999/hit/f\"}},{\"op\":\"ins\",\"parent\":3,\"id\":5,\"before\":null},"
        "{\"op\":\"el\",\"id\":6,\"tag\":\"style\"},{\"op\":\"ins\",\"parent\":3,\"id\":6,\"before\":null},"
        "{\"op\":\"text\",\"id\":7,\"data\":\"@import url(http://127.0.0.1:9999/i); p{color:red;background:url(http://127.0.0.1:9999/b)}\"},{\"op\":\"ins\",\"parent\":3,\"id\":7,\"before\":null}]";
    std::string out = v.processOps(ops);
    auto rendered = Json::parse(out);
    CHECK(rendered.has_value() && rendered->isArray());
    // Nothing that is attached, set as an attribute or emitted as CSS may carry the canary.
    std::string attached;
    for (const auto& op : rendered->arr()) {
        const std::string& k = op["op"].asString();
        if (k == "ins" || k == "attr" || k == "css" || k == "style" || k == "el") attached += op.dump();
    }
    std::string lower = attached;
    for (auto& c : lower) c = char(std::tolower((unsigned char)c));
    CHECK_MSG(attached.find("127.0.0.1") == std::string::npos, attached);
    CHECK_MSG(lower.find("onerror") == std::string::npos, attached);
    CHECK_MSG(attached.find("\"tag\":\"iframe\"") == std::string::npos, attached);
    CHECK_MSG(out.find("data-tg-placeholder") != std::string::npos, out);
    bool blocked = false;
    for (auto& d : diags) if (d.first == "url-blocked") blocked = true;
    CHECK(blocked);
    CHECK(v.validateQuery("{\"q\":\"rect\",\"id\":4}") == std::optional<std::string>("{\"q\":\"rect\",\"id\":4}"));
    CHECK(!v.validateQuery("{\"q\":\"eval\",\"code\":\"1\"}"));
    CHECK(v.rewriteMediaQuery("(prefers-color-scheme: dark)") == "(min-width: 99999px)");
}

/// Churn validators, each with its own thread, and let them tear down.
TEST(validator_repeated_create_and_teardown) {
    for (int i = 0; i < 25; i++) {
        TrustedValidator v(i % 2 == 0 ? "light" : "dark", RES, [](const std::string&, const std::string&) {});
        CHECK(v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"p\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]").find("\"tag\":\"p\"") != std::string::npos);
        CHECK(v.nodeCount() == 2);
    }
}

TEST(validator_stylesheet_text_is_only_shipped_sanitized) {
    std::vector<std::string> diags;
    TrustedValidator v("dark", RES, [&](const std::string& k, const std::string& d) { diags.push_back(k + ": " + d); });
    std::string ops =
        "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},"
        "{\"op\":\"el\",\"id\":6,\"tag\":\"style\"},{\"op\":\"ins\",\"parent\":2,\"id\":6,\"before\":null},"
        "{\"op\":\"text\",\"id\":7,\"data\":\"@import url(http://127.0.0.1:9999/i); @media (prefers-color-scheme: dark){p{color:red}} p{background:url(http://127.0.0.1:9999/b)}\"},{\"op\":\"ins\",\"parent\":6,\"id\":7,\"before\":null}]";
    std::string out = v.processOps(ops);
    CHECK_MSG(out.find("{\"op\":\"rm\",\"id\":7}") != std::string::npos, out);
    CHECK_MSG(out.find("@media (min-width: 0px){p{color:red}}") != std::string::npos, out);
    bool reported = false;
    for (auto& d : diags) if (d.rfind("url-blocked", 0) == 0 || d.rfind("css-dropped", 0) == 0) reported = true;
    CHECK(reported);
    CHECK_MSG(out.find("\"css\",\"id\":6,\"text\":\"@import") == std::string::npos, out);
    CHECK(v.nodeCount() == 4);
}

// ───────────── WidgetHost + Blitz renderer (-DTW_BLITZ=ON) ─────────────
#ifdef TW_HAVE_BLITZ

/// One widget on WidgetHost with the Blitz renderer; `blitz` is the renderer the factory built.
struct BlitzRun {
    BlitzRenderer* blitz = nullptr;
    WidgetHost host;
    explicit BlitzRun(const std::string& html, double dpr = 2)
        : host(html, WidgetTheme{}, 400, [] { WidgetHostOptions o; o.devicePixelRatio = 2; return o; }(), RES,
               [this, dpr](std::shared_ptr<SandboxResources>, const std::string& theme, double width) -> std::unique_ptr<Renderer> {
                   auto r = std::make_unique<BlitzRenderer>(theme, width, dpr);
                   blitz = r.get();
                   return r;
               }) {
        host.start();
        host.waitLoaded(20);
        host.waitRendered(5);
    }
};

/// The Rust document answers layout queries with real geometry (not the harness's canned 100×20),
/// reports the document height, and paints a raster whose size follows width × dpr.
TEST(blitz_lays_out_answers_queries_and_paints) {
    BlitzRun r("<style>body{margin:0}#d{height:50px;background:#f00}</style><div id=\"d\">x</div>"
               "<script>console.log('rect=' + document.getElementById('d').getBoundingClientRect().width + 'x' + document.getElementById('d').getBoundingClientRect().height);</script>");
    auto d = r.host.diagnostics();
    CHECK_MSG(d.loaded, "loaded");
    CHECK_MSG(d.rendered, "the post-load ack query came back");
    CHECK_MSG(!d.dead, d.dead.value_or(""));
    CHECK_MSG(d.hasLog("rect=400x50"), [&] { std::string s; for (auto& l : d.logs) s += l.second + "\n"; return s; }());
    CHECK_MSG(d.height >= 50 && d.height < 200, "height " + std::to_string(d.height));
    BlitzRenderer::Frame f = r.blitz->paint();
    CHECK_MSG(f.width == 800, "frame width " + std::to_string(f.width));
    CHECK_MSG(f.height >= 100, "frame height " + std::to_string(f.height));
    // A red pixel inside the div (premultiplied RGBA, opaque).
    size_t i = (size_t(20) * f.width + 20) * 4;
    CHECK_MSG(f.rgba.size() > i + 3 && f.rgba[i] > 200 && f.rgba[i + 1] < 50 && f.rgba[i + 3] == 255, "pixel at (20,20)");
    CHECK(d.countDiag("renderer-violation") == 0);
}

/// A pointer from the host view is hit-tested by the document and reaches the widget's listener as a click,
/// marked trusted, so it is a user gesture for a privileged bridge call.
TEST(blitz_click_reaches_the_widget) {
    BlitzRun r("<button id=\"b\" style=\"display:block;width:200px;height:60px;margin:0\">go</button>"
               "<script>document.getElementById('b').addEventListener('click', function (e) { console.log('clicked ' + e.target.id); TelegramWidget.openLink('https://example.com/clicked'); });</script>");
    r.blitz->click(20, 20);
    CHECK_MSG(r.host.waitUntil([](const WidgetDiagnostics& d) { return d.hasLog("clicked b"); }, 3), "click reached the widget");
    r.blitz->click(300, 200);
    sleepMs(200);
    auto d = r.host.diagnostics();
    int clicks = 0;
    for (auto& l : d.logs) if (l.second == "clicked b") clicks++;
    CHECK_MSG(clicks == 1, "a click outside the button must not fire it: " + std::to_string(clicks));
    int sent = 0;
    for (auto& a : d.actions) if (a.kind == WidgetAction::OpenLink && a.url == "https://example.com/clicked") sent++;
    CHECK_MSG(sent == 1, "the renderer marks its input trusted, so the click is a gesture: " + std::to_string(sent));
}

/// A killed runtime freezes the document: the last render stays, pointers stop reaching anything.
TEST(blitz_frozen_after_kill_keeps_last_render) {
    BlitzRun r("<style>body{margin:0}</style><div style=\"height:40px;background:#00f\"></div><script>setTimeout(function () { for (;;) {} }, 50);</script>");
    CHECK_MSG(r.host.waitUntil([](const WidgetDiagnostics& d) { return d.dead.has_value(); }, 10), "the CPU limit kills the loop");
    sleepMs(100);
    BlitzRenderer::Frame f = r.blitz->paint();
    CHECK_MSG(!f.empty(), "still paints after the kill");
    // (10,10) CSS px at dpr 2, inside the div; the fatal badge sits bottom-right and the grayscale filter is 30 %.
    size_t i = (size_t(20) * f.width + 20) * 4;
    CHECK_MSG(f.rgba.size() > i + 3 && f.rgba[i + 2] > 150 && f.rgba[i] < 120, "the last render (blue div) is kept");
}

#endif

// ───────────── WidgetHost + litehtml renderer (-DTW_LITEHTML=ON) ─────────────
#ifdef TW_HAVE_LITEHTML

/// One widget on WidgetHost with the litehtml renderer (the Blitz tests above, on the other native document).
struct LitehtmlRun {
    LitehtmlRenderer* lite = nullptr;
    WidgetHost host;
    explicit LitehtmlRun(const std::string& html, double dpr = 2)
        : host(html, WidgetTheme{}, 400, [] { WidgetHostOptions o; o.devicePixelRatio = 2; return o; }(), RES,
               [this, dpr](std::shared_ptr<SandboxResources>, const std::string& theme, double width) -> std::unique_ptr<Renderer> {
                   auto r = std::make_unique<LitehtmlRenderer>(theme, width, dpr);
                   lite = r.get();
                   return r;
               }) {
        host.start();
        host.waitLoaded(20);
        host.waitRendered(5);
    }
};

/// litehtml answers layout queries with real geometry, reports the document height and paints a raster of width × dpr.
TEST(litehtml_lays_out_answers_queries_and_paints) {
    LitehtmlRun r("<style>body{margin:0;padding:0}#d{height:50px;background:#f00}</style><div id=\"d\">x</div>"
                  "<script>console.log('rect=' + document.getElementById('d').getBoundingClientRect().width + 'x' + document.getElementById('d').getBoundingClientRect().height);</script>");
    auto d = r.host.diagnostics();
    CHECK_MSG(d.loaded, "loaded");
    CHECK_MSG(d.rendered, "the post-load ack query came back");
    CHECK_MSG(!d.dead, d.dead.value_or(""));
    CHECK_MSG(d.hasLog("rect=400x50"), [&] { std::string s; for (auto& l : d.logs) s += l.second + "\n"; return s; }());
    CHECK_MSG(d.height >= 50 && d.height < 200, "height " + std::to_string(d.height));
    LitehtmlRenderer::Frame f = r.lite->paint();
    CHECK_MSG(f.width == 800, "frame width " + std::to_string(f.width));
    CHECK_MSG(f.height >= 100, "frame height " + std::to_string(f.height));
    size_t i = (size_t(20) * f.width + 20) * 4;
    CHECK_MSG(f.rgba.size() > i + 3 && f.rgba[i] > 200 && f.rgba[i + 1] < 50 && f.rgba[i + 3] == 255, "pixel at (20,20)");
    CHECK(d.countDiag("renderer-violation") == 0);
    CHECK_MSG(r.lite->html().find("data-twid=") != std::string::npos, "the shadow tree was serialised");
}

/// A pointer is hit-tested by litehtml's render tree and reaches the widget's listener as a click,
/// marked trusted, so it is a user gesture for a privileged bridge call.
TEST(litehtml_click_reaches_the_widget) {
    LitehtmlRun r("<button id=\"b\" style=\"display:block;width:200px;height:60px;margin:0\">go</button>"
                  "<script>document.getElementById('b').addEventListener('click', function (e) { console.log('clicked ' + e.target.id); TelegramWidget.openLink('https://example.com/clicked'); });</script>");
    r.lite->click(20, 20);
    CHECK_MSG(r.host.waitUntil([](const WidgetDiagnostics& d) { return d.hasLog("clicked b"); }, 3), "click reached the widget");
    r.lite->click(300, 200);
    sleepMs(200);
    auto d = r.host.diagnostics();
    int clicks = 0;
    for (auto& l : d.logs) if (l.second == "clicked b") clicks++;
    CHECK_MSG(clicks == 1, "a click outside the button must not fire it: " + std::to_string(clicks));
    int sent = 0;
    for (auto& a : d.actions) if (a.kind == WidgetAction::OpenLink && a.url == "https://example.com/clicked") sent++;
    CHECK_MSG(sent == 1, "the renderer marks its input trusted, so the click is a gesture: " + std::to_string(sent));
}

/// A killed runtime freezes the document: the last render stays, pointers stop reaching anything.
TEST(litehtml_frozen_after_kill_keeps_last_render) {
    LitehtmlRun r("<style>body{margin:0;padding:0}</style><div style=\"height:40px;background:#00f\"></div><script>setTimeout(function () { for (;;) {} }, 50);</script>");
    CHECK_MSG(r.host.waitUntil([](const WidgetDiagnostics& d) { return d.dead.has_value(); }, 10), "the CPU limit kills the loop");
    sleepMs(100);
    LitehtmlRenderer::Frame f = r.lite->paint();
    CHECK_MSG(!f.empty(), "still paints after the kill");
    size_t i = (size_t(20) * f.width + 20) * 4;
    CHECK_MSG(f.rgba.size() > i + 3 && f.rgba[i + 2] > 150 && f.rgba[i] < 120, "the last render (blue div) is kept");
}

#endif

int main(int argc, char** argv) {
    const char* filter = argc > 1 ? argv[1] : nullptr;
    try { RES = SandboxResources::fromDirectory(TW_WIDGET_JS); } catch (const std::exception& e) { fprintf(stderr, "%s\n", e.what()); return 2; }
    int ran = 0;
    for (const auto& t : registry()) {
        if (filter && std::strstr(t.name, filter) == nullptr) continue;
        int before = g_failures;
        auto t0 = std::chrono::steady_clock::now();
        printf("%s\n", t.name);
        fflush(stdout);
        t.fn();
        double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        printf("  %s (%.0f ms)\n", g_failures == before ? "ok" : "FAILED", ms);
        ran++;
    }
    printf("\n%d tests, %d checks, %d failures\n", ran, g_checks, g_failures);
    return g_failures ? 1 : 0;
}
