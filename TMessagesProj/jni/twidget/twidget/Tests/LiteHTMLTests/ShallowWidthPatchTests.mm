#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include "../../Sources/LiteHTML/LHContainer.h"
#include <cstdlib>
#include <string>

namespace
{
struct WidthPatchMode
{
    bool hadValue;
    std::string value;
    const char* name;

    explicit WidthPatchMode(bool enabled, const char* variable = "LH_NO_SHALLOW_WIDTH_PATCH") : name(variable)
    {
        const char* previous = getenv(name);
        hadValue = previous != nullptr;
        if(previous)
            value = previous;
        if(enabled)
            unsetenv(name);
        else
            setenv(name, "1", 1);
    }

    ~WidthPatchMode()
    {
        if(hadValue)
            setenv(name, value.c_str(), 1);
        else
            unsetenv(name);
    }
};

static LHDocument *WidthPage(NSString *html)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:nil];
    [document layoutWithMaxWidth:340 preferredWidth:340];
    return document;
}

static NSData *WidthImage(CGImageRef image)
{
    if(!image)
        return nil;
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static NSString *WidthState(LHDocument *document)
{
    [document layoutWithMaxWidth:340 preferredWidth:340];
    NSMutableString *state = [NSMutableString stringWithString:document.layoutSignature ?: @""];
    for(NSString *key in @[@"1", @"2", @"3", @"4", @"5", @"6", @"7", @"8"])
    {
        NSDictionary *style = [document computedStyleForAttribute:@"data-lh-id" value:key];
        for(NSString *name in [style.allKeys sortedArrayUsingSelector:@selector(compare:)])
            [state appendFormat:@"|%@ %@=%@", key, name, style[name]];
    }
    NSDictionary *bounds = [document visualBoundsByAttribute:@"data-lh-id"];
    for(NSString *key in [bounds.allKeys sortedArrayUsingSelector:@selector(compare:)])
        [state appendFormat:@"|v%@ %@", key, NSStringFromRect([bounds[key] rectValue])];
    NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *declarations = [document declarations:@[@"width", @"transform"] byAttribute:@"data-lh-id"];
    for(NSString *key in [declarations.allKeys sortedArrayUsingSelector:@selector(compare:)])
        for(NSString *name in [declarations[key].allKeys sortedArrayUsingSelector:@selector(compare:)])
            [state appendFormat:@"|d%@ %@=%@", key, name, declarations[key][name]];
    for(int y = 5; y < 360; y += 31)
        for(int x = 7; x < 340; x += 37)
            [state appendFormat:@"|%@", [document attribute:@"data-lh-id" atPoint:CGPointMake(x, y)] ?: @"-"];
    return state;
}

static NSString *WidthMarkup(NSString *display, NSString *width, NSString *nestedWidth, NSString *transform, NSString *reading)
{
    return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;background:#132133;color:#f3f5f8;font:12px -apple-system}.stage{display:%@;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;padding:7px;box-sizing:border-box;position:relative}.cell{min-width:0;flex:1;padding:5px;background:#21344b;border:1px solid #567;border-radius:5px}.clip{height:32px;overflow:hidden;border-radius:4px;background:#101923}.track{display:flex;width:240px;height:32px;align-items:center;background:linear-gradient(90deg,#163c42,#263858)}.track span{width:80px;flex-shrink:0}.nested{min-width:12px;max-width:160px;box-sizing:border-box;padding:3px;border:1px solid #889}.absolute{position:absolute;right:4px;bottom:3px;width:12%%;height:5px;background:#f80}</style><div class=stage data-lh-id=\"1\" style=\"width:%@\"><div class=cell data-lh-id=\"2\"><div class=clip data-lh-id=\"3\"><div class=track data-lh-id=\"4\" style=\"transform:%@\"><span>Active</span><span>Queue</span><span>Ready</span></div></div><span data-lh-id=\"5\">%@</span><div class=nested data-lh-id=\"6\" style=\"width:%@\">Several words that wrap as available width changes.</div></div><div class=cell data-lh-id=\"7\">A second column with wrapping text and a percentage child.<div style=\"width:65%%;height:11px;background:#4a8\"></div></div><div class=absolute data-lh-id=\"8\"></div></div>", display, width, transform, reading, nestedWidth];
}

static NSString *MixedWidthMarkup(int step, NSString *childBackground, bool ancestorBackground)
{
    NSArray<NSString *> *colors = @[@"#26734d", @"#9b5d26", @"#285ba6"];
    NSString *color = colors[step % colors.count];
    NSString *outerColor = ancestorBackground ? color : @"#ddd";
    return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system}.stage{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px}.clip{height:32px;overflow:hidden;border-radius:4px}.track{display:flex;width:240px;height:32px;align-items:center}.track span{width:80px;flex-shrink:0}.unchanged{width:75%%;padding:3px;background:#eee}</style><div style=\"background:#ddd;background-color:%@\"><div class=stage data-lh-id=\"1\" style=\"width:%.2fpx\"><div data-lh-id=\"2\" style=\"height:10px;background:#345;background-color:%@\"><div data-lh-id=\"7\" style=\"height:6px;%@\"></div></div><div class=clip data-lh-id=\"3\"><div class=track data-lh-id=\"4\" style=\"transform:translateX(%.2fpx)\"><span data-lh-id=\"8\">%02d.%02d</span><span>Queue</span></div></div><div class=unchanged data-lh-id=\"5\"><span>Unchanged descendants wrap here</span></div></div></div><div data-lh-id=\"6\" style=\"width:340px;height:12px;background:#345;background-color:%@\"></div>", outerColor, 300.25 - (step % 4) * 23.5, color, childBackground, -10.25 - step * 2.5, 50 + step, step * 3, color];
}
}

