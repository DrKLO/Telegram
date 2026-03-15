package org.telegram.messenger.auto;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.car.app.model.CarIcon;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

import java.io.File;

final class AutoAvatarProvider {

    private static final int AVATAR_MAX_SIZE = 128;
    private static final String NAME_NOT_FOUND = "\0";

    private final int currentAccount;
    private final AccountInstance accountInstance;
    private final LongSparseArray<CarIcon> avatarCache = new LongSparseArray<>();
    private final LongSparseArray<CarIcon> rowAvatarCache = new LongSparseArray<>();
    private final LongSparseArray<IconCompat> senderIconCache = new LongSparseArray<>();
    private final LongSparseArray<String> nameCache = new LongSparseArray<>();
    private Person cachedSelfPerson;

    AutoAvatarProvider(int currentAccount, @NonNull AccountInstance accountInstance) {
        this.currentAccount = currentAccount;
        this.accountInstance = accountInstance;
    }

    void clearNames() {
        nameCache.clear();
    }

    void clearAvatars() {
        avatarCache.clear();
        rowAvatarCache.clear();
        senderIconCache.clear();
        cachedSelfPerson = null;
    }

    void clearAll() {
        clearNames();
        clearAvatars();
    }

    String resolveDialogName(long dialogId) {
        String cached = nameCache.get(dialogId);
        if (cached != null) {
            return NAME_NOT_FOUND.equals(cached) ? null : cached;
        }
        TLObject dialogObject = loadDialogObject(dialogId);
        String title = dialogObject != null ? DialogObject.getDialogTitle(dialogObject) : accountInstance.getMessagesController().getPeerName(dialogId);
        nameCache.put(dialogId, TextUtils.isEmpty(title) ? NAME_NOT_FOUND : title);
        return TextUtils.isEmpty(title) ? null : title;
    }

    CarIcon getDialogIcon(long dialogId) {
        CarIcon cached = avatarCache.get(dialogId);
        if (cached != null) {
            return cached;
        }
        TLObject dialogObject = loadDialogObject(dialogId);
        if (dialogObject == null) {
            return null;
        }

        TLRPC.FileLocation photoLocation = extractPhoto(dialogObject);
        if (photoLocation != null && photoLocation.volume_id != 0) {
            File avatarFile = FileLoader.getInstance(currentAccount).getPathToAttach(photoLocation, true);
            if (avatarFile != null && avatarFile.exists()) {
                Bitmap bitmap = decodeSizedBitmap(avatarFile.getAbsolutePath(), AVATAR_MAX_SIZE);
                if (bitmap != null) {
                    CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build();
                    avatarCache.put(dialogId, icon);
                    return icon;
                }
            }
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(dialogObject, ImageLocation.TYPE_SMALL);
            if (imageLocation != null) {
                FileLoader.getInstance(currentAccount).loadFile(
                        imageLocation, dialogObject, null, FileLoader.PRIORITY_NORMAL, 1);
            }
        }

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        DialogObject.setDialogPhotoTitle(null, avatarDrawable, dialogObject);
        Bitmap bitmap = renderAvatarDrawable(avatarDrawable);
        if (bitmap == null) {
            return null;
        }
        CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build();
        avatarCache.put(dialogId, icon);
        return icon;
    }

    CarIcon getRowDialogIcon(long dialogId) {
        CarIcon cached = rowAvatarCache.get(dialogId);
        if (cached != null) {
            return cached;
        }
        TLObject dialogObject = loadDialogObject(dialogId);
        if (dialogObject == null) {
            return null;
        }

        TLRPC.FileLocation photoLocation = extractPhoto(dialogObject);
        if (photoLocation != null && photoLocation.volume_id != 0) {
            File avatarFile = FileLoader.getInstance(currentAccount).getPathToAttach(photoLocation, true);
            if (avatarFile != null && avatarFile.exists()) {
                Bitmap bitmap = decodeSizedBitmap(avatarFile.getAbsolutePath(), AVATAR_MAX_SIZE);
                if (bitmap != null) {
                    Bitmap circularBitmap = circularCrop(bitmap);
                    if (circularBitmap != null) {
                        CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(circularBitmap)).build();
                        rowAvatarCache.put(dialogId, icon);
                        return icon;
                    }
                }
            }
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(dialogObject, ImageLocation.TYPE_SMALL);
            if (imageLocation != null) {
                FileLoader.getInstance(currentAccount).loadFile(
                        imageLocation, dialogObject, null, FileLoader.PRIORITY_NORMAL, 1);
            }
        }

