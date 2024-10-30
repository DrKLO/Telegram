package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.Locale;

public class ChatGreetingsView extends LinearLayout {

    private TLRPC.Document preloadedGreetingsSticker;
    private TextView titleView;
    private TextView descriptionView;
    private Listener listener;

    private final int currentAccount;

    public FrameLayout stickerContainer;
    public BackupImageView stickerToSendView;
    public BackupImageView nextStickerToSendView;
    private final Theme.ResourcesProvider resourcesProvider;
    boolean wasDraw;

    public ChatGreetingsView(Context context, TLRPC.User user, int currentAccount, TLRPC.Document sticker, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        setPadding(0, dp(8), 0, 0);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setGravity(Gravity.CENTER);

        descriptionView = new TextView(context);
        descriptionView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);

        stickerContainer = new FrameLayout(context);
        stickerToSendView = new BackupImageView(context);
        stickerToSendView.getImageReceiver().setAspectFit(true);
        stickerContainer.addView(stickerToSendView, LayoutHelper.createFrame(112, 112));
        ScaleStateListAnimator.apply(stickerToSendView);

        nextStickerToSendView = new BackupImageView(context);
        nextStickerToSendView.getImageReceiver().setAspectFit(true);
        stickerContainer.addView(nextStickerToSendView, LayoutHelper.createFrame(112, 112));
        nextStickerToSendView.setVisibility(View.GONE);
        nextStickerToSendView.setAlpha(0f);
        ScaleStateListAnimator.apply(nextStickerToSendView);
        updateLayout();

        updateColors();

        setText(getString(R.string.NoMessages), getString(R.string.NoMessagesGreetingsDescription));

