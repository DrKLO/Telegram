#import <XCTest/XCTest.h>
#import "LHDocument.h"
#import "LHImages.h"
#import "LHSanitizer.h"

#include "twidget_assets.h"

#include <random>
#include <string>
#include <vector>

namespace
{
struct Pixel
{
    int r = 0, g = 0, b = 0, a = 0;
};

std::vector<uint8_t> Pixels(CGImageRef image)
{
    size_t               w = CGImageGetWidth(image), h = CGImageGetHeight(image);
    std::vector<uint8_t> bytes(w * h * 4);
    CGColorSpaceRef      space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef         ctx   = CGBitmapContextCreate(bytes.data(), w, h, 8, w * 4, space, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    CGContextDrawImage(ctx, CGRectMake(0, 0, w, h), image);
    CGContextRelease(ctx);
    CGColorSpaceRelease(space);
    return bytes;
}

Pixel At(CGImageRef image, const std::vector<uint8_t>& bytes, CGFloat fx, CGFloat fy)
{
    size_t w = CGImageGetWidth(image), h = CGImageGetHeight(image);
    size_t x = std::min(w - 1, size_t(fx * w)), y = std::min(h - 1, size_t(fy * h));
    const uint8_t* p = &bytes[(y * w + x) * 4];
    return {p[0], p[1], p[2], p[3]};
}

std::string SVGFor(const char* url)
{
    double size = 0;
    char*  svg  = twa_icon_svg(url, &size);
    std::string out = svg ? svg : "";
    twa_free(svg);
    return out;
}

std::string Canonical(const char* url)
{
    char*       s   = twa_icon_canonical_url(url);
    std::string out = s ? s : "";
    twa_free(s);
    return out;
}

NSString* AssetsDirectory()
{
    return [[@(__FILE__) stringByDeletingLastPathComponent] stringByAppendingPathComponent:@"../../Sources/TwidgetAssets"];
}

CGImageRef Render(NSString* html, CGSize size)
{
    LHDocument* document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:size.width preferredWidth:size.width];
    return [document copyImageWithMaxWidth:size.width rect:CGRectMake(0, 0, size.width, size.height) scale:2];
}
}

@interface IconAssetTests : XCTestCase
@end

@implementation IconAssetTests

- (void)tearDown
{
    LHImages.portableSymbols = NO;
    [LHImages setProvider:nil];
    [LHSanitizer setLinkSchemes:@[@"http", @"https", @"mailto"] imageSchemes:@[]];
}

- (void)testIconURLsParse
{
    auto icon = twa::parseIconURL("icon:heart");
    XCTAssertTrue(icon && icon->known);
    XCTAssertEqual(icon->name, "heart");
    XCTAssertEqual(icon->variant, twa::Variant::regular);
    XCTAssertEqual(icon->size, 20.f);
    XCTAssertEqual(icon->rgba, 0x404045ffu);

    icon = twa::parseIconURL("icon:heart-fill?size=24&color=ff2d55");
    XCTAssertTrue(icon && icon->known);
    XCTAssertEqual(icon->name, "heart");
    XCTAssertEqual(icon->variant, twa::Variant::fill);
    XCTAssertEqual(icon->size, 24.f);
    XCTAssertEqual(icon->rgba, 0xff2d55ffu);

    XCTAssertEqual(twa::parseIconURL("icon:star?weight=semibold")->variant, twa::Variant::bold);
    XCTAssertEqual(twa::parseIconURL("icon:star?weight=thin")->variant, twa::Variant::light);
    XCTAssertEqual(twa::parseIconURL("icon:star?weight=medium")->variant, twa::Variant::regular);
    XCTAssertEqual(twa::parseIconURL("icon:star?weight=fill")->variant, twa::Variant::fill);
    XCTAssertEqual(twa::parseIconURL("icon:star?weight=wobbly")->variant, twa::Variant::regular);
    XCTAssertEqual(twa::parseIconURL("icon:star-fill?weight=light")->variant, twa::Variant::fill);
    XCTAssertEqual(twa::parseIconURL("ICON:star?size=12.345")->size, 12.35f);
    XCTAssertEqual(twa::parseIconURL("icon:star?color=%23abc")->rgba, 0xaabbccffu);
    XCTAssertEqual(twa::parseIconURL("icon:star?color=11223344")->rgba, 0x11223344u);
    XCTAssertEqual(twa::parseIconURL("icon:star?color=1234")->rgba, 0x11223344u);
    XCTAssertEqual(twa::parseIconURL("icon:star?size=10&size=30")->size, 30.f);
    XCTAssertEqual(twa::parseIconURL("icon:star?mode=multicolor&color=00ff00")->rgba, 0x00ff00ffu);
    XCTAssertFalse(twa::parseIconURL("sf:star"));
    XCTAssertFalse(twa::parseIconURL("data:image/png,x"));
    XCTAssertFalse(twa::parseSymbolURL("icon:star"));
}

