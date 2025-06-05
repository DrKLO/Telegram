/*-
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2007, by Cisco Systems, Inc. All rights reserved.
 * Copyright (c) 2008-2012, by Randall Stewart. All rights reserved.
 * Copyright (c) 2008-2012, by Michael Tuexen. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * a) Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * b) Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *
 * c) Neither the name of Cisco Systems, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

#if defined(__FreeBSD__) && !defined(__Userspace__)
#include <sys/cdefs.h>
__FBSDID("$FreeBSD: head/sys/netinet/sctp_sysctl.c 365071 2020-09-01 21:19:14Z mjg $");
#endif

#include <netinet/sctp_os.h>
#include <netinet/sctp.h>
#include <netinet/sctp_constants.h>
#include <netinet/sctp_sysctl.h>
#include <netinet/sctp_pcb.h>
#include <netinet/sctputil.h>
#include <netinet/sctp_output.h>
#if defined(__FreeBSD__) && !defined(__Userspace__)
#include <sys/smp.h>
#include <sys/sysctl.h>
#endif
#if defined(__APPLE__) && !defined(__Userspace__)
#include <netinet/sctp_bsd_addr.h>
#endif

#if defined(__FreeBSD__) && !defined(__Userspace__)
FEATURE(sctp, "Stream Control Transmission Protocol");
#endif

/*
 * sysctl tunable variables
 */

void
sctp_init_sysctls()
{
	SCTP_BASE_SYSCTL(sctp_sendspace) = SCTPCTL_MAXDGRAM_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_recvspace) = SCTPCTL_RECVSPACE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_auto_asconf) = SCTPCTL_AUTOASCONF_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_multiple_asconfs) = SCTPCTL_MULTIPLEASCONFS_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_ecn_enable) = SCTPCTL_ECN_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_pr_enable) = SCTPCTL_PR_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_auth_enable) = SCTPCTL_AUTH_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_asconf_enable) = SCTPCTL_ASCONF_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_reconfig_enable) = SCTPCTL_RECONFIG_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_nrsack_enable) = SCTPCTL_NRSACK_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_pktdrop_enable) = SCTPCTL_PKTDROP_ENABLE_DEFAULT;
#if !(defined(__FreeBSD__) && !defined(__Userspace__))
	SCTP_BASE_SYSCTL(sctp_no_csum_on_loopback) = SCTPCTL_LOOPBACK_NOCSUM_DEFAULT;
#endif
	SCTP_BASE_SYSCTL(sctp_peer_chunk_oh) = SCTPCTL_PEER_CHKOH_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_max_burst_default) = SCTPCTL_MAXBURST_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_fr_max_burst_default) = SCTPCTL_FRMAXBURST_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_max_chunks_on_queue) = SCTPCTL_MAXCHUNKS_DEFAULT;
#if defined(__Userspace__)
	if (SCTP_BASE_SYSCTL(sctp_hashtblsize) == 0) {
		SCTP_BASE_SYSCTL(sctp_hashtblsize) = SCTPCTL_TCBHASHSIZE_DEFAULT;
	}
#else
	SCTP_BASE_SYSCTL(sctp_hashtblsize) = SCTPCTL_TCBHASHSIZE_DEFAULT;
#endif
#if defined(__Userspace__)
	if (SCTP_BASE_SYSCTL(sctp_pcbtblsize) == 0) {
		SCTP_BASE_SYSCTL(sctp_pcbtblsize) = SCTPCTL_PCBHASHSIZE_DEFAULT;
	}
#else
	SCTP_BASE_SYSCTL(sctp_pcbtblsize) = SCTPCTL_PCBHASHSIZE_DEFAULT;
#endif
	SCTP_BASE_SYSCTL(sctp_min_split_point) = SCTPCTL_MIN_SPLIT_POINT_DEFAULT;
#if defined(__Userspace__)
	if (SCTP_BASE_SYSCTL(sctp_chunkscale) == 0) {
		SCTP_BASE_SYSCTL(sctp_chunkscale) = SCTPCTL_CHUNKSCALE_DEFAULT;
	}
#else
	SCTP_BASE_SYSCTL(sctp_chunkscale) = SCTPCTL_CHUNKSCALE_DEFAULT;
#endif
	SCTP_BASE_SYSCTL(sctp_delayed_sack_time_default) = SCTPCTL_DELAYED_SACK_TIME_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_sack_freq_default) = SCTPCTL_SACK_FREQ_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_system_free_resc_limit) = SCTPCTL_SYS_RESOURCE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_asoc_free_resc_limit) = SCTPCTL_ASOC_RESOURCE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_heartbeat_interval_default) = SCTPCTL_HEARTBEAT_INTERVAL_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_pmtu_raise_time_default) = SCTPCTL_PMTU_RAISE_TIME_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_shutdown_guard_time_default) = SCTPCTL_SHUTDOWN_GUARD_TIME_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_secret_lifetime_default) = SCTPCTL_SECRET_LIFETIME_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rto_max_default) = SCTPCTL_RTO_MAX_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rto_min_default) = SCTPCTL_RTO_MIN_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rto_initial_default) = SCTPCTL_RTO_INITIAL_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_init_rto_max_default) = SCTPCTL_INIT_RTO_MAX_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_valid_cookie_life_default) = SCTPCTL_VALID_COOKIE_LIFE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_init_rtx_max_default) = SCTPCTL_INIT_RTX_MAX_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_assoc_rtx_max_default) = SCTPCTL_ASSOC_RTX_MAX_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_path_rtx_max_default) = SCTPCTL_PATH_RTX_MAX_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_path_pf_threshold) = SCTPCTL_PATH_PF_THRESHOLD_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_add_more_threshold) = SCTPCTL_ADD_MORE_ON_OUTPUT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_nr_incoming_streams_default) = SCTPCTL_INCOMING_STREAMS_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_nr_outgoing_streams_default) = SCTPCTL_OUTGOING_STREAMS_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_cmt_on_off) = SCTPCTL_CMT_ON_OFF_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_cmt_use_dac) = SCTPCTL_CMT_USE_DAC_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_use_cwnd_based_maxburst) = SCTPCTL_CWND_MAXBURST_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_nat_friendly) = SCTPCTL_NAT_FRIENDLY_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_L2_abc_variable) = SCTPCTL_ABC_L_VAR_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_mbuf_threshold_count) = SCTPCTL_MAX_CHAINED_MBUFS_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_do_drain) = SCTPCTL_DO_SCTP_DRAIN_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_hb_maxburst) = SCTPCTL_HB_MAX_BURST_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_abort_if_one_2_one_hits_limit) = SCTPCTL_ABORT_AT_LIMIT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_min_residual) = SCTPCTL_MIN_RESIDUAL_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_max_retran_chunk) = SCTPCTL_MAX_RETRAN_CHUNK_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_logging_level) = SCTPCTL_LOGGING_LEVEL_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_default_cc_module) = SCTPCTL_DEFAULT_CC_MODULE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_default_ss_module) = SCTPCTL_DEFAULT_SS_MODULE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_default_frag_interleave) = SCTPCTL_DEFAULT_FRAG_INTERLEAVE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_mobility_base) = SCTPCTL_MOBILITY_BASE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_mobility_fasthandoff) = SCTPCTL_MOBILITY_FASTHANDOFF_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_vtag_time_wait) = SCTPCTL_TIME_WAIT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_buffer_splitting) = SCTPCTL_BUFFER_SPLITTING_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_initial_cwnd) = SCTPCTL_INITIAL_CWND_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rttvar_bw) = SCTPCTL_RTTVAR_BW_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rttvar_rtt) = SCTPCTL_RTTVAR_RTT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_rttvar_eqret) = SCTPCTL_RTTVAR_EQRET_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_steady_step) = SCTPCTL_RTTVAR_STEADYS_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_use_dccc_ecn) = SCTPCTL_RTTVAR_DCCCECN_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_blackhole) = SCTPCTL_BLACKHOLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_sendall_limit) = SCTPCTL_SENDALL_LIMIT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_diag_info_code) = SCTPCTL_DIAG_INFO_CODE_DEFAULT;
#if defined(SCTP_LOCAL_TRACE_BUF)
#if defined(_WIN32) && !defined(__Userspace__)
	/* On Windows, the resource for global variables is limited. */
	MALLOC(SCTP_BASE_SYSCTL(sctp_log), struct sctp_log *, sizeof(struct sctp_log), M_SYSCTL, M_ZERO);
#else
	memset(&SCTP_BASE_SYSCTL(sctp_log), 0, sizeof(struct sctp_log));
#endif
#endif
	SCTP_BASE_SYSCTL(sctp_udp_tunneling_port) = SCTPCTL_UDP_TUNNELING_PORT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_enable_sack_immediately) = SCTPCTL_SACK_IMMEDIATELY_ENABLE_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_inits_include_nat_friendly) = SCTPCTL_NAT_FRIENDLY_INITS_DEFAULT;
#if defined(SCTP_DEBUG)
	SCTP_BASE_SYSCTL(sctp_debug_on) = SCTPCTL_DEBUG_DEFAULT;
#endif
#if defined(__APPLE__) && !defined(__Userspace__)
	SCTP_BASE_SYSCTL(sctp_ignore_vmware_interfaces) = SCTPCTL_IGNORE_VMWARE_INTERFACES_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_main_timer) = SCTPCTL_MAIN_TIMER_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_addr_watchdog_limit) = SCTPCTL_ADDR_WATCHDOG_LIMIT_DEFAULT;
	SCTP_BASE_SYSCTL(sctp_vtag_watchdog_limit) = SCTPCTL_VTAG_WATCHDOG_LIMIT_DEFAULT;
#endif
#if defined(__APPLE__) && !defined(__Userspace__)
	SCTP_BASE_SYSCTL(sctp_output_unlocked) = SCTPCTL_OUTPUT_UNLOCKED_DEFAULT;
#endif
}
#if defined(_WIN32) && !defined(__Userspace__)

void
sctp_finish_sysctls()
{
#if defined(SCTP_LOCAL_TRACE_BUF)
	if (SCTP_BASE_SYSCTL(sctp_log) != NULL) {
		FREE(SCTP_BASE_SYSCTL(sctp_log), M_SYSCTL);
		SCTP_BASE_SYSCTL(sctp_log) = NULL;
	}
#endif
}
#endif

