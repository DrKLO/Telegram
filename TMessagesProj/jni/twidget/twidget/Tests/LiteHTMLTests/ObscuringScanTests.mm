#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <algorithm>
#include <initializer_list>

@interface LHDocument (ObscuringCache)
- (void)setObscuringCacheDisabled:(BOOL)disabled;
@end

@interface ObscuringScanTests : XCTestCase
@end

@implementation ObscuringScanTests

static uint64_t Mix(uint64_t h, NSString *s)
{
    for(NSUInteger i = 0; i < s.length; i++)
        h = (h ^ [s characterAtIndex:i]) * 1099511628211ULL;
    return (h ^ 0xff) * 1099511628211ULL;
}

static NSString *Html(uint32_t &seed, int count)
{
    auto next = [&seed](uint32_t mod) { seed = seed * 1664525u + 1013904223u; return (seed >> 8) % mod; };
    NSMutableString *html = [NSMutableString stringWithString:@"<html><body style=\"margin:0\">"];
    int open = 0;
    for(int i = 1; i <= count; i++)
    {
        NSString *extra = @"";
        switch(next(8))
        {
        case 0: extra = @"opacity:0;"; break;
        case 1: extra = @"visibility:hidden;"; break;
        case 2: extra = @"overflow:hidden;"; break;
        default: break;
        }
        [html appendFormat:@"<div data-lh-id=\"%d\" style=\"position:absolute;left:%upx;top:%upx;width:%upx;height:%upx;background:#%06x;%@\">", i, next(200), next(200), 20 + next(80), 20 + next(80), next(0xffffff), extra];
        if(next(3) == 0)
            [html appendString:@"<span>text</span>"];
        if(next(6) == 0 && open < 2)
            open++;
        else
            [html appendString:@"</div>"];
        if(open > 0 && next(4) == 0)
        {
            [html appendString:@"</div>"];
            open--;
        }
    }
    while(open-- > 0)
        [html appendString:@"</div>"];
    [html appendString:@"</body></html>"];
    return html;
}

static uint64_t Signature(BOOL disabled)
{
    uint64_t h = 1469598103934665603ULL;
    uint32_t seed = 7;
    for(int doc = 0; doc < 12; doc++)
    {
        const int count = 40 + doc * 5;
        LHDocument *document = [[LHDocument alloc] initWithHTML:Html(seed, count) css:nil];
        [document layoutWithMaxWidth:400 preferredWidth:400];
        [document setObscuringCacheDisabled:disabled];
        NSMutableSet<NSString *> *mutating = [NSMutableSet set];
        NSSet<NSString *> *fixed = [NSSet setWithObjects:@"3", @"5", @"8", @"13", @"21", nil];
        for(int i = 1; i <= count; i++)
        {
            NSString *value = [NSString stringWithFormat:@"%d", i];
            for(CGFloat margin : {0.0, 4.0, 40.0, 200.0})
            {
                CGRect rect = CGRectMake((i * 37) % 300 - margin, (i * 53) % 300 - margin, 40 + 2 * margin, 30 + 2 * margin);
                for(NSSet<NSString *> *ignoring : @[[NSSet set], fixed, mutating])
                {
                    for(BOOL clipped : {NO, YES})
                    {
                        NSArray<NSString *> *found = [document elementsObscuringAttribute:@"data-lh-id" value:value inRect:rect ignoring:ignoring clipped:clipped];
                        h = Mix(h, found ? [found componentsJoinedByString:@","] : @"nil");
                        BOOL unobscured = [document isElementWithAttribute:@"data-lh-id" value:value unobscuredInRect:rect ignoring:ignoring clipped:clipped];
                        h = Mix(h, unobscured ? @"y" : @"n");
                    }
                }
            }
            [mutating addObject:[NSString stringWithFormat:@"%d", (i * 7) % count + 1]];
        }
    }
    return h;
}

- (void)testObscuringResultsMatchReference
{
    XCTAssertEqual(Signature(NO), 14463270941434942152ULL);
    XCTAssertEqual(Signature(YES), 14463270941434942152ULL);
}

