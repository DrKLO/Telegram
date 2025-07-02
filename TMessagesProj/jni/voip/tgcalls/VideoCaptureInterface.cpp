#include "VideoCaptureInterface.h"

#include "VideoCaptureInterfaceImpl.h"
#include "tgnet/FileLog.h"

namespace tgcalls {

std::unique_ptr<VideoCaptureInterface> VideoCaptureInterface::Create(
   std::shared_ptr<Threads> threads, std::string deviceId, bool isScreenCapture,
   std::shared_ptr<PlatformContext> platformContext) {
	return std::make_unique<VideoCaptureInterfaceImpl>(deviceId, isScreenCapture, platformContext, std::move(threads));
}

VideoCaptureInterface::~VideoCaptureInterface() = default;

} // namespace tgcalls
