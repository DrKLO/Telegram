/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;

public class GroupCreateFinalActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AvatarUpdater.AvatarUpdaterDelegate {
    private PinnedHeaderListView listView;
    private TextView nameTextView;
    private TLRPC.FileLocation avatar;
    private TLRPC.TL_inputFile uploadedAvatar;
    private ArrayList<Integer> selectedContacts;
    private BackupImageView avatarImage;
    private boolean createAfterUpload;
    private View topView;
    private boolean donePressed;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.chatDidCreated);
        NotificationCenter.Instance.addObserver(this, MessagesController.chatDidFailCreate);
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = this;
        selectedContacts = (ArrayList<Integer>)NotificationCenter.Instance.getFromMemCache(2);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.chatDidCreated);
        NotificationCenter.Instance.removeObserver(this, MessagesController.chatDidFailCreate);
        avatarUpdater.clear();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.group_create_final_layout, container, false);

            final ImageButton button2 = (ImageButton)fragmentView.findViewById(R.id.settings_change_avatar_button);
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

                    CharSequence[] items;

                    if (avatar != null) {
                        items = new CharSequence[] {getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley), getStringEntry(R.string.DeletePhoto)};
                    } else {
                        items = new CharSequence[] {getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley)};
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
                                avatarImage.setImage(avatar, "50_50", R.drawable.group_placeholder_blue);
                            }
                        }
                    });
                    builder.show().setCanceledOnTouchOutside(true);
                }
            });

            avatarImage = (BackupImageView)fragmentView.findViewById(R.id.settings_avatar_image);
            topView = fragmentView.findViewById(R.id.top_layout);

            nameTextView = (EditText)fragmentView.findViewById(R.id.bubble_input_text);
            listView = (PinnedHeaderListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(new ListAdapter(parentActivity));
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
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
                    WindowManager manager = (WindowManager)parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    Display display = manager.getDefaultDisplay();
                    int rotation = display.getRotation();
                    int height;
                    int currentActionBarHeight = parentActivity.getSupportActionBar().getHeight();
                    float density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
                    if (currentActionBarHeight != 48 * density && currentActionBarHeight != 40 * density) {
                        height = currentActionBarHeight;
                    } else {
                        height = (int)(48.0f * density);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = (int)(40.0f * density);
                        }
                    }

                    LinearLayout.LayoutParams params2 = (LinearLayout.LayoutParams)topView.getLayoutParams();
                    params2.setMargins(0, height, 0, 0);
                    topView.setLayoutParams(params2);

                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

                    return false;
                }
            });
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
        actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.NewGroup) + "</font>"));

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
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
        if (getSherlockActivity() == null) {
            return;
        }
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
    }

    @Override
    public void didUploadedPhoto(final TLRPC.TL_inputFile file, final TLRPC.PhotoSize small, final TLRPC.PhotoSize big) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                uploadedAvatar = file;
                avatar = small.location;
                avatarImage.setImage(avatar, "50_50", R.drawable.group_placeholder_blue);
                if (createAfterUpload) {
                    Log.e("tmessages", "avatar did uploaded");
                    MessagesController.Instance.createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar);
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_create_menu, menu);
        MenuItem doneItem = menu.findItem(R.id.done_menu_item);
        TextView doneTextView = (TextView)doneItem.getActionView().findViewById(R.id.done_button);
        doneTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (donePressed) {
                    return;
                }
                if (nameTextView.getText().length() == 0) {
                    return;
                }
                donePressed = true;
                Utilities.ShowProgressDialog(parentActivity, getStringEntry(R.string.Loading));
                if (avatarUpdater.uploadingAvatar != null) {
                    createAfterUpload = true;
                } else {
                    MessagesController.Instance.createChat(nameTextView.getText().toString(), selectedContacts, uploadedAvatar);
                }
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == MessagesController.updateInterfaces) {
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (id == MessagesController.chatDidFailCreate) {
            Utilities.HideProgressDialog(parentActivity);
            donePressed = false;
            Log.e("tmessages", "did fail create");
        } else if (id == MessagesController.chatDidCreated) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    Utilities.HideProgressDialog(parentActivity);
                    ChatActivity fragment = new ChatActivity();
                    Bundle bundle = new Bundle();
                    bundle.putInt("chat_id", (Integer)args[0]);
                    fragment.setArguments(bundle);
                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_group_" + args[0], true, false);
                }
            });
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
            return selectedContacts.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            TLRPC.User user = MessagesController.Instance.users.get(selectedContacts.get(position));

            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.messages_search_user_layout, parent, false);
            }
            ContactsActivity.ContactListRowHolder holder = (ContactsActivity.ContactListRowHolder)convertView.getTag();
            if (holder == null) {
                holder = new ContactsActivity.ContactListRowHolder(convertView);
                convertView.setTag(holder);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                holder.nameTextView.setTypeface(typeface);
            }

            View divider = convertView.findViewById(R.id.settings_row_divider);
            if (position == selectedContacts.size() - 1) {
                divider.setVisibility(View.INVISIBLE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                holder.nameTextView.setText(Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>"));
            } else if (user.first_name.length() != 0) {
                holder.nameTextView.setText(Html.fromHtml("<b>" + user.first_name + "</b>"));
            } else {
                holder.nameTextView.setText(Html.fromHtml("<b>" + user.last_name + "</b>"));
            }

            TLRPC.FileLocation photo = null;
            if (user.photo != null) {
                photo = user.photo.photo_small;
            }
            int placeHolderId = Utilities.getUserAvatarForId(user.id);
            holder.avatarImage.setImage(photo, "50_50", placeHolderId);

            if (user.status == null) {
                holder.messageTextView.setTextColor(0xff808080);
                holder.messageTextView.setText(getStringEntry(R.string.Offline));
            } else {
                int currentTime = ConnectionsManager.Instance.getCurrentTime();
                if (user.status.expires > currentTime || user.status.was_online > currentTime) {
                    holder.messageTextView.setTextColor(0xff006fc8);
                    holder.messageTextView.setText(getStringEntry(R.string.Online));
                } else {
                    if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                        holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                    } else {
                        int value = user.status.was_online;
                        if (value == 0) {
                            value = user.status.expires;
                        }
                        holder.messageTextView.setText(getStringEntry(R.string.LastSeen) + " " + Utilities.formatDateOnline(value));
                    }
                    holder.messageTextView.setTextColor(0xff808080);
                }
            }

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
                textView.setText(selectedContacts.size() + " " + getStringEntry(R.string.MEMBER));
            } else {
                textView.setText(selectedContacts.size() + " " + getStringEntry(R.string.MEMBERS));
            }
            return convertView;
        }
    }
}
