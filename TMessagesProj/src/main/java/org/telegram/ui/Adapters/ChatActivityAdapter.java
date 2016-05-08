/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

public class ChatActivityAdapter {

    /*private Context mContext;

    public ChatAdapter(Context context) {
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
        int count = messages.size();
        if (count != 0) {
            if (!endReached) {
                count++;
            }
            if (!forward_end_reached) {
                count++;
            }
        }
        return count;
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
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        int offset = 1;
        if ((!endReached || !forward_end_reached) && messages.size() != 0) {
            if (!endReached) {
                offset = 0;
            }
            if (i == 0 && !endReached || !forward_end_reached && i == (messages.size() + 1 - offset)) {
                View progressBar = null;
                if (view == null) {
                    LayoutInflater li = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_loading_layout, viewGroup, false);
                    progressBar = view.findViewById(R.id.progressLayout);
                    if (ApplicationLoader.isCustomTheme()) {
                        progressBar.setBackgroundResource(R.drawable.system_loader2);
                    } else {
                        progressBar.setBackgroundResource(R.drawable.system_loader1);
                    }
                } else {
                    progressBar = view.findViewById(R.id.progressLayout);
                }
                progressBar.setVisibility(loadsCount > 1 ? View.VISIBLE : View.INVISIBLE);

                return view;
            }
        }
        final MessageObject message = messages.get(messages.size() - i - offset);
        int type = message.contentType;
        if (view == null) {
            if (type == 0) {
                view = new ChatMessageCell(mContext);
            }
            if (type == 1) {
                view = new ChatMediaCell(mContext);
            } else if (type == 2) {
                view = new ChatAudioCell(mContext);
            } else if (type == 3) {
                view = new ChatContactCell(mContext);
            } else if (type == 6) {
                LayoutInflater li = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = li.inflate(R.layout.chat_unread_layout, viewGroup, false);
            } else if (type == 4) {
                view = new ChatActionCell(mContext);
            }

            if (view instanceof ChatBaseCell) {
                ((ChatBaseCell) view).setDelegate(new ChatBaseCell.ChatBaseCellDelegate() {
                    @Override
                    public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell);
                            return;
                        }
                        if (user != null && user.id != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            presentFragment(new ProfileActivity(args));
                        }
                    }

                    @Override
                    public void didPressedCancelSendButton(ChatBaseCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.messageOwner.send_state != 0) {
                            SendMessagesHelper.getInstance().cancelSendingMessage(message);
                        }
                    }

                    @Override
                    public void didLongPressed(ChatBaseCell cell) {
                        createMenu(cell, false);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return actionBar != null && !actionBar.isActionModeShowed();
                    }

                    @Override
                    public void didPressUrl(String url) {
                        if (url.startsWith("@")) {
                            openProfileWithUsername(url.substring(1));
                        } else if (url.startsWith("#")) {
                            MessagesActivity fragment = new MessagesActivity(null);
                            fragment.setSearchString(url);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public void didPressReplyMessage(ChatBaseCell cell, int id) {
                        scrollToMessageId(id, cell.getMessageObject().getId(), true);
                    }
                });
                if (view instanceof ChatMediaCell) {
                    ((ChatMediaCell) view).setAllowedToSetPhoto(openAnimationEnded);
                    ((ChatMediaCell) view).setMediaDelegate(new ChatMediaCell.ChatMediaCellDelegate() {
                        @Override
                        public void didClickedImage(ChatMediaCell cell) {
                            MessageObject message = cell.getMessageObject();
                            if (message.isSendError()) {
                                createMenu(cell, false);
                                return;
                            } else if (message.isSending()) {
                                return;
                            }
                            if (message.type == 1) {
                                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                                PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                            } else if (message.type == 3) {
                                sendSecretMessageRead(message);
                                try {
                                    File f = null;
                                    if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                        f = new File(message.messageOwner.attachPath);
                                    }
                                    if (f == null || f != null && !f.exists()) {
                                        f = FileLoader.getPathToMessage(message.messageOwner);
                                    }
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                    getParentActivity().startActivityForResult(intent, 500);
                                } catch (Exception e) {
                                    alertUserOpenError(message);
                                }
                            } else if (message.type == 4) {
                                if (!isGoogleMapsInstalled()) {
                                    return;
                                }
                                LocationActivity fragment = new LocationActivity();
                                fragment.setMessageObject(message);
                                presentFragment(fragment);
                            } else if (message.type == 9) {
                                File f = null;
                                String fileName = message.getFileName();
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    f = new File(message.messageOwner.attachPath);
                                }
                                if (f == null || f != null && !f.exists()) {
                                    f = FileLoader.getPathToMessage(message.messageOwner);
                                }
                                if (f != null && f.exists()) {
                                    String realMimeType = null;
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        if (message.type == 8 || message.type == 9) {
                                            MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                            int idx = fileName.lastIndexOf('.');
                                            if (idx != -1) {
                                                String ext = fileName.substring(idx + 1);
                                                realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                                if (realMimeType == null) {
                                                    realMimeType = message.messageOwner.media.document.mime_type;
                                                    if (realMimeType == null || realMimeType.length() == 0) {
                                                        realMimeType = null;
                                                    }
                                                }
                                                if (realMimeType != null) {
                                                    intent.setDataAndType(Uri.fromFile(f), realMimeType);
                                                } else {
                                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                }
                                            } else {
                                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                            }
                                        }
                                        if (realMimeType != null) {
                                            try {
                                                getParentActivity().startActivityForResult(intent, 500);
                                            } catch (Exception e) {
                                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                getParentActivity().startActivityForResult(intent, 500);
                                            }
                                        } else {
                                            getParentActivity().startActivityForResult(intent, 500);
                                        }
                                    } catch (Exception e) {
                                        alertUserOpenError(message);
                                    }
                                }
                            }
                        }

                        @Override
                        public void didPressedOther(ChatMediaCell cell) {
                            createMenu(cell, true);
                        }
                    });
                } else if (view instanceof ChatContactCell) {
                    ((ChatContactCell) view).setContactDelegate(new ChatContactCell.ChatContactCellDelegate() {
                        @Override
                        public void didClickAddButton(ChatContactCell cell, TLRPC.User user) {
                            if (actionBar.isActionModeShowed()) {
                                processRowSelect(cell);
                                return;
                            }
                            MessageObject messageObject = cell.getMessageObject();
                            Bundle args = new Bundle();
                            args.putInt("user_id", messageObject.messageOwner.media.user_id);
                            args.putString("phone", messageObject.messageOwner.media.phone_number);
                            args.putBoolean("addContact", true);
                            presentFragment(new ContactAddActivity(args));
                        }

                        @Override
                        public void didClickPhone(ChatContactCell cell) {
                            if (actionBar.isActionModeShowed()) {
                                processRowSelect(cell);
                                return;
                            }
                            final MessageObject messageObject = cell.getMessageObject();
                            if (getParentActivity() == null || messageObject.messageOwner.media.phone_number == null || messageObject.messageOwner.media.phone_number.length() == 0) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Call", R.string.Call)}, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            if (i == 1) {
                                                try {
                                                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + messageObject.messageOwner.media.phone_number));
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    getParentActivity().startActivityForResult(intent, 500);
                                                } catch (Exception e) {
                                                    FileLog.e("tmessages", e);
                                                }
                                            } else if (i == 0) {
                                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                                                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                    clipboard.setText(messageObject.messageOwner.media.phone_number);
                                                } else {
                                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", messageObject.messageOwner.media.phone_number);
                                                    clipboard.setPrimaryClip(clip);
                                                }
                                            }
                                        }
                                    }
                            );
                            showDialog(builder.create());
                        }
                    });
                }
            } else if (view instanceof ChatActionCell) {
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickedImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {
                        createMenu(cell, false);
                    }

                    @Override
                    public void needOpenUserProfile(int uid) {
                        if (uid != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", uid);
                            presentFragment(new ProfileActivity(args));
                        }
                    }
                });
            }
        }

        boolean selected = false;
        boolean disableSelection = false;
        if (actionBar.isActionModeShowed()) {
            if (selectedMessagesIds.containsKey(message.getId())) {
                view.setBackgroundColor(0x6633b5e5);
                selected = true;
            } else {
                view.setBackgroundColor(0);
            }
            disableSelection = true;
        } else {
            view.setBackgroundColor(0);
        }

        if (view instanceof ChatBaseCell) {
            ChatBaseCell baseCell = (ChatBaseCell) view;
            baseCell.isChat = currentChat != null;
            baseCell.setMessageObject(message);
            baseCell.setCheckPressed(!disableSelection, disableSelection && selected);
            if (view instanceof ChatAudioCell && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
                ((ChatAudioCell) view).downloadAudioIfNeed();
            }
            baseCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && message.getId() == highlightMessageId);
        } else if (view instanceof ChatActionCell) {
            ChatActionCell actionCell = (ChatActionCell) view;
            actionCell.setMessageObject(message);
        }
        if (type == 6) {
            TextView messageTextView = (TextView) view.findViewById(R.id.chat_message_text);
            messageTextView.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        int offset = 1;
        if (!endReached && messages.size() != 0) {
            offset = 0;
            if (i == 0) {
                return 5;
            }
        }
        if (!forward_end_reached && i == (messages.size() + 1 - offset)) {
            return 5;
        }
        MessageObject message = messages.get(messages.size() - i - offset);
        return message.contentType;
    }

    @Override
    public int getViewTypeCount() {
        return 7;
    }

    @Override
    public boolean isEmpty() {
        int count = messages.size();
        if (count != 0) {
            if (!endReached) {
                count++;
            }
            if (!forward_end_reached) {
                count++;
            }
        }
        return count == 0;
    }*/
}
