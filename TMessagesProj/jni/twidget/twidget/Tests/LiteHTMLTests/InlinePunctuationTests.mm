#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHDocumentPainting.h"

@interface InlinePunctuationTests : XCTestCase
@end

namespace
{
    struct InlineTextRuns : lhx::Painter
    {
        std::vector<std::pair<std::string, litehtml::position>> runs;
        void text(const char *text, const lhx::TextStyle&, const litehtml::web_color&, const litehtml::position& box) override
        {
            runs.emplace_back(text, box);
        }
        void solidFill(const litehtml::background_layer&, const litehtml::web_color&) override {}
        void path(const litehtml::vector_path&) override {}
    };
}

@implementation InlinePunctuationTests

- (void)testClosingPunctuationStaysWithThePaddedInlineWord
{
    for(NSString *punctuation in @[@",", @";", @".", @":", @"!", @"?", @")", @"]", @"}"])
    {
        NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;font:16px/24px Courier}code{font:inherit;padding:0 4px}</style><div style='width:90px'>xxx <b><code>word</code></b>%@ next</div>", punctuation];
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        InlineTextRuns painter;
        [document paintWithMaxWidth:90 clip:CGRectMake(0, 0, 90, 200) painter:painter];
        CGFloat first = -1, word = -1, mark = -1;
        for(const auto& run : painter.runs)
        {
            if(run.first == "xxx") first = run.second.y.value();
            if(run.first == "word") word = run.second.y.value();
            if(run.first == punctuation.UTF8String) mark = run.second.y.value();
        }
        XCTAssertGreaterThan(word, first, @"%@", punctuation);
        XCTAssertEqual(word, mark, @"%@", punctuation);
    }
}

- (void)testWhitespaceAndHyphensStillAllowWrapping
{
    for(NSString *content in @[@"<code>word</code> next", @"<code>well-known</code>"])
    {
        NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;font:16px/24px Courier}code{font:inherit;padding:0 4px}</style><div style='width:100px'>xxx %@</div>", content];
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        InlineTextRuns painter;
        [document paintWithMaxWidth:100 clip:CGRectMake(0, 0, 100, 200) painter:painter];
        CGFloat first = -1, word = -1;
        for(const auto& run : painter.runs)
        {
            if(run.first == "xxx") first = run.second.y.value();
            if(run.first == "word" || run.first == "well-") word = run.second.y.value();
        }
        XCTAssertGreaterThanOrEqual(first, 0);
        XCTAssertEqual(word, first, @"%@", content);
    }
}

@end
