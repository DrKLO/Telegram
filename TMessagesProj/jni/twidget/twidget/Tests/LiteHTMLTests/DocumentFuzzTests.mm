#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHDocumentPainting.h"

#include <random>
#include <string>
#include <vector>

@interface DocumentFuzzTests : XCTestCase
@end

@implementation DocumentFuzzTests

static NSArray<NSString *> *Seeds(void)
{
    NSMutableArray<NSString *> *seeds = [NSMutableArray array];
    NSString *dir = [[@(__FILE__) stringByDeletingLastPathComponent] stringByAppendingPathComponent:@"../../Examples/chats"];
    for(NSString *name in [[NSFileManager defaultManager] contentsOfDirectoryAtPath:dir error:nil])
    {
        NSString *text = [NSString stringWithContentsOfFile:[dir stringByAppendingPathComponent:name] encoding:NSUTF8StringEncoding error:nil];
        if(text.length > 0)
            [seeds addObject:[text substringToIndex:MIN(text.length, (NSUInteger)60000)]];
    }
    [seeds addObject:@"<table><tr><td colspan=99999 rowspan=99999>x</td></tr></table><svg viewBox=\"0 0 1e30 1e30\"><path d=\"M0 0 A1e30 1e30 0 1 1 1 1Z\"/><text font-size=\"1e9\" dy=\"1e9em\">t</text><linearGradient id=\"g\"><stop offset=\"NaN\"/></linearGradient><rect fill=\"url(#g)\" width=\"1e38\" height=\"-1\"/></svg>"];
    [seeds addObject:@"<style>@import url(http://x); *{width:calc(1e308px*1e308);font-size:1e30px;line-height:-1;animation:a 1s;transition:all 1s} @keyframes a{from{}}</style><div style=\"--a:var(--a);color:var(--a);border-radius:1e30px;box-shadow:0 0 1e30px red;transform:scale(1e30) rotate(NaNdeg)\">x</div>"];
    return seeds;
}

- (void)testMutatedDocumentsDoNotCrash
{
    std::mt19937 rng(20261011);
    NSArray<NSString *> *seeds = Seeds();
    XCTAssertGreaterThan(seeds.count, 3u);
    const std::vector<std::string> inserts = {
        "<", ">", "</div>", "<div>", "<table>", "<td>", "\"", "'", "<svg>", "</svg>", "<style>", "</style>", "{", "}", ";", ":", "(", ")", "url(", "calc(", "var(",
        "light-dark(", "&#0;", "&#xFFFFFFFF;", "\xF0\x9F", "\xC0\x80", "\0", "<!--", "-->", "<![CDATA[", "<script>", "<iframe>", "<img src=", " style=\"", "colspan=1000000",
        "<br>", "<li>", "<ol start=-2147483648>", "<p>", "&nbsp;", "‮", "<math>", "<template>", "<select>", "<option>", "<canvas width=99999999>",
    };
    const char  *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    const double seconds  = budget ? atof(budget) : 15;
    const auto   deadline = [NSDate dateWithTimeIntervalSinceNow:seconds];
    int          documents = 0;
    while([deadline timeIntervalSinceNow] > 0 && documents < (budget ? 1000000 : 400))
    {
        @autoreleasepool
        {
            std::string text = [seeds[rng() % seeds.count] UTF8String];
            const int edits = 1 + rng() % 40;
            for(int e = 0; e < edits && !text.empty(); e++)
            {
                size_t at = rng() % text.size();
                switch(rng() % 4)
                {
                case 0:
                    text.insert(at, inserts[rng() % inserts.size()]);
                    break;
                case 1:
                    text.erase(at, 1 + rng() % 64);
                    break;
                case 2:
                    text[at] = static_cast<char>(rng() % 256);
                    break;
                default:
                {
                    size_t from = rng() % text.size(), len = std::min<size_t>(1 + rng() % 400, text.size() - from);
                    text.insert(at, text.substr(from, len));
                    break;
                }
                }
            }
            NSString *html = [[NSString alloc] initWithBytes:text.data() length:text.size() encoding:NSUTF8StringEncoding] ?: [[NSString alloc] initWithBytes:text.data() length:text.size() encoding:NSISOLatin1StringEncoding];
            LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
            CGSize size = [document layoutWithMaxWidth:(CGFloat)(50 + rng() % 600)];
            XCTAssertTrue(std::isfinite(size.width) && std::isfinite(size.height));
            CGImageRef image = [document copyImageWithMaxWidth:300 rect:CGRectMake(0, 0, 300, std::min<CGFloat>(size.height, 600)) scale:1];
            if(image)
                CGImageRelease(image);
            [document mouseMoved:CGPointMake(rng() % 300, rng() % 300)];
            [document mouseDown:CGPointMake(rng() % 300, rng() % 300)];
            [document mouseUp:CGPointMake(rng() % 300, rng() % 300)];
            (void)[document textFragmentsWithMaxWidth:300];
            (void)[document boundsByAttribute:@"id"];
            documents++;
        }
    }
    NSLog(@"FUZZ documents=%d", documents);
    XCTAssertGreaterThan(documents, 50);
}

