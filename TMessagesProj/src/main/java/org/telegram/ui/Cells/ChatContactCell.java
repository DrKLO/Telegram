/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;

public class ChatContactCell extends ChatBaseCell {

    public ChatContactCell(Context context) {
        super(context);
    }

    /*
    public class ChatListRowHolderEx {
        public BackupImageView avatarImageView;
        public TextView nameTextView;
        public TextView messageTextView;
        public TextView timeTextView;
        public ImageView halfCheckImage;
        public ImageView checkImage;
        public MessageObject message;
        public TextView phoneTextView;
        public BackupImageView contactAvatar;
        public View contactView;
        public ImageView addContactButton;
        public View addContactView;
        public View chatBubbleView;

        public void update() {
            TLRPC.User fromUser = MessagesController.getInstance().getUser(message.messageOwner.from_id);

            int type = message.type;

            if (timeTextView != null) {
                timeTextView.setText(LocaleController.formatterDay.format((long) (message.messageOwner.date) * 1000));
            }

            if (avatarImageView != null && fromUser != null) {
                TLRPC.FileLocation photo = null;
                if (fromUser.photo != null) {
                    photo = fromUser.photo.photo_small;
                }
                int placeHolderId = AndroidUtilities.getUserAvatarForId(fromUser.id);
                avatarImageView.setImage(photo, "50_50", placeHolderId);
            }

            if (type != 12 && type != 13 && nameTextView != null && fromUser != null && type != 8 && type != 9) {
                nameTextView.setText(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                nameTextView.setTextColor(AndroidUtilities.getColorForId(message.messageOwner.from_id));
            }

            if (type == 12 || type == 13) {
                TLRPC.User contactUser = MessagesController.getInstance().getUser(message.messageOwner.media.user_id);
                if (contactUser != null) {
                    nameTextView.setText(ContactsController.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(AndroidUtilities.getColorForId(contactUser.id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (!phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    TLRPC.FileLocation photo = null;
                    if (contactUser.photo != null) {
                        photo = contactUser.photo.photo_small;
                    }
                    int placeHolderId = AndroidUtilities.getUserAvatarForId(contactUser.id);
                    contactAvatar.setImage(photo, "50_50", placeHolderId);
                    if (contactUser.id != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(contactUser.id) == null) {
                        addContactView.setVisibility(View.VISIBLE);
                    } else {
                        addContactView.setVisibility(View.GONE);
                    }
                } else {
                    nameTextView.setText(ContactsController.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(AndroidUtilities.getColorForId(message.messageOwner.media.user_id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (message.messageOwner.media.user_id != 0 && !phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    contactAvatar.setImageResource(AndroidUtilities.getUserAvatarForId(message.messageOwner.media.user_id));
                    addContactView.setVisibility(View.GONE);
                }
            } else if (type == 6) {
                messageTextView.setTextSize(16);
                messageTextView.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
            }

            if (message.isFromMe()) {
                if (halfCheckImage != null) {
                    if (message.isSending()) {
                        checkImage.setVisibility(View.INVISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_clock);
                        halfCheckImage.setVisibility(View.VISIBLE);
                    } else if (message.isSendError()) {
                        halfCheckImage.setVisibility(View.VISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_warning);
                        if (checkImage != null) {
                            checkImage.setVisibility(View.INVISIBLE);
                        }
                    } else if (message.isSent()) {
                        if (!message.isUnread()) {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.VISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_halfcheck);
                        } else {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.INVISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_check);
                        }
                    }
                }
            }
        }

        public ChatListRowHolderEx(View view, int type) {
            avatarImageView = (BackupImageView)view.findViewById(R.id.chat_group_avatar_image);
            nameTextView = (TextView)view.findViewById(R.id.chat_user_group_name);
            timeTextView = (TextView)view.findViewById(R.id.chat_time_text);
            halfCheckImage = (ImageView)view.findViewById(R.id.chat_row_halfcheck);
            checkImage = (ImageView)view.findViewById(R.id.chat_row_check);
            messageTextView = (TextView)view.findViewById(R.id.chat_message_text);
            phoneTextView = (TextView)view.findViewById(R.id.phone_text_view);
            contactAvatar = (BackupImageView)view.findViewById(R.id.contact_avatar);
            contactView = view.findViewById(R.id.shared_layout);
            addContactButton = (ImageView)view.findViewById(R.id.add_contact_button);
            addContactView = view.findViewById(R.id.add_contact_view);
            chatBubbleView = view.findViewById(R.id.chat_bubble_layout);
            if (messageTextView != null) {
                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
            }

            if (addContactButton != null) {
                addContactButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putInt("user_id", message.messageOwner.media.user_id);
                        args.putString("phone", message.messageOwner.media.phone_number);
                        presentFragment(new ContactAddActivity(args));
                    }
                });

                addContactButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactView != null) {
                contactView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (message.type == 12 || message.type == 13) {
                            if (actionBarLayer.isActionModeShowed()) {
                                processRowSelect(view);
                                return;
                            }
                            if (message.messageOwner.media.user_id != UserConfig.getClientUserId()) {
                                TLRPC.User user = null;
                                if (message.messageOwner.media.user_id != 0) {
                                    user = MessagesController.getInstance().getUser(message.messageOwner.media.user_id);
                                }
                                if (user != null) {
                                    Bundle args = new Bundle();
                                    args.putInt("user_id", message.messageOwner.media.user_id);
                                    presentFragment(new UserProfileActivity(args));
                                } else {
                                    if (message.messageOwner.media.phone_number == null || message.messageOwner.media.phone_number.length() == 0) {
                                        return;
                                    }
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setItems(new CharSequence[] {LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Call", R.string.Call)}, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    if (i == 1) {
                                                        try {
                                                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + message.messageOwner.media.phone_number));
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                            getParentActivity().startActivity(intent);
                                                        } catch (Exception e) {
                                                            FileLog.e("tmessages", e);
                                                        }
                                                    } else if (i == 0) {
                                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                                                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            clipboard.setText(message.messageOwner.media.phone_number);
                                                        } else {
                                                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", message.messageOwner.media.phone_number);
                                                            clipboard.setPrimaryClip(clip);
                                                        }
                                                    }
                                                }
                                            }
                                    );
                                    showAlertDialog(builder);
                                }
                            }
                        }
                    }
                });

                contactView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactAvatar != null) {
                contactAvatar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
            }

            if (avatarImageView != null) {
                avatarImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", message.messageOwner.from_id);
                            presentFragment(new UserProfileActivity(args));
                        }
                    }
                });
            }
        }

        private void processOnClick(View view) {
            if (actionBarLayer.isActionModeShowed()) {
                processRowSelect(view);
            }
        }
    }
     */
}
