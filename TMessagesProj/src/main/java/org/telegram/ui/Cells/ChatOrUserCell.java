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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.android.ImageReceiver;

public class ChatOrUserCell extends BaseCell {
    private static TextPaint namePaint;
    private static TextPaint nameEncryptedPaint;
    private static TextPaint onlinePaint;
    private static TextPaint offlinePaint;

    private static Drawable lockDrawable;
    private static Drawable broadcastDrawable;
    private static Drawable groupDrawable;
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
    public boolean useSeparator = false;
    public float drawAlpha = 1;

    public ChatOrUserCell(Context context) {
        super(context);
        init();
    }

    private void init() {
        if (namePaint == null) {
            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(AndroidUtilities.dp(18));
            namePaint.setColor(0xff222222);
        }

        if (nameEncryptedPaint == null) {
            nameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            nameEncryptedPaint.setTextSize(AndroidUtilities.dp(18));
            nameEncryptedPaint.setColor(0xff00a60e);
        }

        if (onlinePaint == null) {
            onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            onlinePaint.setTextSize(AndroidUtilities.dp(15));
            onlinePaint.setColor(0xff316f9f);
        }

        if (offlinePaint == null) {
            offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            offlinePaint.setTextSize(AndroidUtilities.dp(15));
            offlinePaint.setColor(0xff999999);
        }

        if (lockDrawable == null) {
            lockDrawable = getResources().getDrawable(R.drawable.ic_lock_green);
        }

        if (linePaint == null) {
            linePaint = new Paint();
            linePaint.setColor(0xffdcdcdc);
        }

        if (broadcastDrawable == null) {
            broadcastDrawable = getResources().getDrawable(R.drawable.broadcast);
        }

        if (groupDrawable == null) {
            groupDrawable = getResources().getDrawable(R.drawable.grouplist);
        }

        if (avatarImage == null) {
            avatarImage = new ImageReceiver(this);
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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (avatarImage != null) {
            avatarImage.clearImage();
            lastAvatar = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(64));
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
            placeHolderId = AndroidUtilities.getUserAvatarForId(user.id);
        } else if (chat != null) {
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
            }
            if (chat.id > 0) {
                placeHolderId = AndroidUtilities.getGroupAvatarForId(chat.id);
            } else {
                placeHolderId = AndroidUtilities.getBroadcastAvatarForId(chat.id);
            }
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
        avatarImage.setImage(photo, "50_50", placeHolderId == 0 ? null : getResources().getDrawable(placeHolderId), false);

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
        } else if (cellLayout.drawNameGroup) {
            setDrawableBounds(groupDrawable, cellLayout.nameLockLeft, cellLayout.nameLockTop);
            groupDrawable.draw(canvas);
        } else if (cellLayout.drawNameBroadcast) {
            setDrawableBounds(broadcastDrawable, cellLayout.nameLockLeft, cellLayout.nameLockTop);
            broadcastDrawable.draw(canvas);
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

        avatarImage.draw(canvas, cellLayout.avatarLeft, cellLayout.avatarTop, AndroidUtilities.dp(50), AndroidUtilities.dp(50));

        if (useSeparator) {
            int h = getMeasuredHeight();
            if (!usePadding) {
                canvas.drawLine(0, h - 1, getMeasuredWidth(), h, linePaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(11), h - 1, getMeasuredWidth() - AndroidUtilities.dp(11), h, linePaint);
            }
        }
    }

    private class ChatOrUserCellLayout {
        private int nameLeft;
        private int nameTop;
        private int nameWidth;
        private StaticLayout nameLayout;
        private boolean drawNameLock;
        private boolean drawNameBroadcast;
        private boolean drawNameGroup;
        private int nameLockLeft;
        private int nameLockTop;

        private int onlineLeft;
        private int onlineTop = AndroidUtilities.dp(36);
        private int onlineWidth;
        private StaticLayout onlineLayout;

        private int avatarTop = AndroidUtilities.dp(7);
        private int avatarLeft;