static NSString *ScrollHtml(uint32_t &seed, int count, int &scrollers)
{
    auto next = [&seed](uint32_t mod) { seed = seed * 1664525u + 1013904223u; return (seed >> 8) % mod; };
    NSMutableString *html = [NSMutableString stringWithString:@"<html><head><style>.h:hover{opacity:0}.v:hover{visibility:hidden}.p:hover{left:30px}</style></head><body style=\"margin:0\">"];
    int open = 0;
    scrollers = 0;
    for(int i = 1; i <= count; i++)
    {
        NSString *extra = @"";
        NSString *cls = @"";
        bool scroller = false;
        switch(next(14))
        {
        case 0: extra = @"opacity:0;"; break;
        case 1: extra = @"visibility:hidden;"; break;
        case 2: extra = @"overflow:hidden;"; break;
        case 3: case 4: extra = @"overflow:auto;"; scroller = true; break;
        case 5: extra = [NSString stringWithFormat:@"position:sticky;top:%upx;", next(30)]; break;
        case 6: extra = @"position:fixed;"; break;
        case 7: extra = @"opacity:0.5;"; break;
        default: break;
        }
        switch(next(6))
        {
        case 0: cls = @"h"; break;
        case 1: cls = @"v"; break;
        case 2: cls = @"p"; break;
        default: break;
        }
        NSString *pos = [extra hasPrefix:@"position"] ? @"" : (next(3) == 0 ? @"position:relative;" : @"position:absolute;");
        [html appendFormat:@"<div data-lh-id=\"%d\" class=\"%@\" style=\"%@left:%upx;top:%upx;width:%upx;height:%upx;background:#%06x;%@\">", i, cls, pos, next(200), next(200), 20 + next(120), 20 + next(120), next(0xffffff), extra];
        if(scroller)
        {
            scrollers = std::max(scrollers, i);
            [html appendFormat:@"<div style=\"width:%upx;height:%upx\"></div>", 200 + next(400), 200 + next(400)];
        }
        if(next(3) == 0)
            [html appendString:@"<span>text</span>"];
        if(next(4) == 0 && open < 4)
            open++;
        else
            [html appendString:@"</div>"];
        if(open > 0 && next(4) == 0)
        {
            [html appendString:@"</div>"];
            open--;
        }
    }
    while(open-- > 0)
        [html appendString:@"</div>"];
    [html appendString:@"</body></html>"];
    return html;
}

static bool SameResult(NSArray<NSString *> *a, NSArray<NSString *> *b)
{
    return (a == nil && b == nil) || (a != nil && b != nil && [a isEqualToArray:b]);
}

- (void)testCachedMatchesUncachedAcrossScrollAndRestyle
{
    uint32_t seed = 97;
    NSUInteger compared = 0, nonEmpty = 0, mismatches = 0, scrolled = 0;
    for(int round = 0; round < 24; round++)
    {
        int scrollers = 0;
        const int count = 30 + (round % 6) * 12;
        NSString *html = ScrollHtml(seed, count, scrollers);
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:nil viewportSize:CGSizeMake(400, 300)];
        [document layoutWithMaxWidth:400 preferredWidth:400];
        auto next = [&seed](uint32_t mod) { seed = seed * 1664525u + 1013904223u; return (seed >> 8) % mod; };
        NSMutableSet<NSString *> *mutating = [NSMutableSet set];
        NSSet<NSString *> *fixed = [NSSet setWithObjects:@"2", @"5", @"9", @"14", nil];
        for(int step = 0; step < 160; step++)
        {
            switch(next(10))
            {
            case 0: case 1: case 2:
                for(int i = 1; i <= count; i++)
                {
                    CGPoint applied = [document setScrollOffset:CGPointMake(next(300), next(300)) attribute:@"data-lh-id" value:[NSString stringWithFormat:@"%d", i]];
                    scrolled += applied.x > 0 || applied.y > 0;
                }
                break;
            case 3:
                [document setScrollOffset:CGPointMake(next(300), next(300)) attribute:@"data-lh-id" value:[NSString stringWithFormat:@"%u", 1 + next(count)]];
                break;
            case 4:
                [document mouseMoved:CGPointMake(next(400), next(400))];
                break;
            case 5:
                [mutating addObject:[NSString stringWithFormat:@"%u", 1 + next(count)]];
                break;
            case 6:
                if(next(4) == 0)
                {
                    LHDocument *updated = [[LHDocument alloc] initWithPrevious:document styles:@{[NSString stringWithFormat:@"%u", 1 + next(count)]: next(2) ? @"opacity:0" : @"visibility:hidden"} attribute:@"data-lh-id" css:nil];
                    if(updated)
                    {
                        document = updated;
                        [document layoutWithMaxWidth:400 preferredWidth:400];
                    }
                }
                break;
            default:
                break;
            }
            for(int q = 0; q < 6; q++)
            {
                NSString *value = [NSString stringWithFormat:@"%u", 1 + next(count)];
                const CGFloat margin = next(3) == 0 ? 200 : next(40);
                CGRect rect = CGRectMake(next(400) - margin, next(400) - margin, 10 + next(80) + 2 * margin, 10 + next(80) + 2 * margin);
                NSSet<NSString *> *ignoring = @[[NSSet set], fixed, mutating][next(3)];
                BOOL clipped = next(2);
                NSArray<NSString *> *cached = [document elementsObscuringAttribute:@"data-lh-id" value:value inRect:rect ignoring:ignoring clipped:clipped];
                [document setObscuringCacheDisabled:YES];
                NSArray<NSString *> *reference = [document elementsObscuringAttribute:@"data-lh-id" value:value inRect:rect ignoring:ignoring clipped:clipped];
                [document setObscuringCacheDisabled:NO];
                BOOL unobscured = [document isElementWithAttribute:@"data-lh-id" value:value unobscuredInRect:rect ignoringSet:ignoring clipped:clipped];
                if(!SameResult(cached, reference) || unobscured != (reference != nil && reference.count == 0))
                    mismatches++;
                compared++;
                nonEmpty += reference.count > 0;
            }
        }
    }
    XCTAssertEqual(mismatches, 0u);
    XCTAssertGreaterThan(nonEmpty, 100u);
    XCTAssertGreaterThan(scrolled, 100u);
    XCTAssertGreaterThan(compared, 20000u);
}

