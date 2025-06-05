/*-
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2001-2007, by Cisco Systems, Inc. All rights reserved.
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
__FBSDID("$FreeBSD: head/sys/netinet/sctp_peeloff.h 309607 2016-12-06 10:21:25Z tuexen $");
#endif

#ifndef _NETINET_SCTP_PEELOFF_H_
#define _NETINET_SCTP_PEELOFF_H_
#if defined(HAVE_SCTP_PEELOFF_SOCKOPT)
/* socket option peeloff */
struct sctp_peeloff_opt {
#if !(defined(_WIN32) && !defined(__Userspace__))
	int s;
#else
	HANDLE s;
#endif
	sctp_assoc_t assoc_id;
#if !(defined(_WIN32) && !defined(__Userspace__))
	int new_sd;
#else
	HANDLE new_sd;
#endif
};
#endif /* HAVE_SCTP_PEELOFF_SOCKOPT */
#if defined(_KERNEL)
int sctp_can_peel_off(struct socket *, sctp_assoc_t);
int sctp_do_peeloff(struct socket *, struct socket *, sctp_assoc_t);
#if defined(HAVE_SCTP_PEELOFF_SOCKOPT)
struct socket *sctp_get_peeloff(struct socket *, sctp_assoc_t, int *);
int sctp_peeloff_option(struct proc *p, struct sctp_peeloff_opt *peeloff);
#endif /* HAVE_SCTP_PEELOFF_SOCKOPT */
#endif /* _KERNEL */
#if defined(__Userspace__)
int sctp_can_peel_off(struct socket *, sctp_assoc_t);
int sctp_do_peeloff(struct socket *, struct socket *, sctp_assoc_t);
#endif /* __Userspace__ */
#endif /* _NETINET_SCTP_PEELOFF_H_ */
