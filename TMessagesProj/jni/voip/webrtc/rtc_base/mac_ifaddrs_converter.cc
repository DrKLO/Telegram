/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <net/if.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/ifaddrs_converter.h"
#include "rtc_base/logging.h"

#if !defined(WEBRTC_IOS)
#include <net/if_media.h>
#include <netinet/in_var.h>
#else  // WEBRTC_IOS
#define SCOPE6_ID_MAX 16

struct in6_addrlifetime {
  time_t ia6t_expire;    /* valid lifetime expiration time */
  time_t ia6t_preferred; /* preferred lifetime expiration time */
  u_int32_t ia6t_vltime; /* valid lifetime */
  u_int32_t ia6t_pltime; /* prefix lifetime */
};

struct in6_ifstat {
  u_quad_t ifs6_in_receive;      /* # of total input datagram */
  u_quad_t ifs6_in_hdrerr;       /* # of datagrams with invalid hdr */
  u_quad_t ifs6_in_toobig;       /* # of datagrams exceeded MTU */
  u_quad_t ifs6_in_noroute;      /* # of datagrams with no route */
  u_quad_t ifs6_in_addrerr;      /* # of datagrams with invalid dst */
  u_quad_t ifs6_in_protounknown; /* # of datagrams with unknown proto */
                                 /* NOTE: increment on final dst if */
  u_quad_t ifs6_in_truncated;    /* # of truncated datagrams */
  u_quad_t ifs6_in_discard;      /* # of discarded datagrams */
                                 /* NOTE: fragment timeout is not here */
  u_quad_t ifs6_in_deliver;      /* # of datagrams delivered to ULP */
                                 /* NOTE: increment on final dst if */
  u_quad_t ifs6_out_forward;     /* # of datagrams forwarded */
                                 /* NOTE: increment on outgoing if */
  u_quad_t ifs6_out_request;     /* # of outgoing datagrams from ULP */
                                 /* NOTE: does not include forwrads */
  u_quad_t ifs6_out_discard;     /* # of discarded datagrams */
  u_quad_t ifs6_out_fragok;      /* # of datagrams fragmented */
  u_quad_t ifs6_out_fragfail;    /* # of datagrams failed on fragment */
  u_quad_t ifs6_out_fragcreat;   /* # of fragment datagrams */
                                 /* NOTE: this is # after fragment */
  u_quad_t ifs6_reass_reqd;      /* # of incoming fragmented packets */
                                 /* NOTE: increment on final dst if */
  u_quad_t ifs6_reass_ok;        /* # of reassembled packets */
                                 /* NOTE: this is # after reass */
                                 /* NOTE: increment on final dst if */
  u_quad_t ifs6_reass_fail;      /* # of reass failures */
                                 /* NOTE: may not be packet count */
                                 /* NOTE: increment on final dst if */
  u_quad_t ifs6_in_mcast;        /* # of inbound multicast datagrams */
  u_quad_t ifs6_out_mcast;       /* # of outbound multicast datagrams */
};
struct icmp6_ifstat {
  /*
   * Input statistics
   */
  /* ipv6IfIcmpInMsgs, total # of input messages */
  u_quad_t ifs6_in_msg;
  /* ipv6IfIcmpInErrors, # of input error messages */
  u_quad_t ifs6_in_error;
  /* ipv6IfIcmpInDestUnreachs, # of input dest unreach errors */
  u_quad_t ifs6_in_dstunreach;
  /* ipv6IfIcmpInAdminProhibs, # of input admin. prohibited errs */
  u_quad_t ifs6_in_adminprohib;
  /* ipv6IfIcmpInTimeExcds, # of input time exceeded errors */
  u_quad_t ifs6_in_timeexceed;
  /* ipv6IfIcmpInParmProblems, # of input parameter problem errors */
  u_quad_t ifs6_in_paramprob;
  /* ipv6IfIcmpInPktTooBigs, # of input packet too big errors */
  u_quad_t ifs6_in_pkttoobig;
  /* ipv6IfIcmpInEchos, # of input echo requests */
  u_quad_t ifs6_in_echo;
  /* ipv6IfIcmpInEchoReplies, # of input echo replies */
  u_quad_t ifs6_in_echoreply;
  /* ipv6IfIcmpInRouterSolicits, # of input router solicitations */
  u_quad_t ifs6_in_routersolicit;
  /* ipv6IfIcmpInRouterAdvertisements, # of input router advertisements */
  u_quad_t ifs6_in_routeradvert;
  /* ipv6IfIcmpInNeighborSolicits, # of input neighbor solicitations */
  u_quad_t ifs6_in_neighborsolicit;
  /* ipv6IfIcmpInNeighborAdvertisements, # of input neighbor advs. */
  u_quad_t ifs6_in_neighboradvert;
  /* ipv6IfIcmpInRedirects, # of input redirects */
  u_quad_t ifs6_in_redirect;
  /* ipv6IfIcmpInGroupMembQueries, # of input MLD queries */
  u_quad_t ifs6_in_mldquery;
  /* ipv6IfIcmpInGroupMembResponses, # of input MLD reports */
  u_quad_t ifs6_in_mldreport;
  /* ipv6IfIcmpInGroupMembReductions, # of input MLD done */
  u_quad_t ifs6_in_mlddone;

