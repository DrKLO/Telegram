/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <memory.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include "ByteStream.h"
#include "ConnectionSocket.h"
#include "FileLog.h"
#include "Defines.h"
#include "ConnectionsManager.h"
#include "EventObject.h"
#include "Timer.h"
#include "NativeByteBuffer.h"

#ifndef EPOLLRDHUP
#define EPOLLRDHUP 0x2000
#endif

ConnectionSocket::ConnectionSocket() {
    outgoingByteStream = new ByteStream();
    lastEventTime = ConnectionsManager::getInstance().getCurrentTimeMillis();
    eventObject = new EventObject(this, EventObjectTypeConnection);
}

ConnectionSocket::~ConnectionSocket() {
    if (outgoingByteStream != nullptr) {
        delete outgoingByteStream;
        outgoingByteStream = nullptr;
    }
    if (eventObject != nullptr) {
        delete eventObject;
        eventObject = nullptr;
    }
}

void ConnectionSocket::openConnection(std::string address, uint16_t port, bool ipv6) {
    int epolFd = ConnectionsManager::getInstance().epolFd;

    if ((socketFd = socket(ipv6 ? AF_INET6 : AF_INET, SOCK_STREAM, 0)) < 0) {
        DEBUG_E("connection(%p) can't create socket", this);
        closeSocket(1);
        return;
    }

    memset(&socketAddress, 0, sizeof(sockaddr_in));
    memset(&socketAddress6, 0, sizeof(sockaddr_in6));

    if (ipv6) {
        socketAddress6.sin6_family = AF_INET6;
        socketAddress6.sin6_port = htons(port);
        if (inet_pton(AF_INET6, address.c_str(), &socketAddress6.sin6_addr.s6_addr) != 1) {
            DEBUG_E("connection(%p) bad ipv6 %s", this, address.c_str());
            closeSocket(1);
            return;
        }
    } else {
        socketAddress.sin_family = AF_INET;
        socketAddress.sin_port = htons(port);
        if (inet_pton(AF_INET, address.c_str(), &socketAddress.sin_addr.s_addr) != 1) {
            DEBUG_E("connection(%p) bad ipv4 %s", this, address.c_str());
            closeSocket(1);
            return;
        }
    }

    int yes = 1;
    if (setsockopt(socketFd, IPPROTO_TCP, TCP_NODELAY, &yes, sizeof(int))) {
        DEBUG_E("connection(%p) set TCP_NODELAY failed", this);
    }

    if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
        DEBUG_E("connection(%p) set O_NONBLOCK failed", this);
        closeSocket(1);
        return;
    }

    if (connect(socketFd, (ipv6 ? (sockaddr *) &socketAddress6 : (sockaddr *) &socketAddress), (socklen_t) (ipv6 ? sizeof(sockaddr_in6) : sizeof(sockaddr_in))) == -1 && errno != EINPROGRESS) {
        closeSocket(1);
    } else {
        eventMask.events = EPOLLOUT | EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
        eventMask.data.ptr = eventObject;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, socketFd, &eventMask) != 0) {
            DEBUG_E("connection(%p) epoll_ctl, adding socket failed", this);
            closeSocket(1);
        }
    }
}

bool ConnectionSocket::checkSocketError() {
    if (socketFd < 0) {
        return true;
    }
    int ret;
    int code;
    socklen_t len = sizeof(int);
    ret = getsockopt(socketFd, SOL_SOCKET, SO_ERROR, &code, &len);
    return (ret || code) != 0;
}

void ConnectionSocket::closeSocket(int reason) {
    lastEventTime = ConnectionsManager::getInstance().getCurrentTimeMillis();
    ConnectionsManager::getInstance().detachConnection(this);
    if (socketFd >= 0) {
        epoll_ctl(ConnectionsManager::getInstance().epolFd, EPOLL_CTL_DEL, socketFd, NULL);
        if (close(socketFd) != 0) {
            DEBUG_E("connection(%p) unable to close socket", this);
        }
        socketFd = -1;
    }
    onConnectedSent = false;
    outgoingByteStream->clean();
    onDisconnected(reason);
}

void ConnectionSocket::onEvent(uint32_t events) {
    if (events & EPOLLIN) {
        if (checkSocketError()) {
            closeSocket(1);
            return;
        } else {
            ssize_t readCount;
            NativeByteBuffer *buffer = ConnectionsManager::getInstance().networkBuffer;
            while (true) {
                buffer->rewind();
                readCount = recv(socketFd, buffer->bytes(), READ_BUFFER_SIZE, 0);
                if (readCount < 0) {
                    closeSocket(1);
                    DEBUG_E("connection(%p) recv failed", this);
                    return;
                }
                if (readCount > 0) {
                    buffer->limit((uint32_t) readCount);
                    lastEventTime = ConnectionsManager::getInstance().getCurrentTimeMillis();
                    onReceivedData(buffer);
                }
                if (readCount != READ_BUFFER_SIZE) {
                    break;
                }
            }
        }
    }
    if (events & EPOLLOUT) {
        if (checkSocketError() != 0) {
            closeSocket(1);
            return;
        } else {
            if (!onConnectedSent) {
                ConnectionsManager::getInstance().attachConnection(this);
                lastEventTime = ConnectionsManager::getInstance().getCurrentTimeMillis();
                onConnected();
                onConnectedSent = true;
            }
            NativeByteBuffer *buffer = ConnectionsManager::getInstance().networkBuffer;
            buffer->clear();
            outgoingByteStream->get(buffer);
            buffer->flip();

            uint32_t remaining = buffer->remaining();
            if (remaining) {
                ssize_t sentLength;
                if ((sentLength = send(socketFd, buffer->bytes(), remaining, 0)) < 0) {
                    DEBUG_E("connection(%p) send failed", this);
                    closeSocket(1);
                    return;
                } else {
                    outgoingByteStream->discard((uint32_t) sentLength);
                    adjustWriteOp();
                }
            }
        }
    }
    if ((events & EPOLLRDHUP) || (events & EPOLLHUP)) {
        closeSocket(1);
        return;
    }
    if (events & EPOLLERR) {
        DEBUG_E("connection(%p) epoll error", this);
        return;
    }
}

void ConnectionSocket::writeBuffer(NativeByteBuffer *buffer) {
    outgoingByteStream->append(buffer);
    adjustWriteOp();
}

void ConnectionSocket::adjustWriteOp() {
    eventMask.events = EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
    if (outgoingByteStream->hasData()) {
        eventMask.events |= EPOLLOUT;
    }
    eventMask.data.ptr = eventObject;
    if (epoll_ctl(ConnectionsManager::getInstance().epolFd, EPOLL_CTL_MOD, socketFd, &eventMask) != 0) {
        DEBUG_E("connection(%p) epoll_ctl, modify socket failed", this);
        closeSocket(1);
    }
}

void ConnectionSocket::setTimeout(time_t time) {
    timeout = time;
    lastEventTime = ConnectionsManager::getInstance().getCurrentTimeMillis();
}

void ConnectionSocket::checkTimeout(int64_t now) {
    if (timeout != 0 && (now - lastEventTime) > (int64_t) timeout * 1000) {
        closeSocket(2);
    }
}

bool ConnectionSocket::isDisconnected() {
    return socketFd < 0;
}

void ConnectionSocket::dropConnection() {
    closeSocket(0);
}
