/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <unistd.h>
#include <sys/eventfd.h>
#include "EventObject.h"
#include "Connection.h"
#include "Timer.h"

EventObject::EventObject(void *object, EventObjectType type) {
    eventObject = object;
    eventType = type;
}

void EventObject::onEvent(uint32_t events) {
    switch (eventType) {
        case EventObjectTypeConnection: {
            Connection *connection = (Connection *) eventObject;
            connection->onEvent(events);
            break;
        }
        case EventObjectTypeTimer: {
            Timer *timer = (Timer *) eventObject;
            timer->onEvent();
            break;
        }
        case EventObjectTypePipe: {
            int *pipe = (int *) eventObject;
            char ch;
            ssize_t size = 1;
            while (size > 0) {
                size = read(pipe[0], &ch, 1);
            }
            break;
        }
        case EventObjectTypeEvent: {
            int *eventFd = (int *) eventObject;
            uint64_t count;
            eventfd_read(eventFd[0], &count);
            break;
        }
        default:
            break;
    }
}
