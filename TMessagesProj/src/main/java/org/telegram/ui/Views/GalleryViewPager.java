/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.PointF;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.telegram.messenger.FileLog;

public class GalleryViewPager extends ViewPager {
	PointF last;
	public PZSImageView mCurrentView;

	public GalleryViewPager(Context context) {
		super(context);
	}

	public GalleryViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	private float[] handleMotionEvent(MotionEvent event) {
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			last = new PointF(event.getX(0), event.getY(0));
			break;
		case MotionEvent.ACTION_MOVE:
		case MotionEvent.ACTION_UP:
			PointF curr = new PointF(event.getX(0), event.getY(0));
			return new float[] { curr.x - last.x, curr.y - last.y };

		}
		return null;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
        try {
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                super.onTouchEvent(event);
            }

            if (mCurrentView == null) {
                return super.onTouchEvent(event);
            }

            float[] difference = handleMotionEvent(event);

            if (difference != null && mCurrentView.getOnRightSide() && difference[0] < 0) {
                return super.onTouchEvent(event);
            } else if (difference != null && mCurrentView.getOnLeftSide() && difference[0] > 0) {
                return super.onTouchEvent(event);
            } else if (difference == null && (mCurrentView.getOnLeftSide() || mCurrentView.getOnRightSide())) {
                return super.onTouchEvent(event);
            }

            return false;
        } catch (Exception e) {
            try {
                getAdapter().notifyDataSetChanged();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            FileLog.e("tmessages", e);
        }

        return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
        try {
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                super.onInterceptTouchEvent(event);
            }

            if (mCurrentView == null) {
                return super.onInterceptTouchEvent(event);
            }

            float[] difference = handleMotionEvent(event);

            if (difference != null && difference.length > 0 && mCurrentView.getOnRightSide() && difference[0] < 0) {
                return super.onInterceptTouchEvent(event);
            } else if (difference != null && difference.length > 0 && mCurrentView.getOnLeftSide() && difference[0] > 0) {
                return super.onInterceptTouchEvent(event);
            } else if ((difference == null || difference.length == 0) && (mCurrentView.getOnLeftSide() || mCurrentView.getOnRightSide())) {
                return super.onInterceptTouchEvent(event);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

		return false;
	}
}
