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
	nativeSource=new tgvoip::video::VideoToolboxEncoderSource();
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
	return TGVVideoResolution1080;
}

- (void)setVideoRotation: (int)rotation{

}

- (void)pauseStream{

}

- (void)resumeStream{

}

- (tgvoip::video::VideoSource*)nativeVideoSource{
	return nativeSource;
}

@end
