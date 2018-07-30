/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <memory.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include "ByteStream.h"
#include "ConnectionSocket.h"
#include "FileLog.h"
#include "Defines.h"
#include "ConnectionsManager.h"
#include "EventObject.h"
#include "Timer.h"
#include "NativeByteBuffer.h"
#include "BuffersStorage.h"

#ifndef EPOLLRDHUP
#define EPOLLRDHUP 0x2000
#endif

ConnectionSocket::ConnectionSocket(int32_t instance) {
    instanceNum = instance;
    outgoingByteStream = new ByteStream();
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
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

void ConnectionSocket::openConnection(std::string address, uint16_t port, bool ipv6, int32_t networkType) {
    currentNetworkType = networkType;
    isIpv6 = ipv6;
    currentAddress = address;
    currentPort = port;
    int epolFd = ConnectionsManager::getInstance(instanceNum).epolFd;
    ConnectionsManager::getInstance(instanceNum).attachConnection(this);

    memset(&socketAddress, 0, sizeof(sockaddr_in));
    memset(&socketAddress6, 0, sizeof(sockaddr_in6));

    std::string *proxyAddress = &overrideProxyAddress;
    std::string *proxySecret = &overrideProxySecret;
    uint16_t proxyPort = overrideProxyPort;
    if (proxyAddress->empty()) {
        proxyAddress = &ConnectionsManager::getInstance(instanceNum).proxyAddress;
        proxyPort = ConnectionsManager::getInstance(instanceNum).proxyPort;
        proxySecret = &ConnectionsManager::getInstance(instanceNum).proxySecret;
    }

    if (proxyAddress != nullptr && !proxyAddress->empty()) {
        if ((socketFd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            DEBUG_E("connection(%p) can't create proxy socket", this);
            closeSocket(1, -1);
            return;
        }
        if (proxySecret->empty()) {
            proxyAuthState = 1;
        } else {
            proxyAuthState = 0;
        }
        socketAddress.sin_family = AF_INET;
        socketAddress.sin_port = htons(proxyPort);
        bool continueCheckAddress;
        if (inet_pton(AF_INET, proxyAddress->c_str(), &socketAddress.sin_addr.s_addr) != 1) {
            continueCheckAddress = true;
            DEBUG_D("connection(%p) not ipv4 address %s", this, proxyAddress->c_str());
        } else {
            ipv6 = false;
            continueCheckAddress = false;
        }
        if (continueCheckAddress) {
            if (inet_pton(AF_INET6, proxyAddress->c_str(), &socketAddress6.sin6_addr.s6_addr) != 1) {
                continueCheckAddress = true;
                DEBUG_D("connection(%p) not ipv6 address %s", this, proxyAddress->c_str());
            } else {
                ipv6 = true;
                continueCheckAddress = false;
            }
            if (continueCheckAddress) {
                std::string host = ConnectionsManager::getInstance(instanceNum).delegate->getHostByName(*proxyAddress, instanceNum);
                if (host.empty() || inet_pton(AF_INET, host.c_str(), &socketAddress.sin_addr.s_addr) != 1) {
                    continueCheckAddress = true;
                    DEBUG_E("connection(%p) can't resolve host %s address via delegate", this, proxyAddress->c_str());
                } else {
                    continueCheckAddress = false;
                    DEBUG_D("connection(%p) resolved host %s address %x via delegate", this, proxyAddress->c_str(), socketAddress.sin_addr.s_addr);
                }
                if (continueCheckAddress) {
                    struct hostent *he;
                    if ((he = gethostbyname(proxyAddress->c_str())) == nullptr) {
                        DEBUG_E("connection(%p) can't resolve host %s address", this, proxyAddress->c_str());
                        closeSocket(1, -1);
                        return;
                    }
                    struct in_addr **addr_list = (struct in_addr **) he->h_addr_list;
                    if (addr_list[0] != nullptr) {
                        socketAddress.sin_addr.s_addr = addr_list[0]->s_addr;
                        DEBUG_D("connection(%p) resolved host %s address %x", this, proxyAddress->c_str(), addr_list[0]->s_addr);
                        ipv6 = false;
                    } else {
                        DEBUG_E("connection(%p) can't resolve host %s address", this, proxyAddress->c_str());
                        closeSocket(1, -1);
                        return;
                    }
                }
            }
        }
    } else {
        proxyAuthState = 0;
        if ((socketFd = socket(ipv6 ? AF_INET6 : AF_INET, SOCK_STREAM, 0)) < 0) {
            DEBUG_E("connection(%p) can't create socket", this);
            closeSocket(1, -1);
            return;
        }
        if (ipv6) {
            socketAddress6.sin6_family = AF_INET6;
            socketAddress6.sin6_port = htons(port);
            if (inet_pton(AF_INET6, address.c_str(), &socketAddress6.sin6_addr.s6_addr) != 1) {
                DEBUG_E("connection(%p) bad ipv6 %s", this, address.c_str());
                closeSocket(1, -1);
                return;
            }
        } else {
            socketAddress.sin_family = AF_INET;
            socketAddress.sin_port = htons(port);
            if (inet_pton(AF_INET, address.c_str(), &socketAddress.sin_addr.s_addr) != 1) {
                DEBUG_E("connection(%p) bad ipv4 %s", this, address.c_str());
                closeSocket(1, -1);
                return;
            }
        }
    }

    int yes = 1;
    if (setsockopt(socketFd, IPPROTO_TCP, TCP_NODELAY, &yes, sizeof(int))) {
        DEBUG_E("connection(%p) set TCP_NODELAY failed", this);
    }

    if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
        DEBUG_E("connection(%p) set O_NONBLOCK failed", this);
        closeSocket(1, -1);
        return;
    }

    if (connect(socketFd, (ipv6 ? (sockaddr *) &socketAddress6 : (sockaddr *) &socketAddress), (socklen_t) (ipv6 ? sizeof(sockaddr_in6) : sizeof(sockaddr_in))) == -1 && errno != EINPROGRESS) {
        closeSocket(1, -1);
    } else {
        eventMask.events = EPOLLOUT | EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
        eventMask.data.ptr = eventObject;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, socketFd, &eventMask) != 0) {
            DEBUG_E("connection(%p) epoll_ctl, adding socket failed", this);
            closeSocket(1, -1);
        }
    }
}

