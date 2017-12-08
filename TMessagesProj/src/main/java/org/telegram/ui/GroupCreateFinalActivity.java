/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.GroupCreateDividerItemDecoration;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class GroupCreateFinalActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AvatarUpdater.AvatarUpdaterDelegate {

    private GroupCreateAdapter adapter;
    private RecyclerView listView;
    private EditTextBoldCursor editText;
    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem doneItem;
    private ContextProgressView progressView;
    private AnimatorSet doneItemAnimation;
    private FrameLayout editTextContainer;

    private TLRPC.FileLocation avatar;
    private TLRPC.InputFile uploadedAvatar;
    private ArrayList<Integer> selectedContacts;
    private boolean createAfterUpload;
    private boolean donePressed;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();
    private String nameToSet;
    private int chatType = ChatObject.CHAT_TYPE_CHAT;

    private int reqId;

    private final static int done_button = 1;

    public GroupCreateFinalActivity(Bundle args) {
        super(args);
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
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
        final ArrayList<Integer> usersToLoad = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            Integer uid = selectedContacts.get(a);
            if (MessagesController.getInstance().getUser(uid) == null) {
                usersToLoad.add(uid);
            }
        }
        if (!usersToLoad.isEmpty()) {
            final Semaphore semaphore = new Semaphore(0);
            final ArrayList<TLRPC.User> users = new ArrayList<>();
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
                FileLog.e(e);
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
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("NewGroup", R.string.NewGroup));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    if (editText.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(editText, 2, 0);
                        return;
                    }
                    donePressed = true;
                    AndroidUtilities.hideKeyboard(editText);
                    editText.setEnabled(false);

                    if (avatarUpdater.uploadingAvatar != null) {
                        createAfterUpload = true;
                    } else {
                        showEditDoneProgress(true);
                        reqId = MessagesController.getInstance().createChat(editText.getText().toString(), selectedContacts, null, chatType, GroupCreateFinalActivity.this);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        progressView = new ContextProgressView(context, 1);
        doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        progressView.setVisibility(View.INVISIBLE);

        fragmentView = new LinearLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView) {
                    parentLayout.drawHeaderShadow(canvas, editTextContainer.getMeasuredHeight());
                }
                return result;
            }
        };
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        editTextContainer = new FrameLayout(context);
        linearLayout.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable.setInfo(5, null, null, chatType == ChatObject.CHAT_TYPE_BROADCAST);
        avatarImage.setImageDrawable(avatarDrawable);
        editTextContainer.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 16, LocaleController.isRTL ? 16 : 0, 16));

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
                showDialog(builder.create());
            }
        });

        editText = new EditTextBoldCursor(context);
        editText.setHint(chatType == ChatObject.CHAT_TYPE_CHAT ? LocaleController.getString("EnterGroupNamePlaceholder", R.string.EnterGroupNamePlaceholder) : LocaleController.getString("EnterListName", R.string.EnterListName));
        if (nameToSet != null) {
            editText.setText(nameToSet);
            nameToSet = null;
        }
        editText.setMaxLines(4);
        editText.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        editText.setFilters(inputFilters);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 16 : 96, 0, LocaleController.isRTL ? 96 : 16, 0));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                avatarDrawable.setInfo(5, editText.length() > 0 ? editText.getText().toString() : null, null, false);
                avatarImage.invalidate();
            }
        });

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(new GroupCreateDividerItemDecoration());
        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });

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
                    MessagesController.getInstance().createChat(editText.getText().toString(), selectedContacts, null, chatType, GroupCreateFinalActivity.this);
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
        if (editText != null) {
            String text = editText.getText().toString();
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
            if (editText != null) {
                editText.setText(text);
            } else {
                nameToSet = text;
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            if (listView == null) {
                return;
            }
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(mask);
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidFailCreate) {
            reqId = 0;
            donePressed = false;
            showEditDoneProgress(false);
            if (editText != null) {
                editText.setEnabled(true);
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            reqId = 0;
            int chat_id = (Integer) args[0];
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            Bundle args2 = new Bundle();
            args2.putInt("chat_id", chat_id);
            presentFragment(new ChatActivity(args2), true);
            if (uploadedAvatar != null) {
                MessagesController.getInstance().changeChatAvatar(chat_id, uploadedAvatar);
            }
        }
    }

    private void showEditDoneProgress(final boolean show) {
        if (doneItem == null) {
            return;
        }
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            doneItem.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            doneItem.getImageView().setVisibility(View.VISIBLE);
            doneItem.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getImageView(), "alpha", 1.0f));

        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        doneItem.getImageView().setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
    }

    public class GroupCreateAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public GroupCreateAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public int getItemCount() {
            return 1 + selectedContacts.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GroupCreateSectionCell(context);
                    break;
                default:
                    view = new GroupCreateUserCell(context, false);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    GroupCreateSectionCell cell = (GroupCreateSectionCell) holder.itemView;
                    cell.setText(LocaleController.formatPluralString("Members", selectedContacts.size()));
                    break;
                }
                default: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLRPC.User user = MessagesController.getInstance().getUser(selectedContacts.get(position - 1));
                    cell.setUser(user, null, null);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case 0:
                    return 0;
                default:
                    return 1;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
                avatarDrawable.setInfo(5, editText.length() > 0 ? editText.getText().toString() : null, null, false);
                avatarImage.invalidate();
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive),
                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive),
                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText),
                new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor),
                new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GroupCreateSectionCell.class}, null, null, null, Theme.key_graySection),
                new ThemeDescription(listView, 0, new Class[]{GroupCreateSectionCell.class}, new String[]{"drawable"}, null, null, null, Theme.key_groupcreate_sectionShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_groupcreate_onlineText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_groupcreate_offlineText),
                new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, сellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2),
                new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2),

                new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
        };
    }
}
