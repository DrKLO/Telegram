#ifndef LIBTGVOIP_WINDOWS_SPECIFIC_H
#define LIBTGVOIP_WINDOWS_SPECIFIC_H

#include <Windows.h>
#include <string>

namespace tgvoip
{

class WindowsSpecific
{
public:
    static std::string GetErrorMessage(DWORD code);
};

}

#endif // LIBTGVOIP_WINDOWS_SPECIFIC_H