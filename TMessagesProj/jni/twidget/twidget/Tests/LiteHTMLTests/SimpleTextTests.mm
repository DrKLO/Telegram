#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHGPURenderer.h"
#import "LHRenderList.h"
#import <Metal/Metal.h>

#include <random>
#include <set>
#include <string>
#include <vector>

@interface SimpleTextTests : XCTestCase
@end

@implementation SimpleTextTests

static NSArray<NSString *> *Styles()
{
    return @[
        @"font:11px -apple-system",
        @"font:600 15px -apple-system",
        @"font:bold 22px system-ui",
        @"font:13px Helvetica",
        @"font:italic 17px Times",
        @"font:14px Georgia",
        @"font:12px ui-monospace",
        @"font:12px -apple-system;letter-spacing:.08em",
        @"font:16px Avenir Next",
        @"font:300 19px -apple-system;font-variant-numeric:tabular-nums",
        @"font:14px -apple-system;word-spacing:7px;letter-spacing:.5px",
    ];
}

static std::vector<std::string> Words(std::mt19937 &random, size_t count)
{
    static const std::vector<std::string> seeds = {"office", "ffi", "flow", "AVATAR", "Toyota", "Wave", "Yo.", "T,", "10:30", "1x2",
                                                   "-12.5%", "$1,234", "(a)", "fjord", "Th", "LT", "\"quoted\"", "it's", "a/b", "x=y"};
    static const std::string alphabet = "0123456789.,:%$-+ ()AVTWYaeflortxyiuns";
    std::vector<std::string> words(seeds.begin(), seeds.end());
    while(words.size() < count)
    {
        std::string word;
        size_t      length = 1 + random() % 8;
        for(size_t i = 0; i < length; i++)
            word += alphabet[random() % alphabet.size()];
        if(word.front() == ' ' || word.back() == ' ')
            continue;
        words.push_back(word);
    }
    return words;
}

static NSString *Document(const std::vector<std::string> &words, bool pre)
{
    NSMutableString *html = [NSMutableString string];
    NSArray         *styles = Styles();
    for(NSUInteger s = 0; s < styles.count; s++)
    {
        [html appendFormat:@"<p style=\"%@%@\">", styles[s], pre ? @";white-space:pre" : @""];
        for(size_t i = s; i < words.size(); i += 2)
        {
            NSString *word = @(words[i].c_str());
            word = [[word stringByReplacingOccurrencesOfString:@"&" withString:@"&amp;"] stringByReplacingOccurrencesOfString:@"<" withString:@"&lt;"];
            [html appendFormat:@"<span>%@</span> ", word];
        }
        [html appendString:@"</p>"];
    }
    return html;
}

static NSData *Render(NSString *html, NSString **signature, CGFloat *height)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:@"body{margin:3px}p{margin:2px 0;text-decoration:underline}"];
    CGSize      size     = [document layoutWithMaxWidth:360 preferredWidth:360];
    CGImageRef  image    = [document copyImageWithMaxWidth:360 rect:CGRectMake(0, 0, 360, ceil(size.height)) scale:2];
    NSData     *data     = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    *signature = document.layoutSignature;
    *height    = size.height;
    return data;
}

- (void)tearDown
{
    LHDocument.simpleTextEnabled = YES;
    [LHDocument purgeCaches];
}

