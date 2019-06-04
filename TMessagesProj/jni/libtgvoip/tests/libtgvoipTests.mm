//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import <XCTest/XCTest.h>

#import "MockReflector.h"
#include "../VoIPController.h"
#include <openssl/rand.h>
#include "../webrtc_dsp/common_audio/wav_file.h"

@interface libtgvoipTests : XCTestCase

@end

using namespace tgvoip;

@implementation libtgvoipTests{
	VoIPController* controller1;
	VoIPController* controller2;
	std::string testWavFilePath;
}

- (void)setUp {
    [super setUp];
	// this file must be mono 16-bit 48000hz
	NSString* path=[NSString stringWithFormat:@"%@/Downloads/voip_test_input.wav", NSHomeDirectory()];
	testWavFilePath=[path UTF8String];
}

- (void)tearDown {
	
	
    [super tearDown];
}

- (void)initControllers{
	controller1=new VoIPController();
	controller2=new VoIPController();
	
	std::array<std::array<uint8_t, 16>, 2> peerTags=test::MockReflector::GeneratePeerTags();
	std::vector<Endpoint> endpoints1;
	IPv4Address localhost("127.0.0.1");
	IPv6Address emptyV6;
	endpoints1.push_back(Endpoint(1, 1033, localhost, emptyV6, Endpoint::Type::UDP_RELAY, peerTags[0].data()));
	controller1->SetRemoteEndpoints(endpoints1, false, 76);
	std::vector<Endpoint> endpoints2;
	endpoints2.push_back(Endpoint(1, 1033, localhost, emptyV6, Endpoint::Type::UDP_RELAY, peerTags[1].data()));
	controller2->SetRemoteEndpoints(endpoints2, false, 76);
	
	char encryptionKey[256];
	RAND_bytes((uint8_t*)encryptionKey, sizeof(encryptionKey));
	controller1->SetEncryptionKey(encryptionKey, true);
	controller2->SetEncryptionKey(encryptionKey, false);
}

- (void)destroyControllers{
	controller1->Stop();
	delete controller1;
	controller2->Stop();
	delete controller2;
}

- (void)testBasicOperation {
	webrtc::WavReader wavReader1(testWavFilePath);
	webrtc::WavReader wavReader2(testWavFilePath);
	webrtc::WavWriter wavWriter("output.wav", 48000, 1);
	
	test::MockReflector reflector("127.0.0.1", 1033);
	reflector.Start();
	
	[self initControllers];
	
	controller1->SetAudioDataCallbacks([&wavReader1](int16_t* data, size_t len){
		wavReader1.ReadSamples(len, data);
	}, [](int16_t* data, size_t len){
		
	});
	
	controller2->SetAudioDataCallbacks([&wavReader2](int16_t* data, size_t len){
		wavReader2.ReadSamples(len, data);
	}, [&wavWriter](int16_t* data, size_t len){
		wavWriter.WriteSamples(data, len);
	});
	
	controller1->Start();
	controller2->Start();
	controller1->Connect();
	controller2->Connect();
	[NSThread sleepForTimeInterval:10.0];
	
	[self destroyControllers];
	
	reflector.Stop();
}

- (void)testAllocationAndDeallocation{
	test::MockReflector reflector("127.0.0.1", 1033);
	reflector.Start();
	
	for(int i=0;i<10;i++){
		webrtc::WavReader wavReader(testWavFilePath);
		[self initControllers];
		
		controller1->SetAudioDataCallbacks([&wavReader](int16_t* data, size_t len){
			wavReader.ReadSamples(len, data);
		}, [](int16_t* data, size_t len){
			
		});
		
		controller2->SetAudioDataCallbacks([](int16_t* data, size_t len){
			
		}, [](int16_t* data, size_t len){
			
		});
		
		controller1->Start();
		controller2->Start();
		controller1->Connect();
		controller2->Connect();
		[NSThread sleepForTimeInterval:3.0];
		
		[self destroyControllers];
	}
	
	reflector.Stop();
}

- (void)testInitTimeout{
	[self initControllers];
	VoIPController::Config config;
	config.enableNS=config.enableAEC=config.enableAGC=false;
	config.enableCallUpgrade=false;
	config.initTimeout=3.0;
	controller1->SetConfig(config);
	controller1->Start();
	controller1->Connect();
	[NSThread sleepForTimeInterval:1.5];
	XCTAssertEqual(controller1->GetConnectionState(), STATE_WAIT_INIT_ACK);
	[NSThread sleepForTimeInterval:2.0];
	XCTAssertEqual(controller1->GetConnectionState(), STATE_FAILED);
	XCTAssertEqual(controller1->GetLastError(), ERROR_TIMEOUT);
	[self destroyControllers];
}

- (void)testPacketTimeout{
	test::MockReflector reflector("127.0.0.1", 1033);
	reflector.Start();
	[self initControllers];
	
	webrtc::WavReader wavReader(testWavFilePath);
	controller1->SetAudioDataCallbacks([&wavReader](int16_t* data, size_t len){
		wavReader.ReadSamples(len, data);
	}, [](int16_t* data, size_t len){
		
	});
	
	controller2->SetAudioDataCallbacks([](int16_t* data, size_t len){
		
	}, [](int16_t* data, size_t len){
		
	});
	
	VoIPController::Config config;
	config.enableNS=config.enableAEC=config.enableAGC=false;
	config.enableCallUpgrade=false;
	config.initTimeout=3.0;
	config.recvTimeout=1.5;
	controller1->SetConfig(config);
	config.recvTimeout=5.0;
	controller2->SetConfig(config);
	
	controller1->Start();
	controller2->Start();
	controller1->Connect();
	controller2->Connect();
	[NSThread sleepForTimeInterval:2.5];
	XCTAssertEqual(controller1->GetConnectionState(), STATE_ESTABLISHED);
	XCTAssertEqual(controller2->GetConnectionState(), STATE_ESTABLISHED);
	reflector.SetDropAllPackets(true);
	[NSThread sleepForTimeInterval:2.5];
	XCTAssertEqual(controller1->GetConnectionState(), STATE_FAILED);
	XCTAssertEqual(controller1->GetLastError(), ERROR_TIMEOUT);
	XCTAssertEqual(controller2->GetConnectionState(), STATE_RECONNECTING);
	
	[self destroyControllers];
	reflector.Stop();
}

@end
