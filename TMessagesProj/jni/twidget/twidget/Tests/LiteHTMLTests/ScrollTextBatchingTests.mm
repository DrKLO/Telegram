#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include "../../Sources/LiteHTML/LHContainer.h"
#include "../../Sources/LiteHTML/litehtml/include/litehtml/render_item.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <string>

namespace
{
struct ScrollGlobalTextBatchMode
{
    bool previous = LHContainer::s_text_batching.exchange(true, std::memory_order_relaxed);

    ~ScrollGlobalTextBatchMode()
    {
        LHContainer::s_text_batching.store(previous, std::memory_order_relaxed);
    }
};

struct ScrollTextBatchMode
{
    bool hadValue;
    std::string value;

    explicit ScrollTextBatchMode(bool enabled)
    {
        const char* previous = getenv("LH_NO_SCROLL_TEXT_BATCH");
        hadValue = previous != nullptr;
        if(previous)
            value = previous;
        if(enabled)
            unsetenv("LH_NO_SCROLL_TEXT_BATCH");
        else
            setenv("LH_NO_SCROLL_TEXT_BATCH", "1", 1);
    }

    ~ScrollTextBatchMode()
    {
        if(hadValue)
            setenv("LH_NO_SCROLL_TEXT_BATCH", value.c_str(), 1);
        else
            unsetenv("LH_NO_SCROLL_TEXT_BATCH");
    }
};

static LHDocument *ScrollTextDocument(NSString *content, CGFloat phase)
{
    NSString *html = [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:14px -apple-system;color:#247}.window{width:225.25px;height:73.5px;overflow:hidden;border-radius:9px}.track{width:900px;height:400px}.pill{display:inline-block;width:100px;padding:5px 10px;box-sizing:border-box;background:linear-gradient(90deg,#163c42,#263858);color:#92e5c4}</style><div style=\"padding:%.3fpx\"><div id=scroll class=window><div class=track style=\"transform:translate(%.3fpx,%.3fpx)\">%@</div></div></div>", phase, -17.375 + phase, .25 - phase, content];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:nil viewportSize:CGSizeMake(600, 420)];
    [document layoutWithMaxWidth:600 preferredWidth:600];
    return document;
}

static NSData *ScrollTextPixels(LHDocument *document, NSString *value, CGRect rect, CGFloat scale, bool enabled)
{
    ScrollTextBatchMode mode(enabled);
    CGImageRef image = [document copyScrollContentWithAttribute:@"id" value:value rect:rect scale:scale];
    if(!image)
        return nil;
    NSData *bytes = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return bytes;
}

static NSString *ScrollTextDifference(NSData *actual, NSData *expected)
{
    if(actual.length != expected.length)
        return [NSString stringWithFormat:@"length=%lu expected=%lu", actual.length, expected.length];
    const auto* a = static_cast<const uint8_t*>(actual.bytes);
    const auto* b = static_cast<const uint8_t*>(expected.bytes);
    size_t changed = 0, first = actual.length;
    int maximum = 0;
    for(size_t i = 0; i < actual.length; ++i)
    {
        if(a[i] == b[i])
            continue;
        if(first == actual.length)
            first = i;
        ++changed;
        maximum = std::max(maximum, std::abs(int(a[i]) - int(b[i])));
    }
    return [NSString stringWithFormat:@"changed=%zu first=%zu max=%d", changed, first, maximum];
}

static bool ScrollTextHasInk(NSData *pixels)
{
    const auto* bytes = static_cast<const uint8_t*>(pixels.bytes);
    for(size_t i = 3; i < pixels.length; i += 4)
        if(bytes[i])
            return true;
    return false;
}

static double ScrollTextCPUTime()
{
    struct timespec time;
    clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &time);
    return double(time.tv_sec) + double(time.tv_nsec) * 1e-9;
}

static NSString *ScrollTextWorkloadMarkup(int phase, bool dense)
{
    if(dense)
    {
        NSMutableString *html = [NSMutableString stringWithString:@"<!DOCTYPE html><style>body{margin:0;font:13px Menlo,monospace;color:#385878}.window{width:300px;height:256px;overflow:hidden}.track{width:850px;height:640px}.row{margin:0;line-height:20px}</style>"];
        for(int i = 0; i < 12; ++i)
        {
            [html appendFormat:@"<div class=window id=w%d><div class=track style=\"transform:translate(%.3fpx,%.3fpx)\">", i, -.375 * phase, .125 * phase];
            for(int row = 0; row < 12; ++row)
                [html appendFormat:@"<div class=row>Frame%03d ready layout stable text paints preserve exact glyph positions during scrolling across tile boundaries today</div>", i * 12 + row];
            [html appendString:@"</div></div>"];
        }
        return html;
    }
    NSMutableString *html = [NSMutableString stringWithString:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system}.window{width:140.25px;height:32px;overflow:hidden;border-radius:5px}.track{display:flex;width:300px;height:32px;align-items:center}.pill{width:100px;flex-shrink:0;box-sizing:border-box;padding:5px 10px;color:#92e5c4;background:linear-gradient(90deg,#163c42,#263858)}.pill:nth-child(2){color:#ffd08e;background:linear-gradient(90deg,#634c2b,#3d3357)}.pill:nth-child(3){color:#8bc5ff;background:linear-gradient(90deg,#213d67,#193e47)}</style>"];
    for(int i = 0; i < 120; ++i)
        [html appendFormat:@"<div class=window id=w%d><div class=track style=\"transform:translateX(%.3fpx)\"><span class=pill>Active %d</span><span class=pill>Queue %d</span><span class=pill>Ready %d</span></div></div>", i, -95.0 - 90.0 * std::sin(phase * .13 + i * .31), i, i % 9, i % 7];
    return html;
}

static LHDocument *ScrollTextWorkload(int phase, bool dense)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:ScrollTextWorkloadMarkup(phase, dense) css:nil previous:nil viewportSize:CGSizeMake(480, 400)];
    [document layoutWithMaxWidth:480 preferredWidth:480];
    return document;
}

