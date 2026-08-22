package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UnsupportedBlockDrawable;

public class ChatMessageUnsupportedCell extends View implements Theme.Colorable {
    public final UnsupportedBlockDrawable unsupportedBlockDrawable;
    public final Theme.ResourcesProvider resourcesProvider;
    private int unsupportedBlockWidth;
    private int unsupportedBlockHeight;

    public ChatMessageUnsupportedCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        unsupportedBlockDrawable = new UnsupportedBlockDrawable(resourcesProvider);
        unsupportedBlockDrawable.setCallback(this);
        unsupportedBlockDrawable.setTitle(LocaleController.getString(R.string.UnsupportedMessageTitle));
        unsupportedBlockDrawable.setSubtitle(LocaleController.getString(R.string.UnsupportedMessageMessage));
        unsupportedBlockDrawable.setButtonText(LocaleController.getString(R.string.UnsupportedUpdate));
        unsupportedBlockDrawable.setOnClickListener(() -> {
            if (delegate != null) {
                delegate.didPressAppUpdateButton();
            }
        });
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == unsupportedBlockDrawable;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);

        unsupportedBlockWidth = width - dp(36);
        unsupportedBlockHeight = unsupportedBlockDrawable.measure(unsupportedBlockWidth);

        setMeasuredDimension(width, unsupportedBlockHeight + dp(12));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    private ChatMessageCell.ChatMessageCellDelegate delegate;
    private float mViewTop;
    private int mParentH;

    public void setDelegate(ChatMessageCell.ChatMessageCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setVisiblePart(float viewTop, int parentH) {
        mViewTop = viewTop;
        mParentH = parentH;
    }

    public void drawBackground(Canvas canvas) {
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(getMeasuredWidth(), mParentH, 0, mViewTop);
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), mParentH, 0, mViewTop);
        }

        canvas.drawRoundRect(dp(18), dp(6),
            dp(18) + unsupportedBlockWidth,
            dp(6) + unsupportedBlockHeight,
            dp(18), dp(18),
            getThemedPaint(Theme.key_paint_chatActionBackground));

        if (hasGradientService()) {
            canvas.drawRoundRect(dp(18), dp(6),
                dp(18) + unsupportedBlockWidth,
                dp(6) + unsupportedBlockHeight,
                dp(18), dp(18),
                Theme.chat_actionBackgroundGradientDarkenPaint);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        unsupportedBlockDrawable.setBounds(dp(18), dp(6),
            dp(18) + unsupportedBlockWidth,
            dp(6) + unsupportedBlockHeight);
        unsupportedBlockDrawable.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return unsupportedBlockDrawable.onTouchEvent(this, event);
    }

    @Override
    public void updateColors() {
        unsupportedBlockDrawable.updateColors();
    }

    public boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    public Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }
}
