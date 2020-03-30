//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import <Foundation/Foundation.h>
#import <CoreMedia/CoreMedia.h>

namespace tgvoip{
namespace video{
class VideoSource;
}
}

typedef NS_ENUM(int, TGVVideoResolution){
	TGVVideoResolution1080,
	TGVVideoResolution720,
	TGVVideoResolution480,
	TGVVideoResolution360
};

@protocol TGVVideoSourceDelegate <NSObject>

- (void)setFrameRate: (unsigned int)frameRate;

@end

@interface TGVVideoSource : NSObject

- (instancetype)initWithDelegate: (id<TGVVideoSourceDelegate>)delegate;
- (void)sendVideoFrame: (CMSampleBufferRef)buffer;
- (TGVVideoResolution)maximumSupportedVideoResolution;
- (void)setVideoRotation: (unsigned int)rotation;
- (void)pauseStream;
- (void)resumeStream;
- (tgvoip::video::VideoSource*)nativeVideoSource;

- (void)_requestFrameRate: (unsigned int)frameRate;

@end
