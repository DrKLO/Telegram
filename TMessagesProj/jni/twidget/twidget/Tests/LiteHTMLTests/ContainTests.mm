#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <string>

@interface ContainTests : XCTestCase
@end

@implementation ContainTests

static LHDocument *Laid(NSString *html, CGFloat width)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:@"body{margin:0;font:12px -apple-system}"];
    [document layoutWithMaxWidth:width preferredWidth:width];
    return document;
}

static NSData *Pixels(LHDocument *document, CGFloat width)
{
    CGSize     size  = [document layoutWithMaxWidth:width preferredWidth:width];
    CGImageRef image = [document copyImageWithMaxWidth:width rect:CGRectMake(0, 0, width, MAX(1, ceil(size.height))) scale:2];
    NSData    *data  = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static CGRect Box(LHDocument *document, NSString *value)
{
    return [[document boundsByAttribute:@"data-k"][value] rectValue];
}

- (void)testLayoutContainmentStartsAFormattingContext
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *style) {
        return [NSString stringWithFormat:@"<div data-k=\"a\" style=\"%@\"><p data-k=\"p\" style=\"margin:10px 0\">text</p>"
                                           "<i style=\"float:left;width:20px;height:60px;background:red\"></i></div><div data-k=\"b\">after</div>",
                                           style];
    };
    LHDocument *contained = Laid(page(@"contain:layout"), 200);
    LHDocument *rooted    = Laid(page(@"overflow:hidden"), 200);
    LHDocument *plain     = Laid(page(@""), 200);
    XCTAssertEqual(Box(contained, @"a").size.height, Box(rooted, @"a").size.height);
    XCTAssertEqual(Box(contained, @"b").origin.y, Box(rooted, @"b").origin.y);
    XCTAssertNotEqual(Box(contained, @"a").size.height, Box(plain, @"a").size.height);
    XCTAssertEqual(Box(Laid(page(@"contain:content"), 200), @"a").size.height, Box(contained, @"a").size.height);
}

- (void)testPaintContainmentClipsLikeHiddenOverflow
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *style) {
        return [NSString stringWithFormat:@"<div style=\"%@;width:80px;height:30px;padding:4px;background:#eee\">"
                                           "<div style=\"width:160px;height:90px;background:#c00\"></div></div><p>below</p>",
                                           style];
    };
    XCTAssertEqualObjects(Pixels(Laid(page(@"contain:paint"), 240), 240), Pixels(Laid(page(@"overflow:hidden"), 240), 240));
    XCTAssertNotEqualObjects(Pixels(Laid(page(@"contain:paint"), 240), 240), Pixels(Laid(page(@""), 240), 240));
}

- (void)testClipOverflowClipsWithoutStartingAFormattingContext
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *style) {
        return [NSString stringWithFormat:@"<div data-k='box' style='%@'><div style='float:left;width:20px;height:60px;background:red'></div></div><div data-k='after'>after</div>", style];
    };
    LHDocument *clipped = Laid(page(@"overflow:clip"), 200);
    LHDocument *visible = Laid(page(@"overflow:visible"), 200);
    LHDocument *hidden = Laid(page(@"overflow:hidden"), 200);
    XCTAssertEqual(Box(clipped, @"box").size.height, Box(visible, @"box").size.height);
    XCTAssertEqual(Box(clipped, @"after").origin.y, Box(visible, @"after").origin.y);
    XCTAssertNotEqual(Box(clipped, @"box").size.height, Box(hidden, @"box").size.height);
    NSString *paint = @"<div style='overflow:%@;width:80px;height:30px;padding:4px;background:#eee'><div style='width:160px;height:90px;background:#c00'></div></div><p>below</p>";
    XCTAssertEqualObjects(Pixels(Laid([NSString stringWithFormat:paint, @"clip"], 240), 240), Pixels(Laid([NSString stringWithFormat:paint, @"hidden"], 240), 240));
}

- (void)testClipOverflowPreservesContentMinimumInFlexItems
{
    for(NSString *direction in @[@"row", @"column"])
    {
        NSString *(^page)(NSString *) = ^NSString *(NSString *overflow) {
            return [NSString stringWithFormat:@"<div style='display:flex;flex-direction:%@;width:80px;height:80px'><div data-k='item' style='flex:1;overflow:%@'><div style='width:160px;height:200px'></div></div></div>", direction, overflow];
        };
        CGRect clipped = Box(Laid(page(@"clip"), 240), @"item");
        CGRect visible = Box(Laid(page(@"visible"), 240), @"item");
        CGRect hidden = Box(Laid(page(@"hidden"), 240), @"item");
        if([direction isEqualToString:@"row"])
        {
            XCTAssertEqual(clipped.size.width, visible.size.width);
            XCTAssertNotEqual(clipped.size.width, hidden.size.width);
        } else
        {
            XCTAssertEqual(clipped.size.height, visible.size.height);
            XCTAssertNotEqual(clipped.size.height, hidden.size.height);
        }
    }
}