int32_t ConnectionSocket::checkSocketError(int32_t *error) {
    if (socketFd < 0) {
        return true;
    }
    int ret;
    int code;
    socklen_t len = sizeof(int);
    ret = getsockopt(socketFd, SOL_SOCKET, SO_ERROR, &code, &len);
    if (ret != 0 || code != 0) {
        DEBUG_E("socket error 0x%x code 0x%x", ret, code);
    }
    *error = code;
    return (ret || code) != 0;
}

void ConnectionSocket::closeSocket(int32_t reason, int32_t error) {
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
    ConnectionsManager::getInstance(instanceNum).detachConnection(this);
    if (socketFd >= 0) {
        epoll_ctl(ConnectionsManager::getInstance(instanceNum).epolFd, EPOLL_CTL_DEL, socketFd, NULL);
        if (close(socketFd) != 0) {
            DEBUG_E("connection(%p) unable to close socket", this);
        }
        socketFd = -1;
    }
    proxyAuthState = 0;
    onConnectedSent = false;
    outgoingByteStream->clean();
    onDisconnected(reason, error);
}

void ConnectionSocket::onEvent(uint32_t events) {
    if (events & EPOLLIN) {
        int32_t error;
        if (checkSocketError(&error) != 0) {
            closeSocket(1, error);
            return;
        } else {
            ssize_t readCount;
            NativeByteBuffer *buffer = ConnectionsManager::getInstance(instanceNum).networkBuffer;
            while (true) {
                buffer->rewind();
                readCount = recv(socketFd, buffer->bytes(), READ_BUFFER_SIZE, 0);
                if (readCount < 0) {
                    closeSocket(1, -1);
                    DEBUG_E("connection(%p) recv failed", this);
                    return;
                }
                if (readCount > 0) {
                    buffer->limit((uint32_t) readCount);
                    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                    if (proxyAuthState == 2) {
                        if (readCount == 2) {
                            uint8_t auth_method = buffer->bytes()[1];
                            if (auth_method == 0xff) {
                                closeSocket(1, -1);
                                DEBUG_E("connection(%p) unsupported proxy auth method", this);
                            } else if (auth_method == 0x02) {
                                DEBUG_D("connection(%p) proxy auth required", this);
                                proxyAuthState = 3;
                            } else if (auth_method == 0x00) {
                                proxyAuthState = 5;
                            }
                            adjustWriteOp();
                        } else {
                            closeSocket(1, -1);
                            DEBUG_E("connection(%p) invalid proxy response on state 2", this);
                        }
                    } else if (proxyAuthState == 4) {
                        if (readCount == 2) {
                            uint8_t auth_method = buffer->bytes()[1];
                            if (auth_method != 0x00) {
                                closeSocket(1, -1);
                                DEBUG_E("connection(%p) auth invalid", this);
                            } else {
                                proxyAuthState = 5;
                            }
                            adjustWriteOp();
                        } else {
                            closeSocket(1, -1);
                            DEBUG_E("connection(%p) invalid proxy response on state 4", this);
                        }
                    } else if (proxyAuthState == 6) {
                        if (readCount > 2) {
                            uint8_t status = buffer->bytes()[1];
                            if (status == 0x00) {
                                DEBUG_D("connection(%p) connected via proxy", this);
                                proxyAuthState = 0;
                                adjustWriteOp();
                            } else {
                                closeSocket(1, -1);
                                DEBUG_E("connection(%p) invalid proxy status on state 6, 0x%x", this, status);
                            }
                        } else {
                            closeSocket(1, -1);
                            DEBUG_E("connection(%p) invalid proxy response on state 6", this);
                        }
                    } else if (proxyAuthState == 0) {
                        if (ConnectionsManager::getInstance(instanceNum).delegate != nullptr) {
                            ConnectionsManager::getInstance(instanceNum).delegate->onBytesReceived((int32_t) readCount, currentNetworkType, instanceNum);
                        }
                        onReceivedData(buffer);
                    }
                }
                if (readCount != READ_BUFFER_SIZE) {
                    break;
                }
            }
        }
    }
    if (events & EPOLLOUT) {
        int32_t error;
        if (checkSocketError(&error) != 0) {
            closeSocket(1, error);
            return;
        } else {
            if (proxyAuthState != 0) {
                if (proxyAuthState == 1) {
                    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                    proxyAuthState = 2;
                    buffer[0] = 0x05;
                    buffer[1] = 0x02;
                    buffer[2] = 0x00;
                    buffer[3] = 0x02;
                    if (send(socketFd, buffer, 4, 0) < 0) {
                        DEBUG_E("connection(%p) send failed", this);
                        closeSocket(1, -1);
                        return;
                    }
                    adjustWriteOp();
                } else if (proxyAuthState == 3) {
                    buffer[0] = 0x01;
                    std::string *proxyUser;
                    std::string *proxyPassword;
                    if (!overrideProxyAddress.empty()) {
                        proxyUser = &overrideProxyUser;
                        proxyPassword = &overrideProxyPassword;
                    } else {
                        proxyUser = &ConnectionsManager::getInstance(instanceNum).proxyUser;
                        proxyPassword = &ConnectionsManager::getInstance(instanceNum).proxyPassword;
                    }
                    uint8_t len1 = (uint8_t) proxyUser->length();
                    uint8_t len2 = (uint8_t) proxyPassword->length();
                    buffer[1] = len1;
                    memcpy(&buffer[2], proxyUser->c_str(), len1);
                    buffer[2 + len1] = len2;
                    memcpy(&buffer[3 + len1], proxyPassword->c_str(), len2);
                    proxyAuthState = 4;
                    if (send(socketFd, buffer, 3 + len1 + len2, 0) < 0) {
                        DEBUG_E("connection(%p) send failed", this);
                        closeSocket(1, -1);
                        return;
                    }
                    adjustWriteOp();
                } else if (proxyAuthState == 5) {
                    buffer[0] = 0x05;
                    buffer[1] = 0x01;
                    buffer[2] = 0x00;
                    buffer[3] = (uint8_t) (isIpv6 ? 0x04 : 0x01);
                    uint16_t networkPort = ntohs(currentPort);
                    inet_pton(isIpv6 ? AF_INET6 : AF_INET, currentAddress.c_str(), &buffer[4]);
                    memcpy(&buffer[4 + (isIpv6 ? 16 : 4)], &networkPort, sizeof(uint16_t));
                    proxyAuthState = 6;
                    if (send(socketFd, buffer, 4 + (isIpv6 ? 16 : 4) + 2, 0) < 0) {
                        DEBUG_E("connection(%p) send failed", this);
                        closeSocket(1, -1);
                        return;
                    }
                    adjustWriteOp();
                }
            } else {
                if (!onConnectedSent) {
                    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                    onConnected();
                    onConnectedSent = true;
                }
                NativeByteBuffer *buffer = ConnectionsManager::getInstance(instanceNum).networkBuffer;
                buffer->clear();
                outgoingByteStream->get(buffer);
                buffer->flip();

                uint32_t remaining = buffer->remaining();
                if (remaining) {
                    ssize_t sentLength;
                    if ((sentLength = send(socketFd, buffer->bytes(), remaining, 0)) < 0) {
                        DEBUG_E("connection(%p) send failed", this);
                        closeSocket(1, -1);
                        return;
                    } else {
                        if (ConnectionsManager::getInstance(instanceNum).delegate != nullptr) {
                            ConnectionsManager::getInstance(instanceNum).delegate->onBytesSent((int32_t) sentLength, currentNetworkType, instanceNum);
                        }
                        outgoingByteStream->discard((uint32_t) sentLength);
                        adjustWriteOp();
                    }
                }
            }
        }
    }
    if (events & EPOLLHUP) {
        DEBUG_E("socket event has EPOLLHUP");
        closeSocket(1, -1);
        return;
    } else if (events & EPOLLRDHUP) {
        DEBUG_E("socket event has EPOLLRDHUP");
        closeSocket(1, -1);
        return;
    }
    if (events & EPOLLERR) {
        DEBUG_E("connection(%p) epoll error", this);
        return;
    }
}

