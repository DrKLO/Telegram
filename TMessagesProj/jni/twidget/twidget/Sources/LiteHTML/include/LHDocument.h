#import <Cocoa/Cocoa.h>
#import "LHTextFragment.h"

NS_ASSUME_NONNULL_BEGIN

typedef struct LHKeyedRect {
    NSInteger key;
    CGRect rect;
} LHKeyedRect;

@class LHGPURenderer;

@interface LHDisplayList : NSObject
@property (readonly) BOOL gpuSupported;

- (instancetype)init NS_UNAVAILABLE;

- (NSArray<NSValue *> *)changedRectsSince:(LHDisplayList *)previous NS_SWIFT_NAME(changedRects(since:));
- (CGFloat)unchangedBottomSince:(LHDisplayList *)previous previousHeight:(CGFloat)previousHeight height:(CGFloat)height below:(CGFloat)top NS_SWIFT_NAME(unchangedBottom(since:previousHeight:height:below:));

@end

@interface LHSVGShape : NSObject
@property (nonatomic, readonly) CGPathRef path;
@property (nonatomic, readonly, nullable) CGColorRef fillColor;
@property (nonatomic, readonly, nullable) NSArray *gradientColors;
@property (nonatomic, readonly, nullable) NSArray<NSNumber *> *gradientLocations;
@property (nonatomic, readonly) CGPoint gradientStart;
@property (nonatomic, readonly) CGPoint gradientEnd;
@property (nonatomic, readonly) BOOL evenOdd;
@property (nonatomic, readonly, nullable) CGColorRef strokeColor;
@property (nonatomic, readonly) CGFloat lineWidth;
@property (nonatomic, readonly) int lineCap;
@property (nonatomic, readonly) int lineJoin;
@property (nonatomic, readonly) CGFloat miterLimit;
@end

@interface LHSVGShapes : NSObject
@property (nonatomic, readonly) CGRect frame;
@property (nonatomic, readonly) NSArray<LHSVGShape *> *shapes;
@end

@interface LHBackgroundTile : NSObject
@property (nonatomic, readonly) CGImageRef image;
@property (nonatomic, readonly) CGRect tile;
@property (nonatomic, readonly) CGRect area;
@property (nonatomic, readonly) CGRect clip;
- (CGFloat)radius:(NSInteger)index;
@end

@interface LHDocument : NSObject

- (instancetype)initWithHTML:(NSString *)html css:(nullable NSString *)css NS_DESIGNATED_INITIALIZER;
/// Groups glyph drawing of non-overlapping text runs into one call per font and colour while drawing tiles.
+ (void)setTextBatchingEnabled:(BOOL)enabled;
+ (void)setExactGroupBoundsEnabled:(BOOL)enabled;
+ (long)shadowLineBatches;
+ (long)sourceFilterCacheHits;
+ (void)setSourceFilterCacheEnabled:(BOOL)enabled;
+ (void)setSVGGroupCacheEnabled:(BOOL)enabled;
/// Vector shapes of the <svg> whose `attribute` equals `value`, in document coordinates, or nil when the SVG uses
/// anything beyond solid or axis linear-gradient fills and plain strokes.
- (nullable LHSVGShapes *)svgShapesWithAttribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth NS_SWIFT_NAME(svgShapes(attribute:value:maxWidth:));
/// Builds the next frame of `previous`. When only inline `style` values and text changed, the previous
/// document's tree is patched in place and handed over (much cheaper than parsing); `previous` then
/// rebuilds itself from its own HTML if it is used again. Otherwise this parses `html` from scratch.
- (instancetype)initWithHTML:(NSString *)html css:(nullable NSString *)css previous:(nullable LHDocument *)previous;
- (instancetype)initWithHTML:(NSString *)html css:(nullable NSString *)css previous:(nullable LHDocument *)previous viewportSize:(CGSize)viewportSize NS_DESIGNATED_INITIALIZER;
@property (readonly) CGSize viewportSize;
@property (readonly) BOOL dependsOnViewport;
/// Patches `previous` in place with new inline style values, keyed by the value of `attribute`, without parsing HTML.
/// Each value must be the exact `style` attribute text the full HTML would carry, and every element must already have a
/// style attribute. Runs the same checks as an HTML patch and returns nil, leaving `previous` usable, when it can't apply.
- (nullable instancetype)initWithPrevious:(LHDocument *)previous styles:(NSDictionary<NSString *, NSString *> *)styles attribute:(NSString *)attribute css:(nullable NSString *)css NS_DESIGNATED_INITIALIZER;
/// Patches `previous` in place with new attribute values and texts. `changes` holds NUL-terminated triples: the value of
/// `attribute` naming the element, an attribute name or the empty name for the text of an element whose only child is one
/// text run, and the value. Each value must be the exact text the full HTML would carry there, and every changed attribute
/// and text must already be there. Returns nil, leaving `previous` usable, when it can't apply.
- (nullable instancetype)initWithPrevious:(LHDocument *)previous changes:(NSData *)changes attribute:(NSString *)attribute css:(nullable NSString *)css NS_DESIGNATED_INITIALIZER;
/// Whether this document was made by patching the previous one.
/// Fonts resolved and cached for every document in the process; fonts no document uses are dropped past a bound.
+ (NSUInteger)cachedFontCount;
+ (NSFont *)fontForFamilies:(NSString *)families size:(CGFloat)size weight:(NSInteger)weight italic:(BOOL)italic NS_SWIFT_NAME(font(families:size:weight:italic:));
+ (NSUInteger)rasterCacheBytes;
+ (void)purgeCaches;
@property (class, nonatomic) BOOL simpleTextEnabled;
+ (NSDictionary<NSString*, NSNumber*>*)simpleTextStats;
+ (NSUInteger)textMeasureCount;
+ (NSDictionary<NSString*, NSNumber*>*)layoutCacheStats;
@property (readonly) BOOL reusedPrevious;
@property (readonly) BOOL reusedLayout;
@property (readonly, nullable) NSArray<NSValue *> *layoutDirtyRects;
/// After a paint-only patch, the document rects that hold every pixel it can have changed; nil when the change may
/// paint anywhere else, so the caller has to diff display lists.
@property (readonly, nullable) NSArray<NSValue *> *patchDirtyRects;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, readonly, copy) NSString *html;
@property (nonatomic) BOOL clipCulling;
@property (nonatomic) BOOL subtreeCulling;
@property (nonatomic) BOOL controlsActive;
@property (nonatomic, nullable) CGColorSpaceRef colorSpace;

