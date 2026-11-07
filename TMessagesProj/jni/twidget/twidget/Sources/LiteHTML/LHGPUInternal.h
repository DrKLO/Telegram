#pragma once

#import "LHGPURenderer.h"
#import "LHDocument.h"
#include "LHContainer.h"

@interface LHGPURenderer ()
- (void)beginRecordingWithScale:(CGFloat)scale space:(CGColorSpaceRef)space recorder:(LHGPURecorder *)recorder;
@end

@interface LHDisplayList ()
- (const std::vector<LHGPUOp> *)gpuOps;
- (BOOL)gpuUnsupported;
- (void)setGPUOps:(std::vector<LHGPUOp>)ops unsupported:(BOOL)unsupported;
@end
