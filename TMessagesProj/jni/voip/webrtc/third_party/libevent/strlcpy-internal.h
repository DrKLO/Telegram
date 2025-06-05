#ifndef _STRLCPY_INTERNAL_H_
#define _STRLCPY_INTERNAL_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "config.h"

#ifndef HAVE_STRLCPY
#include <string.h>
size_t _event_strlcpy(char *dst, const char *src, size_t siz);
#define strlcpy _event_strlcpy
#endif

#ifdef __cplusplus
}
#endif

#endif

