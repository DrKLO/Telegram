#import "TGRTCCVPixelBuffer.h"

@interface TGRTCCVPixelBuffer () {
    CMSampleBufferRef _sampleBuffer;
}

@end

@implementation TGRTCCVPixelBuffer

- (void)dealloc {
    if (_sampleBuffer) {
        CFRelease(_sampleBuffer);
    }
}

- (void)storeSampleBufferReference:(CMSampleBufferRef _Nonnull)sampleBuffer {
    if (_sampleBuffer) {
        CFRelease(_sampleBuffer);
        _sampleBuffer = nil;
    }
    
    if (sampleBuffer) {
        _sampleBuffer = (CMSampleBufferRef)CFRetain(sampleBuffer);
    }
}

@end
