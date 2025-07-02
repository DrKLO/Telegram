package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.web.WebInstantView;

public class ImageLocation {

    public int dc_id;
    public byte[] file_reference;
    public byte[] key;
    public byte[] iv;
    public long access_hash;
    public TLRPC.TL_fileLocationToBeDeprecated location;

    public String path;

    public SecureDocument secureDocument;

    public TLRPC.Document document;

    public long videoSeekTo;

    public TLRPC.PhotoSize photoSize;
    public TLRPC.Photo photo;
    public int photoPeerType;
    public TLRPC.InputPeer photoPeer;
    public TLRPC.InputStickerSet stickerSet;
    public int imageType;

    public int thumbVersion;

    public long currentSize;

    public long photoId;
    public long documentId;
    public String thumbSize;

    public WebFile webFile;

    public WebInstantView.WebPhoto instantFile;

    public static ImageLocation getForPath(String path) {
        if (path == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.path = path;
        return imageLocation;
    }

    public static ImageLocation getForVideoPath(String path) {
        if (path == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.path = path;
        imageLocation.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
        return imageLocation;
    }

    public static ImageLocation getForSecureDocument(SecureDocument secureDocument) {
        if (secureDocument == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.secureDocument = secureDocument;
        return imageLocation;
    }

    public static ImageLocation getForDocument(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.document = document;
        imageLocation.key = document.key;
        imageLocation.iv = document.iv;
        imageLocation.currentSize = document.size;
        return imageLocation;
    }

    public static ImageLocation getForWebFile(WebFile webFile) {
        if (webFile == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.webFile = webFile;
        imageLocation.currentSize = webFile.size;
        return imageLocation;
    }

    public static ImageLocation getForInstantFile(WebInstantView.WebPhoto instantFile) {
        if (instantFile == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.instantFile = instantFile;
        return imageLocation;
    }

    public static ImageLocation getForObject(TLRPC.PhotoSize photoSize, TLObject object) {
        if (object instanceof TLRPC.Photo) {
            return getForPhoto(photoSize, (TLRPC.Photo) object);
        } else if (object instanceof TLRPC.Document) {
            return getForDocument(photoSize, (TLRPC.Document) object);
        } else if (object instanceof TLRPC.Message) {
            return getForMessage(photoSize, (TLRPC.Message) object);
        }
        return null;
    }

    public static ImageLocation getForMessage(TLRPC.PhotoSize photoSize, TLRPC.Message message) {
        if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = photoSize;
            return imageLocation;
        }
        return null;
    }

    public static ImageLocation getForPhoto(TLRPC.PhotoSize photoSize, TLRPC.Photo photo) {
        if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = photoSize;
            return imageLocation;
        } else if (photoSize == null || photo == null) {
            return null;
        }
        int dc_id;
        if (photo.dc_id != 0) {
            dc_id = photo.dc_id;
        } else {
            dc_id = photoSize.location.dc_id;
        }
        return getForPhoto(photoSize.location, photoSize.size, photo, null, null, TYPE_SMALL, dc_id, null, photoSize.type);
    }

    public static final int TYPE_BIG = 0;
    public static final int TYPE_SMALL = 1;
    public static final int TYPE_STRIPPED = 2;
    public static final int TYPE_VIDEO_SMALL = 3;
    public static final int TYPE_VIDEO_BIG = 4;

    public static ImageLocation getForUserOrChat(TLObject object, int type) {
        if (object instanceof TLRPC.User) {
            return getForUser((TLRPC.User) object, type);
        } else if (object instanceof TLRPC.Chat) {
            return getForChat((TLRPC.Chat) object, type);
        }
        return null;
    }

    public static ImageLocation getForUser(TLRPC.User user, int type) {
        if (user == null || user.access_hash == 0 || user.photo == null) {
            return null;
        }
        if (type == TYPE_VIDEO_BIG || type == TYPE_VIDEO_SMALL) {
            int currentAccount = UserConfig.selectedAccount;
            if (MessagesController.getInstance(currentAccount).isPremiumUser(user) && user.photo.has_video) {
                final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
                if (userFull != null && userFull.profile_photo != null && userFull.profile_photo.video_sizes != null && !userFull.profile_photo.video_sizes.isEmpty()) {
                    if (type == TYPE_VIDEO_BIG) {
                        TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(userFull.profile_photo.video_sizes, 1000);
                        return ImageLocation.getForPhoto(videoSize, userFull.profile_photo);
                    } else {
                        TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(userFull.profile_photo.video_sizes, 100);
                        for (int i = 0; i < userFull.profile_photo.video_sizes.size(); i++) {
                            if ("p".equals(userFull.profile_photo.video_sizes.get(i).type)) {
                                videoSize = userFull.profile_photo.video_sizes.get(i);
                                break;
                            }
                        }
                        return ImageLocation.getForPhoto(videoSize, userFull.profile_photo);
                    }

                }
            }
            return null;
        }
        if (type == TYPE_STRIPPED) {
            if (user.photo.stripped_thumb == null) {
                return null;
            }
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = new TLRPC.TL_photoStrippedSize();
            imageLocation.photoSize.type = "s";
            imageLocation.photoSize.bytes = user.photo.stripped_thumb;
            return imageLocation;
        }
        TLRPC.FileLocation fileLocation = type == TYPE_BIG ? user.photo.photo_big : user.photo.photo_small;
        if (fileLocation == null) {
            return null;
        }
        TLRPC.TL_inputPeerUser inputPeer = new TLRPC.TL_inputPeerUser();
        inputPeer.user_id = user.id;
        inputPeer.access_hash = user.access_hash;
        int dc_id;
        if (user.photo.dc_id != 0) {
            dc_id = user.photo.dc_id;
        } else {
            dc_id = fileLocation.dc_id;
        }
        ImageLocation location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dc_id, null, null);
        location.photoId = user.photo.photo_id;
        return location;
    }

    public static ImageLocation getForChat(TLRPC.Chat chat, int type) {
        if (chat == null || chat.photo == null) {
            return null;
        }
        if (type == TYPE_STRIPPED) {
            if (chat.photo.stripped_thumb == null) {
                return null;
            }
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = new TLRPC.TL_photoStrippedSize();
            imageLocation.photoSize.type = "s";
            imageLocation.photoSize.bytes = chat.photo.stripped_thumb;
            return imageLocation;
        }
        TLRPC.FileLocation fileLocation = type == TYPE_BIG ? chat.photo.photo_big : chat.photo.photo_small;
        if (fileLocation == null) {
            return null;
        }
        TLRPC.InputPeer inputPeer;
        if (ChatObject.isChannel(chat)) {
            if (chat.access_hash == 0) {
                return null;
            }
            inputPeer = new TLRPC.TL_inputPeerChannel();
            inputPeer.channel_id = chat.id;
            inputPeer.access_hash = chat.access_hash;
        } else {
            inputPeer = new TLRPC.TL_inputPeerChat();
            inputPeer.chat_id = chat.id;
        }
        int dc_id;
        if (chat.photo.dc_id != 0) {
            dc_id = chat.photo.dc_id;
        } else {
            dc_id = fileLocation.dc_id;
        }
        ImageLocation location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dc_id, null, null);
        location.photoId = chat.photo.photo_id;
        return location;
    }

    public static ImageLocation getForSticker(TLRPC.PhotoSize photoSize, TLRPC.Document sticker, int thumbVersion) {
        if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = photoSize;
            return imageLocation;
        } else if (photoSize == null || sticker == null) {
            return null;
        }
        TLRPC.InputStickerSet stickerSet = MediaDataController.getInputStickerSet(sticker);
        if (stickerSet == null) {
            return null;
        }
        ImageLocation imageLocation = getForPhoto(photoSize.location, photoSize.size, null, null, null, TYPE_SMALL, sticker.dc_id, stickerSet, photoSize.type);
        if (photoSize.type.equalsIgnoreCase("a")) {
            imageLocation.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
        } else if (photoSize.type.equalsIgnoreCase("v")) {
            imageLocation.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
        }
        imageLocation.thumbVersion = thumbVersion;
        return imageLocation;
    }

