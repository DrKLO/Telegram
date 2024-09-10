package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class TooManyCommunitiesHintCell extends FrameLayout {

    private ImageView imageView;
    private TextView headerTextView;
    private TextView messageTextView;
    private FrameLayout imageLayout;

    public TooManyCommunitiesHintCell(Context context) {
        super(context);

        imageView = new ImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_nameMessage_threeLines), PorterDuff.Mode.MULTIPLY));

        headerTextView = new TextView(context);
        headerTextView.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerTextView.setTypeface(AndroidUtilities.bold());
        headerTextView.setGravity(Gravity.CENTER);
        addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 75, 52, 0));

        messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_chats_message));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageTextView.setGravity(Gravity.CENTER);
        addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 36, 110, 36, 0));

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        String s = "500";
        imageLayout = new FrameLayout(context) {

            RectF rect = new RectF();


            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setColor(Theme.getColor(Theme.key_text_RedRegular));

                canvas.save();
                canvas.translate(getMeasuredWidth() - textPaint.measureText(s) - AndroidUtilities.dp(8), AndroidUtilities.dpf2(7f));
                rect.set(0, 0, textPaint.measureText(s), textPaint.getTextSize());
                rect.inset(-AndroidUtilities.dp(6), -AndroidUtilities.dp(3));
                float r = (textPaint.getTextSize()) / 2f + AndroidUtilities.dp(3);
                canvas.drawRoundRect(rect, r, r, paint);
                canvas.drawText(s, 0, textPaint.getTextSize() - AndroidUtilities.dpf2(2f), textPaint);
                canvas.restore();
            }
        };
        imageLayout.setWillNotDraw(false);
        imageLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        addView(imageLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 12, 0, 6));
        headerTextView.setText(LocaleController.getString(R.string.TooManyCommunities));
        imageView.setImageResource(R.drawable.groups_limit1);
    }

    public void setMessageText(String message) {
        messageTextView.setText(message);
    }
}
