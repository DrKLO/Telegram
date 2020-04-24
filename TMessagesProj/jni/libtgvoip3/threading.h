//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef THREADING_H
#define THREADING_H

#include "utils.h"

#include <functional>

#if defined(_POSIX_THREADS) || defined(_POSIX_VERSION) || defined(__unix__) || defined(__unix) || (defined(__APPLE__) && defined(__MACH__))

#include <pthread.h>
#include <sched.h>
#include <semaphore.h>
#include <unistd.h>
#ifdef __APPLE__
#include "os/darwin/DarwinSpecific.h"
#endif

namespace tgvoip
{

class Mutex
{
public:
    Mutex();
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(Mutex);
    ~Mutex();
    void Lock();
    void Unlock();
    pthread_mutex_t* NativeHandle();

private:
    pthread_mutex_t m_mutex;
};

class Thread
{
public:
    Thread(std::function<void()> entry);
    virtual ~Thread();

    void Start();
    void Join();
    void SetName(const char* name);
    void SetMaxPriority();
    bool IsCurrent();
    static void Sleep(double seconds);

private:
    static void* ActualEntryPoint(void* arg);
    std::function<void()> m_entry;
    pthread_t m_thread = 0;
    const char* m_name = nullptr;
#ifdef __APPLE__
    bool m_maxPriority = false;
#endif
    bool m_valid = false;
};

} // namespace tgvoip

#ifdef __APPLE__
#include <dispatch/dispatch.h>
#endif

namespace tgvoip
{

class Semaphore
{
public:
    Semaphore(unsigned int maxCount, unsigned int initValue);
    ~Semaphore();

    void Acquire();
    void Release();
    void Acquire(int count);
    void Release(int count);

private:
#ifdef __APPLE__
    dispatch_semaphore_t m_sem;
#else
    sem_t m_sem;
#endif
};

} // namespace tgvoip

#elif defined(_WIN32)

#include <Windows.h>
#include <cassert>

namespace tgvoip
{

class Mutex
{
public:
    Mutex()
    {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
        InitializeCriticalSection(&section);
#else
        InitializeCriticalSectionEx(&section, 0, 0);
#endif
    }

    ~Mutex()
    {
        DeleteCriticalSection(&section);
    }

    void Lock()
    {
        EnterCriticalSection(&section);
    }

    void Unlock()
    {
        LeaveCriticalSection(&section);
    }

private:
    Mutex(const Mutex& other);
    CRITICAL_SECTION section;
};

class Thread
{
public:
    Thread(std::function<void()> entry)
        : entry(entry)
    {
        name = NULL;
        thread = NULL;
    }

    ~Thread()
    {
    }

    void Start()
    {
        thread = CreateThread(NULL, 0, Thread::ActualEntryPoint, this, 0, &id);
    }

    void Join()
    {
        if (!thread)
            return;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
        WaitForSingleObject(thread, INFINITE);
#else
        WaitForSingleObjectEx(thread, INFINITE, false);
#endif
        CloseHandle(thread);
    }

    void SetName(const char* name)
    {
        this->name = name;
    }

    void SetMaxPriority()
    {
        SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST);
    }

    static void Sleep(double seconds)
    {
        ::Sleep((DWORD)(seconds * 1000));
    }

    bool IsCurrent()
    {
        return id == GetCurrentThreadId();
    }

private:
    static const DWORD MS_VC_EXCEPTION = 0x406D1388;

#pragma pack(push, 8)
    typedef struct tagTHREADNAME_INFO
    {
        DWORD dwType; // Must be 0x1000.
        LPCSTR szName; // Pointer to name (in user addr space).
        DWORD dwThreadID; // Thread ID (-1=caller thread).
        DWORD dwFlags; // Reserved for future use, must be zero.
    } THREADNAME_INFO;
#pragma pack(pop)

    static DWORD WINAPI ActualEntryPoint(void* arg)
    {
        Thread* self = reinterpret_cast<Thread*>(arg);
        if (self->name)
        {
            THREADNAME_INFO info;
            info.dwType = 0x1000;
            info.szName = self->name;
            info.dwThreadID = -1;
            info.dwFlags = 0;
            __try
            {
                RaiseException(MS_VC_EXCEPTION, 0, sizeof(info) / sizeof(ULONG_PTR), (ULONG_PTR*)&info);
            }
            __except (EXCEPTION_EXECUTE_HANDLER)
            {
            }
        }
        self->entry();
        return 0;
    }
    std::function<void()> entry;
    HANDLE thread;
    DWORD id;
    const char* name;
};

class Semaphore
{
public:
    Semaphore(unsigned int maxCount, unsigned int initValue)
    {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
        h = CreateSemaphore(NULL, initValue, maxCount, NULL);
#else
        h = CreateSemaphoreEx(NULL, initValue, maxCount, NULL, 0, SEMAPHORE_ALL_ACCESS);
        assert(h);
#endif
    }

    ~Semaphore()
    {
        CloseHandle(h);
    }

    void Acquire()
    {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
        WaitForSingleObject(h, INFINITE);
#else
        WaitForSingleObjectEx(h, INFINITE, false);
#endif
    }

    void Release()
    {
        ReleaseSemaphore(h, 1, NULL);
    }

    void Acquire(int count)
    {
        for (int i = 0; i < count; i++)
            Acquire();
    }

    void Release(int count)
    {
        ReleaseSemaphore(h, count, NULL);
    }

private:
    HANDLE h;
};

} // namespace tgvoip
#else
#error "No threading implementation for your operating system"
#endif

namespace tgvoip
{

class MutexGuard
{
public:
    MutexGuard(Mutex& mutex);
    ~MutexGuard();

private:
    Mutex& m_mutex;
};

} // namespace tgvoip

#endif // THREADING_H
