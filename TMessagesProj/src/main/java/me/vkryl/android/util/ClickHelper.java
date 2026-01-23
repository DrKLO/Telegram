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
 *
 * File created on 28/11/2016
 */

package me.vkryl.android.util;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.BitwiseUtils;

public class ClickHelper {
  private static final int FLAG_CAUGHT = 0x01;
  private static final int FLAG_LONG_PRESS_SCHEDULED = 0x02;
  private static final int FLAG_IN_LONG_PRESS = 0x04;
  private static final int FLAG_AWAITING_CUSTOM_LONG_PRESS = 0x08;

  private static final int FLAG_NO_SOUND = 0x100;

  public interface Delegate {
    boolean needClickAt (View view, float x, float y);
    void onClickAt (View view, float x, float y);

    default boolean needLongPress (float x, float y) {
      return false;
    }
    // if onLongPressRequestedAt returned false,
    // parent is free to call ClickHelper.onLongPress any time later,
    // but before onLongPressCancelled is called
    default boolean onLongPressRequestedAt (View view, float x, float y) { return false; }
    default long getLongPressDuration () { return ViewConfiguration.getLongPressTimeout(); }
    default void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) { }
    default void onLongPressCancelled (View view, float x, float y) { }
    default void onLongPressFinish (View view, float x, float y) { }

    default boolean ignoreHapticFeedbackSettings (float x, float y) {
      return false;
    }
    default boolean forceEnableVibration () {
      return false;
    }

    default void onClickTouchDown (View view, float x, float y) { }
    default void onClickTouchMove (View view, float x, float y) { }
    default void onClickTouchUp (View view, float x, float y) { }
  }

  private final Delegate delegate;
  private Runnable longPressCallback;

  private boolean regionSet;
  private int left, top, right, bottom;
  private int flags;

  public ClickHelper (Delegate delegate) {
    this.delegate = delegate;
  }

  public ClickHelper setNoSound (boolean noSound) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_NO_SOUND, noSound);
    return this;
  }

  public void setBounds (int left, int top, int right, int bottom) {
    this.regionSet = true;
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public boolean inLongPress () {
    return (flags & FLAG_IN_LONG_PRESS) != 0;
  }

  public void cancel (View view, float x, float y) {
    resetTouch(view, x, y);
  }

  private void resetTouch (View view, float x, float y) {
    if ((flags & FLAG_LONG_PRESS_SCHEDULED) != 0) {
      flags &= ~FLAG_LONG_PRESS_SCHEDULED;
      if (longPressCallback == null)
        throw new AssertionError();
      view.removeCallbacks(longPressCallback);
      longPressCallback = null;
    }
    if ((flags & FLAG_AWAITING_CUSTOM_LONG_PRESS) != 0) {
      flags &= ~FLAG_AWAITING_CUSTOM_LONG_PRESS;
      delegate.onLongPressCancelled(view, x, y);
    }
    if ((flags & FLAG_IN_LONG_PRESS) != 0) {
      delegate.onLongPressFinish(view, x, y);
      flags &= ~FLAG_IN_LONG_PRESS;
    }
    if ((flags & FLAG_CAUGHT) != 0) {
      delegate.onClickTouchUp(view, x, y);
      flags &= ~FLAG_CAUGHT;
    }
  }

  private float startX, startY, longPressX, longPressY;

  private void scheduleLongPress (View view) {
    if (view != null) {
      if (longPressCallback != null)
        throw new AssertionError();
      flags |= FLAG_LONG_PRESS_SCHEDULED;
      longPressCallback = () -> {
        if ((flags & FLAG_LONG_PRESS_SCHEDULED) != 0) {
          if (delegate.onLongPressRequestedAt(view, startX, startY)) {
            flags &= ~FLAG_LONG_PRESS_SCHEDULED;
            longPressCallback = null;
            onLongPress(view, startX, startY);
          } else {
            flags |= FLAG_AWAITING_CUSTOM_LONG_PRESS;
          }
        }
      };
      view.postDelayed(longPressCallback, delegate.getLongPressDuration());
    }
  }

  public final void onLongPress (View view, float x, float y) {
    longPressX = x; longPressY = y;
    if (delegate.ignoreHapticFeedbackSettings(x, y)) {
      ViewUtils.hapticVibrate(view, true, delegate.forceEnableVibration());
    } else {
      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
    flags |= FLAG_IN_LONG_PRESS;
    flags &= ~FLAG_AWAITING_CUSTOM_LONG_PRESS;
    flags &= ~FLAG_LONG_PRESS_SCHEDULED;
    longPressCallback = null;
  }

  // FIXME replace this constant with something more appropriate?
  private static final float TOUCH_SLOP_SCALE = 1.89f;

  public boolean onTouchEvent (View view, MotionEvent e) {
    final float x = e.getX();
    final float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        resetTouch(view, x, y);

        if (regionSet && (x < left || x > right || y < top || y > bottom)) {
          return false;
        }

        if (!delegate.needClickAt(view, x, y)) {
          return false;
        }

        flags |= FLAG_CAUGHT;
        startX = x;
        startY = y;
        delegate.onClickTouchDown(view, x, y);

        if (delegate.needLongPress(x, y)) {
          scheduleLongPress(view);
        }

        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if ((flags & FLAG_CAUGHT) != 0) {
          resetTouch(view, x, y);
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if ((flags & FLAG_CAUGHT) != 0) {
          delegate.onClickTouchMove(view, x, y);
          if ((flags & FLAG_IN_LONG_PRESS) != 0) {
            delegate.onLongPressMove(view, e, x, y, longPressX, longPressY);
          } else if (Math.max(Math.abs(startX - x), Math.abs(startY - y)) > ViewConfiguration.get(view.getContext()).getScaledTouchSlop() * TOUCH_SLOP_SCALE) {
            resetTouch(view, x, y);
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if ((flags & FLAG_CAUGHT) != 0) {
          if ((flags & FLAG_IN_LONG_PRESS) != 0) {
            delegate.onLongPressFinish(view, x, y);
            flags &= ~FLAG_IN_LONG_PRESS;
          } else {
            delegate.onClickAt(view, x, y);
            if ((flags & FLAG_NO_SOUND) == 0) {
              ViewUtils.onClick(view);
            }
          }
          resetTouch(view, x, y);
          return true;
        }
        break;
      }
    }
    return (flags & FLAG_CAUGHT) != 0;
  }

}
