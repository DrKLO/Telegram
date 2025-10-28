#pragma once

#include <cstddef>
#include <memory>

namespace rtc {
class Thread;
}

namespace tgcalls {

class Threads {
public:
  virtual ~Threads() = default;
  virtual rtc::Thread *getNetworkThread() = 0;
  virtual rtc::Thread *getMediaThread() = 0;
  virtual rtc::Thread *getWorkerThread() = 0;

  // it is not possible to decrease pool size
  static void setPoolSize(size_t size);
  static std::shared_ptr<Threads> getThreads();
};

namespace StaticThreads {
rtc::Thread *getNetworkThread();
rtc::Thread *getMediaThread();
rtc::Thread *getWorkerThread();
std::shared_ptr<Threads> &getThreads();
}

};