#if !defined(__Userspace__)
/* It returns an upper limit. No filtering is done here */
static unsigned int
sctp_sysctl_number_of_addresses(struct sctp_inpcb *inp)
{
	unsigned int cnt;
	struct sctp_vrf *vrf;
	struct sctp_ifn *sctp_ifn;
	struct sctp_ifa *sctp_ifa;
	struct sctp_laddr *laddr;

	cnt = 0;
	/* neither Mac OS X nor FreeBSD support mulitple routing functions */
	if ((vrf = sctp_find_vrf(inp->def_vrf_id)) == NULL) {
		return (0);
	}
	if (inp->sctp_flags & SCTP_PCB_FLAGS_BOUNDALL) {
		LIST_FOREACH(sctp_ifn, &vrf->ifnlist, next_ifn) {
			LIST_FOREACH(sctp_ifa, &sctp_ifn->ifalist, next_ifa) {
				switch (sctp_ifa->address.sa.sa_family) {
#ifdef INET
				case AF_INET:
#endif
#ifdef INET6
				case AF_INET6:
#endif
					cnt++;
					break;
				default:
					break;
				}
			}
		}
	} else {
		LIST_FOREACH(laddr, &inp->sctp_addr_list, sctp_nxt_addr) {
			switch (laddr->ifa->address.sa.sa_family) {
#ifdef INET
			case AF_INET:
#endif
#ifdef INET6
			case AF_INET6:
#endif
				cnt++;
				break;
			default:
				break;
			}
		}
	}
	return (cnt);
}

static int
sctp_sysctl_copy_out_local_addresses(struct sctp_inpcb *inp, struct sctp_tcb *stcb, struct sysctl_req *req)
{
	struct sctp_ifn *sctp_ifn;
	struct sctp_ifa *sctp_ifa;
	int loopback_scope, ipv4_local_scope, local_scope, site_scope;
	int ipv4_addr_legal, ipv6_addr_legal;
#if defined(__Userspace__)
	int conn_addr_legal;
#endif
	struct sctp_vrf *vrf;
	struct xsctp_laddr xladdr;
	struct sctp_laddr *laddr;
	int error;

	/* Turn on all the appropriate scope */
	if (stcb) {
		/* use association specific values */
		loopback_scope = stcb->asoc.scope.loopback_scope;
		ipv4_local_scope = stcb->asoc.scope.ipv4_local_scope;
		local_scope = stcb->asoc.scope.local_scope;
		site_scope = stcb->asoc.scope.site_scope;
		ipv4_addr_legal = stcb->asoc.scope.ipv4_addr_legal;
		ipv6_addr_legal = stcb->asoc.scope.ipv6_addr_legal;
#if defined(__Userspace__)
		conn_addr_legal = stcb->asoc.scope.conn_addr_legal;
#endif
	} else {
		/* Use generic values for endpoints. */
		loopback_scope = 1;
		ipv4_local_scope = 1;
		local_scope = 1;
		site_scope = 1;
		if (inp->sctp_flags & SCTP_PCB_FLAGS_BOUND_V6) {
			ipv6_addr_legal = 1;
			if (SCTP_IPV6_V6ONLY(inp)) {
				ipv4_addr_legal = 0;
			} else {
				ipv4_addr_legal = 1;
			}
#if defined(__Userspace__)
			conn_addr_legal = 0;
#endif
		} else {
			ipv6_addr_legal = 0;
#if defined(__Userspace__)
			if (inp->sctp_flags & SCTP_PCB_FLAGS_BOUND_CONN) {
				conn_addr_legal = 1;
				ipv4_addr_legal = 0;
			} else {
				conn_addr_legal = 0;
				ipv4_addr_legal = 1;
			}
#else
			ipv4_addr_legal = 1;
#endif
		}
	}

	/* neither Mac OS X nor FreeBSD support mulitple routing functions */
	if ((vrf = sctp_find_vrf(inp->def_vrf_id)) == NULL) {
		SCTP_INP_RUNLOCK(inp);
		SCTP_INP_INFO_RUNLOCK();
		return (-1);
	}
	if (inp->sctp_flags & SCTP_PCB_FLAGS_BOUNDALL) {
		LIST_FOREACH(sctp_ifn, &vrf->ifnlist, next_ifn) {
			if ((loopback_scope == 0) && SCTP_IFN_IS_IFT_LOOP(sctp_ifn))
				/* Skip loopback if loopback_scope not set */
				continue;
			LIST_FOREACH(sctp_ifa, &sctp_ifn->ifalist, next_ifa) {
				if (stcb) {
					/*
					 * ignore if blacklisted at
					 * association level
					 */
					if (sctp_is_addr_restricted(stcb, sctp_ifa))
						continue;
				}
				switch (sctp_ifa->address.sa.sa_family) {
#ifdef INET
				case AF_INET:
					if (ipv4_addr_legal) {
						struct sockaddr_in *sin;

						sin = &sctp_ifa->address.sin;
						if (sin->sin_addr.s_addr == 0)
							continue;
#if defined(__FreeBSD__) && !defined(__Userspace__)
						if (prison_check_ip4(inp->ip_inp.inp.inp_cred,
						                     &sin->sin_addr) != 0) {
							continue;
						}
#endif
						if ((ipv4_local_scope == 0) && (IN4_ISPRIVATE_ADDRESS(&sin->sin_addr)))
							continue;
					} else {
						continue;
					}
					break;
#endif
#ifdef INET6
				case AF_INET6:
					if (ipv6_addr_legal) {
						struct sockaddr_in6 *sin6;

						sin6 = &sctp_ifa->address.sin6;
						if (IN6_IS_ADDR_UNSPECIFIED(&sin6->sin6_addr))
							continue;
#if defined(__FreeBSD__) && !defined(__Userspace__)
						if (prison_check_ip6(inp->ip_inp.inp.inp_cred,
						                     &sin6->sin6_addr) != 0) {
							continue;
						}
#endif
						if (IN6_IS_ADDR_LINKLOCAL(&sin6->sin6_addr)) {
							if (local_scope == 0)
								continue;
						}
						if ((site_scope == 0) && (IN6_IS_ADDR_SITELOCAL(&sin6->sin6_addr)))
							continue;
					} else {
						continue;
					}
					break;
#endif
#if defined(__Userspace__)
				case AF_CONN:
					if (!conn_addr_legal) {
						continue;
					}
					break;
#endif
				default:
					continue;
				}
				memset((void *)&xladdr, 0, sizeof(struct xsctp_laddr));
				memcpy((void *)&xladdr.address, (const void *)&sctp_ifa->address, sizeof(union sctp_sockstore));
				SCTP_INP_RUNLOCK(inp);
				SCTP_INP_INFO_RUNLOCK();
				error = SYSCTL_OUT(req, &xladdr, sizeof(struct xsctp_laddr));
				if (error) {
					return (error);
				} else {
					SCTP_INP_INFO_RLOCK();
					SCTP_INP_RLOCK(inp);
				}
			}
		}
	} else {
		LIST_FOREACH(laddr, &inp->sctp_addr_list, sctp_nxt_addr) {
			/* ignore if blacklisted at association level */
			if (stcb && sctp_is_addr_restricted(stcb, laddr->ifa))
				continue;
			memset((void *)&xladdr, 0, sizeof(struct xsctp_laddr));
			memcpy((void *)&xladdr.address, (const void *)&laddr->ifa->address, sizeof(union sctp_sockstore));
			xladdr.start_time.tv_sec = (uint32_t)laddr->start_time.tv_sec;
			xladdr.start_time.tv_usec = (uint32_t)laddr->start_time.tv_usec;
			SCTP_INP_RUNLOCK(inp);
			SCTP_INP_INFO_RUNLOCK();
			error = SYSCTL_OUT(req, &xladdr, sizeof(struct xsctp_laddr));
			if (error) {
				return (error);
			} else {
				SCTP_INP_INFO_RLOCK();
				SCTP_INP_RLOCK(inp);
			}
		}
	}
	memset((void *)&xladdr, 0, sizeof(struct xsctp_laddr));
	xladdr.last = 1;
	SCTP_INP_RUNLOCK(inp);
	SCTP_INP_INFO_RUNLOCK();
	error = SYSCTL_OUT(req, &xladdr, sizeof(struct xsctp_laddr));

	if (error) {
		return (error);
	} else {
		SCTP_INP_INFO_RLOCK();
		SCTP_INP_RLOCK(inp);
		return (0);
	}
}

/*
 * sysctl functions
 */
