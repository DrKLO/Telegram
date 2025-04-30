//
//  TGRTCMetalContextHolder.m
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.06.2021.
//  Copyright © 2021 Mikhail Filimonov. All rights reserved.
//

#import "TGRTCMetalContextHolder.h"

static NSString *const vertexFunctionName = @"vertexPassthrough";
static NSString *const fragmentFunctionName = @"fragmentColorConversion";
static NSString *const fragmentDoTransformFilter = @"doTransformFilter";
static NSString *const twoInputVertexName = @"twoInputVertex";
static NSString *const transformAndBlendName = @"transformAndBlend";
static NSString *const scaleAndBlurName = @"scaleAndBlur";
static NSString *const fragmentPlainName = @"fragmentPlain";



@implementation TGRTCMetalContextHolder
{
    id<MTLDevice> _device;
    
    id<MTLRenderPipelineState> _pipelineYuvRgb;
    id<MTLRenderPipelineState> _pipelineTransformAndBlend;
    id<MTLRenderPipelineState> _pipelineScaleAndBlur;
    id<MTLRenderPipelineState> _pipelineThrough;
    
    id<MTLSamplerState> _sampler;
    id<MTLLibrary> _defaultLibrary;

    CGDirectDisplayID _displayId;
}

-(id __nullable)init {
    if(self = [super init]) {
        _displayId = CGMainDisplayID();
        _device = CGDirectDisplayCopyCurrentMetalDevice(_displayId);
        _defaultLibrary = [_device newDefaultLibrary];
    }
    if (!_device) {
        return nil;
    }
    _sampler = [self defaultSamplerState:_device];
    [self loadPipelines];
    
    return self;
}


- (id<MTLSamplerState>)defaultSamplerState:(id<MTLDevice>)device {
    MTLSamplerDescriptor *samplerDescriptor = [[MTLSamplerDescriptor alloc] init];
    samplerDescriptor.minFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.magFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.sAddressMode = MTLSamplerAddressModeClampToZero;
    samplerDescriptor.tAddressMode = MTLSamplerAddressModeClampToZero;

    return [device newSamplerStateWithDescriptor:samplerDescriptor];
}

- (void)loadPipelines {
 
    {
        id<MTLFunction> vertexFunction = [_defaultLibrary newFunctionWithName:vertexFunctionName];
        id<MTLFunction> fragmentFunction = [_defaultLibrary newFunctionWithName:fragmentFunctionName];

        MTLRenderPipelineDescriptor *pipelineDescriptor = [[MTLRenderPipelineDescriptor alloc] init];
        pipelineDescriptor.vertexFunction = vertexFunction;
        pipelineDescriptor.fragmentFunction = fragmentFunction;
        pipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        pipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatInvalid;
        NSError *error = nil;
        _pipelineYuvRgb = [_device newRenderPipelineStateWithDescriptor:pipelineDescriptor error:&error];
    }
    {
        id<MTLFunction> vertexFunction = [_defaultLibrary newFunctionWithName:vertexFunctionName];
        id<MTLFunction> fragmentFunction = [_defaultLibrary newFunctionWithName:fragmentPlainName];

        MTLRenderPipelineDescriptor *pipelineDescriptor = [[MTLRenderPipelineDescriptor alloc] init];
        pipelineDescriptor.vertexFunction = vertexFunction;
        pipelineDescriptor.fragmentFunction = fragmentFunction;
        pipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        pipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatInvalid;
        NSError *error = nil;
        _pipelineThrough = [_device newRenderPipelineStateWithDescriptor:pipelineDescriptor error:&error];
    }
    
    {
        id<MTLFunction> vertexFunction = [_defaultLibrary newFunctionWithName:twoInputVertexName];
        id<MTLFunction> fragmentFunction = [_defaultLibrary newFunctionWithName:transformAndBlendName];

        MTLRenderPipelineDescriptor *pipelineDescriptor = [[MTLRenderPipelineDescriptor alloc] init];
        pipelineDescriptor.vertexFunction = vertexFunction;
        pipelineDescriptor.fragmentFunction = fragmentFunction;
        pipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        pipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatInvalid;
        NSError *error = nil;
        _pipelineTransformAndBlend = [_device newRenderPipelineStateWithDescriptor:pipelineDescriptor error:&error];
    }
    {
        id<MTLFunction> vertexFunction = [_defaultLibrary newFunctionWithName:vertexFunctionName];
        id<MTLFunction> fragmentFunction = [_defaultLibrary newFunctionWithName:scaleAndBlurName];

        MTLRenderPipelineDescriptor *pipelineDescriptor = [[MTLRenderPipelineDescriptor alloc] init];
        pipelineDescriptor.vertexFunction = vertexFunction;
        pipelineDescriptor.fragmentFunction = fragmentFunction;
        pipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        pipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatInvalid;
        NSError *error = nil;
        _pipelineScaleAndBlur = [_device newRenderPipelineStateWithDescriptor:pipelineDescriptor error:&error];
    }
}

-(id<MTLDevice>)device {
    return _device;
}
-(id<MTLRenderPipelineState>)pipelineYuvRgb {
    return _pipelineYuvRgb;
}
-(id<MTLRenderPipelineState>)pipelineTransformAndBlend {
    return _pipelineTransformAndBlend;
}
-(id<MTLRenderPipelineState>)pipelineScaleAndBlur {
    return _pipelineScaleAndBlur;
}
-(id<MTLRenderPipelineState>)pipelineThrough {
    return _pipelineThrough;
}
-(id<MTLSamplerState>)sampler {
    return _sampler;
}
-(CGDirectDisplayID)displayId {
    return _displayId;
}
@end
