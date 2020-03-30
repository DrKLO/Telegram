//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_UTILS_H
#define LIBTGVOIP_UTILS_H

#define TGVOIP_DISALLOW_COPY_AND_ASSIGN(TypeName) \
TypeName(const TypeName&) = delete;   \
void operator=(TypeName&) = delete

#define TGVOIP_MOVE_ONLY(TypeName) \
TGVOIP_DISALLOW_COPY_AND_ASSIGN(TypeName); \
TypeName(TypeName&&) = default; \
TypeName& operator=(TypeName&&) = default

#endif /* LIBTGVOIP_UTILS_H */
