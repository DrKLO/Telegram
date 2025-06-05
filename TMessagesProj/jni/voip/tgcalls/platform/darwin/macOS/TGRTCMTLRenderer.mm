/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "TGRTCMTLRenderer+Private.h"

#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>
#import <MetalPerformanceShaders/MetalPerformanceShaders.h>
#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "TGRTCMetalContextHolder.h"

#include "api/video/video_rotation.h"
#include "rtc_base/checks.h"


MTLFrameSize MTLAspectFitted(MTLFrameSize from, MTLFrameSize to) {
    double scale = std::min(
        from.width / std::max(1., double(to.width)),
        from.height / std::max(1., double(to.height)));
    return {
        float(std::ceil(to.width * scale)),
        float(std::ceil(to.height * scale))
    };
}

MTLFrameSize MTLAspectFilled(MTLFrameSize from, MTLFrameSize to) {
    double scale = std::max(
        to.width / std::max(1., double(from.width)),
        to.height / std::max(1., double(from.height)));
    return {
        float(std::ceil(from.width * scale)),
        float(std::ceil(from.height * scale))
    };
}




static NSString *const pipelineDescriptorLabel = @"RTCPipeline";
static NSString *const commandBufferLabel = @"RTCCommandBuffer";
static NSString *const renderEncoderLabel = @"RTCEncoder";
static NSString *const renderEncoderDebugGroup = @"RTCDrawFrame";


static TGRTCMetalContextHolder *metalContext = nil;



bool initMetal() {
    if (metalContext == nil) {
        metalContext = [[TGRTCMetalContextHolder alloc] init];
    } else if(metalContext.displayId != CGMainDisplayID()) {
        metalContext = [[TGRTCMetalContextHolder alloc] init];
    }
    return metalContext != nil;
}

static inline void getCubeVertexData(size_t frameWidth,
                                     size_t frameHeight,
                                     RTCVideoRotation rotation,
                                     float *buffer) {
  // The computed values are the adjusted texture coordinates, in [0..1].
  // For the left and top, 0.0 means no cropping and e.g. 0.2 means we're skipping 20% of the
  // left/top edge.
  // For the right and bottom, 1.0 means no cropping and e.g. 0.8 means we're skipping 20% of the
  // right/bottom edge (i.e. render up to 80% of the width/height).
    float cropLeft = 0;
    float cropRight = 1;
    float cropTop = 0;
    float cropBottom = 1;

    // These arrays map the view coordinates to texture coordinates, taking cropping and rotation
    // into account. The first two columns are view coordinates, the last two are texture coordinates.
    switch (rotation) {
      case RTCVideoRotation_0: {
        float values[16] = {-1.0, -1.0, cropLeft, cropBottom,
                             1.0, -1.0, cropRight, cropBottom,
                            -1.0,  1.0, cropLeft, cropTop,
                             1.0,  1.0, cropRight, cropTop};
        memcpy(buffer, &values, sizeof(values));
      } break;
      case RTCVideoRotation_90: {
        float values[16] = {-1.0, -1.0, cropRight, cropBottom,
                             1.0, -1.0, cropRight, cropTop,
                            -1.0,  1.0, cropLeft, cropBottom,
                             1.0,  1.0, cropLeft, cropTop};
        memcpy(buffer, &values, sizeof(values));
      } break;
      case RTCVideoRotation_180: {
        float values[16] = {-1.0, -1.0, cropRight, cropTop,
                             1.0, -1.0, cropLeft, cropTop,
                            -1.0,  1.0, cropRight, cropBottom,
                             1.0,  1.0, cropLeft, cropBottom};
        memcpy(buffer, &values, sizeof(values));
      } break;
      case RTCVideoRotation_270: {
        float values[16] = {-1.0, -1.0, cropLeft, cropTop,
                             1.0, -1.0, cropLeft, cropBottom,
                            -1.0, 1.0, cropRight, cropTop,
                             1.0, 1.0, cropRight, cropBottom};
        memcpy(buffer, &values, sizeof(values));
      } break;
    }

}