#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_assoclist SYSCTL_HANDLER_ARGS
{
#pragma unused(oidp, arg1, arg2)
#else
static int
sctp_sysctl_handle_assoclist(SYSCTL_HANDLER_ARGS)
{
#endif
	unsigned int number_of_endpoints;
	unsigned int number_of_local_addresses;
	unsigned int number_of_associations;
	unsigned int number_of_remote_addresses;
	unsigned int n;
	int error;
	struct sctp_inpcb *inp;
	struct sctp_tcb *stcb;
	struct sctp_nets *net;
	struct xsctp_inpcb xinpcb;
	struct xsctp_tcb xstcb;
	struct xsctp_raddr xraddr;
	struct socket *so;

	number_of_endpoints = 0;
	number_of_local_addresses = 0;
	number_of_associations = 0;
	number_of_remote_addresses = 0;

	SCTP_INP_INFO_RLOCK();
#if defined(__APPLE__) && !defined(__Userspace__)
	if (req->oldptr == USER_ADDR_NULL) {
#else
	if (req->oldptr == NULL) {
#endif
		LIST_FOREACH(inp, &SCTP_BASE_INFO(listhead), sctp_list) {
			SCTP_INP_RLOCK(inp);
			number_of_endpoints++;
			number_of_local_addresses += sctp_sysctl_number_of_addresses(inp);
			LIST_FOREACH(stcb, &inp->sctp_asoc_list, sctp_tcblist) {
				number_of_associations++;
				number_of_local_addresses += sctp_sysctl_number_of_addresses(inp);
				TAILQ_FOREACH(net, &stcb->asoc.nets, sctp_next) {
					number_of_remote_addresses++;
				}
			}
			SCTP_INP_RUNLOCK(inp);
		}
		SCTP_INP_INFO_RUNLOCK();
		n = (number_of_endpoints + 1) * sizeof(struct xsctp_inpcb) +
		    (number_of_local_addresses + number_of_endpoints + number_of_associations) * sizeof(struct xsctp_laddr) +
		    (number_of_associations + number_of_endpoints) * sizeof(struct xsctp_tcb) +
		    (number_of_remote_addresses + number_of_associations) * sizeof(struct xsctp_raddr);

		/* request some more memory than needed */
#if !(defined(_WIN32) && !defined(__Userspace__))
		req->oldidx = (n + n / 8);
#else
		req->dataidx = (n + n / 8);
#endif
		return (0);
	}
#if defined(__APPLE__) && !defined(__Userspace__)
	if (req->newptr != USER_ADDR_NULL) {
#else
	if (req->newptr != NULL) {
#endif
		SCTP_INP_INFO_RUNLOCK();
		SCTP_LTRACE_ERR_RET(NULL, NULL, NULL, SCTP_FROM_SCTP_SYSCTL, EPERM);
		return (EPERM);
	}
	memset(&xinpcb, 0, sizeof(xinpcb));
	memset(&xstcb, 0, sizeof(xstcb));
	memset(&xraddr, 0, sizeof(xraddr));
	LIST_FOREACH(inp, &SCTP_BASE_INFO(listhead), sctp_list) {
		SCTP_INP_RLOCK(inp);
		if (inp->sctp_flags & SCTP_PCB_FLAGS_SOCKET_ALLGONE) {
			/* if its allgone it is being freed - skip it  */
			goto skip;
		}
		xinpcb.last = 0;
		xinpcb.local_port = ntohs(inp->sctp_lport);
		xinpcb.flags = inp->sctp_flags;
		xinpcb.features = inp->sctp_features;
		xinpcb.total_sends = inp->total_sends;
		xinpcb.total_recvs = inp->total_recvs;
		xinpcb.total_nospaces = inp->total_nospaces;
		xinpcb.fragmentation_point = inp->sctp_frag_point;
#if defined(__FreeBSD__) && !defined(__Userspace__)
		xinpcb.socket = (uintptr_t)inp->sctp_socket;
#else
		xinpcb.socket = inp->sctp_socket;
#endif
		so = inp->sctp_socket;
		if ((so == NULL) ||
		    (!SCTP_IS_LISTENING(inp)) ||
		    (inp->sctp_flags & SCTP_PCB_FLAGS_SOCKET_GONE)) {
			xinpcb.qlen = 0;
			xinpcb.maxqlen = 0;
		} else {
#if defined(__FreeBSD__) && !defined(__Userspace__)
			xinpcb.qlen = so->sol_qlen;
			xinpcb.qlen_old = so->sol_qlen > USHRT_MAX ?
			    USHRT_MAX : (uint16_t) so->sol_qlen;
			xinpcb.maxqlen = so->sol_qlimit;
			xinpcb.maxqlen_old = so->sol_qlimit > USHRT_MAX ?
			    USHRT_MAX : (uint16_t) so->sol_qlimit;
#else
			xinpcb.qlen = so->so_qlen;
			xinpcb.maxqlen = so->so_qlimit;
#endif
		}
		SCTP_INP_INCR_REF(inp);
		SCTP_INP_RUNLOCK(inp);
		SCTP_INP_INFO_RUNLOCK();
		error = SYSCTL_OUT(req, &xinpcb, sizeof(struct xsctp_inpcb));
		if (error) {
			SCTP_INP_DECR_REF(inp);
			return (error);
		}
		SCTP_INP_INFO_RLOCK();
		SCTP_INP_RLOCK(inp);
		error = sctp_sysctl_copy_out_local_addresses(inp, NULL, req);
		if (error) {
			SCTP_INP_DECR_REF(inp);
			return (error);
		}
		LIST_FOREACH(stcb, &inp->sctp_asoc_list, sctp_tcblist) {
			SCTP_TCB_LOCK(stcb);
			atomic_add_int(&stcb->asoc.refcnt, 1);
			SCTP_TCB_UNLOCK(stcb);
			xstcb.last = 0;
			xstcb.local_port = ntohs(inp->sctp_lport);
			xstcb.remote_port = ntohs(stcb->rport);
			if (stcb->asoc.primary_destination != NULL)
				xstcb.primary_addr = stcb->asoc.primary_destination->ro._l_addr;
			xstcb.heartbeat_interval = stcb->asoc.heart_beat_delay;
			xstcb.state = (uint32_t)sctp_map_assoc_state(stcb->asoc.state);
			xstcb.assoc_id = sctp_get_associd(stcb);
			xstcb.peers_rwnd = stcb->asoc.peers_rwnd;
			xstcb.in_streams = stcb->asoc.streamincnt;
			xstcb.out_streams = stcb->asoc.streamoutcnt;
			xstcb.max_nr_retrans = stcb->asoc.overall_error_count;
			xstcb.primary_process = 0; /* not really supported yet */
			xstcb.T1_expireries = stcb->asoc.timoinit + stcb->asoc.timocookie;
			xstcb.T2_expireries = stcb->asoc.timoshutdown + stcb->asoc.timoshutdownack;
			xstcb.retransmitted_tsns = stcb->asoc.marked_retrans;
			xstcb.start_time.tv_sec = (uint32_t)stcb->asoc.start_time.tv_sec;
			xstcb.start_time.tv_usec = (uint32_t)stcb->asoc.start_time.tv_usec;
			xstcb.discontinuity_time.tv_sec = (uint32_t)stcb->asoc.discontinuity_time.tv_sec;
			xstcb.discontinuity_time.tv_usec = (uint32_t)stcb->asoc.discontinuity_time.tv_usec;
			xstcb.total_sends = stcb->total_sends;
			xstcb.total_recvs = stcb->total_recvs;
			xstcb.local_tag = stcb->asoc.my_vtag;
			xstcb.remote_tag = stcb->asoc.peer_vtag;
			xstcb.initial_tsn = stcb->asoc.init_seq_number;
			xstcb.highest_tsn = stcb->asoc.sending_seq - 1;
			xstcb.cumulative_tsn = stcb->asoc.last_acked_seq;
			xstcb.cumulative_tsn_ack = stcb->asoc.cumulative_tsn;
			xstcb.mtu = stcb->asoc.smallest_mtu;
			xstcb.refcnt = stcb->asoc.refcnt;
			SCTP_INP_RUNLOCK(inp);
			SCTP_INP_INFO_RUNLOCK();
			error = SYSCTL_OUT(req, &xstcb, sizeof(struct xsctp_tcb));
			if (error) {
				SCTP_INP_DECR_REF(inp);
				atomic_subtract_int(&stcb->asoc.refcnt, 1);
				return (error);
			}
			SCTP_INP_INFO_RLOCK();
			SCTP_INP_RLOCK(inp);
			error = sctp_sysctl_copy_out_local_addresses(inp, stcb, req);
			if (error) {
				SCTP_INP_DECR_REF(inp);
				atomic_subtract_int(&stcb->asoc.refcnt, 1);
				return (error);
			}
			TAILQ_FOREACH(net, &stcb->asoc.nets, sctp_next) {
				xraddr.last = 0;
				xraddr.address = net->ro._l_addr;
				xraddr.active = ((net->dest_state & SCTP_ADDR_REACHABLE) == SCTP_ADDR_REACHABLE);
				xraddr.confirmed = ((net->dest_state & SCTP_ADDR_UNCONFIRMED) == 0);
				xraddr.heartbeat_enabled = ((net->dest_state & SCTP_ADDR_NOHB) == 0);
				xraddr.potentially_failed = ((net->dest_state & SCTP_ADDR_PF) == SCTP_ADDR_PF);
				xraddr.rto = net->RTO;
				xraddr.max_path_rtx = net->failure_threshold;
				xraddr.rtx = net->marked_retrans;
				xraddr.error_counter = net->error_count;
				xraddr.cwnd = net->cwnd;
				xraddr.flight_size = net->flight_size;
				xraddr.mtu = net->mtu;
				xraddr.rtt = net->rtt / 1000;
				xraddr.heartbeat_interval = net->heart_beat_delay;
				xraddr.ssthresh = net->ssthresh;
				xraddr.encaps_port = net->port;
				if (net->dest_state & SCTP_ADDR_UNCONFIRMED) {
					xraddr.state = SCTP_UNCONFIRMED;
				} else if (net->dest_state & SCTP_ADDR_REACHABLE) {
					xraddr.state = SCTP_ACTIVE;
				} else {
					xraddr.state = SCTP_INACTIVE;
				}
				xraddr.start_time.tv_sec = (uint32_t)net->start_time.tv_sec;
				xraddr.start_time.tv_usec = (uint32_t)net->start_time.tv_usec;
				SCTP_INP_RUNLOCK(inp);
				SCTP_INP_INFO_RUNLOCK();
				error = SYSCTL_OUT(req, &xraddr, sizeof(struct xsctp_raddr));
				if (error) {
					SCTP_INP_DECR_REF(inp);
					atomic_subtract_int(&stcb->asoc.refcnt, 1);
					return (error);
				}
				SCTP_INP_INFO_RLOCK();
				SCTP_INP_RLOCK(inp);
			}
			atomic_subtract_int(&stcb->asoc.refcnt, 1);
			memset((void *)&xraddr, 0, sizeof(struct xsctp_raddr));
			xraddr.last = 1;
			SCTP_INP_RUNLOCK(inp);
			SCTP_INP_INFO_RUNLOCK();
			error = SYSCTL_OUT(req, &xraddr, sizeof(struct xsctp_raddr));
			if (error) {
				SCTP_INP_DECR_REF(inp);
				return (error);
			}
			SCTP_INP_INFO_RLOCK();
			SCTP_INP_RLOCK(inp);
		}
		SCTP_INP_DECR_REF(inp);
		SCTP_INP_RUNLOCK(inp);
		SCTP_INP_INFO_RUNLOCK();
		memset((void *)&xstcb, 0, sizeof(struct xsctp_tcb));
		xstcb.last = 1;
		error = SYSCTL_OUT(req, &xstcb, sizeof(struct xsctp_tcb));
		if (error) {
			return (error);
		}
skip:
		SCTP_INP_INFO_RLOCK();
	}
	SCTP_INP_INFO_RUNLOCK();

	memset((void *)&xinpcb, 0, sizeof(struct xsctp_inpcb));
	xinpcb.last = 1;
	error = SYSCTL_OUT(req, &xinpcb, sizeof(struct xsctp_inpcb));
	return (error);
}

#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_udp_tunneling SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2)
#else
static int
sctp_sysctl_handle_udp_tunneling(SYSCTL_HANDLER_ARGS)
{
#endif
	int error;
	uint32_t old, new;

	SCTP_INP_INFO_RLOCK();
	old = SCTP_BASE_SYSCTL(sctp_udp_tunneling_port);
	SCTP_INP_INFO_RUNLOCK();
	new = old;
	error = sysctl_handle_int(oidp, &new, 0, req);
	if ((error == 0) &&
#if defined(__APPLE__) && !defined(__Userspace__)
	    (req->newptr != USER_ADDR_NULL)) {
#else
	    (req->newptr != NULL)) {
#endif
#if defined(_WIN32) && !defined(__Userspace__)
		SCTP_INP_INFO_WLOCK();
		sctp_over_udp_restart();
		SCTP_INP_INFO_WUNLOCK();
#else
#if (SCTPCTL_UDP_TUNNELING_PORT_MIN == 0)
		if (new > SCTPCTL_UDP_TUNNELING_PORT_MAX) {
#else
		if ((new < SCTPCTL_UDP_TUNNELING_PORT_MIN) ||
		    (new > SCTPCTL_UDP_TUNNELING_PORT_MAX)) {
#endif
			error = EINVAL;
		} else {
			SCTP_INP_INFO_WLOCK();
			SCTP_BASE_SYSCTL(sctp_udp_tunneling_port) = new;
			if (old != 0) {
				sctp_over_udp_stop();
			}
			if (new != 0) {
				error = sctp_over_udp_start();
			}
			SCTP_INP_INFO_WUNLOCK();
		}
#endif
	}
	return (error);
}
#if defined(__APPLE__) && !defined(__Userspace__)

int sctp_is_vmware_interface(struct ifnet *);

static int
sctp_sysctl_handle_vmware_interfaces SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2)
	int error;
	uint32_t old, new;

	old = SCTP_BASE_SYSCTL(sctp_ignore_vmware_interfaces);
	new = old;
	error = sysctl_handle_int(oidp, &new, 0, req);
	if ((error == 0) && (req->newptr != USER_ADDR_NULL)) {
		if ((new < SCTPCTL_IGNORE_VMWARE_INTERFACES_MIN) ||
		    (new > SCTPCTL_IGNORE_VMWARE_INTERFACES_MAX)) {
			error = EINVAL;
		} else {
			if ((old == 1) && (new == 0)) {
				sctp_add_or_del_interfaces(sctp_is_vmware_interface, 1);
			}
			if ((old == 0) && (new == 1)) {
				sctp_add_or_del_interfaces(sctp_is_vmware_interface, 0);
			}
			if (old != new) {
				SCTP_BASE_SYSCTL(sctp_ignore_vmware_interfaces) = new;
			}
		}
	}
	return (error);
}
#endif

#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_auth SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2)
#else
static int
sctp_sysctl_handle_auth(SYSCTL_HANDLER_ARGS)
{
#endif
	int error;
	uint32_t new;

	new = SCTP_BASE_SYSCTL(sctp_auth_enable);
	error = sysctl_handle_int(oidp, &new, 0, req);
	if ((error == 0) &&
#if defined(__APPLE__) && !defined(__Userspace__)
	    (req->newptr != USER_ADDR_NULL)) {
#else
	    (req->newptr != NULL)) {
#endif
#if (SCTPCTL_AUTH_ENABLE_MIN == 0)
		if ((new > SCTPCTL_AUTH_ENABLE_MAX) ||
		    ((new == 0) && (SCTP_BASE_SYSCTL(sctp_asconf_enable) == 1))) {
#else
		if ((new < SCTPCTL_AUTH_ENABLE_MIN) ||
		    (new > SCTPCTL_AUTH_ENABLE_MAX) ||
		    ((new == 0) && (SCTP_BASE_SYSCTL(sctp_asconf_enable) == 1))) {
#endif
			error = EINVAL;
		} else {
			SCTP_BASE_SYSCTL(sctp_auth_enable) = new;
		}
	}
	return (error);
}

#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_asconf SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2)
#else
static int
sctp_sysctl_handle_asconf(SYSCTL_HANDLER_ARGS)
{
#endif
	int error;
	uint32_t new;

	new = SCTP_BASE_SYSCTL(sctp_asconf_enable);
	error = sysctl_handle_int(oidp, &new, 0, req);
	if ((error == 0) &&
#if defined(__APPLE__) && !defined(__Userspace__)
	    (req->newptr != USER_ADDR_NULL)) {
#else
	    (req->newptr != NULL)) {
#endif
#if (SCTPCTL_ASCONF_ENABLE_MIN == 0)
		if ((new > SCTPCTL_ASCONF_ENABLE_MAX) ||
		    ((new == 1) && (SCTP_BASE_SYSCTL(sctp_auth_enable) == 0))) {
#else
		if ((new < SCTPCTL_ASCONF_ENABLE_MIN) ||
		    (new > SCTPCTL_ASCONF_ENABLE_MAX) ||
		    ((new == 1) && (SCTP_BASE_SYSCTL(sctp_auth_enable) == 0))) {
#endif
			error = EINVAL;
		} else {
			SCTP_BASE_SYSCTL(sctp_asconf_enable) = new;
		}
	}
	return (error);
}

#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_stats SYSCTL_HANDLER_ARGS
{
#pragma unused(oidp, arg1, arg2)
#else
static int
sctp_sysctl_handle_stats(SYSCTL_HANDLER_ARGS)
{
#endif
	int error;
#if defined(__FreeBSD__) && !defined(__Userspace__)
#if defined(SMP) && defined(SCTP_USE_PERCPU_STAT)
	struct sctpstat *sarry;
	struct sctpstat sb;
	int cpu;
#endif
	struct sctpstat sb_temp;
#endif

#if defined(__APPLE__) && !defined(__Userspace__)
	if ((req->newptr != USER_ADDR_NULL) &&
#else
	if ((req->newptr != NULL) &&
#endif
	    (req->newlen != sizeof(struct sctpstat))) {
		return (EINVAL);
	}
#if defined(__FreeBSD__) && !defined(__Userspace__)
	memset(&sb_temp, 0, sizeof(struct sctpstat));

	if (req->newptr != NULL) {
		error = SYSCTL_IN(req, &sb_temp, sizeof(struct sctpstat));
		if (error != 0) {
			return (error);
		}
	}
#if defined(SMP) && defined(SCTP_USE_PERCPU_STAT)
	memset(&sb, 0, sizeof(sb));
	for (cpu = 0; cpu < mp_maxid; cpu++) {
		sarry = &SCTP_BASE_STATS[cpu];
		if (sarry->sctps_discontinuitytime.tv_sec > sb.sctps_discontinuitytime.tv_sec) {
			sb.sctps_discontinuitytime.tv_sec = sarry->sctps_discontinuitytime.tv_sec;
			sb.sctps_discontinuitytime.tv_usec = sarry->sctps_discontinuitytime.tv_usec;
		}
		sb.sctps_currestab += sarry->sctps_currestab;
		sb.sctps_activeestab += sarry->sctps_activeestab;
		sb.sctps_restartestab += sarry->sctps_restartestab;
		sb.sctps_collisionestab += sarry->sctps_collisionestab;
		sb.sctps_passiveestab += sarry->sctps_passiveestab;
		sb.sctps_aborted += sarry->sctps_aborted;
		sb.sctps_shutdown += sarry->sctps_shutdown;
		sb.sctps_outoftheblue += sarry->sctps_outoftheblue;
		sb.sctps_checksumerrors += sarry->sctps_checksumerrors;
		sb.sctps_outcontrolchunks += sarry->sctps_outcontrolchunks;
		sb.sctps_outorderchunks += sarry->sctps_outorderchunks;
		sb.sctps_outunorderchunks += sarry->sctps_outunorderchunks;
		sb.sctps_incontrolchunks += sarry->sctps_incontrolchunks;
		sb.sctps_inorderchunks += sarry->sctps_inorderchunks;
		sb.sctps_inunorderchunks += sarry->sctps_inunorderchunks;
		sb.sctps_fragusrmsgs += sarry->sctps_fragusrmsgs;
		sb.sctps_reasmusrmsgs += sarry->sctps_reasmusrmsgs;
		sb.sctps_outpackets += sarry->sctps_outpackets;
		sb.sctps_inpackets += sarry->sctps_inpackets;
		sb.sctps_recvpackets += sarry->sctps_recvpackets;
		sb.sctps_recvdatagrams += sarry->sctps_recvdatagrams;
		sb.sctps_recvpktwithdata += sarry->sctps_recvpktwithdata;
		sb.sctps_recvsacks += sarry->sctps_recvsacks;
		sb.sctps_recvdata += sarry->sctps_recvdata;
		sb.sctps_recvdupdata += sarry->sctps_recvdupdata;
		sb.sctps_recvheartbeat += sarry->sctps_recvheartbeat;
		sb.sctps_recvheartbeatack += sarry->sctps_recvheartbeatack;
		sb.sctps_recvecne += sarry->sctps_recvecne;
		sb.sctps_recvauth += sarry->sctps_recvauth;
		sb.sctps_recvauthmissing += sarry->sctps_recvauthmissing;
		sb.sctps_recvivalhmacid += sarry->sctps_recvivalhmacid;
		sb.sctps_recvivalkeyid += sarry->sctps_recvivalkeyid;
		sb.sctps_recvauthfailed += sarry->sctps_recvauthfailed;
		sb.sctps_recvexpress += sarry->sctps_recvexpress;
		sb.sctps_recvexpressm += sarry->sctps_recvexpressm;
		sb.sctps_recvswcrc += sarry->sctps_recvswcrc;
		sb.sctps_recvhwcrc += sarry->sctps_recvhwcrc;
		sb.sctps_sendpackets += sarry->sctps_sendpackets;
		sb.sctps_sendsacks += sarry->sctps_sendsacks;
		sb.sctps_senddata += sarry->sctps_senddata;
		sb.sctps_sendretransdata += sarry->sctps_sendretransdata;
		sb.sctps_sendfastretrans += sarry->sctps_sendfastretrans;
		sb.sctps_sendmultfastretrans += sarry->sctps_sendmultfastretrans;
		sb.sctps_sendheartbeat += sarry->sctps_sendheartbeat;
		sb.sctps_sendecne += sarry->sctps_sendecne;
		sb.sctps_sendauth += sarry->sctps_sendauth;
		sb.sctps_senderrors += sarry->sctps_senderrors;
		sb.sctps_sendswcrc += sarry->sctps_sendswcrc;
		sb.sctps_sendhwcrc += sarry->sctps_sendhwcrc;
		sb.sctps_pdrpfmbox += sarry->sctps_pdrpfmbox;
		sb.sctps_pdrpfehos += sarry->sctps_pdrpfehos;
		sb.sctps_pdrpmbda += sarry->sctps_pdrpmbda;
		sb.sctps_pdrpmbct += sarry->sctps_pdrpmbct;
		sb.sctps_pdrpbwrpt += sarry->sctps_pdrpbwrpt;
		sb.sctps_pdrpcrupt += sarry->sctps_pdrpcrupt;
		sb.sctps_pdrpnedat += sarry->sctps_pdrpnedat;
		sb.sctps_pdrppdbrk += sarry->sctps_pdrppdbrk;
		sb.sctps_pdrptsnnf += sarry->sctps_pdrptsnnf;
		sb.sctps_pdrpdnfnd += sarry->sctps_pdrpdnfnd;
		sb.sctps_pdrpdiwnp += sarry->sctps_pdrpdiwnp;
		sb.sctps_pdrpdizrw += sarry->sctps_pdrpdizrw;
		sb.sctps_pdrpbadd += sarry->sctps_pdrpbadd;
		sb.sctps_pdrpmark += sarry->sctps_pdrpmark;
		sb.sctps_timoiterator += sarry->sctps_timoiterator;
		sb.sctps_timodata += sarry->sctps_timodata;
		sb.sctps_timowindowprobe += sarry->sctps_timowindowprobe;
		sb.sctps_timoinit += sarry->sctps_timoinit;
		sb.sctps_timosack += sarry->sctps_timosack;
		sb.sctps_timoshutdown += sarry->sctps_timoshutdown;
		sb.sctps_timoheartbeat += sarry->sctps_timoheartbeat;
		sb.sctps_timocookie += sarry->sctps_timocookie;
		sb.sctps_timosecret += sarry->sctps_timosecret;
		sb.sctps_timopathmtu += sarry->sctps_timopathmtu;
		sb.sctps_timoshutdownack += sarry->sctps_timoshutdownack;
		sb.sctps_timoshutdownguard += sarry->sctps_timoshutdownguard;
		sb.sctps_timostrmrst += sarry->sctps_timostrmrst;
		sb.sctps_timoearlyfr += sarry->sctps_timoearlyfr;
		sb.sctps_timoasconf += sarry->sctps_timoasconf;
		sb.sctps_timodelprim += sarry->sctps_timodelprim;
		sb.sctps_timoautoclose += sarry->sctps_timoautoclose;
		sb.sctps_timoassockill += sarry->sctps_timoassockill;
		sb.sctps_timoinpkill += sarry->sctps_timoinpkill;
		sb.sctps_hdrops += sarry->sctps_hdrops;
		sb.sctps_badsum += sarry->sctps_badsum;
		sb.sctps_noport += sarry->sctps_noport;
		sb.sctps_badvtag += sarry->sctps_badvtag;
		sb.sctps_badsid += sarry->sctps_badsid;
		sb.sctps_nomem += sarry->sctps_nomem;
		sb.sctps_fastretransinrtt += sarry->sctps_fastretransinrtt;
		sb.sctps_markedretrans += sarry->sctps_markedretrans;
		sb.sctps_naglesent += sarry->sctps_naglesent;
		sb.sctps_naglequeued += sarry->sctps_naglequeued;
		sb.sctps_maxburstqueued += sarry->sctps_maxburstqueued;
		sb.sctps_ifnomemqueued += sarry->sctps_ifnomemqueued;
		sb.sctps_windowprobed += sarry->sctps_windowprobed;
		sb.sctps_lowlevelerr += sarry->sctps_lowlevelerr;
		sb.sctps_lowlevelerrusr += sarry->sctps_lowlevelerrusr;
		sb.sctps_datadropchklmt += sarry->sctps_datadropchklmt;
		sb.sctps_datadroprwnd += sarry->sctps_datadroprwnd;
		sb.sctps_ecnereducedcwnd += sarry->sctps_ecnereducedcwnd;
		sb.sctps_vtagexpress += sarry->sctps_vtagexpress;
		sb.sctps_vtagbogus += sarry->sctps_vtagbogus;
		sb.sctps_primary_randry += sarry->sctps_primary_randry;
		sb.sctps_cmt_randry += sarry->sctps_cmt_randry;
		sb.sctps_slowpath_sack += sarry->sctps_slowpath_sack;
		sb.sctps_wu_sacks_sent += sarry->sctps_wu_sacks_sent;
		sb.sctps_sends_with_flags += sarry->sctps_sends_with_flags;
		sb.sctps_sends_with_unord += sarry->sctps_sends_with_unord;
		sb.sctps_sends_with_eof += sarry->sctps_sends_with_eof;
		sb.sctps_sends_with_abort += sarry->sctps_sends_with_abort;
		sb.sctps_protocol_drain_calls += sarry->sctps_protocol_drain_calls;
		sb.sctps_protocol_drains_done += sarry->sctps_protocol_drains_done;
		sb.sctps_read_peeks += sarry->sctps_read_peeks;
		sb.sctps_cached_chk += sarry->sctps_cached_chk;
		sb.sctps_cached_strmoq += sarry->sctps_cached_strmoq;
		sb.sctps_left_abandon += sarry->sctps_left_abandon;
		sb.sctps_send_burst_avoid += sarry->sctps_send_burst_avoid;
		sb.sctps_send_cwnd_avoid += sarry->sctps_send_cwnd_avoid;
		sb.sctps_fwdtsn_map_over += sarry->sctps_fwdtsn_map_over;
		if (req->newptr != NULL) {
			memcpy(sarry, &sb_temp, sizeof(struct sctpstat));
		}
	}
	error = SYSCTL_OUT(req, &sb, sizeof(struct sctpstat));
#else
	error = SYSCTL_OUT(req, &SCTP_BASE_STATS, sizeof(struct sctpstat));
	if (error != 0) {
		return (error);
	}
	if (req->newptr != NULL) {
		memcpy(&SCTP_BASE_STATS, &sb_temp, sizeof(struct sctpstat));
	}
#endif
#else
	error = SYSCTL_OUT(req, &SCTP_BASE_STATS, sizeof(struct sctpstat));
#endif
	return (error);
}

#if defined(SCTP_LOCAL_TRACE_BUF)
#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_trace_log SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2, oidp)
#else
static int
sctp_sysctl_handle_trace_log(SYSCTL_HANDLER_ARGS)
{
#endif
	int error;

#if defined(_WIN32) && !defined(__Userspace__)
	error = SYSCTL_OUT(req, SCTP_BASE_SYSCTL(sctp_log), sizeof(struct sctp_log));
#else
	error = SYSCTL_OUT(req, &SCTP_BASE_SYSCTL(sctp_log), sizeof(struct sctp_log));
#endif
	return (error);
}

#if defined(__APPLE__) && !defined(__Userspace__)
static int
sctp_sysctl_handle_trace_log_clear SYSCTL_HANDLER_ARGS
{
#pragma unused(arg1, arg2, req, oidp)
#else
static int
sctp_sysctl_handle_trace_log_clear(SYSCTL_HANDLER_ARGS)
{
#endif
	int error = 0;
#if defined(_WIN32) && !defined(__Userspace__)
	int value = 0;

	if (req->new_data == NULL) {
		return (error);
	}
	error = SYSCTL_IN(req, &value, sizeof(int));
	if (error == 0 && value != 0 && SCTP_BASE_SYSCTL(sctp_log) != NULL) {
		memset(SCTP_BASE_SYSCTL(sctp_log), 0, sizeof(struct sctp_log));
	}
#else

	memset(&SCTP_BASE_SYSCTL(sctp_log), 0, sizeof(struct sctp_log));
#endif
	return (error);
}
#endif

#if (defined(__APPLE__) || defined(__FreeBSD__)) && !defined(__Userspace__)
#if defined(__FreeBSD__)
#define SCTP_UINT_SYSCTL(mib_name, var_name, prefix)			\
	static int							\
	sctp_sysctl_handle_##mib_name(SYSCTL_HANDLER_ARGS)		\
	{								\
		int error;						\
		uint32_t new;						\
									\
		new = SCTP_BASE_SYSCTL(var_name);			\
		error = sysctl_handle_int(oidp, &new, 0, req);		\
		if ((error == 0) && (req->newptr != NULL)) {		\
			if ((new < prefix##_MIN) ||			\
			    (new > prefix##_MAX)) {			\
				error = EINVAL;				\
			} else {					\
				SCTP_BASE_SYSCTL(var_name) = new;	\
			}						\
		}							\
		return (error);						\
	}								\
	SYSCTL_PROC(_net_inet_sctp, OID_AUTO, mib_name,			\
	                 CTLFLAG_VNET|CTLTYPE_UINT|CTLFLAG_RW, NULL, 0,	\
	                 sctp_sysctl_handle_##mib_name, "UI", prefix##_DESC);
#else
#define SCTP_UINT_SYSCTL(mib_name, var_name, prefix)			\
	static int							\
	sctp_sysctl_handle_##mib_name(struct sysctl_oid *oidp,		\
	                          void *arg1 __attribute__((unused)),	\
	                          int arg2 __attribute__((unused)),	\
	                          struct sysctl_req *req)		\
	{								\
		int error;						\
		uint32_t new;						\
									\
		new = SCTP_BASE_SYSCTL(var_name);			\
		error = sysctl_handle_int(oidp, &new, 0, req);		\
		if ((error == 0) && (req->newptr != USER_ADDR_NULL)) {	\
			if ((new < prefix##_MIN) ||			\
			    (new > prefix##_MAX)) {			\
				error = EINVAL;				\
			} else {					\
				SCTP_BASE_SYSCTL(var_name) = new;	\
			}						\
		}							\
		return (error);						\
	}								\
	SYSCTL_PROC(_net_inet_sctp, OID_AUTO, mib_name,			\
	            CTLTYPE_INT | CTLFLAG_RW, NULL, 0,			\
	            sctp_sysctl_handle_##mib_name, "I", prefix##_DESC);
#define CTLTYPE_UINT CTLTYPE_INT
#define CTLFLAG_VNET 0
#endif

/*
 * sysctl definitions
 */

SCTP_UINT_SYSCTL(sendspace, sctp_sendspace, SCTPCTL_MAXDGRAM)
SCTP_UINT_SYSCTL(recvspace, sctp_recvspace, SCTPCTL_RECVSPACE)
SCTP_UINT_SYSCTL(auto_asconf, sctp_auto_asconf, SCTPCTL_AUTOASCONF)
SCTP_UINT_SYSCTL(ecn_enable, sctp_ecn_enable, SCTPCTL_ECN_ENABLE)
SCTP_UINT_SYSCTL(pr_enable, sctp_pr_enable, SCTPCTL_PR_ENABLE)
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, auth_enable, CTLFLAG_VNET|CTLTYPE_UINT|CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_auth, "IU", SCTPCTL_AUTH_ENABLE_DESC);
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, asconf_enable, CTLFLAG_VNET|CTLTYPE_UINT|CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_asconf, "IU", SCTPCTL_ASCONF_ENABLE_DESC);
SCTP_UINT_SYSCTL(reconfig_enable, sctp_reconfig_enable, SCTPCTL_RECONFIG_ENABLE)
SCTP_UINT_SYSCTL(nrsack_enable, sctp_nrsack_enable, SCTPCTL_NRSACK_ENABLE)
SCTP_UINT_SYSCTL(pktdrop_enable, sctp_pktdrop_enable, SCTPCTL_PKTDROP_ENABLE)
#if defined(__APPLE__) && !defined(__Userspace__)
SCTP_UINT_SYSCTL(loopback_nocsum, sctp_no_csum_on_loopback, SCTPCTL_LOOPBACK_NOCSUM)
#endif
SCTP_UINT_SYSCTL(peer_chkoh, sctp_peer_chunk_oh, SCTPCTL_PEER_CHKOH)
SCTP_UINT_SYSCTL(maxburst, sctp_max_burst_default, SCTPCTL_MAXBURST)
SCTP_UINT_SYSCTL(fr_maxburst, sctp_fr_max_burst_default, SCTPCTL_FRMAXBURST)
SCTP_UINT_SYSCTL(maxchunks, sctp_max_chunks_on_queue, SCTPCTL_MAXCHUNKS)
SCTP_UINT_SYSCTL(tcbhashsize, sctp_hashtblsize, SCTPCTL_TCBHASHSIZE)
SCTP_UINT_SYSCTL(pcbhashsize, sctp_pcbtblsize, SCTPCTL_PCBHASHSIZE)
SCTP_UINT_SYSCTL(min_split_point, sctp_min_split_point, SCTPCTL_MIN_SPLIT_POINT)
SCTP_UINT_SYSCTL(chunkscale, sctp_chunkscale, SCTPCTL_CHUNKSCALE)
SCTP_UINT_SYSCTL(delayed_sack_time, sctp_delayed_sack_time_default, SCTPCTL_DELAYED_SACK_TIME)
SCTP_UINT_SYSCTL(sack_freq, sctp_sack_freq_default, SCTPCTL_SACK_FREQ)
SCTP_UINT_SYSCTL(sys_resource, sctp_system_free_resc_limit, SCTPCTL_SYS_RESOURCE)
SCTP_UINT_SYSCTL(asoc_resource, sctp_asoc_free_resc_limit, SCTPCTL_ASOC_RESOURCE)
SCTP_UINT_SYSCTL(heartbeat_interval, sctp_heartbeat_interval_default, SCTPCTL_HEARTBEAT_INTERVAL)
SCTP_UINT_SYSCTL(pmtu_raise_time, sctp_pmtu_raise_time_default, SCTPCTL_PMTU_RAISE_TIME)
SCTP_UINT_SYSCTL(shutdown_guard_time, sctp_shutdown_guard_time_default, SCTPCTL_SHUTDOWN_GUARD_TIME)
SCTP_UINT_SYSCTL(secret_lifetime, sctp_secret_lifetime_default, SCTPCTL_SECRET_LIFETIME)
SCTP_UINT_SYSCTL(rto_max, sctp_rto_max_default, SCTPCTL_RTO_MAX)
SCTP_UINT_SYSCTL(rto_min, sctp_rto_min_default, SCTPCTL_RTO_MIN)
SCTP_UINT_SYSCTL(rto_initial, sctp_rto_initial_default, SCTPCTL_RTO_INITIAL)
SCTP_UINT_SYSCTL(init_rto_max, sctp_init_rto_max_default, SCTPCTL_INIT_RTO_MAX)
SCTP_UINT_SYSCTL(valid_cookie_life, sctp_valid_cookie_life_default, SCTPCTL_VALID_COOKIE_LIFE)
SCTP_UINT_SYSCTL(init_rtx_max, sctp_init_rtx_max_default, SCTPCTL_INIT_RTX_MAX)
SCTP_UINT_SYSCTL(assoc_rtx_max, sctp_assoc_rtx_max_default, SCTPCTL_ASSOC_RTX_MAX)
SCTP_UINT_SYSCTL(path_rtx_max, sctp_path_rtx_max_default, SCTPCTL_PATH_RTX_MAX)
SCTP_UINT_SYSCTL(path_pf_threshold, sctp_path_pf_threshold, SCTPCTL_PATH_PF_THRESHOLD)
SCTP_UINT_SYSCTL(add_more_on_output, sctp_add_more_threshold, SCTPCTL_ADD_MORE_ON_OUTPUT)
SCTP_UINT_SYSCTL(incoming_streams, sctp_nr_incoming_streams_default, SCTPCTL_INCOMING_STREAMS)
SCTP_UINT_SYSCTL(outgoing_streams, sctp_nr_outgoing_streams_default, SCTPCTL_OUTGOING_STREAMS)
SCTP_UINT_SYSCTL(cmt_on_off, sctp_cmt_on_off, SCTPCTL_CMT_ON_OFF)
SCTP_UINT_SYSCTL(cmt_use_dac, sctp_cmt_use_dac, SCTPCTL_CMT_USE_DAC)
SCTP_UINT_SYSCTL(cwnd_maxburst, sctp_use_cwnd_based_maxburst, SCTPCTL_CWND_MAXBURST)
SCTP_UINT_SYSCTL(nat_friendly, sctp_nat_friendly, SCTPCTL_NAT_FRIENDLY)
SCTP_UINT_SYSCTL(abc_l_var, sctp_L2_abc_variable, SCTPCTL_ABC_L_VAR)
SCTP_UINT_SYSCTL(max_chained_mbufs, sctp_mbuf_threshold_count, SCTPCTL_MAX_CHAINED_MBUFS)
SCTP_UINT_SYSCTL(do_sctp_drain, sctp_do_drain, SCTPCTL_DO_SCTP_DRAIN)
SCTP_UINT_SYSCTL(hb_max_burst, sctp_hb_maxburst, SCTPCTL_HB_MAX_BURST)
SCTP_UINT_SYSCTL(abort_at_limit, sctp_abort_if_one_2_one_hits_limit, SCTPCTL_ABORT_AT_LIMIT)
SCTP_UINT_SYSCTL(min_residual, sctp_min_residual, SCTPCTL_MIN_RESIDUAL)
SCTP_UINT_SYSCTL(max_retran_chunk, sctp_max_retran_chunk, SCTPCTL_MAX_RETRAN_CHUNK)
SCTP_UINT_SYSCTL(log_level, sctp_logging_level, SCTPCTL_LOGGING_LEVEL)
SCTP_UINT_SYSCTL(default_cc_module, sctp_default_cc_module, SCTPCTL_DEFAULT_CC_MODULE)
SCTP_UINT_SYSCTL(default_ss_module, sctp_default_ss_module, SCTPCTL_DEFAULT_SS_MODULE)
SCTP_UINT_SYSCTL(default_frag_interleave, sctp_default_frag_interleave, SCTPCTL_DEFAULT_FRAG_INTERLEAVE)
SCTP_UINT_SYSCTL(mobility_base, sctp_mobility_base, SCTPCTL_MOBILITY_BASE)
SCTP_UINT_SYSCTL(mobility_fasthandoff, sctp_mobility_fasthandoff, SCTPCTL_MOBILITY_FASTHANDOFF)
#if defined(SCTP_LOCAL_TRACE_BUF)
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, log, CTLFLAG_VNET|CTLTYPE_STRUCT|CTLFLAG_RD,
            NULL, 0, sctp_sysctl_handle_trace_log, "S,sctplog", "SCTP logging (struct sctp_log)");
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, clear_trace, CTLFLAG_VNET|CTLTYPE_UINT | CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_trace_log_clear, "IU", "Clear SCTP Logging buffer");
#endif
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, udp_tunneling_port, CTLFLAG_VNET|CTLTYPE_UINT|CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_udp_tunneling, "IU", SCTPCTL_UDP_TUNNELING_PORT_DESC);
SCTP_UINT_SYSCTL(enable_sack_immediately, sctp_enable_sack_immediately, SCTPCTL_SACK_IMMEDIATELY_ENABLE)
SCTP_UINT_SYSCTL(nat_friendly_init, sctp_inits_include_nat_friendly, SCTPCTL_NAT_FRIENDLY_INITS)
SCTP_UINT_SYSCTL(vtag_time_wait, sctp_vtag_time_wait, SCTPCTL_TIME_WAIT)
SCTP_UINT_SYSCTL(buffer_splitting, sctp_buffer_splitting, SCTPCTL_BUFFER_SPLITTING)
SCTP_UINT_SYSCTL(initial_cwnd, sctp_initial_cwnd, SCTPCTL_INITIAL_CWND)
SCTP_UINT_SYSCTL(rttvar_bw, sctp_rttvar_bw, SCTPCTL_RTTVAR_BW)
SCTP_UINT_SYSCTL(rttvar_rtt, sctp_rttvar_rtt, SCTPCTL_RTTVAR_RTT)
SCTP_UINT_SYSCTL(rttvar_eqret, sctp_rttvar_eqret, SCTPCTL_RTTVAR_EQRET)
SCTP_UINT_SYSCTL(rttvar_steady_step, sctp_steady_step, SCTPCTL_RTTVAR_STEADYS)
SCTP_UINT_SYSCTL(use_dcccecn, sctp_use_dccc_ecn, SCTPCTL_RTTVAR_DCCCECN)
SCTP_UINT_SYSCTL(blackhole, sctp_blackhole, SCTPCTL_BLACKHOLE)
SCTP_UINT_SYSCTL(sendall_limit, sctp_sendall_limit, SCTPCTL_SENDALL_LIMIT)
SCTP_UINT_SYSCTL(diag_info_code, sctp_diag_info_code, SCTPCTL_DIAG_INFO_CODE)
#ifdef SCTP_DEBUG
SCTP_UINT_SYSCTL(debug, sctp_debug_on, SCTPCTL_DEBUG)
#endif
#if defined(__APPLE__) && !defined(__Userspace__)
SCTP_UINT_SYSCTL(main_timer, sctp_main_timer, SCTPCTL_MAIN_TIMER)
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, ignore_vmware_interfaces, CTLTYPE_UINT|CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_vmware_interfaces, "IU", SCTPCTL_IGNORE_VMWARE_INTERFACES_DESC);
SCTP_UINT_SYSCTL(addr_watchdog_limit, sctp_addr_watchdog_limit, SCTPCTL_ADDR_WATCHDOG_LIMIT)
SCTP_UINT_SYSCTL(vtag_watchdog_limit, sctp_vtag_watchdog_limit, SCTPCTL_VTAG_WATCHDOG_LIMIT)
#endif
#if defined(__APPLE__) && !defined(__Userspace__)
SCTP_UINT_SYSCTL(output_unlocked, sctp_output_unlocked, SCTPCTL_OUTPUT_UNLOCKED)
#endif
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, stats, CTLFLAG_VNET|CTLTYPE_STRUCT|CTLFLAG_RW,
            NULL, 0, sctp_sysctl_handle_stats, "S,sctpstat", "SCTP statistics (struct sctp_stat)");
SYSCTL_PROC(_net_inet_sctp, OID_AUTO, assoclist, CTLFLAG_VNET|CTLTYPE_OPAQUE|CTLFLAG_RD,
            NULL, 0, sctp_sysctl_handle_assoclist, "S,xassoc", "List of active SCTP associations");

#elif defined(_WIN32) && !defined(__Userspace__)

#define RANGECHK(var, min, max) \
	if ((var) < (min)) { (var) = (min); } \
	else if ((var) > (max)) { (var) = (max); }

static int
sctp_sysctl_handle_int(SYSCTL_HANDLER_ARGS)
{
	int error;

	error = sysctl_handle_int(oidp, oidp->oid_arg1, oidp->oid_arg2, req);
	if (error == 0) {
		RANGECHK(SCTP_BASE_SYSCTL(sctp_sendspace), SCTPCTL_MAXDGRAM_MIN, SCTPCTL_MAXDGRAM_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_recvspace), SCTPCTL_RECVSPACE_MIN, SCTPCTL_RECVSPACE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_auto_asconf), SCTPCTL_AUTOASCONF_MIN, SCTPCTL_AUTOASCONF_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_auto_asconf), SCTPCTL_AUTOASCONF_MIN, SCTPCTL_AUTOASCONF_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_ecn_enable), SCTPCTL_ECN_ENABLE_MIN, SCTPCTL_ECN_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_pr_enable), SCTPCTL_PR_ENABLE_MIN, SCTPCTL_PR_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_reconfig_enable), SCTPCTL_RECONFIG_ENABLE_MIN, SCTPCTL_RECONFIG_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_nrsack_enable), SCTPCTL_NRSACK_ENABLE_MIN, SCTPCTL_NRSACK_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_pktdrop_enable), SCTPCTL_PKTDROP_ENABLE_MIN, SCTPCTL_PKTDROP_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_no_csum_on_loopback), SCTPCTL_LOOPBACK_NOCSUM_MIN, SCTPCTL_LOOPBACK_NOCSUM_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_peer_chunk_oh), SCTPCTL_PEER_CHKOH_MIN, SCTPCTL_PEER_CHKOH_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_max_burst_default), SCTPCTL_MAXBURST_MIN, SCTPCTL_MAXBURST_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_fr_max_burst_default), SCTPCTL_FRMAXBURST_MIN, SCTPCTL_FRMAXBURST_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_max_chunks_on_queue), SCTPCTL_MAXCHUNKS_MIN, SCTPCTL_MAXCHUNKS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_hashtblsize), SCTPCTL_TCBHASHSIZE_MIN, SCTPCTL_TCBHASHSIZE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_pcbtblsize), SCTPCTL_PCBHASHSIZE_MIN, SCTPCTL_PCBHASHSIZE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_min_split_point), SCTPCTL_MIN_SPLIT_POINT_MIN, SCTPCTL_MIN_SPLIT_POINT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_chunkscale), SCTPCTL_CHUNKSCALE_MIN, SCTPCTL_CHUNKSCALE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_delayed_sack_time_default), SCTPCTL_DELAYED_SACK_TIME_MIN, SCTPCTL_DELAYED_SACK_TIME_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_sack_freq_default), SCTPCTL_SACK_FREQ_MIN, SCTPCTL_SACK_FREQ_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_system_free_resc_limit), SCTPCTL_SYS_RESOURCE_MIN, SCTPCTL_SYS_RESOURCE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_asoc_free_resc_limit), SCTPCTL_ASOC_RESOURCE_MIN, SCTPCTL_ASOC_RESOURCE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_heartbeat_interval_default), SCTPCTL_HEARTBEAT_INTERVAL_MIN, SCTPCTL_HEARTBEAT_INTERVAL_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_pmtu_raise_time_default), SCTPCTL_PMTU_RAISE_TIME_MIN, SCTPCTL_PMTU_RAISE_TIME_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_shutdown_guard_time_default), SCTPCTL_SHUTDOWN_GUARD_TIME_MIN, SCTPCTL_SHUTDOWN_GUARD_TIME_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_secret_lifetime_default), SCTPCTL_SECRET_LIFETIME_MIN, SCTPCTL_SECRET_LIFETIME_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rto_max_default), SCTPCTL_RTO_MAX_MIN, SCTPCTL_RTO_MAX_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rto_min_default), SCTPCTL_RTO_MIN_MIN, SCTPCTL_RTO_MIN_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rto_initial_default), SCTPCTL_RTO_INITIAL_MIN, SCTPCTL_RTO_INITIAL_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_init_rto_max_default), SCTPCTL_INIT_RTO_MAX_MIN, SCTPCTL_INIT_RTO_MAX_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_valid_cookie_life_default), SCTPCTL_VALID_COOKIE_LIFE_MIN, SCTPCTL_VALID_COOKIE_LIFE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_init_rtx_max_default), SCTPCTL_INIT_RTX_MAX_MIN, SCTPCTL_INIT_RTX_MAX_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_assoc_rtx_max_default), SCTPCTL_ASSOC_RTX_MAX_MIN, SCTPCTL_ASSOC_RTX_MAX_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_path_rtx_max_default), SCTPCTL_PATH_RTX_MAX_MIN, SCTPCTL_PATH_RTX_MAX_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_path_pf_threshold), SCTPCTL_PATH_PF_THRESHOLD_MIN, SCTPCTL_PATH_PF_THRESHOLD_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_add_more_threshold), SCTPCTL_ADD_MORE_ON_OUTPUT_MIN, SCTPCTL_ADD_MORE_ON_OUTPUT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_nr_incoming_streams_default), SCTPCTL_INCOMING_STREAMS_MIN, SCTPCTL_INCOMING_STREAMS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_nr_outgoing_streams_default), SCTPCTL_OUTGOING_STREAMS_MIN, SCTPCTL_OUTGOING_STREAMS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_cmt_on_off), SCTPCTL_CMT_ON_OFF_MIN, SCTPCTL_CMT_ON_OFF_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_cmt_use_dac), SCTPCTL_CMT_USE_DAC_MIN, SCTPCTL_CMT_USE_DAC_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_use_cwnd_based_maxburst), SCTPCTL_CWND_MAXBURST_MIN, SCTPCTL_CWND_MAXBURST_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_nat_friendly), SCTPCTL_NAT_FRIENDLY_MIN, SCTPCTL_NAT_FRIENDLY_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_L2_abc_variable), SCTPCTL_ABC_L_VAR_MIN, SCTPCTL_ABC_L_VAR_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_mbuf_threshold_count), SCTPCTL_MAX_CHAINED_MBUFS_MIN, SCTPCTL_MAX_CHAINED_MBUFS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_do_drain), SCTPCTL_DO_SCTP_DRAIN_MIN, SCTPCTL_DO_SCTP_DRAIN_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_hb_maxburst), SCTPCTL_HB_MAX_BURST_MIN, SCTPCTL_HB_MAX_BURST_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_abort_if_one_2_one_hits_limit), SCTPCTL_ABORT_AT_LIMIT_MIN, SCTPCTL_ABORT_AT_LIMIT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_min_residual), SCTPCTL_MIN_RESIDUAL_MIN, SCTPCTL_MIN_RESIDUAL_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_max_retran_chunk), SCTPCTL_MAX_RETRAN_CHUNK_MIN, SCTPCTL_MAX_RETRAN_CHUNK_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_logging_level), SCTPCTL_LOGGING_LEVEL_MIN, SCTPCTL_LOGGING_LEVEL_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_default_cc_module), SCTPCTL_DEFAULT_CC_MODULE_MIN, SCTPCTL_DEFAULT_CC_MODULE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_default_ss_module), SCTPCTL_DEFAULT_SS_MODULE_MIN, SCTPCTL_DEFAULT_SS_MODULE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_default_frag_interleave), SCTPCTL_DEFAULT_FRAG_INTERLEAVE_MIN, SCTPCTL_DEFAULT_FRAG_INTERLEAVE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_vtag_time_wait), SCTPCTL_TIME_WAIT_MIN, SCTPCTL_TIME_WAIT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_buffer_splitting), SCTPCTL_BUFFER_SPLITTING_MIN, SCTPCTL_BUFFER_SPLITTING_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_initial_cwnd), SCTPCTL_INITIAL_CWND_MIN, SCTPCTL_INITIAL_CWND_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rttvar_bw), SCTPCTL_RTTVAR_BW_MIN, SCTPCTL_RTTVAR_BW_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rttvar_rtt), SCTPCTL_RTTVAR_RTT_MIN, SCTPCTL_RTTVAR_RTT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_rttvar_eqret), SCTPCTL_RTTVAR_EQRET_MIN, SCTPCTL_RTTVAR_EQRET_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_steady_step), SCTPCTL_RTTVAR_STEADYS_MIN, SCTPCTL_RTTVAR_STEADYS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_use_dccc_ecn), SCTPCTL_RTTVAR_DCCCECN_MIN, SCTPCTL_RTTVAR_DCCCECN_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_mobility_base), SCTPCTL_MOBILITY_BASE_MIN, SCTPCTL_MOBILITY_BASE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_mobility_fasthandoff), SCTPCTL_MOBILITY_FASTHANDOFF_MIN, SCTPCTL_MOBILITY_FASTHANDOFF_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_enable_sack_immediately), SCTPCTL_SACK_IMMEDIATELY_ENABLE_MIN, SCTPCTL_SACK_IMMEDIATELY_ENABLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_inits_include_nat_friendly), SCTPCTL_NAT_FRIENDLY_INITS_MIN, SCTPCTL_NAT_FRIENDLY_INITS_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_blackhole), SCTPCTL_BLACKHOLE_MIN, SCTPCTL_BLACKHOLE_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_sendall_limit), SCTPCTL_SENDALL_LIMIT_MIN, SCTPCTL_SENDALL_LIMIT_MAX);
		RANGECHK(SCTP_BASE_SYSCTL(sctp_diag_info_code), SCTPCTL_DIAG_INFO_CODE_MIN, SCTPCTL_DIAG_INFO_CODE_MAX);
