//
//  SQueueLocalObject.h
//  SSignalKit
//
//  Created by Mikhail Filimonov on 13.01.2021.
//  Copyright © 2021 Telegram. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "SQueue.h"
NS_ASSUME_NONNULL_BEGIN

@interface SQueueLocalObject : NSObject
-(id)initWithQueue:(SQueue *)queue generate:(id (^)(void))next;
-(void)with:(void (^)(id object))f;
@end

NS_ASSUME_NONNULL_END