- (void)testHostileIconURLsFallBackToBoundedPlaceholders
{
    std::string longQuery = "icon:heart?size=20";
    while(longQuery.size() < 100000)
        longQuery += "&color=ff0000";
    std::vector<std::string> names = {"icon:../heart", "icon:..%2Fheart", "icon:/etc/passwd", "icon:heart/../star", "icon:${name}", "icon:Heart", "icon:heart--fill",
                                      "icon:-heart", "icon:heart-", "icon:", "icon:?size=20", "icon:heart\\x", "icon:fill", "icon:" + std::string(70, 'a'), "icon:heart-fill-fill",
                                      longQuery, std::string("icon:hea\0rt", 11), "icon:heart\n", "icon:star?size=20#../../x", "icon://heart"};
    for(const auto& url : names)
    {
        auto icon = twa::parseIconURL(url);
        XCTAssertTrue(icon.has_value(), @"%s", url.c_str());
        if(url == "icon:star?size=20#../../x")
        {
            XCTAssertTrue(icon->known);
            continue;
        }
        XCTAssertFalse(icon->known, @"%s", url.c_str());
        XCTAssertEqual(icon->name, "placeholder", @"%s", url.c_str());
        std::string svg = twa::svg(*icon);
        XCTAssertLessThan(svg.size(), 4096u);
        XCTAssertEqual(svg.find("<path"), svg.rfind("<path"));
    }
    XCTAssertEqual(twa::parseIconURL(longQuery)->size, 20.f);

    std::vector<std::pair<std::string, float>> sizes = {{"1e309", 20}, {"99999", 512}, {"-5", 20}, {"0", 1}, {"NaN", 20}, {"inf", 20}, {"", 20}, {"12px", 20}, {"0.001", 1}, {"511.999", 512}, {"1.5.5", 20}, {"00000000000000000000024", 20}};
    for(const auto& [value, expected] : sizes)
        XCTAssertEqual(twa::parseIconURL("icon:star?size=" + value)->size, expected, @"%s", value.c_str());

    for(const char* color : {"zzzzzz", "#12", "12345", "1234567", "rgb(1,2,3)", "red", "%", "#", "ff00ff00ff"})
        XCTAssertEqual(twa::parseIconURL(std::string("icon:star?color=") + color)->rgba, 0x404045ffu, @"%s", color);

    std::string svg = SVGFor("icon:star?color=ff0000\"><script>alert(1)</script>");
    XCTAssertEqual(svg.find("script"), std::string::npos);
    XCTAssertNotEqual(svg.find("fill=\"#404045\""), std::string::npos);
}

