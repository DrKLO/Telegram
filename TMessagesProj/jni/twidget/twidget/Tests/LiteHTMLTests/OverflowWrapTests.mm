#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHDocumentPainting.h"

#include <string>
#include <vector>

@interface OverflowWrapTests : XCTestCase
@end

namespace
{
    struct WrappedText : lhx::Painter
    {
        std::vector<std::pair<std::string, litehtml::position>> runs;
        void text(const char *text, const lhx::TextStyle&, const litehtml::web_color&, const litehtml::position& box) override
        {
            runs.emplace_back(text, box);
        }
        void solidFill(const litehtml::background_layer&, const litehtml::web_color&) override {}
        void path(const litehtml::vector_path&) override {}
    };

    NSString *Word = @"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    LHDocument *Make(NSString *body)
    {
        LHDocument *document = [[LHDocument alloc] initWithHTML:[@"<!doctype html><style>body{margin:0;font:16px/24px Courier}textarea{font:inherit;padding:0;border:0;width:200px;resize:none}</style>" stringByAppendingString:body] css:nil];
        [document layoutWithMaxWidth:300 preferredWidth:300];
        return document;
    }

    NSData *Pixels(LHDocument *document)
    {
        CGImageRef image = [document copyImageWithMaxWidth:300 rect:CGRectMake(0, 0, 300, 400) scale:2];
        NSData *data = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
        CGImageRelease(image);
        return data;
    }
}

@implementation OverflowWrapTests

- (void)testTextareaWrapsAnUnbrokenWordInLayoutAndPaint
{
    LHDocument *document = Make([NSString stringWithFormat:@"<textarea id='field' rows='1'>%@</textarea>", Word]);
    NSDictionary *metrics = [document textareaMetricsWithAttribute:@"id" value:@"field"];
    XCTAssertEqual([metrics[@"scrollWidth"] intValue], 200);
    XCTAssertEqual([metrics[@"scrollHeight"] intValue], 72);
    WrappedText painter;
    [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 200) painter:painter];
    XCTAssertGreaterThan(painter.runs.size(), 1u);
    XCTAssertLessThanOrEqual(painter.runs.front().second.width.value(), 200);
}

- (void)testOverflowWrapPaintsAllFragmentsAtTheirLinePositions
{
    LHDocument *document = Make([NSString stringWithFormat:@"<div id='field' style='width:200px;overflow-wrap:break-word'>%@</div>", Word]);
    CGRect box = [[document boundsByAttribute:@"id"][@"field"] rectValue];
    XCTAssertEqual(box.size.height, 72);
    WrappedText painter;
    [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 200) painter:painter];
    std::string joined;
    for(const auto& run : painter.runs)
    {
        joined += run.first;
        XCTAssertLessThanOrEqual(run.second.right().value(), 200);
    }
    XCTAssertEqual(joined, std::string(Word.UTF8String));
    XCTAssertEqual(painter.runs.size(), 3u);
}

- (void)testWrappingMatchesExplicitLineBreaks
{
    NSString *style = @"width:200px;white-space:pre-wrap;overflow-wrap:break-word;color:#b02060;text-shadow:1px 2px #acd";
    LHDocument *wrapped = Make([NSString stringWithFormat:@"<div style='%@'>%@</div>", style, Word]);
    NSString *lines = [NSString stringWithFormat:@"%@\n%@\n%@", [Word substringToIndex:20], [Word substringWithRange:NSMakeRange(20, 20)], [Word substringFromIndex:40]];
    LHDocument *lineBreaks = Make([NSString stringWithFormat:@"<div style='%@'>%@</div>", style, lines]);
    XCTAssertEqualObjects(Pixels(wrapped), Pixels(lineBreaks));
}

- (void)testWhitespaceCanDisableEmergencyWrapping
{
    for(NSString *whitespace in @[@"nowrap", @"pre"])
    {
        LHDocument *document = Make([NSString stringWithFormat:@"<div id='field' style='width:200px;overflow-wrap:anywhere;white-space:%@'>%@</div>", whitespace, Word]);
        XCTAssertEqual([[document boundsByAttribute:@"id"][@"field"] rectValue].size.height, 24);
    }
}

- (void)testWordWrapAliasIsInheritedAndCanBeOverridden
{
    for(NSString *property in @[@"word-wrap", @"overflow-wrap"])
    {
        LHDocument *document = Make([NSString stringWithFormat:@"<section style='%@:break-word'><div id='wrapped' style='width:200px'>%@</div><div id='normal' style='width:200px;overflow-wrap:normal'>%@</div></section>", property, Word, Word]);
        NSDictionary *bounds = [document boundsByAttribute:@"id"];
        XCTAssertEqual([bounds[@"wrapped"] rectValue].size.height, 72);
        XCTAssertEqual([bounds[@"normal"] rectValue].size.height, 24);
    }
}

- (void)testAnywhereChangesTheAutomaticMinimumButBreakWordDoesNot
{
    for(NSString *wrapping in @[@"anywhere", @"break-word"])
    {
        LHDocument *document = Make([NSString stringWithFormat:@"<div style='display:flex;width:100px'><div id='field' style='overflow-wrap:%@'>%@</div></div>", wrapping, Word]);
        CGRect box = [[document boundsByAttribute:@"id"][@"field"] rectValue];
        if([wrapping isEqualToString:@"anywhere"])
        {
            XCTAssertEqual(box.size.width, 100);
            XCTAssertGreaterThan(box.size.height, 24);
        } else
            XCTAssertGreaterThan(box.size.width, 400);
    }
}

