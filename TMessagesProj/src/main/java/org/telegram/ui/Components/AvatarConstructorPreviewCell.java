package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class AvatarConstructorPreviewCell extends FrameLayout {

    private AnimatedEmojiDrawable animatedEmojiDrawable;
    private AnimatedEmojiDrawable nextAnimatedEmojiDrawable;
    BackupImageView currentImage;
    BackupImageView nextImage;

    GradientTools currentBackgroundDrawable;
    GradientTools nextBackgroundDrawable;
    TextView textView;

    TLRPC.TL_emojiList emojiList;

    public final boolean forUser;
    private final int currentAccount = UserConfig.selectedAccount;

    int backgroundIndex = 0;
    int emojiIndex = 0;

    float progressToNext = 1f;
    private boolean isAllEmojiDrawablesLoaded;

    Runnable scheduleSwitchToNextRunnable = new Runnable() {
        @Override
        public void run() {
            AndroidUtilities.runOnUIThread(scheduleSwitchToNextRunnable, 1000);
            if (emojiList == null || emojiList.document_id.isEmpty() || progressToNext != 1f) {
                return;
            }
            if (!isAllEmojiDrawablesLoaded && (nextAnimatedEmojiDrawable.getImageReceiver() == null || !nextAnimatedEmojiDrawable.getImageReceiver().hasImageLoaded())) {
                return;
            }
            emojiIndex++;
            backgroundIndex++;

            if (emojiIndex > emojiList.document_id.size() - 1) {
                emojiIndex = 0;
            }
            if (backgroundIndex > AvatarConstructorFragment.defaultColors.length - 1) {
                backgroundIndex = 0;
            }
            animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, emojiList.document_id.get(emojiIndex));
            nextImage.setAnimatedEmojiDrawable(animatedEmojiDrawable);


            int color1 = AvatarConstructorFragment.defaultColors[backgroundIndex][0];
            int color2 = AvatarConstructorFragment.defaultColors[backgroundIndex][1];
            int color3 = AvatarConstructorFragment.defaultColors[backgroundIndex][2];
            int color4 = AvatarConstructorFragment.defaultColors[backgroundIndex][3];

            nextBackgroundDrawable = new GradientTools();
            nextBackgroundDrawable.setColors(color1, color2, color3, color4);

            progressToNext = 0f;
            preloadNextEmojiDrawable();
            invalidate();
        }
    };

    public AvatarConstructorPreviewCell(Context context, boolean forUser) {
        super(context);
        this.forUser = forUser;
        if (forUser) {
            emojiList = MediaDataController.getInstance(currentAccount).profileAvatarConstructorDefault;
        } else {
            emojiList = MediaDataController.getInstance(currentAccount).groupAvatarConstructorDefault;
        }

        if (emojiList == null || emojiList.document_id.isEmpty()) {
            ArrayList<TLRPC.TL_messages_stickerSet> installedEmojipacks = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
            emojiList = new TLRPC.TL_emojiList();
            if (installedEmojipacks.isEmpty()) {
                ArrayList<TLRPC.StickerSetCovered> featured = MediaDataController.getInstance(currentAccount).getFeaturedEmojiSets();
                for (int i = 0; i < featured.size(); i++) {
                    TLRPC.StickerSetCovered set = featured.get(i);
                    if (set.cover != null) {
                        emojiList.document_id.add(set.cover.id);
                    } else if (set instanceof TLRPC.TL_stickerSetFullCovered) {
                        TLRPC.TL_stickerSetFullCovered setFullCovered = ((TLRPC.TL_stickerSetFullCovered) set);
                        if (!setFullCovered.documents.isEmpty()) {
                            emojiList.document_id.add(setFullCovered.documents.get(0).id);
                        }
                    }
                }
            } else {
                for (int i = 0; i < installedEmojipacks.size(); i++) {
                    TLRPC.TL_messages_stickerSet set = installedEmojipacks.get(i);
                    if (!set.documents.isEmpty()) {
                        int index = Math.abs(Utilities.fastRandom.nextInt() % set.documents.size());
                        emojiList.document_id.add(set.documents.get(index).id);
                    }
                }
            }

        }
        currentImage = new BackupImageView(context);
        nextImage = new BackupImageView(context);
        addView(currentImage, LayoutHelper.createFrame(50, 50, Gravity.CENTER_HORIZONTAL));
        addView(nextImage, LayoutHelper.createFrame(50, 50, Gravity.CENTER_HORIZONTAL));

        if (emojiList != null && !emojiList.document_id.isEmpty()) {
            animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, emojiList.document_id.get(0));
            currentImage.setAnimatedEmojiDrawable(animatedEmojiDrawable);
            preloadNextEmojiDrawable();
        }

        int color1 = AvatarConstructorFragment.defaultColors[backgroundIndex][0];
        int color2 = AvatarConstructorFragment.defaultColors[backgroundIndex][1];
        int color3 = AvatarConstructorFragment.defaultColors[backgroundIndex][2];
        int color4 = AvatarConstructorFragment.defaultColors[backgroundIndex][3];

        currentBackgroundDrawable = new GradientTools();
        currentBackgroundDrawable.setColors(color1, color2, color3, color4);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTextColor(Theme.getColor(Theme.key_avatar_text));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(LocaleController.getString(R.string.UseEmoji));

        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.BOTTOM, 10, 10, 10, 10));

    }

    private void preloadNextEmojiDrawable() {
        if (isAllEmojiDrawablesLoaded) {
            return;
        }
        int nextEmojiIndex = emojiIndex + 1;
        if (nextEmojiIndex > emojiList.document_id.size() - 1) {
            isAllEmojiDrawablesLoaded = true;
            return;
        }
        nextAnimatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, emojiList.document_id.get(nextEmojiIndex));
        nextAnimatedEmojiDrawable.preload();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int availableHeight = textView.getTop();
        int imageHeight = (int) (availableHeight * 0.7f);
        int padding = (int) ((availableHeight - imageHeight) * 0.7f);

        currentImage.getLayoutParams().width = currentImage.getLayoutParams().height = imageHeight;
        nextImage.getLayoutParams().width = nextImage.getLayoutParams().height = imageHeight;
        ((LayoutParams) currentImage.getLayoutParams()).topMargin = padding;
        ((LayoutParams) nextImage.getLayoutParams()).topMargin = padding;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (currentBackgroundDrawable != null) {
            currentBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
        if (nextBackgroundDrawable != null) {
            nextBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
        if (progressToNext == 1f) {
            currentBackgroundDrawable.paint.setAlpha(255);
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), currentBackgroundDrawable.paint);
            currentImage.setAlpha(1f);
            currentImage.setScaleX(1f);
            currentImage.setScaleY(1f);
            nextImage.setAlpha(0f);
        } else {
            float progressInternal = CubicBezierInterpolator.DEFAULT.getInterpolation(progressToNext);

            currentBackgroundDrawable.paint.setAlpha(255);
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), currentBackgroundDrawable.paint);
            nextBackgroundDrawable.paint.setAlpha((int) (255 * progressInternal));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), nextBackgroundDrawable.paint);

            progressToNext += 16 / 250f;

            currentImage.setAlpha(1f - progressInternal);
            currentImage.setScaleX(1f - progressInternal);
            currentImage.setScaleY(1f - progressInternal);
            currentImage.setPivotY(0);
            nextImage.setAlpha(progressInternal);
            nextImage.setScaleX(progressInternal);
            nextImage.setScaleY(progressInternal);
            nextImage.setPivotY(nextImage.getMeasuredHeight());
            if (progressToNext > 1f) {
                progressToNext = 1f;
                currentBackgroundDrawable = nextBackgroundDrawable;

                BackupImageView tmp = currentImage;
                currentImage = nextImage;
                nextImage = tmp;
            }
            invalidate();
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AndroidUtilities.runOnUIThread(scheduleSwitchToNextRunnable, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(scheduleSwitchToNextRunnable);
    }

    public AvatarConstructorFragment.BackgroundGradient getBackgroundGradient() {
        AvatarConstructorFragment.BackgroundGradient backgroundGradient = new AvatarConstructorFragment.BackgroundGradient();

        backgroundGradient.color1 = AvatarConstructorFragment.defaultColors[backgroundIndex][0];
        backgroundGradient.color2 = AvatarConstructorFragment.defaultColors[backgroundIndex][1];
        backgroundGradient.color3 = AvatarConstructorFragment.defaultColors[backgroundIndex][2];
        backgroundGradient.color4 = AvatarConstructorFragment.defaultColors[backgroundIndex][3];

        return backgroundGradient;
    }

    public AnimatedEmojiDrawable getAnimatedEmoji() {
        return animatedEmojiDrawable;
    }
}
