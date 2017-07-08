/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

public class Intro {
    public static native void on_draw_frame();
    public static native void set_scroll_offset(float a_offset);
    public static native void set_page(int page);
    public static native void set_date(float a);
    public static native void set_date0(float a);
    public static native void set_pages_textures(int a1, int a2, int a3, int a4, int a5, int a6);
    public static native void set_ic_textures(int a_ic_bubble_dot, int a_ic_bubble, int a_ic_cam_lens, int a_ic_cam, int a_ic_pencil, int a_ic_pin, int a_ic_smile_eye, int a_ic_smile, int a_ic_videocam);
    public static native void set_telegram_textures(int a_telegram_sphere, int a_telegram_plane);
    public static native void set_fast_textures(int a_fast_body, int a_fast_spiral, int a_fast_arrow, int a_fast_arrow_shadow);
    public static native void set_free_textures(int a_knot_up, int a_knot_down);
    public static native void set_powerful_textures(int a_powerful_mask, int a_powerful_star, int a_powerful_infinity, int a_powerful_infinity_white);
    public static native void set_private_textures(int a_private_door, int a_private_screw);
    public static native void on_surface_created();
    public static native void on_surface_changed(int a_width_px, int a_height_px, float a_scale_factor, int a1, int a2, int a3, int a4, int a5);
}