- (void)testRemovingScrollableOverflowClearsThePreviousOffset
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *overflow) {
        return [NSString stringWithFormat:@"<div data-k='box' style='width:160px;height:80px;overflow:%@'><div style='height:80px;background:red'>First</div><div style='height:200px;background:blue'>Second</div></div>", overflow];
    };
    for(NSString *overflow in @[@"clip", @"visible"])
    {
        LHDocument *previous = [[LHDocument alloc] initWithHTML:page(@"auto") css:@"body{margin:0;font:12px -apple-system}" previous:nil];
        [previous layoutWithMaxWidth:240 preferredWidth:240];
        XCTAssertEqual([previous setScrollOffset:CGPointMake(0, 80) attribute:@"data-k" value:@"box"].y, 80);
        LHDocument *patched = [[LHDocument alloc] initWithHTML:page(overflow) css:@"body{margin:0;font:12px -apple-system}" previous:previous];
        XCTAssertTrue(patched.reusedPrevious);
        LHDocument *fresh = Laid(page(overflow), 240);
        XCTAssertEqualObjects(Pixels(patched, 240), Pixels(fresh, 240), @"%@", overflow);
        XCTAssertNil([patched scrollersByAttribute:@"data-k"][@"box"], @"%@", overflow);
        XCTAssertEqual([patched setScrollOffset:CGPointMake(0, 80) attribute:@"data-k" value:@"box"].y, 0, @"%@", overflow);
    }
}

- (void)testHiddenOverflowSupportsScriptScrollingWithoutWheelScrolling
{
    LHDocument *document = Laid(@"<div data-k='box' style='width:160px;height:80px;padding:4px;overflow:hidden'><div data-k='child' style='height:280px;background:linear-gradient(red,blue)'>Scrollable</div></div>", 240);
    NSDictionary *info = [document scrollersByAttribute:@"data-k"][@"box"];
    XCTAssertNotNil(info);
    XCTAssertEqual([info[@"content"] sizeValue].height, 288);
    XCTAssertEqual([info[@"viewport"] rectValue].size.height, 88);
    XCTAssertEqualObjects(info[@"wheel"], @NO);
    XCTAssertEqual([document setScrollOffset:CGPointMake(0, 999) attribute:@"data-k" value:@"box"].y, 200);
    XCTAssertEqual(Box(document, @"child").origin.y, -196);
}

- (void)testLayoutChangesInsideScrolledHiddenOverflowInvalidateVisiblePixels
{
    NSString *(^page)(int) = ^NSString *(int height) {
        return [NSString stringWithFormat:@"<div data-k='box' style='width:160px;height:80px;overflow:hidden'><div style='height:80px'></div><div data-k='edit' style='height:%dpx;background:red'>Text</div><div style='height:200px;background:blue'></div></div>", height];
    };
    NSString *css = @"body{margin:0;height:1000px;font:12px -apple-system}";
    LHDocument *before = [[LHDocument alloc] initWithHTML:page(20) css:css previous:nil];
    [before layoutWithMaxWidth:240 preferredWidth:240];
    [before setScrollOffset:CGPointMake(0, 80) attribute:@"data-k" value:@"box"];
    LHDocument *after = [[LHDocument alloc] initWithHTML:page(40) css:css previous:before];
    XCTAssertTrue(after.reusedPrevious);
    [after layoutWithMaxWidth:240 preferredWidth:240];
    [after setScrollOffset:CGPointMake(0, 80) attribute:@"data-k" value:@"box"];
    NSArray<NSValue *> *rects = after.layoutDirtyRects;
    BOOL covered = rects == nil;
    for(NSValue *rect in rects)
        covered = covered || CGRectContainsPoint(rect.rectValue, CGPointMake(20, 30));
    XCTAssertTrue(covered, @"Visible changed pixels are outside %@", rects);
}

