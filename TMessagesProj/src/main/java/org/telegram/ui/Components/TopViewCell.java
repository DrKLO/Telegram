package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.HintView2;

public class TopViewCell extends LinearLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    public final BackupImageView imageView;
    public final LinkSpanDrawable.LinksTextView titleView;
    public final LinkSpanDrawable.LinksTextView textView;
    public int imageSize = 90;

    public TopViewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);

        imageView = new BackupImageView(context);
        imageView.getImageReceiver().setAutoRepeatCount(1);
        imageView.getImageReceiver().setAutoRepeat(1);
        imageView.setOnClickListener(v -> {
            imageView.getImageReceiver().startAnimation();
        });
        addView(imageView, LayoutHelper.createLinear(90, 90, Gravity.CENTER, 0, 9, 0, 9));

        titleView = new LinkSpanDrawable.LinksTextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 48, 0, 48, 10));

        textView = new LinkSpanDrawable.LinksTextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 48, 0, 48, 17));

        updateColors();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextColor(Theme.getColor(titleView.getVisibility() == View.VISIBLE ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));

        imageView.setLayoutParams(LayoutHelper.createLinear(imageSize, imageSize, Gravity.CENTER, 0, titleView.getVisibility() == View.VISIBLE ? 0 : 9, 0, 9));
    }

    public void setEmoji(String setName, String emoji) {
        MediaDataController.getInstance(UserConfig.selectedAccount).setPlaceholderImage(imageView, setName, emoji, "90_90");
    }
    public void setEmojiSize(int size) {
        if (imageSize != size) {
            imageSize = size;
            updateColors();
        }
    }

    private int lastIconResId;
    public void setEmoji(int iconResId) {
        if (lastIconResId != iconResId) {
            imageView.setImageDrawable(new RLottieDrawable(lastIconResId = iconResId, "" + iconResId, dp(90), dp(90)));
            imageView.getImageReceiver().setAutoRepeat(2);
        }
    }
    public void setEmojiStatic(int iconResId) {
        if (lastIconResId != iconResId) {
            imageView.clearImage();
            imageView.setImageResource(lastIconResId = iconResId);
        }
    }

    public void setText(CharSequence text) {
        titleView.setVisibility(View.GONE);
        textView.setText(text);
        textView.setMaxWidth(HintView2.cutInFancyHalf(text, textView.getPaint()));
        textView.requestLayout();

        updateColors();
    }

    public void setText(CharSequence title, CharSequence text) {
        titleView.setText(title);
        titleView.setVisibility(View.VISIBLE);
        textView.setText(text);
        textView.setMaxWidth(HintView2.cutInFancyHalf(text, textView.getPaint()));
        textView.requestLayout();

        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }
}
