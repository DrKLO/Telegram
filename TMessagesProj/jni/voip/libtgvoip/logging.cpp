//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//


#include <stdio.h>
#include <stdarg.h>
#include <time.h>

#include "VoIPController.h"

#ifdef __ANDROID__
#include <sys/system_properties.h>
#elif defined(__linux__)
#include <sys/utsname.h>
#endif

#ifdef __APPLE__
#include <TargetConditionals.h>
#include "os/darwin/DarwinSpecific.h"
#endif

FILE* tgvoipLogFile=NULL;

void tgvoip_log_file_printf(char level, const char* msg, ...){
	if(tgvoipLogFile){
		va_list argptr;
		va_start(argptr, msg);
		time_t t = time(0);
		struct tm *now = localtime(&t);
		fprintf(tgvoipLogFile, "%02d-%02d %02d:%02d:%02d %c: ", now->tm_mon + 1, now->tm_mday, now->tm_hour, now->tm_min, now->tm_sec, level);
		vfprintf(tgvoipLogFile, msg, argptr);
		fprintf(tgvoipLogFile, "\n");
		fflush(tgvoipLogFile);
	}
}

void tgvoip_log_file_write_header(FILE* file){
	if(file){
		time_t t = time(0);
		struct tm *now = localtime(&t);
#if defined(_WIN32)
		#if WINAPI_PARTITION_DESKTOP
			char systemVersion[64];
			OSVERSIONINFOA vInfo;
			vInfo.dwOSVersionInfoSize=sizeof(vInfo);
			GetVersionExA(&vInfo);
			snprintf(systemVersion, sizeof(systemVersion), "Windows %d.%d.%d %s", vInfo.dwMajorVersion, vInfo.dwMinorVersion, vInfo.dwBuildNumber, vInfo.szCSDVersion);
#else
			char* systemVersion="Windows RT";
#endif
#elif defined(__linux__)
#ifdef __ANDROID__
		char systemVersion[128];
		char sysRel[PROP_VALUE_MAX];
		char deviceVendor[PROP_VALUE_MAX];
		char deviceModel[PROP_VALUE_MAX];
		__system_property_get("ro.build.version.release", sysRel);
		__system_property_get("ro.product.manufacturer", deviceVendor);
		__system_property_get("ro.product.model", deviceModel);
		snprintf(systemVersion, sizeof(systemVersion), "Android %s (%s %s)", sysRel, deviceVendor, deviceModel);
#else
		struct utsname sysname;
		uname(&sysname);
		std::string sysver(sysname.sysname);
		sysver+=" ";
		sysver+=sysname.release;
		sysver+=" (";
		sysver+=sysname.version;
		sysver+=")";
		const char* systemVersion=sysver.c_str();
#endif
#elif defined(__APPLE__)
		char osxVer[128];
		tgvoip::DarwinSpecific::GetSystemName(osxVer, sizeof(osxVer));
		char systemVersion[128];
#if TARGET_OS_OSX
		snprintf(systemVersion, sizeof(systemVersion), "OS X %s", osxVer);
#elif TARGET_OS_IPHONE
		snprintf(systemVersion, sizeof(systemVersion), "iOS %s", osxVer);
#else
		snprintf(systemVersion, sizeof(systemVersion), "Unknown Darwin %s", osxVer);
#endif
#else
		const char* systemVersion="Unknown OS";
#endif

#if defined(__aarch64__)
		const char* cpuArch="ARM64";
#elif defined(__arm__) || defined(_M_ARM)
		const char* cpuArch="ARM";
#elif defined(_M_X64) || defined(__x86_64__)
		const char* cpuArch="x86_64";
#elif defined(_M_IX86) || defined(__i386__)
		const char* cpuArch="x86";
#else
		const char* cpuArch="Unknown CPU";
#endif

		fprintf(file, "---------------\nlibtgvoip v" LIBTGVOIP_VERSION " on %s %s\nLog started on %d/%02d/%d at %d:%02d:%02d\n---------------\n", systemVersion, cpuArch, now->tm_mday, now->tm_mon+1, now->tm_year+1900, now->tm_hour, now->tm_min, now->tm_sec);
	}
}
