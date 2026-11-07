#import <Cocoa/Cocoa.h>

NS_ASSUME_NONNULL_BEGIN

@interface LHImages : NSObject

+ (void)registerImage:(NSImage *)image name:(NSString *)name;
+ (void)setProvider:(nullable NSImage *_Nullable (^)(NSString *url))provider;
@property (class, readonly, nullable) NSImage *_Nullable (^provider)(NSString *url);
+ (void)forgetImageWithURL:(NSString *)url;
+ (nullable CGImageRef)copyImageForURL:(NSString *)url CF_RETURNS_RETAINED NS_SWIFT_NAME(copyImage(forURL:));
+ (CGSize)sizeForURL:(NSString *)url NS_SWIFT_NAME(size(forURL:));
+ (void)prefetchURLs:(NSArray<NSString *> *)urls completion:(nullable void (^)(void))completion;
+ (void)registerDemoImages;
@property (class, nonatomic) BOOL portableSymbols;

@end

NS_ASSUME_NONNULL_END
