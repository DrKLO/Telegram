/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.]
 */
/* ====================================================================
 * Copyright (c) 1998-2001 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#ifndef OPENSSL_HEADER_CRYPTO_INTERNAL_H
#define OPENSSL_HEADER_CRYPTO_INTERNAL_H

#include <openssl/ex_data.h>
#include <openssl/thread.h>

#if defined(OPENSSL_NO_THREADS)
#elif defined(OPENSSL_WINDOWS)
#pragma warning(push, 3)
#include <windows.h>
#pragma warning(pop)
#else
#include <pthread.h>
#endif

#if defined(__cplusplus)
extern "C" {
#endif


/* MSVC's C4701 warning about the use of *potentially*--as opposed to
 * *definitely*--uninitialized values sometimes has false positives. Usually
 * the false positives can and should be worked around by simplifying the
 * control flow. When that is not practical, annotate the function containing
 * the code that triggers the warning with
 * OPENSSL_SUPPRESS_POTENTIALLY_UNINITIALIZED_WARNINGS after its parameters:
 *
 *    void f() OPENSSL_SUPPRESS_POTENTIALLY_UNINITIALIZED_WARNINGS {
 *       ...
 *    }
 *
 * Note that MSVC's control flow analysis seems to operate on a whole-function
 * basis, so the annotation must be placed on the entire function, not just a
 * block within the function. */
#if defined(_MSC_VER)
#define OPENSSL_SUPPRESS_POTENTIALLY_UNINITIALIZED_WARNINGS \
        __pragma(warning(suppress:4701))
#else
#define OPENSSL_SUPPRESS_POTENTIALLY_UNINITIALIZED_WARNINGS
#endif

/* MSVC will sometimes correctly detect unreachable code and issue a warning,
 * which breaks the build since we treat errors as warnings, in some rare cases
 * where we want to allow the dead code to continue to exist. In these
 * situations, annotate the function containing the unreachable code with
 * OPENSSL_SUPPRESS_UNREACHABLE_CODE_WARNINGS after its parameters:
 *
 *    void f() OPENSSL_SUPPRESS_UNREACHABLE_CODE_WARNINGS {
 *       ...
 *    }
 *
 * Note that MSVC's reachability analysis seems to operate on a whole-function
 * basis, so the annotation must be placed on the entire function, not just a
 * block within the function. */
#if defined(_MSC_VER)
#define OPENSSL_SUPPRESS_UNREACHABLE_CODE_WARNINGS \
        __pragma(warning(suppress:4702))
#else
#define OPENSSL_SUPPRESS_UNREACHABLE_CODE_WARNINGS
#endif


#if defined(_MSC_VER)
#define OPENSSL_U64(x) x##UI64
#else

#if defined(OPENSSL_64_BIT)
#define OPENSSL_U64(x) x##UL
#else
#define OPENSSL_U64(x) x##ULL
#endif

#endif  /* defined(_MSC_VER) */

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64) || defined(OPENSSL_ARM) || \
    defined(OPENSSL_AARCH64)
/* OPENSSL_cpuid_setup initializes OPENSSL_ia32cap_P. */
void OPENSSL_cpuid_setup(void);
#endif

#if !defined(inline)
#define inline __inline
#endif


/* Constant-time utility functions.
 *
 * The following methods return a bitmask of all ones (0xff...f) for true and 0
 * for false. This is useful for choosing a value based on the result of a
 * conditional in constant time. For example,
 *
 * if (a < b) {
 *   c = a;
 * } else {
 *   c = b;
 * }
 *
 * can be written as
 *
 * unsigned int lt = constant_time_lt(a, b);
 * c = constant_time_select(lt, a, b); */

/* constant_time_msb returns the given value with the MSB copied to all the
 * other bits. */
static inline unsigned int constant_time_msb(unsigned int a) {
  return (unsigned int)((int)(a) >> (sizeof(int) * 8 - 1));
}

