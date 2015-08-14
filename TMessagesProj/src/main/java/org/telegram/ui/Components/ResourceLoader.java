/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ImageListActivity;

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

    public static Drawable backgroundWhite;

    public static Drawable geoInDrawable;
    public static Drawable geoOutDrawable;

    public static Drawable[][] audioStatesDrawable = new Drawable[10][2];

    public static Drawable placeholderDocInDrawable;
    public static Drawable placeholderDocOutDrawable;
    public static Drawable videoIconDrawable;
    public static Drawable docMenuInDrawable;
    public static Drawable docMenuOutDrawable;
    public static Drawable[] buttonStatesDrawables = new Drawable[8];
    public static Drawable[][] buttonStatesDrawablesDoc = new Drawable[3][2];

    public static void loadRecources(Context context) {
        //if (backgroundDrawableIn == null) {
            //backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in);
            //backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_selected);
            //backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out);
            //backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_selected);
            //backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_photo);
            //backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_photo_selected);
            //backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_photo);
            //backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_photo_selected);
            setBubbles(context);
            checkDrawable = context.getResources().getDrawable(R.drawable.msg_check);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.msg_halfcheck);
            clockDrawable = context.getResources().getDrawable(R.drawable.msg_clock);
            checkMediaDrawable = context.getResources().getDrawable(R.drawable.msg_check_w);
            halfCheckMediaDrawable = context.getResources().getDrawable(R.drawable.msg_halfcheck_w);
            clockMediaDrawable = context.getResources().getDrawable(R.drawable.msg_clock_photo);
            errorDrawable = context.getResources().getDrawable(R.drawable.msg_warning);
            mediaBackgroundDrawable = context.getResources().getDrawable(R.drawable.phototime);
            broadcastDrawable = context.getResources().getDrawable(R.drawable.broadcast3);
            broadcastMediaDrawable = context.getResources().getDrawable(R.drawable.broadcast4);
            backgroundBlack = context.getResources().getDrawable(R.drawable.system_black);
            backgroundBlue = context.getResources().getDrawable(R.drawable.system_blue);

            backgroundWhite = context.getResources().getDrawable(R.drawable.system_white);

            audioStatesDrawable[0][0] = context.getResources().getDrawable(R.drawable.play_w2);
            audioStatesDrawable[0][1] = context.getResources().getDrawable(R.drawable.play_w2_pressed);
            audioStatesDrawable[1][0] = context.getResources().getDrawable(R.drawable.pause_w2);
            audioStatesDrawable[1][1] = context.getResources().getDrawable(R.drawable.pause_w2_pressed);
            audioStatesDrawable[2][0] = context.getResources().getDrawable(R.drawable.download_g);
            audioStatesDrawable[2][1] = context.getResources().getDrawable(R.drawable.download_g_pressed);
            audioStatesDrawable[3][0] = context.getResources().getDrawable(R.drawable.pause_g);
            audioStatesDrawable[3][1] = context.getResources().getDrawable(R.drawable.pause_g_pressed);
            audioStatesDrawable[4][0] = context.getResources().getDrawable(R.drawable.cancel_g);
            audioStatesDrawable[4][1] = context.getResources().getDrawable(R.drawable.cancel_g_pressed);
            audioStatesDrawable[5][0] = context.getResources().getDrawable(R.drawable.play_w);
            audioStatesDrawable[5][1] = context.getResources().getDrawable(R.drawable.play_w_pressed);
            audioStatesDrawable[6][0] = context.getResources().getDrawable(R.drawable.pause_w);
            audioStatesDrawable[6][1] = context.getResources().getDrawable(R.drawable.pause_w_pressed);
            audioStatesDrawable[7][0] = context.getResources().getDrawable(R.drawable.download_b);
            audioStatesDrawable[7][1] = context.getResources().getDrawable(R.drawable.download_b_pressed);
            audioStatesDrawable[8][0] = context.getResources().getDrawable(R.drawable.pause_b);
            audioStatesDrawable[8][1] = context.getResources().getDrawable(R.drawable.pause_b_pressed);
            audioStatesDrawable[9][0] = context.getResources().getDrawable(R.drawable.cancel_b);
            audioStatesDrawable[9][1] = context.getResources().getDrawable(R.drawable.cancel_b_pressed);

            placeholderDocInDrawable = context.getResources().getDrawable(R.drawable.doc_blue);
            placeholderDocOutDrawable = context.getResources().getDrawable(R.drawable.doc_green);
            buttonStatesDrawables[0] = context.getResources().getDrawable(R.drawable.photoload);
            buttonStatesDrawables[1] = context.getResources().getDrawable(R.drawable.photocancel);
            buttonStatesDrawables[2] = context.getResources().getDrawable(R.drawable.photogif);
            buttonStatesDrawables[3] = context.getResources().getDrawable(R.drawable.playvideo);
            buttonStatesDrawables[4] = context.getResources().getDrawable(R.drawable.photopause);
            buttonStatesDrawables[5] = context.getResources().getDrawable(R.drawable.burn);
            buttonStatesDrawables[6] = context.getResources().getDrawable(R.drawable.circle);
            buttonStatesDrawables[7] = context.getResources().getDrawable(R.drawable.photocheck);
            buttonStatesDrawablesDoc[0][0] = context.getResources().getDrawable(R.drawable.docload_b);
            buttonStatesDrawablesDoc[1][0] = context.getResources().getDrawable(R.drawable.doccancel_b);
            buttonStatesDrawablesDoc[2][0] = context.getResources().getDrawable(R.drawable.docpause_b);
            buttonStatesDrawablesDoc[0][1] = context.getResources().getDrawable(R.drawable.docload_g);
            buttonStatesDrawablesDoc[1][1] = context.getResources().getDrawable(R.drawable.doccancel_g);
            buttonStatesDrawablesDoc[2][1] = context.getResources().getDrawable(R.drawable.docpause_g);
            videoIconDrawable = context.getResources().getDrawable(R.drawable.ic_video);
            docMenuInDrawable = context.getResources().getDrawable(R.drawable.doc_actions_b);
            docMenuOutDrawable = context.getResources().getDrawable(R.drawable.doc_actions_g);

            geoInDrawable = context.getResources().getDrawable(R.drawable.location_b);
            geoOutDrawable = context.getResources().getDrawable(R.drawable.location_g);

       // }
    }

    private static void setBubbles(Context context){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        String bubble = themePrefs.getString("chatBubbleStyle", ImageListActivity.getBubbleName(0));
        if(bubble.equals(ImageListActivity.getBubbleName(0))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(1))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_2);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_2_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_2);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_2_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_2_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_2_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_2_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_2_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(2))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_3);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_3_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_3);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_3_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_3_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_3_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_3_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_3_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(3))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_4);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_4_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_4);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_4_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_4_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_4_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_4_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_4_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(4))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_5);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_5_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_5);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_5_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_5_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_5_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_5_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_5_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(5))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_6);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_6_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_6);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_6_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_6_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_6_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_6_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_6_photo_selected);
        } else if(bubble.equals(ImageListActivity.getBubbleName(6))){
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_7);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_7_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_7);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_7_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_7_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_7_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_7_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_7_photo_selected);
        } else{
            backgroundDrawableIn = context.getResources().getDrawable(R.drawable.msg_in);
            backgroundDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_selected);
            backgroundDrawableOut = context.getResources().getDrawable(R.drawable.msg_out);
            backgroundDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_selected);
            backgroundMediaDrawableIn = context.getResources().getDrawable(R.drawable.msg_in_photo);
            backgroundMediaDrawableInSelected = context.getResources().getDrawable(R.drawable.msg_in_photo_selected);
            backgroundMediaDrawableOut = context.getResources().getDrawable(R.drawable.msg_out_photo);
            backgroundMediaDrawableOutSelected = context.getResources().getDrawable(R.drawable.msg_out_photo_selected);
        }
    }
}
