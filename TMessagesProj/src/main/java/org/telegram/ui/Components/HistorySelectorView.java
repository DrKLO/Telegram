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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.R;

import java.util.LinkedList;

public class HistorySelectorView extends LinearLayout {
	private static final String PREFS_NAME = "RECENT_COLORS";
	private static final String HISTORY = "HISTORY";

	private static final int MAX_COLORS = 30;
	
	JSONArray colors;
	OnColorChangedListener listener;
	int color;
	
	public HistorySelectorView(Context context) {
		super(context);
		init();
	}
	
	public HistorySelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	private void init()
	{
		LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View content = inflater.inflate(R.layout.color_historyview, null);
		addView(content, new LayoutParams(LayoutParams.FILL_PARENT , LayoutParams.FILL_PARENT));
		
		readColors();
		
		makeColorList();
	}
	
	private void makeColorList() {
		LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout colorlist = (LinearLayout)findViewById(R.id.colorlist);

		if (colors == null || colors.length() <= 0) {
			View nocolors = findViewById(R.id.nocolors);
			nocolors.setVisibility(View.VISIBLE);
			colorlist.setVisibility(View.GONE);
			findViewById(R.id.colorlistscroll).setVisibility(View.GONE); //have to remove it's parent container too
			return;
		}
		try {		
			for (int i=colors.length()-1; i>=0; i--) {
				final int color = colors.getInt(i);
				ViewGroup boxgroup = (ViewGroup)inflater.inflate(R.layout.color_historyview_item, colorlist, false);
				TextView box = (TextView)boxgroup.findViewById(R.id.colorbox);
				box.setBackgroundColor(color);
				//box.setText("#"+Integer.toHexString(color));
				colorlist.addView(boxgroup );
				box.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						setColor(color);
						onColorChanged();
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void readColors() {
		SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
		try {
			colors = new JSONArray(prefs.getString(HISTORY, ""));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void selectColor(int color) {
		try {
			SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
			if (colors == null) colors = new JSONArray();
			boolean dontadd = false;
			for (int i=0; i < colors.length(); i++) {
				if (colors.getInt(i) == color) { //reusing a recent color! shit.
					dontadd = true;
					colors = moveValueToFront(colors, i, color);
				}
			}
			if (!dontadd) colors.put(color);
			if (colors.length() > MAX_COLORS) {
					JSONArray newcolors = new JSONArray();
					for (int i=colors.length()-MAX_COLORS; i < colors.length(); i++) {
						newcolors.put(colors.getInt(i));
					}
					colors = newcolors;
			}
			Editor edit = prefs.edit();
			edit.putString(HISTORY, colors.toString());
			edit.commit();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public JSONArray moveValueToFront(JSONArray array, int index, int color) throws JSONException {
		LinkedList<Integer> list = new LinkedList<Integer>();
		for (int i=0; i < array.length(); i++) {
			list.add(array.getInt(i));
		}
		
		list.add(color);
		list.remove(index);
		
		array = new JSONArray();
		for (int i : list) {
			array.put(i);
		}

		return array;
	}
	
	
	private void setColor(int color) {
		this.color = color;
	}
	
	private int getColor() {
		return color;
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