- (void)testOutputIsDeterministicAndCanonical
{
    std::string a = SVGFor("icon:heart-fill?size=24&color=FF2D55");
    std::string b = SVGFor("icon:heart?color=%23ff2d55&weight=fill&size=24.00");
    XCTAssertEqual(a, b);
    XCTAssertEqual(a.rfind("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 256 256\"><path fill=\"#ff2d55\" d=\"M", 0), 0u);
    XCTAssertEqual(Canonical("icon:heart?color=FF2D55&size=24&weight=fill"), "icon:heart-fill?size=24&color=ff2d55");
    XCTAssertEqual(Canonical("icon:star?size=13.5&color=00000080&weight=semibold"), "icon:star?size=13.5&color=00000080&weight=bold");
    XCTAssertEqual(Canonical("icon:nope"), "icon:placeholder?size=20&color=404045&weight=regular");
    std::string translucent = SVGFor("icon:star?color=00000080");
    XCTAssertNotEqual(translucent.find("fill-opacity=\"0.502\""), std::string::npos);

    char* data = twa_icon_data_url("icon:star?size=16&color=007aff");
    std::string url = data;
    twa_free(data);
    XCTAssertEqual(url.rfind("data:image/svg+xml,%3Csvg%20xmlns=", 0), 0u);
    XCTAssertEqual(url.find('"'), std::string::npos);
    XCTAssertEqual(url.find('#'), std::string::npos);
    XCTAssertEqual(url.find('<'), std::string::npos);
    XCTAssertEqual(url.find(' '), std::string::npos);
}

- (void)testManifestMatchesTheCompiledTable
{
    NSString*     dir      = AssetsDirectory();
    NSData*       json     = [NSData dataWithContentsOfFile:[dir stringByAppendingPathComponent:@"icons.json"]];
    NSDictionary* manifest = [NSJSONSerialization JSONObjectWithData:json options:0 error:nil];
    XCTAssertEqualObjects(manifest[@"format"], @"twidget-icons");
    XCTAssertEqual([manifest[@"version"] intValue], twa_manifest_version());
    NSArray<NSString*>* icons = manifest[@"icons"];
    XCTAssertEqual(icons.count, twa::iconCount());
    for(NSUInteger i = 0; i < icons.count; i++)
    {
        XCTAssertEqualObjects(icons[i], @(twa::iconName(i)));
        for(NSString* variant in manifest[@"variants"])
        {
            NSString* file = [dir stringByAppendingPathComponent:[NSString stringWithFormat:@"icons/%@/%@.svg", variant, icons[i]]];
            NSString* text = [NSString stringWithContentsOfFile:file encoding:NSUTF8StringEncoding error:nil];
            XCTAssertTrue([text hasPrefix:@"<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 256 256\""], @"%@", file);
            NSRange first = [text rangeOfString:@" d=\""];
            NSString* d = [text substringFromIndex:NSMaxRange(first)];
            d = [d substringToIndex:[d rangeOfString:@"\""].location];
            std::string url = std::string("icon:") + icons[i].UTF8String + "?weight=" + variant.UTF8String;
            XCTAssertNotEqual(SVGFor(url.c_str()).find(d.UTF8String), std::string::npos, @"%@ %@", variant, icons[i]);
        }
    }
    NSDictionary<NSString*, NSString*>* aliases = manifest[@"sfAliases"];
    XCTAssertEqual(aliases.count, twa::symbolAliasCount());
    for(NSString* sf in aliases)
        XCTAssertEqualObjects(@(twa::symbolAlias(sf.UTF8String)), aliases[sf]);
    XCTAssertTrue(twa::hasIcon(((NSString*)manifest[@"placeholder"]).UTF8String));
}