@implementation TGRTCMTLRenderer {
    __kindof CAMetalLayer *_view;

    __kindof CAMetalLayer *_foreground;

    
    TGRTCMetalContextHolder* _context;
    
    id<MTLCommandQueue> _commandQueue;
    id<MTLBuffer> _vertexBuffer;
    id<MTLBuffer> _vertexBufferRotated;
    MTLFrameSize _frameSize;
    MTLFrameSize _scaledSize;
    
    RTCVideoFrame *_frame;
    bool _frameIsUpdated;
    
    RTCVideoRotation _rotation;
    bool _rotationInited;

    id<MTLTexture> _rgbTexture;
    id<MTLTexture> _rgbScaledAndBlurredTexture;
    
    id<MTLBuffer> _vertexBuffer0;
    id<MTLBuffer> _vertexBuffer1;
    id<MTLBuffer> _vertexBuffer2;
    
    dispatch_semaphore_t _inflight1;
    dispatch_semaphore_t _inflight2;

}


@synthesize rotationOverride = _rotationOverride;

- (instancetype)init {
  if (self = [super init]) {
      _inflight1 = dispatch_semaphore_create(0);
      _inflight2 = dispatch_semaphore_create(0);

      _context = metalContext;
      _commandQueue = [_context.device newCommandQueueWithMaxCommandBufferCount:3];

      
      float vertexBufferArray[16] = {0};
      _vertexBuffer = [metalContext.device newBufferWithBytes:vertexBufferArray
                                           length:sizeof(vertexBufferArray)
                                          options:MTLResourceCPUCacheModeWriteCombined];
      float vertexBufferArrayRotated[16] = {0};
      _vertexBufferRotated = [metalContext.device newBufferWithBytes:vertexBufferArrayRotated
                                           length:sizeof(vertexBufferArrayRotated)
                                          options:MTLResourceCPUCacheModeWriteCombined];

      
      float verts[8] = {-1.0, 1.0, 1.0, 1.0, -1.0, -1.0, 1.0, -1.0};
    
      _vertexBuffer0 = [metalContext.device newBufferWithBytes:verts length:sizeof(verts) options:0];
    
      float values[8] = {0};
      
      _vertexBuffer1 = [metalContext.device newBufferWithBytes:values
                                                      length:sizeof(values)
                                                     options:0];
    
      _vertexBuffer2 = [metalContext.device newBufferWithBytes:values
                                                       length:sizeof(values)
                                                      options:0];
      
      
      
  }

  return self;
}

- (BOOL)setSingleRendering:(__kindof CAMetalLayer *)view {
  return [self setupWithView:view foreground: nil];
}
- (BOOL)setDoubleRendering:(__kindof CAMetalLayer *)view foreground:(nonnull __kindof CAMetalLayer *)foreground {
  return [self setupWithView:view foreground: foreground];
}

#pragma mark - Private

- (BOOL)setupWithView:(__kindof CAMetalLayer *)view foreground: (__kindof CAMetalLayer *)foreground {
    _view = view;
    _foreground = foreground;
    view.device = metalContext.device;
    foreground.device = metalContext.device;
    _context = metalContext;
    _rotationInited = false;
    return YES;
}
#pragma mark - Inheritance

- (id<MTLDevice>)currentMetalDevice {
  return metalContext.device;
}


- (void)uploadTexturesToRenderEncoder:(id<MTLRenderCommandEncoder>)renderEncoder {
//  RTC_NOTREACHED() << "Virtual method not implemented in subclass.";
}

- (void)getWidth:(int *)width
          height:(int *)height
         ofFrame:(nonnull RTC_OBJC_TYPE(RTCVideoFrame) *)frame {
 // RTC_NOTREACHED() << "Virtual method not implemented in subclass.";
}

