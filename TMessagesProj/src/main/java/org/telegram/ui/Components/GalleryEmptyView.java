package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

@SuppressLint("ViewConstructor")
public class GalleryEmptyView extends LinearLayout {

    private final TextView titleTextView;
    private final TextView subtitleTextView;

    private final BackupImageView stickerView;
    private final ButtonWithCounterView galleryAccessButton;
    private final ButtonWithCounterView cameraAccessButton;
    private final ButtonWithCounterView useAnEmojiButton;
    private final long emojiDocumentId;

    public GalleryEmptyView(Context context, int currentAccount, boolean forUser) {
        super(context);
        TLRPC.TL_emojiList emojiList = AvatarConstructorPreviewCell.getOrCreateEmojiList(currentAccount, forUser);

        setOrientation(LinearLayout.VERTICAL);

        stickerView = new BackupImageView(context);
        stickerView.setImageDrawable(new RLottieDrawable(R.raw.utyan_gallery, "utyan_gallery", dp(110), dp(110)));

        if (!AndroidUtilities.isTablet()) {
            addView(stickerView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        }

        titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setText(LocaleController.getString(R.string.GalleryAccessAllowAccess));
        titleTextView.setTypeface(AndroidUtilities.bold());
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 7));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        subtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleTextView.setText(LocaleController.getString(UserConfig.getInstance(currentAccount).isPremium() ? R.string.GalleryAccessAllowAccessTextPremium : R.string.GalleryAccessAllowAccessTextNonPremium));
        subtitleTextView.setMaxWidth(dp(260));
        subtitleTextView.setLineSpacing(AndroidUtilities.dp(2), 1);
        addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 14));

        galleryAccessButton = new ButtonWithCounterView(context, null);
        galleryAccessButton.setRound();
        galleryAccessButton.setText(getString(R.string.GalleryAccessAllowAccessButton), false);
        addView(galleryAccessButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        cameraAccessButton = new ButtonWithCounterView(context, false, null);
        cameraAccessButton.setRound();
        SpannableStringBuilder ssb = new SpannableStringBuilder("c");
        ssb.setSpan(new ColoredImageSpan(R.drawable.outline_attach_camera_24), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append("  ").append(getString(R.string.GalleryAccessAllowAccessOpenCamera));
        cameraAccessButton.setText(ssb, false);
        addView(cameraAccessButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

        useAnEmojiButton = new ButtonWithCounterView(context, false, null);
        useAnEmojiButton.setRound();
        useAnEmojiButton.setVisibility(GONE);
        SpannableStringBuilder ssb2 = new SpannableStringBuilder("c");
        if (emojiList.document_id != null && !emojiList.document_id.isEmpty()) {
            emojiDocumentId = emojiList.document_id.get(0);
            ssb2.setSpan(new AnimatedEmojiSpan(emojiDocumentId, null), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb2.append("  ");
        } else {
            emojiDocumentId = 0;
        }
        ssb2.append(getString(R.string.UseEmoji));
        useAnEmojiButton.setText(ssb2, false);
        addView(useAnEmojiButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 1, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int sW1 = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
        final int sH = MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY);

        galleryAccessButton.setUseWrapContent(true);
        cameraAccessButton.setUseWrapContent(true);
        useAnEmojiButton.setUseWrapContent(true);
        galleryAccessButton.measure(sW1, sH);
        cameraAccessButton.measure(sW1, sH);
        useAnEmojiButton.measure(sW1, sH);
        galleryAccessButton.setUseWrapContent(false);
        cameraAccessButton.setUseWrapContent(false);
        useAnEmojiButton.setUseWrapContent(false);

        final int maxWidth = Math.max(Math.max(galleryAccessButton.getMeasuredWidth(),
            cameraAccessButton.getMeasuredWidth()), useAnEmojiButton.getMeasuredWidth());

        galleryAccessButton.getLayoutParams().width = maxWidth + dp(80);
        cameraAccessButton.getLayoutParams().width = maxWidth + dp(80);
        useAnEmojiButton.getLayoutParams().width = maxWidth + dp(80);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setUseAnEmojiVisible(boolean visible) {
        useAnEmojiButton.setVisibility(visible ? VISIBLE : GONE);
    }

    public void doOnGalleryAccessClick(Runnable listener) {
        galleryAccessButton.setOnClickListener(v -> listener.run());
    }

    public void doOnCameraAccess(Runnable listener) {
        cameraAccessButton.setOnClickListener(v -> listener.run());
    }

    public void doOnEmojiButton(Utilities.Callback<Long> listener) {
        useAnEmojiButton.setOnClickListener(v -> listener.run(emojiDocumentId));
    }

    public void setColors() {
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        galleryAccessButton.updateColors();
    }
}
