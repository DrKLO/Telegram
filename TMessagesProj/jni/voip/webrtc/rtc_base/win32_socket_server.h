/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_WIN32_SOCKET_SERVER_H_
#define RTC_BASE_WIN32_SOCKET_SERVER_H_

#if defined(WEBRTC_WIN)
#include "rtc_base/async_socket.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread.h"
#include "rtc_base/win32_window.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// Win32Socket
///////////////////////////////////////////////////////////////////////////////

class Win32Socket : public AsyncSocket {
 public:
  Win32Socket();
  ~Win32Socket() override;

  bool CreateT(int family, int type);

  int Attach(SOCKET s);
  void SetTimeout(int ms);

  // AsyncSocket Interface
  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;
  int Bind(const SocketAddress& addr) override;
  int Connect(const SocketAddress& addr) override;
  int Send(const void* buffer, size_t length) override;
  int SendTo(const void* buffer,
             size_t length,
             const SocketAddress& addr) override;
  int Recv(void* buffer, size_t length, int64_t* timestamp) override;
  int RecvFrom(void* buffer,
               size_t length,
               SocketAddress* out_addr,
               int64_t* timestamp) override;
  int Listen(int backlog) override;
  Win32Socket* Accept(SocketAddress* out_addr) override;
  int Close() override;
  int GetError() const override;
  void SetError(int error) override;
  ConnState GetState() const override;
  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;

 private:
  void CreateSink();
  bool SetAsync(int events);
  int DoConnect(const SocketAddress& addr);
  bool HandleClosed(int close_error);
  void PostClosed();
  void UpdateLastError();
  static int TranslateOption(Option opt, int* slevel, int* sopt);

  void OnSocketNotify(SOCKET socket, int event, int error);
  void OnDnsNotify(HANDLE task, int error);

  SOCKET socket_;
  int error_;
  ConnState state_;
  SocketAddress addr_;  // address that we connected to (see DoConnect)
  uint32_t connect_time_;
  bool closing_;
  int close_error_;

  class EventSink;
  friend class EventSink;
  EventSink* sink_;

  struct DnsLookup;
  DnsLookup* dns_;
};

///////////////////////////////////////////////////////////////////////////////
// Win32SocketServer
///////////////////////////////////////////////////////////////////////////////

class Win32SocketServer : public SocketServer {
 public:
  Win32SocketServer();
  ~Win32SocketServer() override;

  void set_modeless_dialog(HWND hdlg) { hdlg_ = hdlg; }

  // SocketServer Interface
  Socket* CreateSocket(int family, int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

  void SetMessageQueue(Thread* queue) override;
  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

  void Pump();

  HWND handle() { return wnd_.handle(); }

 private:
  class MessageWindow : public Win32Window {
   public:
    explicit MessageWindow(Win32SocketServer* ss) : ss_(ss) {}

   private:
    bool OnMessage(UINT msg, WPARAM wp, LPARAM lp, LRESULT& result) override;
    Win32SocketServer* ss_;
  };

  static const wchar_t kWindowName[];
  Thread* message_queue_;
  MessageWindow wnd_;
  webrtc::Mutex mutex_;
  bool posted_;
  HWND hdlg_;
};

///////////////////////////////////////////////////////////////////////////////
// Win32Thread. Automatically pumps Windows messages.
///////////////////////////////////////////////////////////////////////////////

class Win32Thread : public Thread {
 public:
  explicit Win32Thread(SocketServer* ss);
  ~Win32Thread() override;

  void Run() override;
  void Quit() override;

 private:
  DWORD id_;
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_WIN

#endif  // RTC_BASE_WIN32_SOCKET_SERVER_H_
