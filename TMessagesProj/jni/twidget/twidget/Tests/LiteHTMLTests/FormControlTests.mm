#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHDocumentPainting.h"

#include <string>
#include <vector>

namespace
{
struct CountingPainter : lhx::Painter
{
    std::vector<std::string> texts;
    int                      paths = 0;
    int                      fills = 0;

    void text(const char* text, const lhx::TextStyle& style, const litehtml::web_color& color, const litehtml::position& pos) override
    {
        texts.emplace_back(text);
    }
    void solidFill(const litehtml::background_layer& layer, const litehtml::web_color& color) override
    {
        fills++;
        colors.push_back(color);
    }
    std::vector<litehtml::web_color> colors;
    void path(const litehtml::vector_path& path) override { paths++; }
};
}

@interface FormControlTests : XCTestCase
@end

@implementation FormControlTests

- (NSDictionary<NSString *, NSValue *> *)boundsOf:(NSString *)html width:(CGFloat)width
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:width];
    return [document boundsByAttribute:@"data-t"];
}

- (CGRect)rect:(NSDictionary<NSString *, NSValue *> *)bounds key:(NSString *)key
{
    NSValue *value = bounds[key];
    XCTAssertNotNil(value, @"%@", key);
    return value.rectValue;
}

- (void)testControlsUseWebKitMetrics
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0;font-size:16px'><input data-t='text'> <input type='checkbox' data-t='check'> <input type='range' data-t='range'> <input type='color' data-t='color'> <button data-t='button'>x</button> <select data-t='select'><option>x</option></select></body>" width:800];
    XCTAssertEqualWithAccuracy([self rect:b key:@"text"].size.height, 21, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"text"].size.width, 155.9, 1.5);
    XCTAssertEqualWithAccuracy([self rect:b key:@"check"].size.width, 12, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"range"].size.width, 129, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"range"].size.height, 16, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"color"].size.width, 44, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"color"].size.height, 23, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"button"].size.height, 18, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"select"].size.height, 20, 0.6);
}

- (void)testButtonsKeepTheControlFontWhenRestyled
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0;font-size:24px'><button data-t='b' style='padding:6px 12px;border:1px solid #bbb;background:#fff'>Clicked</button></body>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"b"].size.height, 13 + 12 + 2, 0.6);
}

- (void)testTextInputsStretchInGridAndSelectIgnoresAuthorPadding
{
    NSDictionary *b = [self boundsOf:@"<form style='display:grid;width:300px'><label style='display:grid'>Name <input data-t='input' style='font:14px system-ui;padding:8px;border:1px solid #ccc'></label><select data-t='select' style='font:14px system-ui;padding:8px;border:1px solid #ccc;border-radius:6px'><option>Choose</option></select></form>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"input"].size.width, 300, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"select"].size.height, 22, 1);
}

- (void)testCheckboxSitsTwoPixelsBelowTheBaseline
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0'><label style='display:block;font:16px system-ui;line-height:20px'><input type='radio' data-t='r'>Sydney</label></body>" width:400];
    XCTAssertEqualWithAccuracy(CGRectGetMaxY([self rect:b key:@"r"]), 17.7, 0.8);
}

- (void)testFieldsetSeatsTheLegendInItsBorder
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0;font:16px system-ui'><fieldset data-t='f' style='margin:0;padding:10px 12px;border:1px solid #ddd'><legend data-t='l' style='padding:0 4px'>Question</legend><div data-t='c' style='height:20px'></div></fieldset></body>" width:400];
    CGRect fieldset = [self rect:b key:@"f"];
    CGRect legend   = [self rect:b key:@"l"];
    CGRect content  = [self rect:b key:@"c"];
    XCTAssertEqualWithAccuracy(CGRectGetMinY(legend), CGRectGetMinY(fieldset), 0.6);
    XCTAssertLessThan(legend.size.width, 120);
    XCTAssertEqualWithAccuracy(CGRectGetMinY(content), CGRectGetMaxY(legend) + 10, 0.6);
    XCTAssertEqualWithAccuracy(CGRectGetMaxY(fieldset), CGRectGetMaxY(content) + 11, 0.6);
}

