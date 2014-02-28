/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.Views.ImageReceiver;

import java.lang.ref.WeakReference;

public class ChatOrUserCell extends BaseCell {
    private static TextPaint namePaint;
    private static TextPaint nameEncryptedPaint;
    private static TextPaint onlinePaint;
    private static TextPaint offlinePaint;

    private static Drawable lockDrawable;
    private static Paint linePaint;

    private CharSequence currentName;
    private ImageReceiver avatarImage;
    private String subLabel;

    private ChatOrUserCellLayout cellLayout;
    private TLRPC.User user = null;
    private TLRPC.Chat chat = null;
    private TLRPC.EncryptedChat encryptedChat = null;

    private String lastName = null;
    private int lastStatus = 0;
    private TLRPC.FileLocation lastAvatar = null;

    public boolean usePadding = true;
    public boolean useBoldFont = false;
    public boolean useSeparator = false;
    public float drawAlpha = 1;

    public ChatOrUserCell(Context context) {
        super(context);
        init();
    }

    private void init() {
        if (namePaint == null) {
            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(Utilities.dp(18));
            namePaint.setColor(0xff222222);
        }

        if (nameEncryptedPaint == null) {
            nameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            nameEncryptedPaint.setTextSize(Utilities.dp(18));
            nameEncryptedPaint.setColor(0xff00a60e);
        }

        if (onlinePaint == null) {
            onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            onlinePaint.setTextSize(Utilities.dp(15));
            onlinePaint.setColor(0xff316f9f);
        }

        if (offlinePaint == null) {
            offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            offlinePaint.setTextSize(Utilities.dp(15));
            offlinePaint.setColor(0xff999999);
        }

        if (lockDrawable == null) {
            lockDrawable = getResources().getDrawable(R.drawable.ic_lock_green);
        }

        if (linePaint == null) {
            linePaint = new Paint();
            linePaint.setColor(0xffdcdcdc);
        }

        if (avatarImage == null) {
            avatarImage = new ImageReceiver();
            avatarImage.parentView = new WeakReference<View>(this);
        }

        if (cellLayout == null) {
            cellLayout = new ChatOrUserCellLayout();
        }
    }

    public void setData(TLRPC.User u, TLRPC.Chat c, TLRPC.EncryptedChat ec, CharSequence n, String s) {
        currentName = n;
        user = u;
        chat = c;
        encryptedChat = ec;
        subLabel = s;
        update(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Utilities.dp(64));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null && chat == null && encryptedChat == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        if (changed) {
            buildLayout();
        }
    }

    public void buildLayout() {
        cellLayout.build(getMeasuredWidth(), getMeasuredHeight());
    }

