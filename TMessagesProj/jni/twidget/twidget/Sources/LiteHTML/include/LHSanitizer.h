#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The rules every document is sanitized with (an allowlist validator). By default links may use
/// `http`, `https`, `mailto` and `#fragment`, and images only `data:image/…` and the built-in `icon:` set. Hosts add their own schemes here;
/// URLs with them reach the host's link handler or image provider, never the network.
@interface LHSanitizer : NSObject

+ (void)setLinkSchemes:(NSArray<NSString *> *)linkSchemes imageSchemes:(NSArray<NSString *> *)imageSchemes;
+ (void)addImageScheme:(NSString *)scheme;

- (instancetype)init NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