#ifdef SCTP_DEBUG
		RANGECHK(SCTP_BASE_SYSCTL(sctp_debug_on), SCTPCTL_DEBUG_MIN, SCTPCTL_DEBUG_MAX);
#endif
	}
	return (error);
}

void
sysctl_setup_sctp(void)
{
	sysctl_add_oid(&sysctl_oid_top, "sendspace", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_sendspace), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MAXDGRAM_DESC);

	sysctl_add_oid(&sysctl_oid_top, "recvspace", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_recvspace), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RECVSPACE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "auto_asconf", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_auto_asconf), 0, sctp_sysctl_handle_int,
	    SCTPCTL_AUTOASCONF_DESC);

	sysctl_add_oid(&sysctl_oid_top, "ecn_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_ecn_enable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ECN_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "pr_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_pr_enable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PR_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "auth_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_auth_enable), 0, sctp_sysctl_handle_auth,
	    SCTPCTL_AUTH_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "asconf_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_asconf_enable), 0, sctp_sysctl_handle_asconf,
	    SCTPCTL_ASCONF_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "reconfig_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_reconfig_enable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RECONFIG_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "nrsack_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_nrsack_enable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_NRSACK_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "pktdrop_enable", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_pktdrop_enable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PKTDROP_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "loopback_nocsum", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_no_csum_on_loopback), 0, sctp_sysctl_handle_int,
	    SCTPCTL_LOOPBACK_NOCSUM_DESC);

	sysctl_add_oid(&sysctl_oid_top, "peer_chkoh", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_peer_chunk_oh), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PEER_CHKOH_DESC);

	sysctl_add_oid(&sysctl_oid_top, "maxburst", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_max_burst_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MAXBURST_DESC);

	sysctl_add_oid(&sysctl_oid_top, "fr_maxburst", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_fr_max_burst_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_FRMAXBURST_DESC);

	sysctl_add_oid(&sysctl_oid_top, "maxchunks", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_max_chunks_on_queue), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MAXCHUNKS_DESC);

	sysctl_add_oid(&sysctl_oid_top, "tcbhashsize", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_hashtblsize), 0, sctp_sysctl_handle_int,
	    SCTPCTL_TCBHASHSIZE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "pcbhashsize", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_pcbtblsize), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PCBHASHSIZE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "min_split_point", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_min_split_point), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MIN_SPLIT_POINT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "chunkscale", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_chunkscale), 0, sctp_sysctl_handle_int,
	    SCTPCTL_CHUNKSCALE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "delayed_sack_time", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_delayed_sack_time_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DELAYED_SACK_TIME_DESC);

	sysctl_add_oid(&sysctl_oid_top, "sack_freq", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_sack_freq_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SACK_FREQ_DESC);

	sysctl_add_oid(&sysctl_oid_top, "sys_resource", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_system_free_resc_limit), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SYS_RESOURCE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "asoc_resource", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_asoc_free_resc_limit), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ASOC_RESOURCE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "heartbeat_interval", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_heartbeat_interval_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_HEARTBEAT_INTERVAL_DESC);

	sysctl_add_oid(&sysctl_oid_top, "pmtu_raise_time", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_pmtu_raise_time_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PMTU_RAISE_TIME_DESC);

	sysctl_add_oid(&sysctl_oid_top, "shutdown_guard_time", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_shutdown_guard_time_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SHUTDOWN_GUARD_TIME_DESC);

	sysctl_add_oid(&sysctl_oid_top, "secret_lifetime", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_secret_lifetime_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SECRET_LIFETIME_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rto_max", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rto_max_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTO_MAX_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rto_min", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rto_min_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTO_MIN_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rto_initial", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rto_initial_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTO_INITIAL_DESC);

	sysctl_add_oid(&sysctl_oid_top, "init_rto_max", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_init_rto_max_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_INIT_RTO_MAX_DESC);

	sysctl_add_oid(&sysctl_oid_top, "valid_cookie_life", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_valid_cookie_life_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_VALID_COOKIE_LIFE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "init_rtx_max", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_init_rtx_max_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_INIT_RTX_MAX_DESC);

	sysctl_add_oid(&sysctl_oid_top, "assoc_rtx_max", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_assoc_rtx_max_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ASSOC_RTX_MAX_DESC);

	sysctl_add_oid(&sysctl_oid_top, "path_rtx_max", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_path_rtx_max_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PATH_RTX_MAX_DESC);

	sysctl_add_oid(&sysctl_oid_top, "path_pf_threshold", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_path_pf_threshold), 0, sctp_sysctl_handle_int,
	    SCTPCTL_PATH_PF_THRESHOLD_DESC);

	sysctl_add_oid(&sysctl_oid_top, "add_more_on_output", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_add_more_threshold), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ADD_MORE_ON_OUTPUT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "incoming_streams", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_nr_incoming_streams_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_INCOMING_STREAMS_DESC);

	sysctl_add_oid(&sysctl_oid_top, "outgoing_streams", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_nr_outgoing_streams_default), 0, sctp_sysctl_handle_int,
	    SCTPCTL_OUTGOING_STREAMS_DESC);

	sysctl_add_oid(&sysctl_oid_top, "cmt_on_off", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_cmt_on_off), 0, sctp_sysctl_handle_int,
	    SCTPCTL_CMT_ON_OFF_DESC);

	sysctl_add_oid(&sysctl_oid_top, "cmt_use_dac", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_cmt_use_dac), 0, sctp_sysctl_handle_int,
	    SCTPCTL_CMT_USE_DAC_DESC);

	sysctl_add_oid(&sysctl_oid_top, "cwnd_maxburst", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_use_cwnd_based_maxburst), 0, sctp_sysctl_handle_int,
	    SCTPCTL_CWND_MAXBURST_DESC);

	sysctl_add_oid(&sysctl_oid_top, "nat_friendly", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_nat_friendly), 0, sctp_sysctl_handle_int,
	    SCTPCTL_NAT_FRIENDLY_DESC);

	sysctl_add_oid(&sysctl_oid_top, "abc_l_var", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_L2_abc_variable), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ABC_L_VAR_DESC);

	sysctl_add_oid(&sysctl_oid_top, "max_chained_mbufs", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_mbuf_threshold_count), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MAX_CHAINED_MBUFS_DESC);

	sysctl_add_oid(&sysctl_oid_top, "do_sctp_drain", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_do_drain), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DO_SCTP_DRAIN_DESC);

	sysctl_add_oid(&sysctl_oid_top, "hb_max_burst", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_hb_maxburst), 0, sctp_sysctl_handle_int,
	    SCTPCTL_HB_MAX_BURST_DESC);

	sysctl_add_oid(&sysctl_oid_top, "abort_at_limit", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_abort_if_one_2_one_hits_limit), 0, sctp_sysctl_handle_int,
	    SCTPCTL_ABORT_AT_LIMIT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "min_residual", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_min_residual), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MIN_RESIDUAL_DESC);

	sysctl_add_oid(&sysctl_oid_top, "max_retran_chunk", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_max_retran_chunk), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MAX_RETRAN_CHUNK_DESC);

	sysctl_add_oid(&sysctl_oid_top, "log_level", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_logging_level), 0, sctp_sysctl_handle_int,
	    SCTPCTL_LOGGING_LEVEL_DESC);

	sysctl_add_oid(&sysctl_oid_top, "default_cc_module", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_default_cc_module), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DEFAULT_CC_MODULE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "default_ss_module", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_default_ss_module), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DEFAULT_SS_MODULE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "default_frag_interleave", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_default_frag_interleave), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DEFAULT_FRAG_INTERLEAVE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "mobility_base", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_mobility_base), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MOBILITY_BASE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "mobility_fasthandoff", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_mobility_fasthandoff), 0, sctp_sysctl_handle_int,
	    SCTPCTL_MOBILITY_FASTHANDOFF_DESC);

