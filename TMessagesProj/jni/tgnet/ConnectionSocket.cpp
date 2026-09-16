/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <cassert>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <sys/socket.h>
#include <memory.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <openssl/rand.h>
#include <openssl/hmac.h>
#include <algorithm>
#include <utility>
#include <openssl/bn.h>
#include "ByteStream.h"
#include "ConnectionSocket.h"
#include "FileLog.h"
#include "Defines.h"
#include "ConnectionsManager.h"
#include "EventObject.h"
#include "Timer.h"
#include "NativeByteBuffer.h"
#include "BuffersStorage.h"
#include "Connection.h"
#include "TLSHello.h"
#include <random>

#ifndef EPOLLRDHUP
#define EPOLLRDHUP 0x2000
#endif

#define MAX_GREASE 8

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
    if (tempBuffer != nullptr) {
        delete tempBuffer;
        tempBuffer = nullptr;
    }
    if (tlsBuffer != nullptr) {
        tlsBuffer->reuse();
        tlsBuffer = nullptr;
    }
}

void ConnectionSocket::openConnection(std::string address, uint16_t port, std::string secret, bool ipv6, int32_t networkType) {
    currentNetworkType = networkType;
    isIpv6 = ipv6;
    currentAddress = address;
    currentPort = port;
    waitingForHostResolve = "";
    adjustWriteOpAfterResolve = false;
    tlsState = 0;
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

    if (!proxyAddress->empty()) {
        if (LOGS_ENABLED) DEBUG_D("connection(%p) connecting via proxy %s:%d secret[%d]", this, proxyAddress->c_str(), proxyPort, (int) proxySecret->size());
        if ((socketFd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) can't create proxy socket", this);
            closeSocket(1, -1);
            return;
        }
        uint32_t tempBuffLength;
        if (proxySecret->empty()) {
            proxyAuthState = 1;
            tempBuffLength = 1024;
        } else if (proxySecret->size() > 17 && (*proxySecret)[0] == '\xee') {
            proxyAuthState = 10;
            currentSecret = proxySecret->substr(1, 16);
            currentSecretDomain = proxySecret->substr(17);
            tempBuffLength = 65 * 1024;
        } else {
            proxyAuthState = 0;
            tempBuffLength = 0;
        }
        if (tempBuffLength > 0) {
            if (tempBuffer == nullptr || tempBuffer->length < tempBuffLength) {
                if (tempBuffer != nullptr) {
                    delete tempBuffer;
                }
                tempBuffer = new ByteArray(tempBuffLength);
            }
        }
        socketAddress.sin_family = AF_INET;
        socketAddress.sin_port = htons(proxyPort);
        bool continueCheckAddress;
        if (inet_pton(AF_INET, proxyAddress->c_str(), &socketAddress.sin_addr.s_addr) != 1) {
            continueCheckAddress = true;
            if (LOGS_ENABLED) DEBUG_D("connection(%p) not ipv4 address %s", this, proxyAddress->c_str());
        } else {
            ipv6 = false;
            continueCheckAddress = false;
        }
        if (continueCheckAddress) {
            if (inet_pton(AF_INET6, proxyAddress->c_str(), &socketAddress6.sin6_addr.s6_addr) != 1) {
                continueCheckAddress = true;
                if (LOGS_ENABLED) DEBUG_D("connection(%p) not ipv6 address %s", this, proxyAddress->c_str());
            } else {
                ipv6 = true;
                continueCheckAddress = false;
            }
            if (continueCheckAddress) {
#ifdef USE_DELEGATE_HOST_RESOLVE
                waitingForHostResolve = *proxyAddress;
                ConnectionsManager::getInstance(instanceNum).delegate->getHostByName(*proxyAddress, instanceNum, this);
                return;
#else
                struct hostent *he;
                if ((he = gethostbyname(proxyAddress->c_str())) == nullptr) {
                    if (LOGS_ENABLED) DEBUG_E("connection(%p) can't resolve host %s address", this, proxyAddress->c_str());
                    closeSocket(1, -1);
                    return;
                }
                struct in_addr **addr_list = (struct in_addr **) he->h_addr_list;
                if (addr_list[0] != nullptr) {
                    socketAddress.sin_addr.s_addr = addr_list[0]->s_addr;
                    if (LOGS_ENABLED) DEBUG_D("connection(%p) resolved host %s address %x", this, proxyAddress->c_str(), addr_list[0]->s_addr);
                    ipv6 = false;
                } else {
                    if (LOGS_ENABLED) DEBUG_E("connection(%p) can't resolve host %s address", this, proxyAddress->c_str());
                    closeSocket(1, -1);
                    return;
                }
#endif
            }
        }
    } else {
        proxyAuthState = 0;
        if ((socketFd = socket(ipv6 ? AF_INET6 : AF_INET, SOCK_STREAM, 0)) < 0) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) can't create socket", this);
            closeSocket(1, -1);
            return;
        }
        if (ipv6) {
            socketAddress6.sin6_family = AF_INET6;
            socketAddress6.sin6_port = htons(port);
            if (inet_pton(AF_INET6, address.c_str(), &socketAddress6.sin6_addr.s6_addr) != 1) {
                if (LOGS_ENABLED) DEBUG_E("connection(%p) bad ipv6 %s", this, address.c_str());
                closeSocket(1, -1);
                return;
            }
        } else {
            socketAddress.sin_family = AF_INET;
            socketAddress.sin_port = htons(port);
            if (inet_pton(AF_INET, address.c_str(), &socketAddress.sin_addr.s_addr) != 1) {
                if (LOGS_ENABLED) DEBUG_E("connection(%p) bad ipv4 %s", this, address.c_str());
                closeSocket(1, -1);
                return;
            }
        }
        uint32_t tempBuffLength;
        if (secret.size() > 17 && secret[0] == '\xee') {
            proxyAuthState = 10;
            currentSecret = secret.substr(1, 16);
            currentSecretDomain = secret.substr(17);
            tempBuffLength = 65 * 1024;
        } else {
            proxyAuthState = 0;
            tempBuffLength = 0;
        }
        if (tempBuffLength > 0) {
            if (tempBuffer == nullptr || tempBuffer->length < tempBuffLength) {
                if (tempBuffer != nullptr) {
                    delete tempBuffer;
                }
                tempBuffer = new ByteArray(tempBuffLength);
            }
        }
    }

    openConnectionInternal(ipv6);
}

