// widget-sandbox's conformance set on TwidgetCore, following packages/conformance/CLIENTS.md: every widget
// of the automatic set, in each of its themes, through the desktop host as a client configures it
// (strict policy, the profile's features), drawn off screen by the Direct2D backend at DPR 2. The
// widget's verdict is read from the validated ops; the scripted input goes through a bubble that
// arbitrates it by the shared gesture policy, as a client's does; the stages are cropped from the
// paint. The run file is the input of the set's report tool.
//
//   twidget-conformance [out dir] [--width N]... [--filter text] [--sandbox <widget-sandbox>]
#include "d2d_backend.h"
#include "renderer.h"
#include "twidget/icu_intl.h"
#include "twidget/json.h"
#include "twidget/renderer.h"
#include "twidget/resources.h"
#include "twidget/widget_host.h"

#include <d3d11.h>
#include <dxgi.h>
#include <wincodec.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <optional>
#include <sstream>
#include <thread>

using twidget::Json;
namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace {

constexpr double DPR = 2;  // the references are Chromium at DPR 2

double now_ms() {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

std::string read_file(const fs::path& path) {
    std::ifstream in(path, std::ios::binary);
    std::ostringstream s;
    s << in.rdbuf();
    return s.str();
}

struct Device {
    winrt::com_ptr<ID3D11Device> d3d;
    winrt::com_ptr<ID2D1Factory1> factory;
    winrt::com_ptr<ID2D1Device> d2d;
    winrt::com_ptr<IDWriteFactory> dwrite;
    winrt::com_ptr<IWICImagingFactory> wic;

    // WARP: the same pixels on every machine.
    Device() {
        winrt::check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_WARP, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT, nullptr, 0, D3D11_SDK_VERSION, d3d.put(), nullptr, nullptr));
        winrt::check_hresult(D2D1CreateFactory(D2D1_FACTORY_TYPE_MULTI_THREADED, factory.put()));
        winrt::check_hresult(factory->CreateDevice(d3d.as<IDXGIDevice>().get(), d2d.put()));
        winrt::check_hresult(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), reinterpret_cast<IUnknown**>(dwrite.put())));
        winrt::check_hresult(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(wic.put())));
    }
};

struct Image {
    int w = 0, h = 0;
    std::vector<uint8_t> bgra;  // premultiplied
};

/// `rect` (CSS px) of `image` (device px) as straight-alpha BGRA, transparent where the widget ends.
Image crop(const Image& image, double x, double y, double w, double h) {
    Image out;
    out.w = int(std::lround(w * DPR));
    out.h = int(std::lround(h * DPR));
    out.bgra.assign(size_t(out.w) * out.h * 4, 0);
    int x0 = int(std::lround(x * DPR)), y0 = int(std::lround(y * DPR));
    for (int row = 0; row < out.h; row++) {
        int sy = y0 + row;
        if (sy < 0 || sy >= image.h) continue;
        for (int col = 0; col < out.w; col++) {
            int sx = x0 + col;
            if (sx < 0 || sx >= image.w) continue;
            const uint8_t* s = image.bgra.data() + (size_t(sy) * image.w + sx) * 4;
            uint8_t* d = out.bgra.data() + (size_t(row) * out.w + col) * 4;
            uint8_t a = s[3];
            for (int c = 0; c < 3; c++) d[c] = a ? uint8_t(std::min(255, (s[c] * 255 + a / 2) / a)) : 0;
            d[3] = a;
        }
    }
    return out;
}

void write_png(IWICImagingFactory* wic, const fs::path& path, const Image& image) {
    winrt::com_ptr<IWICStream> stream;
    winrt::check_hresult(wic->CreateStream(stream.put()));
    winrt::check_hresult(stream->InitializeFromFilename(path.wstring().c_str(), GENERIC_WRITE));
    winrt::com_ptr<IWICBitmapEncoder> encoder;
    winrt::check_hresult(wic->CreateEncoder(GUID_ContainerFormatPng, nullptr, encoder.put()));
    winrt::check_hresult(encoder->Initialize(stream.get(), WICBitmapEncoderNoCache));
    winrt::com_ptr<IWICBitmapFrameEncode> frame;
    winrt::check_hresult(encoder->CreateNewFrame(frame.put(), nullptr));
    winrt::check_hresult(frame->Initialize(nullptr));
    winrt::check_hresult(frame->SetSize(UINT(image.w), UINT(image.h)));
    WICPixelFormatGUID format = GUID_WICPixelFormat32bppBGRA;
    winrt::check_hresult(frame->SetPixelFormat(&format));
    winrt::check_hresult(frame->WritePixels(UINT(image.h), UINT(image.w * 4), UINT(image.bgra.size()), const_cast<uint8_t*>(image.bgra.data())));
    winrt::check_hresult(frame->Commit());
    winrt::check_hresult(encoder->Commit());
}