/* constant_time_lt returns 0xff..f if a < b and 0 otherwise. */
static inline unsigned int constant_time_lt(unsigned int a, unsigned int b) {
  /* Consider the two cases of the problem:
   *   msb(a) == msb(b): a < b iff the MSB of a - b is set.
   *   msb(a) != msb(b): a < b iff the MSB of b is set.
   *
   * If msb(a) == msb(b) then the following evaluates as:
   *   msb(a^((a^b)|((a-b)^a))) ==
   *   msb(a^((a-b) ^ a))       ==   (because msb(a^b) == 0)
   *   msb(a^a^(a-b))           ==   (rearranging)
   *   msb(a-b)                      (because ∀x. x^x == 0)
   *
   * Else, if msb(a) != msb(b) then the following evaluates as:
   *   msb(a^((a^b)|((a-b)^a))) ==
   *   msb(a^(𝟙 | ((a-b)^a)))   ==   (because msb(a^b) == 1 and 𝟙
   *                                  represents a value s.t. msb(𝟙) = 1)
   *   msb(a^𝟙)                 ==   (because ORing with 1 results in 1)
   *   msb(b)
   *
   *
   * Here is an SMT-LIB verification of this formula:
   *
   * (define-fun lt ((a (_ BitVec 32)) (b (_ BitVec 32))) (_ BitVec 32)
   *   (bvxor a (bvor (bvxor a b) (bvxor (bvsub a b) a)))
   * )
   *
   * (declare-fun a () (_ BitVec 32))
   * (declare-fun b () (_ BitVec 32))
   *
   * (assert (not (= (= #x00000001 (bvlshr (lt a b) #x0000001f)) (bvult a b))))
   * (check-sat)
   * (get-model)
   */
  return constant_time_msb(a^((a^b)|((a-b)^a)));
}

/* constant_time_lt_8 acts like |constant_time_lt| but returns an 8-bit mask. */
static inline uint8_t constant_time_lt_8(unsigned int a, unsigned int b) {
  return (uint8_t)(constant_time_lt(a, b));
}

/* constant_time_gt returns 0xff..f if a >= b and 0 otherwise. */
static inline unsigned int constant_time_ge(unsigned int a, unsigned int b) {
  return ~constant_time_lt(a, b);
}

/* constant_time_ge_8 acts like |constant_time_ge| but returns an 8-bit mask. */
static inline uint8_t constant_time_ge_8(unsigned int a, unsigned int b) {
  return (uint8_t)(constant_time_ge(a, b));
}

/* constant_time_is_zero returns 0xff..f if a == 0 and 0 otherwise. */
static inline unsigned int constant_time_is_zero(unsigned int a) {
  /* Here is an SMT-LIB verification of this formula:
   *
   * (define-fun is_zero ((a (_ BitVec 32))) (_ BitVec 32)
   *   (bvand (bvnot a) (bvsub a #x00000001))
   * )
   *
   * (declare-fun a () (_ BitVec 32))
   *
   * (assert (not (= (= #x00000001 (bvlshr (is_zero a) #x0000001f)) (= a #x00000000))))
   * (check-sat)
   * (get-model)
   */
  return constant_time_msb(~a & (a - 1));
}

/* constant_time_is_zero_8 acts like constant_time_is_zero but returns an 8-bit
 * mask. */
static inline uint8_t constant_time_is_zero_8(unsigned int a) {
  return (uint8_t)(constant_time_is_zero(a));
}

/* constant_time_eq returns 0xff..f if a == b and 0 otherwise. */
static inline unsigned int constant_time_eq(unsigned int a, unsigned int b) {
  return constant_time_is_zero(a ^ b);
}

/* constant_time_eq_8 acts like |constant_time_eq| but returns an 8-bit mask. */
static inline uint8_t constant_time_eq_8(unsigned int a, unsigned int b) {
  return (uint8_t)(constant_time_eq(a, b));
}

/* constant_time_eq_int acts like |constant_time_eq| but works on int values. */
static inline unsigned int constant_time_eq_int(int a, int b) {
  return constant_time_eq((unsigned)(a), (unsigned)(b));
}

