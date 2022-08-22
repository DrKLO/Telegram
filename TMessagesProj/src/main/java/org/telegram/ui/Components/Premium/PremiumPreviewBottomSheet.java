package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;

public class PremiumPreviewBottomSheet extends BottomSheetWithRecyclerListView {

    ArrayList<PremiumPreviewFragment.PremiumFeatureData> premiumFeatures = new ArrayList<>();
    int currentAccount;
    TLRPC.User user;
    GiftPremiumBottomSheet.GiftTier giftTier;
    boolean isOutboundGift;

    PremiumFeatureCell dummyCell;
    int totalGradientHeight;

    int rowCount;
    int paddingRow;
    int featuresStartRow;
    int featuresEndRow;
    int sectionRow;
    int helpUsRow;
    int buttonRow;

    FireworksOverlay fireworksOverlay;
    PremiumGradient.GradientTools gradientTools;
    StarParticlesView starParticlesView;
    GLIconTextureView iconTextureView;
    ViewGroup iconContainer;
    BaseFragment fragment;

    public float startEnterFromX;
    public float startEnterFromY;
    public float startEnterFromX1;
    public float startEnterFromY1;
    public float startEnterFromScale;
    public SimpleTextView startEnterFromView;

    int[] coords = new int[2];
    float enterTransitionProgress = 0;
    boolean enterTransitionInProgress;
    ValueAnimator enterAnimator;

    boolean animateConfetti;
    FrameLayout buttonContainer;

    public PremiumPreviewBottomSheet(BaseFragment fragment, int currentAccount, TLRPC.User user) {
        this(fragment, currentAccount, user, null);
    }

    public PremiumPreviewBottomSheet(BaseFragment fragment, int currentAccount, TLRPC.User user, GiftPremiumBottomSheet.GiftTier gift) {
        super(fragment, false, false);
        this.fragment = fragment;
        topPadding = 0.26f;
        this.user = user;
        this.currentAccount = currentAccount;
        this.giftTier = gift;
        dummyCell = new PremiumFeatureCell(getContext());
        PremiumPreviewFragment.fillPremiumFeaturesList(premiumFeatures, currentAccount);

        if (giftTier != null || UserConfig.getInstance(currentAccount).isPremium()) {
            buttonContainer.setVisibility(View.GONE);
        }

        gradientTools = new PremiumGradient.GradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4);
        gradientTools.exactly = true;
        gradientTools.x1 = 0;
        gradientTools.y1 = 1f;
        gradientTools.x2 = 0;
        gradientTools.y2 = 0f;
        gradientTools.cx = 0;
        gradientTools.cy = 0;

