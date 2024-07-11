package org.telegram.ui;

import static org.telegram.ui.CacheControlActivity.TYPE_DOCUMENTS;
import static org.telegram.ui.CacheControlActivity.TYPE_MUSIC;
import static org.telegram.ui.CacheControlActivity.TYPE_VIDEOS;
import static org.telegram.ui.CacheControlActivity.UNKNOWN_CHATS_DIALOG_ID;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.NestedSizeNotifierLayout;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Storage.CacheModel;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class CachedMediaLayout extends FrameLayout implements NestedSizeNotifierLayout.ChildLayout {

    private static final int PAGE_TYPE_CHATS = 0;
    private static final int PAGE_TYPE_MEDIA = 1;
    private static final int PAGE_TYPE_DOCUMENTS = 2;
    private static final int PAGE_TYPE_MUSIC = 3;
    private static final int PAGE_TYPE_STORIES = 4;
    private static final int PAGE_TYPE_VOICE = 5;

    private static final int VIEW_TYPE_CHAT = 1;
    private static final int VIEW_TYPE_FILE_ENTRY = 2;
    private final LinearLayout actionModeLayout;
    private final ImageView closeButton;
    private final BackDrawable backDrawable;
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    public final AnimatedTextView selectedMessagesCountTextView;
    private final ActionBarMenuItem clearItem;
    private final ViewPagerFixed.TabsView tabs;
    private final View divider;

    BaseFragment parentFragment;

    ArrayList<Page> pages = new ArrayList<>();
    CacheModel cacheModel;
    ViewPagerFixed viewPagerFixed;

    Page[] allPages = new Page[5];

    BasePlaceProvider placeProvider;
    private int bottomPadding;

    public CachedMediaLayout(@NonNull Context context, BaseFragment parentFragment) {
        super(context);
        this.parentFragment = parentFragment;

        int CacheTabChats;
        allPages[PAGE_TYPE_CHATS] = new Page(LocaleController.getString("FilterChats", R.string.FilterChats), PAGE_TYPE_CHATS, new DialogsAdapter());
        //allPages[PAGE_TYPE_STORIES] = new Page(LocaleController.getString("FilterStories", R.string.FilterStories), PAGE_TYPE_STORIES, new MediaAdapter(true));
        allPages[PAGE_TYPE_MEDIA] = new Page(LocaleController.getString("MediaTab", R.string.MediaTab), PAGE_TYPE_MEDIA, new MediaAdapter(false));
        allPages[PAGE_TYPE_DOCUMENTS] = new Page(LocaleController.getString("SharedFilesTab2", R.string.SharedFilesTab2), PAGE_TYPE_DOCUMENTS, new DocumentsAdapter());
        allPages[PAGE_TYPE_MUSIC] = new Page(LocaleController.getString("Music", R.string.Music), PAGE_TYPE_MUSIC, new MusicAdapter());
        //   allPages[PAGE_TYPE_VOICE] = new Page(LocaleController.getString("Voice", R.string.Voice), PAGE_TYPE_VOICE, new VoiceAdapter());

        for (int i = 0; i < allPages.length; i++) {
            if (allPages[i] == null) {
                continue;
            }
            pages.add(i, allPages[i]);
        }

        viewPagerFixed = new ViewPagerFixed(getContext());
        viewPagerFixed.setAllowDisallowInterceptTouch(false);
        addView(viewPagerFixed, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 48, 0, 0));
        addView(tabs = viewPagerFixed.createTabsView(true, 3), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        divider = new View(getContext());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, 0, 0, 48, 0, 0));
        divider.getLayoutParams().height = 1;
        viewPagerFixed.setAdapter(new ViewPagerFixed.Adapter() {

            private ActionBarPopupWindow popupWindow;

            @Override
            public String getItemTitle(int position) {
                return pages.get(position).title;
            }

            @Override
            public int getItemCount() {
                return pages.size();
            }

            @Override
            public int getItemId(int position) {
                return pages.get(position).type;
            }

            @Override
            public View createView(int viewType) {
                RecyclerListView recyclerListView = new RecyclerListView(context);

                DefaultItemAnimator itemAnimator = (DefaultItemAnimator) recyclerListView.getItemAnimator();
                itemAnimator.setDelayAnimations(false);
                itemAnimator.setSupportsChangeAnimations(false);
                recyclerListView.setClipToPadding(false);
                recyclerListView.setPadding(0, 0, 0, bottomPadding);
                recyclerListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        BaseAdapter adapter = (BaseAdapter) recyclerListView.getAdapter();
                        ItemInner itemInner = adapter.itemInners.get(position);
                        //if (cacheModel.getSelectedFiles() == 0) {
                        if (view instanceof SharedPhotoVideoCell2) {
                            boolean isStory = ((MediaAdapter) adapter).isStories;
                            if (isStory) {
                                TL_stories.StoryItem storyItem = new TL_stories.TL_storyItem();
                                storyItem.dialogId = itemInner.file.dialogId;
                                storyItem.id = Objects.hash(itemInner.file.file.getAbsolutePath());
                                storyItem.attachPath = itemInner.file.file.getAbsolutePath();
                                storyItem.date = -1;
                                parentFragment.getOrCreateStoryViewer().open(context, storyItem, StoriesListPlaceProvider.of(recyclerListView));
                            } else {
                                openPhoto(itemInner, (MediaAdapter) adapter, recyclerListView, (SharedPhotoVideoCell2) view);
                            }
                            return;
                        }

                        //}
                        if (delegate != null) {
                            delegate.onItemSelected(itemInner.entities, itemInner.file, false);
                        }
                    }
                });
                recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
                    BaseAdapter adapter = (BaseAdapter) recyclerListView.getAdapter();
                    ItemInner itemInner = adapter.itemInners.get(position);
                    if (view instanceof CacheCell || view instanceof SharedPhotoVideoCell2) {
                        ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
                        if (view instanceof SharedPhotoVideoCell2) {
                            ActionBarMenuItem.addItem(popupWindowLayout, R.drawable.msg_view_file, LocaleController.getString("CacheOpenFile", R.string.CacheOpenFile), false, null).setOnClickListener(v -> {
                                openPhoto(itemInner, (MediaAdapter) adapter, recyclerListView, (SharedPhotoVideoCell2) view);
                                if (popupWindow != null) {
                                    popupWindow.dismiss();
                                }
                            });
                        } else if (((CacheCell) view).container.getChildAt(0) instanceof SharedAudioCell) {
                            ActionBarMenuItem.addItem(popupWindowLayout, R.drawable.msg_played, LocaleController.getString("PlayFile", R.string.PlayFile), false, null).setOnClickListener(v -> {
                                openItem(itemInner.file, (CacheCell) view);
                                if (popupWindow != null) {
                                    popupWindow.dismiss();
                                }
                            });
                        } else {
                            ActionBarMenuItem.addItem(popupWindowLayout, R.drawable.msg_view_file, LocaleController.getString("CacheOpenFile", R.string.CacheOpenFile), false, null).setOnClickListener(v -> {
                                openItem(itemInner.file, (CacheCell) view);
                                if (popupWindow != null) {
                                    popupWindow.dismiss();
                                }
                            });
                        }
                        if (itemInner.file.dialogId != 0 && itemInner.file.messageId != 0) {
                            ActionBarMenuItem.addItem(popupWindowLayout, R.drawable.msg_viewintopic, LocaleController.getString("ViewInChat", R.string.ViewInChat), false, null).setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                if (itemInner.file.dialogId > 0) {
                                    args.putLong("user_id", itemInner.file.dialogId);
                                } else {
                                    args.putLong("chat_id", -itemInner.file.dialogId);
                                }
                                args.putInt("message_id", itemInner.file.messageId);
                                parentFragment.presentFragment(new ChatActivity(args));
                                delegate.dismiss();
                                if (popupWindow != null) {
                                    popupWindow.dismiss();
                                }
                            });
                        }
                        ActionBarMenuItem.addItem(popupWindowLayout, R.drawable.msg_select,
                                !cacheModel.selectedFiles.contains(itemInner.file) ? LocaleController.getString("Select", R.string.Select) : LocaleController.getString("Deselect", R.string.Deselect),
                                false, null).setOnClickListener(v -> {
                            if (delegate != null) {
                                delegate.onItemSelected(itemInner.entities, itemInner.file, true);
                            }
                            if (popupWindow != null) {
                                popupWindow.dismiss();
                            }
                        });
                        popupWindow = AlertsCreator.createSimplePopup(parentFragment, popupWindowLayout, view, (int) x, (int) y);
                        getRootView().dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
                        return true;
                    } else {
                        if (delegate != null) {
                            delegate.onItemSelected(itemInner.entities, itemInner.file, true);
                        }
                    }
                    return true;
                });
                return recyclerListView;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                RecyclerListView recyclerListView = (RecyclerListView) view;
                recyclerListView.setAdapter(pages.get(position).adapter);
                if (pages.get(position).type == PAGE_TYPE_MEDIA || pages.get(position).type == PAGE_TYPE_STORIES) {
                    recyclerListView.setLayoutManager(new GridLayoutManager(view.getContext(), 3));
                } else {
                    recyclerListView.setLayoutManager(new LinearLayoutManager(view.getContext()));
                }
                recyclerListView.setTag(pages.get(position).type);
            }

            @Override
            public boolean hasStableId() {
                return true;
            }

        });

        actionModeLayout = new LinearLayout(context);
        actionModeLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionModeLayout.setAlpha(0.0f);
        actionModeLayout.setClickable(true);
        addView(actionModeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        AndroidUtilities.updateViewVisibilityAnimated(actionModeLayout, false, 1f, false);

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(backDrawable = new BackDrawable(true));
        backDrawable.setColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        closeButton.setContentDescription(LocaleController.getString("Close", R.string.Close));
        actionModeLayout.addView(closeButton, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(closeButton);
        closeButton.setOnClickListener(v -> {
            delegate.clearSelection();
        });

        selectedMessagesCountTextView = new AnimatedTextView(context, true, true, true);
        selectedMessagesCountTextView.setTextSize(AndroidUtilities.dp(18));
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.bold());
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeLayout.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 18, 0, 0, 0));
        actionModeViews.add(selectedMessagesCountTextView);

        clearItem = new ActionBarMenuItem(context, null, Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), false);
        clearItem.setIcon(R.drawable.msg_clear);
        clearItem.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
        clearItem.setDuplicateParentStateEnabled(false);
        actionModeLayout.addView(clearItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(clearItem);
        clearItem.setOnClickListener(v -> {
            delegate.clear();
        });
    }

    private void openPhoto(ItemInner itemInner, MediaAdapter adapter, RecyclerListView recyclerListView, SharedPhotoVideoCell2 view) {
        MediaAdapter mediaAdapter = (MediaAdapter) adapter;
        PhotoViewer.getInstance().setParentActivity(parentFragment);
        if (placeProvider == null) {
            placeProvider = new BasePlaceProvider();
        }
        placeProvider.setRecyclerListView(recyclerListView);
        int p = adapter.itemInners.indexOf(itemInner);
        if (p >= 0) {
            PhotoViewer.getInstance().openPhotoForSelect(mediaAdapter.getPhotos(), adapter.itemInners.indexOf(itemInner), PhotoViewer.SELECT_TYPE_NO_SELECT, false, placeProvider, null);
        }
    }

    private void openItem(CacheModel.FileInfo fileInfo, CacheCell cacheCell) {
        RecyclerListView recyclerListView = (RecyclerListView) viewPagerFixed.getCurrentView();
        if (cacheCell.type == TYPE_DOCUMENTS) {
            if (!(recyclerListView.getAdapter() instanceof DocumentsAdapter)) {
                return;
            }
            DocumentsAdapter documentsAdapter = (DocumentsAdapter) recyclerListView.getAdapter();
            PhotoViewer.getInstance().setParentActivity(parentFragment);
            if (placeProvider == null) {
                placeProvider = new BasePlaceProvider();
            }
            placeProvider.setRecyclerListView(recyclerListView);

            if (fileIsMedia(fileInfo.file)) {
                ArrayList<Object> photoEntries = new ArrayList<>();
                photoEntries.add(new MediaController.PhotoEntry(0, 0, 0, fileInfo.file.getPath(), 0, fileInfo.type == TYPE_VIDEOS, 0, 0, 0));
                PhotoViewer.getInstance().openPhotoForSelect(photoEntries, 0, PhotoViewer.SELECT_TYPE_NO_SELECT, false, placeProvider, null);
            } else {
                AndroidUtilities.openForView(fileInfo.file, fileInfo.file.getName(), null, parentFragment.getParentActivity(), null, false);
            }
        }
        if (cacheCell.type == TYPE_MUSIC) {
            if (MediaController.getInstance().isPlayingMessage(fileInfo.messageObject)) {
                if (!MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().pauseMessage(fileInfo.messageObject);
                } else {
                    MediaController.getInstance().playMessage(fileInfo.messageObject);
                }
            } else {
                // MediaController.getInstance().setPlaylist(documentsAdapter.createPlaylist(), fileInfo.messageObject, 0);
                MediaController.getInstance().playMessage(fileInfo.messageObject);
            }
        }
        return;
    }

    private SharedPhotoVideoCell2 getCellForIndex(int index) {
        RecyclerListView listView = getListView();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == index && child instanceof SharedPhotoVideoCell2) {
                return (SharedPhotoVideoCell2) child;
            }
        }
        return null;
    }

    public void setCacheModel(CacheModel cacheModel) {
        this.cacheModel = cacheModel;
        update();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //itemSize = ((MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(6 * 2) - AndroidUtilities.dp(5 * 2)) / 3);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
    }

    public void update() {
        ArrayList<Page> oldPages = new ArrayList<>();
        oldPages.addAll(pages);
        pages.clear();
        if (cacheModel != null) {
            for (int i = 0; i < allPages.length; i++) {
                if (allPages[i] == null) {
                    continue;
                }
                if (allPages[i].type == PAGE_TYPE_CHATS && !cacheModel.entities.isEmpty()) {
                    pages.add(allPages[i]);
                } else if (allPages[i].type == PAGE_TYPE_MEDIA && !cacheModel.media.isEmpty()) {
                    pages.add(allPages[i]);
                } else if (allPages[i].type == PAGE_TYPE_DOCUMENTS && !cacheModel.documents.isEmpty()) {
                    pages.add(allPages[i]);
                } else if (allPages[i].type == PAGE_TYPE_MUSIC && !cacheModel.music.isEmpty()) {
                    pages.add(allPages[i]);
                } else if (allPages[i].type == PAGE_TYPE_VOICE && !cacheModel.voice.isEmpty()) {
                    pages.add(allPages[i]);
                } else if (allPages[i].type == PAGE_TYPE_STORIES && !cacheModel.stories.isEmpty()) {
                    pages.add(allPages[i]);
                }
            }
        }
        if (pages.size() == 1 && cacheModel.isDialog) {
            tabs.setVisibility(View.GONE);
            ((MarginLayoutParams) viewPagerFixed.getLayoutParams()).topMargin = 0;
            ((MarginLayoutParams) divider.getLayoutParams()).topMargin = 0;
        }
        boolean rebuildPager = false;
        if (oldPages.size() == pages.size()) {
            for (int i = 0; i < oldPages.size(); i++) {
                if (oldPages.get(i).type != pages.get(i).type) {
                    rebuildPager = true;
                    break;
                }
            }
        } else {
            rebuildPager = true;
        }
        if (rebuildPager) {
            viewPagerFixed.rebuild(true);
        }
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).adapter != null) {
                pages.get(i).adapter.update();
            }
        }
    }

    @Override
    public RecyclerListView getListView() {
        if (viewPagerFixed.getCurrentView() == null) {
            return null;
        }
        return (RecyclerListView) viewPagerFixed.getCurrentView();
    }

    @Override
    public boolean isAttached() {
        return true;
    }

    public void updateVisibleRows() {
        for (int i = 0; i < viewPagerFixed.getViewPages().length; i++) {
            RecyclerListView recyclerListView = (RecyclerListView) viewPagerFixed.getViewPages()[i];
            AndroidUtilities.updateVisibleRows(recyclerListView);
        }
    }

    public void setBottomPadding(int padding) {
        this.bottomPadding = padding;
        for (int i = 0; i < viewPagerFixed.getViewPages().length; i++) {
            RecyclerListView recyclerListView = (RecyclerListView) viewPagerFixed.getViewPages()[i];
            if (recyclerListView != null) {
                recyclerListView.setPadding(0, 0, 0, padding);
            }
        }
    }

    protected void showActionMode(boolean show) {

    }

    protected boolean actionModeIsVisible() {
        return false;
    }