    public void update(int mask) {
        int placeHolderId = 0;
        TLRPC.FileLocation photo = null;
        if (user != null) {
            if (user.photo != null) {
                photo = user.photo.photo_small;
            }
            placeHolderId = Utilities.getUserAvatarForId(user.id);
        } else if (chat != null) {
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
            }
            placeHolderId = Utilities.getGroupAvatarForId(chat.id);
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && user != null || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 && chat != null) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null && lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0 && user != null) {
                int newStatus = 0;
                if (user.status != null) {
                    newStatus = user.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && ((mask & MessagesController.UPDATE_MASK_NAME) != 0 && user != null) || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 && chat != null) {
                String newName;
                if (user != null) {
                    newName = user.first_name + user.last_name;
                } else {
                    newName = chat.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }

            if (!continueUpdate) {
                return;
            }
        }

        if (user != null) {
            if (user.status != null) {
                lastStatus = user.status.expires;
            } else {
                lastStatus = 0;
            }
            lastName = user.first_name + user.last_name;
        } else if (chat != null) {
            lastName = chat.title;
        }


        lastAvatar = photo;
        avatarImage.setImage(photo, "50_50", placeHolderId == 0 ? null : getResources().getDrawable(placeHolderId));

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (user == null && chat == null && encryptedChat == null) {
            return;
        }

        if (cellLayout == null) {
            requestLayout();
            return;
        }

        if (drawAlpha != 1) {
            canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(), (int)(255 * drawAlpha), Canvas.HAS_ALPHA_LAYER_SAVE_FLAG);
        }

        if (cellLayout.drawNameLock) {
            setDrawableBounds(lockDrawable, cellLayout.nameLockLeft, cellLayout.nameLockTop);
            lockDrawable.draw(canvas);
        }

        canvas.save();
        canvas.translate(cellLayout.nameLeft, cellLayout.nameTop);
        cellLayout.nameLayout.draw(canvas);
        canvas.restore();

        if (cellLayout.onlineLayout != null) {
            canvas.save();
            canvas.translate(cellLayout.onlineLeft, cellLayout.onlineTop);
            cellLayout.onlineLayout.draw(canvas);
            canvas.restore();
        }

        avatarImage.draw(canvas, cellLayout.avatarLeft, cellLayout.avatarTop, Utilities.dp(50), Utilities.dp(50));

        if (useSeparator) {
            int h = getMeasuredHeight();
            if (!usePadding) {
                canvas.drawLine(0, h - 1, getMeasuredWidth(), h, linePaint);
            } else {
                canvas.drawLine(Utilities.dp(11), h - 1, getMeasuredWidth() - Utilities.dp(11), h, linePaint);
            }
        }
    }

    private class ChatOrUserCellLayout {
        private int nameLeft;
        private int nameTop;
        private int nameWidth;
        private StaticLayout nameLayout;
        private boolean drawNameLock;
        private int nameLockLeft;
        private int nameLockTop = Utilities.dp(15);

        private int onlineLeft;
        private int onlineTop = Utilities.dp(36);
        private int onlineWidth;
        private StaticLayout onlineLayout;

        private int avatarTop = Utilities.dp(7);
        private int avatarLeft;

        public void build(int width, int height) {
            CharSequence nameString = "";
            TextPaint currentNamePaint;

            if (encryptedChat != null) {
                drawNameLock = true;
                if (!Utilities.isRTL) {
                    nameLockLeft = Utilities.dp(61 + (usePadding ? 11 : 0));
                    nameLeft = Utilities.dp(65 + (usePadding ? 11 : 0)) + lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = width - Utilities.dp(63 + (usePadding ? 11 : 0)) - lockDrawable.getIntrinsicWidth();
                    nameLeft = usePadding ? Utilities.dp(11) : 0;
                }
            } else {
                drawNameLock = false;
                if (!Utilities.isRTL) {
                    nameLeft = Utilities.dp(61 + (usePadding ? 11 : 0));
                } else {
                    nameLeft = usePadding ? Utilities.dp(11) : 0;
                }
            }

            if (currentName != null) {
                nameString = currentName;
            } else {
                if (useBoldFont) {
                    if (user != null) {
                        if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                            nameString = Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>");
                        } else if (user.first_name.length() != 0) {
                            nameString = Html.fromHtml("<b>" + user.first_name + "</b>");
                        } else {
                            nameString = Html.fromHtml("<b>" + user.last_name + "</b>");
                        }
                    }
                } else {
                    String nameString2 = "";
                    if (chat != null) {
                        nameString2 = chat.title;
                    } else if (user != null) {
                        if (user.id / 1000 != 333 && ContactsController.Instance.contactsDict.get(user.id) == null) {
                            if (ContactsController.Instance.contactsDict.size() == 0 && ContactsController.Instance.loadingContacts) {
                                nameString2 = Utilities.formatName(user.first_name, user.last_name);
                            } else {
                                if (user.phone != null && user.phone.length() != 0) {
                                    nameString2 = PhoneFormat.Instance.format("+" + user.phone);
                                } else {
                                    nameString2 = Utilities.formatName(user.first_name, user.last_name);
                                }
                            }
                        } else {
                            nameString2 = Utilities.formatName(user.first_name, user.last_name);
                        }
                    }
                    nameString = nameString2.replace("\n", " ");
                }
            }
            if (nameString.length() == 0) {
                nameString = ApplicationLoader.applicationContext.getString(R.string.HiddenName);
            }
            if (encryptedChat != null) {
                currentNamePaint = nameEncryptedPaint;
            } else {
                currentNamePaint = namePaint;
            }

            if (!Utilities.isRTL) {
                onlineWidth = nameWidth = width - nameLeft - Utilities.dp(3 + (usePadding ? 11 : 0));
            } else {
                onlineWidth = nameWidth = width - nameLeft - Utilities.dp(61 + (usePadding ? 11 : 0));
            }
            if (drawNameLock) {
                nameWidth -= Utilities.dp(6) + lockDrawable.getIntrinsicWidth();
            }

            CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - Utilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            if (chat == null) {
                if (!Utilities.isRTL) {
                    onlineLeft = Utilities.dp(61 + (usePadding ? 11 : 0));
                } else {
                    onlineLeft = usePadding ? Utilities.dp(11) : 0;
                }

                String onlineString = "";
                TextPaint currentOnlinePaint = offlinePaint;

                if (subLabel != null) {
                    onlineString = subLabel;
                } else {
                    if (user != null) {
                        if (user.status == null) {
                            onlineString = getResources().getString(R.string.Offline);
                        } else {
                            int currentTime = ConnectionsManager.Instance.getCurrentTime();
                            if (user.id == UserConfig.clientUserId || user.status.expires > currentTime) {
                                currentOnlinePaint = onlinePaint;
                                onlineString = getResources().getString(R.string.Online);
                            } else {
                                if (user.status.expires <= 10000) {
                                    onlineString = getResources().getString(R.string.Invisible);
                                } else {
                                    onlineString = Utilities.formatDateOnline(user.status.expires);
                                }
                            }
                        }
                    }
                }

                CharSequence onlineStringFinal = TextUtils.ellipsize(onlineString, currentOnlinePaint, nameWidth - Utilities.dp(12), TextUtils.TruncateAt.END);
                onlineLayout = new StaticLayout(onlineStringFinal, currentOnlinePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                nameTop = Utilities.dp(12);
            } else {
                onlineLayout = null;
                nameTop = Utilities.dp(22);
            }

            if (!Utilities.isRTL) {
                avatarLeft = usePadding ? Utilities.dp(11) : 0;
            } else {
                avatarLeft = width - Utilities.dp(50 + (usePadding ? 11 : 0));
            }
            avatarImage.imageX = avatarLeft;
            avatarImage.imageY = avatarTop;
            avatarImage.imageW = Utilities.dp(50);
            avatarImage.imageH = Utilities.dp(50);

            double widthpx = 0;
            float left = 0;
            if (Utilities.isRTL) {
                if (nameLayout.getLineCount() > 0) {
                    left = nameLayout.getLineLeft(0);
                    if (left == 0) {
                        widthpx = Math.ceil(nameLayout.getLineWidth(0));
                        if (widthpx < nameWidth) {
                            nameLeft += (nameWidth - widthpx);
                        }
                    }
                }
                if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                    left = onlineLayout.getLineLeft(0);
                    if (left == 0) {
                        widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                        if (widthpx < onlineWidth) {
                            onlineLeft += (onlineWidth - widthpx);
                        }
                    }
                }
            } else {
                if (nameLayout.getLineCount() > 0) {
                    left = nameLayout.getLineRight(0);
                    if (left == nameWidth) {
                        widthpx = Math.ceil(nameLayout.getLineWidth(0));
                        if (widthpx < nameWidth) {
                            nameLeft -= (nameWidth - widthpx);
                        }
                    }
                }
                if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                    left = onlineLayout.getLineRight(0);
                    if (left == onlineWidth) {
                        widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                        if (widthpx < onlineWidth) {
                            onlineLeft -= (onlineWidth - widthpx);
                        }
                    }
                }
            }
        }
    }
}