- (void)testSymbolAliasesCoverTheDemoCorpus
{
    NSArray<NSString*>* corpus = @[
        @"airplane", @"arrow.triangle.2.circlepath", @"arrow.triangle.merge", @"arrow.triangle.pull", @"arrow.uturn.backward.circle.fill", @"backward.fill",
        @"bag.fill", @"bell.fill", @"bolt.fill", @"bubble.left.fill", @"building.2.fill", @"chart.bar.xaxis", @"checkmark.circle.fill", @"checkmark.seal.fill",
        @"chevron.left.forwardslash.chevron.right", @"circle", @"clock.fill", @"cloud.drizzle.fill", @"cloud.rain.fill", @"cloud.sun.fill", @"cloud.sun.rain.fill",
        @"creditcard.fill", @"cross.case.fill", @"cup.and.saucer.fill", @"doc.richtext", @"doc.text", @"drop.fill", @"envelope.fill", @"exclamationmark.triangle.fill",
        @"figure.walk", @"flame.fill", @"folder.fill", @"forward.fill", @"gamecontroller.fill", @"gift.fill", @"hammer.fill", @"hare.fill", @"heart", @"heart.fill",
        @"house.fill", @"ladybug.fill", @"leaf.fill", @"lock.fill", @"map.fill", @"moon.stars.fill", @"mountain.2.fill", @"newspaper.fill", @"paperplane.fill",
        @"pause.fill", @"pawprint.fill", @"pencil.circle.fill", @"person.2.fill", @"person.3.fill", @"play.fill", @"questionmark.circle.fill", @"repeat", @"repeat.1",
        @"scalemass.fill", @"shippingbox", @"shippingbox.fill", @"shuffle", @"snowflake", @"star.fill", @"sun.horizon.fill", @"sun.max.fill", @"text.bubble.fill", @"trophy.fill",
        @"truck.box.fill", @"wand.and.stars", @"wifi", @"wind", @"xmark.circle.fill", @"xmark.octagon.fill"
    ];
    for(NSString* name in corpus)
    {
        auto icon = twa::parseSymbolURL(std::string("sf:") + name.UTF8String + "?size=20");
        XCTAssertTrue(icon && icon->known, @"%@ has no alias", name);
        XCTAssertEqual(icon->size, 24.f);
        if([name hasSuffix:@".fill"])
            XCTAssertEqual(icon->variant, twa::Variant::fill, @"%@", name);
    }
    auto trophy = twa::parseSymbolURL("sf:trophy.fill?size=22&mode=multicolor");
    XCTAssertEqual(trophy->rgba, 0xffcc00ffu);
    XCTAssertEqual(trophy->size, 26.4f);
    XCTAssertFalse(twa::parseSymbolURL("sf:not.a.symbol")->known);
    XCTAssertEqual(twa::parseSymbolURL("sf:circle?weight=fill")->variant, twa::Variant::regular);
}

- (void)testIconsRenderWithTheirSizeAndColour
{
    CGImageRef image = Render(@"<body style=\"margin: 0\"><img style=\"display: block\" src=\"icon:square-fill?size=40&color=ff0000\"><img style=\"display: block\" src=\"icon:circle-fill?size=16&color=0000ff80\">", CGSizeMake(40, 56));
    XCTAssertTrue(image != nullptr);
    auto bytes = Pixels(image);
    Pixel red  = At(image, bytes, 0.5, 20.0 / 56);
    XCTAssertGreaterThan(red.r, 240);
    XCTAssertLessThan(red.g, 10);
    XCTAssertGreaterThan(red.a, 240);
    Pixel corner = At(image, bytes, 0.5 / 40, 0.5 / 56);
    XCTAssertLessThan(corner.a, 10);
    Pixel blue = At(image, bytes, 8.0 / 40, 48.0 / 56);
    XCTAssertGreaterThan(blue.b, 100);
    XCTAssertGreaterThan(blue.a, 110);
    XCTAssertLessThan(blue.a, 145);
    CGImageRelease(image);

    LHDocument* document = [[LHDocument alloc] initWithHTML:@"<img id=\"a\" src=\"icon:heart?size=33.5\"><img id=\"b\" src=\"icon:does-not-exist?size=18\">" css:nil];
    [document layoutWithMaxWidth:300];
    CGRect a = [document boundsOfElementsMatching:@"#a"];
    CGRect b = [document boundsOfElementsMatching:@"#b"];
    XCTAssertEqualWithAccuracy(a.size.width, 33.5, 0.01);
    XCTAssertEqualWithAccuracy(a.size.height, 33.5, 0.01);
    XCTAssertEqualWithAccuracy(b.size.width, 18, 0.01);
}