        CarIcon fallback = getDialogIcon(dialogId);
        if (fallback != null) {
            rowAvatarCache.put(dialogId, fallback);
        }
        return fallback;
    }

    Person getSelfPerson() {
        if (cachedSelfPerson != null) {
            return cachedSelfPerson;
        }
        TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
        String name = self != null ? UserObject.getFirstName(self) : null;
        if (TextUtils.isEmpty(name)) {
            name = "You";
        }
        Person.Builder builder = new Person.Builder()
                .setName(name)
                .setKey("self_" + (self != null ? self.id : 0));
        if (self != null) {
            IconCompat icon = getSenderIcon(self.id, self);
            if (icon != null) {
                builder.setIcon(icon);
            }
        }
        cachedSelfPerson = builder.build();
        return cachedSelfPerson;
    }

    Person buildSenderPerson(TLRPC.User user, String fallbackName) {
        String name = !TextUtils.isEmpty(fallbackName) ? fallbackName : "User";
        Person.Builder builder = new Person.Builder()
                .setName(name)
                .setKey("user_" + (user != null ? user.id : 0));
        if (user != null) {
            IconCompat icon = getSenderIcon(user.id, user);
            if (icon != null) {
                builder.setIcon(icon);
            }
        }
        return builder.build();
    }

    Person buildSenderPerson(TLRPC.Chat chat, String fallbackName) {
        String name = !TextUtils.isEmpty(fallbackName) ? fallbackName : "Chat";
        return new Person.Builder()
                .setName(name)
                .setKey("chat_" + (chat != null ? chat.id : 0))
                .build();
    }

    private IconCompat getSenderIcon(long senderId, TLRPC.User user) {
        IconCompat cached = senderIconCache.get(senderId);
        if (cached != null) {
            return cached;
        }
        if (user != null && user.photo != null && user.photo.photo_small != null) {
            File avatarFile = FileLoader.getInstance(currentAccount)
                    .getPathToAttach(user.photo.photo_small, true);
            if (avatarFile != null && avatarFile.exists()) {
                Bitmap bitmap = decodeSizedBitmap(avatarFile.getAbsolutePath(), AVATAR_MAX_SIZE);
                if (bitmap != null) {
                    IconCompat icon = IconCompat.createWithBitmap(bitmap);
                    senderIconCache.put(senderId, icon);
                    return icon;
                }
            }
        }
        return null;
    }

    private TLObject loadDialogObject(long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = accountInstance.getMessagesController().getUser(dialogId);
            if (user == null) {
                user = accountInstance.getMessagesStorage().getUserSync(dialogId);
                if (user != null) {
                    accountInstance.getMessagesController().putUser(user, true);
                }
            }
            return user;
        }
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialogId);
            if (chat == null) {
                chat = accountInstance.getMessagesStorage().getChatSync(-dialogId);
                if (chat != null) {
                    accountInstance.getMessagesController().putChat(chat, true);
                }
            }
            return chat;
        }
        return null;
    }

    private static TLRPC.FileLocation extractPhoto(TLObject dialogObject) {
        if (dialogObject instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) dialogObject;
            return user.photo != null ? user.photo.photo_small : null;
        }
        if (dialogObject instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) dialogObject;
            return chat.photo != null ? chat.photo.photo_small : null;
        }
        return null;
    }

    private static Bitmap renderAvatarDrawable(AvatarDrawable avatarDrawable) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(AVATAR_MAX_SIZE, AVATAR_MAX_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            avatarDrawable.setBounds(0, 0, AVATAR_MAX_SIZE, AVATAR_MAX_SIZE);
            avatarDrawable.draw(canvas);
            return bitmap;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static Bitmap decodeSizedBitmap(String path, int maxSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = Math.max(1, Math.max(options.outWidth / maxSize, options.outHeight / maxSize));
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private static Bitmap circularCrop(Bitmap source) {
        try {
            int size = Math.min(source.getWidth(), source.getHeight());
            if (size <= 0) {
                return null;
            }
            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;
            Bitmap squared = Bitmap.createBitmap(source, x, y, size, size);
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            float radius = size / 2f;
            canvas.drawCircle(radius, radius, radius, paint);
            return output;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