- (BOOL)setupTexturesForFrame:(nonnull RTC_OBJC_TYPE(RTCVideoFrame) *)frame {
    RTCVideoRotation rotation;
    NSValue *rotationOverride = self.rotationOverride;
    if (rotationOverride) {
        [rotationOverride getValue:&rotation];
    } else {
        rotation = frame.rotation;
    }
    
    _frameIsUpdated = true;//_frame.timeStampNs != frame.timeStampNs;
    _frame = frame;
    
    int frameWidth, frameHeight;
    [self getWidth:&frameWidth
            height:&frameHeight
           ofFrame:frame];

    if (frameWidth != _frameSize.width || frameHeight != _frameSize.height || _rotation != rotation || !_rotationInited) {
        
        bool rotationIsUpdated = _rotation != rotation || !_rotationInited;
          
        _rotation = rotation;
        _frameSize.width = frameWidth;
        _frameSize.height = frameHeight;
        _frameIsUpdated = true;

      
        MTLFrameSize small;
        small.width = _frameSize.width / 4;
        small.height = _frameSize.height / 4;

        _scaledSize = MTLAspectFitted(small, _frameSize);
        _rgbTexture = [self createTextureWithUsage: MTLTextureUsageShaderRead|MTLTextureUsageRenderTarget size:_frameSize];
      
        _rgbScaledAndBlurredTexture = [self createTextureWithUsage:MTLTextureUsageShaderRead|MTLTextureUsageRenderTarget size:_scaledSize];
      
        
        if (rotationIsUpdated) {
            getCubeVertexData(frameWidth,
                              frameHeight,
                              RTCVideoRotation_0,
                              (float *)_vertexBuffer.contents);
            
            getCubeVertexData(frameWidth,
                              frameHeight,
                              rotation,
                              (float *)_vertexBufferRotated.contents);
           

            switch (rotation) {
                case RTCVideoRotation_0: {
                    float values[8] = {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0};
                    memcpy((float *)_vertexBuffer1.contents, &values, sizeof(values));
                    memcpy((float *)_vertexBuffer2.contents, &values, sizeof(values));
                } break;
                case RTCVideoRotation_90: {
                    float values[8] = {0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0};
                    memcpy((float *)_vertexBuffer1.contents, &values, sizeof(values));
                    memcpy((float *)_vertexBuffer2.contents, &values, sizeof(values));
                } break;
                case RTCVideoRotation_180: {
                    //[xLimit, yLimit, 0.0, yLimit, xLimit, 0.0, 0.0, 0.0]
                    float values[8] = {1.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0};
                    memcpy(_vertexBuffer1.contents, &values, sizeof(values));
                    memcpy(_vertexBuffer2.contents, &values, sizeof(values));
                } break;
                case RTCVideoRotation_270: {
                    float values[8] = {1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0};
                    memcpy(_vertexBuffer1.contents, &values, sizeof(values));
                    memcpy(_vertexBuffer2.contents, &values, sizeof(values));
                } break;
            }
            _rotationInited = true;
        }
    }
    return YES;
}

#pragma mark - GPU methods


- (id<MTLTexture>)createTextureWithUsage:(MTLTextureUsage) usage size:(MTLFrameSize)size {
    MTLTextureDescriptor *rgbTextureDescriptor = [MTLTextureDescriptor
                                                  texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                  width:size.width
                                                  height:size.height
                                                  mipmapped:NO];
    rgbTextureDescriptor.usage = usage;
    return [metalContext.device newTextureWithDescriptor:rgbTextureDescriptor];
}

- (id<MTLRenderCommandEncoder>)createRenderEncoderForTarget: (id<MTLTexture>)texture with: (id<MTLCommandBuffer>)commandBuffer {
    MTLRenderPassDescriptor *renderPassDescriptor = [[MTLRenderPassDescriptor alloc] init];
    renderPassDescriptor.colorAttachments[0].texture = texture;
    renderPassDescriptor.colorAttachments[0].loadAction = MTLLoadActionDontCare;
    renderPassDescriptor.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0);
    
    id<MTLRenderCommandEncoder> renderEncoder = [commandBuffer renderCommandEncoderWithDescriptor:renderPassDescriptor];
    renderEncoder.label = renderEncoderLabel;
    
    return renderEncoder;
}


