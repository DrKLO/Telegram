#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <vector>

@interface WebKitParityTests : XCTestCase
@end

namespace
{
    struct Pixels
    {
        size_t               width = 0, height = 0;
        std::vector<uint8_t> rgba;

        const uint8_t* at(size_t x, size_t y) const { return &rgba[(y * width + x) * 4]; }
        double alphaSum() const
        {
            double sum = 0;
            for(size_t i = 3; i < rgba.size(); i += 4)
                sum += rgba[i];
            return sum / 255;
        }
    };

    Pixels Render(NSString* html, CGFloat width, CGFloat* heightOut = nullptr)
    {
        LHDocument* doc  = [[LHDocument alloc] initWithHTML:[@"<style>body{margin:0}</style>" stringByAppendingString:html] css:nil];
        CGSize      size = [doc layoutWithMaxWidth:width];
        if(heightOut)
            *heightOut = size.height;
        Pixels     out;
        CGImageRef image = [doc copyImageWithMaxWidth:width rect:CGRectMake(0, 0, width, std::max<CGFloat>(1, size.height)) scale:2];
        if(!image)
            return out;
        out.width  = CGImageGetWidth(image);
        out.height = CGImageGetHeight(image);
        out.rgba.assign(out.width * out.height * 4, 0);
        CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
        CGContextRef    ctx   = CGBitmapContextCreate(out.rgba.data(), out.width, out.height, 8, out.width * 4, space,
                                                      kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        CGContextDrawImage(ctx, CGRectMake(0, 0, out.width, out.height), image);
        CGContextRelease(ctx);
        CGColorSpaceRelease(space);
        CGImageRelease(image);
        return out;
    }
}

@implementation WebKitParityTests

- (void)testFontSmoothingDilatesGlyphsUnlessAntialiased
{
    NSString* text = @"<p style=\"margin:0;font:15px -apple-system\">Bom dia! Checked us all in</p>";
    double    smooth = Render(text, 300).alphaSum();
    double    antialiased =
        Render([NSString stringWithFormat:@"<div style=\"-webkit-font-smoothing:antialiased\">%@</div>", text], 300).alphaSum();
    double inherited = Render([NSString stringWithFormat:@"<div style=\"-webkit-font-smoothing:antialiased\"><div style=\"-webkit-font-smoothing:auto\">%@</div></div>", text], 300).alphaSum();
    XCTAssertGreaterThan(antialiased, 0);
    XCTAssertGreaterThan(smooth, antialiased * 1.08);
    XCTAssertEqualWithAccuracy(inherited, smooth, 0.5);
}

- (void)testStylesheetFillReferencesPaintServers
{
    NSString* html = @"<style>.a{fill:url(#g)}.b{fill:url(#missing)}.c{fill:url(#missing) red}</style>"
                     @"<svg width=\"300\" height=\"60\" style=\"display:block\"><defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#00f\"/><stop offset=\"1\" stop-color=\"#00f\"/></linearGradient></defs>"
                     @"<rect class=\"a\" x=\"0\" width=\"80\" height=\"60\"/><rect class=\"b\" x=\"100\" width=\"80\" height=\"60\"/><rect class=\"c\" x=\"200\" width=\"80\" height=\"60\"/></svg>";
    Pixels p = Render(html, 300);
    XCTAssertEqual(p.at(80, 60)[2], 255);
    XCTAssertEqual(p.at(80, 60)[3], 255);
    XCTAssertEqual(p.at(280, 60)[3], 0);
    XCTAssertEqual(p.at(480, 60)[0], 255);
    XCTAssertEqual(p.at(480, 60)[3], 255);
}

- (void)testTableHeadersCenterUnlessTheRowAlignsAndTextAlignEndAlignsRight
{
    NSString* box = @"<span style=\"display:inline-block;width:20px;height:10px;background:#f00;vertical-align:top\"></span>";
    NSString* table = @"<table style=\"width:200px;border-spacing:0;%@\"><tr><th style=\"padding:0;line-height:10px\">%@</th></tr></table>";
    Pixels centered = Render([NSString stringWithFormat:table, @"", box], 200);
    XCTAssertEqual(centered.at(200, 10)[3], 255);
    XCTAssertEqual(centered.at(20, 10)[3], 0);
    Pixels left = Render([NSString stringWithFormat:table, @"text-align:left", box], 200);
    XCTAssertEqual(left.at(20, 10)[3], 255);
    XCTAssertEqual(left.at(200, 10)[3], 0);
    Pixels end = Render([NSString stringWithFormat:@"<div style=\"width:200px;text-align:end;line-height:10px\">%@</div>", box], 200);
    XCTAssertEqual(end.at(380, 10)[3], 255);
    XCTAssertEqual(end.at(20, 10)[3], 0);
}

- (void)testWordsKeepFractionalPositionsLikeOneShapedRun
{
    Pixels p = Render(@"<div style=\"font:15px -apple-system;line-height:20px;white-space:nowrap\">l l l l l l l l l l l l</div>", 400);
    std::vector<double> centroids;
    double              sum = 0, weighted = 0;
    for(size_t x = 0; x < p.width; x++)
    {
        double column = 0;
        for(size_t y = 0; y < p.height; y++)
            column += p.at(x, y)[3];
        if(column > 0)
        {
            sum += column;
            weighted += column * x;
        } else if(sum > 0)
        {
            centroids.push_back(weighted / sum);
            sum = weighted = 0;
        }
    }
    XCTAssertEqual(centroids.size(), 12u);
    bool fractional = false;
    for(size_t i = 1; i < centroids.size(); i++)
    {
        double step = centroids[i] - centroids[i - 1];
        fractional  = fractional || std::fabs(step - std::round(step)) > 0.3;
    }
    XCTAssertTrue(fractional);
}

- (void)testInsetOutsetGrooveBordersShadeLikeWebKit
{
    Pixels inset = Render(@"<div style=\"width:20px;height:20px;border:4px inset #808080\"></div>", 100);
    XCTAssertEqualWithAccuracy(inset.at(28, 2)[0], 44, 2);
    XCTAssertEqualWithAccuracy(inset.at(28, 54)[0], 213, 2);
    Pixels outset = Render(@"<div style=\"width:20px;height:20px;border:4px outset #808080\"></div>", 100);
    XCTAssertEqualWithAccuracy(outset.at(28, 2)[0], 213, 2);
    XCTAssertEqualWithAccuracy(outset.at(2, 28)[0], 213, 2);
    XCTAssertEqualWithAccuracy(outset.at(54, 28)[0], 44, 2);
    Pixels groove = Render(@"<div style=\"width:20px;height:20px;border:8px groove #808080\"></div>", 100);
    XCTAssertEqualWithAccuracy(groove.at(36, 2)[0], 44, 2);
    XCTAssertEqualWithAccuracy(groove.at(36, 13)[0], 213, 2);
    Pixels small = Render(@"<div style=\"font-size:20px\"><small style=\"display:inline-block;width:1em;height:1em;background:#000\"></small></div>", 100);
    size_t width = 0;
    for(size_t x = 0; x < small.width; x++)
        width += small.at(x, small.height - 6)[3] > 128;
    XCTAssertEqualWithAccuracy(double(width), 2 * 20 / 1.2, 2);
}

- (void)testAutoTablesSpreadSpaceByMaxContentAndFixedTablesSplitTheRestEvenly
{
    NSString* cell = @"<td style=\"padding:0;background:%@\"><span style=\"display:inline-block;width:%dpx;height:10px\"></span></td>";
    NSString* autoTable = [NSString stringWithFormat:@"<table style=\"width:400px;border-spacing:0\"><tr>%@%@</tr></table>",
                                                     [NSString stringWithFormat:cell, @"#f00", 20], [NSString stringWithFormat:cell, @"#00f", 60]];
    Pixels a = Render(autoTable, 400);
    XCTAssertEqual(a.at(196, 10)[0], 255);
    XCTAssertEqual(a.at(204, 10)[2], 255);
    NSString* fixedTable = @"<table style=\"width:300px;border-spacing:0;table-layout:fixed\"><tr>"
                           @"<td style=\"padding:0;width:50px;background:#f00\">a</td><td style=\"padding:0;background:#0f0\">b</td><td style=\"padding:0;background:#00f\">c</td></tr>"
                           @"<tr><td style=\"padding:0\">much wider text here</td><td></td><td></td></tr></table>";
    Pixels f = Render(fixedTable, 300);
    XCTAssertEqual(f.at(96, 4)[0], 255);
    XCTAssertEqual(f.at(104, 4)[1], 255);
    XCTAssertEqual(f.at(346, 4)[1], 255);
    XCTAssertEqual(f.at(354, 4)[2], 255);
}

- (void)testAutoMarginsCenterFlexAndGridContainers
{
    for(NSString* display in @[@"grid", @"flex", @"block"])
    {
        Pixels p = Render([NSString stringWithFormat:@"<div style=\"width:400px\"><div style=\"display:%@;max-width:100px;height:10px;margin:0 auto;background:#f00\"></div></div>", display], 400);
        XCTAssertEqual(p.at(296, 10)[0], 0, @"%@", display);
        XCTAssertEqual(p.at(304, 10)[0], 255, @"%@", display);
        XCTAssertEqual(p.at(496, 10)[0], 255, @"%@", display);
        XCTAssertEqual(p.at(504, 10)[0], 0, @"%@", display);
    }
}

- (void)testTableCellsCenterBlockContentVertically
{
    Pixels p = Render(@"<table style=\"border-spacing:0\"><tr><td style=\"padding:0;height:60px\"><div style=\"width:20px;height:20px;background:#f00\"></div></td>"
                      @"<td style=\"padding:0;vertical-align:bottom\"><div style=\"width:20px;height:20px;background:#00f\"></div></td></tr></table>", 100);
    XCTAssertEqual(p.at(20, 30)[3], 0);
    XCTAssertEqual(p.at(20, 60)[0], 255);
    XCTAssertEqual(p.at(20, 90)[3], 0);
    XCTAssertEqual(p.at(60, 110)[2], 255);
    XCTAssertEqual(p.at(60, 60)[3], 0);
}

- (void)testTimesHelveticaCourierGetWebKitAscentAndTheSystemFontIsTheDefault
{
    for(NSString* family in @[@"serif", @"sans-serif", @"Courier"])
    {
        CGFloat height = 0;
        Render([NSString stringWithFormat:@"<div style=\"font-family:%@;font-size:13px\">Hxg</div>", family], 200, &height);
        XCTAssertEqual(height, 15, @"%@", family);
    }
    CGFloat system = 0, plain = 0, unknown = 0;
    Render(@"<div style=\"font-family:-apple-system;font-size:20px\">Hxg</div>", 200, &system);
    Render(@"<div style=\"font-size:20px\">Hxg</div>", 200, &plain);
    Render(@"<div style=\"font-family:'No Such Font';font-size:20px\">Hxg</div>", 200, &unknown);
    XCTAssertEqual(plain, system);
    XCTAssertEqual(unknown, system);
}

- (void)testLineBoxesKeepTheStrutAndHalfLeadingOfEveryInlineBox
{
    CGFloat plain = 0, small = 0, tiny = 0, mixed = 0;
    Render(@"<div style=\"font:20px -apple-system\">x</div>", 200, &plain);
    Render(@"<div style=\"font:20px -apple-system\"><small>x</small></div>", 200, &small);
    Render(@"<div style=\"font:20px -apple-system;line-height:30px\"><span style=\"font-size:10px;line-height:10px\">x</span></div>", 200, &tiny);
    Render(@"<div style=\"font:12px -apple-system;line-height:16px\"><span style=\"font-size:24px;line-height:16px\">X</span>x</div>", 200, &mixed);
    XCTAssertEqual(small, plain);
    XCTAssertEqual(tiny, 30);
    XCTAssertGreaterThan(mixed, 16);
}

@end
