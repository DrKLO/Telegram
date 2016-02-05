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
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.telegram.messenger.R;

public class RgbSelectorView extends LinearLayout {

	private SeekBar seekRed;
	private SeekBar seekGreen;
	private SeekBar seekBlue;
	private SeekBar seekAlpha;
	private ImageView imgPreview;
	private OnColorChangedListener listener;
	
	public RgbSelectorView(Context context) {
		super(context);
		init();
	}

	public RgbSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init()
	{
		LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rgbView = inflater.inflate(R.layout.color_rgbview, null);
		
		addView(rgbView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				setPreviewImage();
				onColorChanged();
			}
		};
		
		seekRed = (SeekBar)rgbView.findViewById(R.id.color_rgb_seekRed);
		seekRed.setOnSeekBarChangeListener(listener);
		seekGreen = (SeekBar)rgbView.findViewById(R.id.color_rgb_seekGreen);
		seekGreen.setOnSeekBarChangeListener(listener);
		seekBlue = (SeekBar)rgbView.findViewById(R.id.color_rgb_seekBlue);
		seekBlue.setOnSeekBarChangeListener(listener);
		seekAlpha = (SeekBar)rgbView.findViewById(R.id.color_rgb_seekAlpha);
		seekAlpha.setOnSeekBarChangeListener(listener);
		imgPreview = (ImageView)rgbView.findViewById(R.id.color_rgb_imgpreview);
		
		setColor(Color.BLACK);
	}
	
	private void setPreviewImage()
	{
		Bitmap preview = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		preview.setPixel(0, 0, getColor());
		
		imgPreview.setImageBitmap(preview);
	}
	
	public int getColor()
	{
		return Color.argb(seekAlpha.getProgress(), seekRed.getProgress(), seekGreen.getProgress(), seekBlue.getProgress());
	}
	
	public void setColor(int color)
	{
		seekAlpha.setProgress(Color.alpha(color));
		seekRed.setProgress(Color.red(color));
		seekGreen.setProgress(Color.green(color));
		seekBlue.setProgress(Color.blue(color));
		setPreviewImage();
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
}
