#include "VideoCaptureInterface.h"

#include "VideoCaptureInterfaceImpl.h"

namespace tgcalls {

std::unique_ptr<VideoCaptureInterface> VideoCaptureInterface::Create(std::string deviceId, std::shared_ptr<PlatformContext> platformContext) {
	return std::make_unique<VideoCaptureInterfaceImpl>(deviceId, platformContext);
}

VideoCaptureInterface::~VideoCaptureInterface() = default;

} // namespace tgcalls