/// What the widget last published in `data-conformance` on its <html> element.
struct Verdict {
    std::optional<Json> value;
    std::string status() const { return value ? (*value)["status"].asString() : std::string(); }
};

/// WidgetHost's Renderer on TwidgetCore, painting nothing until asked for a snapshot. The core is not
/// locked on its side: the host thread (messages), the clock thread (frames) and the runner (input,
/// snapshots) take turns on one mutex; messages to the host go out after it is released.
class OffscreenRenderer final : public twidget::Renderer {
public:
    OffscreenRenderer(Device& device, bool dark, double width, double max_height)
        : device_(device), backend_(device.d2d.get(), device.dwrite.get(), device.wic.get()),
          core_(std::make_unique<twlite::Renderer>(width, DPR, dark, backend_)), dark_(dark) {
        std::vector<Json> out;
        core_->set_max_height(max_height, out);
        clock_ = std::thread([this] { clock_loop(); });
    }
    ~OffscreenRenderer() override { destroy(); }

    void load() override {
        if (onMessage) onMessage("{\"type\":\"ready\"}");
    }

    void send(const std::string& json) override {
        if (json.rfind("{\"type\":\"requestFrame\"", 0) == 0) {
            std::lock_guard l(clock_mutex_);
            frame_requested_ = true;
            return;
        }
        auto msg = Json::parse(json);
        if (!msg) return;
        if ((*msg)["type"].asString() == "ops") probe((*msg)["ops"]);
        run([&] { return core_->receive(*msg); });
    }

    void setTheme(const std::string& theme) override {
        run([&] {
            std::vector<Json> out;
            dark_ = theme == "dark";
            core_->set_theme(dark_, out);
            return out;
        });
    }

    void setWidth(double width) override {
        run([&] {
            std::vector<Json> out;
            core_->set_width(width, out);
            return out;
        });
    }

    void destroy() override {
        {
            std::lock_guard l(clock_mutex_);
            if (stop_) return;
            stop_ = true;
        }
        if (clock_.joinable()) clock_.join();
        std::lock_guard l(mutex_);
        core_.reset();
    }

    /// One primary pointer; on a pointerdown, the claim the renderer reported for the press.
    twidget::WidgetGestureClaim touch(int action, double x, double y, bool mouse = false, int button = 0) {
        twidget::WidgetGestureClaim claim;
        run([&] {
            auto out = core_->touch(action, x, y, 0, true, mouse, 0, button);
            for (const Json& m : out)
                if (m["type"].asString() == "gesture") claim = {m["x"].asBool(), m["y"].asBool(), m["hold"].asBool()};
            return out;
        });
        return claim;
    }
    void hover(int action, double x, double y) {
        run([&] { return core_->hover(action, x, y, 0); });
    }
    void wheel(double x, double y, double dy) {
        run([&] { return core_->wheel(x, y, 0, dy, 0, 0); });
    }
    void key(const std::string& type, const std::string& key, const std::string& code) {
        run([&] { return core_->key(type, key, code, false, 0); });
    }
    void set_visible(bool visible) {
        std::lock_guard l(clock_mutex_);
        visible_ = visible;
    }

    /// The core keeps the keyboard only through its editor: none means the chat input keeps it.
    bool editing() {
        std::lock_guard l(mutex_);
        return core_ && core_->editor();
    }

