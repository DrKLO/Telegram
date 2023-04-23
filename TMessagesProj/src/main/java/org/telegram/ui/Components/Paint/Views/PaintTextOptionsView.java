package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.Arrays;
import java.util.List;

public class PaintTextOptionsView extends LinearLayout {
    public final static int ALIGN_LEFT = 0,
        ALIGN_CENTER = 1,
        ALIGN_RIGHT = 2;

    private final static List<AlignFramePair> ALIGN_PAIRS = Arrays.asList(
            new AlignFramePair(ALIGN_LEFT, ALIGN_CENTER, 20, 0),
            new AlignFramePair(ALIGN_LEFT, ALIGN_RIGHT, 20, 40),
            new AlignFramePair(ALIGN_CENTER, ALIGN_LEFT, 0, 20),
            new AlignFramePair(ALIGN_CENTER, ALIGN_RIGHT, 60, 40),
            new AlignFramePair(ALIGN_RIGHT, ALIGN_LEFT, 40, 20),
            new AlignFramePair(ALIGN_RIGHT, ALIGN_CENTER, 40, 60)
    );
    private int currentAlign = ALIGN_LEFT;
    private ChatActivityEnterViewAnimatedIconView emojiButton;
    private RLottieImageView alignView;
    private ImageView outlineView;
    private ImageView plusView;
    private View colorClickableView;

    private TypefaceCell typefaceCell;
    private PaintTypefaceListView typefaceListView;

    private Delegate delegate;

    private int outlineType;

    public PaintTextOptionsView(Context context) {
        super(context);

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);

        colorClickableView = new View(context);
        colorClickableView.setOnClickListener(v -> delegate.onColorPickerSelected());
        addView(colorClickableView, LayoutHelper.createLinear(24, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 16, 0));

