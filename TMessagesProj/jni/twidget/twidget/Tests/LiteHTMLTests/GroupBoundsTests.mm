#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <cmath>

@interface GroupBoundsTests : XCTestCase
@end

@implementation GroupBoundsTests

static NSData *Render(NSString *html, BOOL exact)
{
    [LHDocument setExactGroupBoundsEnabled:exact];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    CGFloat height = MIN(MAX(document.size.height, 1), 3000);
    size_t pw = 840, ph = (size_t)std::ceil(height * 2);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef ctx = CGBitmapContextCreate(nullptr, pw, ph, 8, pw * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, 2, -2);
    for(CGFloat top = 0; top < height; top += 256)
    {
        CGContextSaveGState(ctx);
        CGRect tile = CGRectMake(0, top, 420, MIN(256, height - top));
        CGContextClipToRect(ctx, tile);
        [document drawInContext:ctx origin:CGPointZero clip:tile];
        CGContextRestoreGState(ctx);
    }
    NSData *data = [NSData dataWithBytes:CGBitmapContextGetData(ctx) length:pw * ph * 4];
    CGContextRelease(ctx);
    [LHDocument setExactGroupBoundsEnabled:YES];
    return data;
}

static NSArray<NSString *> *Files(NSArray<NSString *> *dirs)
{
    NSMutableArray<NSString *> *out = [NSMutableArray array];
    for(NSString *dir in dirs)
    {
        NSDirectoryEnumerator *e = [[NSFileManager defaultManager] enumeratorAtPath:dir];
        for(NSString *rel in e)
            if([rel.pathExtension isEqualToString:@"html"])
                [out addObject:[dir stringByAppendingPathComponent:rel]];
    }
    return [out sortedArrayUsingSelector:@selector(compare:)];
}

- (void)testCorpusIsByteIdenticalWithExactGroupBounds
{
    NSMutableArray<NSString *> *dirs = [NSMutableArray arrayWithObject:[[@(__FILE__) stringByDeletingLastPathComponent] stringByAppendingPathComponent:@"../../Examples/chats"]];
    NSString *extra = NSProcessInfo.processInfo.environment[@"GROUP_BOUNDS_CORPUS"];
    if(extra.length)
        [dirs addObjectsFromArray:[extra componentsSeparatedByString:@":"]];
    NSMutableArray<NSString *> *seeds = [NSMutableArray array];
    for(NSString *name in [[NSFileManager defaultManager] contentsOfDirectoryAtPath:dirs[0] error:nil])
        [seeds addObject:[dirs[0] stringByAppendingPathComponent:name]];
    NSArray<NSString *> *files = [seeds arrayByAddingObjectsFromArray:Files([dirs subarrayWithRange:NSMakeRange(1, dirs.count - 1)])];
    NSUInteger differing = 0, checked = 0;
    for(NSString *path in files)
    {
        NSString *html = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
        if(!html.length)
            continue;
        checked++;
        if(![Render(html, NO) isEqualToData:Render(html, YES)])
        {
            differing++;
            NSLog(@"GROUPBOUNDS differs: %@", path.lastPathComponent);
        }
    }
    NSLog(@"GROUPBOUNDS checked %lu files, %lu differ", (unsigned long)checked, (unsigned long)differing);
    XCTAssertGreaterThan(checked, 0u);
    XCTAssertEqual(differing, 0u);
}

- (void)testOverflowingGroupContentIsByteIdentical
{
    NSArray<NSString *> *cases = @[
        @"<div style=\"opacity:.6;width:60px;height:40px;background:#08f\"><div style=\"transform:translate(120px,30px) rotate(20deg) scale(1.8);width:50px;height:30px;background:#f80\">t</div></div><div style=\"height:120px\"></div>",
        @"<div style=\"opacity:.5;overflow:hidden;width:80px;height:40px;background:#fc0\"><div style=\"position:absolute;left:150px;top:90px;width:90px;height:60px;background:#c0f\">abs</div></div><div style=\"position:relative;opacity:.5;width:80px;height:40px\"><div style=\"position:fixed;left:200px;top:10px;width:60px;height:60px;background:#0c8\"></div></div><div style=\"height:160px\"></div>",
        @"<div style=\"opacity:.7;width:80px;height:40px;background:#eee;margin:40px\"><div style=\"filter:drop-shadow(30px 30px 12px #f00) blur(4px);width:60px;height:30px;background:#00f\"></div><div style=\"filter:drop-shadow(-40px 0 0 #0a0)\">shadowed</div></div>",
        @"<div style=\"opacity:.6;width:60px;height:60px;margin:30px\"><svg width=\"60\" height=\"60\" style=\"overflow:visible\"><circle cx=\"30\" cy=\"30\" r=\"70\" fill=\"#f0a\" stroke=\"#000\" stroke-width=\"12\"/></svg></div><div style=\"height:80px\"></div>",
        @"<div style=\"opacity:.6;width:40px;height:30px;margin:20px;font:italic 64px Zapfino,Snell Roundhand,serif\">fjQ</div><div style=\"opacity:.6;width:20px;margin:20px;font:120px Apple Chancery,serif;line-height:10px\">Ğ</div><div style=\"opacity:.6;width:30px;margin:20px;font-size:48px\">ஸ்ரீ \U0001F469‍\U0001F469</div><div style=\"height:80px\"></div>",
    ];
    for(NSString *html in cases)
        XCTAssertEqualObjects(Render(html, NO), Render(html, YES), @"%@", [html substringToIndex:MIN(html.length, (NSUInteger)60)]);
}

@end
