package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class TopicSearchCell extends FrameLayout {

    BackupImageView backupImageView;
    TextView textView;
    TLRPC.TL_forumTopic topic;
    public boolean drawDivider;

    public TopicSearchCell(@NonNull Context context) {
        super(context);
        backupImageView = new BackupImageView(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.bold());

        if (LocaleController.isRTL) {
            addView(backupImageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 12, 0, 12, 0));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 12, 0, 56, 0));
        } else {
            addView(backupImageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 56, 0, 12, 0));
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    public void setTopic(TLRPC.TL_forumTopic topic) {
        this.topic = topic;
        if (TextUtils.isEmpty(topic.searchQuery)) {
            textView.setText(AndroidUtilities.removeDiacritics(topic.title));
        } else {
            textView.setText(AndroidUtilities.highlightText(AndroidUtilities.removeDiacritics(topic.title), topic.searchQuery, null));
        }
        ForumUtilities.setTopicIcon(backupImageView, topic);
        if (backupImageView != null && backupImageView.getImageReceiver() != null && backupImageView.getImageReceiver().getDrawable() instanceof ForumUtilities.GeneralTopicDrawable) {
            ((ForumUtilities.GeneralTopicDrawable) backupImageView.getImageReceiver().getDrawable()).setColor(Theme.getColor(Theme.key_chats_archiveBackground));
        }
    }

    public TLRPC.TL_forumTopic getTopic() {
        return topic;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (drawDivider) {
            int left = AndroidUtilities.dp(56);
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - left, getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(left, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }
}
