/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;

import com.google.android.gms.maps.SupportMapFragment;

public class NiceSupportMapFragment extends SupportMapFragment {

	private View drawingView;
	private boolean hasTextureViewSupport = false;
	private boolean preventParentScrolling = true;

	private boolean textureViewSupport() {
		boolean exist = true;
		try {
			Class.forName("android.view.TextureView");
		} catch (ClassNotFoundException e) {
			exist = false;
		}
		return exist;
	}

	private View searchAndFindDrawingView(ViewGroup group) {
		int childCount = group.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = group.getChildAt(i);
			if (child instanceof ViewGroup) {
				View view = searchAndFindDrawingView((ViewGroup) child);

				if (view != null) {
					return view;
				}
			}

			if (child instanceof SurfaceView) {
				return (View) child;
			}

			if (hasTextureViewSupport) { // if we have support for texture view
				if (child instanceof TextureView) {
					return (View) child;
				}
			}
		}
		return null;
	}

	@SuppressLint("NewApi")
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {

		ViewGroup view = (ViewGroup) super.onCreateView(inflater, container,
				savedInstanceState);
		view.setBackgroundColor(0x00000000); // Set Root View to be transparent
		// to prevent black screen on
		// load

		hasTextureViewSupport = textureViewSupport(); // Find out if we support
		// texture view on this
		// device
		drawingView = searchAndFindDrawingView(view); // Find the view the map
		// is using for Open GL

		if (drawingView == null)
			return view; // If we didn't get anything then abort

		drawingView.setBackgroundColor(0x00000000); // Stop black artifact from
		// being left behind on
		// scroll

		// Create On Touch Listener for MapView Parent Scrolling Fix - Many thanks to Gemerson Ribas (gmribas) for help with this fix.
		OnTouchListener touchListener = new OnTouchListener() {
			public boolean onTouch(View view, MotionEvent event) {

				int action = event.getAction();

				switch (action) {

					case MotionEvent.ACTION_DOWN:
						// Disallow Parent to intercept touch events.
						view.getParent().requestDisallowInterceptTouchEvent(preventParentScrolling);
						break;

					case MotionEvent.ACTION_UP:
						// Allow Parent to intercept touch events.
						view.getParent().requestDisallowInterceptTouchEvent(!preventParentScrolling);
						break;

				}

				// Handle View touch events.
				view.onTouchEvent(event);
				return false;
			}
		};

		// texture view
		if (hasTextureViewSupport) { // If we support texture view and the
			// drawing view is a TextureView then
			// tweak it and return the fragment view

			if (drawingView instanceof TextureView) {

				TextureView textureView = (TextureView) drawingView;

				// Stop Containing Views from moving when a user is interacting
				// with Map View Directly
				textureView.setOnTouchListener(touchListener);

				return view;
			}

		}

		// Otherwise continue onto legacy surface view hack
		final SurfaceView surfaceView = (SurfaceView) drawingView;

		// Fix for reducing black view flash issues
		SurfaceHolder holder = surfaceView.getHolder();
		holder.setFormat(PixelFormat.RGB_888);

		// Stop Containing Views from moving when a user is interacting with
		// Map View Directly
		surfaceView.setOnTouchListener(touchListener);

		return view;
	}

	public boolean getPreventParentScrolling() {
		return preventParentScrolling;
	}

	public void setPreventParentScrolling(boolean value) {
		preventParentScrolling = value;
	}

}