/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.objects;

import android.graphics.Bitmap;

import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationLoader;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class MessageObject {
    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public int type;
    public ArrayList<PhotoObject> photoThumbs;
    public Bitmap imagePreview;
    public PhotoObject previewPhoto;
    public String dateKey;
    public boolean deleted = false;

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users) {
        messageOwner = message;

        if (message instanceof TLRPC.TL_messageService) {
            if (message.action != null) {
                TLRPC.User fromUser = users.get(message.from_id);
                if (fromUser == null) {
                    fromUser = MessagesController.Instance.users.get(message.from_id);
                }
                if (message.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionCreateGroup).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionCreateGroup).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionCreateGroup).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionLeftUser).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                        } else {
                            if (fromUser != null) {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionLeftUser).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionLeftUser).replace("un1", "");
                            }
                        }
                    } else {
                        TLRPC.User who = users.get(message.action.user_id);
                        if (who == null) {
                            MessagesController.Instance.users.get(message.action.user_id);
                        }
                        String str = ApplicationLoader.applicationContext.getString(R.string.ActionKickUser);
                        if (who != null && fromUser != null) {
                            if (message.from_id == UserConfig.clientUserId) {
                                messageText = str.replace("un2", Utilities.formatName(who.first_name, who.last_name)).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                            } else if (message.action.user_id == UserConfig.clientUserId) {
                                messageText = str.replace("un2", ApplicationLoader.applicationContext.getString(R.string.FromYou)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = str.replace("un2", Utilities.formatName(who.first_name, who.last_name)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            }
                        } else {
                            messageText = str.replace("un2", "").replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    TLRPC.User whoUser = users.get(message.action.user_id);
                    if (whoUser == null) {
                        MessagesController.Instance.users.get(message.action.user_id);
                    }
                    String str = ApplicationLoader.applicationContext.getString(R.string.ActionAddUser);
                    if (whoUser != null && fromUser != null) {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = str.replace("un2", Utilities.formatName(whoUser.first_name, whoUser.last_name)).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                        } else if (message.action.user_id == UserConfig.clientUserId) {
                            messageText = str.replace("un2", ApplicationLoader.applicationContext.getString(R.string.FromYou)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = str.replace("un2", Utilities.formatName(whoUser.first_name, whoUser.last_name)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        }
                    } else {
                        messageText = str.replace("un2", "").replace("un1", "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    photoThumbs = new ArrayList<PhotoObject>();
                    for (TLRPC.PhotoSize size : message.action.photo.sizes) {
                        photoThumbs.add(new PhotoObject(size));
                    }
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedPhoto).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedPhoto).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedTitle).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou)).replace("un2", message.action.title);
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedTitle).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name)).replace("un2", message.action.title);
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedTitle).replace("un1", "").replace("un2", message.action.title);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionRemovedPhoto).replace("un1", ApplicationLoader.applicationContext.getString(R.string.FromYou));
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionRemovedPhoto).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionRemovedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (message.action.ttl != 0) {
                        String timeString;
                        if (message.action.ttl == 2) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime2s);
                        } else if (message.action.ttl == 5) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime5s);
                        } else if (message.action.ttl == 60) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1m);
                        } else if (message.action.ttl == 60 * 60) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1h);
                        } else if (message.action.ttl == 60 * 60 * 24) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1d);
                        } else if (message.action.ttl == 60 * 60 * 24 * 7) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1w);
                        } else {
                            timeString = String.format("%d", message.action.ttl);
                        }
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChangedOutgoing), timeString);
                        } else {
                            if (fromUser != null) {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChanged), fromUser.first_name, timeString);
                            } else {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChanged), "", timeString);
                            }
                        }
                    } else {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeRemoved), ApplicationLoader.applicationContext.getString(R.string.FromYou));
                        } else {
                            if (fromUser != null) {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeRemoved), fromUser.first_name);
                            } else {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeRemoved), "");
                            }
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                    messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationUnrecognizedDevice, message.action.title, message.action.address);
                } else if (message.action instanceof TLRPC.TL_messageActionUserJoined) {
                    if (fromUser != null) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactJoined, Utilities.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactJoined, "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    if (fromUser != null) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactNewPhoto, Utilities.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactNewPhoto, "");
                    }
                }
            }
        } else if (message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                photoThumbs = new ArrayList<PhotoObject>();
                for (TLRPC.PhotoSize size : message.media.photo.sizes) {
                    PhotoObject obj = new PhotoObject(size);
                    photoThumbs.add(obj);
                    if (imagePreview == null && obj.image != null) {
                        imagePreview = obj.image;
                    }
                }
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachPhoto);
            } else if (message.media instanceof TLRPC.TL_messageMediaVideo) {
                photoThumbs = new ArrayList<PhotoObject>();
                PhotoObject obj = new PhotoObject(message.media.video.thumb);
                photoThumbs.add(obj);
                if (imagePreview == null && obj.image != null) {
                    imagePreview = obj.image;
                }
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachVideo);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachContact);
            } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.UnsuppotedMedia);
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachDocument);
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachAudio);
            }
        } else {
            messageText = message.message;
        }
        messageText = Emoji.replaceEmoji(messageText);


        if (message instanceof TLRPC.TL_message || (message instanceof TLRPC.TL_messageForwarded && (message.media == null || !(message.media instanceof TLRPC.TL_messageMediaEmpty)))) {
            if (message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 0;
                } else {
                    type = 1;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 2;
                } else {
                    type = 3;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaGeo) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 4;
                } else {
                    type = 5;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaVideo) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 6;
                } else {
                    type = 7;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaContact) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 12;
                } else {
                    type = 13;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 0;
                } else {
                    type = 1;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaDocument) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 16;
                } else {
                    type = 17;
                }
            }
        } else if (message instanceof TLRPC.TL_messageService) {
            if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                type = 11;
            } else {
                type = 10;
            }
        } else if (message instanceof TLRPC.TL_messageForwarded) {
            if (message.from_id == UserConfig.clientUserId) {
                type = 8;
            } else {
                type = 9;
            }
        }

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long)(messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
    }

    public String getFileName() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
            return getAttachFileName(messageOwner.media.video);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return getAttachFileName(messageOwner.media.document);
        }
        return "";
    }

    public static String getAttachFileName(TLObject attach) {
        if (attach instanceof TLRPC.Video) {
            TLRPC.Video video = (TLRPC.Video)attach;
            return video.dc_id + "_" + video.id + ".mp4";
        } else if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document)attach;
            String ext = document.file_name;
            int idx = -1;
            if (ext == null || (idx = ext.lastIndexOf(".")) == -1) {
                ext = "";
            } else {
                ext = ext.substring(idx);
            }
            if (ext.length() > 1) {
                return document.dc_id + "_" + document.id + ext;
            } else {
                return document.dc_id + "_" + document.id;
            }
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize)attach;
            return photo.location.volume_id + "_" + photo.location.local_id + ".jpg";
        }
        return "";
    }
}
