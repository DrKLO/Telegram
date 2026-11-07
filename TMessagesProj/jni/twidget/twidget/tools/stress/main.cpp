// widget-sandbox's stress kit on TwidgetCore, following packages/stress/CLIENTS.md: every widget of the manifest
// through the desktop host in the pass's configuration, in a 600 px bubble at the reference width, light theme.
// The renderer is a Windows client's: a frame clock of 60 Hz on the compositor clock, every frame the core changed
// painted by the Direct2D backend on the GPU into a bitmap the size of the bubble, and the core's compositor layers
// rasterised when their content changes. "Your frames" are that clock's ticks; the renderer's own work is the
// batches it applied (parse and receive), its ticks and its paints.
//
//   twidget-stress [out dir] [--pass measure|production] [--filter text] [--width N] [--dpr N]
//                  [--warmup ms] [--measure ms] [--no-cool] [--sandbox <widget-sandbox>]
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
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <map>
#include <mutex>
#include <optional>
#include <sstream>
#include <thread>

using twidget::Json;
namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace {

// The rate a Windows client's widget clock runs at, whatever the display's: the frame period of the run file.
constexpr double FRAME_HZ = 60;
constexpr double MAX_HEIGHT = 600;

double now_ms() {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

std::string read_file(const fs::path& path) {
    std::ifstream in(path, std::ios::binary);
    std::ostringstream s;
    s << in.rdbuf();
    return s.str();
}

std::string narrow(const wchar_t* text) {
    int n = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    std::string out(n > 0 ? n - 1 : 0, '\0');
    if (n > 1) WideCharToMultiByte(CP_UTF8, 0, text, -1, out.data(), n, nullptr, nullptr);
    return out;
}

struct Device {
    winrt::com_ptr<ID3D11Device> d3d;
    winrt::com_ptr<ID2D1Factory1> factory;
    winrt::com_ptr<ID2D1Device> d2d;
    winrt::com_ptr<IDWriteFactory> dwrite;
    winrt::com_ptr<IWICImagingFactory> wic;
    std::string adapter;

    // The GPU, as a client paints: these are timings, not pixels to compare.
    Device() {
        winrt::check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT, nullptr, 0, D3D11_SDK_VERSION, d3d.put(), nullptr, nullptr));
        winrt::check_hresult(D2D1CreateFactory(D2D1_FACTORY_TYPE_MULTI_THREADED, factory.put()));
        winrt::check_hresult(factory->CreateDevice(d3d.as<IDXGIDevice>().get(), d2d.put()));
        winrt::check_hresult(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), reinterpret_cast<IUnknown**>(dwrite.put())));
        winrt::check_hresult(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(wic.put())));
        winrt::com_ptr<IDXGIAdapter> dxgi;
        DXGI_ADAPTER_DESC desc{};
        if (SUCCEEDED(d3d.as<IDXGIDevice>()->GetAdapter(dxgi.put())) && SUCCEEDED(dxgi->GetDesc(&desc))) adapter = narrow(desc.Description);
    }
};

/// The compositor's next frame, or WAIT_FAILED before Windows 11, which has no compositor clock to wait on.
DWORD wait_for_compositor_clock(DWORD timeout_ms) {
    using Wait = DWORD(WINAPI*)(UINT, const HANDLE*, DWORD);
    static const Wait wait = [] {
        HMODULE dcomp = LoadLibraryW(L"dcomp.dll");
        return dcomp ? reinterpret_cast<Wait>(GetProcAddress(dcomp, "DCompositionWaitForCompositorClock")) : nullptr;
    }();
    return wait ? wait(0, nullptr, timeout_ms) : WAIT_FAILED;
}

struct Tick {
    double at = 0;
    double work_ms = 0;  // the renderer's own work since the previous tick, this one's included
};

/// What the widget publishes in `data-stress`, and when it reached the host.
struct Published {
    double at = 0;
    Json value;
};

/// A compositor layer's raster: the client keeps one surface per layer and draws it again only when its content
/// changes; moving it is the compositor's work, outside the renderer's.
struct LayerRaster {
    winrt::com_ptr<ID2D1Bitmap1> bitmap;
    uint32_t w = 0, h = 0;
    uint64_t content_version = 0;
};

