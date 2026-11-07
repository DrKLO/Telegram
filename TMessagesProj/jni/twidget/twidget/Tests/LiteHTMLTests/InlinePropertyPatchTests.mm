#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <random>

@interface InlinePropertyPatchTests : XCTestCase
@end

@implementation InlinePropertyPatchTests

static LHDocument *PropertyPage(NSString *html)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:nil];
    [document layoutWithMaxWidth:340 preferredWidth:340];
    return document;
}

static NSData *PropertyPixels(LHDocument *document)
{
    [document layoutWithMaxWidth:340 preferredWidth:340];
    CGImageRef image = [document copyImageWithMaxWidth:340 rect:CGRectMake(0, 0, 340, 300) scale:1];
    if(!image)
        return nil;
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static NSString *PropertyState(LHDocument *document)
{
    [document layoutWithMaxWidth:340 preferredWidth:340];
    NSMutableString *state = [NSMutableString stringWithString:document.layoutSignature ?: @""];
    for(NSString *key in @[@"1", @"2", @"3", @"4"])
    {
        NSDictionary *style = [document computedStyleForAttribute:@"data-lh-id" value:key];
        for(NSString *name in [style.allKeys sortedArrayUsingSelector:@selector(compare:)])
            [state appendFormat:@"|%@ %@=%@", key, name, style[name]];
    }
    NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *declarations = [document declarations:@[@"transform", @"opacity", @"background-color"] byAttribute:@"data-lh-id"];
    for(NSString *key in [declarations.allKeys sortedArrayUsingSelector:@selector(compare:)])
        for(NSString *name in [declarations[key].allKeys sortedArrayUsingSelector:@selector(compare:)])
            [state appendFormat:@"|d%@ %@=%@", key, name, declarations[key][name]];
    NSDictionary *bounds = [document visualBoundsByAttribute:@"data-lh-id"];
    for(NSString *key in [bounds.allKeys sortedArrayUsingSelector:@selector(compare:)])
        [state appendFormat:@"|v%@ %@", key, NSStringFromRect([bounds[key] rectValue])];
    for(int y = 6; y < 300; y += 23)
        for(int x = 7; x < 340; x += 29)
            [state appendFormat:@"|%@", [document attribute:@"data-lh-id" atPoint:CGPointMake(x, y)] ?: @"-"];
    return state;
}

static NSString *PropertyMarkup(NSString *child, NSString *cls, NSString *style, NSString *sibling)
{
    return [NSString stringWithFormat:@"<style>body{margin:0;background:#202020;color:#f5f5f5;--tint:#32ade6}.stage{position:relative;height:280px}.t{position:absolute;left:30px;top:40px;width:90px;height:60px;background:#5856d6;border-radius:6px}.alt{transform:rotate(5deg) !important;opacity:.6}.big{width:120px}.child{margin:8px;padding:4px;%@}.s{position:absolute;left:180px;top:120px;width:60px;height:40px;background:#ff9500}</style><div class=stage><div class=\"%@\" data-lh-id=\"1\" style=\"%@\"><div class=child data-lh-id=\"3\">a <b>b</b></div></div><div class=s data-lh-id=\"2\" style=\"%@\"><span data-lh-id=\"4\">s</span></div></div>", child, cls, style, sibling];
}

- (void)testRandomInlinePropertySequencesMatchFreshDocuments
{
    NSArray<NSString *> *transforms = @[@"translate(10px, 20px)", @"translate(12.5px, -4px) rotate(30deg) scale(.8)", @"rotate(-15deg)", @"scale(1.4)", @"none", @"matrix(1,0,0,1,40,10)", @"translate(1em, 2em)", @"translate(50%, 10%)", @"garbage(1)", @"translate(calc(10px + 5px), 0)", @"rotate(90deg) translate(-20px, 3px)"];
    NSArray<NSString *> *opacities = @[@"1", @".5", @"0", @"0.25", @"inherit", @"bogus", @"calc(.3 + .2)"];
    NSArray<NSString *> *backgrounds = @[@"#34c759", @"rgba(255,0,0,.4)", @"currentColor", @"transparent", @"hsl(200, 80%, 58%)", @"inherit", @"invalid"];
    NSArray<NSString *> *others = @[@"width:110px", @"color:#ff2d55", @"padding:3px", @"left:60px", @"background:#0a84ff", @"transform-origin:0 0", @"--x:1", @"background-color:var(--tint)", @"opacity:.7!important", @"transform:translate(3px,3px) !important", @"box-shadow:0 2px 6px #000", @"all:initial"];
    NSArray<NSString *> *children = @[@"", @"transform:inherit", @"opacity:inherit", @"background-color:inherit", @"background:inherit"];
    NSArray<NSString *> *names = @[@"transform", @"opacity", @"background-color"];
    NSArray<NSDictionary<NSString *, NSString *> *> *casings = @[@{}, @{@"transform": @"Transform", @"opacity": @"OPACITY", @"background-color": @"Background-Color"}, @{@"transform": @"tRaNsFoRm", @"background-color": @"background-COLOR"}];
    for(NSDictionary<NSString *, NSString *> *casing in casings)
    for(NSString *child in children)
    {
        std::mt19937 random(static_cast<unsigned>(child.length * 7919 + 17 + casing.count * 104729));
        auto pick = [&random](NSArray *list) { return list[random() % list.count]; };
        NSMutableDictionary<NSString *, NSString *> *props = [@{@"transform": transforms[0], @"opacity": @"1", @"background-color": backgrounds[0]} mutableCopy];
        NSMutableArray<NSString *> *extra = [NSMutableArray array];
        NSString *cls = @"t";
        NSString *sibling = @"transform:translate(0px,0px)";
        auto compose = [&]() {
            NSMutableArray<NSString *> *parts = [NSMutableArray array];
            for(NSString *name in names)
                if(props[name])
                    [parts addObject:[NSString stringWithFormat:@"%@:%@", casing[name] ?: name, props[name]]];
            [parts addObjectsFromArray:extra];
            return [parts componentsJoinedByString:@";"];
        };
        LHDocument *previous = PropertyPage(PropertyMarkup(child, cls, compose(), sibling));
        for(int step = 0; step < 70; step++)
        {
            const unsigned roll = random() % 20;
            NSString *name = pick(names);
            if(roll < 10)
                props[name] = pick(name == names[0] ? transforms : name == names[1] ? opacities : backgrounds);
            else if(roll < 12)
            {
                props[names[0]] = pick(transforms);
                props[names[2]] = pick(backgrounds);
            } else if(roll < 13)
                [props removeObjectForKey:name];
            else if(roll < 15)
            {
                if(extra.count && random() % 2)
                    [extra removeObjectAtIndex:random() % extra.count];
                else
                    [extra addObject:pick(others)];
            } else if(roll < 16)
                cls = [cls isEqualToString:@"t"] ? @"t alt" : random() % 2 ? @"t big" : @"t";
            else if(roll < 18)
                sibling = random() % 2 ? [NSString stringWithFormat:@"transform:%@", pick(transforms)] : [NSString stringWithFormat:@"opacity:%@;background-color:%@", pick(opacities), pick(backgrounds)];
            else
            {
                props[name] = pick(name == names[0] ? transforms : name == names[1] ? opacities : backgrounds);
                cls = random() % 2 ? @"t alt" : @"t";
            }
            NSString *html = PropertyMarkup(child, cls, compose(), sibling);
            LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
            LHDocument *fresh = PropertyPage(html);
            XCTAssertEqualObjects(PropertyState(patched), PropertyState(fresh), @"child=%@ step=%d %@", child, step, html);
            XCTAssertEqualObjects(PropertyPixels(patched), PropertyPixels(fresh), @"child=%@ step=%d %@", child, step, html);
            previous = patched;
        }
    }
}

- (void)testCapitalisedPropertyNamesWithInheritingChildMatchFreshDocuments
{
    NSArray<NSString *> *styles = @[@"Transform:scale(2)", @"Transform:scale(1)", @"TRANSFORM:rotate(10deg);Background-Color:#34c759", @"transform:scale(2);Opacity:.5", @"Background-Color:#ff2d55", @"Opacity:.3;Transform:translate(4px,2px)", @"Transform:scale(2)"];
    for(NSString *first in @[@"transform", @"Transform"])
    {
        NSString *a = [NSString stringWithFormat:@"%@:scale(1.4);opacity:0;width:110px;transform:translate(3px,3px) !important", first];
        NSString *b = [NSString stringWithFormat:@"%@:scale(1.4);opacity:0;width:110px", first];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:PropertyMarkup(@"", @"t", b, @"opacity:1") css:nil previous:PropertyPage(PropertyMarkup(@"", @"t", a, @"opacity:1"))];
        XCTAssertEqualObjects(PropertyState(patched), PropertyState(PropertyPage(PropertyMarkup(@"", @"t", b, @"opacity:1"))), @"duplicate %@", first);
    }
    for(NSString *child in @[@"background:inherit", @"background-color:inherit", @"opacity:inherit", @"transform:inherit"])
    {
        LHDocument *previous = PropertyPage(PropertyMarkup(child, @"t", @"transform:none", @"opacity:1"));
        for(NSString *style in styles)
        {
            NSString *html = PropertyMarkup(child, @"t", style, @"opacity:1");
            LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
            LHDocument *fresh = PropertyPage(html);
            XCTAssertEqualObjects(PropertyState(patched), PropertyState(fresh), @"child=%@ %@", child, style);
            XCTAssertEqualObjects(PropertyPixels(patched), PropertyPixels(fresh), @"child=%@ %@", child, style);
            previous = patched;
        }
    }
}

