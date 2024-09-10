package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class TranslateAlert2 extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private Integer reqId;
    private CharSequence reqText;
    private ArrayList<TLRPC.MessageEntity> reqMessageEntities;
    private TLRPC.InputPeer reqPeer;
    private int reqMessageId;

    private String fromLanguage, toLanguage;
    private String prevToLanguage;

    private HeaderView headerView;
    private LoadingTextView loadingTextView;
    private FrameLayout textViewContainer;
    private LinkSpanDrawable.LinksTextView textView;

    private boolean sheetTopNotAnimate;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private PaddedAdapter adapter;

    private View buttonShadowView;
    private FrameLayout buttonView;
    private TextView buttonTextView;

    private BaseFragment fragment;
    private Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress;
    private boolean firstTranslation = true;

    public TranslateAlert2(
        Context context,
        String fromLanguage, String toLanguage,
        CharSequence text, ArrayList<TLRPC.MessageEntity> entities,
        Theme.ResourcesProvider resourcesProvider
    ) {
        this(context, fromLanguage, toLanguage, text, entities, null, 0, resourcesProvider);
    }

    private TranslateAlert2(
        Context context,
        String fromLanguage, String toLanguage,
        CharSequence text, ArrayList<TLRPC.MessageEntity> entities, TLRPC.InputPeer peer, int messageId,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, false, resourcesProvider);

        backgroundPaddingLeft = 0;

        fixNavigationBar();

        this.reqText = text;
        this.reqPeer = peer;
        this.reqMessageId = messageId;

        this.fromLanguage = fromLanguage;
        this.toLanguage = toLanguage;

        containerView = new ContainerView(context);
        sheetTopAnimated = new AnimatedFloat(containerView, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        loadingTextView = new LoadingTextView(context);
        loadingTextView.setPadding(dp(22), dp(12), dp(22), dp(6));
        loadingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        loadingTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        loadingTextView.setLinkTextColor(Theme.multAlpha(getThemedColor(Theme.key_dialogTextBlack), .2f));
        loadingTextView.setText(Emoji.replaceEmoji(text == null ? "" : text.toString(), loadingTextView.getPaint().getFontMetricsInt(), true));

        textViewContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setDisablePaddingsOffsetY(true);
        textView.setPadding(dp(22), dp(12), dp(22), dp(6));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        textView.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        textView.setTextIsSelectable(true);
        textView.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        int handleColor = getThemedColor(Theme.key_chat_TextSelectionCursor);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !XiaomiUtilities.isMIUI()) {
                Drawable left = textView.getTextSelectHandleLeft();
                left.setColorFilter(handleColor, PorterDuff.Mode.SRC_IN);
                textView.setTextSelectHandleLeft(left);

                Drawable right = textView.getTextSelectHandleRight();
                right.setColorFilter(handleColor, PorterDuff.Mode.SRC_IN);
                textView.setTextSelectHandleRight(right);
            }
        } catch (Exception e) {}
        textViewContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < getSheetTop() - getTop()) {
                    dismiss();
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                return true;
            }

            @Override
            public void requestChildFocus(View child, View focused) {}
        };
        listView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        listView.setPadding(0, AndroidUtilities.statusBarHeight + dp(56), 0, dp(80));
        listView.setClipToPadding(true);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new PaddedAdapter(context, loadingTextView));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                containerView.invalidate();
                updateButtonShadow(listView.canScrollVertically(1));
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    sheetTopNotAnimate = false;
                }
                if ((newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_SETTLING) && getSheetTop(false) > 0 && getSheetTop(false) < dp(64 + 32) && listView.canScrollVertically(1) && hasEnoughHeight()) {
                    sheetTopNotAnimate = true;
                    listView.smoothScrollBy(0, (int) getSheetTop(false));
                }
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }
        };
        itemAnimator.setDurations(180);
        itemAnimator.setInterpolator(new LinearInterpolator());
        listView.setItemAnimator(itemAnimator);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        headerView = new HeaderView(context);
        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        buttonView = new FrameLayout(context);
        buttonView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        buttonShadowView = new View(context);
        buttonShadowView.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        buttonShadowView.setAlpha(0);
        buttonView.addView(buttonShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight() / dpf2(1), Gravity.TOP | Gravity.FILL_HORIZONTAL));

        buttonTextView = new TextView(context);
        buttonTextView.setLines(1);
        buttonTextView.setSingleLine(true);
        buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonTextView.setEllipsize(TextUtils.TruncateAt.END);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setText(LocaleController.getString(R.string.CloseTranslation));
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
        buttonTextView.setOnClickListener(e -> dismiss());
        buttonView.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 16, 16, 16, 16));

        containerView.addView(buttonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        translate();
    }

    private boolean hasEnoughHeight() {
        float height = 0;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == 1)
                height += child.getHeight();
        }
        return height >= listView.getHeight() - listView.getPaddingTop() - listView.getPaddingBottom();
    }

    public void translate() {
        if (reqId != null) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = null;
        }
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        TLRPC.TL_textWithEntities textWithEntities = new TLRPC.TL_textWithEntities();
        textWithEntities.text = reqText == null ? "" : reqText.toString();
        if (reqMessageEntities != null) {
            textWithEntities.entities = reqMessageEntities;
        }
        if (reqPeer != null) {
            req.flags |= 1;
            req.peer = reqPeer;
            req.id.add(reqMessageId);
        } else {
            req.flags |= 2;
            req.text.add(textWithEntities);
        }