  /*
   * Output statistics. We should solve unresolved routing problem...
   */
  /* ipv6IfIcmpOutMsgs, total # of output messages */
  u_quad_t ifs6_out_msg;
  /* ipv6IfIcmpOutErrors, # of output error messages */
  u_quad_t ifs6_out_error;
  /* ipv6IfIcmpOutDestUnreachs, # of output dest unreach errors */
  u_quad_t ifs6_out_dstunreach;
  /* ipv6IfIcmpOutAdminProhibs, # of output admin. prohibited errs */
  u_quad_t ifs6_out_adminprohib;
  /* ipv6IfIcmpOutTimeExcds, # of output time exceeded errors */
  u_quad_t ifs6_out_timeexceed;
  /* ipv6IfIcmpOutParmProblems, # of output parameter problem errors */
  u_quad_t ifs6_out_paramprob;
  /* ipv6IfIcmpOutPktTooBigs, # of output packet too big errors */
  u_quad_t ifs6_out_pkttoobig;
  /* ipv6IfIcmpOutEchos, # of output echo requests */
  u_quad_t ifs6_out_echo;
  /* ipv6IfIcmpOutEchoReplies, # of output echo replies */
  u_quad_t ifs6_out_echoreply;
  /* ipv6IfIcmpOutRouterSolicits, # of output router solicitations */
  u_quad_t ifs6_out_routersolicit;
  /* ipv6IfIcmpOutRouterAdvertisements, # of output router advs. */
  u_quad_t ifs6_out_routeradvert;
  /* ipv6IfIcmpOutNeighborSolicits, # of output neighbor solicitations */
  u_quad_t ifs6_out_neighborsolicit;
  /* ipv6IfIcmpOutNeighborAdvertisements, # of output neighbor advs. */
  u_quad_t ifs6_out_neighboradvert;
  /* ipv6IfIcmpOutRedirects, # of output redirects */
  u_quad_t ifs6_out_redirect;
  /* ipv6IfIcmpOutGroupMembQueries, # of output MLD queries */
  u_quad_t ifs6_out_mldquery;
  /* ipv6IfIcmpOutGroupMembResponses, # of output MLD reports */
  u_quad_t ifs6_out_mldreport;
  /* ipv6IfIcmpOutGroupMembReductions, # of output MLD done */
  u_quad_t ifs6_out_mlddone;
};

struct in6_ifreq {
  char ifr_name[IFNAMSIZ];
  union {
    struct sockaddr_in6 ifru_addr;
    struct sockaddr_in6 ifru_dstaddr;
    int ifru_flags;
    int ifru_flags6;
    int ifru_metric;
    int ifru_intval;
    caddr_t ifru_data;
    struct in6_addrlifetime ifru_lifetime;
    struct in6_ifstat ifru_stat;
    struct icmp6_ifstat ifru_icmp6stat;
    u_int32_t ifru_scope_id[SCOPE6_ID_MAX];
  } ifr_ifru;
};

#define SIOCGIFAFLAG_IN6 _IOWR('i', 73, struct in6_ifreq)