    Image snapshot() {
        std::lock_guard l(mutex_);
        Image image;
        if (!core_) return image;
        twlite::DisplayList list = core_->paint();
        image.w = int(core_->px_width());
        image.h = int(core_->px_height());
        if (image.w <= 0 || image.h <= 0) return image;
        winrt::com_ptr<ID2D1DeviceContext> ctx;
        winrt::check_hresult(device_.d2d->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, ctx.put()));
        auto format = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
        winrt::com_ptr<ID2D1Bitmap1> target, readback;
        winrt::check_hresult(ctx->CreateBitmap(D2D1::SizeU(image.w, image.h), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, format), target.put()));
        winrt::check_hresult(ctx->CreateBitmap(D2D1::SizeU(image.w, image.h), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_CPU_READ | D2D1_BITMAP_OPTIONS_CANNOT_DRAW, format), readback.put()));
        ctx->SetTarget(target.get());
        ctx->BeginDraw();
        // The bubble under the transparent renderer, in the default palette's bg_color as the host passes no other.
        ctx->Clear(D2D1::ColorF(dark_ ? 0x212121 : 0xffffff));
        ctx->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
        ctx->SetTransform(D2D1::Matrix3x2F::Scale(float(DPR), float(DPR)));
        backend_.paint(list, ctx.get());
        winrt::check_hresult(ctx->EndDraw());
        winrt::check_hresult(readback->CopyFromBitmap(nullptr, target.get(), nullptr));
        D2D1_MAPPED_RECT mapped;
        winrt::check_hresult(readback->Map(D2D1_MAP_OPTIONS_READ, &mapped));
        image.bgra.resize(size_t(image.w) * image.h * 4);
        for (int y = 0; y < image.h; y++) memcpy(image.bgra.data() + size_t(y) * image.w * 4, mapped.bits + size_t(y) * mapped.pitch, size_t(image.w) * 4);
        readback->Unmap();
        return image;
    }

    /// Waits until the widget publishes a status in `wanted`, or `timeout` passes; the last verdict either way.
    Verdict wait_for(std::initializer_list<const char*> wanted, std::chrono::milliseconds timeout) {
        std::unique_lock l(verdict_mutex_);
        verdict_cv_.wait_for(l, timeout, [&] {
            std::string status = verdict_.status();
            for (const char* w : wanted)
                if (status == w) return true;
            return false;
        });
        return verdict_;
    }

    Verdict verdict() {
        std::lock_guard l(verdict_mutex_);
        return verdict_;
    }

private:
    template <class Call> void run(Call call) {
        std::vector<Json> out;
        {
            std::lock_guard l(mutex_);
            if (!core_) return;
            out = call();
        }
        for (const Json& m : out)
            if (onMessage) onMessage(m.dump());
    }

    // The validated ops, as CLIENTS.md reads them: the html element's id from its `el` op, then every `attr`
    // op on it naming data-conformance.
    void probe(const Json& ops) {
        for (const Json& op : ops.arr()) {
            const std::string& kind = op["op"].asString();
            if (kind == "el" && op["adopt"].asString() == "html") {
                html_ = int64_t(op["id"].asNumber());
                for (const Json& a : op["attrs"].arr())
                    if (a[size_t(0)].asString() == "data-conformance") publish(a[size_t(1)].asString());
            } else if (kind == "attr" && html_ && int64_t(op["id"].asNumber()) == html_ && op["name"].asString() == "data-conformance") {
                publish(op["value"].asString());
            }
        }
    }

    void publish(const std::string& value) {
        auto parsed = Json::parse(value);
        if (!parsed) return;
        {
            std::lock_guard l(verdict_mutex_);
            verdict_.value = std::move(*parsed);
        }
        verdict_cv_.notify_all();
    }

    // One tick per display refresh at most: frames the widget asked for, animations and coalesced moves.
    void clock_loop() {
        double wake_at = 0;
        while (true) {
            std::this_thread::sleep_for(16ms);
            bool frame, visible;
            {
                std::lock_guard l(clock_mutex_);
                if (stop_) return;
                frame = frame_requested_;
                frame_requested_ = false;
                visible = visible_;
            }
            if (frame && onMessage) onMessage("{\"type\":\"frame\",\"time\":" + Json::number(now_ms()) + "}");
            if (!visible) continue;
            std::vector<Json> out;
            {
                std::lock_guard l(mutex_);
                if (!core_) return;
                if (core_->wants_tick() || (wake_at && now_ms() >= wake_at)) {
                    out = core_->tick();
                    double next = core_->next_tick_in();
                    wake_at = std::isfinite(next) ? now_ms() + next * 1000 : 0;
                }
            }
            for (const Json& m : out)
                if (onMessage) onMessage(m.dump());
        }
    }

    Device& device_;
    twlite::D2DBackend backend_;
    std::mutex mutex_;  // core_ and every call into it
    std::unique_ptr<twlite::Renderer> core_;
    bool dark_;

    std::mutex clock_mutex_;
    std::thread clock_;
    bool frame_requested_ = false;
    bool visible_ = true;
    bool stop_ = false;

    int64_t html_ = 0;  // the host thread's only
    std::mutex verdict_mutex_;
    std::condition_variable verdict_cv_;
    Verdict verdict_;
};

