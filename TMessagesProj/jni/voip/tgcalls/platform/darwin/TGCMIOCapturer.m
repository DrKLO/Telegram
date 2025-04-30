//
//  Capturer.m
//  CoreMediaMacCapture
//
//  Created by Mikhail Filimonov on 21.06.2021.
//

#import "TGCMIOCapturer.h"
#import "TGCMIODevice.h"

@interface TGCMIOCapturer ()

@end

@implementation TGCMIOCapturer
{
    AVCaptureDevice * _captureDevice;
    TGCMIODevice * _device;
}
-(id)initWithDeviceId:(AVCaptureDevice *)device {
    if (self = [super init]) {
        _captureDevice = device;
        
    }
    return self;
}

-(void)start:(renderBlock)renderBlock {
    _device = [TGCMIODevice FindDeviceByUniqueId:_captureDevice];
   
    [_device run:^(CMSampleBufferRef sampleBuffer) {
        renderBlock(sampleBuffer);
    }];
    
    
}
-(void)stop {
    [_device stop];
}

-(void)dealloc {
    
}

@end
