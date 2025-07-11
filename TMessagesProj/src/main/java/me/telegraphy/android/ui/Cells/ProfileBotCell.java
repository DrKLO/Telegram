package me.telegraphy.android.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
// import android.graphics.Paint; // Not used directly
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import me.telegraphy.android.messenger.AndroidUtilities;
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.MessageObject; // Used for replaceMarkdownLinks
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.tgnet.TLRPC; // Required for TLRPC.User, TLRPC.UserFull, TLRPC.BotInfo
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.LinkSpanDrawable; // For clickable links in description/commands
import me.telegraphy.android.ui.Components.RecyclerListView; // For ThemeDescription context

import java.util.ArrayList;
import java.util.List;

public class ProfileBotCell extends FrameLayout {

    private TextView titleTextView;
    private TextView descriptionTextView;
    private TextView commandsTitleTextView;
    private TextView commandsTextView;
    private boolean drawDivider = true;

    public ProfileBotCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("BotInfo", R.string.BotInfo));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 10, 16, 0));

        descriptionTextView = new TextView(context);
        descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        descriptionTextView.setMovementMethod(LinkSpanDrawable.LinkMovementMethod.getInstance()); // Enable clickable links
        // Top margin will be set in setData or onMeasure
        addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 40, 16, 0));

        commandsTitleTextView = new TextView(context);
        commandsTitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        commandsTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15); // Slightly different from title
        commandsTitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        commandsTitleTextView.setText(LocaleController.getString("BotCommands", R.string.BotCommands));
        commandsTitleTextView.setVisibility(GONE); // Initially hidden
        // Top margin will be set in setData or onMeasure
        addView(commandsTitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 0));

        commandsTextView = new TextView(context);
        commandsTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        commandsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        commandsTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        commandsTextView.setMovementMethod(LinkSpanDrawable.LinkMovementMethod.getInstance()); // Enable clickable links
        commandsTextView.setVisibility(GONE); // Initially hidden
        // Top margin will be set in setData or onMeasure, add bottom padding for the cell
        addView(commandsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 10));
    }

    public void setData(TLRPC.User botUser, TLRPC.UserFull botFull) {
        if (botUser == null || !botUser.bot || botFull == null || botFull.bot_info == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        TLRPC.BotInfo botInfo = botFull.bot_info;
        boolean hasDescription = !TextUtils.isEmpty(botInfo.description);
        boolean hasCommands = botInfo.commands != null && !botInfo.commands.isEmpty();

        if (!hasDescription && !hasCommands) {
            // If bot has no description and no commands, maybe hide the whole cell or show a default message
            // For now, let's assume it might still be visible if title is always shown.
            // If title itself should hide, then setVisibility(GONE) here.
            // Based on provided logic, if both are empty, cell becomes GONE.
            setVisibility(GONE);
            return;
        }

        int currentTopMargin = AndroidUtilities.dp(10 + titleTextView.getLineHeight() + 10); // Approx. after title + padding

        if (hasDescription) {
            // Use MessageObject.replaceMarkdownLinks if description supports markdown
            // Context is needed for replaceMarkdownLinks if it creates spans that need it.
            CharSequence botDescription = MessageObject.replaceMarkdownLinks(getContext(), botInfo.description);
            descriptionTextView.setText(botDescription);
            descriptionTextView.setVisibility(VISIBLE);
            LayoutParams descLp = (LayoutParams) descriptionTextView.getLayoutParams();
            descLp.topMargin = currentTopMargin;
            descriptionTextView.setLayoutParams(descLp);

            // Measure description to correctly position commands section below it
            descriptionTextView.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() > 0 ? getMeasuredWidth() - AndroidUtilities.dp(32) : AndroidUtilities.displaySize.x - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            currentTopMargin += descriptionTextView.getMeasuredHeight() + AndroidUtilities.dp(12); // Add margin after description
        } else {
            descriptionTextView.setVisibility(GONE);
        }

        if (hasCommands) {
            LayoutParams commandsTitleLp = (LayoutParams) commandsTitleTextView.getLayoutParams();
            commandsTitleLp.topMargin = currentTopMargin;
            commandsTitleTextView.setLayoutParams(commandsTitleLp);
            commandsTitleTextView.setVisibility(VISIBLE);
            currentTopMargin += AndroidUtilities.dp(20 + 4); // Approx height of commandsTitle + margin

            SpannableStringBuilder commandsBuilder = new SpannableStringBuilder();
            for (int i = 0; i < botInfo.commands.size(); i++) {
                TLRPC.TL_botCommand cmd = botInfo.commands.get(i);
                if (i > 0) commandsBuilder.append("\n");
                commandsBuilder.append("/").append(cmd.command).append(" — ").append(cmd.description);
            }
            commandsTextView.setText(commandsBuilder);
            LayoutParams commandsLp = (LayoutParams) commandsTextView.getLayoutParams();
            commandsLp.topMargin = currentTopMargin;
            commandsTextView.setLayoutParams(commandsLp);
            commandsTextView.setVisibility(VISIBLE);
        } else {
            commandsTitleTextView.setVisibility(GONE);
            commandsTextView.setVisibility(GONE);
        }
        requestLayout(); // Important to re-measure and re-layout
    }

    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = AndroidUtilities.dp(10); // Top padding
        // Measure titleTextView
        titleTextView.measure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        height += titleTextView.getMeasuredHeight() + AndroidUtilities.dp(10); // Title + padding below it

        if (descriptionTextView.getVisibility() == VISIBLE) {
            descriptionTextView.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            height += descriptionTextView.getMeasuredHeight() + AndroidUtilities.dp(12); // Description + margin
        }
        if (commandsTitleTextView.getVisibility() == VISIBLE) {
            commandsTitleTextView.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY) // Approx fixed height for title
            );
            height += commandsTitleTextView.getMeasuredHeight() + AndroidUtilities.dp(4); // Commands title + margin
        }
        if (commandsTextView.getVisibility() == VISIBLE) {
            commandsTextView.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            height += commandsTextView.getMeasuredHeight();
        }
        height += AndroidUtilities.dp(10); // Bottom padding

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileBotCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(ProfileBotCell.class, ThemeDescription.FLAG_DIVIDER, null, Theme.dividerPaint, null, null, Theme.key_divider));

        descriptions.add(new ThemeDescription(ProfileBotCell.class, 0, new Class[]{TextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileBotCell.class, 0, new Class[]{TextView.class}, new String[]{"descriptionTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(ProfileBotCell.class, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextView.class}, new String[]{"descriptionTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText)); // For links in description

        descriptions.add(new ThemeDescription(ProfileBotCell.class, 0, new Class[]{TextView.class}, new String[]{"commandsTitleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileBotCell.class, 0, new Class[]{TextView.class}, new String[]{"commandsTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(ProfileBotCell.class, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextView.class}, new String[]{"commandsTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText)); // For links in commands
    }
}