/// WidgetHost's Renderer on TwidgetCore, ticked and painted as a Windows client's: frames requested by the widget,
/// animations and paints wait for the next tick of a 60 Hz clock aligned to the compositor's, and transform and
/// opacity animations are compositor layers. The core is not locked on its side: the host thread (messages) and the
/// clock thread (frames, paints) take turns on one mutex.
class StressRenderer final : public twidget::Renderer {
public:
    StressRenderer(Device& device, double width, double dpr)
        : backend_(device.d2d.get(), device.dwrite.get(), device.wic.get()),
          core_(std::make_unique<twlite::Renderer>(width, dpr, false, backend_)), dpr_(dpr) {
        std::vector<Json> out;
        core_->set_max_height(MAX_HEIGHT, out);
        core_->set_compositing(true);
        winrt::check_hresult(device.d2d->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, ctx_.put()));
        clock_ = std::thread([this] { clock_loop(); });
    }
    ~StressRenderer() override { destroy(); }

    void load() override {
        if (onMessage) onMessage("{\"type\":\"ready\"}");
        schedule();
    }

    void send(const std::string& json) override {
        if (json.rfind("{\"type\":\"requestFrame\"", 0) == 0) {
            {
                std::lock_guard l(clock_mutex_);
                frame_requested_ = true;
            }
            clock_cv_.notify_all();
            return;
        }
        double t0 = now_ms();
        auto msg = Json::parse(json);
        if (!msg) return;
        if ((*msg)["type"].asString() == "ops") probe((*msg)["ops"]);
        std::vector<Json> out;
        {
            std::lock_guard l(mutex_);
            if (!core_) return;
            out = core_->receive(*msg);
        }
        add_work(now_ms() - t0);
        for (const Json& m : out)
            if (onMessage) onMessage(m.dump());
        schedule();
    }

    void setTheme(const std::string& theme) override {
        std::vector<Json> out;
        {
            std::lock_guard l(mutex_);
            if (!core_) return;
            core_->set_theme(theme == "dark", out);
        }
        for (const Json& m : out)
            if (onMessage) onMessage(m.dump());
        schedule();
    }

    void setWidth(double width) override {
        std::vector<Json> out;
        {
            std::lock_guard l(mutex_);
            if (!core_) return;
            core_->set_width(width, out);
        }
        for (const Json& m : out)
            if (onMessage) onMessage(m.dump());
        schedule();
    }

    void destroy() override {
        {
            std::lock_guard l(clock_mutex_);
            if (stop_) return;
            stop_ = true;
        }
        clock_cv_.notify_all();
        if (clock_.joinable()) clock_.join();
        std::lock_guard l(mutex_);
        core_.reset();
    }

    /// Ticks from `from` on.
    std::vector<Tick> ticks_since(double from) {
        std::lock_guard l(stat_mutex_);
        std::vector<Tick> out;
        for (const Tick& t : ticks_)
            if (t.at >= from) out.push_back(t);
        return out;
    }
    /// What the widget published in `data-stress` from `from` on, and the last value before it.
    std::vector<Published> published_since(double from, std::optional<Published>& before) {
        std::lock_guard l(stat_mutex_);
        std::vector<Published> out;
        before.reset();
        for (const Published& p : published_) {
            if (p.at >= from) out.push_back(p);
            else before = p;
        }
        return out;
    }
    std::optional<Json> last_published() {
        std::lock_guard l(stat_mutex_);
        if (published_.empty()) return std::nullopt;
        return published_.back().value;
    }
    int64_t ops() const { return ops_; }
    int64_t max_batch() const { return max_batch_; }
    size_t display_list_size() const { return display_list_size_; }