- (id<MTLTexture>)convertYUVtoRGV:(id<MTLBuffer>)buffer {
    id<MTLTexture> rgbTexture = _rgbTexture;
    if (_frameIsUpdated) {
        id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
        
        id<MTLRenderCommandEncoder> renderEncoder = [self createRenderEncoderForTarget: rgbTexture with: commandBuffer];
        [renderEncoder pushDebugGroup:renderEncoderDebugGroup];
        [renderEncoder setRenderPipelineState:_context.pipelineYuvRgb];
        [renderEncoder setVertexBuffer:buffer offset:0 atIndex:0];
        [self uploadTexturesToRenderEncoder:renderEncoder];
        [renderEncoder setFragmentSamplerState:_context.sampler atIndex:0];
        
        [renderEncoder drawPrimitives:MTLPrimitiveTypeTriangleStrip
                        vertexStart:0
                        vertexCount:4
                        instanceCount:1];
        [renderEncoder popDebugGroup];
        [renderEncoder endEncoding];

        [commandBuffer commit];
    }
    
    return rgbTexture;
}

- (id<MTLTexture>)scaleAndBlur:(id<MTLTexture>)inputTexture scale:(simd_float2)scale {

    id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
    
    id<MTLRenderCommandEncoder> renderEncoder = [self createRenderEncoderForTarget: _rgbScaledAndBlurredTexture with: commandBuffer];
    [renderEncoder pushDebugGroup:renderEncoderDebugGroup];
    [renderEncoder setRenderPipelineState:_context.pipelineScaleAndBlur];

    [renderEncoder setFragmentTexture:inputTexture atIndex:0];
    
    [renderEncoder setVertexBuffer:_vertexBuffer offset:0 atIndex:0];

    [renderEncoder setFragmentBytes:&scale length:sizeof(scale) atIndex:0];
    [renderEncoder setFragmentSamplerState:_context.sampler atIndex:0];

    bool vertical = true;
    [renderEncoder setFragmentBytes:&vertical length:sizeof(vertical) atIndex:1];

    
    [renderEncoder drawPrimitives:MTLPrimitiveTypeTriangleStrip
                    vertexStart:0
                    vertexCount:4
                    instanceCount:1];
    [renderEncoder popDebugGroup];
    [renderEncoder endEncoding];

    [commandBuffer commit];
//    [commandBuffer waitUntilCompleted];
    
    return _rgbScaledAndBlurredTexture;
}

- (void)mergeYUVTexturesInTarget:(id<MTLTexture>)targetTexture foregroundTexture: (id<MTLTexture>)foregroundTexture backgroundTexture:(id<MTLTexture>)backgroundTexture scale1:(simd_float2)scale1 scale2:(simd_float2)scale2 {
    id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];

    id<MTLRenderCommandEncoder> renderEncoder = [self createRenderEncoderForTarget: targetTexture with: commandBuffer];
    [renderEncoder pushDebugGroup:renderEncoderDebugGroup];
    [renderEncoder setRenderPipelineState:_context.pipelineTransformAndBlend];
    
    
    [renderEncoder setVertexBuffer:_vertexBuffer0 offset:0 atIndex:0];
    [renderEncoder setVertexBuffer:_vertexBuffer1 offset:0 atIndex:1];
    [renderEncoder setVertexBuffer:_vertexBuffer2 offset:0 atIndex:2];
    
    [renderEncoder setFragmentTexture:foregroundTexture atIndex:0];
    [renderEncoder setFragmentTexture:backgroundTexture atIndex:1];

    [renderEncoder setFragmentBytes:&scale1 length:sizeof(scale1) atIndex:0];
    [renderEncoder setFragmentBytes:&scale2 length:sizeof(scale2) atIndex:1];
    
    [renderEncoder setFragmentSamplerState:_context.sampler atIndex:0];
    [renderEncoder setFragmentSamplerState:_context.sampler atIndex:1];
    
    [renderEncoder drawPrimitives:MTLPrimitiveTypeTriangleStrip
                    vertexStart:0
                    vertexCount:4
                    instanceCount:1];
    [renderEncoder popDebugGroup];
    [renderEncoder endEncoding];

    [commandBuffer commit];
//    [commandBuffer waitUntilCompleted];
}

