package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.HintView2;

public class TopViewCell extends LinearLayout {

    public final BackupImageView imageView;
    public final LinkSpanDrawable.LinksTextView textView;
    private int maxWidth;

    public TopViewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        setOrientation(VERTICAL);

        imageView = new BackupImageView(context);
        imageView.getImageReceiver().setAutoRepeatCount(1);
        imageView.getImageReceiver().setAutoRepeat(1);
        imageView.setOnClickListener(v -> {
            imageView.getImageReceiver().startAnimation();
        });
        addView(imageView, LayoutHelper.createLinear(90, 90, Gravity.CENTER, 0, 9, 0, 9));

        textView = new LinkSpanDrawable.LinksTextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (maxWidth > 0 && maxWidth < width) {
                    width = maxWidth;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 48, 0, 48, 17));

    }

    public void setEmoji(String setName, String emoji) {
        MediaDataController.getInstance(UserConfig.selectedAccount).setPlaceholderImage(imageView, setName, emoji, "90_90");
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
        textView.setText(text);
        maxWidth = HintView2.cutInFancyHalf(text, textView.getPaint());
        textView.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }
}