- (void)testIgnoringSetVariantMatches
{
    uint32_t seed = 11;
    LHDocument *document = [[LHDocument alloc] initWithHTML:Html(seed, 80) css:nil];
    [document layoutWithMaxWidth:400 preferredWidth:400];
    NSMutableSet<NSString *> *mutating = [NSMutableSet set];
    NSSet<NSString *> *fixed = [NSSet setWithObjects:@"2", @"4", @"9", nil];
    NSUInteger nonEmpty = 0, missing = 0;
    for(int i = 1; i <= 80; i++)
    {
        NSString *value = [NSString stringWithFormat:@"%d", i];
        CGRect rect = CGRectMake((i * 37) % 300 - 20, (i * 53) % 300 - 20, 80, 70);
        for(NSSet<NSString *> *ignoring : @[fixed, mutating, fixed])
        {
            NSArray<NSString *> *a = [document elementsObscuringAttribute:@"data-lh-id" value:value inRect:rect ignoring:[ignoring copy] clipped:NO];
            NSArray<NSString *> *b = [document elementsObscuringAttribute:@"data-lh-id" value:value inRect:rect ignoringSet:ignoring clipped:NO];
            XCTAssertEqualObjects(a, b);
            XCTAssertEqual([document isElementWithAttribute:@"data-lh-id" value:value unobscuredInRect:rect ignoringSet:ignoring clipped:NO], a != nil && a.count == 0);
            nonEmpty += a.count > 0;
            missing += a == nil;
        }
        [mutating addObject:[NSString stringWithFormat:@"%d", (i * 7) % 80 + 1]];
    }
    XCTAssertGreaterThan(nonEmpty, 0u);
    XCTAssertLessThan(missing, 240u);
}

- (void)testObscuringCallCostBench
{
    if(!getenv("TWIDGET_BENCH"))
        return;
    for(int count : {150, 1000})
    {
        uint32_t seed = 5;
        int scrollers = 0;
        LHDocument *document = [[LHDocument alloc] initWithHTML:ScrollHtml(seed, count, scrollers) css:nil previous:nil viewportSize:CGSizeMake(400, 300)];
        [document layoutWithMaxWidth:400 preferredWidth:400];
        NSSet<NSString *> *ignoring = [NSSet setWithObjects:@"2", @"5", @"9", nil];
        for(BOOL disabled : {YES, NO})
        {
            for(BOOL scrolling : {NO, YES})
            {
                [document setObscuringCacheDisabled:disabled];
                NSUInteger calls = 0, sink = 0;
                const CFAbsoluteTime start = CFAbsoluteTimeGetCurrent();
                for(int frame = 0; frame < 40; frame++)
                {
                    if(scrolling && scrollers > 0)
                        [document setScrollOffset:CGPointMake(frame, frame * 2) attribute:@"data-lh-id" value:[NSString stringWithFormat:@"%d", scrollers]];
                    for(int i = 1; i <= count; i += std::max(1, count / 60))
                    {
                        NSString *value = [NSString stringWithFormat:@"%d", i];
                        CGRect rect = CGRectMake((i * 37) % 300 - 20, (i * 53) % 300 - 20, 80, 70);
                        sink += [document isElementWithAttribute:@"data-lh-id" value:value unobscuredInRect:rect ignoringSet:ignoring clipped:NO];
                        calls++;
                    }
                }
                const double us = (CFAbsoluteTimeGetCurrent() - start) * 1e6 / calls;
                NSLog(@"OBSCURING n=%d cache=%s scroll=%s calls=%lu per-call=%.2fus sink=%lu", count, disabled ? "off" : "on", scrolling ? "yes" : "no", (unsigned long)calls, us, (unsigned long)sink);
            }
        }
    }
}

@end
