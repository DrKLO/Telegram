package org.telegram.messenger.utils;

import android.os.Bundle;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class PhotoUtilities {

    public static void applyPhotoToUser(TLRPC.Photo photo, TLRPC.User user, boolean personal) {
        ArrayList<TLRPC.PhotoSize> sizes = photo.sizes;
        TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 100);
        TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(sizes, 1000);

        user.flags |= 32;
        user.photo = new TLRPC.TL_userProfilePhoto();
        user.photo.personal = personal;
        user.photo.photo_id = photo.id;
        user.photo.has_video = photo.video_sizes != null && photo.video_sizes.size() > 0;
        if (smallSize2 != null) {
            user.photo.photo_small = smallSize2.location;
        }
        if (bigSize2 != null) {
            user.photo.photo_big = bigSize2.location;
        }
    }

    public static void setImageAsAvatar(MediaController.PhotoEntry entry, BaseFragment baseFragment, Runnable onDone) {
        INavigationLayout layout = baseFragment.getParentLayout();
        int currentAccount = baseFragment.getCurrentAccount();

        ImageUpdater imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
        imageUpdater.parentFragment = baseFragment;
        imageUpdater.processEntry(entry);
        imageUpdater.setDelegate((photo, video, videoStartTimestamp, videoPath, bigSize, smallSize, isVideo, emojiMarkup) -> AndroidUtilities.runOnUIThread(() -> {
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_photos_photo) {
                    TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                    MessagesController.getInstance(currentAccount).putUsers(photos_photo.users, false);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).clientUserId);
                    if (photos_photo.photo instanceof TLRPC.TL_photo) {
                        if (user != null) {
                            TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(photos_photo.photo.sizes, 100);
                            TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(photos_photo.photo.sizes, 1000);
                            if (smallSize2 != null && smallSize != null && smallSize.location != null) {
                                File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize2, true);
                                File src = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize.location, true);
                                src.renameTo(destFile);
                                String oldKey = smallSize.location.volume_id + "_" + smallSize.location.local_id + "@50_50";
                                String newKey = smallSize2.location.volume_id + "_" + smallSize2.location.local_id + "@50_50";
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), false);
                            }

                            if (bigSize2 != null && bigSize != null && bigSize.location != null) {
                                File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize2, true);
                                File src = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize.location, true);
                                src.renameTo(destFile);
                            }

                            PhotoUtilities.applyPhotoToUser(photos_photo.photo, user, false);
                            UserConfig.getInstance(currentAccount).setCurrentUser(user);
                            UserConfig.getInstance(currentAccount).saveConfig(true);
                            if (onDone != null) {
                                onDone.run();
                            }
                            CharSequence title = AndroidUtilities.replaceTags(LocaleController.getString("ApplyAvatarHintTitle", R.string.ApplyAvatarHintTitle));
                            CharSequence subtitle = AndroidUtilities.replaceSingleTag(LocaleController.getString("ApplyAvatarHint", R.string.ApplyAvatarHint), () -> {
                                Bundle args = new Bundle();
                                args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId);
                                layout.getLastFragment().presentFragment(new ProfileActivity(args));
                            });
                            BulletinFactory.of(layout.getLastFragment()).createUsersBulletin(Collections.singletonList(user), title, subtitle, null).show();
                        }
                    }
                }
            }));
            imageUpdater.onPause();
        }));
    }

    public static void replacePhotoImagesInCache(int currentAccount, TLRPC.Photo photo, TLRPC.Photo photoToReplace) {
        TLRPC.PhotoSize smallSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 100);
        TLRPC.PhotoSize bigSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 1000);

        TLRPC.PhotoSize smallSize2 = FileLoader.getClosestPhotoSizeWithSize(photoToReplace.sizes, 100);
        TLRPC.PhotoSize bigSize2 = FileLoader.getClosestPhotoSizeWithSize(photoToReplace.sizes, 1000);
        if (smallSize2 != null && smallSize != null) {
            File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize2, true);
            File src = FileLoader.getInstance(currentAccount).getPathToAttach(smallSize, true);
            src.renameTo(destFile);
            String oldKey = smallSize.location.volume_id + "_" + smallSize.location.local_id + "@50_50";
            String newKey = smallSize2.location.volume_id + "_" + smallSize2.location.local_id + "@50_50";
            ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForPhoto(smallSize, photo), false);
        }

        if (bigSize2 != null && bigSize != null) {
            File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize2, true);
            File src = FileLoader.getInstance(currentAccount).getPathToAttach(bigSize, true);
            src.renameTo(destFile);
            String oldKey = bigSize.location.volume_id + "_" + bigSize.location.local_id + "@150_150";
            String newKey = bigSize2.location.volume_id + "_" + bigSize2.location.local_id + "@150_150";
            ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForPhoto(bigSize, photo), false);
        }
    }

    public static void applyPhotoToUser(TLRPC.PhotoSize smallSize, TLRPC.PhotoSize bigSize, boolean hasVideo, TLRPC.User user, boolean personal) {
        user.flags |= 32;
        user.photo = new TLRPC.TL_userProfilePhoto();
        user.photo.personal = personal;
        user.photo.photo_id = 0;
        user.photo.has_video = hasVideo;
        if (smallSize != null) {
            user.photo.photo_small = smallSize.location;
        }
        if (bigSize != null) {
            user.photo.photo_big = bigSize.location;
        }
    }

    public static void showAvatartConstructorForUpdateUserPhoto(ChatActivity chatActivity, TLRPC.VideoSize emojiMarkup) {
        ImageUpdater imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
        imageUpdater.parentFragment = chatActivity;
        imageUpdater.showAvatarConstructor(emojiMarkup);
        final TLRPC.FileLocation[] avatar = new TLRPC.FileLocation[1];
        final TLRPC.FileLocation[] avatarBig = new TLRPC.FileLocation[1];
        long userId = chatActivity.getUserConfig().getClientUserId();
        imageUpdater.setDelegate((photo, video, videoStartTimestamp, videoPath, bigSize, smallSize, isVideo, emojiMarkup1) -> {
            if (photo != null || video != null || emojiMarkup1 != null) {
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
                if (emojiMarkup1 != null) {
                    req.video_emoji_markup = emojiMarkup1;
                    req.flags |= 16;
                }
                chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        TLRPC.User user = chatActivity.getMessagesController().getUser(chatActivity.getUserConfig().getClientUserId());

                        TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                        ArrayList<TLRPC.PhotoSize> sizes = photos_photo.photo.sizes;
                        TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150);
                        TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800);
                        TLRPC.VideoSize videoSize = photos_photo.photo.video_sizes.isEmpty() ? null : FileLoader.getClosestVideoSizeWithSize(photos_photo.photo.video_sizes, 1000);
                        user.photo = new TLRPC.TL_userProfilePhoto();
                        user.photo.photo_id = photos_photo.photo.id;
                        if (small != null) {
                            user.photo.photo_small = small.location;
                        }
                        if (big != null) {
                            user.photo.photo_big = big.location;
                        }

                        if (small != null && avatar[0] != null) {
                            File destFile = FileLoader.getInstance(chatActivity.getCurrentAccount()).getPathToAttach(small, true);
                            File src = FileLoader.getInstance(chatActivity.getCurrentAccount()).getPathToAttach(avatar[0], true);
                            src.renameTo(destFile);
                            String oldKey = avatar[0].volume_id + "_" + avatar[0].local_id + "@50_50";
                            String newKey = small.location.volume_id + "_" + small.location.local_id + "@50_50";
                            ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), false);
                        }

                        if (videoSize != null && videoPath != null) {
                            File destFile = FileLoader.getInstance(chatActivity.getCurrentAccount()).getPathToAttach(videoSize, "mp4", true);
                            File src = new File(videoPath);
                            src.renameTo(destFile);
                        } else if (big != null && avatarBig[0] != null) {
                            File destFile = FileLoader.getInstance(chatActivity.getCurrentAccount()).getPathToAttach(big, true);
                            File src = FileLoader.getInstance(chatActivity.getCurrentAccount()).getPathToAttach(avatarBig[0], true);
                            src.renameTo(destFile);
                        }
                        chatActivity.getMessagesStorage().addDialogPhoto(user.id, ((TLRPC.TL_photos_photo) response).photo);
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        chatActivity.getMessagesStorage().putUsersAndChats(users, null, false, true);
                        TLRPC.UserFull userFull = chatActivity.getMessagesController().getUserFull(userId);
                        userFull.profile_photo = photos_photo.photo;
                        chatActivity.getMessagesStorage().updateUserInfo(userFull, false);
                        CharSequence title = AndroidUtilities.replaceTags(LocaleController.getString("ApplyAvatarHintTitle", R.string.ApplyAvatarHintTitle));
                        CharSequence subtitle = AndroidUtilities.replaceSingleTag(LocaleController.getString("ApplyAvatarHint", R.string.ApplyAvatarHint), () -> {
                            Bundle args = new Bundle();
                            args.putLong("user_id", userId);
                            chatActivity.presentFragment(new ProfileActivity(args));
                        });
                        BulletinFactory.of(chatActivity).createUsersBulletin(Collections.singletonList(user), title, subtitle, null).show();
                    }
                }));
            } else {
                avatar[0] = smallSize.location;
                avatarBig[0] = bigSize.location;
            }
        });
    }
}