        preloadedGreetingsSticker = sticker;
        if (preloadedGreetingsSticker == null) {
            preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker();
        }
    }

    public void setText(CharSequence title, CharSequence description) {
        titleView.setText(title);
        descriptionView.setText(description);
        descriptionView.setMaxWidth(HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint()));
        stickerToSendView.setContentDescription(descriptionView.getText());
    }

    private RLottieImageView premiumIconView;
    private TextView premiumTextView;
    private TextView premiumButtonView;

    private boolean premiumLock;
    public void setPremiumLock(boolean lock, long dialogId) {
        if (premiumLock == lock) return;
        premiumLock = lock;
        if (premiumLock) {
            if (premiumIconView == null) {
                premiumIconView = new RLottieImageView(getContext());
                premiumIconView.setScaleType(ImageView.ScaleType.CENTER);
                premiumIconView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                premiumIconView.setBackground(Theme.createCircleDrawable(dp(78), 0x1c000000));
                premiumIconView.setAnimation(R.raw.large_message_lock, 80, 80);
                premiumIconView.setOnClickListener(v -> {
                    premiumIconView.setProgress(0);
                    premiumIconView.playAnimation();
                });
            }
            premiumIconView.playAnimation();
            if (premiumTextView == null) {
                premiumTextView = new TextView(getContext());
                premiumTextView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                premiumTextView.setGravity(Gravity.CENTER);
                premiumTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            }
            String username = "";
            if (dialogId >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user != null) {
                    username = UserObject.getUserName(user);
                }
            }
            String text;
            if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
                text = formatString(R.string.MessageLockedPremiumLocked, username);
            } else {
                text = formatString(R.string.MessageLockedPremium, username);
            }
            premiumTextView.setText(AndroidUtilities.replaceTags(text));
            premiumTextView.setMaxWidth(HintView2.cutInFancyHalf(premiumTextView.getText(), premiumTextView.getPaint()));
            premiumTextView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            premiumTextView.setLineSpacing(dp(2f), 1f);
            if (premiumButtonView == null) {
                premiumButtonView = new TextView(getContext()) {
                    StarParticlesView.Drawable starParticlesDrawable;

                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        super.onLayout(changed, left, top, right, bottom);
                        starParticlesDrawable = new StarParticlesView.Drawable(10);
                        starParticlesDrawable.type = 100;
                        starParticlesDrawable.isCircle = false;
                        starParticlesDrawable.roundEffect = true;
                        starParticlesDrawable.useRotate = false;
                        starParticlesDrawable.useBlur = true;
                        starParticlesDrawable.checkBounds = true;
                        starParticlesDrawable.size1 = 1;
                        starParticlesDrawable.k1 = starParticlesDrawable.k2 = starParticlesDrawable.k3 = 0.98f;
                        starParticlesDrawable.paused = false;
                        starParticlesDrawable.speedScale = 0f;
                        starParticlesDrawable.minLifeTime = 750;
                        starParticlesDrawable.randLifeTime = 750;
                        starParticlesDrawable.init();

                        AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                        starParticlesDrawable.rect.set(AndroidUtilities.rectTmp);
                        starParticlesDrawable.rect2.set(AndroidUtilities.rectTmp);
                        starParticlesDrawable.resetPositions();

                        clipPath.reset();
                        clipPath.addRoundRect(AndroidUtilities.rectTmp, getHeight() / 2f, getHeight() / 2f, Path.Direction.CW);
                    }

                    private final Path clipPath = new Path();
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (starParticlesDrawable != null) {
                            canvas.save();
                            canvas.clipPath(clipPath);
                            starParticlesDrawable.onDraw(canvas);
                            canvas.restore();
                            invalidate();
                        }
                        super.onDraw(canvas);
                    }
                };
                premiumButtonView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                premiumButtonView.setGravity(Gravity.CENTER);
                premiumButtonView.setTypeface(AndroidUtilities.bold());
                premiumButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                premiumButtonView.setPadding(dp(13), dp(6.66f), dp(13), dp(7));
                premiumButtonView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(15), 0x1e000000, 0x33000000));

                ScaleStateListAnimator.apply(premiumButtonView);
            }
            premiumButtonView.setText(LocaleController.getString(R.string.MessagePremiumUnlock));
            premiumButtonView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            premiumButtonView.setOnClickListener(v -> {
                BaseFragment fragment = LaunchActivity.getLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(new PremiumPreviewFragment("contact"));
                }
            });
        }
        updateLayout();
    }

    private void updateLayout() {
        removeAllViews();
        if (premiumLock) {
            addView(premiumIconView, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 9, 20, 9));
            final boolean premiumLocked = MessagesController.getInstance(currentAccount).premiumFeaturesBlocked();
            addView(premiumTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 0, 20, premiumLocked ? 13 : 9));
            if (!premiumLocked) {
                addView(premiumButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 2, 20, 13));
            }
        } else {
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 6));
            addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 6));
            addView(stickerContainer, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL, 16, 10, 16, 16));
        }
    }

    public void setSticker(TLRPC.Document sticker) {
        if (sticker == null) {
            return;
        }
        wasDraw = true;
        nextStickerToSendView.clearImage();
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f);
        if (svgThumb != null) {
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), svgThumb, 0, sticker);
        } else {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), ImageLocation.getForDocument(thumb, sticker), null, 0, sticker);
        }
        stickerToSendView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGreetings(sticker);
            }
        });
    }

    public void setSticker(String stickerPath) {
        if (stickerPath == null) {
            return;
        }
        wasDraw = true;
        nextStickerToSendView.clearImage();
        stickerToSendView.setImage(ImageLocation.getForPath(stickerPath), "256_256", null, null, 0, null);
    }

    public void setNextSticker(TLRPC.Document sticker, Runnable whenDone) {
        if (sticker == null) {
            return;
        }
        if (togglingStickersAnimator != null) {
            togglingStickersAnimator.cancel();
        }
        nextStickerToSendView.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            private boolean waited;
            @Override
            public void didSetImageBitmap(int type, String key, Drawable drawable) {
                if (waited) {
                    return;
                }
                if ((type == ImageReceiver.TYPE_IMAGE || type == ImageReceiver.TYPE_MEDIA) && drawable != null) {
                    waited = true;
                    if (drawable instanceof RLottieDrawable && ((RLottieDrawable) drawable).bitmapsCache != null && ((RLottieDrawable) drawable).bitmapsCache.needGenCache()) {
                        ((RLottieDrawable) drawable).whenCacheDone = () -> {
                            toggleToNextSticker();
                            if (whenDone != null) {
                                whenDone.run();
                            }
                        };
                    } else {
                        toggleToNextSticker();
                        if (whenDone != null) {
                            whenDone.run();
                        }
                    }
                }
            }

            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {}
        });
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f);
        if (svgThumb != null) {
            nextStickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), svgThumb, 0, sticker);
        } else {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
            nextStickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), ImageLocation.getForDocument(thumb, sticker), null, 0, sticker);
        }
        nextStickerToSendView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGreetings(sticker);
            }
        });
    }

    private AnimatorSet togglingStickersAnimator;
    private void toggleToNextSticker() {
        if (togglingStickersAnimator != null) {
            togglingStickersAnimator.cancel();
        }

        nextStickerToSendView.setVisibility(View.VISIBLE);
        stickerToSendView.setVisibility(View.VISIBLE);

        togglingStickersAnimator = new AnimatorSet();
        togglingStickersAnimator.setDuration(420);
        togglingStickersAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        togglingStickersAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;

                BackupImageView temp = stickerToSendView;
                stickerToSendView = nextStickerToSendView;
                nextStickerToSendView = temp;

                nextStickerToSendView.setVisibility(View.GONE);
                nextStickerToSendView.setAlpha(0f);
                stickerToSendView.setVisibility(View.VISIBLE);
                stickerToSendView.setAlpha(1f);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }
        });
        togglingStickersAnimator.playTogether(
            ObjectAnimator.ofFloat(nextStickerToSendView, View.ALPHA, 0f, 1f),
            ObjectAnimator.ofFloat(nextStickerToSendView, View.SCALE_X, .7f, 1f),
            ObjectAnimator.ofFloat(nextStickerToSendView, View.SCALE_Y, .7f, 1f),
            ObjectAnimator.ofFloat(nextStickerToSendView, View.TRANSLATION_Y, -dp(24), 0),

            ObjectAnimator.ofFloat(stickerToSendView, View.ALPHA, 1f, 0f),
            ObjectAnimator.ofFloat(stickerToSendView, View.SCALE_X, 1f, .7f),
            ObjectAnimator.ofFloat(stickerToSendView, View.SCALE_Y, 1f, .7f),
            ObjectAnimator.ofFloat(stickerToSendView, View.TRANSLATION_Y, 0, dp(24))
        );
        togglingStickersAnimator.start();
    }

    public static String createFilter(TLRPC.Document document) {
        float maxHeight;
        float maxWidth;
        int photoWidth = 0;
        int photoHeight = 0;
        if (AndroidUtilities.isTablet()) {
            maxHeight = maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f;
        } else {
            maxHeight = maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                photoWidth = attribute.w;
                photoHeight = attribute.h;
                break;
            }
        }
        if (MessageObject.isAnimatedStickerDocument(document, true) && photoWidth == 0 && photoHeight == 0) {
            photoWidth = photoHeight = 512;
        }

        if (photoWidth == 0) {
            photoHeight = (int) maxHeight;
            photoWidth = photoHeight + dp(100);
        }
        photoHeight *= maxWidth / photoWidth;
        photoWidth = (int) maxWidth;
        if (photoHeight > maxHeight) {
            photoWidth *= maxHeight / photoHeight;
            photoHeight = (int) maxHeight;
        }

        int w = (int) (photoWidth / AndroidUtilities.density);
        int h = (int) (photoHeight / AndroidUtilities.density);
        return String.format(Locale.US, "%d_%d", w, h);
    }

    private void updateColors() {
        titleView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        descriptionView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onGreetings(TLRPC.Document sticker);
    }

    boolean ignoreLayot;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayot = true;
        if (!preview) {
            descriptionView.setVisibility(View.VISIBLE);
        }
        stickerToSendView.setVisibility(View.VISIBLE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() > MeasureSpec.getSize(heightMeasureSpec) && !preview) {
            descriptionView.setVisibility(View.GONE);
            stickerToSendView.setVisibility(View.GONE);
        } else {
            if (!preview) {
                descriptionView.setVisibility(View.VISIBLE);
            }
            stickerToSendView.setVisibility(View.VISIBLE);
        }
        ignoreLayot = false;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean preview;
    public void setPreview(CharSequence title, CharSequence message) {
        preview = true;
        titleView.setText(TextUtils.isEmpty(title == null ? null : title.toString().trim()) ? getString(R.string.NoMessages) : title);
        descriptionView.setText(TextUtils.isEmpty(message == null ? null : message.toString().trim()) ? getString(R.string.NoMessagesGreetingsDescription) : message);
        descriptionView.setMaxWidth(
            descriptionView.getText().length() > 60 ?
                Math.min((int) (AndroidUtilities.displaySize.x * .5f), HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint())) :
                (int) (AndroidUtilities.displaySize.x * .5f)
        );
    }

    private float viewTop;
    private float viewTranslationX;
    private int backgroundHeight;
    private boolean visiblePartSet;

    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
        viewTranslationX = 0;
    }

    private boolean disableBackground;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!disableBackground) {
            if (resourcesProvider != null) {
                resourcesProvider.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(16), dp(16), Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider));
            }
        }
        if (!wasDraw) {
            wasDraw = true;
            setSticker(preloadedGreetingsSticker);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayot) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        fetchSticker();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    private void fetchSticker() {
        if (preloadedGreetingsSticker == null) {
            preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker();
            if (wasDraw) {
                setSticker(preloadedGreetingsSticker);
            }
        }
    }

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
        disableBackground = true;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public static void showPremiumSheet(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), 0, dp(16), 0);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setAnimation(R.raw.large_message_lock, 80, 80);
        imageView.playAnimation();
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        final boolean premiumLocked = MessagesController.getInstance(currentAccount).premiumFeaturesBlocked();

        TextView headerView = new TextView(context);
        headerView.setTypeface(AndroidUtilities.bold());
        headerView.setGravity(Gravity.CENTER);
        headerView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerView.setText(LocaleController.getString(premiumLocked ? R.string.PremiumMessageHeaderLocked : R.string.PremiumMessageHeader));
        layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 0));

        TextView descriptionView = new TextView(context);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        String username = "";
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            username = UserObject.getFirstName(user);
        }
        descriptionView.setText(AndroidUtilities.replaceTags(formatString(premiumLocked ? R.string.PremiumMessageTextLocked : R.string.PremiumMessageText, username, username)));
        layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 9, 12, 19));

        if (!premiumLocked) {
            PremiumButtonView button2 = new PremiumButtonView(context, true, resourcesProvider);
            button2.setOnClickListener(v2 -> {
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment != null) {
                    lastFragment.presentFragment(new PremiumPreviewFragment("contact"));
                    sheet.dismiss();
                }
            });
            button2.setOverlayText(LocaleController.getString(R.string.PremiumMessageButton), false, false);
            layout.addView(button2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        }

        sheet.setCustomView(layout);
        sheet.show();
    }
}
