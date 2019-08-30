#include "vimageloader.h"
#include "config.h"
#include "vdebug.h"
#ifndef WIN32
#include <dlfcn.h>
#else
#include <Windows.h>
#endif
#include <cstring>

using lottie_image_load_f = unsigned char *(*)(const char *filename, int *x,
                                               int *y, int *comp, int req_comp);
using lottie_image_load_data_f = unsigned char *(*)(const char *data, int len,
                                                    int *x, int *y, int *comp,
                                                    int req_comp);
using lottie_image_free_f = void (*)(unsigned char *);

#ifdef __cplusplus
extern "C" {
#endif

extern unsigned char *lottie_image_load(char const *filename, int *x, int *y,
                                        int *comp, int req_comp);
extern unsigned char *lottie_image_load_from_data(const char *imageData,
                                                  int len, int *x, int *y,
                                                  int *comp, int req_comp);
extern void           lottie_image_free(unsigned char *data);

#ifdef __cplusplus
}
#endif

struct VImageLoader::Impl {
    lottie_image_load_f      imageLoad{nullptr};
    lottie_image_free_f      imageFree{nullptr};
    lottie_image_load_data_f imageFromData{nullptr};

#ifndef LOTTIE_STATIC_IMAGE_LOADER
#ifdef WIN32
    HMODULE dl_handle{nullptr};
    bool    moduleLoad()
    {
        dl_handle = LoadLibraryA("librlottie-image-loader.dll");
        return (dl_handle == nullptr);
    }
    void moduleFree()
    {
        if (dl_handle) FreeLibrary(dl_handle);
    }
    void init()
    {
        imageLoad =
            (lottie_image_load_f)GetProcAddress(dl_handle, "lottie_image_load");
        imageFree =
            (lottie_image_free_f)GetProcAddress(dl_handle, "lottie_image_free");
        imageFromData = (lottie_image_load_data_f)GetProcAddress(
            dl_handle, "lottie_image_load_from_data");
    }
#else
    void *dl_handle{nullptr};
    void  init()
    {
        imageLoad = (lottie_image_load_f)dlsym(dl_handle, "lottie_image_load");
        imageFree = (lottie_image_free_f)dlsym(dl_handle, "lottie_image_free");
        imageFromData = (lottie_image_load_data_f)dlsym(
            dl_handle, "lottie_image_load_from_data");
    }

    void moduleFree()
    {
        if (dl_handle) dlclose(dl_handle);
    }
#ifdef __APPLE__
    bool moduleLoad()
    {
        dl_handle = dlopen("librlottie-image-loader.dylib", RTLD_LAZY);
        return (dl_handle == nullptr);
    }
#else
    bool moduleLoad()
    {
        dl_handle = dlopen("librlottie-image-loader.so", RTLD_LAZY);
        return (dl_handle == nullptr);
    }
#endif
#endif
#else
    void *dl_handle{nullptr};
    void  init()
    {
        imageLoad = lottie_image_load;
        imageFree = lottie_image_free;
        imageFromData = lottie_image_load_from_data;
    }
    void moduleFree() {}
    bool moduleLoad() { return false; }
#endif

    Impl()
    {
        if (moduleLoad()) {
            vWarning << "Failed to dlopen librlottie-image-loader library";
            return;
        }

        init();

        if (!imageLoad)
            vWarning << "Failed to find symbol lottie_image_load in "
                        "librlottie-image-loader library";

        if (!imageFree)
            vWarning << "Failed to find symbol lottie_image_free in "
                        "librlottie-image-loader library";

        if (!imageFromData)
            vWarning << "Failed to find symbol lottie_image_load_data in "
                        "librlottie-image-loader library";
    }

    ~Impl() { moduleFree(); }

    VBitmap createBitmap(unsigned char *data, int width, int height,
                         int channel)
    {
        // premultiply alpha
        if (channel == 4)
            convertToBGRAPremul(data, width, height);
        else
            convertToBGRA(data, width, height);

        // create a bitmap of same size.
        VBitmap result =
            VBitmap(width, height, VBitmap::Format::ARGB32);

        // copy the data to bitmap buffer
        memcpy(result.data(), data, width * height * 4);

        // free the image data
        imageFree(data);

        return result;
    }

    VBitmap load(const char *fileName)
    {
        if (!imageLoad) return VBitmap();

        int            width, height, n;
        unsigned char *data = imageLoad(fileName, &width, &height, &n, 4);

        if (!data) {
            return VBitmap();
        }

        return createBitmap(data, width, height, n);
    }

    VBitmap load(const char *imageData, int len)
    {
        if (!imageFromData) return VBitmap();

        int            width, height, n;
        unsigned char *data =
            imageFromData(imageData, len, &width, &height, &n, 4);

        if (!data) {
            return VBitmap();
        }

        return createBitmap(data, width, height, n);
    }
    /*
     * convert from RGBA to BGRA and premultiply
     */
    void convertToBGRAPremul(unsigned char *bits, int width, int height)
    {
        int            pixelCount = width * height;
        unsigned char *pix = bits;
        for (int i = 0; i < pixelCount; i++) {
            unsigned char r = pix[0];
            unsigned char g = pix[1];
            unsigned char b = pix[2];
            unsigned char a = pix[3];

            r = (r * a) / 255;
            g = (g * a) / 255;
            b = (b * a) / 255;

            pix[0] = b;
            pix[1] = g;
            pix[2] = r;

            pix += 4;
        }
    }
    /*
     * convert from RGBA to BGRA
     */
    void convertToBGRA(unsigned char *bits, int width, int height)
    {
        int            pixelCount = width * height;
        unsigned char *pix = bits;
        for (int i = 0; i < pixelCount; i++) {
            unsigned char r = pix[0];
            unsigned char b = pix[2];
            pix[0] = b;
            pix[2] = r;
            pix += 4;
        }
    }
};

VImageLoader::VImageLoader() : mImpl(std::make_unique<VImageLoader::Impl>()) {}

VImageLoader::~VImageLoader() {}

VBitmap VImageLoader::load(const char *fileName)
{
    return mImpl->load(fileName);
}

VBitmap VImageLoader::load(const char *data, int len)
{
    return mImpl->load(data, len);
}
