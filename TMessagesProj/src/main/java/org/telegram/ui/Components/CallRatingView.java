package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class CallRatingView extends LinearLayout {
	private final int numStars = 5;
	private int selectedRating = 0;
	private OnRatingChangeListener listener;

	private final RLottieImageView[] imageViews = new RLottieImageView[numStars];
	private final RLottieDrawable[] drawables = new RLottieDrawable[numStars];

	public CallRatingView(Context context) {
		super(context);

		for (int i = 0; i < numStars; i++) {
			RLottieImageView rLottieImageView = new RLottieImageView(context);

			RLottieDrawable rLottieDrawable = new RLottieDrawable(R.raw.star, String.valueOf(R.raw.star),
					AndroidUtilities.dp(40), AndroidUtilities.dp(40), true, null);

			imageViews[i] = rLottieImageView;
			drawables[i] = rLottieDrawable;

			imageViews[i].setAnimation(drawables[i]);

			int ii = i+1;
			imageViews[i].setOnClickListener(v -> {
				updateRating(ii);
			});

			addView(imageViews[i], LayoutHelper.createLinear(40, 40, Gravity.CENTER_HORIZONTAL,
					4, 0, 4, 0));
		}
	}

	private int lastRatingValue;

	private void updateRating(int rating) {
		if (lastRatingValue != 0) return;
		if (rating == lastRatingValue) return;

		listener.onRatingChanged(rating);

		if (rating < lastRatingValue) {
			for (int i = rating; i < lastRatingValue; i ++) {
				RLottieImageView imageView = imageViews[i];
				imageView.setProgress(0f);
				imageView.setOnlyLastFrame(true);
			}
		} else {
			for (int i = lastRatingValue; i < rating; i++) {
				imageViews[i].playAnimation();
			}
		}

		lastRatingValue = rating;
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