- (void)testRemovingTransformOriginRestoresTheDefaultOrigin
{
    for(NSArray<NSString *> *pair in @[@[@"tRaNsFoRm", @"background-COLOR"], @[@"transform", @"background-color"], @[@"Transform", @"background-color"], @[@"transform", @"Background-color"]])
    for(NSString *cls in @[@"t alt", @"t"])
    {
        NSString *a = [NSString stringWithFormat:@"%@:translate(12.5px, -4px) rotate(30deg) scale(.8);opacity:inherit;%@:invalid;transform-origin:0 0", pair[0], pair[1]];
        NSString *b = [NSString stringWithFormat:@"%@:translate(12.5px, -4px) rotate(30deg) scale(.8);opacity:inherit;%@:invalid", pair[0], pair[1]];
        LHDocument *patched = [[LHDocument alloc] initWithHTML:PropertyMarkup(@"", cls, b, @"opacity:1") css:nil previous:PropertyPage(PropertyMarkup(@"", cls, a, @"opacity:1"))];
        XCTAssertEqualObjects(PropertyState(patched), PropertyState(PropertyPage(PropertyMarkup(@"", cls, b, @"opacity:1"))), @"%@ %@ %@", pair[0], pair[1], cls);
    }
}

- (void)testTransformOnlyChangesTakeThePatchPath
{
    LHDocument *previous = PropertyPage(PropertyMarkup(@"", @"t", @"background-color:#34c759;transform:translate(1px,2px)", @"opacity:0.4"));
    for(int step = 0; step < 20; step++)
    {
        NSString *html = PropertyMarkup(@"", @"t", [NSString stringWithFormat:@"background-color:#34c759;transform:translate(%dpx,2px) rotate(%ddeg)", step * 3, step * 7], [NSString stringWithFormat:@"opacity:%g", 0.5 + step / 50.0]);
        LHDocument *patched = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
        XCTAssertTrue(patched.reusedPrevious);
        XCTAssertEqualObjects(PropertyState(patched), PropertyState(PropertyPage(html)), @"step=%d", step);
        XCTAssertEqualObjects(PropertyPixels(patched), PropertyPixels(PropertyPage(html)), @"step=%d", step);
        previous = patched;
    }
}

@end
