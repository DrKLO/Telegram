/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/wayland/egl_dmabuf.h"

#include <asm/ioctl.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <libdrm/drm_fourcc.h>
#include <linux/types.h>
#include <spa/param/video/format-utils.h>
#include <unistd.h>
#include <xf86drm.h>

#include "absl/memory/memory.h"
#include "absl/types/optional.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/sanitizer.h"
#include "rtc_base/string_encode.h"

namespace webrtc {

// EGL
typedef EGLBoolean (*eglBindAPI_func)(EGLenum api);
typedef EGLContext (*eglCreateContext_func)(EGLDisplay dpy,
                                            EGLConfig config,
                                            EGLContext share_context,
                                            const EGLint* attrib_list);
typedef EGLBoolean (*eglDestroyContext_func)(EGLDisplay display,
                                             EGLContext context);
typedef EGLBoolean (*eglTerminate_func)(EGLDisplay display);
typedef EGLImageKHR (*eglCreateImageKHR_func)(EGLDisplay dpy,
                                              EGLContext ctx,
                                              EGLenum target,
                                              EGLClientBuffer buffer,
                                              const EGLint* attrib_list);
typedef EGLBoolean (*eglDestroyImageKHR_func)(EGLDisplay dpy,
                                              EGLImageKHR image);
typedef EGLint (*eglGetError_func)(void);
typedef void* (*eglGetProcAddress_func)(const char*);
typedef EGLDisplay (*eglGetPlatformDisplayEXT_func)(EGLenum platform,
                                                    void* native_display,
                                                    const EGLint* attrib_list);
typedef EGLDisplay (*eglGetPlatformDisplay_func)(EGLenum platform,
                                                 void* native_display,
                                                 const EGLAttrib* attrib_list);

typedef EGLBoolean (*eglInitialize_func)(EGLDisplay dpy,
                                         EGLint* major,
                                         EGLint* minor);
typedef EGLBoolean (*eglMakeCurrent_func)(EGLDisplay dpy,
                                          EGLSurface draw,
                                          EGLSurface read,
                                          EGLContext ctx);
typedef EGLBoolean (*eglQueryDmaBufFormatsEXT_func)(EGLDisplay dpy,
                                                    EGLint max_formats,
                                                    EGLint* formats,
                                                    EGLint* num_formats);
typedef EGLBoolean (*eglQueryDmaBufModifiersEXT_func)(EGLDisplay dpy,
                                                      EGLint format,
                                                      EGLint max_modifiers,
                                                      EGLuint64KHR* modifiers,
                                                      EGLBoolean* external_only,
                                                      EGLint* num_modifiers);
typedef const char* (*eglQueryString_func)(EGLDisplay dpy, EGLint name);
typedef void (*glEGLImageTargetTexture2DOES_func)(GLenum target,
                                                  GLeglImageOES image);

// This doesn't follow naming conventions in WebRTC, where the naming
// should look like e.g. egl_bind_api instead of EglBindAPI, however
// we named them according to the exported functions they map to for
// consistency.
eglBindAPI_func EglBindAPI = nullptr;
eglCreateContext_func EglCreateContext = nullptr;
eglDestroyContext_func EglDestroyContext = nullptr;
eglTerminate_func EglTerminate = nullptr;
eglCreateImageKHR_func EglCreateImageKHR = nullptr;
eglDestroyImageKHR_func EglDestroyImageKHR = nullptr;
eglGetError_func EglGetError = nullptr;
eglGetProcAddress_func EglGetProcAddress = nullptr;
eglGetPlatformDisplayEXT_func EglGetPlatformDisplayEXT = nullptr;
eglGetPlatformDisplay_func EglGetPlatformDisplay = nullptr;
eglInitialize_func EglInitialize = nullptr;
eglMakeCurrent_func EglMakeCurrent = nullptr;
eglQueryDmaBufFormatsEXT_func EglQueryDmaBufFormatsEXT = nullptr;
eglQueryDmaBufModifiersEXT_func EglQueryDmaBufModifiersEXT = nullptr;
eglQueryString_func EglQueryString = nullptr;
glEGLImageTargetTexture2DOES_func GlEGLImageTargetTexture2DOES = nullptr;

// GL
typedef void (*glBindTexture_func)(GLenum target, GLuint texture);
typedef void (*glDeleteTextures_func)(GLsizei n, const GLuint* textures);
typedef void (*glGenTextures_func)(GLsizei n, GLuint* textures);
typedef GLenum (*glGetError_func)(void);
typedef const GLubyte* (*glGetString_func)(GLenum name);
typedef void (*glReadPixels_func)(GLint x,
                                  GLint y,
                                  GLsizei width,
                                  GLsizei height,
                                  GLenum format,
                                  GLenum type,
                                  void* data);
typedef void (*glGenFramebuffers_func)(GLsizei n, GLuint* ids);
typedef void (*glDeleteFramebuffers_func)(GLsizei n,
                                          const GLuint* framebuffers);
typedef void (*glBindFramebuffer_func)(GLenum target, GLuint framebuffer);
typedef void (*glFramebufferTexture2D_func)(GLenum target,
                                            GLenum attachment,
                                            GLenum textarget,
                                            GLuint texture,
                                            GLint level);
typedef GLenum (*glCheckFramebufferStatus_func)(GLenum target);
typedef void (*glTexParameteri_func)(GLenum target, GLenum pname, GLint param);
typedef void* (*glXGetProcAddressARB_func)(const char*);

// This doesn't follow naming conventions in WebRTC, where the naming
// should look like e.g. egl_bind_api instead of EglBindAPI, however
// we named them according to the exported functions they map to for
// consistency.
glBindTexture_func GlBindTexture = nullptr;
glDeleteTextures_func GlDeleteTextures = nullptr;
glGenTextures_func GlGenTextures = nullptr;
glGetError_func GlGetError = nullptr;
glGetString_func GlGetString = nullptr;
glReadPixels_func GlReadPixels = nullptr;
glGenFramebuffers_func GlGenFramebuffers = nullptr;
glDeleteFramebuffers_func GlDeleteFramebuffers = nullptr;
glBindFramebuffer_func GlBindFramebuffer = nullptr;
glFramebufferTexture2D_func GlFramebufferTexture2D = nullptr;
glCheckFramebufferStatus_func GlCheckFramebufferStatus = nullptr;
glTexParameteri_func GlTexParameteri = nullptr;
glXGetProcAddressARB_func GlXGetProcAddressARB = nullptr;

static const std::string FormatGLError(GLenum err) {
  switch (err) {
    case GL_NO_ERROR:
      return "GL_NO_ERROR";
    case GL_INVALID_ENUM:
      return "GL_INVALID_ENUM";
    case GL_INVALID_VALUE:
      return "GL_INVALID_VALUE";
    case GL_INVALID_OPERATION:
      return "GL_INVALID_OPERATION";
    case GL_STACK_OVERFLOW:
      return "GL_STACK_OVERFLOW";
    case GL_STACK_UNDERFLOW:
      return "GL_STACK_UNDERFLOW";
    case GL_OUT_OF_MEMORY:
      return "GL_OUT_OF_MEMORY";
    default:
      return "GL error code: " + std::to_string(err);
  }
}

static const std::string FormatEGLError(EGLint err) {
  switch (err) {
    case EGL_NOT_INITIALIZED:
      return "EGL_NOT_INITIALIZED";
    case EGL_BAD_ACCESS:
      return "EGL_BAD_ACCESS";
    case EGL_BAD_ALLOC:
      return "EGL_BAD_ALLOC";
    case EGL_BAD_ATTRIBUTE:
      return "EGL_BAD_ATTRIBUTE";
    case EGL_BAD_CONTEXT:
      return "EGL_BAD_CONTEXT";
    case EGL_BAD_CONFIG:
      return "EGL_BAD_CONFIG";
    case EGL_BAD_CURRENT_SURFACE:
      return "EGL_BAD_CURRENT_SURFACE";
    case EGL_BAD_DISPLAY:
      return "EGL_BAD_DISPLAY";
    case EGL_BAD_SURFACE:
      return "EGL_BAD_SURFACE";
    case EGL_BAD_MATCH:
      return "EGL_BAD_MATCH";
    case EGL_BAD_PARAMETER:
      return "EGL_BAD_PARAMETER";
    case EGL_BAD_NATIVE_PIXMAP:
      return "EGL_BAD_NATIVE_PIXMAP";
    case EGL_BAD_NATIVE_WINDOW:
      return "EGL_BAD_NATIVE_WINDOW";
    case EGL_CONTEXT_LOST:
      return "EGL_CONTEXT_LOST";
    default:
      return "EGL error code: " + std::to_string(err);
  }
}

static uint32_t SpaPixelFormatToDrmFormat(uint32_t spa_format) {
  switch (spa_format) {
    case SPA_VIDEO_FORMAT_RGBA:
      return DRM_FORMAT_ABGR8888;
    case SPA_VIDEO_FORMAT_RGBx:
      return DRM_FORMAT_XBGR8888;
    case SPA_VIDEO_FORMAT_BGRA:
      return DRM_FORMAT_ARGB8888;
    case SPA_VIDEO_FORMAT_BGRx:
      return DRM_FORMAT_XRGB8888;
    default:
      return DRM_FORMAT_INVALID;
  }
}

static void CloseLibrary(void* library) {
  if (library) {
    dlclose(library);
    library = nullptr;
  }
}

static void* g_lib_egl = nullptr;

RTC_NO_SANITIZE("cfi-icall")
static bool OpenEGL() {
  g_lib_egl = dlopen("libEGL.so.1", RTLD_NOW | RTLD_GLOBAL);
  if (g_lib_egl) {
    EglGetProcAddress =
        (eglGetProcAddress_func)dlsym(g_lib_egl, "eglGetProcAddress");
    return EglGetProcAddress;
  }

  return false;
}

RTC_NO_SANITIZE("cfi-icall")
static bool LoadEGL() {
  if (OpenEGL()) {
    EglBindAPI = (eglBindAPI_func)EglGetProcAddress("eglBindAPI");
    EglCreateContext =
        (eglCreateContext_func)EglGetProcAddress("eglCreateContext");
    EglDestroyContext =
        (eglDestroyContext_func)EglGetProcAddress("eglDestroyContext");
    EglTerminate = (eglTerminate_func)EglGetProcAddress("eglTerminate");
    EglCreateImageKHR =
        (eglCreateImageKHR_func)EglGetProcAddress("eglCreateImageKHR");
    EglDestroyImageKHR =
        (eglDestroyImageKHR_func)EglGetProcAddress("eglDestroyImageKHR");
    EglGetError = (eglGetError_func)EglGetProcAddress("eglGetError");
    EglGetPlatformDisplayEXT = (eglGetPlatformDisplayEXT_func)EglGetProcAddress(
        "eglGetPlatformDisplayEXT");
    EglGetPlatformDisplay =
        (eglGetPlatformDisplay_func)EglGetProcAddress("eglGetPlatformDisplay");
    EglInitialize = (eglInitialize_func)EglGetProcAddress("eglInitialize");
    EglMakeCurrent = (eglMakeCurrent_func)EglGetProcAddress("eglMakeCurrent");
    EglQueryString = (eglQueryString_func)EglGetProcAddress("eglQueryString");
    GlEGLImageTargetTexture2DOES =
        (glEGLImageTargetTexture2DOES_func)EglGetProcAddress(
            "glEGLImageTargetTexture2DOES");

    return EglBindAPI && EglCreateContext && EglCreateImageKHR &&
           EglTerminate && EglDestroyContext && EglDestroyImageKHR &&
           EglGetError && EglGetPlatformDisplayEXT && EglGetPlatformDisplay &&
           EglInitialize && EglMakeCurrent && EglQueryString &&
           GlEGLImageTargetTexture2DOES;
  }

  return false;
}

static void* g_lib_gl = nullptr;

RTC_NO_SANITIZE("cfi-icall")
static bool OpenGL() {
  std::vector<std::string> names = {"libGL.so.1", "libGL.so"};
  for (const std::string& name : names) {
    g_lib_gl = dlopen(name.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (g_lib_gl) {
      GlXGetProcAddressARB =
          (glXGetProcAddressARB_func)dlsym(g_lib_gl, "glXGetProcAddressARB");
      return GlXGetProcAddressARB;
    }
  }

  return false;
}

RTC_NO_SANITIZE("cfi-icall")
static bool LoadGL() {
  if (OpenGL()) {
    GlGetString = (glGetString_func)GlXGetProcAddressARB("glGetString");
    if (!GlGetString) {
      return false;
    }

    GlBindTexture = (glBindTexture_func)GlXGetProcAddressARB("glBindTexture");
    GlDeleteTextures =
        (glDeleteTextures_func)GlXGetProcAddressARB("glDeleteTextures");
    GlGenTextures = (glGenTextures_func)GlXGetProcAddressARB("glGenTextures");
    GlGetError = (glGetError_func)GlXGetProcAddressARB("glGetError");
    GlReadPixels = (glReadPixels_func)GlXGetProcAddressARB("glReadPixels");
    GlGenFramebuffers =
        (glGenFramebuffers_func)GlXGetProcAddressARB("glGenFramebuffers");
    GlDeleteFramebuffers =
        (glDeleteFramebuffers_func)GlXGetProcAddressARB("glDeleteFramebuffers");
    GlBindFramebuffer =
        (glBindFramebuffer_func)GlXGetProcAddressARB("glBindFramebuffer");
    GlFramebufferTexture2D = (glFramebufferTexture2D_func)GlXGetProcAddressARB(
        "glFramebufferTexture2D");
    GlCheckFramebufferStatus =
        (glCheckFramebufferStatus_func)GlXGetProcAddressARB(
            "glCheckFramebufferStatus");

    GlTexParameteri =
        (glTexParameteri_func)GlXGetProcAddressARB("glTexParameteri");

    return GlBindTexture && GlDeleteTextures && GlGenTextures && GlGetError &&
           GlReadPixels && GlGenFramebuffers && GlDeleteFramebuffers &&
           GlBindFramebuffer && GlFramebufferTexture2D &&
           GlCheckFramebufferStatus && GlTexParameteri;
  }

  return false;
}

RTC_NO_SANITIZE("cfi-icall")
EglDmaBuf::EglDmaBuf() {
  if (!LoadEGL()) {
    RTC_LOG(LS_ERROR) << "Unable to load EGL entry functions.";
    CloseLibrary(g_lib_egl);
    return;
  }

  if (!LoadGL()) {
    RTC_LOG(LS_ERROR) << "Failed to load OpenGL entry functions.";
    CloseLibrary(g_lib_gl);
    return;
  }

  if (!GetClientExtensions(EGL_NO_DISPLAY, EGL_EXTENSIONS)) {
    return;
  }

  bool has_platform_base_ext = false;
  bool has_platform_gbm_ext = false;
  bool has_khr_platform_gbm_ext = false;

  for (const auto& extension : egl_.extensions) {
    if (extension == "EGL_EXT_platform_base") {
      has_platform_base_ext = true;
      continue;
    } else if (extension == "EGL_MESA_platform_gbm") {
      has_platform_gbm_ext = true;
      continue;
    } else if (extension == "EGL_KHR_platform_gbm") {
      has_khr_platform_gbm_ext = true;
      continue;
    }
  }

  if (!has_platform_base_ext || !has_platform_gbm_ext ||
      !has_khr_platform_gbm_ext) {
    RTC_LOG(LS_ERROR) << "One of required EGL extensions is missing";
    return;
  }

  egl_.display = EglGetPlatformDisplay(EGL_PLATFORM_WAYLAND_KHR,
                                       (void*)EGL_DEFAULT_DISPLAY, nullptr);

  if (egl_.display == EGL_NO_DISPLAY) {
    RTC_LOG(LS_ERROR) << "Failed to obtain default EGL display: "
                      << FormatEGLError(EglGetError()) << "\n"
                      << "Defaulting to using first available render node";
    absl::optional<std::string> render_node = GetRenderNode();
    if (!render_node) {
      return;
    }

    drm_fd_ = open(render_node->c_str(), O_RDWR);

    if (drm_fd_ < 0) {
      RTC_LOG(LS_ERROR) << "Failed to open drm render node: "
                        << strerror(errno);
      return;
    }

    gbm_device_ = gbm_create_device(drm_fd_);

    if (!gbm_device_) {
      RTC_LOG(LS_ERROR) << "Cannot create GBM device: " << strerror(errno);
      close(drm_fd_);
      return;
    }

    // Use eglGetPlatformDisplayEXT() to get the display pointer
    // if the implementation supports it.
    egl_.display =
        EglGetPlatformDisplayEXT(EGL_PLATFORM_GBM_KHR, gbm_device_, nullptr);
  }

  if (egl_.display == EGL_NO_DISPLAY) {
    RTC_LOG(LS_ERROR) << "Error during obtaining EGL display: "
                      << FormatEGLError(EglGetError());
    return;
  }

  EGLint major, minor;
  if (EglInitialize(egl_.display, &major, &minor) == EGL_FALSE) {
    RTC_LOG(LS_ERROR) << "Error during eglInitialize: "
                      << FormatEGLError(EglGetError());
    return;
  }

  if (EglBindAPI(EGL_OPENGL_API) == EGL_FALSE) {
    RTC_LOG(LS_ERROR) << "bind OpenGL API failed";
    return;
  }

  egl_.context =
      EglCreateContext(egl_.display, nullptr, EGL_NO_CONTEXT, nullptr);

  if (egl_.context == EGL_NO_CONTEXT) {
    RTC_LOG(LS_ERROR) << "Couldn't create EGL context: "
                      << FormatGLError(EglGetError());
    return;
  }

  if (!GetClientExtensions(egl_.display, EGL_EXTENSIONS)) {
    return;
  }

  bool has_image_dma_buf_import_modifiers_ext = false;

  for (const auto& extension : egl_.extensions) {
    if (extension == "EGL_EXT_image_dma_buf_import") {
      has_image_dma_buf_import_ext_ = true;
      continue;
    } else if (extension == "EGL_EXT_image_dma_buf_import_modifiers") {
      has_image_dma_buf_import_modifiers_ext = true;
      continue;
    }
  }

  if (has_image_dma_buf_import_ext_ && has_image_dma_buf_import_modifiers_ext) {
    EglQueryDmaBufFormatsEXT = (eglQueryDmaBufFormatsEXT_func)EglGetProcAddress(
        "eglQueryDmaBufFormatsEXT");
    EglQueryDmaBufModifiersEXT =
        (eglQueryDmaBufModifiersEXT_func)EglGetProcAddress(
            "eglQueryDmaBufModifiersEXT");
  }

  RTC_LOG(LS_INFO) << "Egl initialization succeeded";
  egl_initialized_ = true;
}

RTC_NO_SANITIZE("cfi-icall")
EglDmaBuf::~EglDmaBuf() {
  if (gbm_device_) {
    gbm_device_destroy(gbm_device_);
    close(drm_fd_);
  }

  if (egl_.context != EGL_NO_CONTEXT) {
    EglDestroyContext(egl_.display, egl_.context);
  }

  if (egl_.display != EGL_NO_DISPLAY) {
    EglTerminate(egl_.display);
  }

  if (fbo_) {
    GlDeleteFramebuffers(1, &fbo_);
  }

  if (texture_) {
    GlDeleteTextures(1, &texture_);
  }

  // BUG: crbug.com/1290566
  // Closing libEGL.so.1 when using NVidia drivers causes a crash
  // when EglGetPlatformDisplayEXT() is used, at least this one is enough
  // to be called to make it crash.
  // It also looks that libepoxy and glad don't dlclose it either
  // CloseLibrary(g_lib_egl);
  // CloseLibrary(g_lib_gl);
}

RTC_NO_SANITIZE("cfi-icall")
bool EglDmaBuf::GetClientExtensions(EGLDisplay dpy, EGLint name) {
  // Get the list of client extensions
  const char* client_extensions_cstring = EglQueryString(dpy, name);
  if (!client_extensions_cstring) {
    // If eglQueryString() returned NULL, the implementation doesn't support
    // EGL_EXT_client_extensions. Expect an EGL_BAD_DISPLAY error.
    RTC_LOG(LS_ERROR) << "No client extensions defined! "
                      << FormatEGLError(EglGetError());
    return false;
  }

  std::vector<absl::string_view> client_extensions =
      rtc::split(client_extensions_cstring, ' ');
  for (const auto& extension : client_extensions) {
    egl_.extensions.push_back(std::string(extension));
  }

  return true;
}

RTC_NO_SANITIZE("cfi-icall")
bool EglDmaBuf::ImageFromDmaBuf(const DesktopSize& size,
                                uint32_t format,
                                const std::vector<PlaneData>& plane_datas,
                                uint64_t modifier,
                                const DesktopVector& offset,
                                const DesktopSize& buffer_size,
                                uint8_t* data) {
  if (!egl_initialized_) {
    return false;
  }

  if (plane_datas.size() <= 0) {
    RTC_LOG(LS_ERROR) << "Failed to process buffer: invalid number of planes";
    return false;
  }

  EGLint attribs[47];
  int atti = 0;

  attribs[atti++] = EGL_WIDTH;
  attribs[atti++] = static_cast<EGLint>(size.width());
  attribs[atti++] = EGL_HEIGHT;
  attribs[atti++] = static_cast<EGLint>(size.height());
  attribs[atti++] = EGL_LINUX_DRM_FOURCC_EXT;
  attribs[atti++] = SpaPixelFormatToDrmFormat(format);

  if (plane_datas.size() > 0) {
    attribs[atti++] = EGL_DMA_BUF_PLANE0_FD_EXT;
    attribs[atti++] = plane_datas[0].fd;
    attribs[atti++] = EGL_DMA_BUF_PLANE0_OFFSET_EXT;
    attribs[atti++] = plane_datas[0].offset;
    attribs[atti++] = EGL_DMA_BUF_PLANE0_PITCH_EXT;
    attribs[atti++] = plane_datas[0].stride;

    if (modifier != DRM_FORMAT_MOD_INVALID) {
      attribs[atti++] = EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT;
      attribs[atti++] = modifier & 0xFFFFFFFF;
      attribs[atti++] = EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT;
      attribs[atti++] = modifier >> 32;
    }
  }

  if (plane_datas.size() > 1) {
    attribs[atti++] = EGL_DMA_BUF_PLANE1_FD_EXT;
    attribs[atti++] = plane_datas[1].fd;
    attribs[atti++] = EGL_DMA_BUF_PLANE1_OFFSET_EXT;
    attribs[atti++] = plane_datas[1].offset;
    attribs[atti++] = EGL_DMA_BUF_PLANE1_PITCH_EXT;
    attribs[atti++] = plane_datas[1].stride;

    if (modifier != DRM_FORMAT_MOD_INVALID) {
      attribs[atti++] = EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT;
      attribs[atti++] = modifier & 0xFFFFFFFF;
      attribs[atti++] = EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT;
      attribs[atti++] = modifier >> 32;
    }
  }

  if (plane_datas.size() > 2) {
    attribs[atti++] = EGL_DMA_BUF_PLANE2_FD_EXT;
    attribs[atti++] = plane_datas[2].fd;
    attribs[atti++] = EGL_DMA_BUF_PLANE2_OFFSET_EXT;
    attribs[atti++] = plane_datas[2].offset;
    attribs[atti++] = EGL_DMA_BUF_PLANE2_PITCH_EXT;
    attribs[atti++] = plane_datas[2].stride;

    if (modifier != DRM_FORMAT_MOD_INVALID) {
      attribs[atti++] = EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT;
      attribs[atti++] = modifier & 0xFFFFFFFF;
      attribs[atti++] = EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT;
      attribs[atti++] = modifier >> 32;
    }
  }

  if (plane_datas.size() > 3) {
    attribs[atti++] = EGL_DMA_BUF_PLANE3_FD_EXT;
    attribs[atti++] = plane_datas[3].fd;
    attribs[atti++] = EGL_DMA_BUF_PLANE3_OFFSET_EXT;
    attribs[atti++] = plane_datas[3].offset;
    attribs[atti++] = EGL_DMA_BUF_PLANE3_PITCH_EXT;
    attribs[atti++] = plane_datas[3].stride;

    if (modifier != DRM_FORMAT_MOD_INVALID) {
      attribs[atti++] = EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT;
      attribs[atti++] = modifier & 0xFFFFFFFF;
      attribs[atti++] = EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT;
      attribs[atti++] = modifier >> 32;
    }
  }

  attribs[atti++] = EGL_NONE;

  // bind context to render thread
  EglMakeCurrent(egl_.display, EGL_NO_SURFACE, EGL_NO_SURFACE, egl_.context);

  // create EGL image from attribute list
  EGLImageKHR image = EglCreateImageKHR(
      egl_.display, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, attribs);

  if (image == EGL_NO_IMAGE) {
    RTC_LOG(LS_ERROR) << "Failed to record frame: Error creating EGLImage - "
                      << FormatEGLError(EglGetError());
    return false;
  }

  // create GL 2D texture for framebuffer
  if (!texture_) {
    GlGenTextures(1, &texture_);
    GlTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    GlTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    GlTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    GlTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  }
  GlBindTexture(GL_TEXTURE_2D, texture_);
  GlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, image);

  if (!fbo_) {
    GlGenFramebuffers(1, &fbo_);
  }

  GlBindFramebuffer(GL_FRAMEBUFFER, fbo_);
  GlFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                         texture_, 0);
  if (GlCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
    RTC_LOG(LS_ERROR) << "Failed to bind DMA buf framebuffer";
    EglDestroyImageKHR(egl_.display, image);
    return false;
  }