- (void)testRenderingIsDeterministic
{
    NSString* html = @"<div style=\"line-height: 0\"><img src=\"icon:gear?size=24&color=34c759&weight=bold\"><img src=\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24'%3E%3Ccircle cx='12' cy='12' r='9' fill='%23ff9500'/%3E%3C/svg%3E\"></div>";
    CGImageRef a = Render(html, CGSizeMake(48, 24));
    [LHImages forgetImageWithURL:@"icon:gear?size=24&color=34c759&weight=bold"];
    CGImageRef b = Render(html, CGSizeMake(48, 24));
    XCTAssertTrue(a && b);
    XCTAssertTrue(Pixels(a) == Pixels(b));
    CGImageRelease(a);
    CGImageRelease(b);
}

- (void)testIconSchemeNeedsNoHostRegistration
{
    [LHSanitizer setLinkSchemes:@[@"https"] imageSchemes:@[]];
    LHDocument* document = [[LHDocument alloc] initWithHTML:@"<img src=\"icon:star\"><div style=\"background-image: url(icon:star)\">x</div><img src=\"sf:star\"><img src=\"app:logo\">" css:nil];
    [document layoutWithMaxWidth:300];
    XCTAssertEqual(document.sanitizerCounts[@"url-blocked"].intValue, 2, @"%@", document.sanitizerDetails);
}

- (void)testPortableSymbolsResolveThroughAliases
{
    [LHSanitizer setLinkSchemes:@[@"https"] imageSchemes:@[@"sf"]];
    LHImages.portableSymbols = YES;
    CGImageRef viaSymbol = Render(@"<div style=\"line-height: 0\"><img src=\"sf:heart.fill?size=20&color=ff2d55\"></div>", CGSizeMake(24, 24));
    CGImageRef viaIcon   = Render(@"<div style=\"line-height: 0\"><img src=\"icon:heart-fill?size=24&color=ff2d55\"></div>", CGSizeMake(24, 24));
    XCTAssertTrue(viaSymbol && viaIcon);
    XCTAssertTrue(Pixels(viaSymbol) == Pixels(viaIcon));
    CGImageRelease(viaSymbol);
    CGImageRelease(viaIcon);
    LHDocument* document = [[LHDocument alloc] initWithHTML:@"<img id=\"u\" src=\"sf:no.such.symbol?size=10\">" css:nil];
    [document layoutWithMaxWidth:300];
    XCTAssertEqualWithAccuracy([document boundsOfElementsMatching:@"#u"].size.width, 12, 0.01);
}

- (void)testHostileSVGDataNeverFetchesOrRecurses
{
    [LHSanitizer setLinkSchemes:@[@"https"] imageSchemes:@[@"asset"]];
    __block NSMutableArray<NSString*>* asked = [NSMutableArray array];
    [LHImages setProvider:^NSImage*(NSString* url) {
        @synchronized(asked)
        {
            [asked addObject:url];
        }
        return nil;
    }];
    NSString* nested = @"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10'%3E%3Cimage href='data:image/svg+xml,%253Csvg xmlns=%2527http://www.w3.org/2000/svg%2527 width=%252710%2527 height=%252710%2527/%253E' width='10' height='10'/%3E%3C/svg%3E";
    NSArray<NSString*>* payloads = @[
        @"<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20' onload='alert(1)'><script>alert(1)</script><rect width='20' height='20' fill='red'/></svg>",
        @"<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><foreignObject width='20' height='20'><img src='https://evil.example/p.png'/><iframe src='https://evil.example'></iframe></foreignObject></svg>",
        @"<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' width='20' height='20'><image href='https://evil.example/a.png' width='20' height='20'/><image xlink:href='file:///etc/passwd' width='20' height='20'/><use href='https://evil.example/s.svg#a'/><rect fill='url(https://evil.example/f)' width='5' height='5'/></svg>",
        @"<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><style>@import url(https://evil.example/x.css); rect { fill: url(https://evil.example/f); }</style><rect width='20' height='20' style='background-image:url(asset:spy)'/></svg>",
        @"<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><text>&x;</text></svg>",
        @"<div style='position:absolute;width:999px;height:999px;background:red'>html</div><svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'/>",
        @"<svg xmlns='http://www.w3.org/2000/svg' width='99999999' height='99999999'><rect width='1' height='1'/></svg>",
        @"<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><a href='https://evil.example'><rect width='20' height='20'/></a><image href='icon:star' width='20' height='20'/></svg>",
    ];
    NSMutableString* html = [NSMutableString stringWithString:@"<div>"];
    for(NSString* payload in payloads)
    {
        NSString* encoded = [payload stringByAddingPercentEncodingWithAllowedCharacters:NSCharacterSet.alphanumericCharacterSet];
        [html appendFormat:@"<img src=\"data:image/svg+xml,%@\"><img src=\"data:image/svg+xml;base64,%@\">", encoded,
                           [[payload dataUsingEncoding:NSUTF8StringEncoding] base64EncodedStringWithOptions:0]];
    }
    [html appendFormat:@"<img src=\"%@\"></div>", nested];
    CGImageRef image = Render(html, CGSizeMake(400, 400));
    XCTAssertTrue(image != nullptr);
    CGImageRelease(image);
    for(NSString* url in asked)
        XCTAssertFalse([url hasPrefix:@"http"] || [url hasPrefix:@"file"], @"%@", url);
    XCTAssertFalse([asked containsObject:@"asset:spy"]);
}

