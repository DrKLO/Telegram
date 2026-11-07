#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface SVGGeometryTests : XCTestCase
@end

@implementation SVGGeometryTests

static NSData *SVGCachePixels(LHDocument *document)
{
    [document layoutWithMaxWidth:400 preferredWidth:400];
    CGImageRef image = [document copyImageWithMaxWidth:400 rect:CGRectMake(0, 0, 400, 240) scale:2];
    if(!image)
    {
        XCTFail(@"SVG render failed");
        return [NSData data];
    }
    NSData *data = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
    CGImageRelease(image);
    return data;
}

static LHDocument *SVGCacheWarmDocument(NSString *html)
{
    NSString *initial = [html stringByReplacingOccurrencesOfString:@"style='opacity:1'" withString:@"style='opacity:.99'"];
    LHDocument *previous = [[LHDocument alloc] initWithHTML:initial css:nil previous:nil];
    SVGCachePixels(previous);
    LHDocument *document = [[LHDocument alloc] initWithPrevious:previous styles:@{@"hand": @"opacity:1"} attribute:@"id" css:nil];
    XCTAssertNotNil(document);
    if(!document) return previous;
    SVGCachePixels(document);
    return document;
}

- (NSString *)groupScene:(NSString *)attributes extra:(NSString *)extra style:(NSString *)style
{
    NSMutableString *html = [NSMutableString stringWithFormat:@"<style>body{margin:0;background:#fff;color:#246b92}</style><svg id='svg' style='%@' width='200' height='100' viewBox='0 0 100 100'>%@<g %@>", style, extra, attributes];
    for(int i = 0; i < 12; i++)
        [html appendFormat:@"<circle cx='%d' cy='%d' r='9' stroke-width='2'/>", i * 12 - 15, 20 + i % 3 * 20];
    [html appendString:@"</g><path id='hand' style='opacity:1' d='M5 90 L90 85' stroke='orange' stroke-width='3'/></svg>"];
    return html;
}

- (void)assertPixels:(NSData *)a match:(NSData *)b
{
    XCTAssertEqual(a.length, b.length);
    if(a.length != b.length) return;
    const uint8_t *left = (const uint8_t *)a.bytes, *right = (const uint8_t *)b.bytes;
    int worst = 0;
    for(NSUInteger i = 0; i < a.length; i++)
        worst = MAX(worst, abs((int)left[i] - (int)right[i]));
    XCTAssertLessThanOrEqual(worst, 1);
}

- (void)testSVGGroupCachePreservesFractionalAndLetterboxedPixels
{
    for(NSString *style in @[@"display:block", @"display:block;margin:10.25px;width:200.25px;height:100.5px", @"display:block;opacity:.55;transform:scaleX(-1)"])
    {
        NSString *html = [self groupScene:@"fill='currentColor' stroke='#c74880'" extra:@"" style:style];
        [LHDocument setSVGGroupCacheEnabled:NO];
        LHDocument *document = SVGCacheWarmDocument(html);
        [LHDocument purgeCaches];
        NSData *plain = SVGCachePixels(document);
        NSUInteger plainBytes = [LHDocument rasterCacheBytes];
        [LHDocument setSVGGroupCacheEnabled:YES];
        [LHDocument purgeCaches];
        NSData *cached = SVGCachePixels(document);
        if([style isEqualToString:@"display:block"])
            XCTAssertGreaterThan([LHDocument rasterCacheBytes], plainBytes);
        [self assertPixels:plain match:cached];
    }
}

- (void)testSVGGroupCacheInvalidatesInheritedColors
{
    NSString *first = [self groupScene:@"fill='currentColor' stroke='#c74880'" extra:@"" style:@"display:block;color:#246b92"];
    NSString *next = [first stringByReplacingOccurrencesOfString:@"display:block;color:#246b92" withString:@"display:block;color:#a2d534"];
    [LHDocument setSVGGroupCacheEnabled:YES];
    [LHDocument purgeCaches];
    LHDocument *document = SVGCacheWarmDocument(first);
    NSData *initial = SVGCachePixels(document);
    LHDocument *updated = [[LHDocument alloc] initWithPrevious:document styles:@{@"svg": @"display:block;color:#a2d534"} attribute:@"id" css:nil];
    XCTAssertNotNil(updated);
    NSData *cached = SVGCachePixels(updated);
    XCTAssertNotEqualObjects(initial, cached);
    [LHDocument setSVGGroupCacheEnabled:NO];
    [LHDocument purgeCaches];
    LHDocument *reference = [[LHDocument alloc] initWithHTML:next css:nil];
    [self assertPixels:cached match:SVGCachePixels(reference)];
    [LHDocument setSVGGroupCacheEnabled:YES];
}

