/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.Cells.ShareTopicCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageStatisticActivity;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ShareAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout frameLayout;
    private FrameLayout frameLayout2;
    private EditTextEmoji commentTextView;
    private FrameLayout writeButtonContainer;
    private View selectedCountView;
    private TextView pickerBottomLayout;
    private FrameLayout bulletinContainer;
    public FrameLayout bulletinContainer2;
    private LinearLayout sharesCountLayout;
    private AnimatorSet animatorSet;
    private RecyclerListView topicsGridView;
    private RecyclerListView gridView;
    private RecyclerListView searchGridView;
    private GridLayoutManager layoutManager;
    private GridLayoutManager topicsLayoutManager;
    private FillLastGridLayoutManager searchLayoutManager;
    private ShareDialogsAdapter listAdapter;
    private ShareTopicsAdapter shareTopicsAdapter;
    private ShareSearchAdapter searchAdapter;
    protected ArrayList<MessageObject> sendingMessageObjects;
    private String[] sendingText = new String[2];
    private int hasPoll;
    private StickerEmptyView searchEmptyView;
    private Drawable shadowDrawable;
    private View[] shadow = new View[2];
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    protected LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();
    protected Map<TLRPC.Dialog, TLRPC.TL_forumTopic> selectedDialogTopics = new HashMap<>();
    private SwitchView switchView;
    private int containerViewTop = -1;
    private boolean fullyShown = false;
    private boolean includeStory;
    public boolean includeStoryFromMessage;

    private ChatActivity parentFragment;
    private Activity parentActivity;

    private boolean darkTheme;
    public boolean forceDarkThemeForHint;

    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private TLRPC.TL_exportedMessageLink exportedMessageLink;
    private boolean loadingLink;
    private boolean copyLinkOnEnd;

    private boolean isChannel;
    private String[] linkToCopy = new String[2];

    private int scrollOffsetY;
    private int previousScrollOffsetY;
    private int topBeforeSwitch;
    private boolean panTranslationMoveLayout;

    private ShareAlertDelegate delegate;
    private float currentPanTranslationY;

    private float captionEditTextTopOffset;
    private float chatActivityEnterViewAnimateFromTop;
    private ValueAnimator topBackgroundAnimator;

    RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;
    SearchField searchView;
    ActionBar topicsBackActionBar;
    private boolean updateSearchAdapter;

    private SpringAnimation topicsAnimation;
    private TLRPC.Dialog selectedTopicDialog;

    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private ArrayList<DialogsSearchAdapter.RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private LongSparseArray<DialogsSearchAdapter.RecentSearchObject> recentSearchObjectsById = new LongSparseArray<>();
    private final Theme.ResourcesProvider resourcesProvider;
    TL_stories.StoryItem storyItem;

    public void setStoryToShare(TL_stories.StoryItem storyItem) {
        this.storyItem = storyItem;
    }

    public interface ShareAlertDelegate {
        default void didShare() {

        }

        default boolean didCopy() {
            return false;
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class SwitchView extends FrameLayout {

        private View searchBackground;
        private SimpleTextView rightTab;
        private SimpleTextView leftTab;
        private View slidingView;
        private int currentTab;
        private float tabSwitchProgress;
        private AnimatorSet animator;
        private LinearGradient linearGradient;
        private Paint paint;
        private RectF rect;
        private int lastColor;

        public SwitchView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rect = new RectF();

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(dp(18), getThemedColor(darkTheme ? Theme.key_voipgroup_searchBackground : Theme.key_dialogSearchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 0, 14, 0));

            slidingView = new View(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    invalidate();
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);

                    int color01 = 0xff75CB6B;
                    int color02 = 0xff4FAFBE;
                    int color11 = 0xff5F94F5;
                    int color12 = 0xffB95A91;

                    int color0 = AndroidUtilities.getOffsetColor(color01, color11, getTranslationX() / getMeasuredWidth(), 1.0f);
                    int color1 = AndroidUtilities.getOffsetColor(color02, color12, getTranslationX() / getMeasuredWidth(), 1.0f);
                    if (color0 != lastColor) {
                        linearGradient = new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{color0, color1}, null, Shader.TileMode.CLAMP);
                        paint.setShader(linearGradient);
                    }
                    rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.drawRoundRect(rect, dp(18), dp(18), paint);
                }
            };
            addView(slidingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 0, 14, 0));

            leftTab = new SimpleTextView(context);
            leftTab.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            leftTab.setTextSize(13);
            leftTab.setLeftDrawable(R.drawable.msg_tabs_mic1);
            leftTab.setText(LocaleController.getString(R.string.VoipGroupInviteCanSpeak));
            leftTab.setGravity(Gravity.CENTER);
            addView(leftTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 14, 0, 0, 0));
            leftTab.setOnClickListener(v -> switchToTab(0));

            rightTab = new SimpleTextView(context);
            rightTab.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            rightTab.setTextSize(13);
            rightTab.setLeftDrawable(R.drawable.msg_tabs_mic2);
            rightTab.setText(LocaleController.getString(R.string.VoipGroupInviteListenOnly));
            rightTab.setGravity(Gravity.CENTER);
            addView(rightTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 14, 0));
            rightTab.setOnClickListener(v -> switchToTab(1));
        }

        protected void onTabSwitch(int num) {

        }

        private void switchToTab(int tab) {
            if (currentTab == tab) {
                return;
            }
            currentTab = tab;
            if (animator != null) {
                animator.cancel();
            }
            animator = new AnimatorSet();
            animator.playTogether(ObjectAnimator.ofFloat(slidingView, View.TRANSLATION_X, currentTab == 0 ? 0 : slidingView.getMeasuredWidth()));
            animator.setDuration(180);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                }
            });
            animator.start();
            onTabSwitch(currentTab);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec) - dp(28);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) leftTab.getLayoutParams();
            layoutParams.width = width / 2;

            layoutParams = (FrameLayout.LayoutParams) rightTab.getLayoutParams();
            layoutParams.width = width / 2;
            layoutParams.leftMargin = width / 2 + dp(14);

            layoutParams = (FrameLayout.LayoutParams) slidingView.getLayoutParams();
            layoutParams.width = width / 2;
            if (animator != null) {
                animator.cancel();
            }
            slidingView.setTranslationX(currentTab == 0 ? 0 : layoutParams.width);

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class SearchField extends FrameLayout {

        private View searchBackground;
        private ImageView searchIconImageView;
        private ImageView clearSearchImageView;
        private CloseProgressDrawable2 progressDrawable;
        private EditTextBoldCursor searchEditText;
        private View backgroundView;

        public SearchField(Context context) {
            super(context);

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(dp(18), getThemedColor(darkTheme ? Theme.key_voipgroup_searchBackground : Theme.key_dialogSearchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0));

            searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(darkTheme ? Theme.key_voipgroup_mutedIcon : Theme.key_dialogSearchIcon), PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2() {
                @Override
                protected int getCurrentColor() {
                    return getThemedColor(darkTheme ? Theme.key_voipgroup_searchPlaceholder : Theme.key_dialogSearchIcon);
                }
            });
            progressDrawable.setSide(dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                updateSearchAdapter = true;
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context);
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(getThemedColor(darkTheme ? Theme.key_voipgroup_searchPlaceholder : Theme.key_dialogSearchHint));
            searchEditText.setTextColor(getThemedColor(darkTheme ? Theme.key_voipgroup_searchText : Theme.key_dialogSearchText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            searchEditText.setHint(LocaleController.getString(R.string.ShareSendTo));
            searchEditText.setCursorColor(getThemedColor(darkTheme ? Theme.key_voipgroup_searchText : Theme.key_featuredStickers_addedIcon));
            searchEditText.setCursorSize(dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    if (!TextUtils.isEmpty(searchEditText.getText())) {
                        checkCurrentList(false);
                    }

                    if (!updateSearchAdapter) {
                        return;
                    }
                    String text = searchEditText.getText().toString();
                    if (text.length() != 0) {
                        if (searchEmptyView != null) {
                            searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
                        }
                    } else {
                        if (gridView.getAdapter() != listAdapter) {
                            int top = getCurrentTop();
                            searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
                            searchEmptyView.showProgress(false, true);
                            checkCurrentList(false);
                            listAdapter.notifyDataSetChanged();
                            if (top > 0) {
                                layoutManager.scrollToPositionWithOffset(0, -top);
                            }
                        }
                    }
                    if (searchAdapter != null) {
                        searchAdapter.searchDialogs(text);
                    }
                }
            });
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    AndroidUtilities.hideKeyboard(searchEditText);
                }
                return false;
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }
    }

    public static ShareAlert createShareAlert(final Context context, MessageObject messageObject, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        ArrayList<MessageObject> arrayList;
        if (messageObject != null) {
            arrayList = new ArrayList<>();
            arrayList.add(messageObject);
        } else {
            arrayList = null;
        }
        return new ShareAlert(context, null, arrayList, text, null, channel, copyLink, null, fullScreen, false);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        this(context, messages, text, channel, copyLink, fullScreen, null);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean channel, final String copyLink, boolean fullScreen, Theme.ResourcesProvider resourcesProvider) {
        this(context, null, messages, text, null, channel, copyLink, null, fullScreen, false, false, resourcesProvider);
    }

    public ShareAlert(final Context context, ChatActivity fragment, ArrayList<MessageObject> messages, final String text, final String text2, boolean channel, final String copyLink, final String copyLink2, boolean fullScreen, boolean forCall) {
        this(context, fragment, messages, text, text2, channel, copyLink, copyLink2, fullScreen, forCall, false, null);
    }

    public ShareAlert(final Context context, ChatActivity fragment, ArrayList<MessageObject> messages, final String text, final String text2, boolean channel, final String copyLink, final String copyLink2, boolean fullScreen, boolean forCall, boolean includeStory, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.includeStory = includeStory;

        parentActivity = AndroidUtilities.findActivity(context);

        darkTheme = forCall;

        parentFragment = fragment;
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        int backgroundColor = getThemedColor(behindKeyboardColorKey = (darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground));
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        fixNavigationBar(backgroundColor);

        isFullscreen = fullScreen;
        linkToCopy[0] = copyLink;
        linkToCopy[1] = copyLink2;
        sendingMessageObjects = messages;
        searchAdapter = new ShareSearchAdapter(context);
        isChannel = channel;
        sendingText[0] = text;
        sendingText[1] = text2;
        useSmoothKeyboard = true;

        super.setDelegate(new BottomSheetDelegate() {
            @Override
            public void onOpenAnimationEnd() {
                fullyShown = true;
            }
        });

        if (sendingMessageObjects != null) {
            for (int a = 0, N = sendingMessageObjects.size(); a < N; a++) {
                MessageObject messageObject = sendingMessageObjects.get(a);
                if (messageObject.isPoll()) {
                    hasPoll = messageObject.isPublicPoll() ? 2 : 1;
                    if (hasPoll == 2) {
                        break;
                    }
                }
            }
        }

        if (channel) {
            loadingLink = true;
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messages.get(0).getId();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(messages.get(0).messageOwner.peer_id.channel_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                    if (copyLinkOnEnd) {
                        copyLink(context);
                    }
                }
                loadingLink = false;
            }));
        }


        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout = false;
            private RectF rect1 = new RectF();
            private boolean fullHeight;
            private int topOffset;
            private int previousTopOffset;

            private int fromScrollY;
            private int toScrollY;

            private int fromOffsetTop;
            private int toOffsetTop;

            {
                adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {

                    @Override
                    protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                        super.onTransitionStart(keyboardVisible, contentHeight);
                        if (previousScrollOffsetY != scrollOffsetY) {
                            fromScrollY = previousScrollOffsetY;
                            toScrollY = scrollOffsetY;
                            panTranslationMoveLayout = true;
                            scrollOffsetY = fromScrollY;
                        } else {
                            fromScrollY = -1;
                        }

                        if (topOffset != previousTopOffset) {
                            fromOffsetTop = 0;
                            toOffsetTop = 0;
                            panTranslationMoveLayout = true;

                            if (!keyboardVisible) {
                                toOffsetTop -= topOffset - previousTopOffset;
                            } else {
                                toOffsetTop += topOffset - previousTopOffset;
                            }
                            scrollOffsetY = keyboardVisible ? fromScrollY : toScrollY;
                        } else {
                            fromOffsetTop = -1;
                        }
                        gridView.setTopGlowOffset((int) (currentPanTranslationY + scrollOffsetY));
                        frameLayout.setTranslationY(currentPanTranslationY + scrollOffsetY);
                        searchEmptyView.setTranslationY(currentPanTranslationY + scrollOffsetY);
                        invalidate();
                    }

                    @Override
                    protected void onTransitionEnd() {
                        super.onTransitionEnd();
                        panTranslationMoveLayout = false;
                        previousScrollOffsetY = scrollOffsetY;
                        gridView.setTopGlowOffset(scrollOffsetY);
                        frameLayout.setTranslationY(scrollOffsetY);
                        searchEmptyView.setTranslationY(scrollOffsetY);
                        gridView.setTranslationY(0);
                        searchGridView.setTranslationY(0);
                    }

                    @Override
                    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                        super.onPanTranslationUpdate(y, progress, keyboardVisible);
                        for (int i = 0; i < containerView.getChildCount(); i++) {
                            if (containerView.getChildAt(i) != pickerBottomLayout && containerView.getChildAt(i) != bulletinContainer && containerView.getChildAt(i) != shadow[1] && containerView.getChildAt(i) != sharesCountLayout
                                    && containerView.getChildAt(i) != frameLayout2 && containerView.getChildAt(i) != writeButtonContainer && containerView.getChildAt(i) != selectedCountView) {
                                containerView.getChildAt(i).setTranslationY(y);
                            }
                        }
                        currentPanTranslationY = y;
                        if (fromScrollY != -1) {
                            float p = keyboardVisible ? progress : (1f - progress);
                            scrollOffsetY = (int) (fromScrollY * (1f - p) + toScrollY * p);
                            float translationY = currentPanTranslationY + (fromScrollY - toScrollY) * (1f - p);
                            gridView.setTranslationY(translationY);
                            if (keyboardVisible) {
                                searchGridView.setTranslationY(translationY);
                            } else {
                                searchGridView.setTranslationY(translationY + gridView.getPaddingTop());
                            }
                        } else if (fromOffsetTop != -1) {
                            scrollOffsetY = (int) (fromOffsetTop * (1f - progress) + toOffsetTop * progress);
                            float p = keyboardVisible ? (1f - progress) : progress;
                            if (keyboardVisible) {
                                gridView.setTranslationY(currentPanTranslationY - (fromOffsetTop - toOffsetTop) * progress);
                            } else {
                                gridView.setTranslationY(currentPanTranslationY + (toOffsetTop - fromOffsetTop) * p);
                            }
                        }
                        gridView.setTopGlowOffset((int) (scrollOffsetY + currentPanTranslationY));
                        frameLayout.setTranslationY(scrollOffsetY + currentPanTranslationY);
                        searchEmptyView.setTranslationY(scrollOffsetY + currentPanTranslationY);
                        frameLayout2.invalidate();
                        setCurrentPanTranslationY(currentPanTranslationY);
                        invalidate();
                    }

                    @Override
                    protected boolean heightAnimationEnabled() {
                        if (isDismissed() || !fullyShown) {
                            return false;
                        }
                        return !commentTextView.isPopupVisible();
                    }
                };
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                adjustPanLayoutHelper.setResizableView(this);
                adjustPanLayoutHelper.onAttach();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight;
                if (getLayoutParams().height > 0) {
                    totalHeight = getLayoutParams().height;
                } else {
                    totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                }

                layoutManager.setNeedFixGap(getLayoutParams().height <= 0);
                searchLayoutManager.setNeedFixGap(getLayoutParams().height <= 0);
                if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();

                int size = Math.max(searchAdapter.getItemCount(), listAdapter.getItemCount() - 1);
                int contentSize = dp(103) + dp(48) + Math.max(2, (int) Math.ceil(size / 4.0f)) * dp(103) + backgroundPaddingTop;
                if (topicsGridView.getVisibility() != View.GONE) {
                    int topicsSize = dp(103) + dp(48) + Math.max(2, (int) Math.ceil((shareTopicsAdapter.getItemCount() - 1) / 4.0f)) * dp(103) + backgroundPaddingTop;
                    if (topicsSize > contentSize) {
                        contentSize = AndroidUtilities.lerp(contentSize, topicsSize, topicsGridView.getAlpha());
                    }
                }
                int padding = (contentSize < availableHeight ? 0 : availableHeight - (availableHeight / 5 * 3)) + dp(8);
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(0, padding, 0, dp(48));
                    topicsGridView.setPadding(0, padding, 0, dp(48));
                    ignoreLayout = false;
                }

                if (keyboardVisible && getLayoutParams().height <= 0 && searchGridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    searchGridView.setPadding(0, 0, 0, dp(48));
                    ignoreLayout = false;
                }
                fullHeight = contentSize >= totalHeight;
                topOffset = fullHeight ? 0 : totalHeight - contentSize;
                ignoreLayout = true;
                checkCurrentList(false);
                ignoreLayout = false;

                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                widthSize -= backgroundPaddingLeft * 2;

                int keyboardSize = 0;
                if (!commentTextView.isWaitingForKeyboardOpen() && keyboardSize <= dp(20) && !commentTextView.isPopupShowing() && !commentTextView.isAnimatePopupClosing()) {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                ignoreLayout = true;
                if (keyboardSize <= dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        int paddingBottom;
                        if (keyboardVisible) {
                            paddingBottom = 0;
                        } else {
                            paddingBottom = commentTextView.getEmojiPadding();
                        }
                        heightSize -= paddingBottom;
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                    int visibility = commentTextView.isPopupShowing() ? GONE : VISIBLE;
                    if (pickerBottomLayout != null) {
                        pickerBottomLayout.setVisibility(visibility);
                        if (sharesCountLayout != null) {
                            sharesCountLayout.setVisibility(visibility);
                        }
                    }
                } else {
                    commentTextView.hideEmojiView();
                    if (pickerBottomLayout != null) {
                        pickerBottomLayout.setVisibility(GONE);
                        if (sharesCountLayout != null) {
                            sharesCountLayout.setVisibility(GONE);
                        }
                    }
                }
                ignoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom;
                if (keyboardVisible) {
                    paddingBottom = 0;
                } else {
                    paddingBottom = keyboardSize <= dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
                }
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop() + topOffset;
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - (t + topOffset) - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
                updateLayout();

            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!fullHeight) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < topOffset - dp(30)) {
                        dismiss();
                        return true;
                    }
                } else {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - dp(30)) {
                        dismiss();
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            private boolean lightStatusBar = AndroidUtilities.computePerceivedBrightness(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground)) > .721f;
            private final AnimatedFloat pinnedToTop = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(0, currentPanTranslationY);
                int y = scrollOffsetY - backgroundPaddingTop + dp(6) + topOffset;
                int top = containerViewTop = scrollOffsetY - backgroundPaddingTop - dp(13) + topOffset;
                int height = getMeasuredHeight() + dp(30 + 30) + backgroundPaddingTop;
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                float pinAlpha = 0;
                if (!isFullscreen && Build.VERSION.SDK_INT >= 21) {
                    y += AndroidUtilities.statusBarHeight;
                    final boolean pinnedToTop = fullHeight && top + backgroundPaddingTop < AndroidUtilities.statusBarHeight;
                    top = AndroidUtilities.lerp(top + AndroidUtilities.statusBarHeight, -backgroundPaddingTop, pinAlpha = this.pinnedToTop.set(pinnedToTop));
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (bulletinContainer2 != null) {
                    if (top <= AndroidUtilities.statusBarHeight && bulletinContainer2.getChildCount() > 0) {
                        bulletinContainer2.setTranslationY(0);
                        Bulletin bulletin = Bulletin.getVisibleBulletin();
                        if (bulletin != null) {
                            if (bulletin.getLayout() != null) {
                                bulletin.getLayout().setTop(true);
                            }
                            bulletin.hide();
                        }
                    } else {
                        bulletinContainer2.setTranslationY(Math.max(0, top + backgroundPaddingTop - bulletinContainer2.getTop() - bulletinContainer2.getMeasuredHeight()));
                    }
                }

                if (pinAlpha < 1) {
                    int w = dp(36);
                    rect1.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + dp(4));
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(darkTheme ? Theme.key_voipgroup_scrollUp : Theme.key_sheet_scrollUp));
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (Theme.dialogs_onlineCirclePaint.getAlpha() * (1f - pinAlpha)));
                    canvas.drawRoundRect(rect1, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = getSystemUiVisibility();
                    boolean shouldBeLightStatusBar = lightStatusBar && statusBarHeight > AndroidUtilities.statusBarHeight * .5f;
                    boolean isLightStatusBar = (flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) > 0;
                    if (shouldBeLightStatusBar != isLightStatusBar) {
                        if (shouldBeLightStatusBar) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        } else {
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        setSystemUiVisibility(flags);
                    }
                }

                canvas.restore();
                previousTopOffset = topOffset;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, getPaddingTop() + currentPanTranslationY, getMeasuredWidth(), getMeasuredHeight() + currentPanTranslationY + dp(50));
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        containerView = sizeNotifierFrameLayout;
        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground));

        if (darkTheme && linkToCopy[1] != null) {
            switchView = new SwitchView(context) {
                @Override
                protected void onTabSwitch(int num) {
                    if (pickerBottomLayout == null) {
                        return;
                    }
                    if (num == 0) {
                        pickerBottomLayout.setText(LocaleController.getString(R.string.VoipGroupCopySpeakerLink).toUpperCase());
                    } else {
                        pickerBottomLayout.setText(LocaleController.getString(R.string.VoipGroupCopyListenLink).toUpperCase());
                    }
                }
            };
            frameLayout.addView(switchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 0, 11, 0, 0));
        }

        searchView = new SearchField(context);
        frameLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.BOTTOM | Gravity.LEFT));
        topicsBackActionBar = new ActionBar(context);
        topicsBackActionBar.setOccupyStatusBar(false);
        topicsBackActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        topicsBackActionBar.setTitleColor(getThemedColor(Theme.key_dialogTextBlack));
        topicsBackActionBar.setSubtitleColor(getThemedColor(Theme.key_dialogTextGray2));
        topicsBackActionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        topicsBackActionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        topicsBackActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                onBackPressed();
            }
        });
        topicsBackActionBar.setVisibility(View.GONE);
        frameLayout.addView(topicsBackActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.BOTTOM | Gravity.LEFT));

        topicsGridView = new RecyclerListView(context, resourcesProvider);
        topicsGridView.setLayoutManager(topicsLayoutManager = new GridLayoutManager(context, 4));
        topicsLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0) {
                    return topicsLayoutManager.getSpanCount();
                }
                return 1;
            }
        });
        topicsGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
            }
        });
        topicsGridView.setAdapter(shareTopicsAdapter = new ShareTopicsAdapter(context));
        topicsGridView.setGlowColor(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogScrollGlow));
        topicsGridView.setVerticalScrollBarEnabled(false);
        topicsGridView.setHorizontalScrollBarEnabled(false);
        topicsGridView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        topicsGridView.setSelectorDrawableColor(0);
        topicsGridView.setItemSelectorColorProvider(i -> 0);
        topicsGridView.setPadding(0, 0, 0, dp(48));
        topicsGridView.setClipToPadding(false);
        topicsGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    int pos = holder.getAdapterPosition();
                    outRect.left = pos % 4 == 0 ? 0 : dp(4);
                    outRect.right = pos % 4 == 3 ? 0 : dp(4);
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        topicsGridView.setOnItemClickListener((view, position) -> {
            TLRPC.TL_forumTopic topic = shareTopicsAdapter.getItem(position);
            if (topic == null || selectedTopicDialog == null) {
                return;
            }

            long dialogId = selectedTopicDialog.id;
            TLRPC.Dialog dialog = selectedTopicDialog;

            selectedDialogs.put(dialogId, dialog);
            selectedDialogTopics.put(dialog, topic);
            updateSelectedCount(2);

            if (searchIsVisible || searchWasVisibleBeforeTopics) {
                TLRPC.Dialog existingDialog = listAdapter.dialogsMap.get(dialog.id);
                if (existingDialog == null) {
                    listAdapter.dialogsMap.put(dialog.id, dialog);
                    listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, dialog);
                }
                listAdapter.notifyDataSetChanged();
                updateSearchAdapter = false;
                searchView.searchEditText.setText("");
                checkCurrentList(false);
            }
            for (int i = 0; i < getMainGridView().getChildCount(); i++) {
                View child = getMainGridView().getChildAt(i);

                if (child instanceof ShareDialogCell && ((ShareDialogCell) child).getCurrentDialog() == selectedTopicDialog.id) {
                    ShareDialogCell cell = (ShareDialogCell) child;

                    if (cell != null) {
                        cell.setTopic(topic, true);
                        cell.setChecked(true, true);
                    }
                }
            }
            collapseTopics();
        });
        topicsGridView.setVisibility(View.GONE);
        containerView.addView(topicsGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        gridView = new RecyclerListView(context, resourcesProvider) {

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= dp(darkTheme && linkToCopy[1] != null ? 111 : 58) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }

            @Override
            public void draw(Canvas canvas) {
                if (topicsGridView.getVisibility() != View.GONE) {
                    canvas.save();
                    canvas.clipRect(0, scrollOffsetY + dp(darkTheme && linkToCopy[1] != null ? 111 : 58), getWidth(), getHeight());
                }
                super.draw(canvas);
                if (topicsGridView.getVisibility() != View.GONE) {
                    canvas.restore();
                }
            }
        };
        gridView.setSelectorDrawableColor(0);
        gridView.setItemSelectorColorProvider(i -> 0);
        gridView.setPadding(0, 0, 0, dp(48));
        gridView.setClipToPadding(false);
        gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 4));
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0) {
                    return layoutManager.getSpanCount();
                }
                return 1;
            }
        });
        gridView.setHorizontalScrollBarEnabled(false);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    int pos = holder.getAdapterPosition();
                    outRect.left = pos % 4 == 0 ? 0 : dp(4);
                    outRect.right = pos % 4 == 3 ? 0 : dp(4);
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        gridView.setAdapter(listAdapter = new ShareDialogsAdapter(context));
        gridView.setGlowColor(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogScrollGlow));
        gridView.setOnItemClickListener((view, position) -> {
            if (position < 0) {
                return;
            }
            TLRPC.Dialog dialog = listAdapter.getItem(position);
            if (dialog == null) {
                return;
            }
            selectDialog(view, dialog);
        });
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().getLayout() != null && Bulletin.getVisibleBulletin().getLayout().getParent() instanceof View && ((View) Bulletin.getVisibleBulletin().getLayout().getParent()).getParent() == bulletinContainer2) {
                    Bulletin.hideVisible();
                }
            }
        });

        searchGridView = new RecyclerListView(context, resourcesProvider) {

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= dp(darkTheme && linkToCopy[1] != null ? 111 : 58) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }

            @Override
            public void draw(Canvas canvas) {
                if (topicsGridView.getVisibility() != View.GONE) {
                    canvas.save();
                    canvas.clipRect(0, scrollOffsetY + dp(darkTheme && linkToCopy[1] != null ? 111 : 58), getWidth(), getHeight());
                }
                super.draw(canvas);
                if (topicsGridView.getVisibility() != View.GONE) {
                    canvas.restore();
                }
            }
        };
        searchGridView.setItemSelectorColorProvider(i -> 0);
        searchGridView.setSelectorDrawableColor(0);
        searchGridView.setPadding(0, 0, 0, dp(48));
        searchGridView.setClipToPadding(false);
        searchGridView.setLayoutManager(searchLayoutManager = new FillLastGridLayoutManager(getContext(), 4, 0, searchGridView));
        searchLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return searchAdapter.getSpanSize(4, position);
            }
        });
        searchGridView.setOnItemClickListener((view, position) -> {
            if (position < 0) {
                return;
            }
            TLRPC.Dialog dialog = searchAdapter.getItem(position);
            if (dialog == null) {
                return;
            }
            selectDialog(view, dialog);
        });
        searchGridView.setHasFixedSize(true);
        searchGridView.setItemAnimator(null);
        searchGridView.setHorizontalScrollBarEnabled(false);
        searchGridView.setVerticalScrollBarEnabled(false);
        searchGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
            }
        });
        searchGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                final RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    if (holder.getItemViewType() != 5) {
                        outRect.left = outRect.right = 0;
                    } else {
                        final int pos = holder.getAdapterPosition();
                        outRect.left = pos % 4 == 0 ? 0 : dp(4);
                        outRect.right = pos % 4 == 3 ? 0 : dp(4);
                    }
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        searchGridView.setAdapter(searchAdapter);
        searchGridView.setGlowColor(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogScrollGlow));

        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(searchGridView, true);

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, resourcesProvider);
        flickerLoadingView.setViewType(FlickerLoadingView.SHARE_ALERT_TYPE);
        if (darkTheme) {
            flickerLoadingView.setColors(Theme.key_voipgroup_inviteMembersBackground, Theme.key_voipgroup_searchBackground, -1);
        }
        searchEmptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider);
        searchEmptyView.addView(flickerLoadingView, 0);
        searchEmptyView.setAnimateLayoutChange(true);
        searchEmptyView.showProgress(false, false);
        if (darkTheme) {
            searchEmptyView.title.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
        }
        searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
        searchGridView.setEmptyView(searchEmptyView);
        searchGridView.setHideIfEmpty(false);
        searchGridView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));
        containerView.addView(searchGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = dp(darkTheme && linkToCopy[1] != null ? 111 : 58);
        shadow[0] = new View(context);
        shadow[0].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadow[0].setAlpha(0.0f);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, darkTheme && linkToCopy[1] != null ? 111 : 58, Gravity.LEFT | Gravity.TOP));

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = dp(48);
        shadow[1] = new View(context);
        shadow[1].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        containerView.addView(shadow[1], frameLayoutParams);

        if (isChannel || linkToCopy[0] != null) {
            pickerBottomLayout = new TextView(context);
            pickerBottomLayout.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground), getThemedColor(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_listSelector)));
            pickerBottomLayout.setTextColor(getThemedColor(darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2));
            pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pickerBottomLayout.setPadding(dp(18), 0, dp(18), 0);
            pickerBottomLayout.setTypeface(AndroidUtilities.bold());
            pickerBottomLayout.setGravity(Gravity.CENTER);
            if (darkTheme && linkToCopy[1] != null) {
                pickerBottomLayout.setText(LocaleController.getString(R.string.VoipGroupCopySpeakerLink).toUpperCase());
            } else {
                pickerBottomLayout.setText(LocaleController.getString(R.string.CopyLink).toUpperCase());
            }
            pickerBottomLayout.setOnClickListener(v -> {
                if (selectedDialogs.size() == 0 && (isChannel || linkToCopy[0] != null)) {
                    dismiss();
                    if (linkToCopy[0] == null && loadingLink) {
                        copyLinkOnEnd = true;
                        Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString(R.string.Loading), Toast.LENGTH_SHORT).show();
                    } else {
                        copyLink(ShareAlert.this.getContext());
                    }
                }
            });
            containerView.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

            if (parentFragment != null && ChatObject.hasAdminRights(parentFragment.getCurrentChat()) && sendingMessageObjects.size() > 0 && sendingMessageObjects.get(0).messageOwner.forwards > 0) {
                MessageObject messageObject = sendingMessageObjects.get(0);
                if (!messageObject.isForwarded()) {
                    sharesCountLayout = new LinearLayout(context);
                    sharesCountLayout.setOrientation(LinearLayout.HORIZONTAL);
                    sharesCountLayout.setGravity(Gravity.CENTER_VERTICAL);
                    sharesCountLayout.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_listSelector), 2));
                    containerView.addView(sharesCountLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.RIGHT | Gravity.BOTTOM, 6, 0, -6, 0));
                    sharesCountLayout.setOnClickListener(view -> parentFragment.presentFragment(new MessageStatisticActivity(messageObject)));

                    ImageView imageView = new ImageView(context);
                    imageView.setImageResource(R.drawable.share_arrow);
                    imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2), PorterDuff.Mode.MULTIPLY));
                    sharesCountLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

                    TextView textView = new TextView(context);
                    textView.setText(String.format("%d", messageObject.messageOwner.forwards));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTextColor(getThemedColor(darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2));
                    textView.setGravity(Gravity.CENTER_VERTICAL);
                    textView.setTypeface(AndroidUtilities.bold());
                    sharesCountLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 8, 0, 20, 0));
                }
            }
        } else {
            shadow[1].setAlpha(0.0f);
        }

        bulletinContainer = new FrameLayout(context);
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, pickerBottomLayout != null ? 48 : 0));

        bulletinContainer2 = new FrameLayout(context);
        containerView.addView(bulletinContainer2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        frameLayout2 = new FrameLayout(context) {

            private final Paint p = new Paint();
            private int color;

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility != View.VISIBLE) {
                    shadow[1].setTranslationY(0);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                invalidate();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (chatActivityEnterViewAnimateFromTop != 0 && chatActivityEnterViewAnimateFromTop != frameLayout2.getTop() + chatActivityEnterViewAnimateFromTop) {
                    if (topBackgroundAnimator != null) {
                        topBackgroundAnimator.cancel();
                    }
                    captionEditTextTopOffset = chatActivityEnterViewAnimateFromTop - (frameLayout2.getTop() + captionEditTextTopOffset);
                    topBackgroundAnimator = ValueAnimator.ofFloat(captionEditTextTopOffset, 0);
                    topBackgroundAnimator.addUpdateListener(valueAnimator -> {
                        captionEditTextTopOffset = (float) valueAnimator.getAnimatedValue();
                        frameLayout2.invalidate();
                        invalidate();
                    });
                    topBackgroundAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    topBackgroundAnimator.setDuration(200);
                    topBackgroundAnimator.start();
                    chatActivityEnterViewAnimateFromTop = 0;
                }
                float alphaOffset = (frameLayout2.getMeasuredHeight() - dp(48)) * (1f - getAlpha());
                shadow[1].setTranslationY(-(frameLayout2.getMeasuredHeight() - dp(48)) + captionEditTextTopOffset + currentPanTranslationY + alphaOffset);

//                int newColor = getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground);
//                if (color != newColor) {
//                    color = newColor;
//                    p.setColor(color);
//                }
//                canvas.drawRect(0, captionEditTextTopOffset + alphaOffset, getMeasuredWidth(), getMeasuredHeight(), p);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        frameLayout2.setWillNotDraw(false);
        frameLayout2.setAlpha(0.0f);
        frameLayout2.setVisibility(View.INVISIBLE);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, true, resourcesProvider) {

            private boolean shouldAnimateEditTextWithBounds;
            private int messageEditTextPredrawHeigth;
            private int messageEditTextPredrawScrollY;
            private ValueAnimator messageEditTextAnimator;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (shouldAnimateEditTextWithBounds) {
                    EditTextCaption editText = commentTextView.getEditText();
                    float dy = (messageEditTextPredrawHeigth - editText.getMeasuredHeight()) + (messageEditTextPredrawScrollY - editText.getScrollY());
                    editText.setOffsetY(editText.getOffsetY() - dy);
                    ValueAnimator a = ValueAnimator.ofFloat(editText.getOffsetY(), 0);
                    a.addUpdateListener(animation -> editText.setOffsetY((float) animation.getAnimatedValue()));
                    if (messageEditTextAnimator != null) {
                        messageEditTextAnimator.cancel();
                    }
                    messageEditTextAnimator = a;
                    a.setDuration(200);
                    a.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    a.start();
                    shouldAnimateEditTextWithBounds = false;
                }
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                if (!TextUtils.isEmpty(getEditText().getText())) {
                    shouldAnimateEditTextWithBounds = true;
                    messageEditTextPredrawHeigth = getEditText().getMeasuredHeight();
                    messageEditTextPredrawScrollY = getEditText().getScrollY();
                    invalidate();
                } else {
                    getEditText().animate().cancel();
                    getEditText().setOffsetY(0);
                    shouldAnimateEditTextWithBounds = false;
                }
                chatActivityEnterViewAnimateFromTop = frameLayout2.getTop() + captionEditTextTopOffset;
                frameLayout2.invalidate();
            }

            @Override
            protected void showPopup(int show) {
                super.showPopup(show);
                if (darkTheme) {
                    navBarColorKey = -1;
                    AndroidUtilities.setNavigationBarColor(ShareAlert.this.getWindow(), ShareAlert.this.getThemedColor(Theme.key_windowBackgroundGray), true, color -> {
                        ShareAlert.this.setOverlayNavBarColor(navBarColor = color);
                    });
                }
            }

            @Override
            public void hidePopup(boolean byBackButton) {
                super.hidePopup(byBackButton);
                if (darkTheme) {
                    navBarColorKey = -1;
                    AndroidUtilities.setNavigationBarColor(ShareAlert.this.getWindow(), ShareAlert.this.getThemedColor(Theme.key_voipgroup_inviteMembersBackground), true, color -> {
                        ShareAlert.this.setOverlayNavBarColor(navBarColor = color);
                    });
                }
            }
        };
        if (darkTheme) {
            commentTextView.getEditText().setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            commentTextView.getEditText().setCursorColor(getThemedColor(Theme.key_voipgroup_nameText));
        }
        commentTextView.setBackgroundColor(backgroundColor);
        commentTextView.setHint(LocaleController.getString(R.string.ShareComment));
        commentTextView.onResume();
        commentTextView.setPadding(0, 0, dp(84), 0);
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        frameLayout2.setClipChildren(false);
        frameLayout2.setClipToPadding(false);
        commentTextView.setClipChildren(false);

        writeButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size()));
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        writeButtonContainer.setFocusable(true);
        writeButtonContainer.setFocusableInTouchMode(true);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        containerView.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 10));

        ImageView writeButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(dp(56), getThemedColor(Theme.key_dialogFloatingButton), getThemedColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(dp(56), dp(56));
            drawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(drawable);
        writeButton.setImageResource(R.drawable.attach_send);
        writeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        writeButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, dp(56), dp(56));
                }
            });
        }
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));
        writeButton.setOnClickListener(v -> sendInternal(true));
        writeButton.setOnLongClickListener(v -> {
            return onSendLongClick(writeButton);
        });

        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, selectedDialogs.size()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(dp(16) + textSize, dp(24));
                int cx = getMeasuredWidth() / 2;
                int cy = getMeasuredHeight() / 2;

                textPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBoxCheck));
                paint.setColor(getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, dp(12), dp(12), paint);

                paint.setColor(getThemedColor(Theme.key_dialogFloatingButton));
                rect.set(cx - size / 2 + dp(2), dp(2), cx + size / 2 - dp(2), getMeasuredHeight() - dp(2));
                canvas.drawRoundRect(rect, dp(10), dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
        containerView.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        updateSelectedCount(0);

        DialogsActivity.loadDialogs(AccountInstance.getInstance(currentAccount));
        if (listAdapter.dialogs.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        }

        DialogsSearchAdapter.loadRecentSearch(currentAccount, 0, new DialogsSearchAdapter.OnRecentSearchLoaded() {
            @Override
            public void setRecentSearch(ArrayList<DialogsSearchAdapter.RecentSearchObject> arrayList, LongSparseArray<DialogsSearchAdapter.RecentSearchObject> hashMap) {
                if (arrayList != null) {
                    for (int i = 0; i < arrayList.size(); ++i) {
                        DialogsSearchAdapter.RecentSearchObject recentSearchObject = arrayList.get(i);
                        if (recentSearchObject.object instanceof TLRPC.Chat && !ChatObject.canWriteToChat((TLRPC.Chat) recentSearchObject.object)) {
                            arrayList.remove(i);
                            i--;
                        }
                    }
                }
                recentSearchObjects = arrayList;
                recentSearchObjectsById = hashMap;
                for (int a = 0; a < recentSearchObjects.size(); a++) {
                    DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(a);
                    if (recentSearchObject.object instanceof TLRPC.User) {
                        MessagesController.getInstance(currentAccount).putUser((TLRPC.User) recentSearchObject.object, true);
                    } else if (recentSearchObject.object instanceof TLRPC.Chat) {
                        MessagesController.getInstance(currentAccount).putChat((TLRPC.Chat) recentSearchObject.object, true);
                    } else if (recentSearchObject.object instanceof TLRPC.EncryptedChat) {
                        MessagesController.getInstance(currentAccount).putEncryptedChat((TLRPC.EncryptedChat) recentSearchObject.object, true);
                    }
                }
                searchAdapter.notifyDataSetChanged();
            }
        });
        MediaDataController.getInstance(currentAccount).loadHints(true);

        AndroidUtilities.updateViewVisibilityAnimated(gridView, true, 1f, false);
        AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false, 1f, false);
    }

    protected void onShareStory(View cell) {

    }
    
    private void showPremiumBlockedToast(View view, long dialogId) {
        AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        String username = "";
        if (dialogId >= 0) {
            username = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        }
        Bulletin bulletin;
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            bulletin = BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)));
        } else {
            bulletin = BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)), LocaleController.getString(R.string.UserBlockedNonPremiumButton), () -> {
                Runnable openPremium = () -> {
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment != null) {
                        BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                        params.transitionFromLeft = true;
                        params.allowNestedScroll = false;
                        lastFragment.showAsSheet(new PremiumPreviewFragment("noncontacts"), params);
                    }
                };
                if (isKeyboardVisible()) {
                    if (searchView != null) {
                        AndroidUtilities.hideKeyboard(searchView.searchEditText);
                    }
                    AndroidUtilities.runOnUIThread(openPremium, 300);
                } else {
                    openPremium.run();
                }
            });
        }
        bulletin.show();
    }

    private int shiftDp = 4;
    private void selectDialog(View cell, TLRPC.Dialog dialog) {
        if (dialog instanceof ShareDialogsAdapter.MyStoryDialog) {
            onShareStory(cell);
            return;
        }
        if (dialog != null && (cell instanceof ShareDialogCell && ((ShareDialogCell) cell).isBlocked() || cell instanceof ProfileSearchCell && ((ProfileSearchCell) cell).isBlocked())) {
            showPremiumBlockedToast(cell, dialog.id);
            return;
        }
        if (topicsGridView.getVisibility() != View.GONE || parentActivity == null) {
            return;
        }

        if (DialogObject.isChatDialog(dialog.id)) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
            if (ChatObject.isChannel(chat) && !chat.megagroup && (!ChatObject.isCanWriteToChannel(-dialog.id, currentAccount) || hasPoll == 2)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString(R.string.SendMessageTitle));
                if (hasPoll == 2) {
                    if (isChannel) {
                        builder.setMessage(LocaleController.getString(R.string.PublicPollCantForward));
                    } else if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS)) {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPollsAll));
                    } else {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPolls));
                    }
                } else {
                    builder.setMessage(LocaleController.getString(R.string.ChannelCantSendMessage));
                }
                builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                builder.show();
                return;
            }
        } else if (DialogObject.isEncryptedDialog(dialog.id) && (hasPoll != 0)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(LocaleController.getString(R.string.SendMessageTitle));
            if (hasPoll != 0) {
                builder.setMessage(LocaleController.getString(R.string.PollCantForwardSecretChat));
            } else {
                builder.setMessage(LocaleController.getString(R.string.InvoiceCantForwardSecretChat));
            }
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.show();
            return;
        }
        if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
            selectedDialogs.remove(dialog.id);
            selectedDialogTopics.remove(dialog);
            if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(false, true);
            } else if (cell instanceof ShareDialogCell) {
                ((ShareDialogCell) cell).setChecked(false, true);
            }
            updateSelectedCount(1);
        } else {
            if (DialogObject.isChatDialog(dialog.id) && MessagesController.getInstance(currentAccount).getChat(-dialog.id) != null && MessagesController.getInstance(currentAccount).getChat(-dialog.id).forum) {
                selectedTopicDialog = dialog;
                topicsLayoutManager.scrollToPositionWithOffset(0, scrollOffsetY - topicsGridView.getPaddingTop());
                AtomicReference<Runnable> timeoutRef = new AtomicReference<>();
                NotificationCenter.NotificationCenterDelegate delegate = new NotificationCenter.NotificationCenterDelegate() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void didReceivedNotification(int id, int account, Object... args) {
                        long chatId = (long) args[0];
                        if (chatId == -dialog.id) {
                            boolean animate = shareTopicsAdapter.topics == null && MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id) != null || timeoutRef.get() == null;

                            shareTopicsAdapter.topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id);
                            if (animate) {
                                shareTopicsAdapter.notifyDataSetChanged();
                            }

                            if (shareTopicsAdapter.topics != null) {
                                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
                            }

                            if (animate) {
                                topicsGridView.setVisibility(View.VISIBLE);
                                topicsGridView.setAlpha(0);
                                topicsBackActionBar.setVisibility(View.VISIBLE);
                                topicsBackActionBar.setAlpha(0);
                                topicsBackActionBar.setTitle(MessagesController.getInstance(currentAccount).getChat(-dialog.id).title);
                                topicsBackActionBar.setSubtitle(LocaleController.getString(R.string.SelectTopic));
                                searchWasVisibleBeforeTopics = searchIsVisible;

                                if (topicsAnimation != null) {
                                    topicsAnimation.cancel();
                                }

                                int[] loc = new int[2];
                                topicsAnimation = new SpringAnimation(new FloatValueHolder(0))
                                        .setSpring(new SpringForce(1000)
                                                .setStiffness(parentFragment != null && parentFragment.shareAlertDebugTopicsSlowMotion ? 10f : 800f)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
                                topicsAnimation.addUpdateListener((animation, value, velocity) -> {
                                    value /= 1000;

                                    invalidateTopicsAnimation(cell, loc, value);
                                });
                                topicsAnimation.addEndListener((animation, canceled, value, velocity) -> {
                                    gridView.setVisibility(View.GONE);
                                    searchGridView.setVisibility(View.GONE);
                                    searchView.setVisibility(View.GONE);

                                    topicsAnimation = null;
                                });
                                topicsAnimation.start();

                                if (timeoutRef.get() != null) {
                                    AndroidUtilities.cancelRunOnUIThread(timeoutRef.get());
                                    timeoutRef.set(null);
                                }
                            }
                        }
                    }
                };
                timeoutRef.set(() -> {
                    timeoutRef.set(null);
                    delegate.didReceivedNotification(NotificationCenter.topicsDidLoaded, currentAccount, -dialog.id);
                });
                NotificationCenter.getInstance(currentAccount).addObserver(delegate, NotificationCenter.topicsDidLoaded);
                if (MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id) != null) {
                    delegate.didReceivedNotification(NotificationCenter.topicsDidLoaded, currentAccount, -dialog.id);
                } else {
                    MessagesController.getInstance(currentAccount).getTopicsController().loadTopics(-dialog.id);
                    AndroidUtilities.runOnUIThread(timeoutRef.get(), 300);
                }
                return;
            }

            selectedDialogs.put(dialog.id, dialog);
            if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(true, true);
            } else if (cell instanceof ShareDialogCell) {
                ((ShareDialogCell) cell).setChecked(true, true);
            }
            updateSelectedCount(2);
            long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
            if (searchIsVisible) {
                TLRPC.Dialog existingDialog = listAdapter.dialogsMap.get(dialog.id);
                if (existingDialog == null) {
                    listAdapter.dialogsMap.put(dialog.id, dialog);
                    listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, dialog);
                } else if (existingDialog.id != selfUserId) {
                    listAdapter.dialogs.remove(existingDialog);
                    listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, existingDialog);
                }
                listAdapter.notifyDataSetChanged();
                updateSearchAdapter = false;
                searchView.searchEditText.setText("");
                checkCurrentList(false);
                searchView.hideKeyboard();
            }
        }
        if (searchAdapter != null && searchAdapter.categoryAdapter != null) {
            searchAdapter.categoryAdapter.notifyItemRangeChanged(0, searchAdapter.categoryAdapter.getItemCount());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void collapseTopics() {
        if (selectedTopicDialog == null) {
            return;
        }
        TLRPC.Dialog dialog = selectedTopicDialog;
        selectedTopicDialog = null;

        View cell = null;
        for (int i = 0; i < getMainGridView().getChildCount(); i++) {
            View child = getMainGridView().getChildAt(i);

            if (child instanceof ShareDialogCell && ((ShareDialogCell) child).getCurrentDialog() == dialog.id) {
                cell = child;
            }
        }

        if (cell == null) {
            return;
        }

        if (topicsAnimation != null) {
            topicsAnimation.cancel();
        }

        getMainGridView().setVisibility(View.VISIBLE);
        searchView.setVisibility(View.VISIBLE);

        if (searchIsVisible || searchWasVisibleBeforeTopics) {
            sizeNotifierFrameLayout.adjustPanLayoutHelper.ignoreOnce();
            searchView.searchEditText.requestFocus();
            AndroidUtilities.showKeyboard(searchView.searchEditText);
        }

        int[] loc = new int[2];
        View finalCell = cell;
        topicsAnimation = new SpringAnimation(new FloatValueHolder(1000))
                .setSpring(new SpringForce(0)
                        .setStiffness(parentFragment != null && parentFragment.shareAlertDebugTopicsSlowMotion ? 10f : 800f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        topicsAnimation.addUpdateListener((animation, value, velocity) -> {
            value /= 1000;

            invalidateTopicsAnimation(finalCell, loc, value);
        });
        topicsAnimation.addEndListener((animation, canceled, value, velocity) -> {
            topicsGridView.setVisibility(View.GONE);
            topicsBackActionBar.setVisibility(View.GONE);

            shareTopicsAdapter.topics = null;
            shareTopicsAdapter.notifyDataSetChanged();

            topicsAnimation = null;
            searchWasVisibleBeforeTopics = false;
        });
        topicsAnimation.start();
    }

    private void invalidateTopicsAnimation(View cell, int[] loc, float value) {
        topicsGridView.setPivotX(cell.getX() + cell.getWidth() / 2f);
        topicsGridView.setPivotY(cell.getY() + cell.getHeight() / 2f);
        topicsGridView.setScaleX(0.75f + value * 0.25f);
        topicsGridView.setScaleY(0.75f + value * 0.25f);
        topicsGridView.setAlpha(value);

        RecyclerListView mainGridView = getMainGridView();
        mainGridView.setPivotX(cell.getX() + cell.getWidth() / 2f);
        mainGridView.setPivotY(cell.getY() + cell.getHeight() / 2f);
        mainGridView.setScaleX(1f + value * 0.25f);
        mainGridView.setScaleY(1f + value * 0.25f);
        mainGridView.setAlpha(1f - value);

        searchView.setPivotX(searchView.getWidth() / 2f);
        searchView.setPivotY(0);
        searchView.setScaleX(0.9f + (1f - value) * 0.1f);
        searchView.setScaleY(0.9f + (1f - value) * 0.1f);
        searchView.setAlpha(1f - value);

        topicsBackActionBar.getBackButton().setTranslationX(-dp(16) * (1f - value));
        topicsBackActionBar.getTitleTextView().setTranslationY(dp(16) * (1f - value));
        topicsBackActionBar.getSubtitleTextView().setTranslationY(dp(16) * (1f - value));
        topicsBackActionBar.setAlpha(value);

        topicsGridView.getLocationInWindow(loc);
        float moveValue = CubicBezierInterpolator.EASE_OUT.getInterpolation(value);
        for (int i = 0; i < mainGridView.getChildCount(); i++) {
            View v = mainGridView.getChildAt(i);
            if (v instanceof ShareDialogCell) {
                v.setTranslationX((v.getX() - cell.getX()) * 0.5f * moveValue);
                v.setTranslationY((v.getY() - cell.getY()) * 0.5f * moveValue);

                if (v != cell) {
                    v.setAlpha(1f - Math.min(value, 0.5f) / 0.5f);
                } else {
                    v.setAlpha(1f - value);
                }
            }
        }
        for (int i = 0; i < topicsGridView.getChildCount(); i++) {
            View v = topicsGridView.getChildAt(i);
            if (v instanceof ShareTopicCell) {
                v.setTranslationX((float) (-(v.getX() - cell.getX()) * Math.pow(1f - moveValue, 2)));
                v.setTranslationY((float) (-(v.getY() + topicsGridView.getTranslationY() - cell.getY()) * Math.pow(1f - moveValue, 2)));
            }
        }
        containerView.requestLayout();
        mainGridView.invalidate();
    }

    @Override
    public int getContainerViewHeight() {
        return containerView.getMeasuredHeight() - containerViewTop;
    }

    private boolean showSendersName = true;
    private ActionBarPopupWindow sendPopupWindow;
    private boolean onSendLongClick(View view) {
        if (parentActivity == null) {
            return false;
        }
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        if (sendingMessageObjects != null) {
            ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout1 = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider);
            if (darkTheme) {
                sendPopupLayout1.setBackgroundColor(getThemedColor(Theme.key_voipgroup_inviteMembersBackground));
            }
            sendPopupLayout1.setAnimationEnabled(false);
            sendPopupLayout1.setOnTouchListener(new View.OnTouchListener() {
                private android.graphics.Rect popupRect = new android.graphics.Rect();

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            v.getHitRect(popupRect);
                            if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                sendPopupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            sendPopupLayout1.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                    sendPopupWindow.dismiss();
                }
            });
            sendPopupLayout1.setShownFromBottom(false);

            ActionBarMenuSubItem showSendersNameView = new ActionBarMenuSubItem(getContext(), true, true, false, resourcesProvider);
            if (darkTheme) {
                showSendersNameView.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            }
            sendPopupLayout1.addView(showSendersNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            showSendersNameView.setTextAndIcon(false ? LocaleController.getString(R.string.ShowSenderNames) : LocaleController.getString(R.string.ShowSendersName), 0);
            showSendersNameView.setChecked(showSendersName = true);

            ActionBarMenuSubItem hideSendersNameView = new ActionBarMenuSubItem(getContext(), true, false, true, resourcesProvider);
            if (darkTheme) {
                hideSendersNameView.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            }
            sendPopupLayout1.addView(hideSendersNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            hideSendersNameView.setTextAndIcon(false ? LocaleController.getString(R.string.HideSenderNames) : LocaleController.getString(R.string.HideSendersName), 0);
            hideSendersNameView.setChecked(!showSendersName);
            showSendersNameView.setOnClickListener(e -> {
                showSendersNameView.setChecked(showSendersName = true);
                hideSendersNameView.setChecked(!showSendersName);
            });
            hideSendersNameView.setOnClickListener(e -> {
                showSendersNameView.setChecked(showSendersName = false);
                hideSendersNameView.setChecked(!showSendersName);
            });
            sendPopupLayout1.setupRadialSelectors(getThemedColor(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_dialogButtonSelector));

            layout.addView(sendPopupLayout1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, -8));
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout2 = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider);
        if (darkTheme) {
            sendPopupLayout2.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground));
        }
        sendPopupLayout2.setAnimationEnabled(false);
        sendPopupLayout2.setOnTouchListener(new View.OnTouchListener() {
            private android.graphics.Rect popupRect = new android.graphics.Rect();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        v.getHitRect(popupRect);
                        if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                            sendPopupWindow.dismiss();
                        }
                    }
                }
                return false;
            }
        });
        sendPopupLayout2.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
        });
        sendPopupLayout2.setShownFromBottom(false);

        ActionBarMenuSubItem sendWithoutSound = new ActionBarMenuSubItem(getContext(), true, true, resourcesProvider);
        if (darkTheme) {
            sendWithoutSound.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            sendWithoutSound.setIconColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        }
        sendWithoutSound.setTextAndIcon(LocaleController.getString(R.string.SendWithoutSound), R.drawable.input_notify_off);
        sendWithoutSound.setMinimumWidth(dp(196));
        sendPopupLayout2.addView(sendWithoutSound, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        sendWithoutSound.setOnClickListener(v -> {
            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
            sendInternal(false);
        });
        ActionBarMenuSubItem sendMessage = new ActionBarMenuSubItem(getContext(), true, true, resourcesProvider);
        if (darkTheme) {
            sendMessage.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            sendMessage.setIconColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        }
        sendMessage.setTextAndIcon(LocaleController.getString(R.string.SendMessage), R.drawable.msg_send);
        sendMessage.setMinimumWidth(dp(196));
        sendPopupLayout2.addView(sendMessage, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        sendMessage.setOnClickListener(v -> {
            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
            sendInternal(true);
        });
        sendPopupLayout2.setupRadialSelectors(getThemedColor(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_dialogButtonSelector));

        layout.addView(sendPopupLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sendPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        sendPopupWindow.setAnimationEnabled(false);
        sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
        sendPopupWindow.setOutsideTouchable(true);
        sendPopupWindow.setClippingEnabled(true);
        sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        sendPopupWindow.getContentView().setFocusableInTouchMode(true);
        SharedConfig.removeScheduledOrNoSoundHint();

        layout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        sendPopupWindow.setFocusable(true);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        int y;
        if (keyboardVisible && parentFragment != null && parentFragment.contentView.getMeasuredHeight() > dp(58)) {
            y = location[1] + view.getMeasuredHeight();
        } else {
            y = location[1] - layout.getMeasuredHeight() - dp(2);
        }
        sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - layout.getMeasuredWidth() + dp(8), y);
        sendPopupWindow.dimBehind();
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

        return true;
    }

    protected void sendInternal(boolean withSound) {
        for (int a = 0; a < selectedDialogs.size(); a++) {
            long key = selectedDialogs.keyAt(a);
            if (AlertsCreator.checkSlowMode(getContext(), currentAccount, key, frameLayout2.getTag() != null && commentTextView.length() > 0)) {
                return;
            }
        }

        CharSequence[] text = new CharSequence[] { commentTextView.getText() };
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(text, true);
        if (sendingMessageObjects != null) {
            List<Long> removeKeys = new ArrayList<>();
            for (int a = 0; a < selectedDialogs.size(); a++) {
                long key = selectedDialogs.keyAt(a);
                TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                MessageObject replyTopMsg = topic != null ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;
                if (replyTopMsg != null) {
                    replyTopMsg.isTopicMainMessage = true;
                }
                if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, null, false));
                }
                int result = SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, key, !showSendersName,false, withSound, 0, replyTopMsg);
                if (result != 0) {
                    removeKeys.add(key);
                }
                if (selectedDialogs.size() == 1) {
                    AlertsCreator.showSendMediaAlert(result, parentFragment, null);

                    if (result != 0) {
                        break;
                    }
                }
            }
            for (long key : removeKeys) {
                TLRPC.Dialog dialog = selectedDialogs.get(key);
                selectedDialogs.remove(key);
                if (dialog != null) {
                    selectedDialogTopics.remove(dialog);
                }
            }
            if (!selectedDialogs.isEmpty()) {
                onSend(selectedDialogs, sendingMessageObjects.size(), selectedDialogs.size() == 1 ? selectedDialogTopics.get(selectedDialogs.valueAt(0)) : null);
            }
        } else {
            int num;
            if (switchView != null) {
                num = switchView.currentTab;
            } else {
                num = 0;
            }
            if (storyItem != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    long key = selectedDialogs.keyAt(a);
                    TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                    MessageObject replyTopMsg = topic != null ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;

                    SendMessagesHelper.SendMessageParams params;
                    if (storyItem == null) {
                        if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                            params = SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, null, false);
                        } else {
                            params = SendMessagesHelper.SendMessageParams.of(sendingText[num], key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, null, false);
                        }
                    } else {
                        if (frameLayout2.getTag() != null && commentTextView.length() > 0 && text[0] != null) {
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text[0].toString(), key, null, replyTopMsg, null, true, null, null, null, withSound, 0, null, false));
                        }
                        params = SendMessagesHelper.SendMessageParams.of(null, key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, null, false);
                        params.sendingStory = storyItem;
                    }
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                }
            } else if (sendingText[num] != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    long key = selectedDialogs.keyAt(a);
                    TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                    MessageObject replyTopMsg = topic != null ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;

                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, null, false));
                    }
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(sendingText[num], key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, null, false));
                }
            }
            onSend(selectedDialogs, 1, selectedDialogTopics.get(selectedDialogs.valueAt(0)));
        }
        if (delegate != null) {
            delegate.didShare();
        }
        dismiss();
    }

    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic) {

    }

    protected boolean doSend(LongSparseArray<TLRPC.Dialog> dids, TLRPC.TL_forumTopic topic) {
        return false;
    }

    private int getCurrentTop() {
        if (gridView.getChildCount() != 0) {
            View child = gridView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
            if (holder != null) {
                return gridView.getPaddingTop() - (holder.getLayoutPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
    }

    private RecyclerListView getMainGridView() {
        return searchIsVisible || searchWasVisibleBeforeTopics ? searchGridView : gridView;
    }

    public void setDelegate(ShareAlertDelegate shareAlertDelegate) {
        delegate = shareAlertDelegate;
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (selectedTopicDialog != null) {
            collapseTopics();
            return;
        }
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (listAdapter != null) {
                listAdapter.fetchDialogs();
            }
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    int lastOffset = Integer.MAX_VALUE;

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (panTranslationMoveLayout) {
            return;
        }
        View child;
        RecyclerListView.Holder holder = null;
        RecyclerListView listView = searchIsVisible ? searchGridView : gridView;

        if (listView.getChildCount() <= 0) {
            return;
        }
        child = listView.getChildAt(0);
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i).getTop() < child.getTop()) {
                child = listView.getChildAt(i);
            }
        }
        holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);

        int top = child.getTop() - dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            lastOffset = child.getTop();
            newOffset = top;
            runShadowAnimation(0, false);
        } else {
            lastOffset = Integer.MAX_VALUE;
            runShadowAnimation(0, true);
        }

        if (topicsGridView.getVisibility() == View.VISIBLE) {
            listView = topicsGridView;

            if (listView.getChildCount() <= 0) {
                return;
            }
            child = listView.getChildAt(0);
            for (int i = 0; i < listView.getChildCount(); i++) {
                if (listView.getChildAt(i).getTop() < child.getTop()) {
                    child = listView.getChildAt(i);
                }
            }
            holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);

            int topicsTop = child.getTop() - dp(8);
            int topicsNewOffset = topicsTop > 0 && holder != null && holder.getAdapterPosition() == 0 ? topicsTop : 0;
            if (topicsTop >= 0 && holder != null && holder.getAdapterPosition() == 0) {
                lastOffset = child.getTop();
                topicsNewOffset = topicsTop;
                runShadowAnimation(0, false);
            } else {
                lastOffset = Integer.MAX_VALUE;
                runShadowAnimation(0, true);
            }
            newOffset = AndroidUtilities.lerp(newOffset, topicsNewOffset, topicsGridView.getAlpha());
        }

        if (scrollOffsetY != newOffset) {
            previousScrollOffsetY = scrollOffsetY;
            gridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            searchGridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            topicsGridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            frameLayout.setTranslationY(scrollOffsetY + currentPanTranslationY);
            searchEmptyView.setTranslationY(scrollOffsetY + currentPanTranslationY);
            containerView.invalidate();
        }
    }

    private void runShadowAnimation(final int num, final boolean show) {
        if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
            shadow[num].setTag(show ? null : 1);
            if (show) {
                shadow[num].setVisibility(View.VISIBLE);
            }
            if (shadowAnimation[num] != null) {
                shadowAnimation[num].cancel();
            }
            shadowAnimation[num] = new AnimatorSet();
            shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        if (!show) {
                            shadow[num].setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation[num] = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }
            });
            shadowAnimation[num].start();
        }
    }

    private void copyLink(Context context) {
        if (exportedMessageLink == null && linkToCopy[0] == null) {
            return;
        }
        try {
            String link;
            if (switchView != null) {
                link = linkToCopy[switchView.currentTab];
            } else {
                link = linkToCopy[0];
            }
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("label", link != null ? link : exportedMessageLink.link);
            clipboard.setPrimaryClip(clip);
            if ((delegate == null || !delegate.didCopy()) && parentActivity instanceof LaunchActivity) {
                final boolean isPrivate = exportedMessageLink != null && exportedMessageLink.link.contains("/c/");
                ((LaunchActivity) parentActivity).showBulletin(factory -> factory.createCopyLinkBulletin(isPrivate, resourcesProvider));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean showCommentTextView(final boolean show) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            writeButtonContainer.setVisibility(View.VISIBLE);
        }
        if (pickerBottomLayout != null) {
            ViewCompat.setImportantForAccessibility(pickerBottomLayout, show ? ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (sharesCountLayout != null) {
            ViewCompat.setImportantForAccessibility(sharesCountLayout, show ? ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, show ? 1.0f : 0.0f));
        if (pickerBottomLayout == null || pickerBottomLayout.getVisibility() != View.VISIBLE) {
            animators.add(ObjectAnimator.ofFloat(shadow[1], View.ALPHA, show ? 1.0f : 0.0f));
        }

        animatorSet.playTogether(animators);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    if (!show) {
                        frameLayout2.setVisibility(View.INVISIBLE);
                        writeButtonContainer.setVisibility(View.INVISIBLE);
                    }
                    animatorSet = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(animatorSet)) {
                    animatorSet = null;
                }
            }
        });
        animatorSet.start();
        return true;
    }

    public void updateSelectedCount(int animated) {
        if (selectedDialogs.size() == 0) {
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false);
        } else {
            selectedCountView.invalidate();
            if (!showCommentTextView(true) && animated != 0) {
                selectedCountView.setPivotX(dp(21));
                selectedCountView.setPivotY(dp(12));
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, animated == 1 ? 1.1f : 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, animated == 1 ? 1.1f : 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
                animatorSet.setDuration(180);
                animatorSet.start();
            } else {
                selectedCountView.setPivotX(0);
                selectedCountView.setPivotY(0);
            }
        }
    }

    @Override
    public void dismiss() {
        if (commentTextView != null) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        fullyShown = false;
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    private class ShareDialogsAdapter extends RecyclerListView.SelectionAdapter {

        private class MyStoryDialog extends TLRPC.Dialog {
            { id = Long.MAX_VALUE; }
        }

        private Context context;
        private int currentCount;
        private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        private LongSparseArray<TLRPC.Dialog> dialogsMap = new LongSparseArray<>();

        public ShareDialogsAdapter(Context context) {
            this.context = context;
            fetchDialogs();
        }

        public void fetchDialogs() {
            dialogs.clear();
            dialogsMap.clear();
            long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
            if (includeStory) {
                MyStoryDialog d = new MyStoryDialog();
                dialogs.add(d);
                dialogsMap.put(d.id, d);
            }
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
            }
            dialogs.addAll(archivedDialogs);
            if (parentFragment != null) {
                switch (parentFragment.shareAlertDebugMode) {
                    case ChatActivity.DEBUG_SHARE_ALERT_MODE_LESS:
                        List<TLRPC.Dialog> sublist = new ArrayList<>(dialogs.subList(0, Math.min(4, dialogs.size())));
                        dialogs.clear();
                        dialogs.addAll(sublist);
                        break;
                    case ChatActivity.DEBUG_SHARE_ALERT_MODE_MORE:
                        while (!dialogs.isEmpty() && dialogs.size() < 80) {
                            dialogs.add(dialogs.get(dialogs.size() - 1));
                        }
                        break;
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            int count = dialogs.size();
            if (count != 0) {
                count++;
            }
            return count;
        }

        public TLRPC.Dialog getItem(int position) {
            position--;
            if (position < 0 || position >= dialogs.size()) {
                return null;
            }
            return dialogs.get(position);
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
                    view = new ShareDialogCell(context, darkTheme ? ShareDialogCell.TYPE_CALL : ShareDialogCell.TYPE_SHARE, resourcesProvider) {
                        @Override
                        protected String repostToCustomName() {
                            if (includeStoryFromMessage) {
                                return LocaleController.getString(R.string.RepostToStory);
                            }
                            return super.repostToCustomName();
                        }
                    };
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(darkTheme && linkToCopy[1] != null ? 109 : 56)));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                TLRPC.Dialog dialog = getItem(position);
                if (dialog == null) return;
                cell.setTopic(selectedDialogTopics.get(dialog), false);
                cell.setDialog(dialog.id, selectedDialogs.indexOfKey(dialog.id) >= 0, null);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            }
            return 0;
        }
    }

    private class ShareTopicsAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private List<TLRPC.TL_forumTopic> topics;

        public ShareTopicsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return topics == null ? 0 : topics.size() + 1;
        }

        public TLRPC.TL_forumTopic getItem(int position) {
            position--;
            if (topics == null || position < 0 || position >= topics.size()) {
                return null;
            }
            return topics.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShareTopicCell(context, resourcesProvider);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, ActionBar.getCurrentActionBarHeight()));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareTopicCell cell = (ShareTopicCell) holder.itemView;
                TLRPC.TL_forumTopic topic = getItem(position);
                cell.setTopic(selectedTopicDialog, topic, selectedDialogs.indexOfKey(topic.id) >= 0, null);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 1 : 0;
        }
    }

    public static class DialogSearchResult {
        public TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public class ShareSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private Runnable searchRunnable2;
        private String lastSearchText;
        private int reqId;
        private int lastReqId;
        private int lastSearchId;

        private int lastGlobalSearchId;
        private int lastLocalSearchId;

        int hintsCell = -1;
        int resentTitleCell = -1;
        int firstEmptyViewCell = -1;
        int recentDialogsStartRow = -1;
        int searchResultsStartRow = -1;
        int lastFilledItem = -1;

        int itemsCount;

        DialogsSearchAdapter.CategoryAdapterRecycler categoryAdapter;
        RecyclerView categoryListView;

        public ShareSearchAdapter(Context context) {
            this.context = context;
            searchAdapterHelper = new SearchAdapterHelper(false) {
                @Override
                protected boolean filter(TLObject obj) {
                    return !(obj instanceof TLRPC.Chat) || ChatObject.canWriteToChat((TLRPC.Chat) obj);
                }
            };
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged(int searchId) {
                    lastGlobalSearchId = searchId;
                    if (lastLocalSearchId != searchId) {
                        searchResult.clear();
                    }
                    int oldItemsCount = lastItemCont;
                    if (getItemCount() == 0 && !searchAdapterHelper.isSearchInProgress() && !internalDialogsIsSearching) {
                        searchEmptyView.showProgress(false, true);
                    } else {
                        recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount);
                    }
                    notifyDataSetChanged();
                    checkCurrentList(true);
                }

                @Override
                public boolean canApplySearchResults(int searchId) {
                    return searchId == lastSearchId;
                }
            });
        }

        boolean internalDialogsIsSearching = false;
        private void searchDialogsInternal(final String query, final int searchId) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                try {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    int resultCount = 0;

                    LongSparseArray<DialogSearchResult> dialogsResult = new LongSparseArray<>();
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400");
                    while (cursor.next()) {
                        long id = cursor.longValue(0);
                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                        dialogSearchResult.date = cursor.intValue(1);
                        dialogsResult.put(id, dialogSearchResult);

                        if (DialogObject.isUserDialog(id)) {
                            if (!usersToLoad.contains(id)) {
                                usersToLoad.add(id);
                            }
                        } else if (DialogObject.isChatDialog(id)) {
                            if (!chatsToLoad.contains(-id)) {
                                chatsToLoad.add(-id);
                            }
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        DialogSearchResult dialogSearchResult = dialogsResult.get(user.id);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
                                        }
                                        dialogSearchResult.object = user;
                                        dialogSearchResult.dialog.id = user.id;
                                        resultCount++;
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            for (int a = 0; a < search.length; a++) {
                                String q = search[a];
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                                            DialogSearchResult dialogSearchResult = dialogsResult.get(-chat.id);
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                            dialogSearchResult.object = chat;
                                            dialogSearchResult.dialog.id = -chat.id;
                                            resultCount++;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    ArrayList<Object> searchResults = new ArrayList<>(resultCount);
                    for (int a = 0; a < dialogsResult.size(); a++) {
                        DialogSearchResult dialogSearchResult = dialogsResult.valueAt(a);
                        if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                            searchResults.add(dialogSearchResult);
                        }
                    }

                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                        long uid = cursor.longValue(3);
                        if (dialogsResult.indexOfKey(uid) >= 0) {
                            continue;
                        }
                        String name = cursor.stringValue(2);
                        String tName = LocaleController.getInstance().getTranslitString(name);
                        if (name.equals(tName)) {
                            tName = null;
                        }
                        String username = null;
                        int usernamePos = name.lastIndexOf(";;;");
                        if (usernamePos != -1) {
                            username = name.substring(usernamePos + 3);
                        }
                        int found = 0;
                        for (String q : search) {
                            if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (username != null && username.startsWith(q)) {
                                found = 2;
                            }
                            if (found != 0) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(1);
                                    }
                                    dialogSearchResult.dialog.id = user.id;
                                    dialogSearchResult.object = user;
                                    if (found == 1) {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                    } else {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
                                    }
                                    searchResults.add(dialogSearchResult);
                                }
                                break;
                            }
                        }
                    }
                    cursor.dispose();

                    Collections.sort(searchResults, (lhs, rhs) -> {
                        DialogSearchResult res1 = (DialogSearchResult) lhs;
                        DialogSearchResult res2 = (DialogSearchResult) rhs;
                        if (res1.date < res2.date) {
                            return 1;
                        } else if (res1.date > res2.date) {
                            return -1;
                        }
                        return 0;
                    });

                    updateSearchResults(searchResults, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        private void updateSearchResults(final ArrayList<Object> result, final int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                int oldItemCount = getItemCount();
                internalDialogsIsSearching = false;
                lastLocalSearchId = searchId;
                if (lastGlobalSearchId != searchId) {
                    searchAdapterHelper.clear();
                }
                if (gridView.getAdapter() != searchAdapter) {
                    topBeforeSwitch = getCurrentTop();
                    searchAdapter.notifyDataSetChanged();
                }
                for (int a = 0; a < result.size(); a++) {
                    DialogSearchResult obj = (DialogSearchResult) result.get(a);
                    if (obj.object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) obj.object;
                        MessagesController.getInstance(currentAccount).putUser(user, true);
                    } else if (obj.object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj.object;
                        MessagesController.getInstance(currentAccount).putChat(chat, true);
                    }
                }
                boolean becomeEmpty = !searchResult.isEmpty() && result.isEmpty();
                boolean isEmpty = searchResult.isEmpty() && result.isEmpty();
                if (becomeEmpty) {
                    topBeforeSwitch = getCurrentTop();
                }
                searchResult = result;
                searchAdapterHelper.mergeResults(searchResult, null);
                int oldItemsCount = lastItemCont;
                if (getItemCount() == 0 && !searchAdapterHelper.isSearchInProgress() && !internalDialogsIsSearching) {
                    searchEmptyView.showProgress(false, true);
                } else {
                    recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount);
                }
                notifyDataSetChanged();
                checkCurrentList(true);

            });
        }

        public void searchDialogs(final String query) {
            if (query != null && query.equals(lastSearchText)) {
                return;
            }
            lastSearchText = query;
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (searchRunnable2 != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable2);
                searchRunnable2 = null;
            }
            searchResult.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, true, true, true, false, 0, false, 0, 0);
            notifyDataSetChanged();
            checkCurrentList(true);

            if (TextUtils.isEmpty(query)) {
                topBeforeSwitch = getCurrentTop();
                lastSearchId = -1;
                internalDialogsIsSearching = false;
            } else {
                internalDialogsIsSearching = true;
                final int searchId = ++lastSearchId;
                searchEmptyView.showProgress(true, true);
                Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                    searchRunnable = null;
                    searchDialogsInternal(query, searchId);
                    AndroidUtilities.runOnUIThread(searchRunnable2 = () -> {
                        searchRunnable2 = null;
                        if (searchId != lastSearchId) {
                            return;
                        }
                        searchAdapterHelper.queryServerSearch(query, true, true, true, true, false, 0, false, 0, searchId);
                    });
                }, 300);
            }
            checkCurrentList(false);
        }

        int lastItemCont;

        @Override
        public int getItemCount() {
            itemsCount = 0;
            hintsCell = -1;
            resentTitleCell = -1;
            recentDialogsStartRow = -1;
            searchResultsStartRow = -1;
            lastFilledItem = -1;

            if (TextUtils.isEmpty(lastSearchText)) {
                firstEmptyViewCell = itemsCount++;
                hintsCell = itemsCount++;

                if (recentSearchObjects.size() > 0) {
                    resentTitleCell = itemsCount++;
                    recentDialogsStartRow = itemsCount;
                    itemsCount += recentSearchObjects.size();
                }
                lastFilledItem = itemsCount++;
                return lastItemCont = itemsCount;
            } else {
                firstEmptyViewCell = itemsCount++;
                searchResultsStartRow = itemsCount;
                itemsCount += (searchResult.size() + searchAdapterHelper.getLocalServerSearch().size());
                if (itemsCount == 1) {
                    firstEmptyViewCell = -1;
                    return lastItemCont = itemsCount = 0;
                }
                lastFilledItem = itemsCount++;
            }
            return lastItemCont = itemsCount;
        }

        public TLRPC.Dialog getItem(int position) {
            if (position >= recentDialogsStartRow && recentDialogsStartRow >= 0) {
                int index = position - recentDialogsStartRow;
                if (index < 0 || index >= recentSearchObjects.size()) {
                    return null;
                }
                DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(index);
                TLObject object = recentSearchObject.object;
                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                if (object instanceof TLRPC.User) {
                    dialog.id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    dialog.id = -((TLRPC.Chat) object).id;
                } else {
                    return null;
                }
                return dialog;
            }
            position--;
            if (position < 0) {
                return null;
            }
            if (position < searchResult.size()) {
                return ((DialogSearchResult) searchResult.get(position)).dialog;
            } else {
                position -= searchResult.size();
            }
            ArrayList<TLObject> arrayList = searchAdapterHelper.getLocalServerSearch();
            if (position < arrayList.size()) {
                TLObject object = arrayList.get(position);
                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                if (object instanceof TLRPC.User) {
                    dialog.id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    dialog.id = -((TLRPC.Chat) object).id;
                } else {
                    return null;
                }
                return dialog;
            }
            return null;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1 || holder.getItemViewType() == 4) {
                return false;
            }
            return true;
        }


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 5: {
                    view = new ShareDialogCell(context, darkTheme ? ShareDialogCell.TYPE_CALL : ShareDialogCell.TYPE_SHARE, resourcesProvider);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case 0: {
                    view = new ProfileSearchCell(context, resourcesProvider).useCustomPaints().showPremiumBlock(true);
                    break;
                }
                default:
                case 1: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(darkTheme && linkToCopy[1] != null ? 109 : 56)));
                    break;
                }
                case 2: {
                    RecyclerListView horizontalListView = new RecyclerListView(context, resourcesProvider) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    categoryListView = horizontalListView;
                    horizontalListView.setItemAnimator(null);
                    horizontalListView.setLayoutAnimation(null);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(context) {
                        @Override
                        public boolean supportsPredictiveItemAnimations() {
                            return false;
                        }
                    };
                    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    horizontalListView.setLayoutManager(layoutManager);
                    horizontalListView.setAdapter(categoryAdapter = new DialogsSearchAdapter.CategoryAdapterRecycler(context, currentAccount, true, true, resourcesProvider) {
                        @Override
                        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                            HintDialogCell cell = (HintDialogCell) holder.itemView;
                            if (darkTheme || forceDarkThemeForHint) {
                                cell.setColors(Theme.key_voipgroup_nameText, Theme.key_voipgroup_inviteMembersBackground);
                            }

                            TLRPC.TL_topPeer peer = MediaDataController.getInstance(currentAccount).hints.get(position);
                            TLRPC.Chat chat = null;
                            TLRPC.User user = null;
                            long did = 0;
                            if (peer.peer.user_id != 0) {
                                did = peer.peer.user_id;
                                user = MessagesController.getInstance(currentAccount).getUser(peer.peer.user_id);
                            } else if (peer.peer.channel_id != 0) {
                                did = -peer.peer.channel_id;
                                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.channel_id);
                            } else if (peer.peer.chat_id != 0) {
                                did = -peer.peer.chat_id;
                                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.chat_id);
                            }
                            boolean animated = did == cell.getDialogId();
                            cell.setTag(did);
                            String name = "";
                            if (user != null) {
                                name = UserObject.getFirstName(user);
                            } else if (chat != null) {
                                name = chat.title;
                            }
                            cell.setDialog(did, true, name);
                            cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, animated);
                        }
                    });
                    horizontalListView.setOnItemClickListener((view1, position) -> {
                        HintDialogCell cell = (HintDialogCell) view1;
                        TLRPC.TL_topPeer peer = MediaDataController.getInstance(currentAccount).hints.get(position);
                        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                        TLRPC.Chat chat = null;
                        TLRPC.User user = null;
                        long did = 0;
                        if (peer.peer.user_id != 0) {
                            did = peer.peer.user_id;
                        } else if (peer.peer.channel_id != 0) {
                            did = -peer.peer.channel_id;
                        } else if (peer.peer.chat_id != 0) {
                            did = -peer.peer.chat_id;
                        }
                        if (cell.isBlocked()) {
                            showPremiumBlockedToast(cell, did);
                            return;
                        }
                        dialog.id = did;
                        selectDialog(null, dialog);
                        cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, true);
                    });
                    view = horizontalListView;
                    break;
                }
                case 3: {
                    GraySectionCell graySectionCell = new GraySectionCell(context, resourcesProvider);
                    graySectionCell.setTextColor(darkTheme ? Theme.key_voipgroup_nameText : Theme.key_graySectionText);
                    graySectionCell.setBackgroundColor(getThemedColor(darkTheme ? Theme.key_voipgroup_searchBackground : Theme.key_graySection));
                    graySectionCell.setText(LocaleController.getString(R.string.Recent));
                    view = graySectionCell;
                    break;
                }
                case 4: {
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(searchLayoutManager.lastItemHeight, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0 || holder.getItemViewType() == 5) {
//                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
//                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                CharSequence name = null;
                TLObject object = null;
                TLRPC.EncryptedChat ec = null;
                long id = 0;

                if (TextUtils.isEmpty(lastSearchText)) {
                    if (recentDialogsStartRow >= 0 && position >= recentDialogsStartRow) {
                        int p = position - recentDialogsStartRow;
                        DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(p);
                        object = recentSearchObject.object;
                        if (object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            id = user.id;
                            name = ContactsController.formatName(user.first_name, user.last_name);
                        } else if (object instanceof TLRPC.Chat) {
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            id = -chat.id;
                            name = chat.title;
                        } else if (object instanceof TLRPC.TL_encryptedChat) {
                            TLRPC.TL_encryptedChat chat = (TLRPC.TL_encryptedChat) object;
                            ec = chat;
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(chat.user_id);
                            if (user != null) {
                                id = user.id;
                                name = ContactsController.formatName(user.first_name, user.last_name);
                            }
                        }
                        String foundUserName = searchAdapterHelper.getLastFoundUsername();
                        if (!TextUtils.isEmpty(foundUserName)) {
                            int index;
                            if (name != null && (index = AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName)) != -1) {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(name);
                                spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                name = spannableStringBuilder;
                            }
                        }
                    }
                    if (holder.itemView instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) holder.itemView).setData(object, ec, name, null, false, false);
                        ((ProfileSearchCell) holder.itemView).useSeparator = position < getItemCount() - 2;
                    } else if (holder.itemView instanceof ShareDialogCell) {
                        ((ShareDialogCell) holder.itemView).setDialog(id, selectedDialogs.indexOfKey(id) >= 0, name);
                    }
                    return;
                }
                position--;
                if (position < searchResult.size()) {
                    DialogSearchResult result = (DialogSearchResult) searchResult.get(position);
                    id = result.dialog.id;
                    name = result.name;
                } else {
                    position -= searchResult.size();
                    ArrayList<TLObject> arrayList = searchAdapterHelper.getLocalServerSearch();
                    object = arrayList.get(position);
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        id = user.id;
                        name = ContactsController.formatName(user.first_name, user.last_name);
                    } else {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        id = -chat.id;
                        name = chat.title;
                    }
                    String foundUserName = searchAdapterHelper.getLastFoundUsername();
                    if (!TextUtils.isEmpty(foundUserName)) {
                        int index;
                        if (name != null && (index = AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName)) != -1) {
                            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(name);
                            spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            name = spannableStringBuilder;
                        }
                    }
                }
                if (holder.itemView instanceof ProfileSearchCell) {
                    ((ProfileSearchCell) holder.itemView).setData(object, ec, name, null, false, false);
                    ((ProfileSearchCell) holder.itemView).useSeparator = position < getItemCount() - 2;
                } else if (holder.itemView instanceof ShareDialogCell) {
                    ((ShareDialogCell) holder.itemView).setDialog(id, selectedDialogs.indexOfKey(id) >= 0, name);
                }
            } else if (holder.getItemViewType() == 2) {
                ((RecyclerListView) holder.itemView).getAdapter().notifyDataSetChanged();
            }
        }


        @Override
        public int getItemViewType(int position) {
            if (position == lastFilledItem) {
                return 4;
            } else if (position == firstEmptyViewCell) {
                return 1;
            } else if (position == hintsCell) {
                return 2;
            } else if (position == resentTitleCell) {
                return 3;
            }
            return TextUtils.isEmpty(lastSearchText) ? 0 : 5;
        }

        public boolean isSearching() {
            return !TextUtils.isEmpty(lastSearchText);
        }

        public int getSpanSize(int spanCount, int position) {
            if (position == hintsCell || position == resentTitleCell || position == firstEmptyViewCell || position == lastFilledItem) {
                return spanCount;
            }
            final int viewType = getItemViewType(position);
            if (viewType == 0) {
                return spanCount;
            }
            return 1;
        }
    }

    private boolean searchIsVisible;
    private boolean searchWasVisibleBeforeTopics;

    private void checkCurrentList(boolean force) {
        boolean searchVisibleLocal = false;
        if (!TextUtils.isEmpty(searchView.searchEditText.getText()) || (keyboardVisible && searchView.searchEditText.hasFocus()) || searchWasVisibleBeforeTopics) {
            searchVisibleLocal = true;
            updateSearchAdapter = true;
            if (selectedTopicDialog == null) {
                AndroidUtilities.updateViewVisibilityAnimated(gridView, false, 0.98f, true);
                AndroidUtilities.updateViewVisibilityAnimated(searchGridView, true);
            }
        } else {
            if (selectedTopicDialog == null) {
                AndroidUtilities.updateViewVisibilityAnimated(gridView, true, 0.98f, true);
                AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false);
            }
        }

        if (searchIsVisible != searchVisibleLocal || force) {
            searchIsVisible = searchVisibleLocal;
            searchAdapter.notifyDataSetChanged();
            listAdapter.notifyDataSetChanged();
            if (searchIsVisible) {
                if (lastOffset == Integer.MAX_VALUE) {
                    ((LinearLayoutManager) searchGridView.getLayoutManager()).scrollToPositionWithOffset(0, -searchGridView.getPaddingTop());
                } else {
                    ((LinearLayoutManager) searchGridView.getLayoutManager()).scrollToPositionWithOffset(0, lastOffset - searchGridView.getPaddingTop());
                }
                searchAdapter.searchDialogs(searchView.searchEditText.getText().toString());
            } else {
                if (lastOffset == Integer.MAX_VALUE) {
                    layoutManager.scrollToPositionWithOffset(0,  0);
                } else {
                    layoutManager.scrollToPositionWithOffset(0, 0);
                }
            }
        }
    }
}
