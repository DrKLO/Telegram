#import "LHImages.h"
#include "LHContainer.h"

static NSColor *Hex(uint32_t v, CGFloat a = 1.0)
{
    return [NSColor colorWithSRGBRed:((v >> 16) & 0xff) / 255.0 green:((v >> 8) & 0xff) / 255.0 blue:(v & 0xff) / 255.0 alpha:a];
}

static NSFont *LogoFont(void)
{
    if(@available(macOS 10.15, *))
        return [NSFont monospacedSystemFontOfSize:22 weight:NSFontWeightBold];
    return [NSFont fontWithName:@"Menlo-Bold" size:22] ?: [NSFont boldSystemFontOfSize:22];
}

static NSImage *Draw(CGFloat w, CGFloat h, void (^body)(NSRect r))
{
    return [NSImage imageWithSize:NSMakeSize(w, h) flipped:YES drawingHandler:^BOOL(NSRect r) {
        body(r);
        return YES;
    }];
}

static void Gradient(NSRect r, NSArray<NSColor *> *colors, CGFloat angle)
{
    [[[NSGradient alloc] initWithColors:colors] drawInRect:r angle:angle];
}

static void RegisterDemoImages(void)
{
    LHRegisterAppImage(@"sunset", Draw(360, 180, ^(NSRect r) {
        Gradient(r, @[Hex(0x2b1055), Hex(0x7597de), Hex(0xff9a8b), Hex(0xffd194)], -90);
        [Hex(0xfff3c4) setFill];
        [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect(220, 78, 64, 64)] fill];
        NSArray *layers = @[@[@(0x6a3d7c), @130, @(0.9)], @[@(0x3f2a5c), @150, @(1.0)]];
        for(NSArray *l in layers)
        {
            NSBezierPath *p = [NSBezierPath bezierPath];
            CGFloat base = [l[1] doubleValue];
            [p moveToPoint:NSMakePoint(0, 180)];
            [p lineToPoint:NSMakePoint(0, base)];
            for(int i = 0; i <= 12; i++)
                [p lineToPoint:NSMakePoint(i * 30, base - ((i * 37) % 23) - (i % 2 ? 6 : 18))];
            [p lineToPoint:NSMakePoint(360, 180)];
            [p closePath];
            [Hex([l[0] unsignedIntValue], [l[2] doubleValue]) setFill];
            [p fill];
        }
    }));

    LHRegisterAppImage(@"aurora", Draw(200, 200, ^(NSRect r) {
        Gradient(r, @[Hex(0x0b1d3a), Hex(0x10294d), Hex(0x0d3b4f)], -90);
        for(int band = 0; band < 3; band++)
        {
            NSBezierPath *p = [NSBezierPath bezierPath];
            CGFloat y = 50 + band * 28;
            [p moveToPoint:NSMakePoint(-10, y)];
            [p curveToPoint:NSMakePoint(210, y + 20) controlPoint1:NSMakePoint(60, y - 50) controlPoint2:NSMakePoint(140, y + 60)];
            p.lineWidth = 18 - band * 4;
            [Hex(band == 1 ? 0x5ef2c4 : 0x38d39f, 0.55 - band * 0.12) setStroke];
            [p stroke];
        }
        [Hex(0xffffff, 0.8) setFill];
        for(int i = 0; i < 40; i++)
            [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect((i * 53) % 200, (i * 29) % 110, 1.6, 1.6)] fill];
        [Hex(0x081322) setFill];
        NSBezierPath *hill = [NSBezierPath bezierPath];
        [hill moveToPoint:NSMakePoint(0, 200)];
        [hill lineToPoint:NSMakePoint(0, 165)];
        [hill curveToPoint:NSMakePoint(200, 160) controlPoint1:NSMakePoint(70, 140) controlPoint2:NSMakePoint(130, 185)];
        [hill lineToPoint:NSMakePoint(200, 200)];
        [hill fill];
    }));

    LHRegisterAppImage(@"ocean", Draw(200, 200, ^(NSRect r) {
        Gradient(r, @[Hex(0x89f7fe), Hex(0x66a6ff), Hex(0x2a5298)], -90);
        for(int i = 0; i < 6; i++)
        {
            NSBezierPath *p = [NSBezierPath bezierPath];
            CGFloat y = 70 + i * 24;
            [p moveToPoint:NSMakePoint(0, y)];
            for(int x = 0; x < 200; x += 40)
                [p curveToPoint:NSMakePoint(x + 40, y) controlPoint1:NSMakePoint(x + 10, y - 8) controlPoint2:NSMakePoint(x + 30, y + 8)];
            p.lineWidth = 2;
            [Hex(0xffffff, 0.35) setStroke];
            [p stroke];
        }
    }));

    LHRegisterAppImage(@"dunes", Draw(200, 200, ^(NSRect r) {
        Gradient(r, @[Hex(0xffe29f), Hex(0xffa99f), Hex(0xff719a)], -90);
        NSArray *cols = @[@(0xf6a05c), @(0xe07b39), @(0xb85a26)];
        for(int i = 0; i < 3; i++)
        {
            NSBezierPath *p = [NSBezierPath bezierPath];
            CGFloat y = 110 + i * 30;
            [p moveToPoint:NSMakePoint(0, 200)];
            [p lineToPoint:NSMakePoint(0, y)];
            [p curveToPoint:NSMakePoint(200, y - 10 + i * 8) controlPoint1:NSMakePoint(60, y - 40) controlPoint2:NSMakePoint(120, y + 30)];
            [p lineToPoint:NSMakePoint(200, 200)];
            [Hex([cols[i] unsignedIntValue]) setFill];
            [p fill];
        }
    }));

    LHRegisterAppImage(@"forest", Draw(200, 200, ^(NSRect r) {
        Gradient(r, @[Hex(0xd4fc79), Hex(0x96e6a1), Hex(0x3a7d44)], -90);
        for(int i = 0; i < 9; i++)
        {
            CGFloat x = -10 + i * 26, h = 70 + (i * 37) % 50, base = 200;
            NSBezierPath *t = [NSBezierPath bezierPath];
            [t moveToPoint:NSMakePoint(x, base)];
            [t lineToPoint:NSMakePoint(x + 20, base - h)];
            [t lineToPoint:NSMakePoint(x + 40, base)];
            [t closePath];
            [Hex(i % 2 ? 0x1e5631 : 0x2d6a4f) setFill];
            [t fill];
        }
    }));

    LHRegisterAppImage(@"map", Draw(360, 150, ^(NSRect r) {
        [Hex(0xf2efe9) setFill];
        NSRectFill(r);
        [Hex(0xc8e6c9) setFill];
        [[NSBezierPath bezierPathWithRoundedRect:NSMakeRect(30, 20, 90, 60) xRadius:18 yRadius:18] fill];
        [Hex(0xaad3df) setFill];
        NSBezierPath *river = [NSBezierPath bezierPath];
        [river moveToPoint:NSMakePoint(0, 120)];
        [river curveToPoint:NSMakePoint(360, 100) controlPoint1:NSMakePoint(120, 80) controlPoint2:NSMakePoint(240, 150)];
        river.lineWidth = 14;
        [Hex(0xaad3df) setStroke];
        [river stroke];
        [NSColor.whiteColor setStroke];
        for(NSArray *seg in @[@[@0, @60, @360, @50], @[@200, @0, @230, @150], @[@90, @0, @140, @150], @[@0, @140, @360, @30]])
        {
            NSBezierPath *road = [NSBezierPath bezierPath];
            [road moveToPoint:NSMakePoint([seg[0] doubleValue], [seg[1] doubleValue])];
            [road lineToPoint:NSMakePoint([seg[2] doubleValue], [seg[3] doubleValue])];
            road.lineWidth = 6;
            [road stroke];
        }
        [Hex(0xff3b30) setFill];
        NSBezierPath *pin = [NSBezierPath bezierPathWithOvalInRect:NSMakeRect(196, 40, 22, 22)];
        [pin fill];
        NSBezierPath *tip = [NSBezierPath bezierPath];
        [tip moveToPoint:NSMakePoint(199, 56)];
        [tip lineToPoint:NSMakePoint(207, 72)];
        [tip lineToPoint:NSMakePoint(215, 56)];
        [tip fill];
        [NSColor.whiteColor setFill];
        [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect(203, 47, 8, 8)] fill];
    }));

    LHRegisterAppImage(@"logo", Draw(64, 64, ^(NSRect r) {
        NSBezierPath *p = [NSBezierPath bezierPathWithRoundedRect:r xRadius:14 yRadius:14];
        [[[NSGradient alloc] initWithColors:@[Hex(0x7f5af0), Hex(0x2cb67d)]] drawInBezierPath:p angle:-45];
        NSDictionary *a = @{NSFontAttributeName: LogoFont(),
                            NSForegroundColorAttributeName: NSColor.whiteColor};
        NSSize sz = [@"</>" sizeWithAttributes:a];
        [@"</>" drawAtPoint:NSMakePoint(32 - sz.width / 2, 32 - sz.height / 2) withAttributes:a];
    }));
}

