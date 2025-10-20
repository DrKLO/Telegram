/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.PhotoUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LNavigation.NavigationExt;

import java.io.File;
import java.util.ArrayList;

public class ContactAddActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private View doneButton;
    private EditTextCell firstNameField;
    private EditTextCell lastNameField;
    private EditTextCell noteField;
    private BackupImageView avatarImage;
    private TextView nameTextView;
    private TextView onlineTextView;
    private AvatarDrawable avatarDrawable;
    private Theme.ResourcesProvider resourcesProvider;
    private RadialProgressView avatarProgressView;
    private View avatarOverlay;
    private AnimatorSet avatarAnimation;

    private TextCell suggestPhoto;
    private TextCell setAvatarCell;
    private TextCell oldPhotoCell;
    private TextCell suggestBirthday;

    private MessagesController.DialogPhotos dialogPhotos;

    private long user_id;
    private boolean addContact;
    private boolean focusNotes;
    private boolean needAddException;
    private String phone;

    private String firstNameFromCard;
    private String lastNameFromCard;

    private ContactAddActivityDelegate delegate;

    private final static int done_button = 1;
    private ImageUpdater imageUpdater;
    private TLRPC.FileLocation avatar;

    private final static int TYPE_SUGGEST = 1;
    private final static int TYPE_SET = 2;
    private int photoSelectedType;
    private int photoSelectedTypeFinal;
    private TLRPC.Photo prevAvatar;
    private BackupImageView oldAvatarView;

    private FrameLayout infoLayout;
    private UniversalRecyclerView listView;


    public interface ContactAddActivityDelegate {
        void didAddToContacts();
    }

    public ContactAddActivity(Bundle args) {
        super(args);
        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
    }

    public ContactAddActivity(Bundle args, Theme.ResourcesProvider resourcesProvider) {
        super(args);
        this.resourcesProvider = resourcesProvider;
        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogPhotosUpdate);
        user_id = getArguments().getLong("user_id", 0);
        phone = getArguments().getString("phone");
        firstNameFromCard = getArguments().getString("first_name_card");
        lastNameFromCard = getArguments().getString("last_name_card");
        addContact = getArguments().getBoolean("addContact", false);
        focusNotes = getArguments().getBoolean("focus_notes", false);
        needAddException = MessagesController.getNotificationsSettings(currentAccount).getBoolean("dialog_bar_exception" + user_id, false);
        TLRPC.User user = null;
        if (user_id != 0) {
            user = getMessagesController().getUser(user_id);
        }
        if (imageUpdater != null) {
            imageUpdater.parentFragment = this;
            imageUpdater.setDelegate(this);
        }
        dialogPhotos = MessagesController.getInstance(currentAccount).getDialogPhotos(user_id);

        return user != null && super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogPhotosUpdate);
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (addContact) {
            actionBar.setTitle(getString(R.string.NewContact));
        } else {
            actionBar.setTitle(getString(R.string.EditContact));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (firstNameField.getText().length() != 0) {
                        final TLRPC.User user = getMessagesController().getUser(user_id);
                        final TLRPC.UserFull userInfo = getMessagesController().getUserFull(user_id);
                        user.first_name = firstNameField.getText().toString();
                        user.last_name = lastNameField.getText().toString();
                        user.contact = true;
                        final TLRPC.TL_textWithEntities note = noteField.getTextWithEntities();
                        getMessagesController().putUser(user, false);
                        getContactsController().addContact(user, note, needAddException && checkShare);
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("dialog_bar_vis3" + user_id, 3).commit();
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, user_id);
                        if (userInfo != null) {
                            if (note != null && note.text.length() > 0) {
                                userInfo.flags2 |= TLObject.FLAG_22;
                                userInfo.note = note;
                            } else {
                                userInfo.flags2 &=~ TLObject.FLAG_22;
                                userInfo.note = null;
                            }
                            MessagesStorage.getInstance(currentAccount).updateUserInfo(userInfo, true);
                            getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, userInfo.id, userInfo);
                        }
                        finishFragment();
                        if (delegate != null) {
                            delegate.didAddToContacts();
                        }
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItem(done_button, getString(R.string.Done).toUpperCase());

        final FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