void ConnectionSocket::openConnectionInternal(bool ipv6) {
    int epolFd = ConnectionsManager::getInstance(instanceNum).epolFd;
    int yes = 1;
    if (setsockopt(socketFd, IPPROTO_TCP, TCP_NODELAY, &yes, sizeof(int))) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) set TCP_NODELAY failed", this);
    }
#ifdef DEBUG_VERSION
    int size = 4 * 1024 * 1024;
    if (setsockopt(socketFd, SOL_SOCKET, SO_SNDBUF, &size, sizeof(int))) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) set SO_SNDBUF failed", this);
    }
    if (setsockopt(socketFd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(int))) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) set SO_RCVBUF failed", this);
    }
#endif

    if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) set O_NONBLOCK failed", this);
        closeSocket(1, -1);
        return;
    }

    if (connect(socketFd, (ipv6 ? (sockaddr *) &socketAddress6 : (sockaddr *) &socketAddress), (socklen_t) (ipv6 ? sizeof(sockaddr_in6) : sizeof(sockaddr_in))) == -1 && errno != EINPROGRESS) {
        closeSocket(1, -1);
    } else {
        eventMask.events = EPOLLOUT | EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
        eventMask.data.ptr = eventObject;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, socketFd, &eventMask) != 0) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) epoll_ctl, adding socket failed", this);
            closeSocket(1, -1);
        }
    }
    if (adjustWriteOpAfterResolve) {
        adjustWriteOp();
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
        if (LOGS_ENABLED) DEBUG_E("socket error 0x%x code 0x%x", ret, code);
    }
    *error = code;
    return (ret || code) != 0;
}

void ConnectionSocket::closeSocket(int32_t reason, int32_t error) {
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
    ConnectionsManager::getInstance(instanceNum).detachConnection(this);
    if (socketFd >= 0) {
        epoll_ctl(ConnectionsManager::getInstance(instanceNum).epolFd, EPOLL_CTL_DEL, socketFd, nullptr);
        if (close(socketFd) != 0) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) unable to close socket", this);
        }
        socketFd = -1;
    }
    waitingForHostResolve = "";
    adjustWriteOpAfterResolve = false;
    proxyAuthState = 0;
    tlsState = 0;
    onConnectedSent = false;
    outgoingByteStream->clean();
    if (tlsBuffer != nullptr) {
        tlsBuffer->reuse();
        tlsBuffer = nullptr;
    }
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
                int err = errno;
