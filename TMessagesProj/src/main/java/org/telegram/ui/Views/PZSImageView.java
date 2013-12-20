/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class PZSImageView extends BackupImageView {

	enum ImageScaleType {
		FitCenter, TopCrop, CenterCrop
	}

	public ImageScaleType defaultScaleType = ImageScaleType.FitCenter;
	public ImageScaleType doubleTapScaleType = ImageScaleType.TopCrop;

	// private static final String TAG = "GalleryImageView";

	// wrapped motion event code.
	protected static final int PZS_ACTION_INIT = 100;
	protected static final int PZS_ACTION_SCALE = 1001;
	protected static final int PZS_ACTION_TRANSLATE = 1002;
	protected static final int PZS_ACTION_SCALE_TO_TRANSLATE = 1003;
	protected static final int PZS_ACTION_TRANSLATE_TO_SCALE = 1004;
	protected static final int PZS_ACTION_FIT_CENTER = 1005;
	protected static final int PZS_ACTION_CENTER_CROP = 1006;
	protected static final int PZS_ACTION_TO_LEFT_SIDE = 1007;
	protected static final int PZS_ACTION_TO_RIGHT_SIDE = 1008;
	protected static final int PZS_ACTION_TOP_CROP = 1009;
	protected static final int PZS_ACTION_CANCEL = -1;

	private final static float MAX_SCALE_TO_SCREEN = 2.f;
	private final static float MIN_SCALE_TO_SCREEN = 1.f;

	private static final float MIN_SCALE_SPAN = 10.f;

	// calculated min / max scale ratio based on image & screen size.
	private float mMinScaleFactor = 1.f;
	private float mMaxScaleFactor = 2.f;

    public boolean isVideo = false;
	private boolean mIsFirstDraw = true; // check flag to calculate necessary
											// init values.
	private int mImageWidth; // current set image width
	private int mImageHeight; // current set image height

	private Context mContext;

    public TextView videoText = null;

	public PZSImageView(Context context) {
		super(context);
		mContext = context;
		init();

	}

	public PZSImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		init();

	}

	public PZSImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		init();
	}

	private void init() {
		// should use matrix scale type.
		setScaleType(ScaleType.MATRIX);
		Matrix mat = getImageMatrix();
		mat.reset();
		setImageMatrix(mat);

		gd = new GestureDetector(mContext, new SimpleOnGestureListener() {

			/*@Override
			public boolean onDoubleTap(MotionEvent event) {
				int action = parseDoubleTapMotionEvent(event);
				touchAction(action, event);
				return true; // indicate event was handled
			}*/

			@Override
			public boolean onSingleTapConfirmed(MotionEvent ev) {
                ((AbstractGalleryActivity) mContext).topBtn();
				return true;
			}

		});

        videoText = new TextView(getContext());
        videoText.setText(getResources().getString(R.string.NoResult));
        videoText.setTextColor(0xffffffff);
        videoText.setBackgroundColor(0x66000000);
        videoText.setGravity(Gravity.CENTER);
        videoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        videoText.setText(getResources().getString(R.string.NoChats));
        videoText.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
	}

	GestureDetector gd;

    @Override
    public void setImageBitmap(Bitmap bitmap, String imgKey) {
        super.setImageBitmap(bitmap, imgKey);
        mIsFirstDraw = true;
        if (bitmap != null) {
            mImageWidth = bitmap.getWidth();
            mImageHeight = bitmap.getHeight();
        } else {
            mImageWidth = getWidth();
            mImageHeight = getHeight();
        }
    }

    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        mIsFirstDraw = true;
        if (bitmap != null) {
            mImageWidth = bitmap.getWidth();
            mImageHeight = bitmap.getHeight();
        } else {
            mImageWidth = getWidth();
            mImageHeight = getHeight();
        }
    }

    public void setImageBitmapMy(Bitmap bitmap) {
        super.setImageBitmapMy(bitmap);
        mIsFirstDraw = true;
        if (bitmap != null) {
            mImageWidth = bitmap.getWidth();
            mImageHeight = bitmap.getHeight();
        } else {
            mImageWidth = getWidth();
            mImageHeight = getHeight();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mIsFirstDraw = true;
        if (getDrawable() == null) {
            mImageHeight = h;
            mImageWidth = w;
        }
    }

    @Override
	protected void onDraw(Canvas canvas) {

        if (mIsFirstDraw) {
			mIsFirstDraw = false;
			if (defaultScaleType == ImageScaleType.FitCenter)
				fitCenter();
			else if (defaultScaleType == ImageScaleType.TopCrop)
				topCrop();
			else if (defaultScaleType == ImageScaleType.CenterCrop)
				centerCrop();
			calculateScaleFactorLimit();
			validateMatrix();
		}

		setImageMatrix(mCurrentMatrix);

        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            FileLog.e("tmessages", "trying draw " + currentPath);
        }
	}

	private void calculateScaleFactorLimit() {

		// set max / min scale factor.
		mMaxScaleFactor = Math.max(getHeight() * MAX_SCALE_TO_SCREEN
				/ mImageHeight, getWidth() * MAX_SCALE_TO_SCREEN / mImageWidth);

		mMinScaleFactor = Math.min(getHeight() * MIN_SCALE_TO_SCREEN
				/ mImageHeight, getWidth() * MIN_SCALE_TO_SCREEN / mImageWidth);

        if (getDrawable() == null) {
            mMaxScaleFactor = mMinScaleFactor;
        }
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (gd.onTouchEvent(event)) {
			return true;
		}
		int action = parseMotionEvent(event);
		touchAction(action, event);
		return true; // indicate event was handled
	}

	private void touchAction(int action, MotionEvent event) {
		switch (action) {
		case PZS_ACTION_INIT:
			initGestureAction(event.getX(), event.getY());
			break;
		case PZS_ACTION_SCALE:
			handleScale(event);
			break;
		case PZS_ACTION_TRANSLATE:
			handleTranslate(event);
			break;
		case PZS_ACTION_TRANSLATE_TO_SCALE:
			initGestureAction(event.getX(), event.getY());
			break;
		case PZS_ACTION_SCALE_TO_TRANSLATE:
			int activeIndex = (event.getActionIndex() == 0 ? 1 : 0);
			initGestureAction(event.getX(activeIndex), event.getY(activeIndex));
			break;
		case PZS_ACTION_FIT_CENTER:
			fitCenter();
			initGestureAction(event.getX(), event.getY());
			break;
		case PZS_ACTION_CENTER_CROP:
			centerCrop();
			initGestureAction(event.getX(), event.getY());
			break;
		case PZS_ACTION_TOP_CROP:
			topCrop();
			initGestureAction(event.getX(), event.getY());
			break;
		case PZS_ACTION_TO_LEFT_SIDE:
			toLeftSide();
			break;
		case PZS_ACTION_TO_RIGHT_SIDE:
			toRightSide();
			break;
		case PZS_ACTION_CANCEL:
			break;
		}

		// check current position of bitmap.
		validateMatrix();
		updateMatrix();
	}

	private int parseDoubleTapMotionEvent(MotionEvent ev) {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);
		float scaleNow = values[Matrix.MSCALE_X];
		float scaleX = (getWidth() - getPaddingLeft() - getPaddingRight())
				/ (float) mImageWidth;
		float scaleY = (getHeight() - getPaddingTop() - getPaddingBottom())
				/ (float) mImageHeight;
		if (scaleNow >= Math.max(scaleX, scaleY))
			return PZS_ACTION_FIT_CENTER;
		else if (scaleNow < Math.max(scaleX, scaleY)) {
			if (doubleTapScaleType == ImageScaleType.FitCenter)
				return PZS_ACTION_FIT_CENTER;
			else if (doubleTapScaleType == ImageScaleType.TopCrop)
				return PZS_ACTION_TOP_CROP;
			else if (doubleTapScaleType == ImageScaleType.CenterCrop)
				return PZS_ACTION_CENTER_CROP;

		}
		return PZS_ACTION_FIT_CENTER;
	}

	private int parseMotionEvent(MotionEvent ev) {

		switch (ev.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			return PZS_ACTION_INIT;
		case MotionEvent.ACTION_POINTER_DOWN:
			// more than one pointer is pressed...
			return PZS_ACTION_TRANSLATE_TO_SCALE;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			if (ev.getPointerCount() == 2) {
				return PZS_ACTION_SCALE_TO_TRANSLATE;
			} else {
				return PZS_ACTION_INIT;
			}
		case MotionEvent.ACTION_MOVE:
			if (ev.getPointerCount() == 1)
				return PZS_ACTION_TRANSLATE;
			else if (ev.getPointerCount() == 2)
				return PZS_ACTION_SCALE;
			return 0;
		}
		return 0;
	}

	// ///////////////////////////////////////////////
	// Related matrix calculation stuffs.
	// ///////////////////////////////////////////////

	private Matrix mCurrentMatrix = new Matrix();
	private Matrix mSavedMatrix = new Matrix();

	// Remember some things for zooming
	private PointF mStartPoint = new PointF();
	private PointF mMidPoint = new PointF();
	private float mInitScaleSpan = 1f;

	protected void initGestureAction(float x, float y) {
		mSavedMatrix.set(mCurrentMatrix);
		mStartPoint.set(x, y);
		mInitScaleSpan = 0.f;
	}

	protected void handleScale(MotionEvent event) {
        if (isVideo) {
            return;
        }
		float newSpan = spacing(event);

		// if two finger is too close, pointer index is bumped.. so just ignore
		// it.
		if (newSpan < MIN_SCALE_SPAN)
			return;

		if (mInitScaleSpan == 0.f) {
			// init values. scale gesture action is just started.
			mInitScaleSpan = newSpan;
			midPoint(mMidPoint, event);
		} else {
			float scale = normalizeScaleFactor(mSavedMatrix, newSpan,
					mInitScaleSpan);
			mCurrentMatrix.set(mSavedMatrix);
			mCurrentMatrix.postScale(scale, scale, mMidPoint.x, mMidPoint.y);
		}
	}

	private float normalizeScaleFactor(Matrix curMat, float newSpan,
			float stdSpan) {

		float values[] = new float[9];
		curMat.getValues(values);
		float scale = values[Matrix.MSCALE_X];

		if (stdSpan == newSpan) {
			return scale;
		} else {
			float newScaleFactor = newSpan / stdSpan;
			float candinateScale = scale * newScaleFactor;

			if (candinateScale > mMaxScaleFactor) {
				return mMaxScaleFactor / scale;
			} else if (candinateScale < mMinScaleFactor) {
				return mMinScaleFactor / scale;
			} else {
				return newScaleFactor;
			}
		}
	}

	protected void handleTranslate(MotionEvent event) {
		mCurrentMatrix.set(mSavedMatrix);
		mCurrentMatrix.postTranslate(event.getX() - mStartPoint.x, event.getY()
				- mStartPoint.y);
	}

	private RectF mTraslateLimitRect = new RectF(); // reuse instance.

	public boolean getOnLeftSide() {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);
		float tranX = values[Matrix.MTRANS_X];
        return tranX >= mTraslateLimitRect.right;
    }

	public boolean getOnRightSide() {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);
		float tranX = values[Matrix.MTRANS_X];
        return tranX <= mTraslateLimitRect.left;
    }

	private void validateMatrix() {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);

		// get current matrix values.
		float scale = values[Matrix.MSCALE_X];
		float tranX = values[Matrix.MTRANS_X];
		float tranY = values[Matrix.MTRANS_Y];

		int imageHeight = (int) (scale * mImageHeight);
		int imageWidth = (int) (scale * mImageWidth);
        if (imageHeight == 0 || imageWidth == 0) {
            imageHeight = getHeight();
            imageWidth = getWidth();
        }

		mTraslateLimitRect.setEmpty();
		// don't think about optimize code. first, just write code case by case.

		// check TOP & BOTTOM
		if (imageHeight > getHeight()) {
			// image height is taller than view
			mTraslateLimitRect.top = getHeight() - imageHeight
					- getPaddingTop() - getPaddingBottom();
			mTraslateLimitRect.bottom = 0.f;
		} else {
			mTraslateLimitRect.top = mTraslateLimitRect.bottom = (getHeight()
					- imageHeight - getPaddingTop() - getPaddingBottom()) / 2.f;
		}

		// check LEFT & RIGHT
		if (imageWidth > getWidth()) {
			// image width is longer than view
			mTraslateLimitRect.left = getWidth() - imageWidth
					- getPaddingRight() - getPaddingLeft();
			mTraslateLimitRect.right = 0.f;
		} else {
			mTraslateLimitRect.left = mTraslateLimitRect.right = (getWidth()
					- imageWidth - getPaddingLeft() - getPaddingRight()) / 2.f;
		}

		float newTranX = tranX;
		newTranX = Math.max(newTranX, mTraslateLimitRect.left);
		newTranX = Math.min(newTranX, mTraslateLimitRect.right);

		float newTranY = tranY;
		newTranY = Math.max(newTranY, mTraslateLimitRect.top);
		newTranY = Math.min(newTranY, mTraslateLimitRect.bottom);

		values[Matrix.MTRANS_X] = newTranX;
		values[Matrix.MTRANS_Y] = newTranY;
		mCurrentMatrix.setValues(values);

		if (!mTraslateLimitRect.contains(tranX, tranY)) {
			// set new start point.
			mStartPoint.offset(tranX - newTranX, tranY - newTranY);
		}
	}

	protected void updateMatrix() {
		setImageMatrix(mCurrentMatrix);
	}

	protected void fitCenter() {
		// move image to center....
		mCurrentMatrix.reset();

		float scaleX = (getWidth() - getPaddingLeft() - getPaddingRight())
				/ (float) mImageWidth;
		float scaleY = (getHeight() - getPaddingTop() - getPaddingBottom())
				/ (float) mImageHeight;
		float scale = Math.min(scaleX, scaleY);

		float dx = (getWidth() - getPaddingLeft() - getPaddingRight() - mImageWidth
				* scale) / 2.f;
		float dy = (getHeight() - getPaddingTop() - getPaddingBottom() - mImageHeight
				* scale) / 2.f;
		mCurrentMatrix.postScale(scale, scale);
		mCurrentMatrix.postTranslate(dx, dy);
		setImageMatrix(mCurrentMatrix);
	}

	public void toLeftSide() {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);
		float tranX = values[Matrix.MTRANS_X];
		mCurrentMatrix.postTranslate(mTraslateLimitRect.right - tranX, 0);
		setImageMatrix(mCurrentMatrix);
	}

	public void toRightSide() {
		float values[] = new float[9];
		mCurrentMatrix.getValues(values);
		float tranX = values[Matrix.MTRANS_X];
		mCurrentMatrix.postTranslate(mTraslateLimitRect.left - tranX, 0);
		setImageMatrix(mCurrentMatrix);
	}

	protected void centerCrop() {
		mCurrentMatrix.reset();

		float scaleX = (getWidth() - getPaddingLeft() - getPaddingRight())
				/ (float) mImageWidth;
		float scaleY = (getHeight() - getPaddingTop() - getPaddingBottom())
				/ (float) mImageHeight;
		float scale = Math.max(scaleX, scaleY);

		float dx = (getWidth() - getPaddingLeft() - getPaddingRight() - mImageWidth
				* scale) / 2.f;
		float dy = (getHeight() - getPaddingTop() - getPaddingBottom() - mImageHeight
				* scale) / 2.f;

		mCurrentMatrix.postScale(scale, scale);
		mCurrentMatrix.postTranslate(dx, dy);
		setImageMatrix(mCurrentMatrix);
	}

	protected void topCrop() {
		mCurrentMatrix.reset();

		float scaleX = (getWidth() - getPaddingLeft() - getPaddingRight())
				/ (float) mImageWidth;
		float scaleY = (getHeight() - getPaddingTop() - getPaddingBottom())
				/ (float) mImageHeight;
		float scale = Math.max(scaleX, scaleY);

		mCurrentMatrix.postScale(scale, scale);
		mCurrentMatrix.postTranslate(0, 0);
		setImageMatrix(mCurrentMatrix);
	}

	/** Determine the space between the first two fingers */
	private float spacing(MotionEvent event) {
		// ...
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	/** Calculate the mid point of the first two fingers */
	private void midPoint(PointF point, MotionEvent event) {
		// ...
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

}
