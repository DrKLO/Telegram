package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class StickerEmptyView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static int STICKER_TYPE_NO_CONTACTS = 0;
    public final static int STICKER_TYPE_SEARCH = 1;
    public final static int STICKER_TYPE_ALBUM = 11;
    public final static int STICKER_TYPE_PRIVACY = 12;
    public final static int STICKER_TYPE_DONE = 16;

    public LinearLayout linearLayout;
    public BackupImageView stickerView;
    private RadialProgressView progressBar;
    public final TextView title;
    public final TextView subtitle;
    private boolean progressShowing;
    private final Theme.ResourcesProvider resourcesProvider;

    private int stickerType;

    public final View progressView;

    int keyboardSize;

    int currentAccount = UserConfig.selectedAccount;

    boolean preventMoving;

    Runnable showProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (progressView != null) {
                if (progressView.getVisibility() != View.VISIBLE) {
                    progressView.setVisibility(View.VISIBLE);
                    progressView.setAlpha(0f);
                }
                progressView.animate().setListener(null).cancel();
                progressView.animate().alpha(1f).setDuration(150).start();
            } else {
                progressBar.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start();
            }
        }
    };
    private boolean animateLayoutChange;

    public StickerEmptyView(@NonNull Context context, View progressView, int type) {
        this(context, progressView, type, null);
    }

    public StickerEmptyView(@NonNull Context context, View progressView, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.progressView = progressView;
        stickerType = type;

        linearLayout = new LinearLayout(context) {
            @Override
            public void setVisibility(int visibility) {
                if (getVisibility() == View.GONE && visibility == View.VISIBLE) {
                    setSticker();
                    if (LiteMode.isEnabled(LiteMode.FLAGS_ANIMATED_STICKERS)) {
                        stickerView.getImageReceiver().startAnimation();
                    }
                } else if (visibility == View.GONE) {
                    stickerView.getImageReceiver().clearImage();
                }
                super.setVisibility(visibility);
            }
        };
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        stickerView = new BackupImageView(context);
        stickerView.setOnClickListener(view -> stickerView.getImageReceiver().startAnimation());

        title = new TextView(context);

        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setTag(Theme.key_windowBackgroundWhiteBlackText);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setGravity(Gravity.CENTER);

        subtitle = new TextView(context);
        subtitle.setTag(Theme.key_windowBackgroundWhiteGrayText);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);

        linearLayout.addView(stickerView, LayoutHelper.createLinear(117, 117, Gravity.CENTER_HORIZONTAL));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 46, 0, 46, 30));

        if (progressView == null) {
            progressBar = new RadialProgressView(context, resourcesProvider);
            progressBar.setAlpha(0);
            progressBar.setScaleY(0.5f);
            progressBar.setScaleX(0.5f);
            addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    private int lastH;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if ((animateLayoutChange || preventMoving) && lastH > 0 && lastH != getMeasuredHeight()) {
            float y = (lastH - getMeasuredHeight()) / 2f;
            linearLayout.setTranslationY(linearLayout.getTranslationY() + y);
            if (!preventMoving) {
                linearLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
            }
            if (progressBar != null) {
                progressBar.setTranslationY(progressBar.getTranslationY() + y);
                if (!preventMoving) {
                    progressBar.animate().translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
                }
            }
        }
        lastH = getMeasuredHeight();
    }

    int colorKey1 = Theme.key_emptyListPlaceholder;

    public void setColors(int titleKey, int subtitleKey, int key1, int key2) {
        title.setTag(titleKey);
        title.setTextColor(getThemedColor(titleKey));

        subtitle.setTag(subtitleKey);
        subtitle.setTextColor(getThemedColor(subtitleKey));
        colorKey1 = key1;
    }

    @Override
    public void setVisibility(int visibility) {
        if (getVisibility() != visibility) {
            if (visibility == VISIBLE) {
                if (progressShowing) {
                    linearLayout.animate().alpha(0f).scaleY(0.8f).scaleX(0.8f).setDuration(150).start();
                    progressView.animate().setListener(null).cancel();
                    progressView.setVisibility(VISIBLE);
                    progressView.setAlpha(1f);
                    //showProgressRunnable.run();
                } else {
                    linearLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start();
                    if (progressView != null) {
                        progressView.animate().setListener(null).cancel();
                        progressView.animate().setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                progressView.setVisibility(View.GONE);
                            }
                        }).alpha(0f).setDuration(150).start();
                    } else {
                        progressBar.animate().alpha(0f).scaleY(0.5f).scaleX(0.5f).setDuration(150).start();
                    }
                    stickerView.getImageReceiver().startAnimation();
                }
            }
        }
        super.setVisibility(visibility);
        if (getVisibility() == VISIBLE) {
            setSticker();
        } else {
            lastH = 0;
            linearLayout.setAlpha(0f);
            linearLayout.setScaleX(0.8f);
            linearLayout.setScaleY(0.8f);

            if (progressView != null) {
                progressView.animate().setListener(null).cancel();
                progressView.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressView.setVisibility(View.GONE);
                    }
                }).alpha(0f).setDuration(150).start();
            } else {
                progressBar.setAlpha(0f);
                progressBar.setScaleX(0.5f);
                progressBar.setScaleY(0.5f);
            }
            stickerView.getImageReceiver().stopAnimation();
            stickerView.getImageReceiver().clearImage();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            setSticker();
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    private void setSticker() {
        String imageFilter = null;
        TLRPC.Document document = null;
        TLRPC.TL_messages_stickerSet set = null;
        if (stickerType == STICKER_TYPE_DONE) {
            document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker("\uD83D\uDC4D");
        } else {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            }
            if (set != null && stickerType >= 0 && stickerType < set.documents.size()) {
                document = set.documents.get(stickerType);
            }
            imageFilter = "130_130";
        }

        if (!LiteMode.isEnabled(LiteMode.FLAGS_ANIMATED_STICKERS)) {
            imageFilter += "_firstframe";
        }

        if (document != null) {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, colorKey1, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }

            ImageLocation imageLocation = ImageLocation.getForDocument(document);
            stickerView.setImage(imageLocation, imageFilter, "tgs", svgThumb, set);
            if (stickerType == 9 || stickerType == STICKER_TYPE_NO_CONTACTS) {
                stickerView.getImageReceiver().setAutoRepeat(1);
            } else {
                stickerView.getImageReceiver().setAutoRepeat(2);
            }
        } else {
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null);
            stickerView.getImageReceiver().clearImage();
        }
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME.equals(name) && getVisibility() == VISIBLE) {
                setSticker();
            }
        }
    }

    public void setKeyboardHeight(int keyboardSize, boolean animated) {
        if (this.keyboardSize != keyboardSize) {
            if (getVisibility() != View.VISIBLE) {
                animated = false;
            }
            this.keyboardSize = keyboardSize;
            float y = -(keyboardSize >> 1) + (keyboardSize > 0 ? AndroidUtilities.dp(20) : 0);
            if (animated) {
                linearLayout.animate().translationY(y).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
                if (progressBar != null) {
                    progressBar.animate().translationY(y).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
                }
            } else {
                linearLayout.setTranslationY(y);
                if (progressBar != null) {
                    progressBar.setTranslationY(y);
                }
            }
        }
    }

    public void showProgress(boolean show) {
        showProgress(show, true);
    }

    public void showProgress(boolean show, boolean animated) {
        if (progressShowing != show) {
            progressShowing = show;
            if (getVisibility() != View.VISIBLE) {
                return;
            }
            if (animated) {
                if (show) {
                    linearLayout.animate().alpha(0f).scaleY(0.8f).scaleX(0.8f).setDuration(150).start();
                    showProgressRunnable.run();
                } else {
                    linearLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start();
                    if (progressView != null) {
                        progressView.animate().setListener(null).cancel();
                        progressView.animate().setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                progressView.setVisibility(View.GONE);
                            }
                        }).alpha(0).setDuration(150).start();
                    } else {
                        progressBar.animate().alpha(0f).scaleY(0.5f).scaleX(0.5f).setDuration(150).start();
                    }
                    stickerView.getImageReceiver().startAnimation();
                }
            } else {
                if (show) {
                    linearLayout.animate().cancel();
                    linearLayout.setAlpha(0);
                    linearLayout.setScaleX(0.8f);
                    linearLayout.setScaleY(0.8f);
                    if (progressView != null) {
                        progressView.animate().setListener(null).cancel();
                        progressView.setAlpha(1);
                        progressView.setVisibility(View.VISIBLE);
                    } else {
                        progressBar.setAlpha(1f);
                        progressBar.setScaleX(1f);
                        progressBar.setScaleY(1f);
                    }
                } else {
                    linearLayout.animate().cancel();
                    linearLayout.setAlpha(1f);
                    linearLayout.setScaleX(1f);
                    linearLayout.setScaleY(1f);
                    if (progressView != null) {
                        progressView.animate().setListener(null).cancel();
                        progressView.setVisibility(View.GONE);
                    } else {
                        progressBar.setAlpha(0f);
                        progressBar.setScaleX(0.5f);
                        progressBar.setScaleY(0.5f);
                    }
                }
            }
        }
    }

    public void setAnimateLayoutChange(boolean animate) {
        this.animateLayoutChange = animate;
    }

    public void setPreventMoving(boolean preventMoving) {
        this.preventMoving = preventMoving;
        if (!preventMoving) {
            linearLayout.setTranslationY(0);
            if (progressBar != null) {
                progressBar.setTranslationY(0);
            }
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setStickerType(int stickerType) {
        if (this.stickerType != stickerType) {
            this.stickerType = stickerType;
            setSticker();
        }
    }
}
