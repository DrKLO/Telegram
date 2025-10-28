#ifndef SCREEN_CAPTURER_H
#define SCREEN_CAPTURER_H
#ifndef WEBRTC_IOS
#import <Foundation/Foundation.h>


#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"
#import "rtc_base/time_utils.h"

#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"

#import "sdk/objc/native/src/objc_video_track_source.h"
#import "sdk/objc/native/src/objc_frame_buffer.h"
#import "pc/video_track_source_proxy.h"
#import "tgcalls/platform/darwin/VideoCameraCapturerMac.h"
#import "tgcalls/desktop_capturer/DesktopCaptureSource.h"

@interface DesktopSharingCapturer : NSObject<CapturerInterface>
- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)trackSource captureSource:(tgcalls::DesktopCaptureSource)captureSource;

@end


#endif //WEBRTC_IOS
#endif
