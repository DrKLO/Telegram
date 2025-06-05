#ifndef TGCALLS_UWP_PLATFORM_CONTEXT_H
#define TGCALLS_UWP_PLATFORM_CONTEXT_H

#include "PlatformContext.h"

#include <winrt/Windows.Graphics.Capture.h>

using namespace winrt::Windows::Graphics::Capture;

namespace tgcalls {

    class UwpContext : public PlatformContext {

    public:
        UwpContext(GraphicsCaptureItem item)
        : item(item)
        {

        }

        virtual ~UwpContext() = default;

        GraphicsCaptureItem item;
    };

} // namespace tgcalls

#endif
