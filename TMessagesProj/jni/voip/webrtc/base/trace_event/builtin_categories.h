// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_BUILTIN_CATEGORIES_H_
#define BASE_TRACE_EVENT_BUILTIN_CATEGORIES_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/stl_util.h"
#include "base/trace_event/common/trace_event_common.h"
#include "build/build_config.h"

// List of builtin category names. If you want to use a new category name in
// your code and you get a static assert, this is the right place to register
// the name. If the name is going to be used only for testing, please add it to
// |kIgnoredCategoriesForTesting| instead.
//
// Prefer to use '_' to separate word of category name, like content_capture.
//
// Parameter |X| must be a *macro* that takes a single |name| string argument,
// denoting a category name.
#define INTERNAL_TRACE_LIST_BUILTIN_CATEGORIES(X)                        \
  /* These entries must go first to be consistent with the               \
   * CategoryRegistry::kCategory* consts.*/                              \
  X("tracing categories exhausted; must increase kMaxCategories")        \
  X("tracing already shutdown")                                          \
  X("__metadata")                                                        \
  /* The rest of the list is in alphabetical order */                    \
  X("accessibility")                                                     \
  X("AccountFetcherService")                                             \
  X("android_webview")                                                   \
  X("audio")                                                             \
  X("base")                                                              \
  X("benchmark")                                                         \
  X("blink")                                                             \
  X("blink.bindings")                                                    \
  X("blink.animations")                                                  \
  X("blink.console")                                                     \
  X("blink_gc")                                                          \
  X("blink.net")                                                         \
  X("blink_style")                                                       \
  X("blink.user_timing")                                                 \
  X("blink.worker")                                                      \
  X("Blob")                                                              \
  X("browser")                                                           \
  X("browsing_data")                                                     \
  X("CacheStorage")                                                      \
  X("camera")                                                            \
  X("cast_perf_test")                                                    \
  X("cast.stream")                                                       \
  X("cc")                                                                \
  X("cc.debug")                                                          \
  X("cdp.perf")                                                          \
  X("chromeos")                                                          \
  X("cma")                                                               \
  X("compositor")                                                        \
  X("content")                                                           \
  X("content_capture")                                                   \
  X("devtools")                                                          \
  X("devtools.timeline")                                                 \
  X("devtools.timeline.async")                                           \
  X("disk_cache")                                                        \
  X("download")                                                          \
  X("download_service")                                                  \
  X("drm")                                                               \
  X("drmcursor")                                                         \
  X("dwrite")                                                            \
  X("DXVA Decoding")                                                     \
  X("EarlyJava")                                                         \
  X("evdev")                                                             \
  X("event")                                                             \
  X("exo")                                                               \
  X("explore_sites")                                                     \
  X("FileSystem")                                                        \
  X("file_system_provider")                                              \
  X("fonts")                                                             \
  X("GAMEPAD")                                                           \
  X("gpu")                                                               \
  X("gpu.capture")                                                       \
  X("headless")                                                          \
  X("hwoverlays")                                                        \
  X("identity")                                                          \
  X("IndexedDB")                                                         \
  X("input")                                                             \
  X("io")                                                                \
  X("ipc")                                                               \
  X("Java")                                                              \
  X("jni")                                                               \
  X("jpeg")                                                              \
  X("latency")                                                           \
  X("latencyInfo")                                                       \
  X("leveldb")                                                           \
  X("loading")                                                           \
  X("log")                                                               \
  X("login")                                                             \
  X("media")                                                             \
  X("media_router")                                                      \
  X("memory")                                                            \
  X("midi")                                                              \
  X("mojom")                                                             \
  X("mus")                                                               \
  X("native")                                                            \
  X("navigation")                                                        \
  X("net")                                                               \
  X("netlog")                                                            \
  X("offline_pages")                                                     \
  X("omnibox")                                                           \
  X("oobe")                                                              \
  X("ozone")                                                             \
  X("passwords")                                                         \
  X("p2p")                                                               \
  X("page-serialization")                                                \
  X("pepper")                                                            \
  X("ppapi")                                                             \
  X("ppapi proxy")                                                       \
  X("rail")                                                              \
  X("renderer")                                                          \
  X("renderer_host")                                                     \
  X("renderer.scheduler")                                                \
  X("RLZ")                                                               \
  X("safe_browsing")                                                     \
  X("screenlock_monitor")                                                \
  X("sequence_manager")                                                  \
  X("service_manager")                                                   \
  X("ServiceWorker")                                                     \
  X("sharing")                                                           \
  X("shell")                                                             \
  X("shortcut_viewer")                                                   \
  X("shutdown")                                                          \
  X("SiteEngagement")                                                    \
  X("skia")                                                              \
  X("sql")                                                               \
  X("startup")                                                           \
  X("sync")                                                              \
  X("sync_lock_contention")                                              \
  X("thread_pool")                                                       \
  X("test_gpu")                                                          \
  X("test_tracing")                                                      \
  X("toplevel")                                                          \
  X("ui")                                                                \
  X("v8")                                                                \
  X("v8.execute")                                                        \
  X("ValueStoreFrontend::Backend")                                       \
  X("views")                                                             \
  X("views.frame")                                                       \
  X("viz")                                                               \
  X("vk")                                                                \
  X("wayland")                                                           \
  X("webaudio")                                                          \
  X("weblayer")                                                          \
  X("WebCore")                                                           \
  X("webrtc")                                                            \
  X("xr")                                                                \
  X(TRACE_DISABLED_BY_DEFAULT("animation-worklet"))                      \
  X(TRACE_DISABLED_BY_DEFAULT("audio-worklet"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("blink.debug"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("blink.debug.display_lock"))               \
  X(TRACE_DISABLED_BY_DEFAULT("blink.debug.layout"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("blink.debug.layout.trees"))               \
  X(TRACE_DISABLED_BY_DEFAULT("blink.feature_usage"))                    \
  X(TRACE_DISABLED_BY_DEFAULT("blink_gc"))                               \
  X(TRACE_DISABLED_BY_DEFAULT("blink.image_decoding"))                   \
  X(TRACE_DISABLED_BY_DEFAULT("blink.invalidation"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("cc"))                                     \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug"))                               \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.cdp-perf"))                      \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.display_items"))                 \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.picture"))                       \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.scheduler"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.scheduler.frames"))              \
  X(TRACE_DISABLED_BY_DEFAULT("cc.debug.scheduler.now"))                 \
  X(TRACE_DISABLED_BY_DEFAULT("cpu_profiler"))                           \
  X(TRACE_DISABLED_BY_DEFAULT("cpu_profiler.debug"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.screenshot"))                    \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline"))                      \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline.frame"))                \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline.inputs"))               \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline.invalidationTracking")) \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline.layers"))               \
  X(TRACE_DISABLED_BY_DEFAULT("devtools.timeline.picture"))              \
  X(TRACE_DISABLED_BY_DEFAULT("file"))                                   \
  X(TRACE_DISABLED_BY_DEFAULT("fonts"))                                  \
  X(TRACE_DISABLED_BY_DEFAULT("gpu_cmd_queue"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("gpu.dawn"))                               \
  X(TRACE_DISABLED_BY_DEFAULT("gpu.debug"))                              \
  X(TRACE_DISABLED_BY_DEFAULT("gpu.decoder"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("gpu.device"))                             \
  X(TRACE_DISABLED_BY_DEFAULT("gpu.service"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("histogram_samples"))                      \
  X(TRACE_DISABLED_BY_DEFAULT("java-heap-profiler"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("layer-element"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("layout_shift.debug"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("lifecycles"))                             \
  X(TRACE_DISABLED_BY_DEFAULT("loading"))                                \
  X(TRACE_DISABLED_BY_DEFAULT("memory-infra"))                           \
  X(TRACE_DISABLED_BY_DEFAULT("memory-infra.v8.code_stats"))             \
  X(TRACE_DISABLED_BY_DEFAULT("net"))                                    \
  X(TRACE_DISABLED_BY_DEFAULT("network"))                                \
  X(TRACE_DISABLED_BY_DEFAULT("paint-worklet"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("power"))                                  \
  X(TRACE_DISABLED_BY_DEFAULT("renderer.scheduler"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("renderer.scheduler.debug"))               \
  X(TRACE_DISABLED_BY_DEFAULT("sequence_manager"))                       \
  X(TRACE_DISABLED_BY_DEFAULT("sequence_manager.debug"))                 \
  X(TRACE_DISABLED_BY_DEFAULT("sequence_manager.verbose_snapshots"))     \
  X(TRACE_DISABLED_BY_DEFAULT("skia"))                                   \
  X(TRACE_DISABLED_BY_DEFAULT("skia.gpu"))                               \
  X(TRACE_DISABLED_BY_DEFAULT("skia.gpu.cache"))                         \
  X(TRACE_DISABLED_BY_DEFAULT("SyncFileSystem"))                         \
  X(TRACE_DISABLED_BY_DEFAULT("system_stats"))                           \
  X(TRACE_DISABLED_BY_DEFAULT("thread_pool_diagnostics"))                \
  X(TRACE_DISABLED_BY_DEFAULT("toplevel.flow"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("toplevel.ipc"))                           \
  X(TRACE_DISABLED_BY_DEFAULT("user_action_samples"))                    \
  X(TRACE_DISABLED_BY_DEFAULT("v8.compile"))                             \
  X(TRACE_DISABLED_BY_DEFAULT("v8.cpu_profiler"))                        \
  X(TRACE_DISABLED_BY_DEFAULT("v8.cpu_profiler.hires"))                  \
  X(TRACE_DISABLED_BY_DEFAULT("v8.gc"))                                  \
  X(TRACE_DISABLED_BY_DEFAULT("v8.gc_stats"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("v8.ic_stats"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("v8.runtime"))                             \
  X(TRACE_DISABLED_BY_DEFAULT("v8.runtime_stats"))                       \
  X(TRACE_DISABLED_BY_DEFAULT("v8.runtime_stats_sampling"))              \
  X(TRACE_DISABLED_BY_DEFAULT("v8.turbofan"))                            \
  X(TRACE_DISABLED_BY_DEFAULT("v8.wasm"))                                \
  X(TRACE_DISABLED_BY_DEFAULT("video_and_image_capture"))                \
  X(TRACE_DISABLED_BY_DEFAULT("viz.debug.overlay_planes"))               \
  X(TRACE_DISABLED_BY_DEFAULT("viz.hit_testing_flow"))                   \
  X(TRACE_DISABLED_BY_DEFAULT("viz.overdraw"))                           \
  X(TRACE_DISABLED_BY_DEFAULT("viz.quads"))                              \
  X(TRACE_DISABLED_BY_DEFAULT("viz.surface_id_flow"))                    \
  X(TRACE_DISABLED_BY_DEFAULT("viz.surface_lifetime"))                   \
  X(TRACE_DISABLED_BY_DEFAULT("viz.triangles"))                          \
  X(TRACE_DISABLED_BY_DEFAULT("webaudio.audionode"))                     \
  X(TRACE_DISABLED_BY_DEFAULT("worker.scheduler"))

#define INTERNAL_TRACE_INIT_CATEGORY_NAME(name) name,

#define INTERNAL_TRACE_INIT_CATEGORY(name) {0, 0, name},

namespace base {
namespace trace_event {

// Constexpr version of string comparison operator. |a| and |b| must be valid
// C-style strings known at compile-time.
constexpr bool StrEqConstexpr(const char* a, const char* b) {
  for (; *a != '\0' && *b != '\0'; ++a, ++b) {
    if (*a != *b)
      return false;
  }
  return *a == *b;
}

// Tests for |StrEqConstexpr()|.
static_assert(StrEqConstexpr("foo", "foo"), "strings should be equal");
static_assert(!StrEqConstexpr("foo", "Foo"), "strings should not be equal");
static_assert(!StrEqConstexpr("foo", "foo1"), "strings should not be equal");
static_assert(!StrEqConstexpr("foo2", "foo"), "strings should not be equal");
static_assert(StrEqConstexpr("", ""), "strings should be equal");
static_assert(!StrEqConstexpr("foo", ""), "strings should not be equal");
static_assert(!StrEqConstexpr("", "foo"), "strings should not be equal");
static_assert(!StrEqConstexpr("ab", "abc"), "strings should not be equal");
static_assert(!StrEqConstexpr("abc", "ab"), "strings should not be equal");

// Static-only class providing access to the compile-time registry of trace
// categories.
class BASE_EXPORT BuiltinCategories {
 public:
  // Returns a built-in category name at |index| in the registry.
  static constexpr const char* At(size_t index) {
    return kBuiltinCategories[index];
  }

  // Returns the amount of built-in categories in the registry.
  static constexpr size_t Size() { return base::size(kBuiltinCategories); }

  // Where in the builtin category list to start when populating the
  // about://tracing UI.
  static constexpr size_t kVisibleCategoryStart = 3;

  // Returns whether the category is either:
  // - Properly registered in the builtin list.
  // - Constists of several categories separated by commas.
  // - Used only in tests.
  // All trace categories are checked against this. A static_assert is triggered
  // if at least one category fails this check.
  static constexpr bool IsAllowedCategory(const char* category) {
#if defined(OS_WIN) && defined(COMPONENT_BUILD)
    return true;
#else
    return IsBuiltinCategory(category) ||
           IsCommaSeparatedCategoryGroup(category) ||
           IsCategoryForTesting(category);
#endif
  }

 private:
  // The array of built-in category names used for compile-time lookup.
  static constexpr const char* kBuiltinCategories[] = {
      INTERNAL_TRACE_LIST_BUILTIN_CATEGORIES(
          INTERNAL_TRACE_INIT_CATEGORY_NAME)};

  // The array of category names used only for testing. It's kept separately
  // from the main list to avoid allocating the space for them in the binary.
  static constexpr const char* kCategoriesForTesting[] = {
      "\001\002\003\n\r",
      "a",
      "all",
      "b",
      "b1",
      "c",
      "c0",
      "c1",
      "c2",
      "c3",
      "c4",
      "cat",
      "cat1",
      "cat2",
      "cat3",
      "cat4",
      "cat5",
      "cat6",
      "category",
      "drink",
      "excluded_cat",
      "filtered_cat",
      "foo",
      "inc",
      "inc2",
      "included",
      "inc_wildcard_",
      "inc_wildcard_abc",
      "inc_wildchar_bla_end",
      "inc_wildchar_x_end",
      "kTestCategory",
      "log",
      "noise",
      "other_included",
      "test",
      "test_category",
      "Testing",
      "TraceEventAgentTestCategory",
      "unfiltered_cat",
      "whitewashed",
      "x",
      TRACE_DISABLED_BY_DEFAULT("c9"),
      TRACE_DISABLED_BY_DEFAULT("cat"),
      TRACE_DISABLED_BY_DEFAULT("filtered_cat"),
      TRACE_DISABLED_BY_DEFAULT("NotTesting"),
      TRACE_DISABLED_BY_DEFAULT("Testing"),
      TRACE_DISABLED_BY_DEFAULT("unfiltered_cat")};

  // Returns whether |str| is in |array| of |array_len|.
  static constexpr bool IsStringInArray(const char* str,
                                        const char* const array[],
                                        size_t array_len) {
    for (size_t i = 0; i < array_len; ++i) {
      if (StrEqConstexpr(str, array[i]))
        return true;
    }
    return false;
  }

  // Returns whether |category_group| contains a ',' symbol, denoting that an
  // event belongs to several categories. We don't add such strings in the
  // builtin list but allow them to pass the static assert.
  static constexpr bool IsCommaSeparatedCategoryGroup(
      const char* category_group) {
    for (; *category_group != '\0'; ++category_group) {
      if (*category_group == ',')
        return true;
    }
    return false;
  }

  // Returns whether |category| is used only for testing.
  static constexpr bool IsCategoryForTesting(const char* category) {
    return IsStringInArray(category, kCategoriesForTesting,
                           base::size(kCategoriesForTesting));
  }

  // Returns whether |category| is registered in the builtin list.
  static constexpr bool IsBuiltinCategory(const char* category) {
    return IsStringInArray(category, kBuiltinCategories,
                           base::size(kBuiltinCategories));
  }

  DISALLOW_IMPLICIT_CONSTRUCTORS(BuiltinCategories);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_BUILTIN_CATEGORIES_H_
