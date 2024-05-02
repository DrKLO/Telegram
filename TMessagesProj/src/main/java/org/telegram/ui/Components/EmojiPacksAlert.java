package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiPacksAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private LongSparseArray<AnimatedEmojiDrawable> animatedEmojiDrawables;

    private BaseFragment fragment;

    private View paddingView;
    private EmojiPacksLoader customEmojiPacks;
    private ContentView contentView;
    private RecyclerListView listView;
    private Adapter adapter;
    private View shadowView;
    private FrameLayout buttonsView;
    private TextView addButtonView;
    private TextView removeButtonView;
    private PremiumButtonView premiumButtonView;
    private GridLayoutManager gridLayoutManager;
    private RecyclerAnimationScrollHelper scrollHelper;

    private CircularProgressDrawable progressDrawable;
    private ActionBarPopupWindow popupWindow;

    private boolean limitCount;

    private boolean hasDescription;
    private float loadT;
    private float lastY;
    private Float fromY;

    int highlightStartPosition = -1, highlightEndPosition = -1;
    private AnimatedFloat highlightAlpha;

    private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public boolean can() {
            return true;
        }

        @Override
        public boolean needSend(int contentType) {
            return fragment instanceof ChatActivity && ((ChatActivity) fragment).canSendMessage() && (UserConfig.getInstance(UserConfig.selectedAccount).isPremium() || ((ChatActivity) fragment).getCurrentUser() != null && UserObject.isUserSelf(((ChatActivity) fragment).getCurrentUser()));
        }

        @Override
        public void sendEmoji(TLRPC.Document emoji) {
            if (fragment instanceof ChatActivity) {
                ((ChatActivity) fragment).sendAnimatedEmoji(emoji, true, 0);
            }
            onCloseByLink();
            dismiss();
        }

        @Override
        public boolean needCopy(TLRPC.Document document) {
            return UserConfig.getInstance(UserConfig.selectedAccount).isPremium() && MessageObject.isAnimatedEmoji(document);
        }

        @Override
        public void copyEmoji(TLRPC.Document document) {
            Spannable spannable = SpannableStringBuilder.valueOf(MessageObject.findAnimatedEmojiEmoticon(document));
            spannable.setSpan(new AnimatedEmojiSpan(document, null), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (AndroidUtilities.addToClipboard(spannable)) {
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyBulletin(LocaleController.getString("EmojiCopied", R.string.EmojiCopied)).show();
            }
        }

        @Override
        public Boolean canSetAsStatus(TLRPC.Document document) {
            if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium() || !MessageObject.isAnimatedEmoji(document)) {
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
            if (document == null) {
                final Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(getContext(), resourcesProvider);
                layout.textView.setText(LocaleController.getString("RemoveStatusInfo", R.string.RemoveStatusInfo));
                layout.imageView.setImageResource(R.drawable.msg_settings_premium);
                Bulletin.UndoButton undoButton = new Bulletin.UndoButton(getContext(), true, resourcesProvider);
                undoButton.setUndoAction(undoAction);
                layout.setButton(undoButton);
                Bulletin.make((FrameLayout) containerView, layout, Bulletin.DURATION_SHORT).show();
            } else {
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createEmojiBulletin(document, LocaleController.getString("SetAsEmojiStatusInfo", R.string.SetAsEmojiStatusInfo), LocaleController.getString("Undo", R.string.Undo), undoAction).show();
            }
        }

        @Override
        public boolean canSchedule() {
            return false;
//            return delegate != null && delegate.canSchedule();
        }

        @Override
        public boolean isInScheduleMode() {
            if (fragment instanceof ChatActivity) {
                return ((ChatActivity) fragment).isInScheduleMode();
            }
            return false;
        }

        @Override
        public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {

        }

        @Override
        public long getDialogId() {

            return 0;
        }
    };

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public EmojiPacksAlert(BaseFragment fragment, Context context, Theme.ResourcesProvider resourceProvider, TLObject parentObject) {
        this(fragment, context, resourceProvider, null, parentObject);
    }

    public EmojiPacksAlert(BaseFragment fragment, Context context, Theme.ResourcesProvider resourceProvider, ArrayList<TLRPC.InputStickerSet> stickerSets) {
        this(fragment, context, resourceProvider, stickerSets, null);
    }

    private EmojiPacksAlert(BaseFragment fragment, Context context, Theme.ResourcesProvider resourceProvider, ArrayList<TLRPC.InputStickerSet> stickerSets, TLObject parentObject) {
        super(context, false, resourceProvider = fragment != null && fragment.getResourceProvider() != null ? fragment.getResourceProvider() : resourceProvider);
        this.fragment = fragment;
        fixNavigationBar();

        if (stickerSets != null) {
            limitCount = stickerSets.size() > 1;
        }

        customEmojiPacks = new EmojiPacksLoader(currentAccount, stickerSets, parentObject) {
            @Override
            protected void onUpdate() {
                updateButton();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            }
        };

        progressDrawable = new CircularProgressDrawable(AndroidUtilities.dp(32), AndroidUtilities.dp(3.5f), getThemedColor(Theme.key_featuredStickers_addButton));

        final ColorFilter colorFilter = new PorterDuffColorFilter(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteLinkText), 178), PorterDuff.Mode.MULTIPLY);
        containerView = contentView = new ContentView(context);

        paddingView = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * (isPortrait ? .56f : .3f)), MeasureSpec.EXACTLY));
            }
        };
        listView = new RecyclerListView(context, resourcesProvider) {

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, previewDelegate, resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                gridLayoutManager.setSpanCount(40);
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
                contentView.updateEmojiDrawables();
                containerView.invalidate();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                contentView.updateEmojiDrawables();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                AnimatedEmojiSpan.release(containerView, animatedEmojiDrawables);
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return false;
            }

            private Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (highlightAlpha != null && highlightStartPosition >= 0 && highlightEndPosition >= 0 && adapter != null && isAttachedToWindow()) {
                    float alpha = highlightAlpha.set(0);

                    if (alpha > 0) {
                        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
                        for (int i = 0; i < getChildCount(); ++i) {
                            View child = getChildAt(i);
                            int position = getChildAdapterPosition(child);

                            if (position != NO_POSITION && position >= highlightStartPosition && position <= highlightEndPosition) {
                                top = Math.min(top, child.getTop() + (int) child.getTranslationY());
                                bottom = Math.max(bottom, child.getBottom() + (int) child.getTranslationY());
                            }
                        }

                        if (top < bottom) {
                            highlightPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_chat_linkSelectBackground), alpha));
                            canvas.drawRect(0, top, getMeasuredWidth(), bottom, highlightPaint);
                        }

                        invalidate();
                    }
                }

                super.dispatchDraw(canvas);
            }
        };
        highlightAlpha = new AnimatedFloat(0, listView, 0, 1250, CubicBezierInterpolator.EASE_IN);
        containerView.setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        containerView.setWillNotDraw(false);
        listView.setWillNotDraw(false);
        listView.setSelectorRadius(AndroidUtilities.dp(6));
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourceProvider));
        listView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(limitCount ? 8 : 68));
        listView.setLayoutManager(gridLayoutManager = new GridLayoutManager(context, 8));
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                if (view instanceof SeparatorView) {
                    outRect.left = -listView.getPaddingLeft();
                    outRect.right = -listView.getPaddingRight();
                } else if (listView.getChildAdapterPosition(view) == 1) {
                    outRect.top = AndroidUtilities.dp(14);
                }
            }
        });
        final Theme.ResourcesProvider finalResourceProvider = resourceProvider;
        RecyclerListView.OnItemClickListener stickersOnItemClickListener;
        listView.setOnItemClickListener(stickersOnItemClickListener = (view, position) -> {
            if (stickerSets == null || stickerSets.size() <= 1) {
                if (popupWindow != null) {
                    popupWindow.dismiss();
                    popupWindow = null;
                    return;
                }
                if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getChatActivityEnterView().getVisibility() == View.VISIBLE && view instanceof EmojiImageView) {
                    final AnimatedEmojiSpan span = ((EmojiImageView) view).span;
                    try {
                        SpannableString text = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(span.document == null ? AnimatedEmojiDrawable.findDocument(currentAccount, span.getDocumentId()) : span.document));
                        text.setSpan(span, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ((Editable) ((ChatActivity) fragment).getChatActivityEnterView().messageEditText.getText()).append(text);
                        onCloseByLink();
                        dismiss();
                    } catch (Exception ignore) {}
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception e) {}
                }
                return;
            }
            if (SystemClock.elapsedRealtime() - premiumButtonClicked < 250) {
                return;
            }
            int i = 0;
            int sz = 0;
            for (int j = 0; i < customEmojiPacks.data.length; ++i) {
                sz = customEmojiPacks.data[i].size();
                if (customEmojiPacks.data.length > 1) {
                    sz = Math.min(gridLayoutManager.getSpanCount() * 2, sz);
                }
                j += 1 + sz + 1;
                if (position < j) {
                    break;
                }
            }
            TLRPC.TL_messages_stickerSet stickerSet = customEmojiPacks.stickerSets == null || i >= customEmojiPacks.stickerSets.size() ? null : customEmojiPacks.stickerSets.get(i);
            if (stickerSet != null && stickerSet.set != null) {
                ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
                TLRPC.TL_inputStickerSetID inputStickerSet = new TLRPC.TL_inputStickerSetID();
                inputStickerSet.id = stickerSet.set.id;
                inputStickerSet.access_hash = stickerSet.set.access_hash;
                inputStickerSets.add(inputStickerSet);
                new EmojiPacksAlert(fragment, getContext(), finalResourceProvider, inputStickerSets) {
                    @Override
                    protected void onCloseByLink() {
                        EmojiPacksAlert.this.dismiss();
                    }
                }.show();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof EmojiImageView) {
                final AnimatedEmojiSpan span = ((EmojiImageView) view).span;
                if (span == null) {
                    return false;
                }

                ActionBarMenuSubItem copyButton = new ActionBarMenuSubItem(getContext(), true, true);
                copyButton.setItemHeight(48);
                copyButton.setPadding(AndroidUtilities.dp(26), 0, AndroidUtilities.dp(26), 0);
                copyButton.setText(LocaleController.getString("Copy", R.string.Copy));
                copyButton.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.4f);
                copyButton.getTextView().setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                copyButton.setOnClickListener(e -> {
                    if (popupWindow == null) {
                        return;
                    }
                    popupWindow.dismiss();
                    popupWindow = null;

                    SpannableString text = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(AnimatedEmojiDrawable.findDocument(currentAccount, span.getDocumentId())));
                    text.setSpan(span, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (AndroidUtilities.addToClipboard(text)) {
                        BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyBulletin(LocaleController.getString("EmojiCopied", R.string.EmojiCopied)).show();
                    }
                });

                LinearLayout layout = new LinearLayout(context);
                Drawable shadowDrawable2 = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
                shadowDrawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
                layout.setBackground(shadowDrawable2);
                layout.addView(copyButton);

                popupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                popupWindow.setClippingEnabled(true);
                popupWindow.setLayoutInScreen(true);
                popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setAnimationStyle(R.style.PopupAnimation);
                int[] loc = new int[2];
                view.getLocationInWindow(loc);
                popupWindow.showAtLocation(view, Gravity.TOP | Gravity.LEFT, loc[0] - AndroidUtilities.dp(49) + view.getMeasuredWidth() / 2, loc[1] - AndroidUtilities.dp(52));

                try {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception e) {}

                return true;
            }
            return false;
        });
        listView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, listView, 0, stickersOnItemClickListener, previewDelegate, resourcesProvider));
        gridLayoutManager.setReverseLayout(false);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (listView.getAdapter() == null || listView.getAdapter().getItemViewType(position) != 1) {
                    return gridLayoutManager.getSpanCount();
                } else {
                    int i = 0;
                    int sz;
                    for (int j = 0; i < customEmojiPacks.data.length; ++i) {
                        sz = customEmojiPacks.data[i].size();
                        if (customEmojiPacks.data.length > 1) {
                            sz = Math.min(gridLayoutManager.getSpanCount() * 2, sz);
                        }
                        j += 1 + sz + 1;
                        if (position < j) {
                            break;
                        }
                    }
                    TLRPC.TL_messages_stickerSet stickerSet = customEmojiPacks.stickerSets == null || i >= customEmojiPacks.stickerSets.size() ? null : customEmojiPacks.stickerSets.get(i);
                    if (stickerSet == null || stickerSet.set == null || stickerSet.set.emojis) {
                        return 5;
                    }
                    return 8;
                }
            }
        });
        scrollHelper = new RecyclerAnimationScrollHelper(listView, gridLayoutManager);

        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        shadowView = new View(context);
        shadowView.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.BOTTOM));
        shadowView.setTranslationY(-AndroidUtilities.dp(68));

        buttonsView = new FrameLayout(context);
        buttonsView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        containerView.addView(buttonsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        addButtonView = new TextView(context);
        addButtonView.setVisibility(View.GONE);
        addButtonView.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), 6));
        addButtonView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        addButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addButtonView.setGravity(Gravity.CENTER);
        buttonsView.addView(addButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 12, 10, 12, 10));

        removeButtonView = new TextView(context);
        removeButtonView.setVisibility(View.GONE);
        removeButtonView.setBackground(Theme.createRadSelectorDrawable(0x0fffffff & getThemedColor(Theme.key_text_RedBold), 0, 0));
        removeButtonView.setTextColor(getThemedColor(Theme.key_text_RedBold));
        removeButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        removeButtonView.setGravity(Gravity.CENTER);
        removeButtonView.setClickable(true);
        buttonsView.addView(removeButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM, 0, 0, 0, 19));

        premiumButtonView = new PremiumButtonView(context, false, resourcesProvider);
        premiumButtonView.setButton(LocaleController.getString("UnlockPremiumEmoji", R.string.UnlockPremiumEmoji), ev -> {
            showPremiumAlert();
        });
        premiumButtonView.setIcon(R.raw.unlock_icon);
        premiumButtonView.buttonLayout.setClickable(true);
        buttonsView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 12, 10, 12, 10));
    }

    @Override
    public void onBackPressed() {
        if (ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().closeWithMenu();
            return;
        }
        super.onBackPressed();
    }

    protected void onButtonClicked(boolean install) {

    }

    private int highlightIndex = -1;
    public void highlight(int setIndex) {
        highlightIndex = setIndex;
    }

    private boolean shown = false;
    private void updateShowButton(boolean show) {
        boolean animated = !shown && show;
        final float removeOffset = (removeButtonView.getVisibility() == View.VISIBLE ? AndroidUtilities.dp(19) : 0);
        if (animated) {
            buttonsView.animate().translationY(show ? removeOffset : AndroidUtilities.dp(16)).alpha(show ? 1 : 0).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            shadowView.animate().translationY(show ? -(AndroidUtilities.dp(68) - removeOffset) : 0).alpha(show ? 1 : 0).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            listView.animate().translationY(limitCount ? 0 : !show ? (AndroidUtilities.dp(68) - removeOffset) : 0).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            buttonsView.setAlpha(show ? 1f : 0);
            buttonsView.setTranslationY(show ? removeOffset : AndroidUtilities.dp(16));
            shadowView.setAlpha(show ? 1f : 0);
            shadowView.setTranslationY(show ? -(AndroidUtilities.dp(68) - removeOffset) : 0);
            listView.setTranslationY(limitCount ? 0 : !show ? (AndroidUtilities.dp(68) - removeOffset) : 0);
        }
        shown = show;
    }

    private class ContentView extends FrameLayout {
        public ContentView(Context context) {
            super(context);
        }

        private Paint paint = new Paint();
        private Path path = new Path();
        private Boolean lastOpen = null;
        boolean attached;
        SparseArray<ArrayList<EmojiImageView>> viewsGroupedByLines = new SparseArray<>();
        ArrayList<DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
        ArrayList<ArrayList<EmojiImageView>> unusedArrays = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();

        private final AnimatedFloat statusBarT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!attached) {
                return;
            }
            paint.setColor(getThemedColor(Theme.key_dialogBackground));
            Theme.applyDefaultShadow(paint);
            path.reset();
            float y = lastY = getListTop();
            float pad = 0;