- (void)testOrphanTablePartsTerminate
{
    NSArray<NSString *> *cases = @[
        @"<div style=\"display:table-cell;height:66px\"></div>",
        @"<div style=\"display:table-row\">x</div>",
        @"<div style=\"display:table-row-group\"><div style=\"display:table-cell\">a</div></div>",
        @"<span style=\"display:table-cell\">a</span>b<span style=\"display:table-cell\">c</span>",
        @"<div style=\"display:table\">a<div style=\"display:table-cell\">b</div></div>",
    ];
    for(NSString *html in cases)
    {
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        CGSize size = [document layoutWithMaxWidth:300];
        XCTAssertTrue(std::isfinite(size.height), @"%@", html);
    }
}

- (void)testStyleSharingRespectsStructuralPseudoClasses
{
    NSString *html = @"<!doctype html><style>body{margin:0} .ev{padding-bottom:8px;height:10px} .ev:last-child{padding-bottom:0}</style><div><div class=\"ev\" data-k=\"a\"></div><div class=\"ev\" data-k=\"b\"></div><div class=\"ev\" data-k=\"c\"></div></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    CGSize size = [document layoutWithMaxWidth:300];
    XCTAssertEqualWithAccuracy(size.height, 46, 0.5);
}

- (void)testWebKitBlockAndLineMetrics
{
    NSDictionary<NSString *, NSNumber *> *cases = @{
        @"<!doctype html><style>body{margin:0;line-height:20px}</style><div style=\"padding:1px\"><div style=\"margin-bottom:8px\">a</div><div><div style=\"margin:5px 0\">x</div></div></div>": @55,
        @"<!doctype html><style>body{margin:0}</style><div style=\"line-height:66px\"><span style=\"display:inline-block;width:10px\"></span></div>": @66,
        @"<!doctype html><style>body{margin:0}</style><table style=\"border-spacing:0\"><tr><td style=\"height:66px;padding:0;border:2px solid red\"></td></tr></table>": @70,
    };
    for(NSString *html in cases)
    {
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        XCTAssertEqualWithAccuracy([document layoutWithMaxWidth:300].height, cases[html].doubleValue, 0.5, @"%@", html);
    }
}

- (void)testTransformArgumentsEvaluateCalc
{
    NSString *(^page)(NSString *) = ^NSString *(NSString *transform) {
        return [NSString stringWithFormat:@"<!doctype html><style>body{margin:0} .b{width:40px;height:40px;background:#f00;transform:%@}</style><div style=\"padding:30px; --w: 5\"><div class=\"b\"></div></div>", transform];
    };
    CGImageRef (^render)(NSString *) = ^CGImageRef(NSString *transform) {
        LHDocument *document = [[LHDocument alloc] initWithHTML:page(transform) css:nil];
        [document layoutWithMaxWidth:200];
        return [document copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, 100) scale:1];
    };
    CGImageRef expected = render(@"translateX(25px) rotate(10deg)");
    for(NSString *variant in @[@"translateX(calc(10px * 2 + 5px)) rotate(calc(20deg / 2))", @"translateX(calc(var(--w) * 5px)) rotate(10deg)"])
    {
        CGImageRef actual = render(variant);
        NSData *a = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(expected));
        NSData *b = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(actual));
        XCTAssertEqualObjects(a, b, @"%@", variant);
        CGImageRelease(actual);
    }
    CGImageRelease(expected);
}

