package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Locale;

public class DialogCellTags {

    private final View parentView;
    private final ArrayList<MessagesController.DialogFilter> filters = new ArrayList<>();
    private final ArrayList<Tag> tags = new ArrayList<>();
    private Tag moreTags = null;

    public DialogCellTags(View view) {
        parentView = view;
    }

    private static class Tag {
        private final static float padDp = 4.66f;
        private final static float heightDp = 14.66f;

        public int filterId;
        public int colorId;

        Text text;
        int color;
        int width;
        private int textHeight;

        public static Tag asMore(View view, int n) {
            Tag tag = new Tag();
            tag.filterId = n;

            String text = "+" + n;
            tag.text = new Text(text, 10, AndroidUtilities.bold()).supportAnimatedEmojis(view);
            tag.width = dp(2 * padDp) + (int) tag.text.getCurrentWidth();
            tag.textHeight = (int) tag.text.getHeight();

            tag.color = Theme.getColor(Theme.key_avatar_nameInMessageBlue);

            return tag;
        }

        public static Tag fromFilter(View view, int currentAccount, MessagesController.DialogFilter filter) {
            Tag tag = new Tag();
            tag.filterId = filter.id;
            tag.colorId = filter.color;

            CharSequence text = new SpannableStringBuilder((filter.name == null ? "" : filter.name).toUpperCase());
            tag.text = new Text(text, 10, AndroidUtilities.bold()).supportAnimatedEmojis(view);
            text = Emoji.replaceEmoji(text, tag.text.getFontMetricsInt(), false);
            text = MessageObject.replaceAnimatedEmoji(text, filter.entities, tag.text.getFontMetricsInt());
            tag.text.setText(text);
            tag.text.setEmojiCacheType(AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER);
            tag.width = dp(2 * padDp) + (int) tag.text.getCurrentWidth();
            tag.textHeight = (int) tag.text.getHeight();

            tag.color = Theme.getColor(Theme.keys_avatar_nameInMessage[filter.color % Theme.keys_avatar_nameInMessage.length]);

            return tag;
        }

        public void draw(Canvas canvas) {
            Theme.dialogs_tagPaint.setColor(Theme.multAlpha(color, Theme.isCurrentThemeDark() ? .20f : .10f));

            AndroidUtilities.rectTmp.set(0, 0, width, dp(heightDp));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), Theme.dialogs_tagPaint);
            text.draw(canvas, dp(padDp), dp(heightDp) / 2f, color, 1.0f);
        }
    }

    public boolean update(int currentAccount, int dialogsType, long dialogId) {
        final AccountInstance account = AccountInstance.getInstance(currentAccount);
        final MessagesController controller = MessagesController.getInstance(currentAccount);

        if (!(controller.folderTags && account.getUserConfig().isPremium())) {
            final boolean wasEmpty = tags.isEmpty();
            tags.clear();
            return !wasEmpty;
        }

        ArrayList<MessagesController.DialogFilter> allFilters = controller.dialogFilters;
        MessagesController.DialogFilter currentFilter = null;
        if (dialogsType == DialogsActivity.DIALOGS_TYPE_FOLDER1) {
            currentFilter = controller.selectedDialogFilter[0];
        } else if (dialogsType == DialogsActivity.DIALOGS_TYPE_FOLDER2) {
            currentFilter = controller.selectedDialogFilter[1];
        }
        filters.clear();
        if (
            dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT ||
            dialogsType == DialogsActivity.DIALOGS_TYPE_FOLDER1 ||
            dialogsType == DialogsActivity.DIALOGS_TYPE_FOLDER2
        ) {
            for (int i = 0; i < allFilters.size(); ++i) {
                MessagesController.DialogFilter filter = allFilters.get(i);
                if (filter == null || filter == currentFilter || filter.color < 0)
                    continue;
                if (filter.includesDialog(account, dialogId)) {
                    filters.add(filter);
                }
            }
        }

        boolean changed = false;

        // remove existing tags
        for (int i = 0; i < tags.size(); ++i) {
            Tag tag = tags.get(i);
            MessagesController.DialogFilter filter = null;
            for (int j = 0; j < filters.size(); ++j) {
                if (filters.get(j).id == tag.filterId) {
                    filter = filters.get(j);
                    break;
                }
            }

            if (filter == null) {
                changed = true;
                tags.remove(i);
                i--;
            } else if (filter.color != tag.colorId || filter.name != null && tag.text != null && filter.name.length() != tag.text.getText().length()) {
                tags.set(i, Tag.fromFilter(parentView, currentAccount, filter));
                changed = true;
            }
        }

        // add new tags
        for (int i = 0; i < filters.size(); ++i) {
            MessagesController.DialogFilter filter = filters.get(i);
            Tag tag = null;
            for (int j = 0; j < tags.size(); ++j) {
                if (tags.get(j).filterId == filter.id) {
                    tag = tags.get(j);
                    break;
                }
            }

            if (tag == null) {
                changed = true;
                tags.add(i, Tag.fromFilter(parentView, currentAccount, filter));
            }
        }

        filters.clear();

        return changed;
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public void clear() {

    }

//    private final static Paint ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//    private final static LinearGradient ellipsizeGradient;
//    private final static Matrix ellipsizeMatrix = new Matrix();
//    static {
//        ellipsizePaint.setShader(ellipsizeGradient = new LinearGradient(0, 0, dp(12), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
//        ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
//    }

    public void draw(Canvas canvas, int width) {
        canvas.clipRect(0, 0, width, dp(Tag.heightDp));
        AndroidUtilities.rectTmp.set(0, 0, width, dp(Tag.heightDp));
        canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 0xFF, Canvas.ALL_SAVE_FLAG);
//        canvas.save();
        if (LocaleController.isRTL) {
            canvas.translate(width, 0);
        }
        int leftwidth = width - dp(25);
        int i = 0;
        for (; i < tags.size(); ++i) {
            Tag tag = tags.get(i);
            leftwidth -= tag.width + dp(4);
            if (leftwidth < 0) break;
            if (LocaleController.isRTL) {
                canvas.translate(-tag.width, 0);
                tag.draw(canvas);
                canvas.translate(-dp(4), 0);
            } else {
                tag.draw(canvas);
                canvas.translate(tag.width + dp(4), 0);
            }
        }
        if (i < tags.size()) {
            final int count = tags.size() - i;
            if (moreTags == null || moreTags.filterId != count) {
                moreTags = Tag.asMore(parentView, count);
            }
            if (LocaleController.isRTL) {
                canvas.translate(-moreTags.width, 0);
                moreTags.draw(canvas);
                canvas.translate(-dp(4), 0);
            } else {
                moreTags.draw(canvas);
                canvas.translate(moreTags.width + dp(4), 0);
            }
        }
//        canvas.restore();
//        canvas.save();
//        ellipsizeMatrix.reset();
//        if (LocaleController.isRTL) {
//            ellipsizeMatrix.postTranslate(0, 0);
//            ellipsizeMatrix.postScale(-1, 1f);
//            ellipsizeMatrix.postTranslate(dp(12), 0);
//        } else {
//            ellipsizeMatrix.postTranslate(width - dp(12), 0);
//        }
//        ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
//        canvas.drawRect(0, 0, width, dp(Tag.heightDp), ellipsizePaint);
//        canvas.restore();
        canvas.restore();
    }

}