#if defined(SCTP_LOCAL_TRACE_BUF)
	sysctl_add_oid(&sysctl_oid_top, "sctp_log", CTLTYPE_STRUCT|CTLFLAG_RD,
	    SCTP_BASE_SYSCTL(sctp_log), sizeof(struct sctp_log), NULL,
	    "SCTP logging (struct sctp_log)");

	sysctl_add_oid(&sysctl_oid_top, "clear_trace", CTLTYPE_INT|CTLFLAG_WR,
	    NULL, 0, sctp_sysctl_handle_trace_log_clear,
	    "Clear SCTP Logging buffer");
#endif

	sysctl_add_oid(&sysctl_oid_top, "udp_tunneling_port", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_udp_tunneling_port), 0, sctp_sysctl_handle_udp_tunneling,
	    SCTPCTL_UDP_TUNNELING_PORT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "enable_sack_immediately", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_enable_sack_immediately), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SACK_IMMEDIATELY_ENABLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "nat_friendly_init", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_inits_include_nat_friendly), 0, sctp_sysctl_handle_int,
	    SCTPCTL_NAT_FRIENDLY_DESC);

	sysctl_add_oid(&sysctl_oid_top, "vtag_time_wait", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_vtag_time_wait), 0, sctp_sysctl_handle_int,
	    SCTPCTL_TIME_WAIT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "buffer_splitting", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_buffer_splitting), 0, sctp_sysctl_handle_int,
	    SCTPCTL_BUFFER_SPLITTING_DESC);

	sysctl_add_oid(&sysctl_oid_top, "initial_cwnd", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_initial_cwnd), 0, sctp_sysctl_handle_int,
	    SCTPCTL_INITIAL_CWND_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rttvar_bw", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rttvar_bw), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTTVAR_BW_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rttvar_rtt", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rttvar_rtt), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTTVAR_RTT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rttvar_eqret", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_rttvar_eqret), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTTVAR_EQRET_DESC);

	sysctl_add_oid(&sysctl_oid_top, "rttvar_steady_step", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_steady_step), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTTVAR_STEADYS_DESC);

	sysctl_add_oid(&sysctl_oid_top, "use_dcccecn", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_use_dccc_ecn), 0, sctp_sysctl_handle_int,
	    SCTPCTL_RTTVAR_DCCCECN_DESC);

	sysctl_add_oid(&sysctl_oid_top, "blackhole", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_blackhole), 0, sctp_sysctl_handle_int,
	    SCTPCTL_BLACKHOLE_DESC);

	sysctl_add_oid(&sysctl_oid_top, "sendall_limit", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_sendall_limit), 0, sctp_sysctl_handle_int,
	    SCTPCTL_SENDALL_LIMIT_DESC);

	sysctl_add_oid(&sysctl_oid_top, "diag_info_code", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_diag_info_code), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DIAG_INFO_CODE_DESC);

#ifdef SCTP_DEBUG
	sysctl_add_oid(&sysctl_oid_top, "debug", CTLTYPE_INT|CTLFLAG_RW,
	    &SCTP_BASE_SYSCTL(sctp_debug_on), 0, sctp_sysctl_handle_int,
	    SCTPCTL_DEBUG_DESC);
#endif

	sysctl_add_oid(&sysctl_oid_top, "stats", CTLTYPE_STRUCT|CTLFLAG_RW,
	    &SCTP_BASE_STATS, sizeof(SCTP_BASE_STATS), NULL,
	    "SCTP statistics (struct sctp_stat)");

	sysctl_add_oid(&sysctl_oid_top, "assoclist", CTLTYPE_STRUCT|CTLFLAG_RD,
	    NULL, 0, sctp_assoclist,
	    "List of active SCTP associations");
}
#endif
#endif