//        linearLayout = new LinearLayout(context);
//        linearLayout.setOrientation(LinearLayout.VERTICAL);
//        fra.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
//        linearLayout.setOnTouchListener((v, event) -> true);

        infoLayout = new FrameLayout(context);
        infoLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(dp(32));
        infoLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 16, 13, 16, 13));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);

        avatarOverlay = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                }
            }
        };
        infoLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 16, 13, 16, 13));

        avatarProgressView = new RadialProgressView(context);
        avatarProgressView.setSize(dp(30));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        infoLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 16, 13, 16, 13));

        showAvatarProgress(false, false);

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        nameTextView.setTypeface(AndroidUtilities.bold());
        infoLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 94, 25.66f, LocaleController.isRTL ? 94 : 0, 0));

        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        infoLayout.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 94, 49.66f, LocaleController.isRTL ? 94 : 0, 0));

        firstNameField = new EditTextCell(context, getString(R.string.FirstName), false, false, -1, resourcesProvider);
        firstNameField.editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        firstNameField.setDivider(true);
        firstNameField.editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                lastNameField.editText.requestFocus();
                lastNameField.editText.setSelection(lastNameField.editText.length());
                return true;
            }
            return false;
        });
        firstNameField.editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            boolean focused;
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                focused = hasFocus;
            }
        });
        firstNameField.setText(firstNameFromCard);

        lastNameField = new EditTextCell(context, getString(R.string.LastName), false, false, -1, resourcesProvider);
        lastNameField.editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        lastNameField.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        lastNameField.editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                doneButton.performClick();
                return true;
            } else if (i == EditorInfo.IME_ACTION_NEXT) {
                noteField.editText.requestFocus();
                noteField.editText.setSelection(lastNameField.editText.length());
                return true;
            }
            return false;
        });
        lastNameField.setText(lastNameFromCard);

        noteField = new EditTextCell(context, getString(R.string.AddNotes), true, true, getMessagesController().config.contactNoteLengthLimit.get(), resourcesProvider);
        noteField.editText.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        noteField.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        noteField.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        noteField.editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                doneButton.performClick();
                return true;
            }
            return false;
        });

        if (!addContact) {
            final TLRPC.User user = getMessagesController().getUser(user_id);

            suggestPhoto = new TextCell(context, resourcesProvider);
            suggestPhoto.setTextAndIcon(LocaleController.formatString(R.string.SuggestUserPhoto, user.first_name), R.drawable.msg_addphoto, true);
            suggestPhoto.setBackground(Theme.getSelectorDrawable(true, resourcesProvider));
            suggestPhoto.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            RLottieDrawable suggestDrawable = new RLottieDrawable(R.raw.photo_suggest_icon, "" + R.raw.photo_suggest_icon, AndroidUtilities.dp(50), AndroidUtilities.dp(50), false, null);
            suggestPhoto.imageView.setTranslationX(-AndroidUtilities.dp(8));
            suggestPhoto.imageView.setAnimation(suggestDrawable);
            suggestPhoto.setOnClickListener(v -> {
                photoSelectedType = TYPE_SUGGEST;
                imageUpdater.setUser(user);
                TLRPC.FileLocation avatar = (user == null || user.photo == null) ? null : user.photo.photo_small;
                imageUpdater.openMenu(avatar != null, () -> {

                }, dialogInterface -> {
                    if (!imageUpdater.isUploadingImage()) {
                        suggestDrawable.setCustomEndFrame(85);
                        suggestPhoto.imageView.playAnimation();
                    } else {
                        suggestDrawable.setCurrentFrame(0, false);
                    }

                }, ImageUpdater.TYPE_SUGGEST_PHOTO_FOR_USER);
                suggestDrawable.setCurrentFrame(0);
                suggestDrawable.setCustomEndFrame(43);
                suggestPhoto.imageView.playAnimation();
            });

            setAvatarCell = new TextCell(context, resourcesProvider);
            setAvatarCell.setTextAndIcon(LocaleController.formatString(R.string.UserSetPhoto, user.first_name), R.drawable.msg_addphoto, false);
            setAvatarCell.setBackground(Theme.getSelectorDrawable(true, resourcesProvider));
            setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            RLottieDrawable cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50), AndroidUtilities.dp(50), false, null);
            setAvatarCell.imageView.setTranslationX(-AndroidUtilities.dp(8));
            setAvatarCell.imageView.setAnimation(cameraDrawable);
            setAvatarCell.setOnClickListener(v -> {
                photoSelectedType = TYPE_SET;
                imageUpdater.setUser(user);
                TLRPC.FileLocation avatar = (user == null || user.photo == null) ? null : user.photo.photo_small;
                imageUpdater.openMenu(avatar != null, () -> {

                }, dialogInterface -> {
                    if (!imageUpdater.isUploadingImage()) {
                        cameraDrawable.setCustomEndFrame(86);
                        setAvatarCell.imageView.playAnimation();
                    } else {
                        cameraDrawable.setCurrentFrame(0, false);
                    }
                }, ImageUpdater.TYPE_SET_PHOTO_FOR_USER);
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                setAvatarCell.imageView.playAnimation();
            });

            oldAvatarView = new BackupImageView(context);
            oldPhotoCell = new TextCell(context, resourcesProvider) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    oldAvatarView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
                    oldAvatarView.setRoundRadius(AndroidUtilities.dp(30));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    int l = AndroidUtilities.dp(21);
                    int t =  (getMeasuredHeight() - oldAvatarView.getMeasuredHeight()) / 2;
                    oldAvatarView.layout(l, t, l + oldAvatarView.getMeasuredWidth(), t + oldAvatarView.getMeasuredHeight());
                }
            };

            if (avatarDrawable == null) {
                avatarDrawable = new AvatarDrawable(user);
            }
            oldAvatarView.setForUserOrChat(user.photo, avatarDrawable);
            oldPhotoCell.addView(oldAvatarView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
            oldPhotoCell.setText(LocaleController.getString(R.string.ResetToOriginalPhoto), false);
            oldPhotoCell.getImageView().setVisibility(View.VISIBLE);
            oldPhotoCell.setBackground(Theme.getSelectorDrawable(true, resourcesProvider));
            oldPhotoCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            oldPhotoCell.setOnClickListener(v -> {
                AlertsCreator.createSimpleAlert(context,
                        LocaleController.getString(R.string.ResetToOriginalPhotoTitle),
                        LocaleController.formatString(R.string.ResetToOriginalPhotoMessage, user.first_name),
                        LocaleController.getString(R.string.Reset), () -> {
                            avatar = null;
                            sendPhotoChangedRequest(null, null,null, null, null, 0, TYPE_SET);

                            TLRPC.User user1 = getMessagesController().getUser(user_id);
                            user1.photo.personal = false;
                            TLRPC.UserFull fullUser = MessagesController.getInstance(currentAccount).getUserFull(user_id);
                            if (fullUser != null) {
                                fullUser.personal_photo = null;
                                fullUser.flags &= ~2097152;
                                getMessagesStorage().updateUserInfo(fullUser, true);
                            }
                            if (prevAvatar != null) {
                                user1.photo.photo_id = prevAvatar.id;
                                ArrayList<TLRPC.PhotoSize> sizes = prevAvatar.sizes;
                                TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 100);
                                TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 1000);

                                if (smallSize2 != null) {
                                    user1.photo.photo_small = smallSize2.location;
                                }
                                if (bigSize2 != null) {
                                    user1.photo.photo_big = bigSize2.location;
                                }
                            } else {
                                user1.photo = null;
                                user1.flags &= ~32;
                            }
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(user);
                            getMessagesStorage().putUsersAndChats(users, null, false, true);
                            updateCustomPhotoInfo();
                            getNotificationCenter().postNotificationName(NotificationCenter.reloadDialogPhotos);
                            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                        }, resourcesProvider).show();
            });

            suggestBirthday = new TextCell(context, resourcesProvider);
            suggestBirthday.setTextAndIcon(LocaleController.formatString(R.string.UserSuggestBirthday), R.drawable.menu_birthday, false);
            suggestBirthday.setBackground(Theme.getSelectorDrawable(true, resourcesProvider));
            suggestBirthday.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            suggestBirthday.setNeedDivider(true);
            suggestBirthday.imageView.setTranslationX(dp(4));
            suggestBirthday.setOnClickListener(v -> {
                showDialog(AlertsCreator.createBirthdayPickerDialog(
                    getContext(),
                    formatString(R.string.UserSuggestBirthdayTitle, UserObject.getForcedFirstName(user)),
                    getString(R.string.UserSuggestBirthdayButton),
                    null,
                    birthday -> {
                        final TLRPC.TL_users_suggestBirthday req = new TLRPC.TL_users_suggestBirthday();
                        req.id = getMessagesController().getInputUser(user_id);
                        req.birthday = birthday;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            presentFragment(ChatActivity.of(user_id), true);
                        }));
                    },
                    null,
                    false, resourcesProvider
                ).create());
            });