  GLenum gl_format = GL_BGRA;
  switch (format) {
    case SPA_VIDEO_FORMAT_RGBx:
      gl_format = GL_RGBA;
      break;
    case SPA_VIDEO_FORMAT_RGBA:
      gl_format = GL_RGBA;
      break;
    case SPA_VIDEO_FORMAT_BGRx:
      gl_format = GL_BGRA;
      break;
    default:
      gl_format = GL_BGRA;
      break;
  }

  GlReadPixels(offset.x(), offset.y(), buffer_size.width(),
               buffer_size.height(), gl_format, GL_UNSIGNED_BYTE, data);

  const GLenum error = GlGetError();
  if (error) {
    RTC_LOG(LS_ERROR) << "Failed to get image from DMA buffer.";
  }

  EglDestroyImageKHR(egl_.display, image);

  return !error;
}

RTC_NO_SANITIZE("cfi-icall")
std::vector<uint64_t> EglDmaBuf::QueryDmaBufModifiers(uint32_t format) {
  if (!egl_initialized_) {
    return {};
  }

  // Explicit modifiers not supported, return just DRM_FORMAT_MOD_INVALID as we
  // can still use modifier-less DMA-BUFs if we have required extension
  if (EglQueryDmaBufFormatsEXT == nullptr ||
      EglQueryDmaBufModifiersEXT == nullptr) {
    return has_image_dma_buf_import_ext_
               ? std::vector<uint64_t>{DRM_FORMAT_MOD_INVALID}
               : std::vector<uint64_t>{};
  }

  uint32_t drm_format = SpaPixelFormatToDrmFormat(format);
  // Should never happen as it's us who controls the list of supported formats
  RTC_DCHECK(drm_format != DRM_FORMAT_INVALID);

  EGLint count = 0;
  EGLBoolean success =
      EglQueryDmaBufFormatsEXT(egl_.display, 0, nullptr, &count);

  if (!success || !count) {
    RTC_LOG(LS_WARNING) << "Cannot query the number of formats.";
    return {DRM_FORMAT_MOD_INVALID};
  }

  std::vector<uint32_t> formats(count);
  if (!EglQueryDmaBufFormatsEXT(egl_.display, count,
                                reinterpret_cast<EGLint*>(formats.data()),
                                &count)) {
    RTC_LOG(LS_WARNING) << "Cannot query a list of formats.";
    return {DRM_FORMAT_MOD_INVALID};
  }

  if (std::find(formats.begin(), formats.end(), drm_format) == formats.end()) {
    RTC_LOG(LS_WARNING) << "Format " << drm_format
                        << " not supported for modifiers.";
    return {DRM_FORMAT_MOD_INVALID};
  }

  success = EglQueryDmaBufModifiersEXT(egl_.display, drm_format, 0, nullptr,
                                       nullptr, &count);

  if (!success || !count) {
    RTC_LOG(LS_WARNING) << "Cannot query the number of modifiers.";
    return {DRM_FORMAT_MOD_INVALID};
  }

  std::vector<uint64_t> modifiers(count);
  if (!EglQueryDmaBufModifiersEXT(egl_.display, drm_format, count,
                                  modifiers.data(), nullptr, &count)) {
    RTC_LOG(LS_WARNING) << "Cannot query a list of modifiers.";
  }

  // Support modifier-less buffers
  modifiers.push_back(DRM_FORMAT_MOD_INVALID);
  return modifiers;
}

absl::optional<std::string> EglDmaBuf::GetRenderNode() {
  int max_devices = drmGetDevices2(0, nullptr, 0);
  if (max_devices <= 0) {
    RTC_LOG(LS_ERROR) << "drmGetDevices2() has not found any devices (errno="
                      << -max_devices << ")";
    return absl::nullopt;
  }

  std::vector<drmDevicePtr> devices(max_devices);
  int ret = drmGetDevices2(0, devices.data(), max_devices);
  if (ret < 0) {
    RTC_LOG(LS_ERROR) << "drmGetDevices2() returned an error " << ret;
    return absl::nullopt;
  }

  std::string render_node;

  for (const drmDevicePtr& device : devices) {
    if (device->available_nodes & (1 << DRM_NODE_RENDER)) {
      render_node = device->nodes[DRM_NODE_RENDER];
      break;
    }
  }

  drmFreeDevices(devices.data(), ret);
  return render_node;
}

}  // namespace webrtc
