/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.IdenticonView;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.util.Locale;

public class UserProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private ListView listView;
    private ListAdapter listAdapter;
    private int user_id;
    private String selectedPhone;
    private int totalMediaCount = -1;
    private boolean creatingChat = false;
    private long dialog_id;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private boolean isRTL;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatCreated);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatUpdated);
        user_id = getArguments().getInt("user_id", 0);
        dialog_id = getArguments().getLong("dialog_id", 0);
        if (dialog_id != 0) {
            currentEncryptedChat = MessagesController.Instance.encryptedChats.get((int)(dialog_id >> 32));
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.encryptedChatCreated);
        NotificationCenter.Instance.removeObserver(this, MessagesController.encryptedChatUpdated);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.user_profile_layout, container, false);
            listAdapter = new ListAdapter(parentActivity);
            Locale locale = Locale.getDefault();
            String lang = locale.getLanguage();
            isRTL = lang != null && lang.toLowerCase().equals("ar");

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == 5 && dialog_id == 0 ||
                            dialog_id != 0 && (i == 6 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat  ||
                                    i == 4 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                        SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        String key;
                        if (dialog_id == 0) {
                            key = "notify_" + user_id;
                        } else {
                            key = "notify_" + dialog_id;
                        }
                        boolean value = preferences.getBoolean(key, true);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(key, !value);
                        editor.commit();
                        listView.invalidateViews();

                        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
                        req.settings = new TLRPC.TL_inputPeerNotifySettings();
                        req.settings.sound = "";
                        req.peer = new TLRPC.TL_inputNotifyPeer();
                        TLRPC.User user = MessagesController.Instance.users.get(user_id);
                        if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                            ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerForeign();
                            ((TLRPC.TL_inputNotifyPeer)req.peer).peer.user_id = user.id;
                            ((TLRPC.TL_inputNotifyPeer)req.peer).peer.access_hash = user.access_hash;
                        } else {
                            ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerContact();
                            ((TLRPC.TL_inputNotifyPeer)req.peer).peer.user_id = user.id;
                        }
                        req.settings.show_previews = true;
                        req.settings.events_mask = 1;
                        if (value) {
                            req.settings.mute_until = (int)(System.currentTimeMillis() / 1000) + 10 * 365 * 24 * 60 * 60;
                        } else {
                            req.settings.mute_until = 0;
                        }
                        ConnectionsManager.Instance.performRpc(req, null, null, true, RPCRequest.RPCRequestClassGeneric);
                    } else if (i == 6 && dialog_id == 0 ||
                            dialog_id != 0 && (i == 7 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                    i == 5 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                        Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                        SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        Uri currentSound = null;

                        String defaultPath = null;
                        Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                        if (defaultUri != null) {
                            defaultPath = defaultUri.getPath();
                        }

                        String path = preferences.getString("sound_path_" + user_id, defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }

                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                        startActivityForResult(tmpIntent, 0);
                    } else if (i == 8 && dialog_id == 0 ||
                            dialog_id != 0 && (i == 9 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                    i == 7 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                        MediaActivity fragment = new MediaActivity();
                        Bundle bundle = new Bundle();
                        if (dialog_id != 0) {
                            bundle.putLong("dialog_id", dialog_id);
                        } else {
                            bundle.putLong("dialog_id", user_id);
                        }
                        fragment.setArguments(bundle);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "media_user_" + user_id, false);
                    } else if (i == 3 && dialog_id == 0) {
                        creatingChat = true;
                        MessagesController.Instance.startSecretChat(parentActivity, user_id);
                    } else if (i == 5 && dialog_id != 0 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                        IdenticonActivity fragment = new IdenticonActivity();
                        Bundle bundle = new Bundle();
                        bundle.putInt("chat_id", (int)(dialog_id >> 32));
                        fragment.setArguments(bundle);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "key_" + dialog_id, false);
                    } else if (i == 4 && dialog_id != 0 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(getStringEntry(R.string.MessageLifetime));
                        builder.setItems(new CharSequence[]{
                                getStringEntry(R.string.ShortMessageLifetimeForever),
                                getStringEntry(R.string.ShortMessageLifetime2s),
                                getStringEntry(R.string.ShortMessageLifetime5s),
                                getStringEntry(R.string.ShortMessageLifetime1m),
                                getStringEntry(R.string.ShortMessageLifetime1h),
                                getStringEntry(R.string.ShortMessageLifetime1d),
                                getStringEntry(R.string.ShortMessageLifetime1w)

                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int oldValue = currentEncryptedChat.ttl;
                                if (which == 0) {
                                    currentEncryptedChat.ttl = 0;
                                } else if (which == 1) {
                                    currentEncryptedChat.ttl = 2;
                                } else if (which == 2) {
                                    currentEncryptedChat.ttl = 5;
                                } else if (which == 3) {
                                    currentEncryptedChat.ttl = 60;
                                } else if (which == 4) {
                                    currentEncryptedChat.ttl = 60 * 60;
                                } else if (which == 5) {
                                    currentEncryptedChat.ttl = 60 * 60 * 24;
                                } else if (which == 6) {
                                    currentEncryptedChat.ttl = 60 * 60 * 24 * 7;
                                }
                                if (oldValue != currentEncryptedChat.ttl) {
                                    if (listView != null) {
                                        listView.invalidateViews();
                                    }
                                    MessagesController.Instance.sendTTLMessage(currentEncryptedChat);
                                    MessagesStorage.Instance.updateEncryptedChat(currentEncryptedChat);
                                }
                            }
                        });
                        builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                        builder.show().setCanceledOnTouchOutside(true);
                    }
                }
            });
            if (dialog_id != 0) {
                MessagesController.Instance.getMediaCount(dialog_id, classGuid, true);
            } else {
                MessagesController.Instance.getMediaCount(user_id, classGuid, true);
            }

            listView.setOnTouchListener(new OnSwipeTouchListener() {
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(parentActivity, ringtone);
                name = rng.getTitle(parentActivity);
                rng.stop();
            }

            SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == 0) {
                if (name != null && ringtone != null) {
                    editor.putString("sound_" + user_id, name);
                    editor.putString("sound_path_" + user_id, ringtone.toString());
                } else {
                    editor.putString("sound_" + user_id, "NoSound");
                    editor.putString("sound_path_" + user_id, "NoSound");
                }
            }
            editor.commit();
            listView.invalidateViews();
        }

    }

    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (id == MessagesController.contactsDidLoaded) {
            if (parentActivity != null) {
                parentActivity.invalidateOptionsMenu();
            }
        } else if (id == MessagesController.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            if (uid > 0 && user_id == uid && dialog_id == 0 || dialog_id != 0 && dialog_id == uid) {
                totalMediaCount = (Integer)args[1];
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.encryptedChatCreated) {
            if (creatingChat) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat)args[0];
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("enc_id", encryptedChat.id);
                fragment.setArguments(bundle);
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_enc_" + id, true, false);
            }
        } else if (id == MessagesController.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
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
        actionBar.setSubtitle(null);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        if (dialog_id != 0) {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.SecretTitle) + "</font>"));
        } else {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.ContactInfo) + "</font>"));
        }

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            if (dialog_id != 0) {
                title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_blue, 0, 0, 0);
                title.setCompoundDrawablePadding((int)(4 * getResources().getDisplayMetrics().density));
            } else {
                title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                title.setCompoundDrawablePadding(0);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getSherlockActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
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
                    WindowManager manager = (WindowManager)parentActivity.getSystemService(Activity.WINDOW_SERVICE);
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
                    listView.setPadding(listView.getPaddingLeft(), height, listView.getPaddingRight(), listView.getPaddingBottom());

                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

                    if (dialog_id != 0) {
                        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
                        if (title == null) {
                            final int subtitleId = ApplicationLoader.applicationContext.getResources().getIdentifier("action_bar_title", "id", "android");
                            title = (TextView)parentActivity.findViewById(subtitleId);
                        }
                        if (title != null) {
                            title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_blue, 0, 0, 0);
                            title.setCompoundDrawablePadding((int)(4 * density));
                        }
                    }

                    return false;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
            case R.id.block_contact: {
                TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
                TLRPC.User user = MessagesController.Instance.users.get(user_id);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.id = new TLRPC.TL_inputUserForeign();
                    req.id.access_hash = user.access_hash;
                    req.id.user_id = user_id;
                } else {
                    req.id = new TLRPC.TL_inputUserContact();
                    req.id.user_id = user_id;
                }
                TLRPC.TL_contactBlocked blocked = new TLRPC.TL_contactBlocked();
                blocked.user_id = user_id;
                blocked.date = (int)(System.currentTimeMillis() / 1000);
                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
                break;
            }
            case R.id.add_contact: {
                TLRPC.User user = MessagesController.Instance.users.get(user_id);
                ContactAddActivity fragment = new ContactAddActivity();
                Bundle args = new Bundle();
                args.putInt("user_id", user.id);
                fragment.setArguments(args);
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "add_contact_" + user.id, false);
                break;
            }
            case R.id.share_contact: {
                MessagesActivity fragment = new MessagesActivity();
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("serverOnly", true);
                fragment.setArguments(args);
                fragment.delegate = this;
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_select", false);
                break;
            }
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, com.actionbarsherlock.view.MenuInflater inflater) {
        if (MessagesController.Instance.contactsDict.get(user_id) == null) {
            TLRPC.User user = MessagesController.Instance.users.get(user_id);
            if (user.phone != null && user.phone.length() != 0) {
                inflater.inflate(R.menu.user_profile_menu, menu);
            } else {
                inflater.inflate(R.menu.user_profile_block_menu, menu);
            }
        } else {
            inflater.inflate(R.menu.user_profile_contact_menu, menu);
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id) {
        if (dialog_id != 0) {
            ChatActivity fragment = new ChatActivity();
            Bundle bundle = new Bundle();
            int lower_part = (int)dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                    bundle.putInt("user_id", lower_part);
                    fragment.setArguments(bundle);
                    fragment.scrollToTopOnResume = true;
                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + lower_part, true, false);
                    removeSelfFromStack();
                    messageFragment.removeSelfFromStack();
                } else if (lower_part < 0) {
                    NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                    bundle.putInt("chat_id", -lower_part);
                    fragment.setArguments(bundle);
                    fragment.scrollToTopOnResume = true;
                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_group_" + -lower_part, true, false);
                    messageFragment.removeSelfFromStack();
                    removeSelfFromStack();
                }
            } else {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                int id = (int)(dialog_id >> 32);
                bundle.putInt("enc_id", id);
                fragment.setArguments(bundle);
                fragment.scrollToTopOnResume = true;
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_enc_" + id, false);
                messageFragment.removeSelfFromStack();
                removeSelfFromStack();
            }
            TLRPC.User user = MessagesController.Instance.users.get(user_id);
            MessagesController.Instance.sendMessage(user, dialog_id);
        }
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
            if (dialog_id == 0) {
                return i == 2 || i == 3 || i == 5 || i == 6 || i == 8;
            } else {
                if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                    return i == 2 || i == 4 || i == 5 || i == 6 || i == 7 || i == 9;
                } else {
                    return i == 2 || i == 4 || i == 5 || i == 9;
                }
            }
        }

        @Override
        public int getCount() {
            if (dialog_id == 0) {
                return 9;
            } else {
                if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                    return 10;
                } else {
                    return 8;
                }
            }
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
                BackupImageView avatarImage;
                TextView onlineText;
                TLRPC.User user = MessagesController.Instance.users.get(user_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_avatar_layout, viewGroup, false);

                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                    onlineText.setTypeface(typeface);
                    avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    avatarImage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            TLRPC.User user = MessagesController.Instance.users.get(user_id);
                            if (user.photo != null && user.photo.photo_big != null) {
                                NotificationCenter.Instance.addToMemCache(3, user.photo.photo_big);
                                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                                startActivity(intent);
                            }
                        }
                    });
                } else {
                    avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_name);

                textView.setText(Utilities.formatName(user.first_name, user.last_name));

                if (user.status == null) {
                    onlineText.setText(getStringEntry(R.string.Offline));
                } else {
                    int currentTime = ConnectionsManager.Instance.getCurrentTime();
                    if (user.status.expires > currentTime || user.status.was_online > currentTime) {
                        onlineText.setText(getStringEntry(R.string.Online));
                    } else {
                        if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                            onlineText.setText(getStringEntry(R.string.Invisible));
                        } else {
                            int value = user.status.was_online;
                            if (value == 0) {
                                value = user.status.expires;
                            }
                            onlineText.setText(String.format("%s %s", getStringEntry(R.string.LastSeen), Utilities.formatDateOnline(value)));
                        }
                    }
                }

                TLRPC.FileLocation photo = null;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                avatarImage.setImage(photo, "50_50", Utilities.getUserAvatarForId(user.id));
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == 1) {
                    textView.setText(getStringEntry(R.string.PHONE));
                } else if (i == 4 && dialog_id == 0 || dialog_id != 0 && i == 3) {
                    textView.setText(getStringEntry(R.string.SETTINGS));
                } else if (i == 7 && dialog_id == 0 ||
                        dialog_id != 0 && (i == 8 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                i == 6 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                    textView.setText(getStringEntry(R.string.SHAREDMEDIA));
                }
            } else if (type == 2) {
                final TLRPC.User user = MessagesController.Instance.users.get(user_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_phone_layout, viewGroup, false);
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (user.phone == null || user.phone.length() == 0) {
                                return;
                            }
                            selectedPhone = user.phone;

                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

                            builder.setItems(new CharSequence[] {getStringEntry(R.string.Copy), getStringEntry(R.string.Call)}, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (i == 1) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + selectedPhone));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else if (i == 0) {
                                        int sdk = android.os.Build.VERSION.SDK_INT;
                                        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                                            clipboard.setText(selectedPhone);
                                        } else {
                                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", selectedPhone);
                                            clipboard.setPrimaryClip(clip);
                                        }
                                    }
                                }
                            });
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    });
                }
                ImageButton button = (ImageButton)view.findViewById(R.id.settings_edit_name);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        TLRPC.User user = MessagesController.Instance.users.get(user_id);
                        if (user == null || user instanceof TLRPC.TL_userEmpty) {
                            return;
                        }
                        NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                        ChatActivity fragment = new ChatActivity();
                        Bundle bundle = new Bundle();
                        bundle.putInt("user_id", user_id);
                        fragment.setArguments(bundle);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + user_id, true, false);
                    }
                });
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                detailTextView.setTypeface(typeface);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 2) {
                    if (user.phone != null && user.phone.length() != 0) {
                        textView.setText(PhoneFormat.Instance.format("+" + user.phone));
                    } else {
                        textView.setText("Unknown");
                    }
                    divider.setVisibility(dialog_id == 0 ? View.VISIBLE : View.INVISIBLE);
                    detailTextView.setText(getStringEntry(R.string.PhoneMobile));
                }
            } else if (type == 3) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_check_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 5 && dialog_id == 0 ||
                        dialog_id != 0 && (i == 6 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                i == 4 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                    SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String key;
                    if (dialog_id == 0) {
                        key = "notify_" + user_id;
                    } else {
                        key = "notify_" + dialog_id;
                    }
                    boolean value = preferences.getBoolean(key, true);
                    ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
                    if (value) {
                        checkButton.setImageResource(R.drawable.btn_check_on);
                    } else {
                        checkButton.setImageResource(R.drawable.btn_check_off);
                    }
                    textView.setText(getStringEntry(R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                }
            } else if (type == 4) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                detailTextView.setTypeface(typeface);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 6 && dialog_id == 0 ||
                        dialog_id != 0 && (i == 7 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                i == 5 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                    SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String name = preferences.getString("sound_" + user_id, getStringEntry(R.string.Default));
                    if (name.equals("NoSound")) {
                        detailTextView.setText(getStringEntry(R.string.NoSound));
                    } else {
                        detailTextView.setText(name);
                    }
                    textView.setText(R.string.Sound);
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == 8 && dialog_id == 0 ||
                        dialog_id != 0 && (i == 9 && currentEncryptedChat instanceof TLRPC.TL_encryptedChat ||
                                i == 7 && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat))) {
                    textView.setText(R.string.SharedMedia);
                    if (totalMediaCount == -1) {
                        detailTextView.setText(getStringEntry(R.string.Loading));
                    } else {
                        detailTextView.setText(String.format("%d", totalMediaCount));
                    }
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == 4 && dialog_id != 0) {
                    TLRPC.EncryptedChat encryptedChat = MessagesController.Instance.encryptedChats.get((int)(dialog_id >> 32));
                    textView.setText(R.string.MessageLifetime);
                    divider.setVisibility(View.VISIBLE);
                    if (encryptedChat.ttl == 0) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetimeForever));
                    } else if (encryptedChat.ttl == 2) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime2s));
                    } else if (encryptedChat.ttl == 5) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime5s));
                    } else if (encryptedChat.ttl == 60) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime1m));
                    } else if (encryptedChat.ttl == 60 * 60) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime1h));
                    } else if (encryptedChat.ttl == 60 * 60 * 24) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime1d));
                    } else if (encryptedChat.ttl == 60 * 60 * 24 * 7) {
                        detailTextView.setText(getStringEntry(R.string.ShortMessageLifetime1w));
                    } else {
                        detailTextView.setText(String.format("%d", encryptedChat.ttl));
                    }
                }
            } else if (type == 5) {
                if (dialog_id == 0) {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_row_button_layout, viewGroup, false);
                    }
                    TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                    View divider = view.findViewById(R.id.settings_row_divider);
                    if (i == 3) {
                        if (isRTL) {
                            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_lock_gray, 0);
                        } else {
                            textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_gray, 0, 0, 0);
                        }
                        textView.setCompoundDrawablePadding((int)(8 * getResources().getDisplayMetrics().density));
                        textView.setText(getStringEntry(R.string.StartEncryptedChat));
                        divider.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.user_profile_identicon_layout, viewGroup, false);
                    }
                    TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                    View divider = view.findViewById(R.id.settings_row_divider);
                    divider.setVisibility(View.VISIBLE);
                    IdenticonView identiconView = (IdenticonView)view.findViewById(R.id.identicon_view);
                    TLRPC.EncryptedChat encryptedChat = MessagesController.Instance.encryptedChats.get((int)(dialog_id >> 32));
                    identiconView.setBytes(encryptedChat.auth_key);
                    textView.setText(getStringEntry(R.string.EncryptionKey));
                }
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (dialog_id != 0) {
                if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                    if (i == 0) {
                        return 0;
                    } else if (i == 1 || i == 3 || i == 8) {
                        return 1;
                    } else if (i == 2) {
                        return 2;
                    } else if (i == 6) {
                        return 3;
                    } else if (i == 7 || i == 9 || i == 4) {
                        return 4;
                    } else if (i == 5) {
                        return 5;
                    }
                } else {
                    if (i == 0) {
                        return 0;
                    } else if (i == 1 || i == 3 || i == 6) {
                        return 1;
                    } else if (i == 2) {
                        return 2;
                    } else if (i == 4) {
                        return 3;
                    } else if (i == 5 || i == 7) {
                        return 4;
                    }
                }
            } else {
                if (i == 0) {
                    return 0;
                } else if (i == 1 || i == 4 || i == 7) {
                    return 1;
                } else if (i == 2) {
                    return 2;
                } else if (i == 5) {
                    return 3;
                } else if (i == 6 || i == 8) {
                    return 4;
                } else if (i == 3) {
                    return 5;
                }
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 6;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