@interface ShallowWidthPatchTests : XCTestCase
@end

@implementation ShallowWidthPatchTests

- (void)assertDocument:(LHDocument *)patched matches:(LHDocument *)fresh label:(NSString *)label scroll:(BOOL)scroll
{
    XCTAssertEqualObjects(WidthState(patched), WidthState(fresh), @"state %@", label);
    XCTAssertEqual(patched.size.width, fresh.size.width, @"width %@", label);
    XCTAssertEqual(patched.size.height, fresh.size.height, @"height %@", label);
    for(CGFloat scale : {1.0, 2.0})
    {
        for(CGRect rect : {CGRectMake(0, 0, 340, 360), CGRectMake(13.25, 21.5, 129, 80)})
        {
            NSData *actual = WidthImage([patched copyImageWithMaxWidth:340 rect:rect scale:scale]);
            NSData *expected = WidthImage([fresh copyImageWithMaxWidth:340 rect:rect scale:scale]);
            XCTAssertNotNil(actual, @"%@", label);
            XCTAssertEqualObjects(actual, expected, @"pixels %@ scale=%g rect=%@", label, scale, NSStringFromRect(NSRectFromCGRect(rect)));
        }
        if(scroll)
        {
            NSData *actual = WidthImage([patched copyScrollContentWithAttribute:@"data-lh-id" value:@"3" rect:CGRectMake(0, 0, 260, 40) scale:scale]);
            NSData *expected = WidthImage([fresh copyScrollContentWithAttribute:@"data-lh-id" value:@"3" rect:CGRectMake(0, 0, 260, 40) scale:scale]);
            XCTAssertNotNil(actual, @"scroll %@", label);
            XCTAssertEqualObjects(actual, expected, @"scroll %@ scale=%g", label, scale);
        }
    }
}

- (void)testWidthOnlyComputesTheOwnerAndRemainsLayoutBearing
{
    using namespace litehtml;
    NSString *body = @"<!DOCTYPE html><style>body{margin:0;font:12px sans-serif}.child{width:50%%}.leaf{padding:2px}</style><div data-lh-id=\"1\" style=\"width:%dpx\"><div class=child data-lh-id=\"2\"><div class=leaf>Some wrapping words in a child</div><div class=leaf>More words</div></div></div>";
    for(bool enabled : {true, false})
    {
        WidthPatchMode mode(enabled);
        LHContainer container;
        container.set_viewport(position(0, 0, 340, 360));
        auto doc = document::createUpdatableFromString([NSString stringWithFormat:body, 300].UTF8String, &container, master_css);
        doc->render(340);
        auto child = doc->root()->select_one("[data-lh-id='2']");
        XCTAssertTrue(child != nullptr);
        const int childStyle = child->css().get_shared_id();
        const uint64_t before = doc->style_epoch();
        bool changed = false;
        XCTAssertTrue(doc->update_from_string([NSString stringWithFormat:body, 180].UTF8String, &changed));
        XCTAssertTrue(changed);
        XCTAssertFalse(doc->last_patch_paint_only());
        if(enabled)
        {
            XCTAssertEqual(doc->style_epoch() - before, uint64_t(1));
            XCTAssertEqual(child->css().get_shared_id(), childStyle);
        } else
            XCTAssertGreaterThan(doc->style_epoch() - before, uint64_t(1));
        doc->render(340);
        XCTAssertEqual(child->css().get_width().val(), 50.0f);
    }
}

