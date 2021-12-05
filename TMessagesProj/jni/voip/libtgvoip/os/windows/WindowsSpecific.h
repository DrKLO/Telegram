#ifndef LIBTGVOIP_WINDOWS_SPECIFIC_H
#define LIBTGVOIP_WINDOWS_SPECIFIC_H

#include <string>
#include <Windows.h>

namespace tgvoip{

	class WindowsSpecific{
	public:
		static std::string GetErrorMessage(DWORD code);
	};

}

#endif // LIBTGVOIP_WINDOWS_SPECIFIC_H