- (void)testSVGGroupCachePreservesNestedOpacityAndAnimatedSiblings
{
    NSString *first = [self groupScene:@"fill='rgba(36,107,146,.6)' stroke='#c74880'" extra:@"" style:@"display:block;margin:10.25px"];
    first = [first stringByReplacingOccurrencesOfString:@"<circle" withString:@"<g opacity='.55'><circle"];
    first = [first stringByReplacingOccurrencesOfString:@"stroke-width='2'/>" withString:@"stroke-width='2'/></g>"];
    NSString *next = [first stringByReplacingOccurrencesOfString:@"style='opacity:1'" withString:@"style='opacity:.5'"];
    [LHDocument setSVGGroupCacheEnabled:YES];
    [LHDocument purgeCaches];
    LHDocument *document = SVGCacheWarmDocument(first);
    NSData *initial = SVGCachePixels(document);
    LHDocument *updated = [[LHDocument alloc] initWithPrevious:document styles:@{@"hand": @"opacity:.5"} attribute:@"id" css:nil];
    XCTAssertNotNil(updated);
    NSData *cached = SVGCachePixels(updated);
    XCTAssertNotEqualObjects(initial, cached);
    [LHDocument setSVGGroupCacheEnabled:NO];
    [LHDocument purgeCaches];
    LHDocument *reference = [[LHDocument alloc] initWithHTML:next css:nil];
    [self assertPixels:cached match:SVGCachePixels(reference)];
    [LHDocument setSVGGroupCacheEnabled:YES];
}

- (void)testSVGGroupCacheTracksReferencedGradientChanges
{
    for(NSString *paint in @[@"fill='url(#paint)'", @"fill='URL(#paint)'", @"style='fill:var(--paint)'", @"fill='var(--paint)'"])
    {
        NSString *extra = @"<style>:root{--paint:url(#paint)}</style><defs><linearGradient id='paint'><stop id='stop' stop-color='#ff0000'/><stop offset='1' stop-color='#0000ff'/></linearGradient></defs>";
        NSString *first = [self groupScene:paint extra:extra style:@"display:block"];
        NSString *next = [first stringByReplacingOccurrencesOfString:@"stop-color='#ff0000'" withString:@"stop-color='#00ff00'"];
        [LHDocument setSVGGroupCacheEnabled:YES];
        [LHDocument purgeCaches];
        LHDocument *document = SVGCacheWarmDocument(first);
        NSData *initial = SVGCachePixels(document);
        const char changes[] = "stop\0stop-color\0#00ff00\0";
        LHDocument *updated = [[LHDocument alloc] initWithPrevious:document changes:[NSData dataWithBytes:changes length:sizeof(changes) - 1] attribute:@"id" css:nil];
        XCTAssertNotNil(updated);
        NSData *cached = SVGCachePixels(updated);
        XCTAssertNotEqualObjects(initial, cached, @"%@", paint);
        [LHDocument setSVGGroupCacheEnabled:NO];
        [LHDocument purgeCaches];
        LHDocument *reference = [[LHDocument alloc] initWithHTML:next css:nil];
        [self assertPixels:cached match:SVGCachePixels(reference)];
        [LHDocument setSVGGroupCacheEnabled:YES];
    }
}

- (void)testStaticSVGDoesNotAllocateGroupImages
{
    NSString *html = [self groupScene:@"fill='currentColor' stroke='#c74880'" extra:@"" style:@"display:block"];
    [LHDocument setSVGGroupCacheEnabled:NO];
    [LHDocument purgeCaches];
    LHDocument *plain = [[LHDocument alloc] initWithHTML:html css:nil];
    SVGCachePixels(plain);
    NSUInteger plainBytes = [LHDocument rasterCacheBytes];
    [LHDocument setSVGGroupCacheEnabled:YES];
    [LHDocument purgeCaches];
    LHDocument *cached = [[LHDocument alloc] initWithHTML:html css:nil];
    SVGCachePixels(cached);
    XCTAssertEqual([LHDocument rasterCacheBytes], plainBytes);
}