- (void)testLearnedShapingMatchesCoreText
{
    std::mt19937 random(7);
    std::vector<std::string> training = Words(random, 900);
    std::set<std::string>    seen(training.begin(), training.end());
    std::vector<std::string> novel;
    for(const auto &word : training)
    {
        for(size_t start = 0; start < word.size(); start++)
        {
            for(size_t length = 1; start + length <= word.size(); length++)
            {
                std::string part = word.substr(start, length);
                if(part.front() != ' ' && part.back() != ' ' && seen.insert(part).second)
                    novel.push_back(part);
            }
        }
    }
    for(int round = 0; round < 3; round++)
    {
        [LHDocument purgeCaches];
        LHDocument.simpleTextEnabled = YES;
        NSString *signature = nil;
        CGFloat   height    = 0;
        Render(Document(training, round == 2), &signature, &height);
        NSDictionary *before = LHDocument.simpleTextStats;
        NSString     *fastSignature = nil;
        CGFloat       fastHeight    = 0;
        NSData       *fast          = Render(Document(novel, round == 2), &fastSignature, &fastHeight);
        NSDictionary *after         = LHDocument.simpleTextStats;
        XCTAssertGreaterThan([after[@"hits"] longValue] - [before[@"hits"] longValue], 500);

        LHDocument.simpleTextEnabled = NO;
        [LHDocument purgeCaches];
        NSString *slowSignature = nil;
        CGFloat   slowHeight    = 0;
        NSData   *slow          = Render(Document(novel, round == 2), &slowSignature, &slowHeight);
        XCTAssertEqual(fastHeight, slowHeight);
        XCTAssertEqualObjects(fastSignature, slowSignature);
        XCTAssertEqualObjects(fast, slow, @"round %d", round);
    }
}

- (void)testTickerNumbersReuseLearnedPairs
{
    [LHDocument purgeCaches];
    std::mt19937 random(3);
    auto table = [&](int seed) {
        NSMutableString *html = [NSMutableString stringWithString:@"<table style=\"font:11px -apple-system;width:100%\">"];
        std::mt19937     values(seed);
        for(int r = 0; r < 40; r++)
        {
            [html appendString:@"<tr>"];
            for(int c = 0; c < 6; c++)
                [html appendFormat:@"<td style=\"text-align:right\">%.2f</td>", 100 + (values() % 100000) / 100.0];
            [html appendString:@"</tr>"];
        }
        [html appendString:@"</table>"];
        return html;
    };
    NSString *signature = nil;
    CGFloat   height    = 0;
    for(int seed = 0; seed < 6; seed++)
        Render(table(seed), &signature, &height);
    NSDictionary *before = LHDocument.simpleTextStats;
    NSString     *fastSignature = nil;
    NSData       *fast          = Render(table(99), &fastSignature, &height);
    NSDictionary *after         = LHDocument.simpleTextStats;
    XCTAssertGreaterThan([after[@"hits"] longValue] - [before[@"hits"] longValue], 200);
    LHDocument.simpleTextEnabled = NO;
    [LHDocument purgeCaches];
    NSString *slowSignature = nil;
    NSData   *slow          = Render(table(99), &slowSignature, &height);
    XCTAssertEqualObjects(fastSignature, slowSignature);
    XCTAssertEqualObjects(fast, slow);
}

- (void)testLigaturesAndNonASCIIStayOnCoreText
{
    [LHDocument purgeCaches];
    NSString *html  = @"<p style=\"font:20px Times\">office</p><p style=\"font:20px Times\">ffi</p><p style=\"font:20px Times\">fi</p>"
                      @"<p style=\"font:20px Times\">café</p><p style=\"font:20px Times\">你好\U0001F600</p>";
    NSString *probe = @"<p style=\"font:20px Times\">ffi</p><p style=\"font:20px Times\">offi</p><p style=\"font:20px Times\">fi</p>"
                      @"<p style=\"font:20px Times\">café</p>";
    NSString *signature = nil;
    CGFloat   height    = 0;
    for(int i = 0; i < 4; i++)
        Render(html, &signature, &height);
    NSDictionary *before = LHDocument.simpleTextStats;
    NSData       *fast   = Render(probe, &signature, &height);
    NSDictionary *after  = LHDocument.simpleTextStats;
    XCTAssertEqual([after[@"hits"] longValue], [before[@"hits"] longValue]);
    LHDocument.simpleTextEnabled = NO;
    [LHDocument purgeCaches];
    NSString *slowSignature = nil;
    NSData   *slow          = Render(probe, &slowSignature, &height);
    XCTAssertEqualObjects(signature, slowSignature);
    XCTAssertEqualObjects(fast, slow);
}