        paddingRow = rowCount++;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        sectionRow = rowCount++;
        if (!UserConfig.getInstance(currentAccount).isPremium() && gift == null) {
            buttonRow = rowCount++;
        }
        recyclerListView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof PremiumFeatureCell) {
                PremiumFeatureCell cell = (PremiumFeatureCell) view;
                PremiumPreviewFragment.sentShowFeaturePreview(currentAccount, cell.data.type);
                if (cell.data.type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS) {
                    DoubledLimitsBottomSheet bottomSheet = new DoubledLimitsBottomSheet(fragment, currentAccount);
                    showDialog(bottomSheet);
                } else {
                    showDialog(new PremiumFeatureBottomSheet(fragment, cell.data.type, false));
                }
            }
        });

        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
        PremiumPreviewFragment.sentShowScreenStat("profile");

        fireworksOverlay = new FireworksOverlay(getContext());
        container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public PremiumPreviewBottomSheet setOutboundGift(boolean outboundGift) {
        isOutboundGift = outboundGift;
        return this;
    }

    public PremiumPreviewBottomSheet setAnimateConfetti(boolean animateConfetti) {
        this.animateConfetti = animateConfetti;
        return this;
    }

    private void showDialog(Dialog dialog) {
        iconTextureView.setDialogVisible(true);
        starParticlesView.setPaused(true);
        dialog.setOnDismissListener(dialog1 -> {
            iconTextureView.setDialogVisible(false);
            starParticlesView.setPaused(false);
        });
        dialog.show();
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);

        PremiumButtonView premiumButtonView = new PremiumButtonView(getContext(), false);
        premiumButtonView.setButton(PremiumPreviewFragment.getPremiumButtonText(currentAccount), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PremiumPreviewFragment.sentPremiumButtonClick();
                PremiumPreviewFragment.buyPremium(fragment, "profile");
            }
        });

        buttonContainer = new FrameLayout(getContext());

        View buttonDivider = new View(getContext());
        buttonDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        buttonContainer.addView(buttonDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));
        buttonDivider.getLayoutParams().height = 1;
        AndroidUtilities.updateViewVisibilityAnimated(buttonDivider, true, 1f, false);

        if (!UserConfig.getInstance(currentAccount).isPremium()) {
            buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));
        }
    }

    @Override
    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
        measureGradient(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
        container.getLocationOnScreen(coords);

    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString("TelegramPremium", R.string.TelegramPremium);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter() {
        return new Adapter();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
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
                    canvas.drawColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_premiumGradient2), Theme.getColor(Theme.key_dialogBackground), 0.5f));
                    iconTextureView.setBackgroundBitmap(bitmap);
                    iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient1;
                    iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient2;
                    iconTextureView.mRenderer.updateColors();
                    linearLayout.addView(iconTextureView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));

                    TextView titleView = new TextView(context);
                    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    titleView.setGravity(Gravity.CENTER_HORIZONTAL);
                    titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    titleView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                    linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_HORIZONTAL, 40, 0, 40, 0));

                    TextView subtitleView = new TextView(context);
                    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
                    subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    subtitleView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                    linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 9, 16, 20));

                    if (giftTier != null) {
                        if (isOutboundGift) {
                            titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogTitle, user != null ? user.first_name : "", giftTier.getMonths()), null));
                            subtitleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogSubtitle, user != null ? user.first_name : ""), null));
                        } else {
                            titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumDialogTitle, user != null ? user.first_name : "", giftTier.getMonths()), null));
                            subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserGiftedPremiumDialogSubtitle)));
                        }
                    } else {
                        titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserDialogTitle, ContactsController.formatName(user.first_name, user.last_name)), null));
                        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.TelegramPremiumUserDialogSubtitle)));
                    }

                    starParticlesView = new StarParticlesView(context);
                    FrameLayout frameLayout = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            starParticlesView.setTranslationY(iconTextureView.getTop() + iconTextureView.getMeasuredHeight() / 2f - starParticlesView.getMeasuredHeight() / 2f);
                        }
                    };
                    frameLayout.setClipChildren(false);
                    frameLayout.addView(starParticlesView);
                    frameLayout.addView(linearLayout);

                    starParticlesView.drawable.useGradient = true;
                    starParticlesView.drawable.init();
                    iconTextureView.setStarParticlesView(starParticlesView);

                    view = frameLayout;
                    break;
                default:
                case 1:
                    view = new PremiumFeatureCell(context) {
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
                    view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
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
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position >= featuresStartRow && position < featuresEndRow) {
                ((PremiumFeatureCell) holder.itemView).setData(premiumFeatures.get(position - featuresStartRow), position != featuresEndRow - 1);
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
            } else if (position >= featuresStartRow && position < featuresEndRow) {
                return 1;
            } else if (position == sectionRow) {
                return 2;
            } else if (position == buttonRow) {
                return 3;
            } else if (position == helpUsRow) {
                return 4;
            }
            return super.getItemViewType(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }
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
                fireworksOverlay.start();
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
        super.mainContainerDispatchDraw(canvas);
        if (startEnterFromView != null && enterTransitionInProgress) {
            canvas.save();

            float[] points = new float[]{startEnterFromX, startEnterFromY};
            startEnterFromView.getMatrix().mapPoints(points);
            Drawable startEnterFromDrawable = startEnterFromView.getRightDrawable();
            float cxFrom = -coords[0] + startEnterFromX1 + points[0];
            float cyFrom = -coords[1] + startEnterFromY1 + points[1];

            float fromSize = startEnterFromScale * startEnterFromDrawable.getIntrinsicWidth();
            float toSize = iconTextureView.getMeasuredHeight() * 0.8f;
            float toSclale = toSize / fromSize;
            float bigIconFromScale = fromSize / toSize;

            float cxTo = iconTextureView.getMeasuredWidth() / 2f;
            View view = iconTextureView;
            while (view != container) {
                if (view == null) {
                    break;
                }
                cxTo += view.getX();
                view = (View) view.getParent();
            }
            float cyTo = iconTextureView.getY() + ((View) iconTextureView.getParent()).getY() + ((View) iconTextureView.getParent().getParent()).getY() + iconTextureView.getMeasuredHeight() / 2f;

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
                canvas.translate(x - iconTextureView.getMeasuredWidth() / 2f, y - iconTextureView.getMeasuredHeight() / 2f);
              //  canvas.saveLayerAlpha(0, 0, iconTextureView.getMeasuredWidth(), iconTextureView.getMeasuredHeight(), (int) (255 * enterTransitionProgress), Canvas.ALL_SAVE_FLAG);
                iconTextureView.draw(canvas);
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
        startEnterFromView.getRightDrawable().setAlpha(0);
        startEnterFromView.invalidate();
        iconTextureView.startEnterAnimation(-360, 100);
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
                ValueAnimator iconAlphaBack = ValueAnimator.ofInt(0, 255);
                Drawable drawable = startEnterFromView.getRightDrawable();
                iconAlphaBack.addUpdateListener(animation1 -> {
                    drawable.setAlpha((Integer) animation1.getAnimatedValue());
                    startEnterFromView.invalidate();
                });
                iconAlphaBack.start();
                super.onAnimationEnd(animation);
            }
        });
        enterAnimator.setDuration(600);
        enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enterAnimator.start();
        return super.onCustomOpenAnimation();
    }
}
