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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.R;

public class HsvColorValueView extends FrameLayout {
	private Paint paint;
	private Shader outerShader;
	private Shader innerShader;
	private float hue = 0;

	private Bitmap drawCache = null;
	private Drawable colorSelector;
	private ImageView selectorView;
	
	private int lastMeasuredSize = -1;
	
	private float saturation = 0;
	private float value = 1;
	
	private OnSaturationOrValueChanged listener;

	public HsvColorValueView(Context context) {
		super(context);
		init();
	}

	public HsvColorValueView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public HsvColorValueView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		colorSelector = getContext().getResources().getDrawable(
				R.drawable.color_selector);
		selectorView = new ImageView(getContext());
		selectorView.setImageDrawable(colorSelector);
		addView(selectorView, new LayoutParams(colorSelector.getIntrinsicWidth(), colorSelector.getIntrinsicHeight()));
		setWillNotDraw(false);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		lastMeasuredSize = Math.min(getMeasuredHeight(), getMeasuredWidth());
		setMeasuredDimension(lastMeasuredSize, lastMeasuredSize);
		if (drawCache != null
				&& drawCache.getHeight() != getBackgroundSize(lastMeasuredSize))
		{
			drawCache.recycle();
			drawCache = null;
		}
	}

	public int getBackgroundOffset() {
		return (int) Math
				.ceil((double) (colorSelector.getIntrinsicHeight() / 2.f));
	}

	private int getBackgroundSize(int availableSize) {
		int offset = getBackgroundOffset();
		return availableSize - 2 * offset;
	}
	
	public int getBackgroundSize()
	{
		ensureCache();
		if (drawCache != null) return drawCache.getHeight();
		return 0;
	}
	
	private void ensureCache()
	{
		if (paint == null) {
			paint = new Paint();
		}
		int baseSize = getHeight();
		if(baseSize <= 0)
			baseSize = getMeasuredHeight();
		if(baseSize <= 0)
			baseSize = lastMeasuredSize;
		int backgroundSize = getBackgroundSize(baseSize);
		if (drawCache == null && backgroundSize > 0) {
			outerShader = new LinearGradient(0.f, 0.f, 0.f, backgroundSize,
					0xffffffff, 0xff000000, TileMode.CLAMP);

			float[] tmp00 = new float[3];
			tmp00[1] = tmp00[2] = 1.f;
			tmp00[0] = hue;
			int rgb = Color.HSVToColor(tmp00);

			innerShader = new LinearGradient(0.f, 0.f, backgroundSize, 0.f,
					0xffffffff, rgb, TileMode.CLAMP);
			ComposeShader shader = new ComposeShader(outerShader, innerShader,
					PorterDuff.Mode.MULTIPLY);

			paint.setShader(shader);

			drawCache = Bitmap.createBitmap(backgroundSize, backgroundSize,
                    Bitmap.Config.ARGB_8888);
			Canvas cacheCanvas = new Canvas(drawCache);
			cacheCanvas.drawRect(0.f, 0.f, backgroundSize, backgroundSize,
					paint);
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		ensureCache();
		canvas.drawBitmap(drawCache, getBackgroundOffset(), getBackgroundOffset(), paint);
	}

	private boolean down = false;
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_DOWN)
		{
			down = true;
			return true;
		}
		if(event.getAction() == MotionEvent.ACTION_UP)
		{
			down = false;
			setSelectorPosition((int)event.getX() - getBackgroundOffset(), (int)event.getY() - getBackgroundOffset(), true);
			return true;
		}
		if(event.getAction() == MotionEvent.ACTION_MOVE && down)
		{
			setSelectorPosition((int)event.getX() - getBackgroundOffset(), (int)event.getY() - getBackgroundOffset(), false);
			return true;
		}
		return super.onTouchEvent(event);
	}
	
	private void setSatAndValueFromPos(int x, int y, boolean up)
	{
		int offset = getBackgroundOffset();

		saturation = ((x - offset) / (float)getBackgroundSize());
		value = 1.f - ((y - offset) / (float)getBackgroundSize());
		
		onSaturationOrValueChanged(up);
	}
	
	private void setSelectorPosition(int x, int y, boolean up)
	{
		setSatAndValueFromPos(x, y, up);
		placeSelector();
	}
	
	private void placeSelector()
	{
		int offset = getBackgroundOffset();
		int halfSize = (int) Math.ceil(selectorView.getHeight() / 2.f);

		int x = (int)(getBackgroundSize() * saturation);
		int y = (int)(getBackgroundSize() * (1.f - value));

		int left = Math.max(0, Math.min(getBackgroundSize(), x)) + offset - halfSize;
		int top = Math.max(0, Math.min(getBackgroundSize(), y)) + offset - halfSize;

		selectorView.layout(left, top, left + selectorView.getWidth(), top + selectorView.getHeight());
	}
	
	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		placeSelector();
	}
	
	private void setPosFromSatAndValue()
	{
		if(drawCache != null)
			placeSelector();
	}

	public void setHue(float hue) {
		this.hue = hue;
		drawCache = null;
		invalidate();
	}
	
	public void setSaturation(float sat)
	{
		saturation = sat;
		setPosFromSatAndValue();
	}
	
	public float getSaturation()
	{
		return saturation;
	}
	
	public void setValue(float value)
	{
		this.value = value;
		setPosFromSatAndValue();
	}
	
	public float getValue()
	{
		return value;
	}
	
	public void setOnSaturationOrValueChanged(OnSaturationOrValueChanged listener)
	{
		this.listener = listener;
	}
	
	private void onSaturationOrValueChanged(boolean up)
	{
		if(listener != null)
			listener.saturationOrValueChanged(this, saturation, value, up);
	}
	
	public interface OnSaturationOrValueChanged
	{
		public void saturationOrValueChanged(HsvColorValueView sender, float saturation, float value, boolean up);
	}
}
