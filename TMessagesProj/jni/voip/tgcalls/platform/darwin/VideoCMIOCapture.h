//
//  VideoCMIOCapture.h
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 21.06.2021.
//  Copyright © 2021 Mikhail Filimonov. All rights reserved.
//

#import <Foundation/Foundation.h>
#include "VideoCameraCapturerMac.h"
NS_ASSUME_NONNULL_BEGIN

@interface VideoCMIOCapture : NSObject<CapturerInterface>
- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source;
- (void)setupCaptureWithDevice:(AVCaptureDevice *)device;
@end

NS_ASSUME_NONNULL_END
