package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.StarParticlesView;

@SuppressLint("ViewConstructor")
public class HeaderCell extends FrameLayout {

    private final GLIconTextureView iconTextureView;
    private final StarParticlesView starParticlesView;
    private final TextView titleView;
    private final LinkSpanDrawable.LinksTextView subtitleView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout linearLayout;
    private LinkSpanDrawable.LinkCollector links;

    private final Paint[] paints;

    public HeaderCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                setPaused(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                setPaused(true);
            }
        };
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_premiumGradient2, resourcesProvider), Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.5f));
        iconTextureView.setBackgroundBitmap(bitmap);
        iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient2;
        iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient1;
        iconTextureView.mRenderer.updateColors();
        linearLayout.addView(iconTextureView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));

        starParticlesView = new StarParticlesView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                drawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(52));
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                starParticlesView.setPaused(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                starParticlesView.setPaused(true);
            }
        };

        paints = new Paint[20];
        updatePaints(0);

        starParticlesView.drawable.useGradient = false;
        starParticlesView.drawable.useBlur = false;
        starParticlesView.drawable.forceMaxAlpha = true;
        starParticlesView.drawable.checkBounds = true;
        starParticlesView.drawable.getPaint = i -> paints[i % paints.length];
        starParticlesView.drawable.init();
        iconTextureView.setStarParticlesView(starParticlesView);

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, -8, 24, 0));

        subtitleView = new LinkSpanDrawable.LinksTextView(context, links = new LinkSpanDrawable.LinkCollector(this), resourcesProvider);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setMovementMethod(LinkMovementMethod.getInstance());
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        linearLayout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 8, 24, 18));

        setClipChildren(false);
        addView(starParticlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 234, Gravity.TOP));
        addView(linearLayout);
        setWillNotDraw(false);
    }

    public void setBoostViaGifsText(TLRPC.Chat currentChat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float cornerRadius = AndroidUtilities.dp(12);
                    outline.setRoundRect(0, 0, view.getWidth(), (int) (view.getHeight() + cornerRadius), cornerRadius);
                }
            });
            setClipToOutline(true);
        }
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.topMargin = -AndroidUtilities.dp(6);
        setLayoutParams(lp);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        titleView.setText(LocaleController.formatString("BoostingBoostsViaGifts", R.string.BoostingBoostsViaGifts));
        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(currentChat);
        subtitleView.setText(LocaleController.formatString(isChannel ? R.string.BoostingGetMoreBoost2 : R.string.BoostingGetMoreBoostGroup));
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
    }

    public void setUsedGiftLinkText() {
        titleView.setText(LocaleController.formatString("BoostingUsedGiftLink", R.string.BoostingUsedGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkUsed", R.string.BoostingLinkUsed)));
    }

    public void setGiftLinkText() {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkAllows", R.string.BoostingLinkAllows)));
    }

    public void setUnclaimedText() {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkAllowsAnyone", R.string.BoostingLinkAllowsAnyone)));
    }

    public void setGiftLinkToUserText(long toUserId, Utilities.Callback<TLObject> onObjectClicked) {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));

        CharSequence description = AndroidUtilities.replaceTags(LocaleController.getString(R.string.BoostingLinkAllowsToUser));
        TLRPC.User toUser = MessagesController.getInstance(UserConfig.selectedAccount).getUser(toUserId);

        SpannableStringBuilder link = AndroidUtilities.replaceSingleTag(
                "**" + UserObject.getUserName(toUser) + "**",
                Theme.key_chat_messageLinkIn, REPLACING_TAG_TYPE_LINKBOLD,
                () -> onObjectClicked.run(toUser),
                resourcesProvider
        );
        subtitleView.setText(AndroidUtilities.replaceCharSequence("%1$s", description, link));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float y = iconTextureView.getTop() + iconTextureView.getMeasuredHeight() / 2f;
        starParticlesView.setTranslationY(y - starParticlesView.getMeasuredHeight() / 2f);
    }

    public void setPaused(boolean value) {
        iconTextureView.setPaused(value);
        starParticlesView.setPaused(value);
    }

    private ValueAnimator goldenAnimator;
    public void setStars(boolean stars) {
        if (goldenAnimator != null) {
            goldenAnimator.cancel();
        }
        final float from = iconTextureView.mRenderer.golden;
        final float to = stars ? 1f : 0f;
        goldenAnimator = ValueAnimator.ofFloat(0, 1f);
        final float[] lastT = new float[] { 0 };
        iconTextureView.cancelIdleAnimation();
        iconTextureView.cancelAnimatons();
        iconTextureView.startBackAnimation();
        goldenAnimator.addUpdateListener(anm -> {
            float t = (float) anm.getAnimatedValue();
            float dt = t - lastT[0];
            lastT[0] = t;
            iconTextureView.mRenderer.golden = lerp(from, to, t);
            iconTextureView.mRenderer.angleX3 += dt * 360f * (stars ? +1 : -1);
            iconTextureView.mRenderer.updateColors();
            updatePaints(iconTextureView.mRenderer.golden);
        });
        goldenAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                float t = 1f;
                float dt = t - lastT[0];
                lastT[0] = t;
                iconTextureView.mRenderer.golden = lerp(from, to, t);
                iconTextureView.mRenderer.angleX3 += dt * 360f * (stars ? +1 : -1);
                iconTextureView.mRenderer.updateColors();
                updatePaints(iconTextureView.mRenderer.golden);

                iconTextureView.scheduleIdleAnimation(750);
            }
        });
        goldenAnimator.setDuration(680);
        goldenAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        goldenAnimator.start();
    }

    private void updatePaints(float golden) {
        final int premiumColor1 = Theme.getColor(Theme.key_premiumGradient1, resourcesProvider);
        final int premiumColor2 = Theme.getColor(Theme.key_premiumGradient2, resourcesProvider);
        final int color1 = ColorUtils.blendARGB(premiumColor1, 0xFFFA5416, golden);
        final int color2 = ColorUtils.blendARGB(premiumColor2, 0xFFFFC837, golden);
        for (int i = 0; i < paints.length; ++i) {
            paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paints[i].setColorFilter(new PorterDuffColorFilter(
                ColorUtils.blendARGB(color1, color2, i / (float) (paints.length - 1)),
                PorterDuff.Mode.SRC_IN)
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (links != null) {
            canvas.save();
            canvas.translate(subtitleView.getLeft(), subtitleView.getTop());
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();
        }
    }
}
