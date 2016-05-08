/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.AudioCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PickerBottomLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class AudioSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView progressView;
    private PickerBottomLayout bottomLayout;

    private boolean loadingAudio;

    private ArrayList<MediaController.AudioEntry> audioEntries = new ArrayList<>();
    private HashMap<Long, MediaController.AudioEntry> selectedAudios = new HashMap<>();

    private AudioSelectActivityDelegate delegate;

    private MessageObject playingAudio;

    public interface AudioSelectActivityDelegate {
        void didSelectAudio(ArrayList<MessageObject> audios);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        loadAudio();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        if (playingAudio != null && MediaController.getInstance().isPlayingAudio(playingAudio)) {
            MediaController.getInstance().cleanupPlayer(true, true);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AttachMusic", R.string.AttachMusic));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        progressView = new EmptyTextProgressView(context);
        progressView.setText(LocaleController.getString("NoAudio", R.string.NoAudio));
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ListView listView = new ListView(context);
        listView.setEmptyView(progressView);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        if (Build.VERSION.SDK_INT >= 11) {
            listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                AudioCell audioCell = (AudioCell) view;
                MediaController.AudioEntry audioEntry = audioCell.getAudioEntry();
                if (selectedAudios.containsKey(audioEntry.id)) {
                    selectedAudios.remove(audioEntry.id);
                    audioCell.setChecked(false);
                } else {
                    selectedAudios.put(audioEntry.id, audioEntry);
                    audioCell.setChecked(true);
                }
                updateBottomLayoutCount();
            }
        });

        bottomLayout = new PickerBottomLayout(context, false);
        frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
        bottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        bottomLayout.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (delegate != null) {
                    ArrayList<MessageObject> audios = new ArrayList<>();
                    for (HashMap.Entry<Long, MediaController.AudioEntry> entry : selectedAudios.entrySet()) {
                        audios.add(entry.getValue().messageObject);
                    }
                    delegate.didSelectAudio(audios);
                }
                finishFragment();
            }
        });

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        if (loadingAudio) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }
        updateBottomLayoutCount();
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.audioDidReset) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateBottomLayoutCount() {
        bottomLayout.updateSelectedCount(selectedAudios.size(), true);
    }

    public void setDelegate(AudioSelectActivityDelegate audioSelectActivityDelegate) {
        delegate = audioSelectActivityDelegate;
    }

    private void loadAudio() {
        loadingAudio = true;
        if (progressView != null) {
            progressView.showProgress();
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String[] projection = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ALBUM
                };

                final ArrayList<MediaController.AudioEntry> newAudioEntries = new ArrayList<>();
                Cursor cursor = null;
                try {
                    cursor = ApplicationLoader.applicationContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE);
                    int id = -2000000000;
                    while (cursor.moveToNext()) {
                        MediaController.AudioEntry audioEntry = new MediaController.AudioEntry();
                        audioEntry.id = cursor.getInt(0);
                        audioEntry.author = cursor.getString(1);
                        audioEntry.title = cursor.getString(2);
                        audioEntry.path = cursor.getString(3);
                        audioEntry.duration = (int) (cursor.getLong(4) / 1000);
                        audioEntry.genre = cursor.getString(5);

                        File file = new File(audioEntry.path);

                        TLRPC.TL_message message = new TLRPC.TL_message();
                        message.out = true;
                        message.id = id;
                        message.to_id = new TLRPC.TL_peerUser();
                        message.to_id.user_id = message.from_id = UserConfig.getClientUserId();
                        message.date = (int) (System.currentTimeMillis() / 1000);
                        message.message = "-1";
                        message.attachPath = audioEntry.path;
                        message.media = new TLRPC.TL_messageMediaDocument();
                        message.media.document = new TLRPC.TL_document();
                        message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

                        String ext = FileLoader.getFileExtension(file);

                        message.media.document.id = 0;
                        message.media.document.access_hash = 0;
                        message.media.document.date = message.date;
                        message.media.document.mime_type = "audio/" + (ext.length() > 0 ? ext : "mp3");
                        message.media.document.size = (int) file.length();
                        message.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                        message.media.document.thumb.type = "s";
                        message.media.document.dc_id = 0;

                        TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                        attributeAudio.duration = audioEntry.duration;
                        attributeAudio.title = audioEntry.title;
                        attributeAudio.performer = audioEntry.author;
                        attributeAudio.flags |= 3;
                        message.media.document.attributes.add(attributeAudio);

                        TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                        fileName.file_name = file.getName();
                        message.media.document.attributes.add(fileName);

                        audioEntry.messageObject = new MessageObject(message, null, false);

                        newAudioEntries.add(audioEntry);
                        id--;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        audioEntries = newAudioEntries;
                        progressView.showTextView();
                        listViewAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            return audioEntries.size();
        }

        @Override
        public Object getItem(int i) {
            return audioEntries.get(i);
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
            if (view == null) {
                view = new AudioCell(mContext);
                ((AudioCell) view).setDelegate(new AudioCell.AudioCellDelegate() {
                    @Override
                    public void startedPlayingAudio(MessageObject messageObject) {
                        playingAudio = messageObject;
                    }
                });
            }
            MediaController.AudioEntry audioEntry = audioEntries.get(i);
            ((AudioCell) view).setAudio(audioEntries.get(i), i != audioEntries.size() - 1, selectedAudios.containsKey(audioEntry.id));
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return audioEntries.isEmpty();
        }
    }
}
