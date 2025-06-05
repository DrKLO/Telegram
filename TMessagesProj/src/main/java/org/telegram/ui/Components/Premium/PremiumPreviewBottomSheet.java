package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.boosts.cells.TextInfoCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;

public class PremiumPreviewBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    protected ArrayList<PremiumPreviewFragment.PremiumFeatureData> premiumFeatures = new ArrayList<>();
    int currentAccount;
    protected TLRPC.User user;
    protected GiftPremiumBottomSheet.GiftTier giftTier;
    boolean isOutboundGift;

    PremiumFeatureCell dummyCell;
    int totalGradientHeight;

    protected int rowCount;
    protected int paddingRow;
    protected int additionStartRow;
    protected int additionEndRow;
    protected int featuresStartRow;
    protected int featuresEndRow;
    protected int sectionRow;
    protected int helpUsRow;
    protected int buttonRow;
    protected int termsRow;

    FireworksOverlay fireworksOverlay;
    PremiumGradient.PremiumGradientTools gradientTools;
    StarParticlesView starParticlesView;
    GLIconTextureView iconTextureView;
    ViewGroup iconContainer;
    BaseFragment fragment;

    public Integer accentColor;
    public float startEnterFromX;
    public float startEnterFromY;
    public float startEnterFromX1;
    public float startEnterFromY1;
    public float startEnterFromScale;
    public View startEnterFromView;
    public View overrideTitleIcon;
    public TLRPC.InputStickerSet statusStickerSet;
    public TLRPC.TL_emojiStatusCollectible emojiStatusCollectible;
    public boolean isEmojiStatus;

    int[] coords = new int[2];
    float enterTransitionProgress = 0;
    boolean enterTransitionInProgress;
    ValueAnimator enterAnimator;

    boolean animateConfetti;
    boolean animateConfettiWithStars;
    FrameLayout buttonContainer;
    FrameLayout bulletinContainer;

    public PremiumPreviewBottomSheet(BaseFragment fragment, int currentAccount, TLRPC.User user, Theme.ResourcesProvider resourcesProvider) {
        this(fragment, currentAccount, user, null, resourcesProvider);
    }

    public PremiumPreviewBottomSheet(BaseFragment fragment, int currentAccount, TLRPC.User user, GiftPremiumBottomSheet.GiftTier gift, Theme.ResourcesProvider resourcesProvider) {
        super(fragment, false, false, false, resourcesProvider);
        fixNavigationBar();
        this.fragment = fragment;
        topPadding = 0.26f;
        this.user = user;
        this.currentAccount = currentAccount;
        this.giftTier = gift;
        dummyCell = new PremiumFeatureCell(getContext());
        PremiumPreviewFragment.fillPremiumFeaturesList(premiumFeatures, currentAccount, false);

        if (giftTier != null || UserConfig.getInstance(currentAccount).isPremium()) {
            buttonContainer.setVisibility(View.GONE);
        }

        gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4);
        gradientTools.exactly = true;
        gradientTools.x1 = 0;
        gradientTools.y1 = 1f;
        gradientTools.x2 = 0;
        gradientTools.y2 = 0f;
        gradientTools.cx = 0;
        gradientTools.cy = 0;

        updateRows();

        recyclerListView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof PremiumFeatureCell) {
                PremiumFeatureCell cell = (PremiumFeatureCell) view;
                PremiumPreviewFragment.sentShowFeaturePreview(currentAccount, cell.data.type);
                showDialog(new PremiumFeatureBottomSheet(fragment, cell.data.type, false));
            }
            onAdditionItemClicked(view);
        });

        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
        PremiumPreviewFragment.sentShowScreenStat("profile");

        fireworksOverlay = new FireworksOverlay(getContext());
        container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bulletinContainer = new FrameLayout(getContext());
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
    }

    protected void onAdditionItemClicked(View view) {

    }

    protected void updateRows() {
        paddingRow = rowCount++;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        sectionRow = rowCount++;
        if (!UserConfig.getInstance(currentAccount).isPremium() && giftTier == null) {
            buttonRow = rowCount++;
        }
    }

    public PremiumPreviewBottomSheet setOutboundGift(boolean outboundGift) {
        isOutboundGift = outboundGift;
        return this;
    }

    public PremiumPreviewBottomSheet setAnimateConfetti(boolean animateConfetti) {
        this.animateConfetti = animateConfetti;
        return this;
    }

    public PremiumPreviewBottomSheet setAnimateConfettiWithStars(boolean animateConfettiWithStars) {
        this.animateConfettiWithStars = animateConfettiWithStars;
        return this;
    }

    @Override
    public boolean showDialog(Dialog dialog) {
        if (iconTextureView != null) {
            iconTextureView.setDialogVisible(true);
        }
        starParticlesView.setPaused(true);
        dialog.setOnDismissListener(dialog1 -> {
            if (iconTextureView != null) {
                iconTextureView.setDialogVisible(false);
            }
            starParticlesView.setPaused(false);
        });
        dialog.show();
        return true;
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);
        currentAccount = UserConfig.selectedAccount;

        PremiumButtonView premiumButtonView = new PremiumButtonView(getContext(), false, resourcesProvider);
        premiumButtonView.setButton(PremiumPreviewFragment.getPremiumButtonText(currentAccount, null), v -> {
            PremiumPreviewFragment.sentPremiumButtonClick();
            PremiumPreviewFragment.buyPremium(fragment, "profile");
        });

        buttonContainer = new FrameLayout(getContext());

        View buttonDivider = new View(getContext());
        buttonDivider.setBackgroundColor(getThemedColor(Theme.key_divider));
        buttonContainer.addView(buttonDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));
        buttonDivider.getLayoutParams().height = 1;
        AndroidUtilities.updateViewVisibilityAnimated(buttonDivider, true, 1f, false);

        if (!UserConfig.getInstance(currentAccount).isPremium() && needDefaultPremiumBtn()) {
            buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));
        }
    }

    protected boolean needDefaultPremiumBtn() {
        return true;
    }

    @Override
    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
        measureGradient(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
        container.getLocationOnScreen(coords);
    }

    private FrameLayout titleViewContainer;
    protected LinkSpanDrawable.LinksTextView titleView[];

    private void titleLoaded(CharSequence newText, boolean animated) {
        if (titleView == null) {
            return;
        }
        titleView[1].setText(newText);
        if (titleView[1].getVisibility() != View.VISIBLE) {
            if (animated) {
                titleView[1].setAlpha(0);
                titleView[1].setVisibility(View.VISIBLE);
                titleView[1].animate().alpha(1).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(200).start();
                titleView[0].animate().alpha(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(200).withEndAction(() -> {
                    titleView[0].setVisibility(View.GONE);
                }).start();

                ValueAnimator heightUpdate = ValueAnimator.ofFloat(0, 1);
                heightUpdate.addUpdateListener(anm -> {
                    float t = (float) anm.getAnimatedValue();
                    titleViewContainer.getLayoutParams().height = AndroidUtilities.lerp(titleView[0].getHeight(), titleView[1].getHeight(), t);
                    titleViewContainer.requestLayout();
                });
                heightUpdate.setInterpolator(CubicBezierInterpolator.DEFAULT);
                heightUpdate.setDuration(200);
                heightUpdate.start();
            } else {
                titleView[1].setAlpha(1);
                titleView[1].setVisibility(View.VISIBLE);
                titleView[0].setAlpha(0);
                titleView[0].setVisibility(View.GONE);
            }
        }
    }

    protected TextView subtitleView;

    public void setTitle(boolean animated) {
        if (titleView == null || subtitleView == null) {
            return;
        }
        if (emojiStatusCollectible != null) {
            String collectionName = emojiStatusCollectible.title;
            int spaceIndex;
            if ((spaceIndex = collectionName.lastIndexOf(' ')) >= 0) {
                collectionName = collectionName.substring(0, spaceIndex);
            }
            titleView[0].setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserStatusCollectibleDialogTitle, DialogObject.getShortName(user), collectionName), () -> {
                Browser.openUrl(getContext(), "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/nft/" + emojiStatusCollectible.slug);
            }));
            subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserStatusDialogSubtitle)));
        } else if (statusStickerSet != null) {
            final String stickerSetPlaceholder = "<STICKERSET>";
            String string = LocaleController.formatString(R.string.TelegramPremiumUserStatusDialogTitle, ContactsController.formatName(user.first_name, user.last_name), stickerSetPlaceholder);
            CharSequence charSequence = AndroidUtilities.replaceSingleLink(string, accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor);
            try {
                charSequence = Emoji.replaceEmoji(charSequence, titleView[0].getPaint().getFontMetricsInt(), false);
            } catch (Exception ignore) {}
            SpannableStringBuilder title = charSequence instanceof SpannableStringBuilder ? ((SpannableStringBuilder) charSequence) : new SpannableStringBuilder(charSequence);
            int index = charSequence.toString().indexOf(stickerSetPlaceholder);
            if (index >= 0) {
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(statusStickerSet, false);
                TLRPC.Document sticker = null;
                if (stickerSet != null && !stickerSet.documents.isEmpty()) {
                    sticker = stickerSet.documents.get(0);
                    if (stickerSet.set != null) {
                        for (int i = 0; i < stickerSet.documents.size(); ++i) {
                            if (stickerSet.documents.get(i).id == stickerSet.set.thumb_document_id) {
                                sticker = stickerSet.documents.get(i);
                                break;
                            }
                        }
                    }
                }
                SpannableStringBuilder replaceWith;
                if (sticker != null) {
                    SpannableStringBuilder animatedEmoji = new SpannableStringBuilder("x");
                    animatedEmoji.setSpan(new AnimatedEmojiSpan(sticker, titleView[0].getPaint().getFontMetricsInt()), 0, animatedEmoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (stickerSet != null && stickerSet.set != null) {
                        animatedEmoji.append("\u00A0").append(stickerSet.set.title);
                    }
                    replaceWith = animatedEmoji;
                } else {
                    SpannableStringBuilder loading = new SpannableStringBuilder("xxxxxx");
                    loading.setSpan(new LoadingSpan(titleView[0], AndroidUtilities.dp(100)), 0, loading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    replaceWith = loading;
                }
                title.replace(index, index + stickerSetPlaceholder.length(), replaceWith);
                title.setSpan(new ClickableSpan() {
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                        if (accentColor != null) {
                            ds.setColor(accentColor);
                        }
                    }
                    @Override
                    public void onClick(@NonNull View view) {}
                }, index, index + replaceWith.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                titleView[1].setOnLinkPressListener(l -> {
                    ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
                    inputStickerSets.add(statusStickerSet);
                    BaseFragment overridenFragment = new BaseFragment() {
                        @Override
                        public Activity getParentActivity() {
                            if (fragment == null) {
                                return null;
                            }
                            return fragment.getParentActivity();
                        }
                        @Override
                        public int getCurrentAccount() {
                            return currentAccount;
                        }
                        @Override
                        public FrameLayout getLayoutContainer() {
                            return bulletinContainer;
                        }
                        @Override
                        public View getFragmentView() {
                            return containerView;
                        }
                        @Override
                        public Dialog showDialog(Dialog dialog) {
                            dialog.show();
                            return dialog;
                        }
                    };
                    if (fragment != null) {
                        overridenFragment.setParentFragment(fragment);
                    }
                    new EmojiPacksAlert(overridenFragment, getContext(), resourcesProvider, inputStickerSets) {
                        @Override
                        protected void onCloseByLink() {
                            PremiumPreviewBottomSheet.this.dismiss();
                        }
                    }.show();
                });
                if (sticker != null) {
                    titleLoaded(title, animated);
                } else {
                    titleView[0].setText(title, null);
                }
            }
            subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserStatusDialogSubtitle)));
        } else if (isEmojiStatus) {
            titleView[0].setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.TelegramPremiumUserStatusDefaultDialogTitle, ContactsController.formatName(user.first_name, user.last_name))));
            subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.TelegramPremiumUserStatusDialogSubtitle, ContactsController.formatName(user.first_name, user.last_name))));
        } else if (giftTier != null) {
            if (isOutboundGift) {
                titleView[0].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogTitleWithPlural, user != null ? user.first_name : "", LocaleController.formatPluralString("GiftMonths", giftTier.getMonths())), accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor));
                subtitleView.setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogSubtitle, user != null ? user.first_name : ""), accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor));
            } else if (user == null || TextUtils.isEmpty(user.first_name) || user.id == 777000) {
                titleView[0].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumDialogTitleWithPluralSomeone, LocaleController.formatPluralString("GiftMonths", giftTier.getMonths())), accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor));
                subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserGiftedPremiumDialogSubtitle)));
            } else {
                titleView[0].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumDialogTitleWithPlural, user.first_name, LocaleController.formatPluralString("GiftMonths", giftTier.getMonths())), accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor));
                subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserGiftedPremiumDialogSubtitle)));
            }
        } else {
            if (user == null) {
                titleView[0].setText(LocaleController.getString(R.string.TelegramPremium));
                subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumSubscribedSubtitle)));
            } else {
                titleView[0].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.TelegramPremiumUserDialogTitle, ContactsController.formatName(user.first_name, user.last_name)), accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueButton) : accentColor));
                subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserDialogSubtitle)));
            }
        }
        try {
            titleView[0].setText(Emoji.replaceEmoji(titleView[0].getText(), titleView[0].getPaint().getFontMetricsInt(), false));
        } catch (Exception ignore) {}
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.TelegramPremium);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new Adapter();
    }

    protected void attachIconContainer(LinearLayout container) {
        container.addView(overrideTitleIcon, LayoutHelper.createLinear(140, 140, Gravity.CENTER_HORIZONTAL, Gravity.CENTER, 10, 10, 10, 10));
    }

    protected void afterCellCreated(int viewType, View view) {

    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            view = onCreateAdditionCell(viewType, context);
            if (view != null) {
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            switch (viewType) {
                case 0:
                    LinearLayout linearLayout = new LinearLayout(context) {
                        @Override
                        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                            if (child == iconTextureView && enterTransitionInProgress) {
                                return true;
                            }
                            return super.drawChild(canvas, child, drawingTime);
                        }
                    };
                    iconContainer = linearLayout;
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    if (overrideTitleIcon == null) {
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
                        canvas.drawColor(ColorUtils.blendARGB(getThemedColor(Theme.key_premiumGradient2), getThemedColor(Theme.key_dialogBackground), 0.5f));
                        iconTextureView.setBackgroundBitmap(bitmap);
                        iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient2;
                        iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient1;
                        iconTextureView.mRenderer.updateColors();
                        linearLayout.addView(iconTextureView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));
                    } else {
                        if (overrideTitleIcon.getParent() != null) {
                            ((ViewGroup) overrideTitleIcon.getParent()).removeView(overrideTitleIcon);
                        }
                        attachIconContainer(linearLayout);
                    }

                    if (titleViewContainer == null) {
                        titleViewContainer = new FrameLayout(context);
                        titleViewContainer.setClipChildren(false);

                        final ColorFilter colorFilter = new PorterDuffColorFilter(accentColor == null ? getThemedColor(Theme.key_windowBackgroundWhiteBlueIcon) : accentColor, PorterDuff.Mode.SRC_IN);
                        titleView = new LinkSpanDrawable.LinksTextView[2];
                        for (int a = 0; a < 2; ++a) {
                            titleView[a] = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
                                @Override
                                protected int emojiCacheType() {
                                    return AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
                                }
                            };
                            titleView[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
                            titleView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                            titleView[a].setTypeface(AndroidUtilities.bold());
                            titleView[a].setGravity(Gravity.CENTER_HORIZONTAL);
                            titleView[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                            titleView[a].setLinkTextColor(getThemedColor(Theme.key_windowBackgroundWhiteLinkText));
                            titleViewContainer.addView(titleView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        }
                    }
                    if (titleViewContainer.getParent() != null) {
                        ((ViewGroup) titleViewContainer.getParent()).removeView(titleViewContainer);
                    }
                    linearLayout.addView(titleViewContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_HORIZONTAL, 40, 0, 40, 0));

                    if (subtitleView == null) {
                        subtitleView = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
                        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
                        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                        subtitleView.setLinkTextColor(getThemedColor(Theme.key_windowBackgroundWhiteLinkText));
                    }
                    if (subtitleView.getParent() != null) {
                        ((ViewGroup) subtitleView.getParent()).removeView(subtitleView);
                    }
                    linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 9, 16, 20));

                    setTitle(false);

                    starParticlesView = new StarParticlesView(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            drawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(52));
                        }

                        @Override
                        protected void configure() {
                            super.configure();
                            drawable.useGradient = true;
                            drawable.useBlur = false;
                            drawable.forceMaxAlpha = true;
                            drawable.checkBounds = true;
                            drawable.init();
                        }
                    };
                    FrameLayout frameLayout = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            float y = 0;
                            if (iconTextureView != null) {
                                y = iconTextureView.getTop() + iconTextureView.getMeasuredHeight() / 2f;
                            } else if (overrideTitleIcon != null) {
                                y = overrideTitleIcon.getTop() + overrideTitleIcon.getMeasuredHeight() / 2f;
                            }
                            starParticlesView.setTranslationY(y - starParticlesView.getMeasuredHeight() / 2f);
                        }
                    };
                    frameLayout.setClipChildren(false);
                    frameLayout.addView(starParticlesView);
                    frameLayout.addView(linearLayout);

                    if (iconTextureView != null) {
                        iconTextureView.setStarParticlesView(starParticlesView);
                    }

                    view = frameLayout;
                    break;
                default:
                case 1:
                    view = new PremiumFeatureCell(context, resourcesProvider) {
                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            AndroidUtilities.rectTmp.set(imageView.getLeft(), imageView.getTop(), imageView.getRight(), imageView.getBottom());
                            gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), totalGradientHeight, 0, -data.yOffset);
                            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), gradientTools.paint);
                            super.dispatchDraw(canvas);
                        }
                    };
                    break;
                case 2:
                    view = new ShadowSectionCell(context, 12, getThemedColor(Theme.key_windowBackgroundGray));
                    break;
                case 3:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(68), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 4:
                    view = new AboutPremiumView(context);
                    break;
                case 5:
                    TextInfoCell cell = new TextInfoCell(context, resourcesProvider);
                    cell.setBackground(true);
                    String terms1 = getString("GiftPremiumPrivacyPolicyAndTerms", R.string.GiftPremiumPrivacyPolicyAndTerms);
                    SpannableStringBuilder stringBuilder1 = AndroidUtilities.replaceSingleTag(
                            terms1,
                            Theme.key_chat_messageLinkIn, 0,
                            () -> Browser.openUrl(fragment.getParentActivity(), LocaleController.getString(R.string.TermsOfServiceUrl)));
                    String terms2 = getString("GiftPremiumPrivacyPolicy", R.string.GiftPremiumPrivacyPolicy);
                    SpannableStringBuilder stringBuilder2 = AndroidUtilities.replaceSingleTag(
                            terms2,
                            Theme.key_chat_messageLinkIn, 0,
                            () -> Browser.openUrl(fragment.getParentActivity(), LocaleController.getString(R.string.PrivacyPolicyUrl)));
                    cell.setText(AndroidUtilities.replaceCharSequence("%1$s", stringBuilder1, stringBuilder2));
                    view = cell;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            afterCellCreated(viewType, view);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position >= featuresStartRow && position < featuresEndRow) {
                ((PremiumFeatureCell) holder.itemView).setData(premiumFeatures.get(position - featuresStartRow), position != featuresEndRow - 1);
            } else if (position >= additionStartRow && position < additionEndRow) {
                onBindAdditionCell(holder.itemView, position);
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == paddingRow) {
                return 0;
            } else if (position >= additionStartRow && position < additionEndRow) {
                return getAdditionItemViewType(position);
            } else if (position >= featuresStartRow && position < featuresEndRow) {
                return 1;
            } else if (position == sectionRow) {
                return 2;
            } else if (position == buttonRow) {
                return 3;
            } else if (position == helpUsRow) {
                return 4;
            } else if (position == termsRow) {
                return 5;
            }
            return super.getItemViewType(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1 || isAdditionViewClickable(holder.getItemViewType());
        }
    }

    protected boolean isAdditionViewClickable(int viewType) {
        return false;
    }

    protected int getAdditionItemViewType(int position) {
        return 0;
    }

    protected View onCreateAdditionCell(int viewType, Context context) {
        return null;
    }

    protected void onBindAdditionCell(View view, int pos) {

    }

    private void measureGradient(int w, int h) {
        int yOffset = 0;
        for (int i = 0; i < premiumFeatures.size(); i++) {
            dummyCell.setData(premiumFeatures.get(i), false);
            dummyCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            premiumFeatures.get(i).yOffset = yOffset;
            yOffset += dummyCell.getMeasuredHeight();
        }

        totalGradientHeight = yOffset;
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);
        if (animateConfetti) {
            AndroidUtilities.runOnUIThread(()->{
                try {
                    container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}
                fireworksOverlay.start(animateConfettiWithStars);
            }, 200);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
        if (enterAnimator != null) {
            enterAnimator.cancel();
        }

        if (fireworksOverlay.isStarted()) {
            fireworksOverlay.animate().alpha(0).setDuration(150).start();
        }
    }

    @Override
    protected void mainContainerDispatchDraw(Canvas canvas) {
        if (overrideTitleIcon != null) {
            overrideTitleIcon.setVisibility(enterTransitionInProgress ? View.INVISIBLE : View.VISIBLE);
        }
        super.mainContainerDispatchDraw(canvas);
        if (startEnterFromView != null && enterTransitionInProgress) {
            View titleIcon = overrideTitleIcon == null ? iconTextureView : overrideTitleIcon;
            if (titleIcon == overrideTitleIcon) {
                overrideTitleIcon.setVisibility(View.VISIBLE);
            }

            canvas.save();

            float[] points = new float[]{startEnterFromX, startEnterFromY};
            startEnterFromView.getMatrix().mapPoints(points);
            Drawable startEnterFromDrawable = null;
            if (startEnterFromView instanceof SimpleTextView) {
                startEnterFromDrawable = ((SimpleTextView) startEnterFromView).getRightDrawable();
            } else if (startEnterFromView instanceof ChatMessageCell) {
                startEnterFromDrawable = ((ChatMessageCell) startEnterFromView).currentNameStatusDrawable;
            }
            if (startEnterFromDrawable == null) {
                canvas.restore();
                return;
            }
            float cxFrom = -coords[0] + startEnterFromX1 + points[0];
            float cyFrom = -coords[1] + startEnterFromY1 + points[1];

            if (AndroidUtilities.isTablet()) {
                View v = fragment.getParentLayout().getView();
                cxFrom += v.getX() + v.getPaddingLeft();
                cyFrom += v.getY() + v.getPaddingTop();
            }

            float fromSize = startEnterFromScale * startEnterFromDrawable.getIntrinsicWidth();
            float toSize = titleIcon.getMeasuredHeight() * 0.8f;
            float toSclale = toSize / fromSize;
            float bigIconFromScale = fromSize / toSize;

            float cxTo = titleIcon.getMeasuredWidth() / 2f;
            View view = titleIcon;
            while (view != container) {
                if (view == null) {
                    break;
                }
                cxTo += view.getX();
                view = (View) view.getParent();
            }
            float cy = 0;
            float cyTo = cy + titleIcon.getY() + ((View) titleIcon.getParent()).getY() + ((View) titleIcon.getParent().getParent()).getY() + titleIcon.getMeasuredHeight() / 2f;

            float x = AndroidUtilities.lerp(cxFrom, cxTo, CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(enterTransitionProgress));
            float y = AndroidUtilities.lerp(cyFrom, cyTo, enterTransitionProgress);

            if (startEnterFromDrawable != null) {
                float s = startEnterFromScale * (1f - enterTransitionProgress) + toSclale * enterTransitionProgress;
                canvas.save();
                canvas.scale(s, s, x, y);
                startEnterFromDrawable.setBounds(
                        (int) x - startEnterFromDrawable.getIntrinsicWidth() / 2,
                        (int) y - startEnterFromDrawable.getIntrinsicHeight() / 2,
                        (int) x + startEnterFromDrawable.getIntrinsicWidth() / 2,
                        (int) y + startEnterFromDrawable.getIntrinsicHeight() / 2);
                startEnterFromDrawable.setAlpha((int) (255 * (1f - Utilities.clamp(enterTransitionProgress, 1, 0))));
                startEnterFromDrawable.draw(canvas);
                startEnterFromDrawable.setAlpha(0);
                canvas.restore();

                s = AndroidUtilities.lerp(bigIconFromScale, 1, enterTransitionProgress);
                canvas.scale(s, s, x, y);
                canvas.translate(x - titleIcon.getMeasuredWidth() / 2f, y - titleIcon.getMeasuredHeight() / 2f);
              //  canvas.saveLayerAlpha(0, 0, titleIcon.getMeasuredWidth(), titleIcon.getMeasuredHeight(), (int) (255 * enterTransitionProgress), Canvas.ALL_SAVE_FLAG);
                titleIcon.draw(canvas);
               // canvas.restore();
            }
            canvas.restore();
        }
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (startEnterFromView == null) {
            return false;
        }
        enterAnimator = ValueAnimator.ofFloat(0, 1f);
        enterTransitionProgress = 0f;
        enterTransitionInProgress = true;
        iconContainer.invalidate();
        final Drawable startEnterFromDrawable;
        if (startEnterFromView instanceof SimpleTextView) {
            startEnterFromDrawable = ((SimpleTextView) startEnterFromView).getRightDrawable();
        } else if (startEnterFromView instanceof ChatMessageCell) {
            startEnterFromDrawable = ((ChatMessageCell) startEnterFromView).currentNameStatusDrawable;
            ((ChatMessageCell) startEnterFromView).invalidateOutbounds();
        } else {
            startEnterFromDrawable = null;
        }
        if (startEnterFromDrawable != null) {
            startEnterFromDrawable.setAlpha(0);
        }
        if (startEnterFromView instanceof ChatMessageCell) {
            ((ChatMessageCell) startEnterFromView).invalidateOutbounds();
        } else {
            startEnterFromView.invalidate();
        }
        if (iconTextureView != null) {
            iconTextureView.startEnterAnimation(-360, 100);
        }
        enterAnimator.addUpdateListener(animation -> {
            enterTransitionProgress = (float) animation.getAnimatedValue();
            container.invalidate();
        });
        enterAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                enterTransitionInProgress = false;
                enterTransitionProgress = 1f;
                iconContainer.invalidate();
                if (startEnterFromDrawable != null) {
                    ValueAnimator iconAlphaBack = ValueAnimator.ofInt(0, 255);
                    iconAlphaBack.addUpdateListener(animation1 -> {
                        startEnterFromDrawable.setAlpha((Integer) animation1.getAnimatedValue());
                        if (startEnterFromView instanceof ChatMessageCell) {
                            ((ChatMessageCell) startEnterFromView).invalidateOutbounds();
                        } else {
                            startEnterFromView.invalidate();
                        }
                    });
                    iconAlphaBack.start();
                }
                super.onAnimationEnd(animation);
            }
        });
        enterAnimator.setDuration(600);
        enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enterAnimator.start();
        return super.onCustomOpenAnimation();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupStickersDidLoad) {
            if (statusStickerSet != null && statusStickerSet.id == (long) args[0]) {
                setTitle(true);
            }
        }
    }
}
