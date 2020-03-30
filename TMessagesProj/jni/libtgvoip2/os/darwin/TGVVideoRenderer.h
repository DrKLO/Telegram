//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

namespace tgvoip{
namespace video{
class VideoRenderer;
}
}

typedef NS_ENUM(int, TGVStreamPauseReason){
	TGVStreamPauseReasonBackground,
	TGVStreamPauseReasonPoorConnection
};

typedef NS_ENUM(int, TGVStreamStopReason){
	TGVStreamStopReasonUser,
	TGVStreamStopReasonPoorConnection
};

@protocol TGVVideoRendererDelegate <NSObject>

- (void)incomingVideoRotationDidChange: (int)rotation;
- (void)incomingVideoStreamWillStartWithFrameSize: (CGSize)size;
- (void)incomingVideoStreamDidStopWithReason: (TGVStreamStopReason)reason;
- (void)incomingVideoStreamDidPauseWithReason: (TGVStreamPauseReason)reason;
- (void)incomingVideoStreamWillResume;

@end

@interface TGVVideoRenderer : NSObject

- (instancetype)initWithDisplayLayer: (AVSampleBufferDisplayLayer *)layer delegate: (id<TGVVideoRendererDelegate>)delegate;
- (tgvoip::video::VideoRenderer*)nativeVideoRenderer;

- (void)_enqueueBuffer: (CMSampleBufferRef)buffer reset: (BOOL)reset;
- (void)_setSizeWidth: (uint16_t)width height: (uint16_t)height;
- (void)_setRotation: (uint16_t)rotation;
- (void)_setStopped;
- (void)_setPaused;
- (void)_setResumed;

@end
