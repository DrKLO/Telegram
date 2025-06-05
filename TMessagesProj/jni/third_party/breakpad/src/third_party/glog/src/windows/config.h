/* src/config.h.in.  Generated from configure.ac by autoheader.  */

/* Namespace for Google classes */
#define GOOGLE_NAMESPACE google

/* Define if you have the `dladdr' function */
#undef HAVE_DLADDR

/* Define to 1 if you have the <dlfcn.h> header file. */
#undef HAVE_DLFCN_H

/* Define to 1 if you have the <execinfo.h> header file. */
#undef HAVE_EXECINFO_H

/* Define to 1 if you have the <inttypes.h> header file. */
#undef HAVE_INTTYPES_H

/* Define to 1 if you have the <libunwind.h> header file. */
#undef HAVE_LIBUNWIND_H

/* define if you have google gflags library */
#undef HAVE_LIB_GFLAGS

/* define if you have libunwind */
#undef HAVE_LIB_UNWIND

/* Define to 1 if you have the <memory.h> header file. */
#undef HAVE_MEMORY_H

/* define if the compiler implements namespaces */
#undef HAVE_NAMESPACES

/* Define if you have POSIX threads libraries and header files. */
#undef HAVE_PTHREAD

/* define if the compiler implements pthread_rwlock_* */
#undef HAVE_RWLOCK

/* Define if you have the `sigaltstack' function */
#undef HAVE_SIGALTSTACK

/* Define to 1 if you have the <stdint.h> header file. */
#undef HAVE_STDINT_H

/* Define to 1 if you have the <stdlib.h> header file. */
#undef HAVE_STDLIB_H

/* Define to 1 if you have the <strings.h> header file. */
#undef HAVE_STRINGS_H

/* Define to 1 if you have the <string.h> header file. */
#undef HAVE_STRING_H

/* Define to 1 if you have the <syscall.h> header file. */
#undef HAVE_SYSCALL_H

/* Define to 1 if you have the <sys/stat.h> header file. */
#undef HAVE_SYS_STAT_H

/* Define to 1 if you have the <sys/syscall.h> header file. */
#undef HAVE_SYS_SYSCALL_H

/* Define to 1 if you have the <sys/types.h> header file. */
#undef HAVE_SYS_TYPES_H

/* Define to 1 if you have the <ucontext.h> header file. */
#undef HAVE_UCONTEXT_H

/* Define to 1 if you have the <unistd.h> header file. */
#undef HAVE_UNISTD_H

/* define if the compiler supports using expression for operator */
#undef HAVE_USING_OPERATOR

/* define if your compiler has __attribute__ */
#undef HAVE___ATTRIBUTE__

/* define if your compiler has __builtin_expect */
#undef HAVE___BUILTIN_EXPECT

/* define if your compiler has __sync_val_compare_and_swap */
#undef HAVE___SYNC_VAL_COMPARE_AND_SWAP

/* Name of package */
#undef PACKAGE

/* Define to the address where bug reports for this package should be sent. */
#undef PACKAGE_BUGREPORT

/* Define to the full name of this package. */
#undef PACKAGE_NAME

/* Define to the full name and version of this package. */
#undef PACKAGE_STRING

/* Define to the one symbol short name of this package. */
#undef PACKAGE_TARNAME

/* Define to the version of this package. */
#undef PACKAGE_VERSION

/* How to access the PC from a struct ucontext */
#undef PC_FROM_UCONTEXT

/* Define to necessary symbol if this constant uses a non-standard name on
   your system. */
#undef PTHREAD_CREATE_JOINABLE

/* The size of `void *', as computed by sizeof. */
#undef SIZEOF_VOID_P

/* Define to 1 if you have the ANSI C header files. */
#undef STDC_HEADERS

/* the namespace where STL code like vector<> is defined */
#undef STL_NAMESPACE

/* Version number of package */
#undef VERSION

/* Stops putting the code inside the Google namespace */
#define _END_GOOGLE_NAMESPACE_ }

/* Puts following code inside the Google namespace */
#define _START_GOOGLE_NAMESPACE_ namespace google {

/* Always the empty-string on non-windows systems. On windows, should be
   "__declspec(dllexport)". This way, when we compile the dll, we export our
   functions/classes. It's safe to define this here because config.h is only
   used internally, to compile the DLL, and every DLL source file #includes
   "config.h" before anything else. */
#ifndef GOOGLE_GLOG_DLL_DECL
# define GOOGLE_GLOG_IS_A_DLL  1   /* not set if you're statically linking */
# define GOOGLE_GLOG_DLL_DECL  __declspec(dllexport)
# define GOOGLE_GLOG_DLL_DECL_FOR_UNITTESTS  __declspec(dllimport)
#endif
