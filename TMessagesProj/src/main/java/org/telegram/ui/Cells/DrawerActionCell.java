/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.FilterCreateActivity;

import java.util.Set;

public class DrawerActionCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView textView;
    private int currentId;
    private RectF rect = new RectF();
    private boolean currentError;

    public DrawerActionCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));
        imageView.getImageReceiver().setFileLoadingPriority(FileLoader.PRIORITY_HIGH);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.TOP, 19, 12, 0, 0));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 72, 0, 16, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean redError = currentError;
        boolean error = currentError;
        if (!error && currentId == 8) {
            Set<String> suggestions = MessagesController.getInstance(UserConfig.selectedAccount).pendingSuggestions;
            error = suggestions.contains("VALIDATE_PHONE_NUMBER") || suggestions.contains("VALIDATE_PASSWORD");
        }
        if (error) {
            int countTop = AndroidUtilities.dp(12.5f);
            int countWidth = AndroidUtilities.dp(9);
            int countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(25);

            int x = countLeft - AndroidUtilities.dp(5.5f);
            rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(14), countTop + AndroidUtilities.dp(23));
            Theme.chat_docBackPaint.setColor(Theme.getColor(redError ? Theme.key_text_RedBold : Theme.key_chats_archiveBackground));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.chat_docBackPaint);

            int w = Theme.dialogs_errorDrawable.getIntrinsicWidth();
            int h = Theme.dialogs_errorDrawable.getIntrinsicHeight();
            Theme.dialogs_errorDrawable.setBounds((int) (rect.centerX() - w / 2), (int) (rect.centerY() - h / 2), (int) (rect.centerX() + w / 2), (int) (rect.centerY() + h / 2));
            Theme.dialogs_errorDrawable.draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
    }

    public void setTextAndIcon(int id, String text, int resId) {
        currentId = id;
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void setError(boolean error) {
        currentError = error;
        invalidate();
    }

    public void setTextAndIcon(int id, CharSequence text, int resId) {
        currentId = id;
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void updateTextAndIcon(String text, int resId) {
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        info.setText(textView.getText());
        info.setClassName(TextView.class.getName());
    }

    public void setBot(TLRPC.TL_attachMenuBot bot) {
        currentId = (int) bot.bot_id;
        try {
            if (bot.side_menu_disclaimer_needed) {
                textView.setText(applyNewSpan(bot.short_name));
            } else {
                textView.setText(bot.short_name);
            }
            TLRPC.TL_attachMenuBotIcon botIcon = MediaDataController.getSideAttachMenuBotIcon(bot);
            if (botIcon != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(botIcon.icon.thumbs, 24 * 3);
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(botIcon.icon.thumbs,  Theme.key_emptyListPlaceholder, 0.2f);
                imageView.setImage(
                    ImageLocation.getForDocument(botIcon.icon), "24_24",
                    ImageLocation.getForDocument(photoSize, botIcon.icon), "24_24",
                    svgThumb != null ? svgThumb : getContext().getResources().getDrawable(R.drawable.msg_bot).mutate(),
                    bot
                );
            } else {
                imageView.setImageResource(R.drawable.msg_bot);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static CharSequence applyNewSpan(String str) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
        spannableStringBuilder.append("  d");
        FilterCreateActivity.NewSpan span = new FilterCreateActivity.NewSpan(10);
        span.setColor(Theme.getColor(Theme.key_premiumGradient1));
        spannableStringBuilder.setSpan(span, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
        return spannableStringBuilder;
    }
}
