#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <initializer_list>

@interface BackgroundStylePatchTests : XCTestCase
@end

@implementation BackgroundStylePatchTests

static LHDocument *BackgroundPage(NSString *html)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:nil];
    [document layoutWithMaxWidth:340 preferredWidth:340];
    return document;
}

static NSData *BackgroundPixels(LHDocument *document, CGFloat scale)
{
    [document layoutWithMaxWidth:340 preferredWidth:340];
    CGImageRef image = [document copyImageWithMaxWidth:340 rect:CGRectMake(0, 0, 340, 420) scale:scale];
    if(!image)
        return nil;
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

- (void)testBackgroundColorSequenceMatchesFreshStyles
{
    NSArray<NSString *> *values = @[@"background-color:#34c759", @"-lh-anim:1;background-color:rgba(255,149,0,.43)", @"background-color:currentColor", @"background-color:var(--tint)", @"background-color:inherit", @"background-color:transparent", @"background-color:invalid", @"background-color:#007aff!important", @"background-color:#f00;background-color:#00f", @"background-color:#00f;background-color:#f00", @"", @"background-color:#af52de"];
    for(NSString *child in @[@"background-color:inherit", @"background:inherit", @"background-color:var(--child)", @"background-color:currentColor", @"background-color:#ffcc00", @""])
        for(CGFloat scale : {1.0, 1.5, 2.0})
        {
            NSString *(^page)(NSString *) = ^NSString *(NSString *value) {
                return [NSString stringWithFormat:@"<style>body{margin:0;background:#202020;color:#f5f5f5;--tint:#32ade6;--child:inherit}.box{padding:12px;background:linear-gradient(90deg,#fff2,#0002) #af52de;border-radius:8px}.child{padding:4px;%@}.outside{position:relative;left:240px}</style><div class=box data-lh-id=\"1\" style=\"%@\"><div class=child>1 <b>2</b><div class=child>3</div></div><div class=\"child outside\">4</div></div>", child, value];
            };
            LHDocument *previous = BackgroundPage(page(values.lastObject));
            for(NSString *value in values)
            {
                LHDocument *patched = [[LHDocument alloc] initWithPrevious:previous styles:@{@"1":value} attribute:@"data-lh-id" css:nil];
                XCTAssertNotNil(patched);
                if(!patched)
                    return;
                XCTAssertEqualObjects(BackgroundPixels(patched, scale), BackgroundPixels(BackgroundPage(page(value)), scale), @"%@ %@ scale=%g", child, value, scale);
                previous = patched;
            }
        }
}

- (void)testMixedParentAndDescendantChangesMatchFreshStyles
{
    NSString *(^page)(NSString *, NSString *, NSString *) = ^NSString *(NSString *parent, NSString *child, NSString *text) {
        return [NSString stringWithFormat:@"<style>body{margin:0}.box{padding:8px}.child{padding:4px}</style><div class=box data-lh-id=\"1\" style=\"%@\"><div class=child data-lh-id=\"2\" style=\"%@\">%@</div></div>", parent, child, text];
    };
    for(NSString *child in @[@"background-color:inherit;font-size:23px;color:blue", @"background-color:currentColor;color:green", @"background-color:inherit", @"background-color:var(--new);--new:#ae5"])
    {
        NSString *html = page(@"background-color:#333", @"color:red", @"before");
        NSString *expected = page(@"background-color:#fb4", child, @"after after");
        LHDocument *patched = [[LHDocument alloc] initWithHTML:expected css:nil previous:BackgroundPage(html)];
        XCTAssertTrue(patched.reusedPrevious);
        XCTAssertEqualObjects(BackgroundPixels(patched, 2), BackgroundPixels(BackgroundPage(expected), 2));
    }
}

- (void)testAnimatedTableBackgroundsPreserveTextAndCollapsedBorders
{
    NSString *(^page)(int) = ^NSString *(int step) {
        NSMutableString *html = [NSMutableString stringWithString:@"<style>body{margin:0;background:#1d1d1d;color:white}table{border-collapse:collapse;width:330px;table-layout:fixed}td{border:1px solid #777;width:34px;height:34px;text-align:center}td:nth-child(3n){border-right:2px solid #fff}.h{font:18px monospace;transform:scale(1.03)}</style><table>"];
        for(int row = 0; row < 9; row++)
        {
            [html appendString:@"<tr>"];
            for(int col = 0; col < 9; col++)
                [html appendFormat:@"<td data-lh-id=\"%d\" style=\"-lh-anim:1;background-color:rgba(52,199,89,%g)\"><div class=h>%d</div></td>", row * 9 + col + 1, ((step + row + col) % 13) / 16.0, (row + col) % 9 + 1];
            [html appendString:@"</tr>"];
        }
        [html appendString:@"</table>"];
        return html;
    };
    LHDocument *previous = BackgroundPage(page(0));
    for(int step = 1; step <= 26; step++)
    {
        LHDocument *patched = [[LHDocument alloc] initWithHTML:page(step) css:nil previous:previous];
        XCTAssertTrue(patched.reusedPrevious);
        XCTAssertEqualObjects(BackgroundPixels(patched, 2), BackgroundPixels(BackgroundPage(page(step)), 2), @"step=%d", step);
        previous = patched;
    }
}

@end
