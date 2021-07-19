package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Created by grishka on 10.02.17.
 */

public class BetterRatingView extends View {
	private Bitmap filledStar, hollowStar;
	private Paint paint = new Paint();
	private int numStars = 5;
	private int selectedRating = 0;
	private OnRatingChangeListener listener;

	public BetterRatingView(Context context) {
		super(context);
		filledStar = BitmapFactory.decodeResource(getResources(), R.drawable.ic_rating_star_filled).extractAlpha();
		hollowStar = BitmapFactory.decodeResource(getResources(), R.drawable.ic_rating_star).extractAlpha();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(numStars * AndroidUtilities.dp(32) + (numStars - 1) * AndroidUtilities.dp(16), AndroidUtilities.dp(32));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		for (int i = 0; i < numStars; i++) {
			paint.setColor(Theme.getColor(i < selectedRating ? Theme.key_dialogTextBlue : Theme.key_dialogTextHint));
			canvas.drawBitmap(i < selectedRating ? filledStar : hollowStar, i * AndroidUtilities.dp(32 + 16), 0, paint);
		}
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
