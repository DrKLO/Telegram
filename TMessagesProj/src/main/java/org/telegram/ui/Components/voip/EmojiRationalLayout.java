package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.widget.LinearLayout;

@SuppressLint("ViewConstructor")
public class EmojiRationalLayout extends LinearLayout {

    private final RectF bgRect = new RectF();
    private final VoIPBackgroundProvider backgroundProvider;

    public EmojiRationalLayout(Context context, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.backgroundProvider = backgroundProvider;
        backgroundProvider.attach(this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        bgRect.set(0, 0, getWidth(), getHeight());
        backgroundProvider.setDarkTranslation(getX(), getY());
        canvas.drawRoundRect(bgRect, dp(20), dp(20), backgroundProvider.getDarkPaint());
        super.dispatchDraw(canvas);
    }
}
