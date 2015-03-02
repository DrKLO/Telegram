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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import org.telegram.messenger.R;

public class HsvAlphaSelectorView extends LinearLayout {

	private Drawable seekSelector;
	private ImageView imgSeekSelector;
	private int minOffset = 0;
	private ImageView imgAlpha;
	
	private int alpha = 0;
	private int color = Color.WHITE;
	
	private boolean dirty = true;
	
	private OnAlphaChangedListener listener;

	public HsvAlphaSelectorView(Context context) {
		super(context);
		init();
	}

	public HsvAlphaSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public void setMinContentOffset(int minOffset) {
		this.minOffset = minOffset;
		LayoutParams params = new LayoutParams(imgAlpha.getLayoutParams());
		params.setMargins(0, getOffset(), 0, getSelectorOffset());
		imgAlpha.setLayoutParams(params);
	}

	private void init() {
		seekSelector = getContext().getResources().getDrawable(
				R.drawable.color_seekselector);
		buildUI();
	}

	private void buildUI() {
		setOrientation(LinearLayout.HORIZONTAL);
		setGravity(Gravity.CENTER_HORIZONTAL);
		setWillNotDraw(false);

		imgSeekSelector = new ImageView(getContext());
		imgSeekSelector.setImageDrawable(seekSelector);
		LayoutParams paramsSeek = new LayoutParams(seekSelector
				.getIntrinsicWidth(), seekSelector.getIntrinsicHeight());
		addView(imgSeekSelector, paramsSeek);

		imgAlpha = new ImageView(getContext());
		imgAlpha.setBackgroundDrawable(getContext().getResources().getDrawable(R.drawable.transparentbackrepeat));
		imgAlpha.setScaleType(ScaleType.FIT_XY);
		LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT);
		params.setMargins(0, getOffset(), 0, getSelectorOffset());
		addView(imgAlpha, params);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		if(dirty)
		{
			dirty = false;
			setAlphaImage();
		}
		super.onDraw(canvas);
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		placeSelector();
	}
	
	private void placeSelector()
	{
		int alphaY = (int)(((float)(255 - alpha) / 255.f) * imgAlpha.getHeight());
		
		int halfHeight = getSelectorOffset();
		int vertOffset = imgAlpha.getTop();

		imgSeekSelector.layout(0, alphaY + vertOffset - halfHeight, imgSeekSelector.getWidth(), alphaY + vertOffset - halfHeight + imgSeekSelector.getHeight());		
	}
	
	private int getSelectorOffset()
	{
		return (int) Math.ceil(imgSeekSelector.getHeight() / 2.f);
	}

	private int getOffset() {
		return Math.max(minOffset, (int) Math.ceil((double) seekSelector
                .getIntrinsicHeight() / 2));
	}
	
	private boolean down = false;
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_DOWN)
		{
			down = true;
			setPosition((int)event.getY());
			return true;
		}
		if(event.getAction() == MotionEvent.ACTION_UP)
		{
			down = false;
			return true;
		}
		if(down && event.getAction() == MotionEvent.ACTION_MOVE)
		{
			setPosition((int)event.getY());
			return true;
		}
		return super.onTouchEvent(event);
	}
	
	private void setPosition(int y)
	{
		int alphaY = y - imgAlpha.getTop();	
		alpha = 255 - Math.min(255, Math.max(0, (int) (((float) alphaY / imgAlpha.getHeight()) * 255.f)));
		
		placeSelector();
		
		onAlphaChanged();
	}
	
	public void setAlpha(int alpha)
	{
		if(this.alpha == alpha)
			return;
		this.alpha = alpha;
		placeSelector();
	}
	
	public float getAlpha()
	{
		return alpha;
	}
	
	public void setColor(int color)
	{
		if(this.color == color)
			return;
		this.color = color;
		setAlphaImage();
	}

	private void setAlphaImage()
	{
		if(imgAlpha.getHeight() <= 0)
		{
			dirty = true;
			invalidate();
			return;
		}
		Paint paint = new Paint();
		Shader shader;
		Bitmap drawCache = null;
		if (drawCache == null) {
			
			int colorFullAlpha = color | 0xFF000000;
			int colorNoAlpha = color & 0x00FFFFFF;

			shader = new LinearGradient(0, imgAlpha.getHeight(), 0, 0,
					colorNoAlpha, colorFullAlpha, TileMode.CLAMP);

			paint.setShader(shader);

			drawCache = Bitmap.createBitmap(imgAlpha.getWidth(), imgAlpha.getHeight(),
                    Bitmap.Config.ARGB_8888);
			Canvas cacheCanvas = new Canvas(drawCache);
			cacheCanvas.drawRect(0.f, 0.f, imgAlpha.getWidth(), imgAlpha.getHeight(),
					paint);
			
			imgAlpha.setImageBitmap(drawCache);
		}
	}
	
	public void setOnAlphaChangedListener(OnAlphaChangedListener listener)
	{
		this.listener = listener;
	}
	
	private void onAlphaChanged()
	{
		if(listener != null)
			listener.alphaChanged(this, alpha);
	}
	
	public interface OnAlphaChangedListener
	{
		public void alphaChanged(HsvAlphaSelectorView sender, int alpha);
	}
}
