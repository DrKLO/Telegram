//
//  DesktopCaptureSourceView.h
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>


NS_ASSUME_NONNULL_BEGIN

@protocol VideoSourceMac
-(NSString *)deviceIdKey;
-(NSString *)title;
-(NSString *)uniqueKey;
-(BOOL)isEqual:(id)another;
@end

@interface DesktopCaptureSourceDataMac : NSObject
@property CGSize aspectSize;
@property double fps;
@property bool captureMouse;
-(id)initWithSize:(CGSize)size fps:(double)fps captureMouse:(bool)captureMouse;

-(NSString *)cachedKey;
@end

@interface DesktopCaptureSourceMac : NSObject <VideoSourceMac>
-(long)uniqueId;
-(BOOL)isWindow;
@end

@interface DesktopCaptureSourceScopeMac : NSObject
@property(nonatomic, strong, readonly) DesktopCaptureSourceDataMac *data;
@property(nonatomic, strong, readonly) DesktopCaptureSourceMac *source;
-(id)initWithSource:(DesktopCaptureSourceMac *)source data:(DesktopCaptureSourceDataMac *)data;
 -(NSString *)cachedKey;
@end

@interface DesktopCaptureSourceManagerMac : NSObject

-(instancetype)init_s;
-(instancetype)init_w;
-(NSArray<DesktopCaptureSourceMac *> *)list;

-(NSView *)createForScope:(DesktopCaptureSourceScopeMac *)scope;
-(void)start:(DesktopCaptureSourceScopeMac *)scope;
-(void)stop:(DesktopCaptureSourceScopeMac *)scope;

@end

NS_ASSUME_NONNULL_END
