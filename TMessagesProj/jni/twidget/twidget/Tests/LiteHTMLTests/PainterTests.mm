#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHSanitizer.h"
#import "LHDocumentPainting.h"
#import "LHImages.h"

#include <cmath>
#include <map>
#include <string>
#include <vector>

namespace
{
struct RecordingPainter : lhx::Painter
{
    std::vector<std::string> texts;
    int                      fills = 0;
    int                      borders_drawn = 0;
    int                      clips = 0;

    void text(const char* text, const lhx::TextStyle& style, const litehtml::web_color& color, const litehtml::position& pos) override
    {
        texts.emplace_back(text);
    }
    void solidFill(const litehtml::background_layer& layer, const litehtml::web_color& color) override { fills++; }
    void borders(const litehtml::borders& b, const litehtml::position& box, bool root) override { borders_drawn++; }
    void pushClip(const litehtml::position& box, const litehtml::border_radiuses& radius) override { clips++; }
};
}

@interface PainterTests : XCTestCase
@end

@implementation PainterTests

- (void)testCustomPainterReceivesTheDocument
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<div style=\"background:#ff0000;border:1px solid #000;overflow:hidden\">Hello <b>world</b></div>" css:nil];
    RecordingPainter painter;
    CGSize size = [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertGreaterThan(size.height, 0);
    XCTAssertGreaterThanOrEqual(painter.fills, 1);
    XCTAssertGreaterThanOrEqual(painter.borders_drawn, 1);
    XCTAssertGreaterThanOrEqual(painter.clips, 1);
    std::string joined;
    for(const auto &t : painter.texts)
        joined += t;
    XCTAssertTrue(joined.find("Hello") != std::string::npos);
    XCTAssertTrue(joined.find("world") != std::string::npos);
}

- (void)testRenderListWithoutGPU
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<div style=\"background:#00ff00;padding:8px;border-radius:6px\">Hello world</div><div style=\"background:#0000ff;height:10px\"></div>" css:nil];
    lhx::RenderRecorder recorder;
    recorder.scale = 2;
    std::map<uint64_t, CGRect> stored;
    int glyphs = 0, masks = 0;
    recorder.store = [&](uint64_t key, CGContextRef ctx, CGRect device) {
        XCTAssertEqual(CGBitmapContextGetWidth(ctx), size_t(device.size.width));
        stored[key] = device;
    };
    recorder.glyph = [&](CTFontRef font, CGGlyph glyph, CGPoint device, lhx::AtlasSlot &slot) {
        lhx::AlphaBitmap bitmap;
        if(!lhx::rasterizeGlyph(font, glyph, device, 2, 512, bitmap))
            return false;
        glyphs++;
        slot.empty  = bitmap.empty;
        slot.u      = 0;
        slot.v      = 0;
        slot.device = CGRectMake(std::floor(device.x) + bitmap.x, std::floor(device.y) + bitmap.y, bitmap.width, bitmap.height);
        return true;
    };
    recorder.mask = [&](uint64_t key, CGRect box, const std::function<void(CGContextRef)> &draw, lhx::AtlasSlot &slot) {
        lhx::AlphaBitmap bitmap;
        if(!lhx::rasterizeMask(box, 2, 512, draw, bitmap))
            return false;
        masks++;
        slot.empty  = false;
        slot.u      = 0;
        slot.v      = 0;
        slot.device = CGRectMake(0, 0, bitmap.width, bitmap.height);
        return true;
    };
    CGSize size = [document recordWithMaxWidth:200 recorder:recorder];
    XCTAssertGreaterThan(size.height, 10);
    XCTAssertFalse(recorder.ops.empty());
    int fills = 0, maskOps = 0;
    for(const auto &op : recorder.ops)
    {
        if(op.kind == lhx::RenderOp::Fill)
            fills++;
        if(op.kind == lhx::RenderOp::Mask)
            maskOps++;
        if(op.kind == lhx::RenderOp::Bitmap)
            XCTAssertTrue(stored.count(op.key));
    }
    XCTAssertGreaterThanOrEqual(fills, 1);
    XCTAssertGreaterThan(glyphs + masks, 0);
    XCTAssertGreaterThan(maskOps, 0);
    XCTAssertEqual(recorder.space, nullptr);
}

@end

@interface SanitizerTests : XCTestCase
@end

@implementation SanitizerTests

- (void)tearDown
{
    [LHSanitizer setLinkSchemes:@[@"http", @"https", @"mailto"] imageSchemes:@[]];
}

- (void)testDefaultPolicyDropsScriptsHandlersAndRemoteImages
{
    [LHSanitizer setLinkSchemes:@[@"http", @"https", @"mailto"] imageSchemes:@[]];
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<div onclick=\"x()\"><script>alert(1)</script><iframe src=\"https://a\"></iframe><img src=\"https://evil/p.gif\"><a href=\"javascript:alert(1)\">x</a></div>" css:nil];
    [document layoutWithMaxWidth:300];
    NSDictionary<NSString *, NSNumber *> *counts = document.sanitizerCounts;
    XCTAssertEqual(counts[@"attr-dropped"].intValue, 1);
    XCTAssertEqual(counts[@"tag-dropped"].intValue, 1);
    XCTAssertEqual(counts[@"url-blocked"].intValue, 2);
}