//            if (fromY != null) {
//                float wasY = y;
//                y = AndroidUtilities.lerp(fromY, y + containerView.getY(), loadT) - containerView.getY();
//                pad = y - wasY;
//            }
            final float statusBarT = this.statusBarT.set(y <= containerView.getPaddingTop());
            y = AndroidUtilities.lerp(y, 0, statusBarT);
            float r = dp((1f - statusBarT) * 14);
            AndroidUtilities.rectTmp.set(getPaddingLeft(), y, getWidth() - getPaddingRight(), getBottom() + r);
            path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.drawPath(path, paint);

            boolean open = statusBarT > .5f;
            if (lastOpen == null || open != lastOpen) {
                updateLightStatusBar(lastOpen = open);
            }

            Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
            Theme.dialogs_onlineCirclePaint.setAlpha((int) (MathUtils.clamp(y / (float) AndroidUtilities.dp(20), 0, 1) * Theme.dialogs_onlineCirclePaint.getAlpha()));
            int w = AndroidUtilities.dp(36);
            y += AndroidUtilities.dp(10);
            AndroidUtilities.rectTmp.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

            shadowView.setVisibility(listView.canScrollVertically(1) || removeButtonView.getVisibility() == View.VISIBLE ? View.VISIBLE : View.INVISIBLE);
            if (listView != null) {
                canvas.save();
                canvas.translate(listView.getLeft(), listView.getY() + pad);
                canvas.clipRect(0, 0, listView.getWidth(), listView.getHeight());
                canvas.saveLayerAlpha(0, 0, listView.getWidth(), listView.getHeight(), (int) (255 * listView.getAlpha()), Canvas.ALL_SAVE_FLAG);

                for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                    ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.valueAt(i);
                    arrayList.clear();
                    unusedArrays.add(arrayList);
                }
                viewsGroupedByLines.clear();
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    if (child instanceof EmojiImageView) {
                        ((EmojiImageView) child).updatePressedProgress();
                        if (animatedEmojiDrawables == null) {
                            continue;
                        }
                        AnimatedEmojiSpan span = ((EmojiImageView) child).span;
                        if (span == null) {
                            continue;
                        }
                        long documentId = span.getDocumentId();
                        AnimatedEmojiDrawable drawable = animatedEmojiDrawables.get(documentId);
                        if (drawable == null) {
                            continue;
                        }
                        drawable.setColorFilter(getAdaptiveEmojiColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText)));
