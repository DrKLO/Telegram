package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Adapters.PaddedListAdapter;
import org.telegram.ui.Business.QuickRepliesActivity;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.MentionCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;

public class MentionsContainerView extends BlurredFrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private final Theme.ResourcesProvider resourcesProvider;

    private MentionsListView listView;
    private LinearLayoutManager linearLayoutManager;
    private ExtendedGridLayoutManager gridLayoutManager;

    private PaddedListAdapter paddedAdapter;
    private MentionsAdapter adapter;
    BaseFragment baseFragment;

    private float containerTop, containerBottom, containerPadding, listViewPadding;
    private boolean allowBlur;
    private RecyclerListView.OnItemClickListener mentionsOnItemClickListener;
    private Delegate delegate;

    public MentionsContainerView(@NonNull Context context, long dialogId, long threadMessageId, BaseFragment baseFragment, SizeNotifierFrameLayout container, Theme.ResourcesProvider resourcesProvider) {
        super(context, container);
        this.baseFragment = baseFragment;
        this.sizeNotifierFrameLayout = container;
        this.resourcesProvider = resourcesProvider;
        this.drawBlur = false;
        this.isTopView = false;
        setVisibility(View.GONE);
        setWillNotDraw(false);

        listViewPadding = (int) Math.min(AndroidUtilities.dp(36 * 3.5f), AndroidUtilities.displaySize.y * 0.22f);

        listView = new MentionsListView(context, resourcesProvider);
        listView.setTranslationY(AndroidUtilities.dp(6));
        linearLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void setReverseLayout(boolean reverseLayout) {
                super.setReverseLayout(reverseLayout);
                listView.setTranslationY((reverseLayout ? -1 : 1) * AndroidUtilities.dp(6));
            }
        };
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        gridLayoutManager = new ExtendedGridLayoutManager(context, 100, false, false) {
            private Size size = new Size();

            @Override
            protected Size getSizeForItem(int i) {
                size.full = false;
                if (i == 0) {
                    size.width = getWidth();
                    size.height = paddedAdapter.getPadding();
                    size.full = true;
                    return size;
                } else {
                    i--;
                }
                if (adapter.getBotContextSwitch() != null || adapter.getBotWebViewSwitch() != null) {
                    i++;
                }
                size.width = 0;
                size.height = 0;
                Object object = adapter.getItem(i);
                if (object instanceof TLRPC.BotInlineResult) {
                    TLRPC.BotInlineResult inlineResult = (TLRPC.BotInlineResult) object;
                    if (inlineResult.document != null) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(inlineResult.document.thumbs, 90);
                        size.width = thumb != null ? thumb.w : 100;
                        size.height = thumb != null ? thumb.h : 100;
                        for (int b = 0; b < inlineResult.document.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.document.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.content != null) {
                        for (int b = 0; b < inlineResult.content.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.content.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.thumb != null) {
                        for (int b = 0; b < inlineResult.thumb.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.thumb.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.photo != null) {
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(inlineResult.photo.sizes, AndroidUtilities.photoSize);
                        if (photoSize != null) {
                            size.width = photoSize.w;
                            size.height = photoSize.h;
                        }
                    }
                }
                return size;
            }

            @Override
            protected int getFlowItemCount() {
                if (adapter.getBotContextSwitch() != null || adapter.getBotWebViewSwitch() != null) {
                    return getItemCount() - 1;
                }
                return super.getFlowItemCount();
            }
        };
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0) {
                    return 100;
                } else {
                    position--;
                }
                Object object = adapter.getItem(position);
                if (object instanceof TLRPC.TL_inlineBotSwitchPM) {
                    return 100;
                } else if (object instanceof TLRPC.Document) {
                    return 20;
                } else {
                    position++;
                    if (adapter.getBotContextSwitch() != null || adapter.getBotWebViewSwitch() != null) {
                        position--;
                    }
                    return gridLayoutManager.getSpanSizeForItem(position);
                }
            }
        });
        DefaultItemAnimator mentionItemAnimator = new DefaultItemAnimator();
        mentionItemAnimator.setAddDuration(150);
        mentionItemAnimator.setMoveDuration(150);
        mentionItemAnimator.setChangeDuration(150);
        mentionItemAnimator.setRemoveDuration(150);
        mentionItemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        mentionItemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(mentionItemAnimator);
        listView.setClipToPadding(false);
        listView.setLayoutManager(linearLayoutManager);

        adapter = new MentionsAdapter(context, false, dialogId, threadMessageId, new MentionsAdapter.MentionsAdapterDelegate() {
            @Override
            public void onItemCountUpdate(int oldCount, int newCount) {
                if (listView.getLayoutManager() != gridLayoutManager && shown) {
                    AndroidUtilities.cancelRunOnUIThread(updateVisibilityRunnable);
                    AndroidUtilities.runOnUIThread(updateVisibilityRunnable, baseFragment.getFragmentBeginToShow() ? 0 : 100);
                }
            }

            @Override
            public void needChangePanelVisibility(boolean show) {
                if (getNeededLayoutManager() != getCurrentLayoutManager() && canOpen()) {
                    if (adapter.getLastItemCount() > 0) {
                        switchLayoutManagerOnEnd = true;
                        updateVisibility(false);
                        return;
                    } else {
                        listView.setLayoutManager(getNeededLayoutManager());
                    }
                }
                if (show && !canOpen()) {
                    show = false;
                }
                if (show && adapter.getItemCountInternal() <= 0) {
                    show = false;
                }
                updateVisibility(show);
            }

            @Override
            public void onContextSearch(boolean searching) {
                MentionsContainerView.this.onContextSearch(searching);
            }

            @Override
            public void onContextClick(TLRPC.BotInlineResult result) {
                MentionsContainerView.this.onContextClick(result);
            }

        }, resourcesProvider, isStories());
        paddedAdapter = new PaddedListAdapter(adapter);
        listView.setAdapter(paddedAdapter);

        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setReversed(false);
    }

    protected boolean canOpen() {
        return true;
    }
    protected void onOpen() {}
    protected void onClose() {}
    protected void onContextSearch(boolean searching) {}
    protected void onContextClick(TLRPC.BotInlineResult result) {}

    private boolean shouldLiftMentions = false;

    public void onPanTransitionStart() {
        shouldLiftMentions = isReversed(); // (getCurrentLayoutManager().getReverseLayout() ? getCurrentLayoutManager().findLastVisibleItemPosition() : getCurrentLayoutManager().findFirstVisibleItemPosition()) > 0;
    }

    public void onPanTransitionUpdate(float translationY) {
        if (shouldLiftMentions) {
            setTranslationY(translationY);
        }
    }

    public void onPanTransitionEnd() {}

    protected void onScrolled(boolean atTop, boolean atBottom) {

    }

    protected void onAnimationScroll() {

    }

    public MentionsListView getListView() {
        return listView;
    }

    public MentionsAdapter getAdapter() {
        return adapter;
    }

    public void setReversed(boolean reversed) {
        if (reversed != isReversed()) {
            scrollToFirst = true;
            linearLayoutManager.setReverseLayout(reversed);
            adapter.setIsReversed(reversed);
        }
    }

    public boolean isReversed() {
        return listView.getLayoutManager() == linearLayoutManager && linearLayoutManager.getReverseLayout();
    }

    public LinearLayoutManager getCurrentLayoutManager() {
        return listView.getLayoutManager() == linearLayoutManager ? linearLayoutManager : gridLayoutManager;
    }

    public LinearLayoutManager getNeededLayoutManager() {
        return (adapter.isStickers() || adapter.isBotContext()) && adapter.isMediaLayout() ? gridLayoutManager : linearLayoutManager;
    }

    private Rect rect = new Rect();
    private Path path;
    private Paint paint;

    public float clipBottom() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        return isReversed() ? 0 : getMeasuredHeight() - containerTop;
    }

    public float clipTop() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        return isReversed() ? containerBottom : 0;
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        boolean reversed = isReversed();
        boolean topPadding = (adapter.isStickers() || adapter.isBotContext()) && adapter.isMediaLayout() && adapter.getBotContextSwitch() == null && adapter.getBotWebViewSwitch() == null;
        containerPadding = AndroidUtilities.dp(2 + (topPadding ? 2 : 0));
        canvas.save();

        float r = AndroidUtilities.dp(6);
        float wasContainerTop = containerTop;
        if (reversed) {
            int paddingViewTop = paddedAdapter.paddingViewAttached ? paddedAdapter.paddingView.getTop() : getHeight();
            float top = Math.max(0, paddingViewTop + listView.getTranslationY()) + containerPadding;
            top = Math.min(top, (1f - hideT) * getHeight());
            rect.set(0, (int) (containerTop = 0), getMeasuredWidth(), (int) (containerBottom = top));
            r = Math.min(r, Math.abs(getMeasuredHeight() - containerBottom));
            if (r > 0) {
                canvas.clipRect(0, 0, getWidth(), getHeight());
                rect.top -= (int) r;
            }
        } else {
            if (listView.getLayoutManager() == gridLayoutManager) {
                containerPadding += AndroidUtilities.dp(2);
                r += AndroidUtilities.dp(2);
            }
            int paddingViewBottom = paddedAdapter.paddingViewAttached ? paddedAdapter.paddingView.getBottom() : 0;
            float top = containerTop = Math.max(0, paddingViewBottom + listView.getTranslationY()) - containerPadding;
            top = Math.max(top, hideT * getHeight());
            rect.set(0, (int) (containerTop = top), getMeasuredWidth(), (int) (containerBottom = getMeasuredHeight()));
            r = Math.min(r, Math.abs(containerTop));
            if (r > 0) {
                canvas.clipRect(0, 0, getWidth(), getHeight());
                rect.bottom += (int) r;
            }
        }
        if (Math.abs(wasContainerTop - containerTop) > 0.1f) {
            onAnimationScroll();
        }

        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShadowLayer(dp(4), 0, 0, 0x1e000000);
        }
        paint.setColor(color != null ? color : getThemedColor(Theme.key_chat_messagePanelBackground));

        if (allowBlur && SharedConfig.chatBlurEnabled() && sizeNotifierFrameLayout != null) {
            if (r > 0) {
                canvas.save();
                if (path == null) {
                    path = new Path();
                } else {
                    path.reset();
                }
                AndroidUtilities.rectTmp.set(rect);
                path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
                canvas.clipPath(path);
            }
            sizeNotifierFrameLayout.drawBlurRect(canvas, getY(), rect, paint, reversed);
            if (r > 0) {
                canvas.restore();
            }
        } else {
            drawRoundRect(canvas, rect, r);
        }
        canvas.clipRect(rect);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public void drawRoundRect(Canvas canvas, Rect rectTmp, float r) {
        AndroidUtilities.rectTmp.set(rectTmp);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);
    }

    private Integer color;
    public void setOverrideColor(int color) {
        this.color = color;
        invalidate();
    }


    private boolean ignoreLayout = false;

    public void setIgnoreLayout(boolean ignore) {
        ignoreLayout = ignore;
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private boolean scrollToFirst = false;
    private boolean shown = false;
    private Runnable updateVisibilityRunnable = () -> {
        updateListViewTranslation(!shown, true);
    };

    public void updateVisibility(boolean show) {
        if (show) {
            boolean reversed = isReversed();
            if (!shown) {
                scrollToFirst = true;
                if (listView.getLayoutManager() == linearLayoutManager) {
                    linearLayoutManager.scrollToPositionWithOffset(0, reversed ? -100000 : 100000);
                }
                if (getVisibility() == View.GONE) {
                    hideT = 1;
                    listView.setTranslationY(reversed ? -(listViewPadding + AndroidUtilities.dp(12)) : (listView.computeVerticalScrollOffset() + listViewPadding));
                }
            }
            setVisibility(View.VISIBLE);
        } else {
            scrollToFirst = false;
        }
        shown = show;
        AndroidUtilities.cancelRunOnUIThread(updateVisibilityRunnable);
        if (listViewTranslationAnimator != null) {
            listViewTranslationAnimator.cancel();
        }
        AndroidUtilities.runOnUIThread(updateVisibilityRunnable, (baseFragment != null && baseFragment.getFragmentBeginToShow()) ? 0 : 100);
        if (show) {
            onOpen();
        } else {
            onClose();
        }
    }

    public boolean isOpen() {
        return shown;
    }

    private SpringAnimation listViewTranslationAnimator;
    private int animationIndex = -1;
    private boolean listViewHiding = false;
    private float hideT = 0;
    private boolean switchLayoutManagerOnEnd = false;
    private int scrollRangeUpdateTries;

    private void updateListViewTranslation(boolean forceZeroHeight, boolean animated) {
        if (listView == null || paddedAdapter == null) {
            scrollRangeUpdateTries = 0;
            return;
        }
        if (listViewHiding && listViewTranslationAnimator != null && listViewTranslationAnimator.isRunning() && forceZeroHeight) {
            scrollRangeUpdateTries = 0;
            return;
        }
        boolean reversed = isReversed();
        float itemHeight;
        if (forceZeroHeight) {
            itemHeight = - containerPadding - AndroidUtilities.dp(6);
        } else {
            int scrollRange = listView.computeVerticalScrollRange();
            itemHeight = scrollRange - paddedAdapter.getPadding() + containerPadding;
            if (scrollRange <= 0 && adapter.getItemCountInternal() > 0 && scrollRangeUpdateTries < 3) {
                scrollRangeUpdateTries++;
                updateVisibility(true);
                return;
            }
        }
        scrollRangeUpdateTries = 0;
        float newTranslationY = (reversed ? -Math.max(0, listViewPadding - itemHeight) : -listViewPadding + Math.max(0, listViewPadding - itemHeight));
        if (forceZeroHeight && !reversed) {
            newTranslationY += listView.computeVerticalScrollOffset();
        }
        Integer updateVisibility = null;
        if (listViewTranslationAnimator != null) {
            listViewTranslationAnimator.cancel();
        }
        if (animated) {
            listViewHiding = forceZeroHeight;
            final float fromTranslation = listView.getTranslationY();
            final float toTranslation = newTranslationY;
            final float fromHideT = hideT;
            final float toHideT = forceZeroHeight ? 1 : 0;
            if (fromTranslation == toTranslation) {
                listViewTranslationAnimator = null;
                updateVisibility = forceZeroHeight ? View.GONE : View.VISIBLE;
                if (switchLayoutManagerOnEnd && forceZeroHeight) {
                    switchLayoutManagerOnEnd = false;
                    listView.setLayoutManager(getNeededLayoutManager());
                    updateVisibility(shown = true);
                }
            } else {
                listViewTranslationAnimator =
                    new SpringAnimation(new FloatValueHolder(fromTranslation))
                        .setSpring(
                            new SpringForce(toTranslation)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                                .setStiffness(550.0f)
                        );
                listViewTranslationAnimator.addUpdateListener((anm, val, vel) -> {
                    listView.setTranslationY(val);
                    onAnimationScroll();
                    hideT = AndroidUtilities.lerp(fromHideT, toHideT, (val - fromTranslation) / (toTranslation - fromTranslation));
                });
                if (forceZeroHeight) {
                    listViewTranslationAnimator.addEndListener((a, cancelled, b, c) -> {
                        if (!cancelled) {
                            listViewTranslationAnimator = null;
                            setVisibility(forceZeroHeight ? View.GONE : View.VISIBLE);
                            if (switchLayoutManagerOnEnd && forceZeroHeight) {
                                switchLayoutManagerOnEnd = false;
                                listView.setLayoutManager(getNeededLayoutManager());
                                updateVisibility(shown = true);
                            }
                        }
                    });
                }
                listViewTranslationAnimator.addEndListener((animation, canceled, value, velocity) -> {
//                    `NotificationCenter.`getInstance(account).onAnimationFinish(animationIndex);
                });
                listViewTranslationAnimator.start();
            }
        } else {
            hideT = forceZeroHeight ? 1 : 0;
            listView.setTranslationY(newTranslationY);
            if (forceZeroHeight) {
                updateVisibility = View.GONE;
//                adapter.clear(true);
            }
        }
        if (updateVisibility != null && getVisibility() != updateVisibility) {
            setVisibility(updateVisibility);
        }
    }

    public void setDialogId(long dialogId) {
        adapter.setDialogId(dialogId);
    }

    private ArrayList<Object> botContextResults;
    private PhotoViewer.PhotoViewerProvider botContextProvider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (index < 0 || index >= botContextResults.size()) {
                return null;
            }
            int count = getListView().getChildCount();
            Object result = botContextResults.get(index);

            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = null;
                View view = getListView().getChildAt(a);
                if (view instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) view;
                    if (cell.getResult() == result) {
                        imageReceiver = cell.getPhotoImage();
                    }
                }

                if (imageReceiver != null) {
                    int[] coords = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = getListView();
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius(true);
                    return object;
                }
            }
            return null;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
            if (index < 0 || index >= botContextResults.size()) {
                return;
            }
            delegate.sendBotInlineResult((TLRPC.BotInlineResult) botContextResults.get(index), notify, scheduleDate);
        }
    };

    public void withDelegate(Delegate delegate) {
        this.delegate = delegate;
        getListView().setOnItemClickListener(mentionsOnItemClickListener = (view, position) -> {
            if (position == 0 || getAdapter().isBannedInline()) {
                return;
            }
            position--;
            Object object = getAdapter().getItem(position);
            int start = getAdapter().getResultStartPosition();
            int len = getAdapter().getResultLength();
            if (getAdapter().isLocalHashtagHint(position)) {
                TLRPC.Chat currentChat = getAdapter().chat;
                if (currentChat == null && getAdapter().parentFragment != null) {
                    currentChat = getAdapter().parentFragment.getCurrentChat();
                }
                delegate.replaceText(start, len, getAdapter().getHashtagHint() + (currentChat != null ? "@" + ChatObject.getPublicUsername(currentChat) : "") + " ", false);
                return;
            } else if (getAdapter().isGlobalHashtagHint(position)) {
                delegate.replaceText(start, len, getAdapter().getHashtagHint() + " ", false);
                return;
            }
            if (object instanceof TLRPC.TL_document) {
                MessageObject.SendAnimationData sendAnimationData = null;
                if (view instanceof StickerCell) {
                    sendAnimationData = ((StickerCell) view).getSendAnimationData();
                }
                TLRPC.TL_document document = (TLRPC.TL_document) object;
                Object parent = getAdapter().getItemParent(position);
                String query = MessageObject.findAnimatedEmojiEmoticon(document);
                delegate.onStickerSelected(document, query, parent);
            } else if (object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                String username = ChatObject.getPublicUsername(chat);
                if (username != null) {
                    delegate.replaceText(start, len, "@" + username + " " , false);
                }
            } else if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;

                if (UserObject.getPublicUsername(user) != null) {
                    delegate.replaceText(start, len, "@" + UserObject.getPublicUsername(user) + " ", false);
                } else {
                    String name = UserObject.getFirstName(user, false);
                    Spannable spannable = new SpannableString(name + " ");
                    spannable.setSpan(new URLSpanUserMention("" + user.id, 3), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    delegate.replaceText(start, len, spannable, false);
                }
            } else if (object instanceof String) {
                delegate.replaceText(start, len, object + " ", false);
            } else if (object instanceof MediaDataController.KeywordResult) {
                String code = ((MediaDataController.KeywordResult) object).emoji;
                delegate.addEmojiToRecent(code);
                if (code != null && code.startsWith("animated_")) {
                    try {
                        Paint.FontMetricsInt fontMetrics = null;
                        try {
                            fontMetrics = delegate.getFontMetrics();
                           // chatActivityEnterView.getEditField().getPaint().getFontMetricsInt();
                        } catch (Exception e) {
                            FileLog.e(e, false);
                        }
                        long documentId = Long.parseLong(code.substring(9));
                        TLRPC.Document document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId);
                        SpannableString emoji = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(document));
                        AnimatedEmojiSpan span;
                        if (document != null) {
                            span = new AnimatedEmojiSpan(document, fontMetrics);
                        } else {
                            span = new AnimatedEmojiSpan(documentId, fontMetrics);
                        }
                        emoji.setSpan(span, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        delegate.replaceText(start, len, emoji, false);
                    } catch (Exception ignore) {
                        delegate.replaceText(start, len, code, true);
                    }
                } else {
                    delegate.replaceText(start, len, code, true);
                }
                updateVisibility(false);
            } if (object instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) object;
                if ((result.type.equals("photo") && (result.photo != null || result.content != null) ||
                        result.type.equals("gif") && (result.document != null || result.content != null) ||
                        result.type.equals("video") && (result.document != null/* || result.content_url != null*/))) {
                    ArrayList<Object> arrayList = botContextResults = new ArrayList<>(getAdapter().getSearchResultBotContext());
                    PhotoViewer.getInstance().setParentActivity(baseFragment, resourcesProvider);
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, getAdapter().getItemPosition(position), 3, false, botContextProvider, null);
                } else {
                    delegate.sendBotInlineResult(result, true, 0);
                }
            }
        });
        getListView().setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, getListView(), 0, mentionsOnItemClickListener, null, resourcesProvider));
    }

    public class MentionsListView extends RecyclerListView {
        private boolean isScrolling, isDragging;

        public MentionsListView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                    isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    int lastVisibleItem;
                    if (getLayoutManager() == gridLayoutManager) {
                        lastVisibleItem = gridLayoutManager.findLastVisibleItemPosition();
                    } else {
                        lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                    }
                    int visibleItemCount = lastVisibleItem == RecyclerView.NO_POSITION ? 0 : lastVisibleItem;
                    if (visibleItemCount > 0 && lastVisibleItem > adapter.getLastItemCount() - 5) {
//                        adapter.loadMoreStickers();
                        adapter.searchForContextBotForNextOffset();
                    }

                    MentionsContainerView.this.onScrolled(!canScrollVertically(-1), !canScrollVertically(1));
                }
            });
            addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    outRect.left = 0;
                    outRect.right = 0;
                    outRect.top = 0;
                    outRect.bottom = 0;
                    if (parent.getLayoutManager() == gridLayoutManager) {
                        int position = parent.getChildAdapterPosition(view);
                        if (position == 0) {
                            return;
                        }
                        if (adapter.isStickers()) {
                            return;
                        } else if (adapter.getBotContextSwitch() != null || adapter.getBotWebViewSwitch() != null) {
                            if (position == 0) {
                                return;
                            }
                            position--;
                            if (!gridLayoutManager.isFirstRow(position)) {
                                outRect.top = AndroidUtilities.dp(2);
                            }
                        } else {
                            outRect.top = AndroidUtilities.dp(2);
                        }
                        outRect.right = gridLayoutManager.isLastInRow(position) ? 0 : AndroidUtilities.dp(2);
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (linearLayoutManager.getReverseLayout()) {
                if (!isDragging && paddedAdapter != null && paddedAdapter.paddingView != null && paddedAdapter.paddingViewAttached && event.getY() > paddedAdapter.paddingView.getTop()) {
                    return false;
                }
            } else {
                if (!isDragging && paddedAdapter != null && paddedAdapter.paddingView != null && paddedAdapter.paddingViewAttached && event.getY() < paddedAdapter.paddingView.getBottom()) {
                    return false;
                }
            }
            boolean result = !isScrolling && ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, null, resourcesProvider);
            if (adapter.isStickers() && event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                adapter.doSomeStickersAction();
            }
            return super.onInterceptTouchEvent(event) || result;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (linearLayoutManager.getReverseLayout()) {
                if (!isDragging && paddedAdapter != null && paddedAdapter.paddingView != null && paddedAdapter.paddingViewAttached && event.getY() > paddedAdapter.paddingView.getTop()) {
                    return false;
                }
            } else {
                if (!isDragging && paddedAdapter != null && paddedAdapter.paddingView != null && paddedAdapter.paddingViewAttached && event.getY() < paddedAdapter.paddingView.getBottom()) {
                    return false;
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        private int lastWidth;
        private int lastHeight;

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int height = b - t;

            int position = -1, offset = 0;
            boolean reversed = isReversed();
            LinearLayoutManager layoutManager = getCurrentLayoutManager();
            position = reversed ? layoutManager.findFirstVisibleItemPosition() : layoutManager.findLastVisibleItemPosition();
            View child = layoutManager.findViewByPosition(position);
            if (child != null) {
                offset = child.getTop() - (reversed ? 0 : lastHeight - height);
            }

            super.onLayout(changed, l, t, r, b);

            if (scrollToFirst) {
                ignoreLayout = true;
                layoutManager.scrollToPositionWithOffset(0, 100000);
                super.onLayout(false, l, t, r, b);
                ignoreLayout = false;
                scrollToFirst = false;
            } else if (position != -1 && width == lastWidth && height - lastHeight != 0) {
                ignoreLayout = true;
                layoutManager.scrollToPositionWithOffset(position, offset, false);
                super.onLayout(false, l, t, r, b);
                ignoreLayout = false;
            }

            lastHeight = height;
            lastWidth = width;
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            MentionsContainerView.this.invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int height = MeasureSpec.getSize(heightSpec);
            if (paddedAdapter != null) {
                paddedAdapter.setPadding(height);
            }
            listViewPadding = (int) Math.min(AndroidUtilities.dp(36 * 3.5f), AndroidUtilities.displaySize.y * 0.22f);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height + (int) listViewPadding, MeasureSpec.EXACTLY));
        }

        @Override
        public void onScrolled(int dx, int dy) {
            super.onScrolled(dx, dy);
            MentionsContainerView.this.invalidate();
        }
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider.getPaint(paintKey);
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public interface Delegate {

        void replaceText(int start, int len, CharSequence replacingString, boolean allowShort);

        Paint.FontMetricsInt getFontMetrics();

        default void onStickerSelected(TLRPC.TL_document document, String query, Object parent) {

        }

        default void addEmojiToRecent(String code) {

        }

        default void sendBotInlineResult(TLRPC.BotInlineResult botInlineResult, boolean notify, int scheduleDate) {}

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            AndroidUtilities.forEachViews(listView, view -> {
                if (view instanceof MentionCell) {
                    ((MentionCell) view).invalidateEmojis();
                } else if (view instanceof QuickRepliesActivity.QuickReplyView) {
                    ((QuickRepliesActivity.QuickReplyView) view).invalidateEmojis();
                } else {
                    view.invalidate();
                }
            });
        }
    }

    protected boolean isStories() {
        return false;
    }

}
