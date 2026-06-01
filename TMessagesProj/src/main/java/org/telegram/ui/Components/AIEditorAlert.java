package org.telegram.ui.Components;

import static androidx.core.view.ViewCompat.performHapticFeedback;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleTag;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.TranslateController.normalizeLanguage;
import static org.telegram.ui.Components.TranslateAlert2.capitalFirst;
import static org.telegram.ui.Components.TranslateAlert2.languageName;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AiTonesController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_aicompose;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

public class AIEditorAlert extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    public static final int TAB_TRANSLATE = 0;
    public static final int TAB_STYLE = 1;
    public static final int TAB_FIX = 2;

    private CharSequence text;
    private boolean translatedTextLoading;
    private CharSequence translatedText;
    private boolean styledTextLoading;
    private CharSequence styledText;
    private boolean fixedTextLoading;
    private CharSequence fixedText;
    private CharSequence fixedTextToCopy;

    private Utilities.Callback<CharSequence> onUseListener;
    private long dialogId;
    private boolean editing;
    private Utilities.Callback4<CharSequence, Integer, Integer, Boolean> onSendListener;

    private final boolean[] accusative = new boolean[1];
    private final boolean[] genitive = new boolean[1];
    private String from_lang;
    private String to_lang;
    private String translateTone;
    private String translateToneTitle;
    private boolean emojify;

    private final AiTonesController tonesController;

    private final FrameLayout tabsContainer;
    private final Tabs tabs;
    private FrameLayout styleTabsContainer;
    private final Tabs styleTabs;
    private final ImageView closeView;

    private HintView2 styleHint;

    private final LinearLayout buttonContainer;
    private final ButtonWithCounterView allButton;
    private final ButtonWithCounterView button;
    private final ButtonWithCounterView sendButton;

    private final FrameLayout bulletinContainer;

    public AIEditorAlert(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);

        tonesController = MessagesController.getInstance(currentAccount).getTonesController();
        tonesController.load();
        tonesController.open = true;

        closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        closeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .10f)));
        actionBar.addView(closeView, LayoutHelper.createFrame(54, 54, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(closeView, .1f, 1.5f);
        closeView.setOnClickListener(v -> this.dismiss());

        tabsContainer = new FrameLayout(context);
        tabs = new Tabs(context, currentAccount, false, resourcesProvider);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        tabs.setRoundRadius(28);
        tabs.addTab(R.drawable.outline_ai_translate2, getString(R.string.AIEditorTabTranslate), this::selectTab);
        tabs.addTab(R.drawable.menu_rewrite, getString(R.string.AIEditorTabStyle), this::selectTab);
        tabs.addTab(R.drawable.menu_proofread, getString(R.string.AIEditorTabFix), this::selectTab);
        tabs.selectTab(TAB_STYLE);
        tabsContainer.addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 12, 0, 12, 0));

        styleTabs = new Tabs(context, currentAccount, true, resourcesProvider);
        styleTabs.setDivider(true);
        styleTabs.setPadding(dp(8), dp(8), dp(8), dp(8));
        styleTabs.setRoundRadius(12);
        styleTabs.setOnItemLongClick(tab -> {
            if (tab.tone instanceof TL_aicompose.TL_aiComposeTone) {
                final TL_aicompose.TL_aiComposeTone tone = (TL_aicompose.TL_aiComposeTone) tab.tone;
                ItemOptions.makeOptions(container, resourcesProvider, tab)
                    .setScrimViewBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)))
                    .addIf(tone.creator, R.drawable.msg_edit, getString(R.string.AIEditorEditStyle), () -> {
                        new CreateAiStyleAlert(getContext(), resourcesProvider)
                            .setEditing(tone)
                            .setOnToneEdited(newTone -> {
                                if (newTone instanceof TL_aicompose.TL_aiComposeTone) {
                                    tonesController.edit((TL_aicompose.TL_aiComposeTone) newTone);
                                }
                                updateStyles();
                            })
                            .show();
                    })
                    .add(R.drawable.msg_share, getString(R.string.AIEditorShareStyle), () -> {
                        final String link = "https://t.me/addstyle/" + tone.slug;
                        new ShareAlert(context, null, link, false, link, false, resourcesProvider) {
                            @Override
                            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                                if (!showToast) return;
                                final BulletinFactory bulletinFactory = BulletinFactory.of(bulletinContainer, resourcesProvider);
                                if (bulletinFactory != null) {
                                    if (dids.size() == 1) {
                                        long did = dids.keyAt(0);
                                        if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                                            bulletinFactory.createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AIEditorStyleSharedToSavedMessages)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                        } else if (did < 0) {
                                            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                                            bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AIEditorStyleSharedTo, topic != null ? topic.title : chat.title)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                        } else {
                                            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                                            bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AIEditorStyleSharedTo, user.first_name)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                        }
                                    } else {
                                        bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatPluralString("AIEditorStyleSharedToManyChats", dids.size(), dids.size()))).hideAfterBottomSheet(false).show();
                                    }
                                    try {
                                        bulletinContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }.show();
                    })
                    .addIf(!tone.creator, R.drawable.msg_delete, getString(R.string.AIEditorRemoveStyle), true, () -> {
                        tonesController.unsave(tone);
                    })
                    .addIf(tone.creator, R.drawable.msg_delete, getString(R.string.AIEditorDeleteStyle), true, () -> {
                        new AlertDialog.Builder(getContext(), resourcesProvider)
                            .setTitle(getString(R.string.AIEditorDeleteStyle))
                            .setMessage(getString(R.string.AIEditorDeleteStyleText))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                                final Browser.Progress progress = di.makeButtonLoading(BUTTON_POSITIVE);

                                progress.init();
                                final TL_aicompose.deleteTone req = new TL_aicompose.deleteTone();
                                req.tone = TL_aicompose.InputAiComposeTone.from(tone);
                                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (bool, err) -> {
                                    progress.end();

                                    MessagesController.getInstance(currentAccount).getTonesController().remove(tone);
                                    updateStyles();
                                });
                            })
                            .makeRed(AlertDialog.BUTTON_POSITIVE)
                            .show();
                    })
                    .show();
                return true;
            }
            return false;
        });
        updateStyles();
        styleTabs.selectTab(-1);

        to_lang = TranslateAlert2.getToLanguage();
        if (to_lang == null) {
            to_lang = TranslateController.currentLanguage();
        }

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);
        topPadding = 0.35f;

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setPadding(dp(12), dp(6), dp(12), dp(12));
        buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
            Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
            getThemedColor(Theme.key_windowBackgroundGray),
            getThemedColor(Theme.key_windowBackgroundGray)
        }));

        button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.OK));
        buttonContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL));

        sendButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        sendButton.setOnClickListener(v -> {
            if (onSendListener != null && getResultText() != null) {
                onSendListener.run(getResultText(), 0, 0, true);
            }
            dismiss();
        });
        sendButton.setOnLongClickListener(v -> {
            if (editing) return false;
            if (onSendListener == null || getResultText() == null) return false;
            final boolean self = dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
            ItemOptions.makeOptions(container, resourcesProvider, sendButton)
                .addIf(!self, R.drawable.input_notify_off, getString(R.string.SendWithoutSound), () -> {
                    onSendListener.run(getResultText(), 0, 0, false);
                    dismiss();
                })
                .add(R.drawable.msg_calendar2, getString(self ? R.string.SetReminder : R.string.ScheduleMessage), () -> {
                    AlertsCreator.createScheduleDatePickerDialog(context, dialogId, new AlertsCreator.ScheduleDatePickerDelegate() {
                        @Override
                        public void didSelectDate(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                            onSendListener.run(getResultText(), scheduleDate, scheduleRepeatPeriod, notify);
                            dismiss();
                        }
                    }, resourcesProvider);
                })
                .show();
            return true;
        });
        buttonContainer.addView(sendButton, LayoutHelper.createLinear(48, 48, Gravity.RIGHT, 10, 0, 0, 0));

        allButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        final SpannableStringBuilder limitButtonText = new SpannableStringBuilder(getString(R.string.AIEditorLimitButton));
        limitButtonText.append(" ");
        int from = limitButtonText.length();
        limitButtonText.append("x50");
        limitButtonText.setSpan(new LimitSpan("x50"), from, limitButtonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        allButton.setText(limitButtonText);
        allButton.setOnClickListener(v -> {
            new PremiumFeatureBottomSheet(getContext(), PremiumPreviewFragment.PREMIUM_FEATURE_AI_EDITOR, true, resourcesProvider).show();
        });

        FrameLayout.LayoutParams buttonContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
        buttonContainerLayoutParams.leftMargin += backgroundPaddingLeft;
        buttonContainerLayoutParams.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonContainer, buttonContainerLayoutParams);

        FrameLayout.LayoutParams allButtonLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 12, 6, 12, 12);
        allButtonLayoutParams.leftMargin += backgroundPaddingLeft;
        allButtonLayoutParams.rightMargin += backgroundPaddingLeft;
        containerView.addView(allButton, allButtonLayoutParams);

        bulletinContainer = new FrameLayout(context);
        final FrameLayout.LayoutParams bulletinContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 0, 0, 0, 12 + 48);
        bulletinContainerLayoutParams.leftMargin += backgroundPaddingLeft;
        bulletinContainerLayoutParams.rightMargin += backgroundPaddingLeft;
        containerView.addView(bulletinContainer, bulletinContainerLayoutParams);

        updateButton(false, false);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(6 + 48 + 12));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSections();
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item == null) return;
        });

        takeTranslationIntoAccount = true;
        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateStyleHintY();
            }
        });

        adapter.update(false);

        AndroidUtilities.runOnUIThread(this::showStyleHint);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.loadedAiComposeTones);
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.loadedAiComposeTones);
        if (tonesController != null) {
            tonesController.open = false;
        }
        super.dismiss();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.loadedAiComposeTones) {
            updateStyles();
        }
    }

    private void updateStyles() {
        final TL_aicompose.AiComposeTone wasSelectedTone = styleTabs.getSelectedTone();
        styleTabs.clearTabs();
        styleTabs.addTab(null, this::selectStyle);
        for (TL_aicompose.AiComposeTone tone : tonesController.tones) {
            styleTabs.addTab(tone, this::selectStyle);
        }
        if (wasSelectedTone != styleTabs.getSelectedTone()) {
            styleTabs.selectTone(null, true);
        }
    }

    private void updateSendButtonIcon() {
        sendButton.setVisibility(editing ? View.GONE : View.VISIBLE);
        final SpannableStringBuilder sendButtonText = new SpannableStringBuilder(getString(R.string.Send));
        final ColoredImageSpan sendIconSpan = new ColoredImageSpan(editing ? R.drawable.filled_profile_edit_24 : R.drawable.send_plane_24);
        sendIconSpan.setTranslateY(dp(1));
        sendButtonText.setSpan(sendIconSpan, 0, sendButtonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sendButton.setText(sendButtonText);
    }

    private void updateButton(boolean showLimit) {
        updateButton(showLimit, true);
    }
    private boolean buttonShowLimit;
    private void updateButton(boolean showLimit, boolean animated) {
        if (animated && buttonShowLimit == showLimit) return;
        buttonShowLimit = showLimit;
        if (animated) {
            allButton.setVisibility(View.VISIBLE);
            buttonContainer.setVisibility(View.VISIBLE);
            allButton.animate()
                .alpha(showLimit ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> {
                    if (!showLimit) allButton.setVisibility(View.GONE);
                })
                .start();
            buttonContainer.animate()
                .alpha(!showLimit ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> {
                    if (showLimit) buttonContainer.setVisibility(View.GONE);
                })
                .start();
        } else {
            allButton.setVisibility(showLimit ? View.VISIBLE : View.GONE);
            allButton.setAlpha(showLimit ? 1.0f : 0.0f);
            buttonContainer.setVisibility(!showLimit ? View.VISIBLE : View.GONE);
            buttonContainer.setAlpha(!showLimit ? 1.0f : 0.0f);
        }
    }

    private void showStyleHint() {
        if (styleHint != null) {
            styleHint.hide();
            styleHint = null;
        }

        styleHint = new HintView2(getContext(), HintView2.DIRECTION_TOP);
        styleHint.setRoundingWithCornerEffect(false);
        styleHint.setPadding(dp(8), 0, dp(8), 0);
        styleHint.setRounding(20);
        styleHint.setShadow(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .25f));
        styleHint.setText(getString(R.string.AIEditorChooseStyle));
        styleHint.setJoint(0.5f, 0);
        styleHint.setDuration(8000L);
        containerView.addView(styleHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        styleHint.show();

        updateStyleHintY();
    }

    private void updateStyleHintY() {
        if (styleHint == null) return;

        View styleTabs = null;
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            final View child = recyclerListView.getChildAt(i);
            final int position = recyclerListView.getChildAdapterPosition(child) - 1;
            final UItem item = adapter.getItem(position);
            if (item != null && item.view == this.styleTabs) {
                styleTabs = child;
                break;
            }
        }
        if (styleTabs != null) {
            styleHint.setVisibility(View.VISIBLE);
            styleHint.setTranslationY(recyclerListView.getY() + styleTabs.getY() + styleTabs.getHeight());
        } else {
            styleHint.setVisibility(View.INVISIBLE);
            styleHint.hide();
        }
    }

    private void selectTab(int tab) {
        if (tabs.getSelectedTab() == tab) return;
        if (styleHint != null) {
            styleHint.hide();
        }
        tabs.selectTab(tab);
        request();
        adapter.update(true);
    }

    public static void showStylesLimitToast(BulletinFactory bulletinFactory, int currentAccount) {
        if (bulletinFactory == null || bulletinFactory.getContext() == null) return;
        final MessagesController m = MessagesController.getInstance(currentAccount);
        final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
        bulletinFactory.createSimpleBulletin(
            !isPremium ? R.raw.star_premium_2 : R.raw.error,
            getString(R.string.AIEditorStyleLimitTitle),
            replaceSingleTag(
                !isPremium ?
                    formatString(R.string.AIEditorStyleLimitTextPremium, m.config.aicomposeToneSavedLimitDefault.get(), m.config.aicomposeToneSavedLimitPremium.get()) :
                    formatString(R.string.AIEditorStyleLimitText, m.config.aicomposeToneSavedLimitPremium.get()),
                () -> {
                    new PremiumFeatureBottomSheet(bulletinFactory.getContext(), PremiumPreviewFragment.PREMIUM_FEATURE_AI_EDITOR, true, bulletinFactory.getResourcesProvider()).show();
                }
            )
        ).show();
    }

    private void selectStyle(TL_aicompose.AiComposeTone tone) {
        if (tone == null) {
            if (
                tonesController.getSavedTonesCount() + 1 >
                    (UserConfig.getInstance(currentAccount).isPremium() ?
                        MessagesController.getInstance(currentAccount).config.aicomposeToneSavedLimitPremium.get() :
                        MessagesController.getInstance(currentAccount).config.aicomposeToneSavedLimitDefault.get()
                    )
            ) {
                showStylesLimitToast(BulletinFactory.of(bulletinContainer, resourcesProvider), currentAccount);
                return;
            }
            new CreateAiStyleAlert(getContext(), resourcesProvider)
                .setOnToneCreated(newTone -> {
                    tonesController.tones.add(0, newTone);
                    updateStyles();

                    BulletinFactory.of(bulletinContainer, resourcesProvider)
                        .createEmojiBulletin(
                            newTone.emoji_id,
                            formatString(R.string.AIEditorToneCreatedTitle, newTone.title),
                            getString(R.string.AIEditorToneCreatedText)
                        )
                        .show();
                })
                .show();
            return;
        }
        if (styleTabs.getSelectedTone() == tone) return;
        if (styleHint != null) {
            styleHint.hide();
        }
        styleTabs.selectTone(tone);
        request();
        adapter.update(true);
    }

    private void selectStyle(int tab) {
        if (styleTabs.getSelectedTab() == tab) return;
        if (styleHint != null) {
            styleHint.hide();
        }
        styleTabs.selectTab(tab);
        request();
        adapter.update(true);
    }

    @Override
    protected void onContainerViewTranslation() {
        super.onContainerViewTranslation();
        if (keyboardContentAnimator != null) {
            buttonContainer.setTranslationY(-(float) keyboardContentAnimator.getAnimatedValue());
        } else {
            buttonContainer.setTranslationY(0);
        }
    }

    @Override
    protected void onActionBarAlpha(float alpha) {
        closeView.setAlpha(1.0f - alpha);
        closeView.setScaleX(lerp(0.6f, 1.0f, 1.0f - alpha));
        closeView.setScaleY(lerp(0.6f, 1.0f, 1.0f - alpha));
    }

    public static CharSequence copy(CharSequence c) {
        if (!(c instanceof Spanned))
            return c.toString();
        Spanned spanned = (Spanned) c;
        SpannableStringBuilder s = new SpannableStringBuilder(c.toString());
        final Class[] spanClasses = new Class[] {
            TextStyleSpan.class,
            CodeHighlighting.Span.class,
            SquigglyLinesSpan.class,
            URLSpanUserMention.class,
            URLSpanReplacement.class,
            URLSpanMono.class,
            URLSpanNoUnderline.class,
            FormattedDateSpan.class,
            URLSpanBrowser.class,
            URLSpanBotCommand.class,
            AnimatedEmojiSpan.class
        };
        for (Class clazz : spanClasses) {
            Object[] spans = spanned.getSpans(0, spanned.length(), clazz);
            for (Object span : spans) {
                final int start = spanned.getSpanStart(span);
                final int end = spanned.getSpanEnd(span);
                s.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    public AIEditorAlert setText(String from_lang, CharSequence text) {
        this.text = copy(text);
        this.from_lang = from_lang;
        return this;
    }
    public AIEditorAlert setText(CharSequence text) {
        this.text = copy(text);
        if (LanguageDetector.hasSupport()) {
            LanguageDetector.detectLanguage(text.toString(), lng -> {
                from_lang = lng;
                adapter.update(true);
            }, e -> {
                FileLog.e(e);
            });
        }
        return this;
    }
    public AIEditorAlert setOnUse(Utilities.Callback<CharSequence> onUse) {
        this.onUseListener = onUse;
        return this;
    }
    public AIEditorAlert setOnSend(long dialogId, boolean editing, Utilities.Callback4<CharSequence, Integer, Integer, Boolean> onSend) {
        this.dialogId = dialogId;
        this.editing = editing;
        this.onSendListener = onSend;
        return this;
    }

    private CharSequence title;
    private RLottieDrawable titleLoadingDrawable;

    @Override
    protected CharSequence getTitle() {
        if (title == null) {
            title = getString(R.string.AIEditor);
            titleLoadingDrawable = new RLottieDrawable(R.raw.emoji_stars, "emoji_stars", dp(24), dp(24));
            titleLoadingDrawable.setAllowDecodeSingleFrame(true);
            titleLoadingDrawable.setAutoRepeat(1);
        }
        return title;
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private boolean collapsed = true;

    private void toggleEmojify(View view) {
        emojify = !emojify;
        request();
        if (view instanceof LinearLayout) {
            final LinearLayout linearLayout = (LinearLayout) view;
            if (linearLayout.getChildAt(0) instanceof CheckBox2) {
                ((CheckBox2) linearLayout.getChildAt(0)).setChecked(emojify, true);
            }
        }
    }

    private CharSequence getResultText() {
        if (loading) return null;
        final int tab = tabs.getSelectedTab();
        if (tab == TAB_TRANSLATE) {
            if (translatedTextLoading) return null;
            return translatedText;
        } else if (tab == TAB_FIX) {
            if (fixedTextLoading) return null;
            return fixedTextToCopy;
        } else {
            if (styledTextLoading) return null;
            if (styledText == null)
                return text;
            return styledText;
        }
    }

    private void copyResult(View view) {
        if (loading) return;
        AndroidUtilities.addToClipboard(getResultText());
    }

    private void collapse(View view) {
        collapsed = false;
        saveScrollPosition();
        adapter.update(true);
        applyScrolledPosition(true);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCustomShadow(tabsContainer));
        items.add(UItem.asShadow(null));
        adapter.itemsOffset = 1;
        adapter.whiteSectionStart();
        int tab = tabs != null ? tabs.getSelectedTab() : TAB_TRANSLATE;
        if (tab == TAB_TRANSLATE) {
            if (from_lang != null && !from_lang.equalsIgnoreCase("und")) {
                String lng = languageName(from_lang, null, genitive);
                String from = getString(genitive != null && genitive[0] ? R.string.AIEditorFrom : R.string.AIEditorFromOther);
                int index = from.indexOf("%s");
                String before, after;
                if (index < 0) {
                    before = ""; after = "";
                } else {
                    before = from.substring(0, index);
                    after = from.substring(index + 2);
                }
                if (TextUtils.isEmpty(before)) lng = capitalFirst(lng);
                items.add(TranslateAlert3.Header.Factory.of(3, before, lng, after, null));
            } else {
                items.add(TranslateAlert3.Header.Factory.of(3, getString(R.string.AIEditorOriginalText), null, null, null));
            }
            items.add(TranslateAlert3.Text.Factory.of(4, text, collapsed, false, this::collapse, null, null));

            String lng = languageName(to_lang, accusative);
            String to = getString(accusative != null && accusative[0] ? R.string.AIEditorTo : R.string.AIEditorToOther);
            int index = to.indexOf("%s");
            String before, after;
            if (index < 0) {
                before = ""; after = "";
            } else {
                before = to.substring(0, index);
                after = to.substring(index + 2);
            }
            if (TextUtils.isEmpty(before)) lng = capitalFirst(lng);
            items.add(TranslateAlert3.Header.Factory.of(5, before, lng + (translateToneTitle != null ? " (" + translateToneTitle + ")" : ""), after, this::onToLangMenu, emojify, this::toggleEmojify, null));
            items.add(TranslateAlert3.Text.Factory.of(translatedTextLoading ? 7 : 6, translatedText, false, false, null, null, !translatedTextLoading ? this::copyResult : null));
        } else if (tab == TAB_STYLE) {
            items.add(UItem.asCustom(styleTabs));
            if (styleTabs != null && styleTabs.getSelectedTab() < 0 && !emojify) {
                items.add(TranslateAlert3.Header.Factory.of(5, getString(R.string.AIEditorOriginal), null, null, null, emojify, this::toggleEmojify, null));
                items.add(TranslateAlert3.Text.Factory.of(styledTextLoading ? 7 : 6, text, false, false, null, null, null));
            } else {
                items.add(TranslateAlert3.Header.Factory.of(5, getString(R.string.AIEditorResult), null, null, null, emojify, this::toggleEmojify, null));
                items.add(TranslateAlert3.Text.Factory.of(styledTextLoading ? 7 : 6, styledText, false, false, null, null, !styledTextLoading ? this::copyResult : null));
            }
        } else if (tab == TAB_FIX) {
            items.add(TranslateAlert3.Header.Factory.of(3, getString(R.string.AIEditorOriginal), null, null, null));
            items.add(TranslateAlert3.Text.Factory.of(4, text, collapsed, false, this::collapse, null, null));

            items.add(TranslateAlert3.Header.Factory.of(5, getString(R.string.AIEditorResult), null, null, null));
            items.add(TranslateAlert3.Text.Factory.of(fixedTextLoading ? 7 : 6, fixedText, false, false, null, null, !fixedTextLoading ? this::copyResult : null));
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(null));
    }

    @Override
    public void show() {
        super.show();
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        updateSendButtonIcon();
        adapter.update(false);
        request();

        if (onUseListener != null) {
            button.setText(getString(R.string.AIEditorApply));
            button.setOnClickListener(v -> {
                if (onUseListener != null && getResultText() != null) {
                    onUseListener.run(getResultText());
                }
                dismiss();
            });
        }
    }

    private void onToLangMenu(View btn) {
        final ItemOptions o = ItemOptions.makeOptions(container, resourcesProvider, btn);
        o.setMaxHeight(dp(450));
        o.setDrawScrim(false);
        o.setOnTopOfScrim();

        final ScrollView scrollView = new ScrollView(getContext());
        final LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(list);
        o.addView(scrollView);

        final ArrayList<TranslateController.Language> suggestedLanguages = TranslateController.getSuggestedLanguages(null);
        final ArrayList<TranslateController.Language> allLanguages = TranslateController.getLanguages();

        if (!TextUtils.isEmpty(to_lang)) {
            addChecked(o, list, true, TranslateAlert2.capitalFirst(TranslateAlert2.languageName(to_lang)), null);
        }
        for (final TranslateController.Language lng : suggestedLanguages) {
            if (!TextUtils.equals(lng.code, to_lang)) {
                addChecked(o, list, false, lng.displayName, () -> {
                    cancelRequest();
                    to_lang = lng.code;
                    TranslateAlert2.setToLanguage(to_lang);
                    request();
                });
            }
        }

        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        list.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        for (final TranslateController.Language lng : allLanguages) {
            addChecked(o, list, TextUtils.equals(lng.code, to_lang), lng.displayName, () -> {
                cancelRequest();
                to_lang = lng.code;
                TranslateAlert2.setToLanguage(to_lang);
                request();
            });
        }

//        o.addSpaceGap(false);
//
//        final Tabs tabs = new Tabs(getContext(), currentAccount, LinearLayout.VERTICAL, false, resourcesProvider);
//        tabs.setPadding(dp(8), dp(8), dp(8), dp(8));
//        tabs.setRoundRadius(12);
//        final Utilities.Callback<Integer> selectStyle = tab -> {
//            if (styleHint != null) {
//                styleHint.hide();
//            }
//            translateTone = tab == 0 ? null : tones[tab - 1];
//            translateToneTitle = tab == 0 ? null : toneTitles[tab - 1];
//            request();
//            o.dismiss();
//        };
//        tabs.addTab("🏳", getString(R.string.AIEditorToneNeutral), null, selectStyle);
//        for (int i = 0; i < tones.length; ++i) {
//            tabs.addTab(null, toneTitles[i], toneDocumentId[i], selectStyle);
//        }
//        tabs.selectTab(translateTone == null ? 0 : 1 + indexOf(tones, translateTone), false);
//        o.addView(tabs, LayoutHelper.createLinear(72, LayoutHelper.MATCH_PARENT));

//        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, height, !vertical ? -8 : 0, vertical ? -8 : 0, 0, 0)
//        if (tabs.getParent() instanceof LinearLayout && ((LinearLayout) tabs.getParent()).getParent() instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
//            final ActionBarPopupWindow.ActionBarPopupWindowLayout layout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) ((LinearLayout) tabs.getParent()).getParent();
//            layout.setLayoutParams();
//        }

        o.show();
    }

    private int indexOf(String[] strings, String string) {
        for (int i = 0; i < strings.length; ++i) {
            if (TextUtils.equals(strings[i], string))
                return i;
        }
        return -1;
    }

    private void addChecked(final ItemOptions o, LinearLayout list, boolean checked, CharSequence text, Runnable onClick) {
        final int textColorKey = Theme.key_actionBarDefaultSubmenuItem;
        final int iconColorKey = Theme.key_actionBarDefaultSubmenuItemIcon;

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(getContext(), true, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);
        subItem.setText(text);
        subItem.setChecked(checked);

        subItem.setColors(Theme.getColor(textColorKey, resourcesProvider), Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        subItem.setOnClickListener(view -> {
            o.dismiss();
            if (!checked && onClick != null) {
                onClick.run();
            }
        });
        list.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private int estimateLinesCount() {
        final int tab = tabs.getSelectedTab();
        CharSequence text = this.text;
        if (tab == TAB_TRANSLATE && translatedText != null) text = translatedText;
        if (tab == TAB_STYLE && styledText != null) text = styledText;
        if (tab == TAB_FIX && fixedText != null) text = fixedText;

        final TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(dp(16));
        final Layout layout = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x - dp(20 + 20 + 12 + 12) - backgroundPaddingLeft - backgroundPaddingLeft, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        return MathUtils.clamp(layout.getLineCount(), 1, 10);
    }

    private int requestId = -1;

    private boolean loading;
    private TLRPC.TL_messages_composeMessageWithAI[] lastRequest = new TLRPC.TL_messages_composeMessageWithAI[3];
    private void request() {
        final TLRPC.TL_textWithEntities fromText = new TLRPC.TL_textWithEntities();
        final CharSequence[] message = new CharSequence[] { text };
        fromText.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
        fromText.text = message[0] == null ? "" : message[0].toString();

        final int tab = tabs.getSelectedTab();

        final TLRPC.TL_messages_composeMessageWithAI req = new TLRPC.TL_messages_composeMessageWithAI();
        req.text = fromText;
        if (tab == TAB_TRANSLATE) {
            req.translate_to_lang = normalizeLanguage(to_lang);
            req.tone = TL_aicompose.InputAiComposeTone.fromDefault(translateTone);
            req.emojify = emojify;
        } else if (tab == TAB_STYLE) {
            final TL_aicompose.AiComposeTone tone = styleTabs.getSelectedTone();
            if (tone instanceof TL_aicompose.TL_aiComposeTone) {
                final TL_aicompose.inputAiComposeToneID input = new TL_aicompose.inputAiComposeToneID();
                input.id = ((TL_aicompose.TL_aiComposeTone) tone).id;
                input.access_hash = ((TL_aicompose.TL_aiComposeTone) tone).access_hash;
                req.tone = input;
            } else if (tone instanceof TL_aicompose.TL_aiComposeToneDefault) {
                final TL_aicompose.inputAiComposeToneDefault input = new TL_aicompose.inputAiComposeToneDefault();
                input.tone = ((TL_aicompose.TL_aiComposeToneDefault) tone).tone;
                req.tone = input;
            }
            req.emojify = emojify;
        } else if (tab == TAB_FIX) {
            req.proofread = true;
        }

        final TLRPC.TL_messages_composeMessageWithAI lastRequest = this.lastRequest[tab];
        if (lastRequest != null && (
            lastRequest.proofread == req.proofread &&
            lastRequest.emojify == req.emojify &&
            TL_aicompose.InputAiComposeTone.equals(lastRequest.tone, req.tone) &&
            TextUtils.equals(lastRequest.translate_to_lang, req.translate_to_lang)
        )) {
            return;
        } else if (!req.emojify && !req.proofread && req.tone == null && req.translate_to_lang == null) {
            return;
        }

        button.setLoading(loading = true);

        final SimpleTextView actionBarTitleTextView = actionBar.getTitleTextView();
        actionBarTitleTextView.setRightDrawable(titleLoadingDrawable);
        titleLoadingDrawable.start();

        int linesCount = estimateLinesCount();
        final SpannableStringBuilder loadingText = new SpannableStringBuilder();
        for (int i = 0; i < linesCount; ++i) {
            if (i > 0) loadingText.append("\n");
            int w = dp((int) (Math.random() * 50));
            int from = loadingText.length();
            loadingText.append(getString(R.string.Loading));
            loadingText.setSpan(new LoadingSpan(null, w, 0).setHeight(dp(6)).setAlpha(0.5f).setFullWidth(true), from, loadingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (tab == TAB_TRANSLATE) {
            translatedTextLoading = true;
            translatedText = loadingText;
        } else if (tab == TAB_STYLE) {
            styledTextLoading = true;
            styledText = loadingText;
        } else if (tab == TAB_FIX) {
            fixedTextLoading = true;
            fixedText = loadingText;
        }

        requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            requestId = -1;

            button.setLoading(loading = false);
            if (err != null && ("SUMMARY_FLOOD_PREMIUM".equalsIgnoreCase(err.text) || "AICOMPOSE_FLOOD_PREMIUM".equalsIgnoreCase(err.text))) {
                BulletinFactory.of(bulletinContainer, resourcesProvider)
                    .createSimpleBulletin(R.raw.star_premium_2, getString(R.string.AIEditorLimitTitle), AndroidUtilities.replaceTags(getString(R.string.AIEditorLimitText)))
                    .show();
                updateButton(true);
                return;
            } else if (err != null) {
                BulletinFactory.of(bulletinContainer, resourcesProvider).showForError(err);

                actionBarTitleTextView.setRightDrawable(null);
                button.setText(getString(R.string.OK));
                button.setOnClickListener(v -> dismiss());
                updateButton(false);
                return;
            }
            if (res == null) {
                actionBarTitleTextView.setRightDrawable(null);

                button.setText(getString(R.string.OK));
                button.setOnClickListener(v -> dismiss());
                updateButton(false);
                return;
            }

            actionBarTitleTextView.setRightDrawable(null);
            updateButton(false);
            this.lastRequest[tab] = req;
            if (tab == TAB_TRANSLATE) {
                translatedTextLoading = false;
                translatedText = MessageObject.formatTextWithEntities(res.result_text);
            } else if (tab == TAB_STYLE) {
                styledTextLoading = false;
                styledText = MessageObject.formatTextWithEntities(res.result_text);
            } else if (tab == TAB_FIX) {
                fixedTextLoading = false;
                if (res.diff_text != null) {
                    fixedText = MessageObject.formatTextWithEntities(res.diff_text);
                    fixedTextToCopy = MessageObject.formatTextWithEntities(res.result_text);
                } else {
                    final CharSequence result = MessageObject.formatTextWithEntities(res.result_text);
                    fixedText = fixedTextToCopy = result;
                }
            }

            adapter.update(true);
        });

        adapter.update(true);
    }
    private void cancelRequest() {
        if (requestId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
            requestId = -1;
        }
        loading = false;
        final SimpleTextView actionBarTitleTextView = actionBar.getTitleTextView();
        if (actionBarTitleTextView != null) {
            actionBarTitleTextView.setRightDrawable(null);
        }
    }

    public static final class Tabs extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final LinearLayout layout;
        private final FrameLayout scrollView;
        private int roundRadiusDp;
        private boolean divider;

        private int selectedTab;
        private AnimatedFloat animatedSelectedTab;

        private Utilities.CallbackReturn<Tab, Boolean> onLongClick;
        public Tabs setOnItemLongClick(Utilities.CallbackReturn<Tab, Boolean> onLongClick) {
            this.onLongClick = onLongClick;
            return this;
        }

        public Tabs(Context context, int currentAccount, boolean withScroll, Theme.ResourcesProvider resourcesProvider) {
            this(context, currentAccount, LinearLayout.HORIZONTAL, withScroll, resourcesProvider);
        }
        public Tabs(Context context, int currentAccount, int orientation, boolean withScroll, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context) {
                private final RectF floorRect = new RectF();
                private final RectF ceilRect  = new RectF();
                private final RectF rect = new RectF();
                private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float selected = animatedSelectedTab == null ? 0.0f : animatedSelectedTab.set(selectedTab);

                    final int floor = (int) Math.floor(selected);
                    final int ceil = (int) Math.ceil(selected);
                    final float t = selected - floor;

                    if (floor >= 0 && floor < getChildCount()) {
                        final View child = getChildAt(floor);
                        floorRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                    }
                    if (ceil >= 0 && ceil < getChildCount()) {
                        final View child = getChildAt(ceil);
                        ceilRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                    }
                    lerp(floorRect, ceilRect, t, rect);
                    selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f));
                    canvas.drawRoundRect(rect, dp(roundRadiusDp), dp(roundRadiusDp), selectorPaint);

                    for (int i = 0; i < getChildCount(); ++i) {
                        final View child = getChildAt(i);
                        if (child instanceof Tab) {
                            final float childSelected = Math.max(0, 1.0f - Math.abs(i - selected));
                            ((Tab) child).updateSelected(childSelected, false);
                        }
                    }

                    super.dispatchDraw(canvas);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final boolean horiz = getOrientation() == LinearLayout.HORIZONTAL;
                    final int availableWidth = horiz ? MeasureSpec.getSize(widthMeasureSpec) : MeasureSpec.getSize(heightMeasureSpec);
                    int totalNaturalWidth = 0;
                    int maximumChildWidth = 0;
                    for (int i = 0; i < getChildCount(); i++) {
                        final View child = getChildAt(i);
                        child.setPadding(dp(8), 0, dp(8), 0);
                        child.measure(
                             horiz ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) : widthMeasureSpec,
                            !horiz ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) : heightMeasureSpec
                        );
                        final int size = horiz ? child.getMeasuredWidth() : child.getMeasuredHeight();
                        maximumChildWidth = Math.max(maximumChildWidth, size);
                        totalNaturalWidth += size;
                    }
                    final boolean useWeight = totalNaturalWidth <= availableWidth && maximumChildWidth < (float) availableWidth / getChildCount();
                    for (int i = 0; i < getChildCount(); i++) {
                        final View child = getChildAt(i);
                        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                        child.setPadding(dp(8), 0, dp(8), 0);
                        if (useWeight) {
                            if (horiz) lp.width = 0; else lp.height = 0;
                            lp.weight = 1f;
                        } else {
                            if (horiz) {
                                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
                            } else {
                                lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                            }
                            lp.weight = 0f;
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            layout.setOrientation(orientation);
            animatedSelectedTab = new AnimatedFloat(layout, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

            if (withScroll) {
                if (orientation == LinearLayout.HORIZONTAL) {
                    scrollView = new HorizontalScrollView(context);
                    ((HorizontalScrollView) scrollView).setFillViewport(true);
                } else {
                    scrollView = new ScrollView(context);
                    ((ScrollView) scrollView).setFillViewport(true);
                }
                scrollView.addView(layout);
                addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            } else {
                scrollView = null;
                addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            }
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            layout.setPadding(left, top, right, bottom);
        }

        public void setDivider(boolean divider) {
            this.divider = divider;
        }

        public void setRoundRadius(int roundRadiusDp) {
            this.roundRadiusDp = roundRadiusDp;
        }

        public void clearTabs() {
            layout.removeAllViews();
        }

        public Tab addTab(int iconResId, CharSequence text, Utilities.Callback<Integer> onClick) {
            final int index = layout.getChildCount();
            final Tab tab = new Tab(getContext(), currentAccount, resourcesProvider);
            tab.setRoundRadius(roundRadiusDp);
            tab.set(iconResId, text);
            tab.setOnClickListener(v -> onClick.run(index));
//            if (scrollView != null) {
//                tab.layout.setPadding(dp(8), 0, dp(8), 0);
//            }
            layout.addView(tab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL));
            return tab;
        }

        public Tab addTab(String emoji, CharSequence text, Long documentId, Utilities.Callback<Integer> onClick) {
            final int index = layout.getChildCount();
            final Tab tab = new Tab(getContext(), currentAccount, resourcesProvider);
            tab.setRoundRadius(roundRadiusDp);
            tab.set(emoji, text, documentId);
            tab.setOnClickListener(v -> onClick.run(index));
//            if (scrollView != null) {
//                tab.layout.setPadding(dp(8), 0, dp(8), 0);
//            }
            layout.addView(tab, LayoutHelper.createLinear(
                layout.getOrientation() == LinearLayout.HORIZONTAL ? 0 : LayoutHelper.MATCH_PARENT,
                layout.getOrientation() == LinearLayout.VERTICAL   ? 0 : LayoutHelper.MATCH_PARENT,
                1,
                Gravity.FILL
            ));
            return tab;
        }

        public Tab addTab(TL_aicompose.AiComposeTone tone, Utilities.Callback<TL_aicompose.AiComposeTone> onClick) {
            final Tab tab = new Tab(getContext(), currentAccount, resourcesProvider);
            tab.tone = tone;
            tab.setRoundRadius(roundRadiusDp);
            if (tone == null) {
                tab.accent = false;
                tab.updateColors();
                tab.set(R.drawable.tone_create, getString(R.string.AIEditorStyleNewCreate));
            } else {
                tab.set(null, tone.title, tone.emoji_id);
            }
            tab.setOnClickListener(v -> onClick.run(tone));
            tab.setOnLongClickListener(v -> {
                if (onLongClick != null)
                    return onLongClick.run(tab);
                return false;
            });
            layout.addView(tab, LayoutHelper.createLinear(
                layout.getOrientation() == LinearLayout.HORIZONTAL ? 0 : LayoutHelper.MATCH_PARENT,
                layout.getOrientation() == LinearLayout.VERTICAL   ? 0 : LayoutHelper.MATCH_PARENT,
                1,
                Gravity.FILL
            ));
            return tab;
        }

        public int getSelectedTab() {
            return selectedTab;
        }

        public TL_aicompose.AiComposeTone getSelectedTone() {
            if (selectedTab < 0 || selectedTab >= layout.getChildCount()) return null;
            final View child = layout.getChildAt(selectedTab);
            if (!(child instanceof Tab)) return null;
            return ((Tab) child).tone;
        }

        public void selectTone(TL_aicompose.AiComposeTone tone) {
            selectTone(tone, true);
        }
        public void selectTone(TL_aicompose.AiComposeTone tone, boolean animated) {
            for (int i = 0; i < layout.getChildCount(); ++i) {
                final View child = layout.getChildAt(i);
                if (child instanceof Tab) {
                    if (((Tab) child).tone != null && ((Tab) child).tone == tone) {
                        selectTab(i, animated);
                        return;
                    }
                }
            }
        }

        public void selectTab(int tab) {
            selectTab(tab, true);
        }
        public void selectTab(int tab, boolean animated) {
            if (selectedTab == tab) return;
            selectedTab = tab;
            if (!animated) {
                animatedSelectedTab.force(selectedTab);
            }
            if (tab >= 0 && tab < layout.getChildCount()) {
                final View child = layout.getChildAt(tab);
                if (child instanceof Tab) {
                    if (((Tab) child).imageView.getAnimatedEmojiDrawable() != null) {
                        final AnimatedEmojiDrawable animatedEmojiDrawable = ((Tab) child).imageView.getAnimatedEmojiDrawable();
                        if (animatedEmojiDrawable.getImageReceiver() != null) {
                            animatedEmojiDrawable.getImageReceiver().startAnimation();
                        }
                    } else {
                        ((Tab) child).imageView.getImageReceiver().startAnimation();
                    }
                }
            }
            layout.invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (divider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null)
                    dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(10), getHeight() - 1, getWidth() - dp(10), getHeight(), dividerPaint);
            }
        }

        public static final class Tab extends FrameLayout implements Theme.Colorable {

            public final LinearLayout layout;
            private final int currentAccount;
            private final Theme.ResourcesProvider resourcesProvider;
            private int roundRadiusDp;

            public boolean accent = true;
            public TL_aicompose.AiComposeTone tone;
            private boolean isEmoji;
            private final BackupImageView imageView;
            private final TextView textView;

            public Tab(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
                super(context);
                this.currentAccount = currentAccount;
                this.resourcesProvider = resourcesProvider;

                layout = new LinearLayout(context);
                layout.setClipToPadding(false);
                layout.setOrientation(LinearLayout.VERTICAL);
                addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 2, 0, 2));

                imageView = new BackupImageView(context);
                NotificationCenter.listenEmojiLoading(imageView);
                layout.addView(imageView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));

                textView = new TextView(context);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                textView.setGravity(Gravity.CENTER);
                textView.setSingleLine();
                layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));

                ScaleStateListAnimator.apply(this, .05f, 1.5f);

                updateSelected(0, true);
            }

            public Tab setRoundRadius(int roundRadiusDp) {
                this.roundRadiusDp = roundRadiusDp;
                updateColors();
                return this;
            }

            public void set(int iconResId, CharSequence text) {
                isEmoji = false;
                imageView.setImageResource(iconResId);
                textView.setText(text);
            }

            public void set(final String emoji, CharSequence text, Long documentId) {
                isEmoji = true;
                imageView.setColorFilter(null);
                imageView.setImageDrawable(Emoji.getEmojiDrawable(emoji));
                textView.setText(text);

                int accountForEmoji = currentAccount;
                if (ConnectionsManager.getInstance(accountForEmoji).isTestBackend()) {
                    for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                        if (UserConfig.getInstance(i).isClientActivated() && !ConnectionsManager.getInstance(i).isTestBackend()) {
                            accountForEmoji = i;
                            break;
                        }
                    }
                }

                if (documentId != null) {
                    final AnimatedEmojiDrawable icon = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_EMOJI_STATUS, currentAccount, documentId);
                    imageView.setAnimatedEmojiDrawable(icon);
                } else if (!TextUtils.isEmpty(emoji)) {
                    final TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
                    inputStickerSetShortName.short_name = "RestrictedEmoji";
                    MediaDataController.getInstance(accountForEmoji).getStickerSet(inputStickerSetShortName, null, false, set -> {
                        if (set == null || set.set == null) return;
                        final String emojiToSearch = emoji.replace("\uFE0F", "");
                        TLRPC.Document document = null;
                        for (int k = 0; k < set.packs.size(); ++k) {
                            if (!set.packs.get(k).documents.isEmpty() && TextUtils.equals(set.packs.get(k).emoticon.replace("\uFE0F", ""), emojiToSearch)) {
                                long documentId1 = set.packs.get(k).documents.get(0);
                                for (int j = 0; j < set.documents.size(); ++j) {
                                    if (set.documents.get(j).id == documentId1) {
                                        document = set.documents.get(j);
                                        break;
                                    }
                                }
                                break;
                            }
                        }

                        if (document != null) {
                            final int size = 24;
                            final Drawable thumb = Emoji.getEmojiDrawable(emoji);
                            final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, size);
                            imageView.setImage(
                                ImageLocation.getForDocument(document),
                                size + "_" + size,
                                ImageLocation.getForDocument(photoSize, document),
                                size + "_" + size,
                                thumb,
                                null
                            );
                        }
                    });
                }
            }

            private float selected;
            public void updateSelected(float selected, boolean force) {
                if (!force && Math.abs(selected - this.selected) < 0.01f) return;
                this.selected = selected;
                final int imageColor = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                    selected
                );
                final int textColor = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                    selected
                );
                imageView.setColorFilter(!isEmoji ? new PorterDuffColorFilter(imageColor, PorterDuff.Mode.SRC_IN) : null);
                imageView.setEmojiColorFilter(new PorterDuffColorFilter(imageColor, PorterDuff.Mode.SRC_IN));
                imageView.invalidate();
                textView.setTextColor(textColor);
            }

            @Override
            public void updateColors() {
                updateSelected(selected, true);
                setBackground(Theme.createRadSelectorDrawable(
                    accent ?
                        Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 0.10f) :
                        Theme.getColor(Theme.key_listSelector, resourcesProvider),
                    roundRadiusDp, roundRadiusDp
                ));
            }
        }

    }

    private final class LimitSpan extends ReplacementSpan {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Text text;

        public LimitSpan(CharSequence text) {
            this.text = new Text(text, 13, AndroidUtilities.getTypeface("fonts/num.otf"));
            this.text.paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return (int) (this.text.getCurrentWidth() + dp(6.66f));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final float cy = (top + bottom) / 2f;
            AndroidUtilities.rectTmp.set(x, cy - dp(7.66f), x + this.text.getCurrentWidth() + dp(6.66f), cy + dp(7.66f));

            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 0xFF, Canvas.ALL_SAVE_FLAG);

            this.paint.setColor(paint.getColor());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), this.paint);

            this.text.draw(canvas, x + dp(3.33f), cy, 0xFFFFFFFF, 1.0f);

            canvas.restore();
        }
    }

    public static class CreateAiStyleAlert extends BottomSheetWithRecyclerListView {

        private final ImageView closeView;

        private final FrameLayout iconCell;
        private final FrameLayout iconButton;
        private final BackupImageView icon;
        private final EditTextCell titleCell;
        private final EditTextCell promptCell;

        private final FrameLayout checkboxCell;
        private final CheckBox2 checkbox;

        private final FrameLayout buttonContainer;
        private final FrameLayout bulletinContainer;
        private final ButtonWithCounterView button;

        private Long emoji_id;

        private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;

        public CreateAiStyleAlert(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, null, true, false, false, false, ActionBarType.SLIDING, resourcesProvider);

            closeView = new ImageView(context);
            closeView.setScaleType(ImageView.ScaleType.CENTER);
            closeView.setImageResource(R.drawable.ic_close_white);
            closeView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            closeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .10f)));
            actionBar.addView(closeView, LayoutHelper.createFrame(54, 54, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 0));
            ScaleStateListAnimator.apply(closeView, .1f, 1.5f);
            closeView.setOnClickListener(v -> this.dismiss());

            iconCell = new FrameLayout(context);
            iconButton = new FrameLayout(context);
            iconButton.setBackground(Theme.createCircleDrawable(dp(100), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            ScaleStateListAnimator.apply(iconButton);
            iconCell.addView(iconButton, LayoutHelper.createFrame(100, 100, Gravity.CENTER));
            icon = new BackupImageView(context);
            updateIcon();
            iconButton.addView(icon, LayoutHelper.createFrame(64, 64, Gravity.CENTER));
            iconButton.setOnClickListener(v -> {
                openIconDialog();
            });

            titleCell = new EditTextCell(context, getString(R.string.AIEditorStyleTitleHint), false, false, MessagesController.getInstance(currentAccount).config.aicomposeToneTitleLengthMax.get(), resourcesProvider);
            titleCell.editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    updateButton();
                }
            });
            promptCell = new EditTextCell(context, getString(R.string.AIEditorStylePromptHint), true, false, MessagesController.getInstance(currentAccount).config.aicomposeTonePromptLengthMax.get(), resourcesProvider);
            promptCell.setShowLimitWhenNear(Math.max(100, MessagesController.getInstance(currentAccount).config.aicomposeTonePromptLengthMax.get() / 2));
            promptCell.editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    updateButton();
                }
            });


            LinearLayout checkboxLayout = new LinearLayout(context);
            checkboxLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
            checkboxLayout.setClipToPadding(false);
            checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
            checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 24, 24));
            checkbox = new CheckBox2(context, 24, resourcesProvider);
            checkbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            checkbox.setDrawUnchecked(true);
            checkbox.setChecked(false, false);
            checkbox.setDrawBackgroundAsArc(10);
            checkboxLayout.addView(checkbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            final TextView checkboxTextView = new TextView(context);
            checkboxTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            checkboxTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            checkboxTextView.setText(getString(R.string.AIEditorStyleAddLink));
            checkboxLayout.addView(checkboxTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
            checkboxLayout.setOnClickListener(v -> {
                checkbox.setChecked(!checkbox.isChecked(), true);
            });

            checkboxCell = new FrameLayout(context);
            checkboxCell.addView(checkboxLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 2, 2, 2, 2));

            behindKeyboardColorKey = Theme.key_windowBackgroundGray;
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(6 + 48 + 12));
            recyclerListView.setClipToPadding(false);
            recyclerListView.setSections();
            recyclerListView.setOnItemClickListener((view, position) -> {
                final UItem item = adapter.getItem(position - 1);
                if (item == null) return;

                if (item.id == 1) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.AIEditorDeleteStyle))
                        .setMessage(getString(R.string.AIEditorDeleteStyleText))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                            final Browser.Progress progress = di.makeButtonLoading(BUTTON_POSITIVE);

                            progress.init();
                            final TL_aicompose.deleteTone req = new TL_aicompose.deleteTone();
                            req.tone = TL_aicompose.InputAiComposeTone.from(editing);
                            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (bool, err) -> {
                                progress.end();
                                di.dismiss();
                                dismiss();

                                MessagesController.getInstance(currentAccount).getTonesController().remove(editing);
                            });
                        })
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                }
            });

            ignoreTouchActionBar = false;
            headerMoveTop = dp(12);
            topPadding = 0.35f;
            smoothKeyboardAnimationEnabled = true;

            takeTranslationIntoAccount = true;
            final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                }
            };
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            recyclerListView.setItemAnimator(itemAnimator);

            buttonContainer = new FrameLayout(context);
            buttonContainer.setPadding(dp(12), dp(6), dp(12), dp(12));
            buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
                getThemedColor(Theme.key_windowBackgroundGray),
                getThemedColor(Theme.key_windowBackgroundGray)
            }));

            FrameLayout.LayoutParams buttonContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
            buttonContainerLayoutParams.leftMargin += backgroundPaddingLeft;
            buttonContainerLayoutParams.rightMargin += backgroundPaddingLeft;
            containerView.addView(buttonContainer, buttonContainerLayoutParams);

            bulletinContainer = new FrameLayout(context);
            FrameLayout.LayoutParams bulletinContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 48 + 12);
            bulletinContainerLayoutParams.leftMargin += backgroundPaddingLeft;
            bulletinContainerLayoutParams.rightMargin += backgroundPaddingLeft;
            containerView.addView(bulletinContainer, bulletinContainerLayoutParams);

            button = new ButtonWithCounterView(context, resourcesProvider).setRound();
            button.setText(getString(R.string.AIEditorStyleCreate));
            button.setOnClickListener(v -> {
                if (button.isLoading()) return;
                if (!button.isEnabled()) {
                    if (emoji_id == null) {
                        openIconDialog();
                    }
                    return;
                }

                button.setLoading(true);
                if (editing != null) {
                    final TL_aicompose.updateTone req = new TL_aicompose.updateTone();
                    req.flags |= TLObject.FLAG_0;
                    req.display_author = checkbox.isChecked();
                    req.tone = TL_aicompose.InputAiComposeTone.from(editing);
                    req.flags |= TLObject.FLAG_1;
                    req.emoji_id = emoji_id;
                    req.flags |= TLObject.FLAG_2;
                    req.title = titleCell.getText().toString();
                    req.flags |= TLObject.FLAG_3;
                    req.prompt = promptCell.getText().toString();
                    ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (tone, err) -> {
                        button.setLoading(false);
                        if (tone != null) {
                            if (onToneEdited != null) {
                                onToneEdited.run(tone);
                            }
                            dismiss();
                        } else if (err != null) {
                            BulletinFactory.of(bulletinContainer, resourcesProvider)
                                .showForError(err);
                        }
                    });

                } else {
                    final TL_aicompose.createTone req = new TL_aicompose.createTone();
                    req.display_author = checkbox.isChecked();
                    req.emoji_id = emoji_id;
                    req.title = titleCell.getText().toString();
                    req.prompt = promptCell.getText().toString();
                    ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (tone, err) -> {
                        button.setLoading(false);
                        if (tone != null) {
                            dismiss();
                            if (onToneCreated != null) {
                                onToneCreated.run(tone);
                            }
                        } else if (err != null) {
                            if ("TONES_SAVED_TOO_MANY".equalsIgnoreCase(err.text)) {
                                showStylesLimitToast(BulletinFactory.of(bulletinContainer, resourcesProvider), currentAccount);
                            } else {
                                BulletinFactory.of(bulletinContainer, resourcesProvider)
                                    .showForError(err);
                            }
                        }
                    });
                }
            });
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL));
            updateButton();

            adapter.update(false);
        }

        private void openIconDialog() {
            if (selectAnimatedEmojiDialog != null) {
                return;
            }
            final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
            SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(null, getContext(), true, dp(150), SelectAnimatedEmojiDialog.TYPE_AI_STYLE_ICON, resourcesProvider) {
                @Override
                protected boolean willApplyEmoji(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                    if (gift != null) {
                        final TL_stars.SavedStarGift savedStarGift = StarsController.getInstance(currentAccount).findUserStarGift(gift.id);
                        return savedStarGift == null || MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) >= 2;
                    }
                    return true;
                }

                @Override
                protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                    emoji_id = documentId;
                    updateIcon();
                    updateButton();
                    if (popup[0] != null) {
                        selectAnimatedEmojiDialog = null;
                        popup[0].dismiss();
                    }
                }
            };
            popupLayout.setSelected(emoji_id);
            popupLayout.setSaveState(1);
            popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    selectAnimatedEmojiDialog = null;
                }
            };
            popup[0].showAsDropDown(iconButton, dp(150), -dp(390), Gravity.BOTTOM);
            popup[0].dimBehind();
        }

        @Override
        protected void onSmoothContainerViewLayout(float ty) {
            super.onSmoothContainerViewLayout(ty);
            buttonContainer.setTranslationY(ty);
        }

        private TL_aicompose.TL_aiComposeTone editing;
        public CreateAiStyleAlert setEditing(TL_aicompose.TL_aiComposeTone tone) {
            editing = tone;

            emoji_id = editing.emoji_id;
            updateIcon();
            titleCell.setText(editing.title);
            promptCell.setText(editing.prompt);
            checkbox.setChecked(editing.author_id != 0, false);

            actionBar.setTitle(getString(R.string.AIEditorEditStyle));
            button.setText(getString(R.string.AIEditorStyleEdit));

            updateButton();
            adapter.update(false);

            return this;
        }

        private Utilities.Callback<TL_aicompose.AiComposeTone> onToneCreated;
        public CreateAiStyleAlert setOnToneCreated(Utilities.Callback<TL_aicompose.AiComposeTone> listener) {
            this.onToneCreated = listener;
            return this;
        }

        private Utilities.Callback<TL_aicompose.AiComposeTone> onToneEdited;
        public CreateAiStyleAlert setOnToneEdited(Utilities.Callback<TL_aicompose.AiComposeTone> listener) {
            this.onToneEdited = listener;
            return this;
        }

        private void updateIcon() {
            if (emoji_id == null) {
                icon.setImageResource(R.drawable.menu_smile_add);
                icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
            } else {
                icon.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, emoji_id));
                icon.setColorFilter(null);
                icon.setEmojiColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
        }

        private void updateButton() {
            button.setEnabled(
                emoji_id != null &&
                titleCell.getText().length() > 0 &&
                promptCell.getText().length() > 0
            );
        }

        private UniversalAdapter adapter;
        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
            adapter.setApplyBackground(false);
            return adapter;
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asCustomShadow(iconCell));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCustom(titleCell));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCustom(promptCell));
            items.add(UItem.asShadow(null));
            if (editing != null) {
                items.add(UItem.asButton(1, getString(R.string.AIEditorDeleteStyle)).red());
                items.add(UItem.asShadow(null));
            }
            items.add(UItem.asCustomShadow(checkboxCell));
        }

        @Override
        protected CharSequence getTitle() {
            return editing != null ? getString(R.string.AIEditorEditStyle) : getString(R.string.AIEditorNewStyle);
        }
    }

    public static class AiStyleAlert extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        public final TL_aicompose.AiComposeTone tone;
        private final AiTonesController tonesController;

        private final ImageView closeView;

        private final FrameLayout iconCell;
        private final FrameLayout iconButton;
        private final BackupImageView icon;

        private final TextView title;
        private final TextView subtitle;

        private final FrameLayout buttonContainer;
        private final FrameLayout bulletinContainer;
        private final ButtonWithCounterView button;

        public AiStyleAlert(Context context, TL_aicompose.AiComposeTone tone, Theme.ResourcesProvider resourcesProvider) {
            super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);

            tonesController = MessagesController.getInstance(currentAccount).getTonesController();
            tonesController.load();

            this.tone = tone;
            examples = new TL_aicompose.aiComposeToneExample[MessagesController.getInstance(currentAccount).config.aicomposeToneExamplesNum.get()];
            if (tone instanceof TL_aicompose.TL_aiComposeTone) {
                examples[0] = ((TL_aicompose.TL_aiComposeTone) tone).example_english;
            }

            closeView = new ImageView(context);
            closeView.setScaleType(ImageView.ScaleType.CENTER);
            closeView.setImageResource(R.drawable.ic_close_white);
            closeView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            closeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .10f)));
            containerView.addView(closeView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.RIGHT, 0, 0, 8, 0));
            ScaleStateListAnimator.apply(closeView, .1f, 1.5f);
            closeView.setOnClickListener(v -> this.dismiss());

            iconCell = new FrameLayout(context);
            iconCell.setClipToPadding(false);
            iconCell.setClipChildren(false);
            iconButton = new FrameLayout(context);
            iconButton.setBackground(Theme.createCircleDrawable(dp(100), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            iconCell.addView(iconButton, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 0, 0, 0));
            icon = new BackupImageView(context);
            icon.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, tone.emoji_id));
            iconButton.addView(icon, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

            title = new TextView(context);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            title.setText(tone.title);

            subtitle = new TextView(context);
            subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setText(getString(R.string.AIEditorStyleText));

            actionBar.setTitle(tone.title);

            behindKeyboardColorKey = Theme.key_windowBackgroundGray;
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(6 + 48 + 12));
            recyclerListView.setClipToPadding(false);
            recyclerListView.setSections();
            recyclerListView.setOnItemClickListener((view, position) -> {
                final UItem item = adapter.getItem(position - 1);
                if (item == null) return;
            });

            ignoreTouchActionBar = false;
            headerMoveTop = dp(36);
            topPadding = 0.35f;

            takeTranslationIntoAccount = true;
            final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                }
            };
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            recyclerListView.setItemAnimator(itemAnimator);

            buttonContainer = new FrameLayout(context);
            buttonContainer.setPadding(dp(16), dp(6), dp(16), dp(12));
            buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
                    getThemedColor(Theme.key_windowBackgroundGray),
                    getThemedColor(Theme.key_windowBackgroundGray)
            }));

            FrameLayout.LayoutParams buttonContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
            buttonContainerLayoutParams.leftMargin += backgroundPaddingLeft;
            buttonContainerLayoutParams.rightMargin += backgroundPaddingLeft;
            containerView.addView(buttonContainer, buttonContainerLayoutParams);

            bulletinContainer = new FrameLayout(context);
            FrameLayout.LayoutParams bulletinContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 48 + 12);
            bulletinContainerLayoutParams.leftMargin += backgroundPaddingLeft;
            bulletinContainerLayoutParams.rightMargin += backgroundPaddingLeft;
            containerView.addView(bulletinContainer, bulletinContainerLayoutParams);

            button = new ButtonWithCounterView(context, resourcesProvider).setRound();
            button.setText(getString(isAlreadyAdded() ? R.string.AIEditorStyleDone : R.string.AIEditorAddStyle));
            button.setOnClickListener(v -> {
                if (!button.isEnabled() || button.isLoading()) return;
                if (isAlreadyAdded()) {
                    dismiss();
                    return;
                }

                button.setLoading(true);
                final TL_aicompose.saveTone req = new TL_aicompose.saveTone();
                req.tone = TL_aicompose.InputAiComposeTone.from(tone);
                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (bool, err) -> {
                    button.setLoading(false);

                    if (err != null) {
                        if ("TONES_SAVED_TOO_MANY".equalsIgnoreCase(err.text)) {
                            showStylesLimitToast(BulletinFactory.of(bulletinContainer, resourcesProvider), currentAccount);
                            return;
                        }
                        BulletinFactory.of(bulletinContainer, resourcesProvider)
                            .showForError(err);
                    } else {
                        MessagesController.getInstance(currentAccount).getTonesController().add(tone);
                        dismiss();

                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            BulletinFactory.of(lastFragment)
                                .createEmojiBulletin(
                                    tone.emoji_id,
                                    getString(R.string.AIEditorToneAddedTitle),
                                    formatString(R.string.AIEditorToneAddedText, tone.title)
                                )
                                .show();
                        }
                    }
                });
            });
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL));

            adapter.update(false);
        }

        private boolean isAlreadyAdded() {
            final TL_aicompose.TL_aiComposeTone t;
            if (tone instanceof TL_aicompose.TL_aiComposeTone) {
                t = (TL_aicompose.TL_aiComposeTone) tone;
            } else return false;
            for (int i = 0; i < tonesController.tones.size(); ++i) {
                final TL_aicompose.AiComposeTone tone = tonesController.tones.get(i);
                if (tone instanceof TL_aicompose.TL_aiComposeTone && ((TL_aicompose.TL_aiComposeTone) tone).id == t.id) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void show() {
            super.show();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.loadedAiComposeTones);
        }

        @Override
        public void dismiss() {
            super.dismiss();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.loadedAiComposeTones);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.loadedAiComposeTones) {
                button.setText(getString(isAlreadyAdded() ? R.string.AIEditorStyleDone : R.string.AIEditorAddStyle));
            }
        }

        @Override
        protected void onActionBarAlpha(float alpha) {
            final SimpleTextView titleView = actionBar.getTitleTextView();
            if (titleView != null) {
                titleView.setAlpha(alpha);
            }
            closeView.setTranslationY(actionBar.getTranslationY() + AndroidUtilities.statusBarHeight + ((actionBar.getHeight() - AndroidUtilities.statusBarHeight) - closeView.getHeight()) / 2f + dp(28) * (1.0f - alpha));
        }

        private UniversalAdapter adapter;
        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
            adapter.setApplyBackground(false);
            return adapter;
        }

        private CharSequence loadingText() {
            return loadingText(5);
        }
        private CharSequence loadingText(int linesCount) {
            final SpannableStringBuilder loadingText = new SpannableStringBuilder();
            for (int i = 0; i < linesCount; ++i) {
                if (i > 0) loadingText.append("\n");
                int w = dp((int) (Math.random() * 50));
                int from = loadingText.length();
                loadingText.append(getString(R.string.Loading));
                loadingText.setSpan(new LoadingSpan(null, w, 0).setHeight(dp(6)).setAlpha(0.5f).setFullWidth(true), from, loadingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return loadingText;
        }

        private int exampleIndex = 0;
        private TL_aicompose.aiComposeToneExample[] examples;

        private void onAnotherExample(View button) {
            if (!(tone instanceof TL_aicompose.TL_aiComposeTone)) return;

            exampleIndex++;
            if (exampleIndex >= examples.length) {
                exampleIndex = 0;
            }

            if (examples[exampleIndex] == null) {
                final int index = exampleIndex;
                final TL_aicompose.getToneExample req = new TL_aicompose.getToneExample();
                req.tone = TL_aicompose.InputAiComposeTone.from(tone);
                req.num = index;
                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (example, err) -> {
                    if (example != null) {
                        examples[index] = example;
                        adapter.update(true);
                    }
                });
            }

            adapter.update(true);
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            adapter.itemsOffset = 1;
            items.add(UItem.asShadow(null));
            items.add(UItem.asCustomShadow(iconCell, true));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCustomShadow(title));
            items.add(UItem.asSpace(dp(1)));
            items.add(UItem.asCustomShadow(subtitle));
            items.add(UItem.asSpace(dp(24)));
            if (tone instanceof TL_aicompose.TL_aiComposeTone) {
                final TL_aicompose.TL_aiComposeTone t = (TL_aicompose.TL_aiComposeTone) tone;
                final TL_aicompose.aiComposeToneExample example = examples[exampleIndex];

                adapter.whiteSectionStart();
                items.add(TranslateAlert3.Header.Factory.of(3, getString(R.string.AIEditorBefore), null, null, null, false, null, this::onAnotherExample));
                items.add(TranslateAlert3.Text.Factory.of(4, example == null ? loadingText() : MessageObject.formatTextWithEntities(example.from), false, false, null, null, null));

                items.add(TranslateAlert3.Header.Factory.of(5, getString(R.string.AIEditorAfter), null, null, null));
                items.add(TranslateAlert3.Text.Factory.of(6, example == null ? loadingText() : MessageObject.formatTextWithEntities(example.to), false, false, null, null, null));

                adapter.whiteSectionEnd();

                final TLRPC.User creator = t.author_id != 0 ? MessagesController.getInstance(currentAccount).getUser(t.author_id) : null;
                String username = UserObject.getPublicUsername(creator);
                if (creator == null) {
                    if (t.installs_count > 0) {
                        items.add(UItem.asShadow(formatPluralString("AIEditorUsedBy", t.installs_count)));
                    }
                } else {
                    items.add(UItem.asShadow(AndroidUtilities.replaceSingleLink((t.installs_count > 0 ? formatPluralString("AIEditorUsedBy", t.installs_count) + " " : "") + (TextUtils.isEmpty(username) ? formatString(R.string.AIEditorCreatedBy, UserObject.getUserName(creator)) : formatString(R.string.AIEditorCreatedBy, "@" + username)), getThemedColor(Theme.key_chat_messageLinkIn), () -> {
                        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                        if (fragment == null) return;
                        fragment.presentFragment(ProfileActivity.of(t.author_id));

                        dismiss();
                    })));
                }
            }
            items.add(UItem.asSpace(dp(32)));
        }

        @Override
        protected CharSequence getTitle() {
            return tone == null ? "" : tone.title;
        }
    }

}