- (CGSize)layoutWithMaxWidth:(CGFloat)maxWidth;
- (CGSize)layoutWithMaxWidth:(CGFloat)maxWidth preferredWidth:(CGFloat)preferredWidth NS_SWIFT_NAME(layout(maxWidth:preferredWidth:));
@property (readonly) CGSize size;

- (void)drawInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip;
- (void)drawInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip baseTransform:(CGAffineTransform)baseTransform;
- (BOOL)drawIfCurrentInContext:(CGContextRef)ctx origin:(CGPoint)origin clip:(CGRect)clip;
- (nullable CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImage(maxWidth:rect:scale:));
- (nullable CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale excluding:(NSSet<NSString *> *)values attribute:(NSString *)attribute CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImage(maxWidth:rect:scale:excluding:attribute:));
- (nullable CGImageRef)copyImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale base:(CGImageRef)base dirty:(CGRect)dirty CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImage(maxWidth:rect:scale:base:dirty:));

- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth NS_SWIFT_NAME(displayList(maxWidth:));
/// Records with boxes snapped to the device pixels of `scale`, as drawing into a context of that scale does.
- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth scale:(CGFloat)scale NS_SWIFT_NAME(displayList(maxWidth:scale:));
- (LHDisplayList *)displayListWithMaxWidth:(CGFloat)maxWidth gpu:(LHGPURenderer *)renderer scale:(CGFloat)scale NS_SWIFT_NAME(displayList(maxWidth:gpu:scale:));
- (CGRect)boundsOfElementsMatching:(NSString *)selector NS_SWIFT_NAME(bounds(matching:));
- (void)setPaintExclusionCacheDisabled:(BOOL)disabled;
@property (readonly) NSDictionary<NSString *, NSNumber *> *paintExclusionCacheStats;
- (void)setPaintExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute NS_SWIFT_NAME(setPaintExclusions(_:attribute:));
- (void)setTextPaintExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute NS_SWIFT_NAME(setTextPaintExclusions(_:attribute:));
/// Elements drawn by layers instead of tiles, keyed by the paint-exclusion attribute: skipped when drawing tiles but drawn into snapshots.
- (void)setLayerExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute NS_SWIFT_NAME(setLayerExclusions(_:attribute:));
@property (readonly) NSSet<NSString *> *layerExclusions;
- (void)setBackgroundImageExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute NS_SWIFT_NAME(setBackgroundImageExclusions(_:attribute:));
- (nullable CGImageRef)copyTileImageWithMaxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale CF_RETURNS_RETAINED NS_SWIFT_NAME(copyTileImage(maxWidth:rect:scale:));
- (nullable CGImageRef)copyImageWithBackgroundPositionX:(double)number percent:(BOOL)percent attribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth rect:(CGRect)rect scale:(CGFloat)scale CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImage(backgroundPositionX:percent:attribute:value:maxWidth:rect:scale:));
@property (readonly) NSInteger restoreMismatches;
- (nullable LHBackgroundTile *)backgroundTileWithAttribute:(NSString *)attribute value:(NSString *)value maxWidth:(CGFloat)maxWidth scale:(CGFloat)scale NS_SWIFT_NAME(backgroundTile(attribute:value:maxWidth:scale:));
- (void)setScrollContentExclusions:(NSSet<NSString *> *)values attribute:(NSString *)attribute NS_SWIFT_NAME(setScrollContentExclusions(_:attribute:));
- (NSDictionary<NSString *, NSDictionary<NSString *, id> *> *)scrollersByAttribute:(NSString *)attribute NS_SWIFT_NAME(scrollers(byAttribute:));
- (nullable NSDictionary<NSString *, NSNumber *> *)textareaMetricsWithAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(textareaMetrics(attribute:value:));
- (nullable NSDictionary<NSString *, NSNumber *> *)offsetMetricsWithAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(offsetMetrics(attribute:value:));
- (NSArray<NSValue *> *)lineBoxesWithAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(lineBoxes(attribute:value:));
- (CGPoint)setScrollOffset:(CGPoint)offset attribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(setScrollOffset(_:attribute:value:));
- (nullable CGImageRef)copyScrollContentWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect)rect scale:(CGFloat)scale CF_RETURNS_RETAINED NS_SWIFT_NAME(copyScrollContent(attribute:value:rect:scale:));
- (nullable CGImageRef)copyScrollContentIfCurrentWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect)rect scale:(CGFloat)scale shouldContinue:(BOOL (NS_NOESCAPE ^)(void))shouldContinue CF_RETURNS_RETAINED NS_SWIFT_NAME(copyScrollContentIfCurrent(attribute:value:rect:scale:shouldContinue:));
- (CGRect)rasterBoundsOfElementWithAttribute:(NSString *)attribute value:(NSString *)value bareShadows:(BOOL)bareShadows NS_SWIFT_NAME(rasterBoundsOfElement(attribute:value:bareShadows:));
- (nullable CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkBounds borderBox:(CGRect *)borderBox CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImageOfElement(attribute:value:scale:inkBounds:borderBox:));
- (nullable CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkBounds borderBox:(CGRect *)borderBox radii:(nullable CGFloat *)radii CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImageOfElement(attribute:value:scale:inkBounds:borderBox:radii:));
- (nullable CGImageRef)copyImageOfElementWithAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale inkBounds:(CGRect *)inkBounds borderBox:(CGRect *)borderBox radii:(nullable CGFloat *)radii bareShadows:(BOOL)bareShadows CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImageOfElement(attribute:value:scale:inkBounds:borderBox:radii:bareShadows:));
- (nullable NSData *)localPaintKeyForAttribute:(NSString *)attribute value:(NSString *)value scale:(CGFloat)scale NS_SWIFT_NAME(localPaintKey(attribute:value:scale:));
- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value isolatedInRect:(CGRect)rect NS_SWIFT_NAME(isElement(attribute:value:isolatedIn:));
- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect NS_SWIFT_NAME(isElement(attribute:value:unobscuredIn:));
- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring NS_SWIFT_NAME(isElement(attribute:value:unobscuredIn:ignoring:));
- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring clipped:(BOOL)clipped NS_SWIFT_NAME(isElement(attribute:value:unobscuredIn:ignoring:clipped:));
- (nullable NSArray<NSString *> *)elementsObscuringAttribute:(NSString *)attribute value:(NSString *)value inRect:(CGRect)rect ignoring:(NSSet<NSString *> *)ignoring clipped:(BOOL)clipped NS_SWIFT_NAME(elementsObscuring(attribute:value:in:ignoring:clipped:));
- (BOOL)isElementWithAttribute:(NSString *)attribute value:(NSString *)value unobscuredInRect:(CGRect)rect ignoringSet:(NSObject *)ignoring clipped:(BOOL)clipped NS_SWIFT_NAME(isElement(attribute:value:unobscuredIn:ignoringSet:clipped:));
- (nullable NSArray<NSString *> *)elementsObscuringAttribute:(NSString *)attribute value:(NSString *)value inRect:(CGRect)rect ignoringSet:(NSObject *)ignoring clipped:(BOOL)clipped NS_SWIFT_NAME(elementsObscuring(attribute:value:in:ignoringSet:clipped:));
- (BOOL)clipOfElementWithAttribute:(NSString *)attribute value:(NSString *)value rect:(CGRect *)rect radii:(CGFloat *)radii NS_SWIFT_NAME(clipOfElement(attribute:value:rect:radii:));
- (BOOL)backgroundColorIsLocalWithAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(backgroundColorIsLocal(attribute:value:));
- (NSDictionary<NSString *, NSNumber *> *)paintOrderOfAttribute:(NSString *)attribute values:(NSSet<NSString *> *)values NS_SWIFT_NAME(paintOrder(attribute:values:));
- (NSDictionary<NSString *, NSNumber *> *)paintOrderOfAttribute:(NSString *)attribute values:(NSSet<NSString *> *)values includingScrollContent:(BOOL)includingScrollContent NS_SWIFT_NAME(paintOrder(attribute:values:includingScrollContent:));
- (nullable NSString *)attribute:(NSString *)name atPoint:(CGPoint)point NS_SWIFT_NAME(attribute(_:at:));
- (nullable NSString *)attribute:(NSString *)name atPoint:(CGPoint)point withinAttribute:(nullable NSString *)attribute value:(nullable NSString *)value NS_SWIFT_NAME(attribute(_:at:withinAttribute:value:));
- (NSDictionary<NSString *, NSValue *> *)boundsByAttribute:(NSString *)name NS_SWIFT_NAME(bounds(byAttribute:));
- (NSDictionary<NSString *, NSValue *> *)visualBoundsByAttribute:(NSString *)name NS_SWIFT_NAME(visualBounds(byAttribute:));
- (NSData *)keyedBoundsByAttribute:(NSString *)name transformed:(BOOL)transformed NS_SWIFT_NAME(keyedBounds(byAttribute:transformed:));
- (NSData *)keyedBoundsByAttribute:(NSString *)name transformed:(BOOL)transformed lineBoxes:(BOOL)lineBoxes NS_SWIFT_NAME(keyedBounds(byAttribute:transformed:lineBoxes:));
@property (readonly, copy) NSString *layoutSignature;
- (NSDictionary<NSString *, NSString *> *)parentsByAttribute:(NSString *)name NS_SWIFT_NAME(parents(byAttribute:));
- (NSDictionary<NSString *, NSString *> *)computedStyleForAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(computedStyle(attribute:value:));
- (NSDictionary<NSString *, NSString *> *)computedStyleForAttribute:(NSString *)attribute value:(NSString *)value pseudo:(nullable NSString *)pseudo NS_SWIFT_NAME(computedStyle(attribute:value:pseudo:));
- (nullable NSString *)customProperty:(NSString *)property attribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(customProperty(_:attribute:value:));
- (nullable NSFont *)fontWithAttribute:(NSString *)attribute value:(NSString *)value lineHeight:(CGFloat *)lineHeight baseline:(CGFloat *)baseline NS_SWIFT_NAME(font(attribute:value:lineHeight:baseline:));
- (nullable NSDictionary<NSString *, NSNumber *> *)svgGeometry:(NSString *)method at:(double)distance attribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(svgGeometry(_:at:attribute:value:));
- (CGRect)svgBoxWithAttribute:(NSString *)attribute value:(NSString *)value NS_SWIFT_NAME(svgBox(attribute:value:));
/// Skips elements that are not rendered (display: none on them or an ancestor): CSS animations and transitions do not
/// run there, and restart when the element is displayed again.
- (NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute NS_SWIFT_NAME(declarations(_:byAttribute:));
- (NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute requiringAny:(NSArray<NSString *> *)required NS_SWIFT_NAME(declarations(_:byAttribute:requiringAny:));
- (nullable NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *)declarations:(NSArray<NSString *> *)names byAttribute:(NSString *)attribute requiringAny:(NSArray<NSString *> *)required changedSince:(uint64_t)known generation:(uint64_t *)generation NS_SWIFT_NAME(declarations(_:byAttribute:requiringAny:changedSince:generation:));

- (NSArray<LHTextFragment *> *)textFragmentsWithMaxWidth:(CGFloat)maxWidth NS_SWIFT_NAME(textFragments(maxWidth:));
- (NSArray<LHTextFragment *> *)selectableTextFragments NS_SWIFT_NAME(selectableTextFragments());

- (BOOL)mouseMoved:(CGPoint)point;
- (BOOL)mouseDown:(CGPoint)point;
- (BOOL)mouseUp:(CGPoint)point;
- (BOOL)mouseExited;

@property (readonly, copy) NSString *cursor;

@property (nonatomic, copy, nullable) void (^anchorHandler)(NSString *url);
@property (nonatomic, copy, nullable) void (^actionHandler)(NSString *action, NSString *_Nullable value);

@property (readonly) double parseMilliseconds;
@property (readonly) double layoutMilliseconds;

@property (readonly) NSDictionary<NSString *, NSNumber *> *sanitizerCounts;
@property (readonly) NSArray<NSString *> *sanitizerDetails;

@end

NS_ASSUME_NONNULL_END
