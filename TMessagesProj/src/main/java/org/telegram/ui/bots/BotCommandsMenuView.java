package org.telegram.ui.bots;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StaticLayoutEx;

import java.util.ArrayList;

public class BotCommandsMenuView extends View {

    final RectF rectTmp = new RectF();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final MenuDrawable backDrawable = new MenuDrawable() {
        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            invalidate();
        }
    };
    RLottieDrawable webViewAnimation = new RLottieDrawable(R.raw.bot_webview_sheet_to_cross, String.valueOf(R.raw.bot_webview_sheet_to_cross) + hashCode(), AndroidUtilities.dp(20), AndroidUtilities.dp(20)) {
        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            invalidate();
        }

        @Override
        protected void invalidateInternal() {
            super.invalidateInternal();
            invalidate();
        }
    };
    public boolean expanded;
    float expandProgress;

    private String menuText = getString(R.string.BotsMenuTitle);
    StaticLayout menuTextLayout;
    private float menuTextWidth;
    boolean isOpened;

    public boolean isWebView;
    boolean isWebViewOpened;

    Drawable backgroundDrawable;
    boolean drawBackgroundDrawable = true;

    public BotCommandsMenuView(Context context) {
        super(context);
        updateColors();
        backDrawable.setMiniIcon(true);
        backDrawable.setRotateToBack(false);
        backDrawable.setRotation(0f, false);
        backDrawable.setCallback(this);
        textPaint.setTypeface(AndroidUtilities.bold());
        backDrawable.setRoundCap();
        backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), Color.TRANSPARENT, Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        backgroundDrawable.setCallback(this);
        setContentDescription(getString("AccDescrBotMenu", R.string.AccDescrBotMenu));
    }

    public void setDrawBackgroundDrawable(boolean drawBackgroundDrawable) {
        this.drawBackgroundDrawable = drawBackgroundDrawable;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        webViewAnimation.setMasterParent(this);
        webViewAnimation.setCurrentParentView(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        webViewAnimation.setMasterParent(this);
    }

    public void setWebView(boolean webView) {
        isWebView = webView;
        invalidate();
    }

    private void updateColors() {
        paint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
        int textColor = Theme.getColor(Theme.key_chat_messagePanelVoiceDuration);
        backDrawable.setBackColor(textColor);
        backDrawable.setIconColor(textColor);
        if (webViewAnimation != null) {
            webViewAnimation.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }
        textPaint.setColor(textColor);
    }

    int lastSize;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec) + MeasureSpec.getSize(heightMeasureSpec) << 16;
        if (lastSize != size || menuTextLayout == null) {
            backDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            textPaint.setTextSize(AndroidUtilities.dp(15));
            lastSize = size;
            CharSequence c = Emoji.replaceEmoji(menuText, textPaint.getFontMetricsInt(), false);
            int w = (int) (AndroidUtilities.displaySize.x * .6f);
            menuTextLayout = StaticLayoutEx.createStaticLayout(c, textPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false, TextUtils.TruncateAt.END, w, 1);
            menuTextWidth = menuTextLayout.getLineCount() > 0 ? menuTextLayout.getLineWidth(0) : 0;
        }
        onTranslationChanged((menuTextWidth + AndroidUtilities.dp(4)) * expandProgress);
        int width = AndroidUtilities.dp(40);
        if (expanded) {
            width += (int) menuTextWidth + AndroidUtilities.dp(4);
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (menuTextLayout != null) {
            boolean update = false;
            if (expanded && expandProgress != 1f) {
                expandProgress += 16f / 150f;
                if (expandProgress > 1) {
                    expandProgress = 1f;
                } else {
                    invalidate();
                }
                update = true;
            } else if (!expanded && expandProgress != 0) {
                expandProgress -= 16f / 150f;
                if (expandProgress < 0) {
                    expandProgress = 0;
                } else {
                    invalidate();
                }
                update = true;
            }

            float expandProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(this.expandProgress);
            if (update && expandProgress > 0) {
                textPaint.setAlpha((int) (255 * expandProgress));
            }

            if (drawBackgroundDrawable) {
                rectTmp.set(0, 0, AndroidUtilities.dp(40) + (menuTextWidth + AndroidUtilities.dp(4)) * expandProgress, getMeasuredHeight());
                canvas.drawRoundRect(rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);
                backgroundDrawable.setBounds((int) rectTmp.left, (int) rectTmp.top, (int) rectTmp.right, (int) rectTmp.bottom);
                backgroundDrawable.draw(canvas);
            }

            if (isWebView) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(9.5f), AndroidUtilities.dp(6));
                RLottieDrawable drawable = webViewAnimation;
                drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
                drawable.draw(canvas);
                canvas.restore();

                if (drawable.isRunning()) {
                    invalidate();
                }
            } else {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(8), AndroidUtilities.dp(4));
                backDrawable.draw(canvas);
                canvas.restore();
            }

            if (expandProgress > 0) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(34), (getMeasuredHeight() - menuTextLayout.getHeight()) / 2f);
                menuTextLayout.draw(canvas);
                canvas.restore();
            }

            if (update) {
                onTranslationChanged((menuTextWidth + AndroidUtilities.dp(4)) * expandProgress);
            }
        }
        super.dispatchDraw(canvas);
    }

    protected void onTranslationChanged(float translationX) {

    }

    public boolean setMenuText(String menuText) {
        if (menuText == null) {
            menuText = getString(R.string.BotsMenuTitle);
        }
        boolean changed = this.menuText == null || !this.menuText.equals(menuText);
        this.menuText = menuText;
        menuTextLayout = null;
        requestLayout();
        return changed;
    }

    public void setExpanded(boolean expanded, boolean animated) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            if (!animated) {
                expandProgress = expanded ? 1f : 0f;
            }
            requestLayout();
            invalidate();
        }
    }

    public boolean isOpened() {
        return isOpened;
    }

    public static class BotCommandsAdapter extends RecyclerListView.SelectionAdapter {

        ArrayList<String> newResult = new ArrayList<>();
        ArrayList<String> newResultHelp = new ArrayList<>();

        public BotCommandsAdapter() {

        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            FlickerLoadingView flickerLoadingView =  new FlickerLoadingView(parent.getContext());
//            flickerLoadingView.setIsSingleCell(true);
//            flickerLoadingView.setViewType(FlickerLoadingView.BOTS_MENU_TYPE);
//            return new RecyclerListView.Holder(flickerLoadingView);
            BotCommandView view = new BotCommandView(parent.getContext());
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            BotCommandView view = (BotCommandView) holder.itemView;
            view.command.setText(newResult.get(position));
            view.description.setText(newResultHelp.get(position));
            view.commandStr = newResult.get(position);
        }

        @Override
        public int getItemCount() {
            return newResult.size();
        }

        public void setBotInfo(LongSparseArray<TL_bots.BotInfo> botInfo) {
            newResult.clear();
            newResultHelp.clear();
            for (int b = 0; b < botInfo.size(); b++) {
                TL_bots.BotInfo info = botInfo.valueAt(b);
                for (int a = 0; a < info.commands.size(); a++) {
                    TLRPC.TL_botCommand botCommand = info.commands.get(a);
                    if (botCommand != null && botCommand.command != null) {
                        newResult.add("/" + botCommand.command);
                        newResultHelp.add(botCommand.description);
                    }
                }
            }
            notifyDataSetChanged();
        }
    }

    public void setOpened(boolean opened) {
        if (isOpened != opened) {
            isOpened = opened;
        }
        if (isWebView) {
            if (isWebViewOpened != opened) {
                RLottieDrawable drawable = webViewAnimation;
                drawable.stop();
                drawable.setPlayInDirectionOfCustomEndFrame(true);
                drawable.setCustomEndFrame(opened ? drawable.getFramesCount() : 1);
                drawable.start();
                isWebViewOpened = opened;
            }
        } else {
            backDrawable.setRotation(opened ? 1f : 0f, true);
        }
    }

    public static class BotCommandView extends LinearLayout {

        TextView command;
        TextView description;
        String commandStr;

        public BotCommandView(@NonNull Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

            description = new TextView(context) {
                @Override
                public void setText(CharSequence text, BufferType type) {
                    text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    super.setText(text, type);
                }
            };
            NotificationCenter.listenEmojiLoading(description);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            description.setTag(Theme.key_windowBackgroundWhiteBlackText);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, AndroidUtilities.dp(8), 0));

            command = new TextView(context);
            command.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            command.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            command.setTag(Theme.key_windowBackgroundWhiteGrayText);
            addView(command, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL));
        }

        public String getCommand() {
            return commandStr;
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || backgroundDrawable == who;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        backgroundDrawable.setState(getDrawableState());
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        backgroundDrawable.jumpToCurrentState();
    }
}