//    public void showActionMode(boolean show) {
//        AndroidUtilities.updateViewVisibilityAnimated(actionModeLayout, show);
//    }

//    public boolean actionModeIsVisible() {
//        return actionModeLayout.getVisibility() == View.VISIBLE;
//    }

    private class Page {
        final public String title;
        final public int type;
        final public BaseAdapter adapter;

        private Page(String title, int type, BaseAdapter adapter) {
            this.title = title;
            this.type = type;
            this.adapter = adapter;
        }
    }

    private abstract class BaseAdapter extends AdapterWithDiffUtils {


        final int type;

        ArrayList<ItemInner> itemInners = new ArrayList<>();

        protected BaseAdapter(int type) {
            this.type = type;
        }

        @Override
        public int getItemViewType(int position) {
            return itemInners.get(position).viewType;
        }

        @Override
        public int getItemCount() {
            return itemInners.size();
        }

        abstract void update();

        public ArrayList<Object> getPhotos() {
            return null;
        }
    }

    private class DialogsAdapter extends BaseAdapter {

        ArrayList<ItemInner> old = new ArrayList<>();

        private DialogsAdapter() {
            super(PAGE_TYPE_CHATS);
        }

        @Override
        void update() {
            old.clear();
            old.addAll(itemInners);
            itemInners.clear();
            if (cacheModel != null) {
                for (int i = 0; i < cacheModel.entities.size(); i++) {
                    itemInners.add(new ItemInner(VIEW_TYPE_CHAT, cacheModel.entities.get(i)));
                }
            }
            setItems(old, itemInners);
//            if (loadingDialogs) {
//                itemInners.add(new ItemInner(VIEW_FLICKER_LOADING_DIALOG, null, null));
//            } else if (dialogsFilesEntities != null && dialogsFilesEntities.size() > 0) {
//
//                itemInners.add(new ItemInner(VIEW_TYPE_INFO, null, null));
//            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case VIEW_TYPE_CHAT:
                    CacheControlActivity.UserCell userCell = new CacheControlActivity.UserCell(getContext(), null);
                    userCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = userCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CHAT:
                    CacheControlActivity.UserCell userCell = (CacheControlActivity.UserCell) holder.itemView;
                    CacheControlActivity.DialogFileEntities dialogFileEntities = itemInners.get(position).entities;
                    TLObject object = parentFragment.getMessagesController().getUserOrChat(dialogFileEntities.dialogId);
                    String title;
                    boolean animated = userCell.dialogFileEntities != null && userCell.dialogFileEntities.dialogId == dialogFileEntities.dialogId;
                    if (dialogFileEntities.dialogId == UNKNOWN_CHATS_DIALOG_ID) {
                        title = LocaleController.getString("CacheOtherChats", R.string.CacheOtherChats);
                        userCell.getImageView().getAvatarDrawable().setAvatarType(AvatarDrawable.AVATAR_TYPE_OTHER_CHATS);
                        userCell.getImageView().setForUserOrChat(null, userCell.getImageView().getAvatarDrawable());
                    } else {
                        title = DialogObject.setDialogPhotoTitle(userCell.getImageView(), object);
                    }
                    userCell.dialogFileEntities = dialogFileEntities;
                    userCell.getImageView().setRoundRadius(AndroidUtilities.dp(object instanceof TLRPC.Chat && ((TLRPC.Chat) object).forum ? 12 : 19));
                    userCell.setTextAndValue(title, AndroidUtilities.formatFileSize(dialogFileEntities.totalSize), position < getItemCount() - 1);
                    userCell.setChecked(cacheModel.isSelected(dialogFileEntities.dialogId), animated);
                    break;
            }
        }


        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private abstract class BaseFilesAdapter extends BaseAdapter {

        ArrayList<ItemInner> oldItems = new ArrayList<>();

        protected BaseFilesAdapter(int type) {
            super(type);
        }

        @Override
        void update() {
            oldItems.clear();
            oldItems.addAll(itemInners);
            itemInners.clear();
            if (cacheModel != null) {
                ArrayList<CacheModel.FileInfo> files = null;
                if (type == PAGE_TYPE_MEDIA) {
                    files = cacheModel.media;
                } else if (type == PAGE_TYPE_DOCUMENTS) {
                    files = cacheModel.documents;
                } else if (type == PAGE_TYPE_MUSIC) {
                    files = cacheModel.music;
                } else if (type == PAGE_TYPE_VOICE) {
                    files = cacheModel.voice;
                } else if (type == PAGE_TYPE_STORIES) {
                    files = cacheModel.stories;
                }
                if (files != null) {
                    for (int i = 0; i < files.size(); i++) {
                        itemInners.add(new ItemInner(VIEW_TYPE_FILE_ENTRY, files.get(i)));
                    }
                }
            }
            setItems(oldItems, itemInners);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class ItemInner extends AdapterWithDiffUtils.Item {

        CacheControlActivity.DialogFileEntities entities;
        CacheModel.FileInfo file;

        public ItemInner(int viewType, CacheControlActivity.DialogFileEntities entities) {
            super(viewType, true);
            this.entities = entities;
        }

        public ItemInner(int viewType, CacheModel.FileInfo file) {
            super(viewType, true);
            this.file = file;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner itemInner = (ItemInner) o;
            if (viewType == itemInner.viewType) {
                if (viewType == VIEW_TYPE_CHAT && entities != null && itemInner.entities != null) {
                    return entities.dialogId == itemInner.entities.dialogId;
                }
                if (viewType == VIEW_TYPE_FILE_ENTRY && file != null && itemInner.file != null) {
                    return Objects.equals(file.file, itemInner.file.file);
                }
                return false;
            }
            return false;
        }
    }

    private class MediaAdapter extends BaseFilesAdapter {

        private SharedPhotoVideoCell2.SharedResources sharedResources;

        boolean isStories;
        private MediaAdapter(boolean stories) {
            super(stories ? PAGE_TYPE_STORIES : PAGE_TYPE_MEDIA);
            this.isStories = stories;
        }

        ArrayList<Object> photoEntries = new ArrayList<>();

        @Override
        void update() {
            super.update();
            photoEntries.clear();
            for (int i = 0; i < itemInners.size(); i++) {
                photoEntries.add(new MediaController.PhotoEntry(0, 0, 0, itemInners.get(i).file.file.getPath(), 0, itemInners.get(i).file.type == TYPE_VIDEOS, 0, 0, 0));
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (sharedResources == null) {
                sharedResources = new SharedPhotoVideoCell2.SharedResources(parent.getContext(), null);
            }
            SharedPhotoVideoCell2 view = new SharedPhotoVideoCell2(parent.getContext(), sharedResources, parentFragment.getCurrentAccount()) {
                @Override
                public void onCheckBoxPressed() {
                    CacheModel.FileInfo file = (CacheModel.FileInfo) getTag();
                    delegate.onItemSelected(null, file, true);
                }
            };
            view.setStyle(SharedPhotoVideoCell2.STYLE_CACHE);
            return new RecyclerListView.Holder(view);
        }

        CombinedDrawable thumb;
        private int storiesPointer;

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (thumb == null) {
                thumb = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_chat_attachPhotoBackground)), Theme.chat_attachEmptyDrawable);
                thumb.setFullsize(true);
            }
            SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) holder.itemView;
            CacheModel.FileInfo file = itemInners.get(position).file;
            boolean animated = file == cell.getTag();
            cell.setTag(file);
            int size = (int) Math.max(100, AndroidUtilities.getRealScreenSize().x / AndroidUtilities.density);
            if (isStories) {
                boolean isVideo = file.file.getAbsolutePath().endsWith(".mp4");
                if (isVideo) {
                    cell.imageReceiver.setImage(ImageLocation.getForPath(file.file.getAbsolutePath()), size + "_" + size + "_pframe", thumb, null, null, 0);
                } else {
                    cell.imageReceiver.setImage(ImageLocation.getForPath(file.file.getAbsolutePath()), size + "_" + size, thumb, null, null, 0);
                }
                cell.storyId = Objects.hash(file.file.getAbsolutePath());
                cell.isStory = true;
                cell.setVideoText(AndroidUtilities.formatFileSize(file.size), true);
            } else if (file.type == TYPE_VIDEOS) {
                cell.imageReceiver.setImage(ImageLocation.getForPath("vthumb://" + 0 + ":" + file.file.getAbsolutePath()), size + "_" + size, thumb, null, null, 0);
                cell.setVideoText(AndroidUtilities.formatFileSize(file.size), true);
            } else {
                cell.imageReceiver.setImage(ImageLocation.getForPath("thumb://" + 0 + ":" + file.file.getAbsolutePath()), size + "_" + size, thumb, null, null, 0);
                cell.setVideoText(AndroidUtilities.formatFileSize(file.size), false);
            }
            cell.setChecked(cacheModel.isSelected(file), animated);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public ArrayList<Object> getPhotos() {
            return photoEntries;
        }
    }

    private class DocumentsAdapter extends BaseFilesAdapter {

        private DocumentsAdapter() {
            super(PAGE_TYPE_DOCUMENTS);
        }

        ArrayList<Object> photoEntries = new ArrayList<>();

        @Override
        void update() {
            super.update();
            photoEntries.clear();
            for (int i = 0; i < itemInners.size(); i++) {
                photoEntries.add(new MediaController.PhotoEntry(0, 0, 0, itemInners.get(i).file.file.getPath(), 0, itemInners.get(i).file.type == TYPE_VIDEOS, 0, 0, 0));
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CacheCell cacheCell = new CacheCell(parent.getContext()) {
                @Override
                public void onCheckBoxPressed() {
                    CacheModel.FileInfo file = (CacheModel.FileInfo) getTag();
                    delegate.onItemSelected(null, file, true);
                }
            };
            cacheCell.type = TYPE_DOCUMENTS;
            SharedDocumentCell cell = new SharedDocumentCell(parent.getContext(), SharedDocumentCell.VIEW_TYPE_CACHE, null);
            cacheCell.container.addView(cell);
            return new RecyclerListView.Holder(cacheCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CacheCell cacheCell = (CacheCell) holder.itemView;
            SharedDocumentCell cell = (SharedDocumentCell) cacheCell.container.getChildAt(0);
            CacheModel.FileInfo file = itemInners.get(position).file;
            boolean animated = file == holder.itemView.getTag();
            boolean divider = position != itemInners.size() - 1;
            holder.itemView.setTag(file);
            long date = file.file.lastModified();

            cell.setTextAndValueAndTypeAndThumb(file.messageType == MessageObject.TYPE_ROUND_VIDEO ? LocaleController.getString("AttachRound", R.string.AttachRound) : file.file.getName(), LocaleController.formatDateAudio(date / 1000, true), Utilities.getExtension(file.file.getName()), null, 0, divider);
            if (!animated) {
                cell.setPhoto(file.file.getPath());
            }
            cell.getImageView().setRoundRadius(file.messageType == MessageObject.TYPE_ROUND_VIDEO ?  AndroidUtilities.dp(20) : AndroidUtilities.dp(4));
            cacheCell.drawDivider = divider;
            cacheCell.sizeTextView.setText(AndroidUtilities.formatFileSize(file.size));
            cacheCell.checkBox.setChecked(cacheModel.isSelected(file), animated);
        }

        @Override
        public ArrayList<Object> getPhotos() {
            return photoEntries;
        }

    }

    private class MusicAdapter extends BaseFilesAdapter {

        private MusicAdapter() {
            super(PAGE_TYPE_MUSIC);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CacheCell cacheCell = new CacheCell(parent.getContext()) {
                @Override
                public void onCheckBoxPressed() {
                    CacheModel.FileInfo file = (CacheModel.FileInfo) getTag();
                    delegate.onItemSelected(null, file, true);
                }
            };
            cacheCell.type = TYPE_MUSIC;
            SharedAudioCell cell = new SharedAudioCell(parent.getContext(), SharedDocumentCell.VIEW_TYPE_DEFAULT, null) {
                @Override
                public void didPressedButton() {
                    openItem((CacheModel.FileInfo) cacheCell.getTag(), cacheCell);
                }
            };
            cell.setCheckForButtonPress(true);
            cacheCell.container.addView(cell);
            return new RecyclerListView.Holder(cacheCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CacheCell cacheCell = (CacheCell) holder.itemView;
            SharedAudioCell cell = (SharedAudioCell) cacheCell.container.getChildAt(0);
            CacheModel.FileInfo fileInfo = itemInners.get(position).file;
            boolean animated = fileInfo == cacheCell.getTag();
            boolean divider = position != itemInners.size() - 1;
            cacheCell.setTag(fileInfo);

            checkMessageObjectForAudio(fileInfo, position);
            cell.setMessageObject(fileInfo.messageObject, divider);
            cell.showName(!fileInfo.metadata.loading, animated);
            cacheCell.drawDivider = divider;
            cacheCell.sizeTextView.setText(AndroidUtilities.formatFileSize(fileInfo.size));
            cacheCell.checkBox.setChecked(cacheModel.isSelected(fileInfo), animated);
        }

        public ArrayList<MessageObject> createPlaylist() {
            ArrayList<MessageObject> playlist = new ArrayList<>();
            for (int i = 0; i < itemInners.size(); i++) {
                checkMessageObjectForAudio(itemInners.get(i).file, i);
                playlist.add(itemInners.get(i).file.messageObject);
            }
            return playlist;
        }
    }

    private void checkMessageObjectForAudio(CacheModel.FileInfo fileInfo, int position) {
        if (fileInfo.messageObject == null) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.out = true;
            message.id = position;
            message.peer_id = new TLRPC.TL_peerUser();
            message.from_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(parentFragment.getCurrentAccount()).getClientUserId();
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.message = "";
            message.attachPath = fileInfo.file.getPath();
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.flags |= 3;
            message.media.document = new TLRPC.TL_document();
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
            message.dialog_id = fileInfo.dialogId;

            String ext = FileLoader.getFileExtension(fileInfo.file);

            message.media.document.id = 0;
            message.media.document.access_hash = 0;
            message.media.document.file_reference = new byte[0];
            message.media.document.date = message.date;
            message.media.document.mime_type = "audio/" + (ext.length() > 0 ? ext : "mp3");
            message.media.document.size = fileInfo.size;
            message.media.document.dc_id = 0;

            TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();

            if (fileInfo.metadata == null) {
                fileInfo.metadata = new CacheModel.FileInfo.FileMetadata();
                fileInfo.metadata.loading = true;
                Utilities.globalQueue.postRunnable(() -> {
                    MediaMetadataRetriever mediaMetadataRetriever = null;
                    String title = "";
                    String author = "";
                    try {
                        mediaMetadataRetriever = new MediaMetadataRetriever();
                        Uri uri = Uri.fromFile(fileInfo.file);
                        mediaMetadataRetriever.setDataSource(getContext(), uri);

                        title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        author = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        try {
                            if (mediaMetadataRetriever != null) {
                                mediaMetadataRetriever.release();
                            }
                        } catch (Throwable e) {

                        }
                    }
                    String finalTitle = title;
                    String finalAuthor = author;
                    AndroidUtilities.runOnUIThread(() -> {
                        fileInfo.metadata.loading = false;
                        attributeAudio.title = fileInfo.metadata.title = finalTitle;
                        attributeAudio.performer = fileInfo.metadata.author = finalAuthor;
                        updateRow(fileInfo, PAGE_TYPE_MUSIC);
                    });
                });

            }
            attributeAudio.flags |= 3;
            message.media.document.attributes.add(attributeAudio);


            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
            fileName.file_name = fileInfo.file.getName();
            message.media.document.attributes.add(fileName);

            fileInfo.messageObject = new MessageObject(parentFragment.getCurrentAccount(), message, false, false);
            fileInfo.messageObject.mediaExists = true;
        }
    }

    private void updateRow(CacheModel.FileInfo fileInfo, int pageType) {
        for (int i = 0; i < viewPagerFixed.getViewPages().length; i++) {
            RecyclerListView recyclerListView = (RecyclerListView) viewPagerFixed.getViewPages()[i];
            if (recyclerListView != null && ((BaseAdapter)recyclerListView.getAdapter()).type == pageType) {
                BaseAdapter adapter = (BaseAdapter) recyclerListView.getAdapter();
                for (int k = 0; k < adapter.itemInners.size(); k++) {
                    if (adapter.itemInners.get(k).file == fileInfo) {
                        adapter.notifyItemChanged(k);
                        break;
                    }
                }
            }
        }
    }

    Delegate delegate;

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public interface Delegate {
        void onItemSelected(CacheControlActivity.DialogFileEntities entities, CacheModel.FileInfo fileInfo, boolean longPress);

        void clear();

        void clearSelection();

        default void dismiss() {

        }
    }

    private class BasePlaceProvider extends PhotoViewer.EmptyPhotoViewerProvider {

        RecyclerListView recyclerListView;

        public void setRecyclerListView(RecyclerListView recyclerListView) {
            this.recyclerListView = recyclerListView;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            SharedPhotoVideoCell2 cell = getCellForIndex(index);
            if (cell != null) {
                int[] coords = new int[2];
                cell.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = recyclerListView;
                object.imageReceiver = cell.imageReceiver;
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getScaleX();
                return object;
            }
            return null;
        }
    }

    private class VoiceAdapter extends BaseFilesAdapter {

        private VoiceAdapter() {
            super(PAGE_TYPE_VOICE);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SharedDocumentCell cell = new SharedDocumentCell(parent.getContext(), SharedDocumentCell.VIEW_TYPE_DEFAULT, null);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SharedDocumentCell cell = (SharedDocumentCell) holder.itemView;
            CacheModel.FileInfo file = itemInners.get(position).file;
            boolean animated = file == cell.getTag();
            cell.setTag(file);
            cell.setTextAndValueAndTypeAndThumb(file.file.getName(), AndroidUtilities.formatFileSize(file.size), Utilities.getExtension(file.file.getName()), null, 0, true);
            cell.setPhoto(file.file.getPath());
            cell.setChecked(cacheModel.isSelected(file), animated);
        }
    }

    private class CacheCell extends FrameLayout {

        CheckBox2 checkBox;
        FrameLayout container;
        TextView sizeTextView;
        boolean drawDivider;
        int type;

        public CacheCell(@NonNull Context context) {
            super(context);
            checkBox = new CheckBox2(context, 21);
            checkBox.setDrawBackgroundAsArc(14);
            checkBox.setColor(Theme.key_checkbox, Theme.key_radioBackground, Theme.key_checkboxCheck);
            View checkBoxClickableView = new View(getContext());
            checkBoxClickableView.setOnClickListener(v -> {
                onCheckBoxPressed();
            });
            container = new FrameLayout(context);

            sizeTextView = new TextView(context);
            sizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            sizeTextView.setGravity(Gravity.RIGHT);
            sizeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));

            if (LocaleController.isRTL) {
                addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0));
                addView(checkBoxClickableView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
                addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 90, 0, 40, 0));
                addView(sizeTextView, LayoutHelper.createFrame(69, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            } else {
                addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));
                addView(checkBoxClickableView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
                addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 48, 0, 90, 0));
                addView(sizeTextView, LayoutHelper.createFrame(69, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 21, 0));
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (drawDivider) {
                if (LocaleController.isRTL) {
                    canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(48), getMeasuredHeight() - 1, Theme.dividerPaint);
                } else {
                    canvas.drawLine(getMeasuredWidth() - AndroidUtilities.dp(90), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
        }

        public void onCheckBoxPressed() {

        }
    }

    public static boolean fileIsMedia(File file) {
        String name = file.getName().toLowerCase();
        return file.getName().endsWith("mp4") || file.getName().endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
    }
}
