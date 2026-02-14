package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.replaceUnderstood;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

//import com.google.mlkit.nl.translate.TranslateLanguage;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.RestrictedLanguagesSelectActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.List;

public class TranslateButton extends FrameLayout {

    private final int currentAccount;
    private final long dialogId;
    private final BaseFragment fragment;

    private Theme.ResourcesProvider resourcesProvider;

    private AnimatedTextView textView;
    private final Drawable translateDrawable;
    public final SpannableString translateIcon;

    private ImageView menuView;

    private boolean[] accusative = new boolean[1];

    public TranslateButton(Context context, ChatActivity chatActivity, Theme.ResourcesProvider resourcesProvider) {
        this(context, chatActivity.getCurrentAccount(), chatActivity.getDialogId(), chatActivity, resourcesProvider);
    }

    public TranslateButton(Context context, int currentAccount, long dialogId, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;

        textView = new AnimatedTextView(context, true, true, false) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(dp(17), 0);
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        textView.setAnimationProperties(.3f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);

        textView.setTextSize(dp(14));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setPadding(dp(4), 0, dp(4), 0);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setIgnoreRTL(!LocaleController.isRTL);
        textView.adaptWidth = false;
        textView.setOnClickListener(e -> onButtonClick());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, 0, 34, 0));

        translateDrawable = getContext().getResources().getDrawable(R.drawable.msg_translate).mutate();
        translateDrawable.setBounds(0, dp(-6), dp(20), dp(20 - 6));
        translateIcon = new SpannableString("x");
        translateIcon.setSpan(new ImageSpan(translateDrawable, DynamicDrawableSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        menuView = new ImageView(context);
        menuView.setScaleType(ImageView.ScaleType.CENTER);
        menuView.setImageResource(R.drawable.msg_mini_customize);
        menuView.setOnClickListener(e -> {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (UserConfig.getInstance(currentAccount).isPremium() || chat != null && chat.autotranslation) {
                onMenuClick();
            } else {
                onCloseClick();
            }
        });
        addView(menuView, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 7, 0));

        updateColors();
    }

    public void updateColors() {
        textView.setTextColor(Theme.getColor(Theme.key_chat_addContact, resourcesProvider));
        textView.setBackground(Theme.createInsetRoundRectDrawable(Theme.getColor(Theme.key_chat_addContact, resourcesProvider) & 0x19ffffff, dp(15), dp(3)));
        menuView.setBackground(Theme.createCircleSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 0));
        menuView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        translateDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_addContact, resourcesProvider), PorterDuff.Mode.MULTIPLY));
    }

    public void setLeftMargin(float leftMargin) {
        textView.setTranslationX(leftMargin / 2f);
    }

    protected void onButtonClick() {

    }

    protected void onCloseClick() {

    }

    protected void onMenuClick() {
        TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();

        final ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), R.drawable.popup_fixed_alert4, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        final ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));

        LinearLayout swipeBack = new LinearLayout(getContext());
        swipeBack.setOrientation(LinearLayout.VERTICAL);
        ScrollView swipeBackScrollView = new ScrollView(getContext()) {
            Drawable topShadowDrawable;
            AnimatedFloat alphaFloat = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            private boolean wasCanScrollVertically;

            @Override
            public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
                boolean canScrollVertically = canScrollVertically(-1);
                if (wasCanScrollVertically != canScrollVertically) {
                    invalidate();
                    wasCanScrollVertically = canScrollVertically;
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                float alpha = .5f * alphaFloat.set(canScrollVertically(-1) ? 1 : 0);
                if (alpha > 0) {
                    if (topShadowDrawable == null) {
                        topShadowDrawable = getContext().getResources().getDrawable(R.drawable.header_shadow);
                    }
                    topShadowDrawable.setBounds(
                            0, getScrollY(), getWidth(), getScrollY() + topShadowDrawable.getIntrinsicHeight()
                    );
                    topShadowDrawable.setAlpha((int) (0xFF * alpha));
                    topShadowDrawable.draw(canvas);
                }
            }
        };
        LinearLayout swipeBackScroll = new LinearLayout(getContext());
        swipeBackScrollView.addView(swipeBackScroll);
        swipeBackScroll.setOrientation(LinearLayout.VERTICAL);
        popupLayout.swipeBackGravityRight = true;
        final int swipeBackIndex = popupLayout.addViewToSwipeBack(swipeBack);

        ActionBarMenuSubItem translateToButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
        translateToButton.setTextAndIcon(getString(R.string.TranslateTo), R.drawable.msg_translate);
        translateToButton.setSubtext(TranslateAlert2.capitalFirst(TranslateAlert2.languageName(translateController.getDialogTranslateTo(dialogId))));
        translateToButton.setItemHeight(56);
        popupLayout.addView(translateToButton);

        ActionBarMenuSubItem backButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
        backButton.setTextAndIcon(getString(R.string.Back), R.drawable.ic_ab_back);
        backButton.setOnClickListener(e -> popupLayout.getSwipeBack().closeForeground());
        swipeBack.addView(backButton);

        swipeBack.addView(swipeBackScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 420));

        String detectedLanguage = translateController.getDialogDetectedLanguage(dialogId);
        String detectedLanguageName = TranslateAlert2.languageName(detectedLanguage);
        String detectedLanguageNameAccusative = TranslateAlert2.languageName(detectedLanguage, accusative);
        String currentTranslateTo = translateController.getDialogTranslateTo(dialogId);

        final ArrayList<TranslateController.Language> suggestedLanguages = TranslateController.getSuggestedLanguages(currentTranslateTo);
        final ArrayList<TranslateController.Language> allLanguages = TranslateController.getLanguages();

        swipeBackScroll.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        final boolean[] addedLanguages = new boolean[1];
        final Runnable addLanguages = () -> {
            if (addedLanguages[0]) return;

            if (currentTranslateTo != null) {
                String displayName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(currentTranslateTo));
                if (displayName != null) {
                    ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, false, false, resourcesProvider);
                    button.setChecked(true);
                    button.setText(displayName);
                    swipeBackScroll.addView(button);
                }
            }
            for (TranslateController.Language lng : suggestedLanguages) {
                final String code = lng.code;
                if (TextUtils.equals(code, detectedLanguage)) {
                    continue;
                }

                ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, false, false, resourcesProvider);
                final boolean checked = currentTranslateTo != null && currentTranslateTo.equals(code);
                button.setChecked(checked);
                button.setText(lng.displayName);
                if (!checked) {
                    button.setOnClickListener(e -> {
                        translateController.setDialogTranslateTo(dialogId, code);
                        popupWindow.dismiss();
                        updateText();
                    });
                }
                swipeBackScroll.addView(button);
            }
            swipeBackScroll.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

            List<String> systemAllLanguages = null;