@implementation LHImages

+ (void)registerImage:(NSImage *)image name:(NSString *)name
{
    LHRegisterAppImage(name, image);
}

+ (void)setProvider:(NSImage *_Nullable (^)(NSString *url))provider
{
    LHSetImageProvider(provider);
}

+ (NSImage *_Nullable (^)(NSString *url))provider
{
    return LHImageProvider();
}

+ (nullable CGImageRef)copyImageForURL:(NSString *)url
{
    return LHCopyImageForURL(url);
}

+ (CGSize)sizeForURL:(NSString *)url
{
    return LHImageSizeForURL(url);
}

+ (void)prefetchURLs:(NSArray<NSString *> *)urls completion:(nullable void (^)(void))completion
{
    NSArray<NSString *> *copied = [urls copy];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        for(NSString *url in copied)
        {
            CGImageRef image = LHCopyImageForURL(url);
            if(image)
                CGImageRelease(image);
        }
        if(completion)
            dispatch_async(dispatch_get_main_queue(), completion);
    });
}

+ (void)forgetImageWithURL:(NSString *)url
{
    LHForgetImage(url);
}

+ (BOOL)portableSymbols
{
    return LHPortableSymbols();
}

+ (void)setPortableSymbols:(BOOL)portableSymbols
{
    LHSetPortableSymbols(portableSymbols);
}

+ (void)registerDemoImages
{
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        RegisterDemoImages();
    });
}

@end
