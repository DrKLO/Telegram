#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface FontCacheTests : XCTestCase
@end

@implementation FontCacheTests

- (NSData *)renderShadow:(NSString *)shadow
{
    NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;background:white;font:24px -apple-system;color:black}p{margin:10px;text-shadow:%@}</style><p>Shadow cache</p>", shadow];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:@""];
    [document layoutWithMaxWidth:300 preferredWidth:300];
    CGImageRef image = [document copyImageWithMaxWidth:300 rect:CGRectMake(0, 0, 300, 80) scale:2];
    XCTAssertNotEqual(image, nullptr);
    if(!image)
        return [NSData data];
    NSData *pixels = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return pixels;
}

- (void)testTransparentLeadingShadowPreservesVisibleFollowingShadow
{
    NSData *visible = [self renderShadow:@"3px 4px 0 #ff0000"];
    NSData *leadingTransparent = [self renderShadow:@"0 0 0 transparent, 3px 4px 0 #ff0000"];
    XCTAssertTrue([visible isEqualToData:leadingTransparent]);
    XCTAssertFalse([visible isEqualToData:[self renderShadow:@"none"]]);
}

- (void)testChangingShadowAfterTransparentLayerInvalidatesPaint
{
    NSString *sheet = @"body{margin:0;font:24px -apple-system}p{margin:10px}";
    LHDocument *before = [[LHDocument alloc] initWithHTML:@"<p style='text-shadow:0 0 0 transparent,3px 4px 0 red'>Shadow cache</p>" css:sheet];
    LHDocument *after = [[LHDocument alloc] initWithHTML:@"<p style='text-shadow:0 0 0 transparent,3px 4px 0 blue'>Shadow cache</p>" css:sheet];
    [before layoutWithMaxWidth:300 preferredWidth:300];
    [after layoutWithMaxWidth:300 preferredWidth:300];
    LHDisplayList *first = [before displayListWithMaxWidth:300];
    LHDisplayList *second = [after displayListWithMaxWidth:300];
    XCTAssertGreaterThan([second changedRectsSince:first].count, 0u);
}

@end