#define IN6_IFF_ANYCAST 0x0001    /* anycast address */
#define IN6_IFF_TENTATIVE 0x0002  /* tentative address */
#define IN6_IFF_DUPLICATED 0x0004 /* DAD detected duplicate */
#define IN6_IFF_DETACHED 0x0008   /* may be detached from the link */
#define IN6_IFF_DEPRECATED 0x0010 /* deprecated address */
#define IN6_IFF_TEMPORARY 0x0080  /* temporary (anonymous) address. */

#endif  // WEBRTC_IOS

namespace rtc {

namespace {

class IPv6AttributesGetter {
 public:
  IPv6AttributesGetter();
  virtual ~IPv6AttributesGetter();
  bool IsInitialized() const;
  bool GetIPAttributes(const char* ifname,
                       const sockaddr* sock_addr,
                       int* native_attributes);

 private:
  // on MAC or IOS, we have to use ioctl with a socket to query an IPv6
  // interface's attribute.
  int ioctl_socket_;
};

IPv6AttributesGetter::IPv6AttributesGetter()
    : ioctl_socket_(
          socket(AF_INET6, SOCK_DGRAM, 0 /* unspecified protocol */)) {
  RTC_DCHECK_GE(ioctl_socket_, 0);
}

bool IPv6AttributesGetter::IsInitialized() const {
  return ioctl_socket_ >= 0;
}

IPv6AttributesGetter::~IPv6AttributesGetter() {
  if (!IsInitialized()) {
    return;
  }
  close(ioctl_socket_);
}

bool IPv6AttributesGetter::GetIPAttributes(const char* ifname,
                                           const sockaddr* sock_addr,
                                           int* native_attributes) {
  if (!IsInitialized()) {
    return false;
  }

  struct in6_ifreq ifr = {};
  strncpy(ifr.ifr_name, ifname, sizeof(ifr.ifr_name) - 1);
  memcpy(&ifr.ifr_ifru.ifru_addr, sock_addr, sock_addr->sa_len);
  int rv = ioctl(ioctl_socket_, SIOCGIFAFLAG_IN6, &ifr);
  if (rv >= 0) {
    *native_attributes = ifr.ifr_ifru.ifru_flags;
  } else {
    RTC_LOG(LS_ERROR) << "ioctl returns " << errno;
  }
  return (rv >= 0);
}

// Converts native IPv6 address attributes to net IPv6 address attributes.  If
// it returns false, the IP address isn't suitable for one-to-one communications
// applications and should be ignored.
bool ConvertNativeToIPAttributes(int native_attributes, int* net_attributes) {
  // For MacOSX, we disallow addresses with attributes IN6_IFF_ANYCASE,
  // IN6_IFF_DUPLICATED, IN6_IFF_TENTATIVE, and IN6_IFF_DETACHED as these are
  // still progressing through duplicated address detection (DAD) or are not
  // suitable for one-to-one communication applications.
  if (native_attributes & (IN6_IFF_ANYCAST | IN6_IFF_DUPLICATED |
                           IN6_IFF_TENTATIVE | IN6_IFF_DETACHED)) {
    return false;
  }

  if (native_attributes & IN6_IFF_TEMPORARY) {
    *net_attributes |= IPV6_ADDRESS_FLAG_TEMPORARY;
  }

  if (native_attributes & IN6_IFF_DEPRECATED) {
    *net_attributes |= IPV6_ADDRESS_FLAG_DEPRECATED;
  }

  return true;
}

class MacIfAddrsConverter : public IfAddrsConverter {
 public:
  MacIfAddrsConverter() : ip_attribute_getter_(new IPv6AttributesGetter()) {}
  ~MacIfAddrsConverter() override {}

  bool ConvertNativeAttributesToIPAttributes(const struct ifaddrs* interface,
                                             int* ip_attributes) override {
    int native_attributes;
    if (!ip_attribute_getter_->GetIPAttributes(
            interface->ifa_name, interface->ifa_addr, &native_attributes)) {
      return false;
    }

    if (!ConvertNativeToIPAttributes(native_attributes, ip_attributes)) {
      return false;
    }

    return true;
  }

 private:
  std::unique_ptr<IPv6AttributesGetter> ip_attribute_getter_;
};

}  // namespace

IfAddrsConverter* CreateIfAddrsConverter() {
  return new MacIfAddrsConverter();
}

}  // namespace rtc
