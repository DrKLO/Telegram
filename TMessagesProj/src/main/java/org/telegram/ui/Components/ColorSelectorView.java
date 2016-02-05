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

import android.app.Dialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import org.telegram.messenger.R;

public class ColorSelectorView extends LinearLayout {
	private static final String HSV_TAG = "HSV";
	private static final String RGB_TAG = "RGB";
	private static final String HEX_TAG = "HEX";

	private org.telegram.ui.Components.RgbSelectorView rgbSelector;
	private org.telegram.ui.Components.HsvSelectorView hsvSelector;
	private org.telegram.ui.Components.HexSelectorView hexSelector;
	private TabHost tabs;
	
	private int maxHeight = 0;
	private int maxWidth = 0;

	private int color;
	
	private OnColorChangedListener listener;

	public ColorSelectorView(Context context) {
		super(context);
		init();
	}

	public ColorSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
 
	public void setColor(int color) {
		setColor(color, null);
	}
	
	public void setDialog(Dialog d) {
		hexSelector.setDialog(d); 
	}

	private void setColor(int color, View sender) {
		if (this.color == color)
			return;
		this.color = color;
		if (sender != hsvSelector)
			hsvSelector.setColor(color);
		if (sender != rgbSelector)
			rgbSelector.setColor(color);
		if (sender != hexSelector)
			hexSelector.setColor(color);
		onColorChanged();
	}

	public int getColor() {
		return color;
	}

	private void init() {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View contentView = inflater.inflate(R.layout.color_colorselectview,
				null);

		addView(contentView, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));

		hsvSelector = new HsvSelectorView(getContext());
		hsvSelector.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
		hsvSelector
				.setOnColorChangedListener(new HsvSelectorView.OnColorChangedListener() {
					@Override
					public void colorChanged(int color) {
						setColor(color);
					}
				});
		rgbSelector = new RgbSelectorView(getContext());
		rgbSelector.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
		rgbSelector
				.setOnColorChangedListener(new RgbSelectorView.OnColorChangedListener() {
					@Override
					public void colorChanged(int color) {
						setColor(color);
					}
				});
		hexSelector = new HexSelectorView(getContext());
		hexSelector.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
		hexSelector
				.setOnColorChangedListener(new HexSelectorView.OnColorChangedListener() {
					@Override
					public void colorChanged(int color) {
						setColor(color);
					}
				});


		tabs = (TabHost) contentView
				.findViewById(R.id.colorview_tabColors);
		//tabs.getTabWidget().setDividerDrawable(new ColorDrawable(0xBDBDBD));

		tabs.setup();
		ColorTabContentFactory factory = new ColorTabContentFactory();
		//TabSpec hsvTab = tabs.newTabSpec(HSV_TAG).setIndicator("HSV", getContext().getResources().getDrawable(R.drawable.hsv32))
		//		.setContent(factory);
		//View tabview = createTabView(tabs.getContext(), HEX_TAG);
		TabSpec hsvTab = tabs.newTabSpec(HSV_TAG).setIndicator(createTabView(tabs.getContext(), HSV_TAG))
				.setContent(factory);
		//TabSpec rgbTab = tabs.newTabSpec(RGB_TAG).setIndicator("RGB", getContext().getResources().getDrawable(R.drawable.rgb32))
		//		.setContent(factory);
		TabSpec rgbTab = tabs.newTabSpec(RGB_TAG).setIndicator(createTabView(tabs.getContext(), RGB_TAG))
				.setContent(factory);
		//TabSpec hexTab = tabs.newTabSpec(HEX_TAG).setIndicator("HEX", getContext().getResources().getDrawable(R.drawable.hex32))
		//.setContent(factory);
		
		TabSpec hexTab = tabs.newTabSpec(HEX_TAG).setIndicator(createTabView(tabs.getContext(), HEX_TAG))
		.setContent(factory);
		tabs.addTab(hsvTab);
		tabs.addTab(rgbTab);
		tabs.addTab(hexTab);
	}

	private static View createTabView(final Context context, final String text) {
		    View view = LayoutInflater.from(context).inflate(R.layout.tabs_bg, null);
		    TextView tv = (TextView) view.findViewById(R.id.tabsText);
		    tv.setText(text);
		    return view;
		}

	
	class ColorTabContentFactory implements TabContentFactory {
		@Override
		public View createTabContent(String tag) {
			if (HSV_TAG.equals(tag)) {
				return hsvSelector;
			}
			if (RGB_TAG.equals(tag)) {
				return rgbSelector;
			}
			if (HEX_TAG.equals(tag)) {
				return hexSelector;
			}

			return null;
		}
	}
	
	private void onColorChanged()
	{
		if(listener != null)
			listener.colorChanged(getColor());
	}
	
	public void setOnColorChangedListener(OnColorChangedListener listener)
	{
		this.listener = listener;
	}
	
	public interface OnColorChangedListener
	{
		public void colorChanged(int color);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if(HSV_TAG.equals(tabs.getCurrentTabTag()))
		{
			maxHeight = getMeasuredHeight();
			maxWidth = getMeasuredWidth();
		}
		setMeasuredDimension(maxWidth, maxHeight);
	}
}
