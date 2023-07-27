package org.telegram.ui.Stories;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AnimatedTextView;

public class StoryPositionView {

    private final SpannableStringBuilder leftSpace;
    private final SpannableStringBuilder rightSpace;
    AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
    int lastHash;
    Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StoryPositionView() {
        textDrawable.setTextSize(AndroidUtilities.dp(13));
        textDrawable.setTextColor(Color.WHITE);
        textDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.23f)));

        leftSpace = new SpannableStringBuilder();
        leftSpace.append(" ").setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(1)), 0, 1, 0);

        rightSpace = new SpannableStringBuilder();
        rightSpace.append(" ").setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(1)), 0, 1, 0);
    }

    public void draw(Canvas canvas, float alpha, int linesPosition, int linesCount, FrameLayout container, PeerStoriesView.PeerHeaderView headerView) {
        int hash = (linesCount << 12) + linesPosition;
        if (lastHash != hash) {
            lastHash = hash;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append(String.valueOf(linesPosition + 1)).append(leftSpace).append("/").append(rightSpace).append(String.valueOf(linesCount));
            textDrawable.setText(spannableStringBuilder, false);
        }
        canvas.save();
        float top = headerView.getY() + headerView.titleView.getTop() + textDrawable.getHeight() / 2f - 1;// + //(headerView.titleView.getMeasuredHeight() - textDrawable.getHeight()) / 2f + AndroidUtilities.dp(1);
        int rightPadding = (int) textDrawable.getCurrentWidth();
        headerView.titleView.setRightPadding(rightPadding);
        float left = AndroidUtilities.dp(4) + headerView.getLeft() + headerView.titleView.getLeft() + headerView.titleView.getTextWidth();
        left -= Utilities.clamp(headerView.titleView.getTextWidth() + rightPadding - headerView.titleView.getWidth(), rightPadding, 0);
        canvas.translate(left, top);

        float horizontalPadding = AndroidUtilities.dp(8);
        float verticalPadding = AndroidUtilities.dp(2);

        AndroidUtilities.rectTmp.set(-horizontalPadding, -verticalPadding, textDrawable.getCurrentWidth() + horizontalPadding, textDrawable.getHeight() + verticalPadding);
       // float r = AndroidUtilities.rectTmp.height() / 2f;
       // canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);

       // canvas.clipRect(AndroidUtilities.rectTmp);
        //canvas.translate(0, textDrawable.getHeight() / 2f - AndroidUtilities.dpf2(0.5f));
        textDrawable.setAlpha((int) (160 * alpha));
        textDrawable.draw(canvas);

        canvas.restore();
    }
}