- (void)testHoleUpdatesKeepDescendantTransformsAndText
{
    using namespace litehtml;
    WidthPatchMode mode(true);
    LHContainer container;
    container.set_viewport(position(0, 0, 340, 360));
    auto doc = document::createUpdatableFromString("<!DOCTYPE html><style>body{margin:0;font:12px sans-serif}.track{width:240px;height:32px}</style><div data-lh-id=\"1\" style=\"width:300px\"><div class=track data-lh-id=\"2\" style=\"transform:translateX(-10px)\"><span data-lh-id=\"3\">50.00</span></div><div>Unchanged descendant</div></div>", &container, master_css);
    doc->render(340);
    auto root = doc->root()->select_one("[data-lh-id='1']");
    auto track = doc->root()->select_one("[data-lh-id='2']");
    auto reading = doc->root()->select_one("[data-lh-id='3']");
    const uint64_t before = doc->style_epoch();
    bool changed = false;
    XCTAssertTrue(doc->update_holes({{root, "style", false, "width:187.5px"},
                                    {track, "style", false, "transform:translateX(-22.5px)"},
                                    {reading, "", true, "79.25"}}, &changed));
    XCTAssertTrue(changed);
    XCTAssertFalse(doc->last_patch_paint_only());
    XCTAssertGreaterThan(doc->style_epoch() - before, uint64_t(1));
    doc->render(340);
    XCTAssertEqual(root->css().get_width().val(), 187.5f);
    XCTAssertEqual(track->css().get_transform().front().x.val(), -22.5f);
    std::string text;
    reading->get_text(text);
    XCTAssertEqual(text, std::string("79.25"));
    size measured;
    reading->children().front()->get_content_size(measured, 340);
    XCTAssertGreaterThan(measured.width.value(), 0.0f);
}

- (void)testWidthTransformAndRetextSequencesMatchFreshDocuments
{
    WidthPatchMode mode(true);
    NSArray<NSString *> *widths = @[@"100%", @"73.125%", @"211.5px", @"95.75%", @"167px", @"88%"];
    for(NSString *display in @[@"grid", @"flex", @"block"])
    {
        LHDocument *previous = WidthPage(WidthMarkup(display, @"100%", @"80%", @"translateX(-12.5px)", @"50.00"));
        for(NSUInteger step = 0; step < widths.count * 2; step++)
        {
            NSString *nested = step % 2 ? @"75%" : @"97.25px";
            NSString *transform = [NSString stringWithFormat:@"translate(%g%%,%gpx)", -5.25 - step * 1.75, step % 3 * .25];
            NSString *html = WidthMarkup(display, widths[step % widths.count], nested, transform, [NSString stringWithFormat:@"%02lu.%02lu", 20 + step, step * 3]);
            LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
            XCTAssertTrue(patched.reusedPrevious, @"%@ step=%lu", display, step);
            XCTAssertFalse(patched.reusedLayout, @"%@ step=%lu", display, step);
            [self assertDocument:patched matches:WidthPage(html) label:[NSString stringWithFormat:@"%@ step=%lu", display, step] scroll:YES];
            previous = patched;
        }
    }
}