/// The client's bubble over the widget, which CLIENTS.md has the actions go through: its recognizers follow
/// the shared gesture policy on the claim the renderer reports, and take a press from the widget (a cancel)
/// when the policy gives it to the bubble. What the bubble then does (a menu, a swipe) is not drawn.
class Bubble {
public:
    explicit Bubble(OffscreenRenderer& renderer) : r_(renderer) {}

    void down(double x, double y, bool mouse = false, int button = 0) {
        mouse_ = mouse;
        taken_ = false;
        twidget::WidgetGestureClaim claim = r_.touch(0, x, y, mouse, button);
        act(arbiter_.down(now_ms(), x, y, claim, mouse ? twidget::PointerKind::Mouse : twidget::PointerKind::Touch, button), x, y);
    }
    void move(double x, double y) {
        if (taken_) return;
        r_.touch(1, x, y, mouse_);
        if (auto v = arbiter_.move(now_ms(), x, y)) act(*v, x, y);
    }
    /// Keeps the press still for `ms`; the hold timer fires at its deadline.
    void hold(double ms, double x, double y) {
        double end = now_ms() + ms;
        while (true) {
            auto deadline = taken_ ? std::nullopt : arbiter_.holdDeadline();
            bool due = deadline && *deadline <= end;
            std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(std::max(0.0, (due ? *deadline : end) - now_ms())));
            if (!due) return;
            if (auto v = arbiter_.tick(now_ms())) act(*v, x, y);
        }
    }
    void up(double x, double y) {
        if (taken_) return;
        auto v = arbiter_.up(now_ms(), x, y);
        r_.touch(v && v->cancel ? 3 : 2, x, y, mouse_);
    }

private:
    void act(const twidget::GestureVerdict& v, double x, double y) {
        if (!v.cancel) return;
        r_.touch(3, x, y, mouse_);
        taken_ = true;
    }

    OffscreenRenderer& r_;
    twidget::GestureArbiter arbiter_;
    bool mouse_ = false;
    bool taken_ = false;
};

struct Point {
    double x = 0, y = 0;
};

/// An action's target: the centre of `targets[name]` in the latest verdict, or a point given as [x, y].
std::optional<Point> target(const Json& t, const Verdict& verdict) {
    if (t.isString()) {
        if (!verdict.value) return std::nullopt;
        const Json& r = (*verdict.value)["targets"][t.asString()];
        if (!r.isArray() || r.arr().size() < 4) return std::nullopt;
        return Point{r[size_t(0)].asNumber() + r[size_t(2)].asNumber() / 2, r[size_t(1)].asNumber() + r[size_t(3)].asNumber() / 2};
    }
    if (t.isArray() && t.arr().size() >= 2) return Point{t[size_t(0)].asNumber(), t[size_t(1)].asNumber()};
    return std::nullopt;
}

std::string key_code(const std::string& key) {
    if (key.size() == 1 && std::isalpha(static_cast<unsigned char>(key[0]))) return std::string("Key") + char(std::toupper(static_cast<unsigned char>(key[0])));
    if (key.size() == 1 && std::isdigit(static_cast<unsigned char>(key[0]))) return "Digit" + key;
    if (key == " ") return "Space";
    return key;
}

constexpr double VISUAL_HEIGHT = 2400;