//        if (fromLanguage != null && !"und".equals(fromLanguage)) {
//            req.flags |= 4;
//            req.from_lang = fromLanguage;
//        }
        String lang = toLanguage;
        if (lang != null) {
            lang = lang.split("_")[0];
        }
        if ("nb".equals(lang)) {
            lang = "no";
        }
        req.to_lang = lang;
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            AndroidUtilities.runOnUIThread(() -> {
                reqId = null;
                if (res instanceof TLRPC.TL_messages_translateResult &&
                    !((TLRPC.TL_messages_translateResult) res).result.isEmpty() &&
                    ((TLRPC.TL_messages_translateResult) res).result.get(0) != null &&
                    ((TLRPC.TL_messages_translateResult) res).result.get(0).text != null
                ) {
                    firstTranslation = false;
                    TLRPC.TL_textWithEntities text = preprocess(textWithEntities, ((TLRPC.TL_messages_translateResult) res).result.get(0));
                    CharSequence translated = SpannableStringBuilder.valueOf(text.text);
                    MessageObject.addEntitiesToText(translated, text.entities, false, true, false, false);
                    translated = preprocessText(translated);
                    textView.setText(translated);
                    adapter.updateMainView(textViewContainer);
                } else if (firstTranslation) {
                    dismiss();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.TranslationFailedAlert2));
                } else {
                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.TranslationFailedAlert2)).show();
                    headerView.toLanguageTextView.setText(languageName(toLanguage = prevToLanguage));
                    adapter.updateMainView(textViewContainer);
                }
            });
        });
    }

    public static TLRPC.TL_textWithEntities preprocess(TLRPC.TL_textWithEntities source, TLRPC.TL_textWithEntities received) {
        if (received == null || received.text == null) {
            return null;
        }
        for (int i = 0; i < received.entities.size(); ++i) {
            TLRPC.MessageEntity entity = received.entities.get(i);
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                if (entity.url == null) {
                    continue;
                }
                String text = received.text.substring(entity.offset, entity.offset + entity.length);
                if (TextUtils.equals(text, entity.url)) {
                    TLRPC.TL_messageEntityUrl newEntity = new TLRPC.TL_messageEntityUrl();
                    newEntity.offset = entity.offset;
                    newEntity.length = entity.length;
                    received.entities.set(i, newEntity);
                } else if (
                    entity.url.startsWith("https://t.me/") &&
                    text.startsWith("@") &&
                    TextUtils.equals(text.substring(1), entity.url.substring(13))
                ) {
                    TLRPC.TL_messageEntityMention newEntity = new TLRPC.TL_messageEntityMention();
                    newEntity.offset = entity.offset;
                    newEntity.length = entity.length;
                    received.entities.set(i, newEntity);
                }
            } else if (entity instanceof TLRPC.TL_messageEntityPre) {
                if (source != null && source.entities != null && i < source.entities.size() && source.entities.get(i) instanceof TLRPC.TL_messageEntityPre) {
                    entity.language = source.entities.get(i).language;
                }
            }
        }
        if (source != null && source.text != null && !source.entities.isEmpty()) {
            HashMap<String, ArrayList<Emoji.EmojiSpanRange>> srcIndexes = groupEmojiRanges(source.text);
            HashMap<String, ArrayList<Emoji.EmojiSpanRange>> destIndexes = groupEmojiRanges(received.text);

            for (int i = 0; i < source.entities.size(); ++i) {
                TLRPC.MessageEntity entity = source.entities.get(i);
                if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    String code = source.text.substring(entity.offset, entity.offset + entity.length);
                    if (TextUtils.isEmpty(code)) {
                        continue;
                    }
                    ArrayList<Emoji.EmojiSpanRange> srcRanges = srcIndexes.get(code);
                    ArrayList<Emoji.EmojiSpanRange> destRanges = destIndexes.get(code);
                    if (srcRanges == null || destRanges == null) {
                        continue;
                    }
                    int srcIndex = -1;
                    for (int j = 0; j < srcRanges.size(); ++j) {
                        Emoji.EmojiSpanRange range = srcRanges.get(j);
                        if (range.start == entity.offset && range.end == entity.offset + entity.length) {
                            srcIndex = j;
                            break;
                        }
                    }
                    if (srcIndex < 0 || srcIndex >= destRanges.size()) {
                        continue;
                    }
                    Emoji.EmojiSpanRange destRange = destRanges.get(srcIndex);
                    if (destRange == null) {
                        continue;
                    }

                    boolean alreadyContainsOne = false;
                    for (int j = 0; j < received.entities.size(); ++j) {
                        TLRPC.MessageEntity destEntity = received.entities.get(j);
                        if (
                            destEntity instanceof TLRPC.TL_messageEntityCustomEmoji &&
                            AndroidUtilities.intersect1d(destRange.start, destRange.end, destEntity.offset, destEntity.offset + destEntity.length)
                        ) {
                            alreadyContainsOne = true;
                            break;
                        }
                    }
                    if (alreadyContainsOne) {
                        continue;
                    }

                    TLRPC.TL_messageEntityCustomEmoji newEntity = new TLRPC.TL_messageEntityCustomEmoji();
                    newEntity.document_id = ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id;
                    newEntity.document = ((TLRPC.TL_messageEntityCustomEmoji) entity).document;
                    newEntity.offset = destRange.start;
                    newEntity.length = destRange.end - destRange.start;
                    received.entities.add(newEntity);
                }
            }
        }
        return received;
    }

    private static HashMap<String, ArrayList<Emoji.EmojiSpanRange>> groupEmojiRanges(CharSequence text) {
        HashMap<String, ArrayList<Emoji.EmojiSpanRange>> result = new HashMap<>();
        if (text == null) {
            return result;
        }
        ArrayList<Emoji.EmojiSpanRange> ranges = Emoji.parseEmojis(text);
        if (ranges == null) {
            return result;
        }
        String string = text.toString();
        for (int i = 0; i < ranges.size(); ++i) {
            Emoji.EmojiSpanRange range = ranges.get(i);
            if (range == null || range.code == null) {
                continue;
            }
            String code = string.substring(range.start, range.end);
            ArrayList<Emoji.EmojiSpanRange> codeRanges = result.get(code);
            if (codeRanges == null) {
                result.put(code, codeRanges = new ArrayList<>());
            }
            codeRanges.add(range);
        }
        return result;
    }

    public static ArrayList<TLRPC.TL_textWithEntities> preprocess(ArrayList<TLRPC.TL_textWithEntities> received) {
        if (received == null) {
            return null;
        }
        for (int i = 0; i < received.size(); ++i) {
            received.set(i, preprocess(null, received.get(i)));
        }
        return received;
    }

    private CharSequence preprocessText(CharSequence text) {
        Spannable spannable = new SpannableStringBuilder(text);
        URLSpan[] urlSpans;
        if (onLinkPress != null || fragment != null) {
            urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
            for (int i = 0; i < urlSpans.length; ++i) {
                URLSpan urlSpan = urlSpans[i];
                int start = spannable.getSpanStart(urlSpan),
                        end = spannable.getSpanEnd(urlSpan);
                if (start == -1 || end == -1) {
                    continue;
                }
                spannable.removeSpan(urlSpan);
                spannable.setSpan(
                    new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View view) {
                            if (onLinkPress != null) {
                                if (onLinkPress.run(urlSpan)) {
                                    dismiss();
                                }
                            } else if (fragment != null) {
                                AlertsCreator.showOpenUrlAlert(fragment, urlSpan.getURL(), false, false);
                            }
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            int alpha = Math.min(ds.getAlpha(), ds.getColor() >> 24 & 0xff);
                            if (!(urlSpan instanceof URLSpanNoUnderline)) {
                                ds.setUnderlineText(true);
                            }
                            ds.setColor(Theme.getColor(Theme.key_dialogTextLink));
                            ds.setAlpha(alpha);
                        }
                    },
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        return Emoji.replaceEmoji(spannable, textView.getPaint().getFontMetricsInt(), true);
    }

    @Override
    public void dismissInternal() {
        if (reqId != null) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = null;
        }
        super.dismissInternal();
    }

    public void setFragment(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public void setOnLinkPress(Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress) {
        this.onLinkPress = onLinkPress;
    }

    public void setNoforwards(boolean noforwards) {
        if (textView != null) {
            textView.setTextIsSelectable(!noforwards);
        }
        if (noforwards) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private class LoadingTextView extends TextView {

        private final LinkPath path = new LinkPath(true);
        private final LoadingDrawable loadingDrawable = new LoadingDrawable();

        public LoadingTextView(Context context) {
            super(context);
            loadingDrawable.usePath(path);
            loadingDrawable.setSpeed(.65f);
            loadingDrawable.setRadiiDp(4);
            setBackground(loadingDrawable);
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(Theme.multAlpha(color, .2f));
            loadingDrawable.setColors(
                Theme.multAlpha(color, 0.03f),
                Theme.multAlpha(color, 0.175f),
                Theme.multAlpha(color, 0.2f),
                Theme.multAlpha(color, 0.45f)
            );
        }

        private void updateDrawable() {
            if (path == null || loadingDrawable == null) {
                return;
            }

            path.rewind();
            if (getLayout() != null && getLayout().getText() != null) {
                path.setCurrentLayout(getLayout(), 0, getPaddingLeft(), getPaddingTop());
                getLayout().getSelectionPath(0, getLayout().getText().length(), path);
            }
            loadingDrawable.updateBounds();
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            updateDrawable();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateDrawable();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            loadingDrawable.reset();
        }
    }

    private static class PaddedAdapter extends RecyclerListView.Adapter {

        private Context mContext;
        private View mMainView;

        public PaddedAdapter(Context context, View mainView) {
            mContext = context;
            mMainView = mainView;
        }

        private int mainViewType = 1;

        public void updateMainView(View newMainView) {
            if (mMainView == newMainView) {
                return;
            }
            mainViewType++;
            mMainView = newMainView;
            notifyItemChanged(1);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new RecyclerListView.Holder(new View(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(
                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * .4f), MeasureSpec.EXACTLY)
                        );
                    }
                });
            } else {
                return new RecyclerListView.Holder(mMainView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else {
                return mainViewType;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private AnimatedFloat sheetTopAnimated;
    private float getSheetTop() {
        return getSheetTop(true);
    }
    private float getSheetTop(boolean animated) {
        float top = listView.getTop();
        if (listView.getChildCount() >= 1) {
            top += Math.max(0, listView.getChildAt(listView.getChildCount() - 1).getTop());
        }
        top = Math.max(0, top - dp(78));
        if (animated && sheetTopAnimated != null) {
            if (!listView.scrollingByUser && !sheetTopNotAnimate) {
                top = sheetTopAnimated.set(top);
            } else {
                sheetTopAnimated.set(top, true);
            }
        }
        return top;
    }

    private class HeaderView extends FrameLayout {

        private ImageView backButton;
        private TextView titleTextView;
        private LinearLayout subtitleView;
        private TextView fromLanguageTextView;
        private ImageView arrowView;
        private AnimatedTextView toLanguageTextView;

        private View backgroundView;

        private View shadow;

        public HeaderView(Context context) {
            super(context);

            backgroundView = new View(context);
            backgroundView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0,  12, 0, 0));

            backButton = new ImageView(context);
            backButton.setScaleType(ImageView.ScaleType.CENTER);
            backButton.setImageResource(R.drawable.ic_ab_back);
            backButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
            backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
            backButton.setAlpha(0f);
            backButton.setOnClickListener(e -> dismiss());
            addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.TOP, 1, 1, 1, 1));

            titleTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (LocaleController.isRTL) {
                        titleTextView.setPivotX(getMeasuredWidth());
                    }
                }
            };
            titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setText(LocaleController.getString(R.string.AutomaticTranslation));
            titleTextView.setPivotX(0);
            titleTextView.setPivotY(0);
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 20, 22, 0));

            subtitleView = new LinearLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (LocaleController.isRTL) {
                        subtitleView.setPivotX(getMeasuredWidth());
                    }
                }
            };
            if (LocaleController.isRTL) {
                subtitleView.setGravity(Gravity.RIGHT);
            }
            subtitleView.setPivotX(0);
            subtitleView.setPivotY(0);
            if (!TextUtils.isEmpty(fromLanguage) && !"und".equals(fromLanguage)) {
                fromLanguageTextView = new TextView(context);
                fromLanguageTextView.setLines(1);
                fromLanguageTextView.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));
                fromLanguageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                fromLanguageTextView.setText(capitalFirst(languageName(fromLanguage)));
                fromLanguageTextView.setPadding(0, dp(2), 0, dp(2));
            }

            arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.search_arrow);
            arrowView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_player_actionBarSubtitle), PorterDuff.Mode.MULTIPLY));
            if (LocaleController.isRTL) {
                arrowView.setScaleX(-1f);
            }

            toLanguageTextView = new AnimatedTextView(context) {
                private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();

                @Override
                protected void onDraw(Canvas canvas) {
                    if (LocaleController.isRTL) {
                        AndroidUtilities.rectTmp.set(getWidth() - width(), (getHeight() - dp(18)) / 2f, getWidth(), (getHeight() + dp(18)) / 2f);
                    } else {
                        AndroidUtilities.rectTmp.set(0, (getHeight() - dp(18)) / 2f, width(), (getHeight() + dp(18)) / 2f);
                    }
                    bgPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_player_actionBarSubtitle), .1175f));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), bgPaint);
                    if (links.draw(canvas)) {
                        invalidate();
                    }

                    super.onDraw(canvas);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        LinkSpanDrawable link = new LinkSpanDrawable(null, resourcesProvider, event.getX(), event.getY());
                        link.setColor(Theme.multAlpha(getThemedColor(Theme.key_player_actionBarSubtitle), .1175f));
                        LinkPath path = link.obtainNewPath();
                        if (LocaleController.isRTL) {
                            AndroidUtilities.rectTmp.set(getWidth() - width(), (getHeight() - dp(18)) / 2f, getWidth(), (getHeight() + dp(18)) / 2f);
                        } else {
                            AndroidUtilities.rectTmp.set(0, (getHeight() - dp(18)) / 2f, width(), (getHeight() + dp(18)) / 2f);
                        }
                        path.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                        links.addLink(link);
                        invalidate();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            performClick();
                        }
                        links.clear();
                        invalidate();
                    }
                    return super.onTouchEvent(event);
                }
            };
            if (LocaleController.isRTL) {
                toLanguageTextView.setGravity(Gravity.RIGHT);
            }
            toLanguageTextView.setAnimationProperties(.25f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            toLanguageTextView.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));
            toLanguageTextView.setTextSize(dp(14));
            toLanguageTextView.setText(capitalFirst(languageName(toLanguage)));
            toLanguageTextView.setPadding(dp(4), dp(2), dp(4), dp(2));
            toLanguageTextView.setOnClickListener(e -> openLanguagesSelect());

            if (LocaleController.isRTL) {
                subtitleView.addView(toLanguageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, fromLanguageTextView != null ? 3 : 0, 0));
                if (fromLanguageTextView != null) {
                    subtitleView.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));
                    subtitleView.addView(fromLanguageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
                }
            } else {
                if (fromLanguageTextView != null) {
                    subtitleView.addView(fromLanguageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                    subtitleView.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));
                }
                subtitleView.addView(toLanguageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, fromLanguageTextView != null ? 3 : 0, 0, 0, 0));
            }

            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 43, 22, 0));

            shadow = new View(context);
            shadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
            shadow.setAlpha(0);
            addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight() / dpf2(1), Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 56, 0, 0));
        }

        public void openLanguagesSelect() {
            ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(Math.min((int) (AndroidUtilities.displaySize.y * .33f), MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.EXACTLY)
                    );
                }
            };

            Drawable shadowDrawable2 = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
            shadowDrawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
            layout.setBackground(shadowDrawable2);

            final Runnable[] dismiss = new Runnable[1];

            ArrayList<LocaleController.LocaleInfo> locales = TranslateController.getLocales();
            boolean first = true;
            for (int i = 0; i < locales.size(); ++i) {
                LocaleController.LocaleInfo localeInfo = locales.get(i);

                if (
                    localeInfo.pluralLangCode.equals(fromLanguage) ||
                    !"remote".equals(localeInfo.pathToFile)
                ) {
                    continue;
                }

                ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, first, i == locales.size() - 1, resourcesProvider);
                button.setText(capitalFirst(languageName(localeInfo.pluralLangCode)));
                button.setChecked(TextUtils.equals(toLanguage, localeInfo.pluralLangCode));
                button.setOnClickListener(e -> {
                    if (dismiss[0] != null) {
                        dismiss[0].run();
                    }

                    if (TextUtils.equals(toLanguage, localeInfo.pluralLangCode)) {
                        return;
                    }

                    if (adapter.mMainView == textViewContainer) {
                        prevToLanguage = toLanguage;
                    }
                    toLanguageTextView.setText(capitalFirst(languageName(toLanguage = localeInfo.pluralLangCode)));
                    adapter.updateMainView(loadingTextView);
                    setToLanguage(toLanguage);
                    translate();
                });
                layout.addView(button);

                first = false;
            }

            ActionBarPopupWindow window = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            dismiss[0] = () -> window.dismiss();
            window.setPauseNotifications(true);
            window.setDismissAnimationDuration(220);
            window.setOutsideTouchable(true);
            window.setClippingEnabled(true);
            window.setAnimationStyle(R.style.PopupContextAnimation);
            window.setFocusable(true);
            int[] location = new int[2];
            toLanguageTextView.getLocationInWindow(location);
            layout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST));
            int height = layout.getMeasuredHeight();
            int y = location[1] > AndroidUtilities.displaySize.y * .9f - height ? location[1] - height + dp(8) : location[1] + toLanguageTextView.getMeasuredHeight() - dp(8);
            window.showAtLocation(containerView, Gravity.TOP | Gravity.LEFT, location[0] - dp(8), y);
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);

            float t = MathUtils.clamp((translationY - AndroidUtilities.statusBarHeight) / dp(64), 0, 1);
            if (!hasEnoughHeight()) {
                t = 1;
            }
            t = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);

            titleTextView.setScaleX(AndroidUtilities.lerp(.85f, 1f, t));
            titleTextView.setScaleY(AndroidUtilities.lerp(.85f, 1f, t));
            titleTextView.setTranslationY(AndroidUtilities.lerp(dpf2(-12), 0, t));
            if (!LocaleController.isRTL) {
                titleTextView.setTranslationX(AndroidUtilities.lerp(dpf2(50), 0, t));
                subtitleView.setTranslationX(AndroidUtilities.lerp(dpf2(50), 0, t));
            }

            subtitleView.setTranslationY(AndroidUtilities.lerp(dpf2(-22), 0, t));

            backButton.setTranslationX(AndroidUtilities.lerp(0, dpf2(-25), t));
            backButton.setAlpha(1f - t);

            shadow.setTranslationY(AndroidUtilities.lerp(0, dpf2(22), t));
            shadow.setAlpha(1f - t);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(78), MeasureSpec.EXACTLY)
            );
        }
    }

    private class ContainerView extends FrameLayout {
        public ContainerView(Context context) {
            super(context);

            bgPaint.setColor(getThemedColor(Theme.key_dialogBackground));
            Theme.applyDefaultShadow(bgPaint);
        }

        private Path bgPath = new Path();
        private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void dispatchDraw(Canvas canvas) {

            float top = getSheetTop();
            final float R = AndroidUtilities.lerp(0, dp(12), MathUtils.clamp(top / dpf2(24), 0, 1));
            headerView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight, top));
            updateLightStatusBar(top <= AndroidUtilities.statusBarHeight / 2f);

            bgPath.rewind();
            AndroidUtilities.rectTmp.set(0, top, getWidth(), getHeight() + R);
            bgPath.addRoundRect(AndroidUtilities.rectTmp, R, R, Path.Direction.CW);
            canvas.drawPath(bgPath, bgPaint);

            super.dispatchDraw(canvas);
        }

        private Boolean lightStatusBarFull;
        private void updateLightStatusBar(boolean full) {
            if (lightStatusBarFull == null || lightStatusBarFull != full) {
                lightStatusBarFull = full;
                AndroidUtilities.setLightStatusBar(getWindow(), AndroidUtilities.computePerceivedBrightness(
                    full ?
                        getThemedColor(Theme.key_dialogBackground) :
                        Theme.blendOver(
                            getThemedColor(Theme.key_actionBarDefault),
                            0x33000000
                        )
                ) > .721f);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return AndroidUtilities.dp(16 + 48 + 16);
                }
            });
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Bulletin.removeDelegate(this);
        }
    }

    public static String capitalFirst(String text) {
        if (text == null || text.length() <= 0) {
            return null;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public static CharSequence capitalFirst(CharSequence text) {
        if (text == null || text.length() <= 0) {
            return null;
        }
        SpannableStringBuilder builder = text instanceof SpannableStringBuilder ? (SpannableStringBuilder) text : SpannableStringBuilder.valueOf(text);
        String string = builder.toString();
        builder.replace(0, 1, string.substring(0, 1).toUpperCase());
        return builder;
    }

    public static String languageName(String locale) {
        return languageName(locale, null);
    }

    public static String languageName(String locale, boolean[] accusative) {
        if (locale == null || locale.equals(TranslateController.UNKNOWN_LANGUAGE) || locale.equals("auto")) {
            return null;
        }

        String simplifiedLocale = locale.split("_")[0];
        if ("nb".equals(simplifiedLocale)) {
            simplifiedLocale = "no";
        }

        // getting localized language name in accusative case
        if (accusative != null) {
            String localed = LocaleController.getString("TranslateLanguage" + simplifiedLocale.toUpperCase());
            if (accusative[0] = (localed != null && !localed.startsWith("LOC_ERR"))) {
                return localed;
            }
        }

        // getting language name from system
        String systemLangName = systemLanguageName(locale);
        if (systemLangName == null) {
            systemLangName = systemLanguageName(simplifiedLocale);
        }
        if (systemLangName != null) {
            return systemLangName;
        }

        // getting language name from lang packs
        if ("no".equals(locale)) {
            locale = "nb";
        }
        final LocaleController.LocaleInfo currentLanguageInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        final LocaleController.LocaleInfo thisLanguageInfo = LocaleController.getInstance().getBuiltinLanguageByPlural(locale);
        if (thisLanguageInfo == null) {
            return null;
        }
        boolean isCurrentLanguageEnglish = currentLanguageInfo != null && "en".equals(currentLanguageInfo.pluralLangCode);
        if (isCurrentLanguageEnglish) {
            return thisLanguageInfo.nameEnglish;
        } else {
            return thisLanguageInfo.name;
        }
    }

    public static String languageNameCapital(String locale) {
        String lng = languageName(locale);
        if (lng == null) return null;
        return lng.substring(0, 1).toUpperCase() + lng.substring(1);
    }

    public static String systemLanguageName(String langCode) {
        return systemLanguageName(langCode, false);
    }

    private static HashMap<String, Locale> localesByCode;
    public static String systemLanguageName(String langCode, boolean inItsOwnLocale) {
        if (langCode == null) {
            return null;
        }
        if (localesByCode == null) {
            localesByCode = new HashMap<>();
            try {
                Locale[] allLocales = Locale.getAvailableLocales();
                for (int i = 0; i < allLocales.length; ++i) {
                    localesByCode.put(allLocales[i].getLanguage(), allLocales[i]);
                    String region = allLocales[i].getCountry();
                    if (region != null && region.length() > 0) {
                        localesByCode.put(allLocales[i].getLanguage() + "-" + region.toLowerCase(), allLocales[i]);
                    }
                }
            } catch (Exception ignore) {}
        }
        langCode = langCode.replace("_", "-").toLowerCase();
        try {
            Locale locale = localesByCode.get(langCode);
            if (locale != null) {
                String name = locale.getDisplayLanguage(inItsOwnLocale ? locale : Locale.getDefault());
                if (langCode.contains("-")) {
                    String region = locale.getDisplayCountry(inItsOwnLocale ? locale : Locale.getDefault());
                    if (!TextUtils.isEmpty(region)) {
                        name += " (" + region + ")";
                    }
                }
                return name;
            }
        } catch (Exception ignore) {}
        return null;
    }


    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            loadingTextView.invalidate();
            textView.invalidate();
        }
    }

    private Boolean buttonShadowShown;
    private void updateButtonShadow(boolean show) {
        if (buttonShadowShown == null || buttonShadowShown != show) {
            buttonShadowShown = show;
            buttonShadowView.animate().cancel();
            buttonShadowView.animate().alpha(show ? 1f : 0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
        }
    }

    public static TranslateAlert2 showAlert(Context context, BaseFragment fragment, int currentAccount, TLRPC.InputPeer peer, int msgId, String fromLanguage, String toLanguage, CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean noforwards, Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress, Runnable onDismiss) {
        TranslateAlert2 alert = new TranslateAlert2(context, fromLanguage, toLanguage, text, entities, peer, msgId, null) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        };
        alert.setNoforwards(noforwards);
        alert.setFragment(fragment);
        alert.setOnLinkPress(onLinkPress);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
        return alert;
    }

    public static TranslateAlert2 showAlert(Context context, BaseFragment fragment, int currentAccount, String fromLanguage, String toLanguage, CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean noforwards, Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress, Runnable onDismiss) {
        if (context == null) {
            return null;
        }
        TranslateAlert2 alert = new TranslateAlert2(context, fromLanguage, toLanguage, text, entities, null) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        };
        alert.setNoforwards(noforwards);
        alert.setFragment(fragment);
        alert.setOnLinkPress(onLinkPress);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
        return alert;
    }

    public static String getToLanguage() {
        return MessagesController.getGlobalMainSettings().getString("translate_to_language", LocaleController.getInstance().getCurrentLocale().getLanguage());
    }

    public static void setToLanguage(String toLang) {
        MessagesController.getGlobalMainSettings().edit().putString("translate_to_language", toLang).apply();
    }

    public static void resetToLanguage() {
        MessagesController.getGlobalMainSettings().edit().remove("translate_to_language").apply();
    }
}