- (void)testInputsPaintNativeShapesAndNoGeneratedCheckGlyph
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<input type='checkbox' checked><input type='range'><select><option>One</option></select>" css:nil];
    CountingPainter painter;
    [document paintWithMaxWidth:400 clip:CGRectMake(0, 0, 400, 400) painter:painter];
    XCTAssertGreaterThanOrEqual(painter.paths, 3);
    for(const auto &t : painter.texts)
        XCTAssertTrue(t.find("\xE2\x9C\x93") == std::string::npos);
}

- (void)testActiveWindowTintsCheckedControlsWithTheAccent
{
    auto paintsRed = [](BOOL active) {
        LHDocument *document = [[LHDocument alloc] initWithHTML:@"<input type='checkbox' checked style='accent-color:#ff0000'>" css:nil];
        document.controlsActive = active;
        CountingPainter painter;
        [document paintWithMaxWidth:200 clip:CGRectMake(0, 0, 200, 200) painter:painter];
        for(const auto &c : painter.colors)
            if(c.red == 255 && c.green == 0 && c.blue == 0)
                return true;
        return false;
    };
    XCTAssertTrue(paintsRed(YES));
    XCTAssertFalse(paintsRed(NO));
}

- (void)testCheckedAndDisabledPseudoClasses
{
    NSDictionary *b = [self boundsOf:@"<style>input:checked + span { display:inline-block; width:50px } button:disabled { width:70px } button:enabled { width:30px }</style><input type='checkbox' checked><span data-t='a'></span><input type='checkbox'><span data-t='b'></span><button disabled data-t='c'></button><button data-t='d'></button>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"a"].size.width, 50, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"b"].size.width, 0, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"c"].size.width, 70, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"d"].size.width, 30, 0.01);
}

- (void)testFlexGapsDoNotStretchItems
{
    NSDictionary *b = [self boundsOf:@"<div style='display:flex;gap:8px;width:380px'><div data-t='a' style='width:38px;height:10px'></div><div style='width:38px'></div><div style='width:38px'></div><div style='width:38px'></div><div style='width:38px'></div><div style='width:38px'></div><div style='width:38px'></div><div style='width:38px'></div></div>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"a"].size.width, 38, 0.01);
}

- (void)testGridCentersItemsInADefiniteHeight
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0'><div style='display:grid;place-items:center;width:40px;height:40px'><span data-t='a' style='display:block;width:10px;height:10px'></span></div><div style='display:grid;place-content:center;width:40px;height:40px'><span data-t='b' style='display:block;width:10px;height:10px'></span></div>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"a"].origin.y, 15, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"a"].origin.x, 15, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"b"].origin.y, 55, 0.6);
    XCTAssertEqualWithAccuracy([self rect:b key:@"b"].origin.x, 15, 0.6);
}

- (void)testDefaultAndMonospaceFontSizes
{
    NSDictionary *b = [self boundsOf:@"<body style='margin:0'><div data-t='a' style='line-height:1'>x</div><pre data-t='b' style='margin:0;line-height:1'>x</pre><div style='font-size:14px'><code data-t='c' style='display:block;line-height:1'>x</code></div></body>" width:400];
    XCTAssertEqualWithAccuracy([self rect:b key:@"a"].size.height, 16, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"b"].size.height, 13, 0.01);
    XCTAssertEqualWithAccuracy([self rect:b key:@"c"].size.height, 14, 0.01);
}

- (void)testBackfaceHiddenSvgDiffersFromTheVisibleOne
{
    NSMutableString *rects = [NSMutableString string];
    for(int i = 0; i < 200; i++)
        [rects appendFormat:@"<rect x='%d' y='%d' width='1' height='1'/>", i % 20, i / 20];
    NSString *html = [NSString stringWithFormat:@"<div style='transform:rotateY(180deg);backface-visibility:hidden;width:100px;height:100px'><svg viewBox='0 0 20 20' width='100' height='100'>%@</svg></div>", rects];
    LHDocument *hidden  = [[LHDocument alloc] initWithHTML:html css:nil];
    LHDocument *visible = [[LHDocument alloc] initWithHTML:[html stringByReplacingOccurrencesOfString:@"rotateY(180deg)" withString:@"rotateY(0deg)"] css:nil];
    LHDisplayList *a = [hidden displayListWithMaxWidth:200];
    LHDisplayList *b = [visible displayListWithMaxWidth:200];
    XCTAssertEqual([a changedRectsSince:b].count > 0, YES);
}

@end
