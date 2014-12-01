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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.FrameLayoutFixed;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class GroupCreateFinalActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AvatarUpdater.AvatarUpdaterDelegate {

    private ListAdapter listAdapter;
    private ListView listView;
    private EditText nameTextView;
    private TLRPC.FileLocation avatar;
    private TLRPC.InputFile uploadedAvatar;
    private ArrayList<Integer> selectedContacts;
    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean createAfterUpload;
    private boolean donePressed;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();
    private ProgressDialog progressDialog = null;
    private String nameToSet = null;
    private boolean isBroadcast = false;

    private final static int done_button = 1;

    public GroupCreateFinalActivity(Bundle args) {
        super(args);
        isBroadcast = args.getBoolean("broadcast", false);
        avatarDrawable = new AvatarDrawable();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidFailCreate);
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = this;
        selectedContacts = getArguments().getIntegerArrayList("result");
        final ArrayList<Integer> usersToLoad = new ArrayList<Integer>();
        for (Integer uid : selectedContacts) {
            if (MessagesController.getInstance().getUser(uid) == null) {
                usersToLoad.add(uid);
            }
        }
        if (!usersToLoad.isEmpty()) {
            final Semaphore semaphore = new Semaphore(0);
            final ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    users.addAll(MessagesStorage.getInstance().getUsers(usersToLoad));
                    semaphore.release();
                }
            });
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (usersToLoad.size() != users.size()) {
                return false;
            }
            if (!users.isEmpty()) {
                for (TLRPC.User user : users) {
                    MessagesController.getInstance().putUser(user, true);
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidFailCreate);
        avatarUpdater.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            if (isBroadcast) {
                actionBar.setTitle(LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
            } else {
                actionBar.setTitle(LocaleController.getString("NewGroup", R.string.NewGroup));
            }

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
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

                        if (isBroadcast) {
                            MessagesController.getInstance().createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar, isBroadcast);
                        } else {
                            if (avatarUpdater.uploadingAvatar != null) {
                                createAfterUpload = true;
                            } else {
                                progressDialog = new ProgressDialog(getParentActivity());
                                progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                                progressDialog.setCanceledOnTouchOutside(false);
                                progressDialog.setCancelable(false);

                                final long reqId = MessagesController.getInstance().createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar, isBroadcast);

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
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            fragmentView = new LinearLayout(getParentActivity());
            LinearLayout linearLayout = (LinearLayout) fragmentView;
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            FrameLayout frameLayout = new FrameLayoutFixed(getParentActivity());
            linearLayout.addView(frameLayout);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            frameLayout.setLayoutParams(layoutParams);

            avatarImage = new BackupImageView(getParentActivity());
            avatarImage.imageReceiver.setRoundRadius(AndroidUtilities.dp(32));
            avatarDrawable.setInfo(5, null, null, isBroadcast);
            avatarImage.setImageDrawable(avatarDrawable);
            frameLayout.addView(avatarImage);
            FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
            layoutParams1.width = AndroidUtilities.dp(64);
            layoutParams1.height = AndroidUtilities.dp(64);
            layoutParams1.topMargin = AndroidUtilities.dp(12);
            layoutParams1.bottomMargin = AndroidUtilities.dp(12);
            layoutParams1.leftMargin = LocaleController.isRTL ? 0 : AndroidUtilities.dp(16);
            layoutParams1.rightMargin = LocaleController.isRTL ? AndroidUtilities.dp(16) : 0;
            layoutParams1.gravity = Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            avatarImage.setLayoutParams(layoutParams1);
            if (!isBroadcast) {
                avatarDrawable.setDrawPhoto(true);
                avatarImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                        CharSequence[] items;

                        if (avatar != null) {
                            items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                        } else {
                            items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley)};
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
                                    avatarImage.setImage(avatar, "50_50", avatarDrawable);
                                }
                            }
                        });
                        showAlertDialog(builder);
                    }
                });
            }

            nameTextView = new EditText(getParentActivity());
            nameTextView.setHint(isBroadcast ? LocaleController.getString("EnterListName", R.string.EnterListName) : LocaleController.getString("EnterGroupNamePlaceholder", R.string.EnterGroupNamePlaceholder));
            if (nameToSet != null) {
                nameTextView.setText(nameToSet);
                nameToSet = null;
            }
            nameTextView.setMaxLines(4);
            nameTextView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setHintTextColor(0xff979797);
            nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            nameTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            nameTextView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            AndroidUtilities.clearCursorDrawable(nameTextView);
            nameTextView.setTextColor(0xff212121);
            frameLayout.addView(nameTextView);
            layoutParams1 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
            layoutParams1.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.height =  FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.leftMargin = LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(96);
            layoutParams1.rightMargin = LocaleController.isRTL ? AndroidUtilities.dp(96) : AndroidUtilities.dp(16);
            layoutParams1.gravity = Gravity.CENTER_VERTICAL;
            nameTextView.setLayoutParams(layoutParams1);
            if (!isBroadcast) {
                nameTextView.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        avatarDrawable.setInfo(5, nameTextView.length() > 0 ? nameTextView.getText().toString() : null, null, isBroadcast);
                        avatarImage.invalidate();
                    }
                });
            }

            GreySectionCell sectionCell = new GreySectionCell(getParentActivity());
            sectionCell.setText(LocaleController.formatPluralString("Members", selectedContacts.size()));
            linearLayout.addView(sectionCell);

            listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            listView.setAdapter(listAdapter = new ListAdapter(getParentActivity()));
            linearLayout.addView(listView);
            layoutParams = (LinearLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);
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
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                uploadedAvatar = file;
                avatar = small.location;
                avatarImage.setImage(avatar, "50_50", avatarDrawable);
                if (createAfterUpload) {
                    FileLog.e("tmessages", "avatar did uploaded");
                    MessagesController.getInstance().createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar, false);
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
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.chatDidFailCreate) {
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            donePressed = false;
        } else if (id == NotificationCenter.chatDidCreated) {
            AndroidUtilities.runOnUIThread(new Runnable() {
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
                    presentFragment(new ChatActivity(args2), true);
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
            if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            }
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
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = new UserCell(mContext, 1);
            }

            TLRPC.User user = MessagesController.getInstance().getUser(selectedContacts.get(i));
            ((UserCell) view).setData(user, null, null, 0);
            return view;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public int getCount() {
            return selectedContacts.size();
        }
    }
}
