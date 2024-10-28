package org.telegram.ui.Components.Share;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.provider.Settings;
import android.util.Property;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ShareContainerLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static Property<ShareContainerLayout, Float> TRANSITION_PROGRESS_VALUE = new Property<ShareContainerLayout, Float>(Float.class, "transitionProgress") {
        @Override
        public Float get(ShareContainerLayout shareContainerLayout) {
            return shareContainerLayout.transitionProgress;
        }

        @Override
        public void set(ShareContainerLayout object, Float value) {
            object.setTransitionProgress(value);
        }
    };

    public final static int TYPE_MESSAGE_EFFECTS = 5;

    private final static int ALPHA_DURATION = 150;
    private final static float SIDE_SCALE = 0.6f;
    private final static float CLIP_PROGRESS = 0.25f;
    public final RecyclerListView recyclerListView;
    public final float durationScale;

    private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private LongSparseArray<TLRPC.Dialog> dialogsMap = new LongSparseArray<>();
    private ChatScrimPopupContainerLayout chatScrimPopupContainerLayout;

    private enum ShareLayoutStatus {
        INITIAL, // 1.Initiate the animation onLongPress
        PRESSED, // 2.Convert the background to OvalShape with transparent/white bg
        POPUP_EXPAND_1, // 3. Start rotating the share arrow to the top while having bg as white
        POPUP_EXPAND_2, // 4. Start the initial expand for the share popup
        FULL_POPUP, // 5. Start expanding/fetching friends list while rotating the arrow down and setting the bg to transparent
        SHOW_FRIENDS, // 6. Start showing more friends while setting the bg to of share button to initial state
        ADDITIONAL_FRIEND, // 7. Load all friends list
        FINAL_POPUP // 8. Final state then reset it to initial state again.
    }

    private enum ShareLayoutPressType {
        PRESS, // Regularly pressed the sharing friend
        LONG_PRESS // LONG pressed the sharing friend
    }


    private static class ShareContentLayout {

    }

    public void setChatScrimView(ChatScrimPopupContainerLayout chatScrimPopupContainerLayout) {
        this.chatScrimPopupContainerLayout = chatScrimPopupContainerLayout;
    }
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float leftAlpha, rightAlpha;
    private float transitionProgress = 1f;
    public RectF rect = new RectF();
    private final Path mPath = new Path();
    public float radius = dp(72);
    private final float bigCircleRadius = dp(8);
    private final float smallCircleRadius = bigCircleRadius / 2;
    public MessageObject messageObject;
    private int currentAccount;
    private float flipVerticalProgress;
    private long lastUpdate;

    ValueAnimator cancelPressedAnimation;
    private final List<TLRPC.Dialog> visibleDialogs = new ArrayList<>(10);

    public List<TLRPC.Dialog> allDialogsList = new ArrayList<>(20);

    private final LinearLayoutManager linearLayoutManager;
    private final ShareContainerLayout.Adapter listAdapter;
    protected LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();
    protected Map<TLRPC.Dialog, TLRPC.TL_forumTopic> selectedDialogTopics = new HashMap<>();
    private ShareContainerLayout.ShareContainerDelegate delegate;

    private final boolean animationEnabled;

    Theme.ResourcesProvider resourcesProvider;
    private TLRPC.Dialog pressedPeer;
    private int pressedPeerPosition;
    private float pressedProgress;
    private float cancelPressedProgress;
    private float pressedViewScale;
    private float otherViewsScale;
    private boolean clicked;
    long lastShareSentTime;
    BaseFragment fragment;
    ValueAnimator pullingDownBackAnimator;

    public ShareHolderView nextRecentPeer;
    float pullingLeftOffset;

    HashSet<View> lastVisibleViews = new HashSet<>();
    public boolean includeStoryFromMessage;
    HashSet<View> lastVisibleViewsTmp = new HashSet<>();
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ChatScrimPopupContainerLayout parentLayout;
    boolean darkTheme;
    private boolean animatePopup;
    public final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private final int type;
    public boolean skipEnterAnimation;
    public boolean isHiddenNextPeer = true;
    private Runnable onSwitchedToLoopView;
    public boolean hasHint;
    public TextView hintView;
    public float bubblesOffset;
    private float miniBubblesOffset;
    private ChatMessageCell chatMessageCell;
    private long lastPeerIdBeingClicked = -5;

    public ShareContainerLayout(int type, BaseFragment fragment, @NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, ChatMessageCell chatMessageCell) {
        super(context);
        this.type = type;
        durationScale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        selectedPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));

        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;
        this.fragment = fragment;
        this.chatMessageCell = chatMessageCell;
        darkTheme = false;

        nextRecentPeer = new ShareHolderView(context, false);
        nextRecentPeer.setVisibility(View.GONE);
        nextRecentPeer.touchable = false;

        animationEnabled = SharedConfig.animationsEnabled() && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;

        recyclerListView = new RecyclerListView(context) {

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
//                if (pressedPeer != null && (child instanceof ShareHolderView) && ((ShareHolderView) child).currentPeer.equals(pressedPeer)) {
//                    return true;
//                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                int action = e.getActionMasked();
                float x = e.getX();
                float y = e.getY();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE: {
                        View childView = findChildViewUnder(x, y);

                        if (childView != null) {
                            ViewHolder viewHolder = getChildViewHolder(childView);

                            if (viewHolder.itemView instanceof ShareHolderView) {
                                ShareHolderView view = (ShareHolderView) viewHolder.itemView;
                                String nameOrTitle;
                                if (DialogObject.isUserDialog(view.currentPeer.id)) {
                                    nameOrTitle = MessagesController.getInstance(currentAccount).getUser(view.currentPeer.id).first_name;
                                } else {
                                    nameOrTitle = MessagesController.getInstance(currentAccount).getChat(-view.currentPeer.id).title;
                                }

                                delegate.onPeerClicked(view, view.currentPeer, false, false, nameOrTitle);
                                peerPressed(view.currentPeer);
                            }
                        } else {
                            if (!clicked) {
                                cancelPressed();
                            }
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        View childView = findChildViewUnder(x, y);

                            if (childView != null) {
                                ViewHolder viewHolder = getChildViewHolder(childView);

                                if (viewHolder.itemView instanceof ShareHolderView) {
                                    ShareHolderView view = (ShareHolderView) viewHolder.itemView;

                                    if (!view.touchable || cancelPressedAnimation != null) {
                                        return false;
                                    }

                                    String nameOrTitle;
                                    if (DialogObject.isUserDialog(view.currentPeer.id)) {
                                        nameOrTitle = MessagesController.getInstance(currentAccount).getUser(view.currentPeer.id).first_name;
                                    } else {
                                        nameOrTitle = MessagesController.getInstance(currentAccount).getChat(-view.currentPeer.id).title;
                                    }

                                    peerPressed(view.currentPeer);
                                    view.sendToUser(false, view.currentPeer, chatMessageCell);
                                    delegate.onPeerClicked(view, view.currentPeer, false, true, nameOrTitle);
                                }
                        } else {
                            if (!clicked) {
                                cancelPressed();
                            }
                        }
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL: {

                        break;
                    }
                    default:
                        break;
                }
                return true;
            }
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (ev.getAction() == MotionEvent.ACTION_UP && getPullingLeftProgress() > 0.95f) {
                        // showCustomMenuDialog();
                    } else {
                        animatePullingBack();
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        recyclerListView.setClipChildren(false);
        recyclerListView.setClipToPadding(false);
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                if (position == 0) {
                    outRect.left = dp(6);
                }
                outRect.right = dp(4);
                if (position == listAdapter.getItemCount() - 1) {
                    outRect.right = dp(6);
                }

            }
        });
        recyclerListView.setLayoutManager(linearLayoutManager);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(listAdapter = new Adapter(getContext()));

        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int i = parent.getChildAdapterPosition(view);
                if (i == 0)
                    outRect.left = dp(8);
                if (i == listAdapter.getItemCount() - 1) {
                    outRect.right = dp(8);
                }
            }
        });

        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        setClipChildren(false);
        setClipToPadding(false);
        invalidateShaders();

        int size = recyclerListView.getLayoutParams().height - recyclerListView.getPaddingTop() - recyclerListView.getPaddingBottom();

        System.out.println("Sizee: " + size);
        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));

        MediaDataController.getInstance(currentAccount).loadHints(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(dp(4));
//            setOutlineProvider(new ViewOutlineProvider() {
//                @Override
//                public void getOutline(View view, Outline outline) {
//                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
//                }
//            });
            setClipToOutline(true);
        }

    }

    public List<TLRPC.Dialog> getVisibleDialogs() {
        return visibleDialogs;
    }

    private ImageView overlayImageView;

    public ImageView getOverlayImageView() {
        return overlayImageView;
    }
    private Bitmap createViewBitmap(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    public ImageView createOverlayImageView(View view, int x, int y, ChatActivity.ChatActivityFragmentView parentLayout) {
        // Ensure the overlayImageView is only added once
        if (overlayImageView != null) {
            parentLayout.removeView(overlayImageView);
        }

        Bitmap bitmap = createViewBitmap(view);
        overlayImageView = new ImageView(getContext());
        overlayImageView.setImageBitmap(bitmap);

        // Set initial position using LayoutParams
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(70),
                dp(70)
        );
        params.leftMargin = x;
        params.topMargin = y;
        overlayImageView.setLayoutParams(params);

        // Add the overlay ImageView to the root view
        parentLayout.addView(overlayImageView);
        return overlayImageView;
    }

    private void animatePullingBack() {
        if (pullingLeftOffset != 0) {
            if (pullingDownBackAnimator != null) {
                pullingDownBackAnimator.cancel();
            }
            pullingDownBackAnimator = ValueAnimator.ofFloat(pullingLeftOffset, 0);
            pullingDownBackAnimator.addUpdateListener(animation -> {
                pullingLeftOffset = (float) pullingDownBackAnimator.getAnimatedValue();
                invalidate();
            });
            pullingDownBackAnimator.setDuration(150);
            pullingDownBackAnimator.start();
        }
    }

    public void setDelegate(ShareContainerLayout.ShareContainerDelegate delegate) {
        this.delegate = delegate;
    }



    @SuppressLint("NotifyDataSetChanged")
    public void setVisibleDialogList(boolean animated) {
        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();

        this.visibleDialogs.clear();
        this.visibleDialogs.addAll(allDialogs);

        for (int i = 0; i < this.visibleDialogs.size(); i++) {

        }
        allDialogsList.clear();
        allDialogsList.addAll(allDialogs);
        int size = getLayoutParams().height - (int) getTopOffset() - getPaddingTop() - getPaddingBottom();
        if (size * allDialogs.size() < dp(200)) {
            getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        System.out.println("dialog sizee: " + size);
        System.out.println("dialog widthh: " + getLayoutParams().width);
        listAdapter.updateItems(animated);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long dt = Math.min(16, System.currentTimeMillis() - lastUpdate);
        lastUpdate = System.currentTimeMillis();

        if (flipVerticalProgress != 1f) {
            flipVerticalProgress = Math.min(1f, flipVerticalProgress + dt / 220f);
            invalidate();
        } else if (flipVerticalProgress != 0f) {
            flipVerticalProgress = Math.max(0f, flipVerticalProgress - dt / 220f);
            invalidate();
        }

        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;

        lastVisibleViewsTmp.clear();
        lastVisibleViewsTmp.addAll(lastVisibleViews);
        lastVisibleViews.clear();

        if (prepareAnimation) {
            invalidate();
        }

        if (pressedPeer != null) {
            if (pressedProgress != 1f) {
                pressedProgress += 16f / 1500f;
                if (pressedProgress >= 1f) {
                    pressedProgress = 1f;
                }
                invalidate();
            }
        }

        pressedViewScale = 1 + 2 * pressedProgress;
        otherViewsScale = 1 - 0.15f * pressedProgress;

        int s = canvas.save();
        float pivotX = LocaleController.isRTL ? getWidth() * 0.125f : getWidth() * 0.875f;


        if (transitionProgress != 1f) {
            float sc = transitionProgress;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        float lt = 0, rt = 1;
        if (LocaleController.isRTL) {
            rt = Math.max(CLIP_PROGRESS, transitionProgress);
        } else {
            lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
        }
        float pullingLeftOffsetProgress = getPullingLeftProgress();
        float expandSize = expandSize();
        if (chatScrimPopupContainerLayout != null) {
            chatScrimPopupContainerLayout.setExpandSize(expandSize);
        }
        float transitionLeftOffset = (getWidth() - getPaddingRight()) * Math.min(1f, lt);
        float hintHeight = getTopOffset();
        rect.set(getPaddingLeft() + transitionLeftOffset, getPaddingTop() + recyclerListView.getMeasuredHeight() * (1f - otherViewsScale) - expandSize, (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom() + expandSize);
        radius = ((rect.height() - hintHeight) - expandSize * 2f) / 2f;


        canvas.restoreToCount(s);


        s = canvas.save();
        if (transitionProgress != 1f) {
            float sc = transitionProgress;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        canvas.restoreToCount(s);


        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        s = canvas.save();
        if (transitionProgress != 1f) {
            float sc = transitionProgress;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        if (transitionProgress != 0 && (getAlpha() == 1f || type == TYPE_MESSAGE_EFFECTS)) {
            int delay = 0;
            int lastPeerX = 0;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                View child = recyclerListView.getChildAt(i);
                if (transitionProgress != 1f && allowSmoothEnterTransition()) {
                    float childCenterX = child.getLeft() + child.getMeasuredWidth() / 2f;
                    delay = (int) (200 * ((Math.abs(childCenterX / (float) recyclerListView.getMeasuredWidth() - 0.8f))));
                }
                if (child instanceof ShareHolderView) {
                    ShareHolderView view = (ShareHolderView) recyclerListView.getChildAt(i);
                    checkPressedProgress(canvas, view);
                    if (child.getLeft() > lastPeerX) {
                        lastPeerX = child.getLeft();
                    }
//                    if (skipEnterAnimation || (view.hasEnterAnimation)) {
//                        continue;
//                    }
                    if (view.getX() + view.getMeasuredWidth() / 2f > 0 && view.getX() + view.getMeasuredWidth() / 2f < recyclerListView.getWidth()) {
                        if (!lastVisibleViewsTmp.contains(view)) {
                            view.play(delay);
                            delay += 30;
                        }
                        //lastVisibleViews.add(view);
                    } else if (!view.isEnter) {
                        view.resetAnimation();
                    }
                } else {
                    checkPressedProgressForOtherViews(child);
                }
            }
            if (pullingLeftOffsetProgress > 0) {
                float progress = getPullingLeftProgress();
                int peerSize = nextRecentPeer.getMeasuredWidth() - dp(2);
                int left = lastPeerX + peerSize;
                float leftProgress = Utilities.clamp(left / (float) (getMeasuredWidth() - nextRecentPeer.getMeasuredWidth()), 1f, 0f);
                float pullingOffsetX = leftProgress * progress * peerSize;

                if (nextRecentPeer.getTag() == null) {
                    nextRecentPeer.setTag(1f);
                    nextRecentPeer.resetAnimation();
                    nextRecentPeer.play(0);
                }
                float scale = Utilities.clamp(progress, 1f, 0f);
                nextRecentPeer.setScaleX(scale);
                nextRecentPeer.setScaleY(scale);
                float additionalOffset = -dp(8);

                nextRecentPeer.setTranslationX(recyclerListView.getX() + left - pullingOffsetX + additionalOffset);
                if (nextRecentPeer.getVisibility() != View.VISIBLE) {
                    nextRecentPeer.setVisibility(View.VISIBLE);
                }
            } else {
                if (nextRecentPeer.getVisibility() != View.GONE && isHiddenNextPeer) {
                    nextRecentPeer.setVisibility(View.GONE);
                }
                if (nextRecentPeer.getTag() != null) {
                    nextRecentPeer.setTag(null);
                }
            }
        }

        canvas.translate((LocaleController.isRTL ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        recyclerListView.setTranslationX(-transitionLeftOffset);
        super.dispatchDraw(canvas);

        canvas.restoreToCount(s);
        invalidate();
    }


    private void checkPressedProgress(Canvas canvas, ShareHolderView view) {
        float pullingOffsetX = 0;
        if (pullingLeftOffset != 0) {
            float progress = getPullingLeftProgress();
            float leftProgress = Utilities.clamp(view.getLeft() / (float) (getMeasuredWidth() - dp(34)), 1f, 0f);
            pullingOffsetX = leftProgress * progress * dp(46);
        }
        if (view.currentPeer.equals(pressedPeer)) {
            view.setPivotX(view.getMeasuredWidth() >> 1);
            view.setScaleX(pressedViewScale);
            view.setScaleY(pressedViewScale);

            if (!clicked) {
                if (cancelPressedAnimation == null) {

                } else {
                }
                if (pressedProgress == 1f) {
                    clicked = true;
                    if (System.currentTimeMillis() - lastShareSentTime > 300) {
                        lastShareSentTime = System.currentTimeMillis();
                        delegate.onPeerClicked(view, view.currentPeer, true, false, null);
                    }
                }
            }

            canvas.save();
            float x = recyclerListView.getX() + view.getX();
            float additionalWidth = (view.getMeasuredWidth() * view.getScaleX() - view.getMeasuredWidth()) / 2f;
            if (x - additionalWidth < 0 && view.getTranslationX() >= 0) {
                view.setTranslationX(-(x - additionalWidth) - pullingOffsetX);
            } else if (x + view.getMeasuredWidth() + additionalWidth > getMeasuredWidth() && view.getTranslationX() <= 0) {
                view.setTranslationX(getMeasuredWidth() - x - view.getMeasuredWidth() - additionalWidth - pullingOffsetX);
            } else {
                view.setTranslationX(0 - pullingOffsetX);
            }
            x = recyclerListView.getX() + view.getX();
            canvas.translate(x, recyclerListView.getY() + view.getY());
            canvas.scale(view.getScaleX(), view.getScaleY(), view.getPivotX(), view.getPivotY());
            view.draw(canvas);
            canvas.restore();
        } else {
            int position = recyclerListView.getChildAdapterPosition(view);
            float translationX;
            translationX = (view.getMeasuredWidth() * (pressedViewScale - 1f)) / 3f - view.getMeasuredWidth() * (1f - otherViewsScale) * (Math.abs(pressedPeerPosition - position) - 1);

            if (position < pressedPeerPosition) {
                view.setPivotX(0);
                view.setTranslationX(-translationX);
            } else {
                view.setPivotX(view.getMeasuredWidth() - pullingOffsetX);
                view.setTranslationX(translationX - pullingOffsetX);
            }
            view.setScaleX(otherViewsScale);
            view.setScaleY(otherViewsScale);

        }
    }

    private void checkPressedProgressForOtherViews(View view) {
        int position = recyclerListView.getChildAdapterPosition(view);
        float translationX;
        translationX = (view.getMeasuredWidth() * (pressedViewScale - 1f)) / 3f - view.getMeasuredWidth() * (1f - otherViewsScale) * (Math.abs(pressedPeerPosition - position) - 1);

        if (position < pressedPeerPosition) {
            view.setPivotX(0);
            view.setTranslationX(-translationX);
        } else {
            view.setPivotX(view.getMeasuredWidth());
            view.setTranslationX(translationX);
        }
        view.setScaleX(otherViewsScale);
        view.setScaleY(otherViewsScale);
    }

    private void invalidatePeers() {
        invalidate();
        recyclerListView.invalidate();
        recyclerListView.invalidateViews();
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof ShareHolderView) {

            } else {
                child.invalidate();
            }
        }
    }

    public float getPullingLeftProgress() {
        return Utilities.clamp(pullingLeftOffset / dp(42), 2f, 0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateShaders();
    }

    /**
     * Invalidates shaders
     */
    private void invalidateShaders() {
        int dp = dp(24);
        float cy = getHeight() / 2f;
        int clr = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
        leftShadowPaint.setShader(new LinearGradient(0, cy, dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rightShadowPaint.setShader(new LinearGradient(getWidth(), cy, getWidth() - dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        invalidate();
    }

    public void setTransitionProgress(float transitionProgress) {
        this.transitionProgress = transitionProgress;
        if (parentLayout != null) {
            parentLayout.setShareTransitionProgress(animatePopup && allowSmoothEnterTransition() ? transitionProgress : 1);
        }
        invalidate();
    }

    public LongSparseArray<TLRPC.Dialog> getSelectedDialogs() {
        return selectedDialogs;
    }


    public void startEnterAnimation(boolean animatePopup) {
        this.animatePopup = animatePopup;
        setTransitionProgress(0);
        setAlpha(1f);
        notificationsLocker.lock();
        ObjectAnimator animator;
        if (allowSmoothEnterTransition()) {
            animator = ObjectAnimator.ofFloat(this, TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(250);
            animator.setInterpolator(new OvershootInterpolator(0.5f));
        } else {
            animator = ObjectAnimator.ofFloat(this, TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(250);
            animator.setInterpolator(new OvershootInterpolator(0.5f));
        }
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                notificationsLocker.unlock();
            }
        });
        animator.start();
    }

    public int getTotalWidth() {
        int itemsCount = getItemsCount();
        return dp(36) * itemsCount - dp(4);
    }

    public int getItemsCount() {
        return visibleDialogs.size() + 1;
    }

    public void onDialogClicked(View emojiView, TLRPC.Dialog visiblePeer, boolean longpress) {
        if (delegate != null) {
            delegate.onPeerClicked(emojiView, visiblePeer, longpress, true, null);
        }
        if (type == TYPE_MESSAGE_EFFECTS) {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {
            }
        }
    }

    private boolean prepareAnimation;

    public void prepareAnimation(boolean b) {
        prepareAnimation = b;
        invalidate();
    }


    public ShareContainerLayout.ShareContainerDelegate getDelegate() {
        return delegate;
    }

    public void setCurrentAccount(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void setFragment(BaseFragment lastFragment) {
        fragment = lastFragment;
    }

    public void reset() {
        isHiddenNextPeer = true;
        pressedPeerPosition = 0;
        pressedProgress = 0;
        pullingLeftOffset = 0;
        pressedPeer = null;
        clicked = false;
        AndroidUtilities.forEachViews(recyclerListView, view -> {
            if (view instanceof ShareHolderView) {
                ShareContainerLayout.ShareHolderView shareHolderView = (ShareContainerLayout.ShareHolderView) view;
                shareHolderView.pressed = false;

                if (skipEnterAnimation) {

                } else {
                    shareHolderView.resetAnimation();
                }
            }

        });
        lastVisibleViews.clear();
        recyclerListView.invalidate();
        invalidate();
    }


    public float getTopOffset() {
        return hasHint ? ((LayoutParams) recyclerListView.getLayoutParams()).topMargin : 0;
    }

    public void setBubbleOffset(float v) {
        bubblesOffset = v;
    }

    private final class LeftRightShadowsListener extends RecyclerView.OnScrollListener {
        private boolean leftVisible, rightVisible;
        private ValueAnimator leftAnimator, rightAnimator;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            boolean l = linearLayoutManager.findFirstVisibleItemPosition() != 0;
            if (l != leftVisible) {
                if (leftAnimator != null)
                    leftAnimator.cancel();
                leftAnimator = startAnimator(leftAlpha, l ? 1 : 0, aFloat -> {
                    leftShadowPaint.setAlpha((int) ((leftAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> leftAnimator = null);

                leftVisible = l;
            }

            boolean r = linearLayoutManager.findLastVisibleItemPosition() != listAdapter.getItemCount() - 1;
            if (r != rightVisible) {
                if (rightAnimator != null)
                    rightAnimator.cancel();
                rightAnimator = startAnimator(rightAlpha, r ? 1 : 0, aFloat -> {
                    rightShadowPaint.setAlpha((int) ((rightAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> rightAnimator = null);

                rightVisible = r;
            }
        }

        private ValueAnimator startAnimator(float fromAlpha, float toAlpha, Consumer<Float> callback, Runnable onEnd) {
            ValueAnimator a = ValueAnimator.ofFloat(fromAlpha, toAlpha).setDuration((long) (Math.abs(toAlpha - fromAlpha) * ALPHA_DURATION));
            a.addUpdateListener(animation -> callback.accept((Float) animation.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
            a.start();
            return a;
        }
    }

    public class ShareHolderView extends ShareDialogCell {
        public TLRPC.Dialog currentPeer;
        private boolean isEnter;
        public boolean hasEnterAnimation;
        public boolean switchedToLoopView;
        public boolean selected;
        public int position;
        public boolean waitingAnimation;

        Runnable playRunnable = new Runnable() {
            @Override
            public void run() {
                waitingAnimation = false;
            }
        };

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);

        }

        ShareHolderView(Context context, boolean recyclerPeer) {
            super(context, ShareDialogCell.TYPE_QUICK_SHARE, fragment.getResourceProvider());
        }

        public boolean isLocked;
        public float enterScale = 1f;
        public ValueAnimator enterAnimator;

        public void updateSelected(TLRPC.Dialog peer, boolean animated) {
            boolean wasSelected = selected;
            selected = selectedDialogs.containsKey(peer.id);
            if (selected != wasSelected) {
                if (!animated) {

                } else {

                }

                requestLayout();
                invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resetAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
        }

        public boolean play(int delay) {
            if (!animationEnabled) {
                resetAnimation();
                isEnter = true;
                if (!hasEnterAnimation) {

                }
                return false;
            }
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            if (hasEnterAnimation) {

            } else {
                if (!isEnter) {
                    enterScale = 0f;

                    enterAnimator = ValueAnimator.ofFloat(0, 1f);
                    enterAnimator.addUpdateListener(anm -> {
                        enterScale = (float) anm.getAnimatedValue();

                    });
                    enterAnimator.setDuration(150);
                    enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    enterAnimator.setStartDelay((long) (delay * durationScale));
                    enterAnimator.start();
                    isEnter = true;
                }
            }
            return false;
        }

        public void resetAnimation() {
            if (hasEnterAnimation) {
                AndroidUtilities.cancelRunOnUIThread(playRunnable);
                if (animationEnabled) {
                } else {
                }


                switchedToLoopView = false;

            } else {

                if (skipEnterAnimation) {

                } else {

                }
            }
            isEnter = false;
        }

        boolean pressed;
        boolean touchable = true;

        private void setPeer(TLRPC.Dialog dialog, int position, ShareHolderView shareHolderView) {
            updateSelected(dialog, false);
            if (currentPeer != null && currentPeer.equals(dialog)) {
                this.position = position;
                return;
            }
            //nextRecentPeer = shareHolderView;
            //resetAnimation();
            currentPeer = dialog;
            setFocusable(true);
        }

        protected void sendToUser(boolean withSound, TLRPC.Dialog dialog, ChatMessageCell cell) {
            TLRPC.TL_forumTopic topic = selectedDialogTopics.get(dialog);
            MessageObject replyTopMsg = topic != null ? new MessageObject(currentAccount, topic.topicStartMessage, true, true) : null;
            ArrayList messages = new ArrayList();
            messages.add(cell.getMessageObject());
            SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, dialog.id, false, false, withSound, 0, replyTopMsg);
        }



        @Override
        protected void dispatchDraw(Canvas canvas) {
//            if (selected && drawSelected) {
//                canvas.drawCircle(getMeasuredWidth() >> 1, getMeasuredHeight() >> 1, (getMeasuredWidth() >> 1) - dp(1), selectedPaint);
//            }
            super.dispatchDraw(canvas);
        }

    }

    private void cancelPressed() {
        if (pressedPeer != null) {
            cancelPressedProgress = 0f;
            float fromProgress = pressedProgress;
            cancelPressedAnimation = ValueAnimator.ofFloat(0, 1f);
            cancelPressedAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    cancelPressedProgress = (float) valueAnimator.getAnimatedValue();
                    pressedProgress = fromProgress * (1f - cancelPressedProgress);
                    invalidate();
                }
            });
            cancelPressedAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    cancelPressedAnimation = null;
                    pressedProgress = 0;
                    pressedPeer = null;
                    invalidate();
                }
            });
            cancelPressedAnimation.setDuration(150);
            cancelPressedAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            cancelPressedAnimation.start();
        }
    }

    private void peerPressed(TLRPC.Dialog pressedPeer) {
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            ShareHolderView ch = (ShareHolderView) recyclerListView.getChildAt(i);
            if (ch.currentPeer.id != pressedPeer.id) {
                ch.applyBlur();
            } else {
                ch.removeBlur();
            }
        }

    }

    public interface ShareContainerDelegate {
        void onPeerClicked(View view, TLRPC.Dialog peer, boolean longpress, boolean releasedFinger, String titleOrName);

        default void hideMenu() {

        }

        default void drawRoundRect(Canvas canvas, RectF rect, float radius, float offsetX, float offsetY, int alpha, boolean isWindow) {

        }

        default boolean needEnterText() {
            return false;
        }


        default boolean drawBackground() {
            return false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        if (type == TYPE_MESSAGE_EFFECTS) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.availableEffectsUpdate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        if (type == TYPE_MESSAGE_EFFECTS) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableEffectsUpdate);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];

        } else if (id == NotificationCenter.emojiLoaded) {
            invalidatePeers();
        } else if (id == NotificationCenter.availableEffectsUpdate) {
            //setMessage(messageObject, null, true);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getAlpha() < 0.5f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void setAlpha(float alpha) {
        if (getAlpha() != alpha && alpha == 0) {
            lastVisibleViews.clear();
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                if (recyclerListView.getChildAt(i) instanceof ShareHolderView) {
                    ShareHolderView view = (ShareHolderView) recyclerListView.getChildAt(i);
                    view.resetAnimation();
                }
            }
        }
        super.setAlpha(alpha);
    }

    @Override
    public void setTranslationX(float translationX) {
        if (translationX != getTranslationX()) {
            super.setTranslationX(translationX);
        }
    }

    public float expandSize() {
        return (int) (getPullingLeftProgress() * dp(6));
    }

    public void setParentLayout(ChatScrimPopupContainerLayout layout) {
        parentLayout = layout;
    }

    public static boolean allowSmoothEnterTransition() {
        return SharedConfig.deviceIsHigh();
    }

    public class Adapter extends AdapterWithDiffUtils {

        public Adapter(Context context) {
            this.context = context;
            fetchDialogs();
        }

        private Context context;
        private int currentCount;

        public void fetchDialogs() {
            dialogs.clear();
            dialogsMap.clear();
            long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;

            if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
                dialogs.add(dialog);
                dialogsMap.put(dialog.id, dialog);
            }
            ArrayList<TLRPC.Dialog> archivedDialogs = new ArrayList<>();
            ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
            for (int a = 0; a < allDialogs.size(); a++) {
                TLRPC.Dialog dialog = allDialogs.get(a);
                if (!(dialog instanceof TLRPC.TL_dialog)) {
                    continue;
                }
                if (dialog.id == selfUserId) {
                    continue;
                }
                if (!DialogObject.isEncryptedDialog(dialog.id)) {
                    if (DialogObject.isUserDialog(dialog.id)) {
                        if (dialog.folder_id == 1) {
                            archivedDialogs.add(dialog);
                        } else {
                            dialogs.add(dialog);
                        }
                        dialogsMap.put(dialog.id, dialog);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                            if (dialog.folder_id == 1) {
                                archivedDialogs.add(dialog);
                            } else {
                                dialogs.add(dialog);
                            }
                            dialogsMap.put(dialog.id, dialog);
                        }
                    }
                }
                if (dialogs.size() >= 5) {
                    break;
                }
            }
            dialogs.addAll(archivedDialogs);
            notifyDataSetChanged();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1) {
                return false;
            }
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShareHolderView(context, false) {
                        @Override
                        protected String repostToCustomName() {
                            if (includeStoryFromMessage) {
                                return LocaleController.getString(R.string.RepostToStory);
                            }
                            return super.repostToCustomName();
                        }
                    };
                    break;
                }
                case 1:
                default: {
                    view = new ShareHolderView(context, false);
                    break;
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ShareDialogCell cell = (ShareDialogCell) holder.itemView;
            TLRPC.Dialog dialog = getItem(position);
            if (dialog == null) return;
            cell.setTopic(selectedDialogTopics.get(dialog), false);
            cell.setDialog(dialog.id, selectedDialogs.indexOfKey(dialog.id) >= 0, null);

            ((ShareHolderView) holder.itemView).setPeer(dialog, position, ((ShareHolderView) holder.itemView));
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            }
            return 0;
        }

        public void updateItems(boolean animated) {

        }

        public TLRPC.Dialog getItem(int position) {
            if (position < 0 || position >= dialogs.size()) {
                return null;
            }
            return dialogs.get(position);
        }

    }

    public boolean paused = false;
    public boolean pausedExceptSelected;

    public void setPaused(boolean paused, boolean exceptSelected) {
        if (this.paused == paused) return;
        this.paused = paused;
        pausedExceptSelected = exceptSelected;
    }

}