/// The server's flags for a kind of message and the chat it is in (WIDGET_MESSAGE_KINDS in widget-sandbox's
/// sdk-policy.ts); false for a kind it does not list.
bool message_context(const std::string& kind, twidget::WidgetContext& out) {
    struct Kind {
        const char* name;
        bool copy, query, background, preview;
    };
    static const Kind kinds[] = {
        {"private", false, true, true, false}, {"group", false, true, false, false}, {"via-bot", false, true, false, false},
        {"no-queries", false, false, false, false}, {"live-copy", true, true, false, false}, {"copy", true, false, false, false},
        {"preview", false, false, false, true},
    };
    for (const Kind& k : kinds) {
        if (kind != k.name) continue;
        out.isCopy = k.copy;
        out.canQuery = k.query;
        out.backgroundQuery = k.background;
        out.preview = k.preview;
        out.chatType = kind == "group" ? "group" : "private";
        return true;
    }
    return false;
}

/// The account a client passes as the viewer where requests are allowed.
twidget::WidgetViewer test_viewer() {
    twidget::WidgetViewer v;
    v.id = 777000001;
    v.firstName = "Conformance";
    v.username = "conformance_runner";
    return v;
}

struct Options {
    fs::path sandbox = TW_SANDBOX;
    fs::path out = "conformance-run";
    std::vector<double> widths;
    std::string filter;
    // What a client preloads, as {"local": {...}, "session": {...}}: a restart, for the storage widgets.
    twidget::WidgetStorage storage;
    // The visual set instead of the automatic one (the SDK group aside, which needs a client's host):
    // stage crops at each of a widget's widths for tools/conformance/visual-report.mjs.
    bool visual = false;
};

