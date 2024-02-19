
#if defined(_MSC_VER) && !defined(__clang__)
#define BUILTIN_CLZ(x)  _CountLeadingZeros(x)
#define BUILTIN_CLZLL(x)  _CountLeadingZeros64(x)
#define BUILTIN_BSWAP64(x)  _byteswap_uint64(x)
#elif defined(__clang__) || defined(__GNUC__)
#define BUILTIN_CLZ(x)  __builtin_clz(x)
#define BUILTIN_CLZLL(x)  __builtin_clzll(x)
#define BUILTIN_BSWAP64(x)  __builtin_bswap64(x)
#else
#error "Unknown compiler"
#endif
