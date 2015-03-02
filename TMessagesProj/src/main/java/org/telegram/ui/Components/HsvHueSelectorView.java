/*
 * Copyright (C) 2011 Devmil (Michael Lamers) 
 * Mail: develmil@googlemail.com
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
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import org.telegram.messenger.R;

public class HsvHueSelectorView extends LinearLayout {

	private Drawable seekSelector;
	private ImageView imgSeekSelector;
	private int minOffset = 0;
	private ImageView imgHue;

	private float hue = 0;

	private OnHueChangedListener listener;

	public HsvHueSelectorView(Context context) {
		super(context);
		init();
	}

	public HsvHueSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public void setMinContentOffset(int minOffset) {
		this.minOffset = minOffset;
		LayoutParams params = new LayoutParams(imgHue.getLayoutParams());
		params.setMargins(0, getOffset(), 0, getSelectorOffset());
		imgHue.setLayoutParams(params);
	}

	private void init() {
		seekSelector = getContext().getResources().getDrawable(
				R.drawable.color_seekselector);
		buildUI();
	}

	private void buildUI() {
		setOrientation(LinearLayout.HORIZONTAL);
		setGravity(Gravity.CENTER_HORIZONTAL);

		imgSeekSelector = new ImageView(getContext());
		imgSeekSelector.setImageDrawable(seekSelector);
		LayoutParams paramsSeek = new LayoutParams(seekSelector
				.getIntrinsicWidth(), seekSelector.getIntrinsicHeight());
		addView(imgSeekSelector, paramsSeek);

		imgHue = new ImageView(getContext());
		imgHue.setImageDrawable(getContext().getResources().getDrawable(
				R.drawable.color_hue));
		imgHue.setScaleType(ScaleType.FIT_XY);
		LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT);
		params.setMargins(0, getOffset(), 0, getSelectorOffset());
		addView(imgHue, params);
		
	}

	private int getOffset() {
		return Math.max(minOffset, getSelectorOffset());
	}

	private int getSelectorOffset() {
		return (int) Math.ceil(seekSelector.getIntrinsicHeight() / 2.f);
	}

	private boolean down = false;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			down = true;
			setPosition((int) event.getY());
			return true;
		}
		if (event.getAction() == MotionEvent.ACTION_UP) {
			down = false;
			return true;
		}
		if (down && event.getAction() == MotionEvent.ACTION_MOVE) {
			setPosition((int) event.getY());
			return true;
		}
		return super.onTouchEvent(event);
	}

	private void setPosition(int y) {
		int hueY = y - getOffset();

		hue = Math.max(Math.min(
                        360.f - (((float) hueY / imgHue.getHeight()) * 360.f), 360.f),
                0.f);

		placeSelector();

		onHueChanged();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		placeSelector();
	}

	private void placeSelector() {
		int hueY = (int) ((((360.f - hue) / 360.f)) * imgHue.getHeight());
		imgSeekSelector.layout(0, hueY + getOffset() - getSelectorOffset(), imgSeekSelector
				.getWidth(), hueY + getOffset() - getSelectorOffset() + imgSeekSelector.getHeight());
	}

	public void setHue(float hue) {
		if(this.hue == hue)
			return;
		this.hue = hue;
		placeSelector();
	}

	public float getHue() {
		return hue;
	}

	public void setOnHueChangedListener(OnHueChangedListener listener) {
		this.listener = listener;
	}

	private void onHueChanged() {
		if (listener != null)
			listener.hueChanged(this, hue);
	}

	public interface OnHueChangedListener {
		public void hueChanged(HsvHueSelectorView sender, float hue);
	}
}
