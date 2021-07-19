#include "VideoCaptureInterface.h"

#include "VideoCaptureInterfaceImpl.h"

namespace tgcalls {

std::unique_ptr<VideoCaptureInterface> VideoCaptureInterface::Create(
   std::shared_ptr<Threads> threads, std::string deviceId,
   std::shared_ptr<PlatformContext> platformContext) {
	return std::make_unique<VideoCaptureInterfaceImpl>(deviceId, platformContext, std::move(threads));
}

VideoCaptureInterface::~VideoCaptureInterface() = default;

} // namespace tgcalls
