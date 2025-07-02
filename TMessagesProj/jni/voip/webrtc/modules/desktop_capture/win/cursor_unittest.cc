/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/cursor.h"

#include <memory>

#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "modules/desktop_capture/win/cursor_unittest_resources.h"
#include "modules/desktop_capture/win/scoped_gdi_object.h"
#include "test/gmock.h"

namespace webrtc {

namespace {

// Loads `left` from resources, converts it to a `MouseCursor` instance and
// compares pixels with `right`. Returns true of MouseCursor bits match `right`.
// `right` must be a 32bpp cursor with alpha channel.
bool ConvertToMouseShapeAndCompare(unsigned left, unsigned right) {
  HMODULE instance = GetModuleHandle(NULL);

  // Load `left` from the EXE module's resources.
  win::ScopedCursor cursor(reinterpret_cast<HCURSOR>(
      LoadImage(instance, MAKEINTRESOURCE(left), IMAGE_CURSOR, 0, 0, 0)));
  EXPECT_TRUE(cursor != NULL);

  // Convert `cursor` to `mouse_shape`.
  HDC dc = GetDC(NULL);
  std::unique_ptr<MouseCursor> mouse_shape(
      CreateMouseCursorFromHCursor(dc, cursor));
  ReleaseDC(NULL, dc);

  EXPECT_TRUE(mouse_shape.get());

  // Load `right`.
  cursor.Set(reinterpret_cast<HCURSOR>(
      LoadImage(instance, MAKEINTRESOURCE(right), IMAGE_CURSOR, 0, 0, 0)));

  ICONINFO iinfo;
  EXPECT_TRUE(GetIconInfo(cursor, &iinfo));
  EXPECT_TRUE(iinfo.hbmColor);

  // Make sure the bitmaps will be freed.
  win::ScopedBitmap scoped_mask(iinfo.hbmMask);
  win::ScopedBitmap scoped_color(iinfo.hbmColor);

  // Get `scoped_color` dimensions.
  BITMAP bitmap_info;
  EXPECT_TRUE(GetObject(scoped_color, sizeof(bitmap_info), &bitmap_info));

  int width = bitmap_info.bmWidth;
  int height = bitmap_info.bmHeight;
  EXPECT_TRUE(DesktopSize(width, height).equals(mouse_shape->image()->size()));

  // Get the pixels from `scoped_color`.
  int size = width * height;
  std::unique_ptr<uint32_t[]> data(new uint32_t[size]);
  EXPECT_TRUE(GetBitmapBits(scoped_color, size * sizeof(uint32_t), data.get()));

  // Compare the 32bpp image in `mouse_shape` with the one loaded from `right`.
  return memcmp(data.get(), mouse_shape->image()->data(),
                size * sizeof(uint32_t)) == 0;
}

}  // namespace

TEST(MouseCursorTest, MatchCursors) {
  EXPECT_TRUE(
      ConvertToMouseShapeAndCompare(IDD_CURSOR1_24BPP, IDD_CURSOR1_32BPP));

  EXPECT_TRUE(
      ConvertToMouseShapeAndCompare(IDD_CURSOR1_8BPP, IDD_CURSOR1_32BPP));

  EXPECT_TRUE(
      ConvertToMouseShapeAndCompare(IDD_CURSOR2_1BPP, IDD_CURSOR2_32BPP));

  EXPECT_TRUE(
      ConvertToMouseShapeAndCompare(IDD_CURSOR3_4BPP, IDD_CURSOR3_32BPP));
}

}  // namespace webrtc
