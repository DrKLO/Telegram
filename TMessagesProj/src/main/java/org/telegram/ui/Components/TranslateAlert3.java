package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.TranslateAlert2.capitalFirst;
import static org.telegram.ui.Components.TranslateAlert2.languageName;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import java.util.ArrayList;

public class TranslateAlert3 extends BottomSheetWithRecyclerListView {

    private ImageView closeView;

    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;

    public TranslateAlert3(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);

        closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        closeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .10f)));
        actionBar.addView(closeView, LayoutHelper.createFrame(54, 54, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(closeView, .1f, 1.5f);
        closeView.setOnClickListener(v -> this.dismiss());

        to_lang = TranslateAlert2.getToLanguage();
        if (to_lang == null) {
            to_lang = TranslateController.currentLanguage();
        }

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
            Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
            getThemedColor(Theme.key_windowBackgroundGray),
            getThemedColor(Theme.key_windowBackgroundGray)
        }));
        button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.OK));
        final FrameLayout.LayoutParams buttonLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 12, 6, 12, 12);
        buttonLayoutParams.leftMargin += backgroundPaddingLeft;
        buttonLayoutParams.rightMargin += backgroundPaddingLeft;
        buttonContainer.addView(button, buttonLayoutParams);
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(6 + 48 + 12));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSections();
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item == null) return;
            if (item.id == 1) {
                if (translated != null && !translatedLoading) {
                    AndroidUtilities.addToClipboard(translated);
                }
            } else if (item.id == 2) {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                    if (fragment == null) return;
                    new PremiumFeatureBottomSheet(getContext(), PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS, true, resourcesProvider)
                        .show();
                    return;
                }
                MessagesController.getInstance(currentAccount).getTranslateController().toggleTranslatingDialog(dialogId);
                dismiss();
            }
        });

        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        adapter.update(false);
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

    private long dialogId;
    private int messageId;

    private CharSequence text;
    private boolean translatedLoading;
    private CharSequence translated;

    private Utilities.Callback<CharSequence> onUseListener;

    private String from_lang;
    private String to_lang;
    private int tone = 1;
    private boolean summarized;
    private boolean noforwards;
    private Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress;

    private String[] tones = new String[] { "formal", "neutral", "casual" };
    private String[] tonesText = new String[] { "Formal", "Neutral", "Casual" };

    public TranslateAlert3 setText(String from_lang, CharSequence text) {
        this.text = text;
        this.from_lang = from_lang;
        return this;
    }
    public TranslateAlert3 setToLanguage(String to_lang) {
        this.to_lang = to_lang;
        return this;
    }
    public TranslateAlert3 setText(CharSequence text) {
        this.text = text;
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
    public TranslateAlert3 setMessage(long dialogId, int messageId) {
        this.dialogId = dialogId;
        this.messageId = messageId;
        return this;
    }
    public TranslateAlert3 setMessage(long dialogId, int messageId, boolean summarized) {
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.summarized = summarized;
        return this;
    }
    public TranslateAlert3 setOnUse(Utilities.Callback<CharSequence> onUse) {
        this.onUseListener = onUse;
        return this;
    }
    public TranslateAlert3 setNoforwards(boolean noforwards) {
        this.noforwards = noforwards;
        return this;
    }
    public TranslateAlert3 setOnLinkPress(Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress) {
        this.onLinkPress = onLinkPress;
        return this;
    }

    @Override
    protected CharSequence getTitle() {
        return dialogId != 0 && messageId != 0 && summarized ? "Summarize & Translate" : "Translate";
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
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

        for (int i = 0; i < tones.length; ++i) {
            final int newTone = i;
            addChecked(o, list, tone == newTone, tonesText[i], () -> {
                cancelRequest();
                tone = newTone;
                TranslateAlert2.setToLanguage(to_lang);
                requestTranslate();
            });
        }

        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        list.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

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
                    requestTranslate();
                });
            }
        }

        gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        list.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        for (final TranslateController.Language lng : allLanguages) {
            addChecked(o, list, TextUtils.equals(lng.code, to_lang), lng.displayName, () -> {
                cancelRequest();
                to_lang = lng.code;
                TranslateAlert2.setToLanguage(to_lang);
                requestTranslate();
            });
        }

        o.show();
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

    private void onLinkPressed(ClickableSpan span) {
        if (span == null) return;
        if (onLinkPress != null && span instanceof URLSpan && onLinkPress.run((URLSpan) span)) {
            return;
        }
        span.onClick(containerView);
    }

    private boolean collapsed = true;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));

        adapter.itemsOffset = 1;
        adapter.whiteSectionStart();
        items.add(Header.Factory.of(3, "", from_lang != null ? capitalFirst(languageName(from_lang)) : getString(R.string.AIEditorOriginalText), null, null));
        items.add(Text.Factory.of(4, text, collapsed, noforwards, view -> {
            collapsed = false;
            saveScrollPosition();
            adapter.update(true);
            applyScrolledPosition(true);
        }, this::onLinkPressed, null));
        items.add(Header.Factory.of(5, "", capitalFirst(languageName(to_lang) + (tone != 1 && tonesText != null ? " (" + tonesText[tone] + ")" : "")), null, this::onToLangMenu));
        items.add(Text.Factory.of(6, translated, false, noforwards, null, this::onLinkPressed, null));
        adapter.whiteSectionEnd();

        items.add(UItem.asShadow(null));

        adapter.whiteSectionStart();
        if (!noforwards) {
            items.add(UItem.asButton(1, R.drawable.msg_copy, getString(R.string.TranslateCopy)));
        }
        if (dialogId != 0 && !MessagesController.getInstance(currentAccount).getTranslateController().isTranslatingDialog(dialogId)) {
            items.add(UItem.asButton(2, R.drawable.msg_translate, getString(R.string.TranslateEntireChat)));
        }
        adapter.whiteSectionEnd();
    }

    @Override
    public void show() {
        super.show();
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }

        adapter.update(false);
        requestTranslate();

        if (onUseListener != null) {
            button.setText("Use This Translation");
            button.setOnClickListener(v -> {
                if (translated != null) onUseListener.run(translated);
                dismiss();
            });
        }
    }

    private int requestId = -1;
    private void requestTranslate() {
        final TLRPC.TL_textWithEntities fromText = new TLRPC.TL_textWithEntities();
        final CharSequence[] message = new CharSequence[] { text };
        fromText.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
        fromText.text = message[0] == null ? "" : message[0].toString();

        if (onUseListener != null) button.setLoading(true);

        final SpannableStringBuilder loadingText = new SpannableStringBuilder();
        loadingText.append(getString(R.string.Loading));
        loadingText.setSpan(new LoadingSpan(null, dp(120), 0), 0, loadingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        translated = loadingText;
        translatedLoading = true;

        if (summarized && dialogId != 0 && messageId != 0) {
            final TLRPC.TL_messages_summarizeText req = new TLRPC.TL_messages_summarizeText();

            req.flags |= TLObject.FLAG_0;
            req.to_lang = to_lang;

            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.id = messageId;

            if (tone != 1) {
                req.flags |= TLObject.FLAG_2;
                req.tone = tones[tone];
            }

            requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                requestId = -1;

                button.setLoading(false);
                if (err != null) {
                    BulletinFactory.of(topBulletinContainer, resourcesProvider).showForError(err);

                    button.setText(getString(R.string.OK));
                    button.setOnClickListener(v -> dismiss());
                    return;
                }

                translated = MessageObject.formatTextWithEntities(res);
                translatedLoading = false;

                adapter.update(true);
            });
        } else {

            final TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
            req.to_lang = to_lang;

            if (dialogId != 0 && messageId != 0) {
                req.flags |= TLObject.FLAG_0;
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.id.add(messageId);
            } else {
                req.flags |= TLObject.FLAG_1;
                req.text.add(fromText);
            }

            if (tone != 1) {
                req.flags |= TLObject.FLAG_2;
                req.tone = tones[tone];
            }

            requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                requestId = -1;

                button.setLoading(false);
                if (err != null) {
                    BulletinFactory.of(topBulletinContainer, resourcesProvider).showForError(err);

                    button.setText(getString(R.string.OK));
                    button.setOnClickListener(v -> dismiss());
                    return;
                }
                if (res == null || res.result.isEmpty()) {
                    button.setText(getString(R.string.OK));
                    button.setOnClickListener(v -> dismiss());
                    return;
                }
                translated = MessageObject.formatTextWithEntities(res.result.get(0));
                translatedLoading = false;

                adapter.update(true);
            });
        }

        adapter.update(true);
    }
    private void cancelRequest() {
        if (requestId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
            requestId = -1;
        }
    }

    public static class Header extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        public final LinearLayout layout1;
        public final LinearLayout layout2;
        public final TextView text1View;
        public final TextView text2View;
        public final TextView text3View;
        public final ImageView imageView;

        public final LinearLayout emojifyContainer;
        public final CheckBox2 emojifyCheckbox;
        public final TextView emojifyTextView;

        public Header(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setClipToPadding(false);
            setPadding(dp(20), dp(12), dp(20), dp(6));

            layout1 = new LinearLayout(context);
            layout1.setOrientation(LinearLayout.HORIZONTAL);
            addView(layout1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

            text1View = new TextView(context);
            text1View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text1View.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD));
            layout1.addView(text1View, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 0, 0));

            layout2 = new LinearLayout(context);
            layout2.setOrientation(LinearLayout.HORIZONTAL);
            layout2.setPadding(dp(6), dp(1), dp(2), dp(1));
            ScaleStateListAnimator.apply(layout2);
            layout1.addView(layout2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, -6, 0, 0, 0));

            text2View = new TextView(context);
            text2View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text2View.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD));
            layout2.addView(text2View, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 0, 0));

            imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.arrows_select);
            layout2.addView(imageView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL | Gravity.LEFT, 1, 0, 0, 0));
            imageView.setTranslationY(dp(1));

            text3View = new TextView(context);
            text3View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text3View.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD));
            layout1.addView(text3View, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, -6, 0, 0, 0));

            emojifyContainer = new LinearLayout(context);
            emojifyContainer.setPadding(dp(4), dp(3), dp(4), dp(3));
            emojifyContainer.setClipToPadding(false);
            emojifyContainer.setOrientation(LinearLayout.HORIZONTAL);
            emojifyCheckbox = new CheckBox2(context, 20, resourcesProvider);
            emojifyCheckbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            emojifyCheckbox.setDrawUnchecked(true);
            emojifyCheckbox.setChecked(false, false);
            emojifyCheckbox.setDrawBackgroundAsArc(10);
            emojifyContainer.addView(emojifyCheckbox, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            emojifyTextView = new TextView(context);
            emojifyTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            emojifyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            emojifyTextView.setTypeface(AndroidUtilities.bold());
            emojifyTextView.setText(LocaleController.getString(R.string.AIEditorEmojify));
            emojifyContainer.addView(emojifyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 3, -1, 2, 0));
            addView(emojifyContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, -3, -6, -3));
            ScaleStateListAnimator.apply(emojifyContainer, 0.025f, 1.5f);

            updateColors();
        }

        @Override
        public void updateColors() {
            text1View.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            text2View.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            text3View.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            layout2.setBackground(layout2.isClickable() ? Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), .10f), dp(10), dp(10)) : null);
            if (layout2.isClickable()) {
                ScaleStateListAnimator.apply(layout2);
            } else {
                ScaleStateListAnimator.reset(layout2);
            }
            emojifyContainer.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 24, 24));
        }

        public void set(CharSequence text1, CharSequence text2, CharSequence text3, View.OnClickListener onText2Click, boolean emojify, View.OnClickListener onEmojifyClick) {
            text1View.setText(text1);
            text2View.setText(text2);
            text3View.setText(text3);
            imageView.setVisibility(onText2Click != null ? View.VISIBLE : View.GONE);
            layout2.setOnClickListener(onText2Click);
            layout2.setClickable(onText2Click != null);
            emojifyCheckbox.setChecked(emojify, false);
            emojifyContainer.setVisibility(onEmojifyClick != null ? View.VISIBLE : View.GONE);
            emojifyContainer.setOnClickListener(onEmojifyClick);
            updateColors();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        public static class Factory extends UItem.UItemFactory<Header> {
            static { setup(new Factory()); }

            @Override
            public Header createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new Header(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((Header) view).set(item.text, item.subtext, item.textValue, item.clickCallback, item.checked, item.clickCallback2);
            }

            public static UItem of(
                int id,
                CharSequence text1,
                CharSequence text2,
                CharSequence text3,
                View.OnClickListener onClick
            ) {
                return of(id, text1, text2, text3, onClick, false, null);
            }

            public static UItem of(
                int id,
                CharSequence text1,
                CharSequence text2,
                CharSequence text3,
                View.OnClickListener onClick,
                boolean emojify,
                View.OnClickListener onEmojifyClick
            ) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.text = text1;
                item.subtext = text2;
                item.textValue = text3;
                item.clickCallback = onClick;
                item.checked = emojify;
                item.clickCallback2 = onEmojifyClick;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext) && TextUtils.equals(a.textValue, b.textValue) && a.clickCallback2 == b.clickCallback2;
            }

            @Override
            public boolean isClickable() {
                return false;
            }
        }
    }

    public static class Text extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        public boolean needDivider;
        public SpoilersTextView shortTextView;
        public TextView moreView;
        private FrameLayout.LayoutParams textViewLayoutParams;
        public SpoilersTextView textView;
        public boolean collapsed;
        public final ImageView copyButton;

        public Text(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setClipToPadding(false);
            setPadding(dp(20), 0, dp(20), dp(16));

            shortTextView = new SpoilersTextView(context) {
                private GradientClip clip = new GradientClip();
                @Override
                protected void onDraw(Canvas canvas) {
                    final int w = moreView.getWidth() + dp(8);
                    canvas.saveLayerAlpha(getScrollX(), 0, getScrollX() + getWidth() - w, getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

                    super.onDraw(canvas);

                    canvas.save();
                    canvas.translate(getPaddingLeft(), getPaddingTop());
                    SquigglyLinesSpan.drawOnText(canvas, getLayout());
                    canvas.restore();

                    AndroidUtilities.rectTmp.set(getWidth() - w - dp(24), 0, getWidth() - w, getHeight());
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);
                    canvas.restore();
                }
            };
            NotificationCenter.listenEmojiLoading(shortTextView);
            shortTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            shortTextView.setMaxLines(1);
            shortTextView.setSingleLine();
            shortTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(shortTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            moreView = new TextView(context);
            moreView.setText(getString(R.string.DescriptionMore));
            moreView.setPadding(dp(8), 0, dp(8), 0);
            moreView.setGravity(Gravity.CENTER);
            ScaleStateListAnimator.apply(moreView);
            addView(moreView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.RIGHT | Gravity.TOP, 0, 1, 0, 0));

            textView = new SpoilersTextView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);

                    canvas.save();
                    canvas.translate(getPaddingLeft(), getPaddingTop());
                    SquigglyLinesSpan.drawOnText(canvas, getLayout());
                    canvas.restore();
                }
            };
            NotificationCenter.listenEmojiLoading(textView);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextIsSelectable(true);
            addView(textView, textViewLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            copyButton = new ImageView(context);
            copyButton.setImageResource(R.drawable.msg_copy);
            copyButton.setScaleType(ImageView.ScaleType.CENTER);
            ScaleStateListAnimator.apply(copyButton);
            addView(copyButton, LayoutHelper.createFrame(38, 38, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, -20 + 4, -16 + 4));
            copyButton.setVisibility(View.GONE);

            updateColors();
        }

        @Override
        public void updateColors() {
            shortTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            moreView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            moreView.setBackground(Theme.createRoundRectDrawable(dp(9), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), .10f)));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            textView.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
            setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
            copyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
            copyButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 0.10f)));
        }

        public void setHandlesColor(int color) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || XiaomiUtilities.isMIUI()) {
                return;
            }
            try {
                Drawable left = textView.getTextSelectHandleLeft();
                left.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                textView.setTextSelectHandleLeft(left);

                Drawable middle = textView.getTextSelectHandle();
                middle.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                textView.setTextSelectHandle(middle);

                Drawable right = textView.getTextSelectHandleRight();
                right.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                textView.setTextSelectHandleRight(right);
            } catch (Exception ignore) {}
        }

        public void set(CharSequence _text, boolean collapsed, View.OnClickListener click, LinkSpanDrawable.LinksTextView.OnLinkPress onLinkPress, boolean noforwards, View.OnClickListener onCopyClick, boolean divider) {
            final SpannableStringBuilder text = new SpannableStringBuilder(_text == null ? "" : AnimatedEmojiSpan.cloneSpans(_text));
            final LoadingSpan[] spans = text.getSpans(0, text.length(), LoadingSpan.class);
            if (spans != null) {
                for (int i = 0; i < spans.length; ++i) {
                    final int start = text.getSpanStart(spans[i]);
                    final int end = text.getSpanEnd(spans[i]);
                    text.removeSpan(spans[i]);
                    text.setSpan(
                        new LoadingSpan(textView, spans[i].size, spans[i].yOffset)
                            .setHeight(spans[i].height)
                            .setAlpha(spans[i].alpha)
                            .setFullWidth(spans[i].fullWidth),
                        start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }

            if (this.collapsed && !collapsed) {
                shortTextView.setVisibility(View.VISIBLE);
                textView.setVisibility(View.VISIBLE);
                shortTextView.animate().alpha(0.0f).withEndAction(() -> {
                    shortTextView.setVisibility(View.GONE);
                }).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
                textView.animate().alpha(1.0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
            } else {
                shortTextView.setVisibility(collapsed ? View.VISIBLE : View.GONE);
                textView.setVisibility(!collapsed ? View.VISIBLE : View.GONE);
            }
            this.collapsed = collapsed;
            moreView.setVisibility(collapsed ? View.VISIBLE : View.GONE);
            moreView.setOnClickListener(click);
            setClipChildren(collapsed);

            shortTextView.setText(text);
            textView.setText(text);
            textView.setTextIsSelectable(!noforwards && (spans == null || spans.length == 0));
            textView.setOnLinkPressListener(onLinkPress);

            copyButton.setVisibility(onCopyClick != null ? View.VISIBLE : View.GONE);
            copyButton.setOnClickListener(onCopyClick);

            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            if (needDivider) {
                TextView textView = collapsed ? this.shortTextView : this.textView;
                if (LocaleController.isRTL) {
                    canvas.drawRect(0, getMeasuredHeight() - 1, textView.getRight(), getMeasuredHeight(), Theme.dividerPaint);
                } else {
                    canvas.drawRect(textView.getLeft(), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), Theme.dividerPaint);
                }
            }
        }

        private int clipHeight = -1;
        private final AnimatedFloat animatedClipHeight = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), animatedClipHeight.set(clipHeight));
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        private boolean needsBottomMargin() {
            if (copyButton.getVisibility() != View.VISIBLE) return false;
            final Layout layout = textView.getLayout();
            if (layout.getLineCount() <= 0) return false;
            return layout.getLineRight(layout.getLineCount() - 1) > layout.getWidth() - dp(42);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            clipHeight = getMeasuredHeight();
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            textViewLayoutParams.bottomMargin = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (needsBottomMargin()) {
                textViewLayoutParams.bottomMargin = dp(26);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            if (getMeasuredHeight() > clipHeight && !collapsed) {
                clipHeight = getMeasuredHeight();
                invalidate();
            } else {
                animatedClipHeight.force(clipHeight = getMeasuredHeight());
            }
        }

        public static class Factory extends UItem.UItemFactory<Text> {
            static { setup(new Factory()); }

            @Override
            public Text createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new Text(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((Text) view).set(item.text, item.collapsed, item.clickCallback, item.object != null ? (LinkSpanDrawable.LinksTextView.OnLinkPress) item.object : null, item.locked, item.clickCallback2, divider);
            }

            public static UItem of(int id, CharSequence text, boolean collapsed, boolean noforwards, View.OnClickListener uncollapse, LinkSpanDrawable.LinksTextView.OnLinkPress onLinkPress, View.OnClickListener onCopyClicked) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.text = text;
                item.collapsed = collapsed;
                item.locked = noforwards;
                item.clickCallback = uncollapse;
                item.object = onLinkPress;
                item.clickCallback2 = onCopyClicked;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return TextUtils.equals(a.text, b.text) && a.collapsed == b.collapsed;
            }

            @Override
            public boolean isClickable() {
                return false;
            }
        }

    }
}
