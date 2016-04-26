/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef TIMER_H
#define TIMER_H

#include <stdint.h>
#include <functional>
#include <sys/epoll.h>

class EventObject;

class Timer {

public:
    Timer(std::function<void()> function);
    ~Timer();

    void start();
    void stop();
    void setTimeout(uint32_t ms, bool repeat);

private:
    void onEvent();

    bool started = false;
    bool repeatable = false;
    uint32_t timeout = 0;
    std::function<void()> callback;
    EventObject *eventObject;

    friend class EventObject;
};

#endif