/* constant_time_eq_int_8 acts like |constant_time_eq_int| but returns an 8-bit
 * mask. */
static inline uint8_t constant_time_eq_int_8(int a, int b) {
  return constant_time_eq_8((unsigned)(a), (unsigned)(b));
}

/* constant_time_select returns (mask & a) | (~mask & b). When |mask| is all 1s
 * or all 0s (as returned by the methods above), the select methods return
 * either |a| (if |mask| is nonzero) or |b| (if |mask| is zero). */
static inline unsigned int constant_time_select(unsigned int mask,
                                                unsigned int a, unsigned int b) {
  return (mask & a) | (~mask & b);
}

/* constant_time_select_8 acts like |constant_time_select| but operates on
 * 8-bit values. */
static inline uint8_t constant_time_select_8(uint8_t mask, uint8_t a,
                                             uint8_t b) {
  return (uint8_t)(constant_time_select(mask, a, b));
}

/* constant_time_select_int acts like |constant_time_select| but operates on
 * ints. */
static inline int constant_time_select_int(unsigned int mask, int a, int b) {
  return (int)(constant_time_select(mask, (unsigned)(a), (unsigned)(b)));
}


/* Thread-safe initialisation. */

#if defined(OPENSSL_NO_THREADS)
typedef uint32_t CRYPTO_once_t;
#define CRYPTO_ONCE_INIT 0
#elif defined(OPENSSL_WINDOWS)
typedef LONG CRYPTO_once_t;
#define CRYPTO_ONCE_INIT 0
#else
typedef pthread_once_t CRYPTO_once_t;
#define CRYPTO_ONCE_INIT PTHREAD_ONCE_INIT
#endif

/* CRYPTO_once calls |init| exactly once per process. This is thread-safe: if
 * concurrent threads call |CRYPTO_once| with the same |CRYPTO_once_t| argument
 * then they will block until |init| completes, but |init| will have only been
 * called once.
 *
 * The |once| argument must be a |CRYPTO_once_t| that has been initialised with
 * the value |CRYPTO_ONCE_INIT|. */
OPENSSL_EXPORT void CRYPTO_once(CRYPTO_once_t *once, void (*init)(void));


/* Reference counting. */

/* CRYPTO_REFCOUNT_MAX is the value at which the reference count saturates. */
#define CRYPTO_REFCOUNT_MAX 0xffffffff

/* CRYPTO_refcount_inc atomically increments the value at |*count| unless the
 * value would overflow. It's safe for multiple threads to concurrently call
 * this or |CRYPTO_refcount_dec_and_test_zero| on the same
 * |CRYPTO_refcount_t|. */
OPENSSL_EXPORT void CRYPTO_refcount_inc(CRYPTO_refcount_t *count);

/* CRYPTO_refcount_dec_and_test_zero tests the value at |*count|:
 *   if it's zero, it crashes the address space.
 *   if it's the maximum value, it returns zero.
 *   otherwise, it atomically decrements it and returns one iff the resulting
 *       value is zero.
 *
 * It's safe for multiple threads to concurrently call this or
 * |CRYPTO_refcount_inc| on the same |CRYPTO_refcount_t|. */
OPENSSL_EXPORT int CRYPTO_refcount_dec_and_test_zero(CRYPTO_refcount_t *count);


/* Locks.
 *
 * Two types of locks are defined: |CRYPTO_MUTEX|, which can be used in
 * structures as normal, and |struct CRYPTO_STATIC_MUTEX|, which can be used as
 * a global lock. A global lock must be initialised to the value
 * |CRYPTO_STATIC_MUTEX_INIT|.
 *
 * |CRYPTO_MUTEX| can appear in public structures and so is defined in
 * thread.h.
 *
 * The global lock is a different type because there's no static initialiser
 * value on Windows for locks, so global locks have to be coupled with a
 * |CRYPTO_once_t| to ensure that the lock is setup before use. This is done
 * automatically by |CRYPTO_STATIC_MUTEX_lock_*|. */

