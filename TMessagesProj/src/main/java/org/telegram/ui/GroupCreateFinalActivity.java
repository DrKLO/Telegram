/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class GroupCreateFinalActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AvatarUpdater.AvatarUpdaterDelegate {
    private PinnedHeaderListView listView;
    private EditText nameTextView;
    private TLRPC.FileLocation avatar;
    private TLRPC.InputFile uploadedAvatar;
    private ArrayList<Integer> selectedContacts;
    private BackupImageView avatarImage;
    private boolean createAfterUpload;
    private boolean donePressed;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();
    private ProgressDialog progressDialog = null;
    private String nameToSet = null;

    private final static int done_button = 1;

    public GroupCreateFinalActivity(Bundle args) {
        super(args);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.chatDidCreated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.chatDidFailCreate);
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = this;
        selectedContacts = getArguments().getIntegerArrayList("result");
        final ArrayList<Integer> usersToLoad = new ArrayList<Integer>();
        for (Integer uid : selectedContacts) {
            if (MessagesController.getInstance().users.get(uid) == null) {
                usersToLoad.add(uid);
            }
        }
        if (!usersToLoad.isEmpty()) {
            final Semaphore semaphore = new Semaphore(0);
            final ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
            final boolean[] error = new boolean[1];
            MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    users.addAll(MessagesStorage.getInstance().getUsers(usersToLoad, error));
                    semaphore.release();
                }
            });
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (error[0]) {
                return false;
            }
            if (!users.isEmpty()) {
                for (TLRPC.User user : users) {
                    MessagesController.getInstance().users.putIfAbsent(user.id, user);
                }
            } else {
                return false;
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.chatDidCreated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.chatDidFailCreate);
        avatarUpdater.clear();
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            actionBarLayer.setTitle(LocaleController.getString("NewGroup", R.string.NewGroup));

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        if (donePressed) {
                            return;
                        }
                        if (nameTextView.getText().length() == 0) {
                            return;
                        }
                        donePressed = true;

                        if (avatarUpdater.uploadingAvatar != null) {
                            createAfterUpload = true;
                        } else {
                            progressDialog = new ProgressDialog(getParentActivity());
                            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                            progressDialog.setCanceledOnTouchOutside(false);
                            progressDialog.setCancelable(false);

                            final long reqId = MessagesController.getInstance().createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar);

                            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ConnectionsManager.getInstance().cancelRpc(reqId, true);
                                    donePressed = false;
                                    try {
                                        dialog.dismiss();
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            progressDialog.show();
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            View doneItem = menu.addItemResource(done_button, R.layout.group_create_done_layout);

            TextView doneTextView = (TextView)doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.group_create_final_layout, container, false);

            final ImageButton button2 = (ImageButton)fragmentView.findViewById(R.id.settings_change_avatar_button);
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    CharSequence[] items;

                    if (avatar != null) {
                        items = new CharSequence[] {LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                    } else {
                        items = new CharSequence[] {LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley)};
                    }

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                avatarUpdater.openCamera();
                            } else if (i == 1) {
                                avatarUpdater.openGallery();
                            } else if (i == 2) {
                                avatar = null;
                                uploadedAvatar = null;
                                avatarImage.setImage(avatar, "50_50", R.drawable.group_blue);
                            }
                        }
                    });
                    showAlertDialog(builder);
                }
            });

            avatarImage = (BackupImageView)fragmentView.findViewById(R.id.settings_avatar_image);

            nameTextView = (EditText)fragmentView.findViewById(R.id.bubble_input_text);
            nameTextView.setHint(LocaleController.getString("EnterGroupNamePlaceholder", R.string.EnterGroupNamePlaceholder));
            if (nameToSet != null) {
                nameTextView.setText(nameToSet);
                nameToSet = null;
            }
            listView = (PinnedHeaderListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(new ListAdapter(getParentActivity()));
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void didUploadedPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize small, final TLRPC.PhotoSize big) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                uploadedAvatar = file;
                avatar = small.location;
                avatarImage.setImage(avatar, "50_50", R.drawable.group_blue);
                if (createAfterUpload) {
                    FileLog.e("tmessages", "avatar did uploaded");
                    MessagesController.getInstance().createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar);
                }
            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
            args.putString("path", avatarUpdater.currentPicturePath);
        }
        if (nameTextView != null) {
            String text = nameTextView.getText().toString();
            if (text != null && text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (avatarUpdater != null) {
            avatarUpdater.currentPicturePath = args.getString("path");
        }
        String text = args.getString("nameTextView");
        if (text != null) {
            if (nameTextView != null) {
                nameTextView.setText(text);
            } else {
                nameToSet = text;
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == MessagesController.chatDidFailCreate) {
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            donePressed = false;
        } else if (id == MessagesController.chatDidCreated) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (progressDialog != null) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                    Bundle args2 = new Bundle();
                    args2.putInt("chat_id", (Integer)args[0]);
                    presentFragment(new ChatActivity(args2));
                }
            });
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
        }
    }

    private class ListAdapter extends SectionedBaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public long getItemId(int section, int position) {
            return 0;
        }

        @Override
        public int getSectionCount() {
            return 1;
        }

        @Override
        public int getCountForSection(int section) {
            if (selectedContacts == null) {
                return 0;
            }
            return selectedContacts.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            TLRPC.User user = MessagesController.getInstance().users.get(selectedContacts.get(position));

            if (convertView == null) {
                convertView = new ChatOrUserCell(mContext);
                ((ChatOrUserCell)convertView).usePadding = false;
            }

            ((ChatOrUserCell)convertView).setData(user, null, null, null, null);
            ((ChatOrUserCell) convertView).useSeparator = position != selectedContacts.size() - 1;

            return convertView;
        }

        @Override
        public int getItemViewType(int section, int position) {
            return 0;
        }

        @Override
        public int getItemViewTypeCount() {
            return 1;
        }

        @Override
        public int getSectionHeaderViewType(int section) {
            return 0;
        }

        @Override
        public int getSectionHeaderViewTypeCount() {
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                convertView.setBackgroundColor(0xffffffff);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
            if (selectedContacts.size() == 1) {
                textView.setText(selectedContacts.size() + " " + LocaleController.getString("MEMBER", R.string.MEMBER));
            } else {
                textView.setText(selectedContacts.size() + " " + LocaleController.getString("MEMBERS", R.string.MEMBERS));
            }
            return convertView;
        }
    }
}
