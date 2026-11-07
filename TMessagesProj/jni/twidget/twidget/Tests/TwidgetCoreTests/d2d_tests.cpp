// Pixel checks of the Direct2D backend (platform/windows) on a WARP device, read back from the bitmaps
// it draws.
#include "d2d_backend.h"
#include "renderer.h"

#include <d3d11.h>
#include <dxgi.h>

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using twidget::Json;
using namespace twlite;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) {
        std::printf("FAIL: %s\n", what);
        failures++;
    }
}

static Json parse(const std::string& s) {
    auto j = Json::parse(s);
    if (!j) {
        std::printf("bad json: %s\n", s.c_str());
        std::exit(2);
    }
    return *j;
}

struct Device {
    winrt::com_ptr<ID3D11Device> d3d;
    winrt::com_ptr<ID2D1Factory1> factory;
    winrt::com_ptr<ID2D1Device> d2d;
    winrt::com_ptr<IDWriteFactory> dwrite;
    winrt::com_ptr<IWICImagingFactory> wic;

    Device() {
        winrt::check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_WARP, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT, nullptr, 0, D3D11_SDK_VERSION, d3d.put(), nullptr, nullptr));
        winrt::check_hresult(D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, factory.put()));
        winrt::check_hresult(factory->CreateDevice(d3d.as<IDXGIDevice>().get(), d2d.put()));
        winrt::check_hresult(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), reinterpret_cast<IUnknown**>(dwrite.put())));
        winrt::check_hresult(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(wic.put())));
    }

    /// `list` painted at `scale` into a w × h bitmap, as BGRA premultiplied bytes.
    std::vector<uint8_t> paint(D2DBackend& backend, const DisplayList& list, int w, int h, float scale) {
        winrt::com_ptr<ID2D1DeviceContext> ctx;
        winrt::check_hresult(d2d->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, ctx.put()));
        auto format = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
        winrt::com_ptr<ID2D1Bitmap1> target, readback;
        winrt::check_hresult(ctx->CreateBitmap(D2D1::SizeU(w, h), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET, format), target.put()));
        winrt::check_hresult(ctx->CreateBitmap(D2D1::SizeU(w, h), nullptr, 0, D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_CPU_READ | D2D1_BITMAP_OPTIONS_CANNOT_DRAW, format), readback.put()));
        ctx->SetTarget(target.get());
        ctx->BeginDraw();
        ctx->Clear(D2D1::ColorF(0, 0));
        ctx->SetTransform(D2D1::Matrix3x2F::Scale(scale, scale));
        backend.paint(list, ctx.get());
        winrt::check_hresult(ctx->EndDraw());
        winrt::check_hresult(readback->CopyFromBitmap(nullptr, target.get(), nullptr));
        D2D1_MAPPED_RECT mapped;
        winrt::check_hresult(readback->Map(D2D1_MAP_OPTIONS_READ, &mapped));
        std::vector<uint8_t> out(size_t(w) * h * 4);
        for (int y = 0; y < h; y++) memcpy(out.data() + size_t(y) * w * 4, mapped.bits + size_t(y) * mapped.pitch, size_t(w) * 4);
        readback->Unmap();
        return out;
    }
};

static bool close(const std::vector<uint8_t>& a, const std::vector<uint8_t>& b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); i++)
        if (std::abs(int(a[i]) - int(b[i])) > 2) return false;
    return true;
}

/// An SVG's paint is rastered once and drawn from the raster where its key recurs: the pixels match
/// painting it directly, after a move too.
static void test_cached_svg(Device& device) {
    D2DBackend backend(device.d2d.get(), device.dwrite.get(), device.wic.get());
    Renderer r(200, 1.5, false, backend);
    r.receive(parse(R"J({"type":"ops","ops":[
      {"op":"el","id":2,"tag":"html","adopt":"html"},{"op":"el","id":3,"tag":"head","adopt":"head"},{"op":"el","id":4,"tag":"body","adopt":"body"},
      {"op":"style","id":4,"name":"margin","value":"0"},
      {"op":"el","id":9,"tag":"div","attrs":[["style","width:40px;height:0"]]},{"op":"ins","parent":4,"id":9,"before":null},
      {"op":"el","id":10,"tag":"div"},{"op":"ins","parent":4,"id":10,"before":null},
      {"op":"el","id":40,"tag":"svg","attrs":[["width","60"],["height","60"],["viewBox","0 0 60 60"]]},{"op":"ins","parent":10,"id":40,"before":null},
      {"op":"el","id":41,"tag":"circle","attrs":[["cx","30"],["cy","30"],["r","20"],["fill","#f00"],["stroke","#00f"],["stroke-width","4"]]},{"op":"ins","parent":40,"id":41,"before":null},
      {"op":"el","id":42,"tag":"path","attrs":[["d","M5 5 L55 10 L30 55 Z"],["fill","rgba(0,128,0,.6)"]]},{"op":"ins","parent":40,"id":42,"before":null}]})J"));
    auto direct = [&](DisplayList list) {
        for (Command& c : list) c.key = 0;
        D2DBackend plain(device.d2d.get(), device.dwrite.get(), device.wic.get());
        return device.paint(plain, list, 300, 210, 1.5f);
    };
    auto pixel = [](const std::vector<uint8_t>& image, int x, int y) { return image.data() + (size_t(y) * 300 + x) * 4; };

    DisplayList first = r.paint();
    auto a = device.paint(backend, first, 300, 210, 1.5f);
    check(backend.cached_rasters() == 1, "the keyed SVG group is rastered");
    check(close(a, direct(first)), "the raster draws what the paint would");
    const uint8_t* red = pixel(a, 21, 45);
    check(red[2] > 240 && red[1] < 15 && red[0] < 15, "the circle is there");

    r.receive(parse(R"({"type":"ops","ops":[{"op":"style","id":9,"name":"height","value":"40px"}]})"));
    DisplayList moved = r.paint();
    auto b = device.paint(backend, moved, 300, 210, 1.5f);
    check(backend.cached_rasters() == 1, "moved, the raster is drawn again where it went");
    check(close(b, direct(moved)), "the same as painting it there");
    red = pixel(b, 21, 105);
    check(red[2] > 240 && red[1] < 15 && red[0] < 15 && pixel(b, 21, 45)[3] == 0, "the circle moved");

    backend.trim();
    check(backend.cached_rasters() == 0, "trim drops the rasters");
}

/// OpenType features reach the layout: Segoe UI's figures are tabular unless proportional ones are asked for.
static void test_font_features(Device& device) {
    D2DBackend backend(device.d2d.get(), device.dwrite.get(), device.wic.get());
    FontMetrics m;
    int tabular = backend.create_font("Segoe UI", 20, 400, false, "", m);
    int proportional = backend.create_font("Segoe UI", 20, 400, false, "pnum=1", m);
    float a = backend.text_width(tabular, "1111"), b = backend.text_width(proportional, "1111");
    check(b > 0 && b < a - 1, "pnum=1 narrows the figures");
}

int main() {
    winrt::init_apartment(winrt::apartment_type::single_threaded);
    Device device;
    test_cached_svg(device);
    test_font_features(device);
    if (failures) {
        std::printf("%d failure(s)\n", failures);
        return 1;
    }
    std::printf("d2d backend: all passed\n");
    return 0;
}
