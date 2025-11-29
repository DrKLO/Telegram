/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
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
class ByteArray;

class ConnectionSocket {

public:
    ConnectionSocket(int32_t instance);
    virtual ~ConnectionSocket();

    void writeBuffer(uint8_t *data, uint32_t size);
    void writeBuffer(NativeByteBuffer *buffer);
    void openConnection(std::string address, uint16_t port, std::string secret, bool ipv6, int32_t networkType);
    void setTimeout(time_t timeout);
    time_t getTimeout();
    bool isDisconnected();
    void dropConnection();
    void setOverrideProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret);
    void onHostNameResolved(std::string host, std::string ip, bool ipv6);

protected:
    int32_t instanceNum;
    void onEvent(uint32_t events);
    bool checkTimeout(int64_t now);
    void resetLastEventTime();
    bool hasTlsHashMismatch();
    virtual void onReceivedData(NativeByteBuffer *buffer) = 0;
    virtual void onDisconnected(int32_t reason, int32_t error) = 0;
    virtual void onConnected() = 0;
    virtual bool hasPendingRequests() = 0;

    std::string overrideProxyUser = "";
    std::string overrideProxyPassword = "";
    std::string overrideProxyAddress = "";
    std::string overrideProxySecret = "";
    uint16_t overrideProxyPort = 1080;

private:
    ByteStream *outgoingByteStream = nullptr;
    struct epoll_event eventMask;
    struct sockaddr_in socketAddress;
    struct sockaddr_in6 socketAddress6;
    int socketFd = -1;
    time_t timeout = 12;
    bool onConnectedSent = false;
    int64_t lastEventTime = 0;
    EventObject *eventObject;
    int32_t currentNetworkType;
    bool isIpv6;
    std::string currentAddress;
    uint16_t currentPort;

    std::string waitingForHostResolve;
    bool adjustWriteOpAfterResolve;

    std::string currentSecret;
    std::string currentSecretDomain;

    bool tlsUseLegacy = false;
    bool tlsHashMismatch = false;
    bool tlsBufferSized = true;
    NativeByteBuffer *tlsBuffer = nullptr;
    ByteArray *tempBuffer = nullptr;
    size_t bytesRead = 0;
    int8_t tlsState = 0;

    uint8_t proxyAuthState;

    int32_t checkSocketError(int32_t *error);
    void closeSocket(int32_t reason, int32_t error);
    void openConnectionInternal(bool ipv6);
    void adjustWriteOp();

    friend class EventObject;
    friend class ConnectionsManager;
    friend class Connection;
};

#endif
