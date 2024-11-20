package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

public class ChooseDownloadQualityLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    public final LinearLayout buttonsLayout;
    private final Callback callback;

    public ChooseDownloadQualityLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        this.callback = callback;
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> {
            swipeBackLayout.closeForeground();
        });
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = dp(8);
        gap.setLayoutParams(layoutParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonsLayout);
    }

    public boolean update(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.media == null) return false;
        if (!messageObject.hasVideoQualities()) return false;

        ArrayList<VideoPlayer.Quality> qualities = VideoPlayer.getQualities(messageObject.currentAccount, messageObject.messageOwner.media.document, messageObject.messageOwner.media.alt_documents, 0, false);

        buttonsLayout.removeAllViews();
        for (int i = 0; i < qualities.size(); ++i) {
            final VideoPlayer.Quality q = qualities.get(i);
            final VideoPlayer.VideoUri uri = q.getDownloadUri();
            String title = LocaleController.formatString(R.string.QualitySaveIn, q.p()) + (q.original ? " (" + LocaleController.getString(R.string.QualitySource) + ")" : "");
            SpannableStringBuilder subtitle = new SpannableStringBuilder();
            if (uri.isCached()) {
                subtitle.append(AndroidUtilities.formatFileSize(uri.document.size));
                subtitle.append(LocaleController.getString(R.string.QualityCached));
            } else {
                final SpannableString s = new SpannableString("s ");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_mini_arrow_mediabold);
                span.rotate(90.0f);
                span.translate(0, dp(1));
                span.spaceScaleX = .85f;
                s.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                subtitle.append(s);
                subtitle.append(AndroidUtilities.formatFileSize(uri.document.size));
            }
            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, 0, title, false, null);
            item.setSubtext(subtitle);
            item.setColors(0xfffafafa, 0xfffafafa);
            item.subtextView.setPadding(0, 0, 0, 0);
            item.setOnClickListener((view) -> {
                callback.onQualitySelected(messageObject, q);
            });
            item.setSelectorColor(0x0fffffff);
        }
        return true;
    }

    public interface Callback {
        void onQualitySelected(MessageObject messageObject, VideoPlayer.Quality quality);
    }

}
