/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include "Timer.h"
#include "FileLog.h"
#include "EventObject.h"
#include "ConnectionsManager.h"

Timer::Timer(std::function<void()> function) {
    eventObject = new EventObject(this, EventObjectTypeTimer);
    callback = function;
}

Timer::~Timer() {
    stop();
    if (eventObject != nullptr) {
        delete eventObject;
        eventObject = nullptr;
    }
}

void Timer::start() {
    if (started || timeout == 0) {
        return;
    }
    started = true;
    ConnectionsManager::getInstance().scheduleEvent(eventObject, timeout);
}

void Timer::stop() {
    if (!started) {
        return;
    }
    started = false;
    ConnectionsManager::getInstance().removeEvent(eventObject);
}

void Timer::setTimeout(uint32_t ms, bool repeat) {
    if (ms == timeout) {
        return;
    }
    repeatable = repeat;
    timeout = ms;
    if (started) {
        ConnectionsManager::getInstance().removeEvent(eventObject);
        ConnectionsManager::getInstance().scheduleEvent(eventObject, timeout);
    }
}

void Timer::onEvent() {
    callback();
    DEBUG_D("timer(%p) call", this);
    if (started && repeatable && timeout != 0) {
        ConnectionsManager::getInstance().scheduleEvent(eventObject, timeout);
    }
}