        alignView = new RLottieImageView(context);
        alignView.setAnimation(R.raw.photo_text_allign, 24, 24);
        RLottieDrawable drawable = alignView.getAnimatedDrawable();
        drawable.setPlayInDirectionOfCustomEndFrame(true);
        drawable.setCustomEndFrame(20);
        drawable.setCurrentFrame(20);
        alignView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        alignView.setOnClickListener(v -> setAlignment((currentAlign + 1) % 3, true));
        alignView.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        addView(alignView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        outlineView = new ImageView(context);
        outlineView.setImageResource(R.drawable.msg_text_outlined);
        outlineView.setPadding(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
        outlineView.setOnClickListener(v -> delegate.onTextOutlineSelected(v));
        addView(outlineView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        plusView = new ImageView(context);
        plusView.setImageResource(R.drawable.msg_add);
        plusView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        plusView.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        plusView.setOnClickListener(v -> delegate.onNewTextSelected());
        plusView.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        addView(plusView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        addView(new Space(context), LayoutHelper.createLinear(0, 0, 1f));

        typefaceCell = new TypefaceCell(context);
        typefaceCell.setCurrent(true);
        typefaceCell.setOnClickListener(v -> delegate.onTypefaceButtonClicked());
        addView(typefaceCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public TypefaceCell getTypefaceCell() {
        return typefaceCell;
    }

    public void setTypefaceListView(PaintTypefaceListView typefaceListView) {
        this.typefaceListView = typefaceListView;
    }

    public View getColorClickableView() {
        return colorClickableView;
    }

    public void getTypefaceCellBounds(RectF out) {
        out.set(typefaceCell.getLeft() + AndroidUtilities.dp(8), typefaceCell.getTop(), typefaceCell.getRight() + AndroidUtilities.dp(8), typefaceCell.getBottom());
    }

    public void animatePlusToIcon(int icon) {
        if (icon == 0) {
            icon = R.drawable.msg_add;
        }
        AndroidUtilities.updateImageViewImageAnimated(plusView, icon);
    }

    public ChatActivityEnterViewAnimatedIconView getEmojiButton() {
        return emojiButton;
    }

    public void setOutlineType(int type) {
        setOutlineType(type, false);
    }

    public void setOutlineType(int type, boolean animate) {
        if (outlineType == type) {
            return;
        }
        this.outlineType = type;

        int res;
        switch (type) {
            default:
            case 0:
                res = R.drawable.msg_text_outlined;
                break;
            case 1:
                res = R.drawable.msg_text_regular;
                break;
            case 2:
                res = R.drawable.msg_text_framed;
                break;
        }
        if (animate) {
            AndroidUtilities.updateImageViewImageAnimated(outlineView, res);
        } else {
            outlineView.setImageResource(res);
        }
    }

    public void setTypeface(String key) {
        if (typefaceCell == null) {
            return;
        }
        for (PaintTypeface typeface : PaintTypeface.BUILT_IN_FONTS) {
            if (typeface.getKey().equals(key)) {
                typefaceCell.bind(typeface);
                return;
            }
        }
        if (!PaintTypeface.fetched(() -> setTypeface(key))) {
            return;
        }
        for (PaintTypeface typeface : PaintTypeface.get()) {
            if (typeface.getKey().equals(key)) {
                typefaceCell.bind(typeface);
                break;
            }
        }
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setAlignment(int align) {
        setAlignment(align, false);
    }

    public void setAlignment(int align, boolean notify) {
        int prevAlign = currentAlign;
        currentAlign = align;

        if (prevAlign == currentAlign) {
            RLottieDrawable drawable = alignView.getAnimatedDrawable();
            AlignFramePair alignPair = ALIGN_PAIRS.get(0);
            for (AlignFramePair pair : ALIGN_PAIRS) {
                if (currentAlign == pair.toAlign) {
                    alignPair = pair;
                    break;
                }
            }
            drawable.setCurrentFrame(alignPair.toFrame);
            drawable.setCustomEndFrame(alignPair.toFrame);

            if (notify) {
                delegate.onTextAlignmentSelected(align);
            }
            return;
        }

        AlignFramePair alignPair = ALIGN_PAIRS.get(0);
        for (AlignFramePair pair : ALIGN_PAIRS) {
            if (prevAlign == pair.fromAlign && currentAlign == pair.toAlign) {
                alignPair = pair;
                break;
            }
        }
        RLottieDrawable drawable = alignView.getAnimatedDrawable();
        drawable.setCurrentFrame(alignPair.fromFrame);
        drawable.setCustomEndFrame(alignPair.toFrame);
        drawable.start();

        if (notify) {
            delegate.onTextAlignmentSelected(align);
        }
    }

    public final static class TypefaceCell extends TextView {
        private boolean isCurrent;
        private Drawable expandDrawable;

        public TypefaceCell(Context context) {
            super(context);

            setTextColor(Color.WHITE);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setCurrent(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (isCurrent) {
                int y = (getHeight() - AndroidUtilities.dp(16)) / 2;
                if (LocaleController.isRTL) {
                    expandDrawable.setBounds(AndroidUtilities.dp(12), y, AndroidUtilities.dp(16 + 12), y + AndroidUtilities.dp(16));
                } else {
                    expandDrawable.setBounds(getWidth() - AndroidUtilities.dp(16 + 12), y, getWidth() - AndroidUtilities.dp(12), y + AndroidUtilities.dp(16));
                }
                expandDrawable.draw(canvas);
            }
        }

        public void setCurrent(boolean current) {
            isCurrent = current;
            if (isCurrent) {
                setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 38 : 14), AndroidUtilities.dp(6), AndroidUtilities.dp(LocaleController.isRTL ? 14 : 38), AndroidUtilities.dp(6));
                setBackground(Theme.AdaptiveRipple.rect(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, AndroidUtilities.dp(32)));
            } else {
                setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14), AndroidUtilities.dp(24), AndroidUtilities.dp(14));
                setBackground(Theme.AdaptiveRipple.rect(0xFF282829));
            }
            if (isCurrent && expandDrawable == null) {
                expandDrawable = ContextCompat.getDrawable(getContext(), R.drawable.photo_expand);
                expandDrawable.setColorFilter(new PorterDuffColorFilter(0x99FFFFFF, PorterDuff.Mode.SRC_IN));
            }
            invalidate();
        }

        public void bind(PaintTypeface typeface) {
            setTypeface(typeface.getTypeface());
            setText(typeface.getName());
        }
    }

    public interface Delegate {
        void onColorPickerSelected();
        void onTextOutlineSelected(View v);
        void onEmojiButtonClick();
        void onNewTextSelected();
        void onTypefaceSelected(PaintTypeface typeface);
        void onTypefaceButtonClicked();
        void onTextAlignmentSelected(int align);
    }

    private final static class AlignFramePair {
        private final int fromAlign;
        private final int toAlign;
        private final int fromFrame;
        private final int toFrame;

        private AlignFramePair(int fromAlign, int toAlign, int fromFrame, int toFrame) {
            this.fromAlign = fromAlign;
            this.toAlign = toAlign;
            this.fromFrame = fromFrame;
            this.toFrame = toFrame;
        }
    }
}