private:
    void schedule() {
        {
            std::lock_guard l(clock_mutex_);
            paint_scheduled_ = true;
        }
        clock_cv_.notify_all();
    }

    void add_work(double ms) {
        std::lock_guard l(stat_mutex_);
        pending_work_ += ms;
    }

    // The validated ops, as CLIENTS.md reads them: their count, and the html element's `data-stress`.
    void probe(const Json& ops) {
        int64_t n = int64_t(ops.arr().size());
        ops_ += n;
        if (n > max_batch_) max_batch_ = n;
        for (const Json& op : ops.arr()) {
            const std::string& kind = op["op"].asString();
            if (kind == "el" && op["adopt"].asString() == "html") {
                html_ = int64_t(op["id"].asNumber());
                for (const Json& a : op["attrs"].arr())
                    if (a[size_t(0)].asString() == "data-stress") publish(a[size_t(1)].asString());
            } else if (kind == "attr" && html_ && int64_t(op["id"].asNumber()) == html_ && op["name"].asString() == "data-stress") {
                publish(op["value"].asString());
            }
        }
    }

    void publish(const std::string& value) {
        auto parsed = Json::parse(value);
        if (!parsed) return;
        std::lock_guard l(stat_mutex_);
        published_.push_back({now_ms(), std::move(*parsed)});
    }

    // At most FRAME_HZ: the first compositor frame at least a period after the last tick, less half a refresh,
    // so that the ticks land on the compositor's frames.
    void wait_for_tick() {
        const double interval = 1000 / FRAME_HZ;
        for (;;) {
            DWORD result = wait_for_compositor_clock(17);
            if (result == WAIT_FAILED) break;
            double now = now_ms();
            if (result == WAIT_OBJECT_0) {
                if (last_frame_) refresh_ = now - last_frame_;
                last_frame_ = now;
            }
            if (now - last_tick_ + refresh_ / 2 >= interval) {
                last_tick_ = now;
                return;
            }
        }
        double left = last_tick_ + interval - now_ms();
        if (left > 0) std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(left));
        last_tick_ = now_ms();
    }

    void clock_loop() {
        double wake_at = 0;
        for (;;) {
            bool frame, paint, tick;
            {
                std::unique_lock l(clock_mutex_);
                // Idle, there is no frame: the clock waits for work, polling only for an animation's wake time.
                if (!clock_cv_.wait_for(l, 10ms, [&] { return stop_ || frame_requested_ || paint_scheduled_ || ticking_ || (wake_at && now_ms() >= wake_at); }))
                    continue;
                if (stop_) break;
                l.unlock();
                wait_for_tick();
                l.lock();
                if (stop_) break;
                frame = frame_requested_;
                paint = paint_scheduled_;
                tick = ticking_ || (wake_at && now_ms() >= wake_at);
                frame_requested_ = paint_scheduled_ = false;
            }
            double at = now_ms();
            if (frame && onMessage) onMessage("{\"type\":\"frame\",\"time\":" + Json::number(at) + "}");
            double t0 = now_ms();
            std::vector<Json> out;
            {
                std::lock_guard l(mutex_);
                if (!core_) break;
                if (tick) {
                    out = core_->tick();
                    double next = core_->next_tick_in();
                    wake_at = std::isfinite(next) ? now_ms() + next * 1000 : 0;
                }
                paint = paint || core_->dirty();
                {
                    std::lock_guard c(clock_mutex_);
                    ticking_ = core_->wants_tick();
                }
                if (paint) draw();
            }
            double work = now_ms() - t0;
            {
                std::lock_guard l(stat_mutex_);
                ticks_.push_back({at, pending_work_ + (tick || paint ? work : 0)});
                pending_work_ = 0;
            }
            for (const Json& m : out)
                if (onMessage) onMessage(m.dump());
        }
    }

    // Under mutex_.
    void draw() {
        uint32_t w = core_->px_width(), h = core_->px_height();
        if (!w || !h) return;
        twlite::DisplayList list = core_->paint();
        display_list_size_ = list.size();
        if (!target_ || w != target_w_ || h != target_h_) {
            target_ = nullptr;
            auto format = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
            winrt::check_hresult(ctx_->CreateBitmap(D2D1::SizeU(w, h), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, format), target_.put()));
            target_w_ = w;
            target_h_ = h;
        }
        ctx_->SetTarget(target_.get());
        ctx_->BeginDraw();
        ctx_->Clear(D2D1::ColorF(0, 0));
        ctx_->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
        ctx_->SetTransform(D2D1::Matrix3x2F::Scale(float(dpr_), float(dpr_)));
        backend_.paint(list, ctx_.get());
        ctx_->EndDraw();
        ctx_->SetTarget(nullptr);

        const twlite::Composition& composition = core_->composition();
        if (composition.version == composition_version_) return;
        composition_version_ = composition.version;
        std::map<int64_t, LayerRaster> alive;
        for (const twlite::Layer& layer : composition.layers) {
            LayerRaster raster = std::move(layers_[layer.id]);
            float scale = float(dpr_) * layer.raster_scale;
            uint32_t pw = uint32_t(std::ceil(layer.bounds.w * scale)), ph = uint32_t(std::ceil(layer.bounds.h * scale));
            if (pw && ph && (!raster.bitmap || pw != raster.w || ph != raster.h || raster.content_version != layer.content_version)) {
                if (!raster.bitmap || pw != raster.w || ph != raster.h) {
                    raster.bitmap = nullptr;
                    auto format = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
                    winrt::check_hresult(ctx_->CreateBitmap(D2D1::SizeU(pw, ph), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, format), raster.bitmap.put()));
                    raster.w = pw;
                    raster.h = ph;
                }
                ctx_->SetTarget(raster.bitmap.get());
                ctx_->BeginDraw();
                ctx_->Clear(D2D1::ColorF(0, 0));
                ctx_->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
                ctx_->SetTransform(D2D1::Matrix3x2F::Translation(-layer.bounds.x, -layer.bounds.y) * D2D1::Matrix3x2F::Scale(scale, scale));
                backend_.paint(layer.content, ctx_.get());
                ctx_->EndDraw();
                ctx_->SetTarget(nullptr);
                raster.content_version = layer.content_version;
            }
            alive[layer.id] = std::move(raster);
        }
        layers_ = std::move(alive);
    }

    twlite::D2DBackend backend_;
    std::mutex mutex_;  // core_, the target and every call into them
    std::unique_ptr<twlite::Renderer> core_;
    double dpr_;
    winrt::com_ptr<ID2D1DeviceContext> ctx_;
    winrt::com_ptr<ID2D1Bitmap1> target_;
    uint32_t target_w_ = 0, target_h_ = 0;
    std::map<int64_t, LayerRaster> layers_;
    uint64_t composition_version_ = 0;
    std::atomic<size_t> display_list_size_{0};

    std::mutex clock_mutex_;
    std::condition_variable clock_cv_;
    std::thread clock_;
    bool frame_requested_ = false, paint_scheduled_ = false, ticking_ = false, stop_ = false;
    double last_tick_ = 0, last_frame_ = 0, refresh_ = 0;  // the clock thread's

    int64_t html_ = 0;  // the host thread's only
    std::atomic<int64_t> ops_{0}, max_batch_{0};
    std::mutex stat_mutex_;
    std::vector<Tick> ticks_;
    double pending_work_ = 0;
    std::vector<Published> published_;
};

