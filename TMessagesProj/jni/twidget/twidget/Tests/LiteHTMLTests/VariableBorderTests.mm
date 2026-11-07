#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface VariableBorderTests : XCTestCase
@end

@implementation VariableBorderTests

- (void)testVariableBorderRespectsDeclarationOrderAndImportance
{
    NSArray<NSArray *> *cases = @[
        @[@"border:none;border-top:1px solid var(--border)", @1],
        @[@"border-top:1px solid var(--border);border:none", @0],
        @[@"border:none!important;border-top:1px solid var(--border)", @0],
        @[@"border-top:1px solid var(--border)!important;border:none", @1],
        @[@"border:none;border-top:1px solid var(--border);border-top-width:3px", @3],
        @[@"border:none;border-top-width:3px;border-top:1px solid var(--border)", @1],
    ];
    for(NSArray *test in cases)
    {
        for(NSNumber *inlineStyle in @[@NO, @YES])
        {
            NSString *html = inlineStyle.boolValue
                ? [NSString stringWithFormat:@"<style>:root{--border:red}body{margin:0}</style><div id='rule' style='%@'></div>", test[0]]
                : [NSString stringWithFormat:@"<style>:root{--border:red}body{margin:0}#rule{%@}</style><div id='rule'></div>", test[0]];
            LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
            [document layoutWithMaxWidth:100 preferredWidth:100];
            XCTAssertEqual([[document boundsByAttribute:@"id"][@"rule"] rectValue].size.height, [test[1] doubleValue], @"%@ inline=%@", test[0], inlineStyle);
        }
    }
}

- (void)testVariableBorderAfterResetKeepsTheRuleVisible
{
    for(NSString *tag in @[@"hr", @"div"])
    {
        for(NSString *color in @[@"red", @"var(--border)"])
        {
            NSString *html = [NSString stringWithFormat:@"<style>:root{--border:red}body{margin:0}#rule{margin:0;border:none;border-top:1px solid %@}</style><%@ id='rule'></%@>", color, tag, tag];
            LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
            [document layoutWithMaxWidth:100 preferredWidth:100];
            CGRect bounds = [[document boundsByAttribute:@"id"][@"rule"] rectValue];
            XCTAssertEqual(bounds.size.height, 1, @"%@ %@", tag, color);
            CGImageRef image = [document copyImageWithMaxWidth:100 rect:CGRectMake(0, 0, 100, 2) scale:1];
            XCTAssertNotEqual(image, nil);
            NSData *data = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
            const uint8_t *bytes = (const uint8_t *)data.bytes;
            XCTAssertNotEqual(bytes[50 * 4 + 3], 0, @"%@ %@", tag, color);
            CGImageRelease(image);
        }
    }
}

@end
