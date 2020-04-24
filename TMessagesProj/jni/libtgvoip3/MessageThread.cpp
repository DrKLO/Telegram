//
// Created by Grishka on 17.06.2018.
//

#include "logging.h"
#include "MessageThread.h"
#include "VoIPController.h"

#include <cassert>
#include <cmath>
#include <cstdint>
#include <ctime>

#ifndef _WIN32
#include <sys/time.h>
#endif

using namespace tgvoip;

MessageThread::MessageThread()
    : Thread(std::bind(&MessageThread::Run, this))
    , m_running(true)
{
    SetName("MessageThread");

#ifdef _WIN32
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
    event = CreateEvent(nullptr, false, false, nullptr);
#else
    event = CreateEventEx(nullptr, nullptr, 0, EVENT_ALL_ACCESS);
#endif
#else
    ::pthread_cond_init(&cond, nullptr);
#endif
}

MessageThread::~MessageThread()
{
    Stop();
#ifdef _WIN32
    CloseHandle(event);
#else
    ::pthread_cond_destroy(&cond);
#endif
}

void MessageThread::Stop()
{
    if (m_running)
    {
        m_running = false;
#ifdef _WIN32
        SetEvent(event);
#else
        ::pthread_cond_signal(&cond);
#endif
        Join();
    }
}

void MessageThread::Run()
{
    m_queueMutex.Lock();
    while (m_running)
    {
        double currentTime = VoIPController::GetCurrentTime();
        double waitTimeout;
        {
            MutexGuard lock(m_queueAccessMutex);
            waitTimeout = m_queue.empty() ? std::numeric_limits<double>::max() : (m_queue.begin()->deliverAt - currentTime);
        }

        if (waitTimeout > 0.0)
        {
#ifdef _WIN32
            queueMutex.Unlock();
            DWORD actualWaitTimeout = waitTimeout == DBL_MAX ? INFINITE : ((DWORD)round(waitTimeout * 1000.0));
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY != WINAPI_FAMILY_PHONE_APP
            WaitForSingleObject(event, actualWaitTimeout);
#else
            WaitForSingleObjectEx(event, actualWaitTimeout, false);
#endif
            // we don't really care if a context switch happens here and anything gets added to the queue by another thread
            // since any new no-delay messages will get delivered on this iteration anyway
            queueMutex.Lock();
#else
            if (waitTimeout != std::numeric_limits<double>::max())
            {
                struct timeval now;
                struct timespec timeout;
                gettimeofday(&now, nullptr);
                waitTimeout += now.tv_sec;
                waitTimeout += (now.tv_usec / 1000000.0);
                timeout.tv_sec = static_cast<std::time_t>(std::floor(waitTimeout));
                timeout.tv_nsec = static_cast<decltype(timeout.tv_nsec)>((waitTimeout - std::floor(waitTimeout)) * 1000 * 1000 * 1000.0);
                ::pthread_cond_timedwait(&cond, m_queueMutex.NativeHandle(), &timeout);
            }
            else
            {
                ::pthread_cond_wait(&cond, m_queueMutex.NativeHandle());
            }
#endif
        }
        if (!m_running)
        {
            m_queueMutex.Unlock();
            return;
        }
        currentTime = VoIPController::GetCurrentTime();

        std::vector<Message> messagesToDeliverNow;
        {
            MutexGuard lock(m_queueAccessMutex);
            auto msgsToDeliverNowBegin = m_queue.begin();
            auto msgsToDeliverNowEnd = m_queue.upper_bound(Message{ .id = 0, .deliverAt = currentTime, .interval = 0, .func = nullptr });
            for (auto it = msgsToDeliverNowBegin; it != msgsToDeliverNowEnd; it = m_queue.erase(it))
                messagesToDeliverNow.emplace_back(*it);
        }

        for (Message& message : messagesToDeliverNow)
        {
            m_cancelCurrent = false;
            if (message.deliverAt == 0.0)
                message.deliverAt = VoIPController::GetCurrentTime();
            if (message.func != nullptr)
                message.func();
            if (!m_cancelCurrent && message.interval > 0.0)
            {
                message.deliverAt += message.interval;
                InsertMessageInternal(message);
            }
        }
    }
    m_queueMutex.Unlock();
}

std::uint32_t MessageThread::Post(std::function<void()> func, double delay, double interval)
{
    assert(delay >= 0);
    Message message;
    double currentTime = VoIPController::GetCurrentTime();
    {
        std::lock_guard<std::mutex> lock(m_mutexLastMessageID);
        message = { m_lastMessageID++, delay == 0.0 ? 0.0 : (currentTime + delay), interval, std::move(func) };
    }
    InsertMessageInternal(message);
    if (!IsCurrent())
    {
#ifdef _WIN32
        SetEvent(event);
#else
        ::pthread_cond_signal(&cond);
#endif
    }
    return message.id;
}

bool MessageThread::Message::operator<(const MessageThread::Message& other) const
{
    return std::tie(deliverAt, id) < std::tie(other.deliverAt, other.id);
}

void MessageThread::InsertMessageInternal(const MessageThread::Message& message)
{
    MutexGuard lock(m_queueAccessMutex);
    m_queue.emplace(message);
}

void MessageThread::Cancel(std::uint32_t id)
{
    MutexGuard lock(m_queueAccessMutex);

    for (auto it = m_queue.begin(); it != m_queue.end(); ++it)
    {
        if (it->id == id)
        {
            m_queue.erase(it);
            break;
        }
    }
}

void MessageThread::CancelSelf()
{
    assert(IsCurrent());
    m_cancelCurrent = true;
}