struct Options {
    fs::path sandbox = TW_SANDBOX;
    fs::path out = "stress-run";
    std::string pass = "measure";
    std::string filter;
    double width = 0;  // the manifest's
    double dpr = 0;    // the system's
    double warmup_ms = 0, measure_ms = 0;
    bool cool = true;
};

/// The host configuration of the pass (CLIENTS.md, "Host configuration").
twidget::WidgetHostOptions host_options(const Options& options) {
    twidget::WidgetHostOptions o;
    o.devicePixelRatio = options.dpr;
    o.features = {"webAnimations"};
    if (options.pass == "measure") {
        o.policy = "compat";
        o.cpuLimitMs = 500;
        o.cpuQuotaMs = 10000;
        o.cpuQuotaWindowMs = 1000;
        o.mainThreadQuotaMs = 10000;
        o.maxTimers = 256;
        o.memoryLimitBytes = size_t(64) << 20;
    }
    return o;
}

Json config_json(const twidget::WidgetHostOptions& o) {
    return Json::object().set("policy", o.policy).set("cpuLimitMs", o.cpuLimitMs).set("cpuQuotaMs", o.cpuQuotaMs)
        .set("cpuQuotaWindowMs", o.cpuQuotaWindowMs).set("mainThreadQuotaMs", o.mainThreadQuotaMs)
        .set("maxTimers", double(o.maxTimers)).set("memoryLimitBytes", double(o.memoryLimitBytes));
}

double percentile(std::vector<double> v, double p) {
    if (v.empty()) return 0;
    std::sort(v.begin(), v.end());
    return v[std::min(v.size() - 1, size_t(p * (v.size() - 1) + 0.5))];
}

Json nullable(std::optional<double> v) {
    return v ? Json(*v) : Json(nullptr);
}

