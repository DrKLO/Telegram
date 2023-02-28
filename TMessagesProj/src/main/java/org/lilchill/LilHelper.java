package org.lilchill;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.util.Log;

import org.lilchill.lilsettings.LilSettingsActivity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.LaunchActivity;


public class LilHelper {

    private boolean supportsNativeBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LaunchActivity.systemBlurEnabled;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public static void chatBlur(SizeNotifierFrameLayout content, View scrimView, Context context, boolean enable) {
        final long s = System.currentTimeMillis();
        final int contentWidth = content.getWidth();
        final int contentHeight = content.getHeight();
        int[] location = new int[2];
        scrimView.getLocationInWindow(location);
        final int sX = location[0];
        final int sY = location[1];
        final int sW = scrimView.getWidth();
        final int sH = scrimView.getHeight();
        Rect rect = new Rect();
        scrimView.getGlobalVisibleRect(rect);
        final int sL = rect.left;
        final int sT = rect.top;
        final int sR = rect.right;
        final int sB = rect.bottom;
        Bitmap contentBitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888);
        Canvas contentCanvas = new Canvas(contentBitmap);
        //contentCanvas.clipOutRect(rect);
        content.draw(contentCanvas);
        final float bi = context.getSharedPreferences(LilSettingsActivity.ls, 0).getFloat(LilSettingsActivity.blurAlpha, 20F);
        RenderEffect bitmapEffect = RenderEffect.createBitmapEffect(contentBitmap);
        ValueAnimator va = ValueAnimator.ofFloat(enable ? 0.1f : bi, enable ? bi : 0.1f);
        va.setDuration(enable ? 220 : 320);
        va.addUpdateListener(animation -> {
            if (enable){
                content.setRenderEffect(RenderEffect.createBlurEffect((float) animation.getAnimatedValue(), (float)animation.getAnimatedValue(), bitmapEffect,  Shader.TileMode.MIRROR));
            } else {
                if ((float) animation.getAnimatedValue() <= 0.2F){
                    content.setRenderEffect(null);
                } else {
                    content.setRenderEffect(RenderEffect.createBlurEffect((float) animation.getAnimatedValue(), (float)animation.getAnimatedValue(), bitmapEffect, Shader.TileMode.MIRROR));
                }
            }
        });
        va.start();
        final long e = System.currentTimeMillis();
        Log.d("LILCHILL", (enable ? "Enabled" : "Disabled") + "\nTime to execute whole function: " + (e - s) + " milliseconds." + "\nAnimation time = " + (enable? 220 : 320) + "milliseconds.");
    }
    @RequiresApi(api = Build.VERSION_CODES.S)
    public static void blurAlert(View v, float blurAlpha, boolean enable){
        ValueAnimator va;
        if (enable){
            va = ValueAnimator.ofFloat(0.1f, blurAlpha);
            va.setDuration(270);
            va.addUpdateListener(a -> v.setRenderEffect(RenderEffect.createBlurEffect((float) a.getAnimatedValue(), (float) a.getAnimatedValue(), Shader.TileMode.MIRROR)));
        } else {
            va = ValueAnimator.ofFloat(blurAlpha, 0.1f);
            va.setDuration(270);
            va.addUpdateListener(a -> {
                if ((float) a.getAnimatedValue() == 0.1f){
                    v.setRenderEffect(null);
                } else {
                    v.setRenderEffect(RenderEffect.createBlurEffect((float) a.getAnimatedValue(), (float) a.getAnimatedValue(), Shader.TileMode.MIRROR));
                }
            });
        }
        va.start();
    }
    private static GradientDrawable createDrawable(int radius, boolean stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(radius));
        if (stroke) {
            d.setStroke(3, Theme.getColor(Theme.key_dialogShadowLine));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            d.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        }
        return d;
    }

    public static GradientDrawable getDrawable(int radius, boolean stroke) {
        return createDrawable(radius, stroke);
    }

    private static GradientDrawable createDrawable(int radius, boolean stroke, String bgColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(radius));
        if (stroke) {
            d.setStroke(3, Theme.getColor(Theme.key_dialogShadowLine));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            d.setColor(ColorStateList.valueOf(Theme.getColor(bgColor)));
        }
        return d;
    }

    public static GradientDrawable getDrawable(int radius, boolean stroke, String bgColor) {
        return createDrawable(radius, stroke, bgColor);
    }
}