    public static ImageLocation getForDocument(TLRPC.VideoSize videoSize, TLRPC.Document document) {
        if (videoSize == null || document == null) {
            return null;
        }
        ImageLocation location = getForPhoto(videoSize.location, videoSize.size, null, document, null, TYPE_SMALL, document.dc_id, null, videoSize.type);
        if ("f".equals(videoSize.type)) {
            location.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
        } else {
            location.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
        }
        return location;
    }

    public static ImageLocation getForPhoto(TLRPC.VideoSize videoSize, TLRPC.Photo photo) {
        if (videoSize == null || photo == null) {
            return null;
        }
        ImageLocation location = getForPhoto(videoSize.location, videoSize.size, photo, null, null, TYPE_SMALL, photo.dc_id, null, videoSize.type);
        location.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
        if ((videoSize.flags & 1) != 0) {
            location.videoSeekTo = (int) (videoSize.video_start_ts * 1000);
        }
        return location;
    }

    public static ImageLocation getForDocument(TLRPC.PhotoSize photoSize, TLRPC.Document document) {
        if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
            ImageLocation imageLocation = new ImageLocation();
            imageLocation.photoSize = photoSize;
            return imageLocation;
        } else if (photoSize == null || document == null) {
            return null;
        }
        return getForPhoto(photoSize.location, photoSize.size, null, document, null, TYPE_SMALL, document.dc_id, null, photoSize.type);
    }

    public static ImageLocation getForLocal(TLRPC.FileLocation location) {
        if (location == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.location = new TLRPC.TL_fileLocationToBeDeprecated();
        imageLocation.location.local_id = location.local_id;
        imageLocation.location.volume_id = location.volume_id;
        imageLocation.location.secret = location.secret;
        imageLocation.location.dc_id = location.dc_id;
        return imageLocation;
    }

    public static ImageLocation getForStickerSet(TLRPC.StickerSet set) {
        if (set == null) return null;
        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(set.thumbs, 90);
        if (photoSize == null) return null;
        TLRPC.InputStickerSet inputStickerSet;
        if (set.access_hash != 0) {
            inputStickerSet = new TLRPC.TL_inputStickerSetID();
            inputStickerSet.id = set.id;
            inputStickerSet.access_hash = set.access_hash;
        } else {
            inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
            inputStickerSet.short_name = set.short_name;
        }
        return getForPhoto(photoSize.location, photoSize.size, null, null, null, TYPE_SMALL, photoSize.location.dc_id, inputStickerSet, photoSize.type);
    }

    private static ImageLocation getForPhoto(TLRPC.FileLocation location, int size, TLRPC.Photo photo, TLRPC.Document document, TLRPC.InputPeer photoPeer, int photoPeerType, int dc_id, TLRPC.InputStickerSet stickerSet, String thumbSize) {
        if (location == null || photo == null && photoPeer == null && stickerSet == null && document == null) {
            return null;
        }
        ImageLocation imageLocation = new ImageLocation();
        imageLocation.dc_id = dc_id;
        imageLocation.photo = photo;
        imageLocation.currentSize = size;
        imageLocation.photoPeer = photoPeer;
        imageLocation.photoPeerType = photoPeerType;
        imageLocation.stickerSet = stickerSet;
        if (location instanceof TLRPC.TL_fileLocationToBeDeprecated) {
            imageLocation.location = (TLRPC.TL_fileLocationToBeDeprecated) location;
            if (photo != null) {
                imageLocation.file_reference = photo.file_reference;
                imageLocation.access_hash = photo.access_hash;
                imageLocation.photoId = photo.id;
                imageLocation.thumbSize = thumbSize;
            } else if (document != null) {
                imageLocation.file_reference = document.file_reference;
                imageLocation.access_hash = document.access_hash;
                imageLocation.documentId = document.id;
                imageLocation.thumbSize = thumbSize;
            }
        } else {
            imageLocation.location = new TLRPC.TL_fileLocationToBeDeprecated();
            imageLocation.location.local_id = location.local_id;
            imageLocation.location.volume_id = location.volume_id;
            imageLocation.location.secret = location.secret;
            imageLocation.dc_id = location.dc_id;
            imageLocation.file_reference = location.file_reference;
            imageLocation.key = location.key;
            imageLocation.iv = location.iv;
            imageLocation.access_hash = location.secret;
        }
        return imageLocation;
    }

    public static String getStrippedKey(Object parentObject, Object fullObject, Object strippedObject) {
        if (parentObject instanceof TLRPC.WebPage || parentObject instanceof MessageObject && ((MessageObject) parentObject).type == MessageObject.TYPE_PAID_MEDIA) {
            if (fullObject instanceof ImageLocation) {
                ImageLocation imageLocation = (ImageLocation) fullObject;
                if (imageLocation.document != null) {
                    fullObject = imageLocation.document;
                } else if (imageLocation.photoSize != null) {
                    fullObject = imageLocation.photoSize;
                } else if (imageLocation.photo != null) {
                    fullObject = imageLocation.photo;
                }
            }
            if (fullObject == null) {
                return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + strippedObject;
            } else if (fullObject instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) fullObject;
                return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + document.id;
            } else if (fullObject instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) fullObject;
                return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + photo.id;
            } else if (fullObject instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize size = (TLRPC.PhotoSize) fullObject;
                if (size.location != null) {
                    return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + size.location.local_id + "_" + size.location.volume_id;
                } else {
                    return "stripped" + FileRefController.getKeyForParentObject(parentObject);
                }
            } else if (fullObject instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation loc = (TLRPC.FileLocation) fullObject;
                return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + loc.local_id + "_" + loc.volume_id;
            }
        }
        return "stripped" + FileRefController.getKeyForParentObject(parentObject);
    }

    public String getKey(Object parentObject, Object fullObject, boolean url) {
        if (secureDocument != null) {
            return secureDocument.secureFile.dc_id + "_" + secureDocument.secureFile.id;
        } else if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
            if (photoSize.bytes.length > 0) {
                return getStrippedKey(parentObject, fullObject == null ? this : fullObject, photoSize);
            }
        } else if (location != null) {
            return location.volume_id + "_" + location.local_id;
        } else if (webFile != null) {
            return Utilities.MD5(webFile.url);
        } else if (instantFile != null) {
            return Utilities.MD5(instantFile.url);
        } else if (document != null) {
            if (!url && document instanceof DocumentObject.ThemeDocument) {
                DocumentObject.ThemeDocument themeDocument = (DocumentObject.ThemeDocument) document;
                return document.dc_id + "_" + document.id + "_" + Theme.getBaseThemeKey(themeDocument.themeSettings) + "_" + themeDocument.themeSettings.accent_color + "_" +
                        (themeDocument.themeSettings.message_colors.size() > 1 ? themeDocument.themeSettings.message_colors.get(1) : 0) + "_" + (themeDocument.themeSettings.message_colors.size() > 0 ? themeDocument.themeSettings.message_colors.get(0) : 0);
            } else if (document.id != 0 && document.dc_id != 0) {
                return document.dc_id + "_" + document.id;
            }
        } else if (path != null) {
            return Utilities.MD5(path);
        }
        return null;
    }

    public boolean isEncrypted() {
        return key != null;
    }

    public long getSize() {
        if (photoSize != null) {
            return photoSize.size;
        } else if (secureDocument != null) {
            if (secureDocument.secureFile != null) {
                return secureDocument.secureFile.size;
            }
        } else if (document != null) {
            return document.size;
        } else if (webFile != null) {
            return webFile.size;
        }
        return currentSize;
    }
}