        public void build(int width, int height) {
            CharSequence nameString = "";
            TextPaint currentNamePaint;

            drawNameBroadcast = false;
            drawNameLock = false;
            drawNameGroup = false;

            if (encryptedChat != null) {
                drawNameLock = true;
                if (!LocaleController.isRTL) {
                    nameLockLeft = AndroidUtilities.dp(61 + (usePadding ? 11 : 0));
                    nameLeft = AndroidUtilities.dp(65 + (usePadding ? 11 : 0)) + lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = width - AndroidUtilities.dp(63 + (usePadding ? 11 : 0)) - lockDrawable.getIntrinsicWidth();
                    nameLeft = usePadding ? AndroidUtilities.dp(11) : 0;
                }
                nameLockTop = AndroidUtilities.dp(15);
            } else {
                if (chat != null) {
                    nameLockTop = AndroidUtilities.dp(26);
                    if (chat.id < 0) {
                        drawNameBroadcast = true;
                    } else {
                        drawNameGroup = true;
                    }
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(61 + (usePadding ? 11 : 0));
                        nameLeft = AndroidUtilities.dp(65 + (usePadding ? 11 : 0)) + (drawNameGroup ? groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
                    } else {
                        nameLockLeft = width - AndroidUtilities.dp(63 + (usePadding ? 11 : 0)) - (drawNameGroup ? groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
                        nameLeft = usePadding ? AndroidUtilities.dp(11) : 0;
                    }
                } else {
                    if (!LocaleController.isRTL) {
                        nameLeft = AndroidUtilities.dp(61 + (usePadding ? 11 : 0));
                    } else {
                        nameLeft = usePadding ? AndroidUtilities.dp(11) : 0;
                    }
                }
            }

            if (currentName != null) {
                nameString = currentName;
            } else {
                String nameString2 = "";
                if (chat != null) {
                    nameString2 = chat.title;
                } else if (user != null) {
                    nameString2 = ContactsController.formatName(user.first_name, user.last_name);
                }
                nameString = nameString2.replace("\n", " ");
            }
            if (nameString.length() == 0) {
                if (user != null && user.phone != null && user.phone.length() != 0) {
                    nameString = PhoneFormat.getInstance().format("+" + user.phone);
                } else {
                    nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
                }
            }
            if (encryptedChat != null) {
                currentNamePaint = nameEncryptedPaint;
            } else {
                currentNamePaint = namePaint;
            }

            if (!LocaleController.isRTL) {
                onlineWidth = nameWidth = width - nameLeft - AndroidUtilities.dp(3 + (usePadding ? 11 : 0));
            } else {
                onlineWidth = nameWidth = width - nameLeft - AndroidUtilities.dp(61 + (usePadding ? 11 : 0));
            }
            if (drawNameLock) {
                nameWidth -= AndroidUtilities.dp(6) + lockDrawable.getIntrinsicWidth();
            } else if (drawNameBroadcast) {
                nameWidth -= AndroidUtilities.dp(6) + broadcastDrawable.getIntrinsicWidth();
            }

            CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            if (chat == null) {
                if (!LocaleController.isRTL) {
                    onlineLeft = AndroidUtilities.dp(61 + (usePadding ? 11 : 0));
                } else {
                    onlineLeft = usePadding ? AndroidUtilities.dp(11) : 0;
                }

                String onlineString = "";
                TextPaint currentOnlinePaint = offlinePaint;

                if (subLabel != null) {
                    onlineString = subLabel;
                } else {
                    onlineString = LocaleController.formatUserStatus(user);
                    if (user != null && (user.id == UserConfig.getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance().getCurrentTime())) {
                        currentOnlinePaint = onlinePaint;
                        onlineString = LocaleController.getString("Online", R.string.Online);
                    }
                }

                CharSequence onlineStringFinal = TextUtils.ellipsize(onlineString, currentOnlinePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
                onlineLayout = new StaticLayout(onlineStringFinal, currentOnlinePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                nameTop = AndroidUtilities.dp(12);
            } else {
                onlineLayout = null;
                nameTop = AndroidUtilities.dp(22);
            }

            if (!LocaleController.isRTL) {
                avatarLeft = usePadding ? AndroidUtilities.dp(11) : 0;
            } else {
                avatarLeft = width - AndroidUtilities.dp(50 + (usePadding ? 11 : 0));
            }
            avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(50), AndroidUtilities.dp(50));


            double widthpx = 0;
            float left = 0;
            if (LocaleController.isRTL) {
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
