/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.PullForegroundDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import drinkless.org.ton.TonApi;

@SuppressWarnings("FieldCanBeLocal")
public class WalletActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, WalletActionSheet.WalletActionSheetDelegate {

    private SimpleTextView statusTextView;
    private PullRecyclerView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;
    private Drawable pinnedHeaderShadowDrawable;

    private ActionBarLayout[] cameraScanLayout;

    private WalletActionSheet walletActionSheet;

    private Paint blackPaint = new Paint();
    private GradientDrawable backgroundDrawable;

    private String walletAddress;
    private TonApi.GenericAccountState accountState;
    private boolean accountStateLoaded;
    private long lastUpdateTime;
    private boolean loadingTransactions;
    private boolean transactionsEndReached;

    private String openTransferAfterOpen;

    private TonApi.RawTransaction lastTransaction;
    private boolean loadingAccountState;
    private HashMap<Long, TonApi.RawTransaction> transactionsDict = new HashMap<>();
    private ArrayList<String> sections = new ArrayList<>();
    private HashMap<String, ArrayList<TonApi.RawTransaction>> sectionArrays = new HashMap<>();

    private float[] radii;

    private final static String PENDING_KEY = "pending";

    private View paddingView;

    private static final int menu_settings = 1;

    private Runnable updateTimeRunnable = () -> updateTime(true);

    public static float viewOffset = 0.0f;

    private long startArchivePullingTime;
    private boolean canShowHiddenPull;
    private boolean wasPulled;
    private PullForegroundDrawable pullForegroundDrawable;

    private static final int SHORT_POLL_DELAY = 3 * 1000;

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 34;

    private Runnable shortPollRunnable = this::loadAccountState;

    public class PullRecyclerView extends RecyclerListView {

        private boolean firstLayout = true;
        private boolean ignoreLayout;

        public PullRecyclerView(Context context) {
            super(context);
        }

        public void setViewsOffset(float offset) {
            viewOffset = offset;
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                getChildAt(i).setTranslationY(viewOffset);
            }
            invalidate();
            fragmentView.invalidate();
        }