- (void)testSVGCacheTracksExternalGradientChanges
{
    for(NSString *paint in @[@"fill='url(#paint)'", @"fill='URL(#paint)'", @"class='filled'", @"style='fill:var(--paint)'", @"fill='var(--paint)'", @"fill='none' style='stroke:var(--paint);stroke-width:8'", @""])
    {
        NSString *inherited = paint.length ? @"" : @"fill:url(#paint);";
        NSString *first = [NSString stringWithFormat:@"<style>body{margin:0;background:white;%@--paint:url(#paint)}.filled{fill:url(#paint)}</style><svg width='0' height='0'><defs><linearGradient id='paint'><stop id='external-stop' stop-color='#ff0000'/><stop offset='1' stop-color='#0000ff'/></linearGradient></defs></svg><svg width='200' height='100'><rect %@ x='10' y='10' width='180' height='80'/></svg>", inherited, paint];
        NSString *next = [first stringByReplacingOccurrencesOfString:@"stop-color='#ff0000'" withString:@"stop-color='#00ff00'"];
        [LHDocument purgeCaches];
        LHDocument *document = [[LHDocument alloc] initWithHTML:first css:nil previous:nil];
        NSData *initial = SVGCachePixels(document);
        const char changes[] = "external-stop\0stop-color\0#00ff00\0";
        LHDocument *updated = [[LHDocument alloc] initWithPrevious:document changes:[NSData dataWithBytes:changes length:sizeof(changes) - 1] attribute:@"id" css:nil];
        XCTAssertNotNil(updated);
        NSData *cached = SVGCachePixels(updated);
        XCTAssertNotEqualObjects(initial, cached, @"%@", paint);
        [LHDocument purgeCaches];
        LHDocument *reference = [[LHDocument alloc] initWithHTML:next css:nil];
        [self assertPixels:cached match:SVGCachePixels(reference)];
    }
}

- (LHDocument*)document
{
    NSString* html = @"<!doctype html><svg width='200' height='100' viewBox='0 0 200 100'>"
                     @"<line data-t=line x1='0' y1='0' x2='30' y2='40'/>"
                     @"<rect data-t=rect x='10' y='10' width='50' height='20'/>"
                     @"<circle data-t=circle cx='50' cy='50' r='10'/>"
                     @"<polyline data-t=poly points='0,0 10,0 10,10'/>"
                     @"<path data-t=path d='M10 80 C 40 10, 65 10, 95 80'/>"
                     @"<path data-t=arc d='M0 50 A50 50 0 0 1 100 50'/>"
                     @"<g data-t=group></g></svg>";
    LHDocument* doc = [[LHDocument alloc] initWithHTML:html css:nil];
    [doc layoutWithMaxWidth:300 preferredWidth:300];
    return doc;
}

- (double)length:(NSString*)key in:(LHDocument*)doc
{
    return [[doc svgGeometry:@"getTotalLength" at:0 attribute:@"data-t" value:key][@"length"] doubleValue];
}

- (void)testTotalLengthMatchesWebKitForBasicShapes
{
    LHDocument* doc = [self document];
    XCTAssertEqualWithAccuracy([self length:@"line" in:doc], 50, 0.01);
    XCTAssertEqualWithAccuracy([self length:@"rect" in:doc], 140, 0.01);
    XCTAssertEqualWithAccuracy([self length:@"circle" in:doc], 2 * M_PI * 10, 0.1);
    XCTAssertEqualWithAccuracy([self length:@"poly" in:doc], 20, 0.01);
    XCTAssertEqualWithAccuracy([self length:@"path" in:doc], 141.2, 1.0);
    XCTAssertEqualWithAccuracy([self length:@"arc" in:doc], M_PI * 50, 0.3);
    XCTAssertNil([doc svgGeometry:@"getTotalLength" at:0 attribute:@"data-t" value:@"group"]);
}

- (void)testPointAtLengthWalksTheOutline
{
    LHDocument*   doc = [self document];
    NSDictionary* p   = [doc svgGeometry:@"getPointAtLength" at:25 attribute:@"data-t" value:@"line"];
    XCTAssertEqualWithAccuracy([p[@"x"] doubleValue], 15, 0.01);
    XCTAssertEqualWithAccuracy([p[@"y"] doubleValue], 20, 0.01);
    p = [doc svgGeometry:@"getPointAtLength" at:15 attribute:@"data-t" value:@"poly"];
    XCTAssertEqualWithAccuracy([p[@"x"] doubleValue], 10, 0.01);
    XCTAssertEqualWithAccuracy([p[@"y"] doubleValue], 5, 0.01);
    p = [doc svgGeometry:@"getPointAtLength" at:1000 attribute:@"data-t" value:@"line"];
    XCTAssertEqualWithAccuracy([p[@"x"] doubleValue], 30, 0.01);
}

- (void)testBBoxCoversTheCurveNotItsControlPoints
{
    NSDictionary* b = [[self document] svgGeometry:@"getBBox" at:0 attribute:@"data-t" value:@"path"];
    XCTAssertEqualWithAccuracy([b[@"x"] doubleValue], 10, 0.5);
    XCTAssertEqualWithAccuracy([b[@"width"] doubleValue], 85, 0.5);
    XCTAssertEqualWithAccuracy([b[@"y"] doubleValue], 27.5, 0.5);
    XCTAssertEqualWithAccuracy([b[@"height"] doubleValue], 52.5, 0.5);
}

@end