static NSData *RenderGPU(LHGPURenderer *renderer, NSString *html, BOOL *supported)
{
    LHDocument    *document = [[LHDocument alloc] initWithHTML:html css:@"body{margin:3px;background:#fff}p{margin:2px 0}"];
    CGSize         size     = [document layoutWithMaxWidth:300 preferredWidth:300];
    LHDisplayList *list     = [document displayListWithMaxWidth:300 gpu:renderer scale:2];
    *supported              = list.gpuSupported;
    NSUInteger height       = (NSUInteger)ceil(size.height);
    MTLTextureDescriptor *descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:600 height:height * 2 mipmapped:NO];
    descriptor.usage       = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
    descriptor.storageMode = MTLStorageModeShared;
    id<MTLTexture>       texture = [renderer.device newTextureWithDescriptor:descriptor];
    id<MTLCommandBuffer> buffer  = [renderer.queue commandBuffer];
    [renderer encodeDisplayList:list tiles:@[[NSValue valueWithRect:NSMakeRect(0, 0, 300, height)]] scale:2 targets:@[texture] commandBuffer:buffer];
    [buffer commit];
    [buffer waitUntilCompleted];
    [renderer endFrame];
    NSMutableData *data = [NSMutableData dataWithLength:600 * 4 * height * 2];
    [texture getBytes:data.mutableBytes bytesPerRow:600 * 4 fromRegion:MTLRegionMake2D(0, 0, 600, height * 2) mipmapLevel:0];
    return data;
}

- (void)testGlyphAtlasRunsMatchCoreTextRuns
{
    LHGPURenderer *renderer = [[LHGPURenderer alloc] initWithDevice:nil];
    if(!renderer)
        return;
    std::mt19937             random(11);
    std::vector<std::string> words = Words(random, 300);
    NSMutableString         *training = [NSMutableString string];
    NSMutableString         *probe    = [NSMutableString string];
    for(NSString *style in @[@"font:13px -apple-system", @"font:600 16px Helvetica", @"font:12px Georgia"])
    {
        [training appendFormat:@"<p style=\"%@\">", style];
        [probe appendFormat:@"<p style=\"%@\">", style];
        for(size_t i = 0; i < words.size(); i++)
        {
            NSString *word = [[@(words[i].c_str()) stringByReplacingOccurrencesOfString:@"&" withString:@"&amp;"] stringByReplacingOccurrencesOfString:@"<" withString:@"&lt;"];
            [training appendFormat:@"%@ ", word];
            if(words[i].size() > 2)
                [probe appendFormat:@"%@ ", [word substringToIndex:word.length - 1]];
        }
        [training appendString:@"</p>"];
        [probe appendString:@"</p>"];
    }
    [LHDocument purgeCaches];
    BOOL supported = NO;
    RenderGPU(renderer, training, &supported);
    NSDictionary *before = LHDocument.simpleTextStats;
    NSData       *fast   = RenderGPU(renderer, probe, &supported);
    XCTAssertTrue(supported);
    XCTAssertGreaterThan([LHDocument.simpleTextStats[@"hits"] longValue] - [before[@"hits"] longValue], 200);
    LHDocument.simpleTextEnabled = NO;
    [LHDocument purgeCaches];
    LHGPURenderer *fresh = [[LHGPURenderer alloc] initWithDevice:nil];
    NSData        *slow  = RenderGPU(fresh, probe, &supported);
    const uint8_t *bytes = (const uint8_t *)fast.bytes;
    size_t         inked = 0;
    for(size_t i = 0; i < fast.length; i += 4)
        inked += bytes[i] < 128;
    XCTAssertGreaterThan(inked, 1000u);
    XCTAssertEqualObjects(fast, slow);
}

- (void)testTransparentLeadingShadowMatchesVisibleShadowOnGPU
{
    LHGPURenderer *renderer = [[LHGPURenderer alloc] initWithDevice:nil];
    XCTSkipIf(!renderer, @"Metal is unavailable");
    BOOL supported = NO;
    NSData *visible = RenderGPU(renderer, @"<p style='font:24px -apple-system;text-shadow:3px 4px 0 red'>Shadow cache</p>", &supported);
    XCTAssertTrue(supported);
    NSData *leadingTransparent = RenderGPU(renderer, @"<p style='font:24px -apple-system;text-shadow:0 0 0 transparent,3px 4px 0 red'>Shadow cache</p>", &supported);
    XCTAssertTrue(supported);
    XCTAssertTrue([visible isEqualToData:leadingTransparent]);
}

@end