#if defined(OPENSSL_NO_THREADS)
struct CRYPTO_STATIC_MUTEX {};
#define CRYPTO_STATIC_MUTEX_INIT {}
#elif defined(OPENSSL_WINDOWS)
struct CRYPTO_STATIC_MUTEX {
  CRYPTO_once_t once;
  CRITICAL_SECTION lock;
};
#define CRYPTO_STATIC_MUTEX_INIT { CRYPTO_ONCE_INIT, { 0 } }
#else
struct CRYPTO_STATIC_MUTEX {
  pthread_rwlock_t lock;
};
#define CRYPTO_STATIC_MUTEX_INIT { PTHREAD_RWLOCK_INITIALIZER }
#endif

/* CRYPTO_MUTEX_init initialises |lock|. If |lock| is a static variable, use a
 * |CRYPTO_STATIC_MUTEX|. */
OPENSSL_EXPORT void CRYPTO_MUTEX_init(CRYPTO_MUTEX *lock);

/* CRYPTO_MUTEX_lock_read locks |lock| such that other threads may also have a
 * read lock, but none may have a write lock. (On Windows, read locks are
 * actually fully exclusive.) */
OPENSSL_EXPORT void CRYPTO_MUTEX_lock_read(CRYPTO_MUTEX *lock);

/* CRYPTO_MUTEX_lock_write locks |lock| such that no other thread has any type
 * of lock on it. */
OPENSSL_EXPORT void CRYPTO_MUTEX_lock_write(CRYPTO_MUTEX *lock);

/* CRYPTO_MUTEX_unlock unlocks |lock|. */
OPENSSL_EXPORT void CRYPTO_MUTEX_unlock(CRYPTO_MUTEX *lock);

/* CRYPTO_MUTEX_cleanup releases all resources held by |lock|. */
OPENSSL_EXPORT void CRYPTO_MUTEX_cleanup(CRYPTO_MUTEX *lock);

/* CRYPTO_STATIC_MUTEX_lock_read locks |lock| such that other threads may also
 * have a read lock, but none may have a write lock. The |lock| variable does
 * not need to be initialised by any function, but must have been statically
 * initialised with |CRYPTO_STATIC_MUTEX_INIT|. */
OPENSSL_EXPORT void CRYPTO_STATIC_MUTEX_lock_read(
    struct CRYPTO_STATIC_MUTEX *lock);

/* CRYPTO_STATIC_MUTEX_lock_write locks |lock| such that no other thread has
 * any type of lock on it.  The |lock| variable does not need to be initialised
 * by any function, but must have been statically initialised with
 * |CRYPTO_STATIC_MUTEX_INIT|. */
OPENSSL_EXPORT void CRYPTO_STATIC_MUTEX_lock_write(
    struct CRYPTO_STATIC_MUTEX *lock);

/* CRYPTO_STATIC_MUTEX_unlock unlocks |lock|. */
OPENSSL_EXPORT void CRYPTO_STATIC_MUTEX_unlock(
    struct CRYPTO_STATIC_MUTEX *lock);


/* Thread local storage. */

/* thread_local_data_t enumerates the types of thread-local data that can be
 * stored. */
typedef enum {
  OPENSSL_THREAD_LOCAL_ERR = 0,
  OPENSSL_THREAD_LOCAL_RAND,
  OPENSSL_THREAD_LOCAL_TEST,
  NUM_OPENSSL_THREAD_LOCALS,
} thread_local_data_t;

/* thread_local_destructor_t is the type of a destructor function that will be
 * called when a thread exits and its thread-local storage needs to be freed. */
typedef void (*thread_local_destructor_t)(void *);

/* CRYPTO_get_thread_local gets the pointer value that is stored for the
 * current thread for the given index, or NULL if none has been set. */
OPENSSL_EXPORT void *CRYPTO_get_thread_local(thread_local_data_t value);

