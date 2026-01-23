package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Text;

public class BotAskCellDrawable extends Drawable {
    private static final int PADDING_TOP = 17;

    private static final int ICON_BOT_CIRCLE_RADIUS = 35;
    private static final int ICON_BOT_SIZE = 40;
    private static final int PADDING_TEXT_TOP = 14;
    private static final int TITLE_TEXT_GAP = 4;
    private static final int PADDING_TEXT_BOTTOM = 2;
    private static final int ICON_ARROW_SIZE = 20;
    private static final int PADDING_BOTTOM = 5;
    
    private final RectF tmpRect = new RectF();

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    private final Text title;
    private final Text text;
    private final Drawable botLogo;
    private final Drawable groupsArrow;
    private int width, height;

    private Paint dPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BotAskCellDrawable(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        title = new Text(LocaleController.getString(R.string.BotForumAskForStartNewChatTitle), 14, AndroidUtilities.bold());
        title.align(Layout.Alignment.ALIGN_CENTER);

        text = new Text("", 13);
        text.multiline(4);
        text.align(Layout.Alignment.ALIGN_CENTER);

        botLogo = context.getResources().getDrawable(R.drawable.filled_topic_new_24).mutate();
        botLogo.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

        groupsArrow = context.getResources().getDrawable(R.drawable.arrow_more).mutate();
        groupsArrow.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        groupsArrow.setAlpha((int) (255 * 0.6));

        dPaint.setColor(0xFF000000);
        dPaint.setAlpha((int) (255 * 0.12f));
    }

    public void set(long dialogId) {
        final int maxWidth = (int) (AndroidUtilities.displaySize.x * .95f);
        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);

        text.multiline(1);
        text.setMaxWidth(9999);
        text.setText(LocaleController.formatString(R.string.BotForumAskForStartNewChat,  UserObject.getUserName(user)));

        float allowedWidth = text.calculateRealWidth() / 2 * 1.20f;
        text.multiline(4);
        text.setMaxWidth(Math.min(maxWidth, allowedWidth));
        if (text.getLineCount() > 2) {
            text.setMaxWidth(Math.min(maxWidth, allowedWidth * 1.2f));
        }

        float width = 0, height = 0;

        width = Math.max(width, text.calculateRealWidth());
        width = Math.max(width, title.calculateRealWidth());
        width = Math.min(width + dp(32), maxWidth);

        height += dp(PADDING_TOP);
        height += dp(ICON_BOT_CIRCLE_RADIUS * 2);
        height += dp(PADDING_TEXT_TOP);
        height += title.getHeight();
        height += dp(TITLE_TEXT_GAP);
        height += text.getHeight();
        height += dp(PADDING_TEXT_BOTTOM);
        height += dp(ICON_ARROW_SIZE);
        height += dp(PADDING_BOTTOM);

        this.width = (int) width;
        this.height = (int) height;
    }



    public int getBubbleWidth() {
        return width;
    }

    public int getBubbleHeight() {
        return height;
    }

    public void draw(Canvas canvas) {
        Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        canvas.drawRoundRect(tmpRect, dp(16), dp(16), backgroundPaint);
        if (hasGradientService()) {
            canvas.drawRoundRect(tmpRect, dp(16), dp(16), Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider));
        }

        canvas.save();

        canvas.translate(0, tmpRect.top + dp(PADDING_TOP));
        canvas.drawCircle(tmpRect.centerX(), dp(ICON_BOT_CIRCLE_RADIUS), dp(ICON_BOT_CIRCLE_RADIUS), dPaint);

        int bx = (int) (tmpRect.centerX() - dp(ICON_BOT_SIZE / 2f));
        int by = dp(ICON_BOT_CIRCLE_RADIUS - ICON_BOT_SIZE / 2f);
        botLogo.setBounds(bx, by, bx + dp(ICON_BOT_SIZE), by + dp(ICON_BOT_SIZE));
        botLogo.draw(canvas);
        canvas.translate(0, dp(ICON_BOT_CIRCLE_RADIUS * 2));

        canvas.translate(0, dp(PADDING_TEXT_TOP));

        title.draw(canvas, tmpRect.centerX() - title.getWidth() / 2.0f, title.getHeight() / 2f, Color.WHITE, 1.0f);
        canvas.translate(0, title.getHeight());
        canvas.translate(0, dp(TITLE_TEXT_GAP));


        text.draw(canvas, tmpRect.centerX() - text.getWidth() / 2.0f, 0, Color.WHITE, 1.0f);
        canvas.translate(0, text.getHeight());

        canvas.translate(0, dp(PADDING_TEXT_BOTTOM));

        groupsArrow.setBounds((int) (tmpRect.centerX() - dp(ICON_ARROW_SIZE / 2f)), 0, (int) (tmpRect.centerX() + dp(ICON_ARROW_SIZE / 2f)), dp(ICON_ARROW_SIZE));
        groupsArrow.draw(canvas);

        canvas.restore();
    }

    private boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        tmpRect.set(bounds);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