//        if ("system".equals(MessagesController.getInstance(currentAccount).translationsAutoEnabled)) {
//            systemAllLanguages = TranslateLanguage.getAllLanguages();
//        }

            for (TranslateController.Language lng : allLanguages) {
                final String code = lng.code;
                if (TextUtils.equals(code, detectedLanguage)) {
                    continue;
                }
                final boolean checked = currentTranslateTo != null && currentTranslateTo.equals(code);

                if (!checked && systemAllLanguages != null && !systemAllLanguages.contains(lng.code)) {
                    continue;
                }

                ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, false, false, resourcesProvider);
                button.setChecked(checked);
                button.setText(lng.displayName);
                if (!checked) {
                    button.setOnClickListener(e -> {
                        translateController.setDialogTranslateTo(dialogId, code);
                        popupWindow.dismiss();
                        updateText();
                    });
                }
                swipeBackScroll.addView(button);
            }

            addedLanguages[0] = true;
        };

//        if (detectedLanguage != null) {
//            ActionBarMenuSubItem translateFromButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
//            translateFromButton.setTextAndIcon(LocaleController.getString(R.string.DetectedLanguage), R.drawable.msg_language);
//            translateFromButton.setSubtext(TranslateAlert2.languageName(detectedLanguage));
//            translateFromButton.setItemHeight(56);
//            popupLayout.addView(translateFromButton);
//        }

