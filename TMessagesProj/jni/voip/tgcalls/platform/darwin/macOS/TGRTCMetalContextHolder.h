//
//  TGRTCMetalContextHolder.h
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.06.2021.
//  Copyright © 2021 Mikhail Filimonov. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <MetalKit/MetalKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface TGRTCMetalContextHolder : NSObject
-(id __nullable)init;
-(id<MTLDevice>)device;
-(id<MTLRenderPipelineState>)pipelineYuvRgb;
-(id<MTLRenderPipelineState>)pipelineTransformAndBlend;
-(id<MTLRenderPipelineState>)pipelineScaleAndBlur;
-(id<MTLRenderPipelineState>)pipelineThrough;
-(id<MTLSamplerState>)sampler;
-(CGDirectDisplayID)displayId;
@end

NS_ASSUME_NONNULL_END