Json run_one(Device& device, const std::shared_ptr<twidget::SandboxResources>& resources, const Options& options, const Json& widget,
             double warmup_ms, double measure_ms) {
    const std::string html = read_file(options.sandbox / "packages/stress" / widget["file"].asString());
    twidget::WidgetHostOptions o = host_options(options);

    StressRenderer* renderer = nullptr;
    auto factory = [&](std::shared_ptr<twidget::SandboxResources>, const std::string&, double w) -> std::unique_ptr<twidget::Renderer> {
        auto r = std::make_unique<StressRenderer>(device, w, options.dpr);
        renderer = r.get();
        return r;
    };

    const double created = now_ms();
    std::atomic<double> died_at{0};
    twidget::WidgetHost host(html, twidget::WidgetTheme{"light"}, options.width, o, resources, factory, std::make_shared<twidget::IcuIntl>());
    host.onChange = [&](const twidget::WidgetDiagnostics& d) {
        if (d.dead && died_at == 0) died_at = now_ms() - created;
    };
    host.start();

    // 2. Loaded (or dead), at most 60 s.
    std::optional<double> boot_ms;
    for (double deadline = created + 60000; now_ms() < deadline; std::this_thread::sleep_for(5ms)) {
        twidget::WidgetDiagnostics d = host.diagnostics();
        if (d.loaded) {
            boot_ms = now_ms() - created;
            break;
        }
        if (d.dead) break;
    }

    // 3–6. Warm up, then the window, reading the widget's own numbers every 500 ms as a client would.
    std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(warmup_ms));
    twidget::WidgetDiagnostics start = host.diagnostics();
    const int64_t ops_start = renderer->ops();
    const double window_start = now_ms();
    for (double end = window_start + measure_ms; now_ms() < end;)
        std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(std::min(500.0, end - now_ms())));
    const double window_end = now_ms();
    twidget::WidgetDiagnostics last = host.diagnostics();
    const int64_t ops_end = renderer->ops();
    const double seconds = (window_end - window_start) / 1000;

    std::vector<Tick> ticks = renderer->ticks_since(window_start);
    std::optional<Published> before;
    std::vector<Published> published = renderer->published_since(window_start, before);
    std::optional<Json> final_value = renderer->last_published();
    const size_t display_list = renderer->display_list_size();
    const int64_t max_batch = renderer->max_batch();

    // 7. Destroy the host.
    host.destroy();

    Json run = Json::object();
    run.set("id", widget["id"]).set("load", widget["load"]).set("value", widget["value"]);
    run.set("loaded", last.loaded).set("bootMs", nullable(boot_ms));
    run.set("dead", last.dead ? Json(*last.dead) : Json(nullptr));
    run.set("diedAtMs", last.dead && died_at > 0 ? Json(double(died_at)) : Json(nullptr));

    // The clock's ticks during the window, as intervals.
    const double period = 1000 / FRAME_HZ;
    std::vector<double> intervals, work;
    double missed = 0, slots = 0;
    for (size_t i = 0; i < ticks.size(); i++) {
        if (ticks[i].at > window_end) break;
        if (ticks[i].work_ms > 0) work.push_back(ticks[i].work_ms);
        if (i == 0) continue;
        double interval = ticks[i].at - ticks[i - 1].at;
        intervals.push_back(interval);
        double n = std::max(1.0, std::round(interval / period));
        missed += n - 1;
        slots += n;
    }
    Json frames = Json::object().set("count", double(intervals.size()));
    if (intervals.empty()) {
        frames.set("p50", Json(nullptr)).set("p95", Json(nullptr)).set("max", Json(nullptr)).set("dropped", Json(nullptr));
    } else {
        frames.set("p50", percentile(intervals, 0.5)).set("p95", percentile(intervals, 0.95))
            .set("max", *std::max_element(intervals.begin(), intervals.end())).set("dropped", missed / slots);
    }
    frames.set("longTaskMs", Json(nullptr)).set("mainThreadMsPerSec", Json(nullptr));
    run.set("frames", std::move(frames));

    Json own = Json::object();
    if (work.empty()) {
        own.set("msPerFrame", Json(nullptr)).set("p95Ms", Json(nullptr));
    } else {
        double sum = 0;
        for (double w : work) sum += w;
        own.set("msPerFrame", sum / work.size()).set("p95Ms", percentile(work, 0.95));
    }
    own.set("displayListOps", double(display_list));
    run.set("renderer", std::move(own));

    Json host_json = Json::object();
    host_json.set("jsMsPerSec", (last.stats.jsMs - start.stats.jsMs) / seconds)
        .set("opsPerSec", double(ops_end - ops_start) / seconds)
        .set("queriesPerSec", (last.stats.queries - start.stats.queries) / seconds)
        .set("queryMsPerSec", (last.stats.queryMs - start.stats.queryMs) / seconds)
        .set("nodes", double(last.nodes)).set("memoryBytes", double(last.stats.memoryBytes))
        .set("throttledTasks", double(last.stats.throttledTasks - start.stats.throttledTasks))
        .set("maxBatchOps", double(max_batch)).set("processMemoryMb", Json(nullptr));
    run.set("host", std::move(host_json));

    // The widget's own numbers over the window: from the value before it to the last one in it.
    Json widget_json = Json::object();
    if (!published.empty()) {
        const Json& a = before ? before->value : published.front().value;
        const Json& b = published.back().value;
        double dt = b["t"].asNumber() - a["t"].asNumber();
        double dframes = b["frames"].asNumber() - a["frames"].asNumber();
        double max_gap = 0;
        for (const Published& p : published) max_gap = std::max(max_gap, p.value["maxGap"].asNumber());
        widget_json.set("fps", dt > 0 ? Json(dframes * 1000 / dt) : Json(nullptr)).set("frames", dframes)
            .set("maxGap", max_gap).set("jank", b["jank"].asNumber() - a["jank"].asNumber());
    } else {
        widget_json.set("fps", Json(nullptr)).set("frames", Json(nullptr)).set("maxGap", Json(nullptr)).set("jank", Json(nullptr));
    }
    widget_json.set("last", final_value ? *final_value : Json(nullptr));
    run.set("widget", std::move(widget_json));

    run.set("thermal", Json(nullptr));
    Json errors = Json::array();
    for (const auto& [level, text] : last.logs)
        if (level == "error" && errors.arr().size() < 20) errors.push(text);
    run.set("errors", std::move(errors));
    Json diags = Json::object();
    for (const auto& [kind, entry] : last.diags) diags.set(kind, double(entry.first));
    run.set("diags", std::move(diags));
    return run;
}

