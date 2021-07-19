#include "WindowsSpecific.h"

using namespace tgvoip;

std::string WindowsSpecific::GetErrorMessage(DWORD code){
	char buf[1024]={0};
	FormatMessageA(FORMAT_MESSAGE_FROM_SYSTEM, NULL, code, MAKELANGID(LANG_ENGLISH, SUBLANG_ENGLISH_US), buf, sizeof(buf), NULL);
	return std::string(buf);
}