        public float getViewOffset() {
            return viewOffset;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (firstLayout) {
                ignoreLayout = true;
                layoutManager.scrollToPositionWithOffset(1, 0);
                ignoreLayout = false;
                firstLayout = false;
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        public void setAdapter(RecyclerView.Adapter adapter) {
            super.setAdapter(adapter);
            firstLayout = true;
        }

        @Override
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            child.setTranslationY(viewOffset);
        }

        @Override
        public void removeView(View view) {
            super.removeView(view);
            view.setTranslationY(0);
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (pullForegroundDrawable != null && viewOffset != 0) {
                pullForegroundDrawable.drawOverScroll(canvas);
            }
            super.onDraw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                listView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                if (wasPulled) {
                    wasPulled = false;
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.doNotShow();
                    }
                    canShowHiddenPull = false;
                }
            }
            boolean result = super.onTouchEvent(e);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                int currentPosition = layoutManager.findFirstVisibleItemPosition();
                if (currentPosition == 0) {
                    View view = layoutManager.findViewByPosition(currentPosition);
                    int height = (int) (AndroidUtilities.dp(72) * PullForegroundDrawable.SNAP_HEIGHT);
                    int diff = view.getTop() + view.getMeasuredHeight();
                    if (view != null) {
                        long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                        listView.smoothScrollBy(0, diff, CubicBezierInterpolator.EASE_OUT_QUINT);
                        if (diff >= height && pullingTime >= PullForegroundDrawable.minPullingTime) {
                            wasPulled = true;
                            AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
                            lastUpdateTime = 0;
                            loadAccountState();
                            updateTime(false);
                        }

                        if (viewOffset != 0) {
                            ValueAnimator valueAnimator = ValueAnimator.ofFloat(viewOffset, 0f);
                            valueAnimator.addUpdateListener(animation -> listView.setViewsOffset((float) animation.getAnimatedValue()));

                            valueAnimator.setDuration((long) (350f - 120f * (viewOffset / PullForegroundDrawable.getMaxOverscroll())));
                            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                            listView.setScrollEnabled(false);
                            valueAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    listView.setScrollEnabled(true);
                                }
                            });
                            valueAnimator.start();
                        }
                    }
                }
            }
            return result;
        }
    }

    public WalletActivity() {
        this(null);
    }

    public WalletActivity(String transferUrl) {
        super();
        openTransferAfterOpen = transferUrl;
        loadAccountState();
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.walletPendingTransactionsChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.walletSyncProgressChanged);
        getTonController().setTransactionCallback((reload, t) -> {
            getTonController().checkPendingTransactionsForFailure(accountState);
            if (reload) {
                onFinishGettingAccountState();
            }
            loadingTransactions = false;
            if (t != null) {
                accountState = getTonController().getCachedAccountState();
                if (!fillTransactions(t, reload) && !reload) {
                    transactionsEndReached = true;
                }
                if (!t.isEmpty() && (lastTransaction == null || !reload)) {
                    lastTransaction = t.get(t.size() - 1);
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                if (paddingView != null) {
                    paddingView.requestLayout();
                }
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
        AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
        getNotificationCenter().removeObserver(this, NotificationCenter.walletPendingTransactionsChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.walletSyncProgressChanged);
        getTonController().setTransactionCallback(null);
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (Build.VERSION.SDK_INT >= 21 && statusTextView != null) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) statusTextView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        if (!BuildVars.TON_WALLET_STANDALONE) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_wallet_whiteText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_wallet_whiteText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackgroundSelector), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == menu_settings) {
                    presentFragment(new WalletSettingsActivity(WalletSettingsActivity.TYPE_SETTINGS, WalletActivity.this));
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(menu_settings, R.drawable.notifications_settings);

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setTextColor(Theme.getColor(Theme.key_wallet_statusText));
        actionBar.addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM, 48, 0, 48, 0));

        return actionBar;
    }

    @Override
    public View createView(Context context) {
        pullForegroundDrawable = new PullForegroundDrawable(LocaleController.getString("WalletSwipeToRefresh", R.string.WalletSwipeToRefresh), LocaleController.getString("WalletReleaseToRefresh", R.string.WalletReleaseToRefresh)) {
            @Override
            protected float getViewOffset() {
                return listView.getViewOffset();
            }
        };
        pullForegroundDrawable.setColors(Theme.key_wallet_pullBackground, Theme.key_wallet_releaseBackground);
        pullForegroundDrawable.showHidden();
        pullForegroundDrawable.setWillDraw(true);

        blackPaint.setColor(Theme.getColor(Theme.key_wallet_blackBackground));
        backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
        int r = AndroidUtilities.dp(13);
        backgroundDrawable.setCornerRadii(radii = new float[] { r, r, r, r, 0, 0, 0, 0 });
        backgroundDrawable.setColor(Theme.getColor(Theme.key_wallet_whiteBackground));

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                int bottom;
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(1);
                if (holder != null) {
                    bottom = holder.itemView.getBottom();
                } else {
                    bottom = 0;
                }
                float rad = AndroidUtilities.dp(13);
                if (bottom < rad) {
                    rad *= bottom / rad;
                }
                bottom += viewOffset;
                radii[0] = radii[1] = radii[2] = radii[3] = rad;
                canvas.drawRect(0, 0, getMeasuredWidth(), bottom + AndroidUtilities.dp(6), blackPaint);
                backgroundDrawable.setBounds(0, bottom - AndroidUtilities.dp(7), getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            }
        };
        frameLayout.setWillNotDraw(false);
        fragmentView = frameLayout;

        pinnedHeaderShadowDrawable = context.getResources().getDrawable(R.drawable.photos_header_shadow);
        pinnedHeaderShadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundGrayShadow), PorterDuff.Mode.MULTIPLY));

        listView = new PullRecyclerView(context);
        listView.setSectionsType(2);
        listView.setPinnedHeaderShadowDrawable(pinnedHeaderShadowDrawable);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                boolean isDragging = listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;

                int measuredDy = dy;
                if (dy < 0) {
                    listView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                    int currentPosition = layoutManager.findFirstVisibleItemPosition();
                    if (currentPosition == 0) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        if (view != null && view.getBottom() <= AndroidUtilities.dp(1)) {
                            currentPosition = 1;
                        }
                    }
                    if (!isDragging) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        int dialogHeight = AndroidUtilities.dp(72) + 1;
                        int canScrollDy = -view.getTop() + (currentPosition - 1) * dialogHeight;
                        int positiveDy = Math.abs(dy);
                        if (canScrollDy < positiveDy) {
                            measuredDy = -canScrollDy;
                        }
                    } else if (currentPosition == 0) {
                        View v = layoutManager.findViewByPosition(currentPosition);
                        float k = 1f + (v.getTop() / (float) v.getMeasuredHeight());
                        if (k > 1f) {
                            k = 1f;
                        }
                        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                        measuredDy *= PullForegroundDrawable.startPullParallax - PullForegroundDrawable.endPullParallax * k;
                        if (measuredDy > -1) {
                            measuredDy = -1;
                        }
                    }
                }

                if (viewOffset != 0 && dy > 0 && isDragging) {
                    float ty = (int) viewOffset;
                    ty -= dy;
                    if (ty < 0) {
                        measuredDy = (int) ty;
                        ty = 0;
                    } else {
                        measuredDy = 0;
                    }
                    listView.setViewsOffset(ty);
                }

                int usedDy = super.scrollVerticallyBy(measuredDy, recycler, state);
                if (pullForegroundDrawable != null) {
                    pullForegroundDrawable.scrollDy = usedDy;
                }
                int currentPosition = layoutManager.findFirstVisibleItemPosition();
                View firstView = null;
                if (currentPosition == 0) {
                    firstView = layoutManager.findViewByPosition(currentPosition);
                }
                if (currentPosition == 0 && firstView != null && firstView.getBottom() >= AndroidUtilities.dp(4)) {
                    if (startArchivePullingTime == 0) {
                        startArchivePullingTime = System.currentTimeMillis();
                    }
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.showHidden();
                    }
                    float k = 1f + (firstView.getTop() / (float) firstView.getMeasuredHeight());
                    if (k > 1f) {
                        k = 1f;
                    }
                    long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                    boolean canShowInternal = k > PullForegroundDrawable.SNAP_HEIGHT && pullingTime > PullForegroundDrawable.minPullingTime + 20;
                    if (canShowHiddenPull != canShowInternal) {
                        canShowHiddenPull = canShowInternal;
                        if (!wasPulled) {
                            listView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            if (pullForegroundDrawable != null) {
                                pullForegroundDrawable.colorize(canShowInternal);
                            }
                        }
                    }
                    if (measuredDy - usedDy != 0 && dy < 0 && isDragging) {
                        float ty;
                        float tk = (viewOffset / PullForegroundDrawable.getMaxOverscroll());
                        tk = 1f - tk;
                        ty = (viewOffset - dy * PullForegroundDrawable.startPullOverScroll * tk);
                        listView.setViewsOffset(ty);
                    }
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.pullProgress = k;
                        pullForegroundDrawable.setListView(listView);
                    }
                } else {
                    startArchivePullingTime = 0;
                    canShowHiddenPull = false;
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.resetText();
                        pullForegroundDrawable.pullProgress = 0f;
                        pullForegroundDrawable.setListView(listView);
                    }
                }
                if (firstView != null) {
                    firstView.invalidate();
                }
                return usedDy;
            }
        });
        listView.setAdapter(adapter = new Adapter(context));
        listView.setGlowColor(Theme.getColor(Theme.key_wallet_blackBackground));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                fragmentView.invalidate();
                if (!loadingTransactions && !transactionsEndReached) {
                    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                    int visibleItemCount = lastVisibleItem == RecyclerView.NO_POSITION ? 0 : lastVisibleItem;
                    if (visibleItemCount > 0 && lastVisibleItem > adapter.getItemCount() - 4) {
                        loadTransactions(false);
                    }
                }
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (view instanceof WalletTransactionCell) {
                WalletTransactionCell cell = (WalletTransactionCell) view;
                if (cell.isEmpty()) {
                    return;
                }
                walletActionSheet = new WalletActionSheet(this, walletAddress, cell.getAddress(), cell.getComment(), cell.getAmount(), cell.getDate(), cell.getStorageFee(), cell.getTransactionFee());
                walletActionSheet.setDelegate(new WalletActionSheet.WalletActionSheetDelegate() {
                    @Override
                    public void openSendToAddress(String address) {
                        if (getTonController().hasPendingTransactions()) {
                            AlertsCreator.showSimpleAlert(WalletActivity.this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletPendingWait", R.string.WalletPendingWait));
                            return;
                        }
                        walletActionSheet = new WalletActionSheet(WalletActivity.this, WalletActionSheet.TYPE_SEND, walletAddress);
                        walletActionSheet.setDelegate(WalletActivity.this);
                        walletActionSheet.setRecipientString(address, true);
                        walletActionSheet.setOnDismissListener(dialog -> {
                            if (walletActionSheet == dialog) {
                                walletActionSheet = null;
                            }
                        });
                        walletActionSheet.show();
                    }
                });
                showDialog(walletActionSheet, (dialog) -> {
                    if (walletActionSheet == dialog) {
                        walletActionSheet = null;
                    }
                });
            }
        });

        updateTime(false);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (walletActionSheet != null) {
            walletActionSheet.onResume();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        scheduleShortPoll();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (walletActionSheet != null) {
            walletActionSheet.onPause();
        }
        AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        if (dialog instanceof WalletActionSheet) {
            return false;
        }
        return super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (walletActionSheet != null) {
            walletActionSheet.onActivityResultFragment(requestCode, resultCode, data);
        }
        if (cameraScanLayout != null && cameraScanLayout[0] != null) {
            cameraScanLayout[0].fragmentsStack.get(0).onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    public void openQrReader() {
        if (getParentActivity() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        processOpenQrReader();
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (getParentActivity() == null) {
            return;
        }
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                processOpenQrReader();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("WalletPermissionNoCamera", R.string.WalletPermissionNoCamera));
                builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && !TextUtils.isEmpty(openTransferAfterOpen)) {
            walletActionSheet = new WalletActionSheet(WalletActivity.this, WalletActionSheet.TYPE_SEND, walletAddress);
            walletActionSheet.setDelegate(this);
            walletActionSheet.parseTonUrl(null, openTransferAfterOpen);
            walletActionSheet.setOnDismissListener(dialog -> {
                if (walletActionSheet == dialog) {
                    walletActionSheet = null;
                }
            });
            walletActionSheet.show();
            openTransferAfterOpen = null;
        }
    }

    private void processOpenQrReader() {
        if (walletActionSheet == null) {
            return;
        }
        cameraScanLayout = CameraScanActivity.showAsSheet(this, true, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                walletActionSheet.parseTonUrl(null, text);
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletPendingTransactionsChanged) {
            fillTransactions(null, false);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.walletSyncProgressChanged) {
            updateTime(false);
        }
    }

    @Override
    protected void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        getTonController().isKeyStoreInvalidated((invalidated) -> {
            if (invalidated && getParentActivity() != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("Wallet", R.string.Wallet));
                builder.setMessage(LocaleController.getString("WalletKeystoreInvalidated", R.string.WalletKeystoreInvalidated));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.setOnDismissListener(dialog -> {
                    getTonController().cleanup();
                    UserConfig userConfig = getUserConfig();
                    userConfig.clearTonConfig();
                    userConfig.saveConfig(false);
                    presentFragment(new WalletCreateActivity(WalletCreateActivity.TYPE_CREATE), true);
                });
                builder.show();
            }
        });
    }

    private void updateTime(boolean schedule) {
        if (statusTextView != null) {
            if (lastUpdateTime == 0) {
                int progress = getTonController().getSyncProgress();
                if (progress != 0 && progress != 100) {
                    statusTextView.setText(LocaleController.formatString("WalletUpdatingProgress", R.string.WalletUpdatingProgress, progress));
                } else {
                    statusTextView.setText(LocaleController.getString("WalletUpdating", R.string.WalletUpdating));
                }
            } else {
                long newTime = getConnectionsManager().getCurrentTime();
                long dt = newTime - lastUpdateTime;
                if (dt < 60) {
                    statusTextView.setText(LocaleController.getString("WalletUpdatedFewSecondsAgo", R.string.WalletUpdatedFewSecondsAgo));
                } else {
                    String time;
                    if (dt < 60 * 60) {
                        time = LocaleController.formatPluralString("Minutes", (int) (dt / 60));
                    } else if (dt < 60 * 60 * 24) {
                        time = LocaleController.formatPluralString("Hours", (int) (dt / 60 / 60));
                    } else {
                        time = LocaleController.formatPluralString("Days", (int) (dt / 60 / 60 / 24));
                    }
                    statusTextView.setText(LocaleController.formatString("WalletUpdatedTimeAgo", R.string.WalletUpdatedTimeAgo, time));
                }
            }
        }
        if (schedule) {
            AndroidUtilities.runOnUIThread(updateTimeRunnable, 60 * 1000);
        }
    }

    private TonApi.InternalTransactionId getLastTransactionId(boolean reload) {
        if (!reload && lastTransaction != null) {
            return lastTransaction.transactionId;
        }
        return TonController.getLastTransactionId(accountState);
    }

    private void scheduleShortPoll() {
        if (isPaused) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
        AndroidUtilities.runOnUIThread(shortPollRunnable, SHORT_POLL_DELAY);
    }

    private void loadAccountState() {
        if (loadingAccountState) {
            return;
        }
        loadingAccountState = true;
        AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
        if (accountState == null) {
            accountState = getTonController().getCachedAccountState();
            fillTransactions(getTonController().getCachedTransactions(), false);
        }
        walletAddress = getTonController().getWalletAddress(getUserConfig().tonPublicKey);
        getTonController().getAccountState(state -> {
            if (state != null) {
                boolean needUpdateTransactions = true;
                if (accountState != null) {
                    TonApi.InternalTransactionId oldTransaction = TonController.getLastTransactionId(accountState);
                    TonApi.InternalTransactionId newTransaction = TonController.getLastTransactionId(state);
                    if (oldTransaction != null && newTransaction != null && oldTransaction.lt == newTransaction.lt && !sections.isEmpty()) {
                        needUpdateTransactions = false;
                        String dateKey = sections.get(sections.size() - 1);
                        ArrayList<TonApi.RawTransaction> arrayList = sectionArrays.get(dateKey);
                        if (arrayList != null && !arrayList.isEmpty()) {
                            lastTransaction = arrayList.get(arrayList.size() - 1);
                        }
                    }
                }
                accountState = state;
                accountStateLoaded = true;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                if (needUpdateTransactions) {
                    loadTransactions(true);
                } else {
                    onFinishGettingAccountState();
                    getTonController().checkPendingTransactionsForFailure(state);
                }
            } else {
                loadingAccountState = false;
                scheduleShortPoll();
                if (getParentActivity() != null) {
                    loadAccountState();
                }
            }
        });

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void onFinishGettingAccountState() {
        lastUpdateTime = TonController.getLastSyncTime(accountState);
        updateTime(true);
        loadingAccountState = false;
        scheduleShortPoll();
    }

    private boolean fillTransactions(ArrayList<TonApi.RawTransaction> arrayList, boolean reload) {
        boolean cleared = sections.isEmpty();
        if (arrayList != null && !arrayList.isEmpty() && reload) {
            TonApi.RawTransaction transaction = arrayList.get(arrayList.size() - 1);
            for (int b = 0, N2 = sections.size(); b < N2; b++) {
                if (PENDING_KEY.equals(sections.get(b))) {
                    continue;
                }
                String key = sections.get(b);
                ArrayList<TonApi.RawTransaction> existingTransactions = sectionArrays.get(key);
                if (existingTransactions.get(0).utime < transaction.utime) {
                    sections.clear();
                    sectionArrays.clear();
                    transactionsDict.clear();
                    lastTransaction = null;
                    transactionsEndReached = false;
                    getTonController().clearPendingCache();
                    cleared = true;
                } else {
                    Collections.reverse(arrayList);
                }
                break;
            }
        }

        ArrayList<TonApi.RawTransaction> pendingTransactions = getTonController().getPendingTransactions();
        if (pendingTransactions.isEmpty()) {
            if (sectionArrays.containsKey(PENDING_KEY)) {
                sectionArrays.remove(PENDING_KEY);
                sections.remove(0);
            }
        } else {
            if (!sectionArrays.containsKey(PENDING_KEY)) {
                sections.add(0, PENDING_KEY);
                sectionArrays.put(PENDING_KEY, pendingTransactions);
            }
        }
        if (arrayList == null) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        boolean added = false;
        for (int a = 0, N = arrayList.size(); a < N; a++) {
            TonApi.RawTransaction transaction = arrayList.get(a);
            if (transactionsDict.containsKey(transaction.transactionId.lt)) {
                continue;
            }
            calendar.setTimeInMillis(transaction.utime * 1000);
            int dateDay = calendar.get(Calendar.DAY_OF_YEAR);
            int dateYear = calendar.get(Calendar.YEAR);
            int dateMonth = calendar.get(Calendar.MONTH);
            String dateKey = String.format(Locale.US, "%d_%02d_%02d", dateYear, dateMonth, dateDay);
            ArrayList<TonApi.RawTransaction> transactions = sectionArrays.get(dateKey);
            if (transactions == null) {
                int addToIndex = sections.size();
                for (int b = 0, N2 = sections.size(); b < N2; b++) {
                    if (PENDING_KEY.equals(sections.get(b))) {
                        continue;
                    }
                    String key = sections.get(b);
                    ArrayList<TonApi.RawTransaction> existingTransactions = sectionArrays.get(key);
                    if (existingTransactions.get(0).utime < transaction.utime) {
                        addToIndex = b;
                        break;
                    }
                }
                transactions = new ArrayList<>();
                sections.add(addToIndex, dateKey);
                sectionArrays.put(dateKey, transactions);
            }
            added = true;
            if (reload && !cleared) {
                transactions.add(0, transaction);
            } else {
                transactions.add(transaction);
            }
            transactionsDict.put(transaction.transactionId.lt, transaction);
        }
        return added;
    }

    private void loadTransactions(boolean reload) {
        if (loadingTransactions || !accountStateLoaded) {
            return;
        }
        loadingTransactions = true;
        getTonController().getTransactions(reload, getLastTransactionId(reload));
    }

    private void showInvoiceSheet(String url, long amount) {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setApplyTopPadding(false);
        builder.setUseFullWidth(false);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(472), MeasureSpec.EXACTLY));
            }
        };
        if (amount == 0) {
            ActionBarMenuItem menuItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_dialogTextBlack));
            menuItem.setLongClickEnabled(false);
            menuItem.setIcon(R.drawable.ic_ab_other);
            menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            menuItem.addSubItem(1, LocaleController.getString("WalletCopyAddress", R.string.WalletCopyAddress));
            menuItem.addSubItem(2, LocaleController.getString("WalletCreateInvoice", R.string.WalletCreateInvoice));
            menuItem.setSubMenuOpenSide(2);
            menuItem.setDelegate(id -> {
                builder.getDismissRunnable().run();
                if (id == 1) {
                    AndroidUtilities.addToClipboard(url);
                    Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                } else if (id == 2) {
                    walletActionSheet = new WalletActionSheet(WalletActivity.this, WalletActionSheet.TYPE_INVOICE, walletAddress);
                    walletActionSheet.setDelegate(new WalletActionSheet.WalletActionSheetDelegate() {
                        @Override
                        public void openInvoice(String url, long amount) {
                            showInvoiceSheet(url, amount);
                        }
                    });
                    walletActionSheet.setOnDismissListener(dialog -> {
                        if (walletActionSheet == dialog) {
                            walletActionSheet = null;
                        }
                    });
                    walletActionSheet.show();
                }
            });
            menuItem.setTranslationX(AndroidUtilities.dp(6));
            menuItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6));
            frameLayout.addView(menuItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT, 0, 12, 10, 0));
            menuItem.setOnClickListener(v -> menuItem.toggleSubMenu());
        }

        TextView titleView = new TextView(context);
        titleView.setLines(1);
        titleView.setSingleLine(true);
        if (amount == 0) {
            titleView.setText(LocaleController.getString("WalletReceiveYourAddress", R.string.WalletReceiveYourAddress));
        } else {
            titleView.setText(LocaleController.getString("WalletYourInvoice", R.string.WalletYourInvoice));
        }
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        frameLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 22, 21, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 78, 0, 0));

        TextView descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        if (amount == 0) {
            descriptionText.setText(LocaleController.getString("WalletTestShareInfo", R.string.WalletTestShareInfo));
        } else {
            descriptionText.setText(AndroidUtilities.replaceTags(LocaleController.formatString("WalletTestShareInvoiceUrlInfo", R.string.WalletTestShareInvoiceUrlInfo, TonController.formatCurrency(amount))));
        }
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        linearLayout.addView(descriptionText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        TextView addressValueTextView = new TextView(context);

        ImageView imageView = new ImageView(context);
        imageView.setImageBitmap(getTonController().createTonQR(context, url, null));
        linearLayout.addView(imageView, LayoutHelper.createLinear(190, 190, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));
        imageView.setOnLongClickListener(v -> {
            TonController.shareBitmap(getParentActivity(), v, url);
            return true;
        });

        addressValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        addressValueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
        addressValueTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        StringBuilder stringBuilder = new StringBuilder(walletAddress);
        stringBuilder.insert(stringBuilder.length() / 2, '\n');
        addressValueTextView.setText(stringBuilder);
        linearLayout.addView(addressValueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        addressValueTextView.setOnLongClickListener(v -> {
            AndroidUtilities.addToClipboard(url);
            Toast.makeText(getParentActivity(), LocaleController.getString("WalletTransactionAddressCopied", R.string.WalletTransactionAddressCopied), Toast.LENGTH_SHORT).show();
            return true;
        });

        TextView buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        if (amount == 0) {
            buttonTextView.setText(LocaleController.getString("WalletShareAddress", R.string.WalletShareAddress));
        } else {
            buttonTextView.setText(LocaleController.getString("WalletShareInvoiceUrl", R.string.WalletShareInvoiceUrl));
        }
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, Gravity.LEFT | Gravity.TOP, 16, 20, 16, 16));
        buttonTextView.setOnClickListener(v -> AndroidUtilities.openSharing(this, url));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        if (Build.VERSION.SDK_INT >= 21) {
            scrollView.setNestedScrollingEnabled(true);
        }

        builder.setCustomView(scrollView);
        BottomSheet bottomSheet = builder.create();
        bottomSheet.setCanDismissWithSwipe(false);
        showDialog(bottomSheet);
    }

    private class Adapter extends RecyclerListView.SectionsAdapter {

        private Context context;

        public Adapter(Context c) {
            context = c;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return section != 0 && row != 0;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new WalletBalanceCell(context) {
                        @Override
                        protected void onReceivePressed() {
                            showInvoiceSheet("ton://transfer/" + walletAddress, 0);
                        }

                        @Override
                        protected void onSendPressed() {
                            if (getTonController().hasPendingTransactions()) {
                                AlertsCreator.showSimpleAlert(WalletActivity.this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletPendingWait", R.string.WalletPendingWait));
                                return;
                            }
                            int syncProgress = getTonController().getSyncProgress();
                            if (syncProgress != 0 && syncProgress != 100) {
                                AlertsCreator.showSimpleAlert(WalletActivity.this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletSendSyncInProgress", R.string.WalletSendSyncInProgress));
                                return;
                            }
                            walletActionSheet = new WalletActionSheet(WalletActivity.this, WalletActionSheet.TYPE_SEND, walletAddress);
                            walletActionSheet.setDelegate(WalletActivity.this);
                            walletActionSheet.setOnDismissListener(dialog -> {
                                if (walletActionSheet == dialog) {
                                    walletActionSheet = null;
                                }
                            });
                            walletActionSheet.show();
                        }
                    };
                    break;
                }
                case 1: {
                    view = new WalletTransactionCell(context);
                    break;
                }
                case 2: {
                    view = new WalletCreatedCell(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int height = Math.max(AndroidUtilities.dp(280), fragmentView.getMeasuredHeight() - AndroidUtilities.dp(236 + 6));
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                }
                case 3: {
                    view = new WalletDateCell(context);
                    break;
                }
                case 4: {
                    view = new View(context) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            pullForegroundDrawable.draw(canvas);
                        }

                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(AndroidUtilities.dp(72)), MeasureSpec.EXACTLY));
                        }
                    };
                    pullForegroundDrawable.setCell(view);
                    break;
                }
                case 5: {
                    view = paddingView = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int n = listView.getChildCount();
                            int itemsCount = adapter.getItemCount();
                            int totalHeight = 0;
                            for (int i = 0; i < n; i++) {
                                View view = listView.getChildAt(i);
                                int pos = listView.getChildAdapterPosition(view);
                                if (pos != 0 && pos != itemsCount - 1) {
                                    totalHeight += listView.getChildAt(i).getMeasuredHeight();
                                }
                            }
                            int paddingHeight = fragmentView.getMeasuredHeight() - totalHeight;
                            if (paddingHeight <= 0) {
                                paddingHeight = 0;
                            }
                            setMeasuredDimension(listView.getMeasuredWidth(), paddingHeight);
                        }
                    };
                    break;
                }
                case 6:
                default: {
                    view = new WalletSyncCell(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int height = Math.max(AndroidUtilities.dp(280), fragmentView.getMeasuredHeight() - AndroidUtilities.dp(236 + 6));
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            switch (holder.getItemViewType()) {
                case 0: {
                    WalletBalanceCell balanceCell = (WalletBalanceCell) holder.itemView;
                    if (getTonController().isWalletLoaded()) {
                        balanceCell.setBalance(TonController.getBalance(accountState));
                    } else {
                        balanceCell.setBalance(-1);
                    }
                    break;
                }
                case 1: {
                    WalletTransactionCell transactionCell = (WalletTransactionCell) holder.itemView;
                    section -= 1;
                    String key = sections.get(section);
                    ArrayList<TonApi.RawTransaction> arrayList = sectionArrays.get(key);
                    transactionCell.setTransaction(arrayList.get(position - 1), position != arrayList.size());
                    break;
                }
                case 2: {
                    WalletCreatedCell createdCell = (WalletCreatedCell) holder.itemView;
                    createdCell.setAddress(walletAddress);
                    break;
                }
                case 3: {
                    WalletDateCell dateCell = (WalletDateCell) holder.itemView;
                    section -= 1;
                    String key = sections.get(section);
                    if (PENDING_KEY.equals(key)) {
                        dateCell.setText(LocaleController.getString("WalletPendingTransactions", R.string.WalletPendingTransactions));
                    } else {
                        ArrayList<TonApi.RawTransaction> arrayList = sectionArrays.get(key);
                        dateCell.setDate(arrayList.get(0).utime);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section == 0) {
                if (position == 0) {
                    return 4;
                } else if (position == 1) {
                    return 0;
                } else {
                    if (getTonController().isWalletLoaded()) {
                        return 2;
                    } else {
                        return 6;
                    }
                }
            } else {
                section -= 1;
                if (section < sections.size()) {
                    return position == 0 ? 3 : 1;
                } else {
                    return 5;
                }
            }
        }

        @Override
        public int getSectionCount() {
            int count = 1;
            if (!sections.isEmpty()) {
                count += sections.size() + 1;
            }
            return count;
        }

        @Override
        public int getCountForSection(int section) {
            if (section == 0) {
                return sections.isEmpty() ? 3 : 2;
            }
            section -= 1;
            if (section < sections.size()) {
                return sectionArrays.get(sections.get(section)).size() + 1;
            } else {
                return 1;
            }
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new WalletDateCell(context);
                view.setBackgroundColor(Theme.getColor(Theme.key_wallet_whiteBackground) & 0xe5ffffff);
            }
            WalletDateCell dateCell = (WalletDateCell) view;
            if (section == 0) {
                dateCell.setAlpha(0.0f);
            } else {
                section -= 1;
                if (section < sections.size()) {
                    view.setAlpha(1.0f);
                    String key = sections.get(section);
                    if (PENDING_KEY.equals(key)) {
                        dateCell.setText(LocaleController.getString("WalletPendingTransactions", R.string.WalletPendingTransactions));
                    } else {
                        ArrayList<TonApi.RawTransaction> arrayList = sectionArrays.get(key);
                        dateCell.setDate(arrayList.get(0).utime);
                    }
                }
            }
            return view;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }
}