static std::pair<size_t, size_t> ScrollTextOwnerCounts(bool dense)
{
    LHContainer container;
    container.set_viewport(litehtml::position(0, 0, 480, 400));
    auto document = litehtml::document::createUpdatableFromString(ScrollTextWorkloadMarkup(0, dense).UTF8String, &container, litehtml::master_css);
    document->render(480);
    auto owner = document->root()->select_one("#w0");
    std::vector<std::shared_ptr<litehtml::render_item>> pending{document->root_render()};
    std::shared_ptr<litehtml::render_item> found;
    while(!pending.empty())
    {
        auto item = pending.back();
        pending.pop_back();
        if(item->src_el() == owner)
        {
            found = item;
            break;
        }
        for(const auto& child : item->children())
            pending.push_back(child);
    }
    if(!found)
        return {};
    std::pair<size_t, size_t> counts{};
    pending = {found};
    while(!pending.empty())
    {
        auto item = pending.back();
        pending.pop_back();
        ++counts.first;
        if(item->src_el()->is_text() && !item->src_el()->is_white_space())
            ++counts.second;
        for(const auto& child : item->children())
            pending.push_back(child);
    }
    return counts;
}

static CGRect ScrollTextWorkloadRect(int step, bool dense)
{
    if(dense)
        return CGRectMake((step % 2) * 512 - .5, ((step / 2) % 2) * 256 - .5, 513, 257);
    return CGRectMake(-.5, -.5, 301, 33);
}
}

@interface ScrollTextBatchingTests : XCTestCase
@end

@implementation ScrollTextBatchingTests

