/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.WallpaperCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.WallpaperUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class WallpapersListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private int rowCount;
    private int uploadImageRow;
    private int setColorRow;
    private int sectionRow;
    private int wallPaperStartRow;
    private int totalWallpaperRows;

    private int currentType;

    private ArrayList<MediaController.SearchImage> searchResult = new ArrayList<>();
    private HashMap<String, MediaController.SearchImage> searchResultKeys = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> searchResultUrls = new HashMap<>();

    public static boolean disableFeatures = true;

    private boolean searching;
    private boolean bingSearchEndReached = true;
    private String lastSearchString;
    private String nextImagesSearchOffset;
    private int imageReqId;
    private int lastSearchToken;
    private boolean searchingUser;
    private String lastSearchImageString;

    private ColorWallpaper addedColorWallpaper;
    private FileWallpaper addedFileWallpaper;
    private FileWallpaper catsWallpaper;
    private FileWallpaper themeWallpaper;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private LinearLayoutManager layoutManager;

    private WallpaperUpdater updater;

    private int columnsCount = 3;

    private long selectedBackground;
    private int selectedColor;
    private ArrayList<Object> wallPapers = new ArrayList<>();
    private boolean loadingWallpapers;

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

    public final static int TYPE_ALL = 0;
    public final static int TYPE_COLOR = 1;

    public static class ColorWallpaper {

        public long id;
        public int color;

        public ColorWallpaper(long i, int c) {
            id = i;
            color = c;
        }
    }

    public static class FileWallpaper {

        public long id;
        public int resId;
        public int thumbResId;
        public File path;

        public FileWallpaper(long i, File f) {
            id = i;
            path = f;
        }

        public FileWallpaper(long i, String f) {
            id = i;
            path = new File(f);
        }

        public FileWallpaper(long i, int r, int t) {
            id = i;
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
            MessagesStorage.getInstance(currentAccount).getWallpapers();
        } else {
            for (int a = 0; a < defaultColors.length; a++) {
                wallPapers.add(new ColorWallpaper(-(a + 3), defaultColors[a]));
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (currentType == TYPE_ALL) {
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoad);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        }
        updater.cleanup();
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View createView(Context context) {
        updater = new WallpaperUpdater(getParentActivity(), this, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap, boolean gallery) {
                presentFragment(new WallpaperActivity(new FileWallpaper(-1, file), bitmap), gallery);
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
                    finishFragment();
                }
            }
        });

        if (currentType == TYPE_ALL) {
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {

                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (editText.getText().length() == 0) {
                        searchResult.clear();
                        searchResultKeys.clear();
                        lastSearchString = null;
                        bingSearchEndReached = true;
                        searching = false;
                        if (imageReqId != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                            imageReqId = 0;
                        }
                        /*if (type == 0) {
                            emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
                        } else if (type == 1) {
                            emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
                        }*/
                        updateSearchInterface();
                    }
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    processSearch(editText);
                }
            });
            searchItem.setSearchFieldHint(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
            searchItem.setVisibility(View.GONE);
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
                if (wallPaperStartRow == -1) {
                    holder = findViewHolderForAdapterPosition(sectionRow);
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
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        listView.setOnItemClickListener((view, position) -> {
            if (position == uploadImageRow) {
                updater.showAlert(false);
            } else if (position == setColorRow) {
                presentFragment(new WallpapersListActivity(TYPE_COLOR));
            }
        });

        //listView.setOnItemLongClickListener((view, position) -> {
        //    return false;
        //});

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                if (visibleItemCount > 0) {
                    int totalItemCount = layoutManager.getItemCount();
                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !searching) {
                        if (!bingSearchEndReached) {
                            searchImages(lastSearchString, nextImagesSearchOffset, true);
                        }
                    }
                }
            }
        });

        updateRows();
        updateSearchInterface();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        selectedBackground = Theme.getSelectedBackgroundId();
        selectedColor = preferences.getInt("selectedColor", 0);
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

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.wallpapersDidLoad) {
            ArrayList<TLRPC.TL_wallPaper> arrayList = (ArrayList<TLRPC.TL_wallPaper>) args[0];
            wallPapers.clear();
            wallPapers.addAll(arrayList);
            fillWallpapersWithCustom();
            loadWallpapers();
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    private void loadWallpapers() {
        long acc = 0;
        for (int a = 0, N = wallPapers.size(); a < N; a++) {
            Object object = wallPapers.get(a);
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
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (!(response instanceof TLRPC.TL_account_wallPapers)) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                TLRPC.TL_account_wallPapers res = (TLRPC.TL_account_wallPapers) response;
                wallPapers.clear();
                wallPapers.addAll(res.wallpapers);
                fillWallpapersWithCustom();
                MessagesStorage.getInstance(currentAccount).putWallpapers(res.wallpapers, true);
            });
        });
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
            catsWallpaper = new FileWallpaper(Theme.DEFAULT_BACKGROUND_ID, R.drawable.background_hd, R.drawable.catstile);
        } else {
            wallPapers.remove(catsWallpaper);
        }
        wallPapers.add(0, catsWallpaper);
        if (themeWallpaper != null) {
            wallPapers.remove(themeWallpaper);
        }
        if (Theme.hasWallpaperFromTheme()) {
            if (themeWallpaper == null) {
                themeWallpaper = new FileWallpaper(Theme.THEME_BACKGROUND_ID, -2, -2);
            }
            wallPapers.add(0, themeWallpaper);
        } else {
            themeWallpaper = null;
        }
        if (selectedColor != 0) {
            addedColorWallpaper = new ColorWallpaper(selectedBackground, selectedColor);
            wallPapers.add(0, addedColorWallpaper);
        } else if (selectedBackground == -1) {
            addedFileWallpaper = new FileWallpaper(selectedBackground, new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg"));
            wallPapers.add(0, addedFileWallpaper);
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
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void processSearch(EditText editText) {
        if (editText.getText().toString().length() == 0) {
            return;
        }
        searchResult.clear();
        searchResultKeys.clear();
        bingSearchEndReached = true;
        searchImages(editText.getText().toString(), "", true);
        lastSearchString = editText.getText().toString();
        if (lastSearchString.length() == 0) {
            lastSearchString = null;
            //if (type == 0) {
            //    emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
            //} else if (type == 1) {
            //    emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
            //}
        } else {
            //emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        }
        updateSearchInterface();
    }

    private void updateSearchInterface() {
        /*if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searching && searchResult.isEmpty() || loadingWallpapers && lastSearchString == null) {
            //emptyView.showProgress();
        } else {
            //emptyView.showTextView();
        }*/
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

    private void searchImages(final String query, final String offset, boolean searchUser) {
        if (searching) {
            searching = false;
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
        }

        lastSearchImageString = query;
        searching = true;

        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).imageSearchBot);
        if (!(object instanceof TLRPC.User)) {
            if (searchUser) {
                searchBotUser();
            }
            return;
        }
        TLRPC.User user = (TLRPC.User) object;

        TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
        req.query = query == null ? "" : query;
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(user);
        req.offset = offset;
        req.peer = new TLRPC.TL_inputPeerEmpty();

        final int token = ++lastSearchToken;
        imageReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != lastSearchToken) {
                return;
            }
            int addedCount = 0;
            if (response != null) {
                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                nextImagesSearchOffset = res.next_offset;
                boolean added = false;

                for (int a = 0, count = res.results.size(); a < count; a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (!"photo".equals(result.type)) {
                        continue;
                    }
                    if (searchResultKeys.containsKey(result.id)) {
                        continue;
                    }

                    added = true;
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
                    bingImage.localUrl = "";

                    searchResult.add(bingImage);

                    searchResultKeys.put(bingImage.id, bingImage);
                    addedCount++;
                    added = true;
                }
                bingSearchEndReached = !added || nextImagesSearchOffset == null;
            }
            searching = false;
            if (addedCount != 0) {
                listAdapter.notifyItemRangeInserted(searchResult.size(), addedCount);
            } else if (bingSearchEndReached) {
                listAdapter.notifyItemRemoved(searchResult.size() - 1);
            }
            if (searching && searchResult.isEmpty() || loadingWallpapers && lastSearchString == null) {
                //emptyView.showProgress();
            } else {
                //emptyView.showTextView();
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(imageReqId, classGuid);
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
        //int position = layoutManager.findFirstVisibleItemPosition();
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
        //layoutManager.scrollToPosition(position);
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
                    break;
                }
                case 2:
                default: {
                    view = new WallpaperCell(mContext) {
                        @Override
                        protected void onWallpaperClick(Object wallPaper) {
                            WallpaperActivity wallpaperActivity = new WallpaperActivity(wallPaper, null);
                            if (currentType == TYPE_COLOR) {
                                wallpaperActivity.setDelegate(WallpapersListActivity.this::removeSelfFromStack);
                            }
                            presentFragment(wallpaperActivity);
                        }

                        @Override
                        protected void onWallpaperLongClick(Object wallPaper) {
                            //presentFragment(new WallpaperActivity(wallPaper, null));
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
                        if (disableFeatures) {
                            textCell.setTextAndIcon(LocaleController.getString("SelectImage", R.string.SelectImage), R.drawable.profile_photos, true);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("UploadImage", R.string.UploadImage), R.drawable.profile_photos, true);
                        }
                    } else if (position == setColorRow) {
                        textCell.setTextAndIcon(LocaleController.getString("SetColor", R.string.SetColor), R.drawable.menu_palette, false);
                    }
                    break;
                }
                case 1: {
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    Drawable drawable = Theme.getThemedDrawable(mContext, wallPaperStartRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    sectionCell.setBackgroundDrawable(combinedDrawable);
                    break;
                }
                case 2:
                default: {
                    WallpaperCell wallpaperCell = (WallpaperCell) holder.itemView;
                    position = (position - wallPaperStartRow) * columnsCount;
                    wallpaperCell.setParams(columnsCount, position == 0, position / columnsCount == totalWallpaperRows - 1);
                    for (int a = 0; a < columnsCount; a++) {
                        int p = position + a;
                        Object wallPaper = p < wallPapers.size() ? wallPapers.get(p) : null;
                        wallpaperCell.setWallpaper(currentType, a, wallPaper, selectedBackground, null, false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == uploadImageRow || position == setColorRow) {
                return 0;
            } else if (position == sectionRow) {
                return 1;
            } else {
                return 2;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
        };
    }
}
