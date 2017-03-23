/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.R;

public class ResourceLoader {

    public static Drawable backgroundDrawableIn;
    public static Drawable backgroundDrawableInSelected;
    public static Drawable backgroundDrawableOut;
    public static Drawable backgroundDrawableOutSelected;
    public static Drawable backgroundMediaDrawableIn;
    public static Drawable backgroundMediaDrawableInSelected;
    public static Drawable backgroundMediaDrawableOut;
    public static Drawable backgroundMediaDrawableOutSelected;
    public static Drawable checkDrawable;
    public static Drawable halfCheckDrawable;
    public static Drawable clockDrawable;
    public static Drawable broadcastDrawable;
    public static Drawable checkMediaDrawable;
    public static Drawable halfCheckMediaDrawable;
    public static Drawable clockMediaDrawable;
    public static Drawable broadcastMediaDrawable;
    public static Drawable errorDrawable;
    public static Drawable backgroundBlack;
    public static Drawable backgroundBlue;
    public static Drawable mediaBackgroundDrawable;
    public static Drawable[] clockChannelDrawable = new Drawable[2];

    public static Drawable[][] shareDrawable = new Drawable[2][2];

    public static Drawable[] viewsCountDrawable = new Drawable[2];
    public static Drawable viewsOutCountDrawable;
    public static Drawable viewsMediaCountDrawable;

    public static Drawable geoInDrawable;
    public static Drawable geoOutDrawable;

    public static Drawable[][] audioStatesDrawable = new Drawable[10][3];

    public static Drawable[] placeholderDocDrawable = new Drawable[3];
    public static Drawable videoIconDrawable;
    public static Drawable[] docMenuDrawable = new Drawable[3];
    public static Drawable[] buttonStatesDrawables = new Drawable[8];
    public static Drawable[][] buttonStatesDrawablesDoc = new Drawable[3][3];

    public static void loadRecources(Context context) {
        if (backgroundDrawableIn == null) {
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_photo_selected);
            checkDrawable = context.getResources().getDrawable(R.drawable.msg_check);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.msg_halfcheck);
            clockDrawable = context.getResources().getDrawable(R.drawable.msg_clock);
            checkMediaDrawable = context.getResources().getDrawable(R.drawable.msg_check_w);
            halfCheckMediaDrawable = context.getResources().getDrawable(R.drawable.msg_halfcheck_w);
            clockMediaDrawable = context.getResources().getDrawable(R.drawable.msg_clock_photo);
            clockChannelDrawable[0] = context.getResources().getDrawable(R.drawable.msg_clock2);
            clockChannelDrawable[1] = context.getResources().getDrawable(R.drawable.msg_clock2_s);
            errorDrawable = context.getResources().getDrawable(R.drawable.msg_warning);
            mediaBackgroundDrawable = context.getResources().getDrawable(R.drawable.phototime);
            broadcastDrawable = context.getResources().getDrawable(R.drawable.broadcast3);
            broadcastMediaDrawable = context.getResources().getDrawable(R.drawable.broadcast4);
            backgroundBlack = context.getResources().getDrawable(R.drawable.system_black);
            backgroundBlue = context.getResources().getDrawable(R.drawable.system_blue);

            viewsCountDrawable[0] = context.getResources().getDrawable(R.drawable.post_views);
            viewsCountDrawable[1] = context.getResources().getDrawable(R.drawable.post_views_s);
            viewsOutCountDrawable = context.getResources().getDrawable(R.drawable.post_viewsg);
            viewsMediaCountDrawable = context.getResources().getDrawable(R.drawable.post_views_w);

            audioStatesDrawable[0][2] = audioStatesDrawable[0][0] = context.getResources().getDrawable(R.drawable.play_w2);
            audioStatesDrawable[0][1] = context.getResources().getDrawable(R.drawable.play_w2_pressed);

            audioStatesDrawable[1][2] = audioStatesDrawable[1][0] = context.getResources().getDrawable(R.drawable.pause_w2);
            audioStatesDrawable[1][1] = context.getResources().getDrawable(R.drawable.pause_w2_pressed);

            audioStatesDrawable[2][0] = context.getResources().getDrawable(R.drawable.download_g);
            audioStatesDrawable[2][1] = context.getResources().getDrawable(R.drawable.download_g_pressed);
            audioStatesDrawable[2][2] = context.getResources().getDrawable(R.drawable.download_g_s);

            audioStatesDrawable[3][0] = context.getResources().getDrawable(R.drawable.pause_g);
            audioStatesDrawable[3][1] = context.getResources().getDrawable(R.drawable.pause_g_pressed);
            audioStatesDrawable[3][2] = context.getResources().getDrawable(R.drawable.pause_g_s);

            audioStatesDrawable[4][0] = context.getResources().getDrawable(R.drawable.cancel_g);
            audioStatesDrawable[4][1] = context.getResources().getDrawable(R.drawable.cancel_g_pressed);
            audioStatesDrawable[4][2] = context.getResources().getDrawable(R.drawable.cancel_g_s);

