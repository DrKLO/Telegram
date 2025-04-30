#ifndef TGRTCCVPIXELBUFFER_H
#define TGRTCCVPIXELBUFFER_H

#import "components/video_frame_buffer/RTCCVPixelBuffer.h"

@interface TGRTCCVPixelBuffer : RTCCVPixelBuffer

@property (nonatomic) bool shouldBeMirrored;
@property (nonatomic) int deviceRelativeVideoRotation;

- (void)storeSampleBufferReference:(CMSampleBufferRef _Nonnull)sampleBuffer;

@end

#endif