//                if (LOGS_ENABLED) DEBUG_D("connection(%p) recv resulted with %d, errno=%d", this, readCount, err);
                if (readCount < 0) {
                    if (err == EAGAIN) {
                        break;
                    }
                    closeSocket(1, -1);
                    if (LOGS_ENABLED) DEBUG_E("connection(%p) recv failed", this);
                    return;
                }
                if (readCount > 0) {
                    buffer->limit((uint32_t) readCount);
                    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                    if (proxyAuthState == 11) {
                        if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS received %d", this, (int) readCount);
                        size_t newBytesRead = bytesRead + readCount;
                        if (newBytesRead > 64 * 1024) {
                            closeSocket(1, -1);
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS client hello too much data", this);
                            return;
                        }
                        if (newBytesRead >= 16) {
                            std::memcpy(tempBuffer->bytes + bytesRead, buffer->bytes(), (size_t) readCount);

                            static std::string hello1 = std::string("\x16\x03\x03", 3);
                            if (std::memcmp(hello1.data(), tempBuffer->bytes, hello1.size()) != 0) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS hello1 mismatch", this);
                                return;
                            }
                            size_t len1 = (tempBuffer->bytes[3] << 8) + tempBuffer->bytes[4];
                            if (len1 > 64 * 1024 - 5) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS len1 invalid", this);
                                return;
                            } else if (newBytesRead < len1 + 5) {
                                if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS client hello wait for more data", this);
                                bytesRead = newBytesRead;
                                return;
                            }

                            static std::string hello2 = std::string("\x14\x03\x03\x00\x01\x01\x17\x03\x03", 9);
                            if (std::memcmp(hello2.data(), tempBuffer->bytes + 5 + len1, hello2.size()) != 0) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS hello2 mismatch", this);
                                return;
                            }
                            size_t len2 = (tempBuffer->bytes[5 + 9 + len1] << 8) + tempBuffer->bytes[5 + 9 + len1 + 1];
                            if (len2 > 64 * 1024 - len1 - 5 - 11) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS len2 invalid", this);
                                return;
                            } else if (newBytesRead < len2 + len1 + 5 + 11) {
                                if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS client hello wait for more data", this);
                                bytesRead = newBytesRead;
                                return;
                            }
                            std::memcpy(tempBuffer->bytes + 64 * 1024 + 32, tempBuffer->bytes + 11, 32);
                            std::memset(tempBuffer->bytes + 11, 0, 32);

                            uint8_t *temp = new uint8_t[32 + newBytesRead];
                            memcpy(temp, tempBuffer->bytes + 64 * 1024, 32);
                            memcpy(temp + 32, tempBuffer->bytes, newBytesRead);
                            uint32_t outLength;
                            HMAC(EVP_sha256(), currentSecret.data(), currentSecret.size(), temp, 32 + newBytesRead, tempBuffer->bytes + 64 * 1024, &outLength);
                            delete[] temp;
                            if (std::memcmp(tempBuffer->bytes + 64 * 1024, tempBuffer->bytes + 64 * 1024 + 32, 32) != 0) {
                                tlsHashMismatch = true;
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS hash mismatch", this);
                                return;
                            }
                            if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS hello complete", this);
                            tlsState = 1;
                            proxyAuthState = 0;
                            bytesRead = 0;
                            adjustWriteOp();
                        } else {
                            std::memcpy(tempBuffer->bytes + bytesRead, buffer->bytes(), (size_t) readCount);
                            bytesRead = newBytesRead;
                        }
                    } else if (proxyAuthState == 2) {
                        if (readCount == 2) {
                            uint8_t auth_method = buffer->bytes()[1];
                            if (auth_method == 0xff) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) unsupported proxy auth method", this);
                            } else if (auth_method == 0x02) {
                                if (LOGS_ENABLED) DEBUG_D("connection(%p) proxy auth required", this);
                                proxyAuthState = 3;
                            } else if (auth_method == 0x00) {
                                proxyAuthState = 5;
                            }
                            adjustWriteOp();
                        } else {
                            closeSocket(1, -1);
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) invalid proxy response on state 2", this);
                        }
                    } else if (proxyAuthState == 4) {
                        if (readCount == 2) {
                            uint8_t auth_method = buffer->bytes()[1];
                            if (auth_method != 0x00) {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) auth invalid", this);
                            } else {
                                proxyAuthState = 5;
                            }
                            adjustWriteOp();
                        } else {
                            closeSocket(1, -1);
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) invalid proxy response on state 4", this);
                        }
                    } else if (proxyAuthState == 6) {
                        if (readCount > 2) {
                            uint8_t status = buffer->bytes()[1];
                            if (status == 0x00) {
                                if (LOGS_ENABLED) DEBUG_D("connection(%p) connected via proxy", this);
                                proxyAuthState = 0;
                                adjustWriteOp();
                            } else {
                                closeSocket(1, -1);
                                if (LOGS_ENABLED) DEBUG_E("connection(%p) invalid proxy status on state 6, 0x%x", this, status);
                            }
                        } else {
                            closeSocket(1, -1);
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) invalid proxy response on state 6", this);
                        }
                    } else if (proxyAuthState == 0) {
                        if (ConnectionsManager::getInstance(instanceNum).delegate != nullptr) {
                            ConnectionsManager::getInstance(instanceNum).delegate->onBytesReceived((int32_t) readCount, currentNetworkType, instanceNum);
                        }
                        if (tlsState != 0) {
                            while (buffer->hasRemaining()) {
                                size_t newBytesRead = buffer->remaining();
                                if (tlsBuffer != nullptr) {
                                    newBytesRead += tlsBuffer->position();
                                    if (tlsBufferSized) {
                                        newBytesRead += 5;
                                    }
                                }
                                if (newBytesRead >= 5) {
                                    if (tlsBuffer == nullptr || !tlsBufferSized) {
                                        uint32_t pos = buffer->position();

                                        uint8_t offset = 0;
                                        uint8_t header[5];
                                        if (tlsBuffer != nullptr) {
                                            offset = (uint8_t) tlsBuffer->position();
                                            memcpy(header, tlsBuffer->bytes(), offset);
                                            tlsBuffer->reuse();
                                            tlsBuffer = nullptr;
                                        }
                                        memcpy(header + offset, buffer->bytes() + pos, (uint8_t) (5 - offset));

                                        static std::string header1 = std::string("\x17\x03\x03", 3);
                                        if (std::memcmp(header1.data(), header, header1.size()) != 0) {
                                            closeSocket(1, -1);
                                            if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS response header1 mismatch", this);
                                            return;
                                        }
                                        uint32_t len1 = (header[3] << 8) + header[4];
                                        if (len1 > 64 * 1024) {
                                            closeSocket(1, -1);
                                            if (LOGS_ENABLED) DEBUG_E("connection(%p) TLS response len1 invalid", this);
                                            return;
                                        } else {
                                            tlsBuffer = BuffersStorage::getInstance().getFreeBuffer(len1);
                                            tlsBufferSized = true;
                                            buffer->position(pos + (5 - offset));
                                        }
                                    } else {
                                        if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS response new data %d", this, buffer->remaining());
                                    }
                                    buffer->limit(std::min(buffer->position() + tlsBuffer->remaining(), buffer->limit()));
                                    tlsBuffer->writeBytes(buffer);
                                    buffer->limit((uint32_t) readCount);
                                    if (tlsBuffer->remaining() == 0) {
                                        tlsBuffer->rewind();
                                        onReceivedData(tlsBuffer);
                                        if (tlsBuffer == nullptr) {
                                            return;
                                        }
                                        tlsBuffer->reuse();
                                        tlsBuffer = nullptr;
                                    } else {
                                        if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS response wait for more data, total size %d, left %d", this, tlsBuffer->limit(), tlsBuffer->remaining());
                                    }
                                } else {
                                    if (tlsBuffer == nullptr) {
                                        tlsBuffer = BuffersStorage::getInstance().getFreeBuffer(4);
                                        tlsBufferSized = false;
                                    }
                                    tlsBuffer->writeBytes(buffer);
                                    if (LOGS_ENABLED) DEBUG_D("connection(%p) TLS response wait for more data, not enough bytes for header, total = %d", this, (int) tlsBuffer->position());
                                }
                            }
                        } else {
                            onReceivedData(buffer);
                        }
                    }
                } else if (readCount == 0) {
                    break;
                }
