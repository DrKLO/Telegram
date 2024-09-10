/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class AvatarDrawable extends Drawable {

    private TextPaint namePaint;
    private boolean hasGradient;
    private boolean hasAdvancedGradient;
    private int color, color2;
    private GradientTools advancedGradient;
    private boolean needApplyColorAccent;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private boolean isProfile;
    private boolean drawDeleted;
    private int avatarType;
    private float archivedAvatarProgress;
    private float scaleSize = 1f;
    private StringBuilder stringBuilder = new StringBuilder(5);
    private int roundRadius = -1;

    private int gradientTop, gradientBottom;
    private int gradientColor1, gradientColor2;
    private LinearGradient gradient;

    private int gradientTop2, gradientBottom2;
    private int gradientColor21, gradientColor22;
    private LinearGradient gradient2;
    private boolean drawAvatarBackground = true;
    private boolean rotate45Background = false;

    public static final int AVATAR_TYPE_NORMAL = 0;
    public static final int AVATAR_TYPE_SAVED = 1;
    public static final int AVATAR_TYPE_ARCHIVED = 2;
    public static final int AVATAR_TYPE_SHARES = 3;
    public static final int AVATAR_TYPE_REPLIES = 12;

    public static final int AVATAR_TYPE_FILTER_CONTACTS = 4;
    public static final int AVATAR_TYPE_FILTER_NON_CONTACTS = 5;
    public static final int AVATAR_TYPE_FILTER_GROUPS = 6;
    public static final int AVATAR_TYPE_FILTER_CHANNELS = 7;
    public static final int AVATAR_TYPE_FILTER_BOTS = 8;
    public static final int AVATAR_TYPE_FILTER_MUTED = 9;
    public static final int AVATAR_TYPE_FILTER_READ = 10;
    public static final int AVATAR_TYPE_FILTER_ARCHIVED = 11;
    public static final int AVATAR_TYPE_REGISTER = 13;
    public static final int AVATAR_TYPE_OTHER_CHATS = 14;
    public static final int AVATAR_TYPE_CLOSE_FRIENDS = 15;
    public static final int AVATAR_TYPE_GIFT = 16;
    public static final int AVATAR_TYPE_COUNTRY = 17;
    public static final int AVATAR_TYPE_UNCLAIMED = 18;
    public static final int AVATAR_TYPE_TO_BE_DISTRIBUTED = 19;
    public static final int AVATAR_TYPE_STORY = 20;
    public static final int AVATAR_TYPE_ANONYMOUS = 21;
    public static final int AVATAR_TYPE_MY_NOTES = 22;
    public static final int AVATAR_TYPE_EXISTING_CHATS = 23;
    public static final int AVATAR_TYPE_NEW_CHATS = 24;
    public static final int AVATAR_TYPE_PREMIUM = 25;
    public static final int AVATAR_TYPE_STARS = 26;

    /**
     * Matches {@link org.telegram.ui.Components.AvatarConstructorFragment#defaultColors}
     * but reordered to preserve color tints.
     */
    public static final int[][] advancedGradients = new int[][]{
            new int[]{0xFFF64884, 0xFFEF5B41, 0xFFF6A730, 0xFFFF7742},
            new int[]{0xFFF5694E, 0xFFF5772C, 0xFFFFD412, 0xFFFFA743},
            new int[]{0xFF837CFF, 0xFFB063FF, 0xFFFF72A9, 0xFFE269FF},
            new int[]{0xFF09D260, 0xFF5EDC40, 0xFFC1E526, 0xFF80DF2B},
            new int[]{0xFF5EB6FB, 0xFF1FCEEB, 0xFF45F7B7, 0xFF1FF1D9},
            new int[]{0xFF4D8DFF, 0xFF2BBFFF, 0xFF20E2CD, 0xFF0EE1F1},
            new int[]{0xFFF94BA0, 0xFFFB5C80, 0xFFFFB23A, 0xFFFE7E62},
    };

    private int alpha = 255;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean invalidateTextLayout;

    public AvatarDrawable() {
        this((Theme.ResourcesProvider) null);
    }

    public AvatarDrawable(Theme.ResourcesProvider resourcesProvider) {
        super();
        this.resourcesProvider = resourcesProvider;
        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTypeface(AndroidUtilities.bold());
        namePaint.setTextSize(dp(18));
    }

    public AvatarDrawable(TLRPC.User user) {
        this(user, false);
    }

    public AvatarDrawable(TLRPC.Chat chat) {
        this(chat, false);
    }

    public AvatarDrawable(TLRPC.User user, boolean profile) {
        this();
        isProfile = profile;
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, null);
            drawDeleted = UserObject.isDeleted(user);
        }
    }

    public AvatarDrawable(TLRPC.Chat chat, boolean profile) {
        this();
        isProfile = profile;
        setInfo(chat);
    }

    public void setDrawAvatarBackground(boolean drawAvatarBackground) {
        this.drawAvatarBackground = drawAvatarBackground;
    }

    public void setProfile(boolean value) {
        isProfile = value;
    }

    public static int getPeerColorIndex(int color) {
        float[] tempHSV = Theme.getTempHsv(5);
        Color.colorToHSV(color, tempHSV);
        final int hue = (int) tempHSV[0];
        if (hue >= 345 || hue < 29) return 0; // red
        if (hue < 67) return 1; // orange
        if (hue < 140) return 3; // green
        if (hue < 199) return 4; // cyan
        if (hue < 234) return 5; // blue
        if (hue < 301) return 2; // violet
        return 6; // pink
    }

    public static int getColorIndex(long id) {
        return (int) Math.abs(id % Theme.keys_avatar_background.length);
    }

    public static int getColorForId(long id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public static int getButtonColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider);
    }

    public static int getIconColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_actionBarIconBlue, resourcesProvider);
    }

    public static int getProfileColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)], resourcesProvider);
    }

    public static int getProfileTextColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_subtitleInProfileBlue, resourcesProvider);
    }

    public static int getProfileBackColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_backgroundActionBarBlue, resourcesProvider);
    }

    public static String colorName(int color) {
        final int[] resIds = new int[] { R.string.ColorRed, R.string.ColorOrange, R.string.ColorViolet, R.string.ColorGreen, R.string.ColorCyan, R.string.ColorBlue, R.string.ColorPink };
        return LocaleController.getString(resIds[color % resIds.length]);
    }

    public static int getNameColorNameForId(long id) {
        return Theme.keys_avatar_nameInMessage[getColorIndex(id)];
    }

    public void setInfo(TLRPC.User user) {
        setInfo(UserConfig.selectedAccount, user);
    }

    public void setInfo(int currentAccount, TLRPC.User user) {
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, null, user != null && user.color != null ? UserObject.getColorId(user) : null, UserObject.getPeerColorForAvatar(currentAccount, user));
            drawDeleted = UserObject.isDeleted(user);
        }
    }

    public void setInfo(TLObject object) {
        if (object instanceof TLRPC.User) {
            setInfo((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            setInfo((TLRPC.Chat) object);
        } else if (object instanceof TLRPC.ChatInvite) {
            setInfo((TLRPC.ChatInvite) object);
        }
    }

    public void setInfo(int currentAccount, TLObject object) {
        if (object instanceof TLRPC.User) {
            setInfo(currentAccount, (TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            setInfo(currentAccount, (TLRPC.Chat) object);
        } else if (object instanceof TLRPC.ChatInvite) {
            setInfo(currentAccount, (TLRPC.ChatInvite) object);
        }
    }

    public void setScaleSize(float value) {
        scaleSize = value;
    }

    public void setAvatarType(int value) {
        avatarType = value;
        rotate45Background = false;
        hasAdvancedGradient = false;
        hasGradient = false;
        if (avatarType == AVATAR_TYPE_REGISTER) {
            color = color2 = Theme.getColor(Theme.key_chats_actionBackground);
        } else if (avatarType == AVATAR_TYPE_ARCHIVED) {
            color = color2 = getThemedColor(Theme.key_avatar_backgroundArchivedHidden);
        } else if (avatarType == AVATAR_TYPE_REPLIES || avatarType == AVATAR_TYPE_SAVED || avatarType == AVATAR_TYPE_OTHER_CHATS) {
            hasGradient = true;
            color = getThemedColor(Theme.key_avatar_backgroundSaved);
            color2 = getThemedColor(Theme.key_avatar_background2Saved);
        } else if (avatarType == AVATAR_TYPE_STORY) {
            rotate45Background = true;
            hasGradient = true;
            color = getThemedColor(Theme.key_stories_circle1);
            color2 = getThemedColor(Theme.key_stories_circle2);
        } else if (avatarType == AVATAR_TYPE_SHARES) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(5)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(5)]);
        } else if (avatarType == AVATAR_TYPE_PREMIUM) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(2)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(2)]);
        } else if (avatarType == AVATAR_TYPE_STARS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(1)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(1)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_CONTACTS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(5)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(5)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_NON_CONTACTS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(4)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(4)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_GROUPS || avatarType == AVATAR_TYPE_EXISTING_CHATS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(3)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(3)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_CHANNELS || avatarType == AVATAR_TYPE_NEW_CHATS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(1)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(1)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_BOTS) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(0)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(0)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_MUTED) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(6)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(6)]);
        } else if (avatarType == AVATAR_TYPE_FILTER_READ) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(5)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(5)]);
        } else if (avatarType == AVATAR_TYPE_COUNTRY) {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(5)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(5)]);
        } else if (avatarType == AVATAR_TYPE_ANONYMOUS) {
            hasAdvancedGradient = true;
            if (advancedGradient == null) {
                advancedGradient = new GradientTools();
            }
            advancedGradient.setColors(0xFF837CFF, 0xFFB063FF, 0xFFFF72A9, 0xFFE269FF);
        } else if (avatarType == AVATAR_TYPE_MY_NOTES) {
            hasAdvancedGradient = true;
            if (advancedGradient == null) {
                advancedGradient = new GradientTools();
            }
            advancedGradient.setColors(0xFF4D8DFF, 0xFF2BBFFF, 0xFF20E2CD, 0xFF0EE1F1);
        } else {
            hasGradient = true;
            color = getThemedColor(Theme.keys_avatar_background[getColorIndex(4)]);
            color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(4)]);
        }
        needApplyColorAccent = avatarType != AVATAR_TYPE_ARCHIVED && avatarType != AVATAR_TYPE_SAVED && avatarType != AVATAR_TYPE_STORY && avatarType != AVATAR_TYPE_ANONYMOUS && avatarType != AVATAR_TYPE_REPLIES && avatarType != AVATAR_TYPE_OTHER_CHATS;
    }

    public void setArchivedAvatarHiddenProgress(float progress) {
        archivedAvatarProgress = progress;
    }

    public int getAvatarType() {
        return avatarType;
    }

    public void setInfo(TLRPC.Chat chat) {
        setInfo(UserConfig.selectedAccount, chat);
    }
    public void setInfo(int currentAccount, TLRPC.Chat chat) {
        if (chat != null) {
            setInfo(chat.id, chat.title, null, null, chat != null && chat.color != null ? ChatObject.getColorId(chat) : null, ChatObject.getPeerColorForAvatar(currentAccount, chat));
        }
    }

    public void setInfo(TLRPC.ChatInvite chat) {
        setInfo(UserConfig.selectedAccount, chat);
    }
    public void setInfo(int currentAccount, TLRPC.ChatInvite chat) {
        if (chat != null) {
            setInfo(0, chat.title, null, null, chat.chat != null && chat.chat.color != null ? ChatObject.getColorId(chat.chat) : null, ChatObject.getPeerColorForAvatar(currentAccount, chat.chat));
        }
    }

    public void setColor(int value) {
        hasGradient = false;
        hasAdvancedGradient = false;
        color = color2 = value;
        needApplyColorAccent = false;
    }

    public void setColor(int value, int value2) {
        hasGradient = true;
        hasAdvancedGradient = false;
        color = value;
        color2 = value2;
        needApplyColorAccent = false;
    }

    public void setTextSize(int size) {
        namePaint.setTextSize(size);
    }

    public void setInfo(long id, String firstName, String lastName) {
        setInfo(id, firstName, lastName, null, null, null);
    }

    public int getColor() {
        return needApplyColorAccent ? Theme.changeColorAccent(color) : color;
    }

    public int getColor2() {
        return needApplyColorAccent ? Theme.changeColorAccent(color2) : color2;
    }

    private static String takeFirstCharacter(String text) {
        ArrayList<Emoji.EmojiSpanRange> ranges = Emoji.parseEmojis(text);
        if (ranges != null && !ranges.isEmpty() && ranges.get(0).start == 0) {
            return text.substring(0, ranges.get(0).end);
        }
        return text.substring(0, text.offsetByCodePoints(0, Math.min(text.codePointCount(0, text.length()), 1)));
    }

    public void setInfo(long id, String firstName, String lastName, String custom) {
        setInfo(id, firstName, lastName, custom, null, null);
    }

    public void setInfo(long id, String firstName, String lastName, String custom, Integer customColor, MessagesController.PeerColor profileColor) {
        setInfo(id, firstName, lastName, custom, customColor, profileColor, false);
    }

    public void setInfo(long id, String firstName, String lastName, String custom, Integer customColor, MessagesController.PeerColor profileColor, boolean advancedGradient) {
        invalidateTextLayout = true;
        if (advancedGradient) {
            hasGradient = false;
            hasAdvancedGradient = true;
            if (this.advancedGradient == null) {
                this.advancedGradient = new GradientTools();
            }
        } else {
            hasGradient = true;
            hasAdvancedGradient = false;
        }

        if (profileColor != null) {
            if (advancedGradient) {
                int[] gradient = advancedGradients[getPeerColorIndex(profileColor.getAvatarColor1())];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = profileColor.getAvatarColor1();
                color2 = profileColor.getAvatarColor2();
            }
        } else if (customColor != null) {
            setPeerColor(customColor);
        } else {
            if (advancedGradient) {
                int[] gradient = advancedGradients[getColorIndex(id)];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
        needApplyColorAccent = id == 5; // Tinting manually set blue color


        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;

        if (firstName == null || firstName.length() == 0) {
            firstName = lastName;
            lastName = null;
        }

        getAvatarSymbols(firstName, lastName, custom, stringBuilder);
    }

    public void setPeerColor(int id) {
        if (advancedGradient != null) {
            hasGradient = false;
            hasAdvancedGradient = true;
        } else {
            hasGradient = true;
            hasAdvancedGradient = false;
        }
        if (id >= 14) {
            MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
            if (messagesController != null && messagesController.peerColors != null && messagesController.peerColors.getColor(id) != null) {
                final int peerColor = messagesController.peerColors.getColor(id).getColor1();
                if (advancedGradient != null) {
                    int[] gradient = advancedGradients[getPeerColorIndex(peerColor)];
                    this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
                } else {
                    color = getThemedColor(Theme.keys_avatar_background[getPeerColorIndex(peerColor)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[getPeerColorIndex(peerColor)]);
                }
            } else {
                if (advancedGradient != null) {
                    int[] gradient = advancedGradients[getColorIndex(id)];
                    this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
                } else {
                    color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
                }
            }
        } else {
            if (advancedGradient != null) {
                int[] gradient = advancedGradients[getColorIndex(id)];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
    }

    public void setText(String text) {
        invalidateTextLayout = true;
        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        getAvatarSymbols(text, null, null, stringBuilder);
    }

    public static void getAvatarSymbols(String firstName, String lastName, String custom, StringBuilder result) {
        result.setLength(0);
        if (custom != null) {
            result.append(custom);
        } else {
            if (firstName != null && firstName.length() > 0) {
                result.append(takeFirstCharacter(firstName));
            }
            if (lastName != null && lastName.length() > 0) {
                String lastNameLastWord = lastName;
                int index;
                if ((index = lastNameLastWord.lastIndexOf(' ')) >= 0) {
                    lastNameLastWord = lastNameLastWord.substring(index + 1);
                }
                if (Build.VERSION.SDK_INT > 17) {
                    result.append("\u200C");
                }
                result.append(takeFirstCharacter(lastNameLastWord));
            } else if (firstName != null && firstName.length() > 0) {
                for (int a = firstName.length() - 1; a >= 0; a--) {
                    if (firstName.charAt(a) == ' ') {
                        if (a != firstName.length() - 1 && firstName.charAt(a + 1) != ' ') {
                            int index = result.length();
                            if (Build.VERSION.SDK_INT > 17) {
                                result.append("\u200C");
                            }
                            result.append(takeFirstCharacter(firstName.substring(index)));
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        namePaint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_avatar_text), alpha));
        Paint backgroundPaint = Theme.avatar_backgroundPaint;
        if (hasAdvancedGradient && advancedGradient != null) {
            advancedGradient.setBounds(bounds.left, bounds.top, bounds.left + size, bounds.top + size);
            backgroundPaint = advancedGradient.paint;
        } else if (hasGradient) {
            int color = ColorUtils.setAlphaComponent(getColor(), alpha);
            int color2 = ColorUtils.setAlphaComponent(getColor2(), alpha);
            if (gradient == null || gradientBottom != bounds.height() || gradientColor1 != color || gradientColor2 != color2) {
                gradient = new LinearGradient(0, 0, 0, gradientBottom = bounds.height(), gradientColor1 = color, gradientColor2 = color2, Shader.TileMode.CLAMP);
            }
            backgroundPaint.setShader(gradient);
            backgroundPaint.setAlpha(alpha);
        } else {
            backgroundPaint.setShader(null);
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(getColor(), alpha));
        }
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        if (drawAvatarBackground) {
            if (rotate45Background) {
                canvas.save();
                canvas.rotate(-45, size / 2.0f, size / 2.0f);
            }
            if (roundRadius > 0) {
                AndroidUtilities.rectTmp.set(0, 0, size, size);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, roundRadius, roundRadius, backgroundPaint);
            } else {
                canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, backgroundPaint);
            }
            if (rotate45Background) {
                canvas.restore();
            }
        }

        if (avatarType == AVATAR_TYPE_ARCHIVED) {
            if (archivedAvatarProgress != 0) {
                backgroundPaint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_avatar_backgroundArchived), alpha));
                canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f * archivedAvatarProgress, backgroundPaint);
                if (Theme.dialogs_archiveAvatarDrawableRecolored) {
                    Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                    Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawableRecolored = false;
                }
            } else {
                if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
                    Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", color);
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", color);
                    Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawableRecolored = true;
                }
            }
            int w = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();
            int h = Theme.dialogs_archiveAvatarDrawable.getIntrinsicHeight();
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            canvas.save();
            Theme.dialogs_archiveAvatarDrawable.setBounds(x, y, x + w, y + h);
            Theme.dialogs_archiveAvatarDrawable.draw(canvas);
            canvas.restore();
        } else if (avatarType != 0) {
            Drawable drawable;

            if (avatarType == AVATAR_TYPE_SAVED) {
                drawable = Theme.avatarDrawables[0];
            } else if (avatarType == AVATAR_TYPE_FILTER_CONTACTS) {
                drawable = Theme.avatarDrawables[2];
            } else if (avatarType == AVATAR_TYPE_FILTER_NON_CONTACTS) {
                drawable = Theme.avatarDrawables[3];
            } else if (avatarType == AVATAR_TYPE_FILTER_GROUPS) {
                drawable = Theme.avatarDrawables[4];
            } else if (avatarType == AVATAR_TYPE_FILTER_CHANNELS) {
                drawable = Theme.avatarDrawables[5];
            } else if (avatarType == AVATAR_TYPE_FILTER_BOTS) {
                drawable = Theme.avatarDrawables[6];
            } else if (avatarType == AVATAR_TYPE_FILTER_MUTED) {
                drawable = Theme.avatarDrawables[7];
            } else if (avatarType == AVATAR_TYPE_FILTER_READ) {
                drawable = Theme.avatarDrawables[8];
            } else if (avatarType == AVATAR_TYPE_SHARES) {
                drawable = Theme.avatarDrawables[10];
            } else if (avatarType == AVATAR_TYPE_REPLIES) {
                drawable = Theme.avatarDrawables[11];
            } else if (avatarType == AVATAR_TYPE_OTHER_CHATS) {
                drawable = Theme.avatarDrawables[12];
            } else if (avatarType == AVATAR_TYPE_CLOSE_FRIENDS) {
                drawable = Theme.avatarDrawables[13];
            } else if (avatarType == AVATAR_TYPE_GIFT) {
                drawable = Theme.avatarDrawables[14];
            } else if (avatarType == AVATAR_TYPE_TO_BE_DISTRIBUTED) {
                drawable = Theme.avatarDrawables[15];
            } else if (avatarType == AVATAR_TYPE_UNCLAIMED) {
                drawable = Theme.avatarDrawables[16];
            } else if (avatarType == AVATAR_TYPE_STORY) {
                drawable = Theme.avatarDrawables[17];
            } else if (avatarType == AVATAR_TYPE_ANONYMOUS) {
                drawable = Theme.avatarDrawables[18];
            } else if (avatarType == AVATAR_TYPE_MY_NOTES) {
                drawable = Theme.avatarDrawables[19];
            } else if (avatarType == AVATAR_TYPE_EXISTING_CHATS) {
                drawable = Theme.avatarDrawables[21];
            } else if (avatarType == AVATAR_TYPE_NEW_CHATS) {
                drawable = Theme.avatarDrawables[20];
            } else if (avatarType == AVATAR_TYPE_PREMIUM) {
                drawable = Theme.avatarDrawables[22];
            } else if (avatarType == AVATAR_TYPE_STARS) {
                drawable = Theme.avatarDrawables[23];
            } else {
                drawable = Theme.avatarDrawables[9];
            }
            if (drawable != null) {
                int w = (int) (drawable.getIntrinsicWidth() * scaleSize);
                int h = (int) (drawable.getIntrinsicHeight() * scaleSize);
                int x = (size - w) / 2;
                int y = (size - h) / 2;
                drawable.setBounds(x, y, x + w, y + h);
                if (alpha != 255) {
                    drawable.setAlpha(alpha);
                    drawable.draw(canvas);
                    drawable.setAlpha(255);
                } else {
                    drawable.draw(canvas);
                }
            }
        } else if (drawDeleted && Theme.avatarDrawables[1] != null) {
            int w = Theme.avatarDrawables[1].getIntrinsicWidth();
            int h = Theme.avatarDrawables[1].getIntrinsicHeight();
            if (w > size - dp(6) || h > size - dp(6)) {
                float scale = size / (float) dp(50);
                w *= scale;
                h *= scale;
            }
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            Theme.avatarDrawables[1].setBounds(x, y, x + w, y + h);
            Theme.avatarDrawables[1].draw(canvas);
        } else {
            if (invalidateTextLayout) {
                invalidateTextLayout = false;
                if (stringBuilder.length() > 0) {
                    CharSequence text = stringBuilder.toString().toUpperCase();
                    text = Emoji.replaceEmoji(text, namePaint.getFontMetricsInt(), dp(16), true);
                    if (textLayout == null || !TextUtils.equals(text, textLayout.getText())) {
                        try {
                            textLayout = new StaticLayout(text, namePaint, dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            if (textLayout.getLineCount() > 0) {
                                textLeft = textLayout.getLineLeft(0);
                                textWidth = textLayout.getLineWidth(0);
                                textHeight = textLayout.getLineBottom(0);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } else {
                    textLayout = null;
                }
            }
            if (textLayout != null) {
                float scale = size / (float) dp(50);
                canvas.scale(scale, scale, size / 2f, size / 2f) ;
                canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);

                textLayout.draw(canvas);
            }
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setRoundRadius(int roundRadius) {
        this.roundRadius = roundRadius;
    }
}
