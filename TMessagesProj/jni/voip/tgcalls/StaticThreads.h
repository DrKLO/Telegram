#pragma once

#include <cstddef>
#include <memory>

namespace rtc {
class Thread;
template <class T>
class scoped_refptr;
}
namespace webrtc {
class SharedModuleThread;
}

namespace tgcalls {

class Threads {
public:
  virtual ~Threads() = default;
  virtual rtc::Thread *getNetworkThread() = 0;
  virtual rtc::Thread *getMediaThread() = 0;
  virtual rtc::Thread *getWorkerThread() = 0;
  virtual rtc::scoped_refptr<webrtc::SharedModuleThread> getSharedModuleThread() = 0;

  // it is not possible to decrease pool size
  static void setPoolSize(size_t size);
  static std::shared_ptr<Threads> getThreads();
};

namespace StaticThreads {
rtc::Thread *getNetworkThread();
rtc::Thread *getMediaThread();
rtc::Thread *getWorkerThread();
rtc::scoped_refptr<webrtc::SharedModuleThread> getSharedMoudleThread();
std::shared_ptr<Threads> &getThreads();
}

};
