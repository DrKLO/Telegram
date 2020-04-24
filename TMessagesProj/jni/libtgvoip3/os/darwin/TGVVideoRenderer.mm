//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import "TGVVideoRenderer.h"
#include "SampleBufferDisplayLayerRenderer.h"
#include "../../logging.h"

@implementation TGVVideoRenderer{
	AVSampleBufferDisplayLayer* layer;
	id<TGVVideoRendererDelegate> delegate;
	tgvoip::video::SampleBufferDisplayLayerRenderer* nativeRenderer;
}

- (instancetype)initWithDisplayLayer:(AVSampleBufferDisplayLayer *)layer delegate:(nonnull id<TGVVideoRendererDelegate>)delegate{
	self=[super init];
	self->layer=layer;
	self->delegate=delegate;
	nativeRenderer=new tgvoip::video::SampleBufferDisplayLayerRenderer(self);
	layer.videoGravity=AVLayerVideoGravityResizeAspect;
	return self;
}

- (void)dealloc{
	delete nativeRenderer;
}

- (tgvoip::video::VideoRenderer *)nativeVideoRenderer{
	return nativeRenderer;
}

- (void)_enqueueBuffer: (CMSampleBufferRef)buffer reset: (BOOL)reset{
	/*if(reset){
		LOGV("Resetting layer");
		[layer flush];
	}*/
	LOGV("Enqueue buffer");
    [layer enqueueSampleBuffer:buffer];
    NSError* error=[layer error];
    if(error){
    	LOGE("enqueueSampleBuffer failed: %s", [error.description cStringUsingEncoding:NSUTF8StringEncoding]);
    }
}

- (void)_setSizeWidth: (uint16_t)width height: (uint16_t)height{
	dispatch_async(dispatch_get_main_queue(), ^{
		LOGI("Callback: setSize %u %u", width, height);
		[delegate incomingVideoStreamWillStartWithFrameSize:CGSizeMake(width, height)];
	});
}

- (void)_setRotation: (uint16_t)rotation{
	dispatch_async(dispatch_get_main_queue(), ^{
		LOGI("Callback: setRotation %u", rotation);
		[delegate incomingVideoRotationDidChange:(int)rotation];
	});
}

- (void)_setStopped{
	dispatch_async(dispatch_get_main_queue(), ^{
		LOGI("Callback: setStopped");
		[delegate incomingVideoStreamDidStopWithReason:TGVStreamStopReasonUser];
	});
}

- (void)_setPaused{
	dispatch_async(dispatch_get_main_queue(), ^{
		LOGI("Callback: setPaused");
		[delegate incomingVideoStreamDidPauseWithReason:TGVStreamPauseReasonBackground];
	});
}

- (void)_setResumed{
	dispatch_async(dispatch_get_main_queue(), ^{
		LOGI("Callback: setResumed");
		[delegate incomingVideoStreamWillResume];
	});
}
@end
