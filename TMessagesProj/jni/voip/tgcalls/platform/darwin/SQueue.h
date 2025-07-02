#import <Foundation/Foundation.h>

@interface SQueue : NSObject

+ (SQueue * _Nonnull)mainQueue;
+ (SQueue * _Nonnull)concurrentDefaultQueue;
+ (SQueue * _Nonnull)concurrentBackgroundQueue;

+ (SQueue * _Nonnull)wrapConcurrentNativeQueue:(dispatch_queue_t _Nonnull)nativeQueue;

- (void)dispatch:(dispatch_block_t _Nonnull)block;
- (void)dispatchSync:(dispatch_block_t _Nonnull)block;
- (void)dispatch:(dispatch_block_t _Nonnull)block synchronous:(bool)synchronous;

- (dispatch_queue_t _Nonnull)_dispatch_queue;

- (bool)isCurrentQueue;

@end
