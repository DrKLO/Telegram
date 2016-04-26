/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef CONNECTIONSOCKET_H
#define CONNECTIONSOCKET_H

#include <sys/epoll.h>
#include <netinet/in.h>
#include <string>

class NativeByteBuffer;
class ConnectionsManager;
class ByteStream;
class EventObject;

class ConnectionSocket {

public:
    ConnectionSocket();
    virtual ~ConnectionSocket();

    void writeBuffer(NativeByteBuffer *buffer);
    void openConnection(std::string address, uint16_t port, bool ipv6);
    void setTimeout(time_t timeout);
    bool isDisconnected();
    void dropConnection();

protected:
    void onEvent(uint32_t events);
    void checkTimeout(int64_t now);
    virtual void onReceivedData(NativeByteBuffer *buffer) = 0;
    virtual void onDisconnected(int reason) = 0;
    virtual void onConnected() = 0;

private:
    ByteStream *outgoingByteStream = nullptr;
    struct epoll_event eventMask;
    struct sockaddr_in socketAddress;
    struct sockaddr_in6 socketAddress6;
    int socketFd = -1;
    time_t timeout = 15;
    bool onConnectedSent = false;
    int64_t lastEventTime = 0;
    EventObject *eventObject;

    bool checkSocketError();
    void closeSocket(int reason);
    void adjustWriteOp();

    friend class EventObject;
    friend class ConnectionsManager;
};

#endif
