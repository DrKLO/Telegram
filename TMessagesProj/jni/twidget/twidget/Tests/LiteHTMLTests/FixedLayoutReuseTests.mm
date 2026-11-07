#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include "../../Sources/LiteHTML/LHContainer.h"
#include "../../Sources/LiteHTML/litehtml/include/litehtml/render_item.h"
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <string>

namespace
{
struct FixedLayoutMode
{
    bool hadValue;
    std::string value;

    explicit FixedLayoutMode(bool enabled)
    {
        const char* previous = getenv("LH_NO_FIXED_LAYOUT_REUSE");
        hadValue = previous != nullptr;
        if(previous)
            value = previous;
        if(enabled)
            unsetenv("LH_NO_FIXED_LAYOUT_REUSE");
        else
            setenv("LH_NO_FIXED_LAYOUT_REUSE", "1", 1);
    }

    ~FixedLayoutMode()
    {
        if(hadValue)
            setenv("LH_NO_FIXED_LAYOUT_REUSE", value.c_str(), 1);
        else
            unsetenv("LH_NO_FIXED_LAYOUT_REUSE");
    }
};

static LHDocument *FixedDocument(NSString *html, LHDocument *previous = nil, CGSize viewport = CGSizeMake(480, 360))
{
    return [[LHDocument alloc] initWithHTML:html css:nil previous:previous viewportSize:viewport];
}

static std::shared_ptr<litehtml::render_item> FixedRenderItem(const litehtml::document::ptr& doc, const char* selector)
{
    auto element = doc->root()->select_one(selector);
    if(!element)
        return nullptr;
    std::vector<std::shared_ptr<litehtml::render_item>> pending{doc->root_render()};
    while(!pending.empty())
    {
        auto item = std::move(pending.back());
        pending.pop_back();
        if(item->src_el() == element)
            return item;
        for(const auto& child : item->children())
            pending.push_back(child);
    }
    return nullptr;
}

static NSData *FixedImage(LHDocument *document, CGRect rect, CGFloat scale, CGFloat width = 480)
{
    CGImageRef image = [document copyImageWithMaxWidth:width rect:rect scale:scale];
    if(!image)
        return nil;
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static NSString *FixedMarkup(NSString *rootStyle, NSString *children, NSString *ancestorStyle, int step)
{
    return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;color:#edf3ff;background:#101923;font:12px -apple-system}.stage{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.cell{min-width:0;padding:6px;border:1px solid #456}.clip{height:68px;overflow:hidden;border-radius:5px}.track{display:flex;width:300px;height:48px;align-items:center}.track>span{width:100px;flex-shrink:0;box-sizing:border-box;padding:5px;color:#9db;background:linear-gradient(90deg,#163c42,#263858)}</style><div data-lh-id=\"1\" class=stage style=\"width:%.3fpx;%@\"><div class=cell><span data-lh-id=\"2\">%02d.%02d</span><div class=clip data-lh-id=\"3\"><div class=track data-lh-id=\"4\" style=\"%@;transform:translateX(%.3fpx)\">%@</div></div></div><div class=cell><span>Independent wrapping words in the neighboring column</span></div></div>", 445.5 - (step % 5) * 39.125, ancestorStyle, 50 + step, step * 3, rootStyle, -10.125 - step * 7.375, children];
}

static NSString *FixedWorkload(int step)
{
    NSMutableString *html = [NSMutableString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system;background:#101923;color:#edf3ff}.matrix{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}.card{min-width:0;padding:5px;border:1px solid #456}.clip{height:32px;overflow:hidden;border-radius:5px}.track{display:flex;width:300px;height:32px;align-items:center}.track>span{width:100px;flex-shrink:0;box-sizing:border-box;padding:5px 10px;background:linear-gradient(90deg,#163c42,#263858)}</style><div class=matrix data-lh-id=\"1\" style=\"width:%.3fpx\">", 440.0 + 35.0 * std::sin(step * .21)];
    for(int i = 0; i < 120; i++)
    {
        const int reading = step - (step - i % 4 + 4) % 4;
        [html appendFormat:@"<div class=card><span>S%03d</span><span data-lh-id=\"%d\">%02d.%02d</span><div class=clip data-lh-id=\"%d\"><div class=track data-lh-id=\"%d\" style=\"transform:translateX(%.3fpx)\"><span>Active %d</span><span>Queue %d</span><span>Ready %d</span></div></div></div>", i, 100 + i, 50 + (reading + 120) % 40, (reading + 120) % 99, 300 + i, 500 + i, -95 - 90 * std::sin(step * .13 + i * .31), i, i % 9, i % 7];
    }
    [html appendString:@"</div>"];
    return html;
}

static NSString *FixedMutationMarkup(CGFloat width, NSString *childStyle, NSString *text, NSString *extra = @"")
{
    return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;color:#edf3ff;background:#101923;font:12px -apple-system}.clip{height:78px;overflow:hidden;border-radius:5px}.track{display:flex;width:300px;height:64px;align-items:center}.track>div{flex-shrink:0;padding:3px;box-sizing:border-box;background:linear-gradient(90deg,#163c42,#263858)}</style><div data-lh-id=\"1\" style=\"width:%.3fpx\"><div class=clip data-lh-id=\"3\"><div class=track data-lh-id=\"4\"><div data-lh-id=\"5\" style=\"%@\">%@</div><div style=\"width:100px\">Queue</div>%@</div></div></div>", width, childStyle, text, extra];
}

static std::string FixedTreeState(const std::shared_ptr<litehtml::render_item>& root, bool normalizeSkippedWhitespace = false)
{
    std::string state;
    std::vector<litehtml::render_item*> pending{root.get()};
    while(!pending.empty())
    {
        auto* item = pending.back();
        pending.pop_back();
        auto p = item->pos_const();
        state += item->skip() ? "skip," : "placed,";
        if(normalizeSkippedWhitespace && item->skip() && item->src_el()->is_white_space())
            p.x = p.y = litehtml::pixel_t(0);
        for(auto value : {p.x, p.y, p.width, p.height})
            state += std::to_string(value.value()) + ",";
        for(const auto* margin : {&item->get_margins(), &item->get_paddings(), &item->get_borders()})
            for(auto value : {margin->left, margin->right, margin->top, margin->bottom})
                state += std::to_string(value.value()) + ",";
        for(const auto& child : item->children())
            pending.push_back(child.get());
        state += ";";
    }
    return state;
}
}

@interface FixedLayoutReuseTests : XCTestCase
@end

@implementation FixedLayoutReuseTests

- (void)expect:(LHDocument *)actual matches:(LHDocument *)expected label:(NSString *)label
{
    [actual layoutWithMaxWidth:480 preferredWidth:480];
    [expected layoutWithMaxWidth:480 preferredWidth:480];
    XCTAssertEqualObjects(actual.layoutSignature, expected.layoutSignature, @"%@", label);
    XCTAssertEqual(actual.size.width, expected.size.width, @"%@", label);
    XCTAssertEqual(actual.size.height, expected.size.height, @"%@", label);
    XCTAssertEqualObjects([actual visualBoundsByAttribute:@"data-lh-id"], [expected visualBoundsByAttribute:@"data-lh-id"], @"%@", label);
    for(CGFloat scale : {1.0, 2.0})
        for(CGRect rect : {CGRectMake(0, 0, 480, 260), CGRectMake(11.25, 17.5, 163, 79)})
        {
            NSData *pixels = FixedImage(actual, rect, scale);
            XCTAssertNotNil(pixels, @"%@", label);
            XCTAssertEqualObjects(pixels, FixedImage(expected, rect, scale), @"%@ scale=%g", label, scale);
        }
    for(int y = 9; y < 200; y += 23)
        for(int x = 7; x < 480; x += 31)
            XCTAssertEqualObjects([actual attribute:@"data-lh-id" atPoint:CGPointMake(x, y)],
                                  [expected attribute:@"data-lh-id" atPoint:CGPointMake(x, y)], @"%@", label);
}

- (void)testFixedFlexReusesResolvedDimensionsAcrossParentWidths
{
    using namespace litehtml;
    for(bool enabled : {false, true})
    {
        FixedLayoutMode mode(enabled);
        LHContainer container;
        container.set_viewport(position(0, 0, 480, 360));
        const char* html = "<style>body{margin:0}.track{display:flex;width:300px;height:48px;padding:3px;border:1px solid;align-items:center}.track>span{width:50%;box-sizing:border-box;padding:2%}</style><div class=track data-lh-id=\"4\"><span>Several wrapping words</span><span>Queue</span></div>";
        auto doc = document::createUpdatableFromString(html, &container, master_css);
        doc->render(480);
        auto track = FixedRenderItem(doc, "[data-lh-id='4']");
        XCTAssertTrue(track != nullptr);
        if(!track)
            return;
        containing_block_context context;
        context.width = {180_px, containing_block_context::cbc_value_type_absolute};
        context.height = {90_px, containing_block_context::cbc_value_type_absolute};
        context.context_idx = 7;
        track->render(5_px, 7_px, context, nullptr);
        int index = 0;
        for(CGPoint origin : {CGPointMake(-17.375, 11.125), CGPointMake(.125, -9.75), CGPointMake(29.75, 12.375)})
        {
            const uint64_t misses = document::s_layout_misses.load();
            const uint64_t hits = document::s_layout_hits.load();
            auto wider = context.new_width(pixel_t(float(241.25 + index++ * 13.375)));
            auto actual = track->render(pixel_t(float(origin.x)), pixel_t(float(origin.y)), wider, nullptr);
            if(enabled)
            {
                XCTAssertEqual(document::s_layout_misses.load(), misses);
                XCTAssertEqual(document::s_layout_hits.load(), hits + 1);
            } else
                XCTAssertGreaterThan(document::s_layout_misses.load(), misses);
            FixedLayoutMode disabled(false);
            LHContainer referenceContainer;
            referenceContainer.set_viewport(position(0, 0, 480, 360));
            auto reference = document::createUpdatableFromString(html, &referenceContainer, master_css);
            reference->render(480);
            auto referenceTrack = FixedRenderItem(reference, "[data-lh-id='4']");
            XCTAssertTrue(referenceTrack != nullptr);
            if(!referenceTrack)
                return;
            auto expected = referenceTrack->render(pixel_t(float(origin.x)), pixel_t(float(origin.y)), wider, nullptr);
            XCTAssertEqual(actual.natural_width.value(), expected.natural_width.value());
            XCTAssertEqual(actual.min_width.value(), expected.min_width.value());
            XCTAssertEqual(FixedTreeState(track), FixedTreeState(referenceTrack));
        }
    }
}

- (void)testResizingAndMixedPaintUpdatesMatchFreshAndDisabledDocuments
{
    NSArray<NSString *> *styles = @[@"", @"padding:3px;border:2px solid #889;box-sizing:border-box",
                                   @"min-width:260px;max-width:280px;min-height:45px;max-height:47px",
                                   @"flex-direction:column", @"flex-wrap:wrap;gap:3%"];
    NSArray<NSString *> *children = @[@"<span>Active</span><span>Queue</span><span>Ready</span>",
                                      @"<span style=\"width:45%;height:50%;padding:3%;margin:1%\">Percentage child wraps here</span><span style=\"flex:1;min-width:10%\">Flexible child</span>",
                                      @"<span style=\"display:flex;flex-direction:column;gap:5%;height:80%\"><b>Nested</b><i>Text</i></span><span>Queue</span>"];
    for(NSString *style in styles)
    for(NSString *content in children)
    {
        LHDocument *enabledPrevious = nil, *disabledPrevious = nil;
        for(int step = 0; step < 7; step++)
        {
            NSString *html = FixedMarkup(style, content, @"", step);
            LHDocument *enabled;
            {
                FixedLayoutMode mode(true);
                enabled = FixedDocument(html, enabledPrevious);
                [enabled layoutWithMaxWidth:480 preferredWidth:480];
            }
            LHDocument *disabled;
            {
                FixedLayoutMode mode(false);
                disabled = FixedDocument(html, disabledPrevious);
                [disabled layoutWithMaxWidth:480 preferredWidth:480];
            }
            NSString *label = [NSString stringWithFormat:@"%@ %@ step=%d", style, content, step];
            [self expect:enabled matches:disabled label:label];
            [self expect:enabled matches:FixedDocument(html) label:label];
            enabledPrevious = enabled;
            disabledPrevious = disabled;
        }
    }
}

- (void)testDependentMarginsSizingFloatsAndPositioningKeepEquivalentRendering
{
    FixedLayoutMode mode(true);
    NSArray<NSString *> *styles = @[@"width:80%", @"width:auto", @"height:auto", @"height:65%",
        @"padding:3%", @"margin:2%", @"margin-left:auto;margin-right:auto", @"max-width:75%;min-height:25%",
        @"display:inline-flex", @"float:left", @"position:relative;left:10%", @"position:absolute;right:5%",
        @"position:fixed;right:7%", @"width:18em;height:4em", @"width:45vw;height:12vh"];
    NSArray<NSString *> *children = @[@"<span>Active words</span><span>Queue</span>",
        @"<span><b style=\"float:left;width:35%;position:relative;left:15%\">Floated</b>Inline text</span><span>Queue</span>",
        @"<span style=\"position:relative;left:10%;top:5%\">Relative</span><span style=\"position:absolute;right:3%\">Absolute</span>"];
    for(NSString *style in styles)
    for(NSString *content in children)
    {
        LHDocument *previous = nil;
        for(int step = 0; step < 5; step++)
        {
            NSString *ancestor = step < 3 ? @"font-size:12px;height:120px;position:relative" : @"font-size:15px;height:160px;position:relative";
            NSString *html = FixedMarkup(style, content, ancestor, step);
            LHDocument *patched = FixedDocument(html, previous);
            [self expect:patched matches:FixedDocument(html)
                      label:[NSString stringWithFormat:@"%@ %@ step=%d", style, content, step]];
            previous = patched;
        }
    }
}

- (void)testDirtySubtreeAndViewportChangesInvalidateReuse
{
    FixedLayoutMode mode(true);
    LHDocument *previous = nil;
    for(int step = 0; step < 9; step++)
    {
        NSString *style = step < 3 ? @"" : step < 6 ? @"width:inherit" : @"width:45vw;height:10vh";
        NSString *content = step % 2 ? @"<span style=\"width:40%;font-size:16px\">Long replacement text that wraps</span><span>Queue</span>" : @"<span style=\"width:60%;font-size:12px\">Short text</span><span>Ready</span>";
        NSString *html = FixedMarkup(style, content, @"", step);
        for(NSNumber *width in @[@480, @340, @520, @480])
        {
            const CGSize viewport = CGSizeMake(width.doubleValue, step % 2 ? 420 : 360);
            LHDocument *patched = FixedDocument(html, previous, viewport);
            LHDocument *fresh = FixedDocument(html, nil, viewport);
            [patched layoutWithMaxWidth:width.doubleValue preferredWidth:width.doubleValue];
            [fresh layoutWithMaxWidth:width.doubleValue preferredWidth:width.doubleValue];
            XCTAssertEqualObjects(patched.layoutSignature, fresh.layoutSignature);
            for(CGFloat scale : {1.0, 2.0})
            {
                CGRect rect = CGRectMake(0, 0, width.doubleValue, 260);
                NSData *image = FixedImage(patched, rect, scale, width.doubleValue);
                XCTAssertNotNil(image);
                XCTAssertEqualObjects(image, FixedImage(fresh, rect, scale, width.doubleValue), @"step=%d width=%@", step, width);
            }
            previous = patched;
        }
    }
}

- (void)testFixedViewportDescendantMutationsInvalidateAndReuseResumes
{
    using namespace litehtml;
    std::vector<std::string> disabledStates;
    for(bool enabled : {false, true})
    {
        FixedLayoutMode mode(enabled);
        size_t stateIndex = 0;
        NSArray<NSString *> *styles = @[@"width:100px;font-size:12px", @"width:100px;font-size:12px",
                                        @"width:100px;font-size:18px", @"width:140px;font-size:18px"];
        NSArray<NSString *> *texts = @[@"Initial text", @"A longer reading that wraps", @"A longer reading that wraps", @"A longer reading that wraps"];
        LHContainer container;
        container.set_viewport(position(0, 0, 480, 360));
        auto doc = document::createUpdatableFromString(FixedMutationMarkup(340, styles[0], texts[0]).UTF8String, &container, master_css);
        doc->render(480);
        LHDocument *previous = FixedDocument(FixedMutationMarkup(340, styles[0], texts[0]));
        [previous layoutWithMaxWidth:480 preferredWidth:480];
        auto track = FixedRenderItem(doc, "[data-lh-id='4']");
        XCTAssertTrue(track != nullptr);
        if(!track)
            return;
        auto expectCore = [&](NSString *html, NSString *label) {
            FixedLayoutMode disabled(false);
            LHContainer referenceContainer;
            referenceContainer.set_viewport(position(0, 0, 480, 360));
            auto reference = document::createUpdatableFromString(html.UTF8String, &referenceContainer, master_css);
            reference->render(480);
            auto expected = FixedRenderItem(reference, "[data-lh-id='4']");
            XCTAssertTrue(expected != nullptr);
            if(!expected)
                return;
            const std::string rawState = FixedTreeState(track);
            if(enabled)
            {
                XCTAssertLessThan(stateIndex, disabledStates.size());
                if(stateIndex < disabledStates.size())
                    XCTAssertTrue(rawState == disabledStates[stateIndex], @"%@ incremental actual=%s disabled=%s", label, rawState.c_str(), disabledStates[stateIndex].c_str());
            } else
                disabledStates.push_back(rawState);
            stateIndex++;
            const std::string actualState = FixedTreeState(track, true);
            const std::string expectedState = FixedTreeState(expected, true);
            XCTAssertTrue(actualState == expectedState, @"%@ enabled=%d actual=%s expected=%s", label, enabled, actualState.c_str(), expectedState.c_str());
        };
        CGFloat parentWidth = 340;
        for(NSUInteger mutation = 0; mutation < styles.count; mutation++)
        {
            NSString *html = FixedMutationMarkup(parentWidth, styles[mutation], texts[mutation]);
            if(mutation > 0)
            {
                bool changed = false;
                XCTAssertTrue(doc->update_from_string(html.UTF8String, &changed));
                XCTAssertTrue(changed);
                track = FixedRenderItem(doc, "[data-lh-id='4']");
                XCTAssertTrue(track != nullptr);
                if(!track)
                    return;
                XCTAssertTrue(track->layout_dirty(), @"mutation=%lu", mutation);
                const uint64_t misses = document::s_layout_misses.load();
                doc->render(480);
                XCTAssertGreaterThan(document::s_layout_misses.load(), misses, @"mutation=%lu", mutation);
            }
            expectCore(html, [NSString stringWithFormat:@"mutation=%lu", mutation]);
            LHDocument *patched = FixedDocument(html, previous);
            [self expect:patched matches:FixedDocument(html) label:[NSString stringWithFormat:@"descendant mutation=%lu", mutation]];
            previous = patched;
            for(CGFloat width : {219.125, 376.75, 284.375})
            {
                parentWidth = width;
                html = FixedMutationMarkup(parentWidth, styles[mutation], texts[mutation]);
                bool changed = false;
                XCTAssertTrue(doc->update_from_string(html.UTF8String, &changed));
                XCTAssertTrue(changed);
                XCTAssertTrue(track == FixedRenderItem(doc, "[data-lh-id='4']"), @"mutation=%lu width=%g", mutation, width);
                XCTAssertFalse(track->layout_dirty(), @"mutation=%lu width=%g", mutation, width);
                const uint64_t hits = document::s_layout_hits.load();
                doc->render(480);
                if(enabled)
                    XCTAssertEqual(document::s_layout_hits.load(), hits + 1, @"mutation=%lu width=%g", mutation, width);
                expectCore(html, [NSString stringWithFormat:@"reuse mutation=%lu width=%g", mutation, width]);
                patched = FixedDocument(html, previous);
                [self expect:patched matches:FixedDocument(html) label:[NSString stringWithFormat:@"reuse mutation=%lu width=%g", mutation, width]];
                previous = patched;
            }
        }
        for(NSString *extra in @[@"<span style=\"position:absolute;left:7px;top:9px;background:#f80\">Positioned child</span>", @""])
        {
            NSString *html = FixedMutationMarkup(parentWidth, styles.lastObject, texts.lastObject, extra);
            LHDocument *patched = FixedDocument(html, previous);
            [self expect:patched matches:FixedDocument(html) label:[NSString stringWithFormat:@"structure %@", extra]];
            previous = patched;
        }
    }
}

- (void)testOptInMatchedFixedLayoutWorkload
{
    XCTSkipIf(!getenv("LH_FIXED_LAYOUT_WORKLOAD"), @"Run separately while other renderer workloads are idle");
    const char* requestedOrder = getenv("LH_FIXED_LAYOUT_WORKLOAD_ORDER");
    const std::string order = requestedOrder ? requestedOrder : "ABBA";
    const bool validOrder = !order.empty() && order.find_first_not_of("AB") == std::string::npos &&
                            order.find('A') != std::string::npos && order.find('B') != std::string::npos;
    XCTAssertTrue(validOrder, @"Use A for disabled and B for enabled, including both modes");
    if(!validOrder)
        return;
    NSMutableArray<NSString *> *states = [NSMutableArray array];
    for(int step = 0; step < 72; step++)
        [states addObject:FixedWorkload(step)];
    uint64_t misses[2] = {}, hits[2] = {};
    uint64_t runs[2] = {};
    for(size_t round = 0; round < order.size(); round++)
    {
        const int enabled = order[round] == 'B';
        FixedLayoutMode mode(enabled);
        LHDocument *previous = nil;
        for(int step = 0; step < 12; step++)
        {
            previous = FixedDocument(states[step], previous);
            [previous layoutWithMaxWidth:480 preferredWidth:480];
        }
        const uint64_t beforeMisses = litehtml::document::s_layout_misses.load();
        const uint64_t beforeHits = litehtml::document::s_layout_hits.load();
        const std::clock_t cpu = std::clock();
        const auto started = std::chrono::steady_clock::now();
        for(int step = 12; step < 72; step++)
        {
            previous = FixedDocument(states[step], previous);
            [previous layoutWithMaxWidth:480 preferredWidth:480];
        }
        const double elapsed = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count();
        const double cpuMS = 1000.0 * double(std::clock() - cpu) / CLOCKS_PER_SEC;
        const uint64_t runMisses = litehtml::document::s_layout_misses.load() - beforeMisses;
        const uint64_t runHits = litehtml::document::s_layout_hits.load() - beforeHits;
        misses[enabled] += runMisses;
        hits[enabled] += runHits;
        runs[enabled]++;
        printf("FIXED_LAYOUT_WORKLOAD enabled=%d states=60 wall_ms=%.3f cpu_ms=%.3f hits=%llu misses=%llu round=%zu order=%s warmup=12\n", enabled, elapsed, cpuMS, (unsigned long long)runHits, (unsigned long long)runMisses, round + 1, order.c_str());
    }
    XCTAssertLessThan(misses[1] * runs[0], misses[0] * runs[1]);
    XCTAssertGreaterThan(hits[1], uint64_t(120 * 60) * runs[1]);
    LHDocument *enabledPrevious = nil, *disabledPrevious = nil;
    for(int step = 0; step < 72; step++)
    {
        @autoreleasepool
        {
            {
                FixedLayoutMode mode(false);
                disabledPrevious = FixedDocument(states[step], disabledPrevious);
                [disabledPrevious layoutWithMaxWidth:480 preferredWidth:480];
            }
            {
                FixedLayoutMode mode(true);
                enabledPrevious = FixedDocument(states[step], enabledPrevious);
                [enabledPrevious layoutWithMaxWidth:480 preferredWidth:480];
            }
            if(step >= 12)
            {
                XCTAssertEqual(enabledPrevious.size.width, disabledPrevious.size.width, @"step=%d", step);
                XCTAssertEqual(enabledPrevious.size.height, disabledPrevious.size.height, @"step=%d", step);
                XCTAssertEqualObjects(enabledPrevious.layoutSignature, disabledPrevious.layoutSignature, @"step=%d", step);
                XCTAssertEqualObjects([enabledPrevious visualBoundsByAttribute:@"data-lh-id"],
                                      [disabledPrevious visualBoundsByAttribute:@"data-lh-id"], @"step=%d", step);
                const CGRect rect = CGRectMake(0, 0, 480, std::ceil(enabledPrevious.size.height));
                NSData *actual, *expected;
                {
                    FixedLayoutMode mode(false);
                    expected = FixedImage(disabledPrevious, rect, 2);
                }
                {
                    FixedLayoutMode mode(true);
                    actual = FixedImage(enabledPrevious, rect, 2);
                }
                XCTAssertNotNil(actual, @"step=%d", step);
                XCTAssertNotNil(expected, @"step=%d", step);
                XCTAssertEqualObjects(actual, expected, @"step=%d", step);
            }
        }
    }
    printf("FIXED_LAYOUT_PARITY states=60 scale=2\n");
}

@end
