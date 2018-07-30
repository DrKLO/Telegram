/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;

public class CallSwipeView extends View {

	private Paint arrowsPaint, pullBgPaint;
	private int[] arrowAlphas = {64, 64, 64};
	private View viewToDrag;
	private boolean dragging = false, dragFromRight;
	private float dragStartX;
	private RectF tmpRect = new RectF();
	private Listener listener;
	private Path arrow = new Path();
	private AnimatorSet arrowAnim;
	private boolean animatingArrows = false;
	private boolean canceled = false;

	public CallSwipeView(Context context) {
		super(context);
		init();
	}

	private void init() {
		arrowsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		arrowsPaint.setColor(0xFFFFFFFF);
		arrowsPaint.setStyle(Paint.Style.STROKE);
		arrowsPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
		pullBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		ArrayList<Animator> anims = new ArrayList<>();
		for (int i = 0; i < arrowAlphas.length; i++) {
			ArrowAnimWrapper aaw = new ArrowAnimWrapper(i);
			ObjectAnimator anim = ObjectAnimator.ofInt(aaw, "arrowAlpha", 64, 255, 64);
			anim.setDuration(700);
			anim.setStartDelay(200 * i);
			//anim.setRepeatCount(ValueAnimator.INFINITE);
			anims.add(anim);
		}
		arrowAnim = new AnimatorSet();
		arrowAnim.playTogether(anims);
		arrowAnim.addListener(new AnimatorListenerAdapter() {
			private long startTime;
			private Runnable restarter = new Runnable() {
				@Override
				public void run() {
					if (arrowAnim != null) {
						arrowAnim.start();
					}
				}
			};

			@Override
			public void onAnimationEnd(Animator animation) {
				if (System.currentTimeMillis() - startTime < animation.getDuration() / 4) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.w("Not repeating animation because previous loop was too fast");
					}
					return;
				}
				if (!canceled && animatingArrows)
					post(restarter);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				canceled = true;
			}

			@Override
			public void onAnimationStart(Animator animation) {
				startTime = System.currentTimeMillis();
			}
		});
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (arrowAnim != null) {
			canceled = true;
			arrowAnim.cancel();
			arrowAnim = null;
		}
	}

	public void setColor(int color) {
		pullBgPaint.setColor(color);
		pullBgPaint.setAlpha(0xB2);
	}

	public void setViewToDrag(View viewToDrag, boolean dragFromRight) {
		this.viewToDrag = viewToDrag;
		this.dragFromRight = dragFromRight;
		updateArrowPath();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	private int getDraggedViewWidth() {
		return getHeight();
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (!isEnabled())
			return false;
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			if ((!dragFromRight && ev.getX() < getDraggedViewWidth()) || (dragFromRight && ev.getX() > getWidth() - getDraggedViewWidth())) {
				dragging = true;
				dragStartX = ev.getX();
				getParent().requestDisallowInterceptTouchEvent(true);
				listener.onDragStart();
				stopAnimatingArrows();
			}
		} else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
			viewToDrag.setTranslationX(Math.max(dragFromRight ? -(getWidth() - getDraggedViewWidth()) : 0, Math.min(ev.getX() - dragStartX, dragFromRight ? 0 : (getWidth() - getDraggedViewWidth()))));
			invalidate();
		} else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
			if (Math.abs(viewToDrag.getTranslationX()) >= getWidth() - getDraggedViewWidth() && ev.getAction() == MotionEvent.ACTION_UP) {
				listener.onDragComplete();
			} else {
				listener.onDragCancel();
				viewToDrag.animate().translationX(0).setDuration(200).start();
				invalidate();
				startAnimatingArrows();
				dragging = false;
			}
		}
		return dragging;
	}

	public void stopAnimatingArrows() {
		animatingArrows = false;
	}

	public void startAnimatingArrows() {
		if (animatingArrows || arrowAnim == null)
			return;
		animatingArrows = true;
		if (arrowAnim != null) {
			arrowAnim.start();
		}
	}

	public void reset() {
		if (arrowAnim == null || canceled) {
			return;
		}
		listener.onDragCancel();
		viewToDrag.animate().translationX(0).setDuration(200).start();
		invalidate();
		startAnimatingArrows();
		dragging = false;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (viewToDrag.getTranslationX() != 0) {
			if (dragFromRight) {
				tmpRect.set(getWidth() + viewToDrag.getTranslationX() - getDraggedViewWidth(), 0, getWidth(), getHeight());
			} else {
				tmpRect.set(0, 0, viewToDrag.getTranslationX() + getDraggedViewWidth(), getHeight());
			}
			canvas.drawRoundRect(tmpRect, getHeight() / 2, getHeight() / 2, pullBgPaint);
		}
		canvas.save();
		if (dragFromRight) {
			canvas.translate(getWidth() - getHeight() - AndroidUtilities.dp(12 + 6), getHeight() / 2);
		} else {
			canvas.translate(getHeight() + AndroidUtilities.dp(12), getHeight() / 2);
		}
		float offsetX = Math.abs(viewToDrag.getTranslationX());
		for (int i = 0; i < 3; i++) {
			float masterAlpha = 1;
			if (offsetX > AndroidUtilities.dp(16 * i)) {
				masterAlpha = 1 - Math.min(1, Math.max(0, (offsetX - i * AndroidUtilities.dp(16)) / AndroidUtilities.dp(16)));
			}
			arrowsPaint.setAlpha(Math.round(arrowAlphas[i] * masterAlpha));
			canvas.drawPath(arrow, arrowsPaint);
			canvas.translate(AndroidUtilities.dp(dragFromRight ? -16 : 16), 0);
		}
		canvas.restore();
		invalidate();
	}

	private void updateArrowPath() {
		arrow.reset();
		int size = AndroidUtilities.dp(6);
		if (dragFromRight) {
			arrow.moveTo(size, -size);
			arrow.lineTo(0, 0);
			arrow.lineTo(size, size);
		} else {
			arrow.moveTo(0, -size);
			arrow.lineTo(size, 0);
			arrow.lineTo(0, size);
		}
	}

	public interface Listener {
		void onDragComplete();
		void onDragStart();
		void onDragCancel();
	}

	private class ArrowAnimWrapper {

		private int index;

		public ArrowAnimWrapper(int value) {
			index = value;
		}

		public int getArrowAlpha() {
			return arrowAlphas[index];
		}

		public void setArrowAlpha(int value) {
			arrowAlphas[index] = value;
		}
	}
}
