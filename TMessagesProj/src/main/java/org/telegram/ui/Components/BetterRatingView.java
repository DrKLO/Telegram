package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Created by grishka on 10.02.17.
 * Modified by CAE
 * Only used in VOIPHelper
 */

public class BetterRatingView extends View {
	private Bitmap filledStar, hollowStar;
	private Paint paint = new Paint();

	private LinearLayout layout;
	private int numStars = 5;


	public RLottieImageView[] images = new RLottieImageView[numStars];
	private int selectedRating = 0;
	private OnRatingChangeListener listener;

	public BetterRatingView(Context context) {
		super(context);
		filledStar = BitmapFactory.decodeResource(getResources(), R.drawable.ic_rating_star_filled).extractAlpha();
		hollowStar = BitmapFactory.decodeResource(getResources(), R.drawable.ic_rating_star).extractAlpha();

		layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.HORIZONTAL);
		setWillNotDraw(false);

		layout.setWillNotDraw(false);

		for (int i=0;i<numStars;i++){
			images[i] = new RLottieImageView(context);
			images[i].setVisibility(VISIBLE);

			images[i].setAnimation(R.raw.star,AndroidUtilities.dp(20),AndroidUtilities.dp(20));
			layout.addView(images[i],LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,LayoutHelper.WRAP_CONTENT));
		}
		layout.setGravity(Gravity.CENTER);

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(numStars * AndroidUtilities.dp(32) + (numStars - 1) * AndroidUtilities.dp(16), AndroidUtilities.dp(32));
	}


	@Override
	protected void onDraw(Canvas canvas) {

		layout.measure(getWidth(), getHeight());
		layout.layout(0, 0, getWidth(), getHeight());

		layout.draw(canvas);

	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float offset = AndroidUtilities.dp(-8);
		for (int i = 0; i < numStars; i++) {
			if (event.getX() > offset && event.getX() < offset + AndroidUtilities.dp(32 + 16)) {
				if (selectedRating != i + 1) {
					selectedRating = i + 1;
					if (listener != null)
						listener.onRatingChanged(selectedRating);
					invalidate();
					break;
				}
			}
			offset += AndroidUtilities.dp(32 + 16);
		}
		return true;
	}

	public int getRating() {
		return selectedRating;
	}

	public void setOnRatingChangeListener(OnRatingChangeListener l) {
		listener = l;
	}

	public interface OnRatingChangeListener {
		void onRatingChanged(int newRating);
	}
}
