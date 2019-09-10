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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
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
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private SearchAdapter searchAdapter;
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
    private ActionBarMenuItem otherItem;
    private EmptyTextProgressView emptyView;

    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;

    private TLRPC.UserFull userInfo;

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
    private final static int search_button = 3;

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
                    int[] coords = new int[2];
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

        MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
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
        ActionBarMenuItem searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                if (otherItem != null) {
                    otherItem.setVisibility(View.GONE);
                }
                searchAdapter.loadFaqWebPage();
                listView.setAdapter(searchAdapter);
                listView.setEmptyView(emptyView);
                avatarContainer.setVisibility(View.GONE);
                writeButton.setVisibility(View.GONE);
                nameTextView.setVisibility(View.GONE);
                onlineTextView.setVisibility(View.GONE);
                extraHeightView.setVisibility(View.GONE);
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                fragmentView.setTag(Theme.key_windowBackgroundWhite);
                needLayout();
            }

            @Override
            public void onSearchCollapse() {
                if (otherItem != null) {
                    otherItem.setVisibility(View.VISIBLE);
                }
                listView.setAdapter(listAdapter);
                listView.setEmptyView(null);
                emptyView.setVisibility(View.GONE);
                avatarContainer.setVisibility(View.VISIBLE);
                writeButton.setVisibility(View.VISIBLE);
                nameTextView.setVisibility(View.VISIBLE);
                onlineTextView.setVisibility(View.VISIBLE);
                extraHeightView.setVisibility(View.VISIBLE);
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                fragmentView.setTag(Theme.key_windowBackgroundGray);
                needLayout();
            }

            @Override
            public void onTextChanged(EditText editText) {
                searchAdapter.search(editText.getText().toString().toLowerCase());
            }
        });
        searchItem.setContentDescription(LocaleController.getString("SearchInSettings", R.string.SearchInSettings));
        searchItem.setSearchFieldHint(LocaleController.getString("SearchInSettings", R.string.SearchInSettings));

        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        otherItem.addSubItem(edit_name, R.drawable.msg_edit, LocaleController.getString("EditName", R.string.EditName));
        otherItem.addSubItem(logout, R.drawable.msg_leave, LocaleController.getString("LogOut", R.string.LogOut));

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
        searchAdapter = new SearchAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);
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
            if (listView.getAdapter() == listAdapter) {
                if (position == notificationRow) {
                    presentFragment(new NotificationsSettingsActivity());
                } else if (position == privacyRow) {
                    presentFragment(new PrivacySettingsActivity());
                } else if (position == dataRow) {
                    presentFragment(new DataSettingsActivity());
                } else if (position == chatRow) {
                    presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                } else if (position == helpRow) {
                    showHelpAlert();
                } else if (position == languageRow) {
                    presentFragment(new LanguageSelectActivity());
                } else if (position == usernameRow) {
                    presentFragment(new ChangeUsernameActivity());
                } else if (position == bioRow) {
                    if (userInfo != null) {
                        presentFragment(new ChangeBioActivity());
                    }
                } else if (position == numberRow) {
                    presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
                }
            } else {
                if (position < 0) {
                    return;
                }
                Object object = numberRow;
                if (searchAdapter.searchWas) {
                    if (position < searchAdapter.searchResults.size()) {
                        object = searchAdapter.searchResults.get(position);
                    } else {
                        position -= searchAdapter.searchResults.size() + 1;
                        if (position >= 0 && position < searchAdapter.faqSearchResults.size()) {
                            object = searchAdapter.faqSearchResults.get(position);
                        }
                    }
                } else {
                    position--;
                    if (position < 0) {
                        return;
                    }
                    if (position < searchAdapter.recentSearches.size()) {
                        object = searchAdapter.recentSearches.get(position);
                    }
                }
                if (object instanceof SearchAdapter.SearchResult) {
                    SearchAdapter.SearchResult result = (SearchAdapter.SearchResult) object;
                    result.open();
                } else if (object instanceof SearchAdapter.FaqSearchResult) {
                    SearchAdapter.FaqSearchResult result = (SearchAdapter.FaqSearchResult) object;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.openArticle, searchAdapter.faqWebPage, result.url);
                }
                if (object != null) {
                    searchAdapter.addRecent(object);
                }
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {

            private int pressCount = 0;

            @Override
            public boolean onItemClick(View view, int position) {
                if (listView.getAdapter() == searchAdapter) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> searchAdapter.clearRecent());
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                    return true;
                }
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
                                BuildVars.DEBUG_PRIVATE_VERSION ? "Check for app updates" : null,
                                LocaleController.getString("DebugMenuReadAllDialogs", R.string.DebugMenuReadAllDialogs),
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
                                SharedConfig.setNoSoundHintShowed(false);
                                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                                editor.remove("archivehint").remove("archivehint_l").remove("gifhint").remove("soundHint").commit();
                            } else if (which == 7) {
                                VoIPHelper.showCallDebugSettings(getParentActivity());
                            } else if (which == 8) {
                                SharedConfig.toggleRoundCamera16to9();
                            } else if (which == 9) {
                                ((LaunchActivity) getParentActivity()).checkAppUpdate(true);
                            } else if (which == 10) {
                                MessagesStorage.getInstance(currentAccount).readAllDialogs();
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

        emptyView = new EmptyTextProgressView(context);
        emptyView.showTextView();
        emptyView.setTextSize(18);
        emptyView.setVisibility(View.GONE);
        emptyView.setShowAtCenter(true);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        frameLayout.addView(avatarContainer, LayoutHelper.createFrame(42, 42, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 0 : 64), 0, (LocaleController.isRTL ? 112 : 0), 0));
        avatarContainer.setOnClickListener(v -> {
            if (avatar != null) {
                return;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                if (user.photo.dc_id != 0) {
                    user.photo.photo_big.dc_id = user.photo.dc_id;
                }
                PhotoViewer.getInstance().openPhoto(user.photo.photo_big, provider);
            }
        });

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarImage.setContentDescription(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
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
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 48 : 118, 0, LocaleController.isRTL ? 166 : 96, 0));

        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Theme.getColor(Theme.key_profile_status));
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        frameLayout.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 48 : 118, 0, LocaleController.isRTL ? 166 : 96, 0));

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
        writeButton.setContentDescription(LocaleController.getString("AccDescrChangeProfilePicture", R.string.AccDescrChangeProfilePicture));

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
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (listView.getAdapter() == searchAdapter) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (layoutManager.getItemCount() == 0) {
                    return;
                }
                int height = 0;
                View child = recyclerView.getChildAt(0);
                if (child != null && avatarContainer.getVisibility() == View.VISIBLE) {
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
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUser(user, false), true);
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
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
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
                userInfo = (TLRPC.UserFull) args[1];
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
        setParentActivityTitle(LocaleController.getString("Settings", R.string.Settings));
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
            layoutParams = (FrameLayout.LayoutParams) emptyView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                emptyView.setLayoutParams(layoutParams);
            }
        }

        if (avatarContainer != null) {
            int currentExtraHeight;
            if (avatarContainer.getVisibility() == View.VISIBLE) {
                currentExtraHeight = extraHeight;
            } else {
                currentExtraHeight = 0;
            }

            float diff = currentExtraHeight / (float) AndroidUtilities.dp(88);
            extraHeightView.setScaleY(diff);
            shadowView.setTranslationY(newTop + currentExtraHeight);

            writeButton.setTranslationY((actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + currentExtraHeight - AndroidUtilities.dp(29.5f));

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
            onlineTextView.setTranslationY((float) Math.floor(avatarY) + AndroidUtilities.dp(22) + (float) Math.floor(11 * AndroidUtilities.density) * diff);
            nameTextView.setScaleX(1.0f + 0.12f * diff);
            nameTextView.setScaleY(1.0f + 0.12f * diff);

            if (LocaleController.isRTL) {
                avatarContainer.setTranslationX(AndroidUtilities.dp(47 + 48) * diff);
                nameTextView.setTranslationX((21 + 48) * AndroidUtilities.density * diff);
                onlineTextView.setTranslationX((21 + 48) * AndroidUtilities.density * diff);
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
        TLRPC.FileLocation photoBig = null;
        if (user.photo != null) {
            photoBig = user.photo.photo_big;
        }
        avatarDrawable = new AvatarDrawable(user, true);

        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));
        if (avatarImage != null) {
            avatarImage.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);

            nameTextView.setText(UserObject.getUserName(user));
            onlineTextView.setText(LocaleController.getString("Online", R.string.Online));

            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);
        }
    }

    private void showHelpAlert() {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
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
                    byte[] data = new byte[1024 * 64];

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

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private class SearchResult {

            private String searchTitle;
            private Runnable openRunnable;
            private String rowName;
            private String[] path;
            private int iconResId;
            private int guid;
            private int num;

            public SearchResult(int g, String search, int icon, Runnable open) {
                this(g, search, null, null, null, icon, open);
            }

            public SearchResult(int g, String search, String pathArg1, int icon, Runnable open) {
                this(g, search, null, pathArg1, null, icon, open);
            }

            public SearchResult(int g, String search, String row, String pathArg1, int icon, Runnable open) {
                this(g, search, row, pathArg1, null, icon, open);
            }

            public SearchResult(int g, String search, String row, String pathArg1, String pathArg2, int icon, Runnable open) {
                guid = g;
                searchTitle = search;
                rowName = row;
                openRunnable = open;
                iconResId = icon;
                if (pathArg1 != null && pathArg2 != null) {
                    path = new String[]{pathArg1, pathArg2};
                } else if (pathArg1 != null) {
                    path = new String[]{pathArg1};
                }
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof SearchResult)) {
                    return false;
                }
                SearchResult result = (SearchResult) obj;
                return guid == result.guid;
            }

            @Override
            public String toString() {
                SerializedData data = new SerializedData();
                data.writeInt32(num);
                data.writeInt32(1);
                data.writeInt32(guid);
                return Utilities.bytesToHex(data.toByteArray());
            }

            private void open() {
                openRunnable.run();
                if (rowName != null) {

                    BaseFragment openingFragment = parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1);
                    try {
                        Field listViewField = openingFragment.getClass().getDeclaredField("listView");
                        listViewField.setAccessible(true);
                        RecyclerListView.IntReturnCallback callback = () -> {
                            int position = -1;
                            try {
                                Field rowField = openingFragment.getClass().getDeclaredField(rowName);
                                Field linearLayoutField = openingFragment.getClass().getDeclaredField("layoutManager");
                                rowField.setAccessible(true);
                                linearLayoutField.setAccessible(true);
                                LinearLayoutManager layoutManager = (LinearLayoutManager) linearLayoutField.get(openingFragment);
                                position = rowField.getInt(openingFragment);
                                layoutManager.scrollToPositionWithOffset(position, 0);
                                rowField.setAccessible(false);
                                linearLayoutField.setAccessible(false);
                                return position;
                            } catch (Throwable ignore) {

                            }
                            return position;
                        };
                        RecyclerListView listView = (RecyclerListView) listViewField.get(openingFragment);
                        listView.highlightRow(callback);
                        listViewField.setAccessible(false);
                    } catch (Throwable ignore) {

                    }
                }
            }
        }

        private class FaqSearchResult {

            private String title;
            private String[] path;
            private String url;
            private int num;

            public FaqSearchResult(String t, String[] p, String u) {
                title = t;
                path = p;
                url = u;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof FaqSearchResult)) {
                    return false;
                }
                FaqSearchResult result = (FaqSearchResult) obj;
                return title.equals(result.title);
            }

            @Override
            public String toString() {
                SerializedData data = new SerializedData();
                data.writeInt32(num);
                data.writeInt32(0);
                data.writeString(title);
                data.writeInt32(path != null ? path.length : 0);
                if (path != null) {
                    for (int a = 0; a < path.length; a++) {
                        data.writeString(path[a]);
                    }
                }
                data.writeString(url);
                return Utilities.bytesToHex(data.toByteArray());
            }
        }

        private SearchResult[] searchArray = new SearchResult[]{
                new SearchResult(500, LocaleController.getString("EditName", R.string.EditName), 0, () -> presentFragment(new ChangeNameActivity())),
                new SearchResult(501, LocaleController.getString("ChangePhoneNumber", R.string.ChangePhoneNumber), 0, () -> presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER))),
                new SearchResult(502, LocaleController.getString("AddAnotherAccount", R.string.AddAnotherAccount), 0, () -> {
                    int freeAccount = -1;
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (!UserConfig.getInstance(a).isClientActivated()) {
                            freeAccount = a;
                            break;
                        }
                    }
                    if (freeAccount >= 0) {
                        presentFragment(new LoginActivity(freeAccount));
                    }
                }),
                new SearchResult(503, LocaleController.getString("UserBio", R.string.UserBio), 0, () -> {
                    if (userInfo != null) {
                        presentFragment(new ChangeBioActivity());
                    }
                }),

                new SearchResult(1, LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(2, LocaleController.getString("NotificationsPrivateChats", R.string.NotificationsPrivateChats), LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsCustomSettingsActivity(NotificationsController.TYPE_PRIVATE, new ArrayList<>(), true))),
                new SearchResult(3, LocaleController.getString("NotificationsGroups", R.string.NotificationsGroups), LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsCustomSettingsActivity(NotificationsController.TYPE_GROUP, new ArrayList<>(), true))),
                new SearchResult(4, LocaleController.getString("NotificationsChannels", R.string.NotificationsChannels), LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsCustomSettingsActivity(NotificationsController.TYPE_CHANNEL, new ArrayList<>(), true))),
                new SearchResult(5, LocaleController.getString("VoipNotificationSettings", R.string.VoipNotificationSettings), "callsSectionRow", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(6, LocaleController.getString("BadgeNumber", R.string.BadgeNumber), "badgeNumberSection", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(7, LocaleController.getString("InAppNotifications", R.string.InAppNotifications), "inappSectionRow", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(8, LocaleController.getString("ContactJoined", R.string.ContactJoined), "contactJoinedRow", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(9, LocaleController.getString("PinnedMessages", R.string.PinnedMessages), "pinnedMessageRow", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),
                new SearchResult(10, LocaleController.getString("ResetAllNotifications", R.string.ResetAllNotifications), "resetNotificationsRow", LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.menu_notifications, () -> presentFragment(new NotificationsSettingsActivity())),

                new SearchResult(100, LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(101, LocaleController.getString("BlockedUsers", R.string.BlockedUsers), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyUsersActivity())),
                new SearchResult(105, LocaleController.getString("PrivacyPhone", R.string.PrivacyPhone), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE, true))),
                new SearchResult(102, LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN, true))),
                new SearchResult(103, LocaleController.getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO, true))),
                new SearchResult(104, LocaleController.getString("PrivacyForwards", R.string.PrivacyForwards), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS, true))),
                new SearchResult(105, LocaleController.getString("PrivacyP2P", R.string.PrivacyP2P), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_P2P, true))),
                new SearchResult(106, LocaleController.getString("Calls", R.string.Calls), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS, true))),
                new SearchResult(107, LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE, true))),
                new SearchResult(108, LocaleController.getString("Passcode", R.string.Passcode), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PasscodeActivity(SharedConfig.passcodeHash.length() > 0 ? 2 : 0))),
                new SearchResult(109, LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new TwoStepVerificationActivity(0))),
                new SearchResult(110, LocaleController.getString("SessionsTitle", R.string.SessionsTitle), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new SessionsActivity(0))),
                new SearchResult(111, LocaleController.getString("PrivacyDeleteCloudDrafts", R.string.PrivacyDeleteCloudDrafts), "clearDraftsRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(112, LocaleController.getString("DeleteAccountIfAwayFor2", R.string.DeleteAccountIfAwayFor2), "deleteAccountRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(113, LocaleController.getString("PrivacyPaymentsClear", R.string.PrivacyPaymentsClear), "paymentsClearRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(114, LocaleController.getString("WebSessionsTitle", R.string.WebSessionsTitle), LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new SessionsActivity(1))),
                new SearchResult(115, LocaleController.getString("SyncContactsDelete", R.string.SyncContactsDelete), "contactsDeleteRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(116, LocaleController.getString("SyncContacts", R.string.SyncContacts), "contactsSyncRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(117, LocaleController.getString("SuggestContacts", R.string.SuggestContacts), "contactsSuggestRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(118, LocaleController.getString("MapPreviewProvider", R.string.MapPreviewProvider), "secretMapRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),
                new SearchResult(119, LocaleController.getString("SecretWebPage", R.string.SecretWebPage), "secretWebpageRow", LocaleController.getString("PrivacySettings", R.string.PrivacySettings), R.drawable.menu_secret, () -> presentFragment(new PrivacySettingsActivity())),

                new SearchResult(200, LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(201, LocaleController.getString("DataUsage", R.string.DataUsage), "usageSectionRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(202, LocaleController.getString("StorageUsage", R.string.StorageUsage), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new CacheControlActivity())),
                new SearchResult(203, LocaleController.getString("KeepMedia", R.string.KeepMedia), "keepMediaRow", LocaleController.getString("DataSettings", R.string.DataSettings), LocaleController.getString("StorageUsage", R.string.StorageUsage), R.drawable.menu_data, () -> presentFragment(new CacheControlActivity())),
                new SearchResult(204, LocaleController.getString("ClearMediaCache", R.string.ClearMediaCache), "cacheRow", LocaleController.getString("DataSettings", R.string.DataSettings), LocaleController.getString("StorageUsage", R.string.StorageUsage), R.drawable.menu_data, () -> presentFragment(new CacheControlActivity())),
                new SearchResult(205, LocaleController.getString("LocalDatabase", R.string.LocalDatabase), "databaseRow", LocaleController.getString("DataSettings", R.string.DataSettings), LocaleController.getString("StorageUsage", R.string.StorageUsage), R.drawable.menu_data, () -> presentFragment(new CacheControlActivity())),
                new SearchResult(206, LocaleController.getString("NetworkUsage", R.string.NetworkUsage), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataUsageActivity())),
                new SearchResult(207, LocaleController.getString("AutomaticMediaDownload", R.string.AutomaticMediaDownload), "mediaDownloadSectionRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(208, LocaleController.getString("WhenUsingMobileData", R.string.WhenUsingMobileData), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataAutoDownloadActivity(0))),
                new SearchResult(209, LocaleController.getString("WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataAutoDownloadActivity(1))),
                new SearchResult(210, LocaleController.getString("WhenRoaming", R.string.WhenRoaming), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataAutoDownloadActivity(2))),
                new SearchResult(211, LocaleController.getString("ResetAutomaticMediaDownload", R.string.ResetAutomaticMediaDownload), "resetDownloadRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(212, LocaleController.getString("AutoplayMedia", R.string.AutoplayMedia), "autoplayHeaderRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(213, LocaleController.getString("AutoplayGIF", R.string.AutoplayGIF), "autoplayGifsRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(214, LocaleController.getString("AutoplayVideo", R.string.AutoplayVideo), "autoplayVideoRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(215, LocaleController.getString("Streaming", R.string.Streaming), "streamSectionRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(216, LocaleController.getString("EnableStreaming", R.string.EnableStreaming), "enableStreamRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(217, LocaleController.getString("Calls", R.string.Calls), "callsSectionRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(218, LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), "useLessDataForCallsRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(219, LocaleController.getString("VoipQuickReplies", R.string.VoipQuickReplies), "quickRepliesRow", LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new DataSettingsActivity())),
                new SearchResult(220, LocaleController.getString("ProxySettings", R.string.ProxySettings), LocaleController.getString("DataSettings", R.string.DataSettings), R.drawable.menu_data, () -> presentFragment(new ProxyListActivity())),
                new SearchResult(221, LocaleController.getString("UseProxyForCalls", R.string.UseProxyForCalls), "callsRow", LocaleController.getString("DataSettings", R.string.DataSettings), LocaleController.getString("ProxySettings", R.string.ProxySettings), R.drawable.menu_data, () -> presentFragment(new ProxyListActivity())),

                new SearchResult(300, LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(301, LocaleController.getString("TextSizeHeader", R.string.TextSizeHeader), "textSizeHeaderRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(302, LocaleController.getString("ChatBackground", R.string.ChatBackground), LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL))),
                new SearchResult(303, LocaleController.getString("SetColor", R.string.SetColor), null, LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("ChatBackground", R.string.ChatBackground), R.drawable.menu_chats, () -> presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_COLOR))),
                new SearchResult(304, LocaleController.getString("ResetChatBackgrounds", R.string.ResetChatBackgrounds), "resetRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("ChatBackground", R.string.ChatBackground), R.drawable.menu_chats, () -> presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL))),
                new SearchResult(305, LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT))),
                new SearchResult(306, LocaleController.getString("ColorTheme", R.string.ColorTheme), "themeHeaderRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(307, LocaleController.getString("ChromeCustomTabs", R.string.ChromeCustomTabs), "customTabsRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(308, LocaleController.getString("DirectShare", R.string.DirectShare), "directShareRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(309, LocaleController.getString("EnableAnimations", R.string.EnableAnimations), "enableAnimationsRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(310, LocaleController.getString("RaiseToSpeak", R.string.RaiseToSpeak), "raiseToSpeakRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(311, LocaleController.getString("SendByEnter", R.string.SendByEnter), "sendByEnterRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(312, LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings), "saveToGalleryRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(312, LocaleController.getString("DistanceUnits", R.string.DistanceUnits), "distanceRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))),
                new SearchResult(313, LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.menu_chats, () -> presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE))),
                new SearchResult(314, LocaleController.getString("SuggestStickers", R.string.SuggestStickers), "suggestRow", LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), R.drawable.menu_chats, () -> presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE))),
                new SearchResult(315, LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), null, LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), R.drawable.menu_chats, () -> presentFragment(new FeaturedStickersActivity())),
                new SearchResult(316, LocaleController.getString("Masks", R.string.Masks), null, LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), R.drawable.menu_chats, () -> presentFragment(new StickersActivity(MediaDataController.TYPE_MASK))),
                new SearchResult(317, LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers), null, LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), R.drawable.menu_chats, () -> presentFragment(new ArchivedStickersActivity(MediaDataController.TYPE_IMAGE))),
                new SearchResult(317, LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks), null, LocaleController.getString("ChatSettings", R.string.ChatSettings), LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), R.drawable.menu_chats, () -> presentFragment(new ArchivedStickersActivity(MediaDataController.TYPE_MASK))),

                new SearchResult(400, LocaleController.getString("Language", R.string.Language), R.drawable.menu_language, () -> presentFragment(new LanguageSelectActivity())),

                new SearchResult(401, LocaleController.getString("SettingsHelp", R.string.SettingsHelp), R.drawable.menu_help, SettingsActivity.this::showHelpAlert),
                new SearchResult(402, LocaleController.getString("AskAQuestion", R.string.AskAQuestion), LocaleController.getString("SettingsHelp", R.string.SettingsHelp), R.drawable.menu_help, () -> showDialog(AlertsCreator.createSupportAlert(SettingsActivity.this))),
                new SearchResult(403, LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ), LocaleController.getString("SettingsHelp", R.string.SettingsHelp), R.drawable.menu_help, () -> Browser.openUrl(getParentActivity(), LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl))),
                new SearchResult(404, LocaleController.getString("PrivacyPolicy", R.string.PrivacyPolicy), LocaleController.getString("SettingsHelp", R.string.SettingsHelp), R.drawable.menu_help, () -> Browser.openUrl(getParentActivity(), LocaleController.getString("PrivacyPolicyUrl", R.string.PrivacyPolicyUrl))),
        };
        private ArrayList<FaqSearchResult> faqSearchArray = new ArrayList<>();

        private Context mContext;
        private ArrayList<CharSequence> resultNames = new ArrayList<>();
        private ArrayList<SearchResult> searchResults = new ArrayList<>();
        private ArrayList<FaqSearchResult> faqSearchResults = new ArrayList<>();
        private ArrayList<Object> recentSearches = new ArrayList<>();
        private boolean searchWas;
        private Runnable searchRunnable;
        private String lastSearchString;
        private TLRPC.WebPage faqWebPage;
        private boolean loadingFaqPage;

        public SearchAdapter(Context context) {
            mContext = context;

            HashMap<Integer, SearchResult> resultHashMap = new HashMap<>();
            for (int a = 0; a < searchArray.length; a++) {
                resultHashMap.put(searchArray[a].guid, searchArray[a]);
            }
            Set<String> set = MessagesController.getGlobalMainSettings().getStringSet("settingsSearchRecent2", null);
            if (set != null) {
                for (String value : set) {
                    try {
                        SerializedData data = new SerializedData(Utilities.hexToBytes(value));
                        int num = data.readInt32(false);
                        int type = data.readInt32(false);
                        if (type == 0) {
                            String title = data.readString(false);
                            int count = data.readInt32(false);
                            String[] path = null;
                            if (count > 0) {
                                path = new String[count];
                                for (int a = 0; a < count; a++) {
                                    path[a] = data.readString(false);
                                }
                            }
                            String url = data.readString(false);
                            FaqSearchResult result = new FaqSearchResult(title, path, url);
                            result.num = num;
                            recentSearches.add(result);
                        } else if (type == 1) {
                            SearchResult result = resultHashMap.get(data.readInt32(false));
                            if (result != null) {
                                result.num = num;
                                recentSearches.add(result);
                            }
                        }
                    } catch (Exception ignore) {

                    }
                }
            }
            Collections.sort(recentSearches, (o1, o2) -> {
                int n1 = getNum(o1);
                int n2 = getNum(o2);
                if (n1 < n2) {
                    return -1;
                } else if (n1 > n2) {
                    return 1;
                }
                return 0;
            });
        }

        private void loadFaqWebPage() {
            if (faqWebPage != null || loadingFaqPage) {
                return;
            }
            loadingFaqPage = true;
            final TLRPC.TL_messages_getWebPage req2 = new TLRPC.TL_messages_getWebPage();
            req2.url = LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl);
            req2.hash = 0;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response2, error2) -> {
                if (response2 instanceof TLRPC.WebPage) {
                    TLRPC.WebPage page = (TLRPC.WebPage) response2;
                    if (page.cached_page != null) {
                        for (int a = 0, N = page.cached_page.blocks.size(); a < N; a++) {
                            TLRPC.PageBlock block = page.cached_page.blocks.get(a);
                            if (block instanceof TLRPC.TL_pageBlockList) {
                                String paragraph = null;
                                if (a != 0) {
                                    TLRPC.PageBlock prevBlock = page.cached_page.blocks.get(a - 1);
                                    if (prevBlock instanceof TLRPC.TL_pageBlockParagraph) {
                                        TLRPC.TL_pageBlockParagraph pageBlockParagraph = (TLRPC.TL_pageBlockParagraph) prevBlock;
                                        paragraph = ArticleViewer.getPlainText(pageBlockParagraph.text).toString();
                                    }
                                }
                                TLRPC.TL_pageBlockList list = (TLRPC.TL_pageBlockList) block;
                                for (int b = 0, N2 = list.items.size(); b < N2; b++) {
                                    TLRPC.PageListItem item = list.items.get(b);
                                    if (item instanceof TLRPC.TL_pageListItemText) {
                                        TLRPC.TL_pageListItemText itemText = (TLRPC.TL_pageListItemText) item;
                                        String url = ArticleViewer.getUrl(itemText.text);
                                        String text = ArticleViewer.getPlainText(itemText.text).toString();
                                        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(text)) {
                                            continue;
                                        }
                                        String[] path;
                                        if (paragraph != null) {
                                            path = new String[]{LocaleController.getString("SettingsSearchFaq", R.string.SettingsSearchFaq), paragraph};
                                        } else {
                                            path = new String[]{LocaleController.getString("SettingsSearchFaq", R.string.SettingsSearchFaq)};
                                        }
                                        faqSearchArray.add(new FaqSearchResult(text, path, url));
                                    }
                                }
                            } else if (block instanceof TLRPC.TL_pageBlockAnchor) {
                                break;
                            }
                        }
                        faqWebPage = page;
                    }
                }
                loadingFaqPage = false;
            });
        }

        @Override
        public int getItemCount() {
            if (searchWas) {
                return searchResults.size() + (faqSearchResults.isEmpty() ? 0 : 1 + faqSearchResults.size());
            }
            return (recentSearches.isEmpty() ? 0 : recentSearches.size() + 1);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    SettingsSearchCell searchCell = (SettingsSearchCell) holder.itemView;
                    if (searchWas) {
                        if (position < searchResults.size()) {
                            SearchResult result = searchResults.get(position);
                            SearchResult prevResult = position > 0 ? searchResults.get(position - 1) : null;
                            int icon;
                            if (prevResult != null && prevResult.iconResId == result.iconResId) {
                                icon = 0;
                            } else {
                                icon = result.iconResId;
                            }
                            searchCell.setTextAndValueAndIcon(resultNames.get(position), result.path, icon, position < searchResults.size() - 1);
                        } else {
                            position -= searchResults.size() + 1;
                            FaqSearchResult result = faqSearchResults.get(position);
                            searchCell.setTextAndValue(resultNames.get(position + searchResults.size()), result.path, true, position < searchResults.size() - 1);
                        }
                    } else {
                        position--;
                        Object object = recentSearches.get(position);
                        if (object instanceof SearchResult) {
                            SearchResult result = (SearchResult) object;
                            searchCell.setTextAndValue(result.searchTitle, result.path, false, position < recentSearches.size() - 1);
                        } else if (object instanceof FaqSearchResult) {
                            FaqSearchResult result = (FaqSearchResult) object;
                            searchCell.setTextAndValue(result.title, result.path, true, position < recentSearches.size() - 1);
                        }
                    }
                    break;
                }
                case 1: {
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    sectionCell.setText(LocaleController.getString("SettingsFaqSearchTitle", R.string.SettingsFaqSearchTitle));
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(LocaleController.getString("SettingsRecent", R.string.SettingsRecent));
                    break;
                }
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SettingsSearchCell(mContext);
                    break;
                case 1:
                    view = new GraySectionCell(mContext);
                    break;
                case 2:
                default:
                    view = new HeaderCell(mContext, 16);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (searchWas) {
                if (position < searchResults.size()) {
                    return 0;
                } else if (position == searchResults.size()) {
                    return 1;
                }
                return 0;
            } else {
                if (position == 0) {
                    return 2;
                }
                return 0;
            }
        }

        public void addRecent(Object object) {
            int index = recentSearches.indexOf(object);
            if (index >= 0) {
                recentSearches.remove(index);
            }
            recentSearches.add(0, object);
            if (!searchWas) {
                notifyDataSetChanged();
            }
            if (recentSearches.size() > 20) {
                recentSearches.remove(recentSearches.size() - 1);
            }
            LinkedHashSet<String> toSave = new LinkedHashSet<>();
            for (int a = 0, N = recentSearches.size(); a < N; a++) {
                Object o = recentSearches.get(a);
                if (o instanceof SearchResult) {
                    ((SearchResult) o).num = a;
                } else if (o instanceof FaqSearchResult) {
                    ((FaqSearchResult) o).num = a;
                }
                toSave.add(o.toString());
            }
            MessagesController.getGlobalMainSettings().edit().putStringSet("settingsSearchRecent2", toSave).commit();
        }

        public void clearRecent() {
            recentSearches.clear();
            MessagesController.getGlobalMainSettings().edit().remove("settingsSearchRecent2").commit();
            notifyDataSetChanged();
        }

        private int getNum(Object o) {
            if (o instanceof SearchResult) {
                return ((SearchResult) o).num;
            } else if (o instanceof FaqSearchResult) {
                return ((FaqSearchResult) o).num;
            }
            return 0;
        }

        public void search(String text) {
            lastSearchString = text;
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(text)) {
                searchWas = false;
                searchResults.clear();
                faqSearchResults.clear();
                resultNames.clear();
                emptyView.setTopImage(0);
                emptyView.setText(LocaleController.getString("SettingsNoRecent", R.string.SettingsNoRecent));
                notifyDataSetChanged();
                return;
            }
            Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                ArrayList<SearchResult> results = new ArrayList<>();
                ArrayList<FaqSearchResult> faqResults = new ArrayList<>();
                ArrayList<CharSequence> names = new ArrayList<>();
                String[] searchArgs = text.split(" ");
                String[] translitArgs = new String[searchArgs.length];
                for (int a = 0; a < searchArgs.length; a++) {
                    translitArgs[a] = LocaleController.getInstance().getTranslitString(searchArgs[a]);
                    if (translitArgs[a].equals(searchArgs[a])) {
                        translitArgs[a] = null;
                    }
                }

                for (int a = 0; a < searchArray.length; a++) {
                    SearchResult result = searchArray[a];
                    String title = " " + result.searchTitle.toLowerCase();
                    SpannableStringBuilder stringBuilder = null;
                    for (int i = 0; i < searchArgs.length; i++) {
                        if (searchArgs[i].length() != 0) {
                            String searchString = searchArgs[i];
                            int index = title.indexOf(" " + searchString);
                            if (index < 0 && translitArgs[i] != null) {
                                searchString = translitArgs[i];
                                index = title.indexOf(" " + searchString);
                            }
                            if (index >= 0) {
                                if (stringBuilder == null) {
                                    stringBuilder = new SpannableStringBuilder(result.searchTitle);
                                }
                                stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + searchString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else {
                                break;
                            }
                        }
                        if (stringBuilder != null && i == searchArgs.length - 1) {
                            if (result.guid == 502) {
                                int freeAccount = -1;
                                for (int b = 0; b < UserConfig.MAX_ACCOUNT_COUNT; b++) {
                                    if (!UserConfig.getInstance(a).isClientActivated()) {
                                        freeAccount = b;
                                        break;
                                    }
                                }
                                if (freeAccount < 0) {
                                    continue;
                                }
                            }
                            results.add(result);
                            names.add(stringBuilder);
                        }
                    }
                }
                if (faqWebPage != null) {
                    for (int a = 0, N = faqSearchArray.size(); a < N; a++) {
                        FaqSearchResult result = faqSearchArray.get(a);
                        String title = " " + result.title.toLowerCase();
                        SpannableStringBuilder stringBuilder = null;
                        for (int i = 0; i < searchArgs.length; i++) {
                            if (searchArgs[i].length() != 0) {
                                String searchString = searchArgs[i];
                                int index = title.indexOf(" " + searchString);
                                if (index < 0 && translitArgs[i] != null) {
                                    searchString = translitArgs[i];
                                    index = title.indexOf(" " + searchString);
                                }
                                if (index >= 0) {
                                    if (stringBuilder == null) {
                                        stringBuilder = new SpannableStringBuilder(result.title);
                                    }
                                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + searchString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else {
                                    break;
                                }
                            }
                            if (stringBuilder != null && i == searchArgs.length - 1) {
                                faqResults.add(result);
                                names.add(stringBuilder);
                            }
                        }
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    if (!text.equals(lastSearchString)) {
                        return;
                    }
                    if (!searchWas) {
                        emptyView.setTopImage(R.drawable.settings_noresults);
                        emptyView.setText(LocaleController.getString("SettingsNoResults", R.string.SettingsNoResults));
                    }
                    searchWas = true;
                    searchResults = results;
                    faqSearchResults = faqResults;
                    resultNames = names;
                    notifyDataSetChanged();
                });
            }, 300);
        }
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
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(extraHeightView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_title),
                new ThemeDescription(onlineTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_status),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon),

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

                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{avatarDrawable}, null, Theme.key_avatar_backgroundInProfileBlue),

                new ThemeDescription(writeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionBackground),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionPressedBackground),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, 0, new Class[]{SettingsSearchCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{SettingsSearchCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{SettingsSearchCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
        };
    }
}