Json run_one(Device& device, const std::shared_ptr<twidget::SandboxResources>& resources, const Options& options, const Json& test,
             const std::string& theme, double width, const fs::path& crops) {
    const std::string id = test["id"].asString();
    const std::string html = read_file(options.sandbox / "packages/conformance" / test["file"].asString());

    twidget::WidgetHostOptions host_options;
    host_options.devicePixelRatio = DPR;
    host_options.features = {"webAnimations"};
    host_options.storage = options.storage;
    // A test with a message runs as that kind of message with the bot's state; the others in no message context.
    const Json& message = test["message"];
    if (message.isArray() && !message.arr().empty()) {
        const Json& spec = message[size_t(0)];
        message_context(spec["kind"].asString(), host_options.context);
        if (spec["chat_data"].isString() && !spec["chat_data"].asString().empty()) host_options.data.chatData = spec["chat_data"].asString();
        if (spec["public_data"].isString() && !spec["public_data"].asString().empty()) host_options.data.publicData = spec["public_data"].asString();
        if (host_options.context.canQuery) host_options.context.viewer = test_viewer();
    }
    // A test with the client's time over its corner gets the band and corner its reference picture was drawn with.
    const Json& safe_area = test["safeArea"];
    if (safe_area.isObject()) {
        twidget::WidgetContentSafeArea& area = host_options.contentSafeArea;
        area.top = safe_area["top"].asNumber();
        area.right = safe_area["right"].asNumber();
        area.bottom = safe_area["bottom"].asNumber();
        area.left = safe_area["left"].asNumber();
        area.cornerLeft = safe_area["cornerLeft"].asNumber();
        area.cornerRight = safe_area["cornerRight"].asNumber();
    }
    OffscreenRenderer* renderer = nullptr;
    auto factory = [&](std::shared_ptr<twidget::SandboxResources>, const std::string& scheme, double w) -> std::unique_ptr<twidget::Renderer> {
        // A visual widget is a column of stages taller than a bubble: it is drawn whole, as the Swift
        // runner draws it, and a stage below the bubble's bound is marked.
        auto r = std::make_unique<OffscreenRenderer>(device, scheme == "dark", w, options.visual ? VISUAL_HEIGHT : 600.0);
        renderer = r.get();
        return r;
    };

    std::mutex bridge_mutex;
    Json bridge = Json::array();
    // A client's Intl: the system's ICU, as a Windows client configures it.
    twidget::WidgetHost host(html, twidget::WidgetTheme{theme}, width, host_options, resources, factory, std::make_shared<twidget::IcuIntl>());
    host.onAction = [&](const twidget::WidgetAction& a) {
        Json call = Json::object();
        Json args = Json::array();
        switch (a.kind) {
        case twidget::WidgetAction::Ready: call.set("method", "ready"); break;
        case twidget::WidgetAction::OpenLink: call.set("method", "openLink"); args.push(a.url); break;
        case twidget::WidgetAction::OpenTelegramLink: call.set("method", "openTelegramLink"); args.push(a.url); break;
        case twidget::WidgetAction::Haptic:
            call.set("method", "haptic");
            args.push(a.hapticKind);
            if (!a.hapticValue.empty()) args.push(a.hapticValue);
            break;
        }
        call.set("args", std::move(args));
        std::lock_guard l(bridge_mutex);
        bridge.push(std::move(call));
    };
    Json commits = Json::array();
    host.onStorageCommit = [&](const std::string& area, const std::map<std::string, std::string>& values) {
        Json o = Json::object();
        for (const auto& [k, v] : values) o.set(k, v);
        std::lock_guard l(bridge_mutex);
        commits.push(Json::object().set("area", area).set("values", std::move(o)));
    };
    // The runner plays the test bot: each request gets the result the manifest gives for its data.
    const Json& bot = test["bot"];
    std::map<std::string, int> asked;
    host.onRequest = [&](const twidget::WidgetRequest& request, std::function<void(const twidget::WidgetRequestOutcome&)> respond) {
        twidget::WidgetRequestOutcome outcome;
        const Json& answer = bot[request.data];
        if (!answer.isObject()) {
            outcome.failure = twidget::WidgetRequestFailure::Failed;
        } else if (answer["result"].isString()) {
            std::string result = answer["result"].asString();
            auto fill = [&](const std::string& name, const std::string& value) {
                for (size_t at = result.find(name); at != std::string::npos; at = result.find(name, at + value.size())) result.replace(at, name.size(), value);
            };
            fill("{n}", std::to_string(++asked[request.data]));
            fill("{from}", "Conformance");
            fill("{time}", "12:00");
            outcome.answer.result = std::move(result);
        }
        respond(outcome);
    };
    host.start();

    const Json& actions = test["actions"];
    bool scripted = !actions.arr().empty();
    Verdict verdict;
    if (test["stops"].isString()) {
        // The stop is the result, and a renderer's paint limit reaches the host a frame after the batch that may also
        // publish `done`: wait for the death instead (CLIENTS.md, step 4).
        host.waitUntil([](const twidget::WidgetDiagnostics& d) { return d.dead.has_value(); }, 20);
        verdict = renderer->verdict();
    } else {
        verdict = renderer->wait_for(scripted ? std::initializer_list<const char*>{"done", "awaiting"} : std::initializer_list<const char*>{"done"}, 20s);
    }
    bool chat_focused = !renderer->editing();

    Json skipped = Json::array();
    Bubble bubble(*renderer);
    if (scripted && verdict.status() == "awaiting") {
        for (size_t i = 0; i < actions.arr().size(); i++) {
            const Json& a = actions[i];
            Verdict now = renderer->verdict();
            auto at = [&](const char* name) { return target(a[name], now); };
            // A preview takes no input: a tap on it picks the inline result.
            if (host_options.context.preview && !a.find("theme") && !a.find("visible") && !a.find("wait")) {
                skipped.push(double(i));
                continue;
            }
            if (a.find("tap")) {
                if (auto p = at("tap")) {
                    bubble.down(p->x, p->y);
                    std::this_thread::sleep_for(60ms);
                    bubble.up(p->x, p->y);
                } else skipped.push(double(i));
            } else if (a.find("dblclick")) {
                if (auto p = at("dblclick")) {
                    for (int n = 0; n < 2; n++) {
                        bubble.down(p->x, p->y);
                        std::this_thread::sleep_for(40ms);
                        bubble.up(p->x, p->y);
                        std::this_thread::sleep_for(60ms);
                    }
                } else skipped.push(double(i));
            } else if (a.find("press")) {
                if (auto p = at("press")) {
                    bubble.down(p->x, p->y);
                    bubble.hold(a["ms"].asNumber(400), p->x, p->y);
                    bubble.up(p->x, p->y);
                } else skipped.push(double(i));
            } else if (a.find("rightclick")) {
                if (auto p = at("rightclick")) {
                    bubble.down(p->x, p->y, true, 2);
                    std::this_thread::sleep_for(60ms);
                    bubble.up(p->x, p->y);
                } else skipped.push(double(i));
            } else if (a.find("drag")) {
                auto from = at("drag");
                std::optional<Point> to;
                if (from && a["by"].asBool() && a["to"].isArray()) to = Point{from->x + a["to"][size_t(0)].asNumber(), from->y + a["to"][size_t(1)].asNumber()};
                else to = at("to");
                if (from && to) {
                    int steps = std::max(1, int(a["steps"].asNumber(10)));
                    bubble.down(from->x, from->y);
                    for (int s = 1; s <= steps; s++) {
                        std::this_thread::sleep_for(16ms);
                        bubble.move(from->x + (to->x - from->x) * s / steps, from->y + (to->y - from->y) * s / steps);
                    }
                    std::this_thread::sleep_for(16ms);
                    bubble.up(to->x, to->y);
                } else skipped.push(double(i));
            } else if (a.find("hover")) {
                if (auto p = at("hover")) renderer->hover(0, p->x, p->y);
                else skipped.push(double(i));
            } else if (a.find("key")) {
                std::string key = a["key"].asString();
                renderer->key("keydown", key, key_code(key));
                renderer->key("keyup", key, key_code(key));
            } else if (a.find("wheel")) {
                if (auto p = at("wheel")) renderer->wheel(p->x, p->y, a["dy"].asNumber());
                else skipped.push(double(i));
            } else if (a.find("theme")) {
                host.setTheme(twidget::WidgetTheme{a["theme"].asString()});
            } else if (a.find("visible")) {
                bool visible = a["visible"].asBool();
                host.setVisible(visible);
                renderer->set_visible(visible);
            } else if (a.find("wait")) {
                std::this_thread::sleep_for(std::chrono::milliseconds(int64_t(a["wait"].asNumber())));
            } else {
                skipped.push(double(i));
            }
            std::this_thread::sleep_for(350ms);
        }
        verdict = renderer->wait_for({"done"}, 10s);
    }

    Json stages = Json::array();
    const Json& wanted = test["stages"];
    if (!wanted.arr().empty()) {
        Image image = renderer->snapshot();
        for (const Json& stage : wanted.arr()) {
            bool themed = false;
            for (const Json& t : stage["themes"].arr()) themed = themed || t.asString() == theme;
            if (!themed) continue;
            std::string name = stage["name"].asString();
            // A visual widget's stages have no fixed rect: its harness measures them.
            const Json& r = stage["rect"].isArray() || !verdict.value ? stage["rect"] : (*verdict.value)["stages"][name];
            if (!r.isArray() || r.arr().size() < 4) continue;
            std::string file = id;
            std::replace(file.begin(), file.end(), '/', '_');
            file += "." + theme + "." + name + ".png";
            write_png(device.wic.get(), crops / file, crop(image, r[size_t(0)].asNumber(), r[size_t(1)].asNumber(), r[size_t(2)].asNumber(), r[size_t(3)].asNumber()));
            stages.push(Json::object().set("name", name).set("crop", "crops/" + file).set("rect", r)
                            .set("beyondCap", r[size_t(1)].asNumber() + r[size_t(3)].asNumber() > 600));
        }
    }
    std::string full;
    if (options.visual && width == 390) {
        full = id;
        std::replace(full.begin(), full.end(), '/', '_');
        full = "crops/" + full + "." + theme + ".full.png";
        write_png(device.wic.get(), options.out / full, renderer->snapshot());
    }

    twidget::WidgetDiagnostics diagnostics = host.diagnostics();
    host.destroy();

    Json errors = Json::array();
    for (const auto& [level, text] : diagnostics.logs)
        if (level == "error") errors.push(text);
    Json checks = verdict.value ? (*verdict.value)["checks"] : Json::array();
    Json observed = Json::object();
    {
        std::lock_guard l(bridge_mutex);
        observed.set("bridge", bridge);
        observed.set("storageCommits", commits);
    }
    observed.set("height", diagnostics.height).set("chatFocused", chat_focused).set("syntheticInput", false);

    Json run = Json::object();
    run.set("id", id).set("theme", theme).set("width", width).set("loaded", diagnostics.loaded);
    run.set("dead", diagnostics.dead ? Json(*diagnostics.dead) : Json(nullptr));
    run.set("status", verdict.status().empty() ? std::string("running") : verdict.status());
    run.set("checks", checks).set("stages", std::move(stages)).set("observed", std::move(observed));
    run.set("skippedActions", std::move(skipped)).set("errors", std::move(errors));
    if (!full.empty()) run.set("full", full);
    return run;
}

} // namespace