std::string cpu_name() {
    wchar_t name[256] = {};
    DWORD size = sizeof name;
    if (RegGetValueW(HKEY_LOCAL_MACHINE, L"HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", L"ProcessorNameString", RRF_RT_REG_SZ, nullptr, name, &size) != ERROR_SUCCESS) return "unknown CPU";
    std::string out = narrow(name);
    while (!out.empty() && out.back() == ' ') out.pop_back();
    return out;
}

std::string os_version() {
    using RtlGetVersion = LONG(WINAPI*)(OSVERSIONINFOW*);
    OSVERSIONINFOW info{sizeof info};
    auto get = reinterpret_cast<RtlGetVersion>(GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlGetVersion"));
    if (!get || get(&info) != 0) return "Windows";
    return "Windows " + std::to_string(info.dwMajorVersion) + "." + std::to_string(info.dwMinorVersion) + "." + std::to_string(info.dwBuildNumber);
}

}  // namespace

int main(int argc, char** argv) {
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    Options options;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--pass" && i + 1 < argc) options.pass = argv[++i];
        else if (arg == "--filter" && i + 1 < argc) options.filter = argv[++i];
        else if (arg == "--width" && i + 1 < argc) options.width = std::atof(argv[++i]);
        else if (arg == "--dpr" && i + 1 < argc) options.dpr = std::atof(argv[++i]);
        else if (arg == "--warmup" && i + 1 < argc) options.warmup_ms = std::atof(argv[++i]);
        else if (arg == "--measure" && i + 1 < argc) options.measure_ms = std::atof(argv[++i]);
        else if (arg == "--no-cool") options.cool = false;
        else if (arg == "--sandbox" && i + 1 < argc) options.sandbox = argv[++i];
        else options.out = arg;
    }
    if (options.pass != "measure" && options.pass != "production") {
        std::printf("--pass is measure or production\n");
        return 2;
    }

    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    if (options.dpr <= 0) options.dpr = GetDpiForSystem() / 96.0;

    winrt::init_apartment(winrt::apartment_type::multi_threaded);
    Device device;
    auto resources = twidget::SandboxResources::fromDirectory((options.sandbox / "packages/desktop/WidgetJS").string());
    auto manifest = Json::parse(read_file(options.sandbox / "packages/stress/manifest.json"));
    if (!manifest) {
        std::printf("cannot read the stress manifest under %s\n", options.sandbox.string().c_str());
        return 2;
    }
    const Json& defaults = (*manifest)["run"];
    if (options.width <= 0) options.width = defaults["width"].asNumber(390);
    const double warmup_ms = options.warmup_ms > 0 ? options.warmup_ms : defaults["warmupMs"].asNumber(2000);
    const double measure_ms = options.measure_ms > 0 ? options.measure_ms : defaults["measureMs"].asNumber(10000);
    fs::create_directories(options.out);

    std::vector<const Json*> widgets;
    for (const Json& w : (*manifest)["widgets"].arr())
        if (options.filter.empty() || w["id"].asString().find(options.filter) != std::string::npos) widgets.push_back(&w);

    Json runs = Json::array();
    for (size_t i = 0; i < widgets.size(); i++) {
        const Json& widget = *widgets[i];
        Json run = run_one(device, resources, options, widget, warmup_ms, measure_ms);
        std::printf("%-22s %s  frames p50 %5.1f p95 %5.1f dropped %4.1f%%  renderer %6.2f ms  widget %5.1f fps  js %6.1f ms/s%s%s\n",
                    widget["id"].asString().c_str(), run["loaded"].asBool() ? "loaded" : "      ",
                    run["frames"]["p50"].asNumber(), run["frames"]["p95"].asNumber(), run["frames"]["dropped"].asNumber() * 100,
                    run["renderer"]["msPerFrame"].asNumber(), run["widget"]["fps"].asNumber(), run["host"]["jsMsPerSec"].asNumber(),
                    run["dead"].isString() ? "  dead: " : "", run["dead"].isString() ? run["dead"].asString().c_str() : "");
        runs.push(std::move(run));
        // 7. A second between levels; after a load's last level, 20 s for the machine to cool down.
        bool last_of_load = i + 1 == widgets.size() || (*widgets[i + 1])["load"].asString() != widget["load"].asString();
        if (i + 1 < widgets.size()) std::this_thread::sleep_for(options.cool && last_of_load ? 20s : 1s);
    }

    char date[32];
    std::time_t t = std::time(nullptr);
    std::tm utc;
    gmtime_s(&utc, &t);
    std::strftime(date, sizeof date, "%Y-%m-%dT%H:%M:%SZ", &utc);

    MEMORYSTATUSEX memory{sizeof memory};
    GlobalMemoryStatusEx(&memory);
    SYSTEM_POWER_STATUS power{};
    GetSystemPowerStatus(&power);

    twidget::WidgetHostOptions requested = host_options(options);
    Json config_requested = config_json(requested);
    config_requested.set("width", options.width).set("theme", "light").set("warmupMs", warmup_ms).set("measureMs", measure_ms);

    Json file = Json::object();
    file.set("kit", Json::object().set("version", (*manifest)["version"]).set("commit", TW_FRAMEWORK));
    file.set("platform", "windows").set("client", "Unigram").set("clientBuild", "twidget " TW_COMMIT).set("framework", TW_FRAMEWORK);
    file.set("renderer", "twidget-core").set("rendererKind", "own");
    file.set("engine", "TwidgetCore " TW_COMMIT ", Direct2D on " + device.adapter + "; the desktop host; a 60 Hz widget clock on the compositor clock");
    file.set("device", Json::object().set("model", cpu_name()).set("os", os_version()).set("class", "desktop")
                           .set("refreshHz", FRAME_HZ).set("dpr", options.dpr).set("ramMb", double(memory.ullTotalPhys >> 20))
                           .set("lowPowerMode", power.SystemStatusFlag == 1));
    file.set("pass", options.pass);
    file.set("config", Json::object().set("requested", std::move(config_requested)).set("applied", config_json(requested)));
    file.set("date", date).set("runs", std::move(runs));
    fs::path written = options.out / ("windows-twidget-core." + options.pass + ".run.json");
    std::ofstream(written, std::ios::binary) << file.dump();
    std::printf("wrote %s\n", written.string().c_str());
    return 0;
}