- (void)testMixedBackgroundUpdatesAdmitShallowWidthAndKeepInheritedFallback
{
    using namespace litehtml;
    WidthPatchMode mode(true);
    for(NSString *background in @[@"background-color:#fff", @"background-color:inherit", @"background:inherit"])
    {
        LHContainer container;
        container.set_viewport(position(0, 0, 340, 360));
        auto doc = document::createUpdatableFromString(MixedWidthMarkup(0, background, false).UTF8String, &container, master_css);
        doc->render(340);
        auto unchanged = doc->root()->select_one("[data-lh-id='5']");
        auto child = doc->root()->select_one("[data-lh-id='7']");
        auto owner = doc->root()->select_one("[data-lh-id='2']");
        const bool inherits = [background containsString:@"inherit"];
        for(int step = 1; step < 5; step++)
        {
            const int unchangedStyle = unchanged->css().get_shared_id();
            const int childStyle = child->css().get_shared_id();
            bool changed = false;
            XCTAssertTrue(doc->update_from_string(MixedWidthMarkup(step, background, false).UTF8String, &changed));
            XCTAssertTrue(changed);
            XCTAssertFalse(doc->last_patch_paint_only());
            XCTAssertEqual(unchanged->css().get_shared_id(), unchangedStyle, @"%@", background);
            if(inherits)
            {
                XCTAssertNotEqual(child->css().get_shared_id(), childStyle, @"%@", background);
                XCTAssertTrue(child->css().get_bg().m_color == owner->css().get_bg().m_color, @"%@", background);
            } else
                XCTAssertEqual(child->css().get_shared_id(), childStyle, @"%@", background);
            doc->render(340);
        }
    }
}

- (void)testMixedBackgroundWidthTransformAndTextPixelsMatchFreshDocuments
{
    WidthPatchMode mode(true);
    for(bool ancestorBackground : {false, true})
    for(NSString *background in @[@"background-color:#fff", @"background-color:inherit", @"background:inherit"])
    {
        LHDocument *previous = WidthPage(MixedWidthMarkup(0, background, ancestorBackground));
        for(int step = 1; step < 7; step++)
        {
            NSString *html = MixedWidthMarkup(step, background, ancestorBackground);
            LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
            XCTAssertTrue(patched.reusedPrevious);
            XCTAssertFalse(patched.reusedLayout);
            [self assertDocument:patched matches:WidthPage(html)
                          label:[NSString stringWithFormat:@"%@ ancestor=%d step=%d", background, ancestorBackground, step]
                         scroll:YES];
            previous = patched;
        }
    }
}

- (void)testInheritedWidthAndUnsupportedOwnersKeepRecursiveFallback
{
    WidthPatchMode mode(true);
    NSArray<NSString *> *children = @[
        @"<div data-lh-id=\"2\" style=\"width:inherit;background:#345\"><div data-lh-id=\"3\" style=\"width:inherit\">Inherited width</div></div>",
        @"<div data-lh-id=\"2\" style=\"all:inherit\">Inherited properties</div>",
        @"<div data-lh-id=\"2\" style=\"--copy:inherit;width:var(--copy)\">Variable inheritance</div>",
        @"<table data-lh-id=\"2\" style=\"width:100%\"><tr><td>First wrapping cell</td><td>Second cell</td></tr></table>",
        @"<svg data-lh-id=\"2\" width=\"100%\" height=\"30\" viewBox=\"0 0 100 30\"><rect width=\"100\" height=\"30\" fill=\"#f80\"/></svg>",
        @"<div data-lh-id=\"2\" style=\"contain:layout;position:relative;width:60%;padding:5px\"><div style=\"position:absolute;right:0;width:25%;height:10px;background:#f80\"></div>Contained words</div>"];
    for(NSString *child in children)
    {
        NSString *(^markup)(int) = ^NSString *(int width) {
            return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system;color:#eee;background:#123}</style><div data-lh-id=\"1\" style=\"width:%dpx\">%@</div>", width, child];
        };
        LHDocument *previous = WidthPage(markup(300));
        for(int width : {187, 260, 131, 300})
        {
            LHDocument *patched = [[LHDocument alloc] initWithHTML:markup(width) css:nil previous:previous];
            [self assertDocument:patched matches:WidthPage(markup(width)) label:child scroll:NO];
            previous = patched;
        }
    }
}

- (void)testStyleSelectorsAndGeneratedContentRematch
{
    WidthPatchMode mode(true);
    for(NSString *rule in @[
        @".stage[style*='180']{background:#c33;color:#0f0}",
        @".stage[style*='180'] .child{width:25%;background:#f80}",
        @".stage:is([style*='180']) .child{padding:17px;background:#f80}",
        @".stage:not([style*='180']) .child{width:25%;background:#f80}",
        @".stage::before{content:attr(style);display:block;background:#f80}"])
    {
        NSString *(^markup)(int) = ^NSString *(int width) {
            return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system}.stage{background:#345}.child{width:60%%}%@</style><div class=stage data-lh-id=\"1\" style=\"width:%dpx\"><div class=child data-lh-id=\"2\">Several wrapping words</div></div>", rule, width];
        };
        LHDocument *previous = WidthPage(markup(300));
        for(int width : {180, 250, 180, 300})
        {
            LHDocument *patched = [[LHDocument alloc] initWithHTML:markup(width) css:nil previous:previous];
            [self assertDocument:patched matches:WidthPage(markup(width)) label:rule scroll:NO];
            previous = patched;
        }
    }
}