- (void)doubleRender {
    id<CAMetalDrawable> background = _view.nextDrawable;
    id<CAMetalDrawable> foreground = _foreground.nextDrawable;

    _rgbTexture = [self convertYUVtoRGV:_vertexBufferRotated];

    CGSize drawableSize = _view.drawableSize;

    MTLFrameSize from;
    MTLFrameSize to;
    
    MTLFrameSize frameSize = _frameSize;
    MTLFrameSize scaledSize = _scaledSize;
    
    
    
    from.width = _view.bounds.size.width;
    from.height = _view.bounds.size.height;

    to.width = drawableSize.width;
    to.height = drawableSize.height;
    
//    bool swap = _rotation == RTCVideoRotation_90 || _rotation == RTCVideoRotation_270;

//    if (swap) {
//        frameSize.width = _frameSize.height;
//        frameSize.height = _frameSize.width;
//        scaledSize.width = _scaledSize.height;
//        scaledSize.height = _scaledSize.width;
//    }
    
    _rgbScaledAndBlurredTexture = [self scaleAndBlur:_rgbTexture scale:simd_make_float2(frameSize.width / scaledSize.width, frameSize.height/ scaledSize.height)];

    id<MTLCommandBuffer> commandBuffer_b = [_commandQueue commandBuffer];
    {
        id<MTLRenderCommandEncoder> renderEncoder = [self createRenderEncoderForTarget: background.texture with: commandBuffer_b];
        [renderEncoder pushDebugGroup:renderEncoderDebugGroup];
        [renderEncoder setRenderPipelineState:_context.pipelineScaleAndBlur];
        [renderEncoder setFragmentTexture:_rgbScaledAndBlurredTexture atIndex:0];
        [renderEncoder setVertexBuffer:_vertexBuffer offset:0 atIndex:0];
        simd_float2 scale = simd_make_float2(scaledSize.width / frameSize.width, scaledSize.height / frameSize.height);
        [renderEncoder setFragmentBytes:&scale length:sizeof(scale) atIndex:0];
        bool vertical = false;
        [renderEncoder setFragmentBytes:&vertical length:sizeof(vertical) atIndex:1];
        [renderEncoder setFragmentSamplerState:_context.sampler atIndex:0];
        [renderEncoder drawPrimitives:MTLPrimitiveTypeTriangleStrip
                        vertexStart:0
                        vertexCount:4
                        instanceCount:1];
        [renderEncoder popDebugGroup];
        [renderEncoder endEncoding];
    }
    

    id<MTLCommandBuffer> commandBuffer_f = [_commandQueue commandBuffer];
    {
        id<MTLRenderCommandEncoder> renderEncoder = [self createRenderEncoderForTarget: foreground.texture with: commandBuffer_f];
        [renderEncoder pushDebugGroup:renderEncoderDebugGroup];
        [renderEncoder setRenderPipelineState:_context.pipelineThrough];
        [renderEncoder setFragmentTexture:_rgbTexture atIndex:0];
        [renderEncoder setVertexBuffer:_vertexBuffer offset:0 atIndex:0];
        [renderEncoder setFragmentSamplerState:_context.sampler atIndex:0];
        [renderEncoder drawPrimitives:MTLPrimitiveTypeTriangleStrip
                        vertexStart:0
                        vertexCount:4
                        instanceCount:1];
        [renderEncoder popDebugGroup];
        [renderEncoder endEncoding];
    }
    

   
    
    dispatch_semaphore_t inflight = _inflight2;

    [commandBuffer_f addCompletedHandler:^(id<MTLCommandBuffer> _Nonnull) {
        dispatch_semaphore_signal(inflight);
    }];
    [commandBuffer_b addCompletedHandler:^(id<MTLCommandBuffer> _Nonnull) {
        dispatch_semaphore_signal(inflight);
    }];
    
    [commandBuffer_b addScheduledHandler:^(id<MTLCommandBuffer> _Nonnull) {
        [background present];
    }];
    [commandBuffer_f addScheduledHandler:^(id<MTLCommandBuffer> _Nonnull) {
        [foreground present];
    }];
        

    [commandBuffer_f commit];
    [commandBuffer_b commit];
    
    dispatch_semaphore_wait(inflight, DISPATCH_TIME_FOREVER);
    dispatch_semaphore_wait(inflight, DISPATCH_TIME_FOREVER);

}
- (void)singleRender {
    id<CAMetalDrawable> drawable = _view.nextDrawable;
    
    CGSize drawableSize = _view.drawableSize;

    MTLFrameSize from;
    MTLFrameSize to;
    
    MTLFrameSize frameSize = _frameSize;
    MTLFrameSize scaledSize = _scaledSize;
    
    
    
    from.width = _view.bounds.size.width;
    from.height = _view.bounds.size.height;

    to.width = drawableSize.width;
    to.height = drawableSize.height;
    
    bool swap = _rotation == RTCVideoRotation_90 || _rotation == RTCVideoRotation_270;

    if (swap) {
        frameSize.width = _frameSize.height;
        frameSize.height = _frameSize.width;
        scaledSize.width = _scaledSize.height;
        scaledSize.height = _scaledSize.width;
    }
    
    float ratio = (float)frameSize.height / (float)frameSize.width;

    
    
    MTLFrameSize viewSize = MTLAspectFilled(to, from);
    
    MTLFrameSize fitted = MTLAspectFitted(from, to);

    
    CGSize viewPortSize = CGSizeMake(viewSize.width, viewSize.height);
    

    
    id<MTLTexture> targetTexture = drawable.texture;
    
    
    CGFloat heightAspectScale = viewPortSize.height / (fitted.width * ratio);
    CGFloat widthAspectScale = viewPortSize.width / (fitted.height * (1.0/ratio));

    _rgbTexture = [self convertYUVtoRGV:_vertexBuffer];

    simd_float2 smallScale = simd_make_float2(frameSize.width / scaledSize.width, frameSize.height / scaledSize.height);
    
    _rgbScaledAndBlurredTexture = [self scaleAndBlur:_rgbTexture scale:smallScale];
    
    simd_float2 scale1 = simd_make_float2(MAX(1.0, widthAspectScale), MAX(1.0, heightAspectScale));
    
    float bgRatio_w = scaledSize.width / frameSize.width;
    float bgRatio_h = scaledSize.height / frameSize.height;

    
    
    simd_float2 scale2 = simd_make_float2(MIN(bgRatio_w, widthAspectScale * bgRatio_w), MIN(bgRatio_h, heightAspectScale * bgRatio_h));
    
    
    if (swap) {
        scale1 = simd_make_float2(MAX(1.0, heightAspectScale), MAX(1.0, widthAspectScale));
        scale2 = simd_make_float2(MIN(1, heightAspectScale * 1), MIN(bgRatio_h, widthAspectScale * bgRatio_h));
    }

    [self mergeYUVTexturesInTarget: targetTexture
                    foregroundTexture: _rgbTexture
                    backgroundTexture: _rgbScaledAndBlurredTexture
                    scale1:scale1
                    scale2:scale2];
    
    id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
    
    
    dispatch_semaphore_t inflight = _inflight1;

    [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> _Nonnull) {
        dispatch_semaphore_signal(inflight);
    }];


    [commandBuffer addScheduledHandler:^(id<MTLCommandBuffer> _Nonnull) {
        [drawable present];
    }];
    
    [commandBuffer commit];
    
    dispatch_semaphore_wait(inflight, DISPATCH_TIME_FOREVER);

//    [commandBuffer commit];
    
}

-(void)dealloc {
    dispatch_semaphore_signal(_inflight1);
    dispatch_semaphore_signal(_inflight2);
    dispatch_semaphore_signal(_inflight2);
    __block CAMetalLayer *view = _view;
    __block CAMetalLayer *foreground = _foreground;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        view = nil;
        foreground = nil;
    });
}

#pragma mark - RTCMTLRenderer

- (void)drawFrame:(RTC_OBJC_TYPE(RTCVideoFrame) *)frame {
  @autoreleasepool {
      if (frame.width != 0 && frame.height != 0) {
          if ([self setupTexturesForFrame:frame]) {
              if (_foreground) {
                  [self doubleRender];
              } else {
                  [self singleRender];
              }
          }
      }
  }
}

@end