/* CRYPTO_set_thread_local sets a pointer value for the current thread at the
 * given index. This function should only be called once per thread for a given
 * |index|: rather than update the pointer value itself, update the data that
 * is pointed to.
 *
 * The destructor function will be called when a thread exits to free this
 * thread-local data. All calls to |CRYPTO_set_thread_local| with the same
 * |index| should have the same |destructor| argument. The destructor may be
 * called with a NULL argument if a thread that never set a thread-local
 * pointer for |index|, exits. The destructor may be called concurrently with
 * different arguments.
 *
 * This function returns one on success or zero on error. If it returns zero
 * then |destructor| has been called with |value| already. */
OPENSSL_EXPORT int CRYPTO_set_thread_local(
    thread_local_data_t index, void *value,
    thread_local_destructor_t destructor);


/* ex_data */

typedef struct crypto_ex_data_func_st CRYPTO_EX_DATA_FUNCS;

/* CRYPTO_EX_DATA_CLASS tracks the ex_indices registered for a type which
 * supports ex_data. It should defined as a static global within the module
 * which defines that type. */
typedef struct {
  struct CRYPTO_STATIC_MUTEX lock;
  STACK_OF(CRYPTO_EX_DATA_FUNCS) *meth;
  /* num_reserved is one if the ex_data index zero is reserved for legacy
   * |TYPE_get_app_data| functions. */
  uint8_t num_reserved;
} CRYPTO_EX_DATA_CLASS;

#define CRYPTO_EX_DATA_CLASS_INIT {CRYPTO_STATIC_MUTEX_INIT, NULL, 0}
#define CRYPTO_EX_DATA_CLASS_INIT_WITH_APP_DATA \
    {CRYPTO_STATIC_MUTEX_INIT, NULL, 1}

/* CRYPTO_get_ex_new_index allocates a new index for |ex_data_class| and writes
 * it to |*out_index|. Each class of object should provide a wrapper function
 * that uses the correct |CRYPTO_EX_DATA_CLASS|. It returns one on success and
 * zero otherwise. */
OPENSSL_EXPORT int CRYPTO_get_ex_new_index(CRYPTO_EX_DATA_CLASS *ex_data_class,
                                           int *out_index, long argl,
                                           void *argp, CRYPTO_EX_new *new_func,
                                           CRYPTO_EX_dup *dup_func,
                                           CRYPTO_EX_free *free_func);

/* CRYPTO_set_ex_data sets an extra data pointer on a given object. Each class
 * of object should provide a wrapper function. */
OPENSSL_EXPORT int CRYPTO_set_ex_data(CRYPTO_EX_DATA *ad, int index, void *val);

/* CRYPTO_get_ex_data returns an extra data pointer for a given object, or NULL
 * if no such index exists. Each class of object should provide a wrapper
 * function. */
OPENSSL_EXPORT void *CRYPTO_get_ex_data(const CRYPTO_EX_DATA *ad, int index);

/* CRYPTO_new_ex_data initialises a newly allocated |CRYPTO_EX_DATA| which is
 * embedded inside of |obj| which is of class |ex_data_class|. Returns one on
 * success and zero otherwise. */
OPENSSL_EXPORT int CRYPTO_new_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class,
                                      void *obj, CRYPTO_EX_DATA *ad);

/* CRYPTO_dup_ex_data duplicates |from| into a freshly allocated
 * |CRYPTO_EX_DATA|, |to|. Both of which are inside objects of the given
 * class. It returns one on success and zero otherwise. */
OPENSSL_EXPORT int CRYPTO_dup_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class,
                                      CRYPTO_EX_DATA *to,
                                      const CRYPTO_EX_DATA *from);

/* CRYPTO_free_ex_data frees |ad|, which is embedded inside |obj|, which is an
 * object of the given class. */
OPENSSL_EXPORT void CRYPTO_free_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class,
                                        void *obj, CRYPTO_EX_DATA *ad);


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_CRYPTO_INTERNAL_H */