            audioStatesDrawable[5][2] = audioStatesDrawable[5][0] = context.getResources().getDrawable(R.drawable.play_w);
            audioStatesDrawable[5][1] = context.getResources().getDrawable(R.drawable.play_w_pressed);

            audioStatesDrawable[6][2] = audioStatesDrawable[6][0] = context.getResources().getDrawable(R.drawable.pause_w);
            audioStatesDrawable[6][1] = context.getResources().getDrawable(R.drawable.pause_w_pressed);

            audioStatesDrawable[7][0] = context.getResources().getDrawable(R.drawable.download_b);
            audioStatesDrawable[7][1] = context.getResources().getDrawable(R.drawable.download_b_pressed);
            audioStatesDrawable[7][2] = context.getResources().getDrawable(R.drawable.download_b_s);

            audioStatesDrawable[8][0] = context.getResources().getDrawable(R.drawable.pause_b);
            audioStatesDrawable[8][1] = context.getResources().getDrawable(R.drawable.pause_b_pressed);
            audioStatesDrawable[8][2] = context.getResources().getDrawable(R.drawable.pause_b_s);

            audioStatesDrawable[9][0] = context.getResources().getDrawable(R.drawable.cancel_b);
            audioStatesDrawable[9][1] = context.getResources().getDrawable(R.drawable.cancel_b_pressed);
            audioStatesDrawable[9][2] = context.getResources().getDrawable(R.drawable.cancel_b_s);

            placeholderDocDrawable[0] = context.getResources().getDrawable(R.drawable.doc_blue);
            placeholderDocDrawable[1] = context.getResources().getDrawable(R.drawable.doc_green);
            placeholderDocDrawable[2] = context.getResources().getDrawable(R.drawable.doc_blue_s);
            buttonStatesDrawables[0] = context.getResources().getDrawable(R.drawable.photoload);
            buttonStatesDrawables[1] = context.getResources().getDrawable(R.drawable.photocancel);
            buttonStatesDrawables[2] = context.getResources().getDrawable(R.drawable.photogif);
            buttonStatesDrawables[3] = context.getResources().getDrawable(R.drawable.playvideo);
            buttonStatesDrawables[4] = context.getResources().getDrawable(R.drawable.photopause);
            buttonStatesDrawables[5] = context.getResources().getDrawable(R.drawable.burn);
            buttonStatesDrawables[6] = context.getResources().getDrawable(R.drawable.circle);
            buttonStatesDrawables[7] = context.getResources().getDrawable(R.drawable.photocheck);
            buttonStatesDrawablesDoc[0][0] = context.getResources().getDrawable(R.drawable.docload_b);
            buttonStatesDrawablesDoc[0][1] = context.getResources().getDrawable(R.drawable.docload_g);
            buttonStatesDrawablesDoc[0][2] = context.getResources().getDrawable(R.drawable.docload_b_s);
            buttonStatesDrawablesDoc[1][0] = context.getResources().getDrawable(R.drawable.doccancel_b);
            buttonStatesDrawablesDoc[1][1] = context.getResources().getDrawable(R.drawable.doccancel_g);
            buttonStatesDrawablesDoc[1][2] = context.getResources().getDrawable(R.drawable.doccancel_b_s);
            buttonStatesDrawablesDoc[2][0] = context.getResources().getDrawable(R.drawable.docpause_b);
            buttonStatesDrawablesDoc[2][1] = context.getResources().getDrawable(R.drawable.docpause_g);
            buttonStatesDrawablesDoc[2][2] = context.getResources().getDrawable(R.drawable.docpause_b_s);
            videoIconDrawable = context.getResources().getDrawable(R.drawable.ic_video);
            docMenuDrawable[0] = context.getResources().getDrawable(R.drawable.doc_actions_b);
            docMenuDrawable[1] = context.getResources().getDrawable(R.drawable.doc_actions_g);
            docMenuDrawable[2] = context.getResources().getDrawable(R.drawable.doc_actions_b_s);

            shareDrawable[0][0] = context.getResources().getDrawable(R.drawable.shareblue);
            shareDrawable[0][1] = context.getResources().getDrawable(R.drawable.shareblue_pressed);
            shareDrawable[1][0] = context.getResources().getDrawable(R.drawable.shareblack);
            shareDrawable[1][1] = context.getResources().getDrawable(R.drawable.shareblack_pressed);

            geoInDrawable = context.getResources().getDrawable(R.drawable.location_b);
            geoOutDrawable = context.getResources().getDrawable(R.drawable.location_g);

            context.getResources().getDrawable(R.drawable.attach_camera_states);
            context.getResources().getDrawable(R.drawable.attach_gallery_states);
            context.getResources().getDrawable(R.drawable.attach_video_states);
            context.getResources().getDrawable(R.drawable.attach_audio_states);
            context.getResources().getDrawable(R.drawable.attach_file_states);
            context.getResources().getDrawable(R.drawable.attach_contact_states);
            context.getResources().getDrawable(R.drawable.attach_location_states);
            context.getResources().getDrawable(R.drawable.attach_hide_states);
        }
    }
}
