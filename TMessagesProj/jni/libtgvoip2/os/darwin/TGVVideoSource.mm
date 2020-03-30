//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import "TGVVideoSource.h"
#include "VideoToolboxEncoderSource.h"

@implementation TGVVideoSource{
	tgvoip::video::VideoToolboxEncoderSource* nativeSource;
	id<TGVVideoSourceDelegate> delegate;
}


- (instancetype)initWithDelegate: (id<TGVVideoSourceDelegate>)delegate{
	self=[super init];
	nativeSource=new tgvoip::video::VideoToolboxEncoderSource(self);
	self->delegate=delegate;
	return self;
}

- (void)dealloc{
	delete nativeSource;
}

- (void)sendVideoFrame: (CMSampleBufferRef)buffer{
	nativeSource->EncodeFrame(buffer);
}

- (TGVVideoResolution)maximumSupportedVideoResolution{
	return tgvoip::video::VideoToolboxEncoderSource::SupportsFullHD() ? TGVVideoResolution1080 : TGVVideoResolution720;
}

- (void)setVideoRotation: (unsigned int)rotation{
	nativeSource->SetRotation(rotation);
}

- (void)pauseStream{
	nativeSource->SetStreamPaused(true);
}

- (void)resumeStream{
	nativeSource->SetStreamPaused(false);
}

- (tgvoip::video::VideoSource*)nativeVideoSource{
	return nativeSource;
}

- (void)_requestFrameRate: (unsigned int)frameRate{
	dispatch_async(dispatch_get_main_queue(), ^{
		[delegate setFrameRate:frameRate];
	});
}

@end
