/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashMap;

public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

    private GridView listView;
    private ListAdapter listAdapter;
    private ArrayList<MessageObject> messages = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> messagesDict = new HashMap<Integer, MessageObject>();
    private long dialog_id;
    private int totalCount = 0;
    private int itemWidth = 100;
    private boolean loading = false;
    private boolean endReached = false;
    private boolean cacheEndReached = false;
    private int max_id = Integer.MAX_VALUE;
    private View progressView;
    private TextView emptyView;

    public MediaActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByServer);
        dialog_id = getArguments().getLong("dialog_id", 0);
        if (((int)dialog_id) == 0) {
            max_id = Integer.MIN_VALUE;
        }
        loading = true;
        MessagesController.getInstance().loadMedia(dialog_id, 0, 50, 0, true, classGuid);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByServer);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("SharedMedia", R.string.SharedMedia));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        if (Build.VERSION.SDK_INT < 11 && listView != null) {
                            listView.setAdapter(null);
                            listView = null;
                            listAdapter = null;
                        }
                        finishFragment();
                    }
                }
            });

            fragmentView = inflater.inflate(R.layout.media_layout, container, false);

            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            listView = (GridView)fragmentView.findViewById(R.id.media_grid);
            progressView = fragmentView.findViewById(R.id.progressLayout);

            listView.setAdapter(listAdapter = new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < 0 || i >= messages.size()) {
                        return;
                    }
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    PhotoViewer.getInstance().openPhoto(messages, i, MediaActivity.this);
                }
            });
            if (loading && messages.isEmpty()) {
                progressView.setVisibility(View.VISIBLE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !loading && !endReached) {
                        loading = true;
                        MessagesController.getInstance().loadMedia(dialog_id, 0, 50, max_id, !cacheEndReached, classGuid);
                    }
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.mediaDidLoaded) {
            long uid = (Long)args[0];
            int guid = (Integer)args[4];
            if (uid == dialog_id && guid == classGuid) {
                loading = false;
                totalCount = (Integer)args[1];
                @SuppressWarnings("uchecked")
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[2];
                boolean added = false;
                boolean enc = ((int)dialog_id) == 0;
                for (MessageObject message : arr) {
                    if (!messagesDict.containsKey(message.messageOwner.id)) {
                        if (!enc) {
                            if (message.messageOwner.id > 0) {
                                max_id = Math.min(message.messageOwner.id, max_id);
                            }
                        } else {
                            max_id = Math.max(message.messageOwner.id, max_id);
                        }
                        messagesDict.put(message.messageOwner.id, message);
                        messages.add(message);
                        added = true;
                    }
                }
                if (!added) {
                    endReached = true;
                }
                cacheEndReached = !(Boolean)args[3];
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (listView != null) {
                    if (listView.getEmptyView() == null) {
                        listView.setEmptyView(emptyView);
                    }
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            @SuppressWarnings("unchecked")
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                MessageObject obj = messagesDict.get(ids);
                if (obj != null) {
                    messages.remove(obj);
                    messagesDict.remove(ids);
                    totalCount--;
                    updated = true;
                }
            }
            if (updated && listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long uid = (Long)args[0];
            if (uid == dialog_id) {
                boolean markAsRead = false;
                @SuppressWarnings("unchecked")
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[1];

                for (MessageObject obj : arr) {
                    if (obj.messageOwner.media == null || !(obj.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) && !(obj.messageOwner.media instanceof TLRPC.TL_messageMediaVideo)) {
                        continue;
                    }
                    if (messagesDict.containsKey(obj.messageOwner.id)) {
                        continue;
                    }
                    boolean enc = ((int)dialog_id) == 0;
                    if (!enc) {
                        if (obj.messageOwner.id > 0) {
                            max_id = Math.min(obj.messageOwner.id, max_id);
                        }
                    } else {
                        max_id = Math.max(obj.messageOwner.id, max_id);
                    }
                    messagesDict.put(obj.messageOwner.id, obj);
                    messages.add(0, obj);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                messagesDict.remove(msgId);
                messagesDict.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
            }
        }
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

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (messageObject == null || listView == null) {
            return null;
        }
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
            if (imageView != null) {
                int num = (Integer)imageView.getTag();
                if (num < 0 || num >= messages.size()) {
                    continue;
                }
                MessageObject message = messages.get(num);
                if (message != null && message.messageOwner.id == messageObject.messageOwner.id) {
                    int coords[] = new int[2];
                    imageView.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                    object.parentView = listView;
                    object.imageReceiver = imageView.imageReceiver;
                    object.thumb = object.imageReceiver.getBitmap();
                    return object;
                }
            }
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() { }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                    int rotation = manager.getDefaultDisplay().getRotation();

                    if (AndroidUtilities.isTablet()) {
                        listView.setNumColumns(4);
                        itemWidth = AndroidUtilities.dp(490) / 4 - AndroidUtilities.dp(2) * 3;
                        listView.setColumnWidth(itemWidth);
                    } else {
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            listView.setNumColumns(6);
                            itemWidth = AndroidUtilities.displaySize.x / 6 - AndroidUtilities.dp(2) * 5;
                            listView.setColumnWidth(itemWidth);
                        } else {
                            listView.setNumColumns(4);
                            itemWidth = AndroidUtilities.displaySize.x / 4 - AndroidUtilities.dp(2) * 3;
                            listView.setColumnWidth(itemWidth);
                        }
                    }
                    listView.setPadding(listView.getPaddingLeft(), AndroidUtilities.dp(4), listView.getPaddingRight(), listView.getPaddingBottom());
                    listAdapter.notifyDataSetChanged();

                    if (listView != null) {
                        listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }

                    return false;
                }
            });
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != messages.size();
        }

        @Override
        public int getCount() {
            return messages.size() + (messages.isEmpty() || endReached ? 0 : 1);
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                MessageObject message = messages.get(i);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_photo_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                imageView.setTag(i);

                if (message.messageOwner.media != null && message.messageOwner.media.photo != null && !message.messageOwner.media.photo.sizes.isEmpty()) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.media.photo.sizes;
                    if (message.imagePreview != null) {
                        imageView.setImageBitmap(message.imagePreview);
                    } else {
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes, 80);
                        imageView.setImage(photoSize.location, null, mContext.getResources().getDrawable(R.drawable.photo_placeholder_in));
                    }
                } else {
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
                imageView.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
            } else if (type == 1) {
                MessageObject message = messages.get(i);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_video_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                TextView textView = (TextView)view.findViewById(R.id.chat_video_time);
                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                imageView.setTag(i);

                if (message.messageOwner.media.video != null && message.messageOwner.media.video.thumb != null) {
                    int duration = message.messageOwner.media.video.duration;
                    int minutes = duration / 60;
                    int seconds = duration - minutes * 60;
                    textView.setText(String.format("%d:%02d", minutes, seconds));
                    if (message.imagePreview != null) {
                        imageView.setImageBitmap(message.imagePreview);
                    } else {
                        imageView.setImage(message.messageOwner.media.video.thumb.location, null, mContext.getResources().getDrawable(R.drawable.photo_placeholder_in));
                    }
                    textView.setVisibility(View.VISIBLE);
                } else {
                    textView.setVisibility(View.GONE);
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
                imageView.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_loading_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == messages.size()) {
                return 2;
            }
            MessageObject message = messages.get(i);
            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return messages.isEmpty();
        }
    }
}
