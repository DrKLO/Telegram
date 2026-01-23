/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AutoDeletePopupWrapper;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class GroupCreateFinalActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private GroupCreateAdapter adapter;
    private RecyclerListView listView;
    private EditTextEmoji editText;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private RLottieImageView avatarEditor;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ContextProgressView progressView;
    private AnimatorSet doneItemAnimation;
    private FrameLayout editTextContainer;
    private ImageView floatingButtonIcon;
    private FrameLayout floatingButtonContainer;
    ActionBarPopupWindow popupWindow;

    private Drawable shadowDrawable;

    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;
    private TLRPC.InputFile inputPhoto;
    private TLRPC.InputFile inputVideo;
    private TLRPC.VideoSize inputEmojiMarkup;
    private String inputVideoPath;
    private double videoTimestamp;
    private ArrayList<Long> selectedContacts;
    private boolean createAfterUpload;
    private boolean donePressed;
    private ImageUpdater imageUpdater;
    private String nameToSet;
    private int chatType;
    private boolean canToggleTopics;

    private RLottieDrawable cameraDrawable;
    
    private boolean forImport;

    private String currentGroupCreateAddress;
    private Location currentGroupCreateLocation;

    private int reqId;
    private int ttlPeriod;

    private final static int done_button = 1;
    private FillLastLinearLayoutManager linearLayoutManager;

    public interface GroupCreateFinalActivityDelegate {
        void didStartChatCreation();
        void didFinishChatCreation(GroupCreateFinalActivity fragment, long chatId);
        void didFailChatCreation();
    }

    private GroupCreateFinalActivityDelegate delegate;

    public GroupCreateFinalActivity(Bundle args) {
        super(args);
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
        avatarDrawable = new AvatarDrawable();
        currentGroupCreateAddress = args.getString("address");
        currentGroupCreateLocation = args.getParcelable("location");
        forImport = args.getBoolean("forImport", false);
        nameToSet = args.getString("title", null);
        canToggleTopics = args.getBoolean("canToggleTopics", true);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidFailCreate);
        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_GROUP, true);
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);
        long[] contacts = getArguments().getLongArray("result");
        if (contacts != null) {
            selectedContacts = new ArrayList<>(contacts.length);
            for (int a = 0; a < contacts.length; a++) {
                selectedContacts.add(contacts[a]);
            }
        }
        final ArrayList<Long> usersToLoad = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            Long uid = selectedContacts.get(a);
            if (getMessagesController().getUser(uid) == null) {
                usersToLoad.add(uid);
            }
        }
        if (!usersToLoad.isEmpty()) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                users.addAll(MessagesStorage.getInstance(currentAccount).getUsers(usersToLoad));
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (usersToLoad.size() != users.size()) {
                return false;
            }
            if (!users.isEmpty()) {
                for (TLRPC.User user : users) {
                    getMessagesController().putUser(user, true);
                }
            } else {
                return false;
            }
        }
        ttlPeriod = getUserConfig().getGlobalTTl() * 60;
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidFailCreate);
        imageUpdater.clear();
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
        }
        if (editText != null) {
            editText.onDestroy();
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.onResume();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        imageUpdater.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (editText != null) {
            editText.onPause();
        }
        imageUpdater.onPause();
    }

    @Override
    public void dismissCurrentDialog() {
        if (imageUpdater.dismissCurrentDialog(visibleDialog)) {
            return;
        }
        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return imageUpdater.dismissDialogOnPause(dialog) && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        imageUpdater.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (editText != null && editText.isPopupShowing()) {
            if (invoked) editText.hidePopup(true);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    protected boolean hideKeyboardOnShow() {
        return false;
    }

    private void setDefaultGroupName() {
        TLRPC.User currentUser = getUserConfig().getCurrentUser();
        int members = selectedContacts.size() + 1;
        if (members >= 2 && members <= 5 && TextUtils.isEmpty(editText.getText())) {
            String txt = "";
            try {
                switch (members) {
                    case 2:
                        txt = LocaleController.formatString(
                                "GroupCreateMembersTwo", R.string.GroupCreateMembersTwo,
                                currentUser.first_name,
                                getFirstNameByPos(0)
                        );
                        break;
                    case 3:
                        txt = LocaleController.formatString(
                                "GroupCreateMembersThree", R.string.GroupCreateMembersThree,
                                currentUser.first_name,
                                getFirstNameByPos(0),
                                getFirstNameByPos(1)
                        );
                        break;
                    case 4:
                        txt = LocaleController.formatString(
                                "GroupCreateMembersFour", R.string.GroupCreateMembersFour,
                                currentUser.first_name,
                                getFirstNameByPos(0),
                                getFirstNameByPos(1),
                                getFirstNameByPos(2)
                        );
                        break;
                    case 5:
                        txt = LocaleController.formatString(
                                "GroupCreateMembersFive", R.string.GroupCreateMembersFive,
                                currentUser.first_name,
                                getFirstNameByPos(0),
                                getFirstNameByPos(1),
                                getFirstNameByPos(2),
                                getFirstNameByPos(3)
                        );
                        break;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (!TextUtils.isEmpty(txt)) {
                editText.setText(txt);
                editText.setSelection(0, editText.getText().length());
            }
        }
    }

    private String getFirstNameByPos(int pos) {
        return getMessagesController().getUser(selectedContacts.get(pos)).first_name;
    }

    @Override
    public View createView(Context context) {
        if (editText != null) {
            editText.onDestroy();
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NewGroup));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20) && !editText.isPopupShowing()) {
                    ignoreLayout = true;
                    editText.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (editText != null && editText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
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
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? editText.getEmojiPadding() : 0;
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
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (editText != null && editText.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fragmentView.setOnTouchListener((v, event) -> true);

        shadowDrawable = context.getResources().getDrawable(R.drawable.greydivider_top).mutate();

        LinearLayout linearLayout = new LinearLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView && shadowDrawable != null) {
                    int y = editTextContainer.getMeasuredHeight();
                    shadowDrawable.setBounds(0, y, getMeasuredWidth(), y + shadowDrawable.getIntrinsicHeight());
                    shadowDrawable.draw(canvas);
                }
                return result;
            }
        };
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        sizeNotifierFrameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        editTextContainer = new FrameLayout(context);
        linearLayout.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context) {
            @Override
            public void invalidate() {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate();
            }

            @Override
            public void invalidate(int l, int t, int r, int b) {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate(l, t, r, b);
            }
        };
        avatarImage.setRoundRadius(AndroidUtilities.dp(chatType == ChatObject.CHAT_TYPE_FORUM ? 16 : 32));
        avatarDrawable.setInfo(5, null, null);
        avatarImage.setImageDrawable(avatarDrawable);
        avatarImage.setContentDescription(LocaleController.getString(R.string.ChoosePhoto));
        editTextContainer.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 16, LocaleController.isRTL ? 16 : 0, 16));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);

        avatarOverlay = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImage != null && avatarProgressView.getVisibility() == VISIBLE && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha() * avatarProgressView.getAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                }
            }
        };
        editTextContainer.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 16, LocaleController.isRTL ? 16 : 0, 16));
        avatarOverlay.setOnClickListener(view -> {
            imageUpdater.openMenu(avatar != null, () -> {
                avatar = null;
                avatarBig = null;
                inputPhoto = null;
                inputVideo = null;
                inputVideoPath = null;
                inputEmojiMarkup = null;
                videoTimestamp = 0;
                showAvatarProgress(false, true);
                avatarImage.setImage(null, null, avatarDrawable, null);
                avatarEditor.setAnimation(cameraDrawable);
                cameraDrawable.setCurrentFrame(0);
            }, dialog -> {
                if (!imageUpdater.isUploadingImage()) {
                    cameraDrawable.setCustomEndFrame(86);
                    avatarEditor.playAnimation();
                } else {
                    cameraDrawable.setCurrentFrame(0, false);
                }
            }, 0);
            cameraDrawable.setCurrentFrame(0);
            cameraDrawable.setCustomEndFrame(43);
            avatarEditor.playAnimation();
        });

        cameraDrawable = new RLottieDrawable(R.raw.camera, "" + R.raw.camera, AndroidUtilities.dp(60), AndroidUtilities.dp(60), false, null);

        avatarEditor = new RLottieImageView(context) {
            @Override
            public void invalidate(int l, int t, int r, int b) {
                super.invalidate(l, t, r, b);
                avatarOverlay.invalidate();
            }

            @Override
            public void invalidate() {
                super.invalidate();
                avatarOverlay.invalidate();
            }
        };
        avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
        avatarEditor.setAnimation(cameraDrawable);
        avatarEditor.setEnabled(false);
        avatarEditor.setClickable(false);
        avatarEditor.setPadding(AndroidUtilities.dp(0), 0, 0, AndroidUtilities.dp(1));
        editTextContainer.addView(avatarEditor, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 15, 16, LocaleController.isRTL ? 15 : 0, 16));

        avatarProgressView = new RadialProgressView(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                avatarOverlay.invalidate();
            }
        };
        avatarProgressView.setSize(AndroidUtilities.dp(30));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        editTextContainer.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 16, LocaleController.isRTL ? 16 : 0, 16));

        showAvatarProgress(false, false);

        editText = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false);
        editText.setHint(chatType == ChatObject.CHAT_TYPE_CHAT || chatType == ChatObject.CHAT_TYPE_MEGAGROUP || chatType == ChatObject.CHAT_TYPE_FORUM ? LocaleController.getString(R.string.EnterGroupNamePlaceholder) : LocaleController.getString(R.string.EnterListName));
        if (nameToSet != null) {
            editText.setText(nameToSet);
            editText.setSelection(editText.getText().length());
            nameToSet = null;
        }
        setDefaultGroupName();
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        editText.setFilters(inputFilters);
        editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));


        listView = new RecyclerListView(context);
        linearLayoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, listView);

        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof TextSettingsCell) {
                if (!AndroidUtilities.isMapsInstalled(GroupCreateFinalActivity.this)) {
                    return;
                }
                LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_GROUP);
                fragment.setDialogId(0);
                fragment.setDelegate((location, live, notify, scheduleDate, payStars) -> {
                    currentGroupCreateLocation.setLatitude(location.geo.lat);
                    currentGroupCreateLocation.setLongitude(location.geo._long);
                    currentGroupCreateAddress = location.address;
                });
                presentFragment(fragment);
            }
            if (view instanceof TextCell && chatType != ChatObject.CHAT_TYPE_FORUM) {
                if (popupWindow != null && popupWindow.isShowing()) {
                    return;
                }
                AutoDeletePopupWrapper autoDeletePopupWrapper = new AutoDeletePopupWrapper(getContext(), null, new AutoDeletePopupWrapper.Callback() {
                    @Override
                    public void dismiss() {
                        popupWindow.dismiss();
                    }

                    @Override
                    public void setAutoDeleteHistory(int time, int action) {
                        ttlPeriod = time;
                        AndroidUtilities.updateVisibleRows(listView);

                    }
                }, true, AutoDeletePopupWrapper.TYPE_GROUP_CREATE, null);

                autoDeletePopupWrapper.updateItems(ttlPeriod);

                popupWindow = new ActionBarPopupWindow(autoDeletePopupWrapper.windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                popupWindow.setPauseNotifications(true);
                popupWindow.setDismissAnimationDuration(220);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setClippingEnabled(true);
                popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                popupWindow.setFocusable(true);
                autoDeletePopupWrapper.windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popupWindow.getContentView().setFocusableInTouchMode(true);
                popupWindow.showAtLocation(getFragmentView(), 0, (int) (view.getX() + x), (int) (view.getY() + y + autoDeletePopupWrapper.windowLayout.getMeasuredHeight() / 2f));
                popupWindow.dimBehind();
            }
        });

        floatingButtonContainer = new FrameLayout(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButtonContainer.setBackgroundDrawable(drawable);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        VerticalPositionAutoAnimator.attach(floatingButtonContainer);
        sizeNotifierFrameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        floatingButtonContainer.setOnClickListener(view -> {
            if (donePressed) {
                return;
            }
            if (editText.length() == 0) {
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                AndroidUtilities.shakeView(editText);
                return;
            }
            donePressed = true;
            AndroidUtilities.hideKeyboard(editText);
            editText.setEnabled(false);

            if (imageUpdater.isUploadingImage()) {
                createAfterUpload = true;
            } else {
                showEditDoneProgress(true);
                reqId = getMessagesController().createChat(editText.getText().toString(), selectedContacts, null, chatType, forImport, currentGroupCreateLocation, currentGroupCreateAddress, ttlPeriod, GroupCreateFinalActivity.this);
            }
        });

        floatingButtonIcon = new ImageView(context);
        floatingButtonIcon.setScaleType(ImageView.ScaleType.CENTER);
        floatingButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButtonIcon.setImageResource(R.drawable.checkbig);
        floatingButtonIcon.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        floatingButtonContainer.setContentDescription(LocaleController.getString(R.string.Done));
        floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);
        floatingButtonContainer.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
    }

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(0.0f);
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            if (photo != null || video != null || emojiMarkup != null) {
                inputPhoto = photo;
                inputVideo = video;
                inputEmojiMarkup = emojiMarkup;
                inputVideoPath = videoPath;
                videoTimestamp = videoStartTimestamp;
                if (createAfterUpload) {
                    if (delegate != null) {
                        delegate.didStartChatCreation();
                    }
                    getMessagesController().createChat(editText.getText().toString(), selectedContacts, null, chatType, forImport, currentGroupCreateLocation, currentGroupCreateAddress, ttlPeriod, GroupCreateFinalActivity.this);
                }
                showAvatarProgress(false, true);
                avatarEditor.setImageDrawable(null);
            } else {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
                showAvatarProgress(true, false);
            }
        });
    }

    @Override
    public String getInitialSearchString() {
        return editText.getText().toString();
    }

    public void setDelegate(GroupCreateFinalActivityDelegate groupCreateFinalActivityDelegate) {
        delegate = groupCreateFinalActivityDelegate;
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarEditor == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                avatarEditor.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarEditor == null) {
                        return;
                    }
                    if (show) {
                        avatarEditor.setVisibility(View.INVISIBLE);
                    } else {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.INVISIBLE);
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.VISIBLE);
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        imageUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
            args.putString("path", imageUpdater.currentPicturePath);
        }
        if (editText != null) {
            String text = editText.getText().toString();
            if (text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (imageUpdater != null) {
            imageUpdater.currentPicturePath = args.getString("path");
        }
        String text = args.getString("nameTextView");
        if (text != null) {
            if (editText != null) {
                editText.setText(text);
            } else {
                nameToSet = text;
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            editText.openKeyboard();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            if (listView == null) {
                return;
            }
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(mask);
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidFailCreate) {
            reqId = 0;
            donePressed = false;
            showEditDoneProgress(false);
            if (editText != null) {
                editText.setEnabled(true);
            }
            if (delegate != null) {
                delegate.didFailChatCreation();
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            reqId = 0;
            long chatId = (Long) args[0];
            if (delegate != null) {
                delegate.didFinishChatCreation(this, chatId);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                Bundle args2 = new Bundle();
                args2.putLong("chat_id", chatId);
                args2.putBoolean("just_created_chat", true);
                presentFragment(new ChatActivity(args2), true);
            }
            if (inputPhoto != null || inputVideo != null || inputEmojiMarkup != null) {
                getMessagesController().changeChatAvatar(chatId, null, inputPhoto, inputVideo, inputEmojiMarkup, videoTimestamp, inputVideoPath, avatar, avatarBig, null);
            }
        }
    }

    private void showEditDoneProgress(final boolean show) {
        if (floatingButtonIcon == null) {
            return;
        }
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            floatingButtonContainer.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            floatingButtonIcon.setVisibility(View.VISIBLE);
            floatingButtonContainer.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "alpha", 1.0f));

        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        floatingButtonIcon.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
    }

    public class GroupCreateAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int usersStartRow;
        private final static int VIEW_TYPE_SHADOW_SECTION_CELL = 0;
        private final static int VIEW_TYPE_HEADER_CELL = 1;
        private final static int VIEW_TYPE_USER_CELL = 2;
        private final static int VIEW_TYPE_TEXT_SETTINGS = 3;
        private final static int VIEW_TYPE_AUTO_DELETE = 4;
        private final static int VIEW_TYPE_TEXT_INFO_CELL = 5;
        private final static int VIEW_TYPE_TOPICS = 6;
        private final static int VIEW_TYPE_LAST_EMPTY_VIEW = 7;

        ArrayList<InnerItem> items = new ArrayList<>();

        public GroupCreateAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public void notifyDataSetChanged() {
            items.clear();
            items.add(new InnerItem(VIEW_TYPE_SHADOW_SECTION_CELL));
            if (chatType == ChatObject.CHAT_TYPE_FORUM) {
                items.add(new InnerItem(VIEW_TYPE_TOPICS));
                items.add(new InnerItem(VIEW_TYPE_TEXT_INFO_CELL, LocaleController.getString(R.string.ForumToggleDescription)));
            } else {
                items.add(new InnerItem(VIEW_TYPE_AUTO_DELETE));
                items.add(new InnerItem(VIEW_TYPE_TEXT_INFO_CELL, LocaleController.getString(R.string.GroupCreateAutodeleteDescription)));
            }
            if (currentGroupCreateAddress != null) {
                items.add(new InnerItem(VIEW_TYPE_HEADER_CELL));
                items.add(new InnerItem(VIEW_TYPE_TEXT_SETTINGS));
                items.add(new InnerItem(VIEW_TYPE_SHADOW_SECTION_CELL));
            }
            if (selectedContacts.size() > 0) {
                items.add(new InnerItem(VIEW_TYPE_HEADER_CELL));
                usersStartRow = items.size();
                for (int i = 0; i < selectedContacts.size(); i++) {
                    items.add(new InnerItem(VIEW_TYPE_USER_CELL));
                }
            }
            items.add(new InnerItem(VIEW_TYPE_LAST_EMPTY_VIEW));

            super.notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_TEXT_SETTINGS || holder.getItemViewType() == VIEW_TYPE_AUTO_DELETE || holder.getItemViewType() == VIEW_TYPE_TOPICS && canToggleTopics;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_SHADOW_SECTION_CELL: {
                    view = new ShadowSectionCell(context);
                    Drawable drawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                }
                case VIEW_TYPE_HEADER_CELL:
                    HeaderCell headerCell = new HeaderCell(context);
                    headerCell.setHeight(46);
                    view = headerCell;
                    break;
                case VIEW_TYPE_USER_CELL:
                    view = new GroupCreateUserCell(context, 0, 3, false);
                    break;
                case VIEW_TYPE_AUTO_DELETE:
                    view = new TextCell(context);
                    break;
                case VIEW_TYPE_TEXT_INFO_CELL:
                    view = new TextInfoPrivacyCell(context);
                    Drawable drawable = Theme.getThemedDrawableByKey(context, selectedContacts.size() == 0 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                case VIEW_TYPE_TOPICS:
                    view = new TextCell(context, 23, false, true, getResourceProvider());
                    break;
                case 3:
                default:
                    view = new TextSettingsCell(context);
                    break;
                case VIEW_TYPE_LAST_EMPTY_VIEW:
                    view = new View(context);
                    if (selectedContacts.isEmpty()) {
                        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    }
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER_CELL: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (currentGroupCreateAddress != null && position == 1) {
                        cell.setText(LocaleController.getString(R.string.AttachLocation));
                    } else {
                        cell.setText(LocaleController.formatPluralString("Members", selectedContacts.size()));
                    }
                    break;
                }
                case VIEW_TYPE_USER_CELL: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLRPC.User user = getMessagesController().getUser(selectedContacts.get(position - usersStartRow));
                    cell.setObject(user, null, null);
                    cell.setDrawDivider(position != items.size() - 1);
                    break;
                }
                case VIEW_TYPE_TEXT_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setText(currentGroupCreateAddress, false);
                    break;
                }
                case VIEW_TYPE_AUTO_DELETE: {
                    TextCell textCell = (TextCell) holder.itemView;
                    String value;
                    if (ttlPeriod == 0) {
                        value = LocaleController.getString(R.string.PasswordOff);
                    } else {
                        value = LocaleController.formatTTLString(ttlPeriod);
                    }
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.AutoDeleteMessages), value, fragmentBeginToShow, R.drawable.msg_autodelete, false);
                    break;
                }
                case VIEW_TYPE_TOPICS: {
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setTextAndCheckAndIcon(LocaleController.getString(R.string.ChannelTopics), true, R.drawable.msg_topics, false);
                    textCell.getCheckBox().setAlpha(.75f);
                    break;
                }
                case VIEW_TYPE_TEXT_INFO_CELL:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    textInfoPrivacyCell.setText(items.get(position).string);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 2) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }

        private class InnerItem extends AdapterWithDiffUtils.Item {

            String string;

            public InnerItem(int viewType) {
                super(viewType, true);
            }

            public InnerItem(int viewType, String string) {
                super(viewType, true);
                this.string = string;
            }


        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2));
        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        return themeDescriptions;
    }
}
