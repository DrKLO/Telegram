/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class GroupCreateSpan extends View {

    private long uid;
    private String key;
    private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable deleteDrawable;
    private RectF rect = new RectF();
    private ImageReceiver imageReceiver;
    private StaticLayout nameLayout;
    private AvatarDrawable avatarDrawable;
    private ContactsController.Contact currentContact;
    private int textWidth;
    private float textX;
    private float progress;
    private boolean deleting;
    private long lastUpdateTime;
    private int[] colors = new int[8];
    private Theme.ResourcesProvider resourcesProvider;
    private boolean small;
    private boolean drawAvatarBackground = true;

    public GroupCreateSpan(Context context, Object object) {
        this(context, object, null);
    }

    public GroupCreateSpan(Context context, ContactsController.Contact contact) {
        this(context, null, contact);
    }

    public GroupCreateSpan(Context context, Object object, ContactsController.Contact contact) {
        this(context, object, contact, null);
    }

    public GroupCreateSpan(Context context, Object object, ContactsController.Contact contact, Theme.ResourcesProvider resourcesProvider) {
        this(context, object, contact, false, resourcesProvider);
    }

    public GroupCreateSpan(Context context, Object object, ContactsController.Contact contact, boolean small, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.small = small;

        currentContact = contact;
        deleteDrawable = getResources().getDrawable(R.drawable.delete);
        textPaint.setTextSize(AndroidUtilities.dp(small ? 13 : 14));

        String firstName;

        ImageLocation imageLocation;
        Object imageParent;

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(20));
        if (object instanceof String) {
            imageLocation = null;
            imageParent = null;
            String str = (String) object;
            avatarDrawable.setScaleSize(.8f);
            switch (str) {
                case "contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                    uid = Long.MIN_VALUE;
                    firstName = LocaleController.getString("FilterContacts", R.string.FilterContacts);
                    break;
                case "non_contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS);
                    uid = Long.MIN_VALUE + 1;
                    firstName = LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts);
                    break;
                case "groups":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                    uid = Long.MIN_VALUE + 2;
                    firstName = LocaleController.getString("FilterGroups", R.string.FilterGroups);
                    break;
                case "channels":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                    uid = Long.MIN_VALUE + 3;
                    firstName = LocaleController.getString("FilterChannels", R.string.FilterChannels);
                    break;
                case "bots":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_BOTS);
                    uid = Long.MIN_VALUE + 4;
                    firstName = LocaleController.getString("FilterBots", R.string.FilterBots);
                    break;
                case "muted":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_MUTED);
                    uid = Long.MIN_VALUE + 5;
                    firstName = LocaleController.getString("FilterMuted", R.string.FilterMuted);
                    break;
                case "read":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_READ);
                    uid = Long.MIN_VALUE + 6;
                    firstName = LocaleController.getString("FilterRead", R.string.FilterRead);
                    break;
                case "existing_chats":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_EXISTING_CHATS);
                    uid = Long.MIN_VALUE + 8;
                    firstName = LocaleController.getString(R.string.FilterExistingChats);
                    break;
                case "new_chats":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_NEW_CHATS);
                    uid = Long.MIN_VALUE + 9;
                    firstName = LocaleController.getString(R.string.FilterNewChats);
                    break;
                case "archived":
                default:
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED);
                    uid = Long.MIN_VALUE + 7;
                    firstName = LocaleController.getString("FilterArchived", R.string.FilterArchived);
                    break;
            }
        } else if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            uid = user.id;
            if (UserObject.isReplyUser(user)) {
                firstName = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageLocation = null;
                imageParent = null;
            } else if (UserObject.isUserSelf(user)) {
                firstName = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageLocation = null;
                imageParent = null;
            } else {
                avatarDrawable.setInfo(user);
                firstName = UserObject.getFirstName(user);
                int index;
                if ((index = firstName.indexOf(' ')) >= 0) {
                    firstName = firstName.substring(0, index);
                }
                imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
                imageParent = user;
            }
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            avatarDrawable.setInfo(chat);
            uid = -chat.id;
            firstName = chat.title;
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            imageParent = chat;
        } else if (object instanceof TLRPC.TL_help_country) {
            TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
            String flag = LocaleController.getLanguageFlag(country.iso2);
            firstName = country.default_name;
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_COUNTRY);
            avatarDrawable.setTextSize(AndroidUtilities.dp(24));
            avatarDrawable.setInfo(0, flag, null, null);
            avatarDrawable.setColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), 0.7f));
            avatarDrawable.setDrawAvatarBackground(drawAvatarBackground = false);
            uid = country.default_name.hashCode();
            imageLocation = null;
            imageParent = null;
        } else {
            avatarDrawable.setInfo(0, contact.first_name, contact.last_name);
            uid = contact.contact_id;
            key = contact.key;
            if (!TextUtils.isEmpty(contact.first_name)) {
                firstName = contact.first_name;
            } else {
                firstName = contact.last_name;
            }
            imageLocation = null;
            imageParent = null;
        }

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(drawAvatarBackground ? 0 : AndroidUtilities.dp(4), 0, AndroidUtilities.dp(small ? 28 : 32), AndroidUtilities.dp(small ? 28 : 32));

        int maxNameWidth;
        if (AndroidUtilities.isTablet()) {
            maxNameWidth = AndroidUtilities.dp(530 - (small ? 28 : 32) - 18 - 57 * 2) / 2;
        } else {
            maxNameWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp((small ? 28 : 32) + 18 + 57 * 2)) / 2;
        }

        firstName = firstName.replace('\n', ' ');
        CharSequence name = firstName;
        name = Emoji.replaceEmoji(name, textPaint.getFontMetricsInt(), AndroidUtilities.dp(12), false);
        name = TextUtils.ellipsize(name, textPaint, maxNameWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(name, textPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        if (nameLayout.getLineCount() > 0) {
            textWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
            textX = -nameLayout.getLineLeft(0);
        }
        imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1);
        updateColors();

        NotificationCenter.listenEmojiLoading(this);
    }

    public void updateColors() {
        int color = avatarDrawable.getColor();
        int back = Theme.getColor(Theme.key_groupcreate_spanBackground, resourcesProvider);
        int delete = Theme.getColor(Theme.key_groupcreate_spanDelete, resourcesProvider);
        colors[0] = Color.red(back);
        colors[1] = Color.red(color);
        colors[2] = Color.green(back);
        colors[3] = Color.green(color);
        colors[4] = Color.blue(back);
        colors[5] = Color.blue(color);
        colors[6] = Color.alpha(back);
        colors[7] = Color.alpha(color);
        deleteDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
        backPaint.setColor(back);
    }

    public boolean isDeleting() {
        return deleting;
    }

    public void startDeleteAnimation() {
        if (deleting) {
            return;
        }
        deleting = true;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void cancelDeleteAnimation() {
        if (!deleting) {
            return;
        }
        deleting = false;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public long getUid() {
        return uid;
    }

    public String getKey() {
        return key;
    }

    public ContactsController.Contact getContact() {
        return currentContact;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp((small ? 28 - 8 : 32) + 25) + textWidth, AndroidUtilities.dp(small ? 28 : 32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (deleting && progress != 1.0f || !deleting && progress != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if (deleting) {
                progress += dt / 120.0f;
                if (progress >= 1.0f) {
                    progress = 1.0f;
                }
            } else {
                progress -= dt / 120.0f;
                if (progress < 0.0f) {
                    progress = 0.0f;
                }
            }
            invalidate();
        }
        canvas.save();
        rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(small ? 28 : 32));
        backPaint.setColor(Color.argb(colors[6] + (int) ((colors[7] - colors[6]) * progress), colors[0] + (int) ((colors[1] - colors[0]) * progress), colors[2] + (int) ((colors[3] - colors[2]) * progress), colors[4] + (int) ((colors[5] - colors[4]) * progress)));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(small ? 14 : 16), AndroidUtilities.dp(small ? 14 : 16), backPaint);
        if (progress != 1f) {
            imageReceiver.draw(canvas);
        }
        if (progress != 0) {
            int color = avatarDrawable.getColor();
            float alpha = Color.alpha(color) / 255.0f;
            backPaint.setColor(color);
            backPaint.setAlpha((int) (255 * progress * alpha));
            canvas.drawCircle(AndroidUtilities.dp(small ? 14 : 16), AndroidUtilities.dp(small ? 14 : 16), AndroidUtilities.dp(small ? 14 : 16), backPaint);
            canvas.save();
            canvas.rotate(45 * (1.0f - progress), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            deleteDrawable.setBounds(AndroidUtilities.dp(small ? 9 : 11), AndroidUtilities.dp(small ? 9 : 11), AndroidUtilities.dp(small ? 19 : 21), AndroidUtilities.dp(small ? 19 : 21));
            deleteDrawable.setAlpha((int) (255 * progress));
            deleteDrawable.draw(canvas);
            canvas.restore();
        }
        canvas.translate(textX + AndroidUtilities.dp((small ? 26 : 32) + 9), AndroidUtilities.dp(small ? 6 : 8));
        int text = Theme.getColor(Theme.key_groupcreate_spanText, resourcesProvider);
        int textSelected = Theme.getColor(Theme.key_avatar_text, resourcesProvider);
        textPaint.setColor(ColorUtils.blendARGB(text, textSelected, progress));

        nameLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(nameLayout.getText());
        if (isDeleting() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId(), LocaleController.getString("Delete", R.string.Delete)));
    }
}