//            getMessagesController().loadDialogPhotos(user_id, 2, 0, true, getClassGuid(), null);
            TLRPC.UserFull userFull = getMessagesController().getUserFull(user_id);
            if (userFull != null) {
                prevAvatar = userFull.profile_photo;
                if (prevAvatar == null) {
                    prevAvatar = userFull.fallback_photo;
                }
            }

            updateCustomPhotoInfo();
        }

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(frameLayout);
                }
            }
        });
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = frameLayout;
    }

    private boolean checkShare = true;

    private boolean firstSet = true;
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TLRPC.User user = getMessagesController().getUser(user_id);

        items.add(UItem.asCustom(infoLayout));
        items.add(UItem.asCustom(firstNameField));
        items.add(UItem.asCustom(lastNameField));

        if (TextUtils.isEmpty(getPhone())) {
            items.add(UItem.asShadow(AndroidUtilities.replaceCharSequence("%1$s", AndroidUtilities.replaceTags(getString(R.string.MobileHiddenExceptionInfo)), UserObject.getFirstName(user))));
        } else {
            if (needAddException) {
                items.add(UItem.asShadow(AndroidUtilities.replaceTags(formatString("MobileVisibleInfo", R.string.MobileVisibleInfo, UserObject.getFirstName(user)))));
            } else {
                items.add(UItem.asShadow(null));
            }
        }

        if (addContact && needAddException) {
            items.add(UItem.asCheck(2, getString(R.string.AddContactShareNumber)).setChecked(checkShare));
            items.add(UItem.asShadow(formatString(R.string.AddContactShareNumberInfo, UserObject.getFirstName(user))));
        }

        items.add(UItem.asCustom(noteField));
        items.add(UItem.asShadow(getString(R.string.AddNotesInfo)));

        if (!addContact) {
            final TLRPC.UserFull userInfo = getMessagesController().getUserFull(user_id);
            if (userInfo != null && userInfo.birthday == null) {
                items.add(UItem.asCustom(suggestBirthday));
            }
            items.add(UItem.asCustom(suggestPhoto));
            items.add(UItem.asCustom(setAvatarCell));
            if (user != null && user.photo != null && user.photo.personal) {
                items.add(UItem.asCustom(oldPhotoCell));
            }

            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(1, getString(R.string.DeleteContact)).red());
        }

        items.add(UItem.asShadow(null));

        if (firstSet) {
            AndroidUtilities.runOnUIThread(() -> {
                if (user != null && firstNameFromCard == null && lastNameFromCard == null) {
                    if (user.phone == null) {
                        if (phone != null) {
                            user.phone = PhoneFormat.stripExceptNumbers(phone);
                        }
                    }
                    firstNameField.setText(user.first_name);
                    firstNameField.editText.setSelection(firstNameField.editText.length());
                    lastNameField.setText(user.last_name);
                }

                final TLRPC.UserFull userInfo = getMessagesController().getUserFull(user_id);
                if (userInfo != null) {
                    if (userInfo.note != null) {
                        noteField.setText(userInfo.note);
                    } else {
                        noteField.setText("");
                    }
                }

                if (focusNotes) {
                    noteField.editText.requestFocus();
                    AndroidUtilities.showKeyboard(noteField.editText);
                }
            });
            firstSet = false;

            AndroidUtilities.runOnUIThread(() -> {
                if (focusNotes) {
                    noteField.editText.requestFocus();
                    AndroidUtilities.showKeyboard(noteField.editText);
                }
            }, 200);
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            final TLRPC.User user = getMessagesController().getUser(user_id);
            if (user == null || getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.DeleteContact))
                .setMessage(LocaleController.getString(R.string.AreYouSureDeleteContact))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialogInterface, i) -> {
                    final ArrayList<TLRPC.User> arrayList = new ArrayList<>();
                    arrayList.add(user);
                    getContactsController().deleteContact(arrayList, true);
                    if (user != null) {
                        user.contact = false;
                    }
                    finishFragment();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .makeRed(DialogInterface.BUTTON_POSITIVE)
                .show();
        } else if (item.id == 2) {
            checkShare = !checkShare;
            ((TextCheckCell) view).setChecked(checkShare);
        }
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarProgressView == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setVisibility(View.VISIBLE);
                avatarAnimation.playTogether(
                    ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f),
                    ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 1.0f)
                );
            } else {
                avatarAnimation.playTogether(
                    ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 0.0f)
                );
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarProgressView == null) {
                        return;
                    }
                    if (!show) {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                        avatarOverlay.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setAlpha(1.0f);
                avatarOverlay.setVisibility(View.VISIBLE);
            } else {
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
                avatarOverlay.setAlpha(0.0f);
                avatarOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void setDelegate(ContactAddActivityDelegate contactAddActivityDelegate) {
        delegate = contactAddActivityDelegate;
    }

    private void updateAvatarLayout() {
        if (nameTextView == null) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(user_id);
        if (user == null) {
            return;
        }
        if (TextUtils.isEmpty(getPhone())) {
            nameTextView.setText(getString(R.string.MobileHidden));
        } else {
            nameTextView.setText(PhoneFormat.getInstance().format("+" + getPhone()));
        }
        onlineTextView.setText(LocaleController.formatUserStatus(currentAccount, user));
        if (avatar == null) {
            avatarImage.setForUserOrChat(user, avatarDrawable = new AvatarDrawable(user));
        }
    }

    private String getPhone() {
        TLRPC.User user = getMessagesController().getUser(user_id);
        return user != null && !TextUtils.isEmpty(user.phone) ? user.phone : phone;
    }

    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateAvatarLayout();
            }
        } else if (id == NotificationCenter.dialogPhotosUpdate) {
            MessagesController.DialogPhotos dialogPhotos = (MessagesController.DialogPhotos) args[0];
            if (dialogPhotos == this.dialogPhotos) {
                ArrayList<TLRPC.Photo> photos = new ArrayList<>(dialogPhotos.photos);
                for (int i = 0; i < photos.size(); i++) {
                    if (photos.get(i) == null) {
                        photos.remove(i);
                        i--;
                    }
                }
                for (int i = 0; i < photos.size(); ++i) {
                    prevAvatar = photos.get(i);
                    updateCustomPhotoInfo();
                    break;
                }
            }
        }
    }

    private void updateCustomPhotoInfo() {
        if (addContact) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(user_id);
        if (user.photo != null && user.photo.personal) {
            if (prevAvatar != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(prevAvatar.sizes, 1000);
                ImageLocation location = ImageLocation.getForPhoto(photoSize, prevAvatar);
                oldAvatarView.setImage(location, "50_50", avatarDrawable, null);
            }
        }
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable(user);
        }
        if (avatar == null) {
            avatarImage.setForUserOrChat(user, avatarDrawable);
        } else {
            avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, getMessagesController().getUser(user_id));
        }
    }

    boolean paused;
    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        imageUpdater.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAvatarLayout();
        imageUpdater.onResume();
    }


    MessageObject suggestPhotoMessageFinal;

    @Override
    public boolean canFinishFragment() {
        return photoSelectedTypeFinal != TYPE_SUGGEST;
    }

    @Override
    public void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            if (imageUpdater.isCanceled()) {
                return;
            }
            if (photoSelectedTypeFinal == TYPE_SET) {
                avatar = smallSize.location;
            } else {
                if (photoSelectedTypeFinal == TYPE_SUGGEST) {
                    NavigationExt.backToFragment(ContactAddActivity.this, fragment -> {
                        if (fragment instanceof ChatActivity) {
                            ChatActivity chatActivity = (ChatActivity) fragment;
                            if (chatActivity.getDialogId() == user_id && chatActivity.getChatMode() == 0) {
                                chatActivity.scrollToLastMessage(true, false);
                                return true;
                            }
                        }
                        return false;
                    });
                }
            }
            if (photo != null || video != null) {
                TLRPC.User user = getMessagesController().getUser(user_id);
                if (suggestPhotoMessageFinal == null && user != null) {
                    PhotoUtilities.applyPhotoToUser(smallSize, bigSize, video != null, user, true);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user);
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    getNotificationCenter().postNotificationName(NotificationCenter.reloadDialogPhotos);
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                }
                sendPhotoChangedRequest(avatar, bigSize.location, photo, video, emojiMarkup, videoStartTimestamp, photoSelectedTypeFinal);
                showAvatarProgress(false, true);
            } else {
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, getMessagesController().getUser(user_id));
                if (photoSelectedTypeFinal == TYPE_SET) {
                    showAvatarProgress(true, false);
                } else {
                    createServiceMessageLocal(smallSize, bigSize, isVideo);
                }
            }
            updateCustomPhotoInfo();
        });
    }

    @Override
    public void didUploadFailed() {
        AndroidUtilities.runOnUIThread(() -> {
            ImageUpdater.ImageUpdaterDelegate.super.didUploadFailed();
            if (suggestPhotoMessageFinal != null) {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(suggestPhotoMessageFinal.getId());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDeleted, ids, 0L, false);
            }
        });
    }

    private void createServiceMessageLocal(TLRPC.PhotoSize smallSize, TLRPC.PhotoSize bigSize, boolean video) {

        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.random_id = SendMessagesHelper.getInstance(currentAccount).getNextRandomId();
        message.dialog_id = user_id;
        message.unread = true;
        message.out = true;
        message.local_id = message.id = getUserConfig().getNewMessageId();
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = getUserConfig().getClientUserId();
        message.flags |= 256;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = user_id;
        message.date = getConnectionsManager().getCurrentTime();
        TLRPC.TL_messageActionSuggestProfilePhoto suggestProfilePhoto = new TLRPC.TL_messageActionSuggestProfilePhoto();
        message.action = suggestProfilePhoto;
        suggestProfilePhoto.photo = new TLRPC.TL_photo();
        suggestProfilePhoto.photo.sizes.add(smallSize);
        suggestProfilePhoto.photo.sizes.add(bigSize);

        suggestProfilePhoto.video = video;
        suggestProfilePhoto.photo.file_reference = new byte[0];

        ArrayList<MessageObject> objArr = new ArrayList<>();
        objArr.add(suggestPhotoMessageFinal = new MessageObject(currentAccount, message, false, false));
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(message);
      //  MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0, false, 0);
        MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(user_id, objArr, 0);


        getMessagesController().photoSuggestion.put(message.local_id, imageUpdater);
    }

    private void sendPhotoChangedRequest(TLRPC.FileLocation avatar, TLRPC.FileLocation bigAvatar, TLRPC.InputFile photo, TLRPC.InputFile video, TLRPC.VideoSize emojiMarkup, double videoStartTimestamp, int photoSelectedTypeFinal) {
        TLRPC.TL_photos_uploadContactProfilePhoto req = new TLRPC.TL_photos_uploadContactProfilePhoto();
        req.user_id = getMessagesController().getInputUser(user_id);

        if (photo != null) {
            req.file = photo;
            req.flags |= 1;
        }
        if (video != null) {
            req.video = video;
            req.flags |= 2;
            req.video_start_ts = videoStartTimestamp;
            req.flags |= 4;
        }
        if (emojiMarkup != null) {
            req.flags |= 32;
            req.video_emoji_markup = emojiMarkup;
        }
        if (photoSelectedTypeFinal == TYPE_SUGGEST) {
            req.suggest = true;
            req.flags |= 8;
        } else {
            req.save = true;
            req.flags |= 16;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (suggestPhotoMessageFinal != null) {
                return;
            }
            if (avatar == null && video == null) {
                return;
            }
            if (response != null) {
                TLRPC.TL_photos_photo photo2 = (TLRPC.TL_photos_photo) response;
                ArrayList<TLRPC.PhotoSize> sizes = photo2.photo.sizes;

                TLRPC.User user = getMessagesController().getUser(user_id);
                TLRPC.UserFull fullUser = MessagesController.getInstance(currentAccount).getUserFull(user_id);
                if (fullUser != null) {
                    fullUser.personal_photo = photo2.photo;
                    fullUser.flags |= 2097152;
                    getMessagesStorage().updateUserInfo(fullUser, true);
                }
                if (user != null) {
                    TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 100);
                    TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 1000);
                    if (smallSize2 != null && avatar != null) {
                        File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize2, true);
                        File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true);
                        src.renameTo(destFile);
                        String oldKey = avatar.volume_id + "_" + avatar.local_id + "@50_50";
                        String newKey = smallSize2.location.volume_id + "_" + smallSize2.location.local_id + "@50_50";
                        ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), false);
                    }

                    if (bigSize2 != null && bigAvatar != null) {
                        File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize2, true);
                        File src = FileLoader.getInstance(currentAccount).getPathToAttach(bigAvatar, true);
                        src.renameTo(destFile);
                    }
                    PhotoUtilities.applyPhotoToUser(photo2.photo, user, true);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user);
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    getMessagesController().getDialogPhotos(user_id).addPhotoAtStart(photo2.photo);

                    getNotificationCenter().postNotificationName(NotificationCenter.reloadDialogPhotos);
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                    if (getParentActivity() != null) {
                        if (photoSelectedTypeFinal == TYPE_SET) {
                            BulletinFactory.of(this).createUsersBulletin(users, AndroidUtilities.replaceTags(formatString("UserCustomPhotoSeted", R.string.UserCustomPhotoSeted, user.first_name))).show();
                        } else {
                            BulletinFactory.of(this).createUsersBulletin(users, AndroidUtilities.replaceTags(formatString("UserCustomPhotoSeted", R.string.UserCustomPhotoSeted, user.first_name))).show();
                        }
                    }
                }
                this.avatar = null;
                updateCustomPhotoInfo();
            }
        }));
    }

    @Override
    public String getInitialSearchString() {
        return ImageUpdater.ImageUpdaterDelegate.super.getInitialSearchString();
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
    }

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        if (avatarProgressView == null) {
            return;
        }
        photoSelectedTypeFinal = photoSelectedType;
        avatarProgressView.setProgress(0.0f);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (avatarImage != null) {
                TLRPC.User user = getMessagesController().getUser(user_id);
                if (user == null) {
                    return;
                }
                avatarDrawable.setInfo(currentAccount, user);
                avatarImage.invalidate();
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(onlineTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

//        themeDescriptions.add(new ThemeDescription(infoTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }

    public static class PhotoSuggestion {

        TLRPC.FileLocation smallLocation;
        TLRPC.FileLocation bigLocation;

        public PhotoSuggestion(TLRPC.FileLocation smallLocation, TLRPC.FileLocation bigLocation) {
            this.smallLocation = smallLocation;
            this.bigLocation = bigLocation;
        }
    }
}