//        popupLayout.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        translateToButton.setOnClickListener(e -> {
            addLanguages.run();
            popupLayout.getSwipeBack().openForeground(swipeBackIndex);
        });

        if (UserConfig.getInstance(currentAccount).isPremium() && detectedLanguageNameAccusative != null) {
            final ActionBarMenuSubItem dontTranslateButton = new ActionBarMenuSubItem(getContext(), false, false, resourcesProvider);
            String text;
            if (accusative[0]) {
                text = LocaleController.formatString(R.string.DoNotTranslateLanguage, detectedLanguageNameAccusative);
            } else {
                text = LocaleController.formatString(R.string.DoNotTranslateLanguageOther, detectedLanguageNameAccusative);
            }
            dontTranslateButton.setMultiline(false);
            dontTranslateButton.setTextAndIcon(HintView2.cutInFancyHalfText(text, dontTranslateButton.getTextView().getPaint()), R.drawable.msg_block2);
            dontTranslateButton.setOnClickListener(e -> {
                RestrictedLanguagesSelectActivity.toggleLanguage(detectedLanguage, true);
                translateController.checkRestrictedLanguagesUpdate();
                translateController.setHideTranslateDialog(dialogId, true);
                String bulletinTextString;
                if (accusative[0]) {
                    bulletinTextString = LocaleController.formatString(R.string.AddedToDoNotTranslate, detectedLanguageNameAccusative);
                } else {
                    bulletinTextString = LocaleController.formatString(R.string.AddedToDoNotTranslateOther, detectedLanguageNameAccusative);
                }
                CharSequence bulletinText = AndroidUtilities.replaceTags(bulletinTextString);
                bulletinText = TranslateAlert2.capitalFirst(bulletinText);
                BulletinFactory.of(fragment).createSimpleBulletin(
                    R.raw.msg_translate,
                    bulletinText,
                    getString(R.string.Settings),
                    () -> fragment.presentFragment(new RestrictedLanguagesSelectActivity())
                ).show();
                popupWindow.dismiss();
            });
            popupLayout.addView(dontTranslateButton);
        }

        final ActionBarMenuSubItem hideButton = new ActionBarMenuSubItem(getContext(), false, false, resourcesProvider);
        hideButton.setTextAndIcon(getString(R.string.Hide), R.drawable.msg_cancel);
        hideButton.setOnClickListener(e -> {
            translateController.setHideTranslateDialog(dialogId, true);
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            final boolean isChannel = chat != null && ChatObject.isChannelAndNotMegaGroup(chat);
            final CharSequence message = AndroidUtilities.replaceTags(
                isChannel ?
                    getString(R.string.TranslationBarHiddenForChannel) :
                    chat != null ?
                        getString(R.string.TranslationBarHiddenForGroup) :
                        getString(R.string.TranslationBarHiddenForChat)
            );
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.msg_translate, message, getString(R.string.UndoNoCaps), () -> {
                translateController.setHideTranslateDialog(dialogId, false);
            }).show();
            popupWindow.dismiss();
        });
        popupLayout.addView(hideButton);

        popupLayout.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        final LinkSpanDrawable.LinksTextView cocoonButton = new LinkSpanDrawable.LinksTextView(getContext());
        cocoonButton.setPadding(dp(13), dp(8.33f), dp(13), dp(8.33f));
        cocoonButton.setDisablePaddingsOffsetY(true);
        cocoonButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        cocoonButton.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        cocoonButton.setEmojiColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        CharSequence cocoonText = TextUtils.concat(AndroidUtilities.replaceTags(getString(R.string.CocoonPoweredBy)), " ", AndroidUtilities.premiumText(getString(R.string.CocoonPoweredByLink), () -> {
            popupWindow.dismiss();
            showCocoonAlert(getContext(), resourcesProvider);
        }));
        SpannableStringBuilder egg = new SpannableStringBuilder("🥚");
        egg.setSpan(new AnimatedEmojiSpan(5197252827247841976L, cocoonButton.getPaint().getFontMetricsInt()), 0, egg.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableStringBuilder eggSpaced = new SpannableStringBuilder(egg);
        eggSpaced.append(" ");
        cocoonText = AndroidUtilities.replaceCharSequence("🥚 ", cocoonText, eggSpaced);
        cocoonText = AndroidUtilities.replaceCharSequence("🥚", cocoonText, egg);
        cocoonButton.setText(HintView2.cutInFancyHalfText(cocoonText, cocoonButton.getPaint()));
        cocoonButton.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 12));
        cocoonButton.setOnClickListener(v -> {
            popupWindow.dismiss();
            showCocoonAlert(getContext(), resourcesProvider);
        });
        popupLayout.addView(cocoonButton);

        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.showAsDropDown(menuView, 0, -menuView.getMeasuredHeight() - dp(8));
    }

    public void updateText() {
        final TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (translateController.isTranslatingDialog(dialogId)) {
            String detectedLanguage = translateController.getDialogDetectedLanguage(dialogId);
            detectedLanguage = TranslateAlert2.languageName(detectedLanguage);
            if (!TextUtils.isEmpty(detectedLanguage)) {
                textView.setText(TextUtils.concat(translateIcon, " ", LocaleController.formatString(R.string.ShowOriginalButtonLanguage, detectedLanguage)));
            } else {
                textView.setText(TextUtils.concat(translateIcon, " ", getString(R.string.ShowOriginalButton)));
            }
        } else {
            String lng = translateController.getDialogTranslateTo(dialogId);
            if (lng == null) {
                lng = "en";
            }
            String text;
            String lang = TranslateAlert2.languageName(lng, accusative);
            if (accusative[0]) {
                text = LocaleController.formatString(R.string.TranslateToButton, lang);
            } else {
                text = LocaleController.formatString(R.string.TranslateToButtonOther, lang);
            }
            textView.setText(TextUtils.concat(translateIcon, " ", text));
        }
        menuView.setImageResource(UserConfig.getInstance(currentAccount).isPremium() || chat != null && chat.autotranslation ? R.drawable.msg_mini_customize : R.drawable.msg_close);
    }

    public static void showCocoonAlert(Context context, Theme.ResourcesProvider resourcesProvider) {
        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);
        builder.setApplyTopPadding(false);
        final BottomSheet[] sheet = new BottomSheet[1];

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout topView = new FrameLayout(context);
        final GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setColors(new int[] { 0xFF0C2065, 0xFF06112A });
        bg.setGradientRadius(dp(150));
        final float r = dp(12);
        bg.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
        topView.setBackground(bg);
        final LinearLayout topViewLayout = new LinearLayout(context);
        topViewLayout.setOrientation(LinearLayout.VERTICAL);
        topView.addView(topViewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final ImageView logoView = new ImageView(context);
        logoView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logoView.setImageResource(R.drawable.cocoon_logo);
        topViewLayout.addView(logoView, LayoutHelper.createLinear(132, 132, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 33, 0, 0));

        final ImageView titleView = new ImageView(context);
        titleView.setImageResource(R.drawable.cocoon_text);
        topViewLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 12, 32, 0));

        final TextView subtitleView = new TextView(context);
        subtitleView.setTextColor(0xFFB8C9EF);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setGravity(Gravity.CENTER);
        final SpannableStringBuilder subtitle = AndroidUtilities.replaceTags(getString(R.string.CocoonSubtitle));
        final TypefaceSpan[] spans = subtitle.getSpans(0, subtitle.length(), TypefaceSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            subtitle.setSpan(new ForegroundColorSpan(0xFFFFFFFF), subtitle.getSpanStart(spans[i]), subtitle.getSpanEnd(spans[i]), subtitle.getSpanFlags(spans[i]));
        }
        subtitleView.setText(subtitle);
        topViewLayout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 14, 32, 20));

        layout.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        layout.addView(
            new ChannelMonetizationLayout.FeatureCell(context, R.drawable.menu_privacy, getString(R.string.CocoonFeature1Title), AndroidUtilities.replaceSingleTag(getString(R.string.CocoonFeature1Text), () -> {
                sheet[0].dismiss();
                Browser.openUrl(context, getString(R.string.CocoonFeature1TextLink));
            }), resourcesProvider),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 32, 16, 32, 16)
        );

        layout.addView(
            new ChannelMonetizationLayout.FeatureCell(context, R.drawable.msg_stats, getString(R.string.CocoonFeature2Title), getString(R.string.CocoonFeature2Text), resourcesProvider),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 32, 0, 32, 16)
        );

        layout.addView(
            new ChannelMonetizationLayout.FeatureCell(context, R.drawable.menu_gift, getString(R.string.CocoonFeature3Title), AndroidUtilities.replaceSingleTag(getString(R.string.CocoonFeature3Text), () -> {
                sheet[0].dismiss();
                Browser.openUrlInSystemBrowser(context, getString(R.string.CocoonFeature3TextLink));
            }), resourcesProvider),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 32, 0, 32, 16)
        );

        final View separatorView = new View(context);
        separatorView.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        layout.addView(separatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL, 24, 0, 24, 0));

        final LinkSpanDrawable.LinksTextView footerView = new LinkSpanDrawable.LinksTextView(context);
        footerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        footerView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        footerView.setGravity(Gravity.CENTER);
        footerView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.CocoonFooter), () -> {
            sheet[0].dismiss();
            Browser.openUrl(context, getString(R.string.CocoonFooterLink));
        }));
        footerView.setPadding(0, dp(18), 0, dp(18));
        footerView.setDisablePaddingsOffsetY(true);
        layout.addView(footerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 32, 0, 32, 0));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(replaceUnderstood(getString(R.string.Understood)));
        button.setOnClickListener(v -> sheet[0].dismiss());
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 16, 0, 16, 16));

        builder.setCustomView(layout);
        sheet[0] = builder.create();
        sheet[0].fixNavigationBar();
        sheet[0].show();
    }
}
