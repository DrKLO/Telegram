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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private LinearLayoutManager layoutManager;
    private FrameLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private TextView nameTextView;
    private TextView onlineTextView;
    private ImageView writeButton;
    private AnimatorSet writeButtonAnimation;
    private ImageUpdater imageUpdater;
    private View extraHeightView;
    private View shadowView;
    private AvatarDrawable avatarDrawable;

    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;

    private TLRPC.TL_userFull userInfo;

    private int extraHeight;

    private int overscrollRow;
    private int numberSectionRow;
    private int numberRow;
    private int usernameRow;
    private int bioRow;
    private int settingsSectionRow;
    private int settingsSectionRow2;
    private int notificationRow;
    private int languageRow;
    private int privacyRow;
    private int dataRow;
    private int chatRow;
    private int helpRow;
    private int versionRow;
    private int rowCount;

    private final static int edit_name = 1;
    private final static int logout = 2;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (fileLocation == null) {
                return null;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                TLRPC.FileLocation photoBig = user.photo.photo_big;
                if (photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                    int coords[] = new int[2];
                    avatarImage.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = avatarImage;
                    object.imageReceiver = avatarImage.getImageReceiver();
                    object.dialogId = UserConfig.getInstance(currentAccount).getClientUserId();
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.size = -1;
                    object.radius = avatarImage.getImageReceiver().getRoundRadius();
                    object.scale = avatarContainer.getScaleX();
                    return object;
                }
            }
            return null;
        }

        @Override
        public void willHidePhotoViewer() {
            avatarImage.getImageReceiver().setVisible(true, true);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        imageUpdater = new ImageUpdater();
        imageUpdater.parentFragment = this;
        imageUpdater.delegate = this;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);

        rowCount = 0;
        overscrollRow = rowCount++;
        numberSectionRow = rowCount++;
        numberRow = rowCount++;
        usernameRow = rowCount++;
        bioRow = rowCount++;
        settingsSectionRow = rowCount++;
        settingsSectionRow2 = rowCount++;
        notificationRow = rowCount++;
        privacyRow = rowCount++;
        dataRow = rowCount++;
        chatRow = rowCount++;
        languageRow = rowCount++;
        helpRow = rowCount++;
        versionRow = rowCount++;

        DataQuery.getInstance(currentAccount).checkFeaturedStickers();
        userInfo = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
        MessagesController.getInstance(currentAccount).loadUserInfo(UserConfig.getInstance(currentAccount).getCurrentUser(), true, classGuid);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (avatarImage != null) {
            avatarImage.setImageDrawable(null);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        imageUpdater.clear();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_avatar_actionBarIconBlue), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAddToContainer(false);
        extraHeight = 88;
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == edit_name) {
                    presentFragment(new ChangeNameActivity());
                } else if (id == logout) {
                    presentFragment(new LogoutActivity());
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
        item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName));
        item.addSubItem(logout, LocaleController.getString("LogOut", R.string.LogOut));

        int scrollTo;
        int scrollToPosition = 0;
        Object writeButtonTag = null;
        if (listView != null) {
            scrollTo = layoutManager.findFirstVisibleItemPosition();
            View topView = layoutManager.findViewByPosition(scrollTo);
            if (topView != null) {
                scrollToPosition = topView.getTop();
            } else {
                scrollTo = -1;
            }
            writeButtonTag = writeButton.getTag();
        } else {
            scrollTo = -1;
        }

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context) {
            @Override
            protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
                if (child == listView) {
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    if (parentLayout != null) {
                        int actionBarHeight = 0;
                        int childCount = getChildCount();
                        for (int a = 0; a < childCount; a++) {
                            View view = getChildAt(a);
                            if (view == child) {
                                continue;
                            }
                            if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                                if (((ActionBar) view).getCastShadows()) {
                                    actionBarHeight = view.getMeasuredHeight();
                                }
                                break;
                            }
                        }
                        parentLayout.drawHeaderShadow(canvas, actionBarHeight);
                    }
                    return result;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setOnItemClickListener((view, position) -> {
            if (position == notificationRow) {
                presentFragment(new NotificationsSettingsActivity());
            } else if (position == privacyRow) {
                presentFragment(new PrivacySettingsActivity());
            } else if (position == dataRow) {
                presentFragment(new DataSettingsActivity());
            } else if (position == chatRow) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            } else if (position == helpRow) {
                BottomSheet.Builder builder = new BottomSheet.Builder(context);
                builder.setApplyTopPadding(false);

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                HeaderCell headerCell = new HeaderCell(context, true, 23, 15, false);
                headerCell.setHeight(47);
                headerCell.setText(LocaleController.getString("SettingsHelp", R.string.SettingsHelp));
                linearLayout.addView(headerCell);

                LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
                linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
                linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                int count = 6;
                for (int a = 0; a < count; a++) {
                    if (a >= 3 && a <= 4 && !BuildVars.LOGS_ENABLED || a == 5 && !BuildVars.DEBUG_VERSION) {
                        continue;
                    }
                    TextCell textCell = new TextCell(context);
                    String text;
                    switch (a) {
                        case 0:
                            text = LocaleController.getString("AskAQuestion", R.string.AskAQuestion);
                            break;
                        case 1:
                            text = LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ);
                            break;
                        case 2:
                            text = LocaleController.getString("PrivacyPolicy", R.string.PrivacyPolicy);
                            break;
                        case 3:
                            text = LocaleController.getString("DebugSendLogs", R.string.DebugSendLogs);
                            break;
                        case 4:
                            text = LocaleController.getString("DebugClearLogs", R.string.DebugClearLogs);
                            break;
                        case 5:
                        default:
                            text = "Switch Backend";
                            break;
                    }
                    textCell.setText(text, BuildVars.LOGS_ENABLED || BuildVars.DEBUG_VERSION ? a != count - 1 : a != 2);
                    textCell.setTag(a);
                    textCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    linearLayoutInviteContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    textCell.setOnClickListener(v2 -> {
                        Integer tag = (Integer) v2.getTag();
                        switch (tag) {
                            case 0: {
                                showDialog(AlertsCreator.createSupportAlert(SettingsActivity.this));
                                break;
                            }
                            case 1:
                                Browser.openUrl(getParentActivity(), LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl));
                                break;
                            case 2:
                                Browser.openUrl(getParentActivity(), LocaleController.getString("PrivacyPolicyUrl", R.string.PrivacyPolicyUrl));
                                break;
                            case 3:
                                sendLogs();
                                break;
                            case 4:
                                FileLog.cleanupLogs();
                                break;
                            case 5: {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                                builder1.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                                builder1.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder1.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                    SharedConfig.pushAuthKey = null;
                                    SharedConfig.pushAuthKeyId = null;
                                    SharedConfig.saveConfig();
                                    ConnectionsManager.getInstance(currentAccount).switchBackend();
                                });
                                builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                showDialog(builder1.create());
                                break;
                            }
                        }
                        builder.getDismissRunnable().run();
                    });
                }
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (position == languageRow) {
                presentFragment(new LanguageSelectActivity());
            } else if (position == usernameRow) {
                presentFragment(new ChangeUsernameActivity());
            } else if (position == bioRow) {
                if (userInfo != null) {
                    presentFragment(new ChangeBioActivity());
                }
            } else if (position == numberRow) {
                presentFragment(new ChangePhoneHelpActivity());
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {

            private int pressCount = 0;

            @Override
            public boolean onItemClick(View view, int position) {
                if (position == versionRow) {
                    pressCount++;
                    if (pressCount >= 2 || BuildVars.DEBUG_PRIVATE_VERSION) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("DebugMenu", R.string.DebugMenu));
                        CharSequence[] items;
                        items = new CharSequence[]{
                                LocaleController.getString("DebugMenuImportContacts", R.string.DebugMenuImportContacts),
                                LocaleController.getString("DebugMenuReloadContacts", R.string.DebugMenuReloadContacts),
                                LocaleController.getString("DebugMenuResetContacts", R.string.DebugMenuResetContacts),
                                LocaleController.getString("DebugMenuResetDialogs", R.string.DebugMenuResetDialogs),
                                BuildVars.LOGS_ENABLED ? LocaleController.getString("DebugMenuDisableLogs", R.string.DebugMenuDisableLogs) : LocaleController.getString("DebugMenuEnableLogs", R.string.DebugMenuEnableLogs),
                                SharedConfig.inappCamera ? LocaleController.getString("DebugMenuDisableCamera", R.string.DebugMenuDisableCamera) : LocaleController.getString("DebugMenuEnableCamera", R.string.DebugMenuEnableCamera),
                                LocaleController.getString("DebugMenuClearMediaCache", R.string.DebugMenuClearMediaCache),
                                LocaleController.getString("DebugMenuCallSettings", R.string.DebugMenuCallSettings),
                                null,
                                BuildVars.DEBUG_PRIVATE_VERSION ? "Check for app updates" : null
                        };
                        builder.setItems(items, (dialog, which) -> {
                            if (which == 0) {
                                UserConfig.getInstance(currentAccount).syncContacts = true;
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                                ContactsController.getInstance(currentAccount).forceImportContacts();
                            } else if (which == 1) {
                                ContactsController.getInstance(currentAccount).loadContacts(false, 0);
                            } else if (which == 2) {
                                ContactsController.getInstance(currentAccount).resetImportedContacts();
                            } else if (which == 3) {
                                MessagesController.getInstance(currentAccount).forceResetDialogs();
                            } else if (which == 4) {
                                BuildVars.LOGS_ENABLED = !BuildVars.LOGS_ENABLED;
                                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
                                sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED).commit();
                            } else if (which == 5) {
                                SharedConfig.toggleInappCamera();
                            } else if (which == 6) {
                                MessagesStorage.getInstance(currentAccount).clearSentMedia();
                                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                                SharedConfig.setNoSoundHintShowed(false);
                            } else if (which == 7) {
                                VoIPHelper.showCallDebugSettings(getParentActivity());
                            } else if (which == 8) {
                                SharedConfig.toggleRoundCamera16to9();
                            } else if (which == 9) {
                                ((LaunchActivity) getParentActivity()).checkAppUpdate(true);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else {
                        try {
                            Toast.makeText(getParentActivity(), "¯\\_(ツ)_/¯", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        frameLayout.addView(actionBar);

        extraHeightView = new View(context);
        extraHeightView.setPivotY(0);
        extraHeightView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        frameLayout.addView(extraHeightView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 88));

        shadowView = new View(context);
        shadowView.setBackgroundResource(R.drawable.header_shadow);
        frameLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));

        avatarContainer = new FrameLayout(context);
        avatarContainer.setPivotX(LocaleController.isRTL ? AndroidUtilities.dp(42) : 0);
        avatarContainer.setPivotY(0);
        frameLayout.addView(avatarContainer, LayoutHelper.createFrame(42, 42, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 0 : 64), 0, (LocaleController.isRTL ? 64 : 0), 0));
        avatarContainer.setOnClickListener(v -> {
            if (avatar != null) {
                return;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().openPhoto(user.photo.photo_big, provider);
            }
        });

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarContainer.addView(avatarImage, LayoutHelper.createFrame(42, 42));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);

        avatarProgressView = new RadialProgressView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, AndroidUtilities.dp(21), paint);
                }
                super.onDraw(canvas);
            }
        };
        avatarProgressView.setSize(AndroidUtilities.dp(26));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarContainer.addView(avatarProgressView, LayoutHelper.createFrame(42, 42));

        showAvatarProgress(false, false);

        nameTextView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotX(LocaleController.isRTL ? getMeasuredWidth() : 0);
            }
        };
        nameTextView.setTextColor(Theme.getColor(Theme.key_profile_title));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setPivotY(0);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 48 : 118, 0, LocaleController.isRTL ? 118 : 48, 0));

        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Theme.getColor(Theme.key_avatar_subtitleInProfileBlue));
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        frameLayout.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 48 : 118, 0, LocaleController.isRTL ? 118 : 48, 0));

        writeButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(drawable);
        writeButton.setImageResource(R.drawable.menu_camera_av);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(writeButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(writeButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            writeButton.setStateListAnimator(animator);
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        frameLayout.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 16 : 0, 0, LocaleController.isRTL ? 0 : 16, 0));
        writeButton.setOnClickListener(v -> {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user == null) {
                user = UserConfig.getInstance(currentAccount).getCurrentUser();
            }
            if (user == null) {
                return;
            }
            imageUpdater.openMenu(user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty), () -> MessagesController.getInstance(currentAccount).deleteUserPhoto(null));
        });

        if (scrollTo != -1) {
            layoutManager.scrollToPositionWithOffset(scrollTo, scrollToPosition);

            if (writeButtonTag != null) {
                writeButton.setTag(0);
                writeButton.setScaleX(0.2f);
                writeButton.setScaleY(0.2f);
                writeButton.setAlpha(0.0f);
                writeButton.setVisibility(View.GONE);
            }
        }

        needLayout();

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (layoutManager.getItemCount() == 0) {
                    return;
                }
                int height = 0;
                View child = recyclerView.getChildAt(0);
                if (child != null) {
                    if (layoutManager.findFirstVisibleItemPosition() == 0) {
                        height = AndroidUtilities.dp(88) + (child.getTop() < 0 ? child.getTop() : 0);
                    }
                    if (extraHeight != height) {
                        extraHeight = height;
                        needLayout();
                    }
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
        AndroidUtilities.runOnUIThread(() -> {
            if (file != null) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.file = file;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                        if (user == null) {
                            user = UserConfig.getInstance(currentAccount).getCurrentUser();
                            if (user == null) {
                                return;
                            }
                            MessagesController.getInstance(currentAccount).putUser(user, false);
                        } else {
                            UserConfig.getInstance(currentAccount).setCurrentUser(user);
                        }
                        TLRPC.TL_photos_photo photo = (TLRPC.TL_photos_photo) response;
                        ArrayList<TLRPC.PhotoSize> sizes = photo.photo.sizes;
                        TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150);
                        TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800);
                        user.photo = new TLRPC.TL_userProfilePhoto();
                        user.photo.photo_id = photo.photo.id;
                        if (small != null) {
                            user.photo.photo_small = small.location;
                        }
                        if (big != null) {
                            user.photo.photo_big = big.location;
                        } else if (small != null) {
                            user.photo.photo_small = small.location;
                        }

                        if (photo != null) {
                            if (small != null && avatar != null) {
                                File destFile = FileLoader.getPathToAttach(small, true);
                                File src = FileLoader.getPathToAttach(avatar, true);
                                src.renameTo(destFile);
                                String oldKey = avatar.volume_id + "_" + avatar.local_id + "@50_50";
                                String newKey = small.location.volume_id + "_" + small.location.local_id + "@50_50";
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, small.location, true);
                            }
                            if (big != null && avatarBig != null) {
                                File destFile = FileLoader.getPathToAttach(big, true);
                                File src = FileLoader.getPathToAttach(avatarBig, true);
                                src.renameTo(destFile);
                            }
                        }

                        MessagesStorage.getInstance(currentAccount).clearUserPhotos(user.id);
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        avatar = null;
                        avatarBig = null;
                        updateUserData();
                        showAvatarProgress(false, true);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                        UserConfig.getInstance(currentAccount).saveConfig(true);
                    });
                });
            } else {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(avatar, "50_50", avatarDrawable, null);
                showAvatarProgress(true, false);
            }
        });
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarProgressView == null) {
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
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarProgressView == null) {
                        return;
                    }
                    if (!show) {
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
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
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
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (imageUpdater != null) {
            imageUpdater.currentPicturePath = args.getString("path");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateUserData();
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            Integer uid = (Integer) args[0];
            if (uid == UserConfig.getInstance(currentAccount).getClientUserId() && listAdapter != null) {
                userInfo = (TLRPC.TL_userFull) args[1];
                listAdapter.notifyItemChanged(bioRow);
            }
        } else if (id == NotificationCenter.emojiDidLoad) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateUserData();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void needLayout() {
        FrameLayout.LayoutParams layoutParams;
        int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
                extraHeightView.setTranslationY(newTop);
            }
        }

        if (avatarContainer != null) {
            float diff = extraHeight / (float) AndroidUtilities.dp(88);
            extraHeightView.setScaleY(diff);
            shadowView.setTranslationY(newTop + extraHeight);

            writeButton.setTranslationY((actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + extraHeight - AndroidUtilities.dp(29.5f));

            final boolean setVisible = diff > 0.2f;
            boolean currentVisible = writeButton.getTag() == null;
            if (setVisible != currentVisible) {
                if (setVisible) {
                    writeButton.setTag(null);
                    writeButton.setVisibility(View.VISIBLE);
                } else {
                    writeButton.setTag(0);
                }
                if (writeButtonAnimation != null) {
                    AnimatorSet old = writeButtonAnimation;
                    writeButtonAnimation = null;
                    old.cancel();
                }
                writeButtonAnimation = new AnimatorSet();
                if (setVisible) {
                    writeButtonAnimation.setInterpolator(new DecelerateInterpolator());
                    writeButtonAnimation.playTogether(
                            ObjectAnimator.ofFloat(writeButton, "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(writeButton, "scaleY", 1.0f),
                            ObjectAnimator.ofFloat(writeButton, "alpha", 1.0f)
                    );
                } else {
                    writeButtonAnimation.setInterpolator(new AccelerateInterpolator());
                    writeButtonAnimation.playTogether(
                            ObjectAnimator.ofFloat(writeButton, "scaleX", 0.2f),
                            ObjectAnimator.ofFloat(writeButton, "scaleY", 0.2f),
                            ObjectAnimator.ofFloat(writeButton, "alpha", 0.0f)
                    );
                }
                writeButtonAnimation.setDuration(150);
                writeButtonAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (writeButtonAnimation != null && writeButtonAnimation.equals(animation)) {
                            writeButton.setVisibility(setVisible ? View.VISIBLE : View.GONE);
                            writeButtonAnimation = null;
                        }
                    }
                });
                writeButtonAnimation.start();
            }

            avatarContainer.setScaleX((42 + 18 * diff) / 42.0f);
            avatarContainer.setScaleY((42 + 18 * diff) / 42.0f);
            avatarProgressView.setSize(AndroidUtilities.dp(26 / avatarContainer.getScaleX()));
            avatarProgressView.setStrokeWidth(3 / avatarContainer.getScaleX());
            float avatarY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff;
            avatarContainer.setTranslationY((float) Math.ceil(avatarY));
            nameTextView.setTranslationY((float) Math.floor(avatarY) - (float) Math.ceil(AndroidUtilities.density) + (float) Math.floor(7 * AndroidUtilities.density * diff));
            onlineTextView.setTranslationY((float) Math.floor(avatarY) + AndroidUtilities.dp(22) + (float )Math.floor(11 * AndroidUtilities.density) * diff);
            nameTextView.setScaleX(1.0f + 0.12f * diff);
            nameTextView.setScaleY(1.0f + 0.12f * diff);

            if (LocaleController.isRTL) {
                avatarContainer.setTranslationX(AndroidUtilities.dp(47) * diff);
                nameTextView.setTranslationX(21 * AndroidUtilities.density * diff);
                onlineTextView.setTranslationX(21 * AndroidUtilities.density * diff);
            } else {
                avatarContainer.setTranslationX(-AndroidUtilities.dp(47) * diff);
                nameTextView.setTranslationX(-21 * AndroidUtilities.density * diff);
                onlineTextView.setTranslationX(-21 * AndroidUtilities.density * diff);
            }
        }
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    private void updateUserData() {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (user == null) {
            return;
        }
        TLRPC.FileLocation photo = null;
        TLRPC.FileLocation photoBig = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
            photoBig = user.photo.photo_big;
        }
        avatarDrawable = new AvatarDrawable(user, true);

        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));
        if (avatarImage != null) {
            avatarImage.setImage(photo, "50_50", avatarDrawable, user);
            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);

            nameTextView.setText(UserObject.getUserName(user));
            onlineTextView.setText(LocaleController.getString("Online", R.string.Online));

            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);
        }
    }

    private void sendLogs() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null);
                File dir = new File(sdCard.getAbsolutePath() + "/logs");

                File zipFile = new File(dir, "logs.zip");
                if (zipFile.exists()) {
                    zipFile.delete();
                }

                File[] files = dir.listFiles();

                boolean[] finished = new boolean[1];

                BufferedInputStream origin = null;
                ZipOutputStream out = null;
                try {
                    FileOutputStream dest = new FileOutputStream(zipFile);
                    out = new ZipOutputStream(new BufferedOutputStream(dest));
                    byte data[] = new byte[1024 * 64];

                    for (int i = 0; i < files.length; i++) {
                        FileInputStream fi = new FileInputStream(files[i]);
                        origin = new BufferedInputStream(fi, data.length);

                        ZipEntry entry = new ZipEntry(files[i].getName());
                        out.putNextEntry(entry);
                        int count;
                        while ((count = origin.read(data, 0, data.length)) != -1) {
                            out.write(data, 0, count);
                        }
                        if (origin != null) {
                            origin.close();
                            origin = null;
                        }
                    }
                    finished[0] = true;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (origin != null) {
                        origin.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignore) {

                    }
                    if (finished[0]) {
                        Uri uri;
                        if (Build.VERSION.SDK_INT >= 24) {
                            uri = FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", zipFile);
                        } else {
                            uri = Uri.fromFile(zipFile);
                        }

                        Intent i = new Intent(Intent.ACTION_SEND);
                        if (Build.VERSION.SDK_INT >= 24) {
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        i.setType("message/rfc822");
                        i.putExtra(Intent.EXTRA_EMAIL, "");
                        i.putExtra(Intent.EXTRA_SUBJECT, "Logs from " + LocaleController.getInstance().formatterStats.format(System.currentTimeMillis()));
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        getParentActivity().startActivityForResult(Intent.createChooser(i, "Select email application."), 500);
                    } else {
                        Toast.makeText(getParentActivity(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (position == overscrollRow) {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(88));
                    }
                    break;
                }
                case 2: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == languageRow) {
                        textCell.setTextAndIcon(LocaleController.getString("Language", R.string.Language), R.drawable.menu_language, true);
                    } else if (position == notificationRow) {
                        textCell.setTextAndIcon(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, true);
                    } else if (position == privacyRow) {
                        textCell.setTextAndIcon(LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, true);
                    } else if (position == dataRow) {
                        textCell.setTextAndIcon(LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, true);
                    } else if (position == chatRow) {
                        textCell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, true);
                    } else if (position == helpRow) {
                        textCell.setTextAndIcon(LocaleController.getString("SettingsHelp", R.string.SettingsHelp), R.drawable.menu_help, false);
                    }
                    break;
                }
                case 4: {
                    if (position == settingsSectionRow2) {
                        ((HeaderCell) holder.itemView).setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                    } else if (position == numberSectionRow) {
                        ((HeaderCell) holder.itemView).setText(LocaleController.getString("Account", R.string.Account));
                    }
                    break;
                }
                case 6: {
                    TextDetailCell textCell = (TextDetailCell) holder.itemView;

                    if (position == numberRow) {
                        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                        String value;
                        if (user != null && user.phone != null && user.phone.length() != 0) {
                            value = PhoneFormat.getInstance().format("+" + user.phone);
                        } else {
                            value = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                        }
                        textCell.setTextAndValue(value, LocaleController.getString("TapToChangePhone", R.string.TapToChangePhone), true);
                    } else if (position == usernameRow) {
                        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                        String value;
                        if (user != null && !TextUtils.isEmpty(user.username)) {
                            value = "@" + user.username;
                        } else {
                            value = LocaleController.getString("UsernameEmpty", R.string.UsernameEmpty);
                        }
                        textCell.setTextAndValue(value, LocaleController.getString("Username", R.string.Username), true);
                    } else if (position == bioRow) {
                        String value;
                        if (userInfo == null || !TextUtils.isEmpty(userInfo.about)) {
                            value = userInfo == null ? LocaleController.getString("Loading", R.string.Loading) : userInfo.about;
                            textCell.setTextWithEmojiAndValue(value, LocaleController.getString("UserBio", R.string.UserBio), false);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("UserBio", R.string.UserBio), LocaleController.getString("UserBioDetail", R.string.UserBioDetail), false);
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == notificationRow || position == numberRow || position == privacyRow ||
                    position == languageRow || position == usernameRow || position == bioRow ||
                    position == versionRow || position == dataRow || position == chatRow ||
                    position == helpRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new EmptyCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new HeaderCell(mContext, 23);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(mContext, 10);
                    cell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.getTextView().setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    cell.getTextView().setMovementMethod(null);
                    cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    try {
                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        int code = pInfo.versionCode / 10;
                        String abi = "";
                        switch (pInfo.versionCode % 10) {
                            case 1:
                            case 3:
                                abi = "arm-v7a";
                                break;
                            case 2:
                            case 4:
                                abi = "x86";
                                break;
                            case 5:
                            case 7:
                                abi = "arm64-v8a";
                                break;
                            case 6:
                            case 8:
                                abi = "x86_64";
                                break;
                            case 0:
                            case 9:
                                abi = "universal " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                                break;
                        }
                        cell.setText(LocaleController.formatString("TelegramVersion", R.string.TelegramVersion, String.format(Locale.US, "v%s (%d) %s", pInfo.versionName, code, abi)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    cell.getTextView().setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
                    view = cell;
                    break;
                case 6:
                    view = new TextDetailCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == overscrollRow) {
                return 0;
            } else if (position == settingsSectionRow) {
                return 1;
            } else if (position == notificationRow || position == privacyRow || position == languageRow ||
                    position == dataRow || position == chatRow || position == helpRow) {
                return 2;
            } else if (position == versionRow) {
                return 5;
            } else if (position == numberRow || position == usernameRow || position == bioRow) {
                return 6;
            } else if (position == settingsSectionRow2 || position == numberSectionRow) {
                return 4;
            } else {
                return 2;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, HeaderCell.class, TextDetailCell.class, TextCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(extraHeightView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_title),
                new ThemeDescription(onlineTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_avatar_subtitleInProfileBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{avatarDrawable}, null, Theme.key_avatar_backgroundInProfileBlue),

                new ThemeDescription(writeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionBackground),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionPressedBackground),
        };
    }
}