//                if (readCount != READ_BUFFER_SIZE) {
//                    break;
//                }
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
                if (proxyAuthState >= 10) {
                    if (proxyAuthState == 10) {
                        lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                        tlsHashMismatch = false;
                        proxyAuthState = 11;
                        TLSHello hello = TLSHello::getDefault();
                        hello.setDomain(currentSecretDomain);
                        uint32_t size = hello.writeToBuffer(tempBuffer->bytes);
                        uint32_t outLength;
                        HMAC(EVP_sha256(), currentSecret.data(), currentSecret.size(), tempBuffer->bytes, size, tempBuffer->bytes + 64 * 1024, &outLength);

                        int32_t currentTime = ConnectionsManager::getInstance(instanceNum).getCurrentTime();
                        int32_t old = ((int32_t *) (tempBuffer->bytes + 64 * 1024 + 28))[0];
                        ((int32_t *) (tempBuffer->bytes + 64 * 1024 + 28))[0] = old ^ currentTime;

                        memcpy(tempBuffer->bytes + 11, tempBuffer->bytes + 64 * 1024, 32);
                        bytesRead = 0;

                        if (send(socketFd, tempBuffer->bytes, size, 0) < 0) {
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) send failed", this);
                            closeSocket(1, -1);
                            return;
                        }
                        adjustWriteOp();
                    }
                } else {
                    if (proxyAuthState == 1) {
                        lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                        proxyAuthState = 2;
                        tempBuffer->bytes[0] = 0x05;
                        tempBuffer->bytes[1] = 0x02;
                        tempBuffer->bytes[2] = 0x00;
                        tempBuffer->bytes[3] = 0x02;
                        if (send(socketFd, tempBuffer->bytes, 4, 0) < 0) {
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) send failed", this);
                            closeSocket(1, -1);
                            return;
                        }
                        adjustWriteOp();
                    } else if (proxyAuthState == 3) {
                        tempBuffer->bytes[0] = 0x01;
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
                        tempBuffer->bytes[1] = len1;
                        memcpy(tempBuffer->bytes + 2, proxyUser->c_str(), len1);
                        tempBuffer->bytes[2 + len1] = len2;
                        memcpy(tempBuffer->bytes + 3 + len1, proxyPassword->c_str(), len2);
                        proxyAuthState = 4;
                        if (send(socketFd, tempBuffer->bytes, 3 + len1 + len2, 0) < 0) {
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) send failed", this);
                            closeSocket(1, -1);
                            return;
                        }
                        adjustWriteOp();
                    } else if (proxyAuthState == 5) {
                        tempBuffer->bytes[0] = 0x05;
                        tempBuffer->bytes[1] = 0x01;
                        tempBuffer->bytes[2] = 0x00;
                        tempBuffer->bytes[3] = (uint8_t) (isIpv6 ? 0x04 : 0x01);
                        uint16_t networkPort = ntohs(currentPort);
                        inet_pton(isIpv6 ? AF_INET6 : AF_INET, currentAddress.c_str(), tempBuffer->bytes + 4);
                        memcpy(tempBuffer->bytes + 4 + (isIpv6 ? 16 : 4), &networkPort, sizeof(uint16_t));
                        proxyAuthState = 6;
                        if (send(socketFd, tempBuffer->bytes, 4 + (isIpv6 ? 16 : 4) + 2, 0) < 0) {
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) send failed", this);
                            closeSocket(1, -1);
                            return;
                        }
                        adjustWriteOp();
                    }
                }
            } else {
                if (!onConnectedSent) {
                    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
                    if (LOGS_ENABLED) DEBUG_D("connection(%p) reset last event time, on connect", this);
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
                    if (tlsState != 0) {
                        if (remaining > 2878) {
                            remaining = 2878;
                        }
                        size_t headersSize = 0;
                        if (tlsState == 1) {
                            static std::string header1 = std::string("\x14\x03\x03\x00\x01\x01", 6);
                            std::memcpy(tempBuffer->bytes, header1.data(), header1.size());
                            headersSize += header1.size();
                            tlsState = 2;
                        }
                        static std::string header2 = std::string("\x17\x03\x03", 3);
                        std::memcpy(tempBuffer->bytes + headersSize, header2.data(), header2.size());
                        headersSize += header2.size();

                        tempBuffer->bytes[headersSize] = static_cast<uint8_t>((remaining >> 8) & 0xff);
                        tempBuffer->bytes[headersSize + 1] = static_cast<uint8_t>(remaining & 0xff);
                        headersSize += 2;

                        std::memcpy(tempBuffer->bytes + headersSize, buffer->bytes(), remaining);

                        if ((sentLength = send(socketFd, tempBuffer->bytes, headersSize + remaining, 0)) < headersSize) {
                            if (LOGS_ENABLED) DEBUG_E("connection(%p) send failed", this);
                            closeSocket(1, -1);
                            return;
                        } else {
                            if (ConnectionsManager::getInstance(instanceNum).delegate != nullptr) {
                                ConnectionsManager::getInstance(instanceNum).delegate->onBytesSent((int32_t) sentLength, currentNetworkType, instanceNum);
                            }
                            outgoingByteStream->discard((uint32_t) (sentLength - headersSize));
                            adjustWriteOp();
                        }
                    } else {
                        if ((sentLength = send(socketFd, buffer->bytes(), remaining, 0)) < 0) {
                            if (LOGS_ENABLED) DEBUG_D("connection(%p) send failed", this);
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
    }
    if (events & EPOLLHUP) {
        if (LOGS_ENABLED) DEBUG_E("socket event has EPOLLHUP");
        closeSocket(1, -1);
        return;
    } else if (events & EPOLLRDHUP) {
        if (LOGS_ENABLED) DEBUG_E("socket event has EPOLLRDHUP");
        closeSocket(1, -1);
        return;
    }
    if (events & EPOLLERR) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) epoll error", this);
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
    if (!waitingForHostResolve.empty()) {
        adjustWriteOpAfterResolve = true;
        return;
    }
    eventMask.events = EPOLLIN | EPOLLRDHUP | EPOLLERR | EPOLLET;
    if (proxyAuthState == 0 && (outgoingByteStream->hasData() || !onConnectedSent) || proxyAuthState == 1 || proxyAuthState == 3 || proxyAuthState == 5 || proxyAuthState == 10) {
        eventMask.events |= EPOLLOUT;
    }
    eventMask.data.ptr = eventObject;
    if (epoll_ctl(ConnectionsManager::getInstance(instanceNum).epolFd, EPOLL_CTL_MOD, socketFd, &eventMask) != 0) {
        if (LOGS_ENABLED) DEBUG_E("connection(%p) epoll_ctl, modify socket failed", this);
        closeSocket(1, -1);
    }
}

void ConnectionSocket::setTimeout(time_t time) {
    timeout = time;
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
    if (LOGS_ENABLED) DEBUG_D("connection(%p) set current timeout = %lld", this, (long long) timeout);
}

time_t ConnectionSocket::getTimeout() {
    return timeout;
}

bool ConnectionSocket::checkTimeout(int64_t now) {
    if (timeout != 0 && (now - lastEventTime) > (int64_t) timeout * 1000) {
        if (!onConnectedSent || hasPendingRequests()) {
            closeSocket(2, 0);
            return true;
        } else {
            lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
            if (LOGS_ENABLED) DEBUG_D("connection(%p) reset last event time, no requests", this);
        }
    }
    return false;
}

bool ConnectionSocket::hasTlsHashMismatch() {
    return tlsHashMismatch;
}

void ConnectionSocket::resetLastEventTime() {
    lastEventTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMonotonicMillis();
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

void ConnectionSocket::onHostNameResolved(std::string host, std::string ip, bool ipv6) {
    ConnectionsManager::getInstance(instanceNum).scheduleTask([&, host, ip, ipv6] {
        if (waitingForHostResolve == host) {
            waitingForHostResolve = "";
            if (ip.empty() || inet_pton(AF_INET, ip.c_str(), &socketAddress.sin_addr.s_addr) != 1) {
                if (LOGS_ENABLED) DEBUG_E("connection(%p) can't resolve host %s address via delegate", this, host.c_str());
                closeSocket(1, -1);
                return;
            }
            if (LOGS_ENABLED) DEBUG_D("connection(%p) resolved host %s address %s via delegate", this, host.c_str(), ip.c_str());
            openConnectionInternal(ipv6);
        }
    });
}
