#import <Cocoa/Cocoa.h>

NS_ASSUME_NONNULL_BEGIN

@interface LHTextFragment : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, readonly, copy) NSString *text;
@property (nonatomic, readonly) CGRect frame;

- (NSUInteger)characterIndexForX:(CGFloat)x NS_SWIFT_NAME(characterIndex(atX:));
- (CGFloat)xForCharacterIndex:(NSUInteger)index NS_SWIFT_NAME(x(forCharacterIndex:));

@end

NS_ASSUME_NONNULL_END