@end

@interface IconFuzzTests : XCTestCase
@end

@implementation IconFuzzTests

- (void)testMutatedIconURLsStayBounded
{
    const char* seeds[] = {"icon:heart-fill?size=24&color=ff2d55", "icon:star?weight=bold&size=13.5", "sf:checkmark.circle.fill?size=13&color=34c759&mode=multicolor", "icon:placeholder", "sf:trophy.fill?mode=multicolor"};
    const char  alphabet[] = "abcdefghijklmnopqrstuvwxyz0123456789-.?&=#%/\\:;${}<>\"' \x01\xff";
    std::mt19937 rng(0x7717);
    double       seconds = [NSProcessInfo.processInfo.environment[@"TWIDGET_FUZZ_SECONDS"] doubleValue];
    NSDate*      until   = [NSDate dateWithTimeIntervalSinceNow:seconds > 0 ? seconds : 1];
    int          rounds  = 0;
    while(rounds < 2000 || [until timeIntervalSinceNow] > 0)
    {
        std::string url = seeds[rng() % 5];
        int         edits = 1 + rng() % 8;
        for(int e = 0; e < edits; e++)
        {
            size_t at = url.empty() ? 0 : rng() % (url.size() + 1);
            switch(rng() % 4)
            {
                case 0:
                    url.insert(at, 1, alphabet[rng() % (sizeof(alphabet) - 1)]);
                    break;
                case 1:
                    if(at < url.size())
                        url.erase(at, 1 + rng() % 4);
                    break;
                case 2:
                    url.insert(at, std::string(rng() % 600, alphabet[rng() % (sizeof(alphabet) - 1)]));
                    break;
                default:
                    url.insert(at, rng() % 2 ? "size=1e400&" : "color=%zz&");
            }
        }
        auto icon = twa::parseURL(url);
        if(icon)
        {
            XCTAssertGreaterThanOrEqual(icon->size, 1.f);
            XCTAssertLessThanOrEqual(icon->size, 512.f);
            std::string svg = twa::svg(*icon);
            XCTAssertLessThan(svg.size(), 16384u);
            XCTAssertEqual(twa::svg(*twa::parseIconURL(twa::canonicalURL(*icon))), svg);
        }
        if(rounds % 50 == 0)
        {
            @autoreleasepool
            {
                NSString* src = [[NSString alloc] initWithBytes:url.data() length:url.size() encoding:NSISOLatin1StringEncoding];
                src = [src stringByReplacingOccurrencesOfString:@"\"" withString:@"&quot;"];
                LHDocument* document = [[LHDocument alloc] initWithHTML:[NSString stringWithFormat:@"<img src=\"%@\">", src] css:nil];
                [document layoutWithMaxWidth:200];
            }
        }
        rounds++;
    }
}

@end