- (void)testCanvasSourcesAreRendererOnly
{
    [LHSanitizer setLinkSchemes:@[@"http", @"https", @"mailto"] imageSchemes:@[]];
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<canvas src=\"twcanvas:a-1-2\"></canvas><canvas src=\"https://evil/p.gif\"></canvas><canvas src=\"data:image/png;base64,AAAA\"></canvas>"
                                                             "<img src=\"twcanvas:a-1-2\"><div style=\"background-image:url(twcanvas:a-1-2)\">x</div><svg><image href=\"twcanvas:a-1-2\"/></svg>" css:nil];
    [document layoutWithMaxWidth:300];
    XCTAssertEqual(document.sanitizerCounts[@"url-blocked"].intValue, 5, @"%@", document.sanitizerDetails);
    for(NSString *detail in document.sanitizerDetails)
        XCTAssertFalse([detail hasPrefix:@"canvas[src]=twcanvas:"], @"%@", detail);
}

- (void)testHostSchemesAreConfigurable
{
    [LHSanitizer setLinkSchemes:@[@"https", @"myapp"] imageSchemes:@[@"asset"]];
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<a href=\"myapp:open\">a</a><img src=\"asset:logo\"><a href=\"http://x\">b</a>" css:nil];
    [document layoutWithMaxWidth:300];
    NSDictionary<NSString *, NSNumber *> *counts = document.sanitizerCounts;
    XCTAssertEqual(counts[@"url-blocked"].intValue, 1);
}

@end

@interface ImageProviderTests : XCTestCase
@end

@implementation ImageProviderTests

- (void)tearDown
{
    [LHImages setProvider:nil];
    [LHSanitizer setLinkSchemes:@[@"http", @"https", @"mailto"] imageSchemes:@[]];
}

- (void)testProviderSuppliesImagesForAllowedSchemes
{
    [LHSanitizer setLinkSchemes:@[@"https"] imageSchemes:@[@"asset"]];
    __block NSInteger asked = 0;
    [LHImages setProvider:^NSImage *(NSString *url) {
        asked++;
        if(![url isEqualToString:@"asset:block"])
            return nil;
        NSImage *image = [[NSImage alloc] initWithSize:NSMakeSize(50, 120)];
        [image lockFocus];
        [[NSColor redColor] setFill];
        NSRectFill(NSMakeRect(0, 0, 50, 120));
        [image unlockFocus];
        return image;
    }];
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<div style=\"line-height:0\"><img src=\"asset:block\"></div>" css:nil];
    RecordingPainter painter;
    CGSize size = [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertGreaterThanOrEqual(size.height, 120);
    XCTAssertGreaterThan(asked, 0);
    [LHImages forgetImageWithURL:@"asset:block"];

    LHDocument *blocked = [[LHDocument alloc] initWithHTML:@"<div style=\"line-height:0\"><img src=\"other:block\"></div>" css:nil];
    CGSize blockedSize = [blocked paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertLessThan(blockedSize.height, 120);
}

@end

@interface DeclarationTests : XCTestCase
@end

@implementation DeclarationTests

- (void)testRawAnimationDeclarationsSurviveTheCascade
{
    NSString *html = @"<style>:root{--d:300ms} .a{animation:pulse 1.2s ease-in-out infinite;transition:transform var(--d) ease;transform:scale(1)} .a.b{transform:scale(1.5)}</style><div class=\"a b\" data-lh-id=\"7\" style=\"opacity:.5\">x</div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:300];
    NSDictionary *found = [document declarations:@[@"animation", @"transition", @"transform", @"opacity", @"color"] byAttribute:@"data-lh-id"][@"7"];
    XCTAssertEqualObjects(found[@"animation"], @"pulse 1.2s ease-in-out infinite");
    XCTAssertEqualObjects(found[@"transition"], @"transform 300ms ease");
    XCTAssertEqualObjects(found[@"transform"], @"scale(1.5)");
    XCTAssertEqualObjects(found[@"opacity"], @".5");
    XCTAssertNil(found[@"color"]);
}

- (void)testAnimationLayerIsHiddenFromRawDeclarations
{
    for(NSString *style in @[@"opacity:.5;-lh-anim:1;opacity:.9;transform:scale(2)", @"opacity: .5; -lh-anim: 1; opacity: .9; transform: scale(2)"])
    {
        NSString *html = [NSString stringWithFormat:@"<style>.a{transform:scale(1)}</style><div class=\"a\" data-lh-id=\"3\" style=\"%@\">x</div>", style];
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        [document layoutWithMaxWidth:300];
        NSDictionary *found = [document declarations:@[@"opacity", @"transform"] byAttribute:@"data-lh-id"][@"3"];
        XCTAssertEqualObjects(found[@"opacity"], @".5");
        XCTAssertEqualObjects(found[@"transform"], @"scale(1)");
    }
}

@end
