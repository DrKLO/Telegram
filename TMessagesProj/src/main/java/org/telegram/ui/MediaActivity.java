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
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.util.ArrayList;
import java.util.HashMap;

public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private GridView listView;
    private ListAdapter listAdapter;
    private ArrayList<MessageObject> messages = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> messagesDict = new HashMap<Integer, MessageObject>();
    private long dialog_id;
    private int totalCount = 0;
    private int orientation = 0;
    private int itemWidth = 100;
    private boolean loading = false;
    private boolean endReached = false;
    private boolean cacheEndReached = false;
    private int max_id;
    private View progressView;
    private View emptyView;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.mediaDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.Instance.addObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.Instance.addObserver(this, MessagesController.messageReceivedByServer);
        dialog_id = getArguments().getLong("dialog_id", 0);
        loading = true;
        MessagesController.Instance.loadMedia(dialog_id, 0, 50, 0, true, classGuid);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.mediaDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messageReceivedByServer);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.media_layout, container, false);

            emptyView = fragmentView.findViewById(R.id.searchEmptyView);
            listView = (GridView)fragmentView.findViewById(R.id.media_grid);
            progressView = fragmentView.findViewById(R.id.progressLayout);

            listView.setAdapter(listAdapter = new ListAdapter(parentActivity));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    NotificationCenter.Instance.addToMemCache(54, messages);
                    NotificationCenter.Instance.addToMemCache(55, i);
                    Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                    startActivity(intent);
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
                        MessagesController.Instance.loadMedia(dialog_id, 0, 50, max_id, !cacheEndReached, classGuid);
                    }
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                }
            });
            emptyView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
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
        if (id == MessagesController.mediaDidLoaded) {
            long uid = (Long)args[0];
            int guid = (Integer)args[4];
            if (uid == dialog_id && guid == classGuid) {
                loading = false;
                totalCount = (Integer)args[1];
                @SuppressWarnings("uchecked")
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[2];
                boolean added = false;
                for (MessageObject message : arr) {
                    if (!messagesDict.containsKey(message.messageOwner.id)) {
                        if (max_id == 0 || message.messageOwner.id < max_id) {
                            max_id = message.messageOwner.id;
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
        } else if (id == MessagesController.messagesDeleted) {
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
        } else if (id == MessagesController.didReceivedNewMessages) {
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
                    if ((max_id == 0 || obj.messageOwner.id < max_id) && obj.messageOwner.id > 0) {
                        max_id = obj.messageOwner.id;
                    }
                    messagesDict.put(obj.messageOwner.id, obj);
                    messages.add(0, obj);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == MessagesController.messageReceivedByServer) {
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
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        actionBar.setTitle(getStringEntry(R.string.SharedMedia));
        actionBar.setSubtitle(null);

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (parentActivity != null) {
                        WindowManager manager = (WindowManager)parentActivity.getSystemService(Activity.WINDOW_SERVICE);
                        int rotation = manager.getDefaultDisplay().getRotation();

                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            orientation = 1;
                            listView.setNumColumns(6);
                            itemWidth = getResources().getDisplayMetrics().widthPixels / 6 - Utilities.dp(2) * 5;
                            listView.setColumnWidth(itemWidth);
                        } else {
                            orientation = 0;
                            listView.setNumColumns(4);
                            itemWidth = getResources().getDisplayMetrics().widthPixels / 4 - Utilities.dp(2) * 3;
                            listView.setColumnWidth(itemWidth);
                        }
                        listView.setPadding(listView.getPaddingLeft(), Utilities.dp(4), listView.getPaddingRight(), listView.getPaddingBottom());
                        listAdapter.notifyDataSetChanged();
                    }
                    if (listView != null) {
                        listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }

                    return false;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if (Build.VERSION.SDK_INT < 11) {
                    listView.setAdapter(null);
                    listView = null;
                    listAdapter = null;
                }
                finishFragment();
                break;
        }
        return true;
    }

    private class ListAdapter extends BaseAdapter {
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

                if (message.messageOwner.media != null && message.messageOwner.media.photo != null && !message.messageOwner.media.photo.sizes.isEmpty()) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.media.photo.sizes;
                    boolean set = false;
//                    for (TLRPC.PhotoSize size : sizes) {
//                        if (size.type != null && size.type.equals("m")) {
//                            set = true;
//                            imageView.setImage(size.location, null, R.drawable.photo_placeholder);
//                            break;
//                        }
//                    }
                    if (!set) {
                        if (message.imagePreview != null) {
                            imageView.setImageBitmap(message.imagePreview);
                        } else {
                            imageView.setImage(message.messageOwner.media.photo.sizes.get(0).location, null, R.drawable.photo_placeholder_in);
                        }
                    }
                } else {
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
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

                if (message.messageOwner.media.video != null && message.messageOwner.media.video.thumb != null) {
                    int duration = message.messageOwner.media.video.duration;
                    int minutes = duration / 60;
                    int seconds = duration - minutes * 60;
                    textView.setText(String.format("%d:%02d", minutes, seconds));
                    if (message.imagePreview != null) {
                        imageView.setImageBitmap(message.imagePreview);
                    } else {
                        imageView.setImage(message.messageOwner.media.video.thumb.location, null, R.drawable.photo_placeholder_in);
                    }
                    textView.setVisibility(View.VISIBLE);
                } else {
                    textView.setVisibility(View.GONE);
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
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
