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
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.R;

public class HsvSelectorView extends LinearLayout {

	private HsvAlphaSelectorView alphaSelector;
	private HsvHueSelectorView hueSelector;
	private HsvColorValueView hsvColorValueView;
	
	private int color;
	
	private OnColorChangedListener listener;

	public HsvSelectorView(Context context) {
		super(context);
		init();
	}

	public HsvSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		buildUI();
	}

	private void buildUI() {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View hsvView = inflater.inflate(R.layout.color_hsvview, null);
        //line 55 android.view.InflateException: Binary XML file line #4: Error inflating class org.telegram.ui.Components.ColorSelectorView
		this.addView(hsvView, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));

		alphaSelector = (HsvAlphaSelectorView) hsvView
				.findViewById(R.id.color_hsv_alpha);
		hsvColorValueView = (HsvColorValueView) hsvView
				.findViewById(R.id.color_hsv_value);
		hueSelector = (HsvHueSelectorView) hsvView.findViewById(R.id.color_hsv_hue);
		
		alphaSelector.setOnAlphaChangedListener(new HsvAlphaSelectorView.OnAlphaChangedListener() {
			@Override
			public void alphaChanged(HsvAlphaSelectorView sender, int alpha) {
				internalSetColor(getCurrentColor(true), true);
			}
		});
		
		hsvColorValueView.setOnSaturationOrValueChanged(new HsvColorValueView.OnSaturationOrValueChanged() {
			@Override
			public void saturationOrValueChanged(HsvColorValueView sender,
					float saturation, float value, boolean up) {
				alphaSelector.setColor(getCurrentColor(false));
				internalSetColor(getCurrentColor(true), up);
			}
		});
		hueSelector.setOnHueChangedListener(new HsvHueSelectorView.OnHueChangedListener() {
			@Override
			public void hueChanged(HsvHueSelectorView sender, float hue) {
				hsvColorValueView.setHue(hue);
				alphaSelector.setColor(getCurrentColor(false));
				internalSetColor(getCurrentColor(true), true);
			}
		});
		
		setColor(Color.BLACK);
	}
	
	private int getCurrentColor(boolean includeAlpha)
	{
		float[] hsv = new float[3];
		hsv[0] = hueSelector.getHue();
		hsv[1] = hsvColorValueView.getSaturation();
		hsv[2] = hsvColorValueView.getValue();
		int alpha = includeAlpha ? (int)alphaSelector.getAlpha() : 255;
		return Color.HSVToColor(alpha, hsv);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		LayoutParams paramsAlpha = new LayoutParams(alphaSelector.getLayoutParams());
		LayoutParams paramsHue = new LayoutParams(hueSelector.getLayoutParams());
		
		paramsAlpha.height = hsvColorValueView.getHeight();
		paramsHue.height = hsvColorValueView.getHeight();

		hueSelector.setMinContentOffset(hsvColorValueView.getBackgroundOffset());
		alphaSelector.setMinContentOffset(hsvColorValueView.getBackgroundOffset());
		
		alphaSelector.setLayoutParams(paramsAlpha);
		hueSelector.setLayoutParams(paramsHue);

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
	public int getColor()
	{
		return color;
	}

	private void internalSetColor(int color, boolean fire)
	{
		this.color = color;
		if(fire)
			onColorChanged();		
	}
	
	public void setColor(int color)
	{
		int alpha = Color.alpha(color);
		alphaSelector.setAlpha(alpha);
		int colorWithoutAlpha = color | 0xFF000000;
		float[] hsv = new float[3];
		Color.colorToHSV(colorWithoutAlpha, hsv);
		hueSelector.setHue(hsv[0]);
		hsvColorValueView.setHue(hsv[0]);
		hsvColorValueView.setSaturation(hsv[1]);
		hsvColorValueView.setValue(hsv[2]);
		alphaSelector.setColor(color);
		internalSetColor(color, this.color != color);	
	}
	
	private void onColorChanged()
	{
		if(listener != null)
			listener.colorChanged(color);
	}
	
	public void setOnColorChangedListener(OnColorChangedListener listener)
	{
		this.listener = listener;
	}
	
	public interface OnColorChangedListener
	{
		public void colorChanged(int color);
	}
	
}
