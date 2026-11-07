#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface PointerEventsHitTests : XCTestCase
@end

@implementation PointerEventsHitTests

static NSString *Hit(NSString *html, CGPoint point)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    return [document attribute:@"data-id" atPoint:point];
}

- (void)testOverlayWithoutPointerEventsPassesClicksThrough
{
    NSString *html = @"<div style='position:relative;height:150px' data-id='wrap'><a href='https://example.com' data-id='link' style='display:block;height:150px'>link</a><div data-id='overlay' style='position:absolute;left:0;top:0;width:420px;height:150px;pointer-events:none'></div></div>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(40, 40)), @"link");
}

- (void)testOverlayWithPointerEventsStillCatchesClicks
{
    NSString *html = @"<div style='position:relative;height:150px' data-id='wrap'><a href='https://example.com' data-id='link' style='display:block;height:150px'>link</a><div data-id='overlay' style='position:absolute;left:0;top:0;width:420px;height:150px'></div></div>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(40, 40)), @"overlay");
}

- (void)testInheritedNoneCanBeTurnedBackOnByAChild
{
    NSString *html = @"<div style='pointer-events:none;height:150px' data-id='outer'><div data-id='inner' style='pointer-events:auto;height:60px'>x</div><div data-id='dead' style='height:60px'>y</div></div>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(40, 20)), @"inner");
    XCTAssertNotEqualObjects(Hit(html, CGPointMake(40, 90)), @"dead");
    XCTAssertNotEqualObjects(Hit(html, CGPointMake(40, 90)), @"outer");
}

- (void)testSVGAnchorShapesAreHitTargets
{
    NSString *html = @"<svg data-id='svg' width='400' height='150' viewBox='0 0 200 75'><rect data-id='bg' width='200' height='75' fill='#eee'/><a href='https://example.com' data-id='link'><g transform='translate(100,0)'><rect data-id='shape' x='10' y='10' width='50' height='30' fill='blue'/></g></a></svg>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(230, 40)), @"shape");
    XCTAssertEqualObjects(Hit(html, CGPointMake(20, 20)), @"bg");
    XCTAssertEqualObjects(Hit(html, CGPointMake(380, 140)), @"bg");
}

- (void)testSVGLaterShapesWinAndEmptyAreasHitTheSVG
{
    NSString *html = @"<svg data-id='svg' width='300' height='100'><circle data-id='under' cx='50' cy='50' r='40'/><a href='https://example.com' data-id='link'><circle data-id='over' cx='60' cy='50' r='20'/></a></svg>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(60, 50)), @"over");
    XCTAssertEqualObjects(Hit(html, CGPointMake(20, 50)), @"under");
    XCTAssertEqualObjects(Hit(html, CGPointMake(250, 50)), @"svg");
}

- (void)testTransformedNotesInsideZeroSizeWorldAreHitAtTheirPaintedPosition
{
    NSString *html = @"<style>body{margin:0}</style><div data-id='desk' style='position:relative;height:500px;overflow:hidden'><div style='position:absolute;width:0;height:0;transform-origin:0 0;transform:translate(40px,60px) scale(.8)'><div data-id='note' style='position:absolute;left:0;top:0;width:100px;height:100px;transform:translate(150px,50px) rotate(10deg)'>note</div></div></div>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(200, 140)), @"note");
    XCTAssertEqualObjects(Hit(html, CGPointMake(50, 50)), @"desk");
}

- (void)testHandleOutsideTransformedNoteIsHitBeforeTheNote
{
    NSString *html = @"<style>body{margin:0}</style><div data-id='desk' style='position:relative;height:500px;overflow:hidden'><div style='position:absolute;width:0;height:0;transform-origin:0 0;transform:translate(40px,60px) scale(.8)'><div data-id='note' style='position:absolute;z-index:100;display:flex;flex-direction:column;width:100px;height:100px;transform:translate(150px,50px) rotate(10deg)'>note<div data-id='handle' style='position:absolute;right:-14px;bottom:-14px;width:30px;height:30px'></div></div></div></div>";
    XCTAssertEqualObjects(Hit(html, CGPointMake(231.8, 185.4)), @"handle");
}

- (void)testScopedHitSkipsTheCompositedTransformAndKeepsChildTransforms
{
    NSString *html = @"<style>body{margin:0}</style><div style='padding:30px 10px'><div data-id='panel' style='width:160px;height:100px;transform:translate(100px,200px)'><div data-id='button' style='width:60px;height:30px;transform:translateX(20px)'>button</div></div></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    XCTAssertEqualObjects([document attribute:@"data-id" atPoint:CGPointMake(40, 40) withinAttribute:@"data-id" value:@"panel"], @"button");
    XCTAssertEqualObjects([document attribute:@"data-id" atPoint:CGPointMake(15, 40) withinAttribute:@"data-id" value:@"panel"], @"panel");
    XCTAssertNil([document attribute:@"data-id" atPoint:CGPointMake(40, 40) withinAttribute:@"data-id" value:@"missing"]);
}

- (void)testScopedHitsRespectPointerEventsVisibilityAndClipPaths
{
    NSString *html = @"<style>body{margin:0}</style><div data-id='panel' style='width:100px;height:100px;transform:translateX(200px);pointer-events:none;clip-path:circle(40px at 50px 50px)'><div data-id='button' style='width:100px;height:50px;pointer-events:auto'>button</div></div><div data-id='hidden' style='visibility:hidden;width:100px;height:100px'></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    XCTAssertEqualObjects([document attribute:@"data-id" atPoint:CGPointMake(50, 30) withinAttribute:@"data-id" value:@"panel"], @"button");
    XCTAssertNil([document attribute:@"data-id" atPoint:CGPointMake(50, 70) withinAttribute:@"data-id" value:@"panel"]);
    XCTAssertNil([document attribute:@"data-id" atPoint:CGPointMake(1, 1) withinAttribute:@"data-id" value:@"panel"]);
    XCTAssertNil([document attribute:@"data-id" atPoint:CGPointMake(50, 150) withinAttribute:@"data-id" value:@"hidden"]);
}

@end