- (void)testUnicodeBreaksPreserveComposedCharacters
{
    NSString *text = @"ééé👩‍👩‍👧‍👦👩‍👩‍👧‍👦🇦🇪🇦🇪किंकर्तव्यविमूढ़";
    for(NSNumber *width in @[@1, @55])
    {
        LHDocument *document = Make([NSString stringWithFormat:@"<div style='width:%@px;overflow-wrap:anywhere'>%@</div>", width, text]);
        WrappedText painter;
        [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 3000) painter:painter];
        NSUInteger end = 0;
        NSMutableString *joined = [NSMutableString string];
        for(const auto& run : painter.runs)
        {
            NSString *part = [NSString stringWithUTF8String:run.first.c_str()];
            XCTAssertNotNil(part);
            [joined appendString:part];
            end += part.length;
            if(end < text.length)
                XCTAssertEqual([text rangeOfComposedCharacterSequenceAtIndex:end].location, end);
        }
        XCTAssertEqualObjects(joined, text);
        XCTAssertGreaterThan(painter.runs.size(), 1u);
    }
}

- (void)testPatchedTextAndWidthMatchFreshLayoutAndPaint
{
    LHDocument *previous = nil;
    for(NSString *width in @[@"200", @"80", @"240", @"120"])
    {
        NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;font:16px/24px Courier}#field{overflow-wrap:break-word}</style><div id='field' style='width:%@px'>%@%@</div><div>Below</div>", width, Word, width];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:html css:nil];
        XCTAssertEqualObjects(Pixels(patched), Pixels(fresh));
        if(previous)
        {
            XCTAssertTrue(patched.reusedPrevious);
            LHDocument *restored = [[LHDocument alloc] initWithHTML:previous.html css:nil];
            XCTAssertEqualObjects(Pixels(previous), Pixels(restored));
        }
        previous = patched;
    }
}

- (void)testWrappingUsesTheWidthBesideFloats
{
    LHDocument *document = Make([NSString stringWithFormat:@"<div style='width:200px;overflow-wrap:break-word'><div style='float:left;width:80px;height:60px'></div>%@</div>", Word]);
    WrappedText painter;
    [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertEqual(painter.runs.size(), 4u);
    XCTAssertEqual(painter.runs.front().first, std::string("ABCDEFGHIJKL"));
    XCTAssertEqual(painter.runs.front().second.x.value(), 80);
    XCTAssertLessThan(painter.runs.front().second.y.value(), 24);
    XCTAssertEqual(painter.runs.back().second.x.value(), 0);
}

- (void)testIndentCanTriggerWrappingOfAnOtherwiseFittingWord
{
    LHDocument *document = Make(@"<div style='width:200px;text-indent:100px;overflow-wrap:break-word'>ABCDEFGHIJKLMNOP</div>");
    WrappedText painter;
    [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertEqual(painter.runs.size(), 2u);
    XCTAssertEqual(painter.runs.front().first, std::string("ABCDEFGHIJ"));
    XCTAssertEqual(painter.runs.front().second.x.value(), 100);
    XCTAssertEqual(painter.runs.back().second.x.value(), 0);
}

- (void)testShortWordWrapsBesideAFloatWithoutWaitingForItsBottom
{
    LHDocument *document = Make(@"<div id='field' style='width:200px;overflow-wrap:break-word'><div style='float:left;width:80px;height:60px'></div>ABCDEFGHIJKLMNOP</div>");
    WrappedText painter;
    [document paintWithMaxWidth:300 clip:CGRectMake(0, 0, 300, 300) painter:painter];
    XCTAssertEqual(painter.runs.size(), 2u);
    XCTAssertEqual(painter.runs.front().first, std::string("ABCDEFGHIJKL"));
    XCTAssertEqual(painter.runs.back().second.x.value(), 80);
    XCTAssertEqual([[document boundsByAttribute:@"id"][@"field"] rectValue].size.height, 48);
}

- (void)testPaddedFlexMinimumTracksFontAndTextChanges
{
    LHDocument *previous = nil;
    for(NSNumber *font in @[@16, @24, @12, @16])
    {
        NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;font:%@px/24px Courier}.row{display:flex;width:100px}.text{padding:8px;border:1px solid red;overflow-wrap:anywhere}</style><div class='row'><div class='text' id='field'>%@%@</div></div>", font, Word, font];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:html css:nil];
        XCTAssertEqualObjects(Pixels(patched), Pixels(fresh));
        XCTAssertEqual([[patched boundsByAttribute:@"id"][@"field"] rectValue].size.width, 100);
        previous = patched;
    }
}

- (void)testTextareaHasScrollableContentIncludingTrailingEmptyLines
{
    LHDocument *document = Make(@"<textarea id='field' style='height:48px;padding:10px'>one\ntwo\nthree\nfour\n</textarea>");
    NSDictionary *info = [document scrollersByAttribute:@"id"][@"field"];
    XCTAssertNotNil(info);
    XCTAssertEqual([info[@"content"] sizeValue].height, 140);
    XCTAssertEqual([info[@"viewport"] rectValue].size.height, 68);
    CGPoint offset = [document setScrollOffset:CGPointMake(0, 1000) attribute:@"id" value:@"field"];
    XCTAssertEqual(offset.y, 72);
}

@end