- (void)testScrollTextPreservesPixelsAcrossTilePhasesAndPaintBarriers
{
    ScrollGlobalTextBatchMode batchMode;
    NSArray<NSString *> *contents = @[
        @"<span class=pill>Active 83</span><span class=pill style='color:#ffd08e'>Queue 7</span><span class=pill>Ready 4</span><p>Repeated words wrap over several lines near the tile boundary</p>",
        @"<p style='color:rgba(20,130,190,.35);letter-spacing:-5px'>Transparent overlapping text words <span style='margin-left:-25px'>same colour overlaps</span><span style='color:rgba(240,40,80,.6);margin-left:-18px'>different colour</span></p>",
        @"<div style='opacity:.37;transform:translate(.375px,-.125px)'><span>First group</span><span style='opacity:.5;transform:rotate(2deg);display:inline-block'>Nested transformed words</span><span>Last group</span></div>",
        @"<div style='width:160px;height:40px;overflow:hidden;border-radius:13px;transform:translate(.25px,.75px)'><p>Clipped text wraps close to every rounded corner</p></div><div style='clip-path:inset(2px round 7px);width:140px;height:35px'>Another clipping boundary with words</div>",
        @"<span>Text before</span><span style='background:rgba(255,80,10,.6);border:1px solid #f00'>Background between words</span><span>Text after</span><b style='display:inline-block;background:linear-gradient(90deg,#0ff8,#f088);transform:translateX(-8px)'>Interleaved group</b>",
        @"<p style='font-style:italic;letter-spacing:-2px'>Italic overhang overlaps words</p><p style='text-decoration:underline overline line-through'>Decorated fallback words</p><p style='font-family:serif'>Café Москва 日本語 😃</p>",
        @"<p style='position:relative;z-index:2;color:#f80'>Foreground words</p><p style='position:relative;z-index:-1;margin-top:-20px;color:#18f'>Negative layer words</p><div style='filter:blur(.5px);opacity:.8'>Filtered fallback words</div>",
        @"<div style='position:relative;width:900px;height:400px'><p style='position:absolute;left:20px;top:245.375px;margin:0;white-space:nowrap'>Words cross a vertical tile boundary</p><p style='position:absolute;left:490.125px;top:20px;margin:0;white-space:nowrap'>Words cross the horizontal tile boundary</p></div>"
    ];
    for(NSUInteger index = 0; index < contents.count; ++index)
        for(CGFloat phase : {0.0, .125, .75})
            for(CGFloat scale : {1.0, 1.5, 2.0})
            {
                @autoreleasepool
                {
                    LHDocument *document = ScrollTextDocument(contents[index], phase);
                    for(CGRect rect : {CGRectMake(-1 / scale, -1 / scale, 300 + 2 / scale, 96 + 2 / scale),
                                        CGRectMake(512 - 1 / scale, -1 / scale, 230 + 2 / scale, 96 + 2 / scale),
                                        CGRectMake(-1 / scale, 256 - 1 / scale, 300 + 2 / scale, 96 + 2 / scale)})
                    {
                        NSData *plain = ScrollTextPixels(document, @"scroll", rect, scale, false);
                        NSData *batched = ScrollTextPixels(document, @"scroll", rect, scale, true);
                        XCTAssertNotNil(plain);
                        XCTAssertNotNil(batched);
                        const bool populated = index == 7 ? rect.origin.x > 0 || rect.origin.y > 0 : rect.origin.x < 0 && rect.origin.y < 0;
                        if(populated)
                        {
                            XCTAssertTrue(ScrollTextHasInk(plain), @"case=%lu phase=%g scale=%g rect=%@", index, phase, scale, NSStringFromRect(rect));
                            XCTAssertTrue(ScrollTextHasInk(batched), @"case=%lu phase=%g scale=%g rect=%@", index, phase, scale, NSStringFromRect(rect));
                        }
                        XCTAssertTrue([plain isEqualToData:batched], @"case=%lu phase=%g scale=%g rect=%@ %@", index, phase, scale, NSStringFromRect(rect), ScrollTextDifference(batched, plain));
                    }
                }
            }
}

- (void)testShadowedScrollTextUsesExactFallbackAndRestoresBatching
{
    ScrollGlobalTextBatchMode batchMode;
    for(NSString *shadow in @[@"0 0 14px #0ff", @"1px 1px 0 #f80,0 0 40px #f0f"])
    {
        LHDocument *document = ScrollTextDocument([NSString stringWithFormat:@"<p style='text-shadow:%@'>Shadow words with overlapping glow</p>", shadow], .375);
        const CGRect rect = CGRectMake(-.5, -.5, 301, 97);
        const long before = [LHDocument shadowLineBatches];
        NSData *plain = ScrollTextPixels(document, @"scroll", rect, 2, false);
        NSData *batched = ScrollTextPixels(document, @"scroll", rect, 2, true);
        XCTAssertEqual([LHDocument shadowLineBatches], before);
        XCTAssertNotNil(plain);
        XCTAssertTrue(ScrollTextHasInk(plain));
        XCTAssertEqualObjects(plain, batched);
        LHDocument *next = ScrollTextDocument(@"<p>Ordinary words after shadow fallback</p>", .375);
        XCTAssertEqualObjects(ScrollTextPixels(next, @"scroll", rect, 2, false), ScrollTextPixels(next, @"scroll", rect, 2, true));
    }
}

