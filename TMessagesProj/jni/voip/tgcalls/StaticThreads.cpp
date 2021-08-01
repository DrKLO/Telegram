#include "StaticThreads.h"

#include "rtc_base/thread.h"
#include "call/call.h"

#include <mutex>
#include <algorithm>

namespace tgcalls {

template <class ValueT, class CreatorT>
class Pool : public std::enable_shared_from_this<Pool<ValueT, CreatorT>> {
  struct Entry {
    std::unique_ptr<ValueT> value;
    size_t refcnt;

    bool operator < (const Entry &other) const {
      return refcnt < other.refcnt;
    }
  };

public:
  explicit Pool(CreatorT creator) : creator_(std::move(creator)) {
  }
  std::shared_ptr<ValueT> get() {
    std::unique_lock<std::mutex> lock(mutex_);
    set_pool_size_locked(1);
    auto i = std::min_element(entries_.begin(), entries_.end()) - entries_.begin();
    return std::shared_ptr<ValueT>(entries_[i].value.get(),
      [i, self = this->shared_from_this()](auto *ptr) {
        self->dec_ref(i);
      });
  }

  void set_pool_size(size_t size) {
    std::unique_lock<std::mutex> lock(mutex_);
    set_pool_size_locked(size);
  }

  void dec_ref(size_t i) {
    std::unique_lock<std::mutex> lock(mutex_);
    entries_.at(i).refcnt--;
  }

private:
  std::mutex mutex_;
  std::vector<Entry> entries_;

  CreatorT creator_;

  void set_pool_size_locked(size_t size) {
    for (size_t i = entries_.size(); i < size; i++) {
      entries_.emplace_back(Entry{creator_(i + 1), 0});
    }
  }
};

class ThreadsImpl : public Threads {
  using Thread = std::unique_ptr<rtc::Thread>;
public:
  explicit ThreadsImpl(size_t i) {
    auto suffix = i == 0 ? "" : "#" + std::to_string(i);
    network_ = create_network("tgc-net" + suffix);
    network_->DisallowAllInvokes();
    media_ = create("tgc-media" + suffix);
    worker_ = create("tgc-work"  + suffix);
    worker_->DisallowAllInvokes();
    worker_->AllowInvokesToThread(network_.get());
  }

  rtc::Thread *getNetworkThread() override {
    return network_.get();
  }
  rtc::Thread *getMediaThread() override {
    return media_.get();
  }
  rtc::Thread *getWorkerThread() override {
    return worker_.get();
  }
  rtc::scoped_refptr<webrtc::SharedModuleThread> getSharedModuleThread() override {
    // This function must be called from a single thread because of SharedModuleThread implementation
    // So we don't care about making it thread safe
    if (!shared_module_thread_) {
      shared_module_thread_ = webrtc::SharedModuleThread::Create(
          webrtc::ProcessThread::Create("tgc-module"),
          [=] { shared_module_thread_ = nullptr; });
    }
    return shared_module_thread_;
  }

private:
  Thread network_;
  Thread media_;
  Thread worker_;
  rtc::scoped_refptr<webrtc::SharedModuleThread> shared_module_thread_;

  static Thread create(const std::string &name) {
    return init(std::unique_ptr<rtc::Thread>(rtc::Thread::Create()), name);
  }
  static Thread create_network(const std::string &name) {
    return init(std::unique_ptr<rtc::Thread>(rtc::Thread::CreateWithSocketServer()), name);
  }

  static Thread init(Thread value, const std::string &name) {
    value->SetName(name, nullptr);
    value->Start();
    return value;
  }
};

class ThreadsCreator {
public:
  std::unique_ptr<Threads> operator()(size_t i) {
   return std::make_unique<ThreadsImpl>(i);
  }
};

Pool<Threads, ThreadsCreator> &get_pool() {
  static auto pool = std::make_shared<Pool<Threads, ThreadsCreator>>(ThreadsCreator());
  return *pool;
}

void Threads::setPoolSize(size_t size){
  get_pool().set_pool_size(size);
}
std::shared_ptr<Threads> Threads::getThreads(){
  return get_pool().get();
}

namespace StaticThreads {

rtc::Thread *getNetworkThread() {
  return getThreads()->getNetworkThread();
}

rtc::Thread *getMediaThread() {
  return getThreads()->getMediaThread();
}

rtc::Thread *getWorkerThread() {
  return getThreads()->getWorkerThread();
}

std::shared_ptr<Threads> &getThreads() {
  static std::shared_ptr<Threads> threads = std::make_shared<ThreadsImpl>(0);
  return threads;
}
};

}
