/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class PrivacyControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private ListAdapter listAdapter;
    private View doneButton;
    private RecyclerListView listView;
    private MessageCell messageCell;

    private int initialRulesType;
    private int initialRulesSubType;
    private ArrayList<Long> initialPlus = new ArrayList<>();
    private ArrayList<Long> initialMinus = new ArrayList<>();
    private final boolean[] initialPlusPremium = new boolean[2];
    private final boolean[] initialPlusMiniapps = new boolean[3];

    private int rulesType;
    private final boolean[] currentPlusPremium = new boolean[2];
    private final boolean[] currentPlusMiniapps = new boolean[3];
    private ArrayList<Long> currentPlus;
    private ArrayList<Long> currentMinus;

    private int currentType;
    private int currentSubType;

    private boolean prevSubtypeContacts;

    private int setBirthdayRow;
    private int messageRow;
    private int sectionRow;
    private int everybodyRow;
    private int myContactsRow;
    private int nobodyRow;
    private int detailRow;
    private int shareSectionRow;
    private int alwaysShareRow;
    private int neverShareRow;
    private int shareDetailRow;
    private int phoneSectionRow;
    private int phoneEverybodyRow;
    private int phoneContactsRow;
    private int phoneDetailRow;
    private int photoForRestRow;
    private int currentPhotoForRestRow;
    private int photoForRestDescriptionRow;
    private int p2pSectionRow;
    private int p2pRow;
    private int p2pDetailRow;
    private int readRow;
    private int readDetailRow;
    private int readPremiumRow;
    private int readPremiumDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    public final static int PRIVACY_RULES_TYPE_LASTSEEN = 0;
    public final static int PRIVACY_RULES_TYPE_INVITE = 1;
    public final static int PRIVACY_RULES_TYPE_CALLS = 2;
    public final static int PRIVACY_RULES_TYPE_P2P = 3;
    public final static int PRIVACY_RULES_TYPE_PHOTO = 4;
    public final static int PRIVACY_RULES_TYPE_FORWARDS = 5;
    public final static int PRIVACY_RULES_TYPE_PHONE = 6;
    public final static int PRIVACY_RULES_TYPE_ADDED_BY_PHONE = 7;
    public final static int PRIVACY_RULES_TYPE_VOICE_MESSAGES = 8;
    public final static int PRIVACY_RULES_TYPE_BIO = 9;
    public final static int PRIVACY_RULES_TYPE_MESSAGES = 10;
    public final static int PRIVACY_RULES_TYPE_BIRTHDAY = 11;
    public final static int PRIVACY_RULES_TYPE_GIFTS = 12;

    public final static int TYPE_EVERYBODY = 0;
    public final static int TYPE_NOBODY = 1;
    public final static int TYPE_CONTACTS = 2;
    ImageUpdater imageUpdater;
    private RLottieDrawable cameraDrawable;
    private TextCell setAvatarCell;
    private BackupImageView oldAvatarView;
    private TextCell oldPhotoCell;
    private TLRPC.PhotoSize avatarForRest;
    private TLRPC.Photo avatarForRestPhoto;

    private boolean currentReadValue;
    private boolean selectedReadValue;

    @Override
    public void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            avatarForRest = smallSize;
            avatarForRestPhoto = null;
            updateAvatarForRestInfo();
            if (photo != null || video != null) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
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
                    req.video_emoji_markup = emojiMarkup;
                    req.flags |= 16;
                }
                req.fallback = true;
                req.flags |= 8;


                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        TLRPC.TL_photos_photo photo2 = (TLRPC.TL_photos_photo) response;
                        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().clientUserId);
                        userFull.flags |= 4194304;
                        userFull.fallback_photo = photo2.photo;
                        getMessagesStorage().updateUserInfo(userFull, true);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadDialogPhotos);

                        TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(photo2.photo.sizes, 100);
                            TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(photo2.photo.sizes, 1000);
                            if (smallSize2 != null && avatarForRest != null) {
                                File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize2, true);
                                File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatarForRest, true);
                                src.renameTo(destFile);
                                String oldKey = avatarForRest.location.volume_id + "_" + avatarForRest.location.local_id + "@50_50";
                                String newKey = smallSize2.location.volume_id + "_" + smallSize2.location.local_id + "@50_50";
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForLocal(smallSize2.location), false);
                            }

                            if (bigSize2 != null && avatarForRest != null) {
                                File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize2, true);
                                File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatarForRest.location, true);
                                src.renameTo(destFile);
                            }
                    }
                }));

                TLRPC.User user = new TLRPC.TL_user();
                user.photo = new TLRPC.TL_userProfilePhoto();
                user.photo.photo_small = smallSize.location;
                user.photo.photo_big = bigSize.location;
                user.first_name = getUserConfig().getCurrentUser().first_name;
                user.last_name = getUserConfig().getCurrentUser().last_name;
                user.access_hash = getUserConfig().getCurrentUser().access_hash;
                BulletinFactory.of(this).createUsersBulletin(Collections.singletonList(user), LocaleController.getString(R.string.PhotoForRestTooltip)).show();
            }
            updateRows(false);
        });
    }

    private void updateAvatarForRestInfo() {
        if (setAvatarCell != null) {
            if (avatarForRest == null) {
                setAvatarCell.getTextView().setText(LocaleController.formatString("SetPhotoForRest", R.string.SetPhotoForRest));
                setAvatarCell.setNeedDivider(false);
            } else {
                setAvatarCell.getTextView().setText(LocaleController.formatString("UpdatePhotoForRest", R.string.UpdatePhotoForRest));
                setAvatarCell.setNeedDivider(true);
            }
        }
        if (oldAvatarView != null && avatarForRest != null) {
            if (avatarForRestPhoto != null) {
                oldAvatarView.setImage(ImageLocation.getForPhoto(avatarForRest, avatarForRestPhoto), "50_50", (Drawable) null, UserConfig.getInstance(currentAccount).getCurrentUser());
            } else {
                oldAvatarView.setImage(ImageLocation.getForLocal(avatarForRest.location), "50_50", (Drawable) null, UserConfig.getInstance(currentAccount).getCurrentUser());
            }
        }
    }

    @Override
    public void didStartUpload(boolean isVideo) {

    }

    private class MessageCell extends FrameLayout {

        private final Runnable invalidateRunnable = this::invalidate;

        private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;

        private ChatMessageCell cell;
        private Drawable backgroundDrawable;
        private Drawable shadowDrawable;
        private HintView hintView;
        private MessageObject messageObject;

        public MessageCell(Context context) {
            super(context);

            setWillNotDraw(false);
            setClipToPadding(false);

            shadowDrawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
            setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());

            TLRPC.Message message = new TLRPC.TL_message();
            message.message = LocaleController.getString(R.string.PrivacyForwardsMessageLine);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + TLRPC.MESSAGE_FLAG_FWD;
            message.from_id = new TLRPC.TL_peerUser();
            message.id = 1;
            message.fwd_from = new TLRPC.TL_messageFwdHeader();
            message.fwd_from.from_name = ContactsController.formatName(currentUser.first_name, currentUser.last_name);
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            messageObject = new MessageObject(currentAccount, message, true, false);
            messageObject.eventId = 1;
            messageObject.resetLayout();

            cell = new ChatMessageCell(context, currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

            });
            cell.isChat = false;
            cell.setFullyDraw(true);
            cell.setMessageObject(messageObject, null, false, false);
            addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            hintView = new HintView(context, 1, true);
            addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            hintView.showForMessageCell(cell, false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
            if (newDrawable != null && backgroundDrawable != newDrawable) {
                if (backgroundGradientDisposable != null) {
                    backgroundGradientDisposable.dispose();
                    backgroundGradientDisposable = null;
                }
                backgroundDrawable = newDrawable;
            }
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable || backgroundDrawable instanceof MotionBackgroundDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                    backgroundGradientDisposable = ((BackgroundGradientDrawable) backgroundDrawable).drawExactBoundsSize(canvas, this);
                } else {
                    backgroundDrawable.draw(canvas);
                }
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    backgroundDrawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                } else {
                    int viewHeight = getMeasuredHeight();
                    float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (viewHeight - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, width, getMeasuredHeight());
                    backgroundDrawable.setBounds(x, y, x + width, y + height);
                }
                backgroundDrawable.draw(canvas);
                canvas.restore();
            } else {
                super.onDraw(canvas);
            }

            shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            shadowDrawable.draw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        protected void dispatchSetPressed(boolean pressed) {

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            cell.invalidate();
        }
    }

    public PrivacyControlActivity(int type) {
        this(type, false);
    }

    public PrivacyControlActivity(int type, boolean load) {
        super();
        rulesType = type;
        if (load) {
            ContactsController.getInstance(currentAccount).loadPrivacySettings();
        }
        if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, true);
            imageUpdater.parentFragment = this;
            imageUpdater.setDelegate(this);
            TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().clientUserId);
            if (UserObject.hasFallbackPhoto(userFull)) {
                TLRPC.PhotoSize smallSize = FileLoader.getClosestPhotoSizeWithSize(userFull.fallback_photo.sizes, 1000);
                if (smallSize != null) {
                    avatarForRest = smallSize;
                    avatarForRestPhoto = userFull.fallback_photo;
                }
            }
        }
    }


    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        checkPrivacy();
        updateRows(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.privacyRulesUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.privacyRulesUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    private int shakeDp = 4;

    @Override
    public View createView(Context context) {
        if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            messageCell = new MessageCell(context);
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyPhone));
        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyForwards));
        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyProfilePhoto));
        } else if (rulesType == PRIVACY_RULES_TYPE_BIO) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyBio));
        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyP2P));
        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            actionBar.setTitle(LocaleController.getString(R.string.Calls));
        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyInvites));
        } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyVoiceMessages));
        } else if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyLastSeen));
        } else if (rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyMessages));
        } else if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyBirthday));
        } else if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
            actionBar.setTitle(LocaleController.getString(R.string.PrivacyGifts));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        boolean hasChanges = hasChanges();
        doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
        doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
        doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        doneButton.setEnabled(hasChanges);

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                drawSectionBackground(canvas, shareSectionRow, shareDetailRow - 1, getThemedColor(Theme.key_windowBackgroundWhite));
                super.dispatchDraw(canvas);
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == currentPhotoForRestRow) {
                AlertDialog alertDialog = AlertsCreator.createSimpleAlert(getContext(),
                        LocaleController.getString(R.string.RemovePublicPhoto),
                        LocaleController.getString(R.string.RemovePhotoForRestDescription),
                        LocaleController.getString(R.string.Remove),
                        () -> {
                            avatarForRest = null;
                            avatarForRestPhoto = null;
                            TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().clientUserId);
                            if (userFull == null || userFull.fallback_photo == null) {
                                return;
                            }
                            TLRPC.Photo photo = userFull.fallback_photo;
                            userFull.flags &= ~4194304;
                            userFull.fallback_photo = null;
                            getMessagesStorage().updateUserInfo(userFull, true);
                            updateAvatarForRestInfo();
                            updateRows(true);

                            TLRPC.TL_inputPhoto inputPhoto = new TLRPC.TL_inputPhoto();
                            inputPhoto.id = photo.id;
                            inputPhoto.access_hash = photo.access_hash;
                            inputPhoto.file_reference = photo.file_reference;
                            if (inputPhoto.file_reference == null) {
                                inputPhoto.file_reference = new byte[0];
                            }

                            MessagesController.getInstance(currentAccount).deleteUserPhoto(inputPhoto);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadDialogPhotos);
                        }, null).create();
                alertDialog.show();
                alertDialog.redPositive();

            } else if (position == photoForRestRow) {
                if (imageUpdater != null) {
                    imageUpdater.openMenu(false, () -> {

                    }, dialogInterface -> {
                        if (!imageUpdater.isUploadingImage()) {
                            cameraDrawable.setCustomEndFrame(86);
                            setAvatarCell.imageView.playAnimation();
                        } else {
                            cameraDrawable.setCurrentFrame(0, false);
                        }
                    }, 0);
                    cameraDrawable.setCurrentFrame(0);
                    cameraDrawable.setCustomEndFrame(43);
                    setAvatarCell.imageView.playAnimation();
                }
            } else if (rulesType == PRIVACY_RULES_TYPE_MESSAGES && position == myContactsRow && !getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getUserConfig().isPremium()) {
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.star_premium_2,
                    LocaleController.getString(R.string.OptionPremiumRequiredTitle),
                    AndroidUtilities.replaceTags(LocaleController.getString(R.string.OptionPremiumRequiredMessage)),
                    LocaleController.getString(R.string.OptionPremiumRequiredButton),
                    () -> {
                        presentFragment(new PremiumPreviewFragment("noncontacts"));
                    }
                ).show();
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(view, shakeDp = -shakeDp);
            } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES && (position == myContactsRow || position == nobodyRow) && !getUserConfig().isPremium()) {
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.star_premium_2,
                        LocaleController.getString(R.string.OptionPremiumRequiredTitle),
                        AndroidUtilities.replaceTags(LocaleController.getString(R.string.OptionPremiumRequiredMessage)),
                        LocaleController.getString(R.string.OptionPremiumRequiredButton),
                        () -> {
                            presentFragment(new PremiumPreviewFragment("settings"));
                        }
                ).show();
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(view, shakeDp = -shakeDp);
            } else if (position == nobodyRow || position == everybodyRow || position == myContactsRow) {
                int newType;
                if (position == nobodyRow) {
                    newType = TYPE_NOBODY;
                } else if (position == everybodyRow) {
                    newType = TYPE_EVERYBODY;
                } else {
                    newType = TYPE_CONTACTS;
                }
                if (newType == currentType) {
                    return;
                }
                currentType = newType;
                Bulletin.hideVisible();
                updateDoneButton();
                updateRows(true);
            } else if (position == phoneContactsRow || position == phoneEverybodyRow) {
                int newType;
                if (position == phoneEverybodyRow) {
                    newType = 0;
                } else {
                    newType = 1;
                }
                if (newType == currentSubType) {
                    return;
                }
                currentSubType = newType;
                updateDoneButton();
                updateRows(true);
            } else if (position == neverShareRow || position == alwaysShareRow) {
                ArrayList<Long> createFromArray;
                if (position == neverShareRow) {
                    createFromArray = currentMinus;
                } else {
                    createFromArray = currentPlus;
                }
                Bundle args = new Bundle();
                args.putBoolean(position == neverShareRow ? "isNeverShare" : "isAlwaysShare", true);
                args.putInt("chatAddType", rulesType != PRIVACY_RULES_TYPE_LASTSEEN ? 1 : 0);
                if (position == alwaysShareRow && rulesType == PRIVACY_RULES_TYPE_INVITE) {
                    args.putBoolean("allowPremium", true);
                }
                final boolean allowMiniapps;
                if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
                    if (currentType == TYPE_NOBODY) {
                        allowMiniapps = (position == alwaysShareRow);
                    } else if (currentType == TYPE_CONTACTS) {
                        allowMiniapps = (position == alwaysShareRow);
                    } else if (currentType == TYPE_EVERYBODY) {
                        allowMiniapps = (position == neverShareRow);
                    } else allowMiniapps = false;
                } else allowMiniapps = false;
                args.putBoolean("allowMiniapps", allowMiniapps);
                GroupCreateActivity fragment = new GroupCreateActivity(args);
                fragment.select(createFromArray, position == alwaysShareRow && currentPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1], allowMiniapps && currentPlusMiniapps[currentType]);
                fragment.setDelegate((premium, miniapps, ids) -> {
                    if (position == neverShareRow) {
                        currentMinus = ids;
                        currentPlusMiniapps[currentType] = allowMiniapps && miniapps;
                        for (int a = 0; a < currentMinus.size(); a++) {
                            currentPlus.remove(currentMinus.get(a));
                        }
                    } else {
                        currentPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1] = premium;
                        currentPlusMiniapps[currentType] = allowMiniapps && miniapps;
                        currentPlus = ids;
                        for (int a = 0; a < currentPlus.size(); a++) {
                            currentMinus.remove(currentPlus.get(a));
                        }
                    }
                    updateDoneButton();
                    listAdapter.notifyDataSetChanged();
                });
                presentFragment(fragment);
            } else if (position == p2pRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_P2P));
            } else if (position == readRow) {
                selectedReadValue = !selectedReadValue;
                updateDoneButton();
                ((TextCheckCell) view).setChecked(selectedReadValue);
            } else if (position == readPremiumRow) {
                presentFragment(new PremiumPreviewFragment("lastseen"));
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                listView.invalidate();
            }
        };
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);

        setMessageText();

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            checkPrivacy();
        } else if (id == NotificationCenter.emojiLoaded) {
            listView.invalidateViews();
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (messageCell != null) {
                messageCell.invalidate();
            }
        }
    }

    private void updateDoneButton() {
        boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
    }

    private void applyCurrentPrivacySettings() {
        if (rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
            TLRPC.TL_account_setGlobalPrivacySettings req2 = new TLRPC.TL_account_setGlobalPrivacySettings();
            req2.settings = new TLRPC.TL_globalPrivacySettings();
            TLRPC.TL_globalPrivacySettings settings = getContactsController().getGlobalPrivacySettings();
            if (settings != null) {
                req2.settings.archive_and_mute_new_noncontact_peers = settings.archive_and_mute_new_noncontact_peers;
                req2.settings.keep_archived_folders = settings.keep_archived_folders;
                req2.settings.keep_archived_unmuted = settings.keep_archived_unmuted;
                req2.settings.hide_read_marks = settings.hide_read_marks;
            }
            req2.settings.new_noncontact_peers_require_premium = currentType == TYPE_CONTACTS;
            getConnectionsManager().sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    showErrorAlert();
                } else {
                    if (settings != null) {
                        settings.new_noncontact_peers_require_premium = req2.settings.new_noncontact_peers_require_premium;
                    }
                    finishFragment();
                    getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
                }
            }));
            return;
        }
        TLRPC.TL_account_setPrivacy req = new TLRPC.TL_account_setPrivacy();
        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneNumber();
            if (currentType == TYPE_NOBODY) {
                TLRPC.TL_account_setPrivacy req2 = new TLRPC.TL_account_setPrivacy();
                req2.key = new TLRPC.TL_inputPrivacyKeyAddedByPhone();
                if (currentSubType == 0) {
                    req2.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                } else {
                    req2.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                        ContactsController.getInstance(currentAccount).setPrivacyRules(privacyRules.rules, PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            req.key = new TLRPC.TL_inputPrivacyKeyForwards();
        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            req.key = new TLRPC.TL_inputPrivacyKeyProfilePhoto();
        } else if (rulesType == PRIVACY_RULES_TYPE_BIO) {
            req.key = new TLRPC.TL_inputPrivacyKeyAbout();
        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneP2P();
        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
        } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES) {
            req.key = new TLRPC.TL_inputPrivacyKeyVoiceMessages();
        } else if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
            req.key = new TLRPC.TL_inputPrivacyKeyBirthday();
        } else if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
            req.key = new TLRPC.TL_inputPrivacyKeyStarGiftsAutoSave();
        } else {
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        }
        if (currentType != 0 && currentPlus.size() > 0) {
            TLRPC.TL_inputPrivacyValueAllowUsers usersRule = new TLRPC.TL_inputPrivacyValueAllowUsers();
            TLRPC.TL_inputPrivacyValueAllowChatParticipants chatsRule = new TLRPC.TL_inputPrivacyValueAllowChatParticipants();
            for (int a = 0; a < currentPlus.size(); a++) {
                long id = currentPlus.get(a);
                if (DialogObject.isUserDialog(id)) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(id);
                    if (user != null) {
                        TLRPC.InputUser inputUser = MessagesController.getInstance(currentAccount).getInputUser(user);
                        if (inputUser != null) {
                            usersRule.users.add(inputUser);
                        }
                    }
                } else {
                    chatsRule.chats.add(-id);
                }
            }
            req.rules.add(usersRule);
            req.rules.add(chatsRule);
        }
        if (currentType != 1 && currentMinus.size() > 0) {
            TLRPC.TL_inputPrivacyValueDisallowUsers usersRule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            TLRPC.TL_inputPrivacyValueDisallowChatParticipants chatsRule = new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
            for (int a = 0; a < currentMinus.size(); a++) {
                long id = currentMinus.get(a);
                if (DialogObject.isUserDialog(id)) {
                    TLRPC.User user = getMessagesController().getUser(id);
                    if (user != null) {
                        TLRPC.InputUser inputUser = getMessagesController().getInputUser(user);
                        if (inputUser != null) {
                            usersRule.users.add(inputUser);
                        }
                    }
                } else {
                    chatsRule.chats.add(-id);
                }
            }
            req.rules.add(usersRule);
            req.rules.add(chatsRule);
        }
        if (currentType == TYPE_EVERYBODY) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        } else if (currentType == TYPE_NOBODY) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        } else if (currentType == TYPE_CONTACTS) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
        }
        if (currentType != 0 && currentPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1]) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowPremium());
        }
        if (currentPlusMiniapps[currentType]) {
            if (currentType == TYPE_EVERYBODY) {
                req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowBots());
            } else {
                req.rules.add(new TLRPC.TL_inputPrivacyValueAllowBots());
            }
        }
        AlertDialog progressDialog = null;
        if (getParentActivity() != null) {
            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setCanCancel(false);
            progressDialog.show();
        }
        final AlertDialog progressDialogFinal = progressDialog;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                if (progressDialogFinal != null) {
                    progressDialogFinal.dismiss();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (error == null) {
                TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                MessagesController.getInstance(currentAccount).putUsers(privacyRules.users, false);
                MessagesController.getInstance(currentAccount).putChats(privacyRules.chats, false);
                ContactsController.getInstance(currentAccount).setPrivacyRules(privacyRules.rules, rulesType);
                finishFragment();
            } else {
                showErrorAlert();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);

        if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN && selectedReadValue != currentReadValue) {
            TLRPC.TL_account_setGlobalPrivacySettings req2 = new TLRPC.TL_account_setGlobalPrivacySettings();
            req2.settings = new TLRPC.TL_globalPrivacySettings();
            TLRPC.TL_globalPrivacySettings settings = getContactsController().getGlobalPrivacySettings();
            req2.settings.archive_and_mute_new_noncontact_peers = settings.archive_and_mute_new_noncontact_peers;
            req2.settings.keep_archived_folders = settings.keep_archived_folders;
            req2.settings.keep_archived_unmuted = settings.keep_archived_unmuted;
            req2.settings.new_noncontact_peers_require_premium = settings.new_noncontact_peers_require_premium;
            req2.settings.hide_read_marks = selectedReadValue;
            getConnectionsManager().sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                settings.hide_read_marks = currentReadValue = req2.settings.hide_read_marks;
            }));
        }
    }

    private void showErrorAlert() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(LocaleController.getString(R.string.PrivacyFloodControlError));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void checkPrivacy() {
        if (rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
            TLRPC.TL_globalPrivacySettings settings = ContactsController.getInstance(currentAccount).getGlobalPrivacySettings();
            initialRulesType = currentType = settings != null && settings.new_noncontact_peers_require_premium ? TYPE_CONTACTS : TYPE_EVERYBODY;
            currentMinus = new ArrayList<>();
            currentPlus = new ArrayList<>();
            return;
        }
        currentPlusPremium[0] = initialPlusPremium[0] = rulesType == PRIVACY_RULES_TYPE_INVITE;
        currentPlusPremium[1] = initialPlusPremium[1] = false;
        currentPlusMiniapps[TYPE_EVERYBODY] = initialPlusMiniapps[TYPE_EVERYBODY] = false;
        currentPlusMiniapps[TYPE_NOBODY] =    initialPlusMiniapps[TYPE_NOBODY] =    rulesType == PRIVACY_RULES_TYPE_GIFTS;
        currentPlusMiniapps[TYPE_CONTACTS] =  initialPlusMiniapps[TYPE_CONTACTS] =  false;
        currentPlus = new ArrayList<>();
        currentMinus = new ArrayList<>();
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(rulesType);
        if (privacyRules == null || privacyRules.size() == 0) {
            currentType = TYPE_NOBODY;
        } else {
            int type = -1;
            boolean premium = false;
            Boolean miniapps = null;
            boolean hadAllowContacts = false;
            for (int a = 0; a < privacyRules.size(); a++) {
                TLRPC.PrivacyRule rule = privacyRules.get(a);
                if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                    TLRPC.TL_privacyValueAllowChatParticipants privacyValueAllowChatParticipants = (TLRPC.TL_privacyValueAllowChatParticipants) rule;
                    for (int b = 0, N = privacyValueAllowChatParticipants.chats.size(); b < N; b++) {
                        currentPlus.add(-privacyValueAllowChatParticipants.chats.get(b));
                    }
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                    TLRPC.TL_privacyValueDisallowChatParticipants privacyValueDisallowChatParticipants = (TLRPC.TL_privacyValueDisallowChatParticipants) rule;
                    for (int b = 0, N = privacyValueDisallowChatParticipants.chats.size(); b < N; b++) {
                        currentMinus.add(-privacyValueDisallowChatParticipants.chats.get(b));
                    }
                } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                    TLRPC.TL_privacyValueAllowUsers privacyValueAllowUsers = (TLRPC.TL_privacyValueAllowUsers) rule;
                    currentPlus.addAll(privacyValueAllowUsers.users);
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                    TLRPC.TL_privacyValueDisallowUsers privacyValueDisallowUsers = (TLRPC.TL_privacyValueDisallowUsers) rule;
                    currentMinus.addAll(privacyValueDisallowUsers.users);
                } else if (rule instanceof TLRPC.TL_privacyValueAllowPremium) {
                    premium = true;
                } else if (rule instanceof TLRPC.TL_privacyValueAllowBots) {
                    miniapps = true;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowBots) {
                    miniapps = false;
                } else if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    type = 0;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll && !hadAllowContacts) {
                    type = 1;
                } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                    hadAllowContacts = true;
                    type = 2;
                } else if (type == -1) {
                    if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                        type = 0;
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll && !hadAllowContacts) {
                        type = 1;
                    } else {
                        type = 2;
                    }
                }
            }
            if (type == TYPE_EVERYBODY || type == -1 && (currentMinus.size() > 0 || miniapps != null && !miniapps)) {
                currentType = TYPE_EVERYBODY;
            } else if (type == TYPE_CONTACTS || type == -1 && currentMinus.size() > 0 && currentPlus.size() > 0) {
                currentType = TYPE_CONTACTS;
            } else if (type == TYPE_NOBODY || type == -1 && (currentPlus.size() > 0 || miniapps != null && miniapps)) {
                currentType = TYPE_NOBODY;
            }
            int a = currentType == TYPE_CONTACTS ? 0 : 1;
            currentPlusPremium[a] = initialPlusPremium[a] = premium;
            currentPlusMiniapps[currentType] = initialPlusMiniapps[currentType] = miniapps != null;
            if (doneButton != null) {
                doneButton.setAlpha(0.0f);
                doneButton.setScaleX(0.0f);
                doneButton.setScaleY(0.0f);
                doneButton.setEnabled(false);
            }
        }
        initialPlus.clear();
        initialMinus.clear();
        initialRulesType = currentType;
        initialPlus.addAll(currentPlus);
        initialMinus.addAll(currentMinus);

        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
            if (privacyRules == null || privacyRules.size() == 0) {
                currentSubType = 0;
            } else {
                for (int a = 0; a < privacyRules.size(); a++) {
                    TLRPC.PrivacyRule rule = privacyRules.get(a);
                    if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                        currentSubType = 0;
                        break;
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                        currentSubType = 2;
                        break;
                    } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                        currentSubType = 1;
                        break;
                    }
                }
            }
            initialRulesSubType = currentSubType;
        }

        if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN) {
            TLRPC.TL_globalPrivacySettings privacySettings = getContactsController().getGlobalPrivacySettings();
            selectedReadValue = currentReadValue = privacySettings != null && privacySettings.hide_read_marks;
        }
        updateRows(false);
    }

    private boolean hasChanges() {
        if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN && (currentType != TYPE_EVERYBODY || currentMinus != null && !currentMinus.isEmpty()) && currentReadValue != selectedReadValue) {
            return true;
        }
        if (initialRulesType != currentType) {
            return true;
        }
        if (rulesType == PRIVACY_RULES_TYPE_PHONE && currentType == TYPE_NOBODY && initialRulesSubType != currentSubType) {
            return true;
        }
        if (currentType != 0 && initialPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1] != currentPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1]) {
            return true;
        }
        if (initialPlusMiniapps[currentType] != currentPlusMiniapps[currentType]) {
            return true;
        }
        if (initialMinus.size() != currentMinus.size()) {
            return true;
        }
        if (initialPlus.size() != currentPlus.size()) {
            return true;
        }
        Collections.sort(initialPlus);
        Collections.sort(currentPlus);
        if (!initialPlus.equals(currentPlus)) {
            return true;
        }
        Collections.sort(initialMinus);
        Collections.sort(currentMinus);
        if (!initialMinus.equals(currentMinus)) {
            return true;
        }
        return false;
    }

    private void updateRows(boolean animated) {
        DiffCallback diffCallback = null;
        if (animated) {
            diffCallback = new DiffCallback();
            diffCallback.fillPositions(diffCallback.oldPositionToItem);
            diffCallback.oldRowCount = rowCount;
        }
        photoForRestRow = -1;
        currentPhotoForRestRow = -1;
        photoForRestDescriptionRow = -1;
        messageRow = -1;
        setBirthdayRow = -1;
        phoneDetailRow = -1;
        phoneSectionRow = -1;
        phoneEverybodyRow = -1;
        phoneContactsRow = -1;
        alwaysShareRow = -1;
        neverShareRow = -1;
        p2pSectionRow = -1;
        p2pRow = -1;
        p2pDetailRow = -1;
        readRow = readDetailRow = -1;
        nobodyRow = -1;
        shareSectionRow = -1;
        shareDetailRow = -1;
        readPremiumRow = readPremiumDetailRow = -1;

        rowCount = 0;
        if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
            if (userFull != null && userFull.birthday == null) {
                setBirthdayRow = rowCount++;
            }
        }
        if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            messageRow = rowCount++;
        }
        sectionRow = rowCount++;
        everybodyRow = rowCount++;
        myContactsRow = rowCount++;
        if (
            rulesType == PRIVACY_RULES_TYPE_PHOTO ||
            rulesType == PRIVACY_RULES_TYPE_BIO ||
            rulesType == PRIVACY_RULES_TYPE_LASTSEEN ||
            rulesType == PRIVACY_RULES_TYPE_CALLS ||
            rulesType == PRIVACY_RULES_TYPE_P2P ||
            rulesType == PRIVACY_RULES_TYPE_FORWARDS ||
            rulesType == PRIVACY_RULES_TYPE_PHONE ||
            rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES ||
            rulesType == PRIVACY_RULES_TYPE_INVITE ||
            rulesType == PRIVACY_RULES_TYPE_BIRTHDAY ||
            rulesType == PRIVACY_RULES_TYPE_GIFTS
        ) {
            nobodyRow = rowCount++;
        }
        if (rulesType == PRIVACY_RULES_TYPE_PHONE && currentType == TYPE_NOBODY) {
            phoneDetailRow = rowCount++;
            phoneSectionRow = rowCount++;
            phoneEverybodyRow = rowCount++;
            phoneContactsRow = rowCount++;
        }
        detailRow = rowCount++;
        if (rulesType != PRIVACY_RULES_TYPE_MESSAGES && (rulesType != PRIVACY_RULES_TYPE_VOICE_MESSAGES || getUserConfig().isPremium())) {
            shareSectionRow = rowCount++;
            if (currentType == TYPE_NOBODY || currentType == TYPE_CONTACTS) {
                alwaysShareRow = rowCount++;
            }
            if (currentType == TYPE_EVERYBODY || currentType == TYPE_CONTACTS) {
                neverShareRow = rowCount++;
            }
            shareDetailRow = rowCount++;
            if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                p2pSectionRow = rowCount++;
                p2pRow = rowCount++;
                p2pDetailRow = rowCount++;
            }

            if (rulesType == PRIVACY_RULES_TYPE_PHOTO && (currentMinus.size() > 0 || currentType == TYPE_CONTACTS || currentType == TYPE_NOBODY)) {
                photoForRestRow = rowCount++;
                if (avatarForRest != null) {
                    currentPhotoForRestRow = rowCount++;
                }
                photoForRestDescriptionRow = rowCount++;
            }
            if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN && (currentType != TYPE_EVERYBODY || currentMinus != null && !currentMinus.isEmpty())) {
                readRow = rowCount++;
                readDetailRow = rowCount++;
            }
            if (rulesType == PRIVACY_RULES_TYPE_LASTSEEN && !getMessagesController().premiumFeaturesBlocked()) {
                readPremiumRow = rowCount++;
                readPremiumDetailRow = rowCount++;
            }
        }

        setMessageText();

        if (listAdapter != null) {
            if (animated) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (!(child instanceof RadioCell)) {
                        continue;
                    }
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                    if (holder == null) {
                        continue;
                    }
                    int position = holder.getAdapterPosition();
                    RadioCell radioCell = (RadioCell) child;
                    if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
                        int checkedType;
                        if (position == everybodyRow) {
                            checkedType = TYPE_EVERYBODY;
                        } else if (position == myContactsRow) {
                            checkedType = TYPE_CONTACTS;
                        } else {
                            checkedType = TYPE_NOBODY;
                        }
                        radioCell.setChecked(currentType == checkedType, true);
                    } else {
                        int checkedType;
                        if (position == phoneContactsRow) {
                            checkedType = 1;
                        } else {
                            checkedType = 0;
                        }
                        radioCell.setChecked(currentSubType == checkedType, true);
                    }
                }

                diffCallback.fillPositions(diffCallback.newPositionToItem);
                DiffUtil.calculateDiff(diffCallback).dispatchUpdatesTo(listAdapter);
                AndroidUtilities.updateVisibleRows(listView);
            } else {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setMessageText() {
        if (messageCell != null) {
            messageCell.messageObject.messageOwner.fwd_from.from_id = new TLRPC.TL_peerUser();
            if (currentType == TYPE_EVERYBODY) {
                messageCell.hintView.setOverrideText(LocaleController.getString(R.string.PrivacyForwardsEverybody));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 1;
            } else if (currentType == TYPE_NOBODY) {
                messageCell.hintView.setOverrideText(LocaleController.getString(R.string.PrivacyForwardsNobody));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 0;
            } else {
                messageCell.hintView.setOverrideText(LocaleController.getString(R.string.PrivacyForwardsContacts));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 1;
            }
            messageCell.cell.forceResetMessageObject();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows(false);

        if (imageUpdater != null) {
            imageUpdater.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (imageUpdater != null) {
            imageUpdater.onPause();
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private void processDone() {
        if (getParentActivity() == null) {
            return;
        }

        if (currentType != 0 && rulesType == PRIVACY_RULES_TYPE_LASTSEEN && !getUserConfig().isPremium()) {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean showed = preferences.getBoolean("privacyAlertShowed", false);
            if (!showed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                    builder.setMessage(LocaleController.getString(R.string.WhoCanAddMeInfo));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.CustomHelp));
                }
                builder.setTitle(LocaleController.getString(R.string.AppName));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                    applyCurrentPrivacySettings();
                    preferences.edit().putBoolean("privacyAlertShowed", true).commit();
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
                return;
            }
        }
        applyCurrentPrivacySettings();
    }

    private boolean checkDiscard() {
        if (doneButton.getAlpha() == 1.0f) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UserRestrictionsApplyChanges));
            builder.setMessage(LocaleController.getString(R.string.PrivacySettingsChangedAlert));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    @Override
    public boolean canBeginSlide() {
        return checkDiscard();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == nobodyRow || position == everybodyRow || position == myContactsRow || position == neverShareRow || position == alwaysShareRow ||
                    position == p2pRow && !ContactsController.getInstance(currentAccount).getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_P2P) ||
                    position == currentPhotoForRestRow || position == photoForRestDescriptionRow || position == photoForRestRow || position == readRow || position == readPremiumRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = messageCell;
                    break;
                case 5:
                default:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                case 6:
                    setAvatarCell = new TextCell(getContext());
                    if (avatarForRest == null) {
                        setAvatarCell.setTextAndIcon(LocaleController.formatString("SetPhotoForRest", R.string.SetPhotoForRest), R.drawable.msg_addphoto, false);
                    } else {
                        setAvatarCell.setTextAndIcon(LocaleController.formatString("UpdatePhotoForRest", R.string.UpdatePhotoForRest), R.drawable.msg_addphoto, true);
                    }
                    setAvatarCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50), AndroidUtilities.dp(50), false, null);
                    setAvatarCell.imageView.setTranslationX(-AndroidUtilities.dp(8));
                    setAvatarCell.imageView.setAnimation(cameraDrawable);
                    setAvatarCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = setAvatarCell;
                    break;
                case 7:
                    oldAvatarView = new BackupImageView(getContext());
                    oldPhotoCell = new TextCell(getContext()) {
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

                    if (avatarForRest != null) {
                        if (avatarForRestPhoto != null) {
                            oldAvatarView.setImage(ImageLocation.getForPhoto(avatarForRest, avatarForRestPhoto), "50_50", (Drawable) null, UserConfig.getInstance(currentAccount).getCurrentUser());
                        } else {
                            oldAvatarView.setImage(ImageLocation.getForLocal(avatarForRest.location), "50_50", (Drawable) null, UserConfig.getInstance(currentAccount).getCurrentUser());
                        }
                    }
                    oldPhotoCell.addView(oldAvatarView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
                    oldPhotoCell.setText(LocaleController.getString(R.string.RemovePublicPhoto), false);
                    oldPhotoCell.getImageView().setVisibility(View.VISIBLE);
                    oldPhotoCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    oldPhotoCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    oldPhotoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = oldPhotoCell;
                    break;
                case 8:
                    TextCheckCell textCheckCell = new TextCheckCell(mContext, resourceProvider);
                    textCheckCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = textCheckCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        private int getUsersCount(ArrayList<Long> arrayList) {
            int count = 0;
            for (int a = 0; a < arrayList.size(); a++) {
                long id = arrayList.get(a);
                if (id > 0) {
                    count++;
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-id);
                    if (chat != null) {
                        count += chat.participants_count;
                    }
                }
            }
            return count;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == alwaysShareRow) {
                        String value;
                        if (currentPlus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", getUsersCount(currentPlus));
                        } else {
                            value = LocaleController.getString(R.string.EmpryUsersPlaceholder);
                        }
                        if (currentPlusPremium[currentType == TYPE_CONTACTS ? 0 : 1]) {
                            if (currentPlus == null || currentPlus.isEmpty()) {
                                value = LocaleController.formatString(R.string.PrivacyPremium);
                            } else {
                                value = LocaleController.formatString(R.string.PrivacyPremiumAnd, value);
                            }
                        }
                        if (currentPlusMiniapps[currentType] && currentType != TYPE_EVERYBODY) {
                            if (currentPlus == null || currentPlus.isEmpty()) {
                                value = LocaleController.formatString(R.string.PrivacyValueBots);
                            } else {
                                value = LocaleController.formatString(R.string.PrivacyValueBotsAnd, value);
                            }
                        }
                        if (rulesType != PRIVACY_RULES_TYPE_LASTSEEN && rulesType != PRIVACY_RULES_TYPE_PHOTO && rulesType != PRIVACY_RULES_TYPE_BIO) {
                            textCell.setTextAndValue(LocaleController.getString(R.string.AlwaysAllow), value, neverShareRow != -1);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString(R.string.AlwaysShareWith), value, neverShareRow != -1);
                        }
                    } else if (position == neverShareRow) {
                        String value;
                        int count = 0;
                        if (currentMinus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", getUsersCount(currentMinus));
                        } else {
                            value = LocaleController.getString(R.string.EmpryUsersPlaceholder);
                        }
                        if (currentPlusMiniapps[currentType] && currentType == TYPE_EVERYBODY) {
                            if (currentMinus == null || currentMinus.isEmpty()) {
                                value = LocaleController.formatString(R.string.PrivacyValueBots);
                            } else {
                                value = LocaleController.formatString(R.string.PrivacyValueBotsAnd, value);
                            }
                        }
                        if (rulesType != PRIVACY_RULES_TYPE_LASTSEEN && rulesType != PRIVACY_RULES_TYPE_PHOTO && rulesType != PRIVACY_RULES_TYPE_BIO) {
                            textCell.setTextAndValue(LocaleController.getString(R.string.NeverAllow), value, false);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString(R.string.NeverShareWith), value, false);
                        }
                    } else if (position == p2pRow) {
                        String value;
                        if (ContactsController.getInstance(currentAccount).getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_P2P)) {
                            value = LocaleController.getString(R.string.Loading);
                        } else {
                            value = PrivacySettingsActivity.formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_P2P);
                        }
                        textCell.setTextAndValue(LocaleController.getString(R.string.PrivacyP2P2), value, false);
                    } else if (position == readPremiumRow) {
                        textCell.setText(LocaleController.getString(getUserConfig().isPremium() ? R.string.PrivacyLastSeenPremiumForPremium : R.string.PrivacyLastSeenPremium), false);
                        textCell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    int backgroundResId = 0;
                    if (position == detailRow && rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
                        privacyCell.setText(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.PrivacyMessagesInfo), () -> {
                            presentFragment(new PremiumPreviewFragment("noncontacts"));
                        }));
                        backgroundResId = R.drawable.greydivider_bottom;
                    } else if (position == detailRow && rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES) {
                        privacyCell.setText(LocaleController.getString(R.string.PrivacyVoiceMessagesInfo));
                        if (getUserConfig().isPremium()) {
                            backgroundResId = R.drawable.greydivider;
                        } else {
                            backgroundResId = R.drawable.greydivider_bottom;
                        }
                    } else if (position == setBirthdayRow) {
                        privacyCell.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.PrivacyBirthdaySet), () -> {
                            showDialog(AlertsCreator.createBirthdayPickerDialog(getContext(), getString(R.string.EditProfileBirthdayTitle), getString(R.string.EditProfileBirthdayButton), null, birthday -> {
                                TLRPC.TL_account_updateBirthday req = new TLRPC.TL_account_updateBirthday();
                                req.flags |= 1;
                                req.birthday = birthday;
                                TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
                                TLRPC.TL_birthday oldBirthday = userFull != null ? userFull.birthday : null;
                                if (userFull != null) {
                                    userFull.flags2 |= 32;
                                    userFull.birthday = birthday;
                                    getMessagesStorage().updateUserInfo(userFull, false);
                                }
                                getMessagesController().invalidateContentSettings();
                                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (res instanceof TLRPC.TL_boolTrue) {
                                        BulletinFactory.of(PrivacyControlActivity.this)
                                            .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.PrivacyBirthdaySetDone))
                                            .setDuration(Bulletin.DURATION_PROLONG).show();
                                    } else {
                                        if (userFull != null) {
                                            if (oldBirthday == null) {
                                                userFull.flags2 &=~ 32;
                                            } else {
                                                userFull.flags2 |= 32;
                                            }
                                            userFull.birthday = oldBirthday;
                                            getMessagesStorage().updateUserInfo(userFull, false);
                                        }
                                        if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                                            if (getContext() != null) {
                                                showDialog(
                                                    new AlertDialog.Builder(getContext(), resourceProvider)
                                                        .setTitle(getString(R.string.PrivacyBirthdayTooOftenTitle))
                                                        .setMessage(getString(R.string.PrivacyBirthdayTooOftenMessage))
                                                        .setPositiveButton(getString(R.string.OK), null)
                                                        .create()
                                                );
                                            }
                                        } else {
                                            BulletinFactory.of(PrivacyControlActivity.this)
                                                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError))
                                                .show();
                                        }
                                    }
                                }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);

                                MessagesController.getInstance(currentAccount).removeSuggestion(0, "BIRTHDAY_SETUP");

                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);
                                updateRows(true);
                            }, null, getResourceProvider()).create());
                        }), true));
                        backgroundResId = R.drawable.greydivider;
                    } else if (position == detailRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            if (prevSubtypeContacts = (currentType == TYPE_NOBODY && currentSubType == 1)) {
                                privacyCell.setText(LocaleController.getString(R.string.PrivacyPhoneInfo3));
                            } else {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                String phoneLinkStr = String.format(Locale.ENGLISH, "https://t.me/+%s", getUserConfig().getClientPhone());
                                SpannableString phoneLink = new SpannableString(phoneLinkStr);
                                phoneLink.setSpan(new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View view) {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", phoneLinkStr);
                                        clipboard.setPrimaryClip(clip);
                                        BulletinFactory.of(PrivacyControlActivity.this).createCopyLinkBulletin(LocaleController.getString(R.string.LinkCopied), getResourceProvider()).show();
                                    }
                                }, 0, phoneLinkStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                                spannableStringBuilder.append(LocaleController.getString(R.string.PrivacyPhoneInfo))
                                        .append("\n\n")
                                        .append(LocaleController.getString(R.string.PrivacyPhoneInfo4))
                                        .append("\n")
                                        .append(phoneLink);

                                privacyCell.setText(spannableStringBuilder);
                            }
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyForwardsInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyProfilePhotoInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIO) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyBioInfo3));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyBirthdayInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyGiftsInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyCallsP2PHelp));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            privacyCell.setText(LocaleController.getString(R.string.WhoCanCallMeInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            privacyCell.setText(LocaleController.getString(R.string.WhoCanAddMeInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString(R.string.CustomHelp));
                        }
                        backgroundResId = R.drawable.greydivider;
                    } else if (position == shareDetailRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyPhoneInfo2));
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyForwardsInfo2));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            if (currentType == TYPE_CONTACTS) {
                                privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PrivacyProfilePhotoInfo5)));
                            } else if (currentType == TYPE_EVERYBODY) {
                                privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PrivacyProfilePhotoInfo3)));
                            } else {
                                privacyCell.setText(LocaleController.getString(R.string.PrivacyProfilePhotoInfo4));
                            }
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            privacyCell.setText(LocaleController.getString(R.string.CustomP2PInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIO) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyBioInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyBirthdayInfo3));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            privacyCell.setText(LocaleController.getString(R.string.CustomCallInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            privacyCell.setText(LocaleController.getString(R.string.CustomShareInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
                            privacyCell.setText(LocaleController.getString(R.string.CustomShareGiftsInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES) {
                            privacyCell.setText(LocaleController.getString(R.string.PrivacyVoiceMessagesInfo2));
                        } else {
                            privacyCell.setText(LocaleController.getString(R.string.CustomShareSettingsHelp));
                        }
                        if (rulesType == PRIVACY_RULES_TYPE_CALLS || rulesType == PRIVACY_RULES_TYPE_LASTSEEN) {
                            backgroundResId = R.drawable.greydivider;
                        } else {
                            backgroundResId = R.drawable.greydivider_bottom;
                        }
                    } else if (position == p2pDetailRow) {
                        backgroundResId = R.drawable.greydivider_bottom;
                    } else if (position == photoForRestDescriptionRow) {
                        privacyCell.setText(LocaleController.getString(R.string.PhotoForRestDescription));
                    } else if (position == readDetailRow) {
                        privacyCell.setText(LocaleController.getString(R.string.HideReadTimeInfo));
                        backgroundResId = readPremiumDetailRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider;
                    } else if (position == readPremiumDetailRow) {
                        privacyCell.setText(LocaleController.getString(getUserConfig().isPremium() ? R.string.PrivacyLastSeenPremiumInfoForPremium : R.string.PrivacyLastSeenPremiumInfo));
                        backgroundResId = R.drawable.greydivider_bottom;
                    }
                    if (backgroundResId != 0) {
                        Drawable drawable = Theme.getThemedDrawableByKey(mContext, backgroundResId, Theme.key_windowBackgroundGrayShadow);
                        CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                        combinedDrawable.setFullsize(true);
                        privacyCell.setBackgroundDrawable(combinedDrawable);
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == sectionRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyPhoneTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyForwardsTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyProfilePhotoTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIO) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyBioTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            headerCell.setText(LocaleController.getString(R.string.P2PEnabledWith));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            headerCell.setText(LocaleController.getString(R.string.WhoCanCallMe));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            headerCell.setText(LocaleController.getString(R.string.WhoCanAddMe));
                        } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyVoiceMessagesTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyMessagesTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_BIRTHDAY) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyBirthdayTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_GIFTS) {
                            headerCell.setText(LocaleController.getString(R.string.PrivacyGiftsTitle));
                        } else {
                            headerCell.setText(LocaleController.getString(R.string.LastSeenTitle));
                        }
                    } else if (position == shareSectionRow) {
                        headerCell.setText(LocaleController.getString(R.string.AddExceptions));
                    } else if (position == p2pSectionRow) {
                        headerCell.setText(LocaleController.getString(R.string.PrivacyP2PHeader));
                    } else if (position == phoneSectionRow) {
                        headerCell.setText(LocaleController.getString(R.string.PrivacyPhoneTitle2));
                    }
                    break;
                case 3:
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    radioCell.setRadioIcon(null);
                    if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
                        if (position == everybodyRow) {
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString(R.string.P2PEverybody), currentType == TYPE_EVERYBODY, true);
                            } else {
                                radioCell.setText(LocaleController.getString(R.string.LastSeenEverybody), currentType == TYPE_EVERYBODY, true);
                            }
                        } else if (position == myContactsRow) {
                            if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES && !getUserConfig().isPremium() || rulesType == PRIVACY_RULES_TYPE_MESSAGES && !getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getUserConfig().isPremium()) {
                                radioCell.setRadioIcon(getContext().getResources().getDrawable(R.drawable.mini_switch_lock).mutate());
                            }
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString(R.string.P2PContacts), currentType == TYPE_CONTACTS, nobodyRow != -1);
                            } else if (rulesType == PRIVACY_RULES_TYPE_MESSAGES) {
                                radioCell.setText(LocaleController.getString(R.string.PrivacyMessagesContactsAndPremium), currentType == TYPE_CONTACTS, nobodyRow != -1);
                            } else {
                                radioCell.setText(LocaleController.getString(R.string.LastSeenContacts), currentType == TYPE_CONTACTS, nobodyRow != -1);
                            }
                        } else {
                            if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES && !getUserConfig().isPremium() || rulesType == PRIVACY_RULES_TYPE_MESSAGES && !getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getUserConfig().isPremium()) {
                                radioCell.setRadioIcon(getContext().getResources().getDrawable(R.drawable.mini_switch_lock).mutate());
                            }
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString(R.string.P2PNobody), currentType == TYPE_NOBODY, false);
                            } else {
                                radioCell.setText(LocaleController.getString(R.string.LastSeenNobody), currentType == TYPE_NOBODY, false);
                            }
                        }
                    } else {
                        if (position == phoneContactsRow) {
                            radioCell.setText(LocaleController.getString(R.string.LastSeenContacts), currentSubType == 1, false);
                        } else if (position == phoneEverybodyRow) {
                            radioCell.setText(LocaleController.getString(R.string.LastSeenEverybody), currentSubType == 0, true);
                        }
                    }
                    break;
                case 8:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == readRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.HideReadTime), selectedReadValue, false);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == alwaysShareRow || position == neverShareRow || position == p2pRow || position == readPremiumRow) {
                return 0;
            } else if (position == shareDetailRow || position == detailRow || position == p2pDetailRow || position == photoForRestDescriptionRow || position == readDetailRow || position == readPremiumDetailRow || position == setBirthdayRow) {
                return 1;
            } else if (position == sectionRow || position == shareSectionRow || position == p2pSectionRow || position == phoneSectionRow) {
                return 2;
            } else if (position == everybodyRow || position == myContactsRow || position == nobodyRow || position == phoneEverybodyRow || position == phoneContactsRow) {
                return 3;
            } else if (position == messageRow) {
                return 4;
            } else if (position == phoneDetailRow) {
                return 5;
            } else if (position == photoForRestRow) {
                return 6;
            } else if (position == currentPhotoForRestRow) {
                return 7;
            } else if (position == readRow) {
                return 8;
            }
            return 0;
        }
    }

    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;

        SparseIntArray oldPositionToItem = new SparseIntArray();
        SparseIntArray newPositionToItem = new SparseIntArray();

        @Override
        public int getOldListSize() {
            return oldRowCount;
        }

        @Override
        public int getNewListSize() {
            return rowCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            int oldIndex = oldPositionToItem.get(oldItemPosition, -1);
            int newIndex = newPositionToItem.get(newItemPosition, -1);
            return oldIndex == newIndex && oldIndex >= 0;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }

        public void fillPositions(SparseIntArray sparseIntArray) {
            sparseIntArray.clear();
            int pointer = 0;
            put(++pointer, messageRow, sparseIntArray);
            put(++pointer, sectionRow, sparseIntArray);
            put(++pointer, everybodyRow, sparseIntArray);
            put(++pointer, myContactsRow, sparseIntArray);
            put(++pointer, nobodyRow, sparseIntArray);
            put(++pointer, detailRow, sparseIntArray);
            put(++pointer, shareSectionRow, sparseIntArray);
            put(++pointer, alwaysShareRow, sparseIntArray);
            put(++pointer, neverShareRow, sparseIntArray);
            put(++pointer, shareDetailRow, sparseIntArray);
            put(++pointer, phoneSectionRow, sparseIntArray);
            put(++pointer, phoneEverybodyRow, sparseIntArray);
            put(++pointer, phoneContactsRow, sparseIntArray);
            put(++pointer, phoneDetailRow, sparseIntArray);
            put(++pointer, photoForRestRow, sparseIntArray);
            put(++pointer, currentPhotoForRestRow, sparseIntArray);
            put(++pointer, photoForRestDescriptionRow, sparseIntArray);
            put(++pointer, p2pSectionRow, sparseIntArray);
            put(++pointer, p2pRow, sparseIntArray);
            put(++pointer, p2pDetailRow, sparseIntArray);
            put(++pointer, readRow, sparseIntArray);
            put(++pointer, readDetailRow, sparseIntArray);
            put(++pointer, readPremiumRow, sparseIntArray);
            put(++pointer, readPremiumDetailRow, sparseIntArray);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, RadioCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgOutDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgOutMediaDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextIn));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextOut));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeSelectedText));

        return themeDescriptions;
    }
}
