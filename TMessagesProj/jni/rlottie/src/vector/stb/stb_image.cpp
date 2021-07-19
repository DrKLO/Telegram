#ifdef _WIN32
#ifdef LOT_BUILD
#ifdef DLL_EXPORT
#define LOT_EXPORT __declspec(dllexport)
#else
#define LOT_EXPORT
#endif
#else
#define LOT_EXPORT __declspec(dllimport)
#endif
#else
#ifdef __GNUC__
#if __GNUC__ >= 4
#define LOT_EXPORT __attribute__((visibility("default")))
#else
#define LOT_EXPORT
#endif
#else
#define LOT_EXPORT
#endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

/*
 * exported function wrapper from the library
 */

LOT_EXPORT unsigned char *lottie_image_load(char const *filename, int *x,
                                            int *y, int *comp, int req_comp)
{
    return nullptr;
}

LOT_EXPORT unsigned char *lottie_image_load_from_data(const char *imageData,
                                                      int len, int *x, int *y,
                                                      int *comp, int req_comp)
{
    return nullptr;
}

LOT_EXPORT void lottie_image_free(unsigned char *data)
{

}

#ifdef __cplusplus
}
#endif