int main(int argc, char** argv) {
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    Options options;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--width" && i + 1 < argc) options.widths.push_back(std::atof(argv[++i]));
        else if (arg == "--filter" && i + 1 < argc) options.filter = argv[++i];
        else if (arg == "--sandbox" && i + 1 < argc) options.sandbox = argv[++i];
        else if (arg == "--visual") options.visual = true;
        else if (arg == "--storage" && i + 1 < argc) {
            auto preload = Json::parse(read_file(argv[++i]));
            auto area = [&](const char* name) {
                twidget::StorageArea out;
                if (preload)
                    for (const auto& [k, v] : (*preload)[name].obj()) out[k] = v.asString();
                return out;
            };
            options.storage = twidget::WidgetStorage{area("local"), area("session")};
        }
        else options.out = arg;
    }
    const bool own_widths = !options.widths.empty();
    if (options.widths.empty()) options.widths = {390};

    winrt::init_apartment(winrt::apartment_type::multi_threaded);
    Device device;
    auto resources = twidget::SandboxResources::fromDirectory((options.sandbox / "packages/desktop/WidgetJS").string());
    auto manifest = Json::parse(read_file(options.sandbox / "packages/conformance/manifest.json"));
    if (!manifest) {
        std::printf("cannot read the manifest under %s\n", options.sandbox.string().c_str());
        return 2;
    }

    fs::path crops = options.out / "crops";
    fs::create_directories(crops);

    Json runs = Json::array();
    for (const Json& test : (*manifest)["tests"].arr()) {
        if (test["set"].asString() != (options.visual ? "visual" : "auto") || test["group"].asString() == "sdk") continue;
        const std::string& id = test["id"].asString();
        if (!options.filter.empty() && id.find(options.filter) == std::string::npos) continue;
        std::vector<double> widths = options.widths;
        if (options.visual && !own_widths) {
            widths.clear();
            for (const Json& w : test["widths"].arr()) widths.push_back(w.asNumber());
        }
        for (double width : widths) {
            for (const Json& theme : test["themes"].arr()) {
                double t0 = now_ms();
                Json run = run_one(device, resources, options, test, theme.asString(), width, crops);
                int pass = 0, fail = 0;
                for (const Json& c : run["checks"].arr()) {
                    const std::string& status = c[size_t(2)].asString();
                    pass += status == "pass";
                    fail += status == "fail";
                }
                std::printf("%-28s %-5s %3.0f  %-8s pass %3d fail %3d  %5.1f s\n", id.c_str(), theme.asString().c_str(), width, run["status"].asString().c_str(), pass, fail,
                            (now_ms() - t0) / 1000);
                runs.push(std::move(run));
            }
        }
    }

    char date[32];
    std::time_t t = std::time(nullptr);
    std::tm utc;
    gmtime_s(&utc, &t);
    std::strftime(date, sizeof date, "%Y-%m-%dT%H:%M:%SZ", &utc);
    Json file = Json::object();
    file.set("platform", "windows").set("renderer", "twidget-core").set("engine", "TwidgetCore, Direct2D (WARP)");
    file.set("framework", TW_FRAMEWORK).set("commit", TW_COMMIT).set("policy", "strict");
    file.set("features", Json(Json::Array{Json("webAnimations")})).set("date", date).set("runs", std::move(runs));
    file.set("palette", "the set's theme params").set("locale", "en");
    fs::path written = options.out / (options.visual ? "windows-twidget-core.visual.run.json" : "run.json");
    std::ofstream(written, std::ios::binary) << file.dump();
    std::printf("wrote %s\n", written.string().c_str());
    return 0;
}
