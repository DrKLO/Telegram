package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TopicSeparator;

@SuppressLint("ViewConstructor")
public class BotAskCell extends View {
    private final Theme.ResourcesProvider resourcesProvider;
    private final BotAskCellDrawable drawable;
    private final TopicSeparator askBotForumSeparator;

    public BotAskCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        drawable = new BotAskCellDrawable(context, currentAccount, resourcesProvider);
        askBotForumSeparator = new TopicSeparator(currentAccount, this, resourcesProvider, true);
        askBotForumSeparator.setText("");
    }

    public void setDialogId(long dialogId) {
        drawable.set(dialogId);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            LayoutHelper.measureSpecExactly(MeasureSpec.getSize(widthMeasureSpec)),
            LayoutHelper.measureSpecExactly(drawable.getBubbleHeight() + dp(40)));
    }

    public int getSideMenuWidth() {
        return 0;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final int sideMenuWidth = getSideMenuWidth();
        final int bx = (getMeasuredWidth() - drawable.getBubbleWidth() + sideMenuWidth) / 2;
        final int by = dp(34);

        applyServiceShaderMatrix(getMeasuredWidth(), getHeight(), sideMenuWidth / 2f, getTop());
        askBotForumSeparator.draw(canvas, getWidth(), sideMenuWidth, 0, 1.0f, 1f, false);
        drawable.setBounds(bx, by, bx + drawable.getBubbleWidth(), by + drawable.getBubbleHeight());
        drawable.draw(canvas);
    }

    private void applyServiceShaderMatrix(int measuredWidth, int backgroundHeight, float x, float viewTop) {
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        } else {
            Theme.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        }
    }
}
