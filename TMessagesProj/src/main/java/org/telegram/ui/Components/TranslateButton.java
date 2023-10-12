package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;

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

        textView = new AnimatedTextView(context, true, true, false);
        textView.setAnimationProperties(.3f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);

        textView.setTextSize(AndroidUtilities.dp(15));
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setIgnoreRTL(!LocaleController.isRTL);
        textView.adaptWidth = false;
        textView.setOnClickListener(e -> onButtonClick());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        translateDrawable = getContext().getResources().getDrawable(R.drawable.msg_translate).mutate();
        translateDrawable.setBounds(0, AndroidUtilities.dp(-8), AndroidUtilities.dp(20), AndroidUtilities.dp(20 - 8));
        translateIcon = new SpannableString("x");
        translateIcon.setSpan(new ImageSpan(translateDrawable, DynamicDrawableSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        menuView = new ImageView(context);
        menuView.setScaleType(ImageView.ScaleType.CENTER);
        menuView.setImageResource(R.drawable.msg_mini_customize);
        menuView.setOnClickListener(e -> {
            if (UserConfig.getInstance(currentAccount).isPremium()) {
                onMenuClick();
            } else {
                onCloseClick();
            }
        });
        addView(menuView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        updateColors();
    }

    public void updateColors() {
        textView.setTextColor(Theme.getColor(Theme.key_chat_addContact, resourcesProvider));
        textView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_chat_addContact, resourcesProvider) & 0x19ffffff, 3));
        menuView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_chat_addContact, resourcesProvider) & 0x19ffffff, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        menuView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_addContact, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        translateDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_addContact, resourcesProvider), PorterDuff.Mode.MULTIPLY));
    }

    protected void onButtonClick() {

    }

    protected void onCloseClick() {

    }

    protected void onMenuClick() {
        TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();

        final ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), R.drawable.popup_fixed_alert2, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
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
        translateToButton.setTextAndIcon(LocaleController.getString("TranslateTo", R.string.TranslateTo), R.drawable.msg_translate);
        translateToButton.setSubtext(TranslateAlert2.capitalFirst(TranslateAlert2.languageName(translateController.getDialogTranslateTo(dialogId))));
        translateToButton.setItemHeight(56);
        translateToButton.setOnClickListener(e -> popupLayout.getSwipeBack().openForeground(swipeBackIndex));
        popupLayout.addView(translateToButton);

        ActionBarMenuSubItem backButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
        backButton.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.ic_ab_back);
        backButton.setOnClickListener(e -> popupLayout.getSwipeBack().closeForeground());
        swipeBack.addView(backButton);

        swipeBack.addView(swipeBackScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 420));

        String detectedLanguage = translateController.getDialogDetectedLanguage(dialogId);
        String detectedLanguageName = TranslateAlert2.languageName(detectedLanguage);
        String detectedLanguageNameAccusative = TranslateAlert2.languageName(detectedLanguage, accusative);
        String currentTranslateTo = translateController.getDialogTranslateTo(dialogId);

        ArrayList<TranslateController.Language> suggestedLanguages = TranslateController.getSuggestedLanguages(currentTranslateTo);
        ArrayList<TranslateController.Language> allLanguages = TranslateController.getLanguages();
        swipeBackScroll.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
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
        for (TranslateController.Language lng : allLanguages) {
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

//        if (detectedLanguage != null) {
//            ActionBarMenuSubItem translateFromButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
//            translateFromButton.setTextAndIcon(LocaleController.getString("DetectedLanguage", R.string.DetectedLanguage), R.drawable.msg_language);
//            translateFromButton.setSubtext(TranslateAlert2.languageName(detectedLanguage));
//            translateFromButton.setItemHeight(56);
//            popupLayout.addView(translateFromButton);
//        }

        popupLayout.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        if (detectedLanguageNameAccusative != null) {
            ActionBarMenuSubItem dontTranslateButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
            String text;
            if (accusative[0]) {
                text = LocaleController.formatString("DoNotTranslateLanguage", R.string.DoNotTranslateLanguage, detectedLanguageNameAccusative);
            } else {
                text = LocaleController.formatString("DoNotTranslateLanguageOther", R.string.DoNotTranslateLanguageOther, detectedLanguageNameAccusative);
            }
            dontTranslateButton.setTextAndIcon(text, R.drawable.msg_block2);
            dontTranslateButton.setOnClickListener(e -> {
                RestrictedLanguagesSelectActivity.toggleLanguage(detectedLanguage, true);
                translateController.checkRestrictedLanguagesUpdate();
                translateController.setHideTranslateDialog(dialogId, true);
                String bulletinTextString;
                if (accusative[0]) {
                    bulletinTextString = LocaleController.formatString("AddedToDoNotTranslate", R.string.AddedToDoNotTranslate, detectedLanguageNameAccusative);
                } else {
                    bulletinTextString = LocaleController.formatString("AddedToDoNotTranslateOther", R.string.AddedToDoNotTranslateOther, detectedLanguageNameAccusative);
                }
                CharSequence bulletinText = AndroidUtilities.replaceTags(bulletinTextString);
                bulletinText = TranslateAlert2.capitalFirst(bulletinText);
                BulletinFactory.of(fragment).createSimpleBulletin(
                    R.raw.msg_translate,
                    bulletinText,
                    LocaleController.getString("Settings", R.string.Settings),
                    () -> fragment.presentFragment(new RestrictedLanguagesSelectActivity())
                ).show();
                popupWindow.dismiss();
            });
            popupLayout.addView(dontTranslateButton);
        }

        ActionBarMenuSubItem hideButton = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
        hideButton.setTextAndIcon(LocaleController.getString("Hide", R.string.Hide), R.drawable.msg_cancel);
        hideButton.setOnClickListener(e -> {
            translateController.setHideTranslateDialog(dialogId, true);
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            final boolean isChannel = chat != null && ChatObject.isChannelAndNotMegaGroup(chat);
            final CharSequence message = AndroidUtilities.replaceTags(
                isChannel ?
                    LocaleController.getString("TranslationBarHiddenForChannel", R.string.TranslationBarHiddenForChannel) :
                    chat != null ?
                        LocaleController.getString("TranslationBarHiddenForGroup", R.string.TranslationBarHiddenForGroup) :
                        LocaleController.getString("TranslationBarHiddenForChat", R.string.TranslationBarHiddenForChat)
            );
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.msg_translate, message, LocaleController.getString("Undo", R.string.Undo), () -> {
                translateController.setHideTranslateDialog(dialogId, false);
            }).show();
            popupWindow.dismiss();
        });
        popupLayout.addView(hideButton);

        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.showAsDropDown(menuView, 0, -menuView.getMeasuredHeight() - AndroidUtilities.dp(8));
    }

    public void updateText() {
        TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();
        if (translateController.isTranslatingDialog(dialogId)) {
            textView.setText(TextUtils.concat(translateIcon, " ", LocaleController.getString("ShowOriginalButton", R.string.ShowOriginalButton)));
        } else {
            String lng = translateController.getDialogTranslateTo(dialogId);
            if (lng == null) {
                lng = "en";
            }
            String text;
            String lang = TranslateAlert2.languageName(lng, accusative);
            if (accusative[0]) {
                text = LocaleController.formatString("TranslateToButton", R.string.TranslateToButton, lang);
            } else {
                text = LocaleController.formatString("TranslateToButtonOther", R.string.TranslateToButtonOther, lang);
            }
            textView.setText(TextUtils.concat(translateIcon, " ", text));
        }
        menuView.setImageResource(UserConfig.getInstance(currentAccount).isPremium() ? R.drawable.msg_mini_customize : R.drawable.msg_close);
    }
}