//                            drawable.addView(this);
                        ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.get(child.getTop());
                        if (arrayList == null) {
                            if (!unusedArrays.isEmpty()) {
                                arrayList = unusedArrays.remove(unusedArrays.size() - 1);
                            } else {
                                arrayList = new ArrayList<>();
                            }
                            viewsGroupedByLines.put(child.getTop(), arrayList);
                        }
                        arrayList.add((EmojiImageView) child);
                    } else {
                        canvas.save();
                        canvas.translate(child.getLeft(), child.getTop());
                        child.draw(canvas);
                        canvas.restore();
                    }
                }

                lineDrawablesTmp.clear();
                lineDrawablesTmp.addAll(lineDrawables);
                lineDrawables.clear();

                long time = System.currentTimeMillis();
                for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                    ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.valueAt(i);
                    View firstView = arrayList.get(0);
                    int position = listView.getChildAdapterPosition(firstView);
                    DrawingInBackgroundLine drawable = null;
                    for (int k = 0; k < lineDrawablesTmp.size(); k++) {
                        if (lineDrawablesTmp.get(k).position == position) {
                            drawable = lineDrawablesTmp.get(k);
                            lineDrawablesTmp.remove(k);
                            break;
                        }
                    }
                    if (drawable == null) {
                        if (!unusedLineDrawables.isEmpty()) {
                            drawable = unusedLineDrawables.remove(unusedLineDrawables.size() - 1);
                        } else {
                            drawable = new DrawingInBackgroundLine();
                            drawable.setLayerNum(7);
                        }
                        drawable.position = position;
                        drawable.onAttachToWindow();
                    }
                    lineDrawables.add(drawable);
                    drawable.imageViewEmojis = arrayList;
                    canvas.save();
                    canvas.translate(0, firstView.getY() + firstView.getPaddingTop());
                    drawable.draw(canvas, time, getMeasuredWidth(), firstView.getMeasuredHeight() - firstView.getPaddingBottom(), 1f);
                    canvas.restore();
                }

                for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                    if (unusedLineDrawables.size() < 3) {
                        unusedLineDrawables.add(lineDrawablesTmp.get(i));
                        lineDrawablesTmp.get(i).imageViewEmojis = null;
                        lineDrawablesTmp.get(i).reset();

                    } else {
                        lineDrawablesTmp.get(i).onDetachFromWindow();
                    }
                }
                lineDrawablesTmp.clear();
                canvas.restore();
                canvas.restore();

                if (listView.getAlpha() < 1) {
                    int cx = getWidth() / 2;
                    int cy = ((int) y + getHeight()) / 2;
                    int R = AndroidUtilities.dp(16);
                    progressDrawable.setAlpha((int) (255 * (1f - listView.getAlpha())));
                    progressDrawable.setBounds(cx - R, cy - R, cx + R, cy + R);
                    progressDrawable.draw(canvas);
                    invalidate();
                }
            }
            super.dispatchDraw(canvas);
        }

        private AnimatedEmojiSpan[] getAnimatedEmojiSpans() {
            if (listView == null) {
                return new AnimatedEmojiSpan[0];
            }
            AnimatedEmojiSpan[] spans = new AnimatedEmojiSpan[listView.getChildCount()];
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof EmojiImageView) {
                    spans[i] = ((EmojiImageView) child).span;
                }
            }
            return spans;
        }

        public void updateEmojiDrawables() {
            animatedEmojiDrawables = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, this, getAnimatedEmojiSpans(), animatedEmojiDrawables);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < getListTop() - AndroidUtilities.dp(6)) {
                dismiss();
            }
            return super.dispatchTouchEvent(event);
        }

        class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {
            public int position;
            ArrayList<EmojiImageView> imageViewEmojis;
            ArrayList<EmojiImageView> drawInBackgroundViews = new ArrayList<>();


            @Override
            public void prepareDraw(long time) {
                drawInBackgroundViews.clear();
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    EmojiImageView imageView = imageViewEmojis.get(i);
                    AnimatedEmojiSpan span = imageView.span;
                    if (span == null) {
                        continue;
                    }
                    AnimatedEmojiDrawable drawable = animatedEmojiDrawables.get(imageView.span.getDocumentId());
                    if (drawable == null || drawable.getImageReceiver() == null) {
                        continue;
                    }

                    drawable.update(time);
                    imageView.backgroundThreadDrawHolder[threadIndex] = drawable.getImageReceiver().setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder[threadIndex], threadIndex);
                    imageView.backgroundThreadDrawHolder[threadIndex].time = time;
                    drawable.setAlpha(255);
                    AndroidUtilities.rectTmp2.set(imageView.getLeft() + imageView.getPaddingLeft(),  imageView.getPaddingTop(), imageView.getRight() - imageView.getPaddingRight(), imageView.getMeasuredHeight() - imageView.getPaddingBottom());
                    imageView.backgroundThreadDrawHolder[threadIndex].setBounds(AndroidUtilities.rectTmp2);
                    drawable.setColorFilter(getAdaptiveEmojiColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText)));
                    imageView.imageReceiver = drawable.getImageReceiver();;
                    drawInBackgroundViews.add(imageView);
                }
            }

            @Override
            public void draw(Canvas canvas, long time, int w, int h, float alpha) {
                if (imageViewEmojis == null) {
                    return;
                }
                boolean drawInUi = imageViewEmojis.size() <= 3 || SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW;
                if (!drawInUi) {
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        EmojiImageView img = imageViewEmojis.get(i);
                        if (img.pressedProgress != 0 || img.backAnimator != null || img.getTranslationX() != 0 || img.getTranslationY() != 0 || img.getAlpha() != 1) {
                            drawInUi = true;
                            break;
                        }
                    }
                }
                if (drawInUi) {
                    prepareDraw(System.currentTimeMillis());
                    drawInUiThread(canvas, alpha);
                    reset();
                } else {
                    super.draw(canvas, time, w, h, alpha);
                }
            }

            @Override
            public void drawInBackground(Canvas canvas) {
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    EmojiImageView imageView = drawInBackgroundViews.get(i);
                    imageView.imageReceiver.draw(canvas, imageView.backgroundThreadDrawHolder[threadIndex]);
                }
            }

            @Override
            protected void drawInUiThread(Canvas canvas, float alpha) {
                if (imageViewEmojis != null) {
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        EmojiImageView imageView = imageViewEmojis.get(i);
                        AnimatedEmojiSpan span = imageView.span;
                        if (span == null) {
                            continue;
                        }
                        AnimatedEmojiDrawable drawable = animatedEmojiDrawables.get(imageView.span.getDocumentId());
                        if (drawable == null || drawable.getImageReceiver() == null) {
                            continue;
                        }
                        if (imageView.imageReceiver != null) {
                            drawable.setAlpha((int) (255 * alpha * imageView.getAlpha()));
                            float hw = (imageView.getWidth() - imageView.getPaddingLeft() - imageView.getPaddingRight()) / 2f;
                            float hh = (imageView.getHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom()) / 2f;
                            float cx = (imageView.getLeft() + imageView.getRight()) / 2f;
                            float cy = imageView.getPaddingTop() + hh;
                            float scale = 1f;
                            if (imageView.pressedProgress != 0) {
                                scale *= 0.8f + 0.2f * (1f - imageView.pressedProgress);
                            }
                            drawable.setBounds(
                                    (int) (cx - hw * imageView.getScaleX() * scale),
                                    (int) (cy - hh * imageView.getScaleY() * scale),
                                    (int) (cx + hw * imageView.getScaleX() * scale),
                                    (int) (cy + hh * imageView.getScaleY() * scale)
                            );
                            drawable.draw(canvas);
                        }
                    }
                }
            }

            @Override
            public void onFrameReady() {
                super.onFrameReady();
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    EmojiImageView imageView = drawInBackgroundViews.get(i);
                    imageView.backgroundThreadDrawHolder[threadIndex].release();
                }
                containerView.invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            for (int i = 0; i < lineDrawables.size(); i++) {
                lineDrawables.get(i).onDetachFromWindow();
            }
            for (int i = 0; i < unusedLineDrawables.size(); i++) {
                unusedLineDrawables.get(i).onDetachFromWindow();
            }
            lineDrawables.clear();
            AnimatedEmojiSpan.release(this, animatedEmojiDrawables);
        }
    }

    protected void onCloseByLink() {

    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            updateInstallment();
        }
    }

    private long premiumButtonClicked;
    public void showPremiumAlert() {
        if (fragment != null) {
            new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
        } else if (getContext() instanceof LaunchActivity) {
            ((LaunchActivity) getContext()).presentFragment(new PremiumPreviewFragment(null));
        }
    }

    private void updateLightStatusBar(boolean open) {
        boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
        boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
        boolean isLight = open ? openBgLight : closedBgLight;
        AndroidUtilities.setLightStatusBar(getWindow(), isLight);
    }

    public void updateInstallment() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof EmojiPackHeader) {
                EmojiPackHeader header = (EmojiPackHeader) child;
                if (header.set != null && header.set.set != null) {
                    header.toggle(MediaDataController.getInstance(currentAccount).isStickerPackInstalled(header.set.set.id), true);
                }
            }
        }
        updateButton();
    }

    public static void installSet(BaseFragment fragment, TLObject set, boolean showBulletIn) {
        installSet(fragment, set, showBulletIn, null, null);
    }
    public static void installSet(BaseFragment fragment, TLObject obj, boolean showBulletIn, Utilities.Callback<Boolean> onDone, Runnable onStickersLoaded) {
        int currentAccount = fragment == null ? UserConfig.selectedAccount : fragment.getCurrentAccount();
        View fragmentView = fragment == null ? null : fragment.getFragmentView();
        if (obj == null) {
            return;
        }
        TLRPC.TL_messages_stickerSet stickerSet = obj instanceof TLRPC.TL_messages_stickerSet ? (TLRPC.TL_messages_stickerSet) obj : null;
        TLRPC.StickerSet set = stickerSet != null ? stickerSet.set : obj instanceof TLRPC.StickerSet ? (TLRPC.StickerSet) obj : null;
        if (set == null) {
            return;
        }

        if (MediaDataController.getInstance(currentAccount).cancelRemovingStickerSet(set.id)) {
            if (onDone != null) {
                onDone.run(true);
            }
            return;
        }
        TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetID();
        req.stickerset.id = set.id;
        req.stickerset.access_hash = set.access_hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            int type = MediaDataController.TYPE_IMAGE;
            if (set.masks) {
                type = MediaDataController.TYPE_MASK;
            } else if (set.emojis) {
                type = MediaDataController.TYPE_EMOJIPACKS;
            }
            try {
                if (error == null) {
                    if (showBulletIn && fragmentView != null) {
                        Bulletin.make(fragment, new StickerSetBulletinLayout(fragment.getFragmentView().getContext(), stickerSet == null ? set : stickerSet, StickerSetBulletinLayout.TYPE_ADDED, null, fragment.getResourceProvider()), Bulletin.DURATION_SHORT).show();
                    }
                    if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                        MediaDataController.getInstance(currentAccount).processStickerSetInstallResultArchive(fragment, true, type, (TLRPC.TL_messages_stickerSetInstallResultArchive) response);
                    }
                    if (onDone != null) {
                        onDone.run(true);
                    }
                } else if (fragmentView != null) {
                    Toast.makeText(fragment.getFragmentView().getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                    if (onDone != null) {
                        onDone.run(false);
                    }
                } else {
                    if (onDone != null) {
                        onDone.run(false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            MediaDataController.getInstance(currentAccount).loadStickers(type, false, true, false, p -> {
                if (onStickersLoaded != null) {
                    onStickersLoaded.run();
                }
            });
        }));
    }

    public static void uninstallSet(BaseFragment fragment, TLRPC.TL_messages_stickerSet set, boolean showBulletin, Runnable onUndo, boolean forget) {
        if (fragment == null || set == null || fragment.getFragmentView() == null) {
            return;
        }
        MediaDataController.getInstance(fragment.getCurrentAccount()).toggleStickerSet(fragment.getFragmentView().getContext(), set, 0, fragment, true, showBulletin, onUndo, forget);
    }

    public static void uninstallSet(Context context, TLRPC.TL_messages_stickerSet set, boolean showBulletin, Runnable onUndo) {
        if (set == null) {
            return;
        }
        MediaDataController.getInstance(UserConfig.selectedAccount).toggleStickerSet(context, set, 0, null, true, showBulletin, onUndo, true);
    }

    private ValueAnimator loadAnimator;
    private void loadAnimation() {
        if (loadAnimator != null) {
            return;
        }
        loadAnimator = ValueAnimator.ofFloat(loadT, 1);
        fromY = lastY + containerView.getY();
        loadAnimator.addUpdateListener(a -> {
            loadT = (float) a.getAnimatedValue();
            listView.setAlpha(loadT);
            addButtonView.setAlpha(loadT);
            removeButtonView.setAlpha(loadT);
            containerView.invalidate();
        });
        loadAnimator.setDuration(250);
        loadAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        loadAnimator.start();
    }

    boolean loaded = true;
    private void updateButton() {
        if (buttonsView == null) {
            return;
        }

        ArrayList<TLRPC.TL_messages_stickerSet> allPacks = customEmojiPacks.stickerSets == null ? new ArrayList<>() : new ArrayList<>(customEmojiPacks.stickerSets);
        for (int i = 0; i < allPacks.size(); ++i) {
            if (allPacks.get(i) == null) {
                allPacks.remove(i--);
            }
        }
        MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        ArrayList<TLRPC.TL_messages_stickerSet> installedPacks = new ArrayList<>();
        ArrayList<TLRPC.TL_messages_stickerSet> notInstalledPacks = new ArrayList<>();
        for (int i = 0; i < allPacks.size(); ++i) {
            TLRPC.TL_messages_stickerSet stickerSet = allPacks.get(i);
            if (stickerSet != null && stickerSet.set != null) {
                if (!mediaDataController.isStickerPackInstalled(stickerSet.set.id)) {
                    notInstalledPacks.add(stickerSet);
                } else {
                    installedPacks.add(stickerSet);
                }
            }
        }

//        boolean mePremium = UserConfig.getInstance(currentAccount).isPremium();
        ArrayList<TLRPC.TL_messages_stickerSet> canInstallPacks = new ArrayList<>(notInstalledPacks);
//        for (int i = 0; i < canInstallPacks.size(); ++i) {
//            if (MessageObject.isPremiumEmojiPack(canInstallPacks.get(i)) && !mePremium) {
//                canInstallPacks.remove(i--);
//            }
//        }

        boolean loadedNow = customEmojiPacks.inputStickerSets != null && allPacks.size() == customEmojiPacks.inputStickerSets.size();
        if (!loaded && loadedNow) {
            loadAnimation();
        }
        loaded = loadedNow;
        if (!loaded) {
            listView.setAlpha(0);
        } else if (highlightIndex >= 0) {
            int currentPosition = gridLayoutManager.findFirstVisibleItemPosition();
            int position = adapter.getSetHeaderPosition(highlightIndex);
            if (Math.abs(currentPosition - position) > (1 + 16 + 1) * 3) {
                scrollHelper.setScrollDirection(currentPosition < position ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
                scrollHelper.scrollToPosition(position, AndroidUtilities.displaySize.y / 2 - AndroidUtilities.dp(170), false, true);
            } else {
                listView.smoothScrollToPosition(position);
            }
            highlightStartPosition = adapter.getSetHeaderPosition(highlightIndex);
            highlightEndPosition = adapter.getSetEndPosition(highlightIndex);
            highlightAlpha.set(1, true);
            listView.invalidate();
            highlightIndex = -1;
        }

        if (!loaded || limitCount) {
            premiumButtonView.setVisibility(View.GONE);
            addButtonView.setVisibility(View.GONE);
            removeButtonView.setVisibility(View.GONE);
            updateShowButton(false);
//        } else if (canInstallPacks.size() <= 0 && notInstalledPacks.size() >= 0 && !mePremium || !loaded) {
//            premiumButtonView.setVisibility(View.VISIBLE);
//            addButtonView.setVisibility(View.GONE);
//            removeButtonView.setVisibility(View.GONE);
//            updateShowButton(true);
        } else {
            premiumButtonView.setVisibility(View.INVISIBLE);
            if (canInstallPacks.size() > 0) {
                addButtonView.setVisibility(View.VISIBLE);
                removeButtonView.setVisibility(View.GONE);
                if (canInstallPacks.size() == 1) {
                    addButtonView.setText(LocaleController.formatPluralString("AddManyEmojiCount", canInstallPacks.get(0).documents.size()));
                } else {
                    addButtonView.setText(LocaleController.formatPluralString("AddManyEmojiPacksCount", canInstallPacks.size()));
                }
                addButtonView.setOnClickListener(ev -> {
                    final int count = canInstallPacks.size();
                    final int[] status = new int[2];
                    for (int i = 0; i < canInstallPacks.size(); ++i) {
                        installSet(fragment, canInstallPacks.get(i), count == 1, count > 1 ?
                            result -> {
                                status[0]++;
                                if (result) {
                                    status[1]++;
                                }
                                if (status[0] == count && status[1] > 0) {
                                    dismiss();
                                    Bulletin.make(fragment, new StickerSetBulletinLayout(fragment.getFragmentView().getContext(), canInstallPacks.get(0), status[1], StickerSetBulletinLayout.TYPE_ADDED,null, fragment.getResourceProvider()), Bulletin.DURATION_SHORT).show();
                                }
                            } : null,
                            null
                        );
                    }
                    onButtonClicked(true);
                    if (count <= 1) {
                        dismiss();
                    }
                });
                updateShowButton(true);
            } else if (installedPacks.size() > 0) {
                addButtonView.setVisibility(View.GONE);
                removeButtonView.setVisibility(View.VISIBLE);
                if (installedPacks.size() == 1) {
                    removeButtonView.setText(LocaleController.formatPluralString("RemoveManyEmojiCount", installedPacks.get(0).documents.size()));
                } else {
                    removeButtonView.setText(LocaleController.formatPluralString("RemoveManyEmojiPacksCount", installedPacks.size()));
                }

                removeButtonView.setOnClickListener(ev -> {
                    dismiss();
                    if (fragment != null) {
                        MediaDataController.getInstance(fragment.getCurrentAccount()).removeMultipleStickerSets(fragment.getContext(), fragment, installedPacks);
                    } else {
                        for (int i = 0; i < installedPacks.size(); ++i) {
                            TLRPC.TL_messages_stickerSet stickerSet = installedPacks.get(i);
                            uninstallSet(getContext(), stickerSet, i == 0, null);
                        }
                    }
                    onButtonClicked(false);
                });
                updateShowButton(true);
            } else {
                addButtonView.setVisibility(View.GONE);
                removeButtonView.setVisibility(View.GONE);
                updateShowButton(false);
            }
        }
    }

    private int getListTop() {
        if (containerView == null) {
            return 0;
        }
        if (listView == null || listView.getChildCount() < 1) {
            return containerView.getPaddingTop();
        }
        View view = listView.getChildAt(0);
        if (view != paddingView) {
            return containerView.getPaddingTop();
        }
        return paddingView.getBottom() + (int) listView.getY();
    }

    @Override
    public void show() {
        super.show();
        listView.setAdapter(adapter = new Adapter());

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);

        customEmojiPacks.start();
        updateButton();
        int currentAccount = fragment == null ? UserConfig.selectedAccount : fragment.getCurrentAccount();
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (customEmojiPacks != null) {
            customEmojiPacks.recycle();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
    }

    @Override
    public int getContainerViewHeight() {
        return (listView == null ? 0 : listView.getMeasuredHeight()) - getListTop() + (containerView == null ? 0 : containerView.getPaddingTop()) + AndroidUtilities.navigationBarHeight + AndroidUtilities.dp(8);
    }

    class SeparatorView extends View {
        public SeparatorView(Context context) {
            super(context);
            setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight());
            params.topMargin = AndroidUtilities.dp(14);
            setLayoutParams(params);
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final int VIEW_TYPE_PADDING = 0;
        private final int VIEW_TYPE_EMOJI = 1;
        private final int VIEW_TYPE_HEADER = 2;
        private final int VIEW_TYPE_TEXT = 3;
        private final int VIEW_TYPE_SEPARATOR = 4;

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == VIEW_TYPE_PADDING) {
                view = paddingView;
            } else if (viewType == VIEW_TYPE_EMOJI) {
                view = new EmojiImageView(getContext());
            } else if (viewType == VIEW_TYPE_HEADER) {
                view = new EmojiPackHeader(getContext(), customEmojiPacks.data.length <= 1);
            } else if (viewType == VIEW_TYPE_TEXT) {
                view = new TextView(getContext());
            } else if (viewType == VIEW_TYPE_SEPARATOR) {
                view = new SeparatorView(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            position--;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_TEXT:
                    TextView textView = (TextView) holder.itemView;
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
                    textView.setText(AndroidUtilities.replaceTags(LocaleController.getString("PremiumPreviewEmojiPack", R.string.PremiumPreviewEmojiPack)));
                    textView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(30), AndroidUtilities.dp(14));
                    break;
                case VIEW_TYPE_EMOJI:
                    if (hasDescription) {
                        position--;
                    }
                    EmojiImageView view = (EmojiImageView) holder.itemView;
                    EmojiView.CustomEmoji customEmoji = null;
                    for (int i = 0, j = 0; i < customEmojiPacks.data.length; ++i) {
                        int size = customEmojiPacks.data[i].size();
                        if (customEmojiPacks.data.length > 1) {
                            size = Math.min(gridLayoutManager.getSpanCount() * 2, size);
                        }
                        if (position > j && position <= j + size) {
                            customEmoji = customEmojiPacks.data[i].get(position - j - 1);
                            break;
                        }
                        j += 1 + size + 1;
                    }
                    if (view.span == null && customEmoji != null || customEmoji == null && view.span != null || customEmoji != null && view.span.documentId != customEmoji.documentId) {
                        if (customEmoji == null) {
                            view.span = null;
                        } else {
                            TLRPC.TL_inputStickerSetID inputStickerSet = new TLRPC.TL_inputStickerSetID();
                            inputStickerSet.id = customEmoji.stickerSet.set.id;
                            inputStickerSet.short_name = customEmoji.stickerSet.set.short_name;
                            inputStickerSet.access_hash = customEmoji.stickerSet.set.access_hash;
                            TLRPC.Document document = customEmoji.getDocument();
                            if (document != null) {
                                view.span = new AnimatedEmojiSpan(document, null);
                            } else {
                                view.span = new AnimatedEmojiSpan(customEmoji.documentId, null);
                            }
                        }
                    }
                    break;
                case VIEW_TYPE_HEADER:
                    if (hasDescription && position > 0) {
                        position--;
                    }
                    int i = 0;
                    for (int j = 0; i < customEmojiPacks.data.length; ++i) {
                        int sz = customEmojiPacks.data[i].size();
                        if (customEmojiPacks.data.length > 1) {
                            sz = Math.min(gridLayoutManager.getSpanCount() * 2, sz);
                        }
                        if (position == j) {
                            break;
                        }
                        j += 1 + sz + 1;
                    }
                    TLRPC.TL_messages_stickerSet stickerSet = customEmojiPacks.stickerSets == null || i >= customEmojiPacks.stickerSets.size() ? null : customEmojiPacks.stickerSets.get(i);
                    boolean premium = false;
                    if (stickerSet != null && stickerSet.documents != null) {
                        for (int j = 0; j < stickerSet.documents.size(); ++j) {
                            if (!MessageObject.isFreeEmoji(stickerSet.documents.get(j))) {
                                premium = true;
                                break;
                            }
                        }
                    }
                    if (i < customEmojiPacks.data.length) {
                        ((EmojiPackHeader) holder.itemView).set(stickerSet, stickerSet == null || stickerSet.documents == null ? 0 : stickerSet.documents.size(), premium);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_PADDING;
            }
            position--;
            if (hasDescription) {
                if (position == 1) {
                    return VIEW_TYPE_TEXT;
                } else if (position > 0) {
                    position--;
                }
            }
            for (int i = 0, j = 0; i < customEmojiPacks.data.length; ++i) {
                if (position == j) {
                    return VIEW_TYPE_HEADER;
                }
                int count = customEmojiPacks.data[i].size();
                if (customEmojiPacks.data.length > 1) {
                    count = Math.min(gridLayoutManager.getSpanCount() * 2, count);
                }
                j += 1 + count;
                if (position == j) {
                    return VIEW_TYPE_SEPARATOR;
                }
                j++;
            }
            return VIEW_TYPE_EMOJI;
        }

        public int getSetHeaderPosition(int setIndex) {
            int position = 1;
            if (hasDescription) {
                position++;
            }
            for (int i = 0; i < customEmojiPacks.data.length; ++i) {
                if (i == setIndex) {
                    return position;
                }
                int count = customEmojiPacks.data[i].size();
                if (customEmojiPacks.data.length > 1) {
                    count = Math.min(gridLayoutManager.getSpanCount() * 2, count);
                }
                position += 1 + count + 1;
            }
            return position;
        }

        public int getSetEndPosition(int setIndex) {
            int position = 1;
            if (hasDescription) {
                position++;
            }
            for (int i = 0; i < customEmojiPacks.data.length; ++i) {
                int count = customEmojiPacks.data[i].size();
                if (customEmojiPacks.data.length > 1) {
                    count = Math.min(gridLayoutManager.getSpanCount() * 2, count);
                }
                if (i == setIndex) {
                    return position + count + 1;
                }
                position += 1 + count + 1;
            }
            return position;
        }

        @Override
        public int getItemCount() {
            hasDescription = !UserConfig.getInstance(currentAccount).isPremium() && customEmojiPacks.stickerSets != null && customEmojiPacks.stickerSets.size() == 1 && MessageObject.isPremiumEmojiPack(customEmojiPacks.stickerSets.get(0));
            return 1 + (hasDescription ? 1 : 0) + customEmojiPacks.getItemsCount() + Math.max(0, customEmojiPacks.data.length - 1);
        }
    }

    private void onSubItemClick(int id) {
        if (customEmojiPacks == null || customEmojiPacks.stickerSets == null || customEmojiPacks.stickerSets.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_stickerSet stickerSet = customEmojiPacks.stickerSets.get(0);
        String stickersUrl;
        if (stickerSet.set != null && stickerSet.set.emojis) {
            stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addemoji/" + stickerSet.set.short_name;
        } else {
            stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/" + stickerSet.set.short_name;
        }
        if (id == 1) {
            Context context = null;
            if (fragment != null) {
                context = fragment.getParentActivity();
            }
            if (context == null) {
                context = getContext();
            }
            ShareAlert alert = new ShareAlert(context, null, stickersUrl, false, stickersUrl, false, resourcesProvider) {
                @Override
                protected void onSend(androidx.collection.LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic) {
                    AndroidUtilities.runOnUIThread(() -> {
                        UndoView undoView;
                        if (fragment instanceof ChatActivity) {
                            undoView = ((ChatActivity) fragment).getUndoView();
                        } else if (fragment instanceof ProfileActivity) {
                            undoView = ((ProfileActivity) fragment).getUndoView();
                        } else {
                            undoView = null;
                        }
                        if (undoView != null) {
                            if (dids.size() == 1) {
                                undoView.showWithAction(dids.valueAt(0).id, UndoView.ACTION_FWD_MESSAGES, count);
                            } else {
                                undoView.showWithAction(0, UndoView.ACTION_FWD_MESSAGES, count, dids.size(), null, null);
                            }
                        }
                    }, 100);
                }
            };
            if (fragment != null) {
                fragment.showDialog(alert);
            } else {
                alert.show();
            }
        } else if (id == 2) {
            try {
                AndroidUtilities.addToClipboard(stickersUrl);
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyLinkBulletin().show();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static class EmojiImageView extends View {

        public ImageReceiver.BackgroundThreadDrawHolder[] backgroundThreadDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        public ImageReceiver imageReceiver;

        public EmojiImageView(Context context) {
            super(context);
        }

        public AnimatedEmojiSpan span;

        public TLRPC.Document getDocument() {
            TLRPC.Document document = null;
            if (span != null) {
                document = span.document;
                if (document == null) {
                    long documentId = span.getDocumentId();
                    document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId);
                }
            }
            return document;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY));
        }

        ValueAnimator backAnimator;
        private float pressedProgress;
        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        if (getParent() instanceof View) {
                            ((View) getParent()).invalidate();
                        }
//                        containerView.invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        public void updatePressedProgress() {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress = Utilities.clamp(pressedProgress + 16f / 100f, 1f, 0);
                invalidate();
            }
        }
    }

    private class EmojiPackHeader extends FrameLayout {

        public LinkSpanDrawable.LinksTextView titleView;
        public TextView subtitleView;
        public TextView addButtonView;
        public TextView removeButtonView;
        public PremiumButtonView unlockButtonView;
        public ActionBarMenuItem optionsButton;

        private boolean single;

        public BaseFragment dummyFragment = new BaseFragment() {
            @Override
            public int getCurrentAccount() {
                return currentAccount;
            }

            @Override
            public View getFragmentView() {
                return EmojiPacksAlert.this.containerView;
            }

            @Override
            public FrameLayout getLayoutContainer() {
                return (FrameLayout) EmojiPacksAlert.this.containerView;
            }

            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return resourcesProvider;
            }
        };

        public EmojiPackHeader(Context context, boolean single) {
            super(context);

            this.single = single;

            float endMarginDp = 8;
            if (!single) {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    unlockButtonView = new PremiumButtonView(context, AndroidUtilities.dp(4), false, resourcesProvider);
                    unlockButtonView.setButton(LocaleController.getString("Unlock", R.string.Unlock), ev -> {
                        premiumButtonClicked = SystemClock.elapsedRealtime();
                        showPremiumAlert();
                    });
                    unlockButtonView.setIcon(R.raw.unlock_icon);

                    MarginLayoutParams iconLayout = (MarginLayoutParams) unlockButtonView.getIconView().getLayoutParams();
                    iconLayout.leftMargin = AndroidUtilities.dp(1);
                    iconLayout.topMargin = AndroidUtilities.dp(1);
                    iconLayout.width = iconLayout.height = AndroidUtilities.dp(20);
                    MarginLayoutParams layout = (MarginLayoutParams) unlockButtonView.getTextView().getLayoutParams();
                    layout.leftMargin = AndroidUtilities.dp(3);
                    unlockButtonView.getChildAt(0).setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);

                    addView(unlockButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.END | Gravity.TOP, 0, 15.66f, 5.66f, 0));

                    unlockButtonView.measure(MeasureSpec.makeMeasureSpec(99999, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY));
                    endMarginDp = (unlockButtonView.getMeasuredWidth() + AndroidUtilities.dp(8 + 8)) / AndroidUtilities.density;
                }

                addButtonView = new TextView(context);
                addButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                addButtonView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
                addButtonView.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), 4));
                addButtonView.setText(LocaleController.getString("Add", R.string.Add));
                addButtonView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
                addButtonView.setGravity(Gravity.CENTER);
                addButtonView.setOnClickListener(e -> {
                    installSet(dummyFragment, set, true);
                    toggle(true, true);
                });
                addView(addButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.END | Gravity.TOP, 0, 15.66f, 5.66f, 0));

                addButtonView.measure(MeasureSpec.makeMeasureSpec(99999, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY));
                endMarginDp = Math.max(endMarginDp, (addButtonView.getMeasuredWidth() + AndroidUtilities.dp(8 + 8)) / AndroidUtilities.density);

                removeButtonView = new TextView(context);
                removeButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                removeButtonView.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
                removeButtonView.setBackground(Theme.createRadSelectorDrawable(0x0fffffff & getThemedColor(Theme.key_featuredStickers_addButton), 4, 4));
                removeButtonView.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
                removeButtonView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                removeButtonView.setGravity(Gravity.CENTER);
                removeButtonView.setOnClickListener(e -> {
                    uninstallSet(dummyFragment, set, true, () -> {
                        toggle(true, true);
                    }, true);
                    toggle(false, true);
                });
                removeButtonView.setClickable(false);
                addView(removeButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.END | Gravity.TOP, 0, 15.66f, 5.66f, 0));

                removeButtonView.setScaleX(0);
                removeButtonView.setScaleY(0);
                removeButtonView.setAlpha(0);

                removeButtonView.measure(MeasureSpec.makeMeasureSpec(99999, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY));
                endMarginDp = Math.max(endMarginDp, (removeButtonView.getMeasuredWidth() + AndroidUtilities.dp(8 + 8)) / AndroidUtilities.density);
            } else {
                endMarginDp = 32;
            }

            titleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            titleView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), 0);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setSingleLine(true);
            titleView.setLines(1);
            titleView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            if (single) {
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 12, 11, endMarginDp, 0));
            } else {
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 6, 10, endMarginDp, 0));
            }

            if (!single) {
                subtitleView = new TextView(context);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
                subtitleView.setEllipsize(TextUtils.TruncateAt.END);
                subtitleView.setSingleLine(true);
                subtitleView.setLines(1);
                addView(subtitleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 8, 31.66f, endMarginDp, 0));
            }

            if (single) {
                optionsButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_sheet_other), resourcesProvider);
                optionsButton.setLongClickEnabled(false);
                optionsButton.setSubMenuOpenSide(2);
                optionsButton.setIcon(R.drawable.ic_ab_other);
                optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_player_actionBarSelector), 1));
                addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 5, 5 - backgroundPaddingLeft / AndroidUtilities.density, 0));
                optionsButton.addSubItem(1, R.drawable.msg_share, LocaleController.getString("StickersShare", R.string.StickersShare));
                optionsButton.addSubItem(2, R.drawable.msg_link, LocaleController.getString("CopyLink", R.string.CopyLink));
                optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
                optionsButton.setDelegate(EmojiPacksAlert.this::onSubItemClick);
                optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            }
        }

        private TLRPC.TL_messages_stickerSet set;
        private boolean toggled = false;
        private float toggleT = 0;
        private ValueAnimator animator;
        private void toggle(boolean enable, boolean animated) {
            if (toggled == enable) {
                return;
            }
            toggled = enable;

            if (animator != null) {
                animator.cancel();
                animator = null;
            }

            if (addButtonView == null || removeButtonView == null) {
                return;
            }

            addButtonView.setClickable(!enable);
            removeButtonView.setClickable(enable);

            if (animated) {
                animator = ValueAnimator.ofFloat(toggleT, enable ? 1f : 0f); // 1 - remove button, 0 - add button
                animator.addUpdateListener(a -> {
                    toggleT = (float) a.getAnimatedValue();
                    addButtonView.setScaleX(1f - toggleT);
                    addButtonView.setScaleY(1f - toggleT);
                    addButtonView.setAlpha(1f - toggleT);
                    removeButtonView.setScaleX(toggleT);
                    removeButtonView.setScaleY(toggleT);
                    removeButtonView.setAlpha(toggleT);
                });
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.setDuration(250);
                animator.start();
            } else {
                toggleT = enable ? 1 : 0;
                addButtonView.setScaleX(enable ? 0 : 1);
                addButtonView.setScaleY(enable ? 0 : 1);
                addButtonView.setAlpha(enable ? 0 : 1);
                removeButtonView.setScaleX(enable ? 1 : 0);
                removeButtonView.setScaleY(enable ? 1 : 0);
                removeButtonView.setAlpha(enable ? 1 : 0);
            }
        }

        public void set(TLRPC.TL_messages_stickerSet set, int size, boolean premium) {
            this.set = set;

            if (set != null && set.set != null) {
                SpannableStringBuilder stringBuilder = null;
                try {
                    if (urlPattern == null) {
                        urlPattern = Pattern.compile("@[a-zA-Z\\d_]{1,32}");
                    }
                    Matcher matcher = urlPattern.matcher(set.set.title);
                    while (matcher.find()) {
                        if (stringBuilder == null) {
                            stringBuilder = new SpannableStringBuilder(set.set.title);
                            titleView.setMovementMethod(new LinkMovementMethodMy());
                        }
                        int start = matcher.start();
                        int end = matcher.end();
                        if (set.set.title.charAt(start) != '@') {
                            start++;
                        }
                        URLSpanNoUnderline url = new URLSpanNoUnderline(set.set.title.subSequence(start + 1, end).toString()) {
                            @Override
                            public void onClick(View widget) {
                                MessagesController.getInstance(currentAccount).openByUserName(getURL(), fragment, 1);
                                onCloseByLink();
                                dismiss();
                            }
                        };
                        stringBuilder.setSpan(url, start, end, 0);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                titleView.setText(stringBuilder != null ? stringBuilder : set.set.title);
            } else {
                titleView.setText(null);
            }

            if (subtitleView != null) {
                if (set == null || set.set == null || set.set.emojis) {
                    subtitleView.setText(LocaleController.formatPluralString("EmojiCount", size));
                } else {
                    subtitleView.setText(LocaleController.formatPluralString("Stickers", size));
                }
            }

            if (premium && unlockButtonView != null && !UserConfig.getInstance(currentAccount).isPremium()) {
                unlockButtonView.setVisibility(VISIBLE);
                if (addButtonView != null) {
                    addButtonView.setVisibility(GONE);
                }
                if (removeButtonView != null) {
                    removeButtonView.setVisibility(GONE);
                }
            } else {
                if (unlockButtonView != null) {
                    unlockButtonView.setVisibility(GONE);
                }
                if (addButtonView != null) {
                    addButtonView.setVisibility(VISIBLE);
                }
                if (removeButtonView != null) {
                    removeButtonView.setVisibility(VISIBLE);
                }

                toggle(set != null && MediaDataController.getInstance(currentAccount).isStickerPackInstalled(set.set.id), false);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(single ? 42 : 56), MeasureSpec.EXACTLY));
        }
    }

    private static Pattern urlPattern;
    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    class EmojiPacksLoader implements NotificationCenter.NotificationCenterDelegate {
        final int loadingStickersCount = 12;

        public ArrayList<TLRPC.InputStickerSet> inputStickerSets;
        public TLObject parentObject;
        public ArrayList<TLRPC.TL_messages_stickerSet> stickerSets;
        public ArrayList<EmojiView.CustomEmoji>[] data;
        private int currentAccount;
        private boolean started = false;

        public EmojiPacksLoader(int currentAccount, ArrayList<TLRPC.InputStickerSet> inputStickerSets, TLObject parentObject) {
            this.currentAccount = currentAccount;
            if (inputStickerSets == null && parentObject == null) {
                inputStickerSets = new ArrayList<>();
            }
            this.inputStickerSets = inputStickerSets;
            this.parentObject = parentObject;
        }

        public void start() {
            if (started) {
                return;
            }
            started = true;
            init();
        }

        public void init() {
            if ((parentObject instanceof TLRPC.Photo || parentObject instanceof TLRPC.Document) && (this.inputStickerSets == null || this.inputStickerSets.isEmpty())) {
                data = new ArrayList[2];
                putStickerSet(0, null);
                putStickerSet(1, null);
                final TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
                if (parentObject instanceof TLRPC.Photo) {
                    TLRPC.Photo photo = (TLRPC.Photo) parentObject;
                    TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
                    inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
                    inputStickeredMediaPhoto.id.id = photo.id;
                    inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
                    inputStickeredMediaPhoto.id.file_reference = photo.file_reference;
                    if (inputStickeredMediaPhoto.id.file_reference == null) {
                        inputStickeredMediaPhoto.id.file_reference = new byte[0];
                    }
                    req.media = inputStickeredMediaPhoto;
                } else if (parentObject instanceof TLRPC.Document) {
                    TLRPC.Document document = (TLRPC.Document) parentObject;
                    TLRPC.TL_inputStickeredMediaDocument inputStickeredMediaDocument = new TLRPC.TL_inputStickeredMediaDocument();
                    inputStickeredMediaDocument.id = new TLRPC.TL_inputDocument();
                    inputStickeredMediaDocument.id.id = document.id;
                    inputStickeredMediaDocument.id.access_hash = document.access_hash;
                    inputStickeredMediaDocument.id.file_reference = document.file_reference;
                    if (inputStickeredMediaDocument.id.file_reference == null) {
                        inputStickeredMediaDocument.id.file_reference = new byte[0];
                    }
                    req.media = inputStickeredMediaDocument;
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (err != null || !(res instanceof TLRPC.Vector)) {
                           EmojiPacksAlert.this.dismiss();
                           if (fragment != null && fragment.getParentActivity() != null) {
                               BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                           }
                        } else {
                           TLRPC.Vector vector = (TLRPC.Vector) res;
                            if (inputStickerSets == null) {
                                inputStickerSets = new ArrayList<>();
                            }
                           for (int i = 0; i < vector.objects.size(); ++i) {
                               Object object = vector.objects.get(i);
                               if (object instanceof TLRPC.StickerSetCovered && ((TLRPC.StickerSetCovered) object).set != null) {
                                   inputStickerSets.add(MediaDataController.getInputStickerSet(((TLRPC.StickerSetCovered) object).set));
                               }
                           }
                           parentObject = null;
                           init();
                        }
                    });
                });
                return;
            }
            stickerSets = new ArrayList<>(inputStickerSets.size());
            data = new ArrayList[inputStickerSets.size()];
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
            final boolean[] failed = new boolean[1];
            for (int i = 0; i < data.length; ++i) {
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSets.get(i), null, false, (set) -> {
                    if (set == null && !failed[0]) {
                        failed[0] = true;
                        AndroidUtilities.runOnUIThread(() -> {
                            dismiss();
                            if (fragment != null && fragment.getParentActivity() != null) {
                                BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("AddEmojiNotFound", R.string.AddEmojiNotFound)).show();
                            }
                        });
                    }
                });
                if (data.length == 1 && stickerSet != null && stickerSet.set != null && !stickerSet.set.emojis) {
                    AndroidUtilities.runOnUIThread(() -> EmojiPacksAlert.this.dismiss());
                    StickersAlert alert = new StickersAlert(getContext(), fragment,  inputStickerSets.get(i), null, fragment instanceof ChatActivity ? ((ChatActivity) fragment).getChatActivityEnterView() : null, resourcesProvider);
                    alert.show();
                    return;
                }
                stickerSets.add(stickerSet);
                putStickerSet(i, stickerSet);
            }
            onUpdate();
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.groupStickersDidLoad) {
                for (int i = 0; i < stickerSets.size(); ++i) {
                    if (stickerSets.get(i) == null) {
                        TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(this.inputStickerSets.get(i), true);
                        if (stickerSets.size() == 1 && stickerSet != null && stickerSet.set != null && !stickerSet.set.emojis) {
                            EmojiPacksAlert.this.dismiss();
                            StickersAlert alert = new StickersAlert(getContext(), fragment,  inputStickerSets.get(i), null, fragment instanceof ChatActivity ? ((ChatActivity) fragment).getChatActivityEnterView() : null, resourcesProvider);
                            alert.show();
                            return;
                        }
                        stickerSets.set(i, stickerSet);
                        if (stickerSet != null) {
                            putStickerSet(i, stickerSet);
                        }
                    }
                }
                onUpdate();
            }
        }

        private void putStickerSet(int index, TLRPC.TL_messages_stickerSet stickerSet) {
            if (index < 0 || index >= data.length) {
                return;
            }
            if (stickerSet == null || stickerSet.documents == null) {
                data[index] = new ArrayList<>(loadingStickersCount);
                for (int j = 0; j < loadingStickersCount; ++j) {
                    data[index].add(null);
                }
            } else {
                data[index] = new ArrayList<>();
                for (int j = 0; j < stickerSet.documents.size(); ++j) {
                    TLRPC.Document document = stickerSet.documents.get(j);
                    if (document == null) {
                        data[index].add(null);
                    } else {
                        EmojiView.CustomEmoji customEmoji = new EmojiView.CustomEmoji();
                        customEmoji.emoticon = findEmoticon(stickerSet, document.id);
                        customEmoji.stickerSet = stickerSet;
                        customEmoji.documentId = document.id;
                        data[index].add(customEmoji);
                        if (limitCount) {
                            final int maxCount = stickerSet == null || stickerSet.set == null || stickerSet.set.emojis ? 16 : 10;
                            if (data[index].size() >= maxCount) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        public void recycle() {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        }

        public String getTitle(int index) {
            if (index < 0 || index >= stickerSets.size()) {
                return null;
            }
            TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(index);
            if (stickerSet == null || stickerSet.set == null) {
                return null;
            }
            return stickerSet.set.title;
        }

        protected void onUpdate() {

        }

        public int getItemsCount() {
            if (data == null) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < data.length; ++i) {
                if (data[i] == null) {
                    continue;
                }
                if (data.length == 1) {
                    count += data[i].size();
                } else {
                    count += Math.min(gridLayoutManager.getSpanCount() * 2, data[i].size());
                }
                count++;
            }
            return count;
        }

        public String findEmoticon(long documentId) {
            String emoticon;
            for (int i = 0; i < stickerSets.size(); ++i) {
                emoticon = findEmoticon(stickerSets.get(i), documentId);
                if (emoticon != null) {
                    return emoticon;
                }
            }
            return null;
        }

        public String findEmoticon(TLRPC.InputStickerSet inputStickerSet, long documentId) {
            if (inputStickerSet == null) {
                return null;
            }
            TLRPC.TL_messages_stickerSet stickerSet = null;
            for (int i = 0; i < stickerSets.size(); ++i) {
                TLRPC.TL_messages_stickerSet s = stickerSets.get(i);
                if (s != null && s.set != null && (inputStickerSet.id == s.set.id || inputStickerSet.short_name != null && inputStickerSet.short_name.equals(s.set.short_name))) {
                    stickerSet = s;
                    break;
                }
            }
            return findEmoticon(stickerSet, documentId);
        }

        public String findEmoticon(TLRPC.TL_messages_stickerSet stickerSet, long documentId) {
            if (stickerSet == null) {
                return null;
            }
            for (int o = 0; o < stickerSet.packs.size(); ++o) {
                TLRPC.TL_stickerPack pack = stickerSet.packs.get(o);
                if (pack.documents != null && pack.documents.contains(documentId)) {
                    return pack.emoticon;
                }
            }
            return null;
        }
    }

    private ColorFilter adaptiveEmojiColorFilter;
    private int adaptiveEmojiColor;
    private ColorFilter getAdaptiveEmojiColorFilter(int color) {
        if (color != adaptiveEmojiColor || adaptiveEmojiColorFilter == null) {
            adaptiveEmojiColorFilter = new PorterDuffColorFilter(adaptiveEmojiColor = color, PorterDuff.Mode.SRC_IN);
        }
        return adaptiveEmojiColorFilter;
    }
}