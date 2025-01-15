package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContentPreviewViewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class SuggestEmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static int DIRECTION_TO_BOTTOM = 0;
    public final static int DIRECTION_TO_TOP = 1;

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private AnchorViewDelegate enterView;

    @Nullable
    private FrameLayout containerView;
    @Nullable
    private RecyclerListView listView;
    @Nullable
    private Adapter adapter;
    private int direction = DIRECTION_TO_BOTTOM;
    private int horizontalPadding = AndroidUtilities.dp(10);

    public interface AnchorViewDelegate {
        BaseFragment getParentFragment();
        void setFieldText(CharSequence text);
        void addTextChangedListener(TextWatcher watcher);
        int getVisibility();
        EditTextBoldCursor getEditField();
        CharSequence getFieldText();
        Editable getEditText();
    }

    private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate;
    private ContentPreviewViewer.ContentPreviewViewerDelegate getPreviewDelegate() {
        if (previewDelegate == null) {
            previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
                @Override
                public boolean can() {
                    return true;
                }

                @Override
                public boolean needSend(int contentType) {
                    if (enterView == null) {
                        return false;
                    }
                    BaseFragment fragment = enterView.getParentFragment();
                    if (fragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) fragment;
                        return chatActivity.canSendMessage() && (UserConfig.getInstance(UserConfig.selectedAccount).isPremium() || chatActivity.getCurrentUser() != null && UserObject.isUserSelf(chatActivity.getCurrentUser()));
                    }
                    return false;
                }

                @Override
                public void sendEmoji(TLRPC.Document emoji) {
                    if (enterView == null) {
                        return;
                    }
                    BaseFragment fragment = enterView.getParentFragment();
                    if (fragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) fragment;
                        chatActivity.sendAnimatedEmoji(emoji, true, 0);
                        enterView.setFieldText("");
                    }
                }

                @Override
                public boolean needCopy(TLRPC.Document document) {
                    if (isCopyForbidden) {
                        return false;
                    }
                    return UserConfig.getInstance(UserConfig.selectedAccount).isPremium();
                }

                @Override
                public void copyEmoji(TLRPC.Document document) {
                    Spannable spannable = SpannableStringBuilder.valueOf(MessageObject.findAnimatedEmojiEmoticon(document));
                    spannable.setSpan(new AnimatedEmojiSpan(document, null), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (AndroidUtilities.addToClipboard(spannable) && enterView != null) {
                        BulletinFactory.of(enterView.getParentFragment()).createCopyBulletin(LocaleController.getString(R.string.EmojiCopied)).show();
                    }
                }

                @Override
                public Boolean canSetAsStatus(TLRPC.Document document) {
                    if (isSetAsStatusForbidden) {
                        return null;
                    }
                    if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                        return null;
                    }
                    TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                    if (user == null) {
                        return null;
                    }
                    Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
                    return document != null && (emojiStatusId == null || emojiStatusId != document.id);
                }

                @Override
                public void setAsEmojiStatus(TLRPC.Document document, Integer until) {
                    TLRPC.EmojiStatus status;
                    if (document == null) {
                        status = new TLRPC.TL_emojiStatusEmpty();
                    } else if (until != null) {
                        status = new TLRPC.TL_emojiStatusUntil();
                        ((TLRPC.TL_emojiStatusUntil) status).document_id = document.id;
                        ((TLRPC.TL_emojiStatusUntil) status).until = until;
                    } else {
                        status = new TLRPC.TL_emojiStatus();
                        ((TLRPC.TL_emojiStatus) status).document_id = document.id;
                    }
                    TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                    final TLRPC.EmojiStatus previousEmojiStatus = user == null ? new TLRPC.TL_emojiStatusEmpty() : user.emoji_status;
                    MessagesController.getInstance(currentAccount).updateEmojiStatus(status);

                    Runnable undoAction = () -> MessagesController.getInstance(currentAccount).updateEmojiStatus(previousEmojiStatus);
                    BaseFragment fragment = enterView == null ? null : enterView.getParentFragment();
                    if (fragment != null) {
                        if (document == null) {
                            final Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(getContext(), resourcesProvider);
                            layout.textView.setText(LocaleController.getString(R.string.RemoveStatusInfo));
                            layout.imageView.setImageResource(R.drawable.msg_settings_premium);
                            Bulletin.UndoButton undoButton = new Bulletin.UndoButton(getContext(), true, resourcesProvider);
                            undoButton.setUndoAction(undoAction);
                            layout.setButton(undoButton);
                            Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT).show();
                        } else {
                            BulletinFactory.of(fragment).createEmojiBulletin(document, LocaleController.getString(R.string.SetAsEmojiStatusInfo), LocaleController.getString(R.string.Undo), undoAction).show();
                        }
                    }
                }

                @Override
                public boolean canSchedule() {
                    return false;
                }

                @Override
                public boolean isInScheduleMode() {
                    if (enterView == null) {
                        return false;
                    }
                    BaseFragment fragment = enterView.getParentFragment();
                    if (fragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) fragment;
                        return chatActivity.isInScheduleMode();
                    } else {
                        return false;
                    }
                }

                @Override
                public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {}

                @Override
                public long getDialogId() {
                    return 0;
                }
            };
        }
        return previewDelegate;
    }

    private boolean show, forceClose;
    @Nullable
    private ArrayList<MediaDataController.KeywordResult> keywordResults;
    private boolean clear;
    private boolean isCopyForbidden;
    private boolean isSetAsStatusForbidden;

    public SuggestEmojiView(Context context, int currentAccount, AnchorViewDelegate enterView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.enterView = enterView;
        this.resourcesProvider = resourcesProvider;

        postDelayed(() -> MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS), 260);
    }

    public void forbidCopy() {
        isCopyForbidden = true;
    }

    public void forbidSetAsStatus() {
        isSetAsStatusForbidden = true;
    }

    private void createListView() {
        if (listView != null) {
            return;
        }

        path = new Path();
        circlePath = new Path();

        containerView = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                SuggestEmojiView.this.drawContainerBegin(canvas);
                super.dispatchDraw(canvas);
                SuggestEmojiView.this.drawContainerEnd(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                this.setPadding(horizontalPadding, direction == DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(8) : AndroidUtilities.dp(6.66f), horizontalPadding, direction == DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(6.66f) : AndroidUtilities.dp(8));
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void setVisibility(int visibility) {
                boolean same = getVisibility() == visibility;
                super.setVisibility(visibility);
                if (!same) {
                    boolean visible = visibility == View.VISIBLE;
                    if (listView != null) {
                        for (int i = 0; i < listView.getChildCount(); ++i) {
                            if (visible) {
                                ((EmojiImageView) listView.getChildAt(i)).attach();
                            } else {
                                ((EmojiImageView) listView.getChildAt(i)).detach();
                            }
                        }
                    }
                }
            }
        };

        showFloat1 = new AnimatedFloat(containerView, 120, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        showFloat2 = new AnimatedFloat(containerView, 150, 600, CubicBezierInterpolator.EASE_OUT_QUINT);
        overshootInterpolator = new OvershootInterpolator(.4f);
        leftGradientAlpha = new AnimatedFloat(containerView, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        rightGradientAlpha = new AnimatedFloat(containerView, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        arrowXAnimated = new AnimatedFloat(containerView, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        listViewCenterAnimated = new AnimatedFloat(containerView, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        listViewWidthAnimated = new AnimatedFloat(containerView, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        listView = new RecyclerListView(getContext()) {
            private boolean left, right;
            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
                boolean left = canScrollHorizontally(-1);
                boolean right = canScrollHorizontally(1);
                if (this.left != left || this.right != right) {
                    if (containerView != null) {
                        containerView.invalidate();
                    }
                    this.left = left;
                    this.right = right;
                }
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, getPreviewDelegate(), resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }
        };
        listView.setAdapter(adapter = new Adapter(this));
        LinearLayoutManager layout = new LinearLayoutManager(getContext());
        layout.setOrientation(RecyclerView.HORIZONTAL);
        listView.setLayoutManager(layout);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(45);
        itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        listView.setItemAnimator(itemAnimator);
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        RecyclerListView.OnItemClickListener onItemClickListener;
        listView.setOnItemClickListener(onItemClickListener = (view, position) -> {
            onClick(((EmojiImageView) view).emoji);
        });
        listView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, listView, 0, onItemClickListener, getPreviewDelegate(), resourcesProvider));

        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44 + 8));
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8 + 6.66f, Gravity.BOTTOM));
        if (enterView != null) {
            enterView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (enterView != null && enterView.getVisibility() == View.VISIBLE) {
                        fireUpdate();
                    }
                }
            });
        }
    }

    public void setDelegate(AnchorViewDelegate delegate) {
        this.enterView = delegate;
    }

    public void setHorizontalPadding(int padding) {
        this.horizontalPadding = padding;
    }

    public AnchorViewDelegate getDelegate() {
        return enterView;
    }

    public void onTextSelectionChanged(int start, int end) {
        fireUpdate();
    }

    public boolean isShown() {
        return show;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        if (this.direction != direction) {
            this.direction = direction;
            requestLayout();
        }
    }

    public void updateColors() {
        if (backgroundPaint != null) {
            backgroundPaint.setColor(Theme.getColor(Theme.key_chat_stickersHintPanel, resourcesProvider));
        }
        Theme.chat_gradientLeftDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_stickersHintPanel, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        Theme.chat_gradientRightDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_stickersHintPanel, resourcesProvider), PorterDuff.Mode.MULTIPLY));
    }

    public void forceClose() {
        if (updateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnable = null;
        }
        show = false;
        forceClose = true;
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    private Runnable updateRunnable;
    public void fireUpdate() {
        if (updateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
        }
        AndroidUtilities.runOnUIThread(updateRunnable = this::update, 16);
    }

    private void update() {
        updateRunnable = null;
        if (enterView == null || enterView.getEditField() == null || enterView.getFieldText() == null) {
            show = false;
            forceClose = true;
            if (containerView != null) {
                containerView.invalidate();
            }
            return;
        }
        int selectionStart = enterView.getEditField().getSelectionStart();
        int selectionEnd = enterView.getEditField().getSelectionEnd();
        if (selectionStart != selectionEnd) {
            show = false;
            if (containerView != null) {
                containerView.invalidate();
            }
            return;
        }
        CharSequence text = enterView.getFieldText();
        Emoji.EmojiSpan[] emojiSpans = (text instanceof Spanned) ? ((Spanned) text).getSpans(Math.max(0, selectionEnd - 24), selectionEnd, Emoji.EmojiSpan.class) : null;
        if (emojiSpans != null && emojiSpans.length > 0 && SharedConfig.suggestAnimatedEmoji && UserConfig.getInstance(currentAccount).isPremium()) {
            Emoji.EmojiSpan lastEmoji = emojiSpans[emojiSpans.length - 1];
            if (lastEmoji != null) {
                int emojiStart = ((Spanned) text).getSpanStart(lastEmoji);
                int emojiEnd   = ((Spanned) text).getSpanEnd(lastEmoji);
                if (selectionStart == emojiEnd) {
                    String emoji = text.toString().substring(emojiStart, emojiEnd);
                    show = true;
                    createListView();
//                    containerView.setVisibility(View.VISIBLE);
                    arrowToSpan = lastEmoji;
                    arrowToStart = arrowToEnd = null;
                    searchAnimated(emoji);
                    if (containerView != null) {
                        containerView.invalidate();
                    }
                    return;
                }
            }
        } else {
            AnimatedEmojiSpan[] aspans = (text instanceof Spanned) ? ((Spanned) text).getSpans(Math.max(0, selectionEnd), selectionEnd, AnimatedEmojiSpan.class) : null;
            if ((aspans == null || aspans.length == 0) && selectionEnd < 52) {
                show = true;
                createListView();
//                containerView.setVisibility(View.VISIBLE);
                arrowToSpan = null;
                searchKeywords(text.toString().substring(0, selectionEnd));
                if (containerView != null) {
                    containerView.invalidate();
                }
                return;
            }
        }
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
        show = false;
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    private int lastQueryType;
    private String lastQuery;
    private int lastQueryId;
    private String[] lastLang;
    private Runnable searchRunnable;
    private long lastLangChangedTime = 0;

    /**
     * The user needs time to change the locale. We estimate this time to be at least 360 ms.
     */
    private String[] detectKeyboardLangThrottleFirstWithDelay() {
        long currentTime = System.currentTimeMillis();
        int delay = 360;
        if (lastLang == null || Math.abs(currentTime - lastLangChangedTime) > delay) {
            lastLangChangedTime = currentTime;
            return AndroidUtilities.getCurrentKeyboardLanguage();
        } else {
            lastLangChangedTime = currentTime;
        }
        return lastLang;
    }

    private MediaDataController.SearchStickersKey loadingKey;
    private void searchKeywords(String query) {
        if (query == null) {
            return;
        }
        if (lastQuery != null && lastQueryType == 1 && lastQuery.equals(query) && !clear && keywordResults != null && !keywordResults.isEmpty()) {
            forceClose = false;
            createListView();
            containerView.setVisibility(View.VISIBLE);
            lastSpanY = AndroidUtilities.dp(10);
            containerView.invalidate();
            return;
        }
        final int id = ++lastQueryId;
        if (loadingKey != null) {
            MediaDataController.getInstance(currentAccount).cancelSearchStickers(loadingKey);
            loadingKey = null;
        }

        String[] lang = detectKeyboardLangThrottleFirstWithDelay();
        if (lastLang == null || !Arrays.equals(lang, lastLang)) {
            MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(lang);
        }
        lastLang = lang;

        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
        searchRunnable = () -> {
            final HashSet<String> addedToResult = new HashSet<>();
            final ArrayList<MediaDataController.KeywordResult> result = new ArrayList<>();
//            Runnable localSearch = () -> {
                MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lang, query, true, (param, alias) -> {
                    if (id != lastQueryId) return;
                    lastQueryType = 1;
                    lastQuery = query;
                    if (param != null) {
                        for (MediaDataController.KeywordResult r : param) {
                            if (!addedToResult.contains(r.emoji)) {
                                addedToResult.add(r.emoji);
                                result.add(r);
                            }
                        }
                    }
                    if (!result.isEmpty()) {
                        clear = false;
                        forceClose = false;
                        createListView();
                        if (containerView != null) {
                            containerView.setVisibility(View.VISIBLE);
                        }
                        lastSpanY = AndroidUtilities.dp(10);
                        keywordResults = param;
                        arrowToStart = 0;
                        arrowToEnd = query.length();
                        if (containerView != null) {
                            containerView.invalidate();
                        }
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else {
                        keywordResults = null;
                        clear = true;
                        forceClose();
                    }
                }, SharedConfig.suggestAnimatedEmoji && UserConfig.getInstance(currentAccount).isPremium());
//            };
//            Runnable serverSearch = () -> {
//                if (ConnectionsManager.getInstance(currentAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
//                    localSearch.run();
//                    return;
//                }
//                loadingKey = MediaDataController.getInstance(currentAccount).searchStickers(true, query, lang == null ? "" : lang[0], emojis -> {
//                    if (id != lastQueryId) return;
//                    AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).putDocuments(emojis);
//                    for (TLRPC.Document doc : emojis) {
//                        final String emoji = "animated_" + doc.id;
//                        if (!addedToResult.contains(emoji)) {
//                            MediaDataController.KeywordResult keywordResult = new MediaDataController.KeywordResult();
//                            keywordResult.emoji = emoji;
//                            addedToResult.add(emoji);
//                            result.add(keywordResult);
//                        }
//                    }
//                    localSearch.run();
//                });
//            };
//            serverSearch.run();
        };
        if (keywordResults == null || keywordResults.isEmpty()) {
            AndroidUtilities.runOnUIThread(searchRunnable, 600);
        } else {
            searchRunnable.run();
        }
    }

    private void searchAnimated(String emoji) {
        if (emoji == null) {
            return;
        }
        if (lastQuery != null && lastQueryType == 2 && lastQuery.equals(emoji) && !clear && keywordResults != null && !keywordResults.isEmpty()) {
            forceClose = false;
            createListView();
            if (containerView != null) {
                containerView.setVisibility(View.VISIBLE);
                containerView.invalidate();
            }
            return;
        }
        final int id = ++lastQueryId;

        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        }

        searchRunnable = () -> {
            ArrayList<MediaDataController.KeywordResult> standard = new ArrayList<>(1);
            standard.add(new MediaDataController.KeywordResult(emoji, null));
            MediaDataController.getInstance(currentAccount).fillWithAnimatedEmoji(standard, 15, false, false, false, () -> {
                if (id == lastQueryId) {
                    lastQuery = emoji;
                    lastQueryType = 2;
                    standard.remove(standard.size() - 1);
                    if (!standard.isEmpty()) {
                        clear = false;
                        forceClose = false;
                        createListView();
                        if (containerView != null) {
                            containerView.setVisibility(View.VISIBLE);
                            containerView.invalidate();
                        }
                        keywordResults = standard;
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else {
                        clear = true;
                        forceClose();
                    }
                }
            });
        };
        if (keywordResults == null || keywordResults.isEmpty()) {
            AndroidUtilities.runOnUIThread(searchRunnable, 600);
        } else {
            searchRunnable.run();
        }
    }

    private CharSequence makeEmoji(String emojiSource) {
        Paint.FontMetricsInt fontMetricsInt = null;
        if (enterView.getEditField() != null) {
            fontMetricsInt = enterView.getEditField().getPaint().getFontMetricsInt();
        }
        if (fontMetricsInt == null) {
            Paint paint = new Paint();
            paint.setTextSize(AndroidUtilities.dp(18));
            fontMetricsInt = paint.getFontMetricsInt();
        }
        CharSequence emoji;
        if (emojiSource != null && emojiSource.startsWith("animated_")) {
            try {
                long documentId = Long.parseLong(emojiSource.substring(9));
                TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                emoji = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(document));
                AnimatedEmojiSpan span;
                if (document == null) {
                    span = new AnimatedEmojiSpan(documentId, fontMetricsInt);
                } else {
                    span = new AnimatedEmojiSpan(document, fontMetricsInt);
                }
                ((SpannableString) emoji).setSpan(span, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
                return null;
            }
        } else {
            emoji = emojiSource;
            emoji = Emoji.replaceEmoji(emoji, fontMetricsInt, AndroidUtilities.dp(20), true);
        }
        return emoji;
    }

    private void onClick(String emojiSource) {
        if (!show || enterView == null || !(enterView.getFieldText() instanceof Spanned)) {
            return;
        }
        int start, end;
        if (arrowToSpan != null) {
            start = ((Spanned) enterView.getFieldText()).getSpanStart(arrowToSpan);
            end = ((Spanned) enterView.getFieldText()).getSpanEnd(arrowToSpan);
        } else if (arrowToStart != null && arrowToEnd != null) {
            start = arrowToStart;
            end = arrowToEnd;
            arrowToStart = arrowToEnd = null;
        } else {
            return;
        }
        Editable editable = enterView.getEditText();
        if (editable == null || start < 0 || end < 0 || start > editable.length() || end > editable.length()) {
            return;
        }
        if (arrowToSpan != null) {
            if (enterView.getFieldText() instanceof Spannable) {
                ((Spannable) enterView.getFieldText()).removeSpan(arrowToSpan);
            }
            arrowToSpan = null;
        }
        String fromString = editable.toString();
        String replacing = fromString.substring(start, end);
        int replacingLength = replacing.length();
        for (int i = end - replacingLength; i >= 0; i -= replacingLength) {
            if (fromString.substring(i, i + replacingLength).equals(replacing)) {
                CharSequence emoji = makeEmoji(emojiSource);
                if (emoji != null) {
                    AnimatedEmojiSpan[] animatedEmojiSpans = editable.getSpans(i, i + replacingLength, AnimatedEmojiSpan.class);
                    if (animatedEmojiSpans != null && animatedEmojiSpans.length > 0) {
                        break;
                    }
                    Emoji.EmojiSpan[] emojiSpans = editable.getSpans(i, i + replacingLength, Emoji.EmojiSpan.class);
                    if (emojiSpans != null) {
                        for (int j = 0; j < emojiSpans.length; ++j) {
                            editable.removeSpan(emojiSpans[j]);
                        }
                    }
                    editable.replace(i, i + replacingLength, "");
                    editable.insert(i, emoji);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignore) {}
        Emoji.addRecentEmoji(emojiSource);
        show = false;
        forceClose = true;
        lastQueryType = 0;
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    private Path path, circlePath;
    private Paint backgroundPaint;
    private AnimatedFloat showFloat1;
    private AnimatedFloat showFloat2;
    private OvershootInterpolator overshootInterpolator;

    private AnimatedFloat leftGradientAlpha;
    private AnimatedFloat rightGradientAlpha;

    private Emoji.EmojiSpan arrowToSpan;
    private float lastSpanY;
    private Integer arrowToStart, arrowToEnd;
    private float arrowX;
    private AnimatedFloat arrowXAnimated;

    private AnimatedFloat listViewCenterAnimated;
    private AnimatedFloat listViewWidthAnimated;

    private void drawContainerBegin(Canvas canvas) {
        if (enterView != null && enterView.getEditField() != null) {
            if (arrowToSpan != null && arrowToSpan.drawn) {
                arrowX = enterView.getEditField().getX() + enterView.getEditField().getPaddingLeft() + arrowToSpan.lastDrawX;
                lastSpanY = arrowToSpan.lastDrawY;
            } else if (arrowToStart != null && arrowToEnd != null) {
                arrowX = enterView.getEditField().getX() + enterView.getEditField().getPaddingLeft() + AndroidUtilities.dp(12);
            }
        }

        final boolean show = this.show && !forceClose && keywordResults != null && !keywordResults.isEmpty() && !clear;
        final float showT1 = showFloat1.set(show ? 1f : 0f);
        final float showT2 = showFloat2.set(show ? 1f : 0f);
        final float arrowX = arrowXAnimated.set(this.arrowX);

        if (showT1 <= 0 && showT2 <= 0 && !show) {
            containerView.setVisibility(View.GONE);
        }

        path.rewind();

        float listViewLeft = listView.getLeft();
        float listViewRight = listView.getLeft() + (keywordResults == null ? 0 : keywordResults.size()) * AndroidUtilities.dp(44);

        boolean force = listViewWidthAnimated.get() <= 0;
        float width =  listViewRight - listViewLeft <= 0 ? listViewWidthAnimated.get() : listViewWidthAnimated.set(listViewRight - listViewLeft, force);
        float center = listViewCenterAnimated.set((listViewLeft + listViewRight) / 2f, force);

        if (enterView != null && enterView.getEditField() != null) {
            if (direction == DIRECTION_TO_BOTTOM) {
                containerView.setTranslationY(-enterView.getEditField().getHeight() - enterView.getEditField().getScrollY() + lastSpanY + AndroidUtilities.dp(5));
            } else if (direction == DIRECTION_TO_TOP) {
                containerView.setTranslationY(-getMeasuredHeight() - enterView.getEditField().getScrollY() + lastSpanY + AndroidUtilities.dp(20) + containerView.getHeight());
            }
        }
        int listViewPaddingLeft = (int) Math.max(this.arrowX - Math.max(width / 4f, Math.min(width / 2f, AndroidUtilities.dp(66))) - listView.getLeft(), 0);
        if (listView.getPaddingLeft() != listViewPaddingLeft) {
            int dx = listView.getPaddingLeft() - listViewPaddingLeft;
            listView.setPadding(listViewPaddingLeft, 0, 0, 0);
            listView.scrollBy(dx, 0);
        }
        int listViewPaddingLeftI = (int) Math.max(arrowX - Math.max(width / 4f, Math.min(width / 2f, AndroidUtilities.dp(66))) - listView.getLeft(), 0);
        listView.setTranslationX(listViewPaddingLeftI - listViewPaddingLeft);

        float left = center - width / 2f + listView.getPaddingLeft() + listView.getTranslationX();
        float top = listView.getTop() + listView.getTranslationY() + listView.getPaddingTop() + (direction == DIRECTION_TO_BOTTOM ? 0: AndroidUtilities.dp(6.66f));
        float right = Math.min(center + width / 2f + listView.getPaddingLeft() + listView.getTranslationX(), getWidth() - containerView.getPaddingRight());
        float bottom = listView.getBottom() + listView.getTranslationY() - (direction == DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(6.66f) : 0);

        float R = Math.min(AndroidUtilities.dp(9), width / 2f), D = R * 2;

        if (direction == DIRECTION_TO_BOTTOM) {
            AndroidUtilities.rectTmp.set(left, bottom - D, left + D, bottom);
            path.arcTo(AndroidUtilities.rectTmp, 90, 90);

            AndroidUtilities.rectTmp.set(left, top, left + D, top + D);
            path.arcTo(AndroidUtilities.rectTmp, -180, 90);

            AndroidUtilities.rectTmp.set(right - D, top, right, top + D);
            path.arcTo(AndroidUtilities.rectTmp, -90, 90);

            AndroidUtilities.rectTmp.set(right - D, bottom - D, right, bottom);
            path.arcTo(AndroidUtilities.rectTmp, 0, 90);

            path.lineTo(arrowX + AndroidUtilities.dp(8.66f), bottom);
            path.lineTo(arrowX, bottom + AndroidUtilities.dp(6.66f));
            path.lineTo(arrowX - AndroidUtilities.dp(8.66f), bottom);
        } else if (direction == DIRECTION_TO_TOP) {
            AndroidUtilities.rectTmp.set(right - D, top, right, top + D);
            path.arcTo(AndroidUtilities.rectTmp, -90, 90);

            AndroidUtilities.rectTmp.set(right - D, bottom - D, right, bottom);
            path.arcTo(AndroidUtilities.rectTmp, 0, 90);

            AndroidUtilities.rectTmp.set(left, bottom - D, left + D, bottom);
            path.arcTo(AndroidUtilities.rectTmp, 90, 90);

            AndroidUtilities.rectTmp.set(left, top, left + D, top + D);
            path.arcTo(AndroidUtilities.rectTmp, -180, 90);

            path.lineTo(arrowX - AndroidUtilities.dp(8.66f), top);
            path.lineTo(arrowX, top - AndroidUtilities.dp(6.66f));
            path.lineTo(arrowX + AndroidUtilities.dp(8.66f), top);
        }
        path.close();

        if (backgroundPaint == null) {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(2)));
            backgroundPaint.setShadowLayer(AndroidUtilities.dp(4.33f), 0, AndroidUtilities.dp(1 / 3f), 0x33000000);
            backgroundPaint.setColor(Theme.getColor(Theme.key_chat_stickersHintPanel, resourcesProvider));
        }

        if (showT1 < 1) {
            circlePath.rewind();
            float cx = arrowX, cy = (direction == DIRECTION_TO_BOTTOM) ? bottom + AndroidUtilities.dp(6.66f) : top - AndroidUtilities.dp(6.66f);
            float toRadius = (float) Math.sqrt(Math.max(
                    Math.max(
                            Math.pow(cx - left, 2) + Math.pow(cy - top, 2),
                            Math.pow(cx - right, 2) + Math.pow(cy - top, 2)
                    ),
                    Math.max(
                            Math.pow(cx - left, 2) + Math.pow(cy - bottom, 2),
                            Math.pow(cx - right, 2) + Math.pow(cy - bottom, 2)
                    )
            ));
            circlePath.addCircle(cx, cy, toRadius * showT1, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(circlePath);
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * showT1), Canvas.ALL_SAVE_FLAG);
        }

        canvas.drawPath(path, backgroundPaint);
        canvas.save();
        canvas.clipPath(path);
    }

    public void drawContainerEnd(Canvas canvas) {
        final float width =  listViewWidthAnimated.get();
        final float center = listViewCenterAnimated.get();

        float left = center - width / 2f + listView.getPaddingLeft() + listView.getTranslationX();
        float top = listView.getTop() + listView.getPaddingTop();
        float right = Math.min(center + width / 2f + listView.getPaddingLeft() + listView.getTranslationX(), getWidth() - containerView.getPaddingRight());
        float bottom = listView.getBottom();

        float leftAlpha = leftGradientAlpha.set(listView.canScrollHorizontally(-1) ? 1f : 0f);
        if (leftAlpha > 0) {
            Theme.chat_gradientRightDrawable.setBounds((int) left, (int) top, (int) left + AndroidUtilities.dp(32), (int) bottom);
            Theme.chat_gradientRightDrawable.setAlpha((int) (255 * leftAlpha));
            Theme.chat_gradientRightDrawable.draw(canvas);
        }

        float rightAlpha = rightGradientAlpha.set(listView.canScrollHorizontally(1) ? 1f : 0f);
        if (rightAlpha > 0) {
            Theme.chat_gradientLeftDrawable.setBounds((int) right - AndroidUtilities.dp(32), (int) top, (int) right, (int) bottom);
            Theme.chat_gradientLeftDrawable.setAlpha((int) (255 * rightAlpha));
            Theme.chat_gradientLeftDrawable.draw(canvas);
        }

        canvas.restore();
        if (showFloat1.get() < 1) {
            canvas.restore();
            canvas.restore();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (listView == null) {
            return super.dispatchTouchEvent(ev);
        }

        final float width =  listViewWidthAnimated.get();
        final float center = listViewCenterAnimated.get();

        AndroidUtilities.rectTmp.set(
            center - width / 2f + listView.getPaddingLeft() + listView.getTranslationX(),
            listView.getTop() + listView.getPaddingTop(),
            Math.min(center + width / 2f + listView.getPaddingLeft() + listView.getTranslationX(), getWidth() - containerView.getPaddingRight()),
            listView.getBottom()
        );
        AndroidUtilities.rectTmp.offset(containerView.getX(), containerView.getY());

        if (show && AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY())) {
            return super.dispatchTouchEvent(ev);
        } else {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                return false;
            } else {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                return super.dispatchTouchEvent(ev);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newEmojiSuggestionsAvailable) {
            if (keywordResults != null && !keywordResults.isEmpty()) {
                fireUpdate();
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    listView.getChildAt(i).invalidate();
                }
            }
        }
    }

    protected int emojiCacheType() {
        return AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD;
    }

    public void invalidateContent() {
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    public class EmojiImageView extends View {

        private String emoji;
        public Drawable drawable;
        private boolean attached;
        private int direction = DIRECTION_TO_BOTTOM;

        private AnimatedFloat pressed = new AnimatedFloat(this, 350, new OvershootInterpolator(5.0f));

        public EmojiImageView(Context context) {
            super(context);
        }

        private final int paddingDp = 3;
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(AndroidUtilities.dp(paddingDp), AndroidUtilities.dp(paddingDp + (direction == DIRECTION_TO_BOTTOM ? 0 : 6.66f)), AndroidUtilities.dp(paddingDp), AndroidUtilities.dp(paddingDp + (direction == DIRECTION_TO_BOTTOM ? 6.66f : 0)));
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44 + 8), MeasureSpec.EXACTLY)
            );
        }

        private void setEmoji(String emoji, int direction) {
            this.emoji = emoji;
            if (emoji != null && emoji.startsWith("animated_")) {
                try {
                    long documentId = Long.parseLong(emoji.substring(9));
                    if (!(drawable instanceof AnimatedEmojiDrawable) || ((AnimatedEmojiDrawable) drawable).getDocumentId() != documentId) {
                        setImageDrawable(AnimatedEmojiDrawable.make(UserConfig.selectedAccount, emojiCacheType(), documentId));
                    }
                } catch (Exception ignore) {
                    setImageDrawable(null);
                }
            } else {
                setImageDrawable(Emoji.getEmojiBigDrawable(emoji));
            }
            if (this.direction != direction) {
                this.direction = direction;
                requestLayout();
            }
        }

        public void setImageDrawable(@Nullable Drawable drawable) {
            if (this.drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) this.drawable).removeView(this);
            }
            this.drawable = drawable;
            if (drawable instanceof AnimatedEmojiDrawable && attached) {
                ((AnimatedEmojiDrawable) drawable).addView(this);
            }
        }

        public void setDirection(int direction) {
            this.direction = direction;
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float scale = 0.8f + 0.2f * (1f - pressed.set(isPressed() ? 1f : 0f));
            if (drawable != null) {
                int cx = getWidth() / 2;
                int cy = (getHeight() - getPaddingBottom() + getPaddingTop()) / 2;
                drawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
                canvas.scale(scale, scale, cx, cy);
                if (drawable instanceof AnimatedEmojiDrawable) {
                    ((AnimatedEmojiDrawable) drawable).setTime(System.currentTimeMillis());
                }
                drawable.draw(canvas);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            detach();
        }

        public void detach() {
            if (drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawable).removeView(this);
            }
            attached = false;
        }
        public void attach() {
            if (drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawable).addView(this);
            }
            attached = true;
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        SuggestEmojiView suggestEmojiView;
        public Adapter(SuggestEmojiView suggestEmojiView) {
            this.suggestEmojiView = suggestEmojiView;
        }

        @Override
        public long getItemId(int position) {
            return suggestEmojiView.keywordResults == null ? 0 : suggestEmojiView.keywordResults.get(position).emoji.hashCode();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new EmojiImageView(suggestEmojiView.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((EmojiImageView) holder.itemView).setEmoji(suggestEmojiView.keywordResults == null ? null : suggestEmojiView.keywordResults.get(position).emoji, suggestEmojiView.getDirection());
        }

        @Override
        public int getItemCount() {
            return suggestEmojiView.keywordResults == null ? 0 : suggestEmojiView.keywordResults.size();
        }
    }
}
