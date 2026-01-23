/*
 * This file is a part of X-Android
 * Copyright © Vyacheslav Krylov 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.vkryl.android;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;

public final class ViewUtils {
  public static void onClick (View v) {
    if (v != null) {
      v.playSoundEffect(SoundEffectConstants.CLICK);
    }
  }

  @SuppressWarnings("deprecation")
  public static void setBackground (View view, Drawable drawable) {
    if (view != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        view.setBackground(drawable);
      } else {
        view.setBackgroundDrawable(drawable);
      }
    }
  }

  public static String getActionName (MotionEvent e) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return MotionEvent.actionToString(e.getAction());
    } else {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: return "ACTION_DOWN";
        case MotionEvent.ACTION_UP: return "ACTION_UP";
        case MotionEvent.ACTION_MOVE: return "ACTION_MOVE";
        case MotionEvent.ACTION_CANCEL: return "ACTION_CANCEL";
      }
      return "ACTION:" + e.getAction();
    }
  }

  public static void runJustBeforeBeingDrawn (final View view, final Runnable runnable) {
    final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw() {
        view.getViewTreeObserver().removeOnPreDrawListener(this);
        runnable.run();
        return true;
      }
    };
    view.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
  }

  @SuppressWarnings("deprecation")
  public static void hapticVibrate (View view, boolean isForce, boolean ignoreSetting) {
    if (view != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ignoreSetting) {
        // TODO[sdk]: replacement for HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
      }
      int feedbackConstant = isForce ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP;
      int flags = ignoreSetting ? HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING : 0;
      view.performHapticFeedback(feedbackConstant, flags);
    }
  }

  public static void fixViewPager (View pager) {
    // Whenever ViewPager gets re-attached to window (e.g. inside RecyclerView while scrolling),
    // first touch event gets processed improperly
    try {
      Method method = pager.getClass().getDeclaredMethod("resetTouch");
      method.setAccessible(true);
      method.invoke(pager);
    } catch (Throwable ignored) { }
    // pager.requestLayout();
  }
}
