/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.WallpaperCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.WallpaperUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class WallpapersListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private int rowCount;
    private int uploadImageRow;
    private int setColorRow;
    private int sectionRow;
    private int wallPaperStartRow;
    private int totalWallpaperRows;
    private int resetSectionRow;
    private int resetRow;
    private int resetInfoRow;

    private int currentType;

    private Paint colorPaint;
    private Paint colorFramePaint;

    private ColorWallpaper addedColorWallpaper;
    private FileWallpaper addedFileWallpaper;
    private FileWallpaper catsWallpaper;
    private FileWallpaper themeWallpaper;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private SearchAdapter searchAdapter;
    private LinearLayoutManager layoutManager;
    private ActionBarMenuItem searchItem;
    private NumberTextView selectedMessagesCountTextView;
    private EmptyTextProgressView searchEmptyView;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private AlertDialog progressDialog;

    private WallpaperUpdater updater;

    private int columnsCount = 3;

    private String selectedBackgroundSlug = "";
    private int selectedColor;
    private int selectedGradientColor;
    private int selectedGradientRotation;
    private float selectedIntensity;
    private boolean selectedBackgroundMotion;
    private boolean selectedBackgroundBlurred;

    private ArrayList<Object> allWallPapers = new ArrayList<>();
    private HashMap<String, Object> allWallPapersDict = new HashMap<>();
    private ArrayList<Object> wallPapers = new ArrayList<>();
    private ArrayList<Object> patterns = new ArrayList<>();
    private boolean loadingWallpapers;

    private LongSparseArray<Object> selectedWallPapers = new LongSparseArray<>();
    private boolean scrolling;

    private final static int forward = 3;
    private final static int delete = 4;

    private static final int[] defaultColors = new int[]{
            0xffffffff,
            0xffd4dfea,
            0xffb3cde1,
            0xff6ab7ea,
            0xff008dd0,
            0xffd3e2da,
            0xffc8e6c9,
            0xffc5e1a5,
            0xff61b06e,
            0xffcdcfaf,
            0xffa7a895,
            0xff7c6f72,
            0xffffd7ae,
            0xffffb66d,
            0xffde8751,
            0xffefd5e0,
            0xffdba1b9,
            0xffffafaf,
            0xfff16a60,
            0xffe8bcea,
            0xff9592ed,
            0xffd9bc60,
            0xffb17e49,
            0xffd5cef7,
            0xffdf506b,
            0xff8bd2cc,
            0xff3c847e,
            0xff22612c,
            0xff244d7c,
            0xff3d3b85,
            0xff65717d,
            0xff18222d,
            0xff000000
    };

    private static final int[] searchColors = new int[]{
            0xff0076ff,
            0xffff0000,
            0xffff8a00,
            0xffffca00,
            0xff00e432,
            0xff1fa9ab,
            0xff7300aa,
            0xfff9bec5,
            0xff734021,
            0xff000000,
            0xff5c585f,
            0xffffffff
    };

    private static final String[] searchColorsNames = new String[]{
            "Blue",
            "Red",
            "Orange",
            "Yellow",
            "Green",
            "Teal",
            "Purple",
            "Pink",
            "Brown",
            "Black",
            "Gray",
            "White"
    };

    private static final int[] searchColorsNamesR = new int[]{
            R.string.Blue,
            R.string.Red,
            R.string.Orange,
            R.string.Yellow,
            R.string.Green,
            R.string.Teal,
            R.string.Purple,
            R.string.Pink,
            R.string.Brown,
            R.string.Black,
            R.string.Gray,
            R.string.White
    };

    public final static int TYPE_ALL = 0;
    public final static int TYPE_COLOR = 1;

    public static class ColorWallpaper {

        public String slug;
        public int color;
        public int gradientColor;
        public int gradientRotation;
        public long patternId;
        public TLRPC.TL_wallPaper pattern;
        public float intensity;
        public File path;
        public boolean motion;

        public ColorWallpaper(String s, int c, int gc, int r) {
            slug = s;
            color = c | 0xff000000;
            gradientColor = gc == 0 ? 0 : gc | 0xff000000;
            gradientRotation = gradientColor != 0 ? r : 45;
            intensity = 1.0f;
        }

        public ColorWallpaper(String s, int c, int gc, int r, float in, boolean m, File ph) {
            slug = s;
            color = c | 0xff000000;
            gradientColor = gc == 0 ? 0 : gc | 0xff000000;
            gradientRotation = gradientColor != 0 ? r : 45;
            intensity = in;
            path = ph;
            motion = m;
        }

        public String getUrl() {
            String color2 = gradientColor != 0 ? String.format("%02x%02x%02x", (byte) (gradientColor >> 16) & 0xff, (byte) (gradientColor >> 8) & 0xff, (byte) (gradientColor & 0xff)).toLowerCase() : null;
            String color1 = String.format("%02x%02x%02x", (byte) (color >> 16) & 0xff, (byte) (color >> 8) & 0xff, (byte) (color & 0xff)).toLowerCase();
            if (color2 != null) {
                color1 += "-" + color2;
                if (pattern != null) {
                    color1 += "&rotation=" + AndroidUtilities.getWallpaperRotation(gradientRotation, true);
                } else {
                    color1 += "?rotation=" + AndroidUtilities.getWallpaperRotation(gradientRotation, true);
                }
            }
            if (pattern != null) {
                String link = "https://" + MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix + "/bg/" + pattern.slug + "?intensity=" + (int) (intensity * 100) + "&bg_color=" + color1;
                if (motion) {
                    link += "&mode=motion";
                }
                return link;
            } else {
                return "https://" + MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix + "/bg/" + color1;
            }
        }
    }

    public static class FileWallpaper {

        public String slug;
        public int resId;
        public int thumbResId;
        public File path;
        public File originalPath;

        public FileWallpaper(String s, File f, File of) {
            slug = s;
            path = f;
            originalPath = of;
        }

        public FileWallpaper(String s, String f) {
            slug = s;
            path = new File(f);
        }

        public FileWallpaper(String s, int r, int t) {
            slug = s;
            resId = r;
            thumbResId = t;
        }
    }

    public WallpapersListActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        if (currentType == TYPE_ALL) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersDidLoad);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersNeedReload);
            MessagesStorage.getInstance(currentAccount).getWallpapers();
        } else {
            for (int a = 0; a < defaultColors.length; a++) {
                wallPapers.add(new ColorWallpaper(Theme.COLOR_BACKGROUND_SLUG, defaultColors[a], 0, 45));
            }
            if (currentType == TYPE_COLOR && patterns.isEmpty()) {
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersDidLoad);
                MessagesStorage.getInstance(currentAccount).getWallpapers();
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (currentType == TYPE_ALL) {
            searchAdapter.onDestroy();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoad);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersNeedReload);
        } else if (currentType == TYPE_COLOR) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoad);
        }
        updater.cleanup();
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorFramePaint.setStrokeWidth(AndroidUtilities.dp(1));
        colorFramePaint.setStyle(Paint.Style.STROKE);
        colorFramePaint.setColor(0x33000000);

        updater = new WallpaperUpdater(getParentActivity(), this, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap, boolean gallery) {
                presentFragment(new ThemePreviewActivity(new FileWallpaper("", file, file), bitmap), gallery);
            }

            @Override
            public void needOpenColorPicker() {

            }
        });

        hasOwnBackground = true;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == TYPE_ALL) {
            actionBar.setTitle(LocaleController.getString("ChatBackground", R.string.ChatBackground));
        } else if (currentType == TYPE_COLOR) {
            actionBar.setTitle(LocaleController.getString("SelectColorTitle", R.string.SelectColorTitle));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        selectedWallPapers.clear();
                        actionBar.hideActionMode();
                        updateRowsSelection();
                    } else {
                        finishFragment();
                    }
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.formatPluralString("DeleteBackground", selectedWallPapers.size()));
                    builder.setMessage(LocaleController.formatString("DeleteChatBackgroundsAlert", R.string.DeleteChatBackgroundsAlert));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                        progressDialog = new AlertDialog(getParentActivity(), 3);
                        progressDialog.setCanCacnel(false);
                        progressDialog.show();

                        ArrayList<Integer> ids = new ArrayList<>();
                        int[] deleteCount = new int[]{selectedWallPapers.size()};
                        for (int b = 0; b < selectedWallPapers.size(); b++) {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) selectedWallPapers.valueAt(b);

                            TLRPC.TL_account_saveWallPaper req = new TLRPC.TL_account_saveWallPaper();
                            req.settings = new TLRPC.TL_wallPaperSettings();
                            req.unsave = true;

                            TLRPC.TL_inputWallPaper inputWallPaper = new TLRPC.TL_inputWallPaper();
                            inputWallPaper.id = wallPaper.id;
                            inputWallPaper.access_hash = wallPaper.access_hash;
                            req.wallpaper = inputWallPaper;

                            if (wallPaper.slug.equals(selectedBackgroundSlug)) {
                                selectedBackgroundSlug = Theme.hasWallpaperFromTheme() ? Theme.THEME_BACKGROUND_SLUG : Theme.DEFAULT_BACKGROUND_SLUG;
                                Theme.getActiveTheme().setOverrideWallpaper(null);
                                Theme.reloadWallpaper();
                            }

                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                deleteCount[0]--;
                                if (deleteCount[0] == 0) {
                                    loadWallpapers();
                                }
                            }));
                        }
                        selectedWallPapers.clear();
                        actionBar.hideActionMode();
                        actionBar.closeSearchField();
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate((fragment1, dids, message, param) -> {
                        StringBuilder fmessage = new StringBuilder();
                        for (int b = 0; b < selectedWallPapers.size(); b++) {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) selectedWallPapers.valueAt(b);
                            String link = AndroidUtilities.getWallPaperUrl(wallPaper);
                            if (!TextUtils.isEmpty(link)) {
                                if (fmessage.length() > 0) {
                                    fmessage.append('\n');
                                }
                                fmessage.append(link);
                            }
                        }
                        selectedWallPapers.clear();
                        actionBar.hideActionMode();
                        actionBar.closeSearchField();

                        if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                            updateRowsSelection();
                            for (int a = 0; a < dids.size(); a++) {
                                long did = dids.get(a);
                                if (message != null) {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0);
                                }
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessage.toString(), did, null, null, null, true, null, null, null, true, 0);
                            }
                            fragment1.finishFragment();
                        } else {
                            long did = dids.get(0);
                            int lower_part = (int) did;
                            int high_part = (int) (did >> 32);
                            Bundle args1 = new Bundle();
                            args1.putBoolean("scrollToTopOnResume", true);
                            if (lower_part != 0) {
                                if (lower_part > 0) {
                                    args1.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args1.putInt("chat_id", -lower_part);
                                }
                            } else {
                                args1.putInt("enc_id", high_part);
                            }
                            if (lower_part != 0) {
                                if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment1)) {
                                    return;
                                }
                            }
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);

                            ChatActivity chatActivity = new ChatActivity(args1);
                            presentFragment(chatActivity, true);
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessage.toString(), did, null, null, null, true, null, null, null, true, 0);
                        }
                    });
                    presentFragment(fragment);
                }
            }
        });

        if (currentType == TYPE_ALL) {
            ActionBarMenu menu = actionBar.createMenu();
            searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    listView.setAdapter(searchAdapter);
                    listView.invalidate();
                }

                @Override
                public void onSearchCollapse() {
                    listView.setAdapter(listAdapter);
                    listView.invalidate();
                    searchAdapter.processSearch(null, true);
                    searchItem.setSearchFieldCaption(null);
                    onCaptionCleared();
                }

                @Override
                public void onTextChanged(EditText editText) {
                    searchAdapter.processSearch(editText.getText().toString(), false);
                }

                @Override
                public void onCaptionCleared() {
                    searchAdapter.clearColor();
                    searchItem.setSearchFieldHint(LocaleController.getString("SearchBackgrounds", R.string.SearchBackgrounds));
                }
            });
            searchItem.setSearchFieldHint(LocaleController.getString("SearchBackgrounds", R.string.SearchBackgrounds));

            final ActionBarMenu actionMode = actionBar.createActionMode(false, null);
            actionMode.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), true);

            selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
            selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);
            actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("Forward", R.string.Forward)));
            actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete)));

            selectedWallPapers.clear();
        }

        fragmentView = new FrameLayout(context);

        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {

            private Paint paint = new Paint();

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void onDraw(Canvas c) {
                ViewHolder holder;
                if (getAdapter() == listAdapter && resetInfoRow != -1) {
                    holder = findViewHolderForAdapterPosition(resetInfoRow);
                } else {
                    holder = null;
                }
                int bottom;
                int height = getMeasuredHeight();
                if (holder != null) {
                    bottom = holder.itemView.getBottom();
                    if (holder.itemView.getBottom() >= height) {
                        bottom = height;
                    }
                } else {
                    bottom = height;
                }

                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                c.drawRect(0, 0, getMeasuredWidth(), bottom, paint);
                if (bottom != height) {
                    paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    c.drawRect(0, bottom, getMeasuredWidth(), height, paint);
                }
            }
        };
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        searchAdapter = new SearchAdapter(context);
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null || listView.getAdapter() == searchAdapter) {
                return;
            }
            if (position == uploadImageRow) {
                updater.openGallery();
            } else if (position == setColorRow) {
                WallpapersListActivity activity = new WallpapersListActivity(TYPE_COLOR);
                activity.patterns = patterns;
                presentFragment(activity);
            } else if (position == resetRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("ResetChatBackgroundsAlertTitle", R.string.ResetChatBackgroundsAlertTitle));
                builder.setMessage(LocaleController.getString("ResetChatBackgroundsAlert", R.string.ResetChatBackgroundsAlert));
                builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                    if (actionBar.isActionModeShowed()) {
                        selectedWallPapers.clear();
                        actionBar.hideActionMode();
                        updateRowsSelection();
                    }
                    progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.setCanCacnel(false);
                    progressDialog.show();
                    TLRPC.TL_account_resetWallPapers req = new TLRPC.TL_account_resetWallPapers();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(this::loadWallpapers));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getAdapter() == searchAdapter) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    if (visibleItemCount > 0) {
                        int totalItemCount = layoutManager.getItemCount();
                        if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2) {
                            searchAdapter.loadMoreResults();
                        }
                    }
                }
            }
        });

        searchEmptyView = new EmptyTextProgressView(context);
        searchEmptyView.setVisibility(View.GONE);
        searchEmptyView.setShowAtCenter(true);
        searchEmptyView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        listView.setEmptyView(searchEmptyView);
        frameLayout.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateRows();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        Theme.OverrideWallpaperInfo overrideWallpaper = themeInfo.overrideWallpaper;
        if (overrideWallpaper != null) {
            selectedBackgroundSlug = overrideWallpaper.slug;
            selectedColor = overrideWallpaper.color;
            selectedGradientColor = overrideWallpaper.gradientColor;
            selectedGradientRotation = overrideWallpaper.rotation;
            selectedIntensity = overrideWallpaper.intensity;
            selectedBackgroundMotion = overrideWallpaper.isMotion;
            selectedBackgroundBlurred = overrideWallpaper.isBlurred;
        } else {
            selectedBackgroundSlug = Theme.hasWallpaperFromTheme() ? Theme.THEME_BACKGROUND_SLUG :  Theme.DEFAULT_BACKGROUND_SLUG;
            selectedColor = 0;
            selectedGradientColor = 0;
            selectedGradientRotation = 45;
            selectedIntensity = 1.0f;
            selectedBackgroundMotion = false;
            selectedBackgroundBlurred = false;
        }
        fillWallpapersWithCustom();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        updater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        String currentPicturePath = updater.getCurrentPicturePath();
        if (currentPicturePath != null) {
            args.putString("path", currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        updater.setCurrentPicturePath(args.getString("path"));
    }

    private boolean onItemLongClick(WallpaperCell view, Object object, int index) {
        if (actionBar.isActionModeShowed() || getParentActivity() == null || !(object instanceof TLRPC.TL_wallPaper)) {
            return false;
        }
        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        selectedWallPapers.put(wallPaper.id, wallPaper);
        selectedMessagesCountTextView.setNumber(1, false);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int i = 0; i < actionModeViews.size(); i++) {
            View view2 = actionModeViews.get(i);
            AndroidUtilities.clearDrawableAnimation(view2);
            animators.add(ObjectAnimator.ofFloat(view2, View.SCALE_Y, 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(250);
        animatorSet.start();
        scrolling = false;
        actionBar.showActionMode();
        view.setChecked(index, true, true);
        return true;
    }

    private void onItemClick(WallpaperCell view, Object object, int index) {
        if (actionBar.isActionModeShowed()) {
            if (!(object instanceof TLRPC.TL_wallPaper)) {
                return;
            }
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
            if (selectedWallPapers.indexOfKey(wallPaper.id) >= 0) {
                selectedWallPapers.remove(wallPaper.id);
            } else {
                selectedWallPapers.put(wallPaper.id, wallPaper);
            }
            if (selectedWallPapers.size() == 0) {
                actionBar.hideActionMode();
            } else {
                selectedMessagesCountTextView.setNumber(selectedWallPapers.size(), true);
            }
            scrolling = false;
            view.setChecked(index, selectedWallPapers.indexOfKey(wallPaper.id) >= 0, true);
        } else {
            String slug = getWallPaperSlug(object);
            if (object instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                if (wallPaper.pattern) {
                    ColorWallpaper colorWallpaper = new ColorWallpaper(wallPaper.slug, wallPaper.settings.background_color, wallPaper.settings.second_background_color, AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false), wallPaper.settings.intensity / 100.0f, wallPaper.settings.motion, null);
                    colorWallpaper.pattern = wallPaper;
                    object = colorWallpaper;
                }
            }
            ThemePreviewActivity wallpaperActivity = new ThemePreviewActivity(object, null);
            if (currentType == TYPE_COLOR) {
                wallpaperActivity.setDelegate(WallpapersListActivity.this::removeSelfFromStack);
            }
            if (selectedBackgroundSlug.equals(slug)) {
                wallpaperActivity.setInitialModes(selectedBackgroundBlurred, selectedBackgroundMotion);
            }
            wallpaperActivity.setPatterns(patterns);
            presentFragment(wallpaperActivity);
        }
    }

    private String getWallPaperSlug(Object object) {
        if (object instanceof TLRPC.TL_wallPaper) {
            return ((TLRPC.TL_wallPaper) object).slug;
        } else if (object instanceof ColorWallpaper) {
            return ((ColorWallpaper) object).slug;
        } else if (object instanceof FileWallpaper) {
            return ((FileWallpaper) object).slug;
        } else {
            return null;
        }
    }

    private void updateRowsSelection() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof WallpaperCell) {
                WallpaperCell cell = (WallpaperCell) child;
                for (int b = 0; b < 5; b++) {
                    cell.setChecked(b, false, true);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.wallpapersDidLoad) {
            ArrayList<TLRPC.TL_wallPaper> arrayList = (ArrayList<TLRPC.TL_wallPaper>) args[0];
            patterns.clear();
            if (currentType != TYPE_COLOR) {
                wallPapers.clear();
                allWallPapers.clear();
                allWallPapersDict.clear();
                allWallPapers.addAll(arrayList);
            }
            for (int a = 0, N = arrayList.size(); a < N; a++) {
                TLRPC.TL_wallPaper wallPaper = arrayList.get(a);
                if (wallPaper.pattern) {
                    patterns.add(wallPaper);
                }
                if (currentType != TYPE_COLOR && (!wallPaper.pattern || wallPaper.settings != null && wallPaper.settings.background_color != 0)) {
                    allWallPapersDict.put(wallPaper.slug, wallPaper);
                    wallPapers.add(wallPaper);
                }
            }
            selectedBackgroundSlug = Theme.getSelectedBackgroundSlug();
            fillWallpapersWithCustom();
            loadWallpapers();
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (listView != null) {
                listView.invalidateViews();
            }
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
        } else if (id == NotificationCenter.wallpapersNeedReload) {
            MessagesStorage.getInstance(currentAccount).getWallpapers();
        }
    }

    private void loadWallpapers() {
        long acc = 0;
        for (int a = 0, N = allWallPapers.size(); a < N; a++) {
            Object object = allWallPapers.get(a);
            if (!(object instanceof TLRPC.TL_wallPaper)) {
                continue;
            }
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
            int high_id = (int) (wallPaper.id >> 32);
            int lower_id = (int) wallPaper.id;
            acc = ((acc * 20261) + 0x80000000L + high_id) % 0x80000000L;
            acc = ((acc * 20261) + 0x80000000L + lower_id) % 0x80000000L;
        }
        TLRPC.TL_account_getWallPapers req = new TLRPC.TL_account_getWallPapers();
        req.hash = (int) acc;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_account_wallPapers) {
                TLRPC.TL_account_wallPapers res = (TLRPC.TL_account_wallPapers) response;
                patterns.clear();
                if (currentType != TYPE_COLOR) {
                    wallPapers.clear();
                    allWallPapersDict.clear();
                    allWallPapers.clear();
                    allWallPapers.addAll(res.wallpapers);
                }
                for (int a = 0, N = res.wallpapers.size(); a < N; a++) {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) res.wallpapers.get(a);
                    allWallPapersDict.put(wallPaper.slug, wallPaper);
                    if (wallPaper.pattern) {
                        patterns.add(wallPaper);
                    }
                    if (currentType != TYPE_COLOR && (!wallPaper.pattern || wallPaper.settings != null && wallPaper.settings.background_color != 0)) {
                        wallPapers.add(wallPaper);
                    }
                }
                fillWallpapersWithCustom();
                MessagesStorage.getInstance(currentAccount).putWallpapers(res.wallpapers, 1);
            }
            if (progressDialog != null) {
                progressDialog.dismiss();
                listView.smoothScrollToPosition(0);
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void fillWallpapersWithCustom() {
        if (currentType != TYPE_ALL) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (addedColorWallpaper != null) {
            wallPapers.remove(addedColorWallpaper);
            addedColorWallpaper = null;
        }
        if (addedFileWallpaper != null) {
            wallPapers.remove(addedFileWallpaper);
            addedFileWallpaper = null;
        }
        if (catsWallpaper == null) {
            catsWallpaper = new FileWallpaper(Theme.DEFAULT_BACKGROUND_SLUG, R.drawable.background_hd, R.drawable.catstile);
        } else {
            wallPapers.remove(catsWallpaper);
        }
        if (themeWallpaper != null) {
            wallPapers.remove(themeWallpaper);
        }
        Object object = allWallPapersDict.get(selectedBackgroundSlug);
        TLRPC.TL_wallPaper pattern = null;
        String slugFinal;
        if (object instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
            Theme.OverrideWallpaperInfo info = Theme.getActiveTheme().overrideWallpaper;
            if (wallPaper.settings != null &&
                    (selectedColor != wallPaper.settings.background_color ||
                    selectedGradientColor != wallPaper.settings.second_background_color ||
                    (selectedGradientColor != 0 && selectedGradientRotation != AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false)) &&
                    (wallPaper.settings.intensity - selectedIntensity) > 0.001f)) {
                pattern = wallPaper;
                object = null;
                slugFinal = "";
            } else {
                slugFinal = selectedBackgroundSlug;
            }
        } else {
            slugFinal = selectedBackgroundSlug;
        }
        boolean currentThemeDark = Theme.getCurrentTheme().isDark();
        Collections.sort(wallPapers, (o1, o2) -> {
            if (o1 instanceof TLRPC.TL_wallPaper && o2 instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper1 = (TLRPC.TL_wallPaper) o1;
                TLRPC.TL_wallPaper wallPaper2 = (TLRPC.TL_wallPaper) o2;
                if (slugFinal.equals(wallPaper1.slug)) {
                    return -1;
                } else if (slugFinal.equals(wallPaper2.slug)) {
                    return 1;
                }
                int index1 = allWallPapers.indexOf(wallPaper1);
                int index2 = allWallPapers.indexOf(wallPaper2);
                if (wallPaper1.dark && wallPaper2.dark || !wallPaper1.dark && !wallPaper2.dark) {
                    if (index1 > index2) {
                        return 1;
                    } else if (index1 < index2) {
                        return -1;
                    } else {
                        return 0;
                    }
                } else if (wallPaper1.dark && !wallPaper2.dark) {
                    if (currentThemeDark) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    if (currentThemeDark) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            }
            return 0;
        });
        if (Theme.hasWallpaperFromTheme() && !Theme.isThemeWallpaperPublic()) {
            if (themeWallpaper == null) {
                themeWallpaper = new FileWallpaper(Theme.THEME_BACKGROUND_SLUG, -2, -2);
            }
            wallPapers.add(0, themeWallpaper);
        } else {
            themeWallpaper = null;
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (TextUtils.isEmpty(selectedBackgroundSlug) || !Theme.DEFAULT_BACKGROUND_SLUG.equals(selectedBackgroundSlug) && object == null) {
            if (!Theme.COLOR_BACKGROUND_SLUG.equals(selectedBackgroundSlug) && selectedColor != 0) {
                if (themeInfo.overrideWallpaper != null) {
                    addedColorWallpaper = new ColorWallpaper(selectedBackgroundSlug, selectedColor, selectedGradientColor, selectedGradientRotation, selectedIntensity, selectedBackgroundMotion, new File(ApplicationLoader.getFilesDirFixed(), themeInfo.overrideWallpaper.fileName));
                    addedColorWallpaper.pattern = pattern;
                    wallPapers.add(0, addedColorWallpaper);
                }
            } else if (selectedColor != 0) {
                addedColorWallpaper = new ColorWallpaper(selectedBackgroundSlug, selectedColor, selectedGradientColor, selectedGradientRotation);
                wallPapers.add(0, addedColorWallpaper);
            } else {
                if (themeInfo.overrideWallpaper != null) {
                    addedFileWallpaper = new FileWallpaper(selectedBackgroundSlug, new File(ApplicationLoader.getFilesDirFixed(), themeInfo.overrideWallpaper.fileName), new File(ApplicationLoader.getFilesDirFixed(), themeInfo.overrideWallpaper.originalFileName));
                    wallPapers.add(0, addedFileWallpaper);
                }
            }
        } else if (selectedColor != 0 && Theme.COLOR_BACKGROUND_SLUG.equals(selectedBackgroundSlug)) {
            addedColorWallpaper = new ColorWallpaper(selectedBackgroundSlug, selectedColor, selectedGradientColor, selectedGradientRotation);
            wallPapers.add(0, addedColorWallpaper);
        }
        if (Theme.DEFAULT_BACKGROUND_SLUG.equals(selectedBackgroundSlug)) {
            wallPapers.add(0, catsWallpaper);
        } else {
            wallPapers.add(catsWallpaper);
        }
        updateRows();
    }

    private void updateRows() {
        rowCount = 0;
        if (currentType == TYPE_ALL) {
            uploadImageRow = rowCount++;
            setColorRow = rowCount++;
            sectionRow = rowCount++;
        } else {
            uploadImageRow = -1;
            setColorRow = -1;
            sectionRow = -1;
        }
        if (!wallPapers.isEmpty()) {
            totalWallpaperRows = (int) Math.ceil(wallPapers.size() / (float) columnsCount);
            wallPaperStartRow = rowCount;
            rowCount += totalWallpaperRows;
        } else {
            wallPaperStartRow = -1;
        }
        if (currentType == TYPE_ALL) {
            resetSectionRow = rowCount++;
            resetRow = rowCount++;
            resetInfoRow = rowCount++;
        } else {
            resetSectionRow = -1;
            resetRow = -1;
            resetInfoRow = -1;
        }
        if (listAdapter != null) {
            scrolling = true;
            listAdapter.notifyDataSetChanged();
        }
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    fixLayoutInternal();
                    if (listView != null) {
                        listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return true;
                }
            });
        }
    }

    private void fixLayoutInternal() {
        if (getParentActivity() == null) {
            return;
        }
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        if (AndroidUtilities.isTablet()) {
            columnsCount = 3;
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 5;
            } else {
                columnsCount = 3;
            }
        }
        updateRows();
    }

    private class ColorCell extends View {

        private int color;

        public ColorCell(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(50), AndroidUtilities.dp(62));
        }

        public void setColor(int value) {
            color = value;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            colorPaint.setColor(color);
            canvas.drawCircle(AndroidUtilities.dp(25), AndroidUtilities.dp(31), AndroidUtilities.dp(18), colorPaint);
            if (color == Theme.getColor(Theme.key_windowBackgroundWhite)) {
                canvas.drawCircle(AndroidUtilities.dp(25), AndroidUtilities.dp(31), AndroidUtilities.dp(18), colorFramePaint);
            }
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private RecyclerListView innerListView;

        private ArrayList<MediaController.SearchImage> searchResult = new ArrayList<>();
        private HashMap<String, MediaController.SearchImage> searchResultKeys = new HashMap<>();
        private boolean bingSearchEndReached = true;
        private String lastSearchString;
        private String selectedColor;
        private String nextImagesSearchOffset;
        private int imageReqId;
        private int lastSearchToken;
        private boolean searchingUser;
        private String lastSearchImageString;
        private Runnable searchRunnable;

        private class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view = new ColorCell(mContext);
                return new RecyclerListView.Holder(view);
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ColorCell cell = (ColorCell) holder.itemView;
                cell.setColor(searchColors[position]);
            }

            @Override
            public int getItemCount() {
                return searchColors.length;
            }
        }

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public RecyclerListView getInnerListView() {
            return innerListView;
        }

        public void onDestroy() {
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
        }

        public void clearColor() {
            selectedColor = null;
            processSearch(null, true);
        }

        private void processSearch(String text, boolean now) {
            if (text != null && selectedColor != null) {
                text = "#color" + selectedColor + " " + text;
            }
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(text)) {
                searchResult.clear();
                searchResultKeys.clear();
                bingSearchEndReached = true;
                lastSearchString = null;
                if (imageReqId != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                    imageReqId = 0;
                }
                searchEmptyView.showTextView();
            } else {
                searchEmptyView.showProgress();
                final String textFinal = text;
                if (now) {
                    doSearch(textFinal);
                } else {
                    searchRunnable = () -> {
                        doSearch(textFinal);
                        searchRunnable = null;
                    };
                    AndroidUtilities.runOnUIThread(searchRunnable, 500);
                }
            }
            notifyDataSetChanged();
        }

        private void doSearch(String textFinal) {
            searchResult.clear();
            searchResultKeys.clear();
            bingSearchEndReached = true;
            searchImages(textFinal, "", true);
            lastSearchString = textFinal;
            notifyDataSetChanged();
        }

        private void searchBotUser() {
            if (searchingUser) {
                return;
            }
            searchingUser = true;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = MessagesController.getInstance(currentAccount).imageSearchBot;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                        String str = lastSearchImageString;
                        lastSearchImageString = null;
                        searchImages(str, "", false);
                    });
                }
            });
        }

        public void loadMoreResults() {
            if (bingSearchEndReached || imageReqId != 0) {
                return;
            }
            searchImages(lastSearchString, nextImagesSearchOffset, true);
        }

        private void searchImages(final String query, final String offset, boolean searchUser) {
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
            lastSearchImageString = query;

            TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).imageSearchBot);
            if (!(object instanceof TLRPC.User)) {
                if (searchUser) {
                    searchBotUser();
                }
                return;
            }
            TLRPC.User user = (TLRPC.User) object;

            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.query = "#wallpaper " + query;
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(user);
            req.offset = offset;
            req.peer = new TLRPC.TL_inputPeerEmpty();

            final int token = ++lastSearchToken;
            imageReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (token != lastSearchToken) {
                    return;
                }
                imageReqId = 0;
                int oldCount = searchResult.size();
                if (response != null) {
                    TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                    nextImagesSearchOffset = res.next_offset;

                    for (int a = 0, count = res.results.size(); a < count; a++) {
                        TLRPC.BotInlineResult result = res.results.get(a);
                        if (!"photo".equals(result.type)) {
                            continue;
                        }
                        if (searchResultKeys.containsKey(result.id)) {
                            continue;
                        }

                        MediaController.SearchImage bingImage = new MediaController.SearchImage();
                        if (result.photo != null) {
                            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, AndroidUtilities.getPhotoSize());
                            TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, 320);
                            if (size == null) {
                                continue;
                            }
                            bingImage.width = size.w;
                            bingImage.height = size.h;
                            bingImage.photoSize = size;
                            bingImage.photo = result.photo;
                            bingImage.size = size.size;
                            bingImage.thumbPhotoSize = size2;
                        } else {
                            if (result.content == null) {
                                continue;
                            }
                            for (int b = 0; b < result.content.attributes.size(); b++) {
                                TLRPC.DocumentAttribute attribute = result.content.attributes.get(b);
                                if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                    bingImage.width = attribute.w;
                                    bingImage.height = attribute.h;
                                    break;
                                }
                            }
                            if (result.thumb != null) {
                                bingImage.thumbUrl = result.thumb.url;
                            } else {
                                bingImage.thumbUrl = null;
                            }
                            bingImage.imageUrl = result.content.url;
                            bingImage.size = result.content.size;
                        }

                        bingImage.id = result.id;
                        bingImage.type = 0;

                        searchResult.add(bingImage);
                        searchResultKeys.put(bingImage.id, bingImage);
                    }
                    bingSearchEndReached = oldCount == searchResult.size() || nextImagesSearchOffset == null;
                }
                if (oldCount != searchResult.size()) {
                    int prevLastRow = oldCount % columnsCount;
                    int oldRowCount = (int) Math.ceil(oldCount / (float) columnsCount);
                    if (prevLastRow != 0) {
                        notifyItemChanged((int) Math.ceil(oldCount / (float) columnsCount) - 1);
                    }
                    int newRowCount = (int) Math.ceil(searchResult.size() / (float) columnsCount);
                    searchAdapter.notifyItemRangeInserted(oldRowCount, newRowCount - oldRowCount);
                }
                searchEmptyView.showTextView();
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(imageReqId, classGuid);
        }

        @Override
        public int getItemCount() {
            if (TextUtils.isEmpty(lastSearchString)) {
                return 2;
            }
            return (int) Math.ceil(searchResult.size() / (float) columnsCount);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 2;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new WallpaperCell(mContext) {
                        @Override
                        protected void onWallpaperClick(Object wallPaper, int index) {
                            presentFragment(new ThemePreviewActivity(wallPaper, null));
                        }
                    };
                    break;
                case 1:
                    RecyclerListView horizontalListView = new RecyclerListView(mContext) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    horizontalListView.setItemAnimator(null);
                    horizontalListView.setLayoutAnimation(null);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(mContext) {
                        @Override
                        public boolean supportsPredictiveItemAnimations() {
                            return false;
                        }
                    };
                    horizontalListView.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
                    horizontalListView.setClipToPadding(false);
                    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    horizontalListView.setLayoutManager(layoutManager);
                    horizontalListView.setAdapter(new CategoryAdapterRecycler());
                    horizontalListView.setOnItemClickListener((view1, position) -> {
                        String color = LocaleController.getString("BackgroundSearchColor", R.string.BackgroundSearchColor);
                        Spannable spannable = new SpannableString(color + " " + LocaleController.getString(searchColorsNames[position], searchColorsNamesR[position]));
                        spannable.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_actionBarDefaultSubtitle)), color.length(), spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        searchItem.setSearchFieldCaption(spannable);
                        searchItem.setSearchFieldHint(null);
                        searchItem.setSearchFieldText("", true);
                        selectedColor = searchColorsNames[position];
                        processSearch("", true);
                    });
                    view = horizontalListView;
                    innerListView = horizontalListView;
                    break;
                case 2:
                    view = new GraySectionCell(mContext);
                    break;
            }
            if (viewType == 1) {
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(60)));
            } else {
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    WallpaperCell wallpaperCell = (WallpaperCell) holder.itemView;
                    position *= columnsCount;
                    int totalRows = (int) Math.ceil(searchResult.size() / (float) columnsCount);
                    wallpaperCell.setParams(columnsCount, position == 0, position / columnsCount == totalRows - 1);
                    for (int a = 0; a < columnsCount; a++) {
                        int p = position + a;
                        Object wallPaper = p < searchResult.size() ? searchResult.get(p) : null;
                        wallpaperCell.setWallpaper(currentType, a, wallPaper, "", null, false);
                    }
                    break;
                }
                case 2: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    cell.setText(LocaleController.getString("SearchByColor", R.string.SearchByColor));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (TextUtils.isEmpty(lastSearchString)) {
                if (position == 0) {
                    return 2;
                } else {
                    return 1;
                }
            }
            return 0;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new TextCell(mContext);
                    break;
                }
                case 1: {
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, wallPaperStartRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                }
                case 3: {
                    view = new TextInfoPrivacyCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                }
                case 2:
                default: {
                    view = new WallpaperCell(mContext) {
                        @Override
                        protected void onWallpaperClick(Object wallPaper, int index) {
                            onItemClick(this, wallPaper, index);
                        }

                        @Override
                        protected boolean onWallpaperLongClick(Object wallPaper, int index) {
                            return onItemLongClick(this, wallPaper, index);
                        }
                    };
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == uploadImageRow) {
                        textCell.setTextAndIcon(LocaleController.getString("SelectFromGallery", R.string.SelectFromGallery), R.drawable.profile_photos, true);
                    } else if (position == setColorRow) {
                        textCell.setTextAndIcon(LocaleController.getString("SetColor", R.string.SetColor), R.drawable.menu_palette, false);
                    } else if (position == resetRow) {
                        textCell.setText(LocaleController.getString("ResetChatBackgrounds", R.string.ResetChatBackgrounds), false);
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == resetInfoRow) {
                        cell.setText(LocaleController.getString("ResetChatBackgroundsInfo", R.string.ResetChatBackgroundsInfo));
                    }
                    break;
                }
                case 2: {
                    WallpaperCell wallpaperCell = (WallpaperCell) holder.itemView;
                    position = (position - wallPaperStartRow) * columnsCount;
                    wallpaperCell.setParams(columnsCount, position == 0, position / columnsCount == totalWallpaperRows - 1);
                    for (int a = 0; a < columnsCount; a++) {
                        int p = position + a;
                        Object object = p < wallPapers.size() ? wallPapers.get(p) : null;
                        String slugFinal;
                        long id;
                        if (object instanceof TLRPC.TL_wallPaper) {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                            Theme.OverrideWallpaperInfo info = Theme.getActiveTheme().overrideWallpaper;
                            if (selectedBackgroundSlug.equals(wallPaper.slug) && wallPaper.settings != null &&
                                    (selectedColor != wallPaper.settings.background_color ||
                                    selectedGradientColor != wallPaper.settings.second_background_color ||
                                    (selectedGradientColor != 0 && selectedGradientRotation != AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false)) &&
                                    (wallPaper.settings.intensity - selectedIntensity) > 0.001f)) {
                                slugFinal = "";
                            } else {
                                slugFinal = selectedBackgroundSlug;
                            }
                            id = wallPaper.id;
                        } else {
                            if (object instanceof ColorWallpaper) {
                                ColorWallpaper colorWallpaper = (ColorWallpaper) object;
                                if (colorWallpaper.color != selectedColor || colorWallpaper.gradientColor != selectedGradientColor) {
                                    slugFinal = "";
                                } else {
                                    slugFinal = selectedBackgroundSlug;
                                }
                            } else {
                                slugFinal = selectedBackgroundSlug;
                            }
                            id = 0;
                        }
                        wallpaperCell.setWallpaper(currentType, a, object, slugFinal, null, false);
                        if (actionBar.isActionModeShowed()) {
                            wallpaperCell.setChecked(a, selectedWallPapers.indexOfKey(id) >= 0, !scrolling);
                        } else {
                            wallpaperCell.setChecked(a, false, !scrolling);
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == uploadImageRow || position == setColorRow || position == resetRow) {
                return 0;
            } else if (position == sectionRow || position == resetSectionRow) {
                return 1;
            } else if (position == resetInfoRow) {
                return 3;
            } else {
                return 2;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        themeDescriptions.add(new ThemeDescription(searchEmptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(searchEmptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));
        themeDescriptions.add(new ThemeDescription(searchEmptyView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        return themeDescriptions;
    }
}
