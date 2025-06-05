package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class StoryFailView extends FrameLayout {

    private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextView titleTextView;
    private final TextView subtitleTextView;
    private final TextView button;

    public StoryFailView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        redPaint.setColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        whitePaint.setColor(Color.WHITE);
        setWillNotDraw(false);

        titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleTextView.setText(LocaleController.getString(R.string.StoryError));
        titleTextView.setTextColor(Color.WHITE);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 44, 0, 0, 0));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 8);
        subtitleTextView.setTextColor(Theme.multAlpha(Color.WHITE, .5f));
        subtitleTextView.setVisibility(View.GONE);
        subtitleTextView.setTranslationY(dp(9));
        addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 44, 0, 0, 0));

        button = new TextView(context);
        button.setPadding(dp(13), 0, dp(13), 0);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), 0x1fffffff, 0x38ffffff));
        button.setTypeface(AndroidUtilities.bold());
        button.setText(LocaleController.getString(R.string.TryAgain));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        addView(button, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 12, 0));
    }

    public void set(TLRPC.TL_error error) {
        if (error == null || TextUtils.isEmpty(error.text)) {
            titleTextView.setTranslationY(0);
            subtitleTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setTranslationY(-dpf2(5.33f));
            subtitleTextView.setText(error.text);
            subtitleTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        button.setOnClickListener(l);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float cx = dp(13 + 10), cy = getHeight() / 2f;

        canvas.drawCircle(cx, cy, dp(10), redPaint);
        AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(1), cy - AndroidUtilities.dpf2(4.6f), cx + AndroidUtilities.dp(1), cy + AndroidUtilities.dpf2(1.6f));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), whitePaint);
        AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(1), cy + AndroidUtilities.dpf2(2.6f), cx + AndroidUtilities.dp(1), cy + AndroidUtilities.dpf2(2.6f + 2));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), whitePaint);
    }
}
