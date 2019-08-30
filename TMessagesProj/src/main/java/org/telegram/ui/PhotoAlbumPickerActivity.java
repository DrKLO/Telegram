/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.PhotoPickerAlbumsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PickerBottomLayout;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PhotoAlbumPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoAlbumPickerActivityDelegate {
        void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos);

        void startPhotoSelectActivity();
    }

    private HashMap<Object, Object> selectedPhotos = new HashMap<>();
    private ArrayList<Object> selectedPhotosOrder = new ArrayList<>();

    private ArrayList<MediaController.AlbumEntry> albumsSorted = null;
    private HashMap<String, MediaController.SearchImage> recentImagesWebKeys = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> recentImagesGifKeys = new HashMap<>();
    private ArrayList<MediaController.SearchImage> recentWebImages = new ArrayList<>();
    private ArrayList<MediaController.SearchImage> recentGifImages = new ArrayList<>();

    private boolean loading = false;

    private int columnsCount = 2;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private FrameLayout progressView;
    private View shadowView;
    private TextView emptyView;
    private PickerBottomLayout pickerBottomLayout;
    private boolean sendPressed;
    private int selectPhotoType;
    private boolean allowSearchImages = true;
    private boolean allowGifs;
    private boolean allowCaption;
    private ChatActivity chatActivity;
    private int maxSelectedPhotos;
    private boolean allowOrder = true;

    private PhotoAlbumPickerActivityDelegate delegate;

    public PhotoAlbumPickerActivity(int selectPhotoType, boolean allowGifs, boolean allowCaption, ChatActivity chatActivity) {
        super();
        this.chatActivity = chatActivity;
        this.selectPhotoType = selectPhotoType;
        this.allowGifs = allowGifs;
        this.allowCaption = allowCaption;
    }

    @Override
    public boolean onFragmentCreate() {
        if (selectPhotoType != 0 || !allowSearchImages) {
            albumsSorted = MediaController.allPhotoAlbums;
        } else {
            albumsSorted = MediaController.allMediaAlbums;
        }
        loading = albumsSorted == null;
        MediaController.loadGalleryPhotosAlbums(classGuid);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentImagesDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentImagesDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    if (delegate != null) {
                        finishFragment(false);
                        delegate.startPhotoSelectActivity();
                    }
                } else if (id == 2) {
                    openPhotoPicker(null, 0);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (allowSearchImages) {
            menu.addItem(2, R.drawable.ic_ab_search).setContentDescription(LocaleController.getString("Search", R.string.Search));
        }
        menu.addItem(1, R.drawable.ic_ab_other).setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));

        fragmentView = new FrameLayout(context);

        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));

        listView = new RecyclerListView(context);
        listView.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setDrawingCacheEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(Theme.getColor(Theme.key_dialogBackground));

        emptyView = new TextView(context);
        emptyView.setTextColor(0xff808080);
        emptyView.setTextSize(20);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        emptyView.setOnTouchListener((v, event) -> true);

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.GONE);
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        RadialProgressView progressBar = new RadialProgressView(context);
        progressBar.setProgressColor(0xff527da3);
        progressView.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        shadowView = new View(context);
        shadowView.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        frameLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        pickerBottomLayout = new PickerBottomLayout(context);
        frameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        pickerBottomLayout.cancelButton.setOnClickListener(view -> finishFragment());
        pickerBottomLayout.doneButton.setOnClickListener(view -> {
            sendSelectedPhotos(selectedPhotos, selectedPhotosOrder);
            finishFragment();
        });

        if (loading && (albumsSorted == null || albumsSorted != null && albumsSorted.isEmpty())) {
            progressView.setVisibility(View.VISIBLE);
            listView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.GONE);
            listView.setEmptyView(emptyView);
        }
        pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            int guid = (Integer) args[0];
            if (classGuid == guid) {
                if (selectPhotoType != 0 || !allowSearchImages) {
                    albumsSorted = (ArrayList<MediaController.AlbumEntry>) args[2];
                } else {
                    albumsSorted = (ArrayList<MediaController.AlbumEntry>) args[1];
                }
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (listView != null && listView.getEmptyView() == null) {
                    listView.setEmptyView(emptyView);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                loading = false;
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.recentImagesDidLoad) {
            int type = (Integer) args[0];
            if (type == 0) {
                recentWebImages = (ArrayList<MediaController.SearchImage>) args[1];
                recentImagesWebKeys.clear();
                for (MediaController.SearchImage searchImage : recentWebImages) {
                    recentImagesWebKeys.put(searchImage.id, searchImage);
                }
            } else if (type == 1) {
                recentGifImages = (ArrayList<MediaController.SearchImage>) args[1];
                recentImagesGifKeys.clear();
                for (MediaController.SearchImage searchImage : recentGifImages) {
                    recentImagesGifKeys.put(searchImage.id, searchImage);
                }
            }
        }
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        maxSelectedPhotos = value;
        allowOrder = order;
    }

    public void setAllowSearchImages(boolean value) {
        allowSearchImages = value;
    }

    public void setDelegate(PhotoAlbumPickerActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void sendSelectedPhotos(HashMap<Object, Object> photos, ArrayList<Object> order) {
        if (photos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        boolean gifChanged = false;
        boolean webChange = false;

        ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
        for (int a = 0; a < order.size(); a++) {
            Object object = photos.get(order.get(a));
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            media.add(info);
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                if (photoEntry.isVideo) {
                    info.path = photoEntry.path;
                    info.videoEditedInfo = photoEntry.editedInfo;
                } else if (photoEntry.imagePath != null) {
                    info.path = photoEntry.imagePath;
                } else if (photoEntry.path != null) {
                    info.path = photoEntry.path;
                }
                info.isVideo = photoEntry.isVideo;
                info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                info.entities = photoEntry.entities;
                info.masks = !photoEntry.stickers.isEmpty() ? new ArrayList<>(photoEntry.stickers) : null;
                info.ttl = photoEntry.ttl;
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                if (searchImage.imagePath != null) {
                    info.path = searchImage.imagePath;
                } else {
                    info.searchImage = searchImage;
                }

                info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                info.entities = searchImage.entities;
                info.masks = !searchImage.stickers.isEmpty() ? new ArrayList<>(searchImage.stickers) : null;
                info.ttl = searchImage.ttl;

                searchImage.date = (int) (System.currentTimeMillis() / 1000);
                if (searchImage.type == 0) {
                    webChange = true;
                    MediaController.SearchImage recentImage = recentImagesWebKeys.get(searchImage.id);
                    if (recentImage != null) {
                        recentWebImages.remove(recentImage);
                        recentWebImages.add(0, recentImage);
                    } else {
                        recentWebImages.add(0, searchImage);
                    }
                } else if (searchImage.type == 1) {
                    gifChanged = true;
                    MediaController.SearchImage recentImage = recentImagesGifKeys.get(searchImage.id);
                    if (recentImage != null) {
                        recentGifImages.remove(recentImage);
                        recentGifImages.add(0, recentImage);
                    } else {
                        recentGifImages.add(0, searchImage);
                    }
                }
            }
        }

        if (webChange) {
            MessagesStorage.getInstance(currentAccount).putWebRecent(recentWebImages);
        }
        if (gifChanged) {
            MessagesStorage.getInstance(currentAccount).putWebRecent(recentGifImages);
        }

        delegate.didSelectPhotos(media);
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
        columnsCount = 2;
        if (!AndroidUtilities.isTablet() && (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90)) {
            columnsCount = 4;
        }
        listAdapter.notifyDataSetChanged();
    }

    private void openPhotoPicker(MediaController.AlbumEntry albumEntry, int type) {
        ArrayList<MediaController.SearchImage> recentImages = null;
        if (albumEntry == null) {
            if (type == 0) {
                recentImages = recentWebImages;
            } else if (type == 1) {
                recentImages = recentGifImages;
            }
        }
        if (albumEntry != null) {
            PhotoPickerActivity fragment = new PhotoPickerActivity(type, albumEntry, selectedPhotos, selectedPhotosOrder, recentImages, selectPhotoType, allowCaption, chatActivity);
            fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                @Override
                public void selectedPhotosChanged() {
                    if (pickerBottomLayout != null) {
                        pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);
                    }
                }

                @Override
                public void actionButtonPressed(boolean canceled) {
                    removeSelfFromStack();
                    if (!canceled) {
                        sendSelectedPhotos(selectedPhotos, selectedPhotosOrder);
                    }
                }
            });
            fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
            presentFragment(fragment);
        } else {
            final HashMap<Object, Object> photos = new HashMap<>();
            final ArrayList<Object> order = new ArrayList<>();
            if (allowGifs) {
                PhotoPickerSearchActivity fragment = new PhotoPickerSearchActivity(photos, order, recentImages, selectPhotoType, allowCaption, chatActivity);
                fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                    @Override
                    public void selectedPhotosChanged() {

                    }

                    @Override
                    public void actionButtonPressed(boolean canceled) {
                        removeSelfFromStack();
                        if (!canceled) {
                            sendSelectedPhotos(photos, order);
                        }
                    }
                });
                fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                presentFragment(fragment);
            } else {
                PhotoPickerActivity fragment = new PhotoPickerActivity(0, albumEntry, photos, order, recentImages, selectPhotoType, allowCaption, chatActivity);
                fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                    @Override
                    public void selectedPhotosChanged() {

                    }

                    @Override
                    public void actionButtonPressed(boolean canceled) {
                        removeSelfFromStack();
                        if (!canceled) {
                            sendSelectedPhotos(photos, order);
                        }
                    }
                });
                fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                presentFragment(fragment);
            }
        }
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
            return albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PhotoPickerAlbumsCell cell = new PhotoPickerAlbumsCell(mContext);
            cell.setDelegate(albumEntry -> openPhotoPicker(albumEntry, 0));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PhotoPickerAlbumsCell photoPickerAlbumsCell = (PhotoPickerAlbumsCell) holder.itemView;
            photoPickerAlbumsCell.setAlbumsCount(columnsCount);
            for (int a = 0; a < columnsCount; a++) {
                int index = position * columnsCount + a;
                if (index < albumsSorted.size()) {
                    MediaController.AlbumEntry albumEntry = albumsSorted.get(index);
                    photoPickerAlbumsCell.setAlbum(a, albumEntry);
                } else {
                    photoPickerAlbumsCell.setAlbum(a, null);
                }
            }
            photoPickerAlbumsCell.requestLayout();
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_dialogButtonSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogBackground),

                new ThemeDescription(shadowView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine),

                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PickerBottomLayout.class}, new String[]{"cancelButton"}, null, null, null, Theme.key_picker_enabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonTextView"}, null, null, null, Theme.key_picker_enabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonTextView"}, null, null, null, Theme.key_picker_disabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonBadgeTextView"}, null, null, null, Theme.key_picker_badgeText),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonBadgeTextView"}, null, null, null, Theme.key_picker_badge),

                new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{Theme.chat_attachEmptyDrawable}, null, Theme.key_chat_attachEmptyImage),
                new ThemeDescription(listView, 0, new Class[]{View.class}, null, null, null, Theme.key_chat_attachPhotoBackground),
        };
    }
}