- (void)testPaintOnlyPatchesMatchFreshDocuments
{
    NSArray<NSArray<NSString *> *> *cases = @[
        @[@"color: #ff0000", @"color: #0000ff"],
        @[@"background-color: #ff0000", @"background-color: #00ff00"],
        @[@"opacity: 0.5", @"opacity: 0.8"],
        @[@"transform: translateX(4px)", @"transform: translateX(20px)"],
        @[@"visibility: visible", @"visibility: hidden"],
        @[@"box-shadow: 0 0 4px #000", @"box-shadow: 0 4px 10px #f00"],
        @[@"border-color: #f00", @"border-color: #0f0"],
        @[@"background-image: linear-gradient(#f00, #00f)", @"background-image: linear-gradient(#0f0, #00f)"],
        @[@"text-shadow: 1px 1px #f00", @"text-shadow: 2px 2px #00f"],
        @[@"outline-color: #f00", @"outline-color: #00f"],
    ];
    for(NSArray<NSString *> *pair in cases)
    {
        NSString *(^page)(NSString *) = ^NSString *(NSString *style) {
            return [NSString stringWithFormat:@"<!doctype html><body style=\"margin:0\"><div data-lh-id=\"1\" style=\"padding:8px; border: 2px solid; outline: 2px solid; %@\">Hello <b>there</b> friend, this wraps over lines</div><p data-lh-id=\"2\">after</p></body>", style];
        };
        LHDocument *first = [[LHDocument alloc] initWithHTML:page(pair[0]) css:nil];
        [first layoutWithMaxWidth:160 preferredWidth:160];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:page(pair[1]) css:nil previous:first];
        CGSize a = [patched layoutWithMaxWidth:160 preferredWidth:160];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:page(pair[1]) css:nil];
        CGSize b = [fresh layoutWithMaxWidth:160 preferredWidth:160];
        XCTAssertEqual(a.height, b.height, @"%@", pair[1]);
        CGImageRef x = [patched copyImageWithMaxWidth:160 rect:CGRectMake(0, 0, 160, b.height) scale:1];
        CGImageRef y = [fresh copyImageWithMaxWidth:160 rect:CGRectMake(0, 0, 160, b.height) scale:1];
        NSData *dx = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(x));
        NSData *dy = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(y));
        XCTAssertEqualObjects(dx, dy, @"%@ -> %@ (reused %d)", pair[0], pair[1], patched.reusedPrevious);
        CGImageRelease(x);
        CGImageRelease(y);
    }
}

- (void)testCachedSVGMatchesUncachedDraw
{
    NSString *format = @"<div style='padding:3.3px 5.7px;background:#f7f2fa;color:%@'><span>Revenue</span><svg width='170' height='40' viewBox='0 0 170 40'>"
                        "<polygon points='3,34 3,26 52.8,27.2 102.7,10.1 163.3,11 163.3,34' fill-opacity='0.15' fill='#af52de'/>"
                        "<polyline points='3,26 52.8,27.2 102.7,10.1 163.3,11' stroke-width='2' stroke='#af52de' fill='none' stroke-linecap='round' stroke-linejoin='round'/>"
                        "<circle cx='163.3' cy='11' r='3' fill='#af52de'/></svg></div>";
    for(NSNumber *scale in @[@1, @1.5, @2, @3])
    {
        LHDocument *previous = [[LHDocument alloc] initWithHTML:[NSString stringWithFormat:format, @"#1c1c1e"] css:nil];
        CGSize size = [previous layoutWithMaxWidth:200 preferredWidth:200];
        for(int i = 0; i < 3; i++)
            CGImageRelease([previous copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, size.height) scale:scale.doubleValue]);
        NSString *next = [NSString stringWithFormat:format, @"#3a3a3c"];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:next css:nil previous:previous];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:next css:nil];
        NSMutableArray<NSData *> *images = [NSMutableArray array];
        for(LHDocument *document in @[patched, fresh])
        {
            CGSize s = [document layoutWithMaxWidth:200 preferredWidth:200];
            CGImageRef image = [document copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, s.height) scale:scale.doubleValue];
            XCTAssertTrue(image != NULL);
            [images addObject:(__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image))];
            CGImageRelease(image);
        }
        XCTAssertEqualObjects(images[0], images[1], @"scale %@", scale);
    }
}

@end