void ConnectionSocket::writeBuffer(uint8_t *data, uint32_t size) {
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(size);
    buffer->writeBytes(data, size);
    outgoingByteStream->append(buffer);
    adjustWriteOp();
}

void ConnectionSocket::writeBuffer(NativeByteBuffer *buffer) {
    outgoingByteStream->append(buffer);
    adjustWriteOp();
}

void ConnectionSocket::adjustWriteOp() {
    eventMask.events = EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
    if (proxyAuthState == 0 && (outgoingByteStream->hasData() || !onConnectedSent) || proxyAuthState == 1 || proxyAuthState == 3 || proxyAuthState == 5) {
        eventMask.events |= EPOLLOUT;
    }
    eventMask.data.ptr = eventObject;
    if (epoll_ctl(ConnectionsManager::getInstance(instanceNum).epolFd, EPOLL_CTL_MOD, socketFd, &eventMask) != 0) {
        DEBUG_E("connection(%p) epoll_ctl, modify socket failed", this);
        closeSocket(1, -1);
    }
}

void ConnectionSocket::setTimeout(time_t time) {
    timeout = time;
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
}

time_t ConnectionSocket::getTimeout() {
    return timeout;
}

void ConnectionSocket::checkTimeout(int64_t now) {
    if (timeout != 0 && (now - lastEventTime) > (int64_t) timeout * 1000) {
        closeSocket(2, 0);
    }
}

bool ConnectionSocket::isDisconnected() {
    return socketFd < 0;
}

void ConnectionSocket::dropConnection() {
    closeSocket(0, 0);
}

void ConnectionSocket::setOverrideProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret) {
    overrideProxyAddress = address;
    overrideProxyPort = port;
    overrideProxyUser = username;
    overrideProxyPassword = password;
    overrideProxySecret = secret;
}