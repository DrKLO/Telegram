// A QuickJS runtime is pinned to one thread with a known, large stack (the
// desktop copy of ios JSThread.swift / android JSThread.kt). QuickJS measures
// recursion from the stack pointer it saw at creation, so a pool thread (or
// std::thread, whose stack size cannot be chosen) is not an option: every
// runtime gets a dedicated thread with `kStackSize` bytes and a QuickJS limit
// well under it, which turns any overflow into a catchable RangeError.
//
// No Qt here on purpose: tdesktop drives the engine from Qt through the
// delegate; the core itself only needs the C++ standard library and pthreads
// (or _beginthreadex on Windows).
#pragma once

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <functional>
#include <future>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <type_traits>

namespace twidget {

class JSThread {
public:
    /// Real stack of the thread; `QuickJSContext` limits must stay below it.
    static constexpr size_t kStackSize = size_t(8) << 20;
    /// Safe QuickJS limit for a context that only ever runs on a JSThread.
    static constexpr size_t kJSStackLimit = size_t(6) << 20;

    explicit JSThread(std::string name);
    ~JSThread();
    JSThread(const JSThread&) = delete;
    JSThread& operator=(const JSThread&) = delete;

    bool isCurrent() const { return std::this_thread::get_id() == id_; }

    /// Runs `fn` on the thread after everything queued before it. Dropped once stopped.
    void async(std::function<void()> fn);

    /// Runs `fn` on the thread and waits for it (inline when already there).
    template <class F>
    auto sync(F&& fn) -> std::invoke_result_t<F> {
        using R = std::invoke_result_t<F>;
        if (isCurrent()) return fn();
        std::promise<R> promise;
        auto future = promise.get_future();
        bool queued = post([&] {
            if constexpr (std::is_void_v<R>) { fn(); promise.set_value(); }
            else promise.set_value(fn());
        });
        if (!queued) {
            if constexpr (std::is_void_v<R>) return;
            else return R{};
        }
        return future.get();
    }

    class Scheduled {
    public:
        /// Safe from any thread; the block will not start after this returns.
        void cancel() { cancelled_.store(true, std::memory_order_release); }
        bool cancelled() const { return cancelled_.load(std::memory_order_acquire); }
    private:
        std::atomic<bool> cancelled_{false};
    };

    /// Runs `fn` on the thread after `seconds` unless cancelled.
    std::shared_ptr<Scheduled> after(double seconds, std::function<void()> fn);
    /// Timers in the queue, cancelled ones not yet swept included (tests).
    size_t queuedTimers();

    /// Stops the loop once queued work has run; the thread then exits. Idempotent.
    void stop();
    /// stop() + wait for the thread to exit (no-op from the thread itself).
    void join();

private:
    struct Timer {
        std::shared_ptr<Scheduled> handle;
        std::function<void()> fn;
    };
    bool post(std::function<void()> fn);
    void run();
    static void* entry(void* self);

    std::string name_;
    std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<std::function<void()>> tasks_;
    std::multimap<std::chrono::steady_clock::time_point, Timer> timers_;
    size_t sweepAt_ = 64; // the queue's size that sweeps out cancelled timers
    bool stopRequested_ = false; // set by the stop task itself, on the thread
    bool stopping_ = false;      // no more work accepted
    bool joined_ = false;
    std::atomic<std::thread::id> id_{};
    void* handle_ = nullptr; // pthread_t* / HANDLE
};

} // namespace twidget
