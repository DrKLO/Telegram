#import <XCTest/XCTest.h>
#import <AppKit/AppKit.h>
#import "LHDocument.h"

@interface CollapsedTableBorderTests : XCTestCase
@end

@implementation CollapsedTableBorderTests

- (LHDocument *)page:(NSString *)html previous:(LHDocument *)previous
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    return document;
}

- (NSBitmapImageRep *)pixels:(LHDocument *)document scale:(CGFloat)scale
{
    CGImageRef image = [document copyImageWithMaxWidth:420 rect:CGRectMake(0, 0, 420, 360) scale:scale];
    XCTAssertNotEqual(image, nullptr);
    if(!image) return nil;
    NSBitmapImageRep *result = [[NSBitmapImageRep alloc] initWithCGImage:image];
    CGImageRelease(image);
    return result;
}

- (void)assertBorders:(NSBitmapImageRep *)actual match:(NSBitmapImageRep *)reference
{
    NSUInteger missing = 0;
    NSMutableArray<NSString *> *samples = [NSMutableArray array];
    for(NSInteger y = 0; y < reference.pixelsHigh; y++)
        for(NSInteger x = 0; x < reference.pixelsWide; x++)
        {
            if(reference.bitsPerSample == 8 && reference.samplesPerPixel >= 3)
            {
                NSUInteger channels[5] = {};
                [reference getPixel:channels atX:x y:y];
                if(channels[0] > 180 && channels[1] > 180 && channels[2] > 180) continue;
            }
            NSColor *expected = [[reference colorAtX:x y:y] colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
            if(expected.alphaComponent < .99 || expected.redComponent > .7 || expected.greenComponent > .7 || expected.blueComponent > .7) continue;
            NSColor *pixel = [[actual colorAtX:x y:y] colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
            if(pixel.redComponent - expected.redComponent > .02 || pixel.greenComponent - expected.greenComponent > .02 || pixel.blueComponent - expected.blueComponent > .02)
            {
                missing++;
                if(samples.count < 8) [samples addObject:[NSString stringWithFormat:@"%ld,%ld: %@ instead of %@", x, y, pixel, expected]];
            }
        }
    XCTAssertEqual(missing, 0u, @"%@", samples);
}

- (NSString *)sudoku:(BOOL)selected
{
    NSMutableString *html = [NSMutableString stringWithString:@"<style>body{margin:4px;background:white}table{border-collapse:collapse;table-layout:fixed;width:360px;border:3px solid #111}td{height:30px;padding:0;border:1px solid #999}td:nth-child(3n){border-right:3px solid #111}tr:nth-child(3n) td{border-bottom:3px solid #111}</style><table>"];
    for(int row = 0; row < 9; row++)
    {
        [html appendString:@"<tr>"];
        for(int col = 0; col < 9; col++)
        {
            BOOL highlight = selected && (row == 3 || col == 2 || (row >= 3 && row < 6 && col < 3));
            [html appendFormat:@"<td id='c%d' style='background-color:%@'></td>", row * 9 + col, highlight ? @"#c4dcff" : @"transparent"];
        }
        [html appendString:@"</tr>"];
    }
    [html appendString:@"</table>"];
    return html;
}

- (void)testSudokuHighlightsPreserveThinAndThickGridLines
{
    for(NSNumber *scale in @[@1, @2])
    {
        LHDocument *plain = [self page:[self sudoku:NO] previous:nil];
        NSBitmapImageRep *reference = [self pixels:plain scale:scale.doubleValue];
        LHDocument *selected = [self page:[self sudoku:YES] previous:plain];
        XCTAssertTrue(selected.reusedPrevious);
        [self assertBorders:[self pixels:selected scale:scale.doubleValue] match:reference];
        [self assertBorders:[self pixels:[self page:[self sudoku:YES] previous:nil] scale:scale.doubleValue] match:reference];
    }
}

- (NSString *)revenue:(BOOL)selected
{
    NSMutableString *html = [NSMutableString stringWithString:@"<style>body{margin:4px;background:white}table{border-collapse:collapse;width:390px}td,th{height:24px;padding:0;border:1px solid #999}th{background:#e8f2fc}</style><table><thead><tr><th rowspan=2></th><th colspan=2></th><th colspan=2></th></tr><tr><th></th><th></th><th></th><th></th></tr></thead><tbody>"];
    for(int row = 0; row < 5; row++)
    {
        [html appendString:@"<tr>"];
        for(int col = 0; col < 5; col++)
            [html appendFormat:@"<td id='r%d' style='background-color:%@'></td>", row * 5 + col, selected && (row == 2 || col == 2) ? @"#c4dcff" : @"transparent"];
        [html appendString:@"</tr>"];
    }
    [html appendString:@"</tbody></table>"];
    return html;
}

- (void)testSelectedRevenueColumnPreservesSharedBordersAndSpanningHeaders
{
    for(NSNumber *scale in @[@1, @2])
    {
        LHDocument *plain = [self page:[self revenue:NO] previous:nil];
        NSBitmapImageRep *reference = [self pixels:plain scale:scale.doubleValue];
        LHDocument *selected = [self page:[self revenue:YES] previous:plain];
        XCTAssertTrue(selected.reusedPrevious);
        [self assertBorders:[self pixels:selected scale:scale.doubleValue] match:reference];
        [self assertBorders:[self pixels:[self page:[self revenue:YES] previous:nil] scale:scale.doubleValue] match:reference];
    }
}

@end