- (void)testOptInMatchedScrollTextRasterWorkload
{
    XCTSkipIf(!getenv("LH_SCROLL_TEXT_BATCH_WORKLOAD"), @"Run separately while other renderer workloads are idle");
    ScrollGlobalTextBatchMode batchMode;
    const char* requestedOrder = getenv("LH_SCROLL_TEXT_BATCH_ORDER");
    const std::string order = requestedOrder ? requestedOrder : "ABBA";
    const bool validOrder = !order.empty() && order.find_first_not_of("AB") == std::string::npos && order.find('A') != std::string::npos && order.find('B') != std::string::npos;
    XCTAssertTrue(validOrder);
    if(!validOrder)
        return;
    const char* requestedKind = getenv("LH_SCROLL_TEXT_BATCH_KIND");
    const std::string kind = requestedKind ? requestedKind : "all";
    const bool validKind = kind == "all" || kind == "ticker" || kind == "dense";
    XCTAssertTrue(validKind);
    if(!validKind)
        return;
    for(bool dense : {false, true})
    {
        const char* name = dense ? "dense" : "ticker";
        if(kind != "all" && kind != name)
            continue;
        const auto counts = ScrollTextOwnerCounts(dense);
        XCTAssertGreaterThan(counts.first, 0u);
        XCTAssertLessThanOrEqual(counts.first, 512u);
        XCTAssertEqual(counts.second, dense ? 192u : 6u);
        const int ownerCount = dense ? 12 : 120;
        fprintf(stderr, "SCROLL_TEXT_FIXTURE kind=%s nodes_per_owner=%zu words_per_owner=%zu owners=%d\n", name, counts.first, counts.second, ownerCount);
        NSMutableArray<LHDocument *> *documents = [NSMutableArray array];
        NSMutableArray<NSString *> *ids = [NSMutableArray array];
        for(int phase = 0; phase < 4; ++phase)
            [documents addObject:ScrollTextWorkload(phase, dense)];
        for(int i = 0; i < ownerCount; ++i)
            [ids addObject:[NSString stringWithFormat:@"w%d", i]];
        for(size_t round = 0; round < order.size(); ++round)
        {
            const bool enabled = order[round] == 'B';
            ScrollTextBatchMode mode(enabled);
            double started = 0;
            for(int step = -4; step < 20; ++step)
            {
                if(step == 0)
                    started = ScrollTextCPUTime();
                @autoreleasepool
                {
                    LHDocument *document = documents[(step + 4) % 4];
                    const CGRect rect = ScrollTextWorkloadRect(step + 4, dense);
                    for(NSString *value in ids)
                    {
                        CGImageRef image = [document copyScrollContentWithAttribute:@"id" value:value rect:rect scale:2];
                        XCTAssertTrue(image != nullptr);
                        if(image)
                            CGImageRelease(image);
                    }
                }
            }
            const double elapsed = ScrollTextCPUTime() - started;
            fprintf(stderr, "SCROLL_TEXT_RASTER kind=%s enabled=%d round=%zu order=%s states=20 rasters=%d cpu_ms=%.3f\n", name, enabled, round, order.c_str(), ownerCount * 20, elapsed * 1000);
        }
        for(int step = 0; step < 20; ++step)
            for(NSString *value in ids)
            {
                @autoreleasepool
                {
                    LHDocument *document = documents[step % 4];
                    const CGRect rect = ScrollTextWorkloadRect(step, dense);
                    NSData *plain = ScrollTextPixels(document, value, rect, 2, false);
                    NSData *batched = ScrollTextPixels(document, value, rect, 2, true);
                    XCTAssertNotNil(plain);
                    XCTAssertTrue(ScrollTextHasInk(plain), @"kind=%s step=%d id=%@", name, step, value);
                    XCTAssertTrue([plain isEqualToData:batched], @"kind=%s step=%d id=%@ %@", name, step, value, ScrollTextDifference(batched, plain));
                }
            }
    }
}

@end
