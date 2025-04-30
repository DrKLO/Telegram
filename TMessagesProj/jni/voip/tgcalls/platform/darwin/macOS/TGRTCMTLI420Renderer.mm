/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "TGRTCMTLI420Renderer.h"

#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>

#import "base/RTCI420Buffer.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"

#import "TGRTCMTLRenderer+Private.h"


@implementation TGRTCMTLI420Renderer {
  // Textures.
  id<MTLTexture> _yTexture;
  id<MTLTexture> _uTexture;
  id<MTLTexture> _vTexture;

  MTLTextureDescriptor *_descriptor;
  MTLTextureDescriptor *_chromaDescriptor;

  int _width;
  int _height;
  int _chromaWidth;
  int _chromaHeight;
}



- (void)getWidth:(nonnull int *)width
          height:(nonnull int *)height
         ofFrame:(nonnull RTC_OBJC_TYPE(RTCVideoFrame) *)frame {
  *width = frame.width;
  *height = frame.height;
}


- (BOOL)setupTexturesForFrame:(nonnull RTC_OBJC_TYPE(RTCVideoFrame) *)frame {
  if (![super setupTexturesForFrame:frame]) {
    return NO;
  }

  id<MTLDevice> device = [self currentMetalDevice];
  if (!device) {
    return NO;
  }

  id<RTC_OBJC_TYPE(RTCI420Buffer)> buffer = [frame.buffer toI420];

  // Luma (y) texture.
  if (!_descriptor || _width != frame.width || _height != frame.height) {
    _width = frame.width;
    _height = frame.height;
    _descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatR8Unorm
                                                                     width:_width
                                                                    height:_height
                                                                 mipmapped:NO];
    _descriptor.usage = MTLTextureUsageShaderRead;
    _yTexture = [device newTextureWithDescriptor:_descriptor];
  }

  // Chroma (u,v) textures
  [_yTexture replaceRegion:MTLRegionMake2D(0, 0, _width, _height)
               mipmapLevel:0
                 withBytes:buffer.dataY
               bytesPerRow:buffer.strideY];

  if (!_chromaDescriptor || _chromaWidth != frame.width / 2 || _chromaHeight != frame.height / 2) {
    _chromaWidth = frame.width / 2;
    _chromaHeight = frame.height / 2;
    _chromaDescriptor =
        [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatR8Unorm
                                                           width:_chromaWidth
                                                          height:_chromaHeight
                                                       mipmapped:NO];
    _chromaDescriptor.usage = MTLTextureUsageShaderRead;
    _uTexture = [device newTextureWithDescriptor:_chromaDescriptor];
    _vTexture = [device newTextureWithDescriptor:_chromaDescriptor];
  }

  [_uTexture replaceRegion:MTLRegionMake2D(0, 0, _chromaWidth, _chromaHeight)
               mipmapLevel:0
                 withBytes:buffer.dataU
               bytesPerRow:buffer.strideU];
  [_vTexture replaceRegion:MTLRegionMake2D(0, 0, _chromaWidth, _chromaHeight)
               mipmapLevel:0
                 withBytes:buffer.dataV
               bytesPerRow:buffer.strideV];

  return (_uTexture != nil) && (_yTexture != nil) && (_vTexture != nil);
}

- (void)uploadTexturesToRenderEncoder:(id<MTLRenderCommandEncoder>)renderEncoder {
  [renderEncoder setFragmentTexture:_yTexture atIndex:0];
  [renderEncoder setFragmentTexture:_uTexture atIndex:1];
  [renderEncoder setFragmentTexture:_vTexture atIndex:2];
}

@end

