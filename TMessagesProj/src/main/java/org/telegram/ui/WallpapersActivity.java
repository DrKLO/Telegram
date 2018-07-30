/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.WallpaperCell;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.WallpaperUpdater;

import java.io.File;
import java.util.ArrayList;

public class WallpapersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private ImageView backgroundImage;
    private FrameLayout progressView;
    private View progressViewBackground;
    private View doneButton;
    private RecyclerListView listView;
    private WallpaperUpdater updater;
    private File wallpaperFile;
    private Drawable themedWallpaper;

    private int selectedBackground;
    private boolean overrideThemeWallpaper;
    private int selectedColor;
    private ArrayList<TLRPC.WallPaper> wallPapers = new ArrayList<>();
    private SparseArray<TLRPC.WallPaper> wallpappersByIds = new SparseArray<>();

    private String loadingFile = null;
    private File loadingFileObject = null;
    private TLRPC.PhotoSize loadingSize = null;

    private final static int done_button = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersDidLoaded);

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        selectedBackground = preferences.getInt("selectedBackground", 1000001);
        overrideThemeWallpaper = preferences.getBoolean("overrideThemeWallpaper", false);
        selectedColor = preferences.getInt("selectedColor", 0);
        MessagesStorage.getInstance(currentAccount).getWallpapers();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        updater.cleanup();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoaded);
    }

    @Override
    public View createView(Context context) {
        themedWallpaper = Theme.getThemedWallpaper(true);
        updater = new WallpaperUpdater(getParentActivity(), new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap) {
                selectedBackground = -1;
                overrideThemeWallpaper = true;
                selectedColor = 0;
                wallpaperFile = file;
                Drawable drawable = backgroundImage.getDrawable();
                backgroundImage.setImageBitmap(bitmap);
            }

            @Override
            public void needOpenColorPicker() {

            }
        });

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChatBackground", R.string.ChatBackground));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    boolean done;
                    TLRPC.WallPaper wallPaper = wallpappersByIds.get(selectedBackground);
                    if (wallPaper != null && wallPaper.id != 1000001 && wallPaper instanceof TLRPC.TL_wallPaper) {
                        int width = AndroidUtilities.displaySize.x;
                        int height = AndroidUtilities.displaySize.y;
                        if (width > height) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(wallPaper.sizes, Math.min(width, height));
                        String fileName = size.location.volume_id + "_" + size.location.local_id + ".jpg";
                        File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                        File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                        try {
                            done = AndroidUtilities.copyFile(f, toFile);
                        } catch (Exception e) {
                            done = false;
                            FileLog.e(e);
                        }
                    } else {
                        if (selectedBackground == -1) {
                            File fromFile = updater.getCurrentWallpaperPath();
                            File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                            try {
                                done = AndroidUtilities.copyFile(fromFile, toFile);
                            } catch (Exception e) {
                                done = false;
                                FileLog.e(e);
                            }
                        } else {
                            done = true;
                        }
                    }

                    if (done) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("selectedBackground", selectedBackground);
                        editor.putInt("selectedColor", selectedColor);
                        editor.putBoolean("overrideThemeWallpaper", Theme.hasWallpaperFromTheme() && overrideThemeWallpaper);
                        editor.commit();
                        Theme.reloadWallpaper();
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        backgroundImage = new ImageView(context);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frameLayout.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        backgroundImage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 52));

        progressViewBackground = new View(context);
        progressViewBackground.setBackgroundResource(R.drawable.system_loader);
        progressView.addView(progressViewBackground, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        RadialProgressView progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(0xffffffff);
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setTag(8);
        listView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        listView.setLayoutManager(layoutManager);
        listView.setDisallowInterceptTouchEvents(true);
        listView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 102, Gravity.LEFT | Gravity.BOTTOM));
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 0) {
                    updater.showAlert(false);
                } else {
                    if (Theme.hasWallpaperFromTheme()) {
                        if (position == 1) {
                            selectedBackground = -2;
                            overrideThemeWallpaper = false;
                            listAdapter.notifyDataSetChanged();
                            processSelectedBackground();
                            return;
                        } else {
                            position -= 2;
                        }
                    } else {
                        position--;
                    }
                    TLRPC.WallPaper wallPaper = wallPapers.get(position);
                    selectedBackground = wallPaper.id;
                    overrideThemeWallpaper = true;
                    listAdapter.notifyDataSetChanged();
                    processSelectedBackground();
                }
            }
        });

        processSelectedBackground();

        return fragmentView;
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

    private void processSelectedBackground() {
        if (Theme.hasWallpaperFromTheme() && !overrideThemeWallpaper) {
            backgroundImage.setImageDrawable(Theme.getThemedWallpaper(false));
        } else {
            TLRPC.WallPaper wallPaper = wallpappersByIds.get(selectedBackground);
            if (selectedBackground != -1 && selectedBackground != 1000001 && wallPaper != null && wallPaper instanceof TLRPC.TL_wallPaper) {
                int width = AndroidUtilities.displaySize.x;
                int height = AndroidUtilities.displaySize.y;
                if (width > height) {
                    int temp = width;
                    width = height;
                    height = temp;
                }
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(wallPaper.sizes, Math.min(width, height));
                if (size == null) {
                    return;
                }
                String fileName = size.location.volume_id + "_" + size.location.local_id + ".jpg";
                File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                if (!f.exists()) {
                    int result[] = AndroidUtilities.calcDrawableColor(backgroundImage.getDrawable());
                    progressViewBackground.getBackground().setColorFilter(new PorterDuffColorFilter(result[0], PorterDuff.Mode.MULTIPLY));
                    loadingFile = fileName;
                    loadingFileObject = f;
                    doneButton.setEnabled(false);
                    progressView.setVisibility(View.VISIBLE);
                    loadingSize = size;
                    selectedColor = 0;
                    FileLoader.getInstance(currentAccount).loadFile(size, null, 1);
                    backgroundImage.setBackgroundColor(0);
                } else {
                    if (loadingFile != null) {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(loadingSize);
                    }
                    loadingFileObject = null;
                    loadingFile = null;
                    loadingSize = null;
                    try {
                        backgroundImage.setImageURI(Uri.fromFile(f));
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    backgroundImage.setBackgroundColor(0);
                    selectedColor = 0;
                    doneButton.setEnabled(true);
                    progressView.setVisibility(View.GONE);
                }
            } else {
                if (loadingFile != null) {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(loadingSize);
                }
                if (selectedBackground == 1000001) {
                    backgroundImage.setImageResource(R.drawable.background_hd);
                    backgroundImage.setBackgroundColor(0);
                    selectedColor = 0;
                } else if (selectedBackground == -1) {
                    File toFile;
                    if (wallpaperFile != null) {
                        toFile = wallpaperFile;
                    } else {
                        toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                    }
                    if (toFile.exists()) {
                        backgroundImage.setImageURI(Uri.fromFile(toFile));
                    } else {
                        selectedBackground = 1000001;
                        overrideThemeWallpaper = true;
                        processSelectedBackground();
                    }
                } else {
                    if (wallPaper == null) {
                        return;
                    }
                    if (wallPaper instanceof TLRPC.TL_wallPaperSolid) {
                        Drawable drawable = backgroundImage.getDrawable();
                        backgroundImage.setImageBitmap(null);
                        selectedColor = 0xff000000 | wallPaper.bg_color;
                        backgroundImage.setBackgroundColor(selectedColor);
                    }
                }
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
                doneButton.setEnabled(true);
                progressView.setVisibility(View.GONE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
                progressView.setVisibility(View.GONE);
                doneButton.setEnabled(false);
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                backgroundImage.setImageURI(Uri.fromFile(loadingFileObject));
                progressView.setVisibility(View.GONE);
                backgroundImage.setBackgroundColor(0);
                doneButton.setEnabled(true);
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
            }
        } else if (id == NotificationCenter.wallpapersDidLoaded) {
            wallPapers = (ArrayList<TLRPC.WallPaper>) args[0];
            wallpappersByIds.clear();
            for (TLRPC.WallPaper wallPaper : wallPapers) {
                wallpappersByIds.put(wallPaper.id, wallPaper);
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (!wallPapers.isEmpty() && backgroundImage != null) {
                processSelectedBackground();
            }
            loadWallpapers();
        }
    }

    private void loadWallpapers() {
        TLRPC.TL_account_getWallPapers req = new TLRPC.TL_account_getWallPapers();
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        wallPapers.clear();
                        TLRPC.Vector res = (TLRPC.Vector) response;
                        wallpappersByIds.clear();
                        for (Object obj : res.objects) {
                            wallPapers.add((TLRPC.WallPaper) obj);
                            wallpappersByIds.put(((TLRPC.WallPaper) obj).id, (TLRPC.WallPaper) obj);
                        }
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        if (backgroundImage != null) {
                            processSelectedBackground();
                        }
                        MessagesStorage.getInstance(currentAccount).putWallpapers(wallPapers);
                    }
                });
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        processSelectedBackground();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            int count = 1 + wallPapers.size();
            if (Theme.hasWallpaperFromTheme()) {
                count++;
            }
            return count;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            WallpaperCell view = new WallpaperCell(mContext);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            WallpaperCell wallpaperCell = (WallpaperCell) viewHolder.itemView;
            if (i == 0) {
                wallpaperCell.setWallpaper(null, !Theme.hasWallpaperFromTheme() || overrideThemeWallpaper ? selectedBackground : -2, null, false);
            } else {
                if (Theme.hasWallpaperFromTheme()) {
                    if (i == 1) {
                        wallpaperCell.setWallpaper(null, overrideThemeWallpaper ? -1 : -2, themedWallpaper, true);
                        return;
                    } else {
                        i -= 2;
                    }
                } else {
                    i--;
                }
                wallpaperCell.setWallpaper(wallPapers.get(i), !Theme.hasWallpaperFromTheme() || overrideThemeWallpaper ? selectedBackground : -2, null, false);
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
