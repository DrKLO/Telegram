#include "threading.h"

using namespace tgvoip;

Mutex::Mutex()
{
    ::pthread_mutex_init(&m_mutex, nullptr);
}

Mutex::~Mutex()
{
    ::pthread_mutex_destroy(&m_mutex);
}

void Mutex::Lock()
{
    ::pthread_mutex_lock(&m_mutex);
}

void Mutex::Unlock()
{
    ::pthread_mutex_unlock(&m_mutex);
}

pthread_mutex_t* Mutex::NativeHandle()
{
    return &m_mutex;
}

Thread::Thread(std::function<void()> entry)
    : m_entry(std::move(entry))
{
}

Thread::~Thread() = default;

void Thread::Start()
{
    if (::pthread_create(&m_thread, nullptr, Thread::ActualEntryPoint, this) == 0)
    {
        m_valid = true;
    }
}

void Thread::Join()
{
    if (m_valid)
        ::pthread_join(m_thread, nullptr);
}

void Thread::SetName(const char* name)
{
    m_name = name;
}

void Thread::SetMaxPriority()
{
#ifdef __APPLE__
    m_maxPriority = true;
#endif
}

void Thread::Sleep(double seconds)
{
    ::usleep(static_cast<useconds_t>(seconds * 1000 * 1000.0));
}

bool Thread::IsCurrent()
{
    return ::pthread_equal(m_thread, pthread_self()) != 0;
}

void* Thread::ActualEntryPoint(void* arg)
{
    Thread* self = reinterpret_cast<Thread*>(arg);
    if (self->m_name)
    {
#if !defined(__APPLE__) && !defined(__gnu_hurd__)
        ::pthread_setname_np(self->m_thread, self->m_name);
#elif !defined(__gnu_hurd__)
        pthread_setname_np(self->name);
        if (self->m_maxPriority)
        {
            DarwinSpecific::SetCurrentThreadPriority(DarwinSpecific::THREAD_PRIO_USER_INTERACTIVE);
        }
#endif
    }
    self->m_entry();
    return nullptr;
}

#ifdef __APPLE__

Semaphore::Semaphore(unsigned int maxCount, unsigned int initValue)
{
    m_sem = dispatch_semaphore_create(initValue);
}

Semaphore::~Semaphore()
{
#if !__has_feature(objc_arc)
    dispatch_release(m_sem);
#endif
}

void Semaphore::Acquire()
{
    dispatch_semaphore_wait(m_sem, DISPATCH_TIME_FOREVER);
}

void Semaphore::Release()
{
    dispatch_semaphore_signal(m_sem);
}

void Semaphore::Acquire(int count)
{
    for (int i = 0; i < count; i++)
        Acquire();
}

void Semaphore::Release(int count)
{
    for (int i = 0; i < count; i++)
        Release();
}

#else

Semaphore::Semaphore(unsigned int maxCount, unsigned int initValue)
{
    ::sem_init(&m_sem, 0, initValue);
}

Semaphore::~Semaphore()
{
    ::sem_destroy(&m_sem);
}

void Semaphore::Acquire()
{
    ::sem_wait(&m_sem);
}

void Semaphore::Release()
{
    ::sem_post(&m_sem);
}

void Semaphore::Acquire(int count)
{
    for (int i = 0; i < count; ++i)
        Acquire();
}

void Semaphore::Release(int count)
{
    for (int i = 0; i < count; ++i)
        Release();
}

MutexGuard::MutexGuard(Mutex& mutex)
    : m_mutex(mutex)
{
    mutex.Lock();
}

MutexGuard::~MutexGuard()
{
    m_mutex.Unlock();
}

#endif
