#include "LogSinkImpl.h"

#include "Instance.h"

#ifdef WEBRTC_WIN
#include "windows.h"
#include <ctime>
#else // WEBRTC_WIN
#include <sys/time.h>
#endif // WEBRTC_WIN

namespace tgcalls {

LogSinkImpl::LogSinkImpl(const Config &config) {
	if (!config.logPath.empty()) {
		_file.open(config.logPath);
	}
}

void LogSinkImpl::OnLogMessage(const std::string &msg, rtc::LoggingSeverity severity, const char *tag) {
	OnLogMessage(std::string(tag) + ": " + msg);
}

void LogSinkImpl::OnLogMessage(const std::string &message, rtc::LoggingSeverity severity) {
	OnLogMessage(message);
}

void LogSinkImpl::OnLogMessage(const std::string &message) {
	time_t rawTime;
	time(&rawTime);
	struct tm timeinfo;
	timeval curTime = { 0 };

#ifdef WEBRTC_WIN
	localtime_s(&timeinfo, &rawTime);

	FILETIME ft;
	unsigned __int64 full = 0;
	GetSystemTimeAsFileTime(&ft);

	full |= ft.dwHighDateTime;
	full <<= 32;
	full |= ft.dwLowDateTime;

	const auto deltaEpochInMicrosecs = 11644473600000000Ui64;
	full -= deltaEpochInMicrosecs;
	full /= 10;
	curTime.tv_sec = (long)(full / 1000000UL);
	curTime.tv_usec = (long)(full % 1000000UL);
#else
	localtime_r(&rawTime, &timeinfo);
	gettimeofday(&curTime, nullptr);
#endif

	int32_t milliseconds = curTime.tv_usec / 1000;

	auto &stream = _file.is_open() ? (std::ostream&)_file : _data;
	stream
		<< (timeinfo.tm_year + 1900)
		<< "-" << (timeinfo.tm_mon + 1)
		<< "-" << (timeinfo.tm_mday)
		<< " " << timeinfo.tm_hour
		<< ":" << timeinfo.tm_min
		<< ":" << timeinfo.tm_sec
		<< ":" << milliseconds
		<< " " << message;
    
#if DEBUG
    printf("%d-%d-%d %d:%d:%d:%d %s\n", timeinfo.tm_year + 1900, timeinfo.tm_mon + 1, timeinfo.tm_mday, timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec, milliseconds, message.c_str());
#endif
}

} // namespace tgcalls