- (void)testSizeContainmentIgnoresContent
{
    LHDocument *document = Laid(@"<div data-k=\"a\" style=\"contain:size;padding:3px\"><p>one</p><p>two</p></div>"
                                 "<div data-k=\"b\" style=\"contain:strict;height:40px;width:50px\"><p>one</p><p>two</p><p>three</p></div>"
                                 "<span data-k=\"c\" style=\"display:inline-block;contain:size;border:1px solid\">wide words here</span>"
                                 "<div data-k=\"d\" style=\"contain:size;min-height:12px\">x</div>",
                                 200);
    XCTAssertEqual(Box(document, @"a").size.height, 6);
    XCTAssertEqual(Box(document, @"b").size.height, 40);
    XCTAssertEqual(Box(document, @"b").size.width, 50);
    XCTAssertEqual(Box(document, @"c").size.width, 2);
    XCTAssertEqual(Box(document, @"d").size.height, 12);
    XCTAssertEqualObjects(Pixels(Laid(@"<p>a <span style=\"contain:strict\">inline ignores it</span> b</p><table><tr style=\"contain:size\"><td>cell</td></tr></table>", 200), 200),
                          Pixels(Laid(@"<p>a <span>inline ignores it</span> b</p><table><tr><td>cell</td></tr></table>", 200), 200));
}

- (void)testContainKeywordsParse
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *value) {
        return [NSString stringWithFormat:@"<div data-k=\"a\" style=\"contain:%@\"><p>one</p></div>", value];
    };
    for(NSString *value in @[@"size", @"strict", @"size layout", @"layout size paint style", @"inline-size size"])
        XCTAssertEqual(Box(Laid(page(value), 200), @"a").size.height, 0, @"%@", value);
    for(NSString *value in @[@"none", @"layout", @"content", @"paint", @"style", @"bogus", @"size bogus", @"inline-size"])
        XCTAssertGreaterThan(Box(Laid(page(value), 200), @"a").size.height, 0, @"%@", value);
}

static NSString *Cards(int changed, int value, NSString *contain)
{
    NSMutableString *html = [NSMutableString stringWithFormat:@"<!DOCTYPE html><style>.card{%@;font-family:-apple-system;height:22px;padding:2px 6px;border-bottom:1px solid #ddd}"
                                                                ".v{float:right}.hot{color:#c00}</style><div data-lh-id=\"1\">",
                                                                contain];
    for(int i = 0; i < 120; i++)
    {
        const bool edited = i == changed;
        [html appendFormat:@"<div data-lh-id=\"%d\" class=\"card\">Entry %d <span data-lh-id=\"%d\" class=\"v%@\">%d.%02d</span></div>", 10 + i * 2, i,
                           11 + i * 2, edited && value % 2 ? @" hot" : @"", edited ? value : i, edited ? value % 100 : 0];
    }
    [html appendString:@"</div>"];
    return html;
}

- (void)testStrictCardsRelayoutOnlyTheEditedCard
{
    for(NSString *contain in @[@"contain:strict", @"contain:size layout", @"contain:content", @"contain:paint"])
    {
        LHDocument *document = [[LHDocument alloc] initWithHTML:Cards(-1, 0, contain) css:nil previous:nil];
        [document layoutWithMaxWidth:280 preferredWidth:280];
        document = [[LHDocument alloc] initWithHTML:Cards(5, 1, contain) css:nil previous:document];
        [document layoutWithMaxWidth:280 preferredWidth:280];
        for(int frame = 2; frame < 14; frame++)
        {
            NSString   *html   = Cards(frame % 7 * 9, 1000 + frame * 37 * (frame % 3 + 1), contain);
            long        hits   = [LHDocument.layoutCacheStats[@"hits"] longValue];
            LHDocument *patch  = [[LHDocument alloc] initWithHTML:html css:nil previous:document];
            LHDocument *fresh  = [[LHDocument alloc] initWithHTML:html css:nil];
            XCTAssertTrue(patch.reusedPrevious, @"%@", contain);
            XCTAssertEqualObjects(Pixels(patch, 280), Pixels(fresh, 280), @"%@ frame %d", contain, frame);
            XCTAssertEqualObjects(patch.layoutSignature, fresh.layoutSignature, @"%@ frame %d", contain, frame);
            const long reused = [LHDocument.layoutCacheStats[@"hits"] longValue] - hits;
            if([contain hasPrefix:@"contain:s"])
                XCTAssertLessThan(reused, 8, @"%@ frame %d", contain, frame);
            else
                XCTAssertGreaterThan(reused, 100, @"%@ frame %d", contain, frame);
            document = patch;
        }
    }
}

@end
