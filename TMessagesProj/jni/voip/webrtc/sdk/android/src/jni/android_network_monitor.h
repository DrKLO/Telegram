/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef SDK_ANDROID_SRC_JNI_ANDROID_NETWORK_MONITOR_H_
#define SDK_ANDROID_SRC_JNI_ANDROID_NETWORK_MONITOR_H_

#include <stdint.h>

#include <map>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "rtc_base/network_monitor.h"
#include "rtc_base/network_monitor_factory.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

typedef int64_t NetworkHandle;

// c++ equivalent of java NetworkChangeDetector.ConnectionType.
enum NetworkType {
  NETWORK_UNKNOWN,
  NETWORK_ETHERNET,
  NETWORK_WIFI,
  NETWORK_5G,
  NETWORK_4G,
  NETWORK_3G,
  NETWORK_2G,
  NETWORK_UNKNOWN_CELLULAR,
  NETWORK_BLUETOOTH,
  NETWORK_VPN,
  NETWORK_NONE
};

// The information is collected from Android OS so that the native code can get
// the network type and handle (Android network ID) for each interface.
struct NetworkInformation {
  std::string interface_name;
  NetworkHandle handle;
  NetworkType type;
  NetworkType underlying_type_for_vpn;
  std::vector<rtc::IPAddress> ip_addresses;

  NetworkInformation();
  NetworkInformation(const NetworkInformation&);
  NetworkInformation(NetworkInformation&&);
  ~NetworkInformation();
  NetworkInformation& operator=(const NetworkInformation&);
  NetworkInformation& operator=(NetworkInformation&&);

  std::string ToString() const;
};

class AndroidNetworkMonitor : public rtc::NetworkMonitorInterface {
 public:
  AndroidNetworkMonitor(JNIEnv* env,
                        const JavaRef<jobject>& j_application_context);
  ~AndroidNetworkMonitor() override;

  // TODO(sakal): Remove once down stream dependencies have been updated.
  static void SetAndroidContext(JNIEnv* jni, jobject context) {}

  void Start() override;
  void Stop() override;

  // Does `this` NetworkMonitorInterface implement BindSocketToNetwork?
  // Only Android returns true.
  virtual bool SupportsBindSocketToNetwork() const override { return true; }

  rtc::NetworkBindingResult BindSocketToNetwork(
      int socket_fd,
      const rtc::IPAddress& address,
      const std::string& if_name) override;
  rtc::AdapterType GetAdapterType(const std::string& if_name) override;
  rtc::AdapterType GetVpnUnderlyingAdapterType(
      const std::string& if_name) override;
  rtc::NetworkPreference GetNetworkPreference(
      const std::string& if_name) override;

  // Always expected to be called on the network thread.
  void SetNetworkInfos(const std::vector<NetworkInformation>& network_infos);

  void NotifyConnectionTypeChanged(JNIEnv* env,
                                   const JavaRef<jobject>& j_caller);
  void NotifyOfNetworkConnect(JNIEnv* env,
                              const JavaRef<jobject>& j_caller,
                              const JavaRef<jobject>& j_network_info);
  void NotifyOfNetworkDisconnect(JNIEnv* env,
                                 const JavaRef<jobject>& j_caller,
                                 jlong network_handle);
  void NotifyOfActiveNetworkList(JNIEnv* env,
                                 const JavaRef<jobject>& j_caller,
                                 const JavaRef<jobjectArray>& j_network_infos);
  void NotifyOfNetworkPreference(JNIEnv* env,
                                 const JavaRef<jobject>& j_caller,
                                 const JavaRef<jobject>& j_connection_type,
                                 jint preference);

  // Visible for testing.
  absl::optional<NetworkHandle> FindNetworkHandleFromAddressOrName(
      const rtc::IPAddress& address,
      const std::string& ifname) const;

 private:
  void OnNetworkConnected_n(const NetworkInformation& network_info);
  void OnNetworkDisconnected_n(NetworkHandle network_handle);
  void OnNetworkPreference_n(NetworkType type,
                             rtc::NetworkPreference preference);

  absl::optional<NetworkHandle> FindNetworkHandleFromIfname(
      const std::string& ifname) const;

  const int android_sdk_int_;
  ScopedJavaGlobalRef<jobject> j_application_context_;
  ScopedJavaGlobalRef<jobject> j_network_monitor_;
  rtc::Thread* const network_thread_;
  bool started_ RTC_GUARDED_BY(network_thread_) = false;
  std::map<std::string, rtc::AdapterType> adapter_type_by_name_
      RTC_GUARDED_BY(network_thread_);
  std::map<std::string, rtc::AdapterType> vpn_underlying_adapter_type_by_name_
      RTC_GUARDED_BY(network_thread_);
  std::map<rtc::IPAddress, NetworkHandle> network_handle_by_address_
      RTC_GUARDED_BY(network_thread_);
  std::map<NetworkHandle, NetworkInformation> network_info_by_handle_
      RTC_GUARDED_BY(network_thread_);
  std::map<rtc::AdapterType, rtc::NetworkPreference>
      network_preference_by_adapter_type_ RTC_GUARDED_BY(network_thread_);
  bool find_network_handle_without_ipv6_temporary_part_
      RTC_GUARDED_BY(network_thread_) = false;
  bool surface_cellular_types_ RTC_GUARDED_BY(network_thread_) = false;

  // NOTE: if bind_using_ifname_ is TRUE
  // then the adapter name is used with substring matching as follows:
  // An adapater name repored by android as 'wlan0'
  // will be matched with 'v4-wlan0' ("v4-wlan0".find("wlan0") != npos).
  // This applies to adapter_type_by_name_, vpn_underlying_adapter_type_by_name_
  // and FindNetworkHandleFromIfname.
  bool bind_using_ifname_ RTC_GUARDED_BY(network_thread_) = true;
  rtc::scoped_refptr<PendingTaskSafetyFlag> safety_flag_
      RTC_PT_GUARDED_BY(network_thread_) = nullptr;
};

class AndroidNetworkMonitorFactory : public rtc::NetworkMonitorFactory {
 public:
  // Deprecated. Pass in application context to this class.
  AndroidNetworkMonitorFactory();

  AndroidNetworkMonitorFactory(JNIEnv* env,
                               const JavaRef<jobject>& j_application_context);

  ~AndroidNetworkMonitorFactory() override;

  rtc::NetworkMonitorInterface* CreateNetworkMonitor() override;

 private:
  ScopedJavaGlobalRef<jobject> j_application_context_;
};

}  // namespace jni
}  // namespace webrtc

// TODO(magjed): Remove once external clients are updated.
namespace webrtc_jni {

using webrtc::jni::AndroidNetworkMonitor;
using webrtc::jni::AndroidNetworkMonitorFactory;

}  // namespace webrtc_jni

#endif  // SDK_ANDROID_SRC_JNI_ANDROID_NETWORK_MONITOR_H_