- (void)testMixedAndAmbiguousChangesMatchFreshDocuments
{
    WidthPatchMode mode(true);
    NSArray<NSString *> *styles = @[@"width:180px", @"width:auto", @"", @"width:175px;width:230px", @"width:garbage", @"width:calc(100% - 20px)", @"width:80%;--copy:30%", @"width:var(--copy)", @"WIDTH:215PX", @"width:inherit", @"width:200px!important", @"width:190px;padding:8px", @"width:75%"];
    for(bool structural : {false, true})
    {
        LHDocument *previous = nil;
        for(NSUInteger step = 0; step < styles.count; step++)
        {
            NSString *cls = step % 2 ? @"stage alternate" : @"stage";
            NSString *text = structural && step % 2 ? @"Changed text contains several extra words" : @"short text";
            NSString *html = [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px -apple-system;--copy:60%%}.stage{padding:3px;background:#345;color:#eee}.alternate{font-size:15px}.child{width:75%%}</style><div class=\"%@\" data-lh-id=\"1\" style=\"%@\"><div class=child data-lh-id=\"2\">%@</div></div>", cls, styles[step], text];
            LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
            [self assertDocument:patched matches:WidthPage(html) label:styles[step] scroll:NO];
            previous = patched;
        }
    }
}

- (void)testGuardedStyleChangesLeaveThePreviousDocumentUnmodified
{
    using namespace litehtml;
    WidthPatchMode mode(true);
    for(bool templateEnabled : {true, false})
    for(NSString *rule in @[@"[style*='180']{color:red}", @".stage::before{content:attr(style)}"])
    {
        WidthPatchMode templateMode(templateEnabled, "LH_NO_TEMPLATE");
        NSString *(^markup)(int) = ^NSString *(int width) {
            return [NSString stringWithFormat:@"<!DOCTYPE html><style>%@</style><div class=stage data-lh-id=\"1\" style=\"width:%dpx\"><span>Unchanged text</span></div>", rule, width];
        };
        LHContainer container;
        container.set_viewport(position(0, 0, 340, 360));
        auto doc = document::createUpdatableFromString(markup(300).UTF8String, &container, master_css);
        doc->render(340);
        auto owner = doc->root()->select_one("[data-lh-id='1']");
        std::string before;
        XCTAssertTrue(doc->template_html(before));
        bool changed = false;
        XCTAssertFalse(doc->update_styles({{owner, "width:180px"}}, &changed));
        XCTAssertFalse(changed);
        XCTAssertFalse(doc->update_holes({{owner, "style", false, "width:180px"}}, &changed));
        XCTAssertFalse(changed);
        XCTAssertFalse(doc->update_from_string(markup(180).UTF8String, &changed));
        XCTAssertFalse(changed);
        std::string after;
        XCTAssertTrue(doc->template_html(after));
        XCTAssertEqual(before, after);
        XCTAssertEqual(std::string(owner->get_attr("style")), std::string("width:300px"));
        [self assertDocument:[[LHDocument alloc] initWithHTML:markup(180) css:nil previous:WidthPage(markup(300))]
                    matches:WidthPage(markup(180)) label:rule scroll:NO];
    }
}

- (void)testReorderedShorthandDeclarationsRestyleDescendants
{
    WidthPatchMode mode(true);
    NSArray<NSString *> *styles = @[
        @"width:300px;font:12px sans-serif;font-size:20px;padding:3px;padding-left:15px",
        @"width:180px;font-size:20px;font:12px sans-serif;padding-left:15px;padding:3px",
        @"font:12px sans-serif;width:250px;font-size:20px;padding:3px;padding-left:15px"];
    LHDocument *previous = nil;
    for(NSString *style in styles)
    {
        NSString *html = [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0}.child{padding-left:inherit;background:#357;color:white}</style><div data-lh-id=\"1\" style=\"%@\"><div class=child data-lh-id=\"2\">Inherited font size and padding</div></div>", style];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
        [self assertDocument:patched matches:WidthPage(html) label:style scroll:NO];
        previous = patched;
    }
}

@end
