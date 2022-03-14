package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.voip.GroupCallMiniTextureView;
import org.telegram.ui.Components.voip.GroupCallRenderersContainer;
import org.telegram.ui.Components.voip.GroupCallStatusIcon;
import org.telegram.ui.GroupCallActivity;

import java.util.ArrayList;

public class GroupCallFullscreenAdapter extends RecyclerListView.SelectionAdapter {

    private ChatObject.Call groupCall;
    private final int currentAccount;

    private final ArrayList<ChatObject.VideoParticipant> videoParticipants = new ArrayList<>();
    private final ArrayList<TLRPC.TL_groupCallParticipant> participants = new ArrayList<>();

    private ArrayList<GroupCallMiniTextureView> attachedRenderers;
    private GroupCallRenderersContainer renderersContainer;
    private final GroupCallActivity activity;
    private boolean visible = false;

    public GroupCallFullscreenAdapter(ChatObject.Call groupCall, int currentAccount, GroupCallActivity activity) {
        this.groupCall = groupCall;
        this.currentAccount = currentAccount;
        this.activity = activity;
    }

    public void setRenderersPool(ArrayList<GroupCallMiniTextureView> attachedRenderers, GroupCallRenderersContainer renderersContainer) {
        this.attachedRenderers = attachedRenderers;
        this.renderersContainer = renderersContainer;
    }

    public void setGroupCall(ChatObject.Call groupCall) {
        this.groupCall = groupCall;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecyclerListView.Holder(new GroupCallUserCell(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupCallUserCell view = (GroupCallUserCell) holder.itemView;
        ChatObject.VideoParticipant oldVideoParticipant = view.videoParticipant;

        ChatObject.VideoParticipant videoParticipant;
        TLRPC.TL_groupCallParticipant participant;
        if (position < videoParticipants.size()) {
            videoParticipant = videoParticipants.get(position);
            participant = videoParticipants.get(position).participant;
        } else if (position - videoParticipants.size() < participants.size()){
            videoParticipant = null;
            participant = participants.get(position - videoParticipants.size());
        } else {
            return;
        }
        view.setParticipant(videoParticipant, participant);

        if (oldVideoParticipant != null && !oldVideoParticipant.equals(videoParticipant) && view.attached && view.getRenderer() != null) {
            view.attachRenderer(false);
            if (videoParticipant != null) {
                view.attachRenderer(true);
            }
        } else if (view.attached) {
            if (view.getRenderer() == null && videoParticipant != null && visible) {
                view.attachRenderer(true);
            } else if (view.getRenderer() != null && videoParticipant == null) {
                view.attachRenderer(false);
            }
        }

    }

    @Override
    public int getItemCount() {
        return videoParticipants.size() + participants.size();
    }

    public void setVisibility(RecyclerListView listView, boolean visibility) {
        visible = visibility;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) view;
                if (cell.getVideoParticipant() != null) {
                    ((GroupCallUserCell) view).attachRenderer(visibility);
                }
            }
        }
    }


    public void scrollTo(ChatObject.VideoParticipant videoParticipant, RecyclerListView fullscreenUsersListView) {
        LinearLayoutManager layoutManager = (LinearLayoutManager)fullscreenUsersListView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        for (int i = 0; i < videoParticipants.size(); i++) {
            if (videoParticipants.get(i).equals(videoParticipant)) {
                layoutManager.scrollToPositionWithOffset(i, AndroidUtilities.dp(13));
                break;
            }
        }
    }

    public class GroupCallUserCell extends FrameLayout implements GroupCallStatusIcon.Callback {

        AvatarDrawable avatarDrawable = new AvatarDrawable();

        private TLRPC.User currentUser;
        private TLRPC.Chat currentChat;

        private BackupImageView avatarImageView;
        boolean hasAvatar;
        long peerId;

        ChatObject.VideoParticipant videoParticipant;
        TLRPC.TL_groupCallParticipant participant;

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float progress = 1f;

        GroupCallMiniTextureView renderer;

        String drawingName;
        String name;
        int nameWidth;

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        RLottieImageView muteButton;

        float selectionProgress;
        boolean selected;
        private boolean lastRaisedHand;
        private boolean lastMuted;

        GroupCallStatusIcon statusIcon;

        org.telegram.ui.Cells.GroupCallUserCell.AvatarWavesDrawable avatarWavesDrawable = new org.telegram.ui.Cells.GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(26), AndroidUtilities.dp(29));

        public GroupCallUserCell(@NonNull Context context) {
            super(context);
            avatarDrawable.setTextSize((int) (AndroidUtilities.dp(18) / 1.15f));
            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 9));
            setWillNotDraw(false);

            backgroundPaint.setColor(Theme.getColor(Theme.key_voipgroup_listViewBackground));
            selectionPaint.setColor(Theme.getColor(Theme.key_voipgroup_speakingText));
            selectionPaint.setStyle(Paint.Style.STROKE);
            selectionPaint.setStrokeWidth(AndroidUtilities.dp(2));
            textPaint.setColor(Color.WHITE);

            muteButton = new RLottieImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    GroupCallUserCell.this.invalidate();
                }
            };
            muteButton.setScaleType(ImageView.ScaleType.CENTER);
            addView(muteButton, LayoutHelper.createFrame(24, 24));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textPaint.setTextSize(AndroidUtilities.dp(12));
            if (name != null) {
                float maxWidth = AndroidUtilities.dp(46);
                float textWidth = textPaint.measureText(name);
                nameWidth = (int) Math.min(maxWidth, textWidth);
                drawingName = TextUtils.ellipsize(name, textPaint, nameWidth, TextUtils.TruncateAt.END).toString();
            }

            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setParticipant(ChatObject.VideoParticipant videoParticipant, TLRPC.TL_groupCallParticipant participant) {
            this.videoParticipant = videoParticipant;
            this.participant = participant;
            long lastPeerId = peerId;
            peerId = MessageObject.getPeerId(participant.peer);
            if (peerId > 0) {
                currentUser = AccountInstance.getInstance(currentAccount).getMessagesController().getUser(peerId);
                currentChat = null;
                avatarDrawable.setInfo(currentUser);

                name = UserObject.getFirstName(currentUser);
                avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);

                ImageLocation imageLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_SMALL);
                hasAvatar = imageLocation != null;
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, currentUser);
            } else {
                currentChat = AccountInstance.getInstance(currentAccount).getMessagesController().getChat(-peerId);
                currentUser = null;
                avatarDrawable.setInfo(currentChat);

                if (currentChat != null) {
                    name = currentChat.title;
                    avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);

                    ImageLocation imageLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_SMALL);
                    hasAvatar = imageLocation != null;
                    avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, currentChat);
                }
            }
            boolean animated = lastPeerId == peerId;
            if (videoParticipant == null) {
                selected = renderersContainer.fullscreenPeerId == MessageObject.getPeerId(participant.peer);
            } else if (renderersContainer.fullscreenParticipant != null) {
                selected = renderersContainer.fullscreenParticipant.equals(videoParticipant);
            } else {
                selected = false;
            }
            if (!animated) {
                setSelectedProgress(selected ? 1f : 0f);
            }
            if (statusIcon != null) {
                statusIcon.setParticipant(participant, animated);
                updateState(animated);
            }
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
        }

        public void setProgressToFullscreen(float progress) {
            if (this.progress == progress) {
                return;
            }
            this.progress = progress;
            if (progress == 1f) {
                avatarImageView.setTranslationY(0);
                avatarImageView.setScaleX(1f);
                avatarImageView.setScaleY(1f);
                backgroundPaint.setAlpha(255);

                invalidate();
                if (renderer != null) {
                    renderer.invalidate();
                }
                return;
            }
            float moveToCenter = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2f - getMeasuredHeight() / 2f;
            float scaleFrom = AndroidUtilities.dp(46) / (float) AndroidUtilities.dp(40);
            float s = scaleFrom * (1f - progress) + 1f * progress;
            avatarImageView.setTranslationY(-moveToCenter * (1f - progress));

            avatarImageView.setScaleX(s);
            avatarImageView.setScaleY(s);
            backgroundPaint.setAlpha((int) (255 * progress));

            invalidate();
            if (renderer != null) {
                renderer.invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (renderer != null && renderer.isFullyVisible() && !activity.drawingForBlur) {
                drawSelection(canvas);
                return;
            }
            if (progress > 0) {
                float p = getMeasuredWidth() / 2f * (1f - progress);
                AndroidUtilities.rectTmp.set(p, p, getMeasuredWidth() - p, getMeasuredHeight() - p);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(13), AndroidUtilities.dp(13), backgroundPaint);
                drawSelection(canvas);
            }

            float cx = avatarImageView.getX() + avatarImageView.getMeasuredWidth() / 2;
            float cy = avatarImageView.getY() + avatarImageView.getMeasuredHeight() / 2;

            avatarWavesDrawable.update();
            avatarWavesDrawable.draw(canvas, cx, cy, this);


            float scaleFrom = AndroidUtilities.dp(46) / (float) AndroidUtilities.dp(40);
            float s = scaleFrom * (1f - progress) + 1f * progress;

            avatarImageView.setScaleX(avatarWavesDrawable.getAvatarScale() * s);
            avatarImageView.setScaleY(avatarWavesDrawable.getAvatarScale() * s);

            super.dispatchDraw(canvas);
        }

        private void drawSelection(Canvas canvas) {
            if (selected && selectionProgress != 1f) {
                float selectedProgressLocal = selectionProgress + 16 / 150f;
                if (selectedProgressLocal > 1f) {
                    selectedProgressLocal = 1f;
                } else {
                    invalidate();
                }
                setSelectedProgress(selectedProgressLocal);
            } else if (!selected && selectionProgress != 0f) {
                float selectedProgressLocal = selectionProgress - 16 / 150f;
                if (selectedProgressLocal < 0) {
                    selectedProgressLocal = 0;
                } else {
                    invalidate();
                }
                setSelectedProgress(selectedProgressLocal);
            }

            if (selectionProgress > 0) {
                float p = getMeasuredWidth() / 2f * (1f - progress);
                AndroidUtilities.rectTmp.set(p, p, getMeasuredWidth() - p, getMeasuredHeight() - p);
                AndroidUtilities.rectTmp.inset(selectionPaint.getStrokeWidth() / 2, selectionPaint.getStrokeWidth() / 2);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), selectionPaint);
            }
        }

        private void setSelectedProgress(float p) {
            if (selectionProgress != p) {
                selectionProgress = p;
                selectionPaint.setAlpha((int) (255 * p));
            }
        }

        public long getPeerId() {
            return peerId;
        }

        public BackupImageView getAvatarImageView() {
            return avatarImageView;
        }

        public TLRPC.TL_groupCallParticipant getParticipant() {
            return participant;
        }

        public ChatObject.VideoParticipant getVideoParticipant() {
            return videoParticipant;
        }

        boolean attached;

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (visible && videoParticipant != null) {
                attachRenderer(true);
            }
            attached = true;
            if (activity.statusIconPool.size() > 0) {
                statusIcon = activity.statusIconPool.remove(activity.statusIconPool.size() - 1);
            } else {
                statusIcon = new GroupCallStatusIcon();
            }
            statusIcon.setCallback(this);
            statusIcon.setImageView(muteButton);
            statusIcon.setParticipant(participant, false);
            updateState(false);
            avatarWavesDrawable.setShowWaves(statusIcon.isSpeaking(), this);
            if (!statusIcon.isSpeaking()) {
                avatarWavesDrawable.setAmplitude(0);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attachRenderer(false);
            attached = false;
            if (statusIcon != null) {
                activity.statusIconPool.add(statusIcon);
                statusIcon.setImageView(null);
                statusIcon.setCallback(null);
            }
            statusIcon = null;
        }

        public void attachRenderer(boolean attach) {
            if (activity.isDismissed()) {
                return;
            }
            if (attach && this.renderer == null) {
                this.renderer = GroupCallMiniTextureView.getOrCreate(attachedRenderers, renderersContainer, null, this, null, videoParticipant, groupCall, activity);
            } else if (!attach) {
                if (renderer != null) {
                    renderer.setSecondaryView(null);
                }
                renderer = null;
            }
        }

        public void setRenderer(GroupCallMiniTextureView renderer) {
            this.renderer = renderer;
        }

        public void drawOverlays(Canvas canvas) {
            if (drawingName != null) {
                canvas.save();
                int paddingStart = (getMeasuredWidth() - nameWidth - AndroidUtilities.dp(24)) / 2;
                textPaint.setAlpha((int) (255 * progress * getAlpha()));
                canvas.drawText(drawingName, paddingStart + AndroidUtilities.dp(22), AndroidUtilities.dp(58 + 11), textPaint);
                canvas.restore();
                canvas.save();
                canvas.translate(paddingStart, AndroidUtilities.dp(53));
                if (muteButton.getDrawable() != null) {
                    muteButton.getDrawable().setAlpha((int) (255 * progress * getAlpha()));
                    muteButton.draw(canvas);
                    muteButton.getDrawable().setAlpha(255);
                }
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == muteButton) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        public float getProgressToFullscreen() {
            return progress;
        }

        public GroupCallMiniTextureView getRenderer() {
            return renderer;
        }

        public void setAmplitude(double value) {
            if (statusIcon != null) {
                statusIcon.setAmplitude(value);
            }
            avatarWavesDrawable.setAmplitude(value);
        }

        int lastColor;
        int lastWavesColor;
        ValueAnimator colorAnimator;


        public void updateState(boolean animated) {
            if (statusIcon == null) {
                return;
            }
            statusIcon.updateIcon(animated);
            int newColor;
            int newWavesColor;
            if (statusIcon.isMutedByMe()) {
                newWavesColor = newColor = Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon);
            } else if (statusIcon.isSpeaking()) {
                newWavesColor = newColor = Theme.getColor(Theme.key_voipgroup_speakingText);
            } else {
                newColor = Theme.getColor(Theme.key_voipgroup_nameText);
                newWavesColor = Theme.getColor(Theme.key_voipgroup_listeningText);
            }


            if (!animated) {
                if (colorAnimator != null) {
                    colorAnimator.removeAllListeners();
                    colorAnimator.cancel();
                }
                lastColor = newColor;
                lastWavesColor = newWavesColor;
                muteButton.setColorFilter(new PorterDuffColorFilter(newColor, PorterDuff.Mode.MULTIPLY));
                textPaint.setColor(lastColor);
                selectionPaint.setColor(newWavesColor);
                avatarWavesDrawable.setColor(ColorUtils.setAlphaComponent(newWavesColor, (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
                invalidate();
            } else {
                int colorFrom = lastColor;
                int colorWavesFrom = lastWavesColor;
                colorAnimator = ValueAnimator.ofFloat(0, 1f);
                colorAnimator.addUpdateListener(valueAnimator -> {
                    lastColor = ColorUtils.blendARGB(colorFrom, newColor, (float) valueAnimator.getAnimatedValue());
                    lastWavesColor = ColorUtils.blendARGB(colorWavesFrom, newWavesColor, (float) valueAnimator.getAnimatedValue());
                    muteButton.setColorFilter(new PorterDuffColorFilter(lastColor, PorterDuff.Mode.MULTIPLY));
                    textPaint.setColor(lastColor);
                    selectionPaint.setColor(lastWavesColor);
                    avatarWavesDrawable.setColor(ColorUtils.setAlphaComponent(lastWavesColor, (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
                    invalidate();
                });
                colorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        lastColor = newColor;
                        lastWavesColor = newWavesColor;
                        muteButton.setColorFilter(new PorterDuffColorFilter(lastColor, PorterDuff.Mode.MULTIPLY));
                        textPaint.setColor(lastColor);
                        selectionPaint.setColor(lastWavesColor);
                        avatarWavesDrawable.setColor(ColorUtils.setAlphaComponent(lastWavesColor, (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
                    }
                });
                colorAnimator.start();
            }
        }

        boolean skipInvalidate;

        @Override
        public void invalidate() {
            if (skipInvalidate) {
                return;
            }
            skipInvalidate = true;
            super.invalidate();
            if (renderer != null) {
                renderer.invalidate();
            } else {
                renderersContainer.invalidate();
            }
            skipInvalidate = false;
        }

        public boolean hasImage() {
            return renderer != null && renderer.hasImage();
        }

        @Override
        public void onStatusChanged() {
            avatarWavesDrawable.setShowWaves(statusIcon.isSpeaking(), this);
            updateState(true);
        }

        public boolean isRemoving(RecyclerListView listView) {
            return listView.getChildAdapterPosition(this) == RecyclerView.NO_POSITION;
        }
    }

    public void update(boolean animated, RecyclerListView listView) {
        if (groupCall == null) {
            return;
        }
        if (animated) {
            ArrayList<TLRPC.TL_groupCallParticipant> oldParticipants = new ArrayList<>(participants);
            ArrayList<ChatObject.VideoParticipant> oldVideoParticipants = new ArrayList<>(videoParticipants);

            participants.clear();
            if (!groupCall.call.rtmp_stream) {
                participants.addAll(groupCall.visibleParticipants);
            }

            videoParticipants.clear();
            if (!groupCall.call.rtmp_stream) {
                videoParticipants.addAll(groupCall.visibleVideoParticipants);
            }

            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldVideoParticipants.size() + oldParticipants.size();
                }

                @Override
                public int getNewListSize() {
                    return videoParticipants.size() + participants.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    if (oldItemPosition < oldVideoParticipants.size() && newItemPosition < videoParticipants.size()) {
                        return oldVideoParticipants.get(oldItemPosition).equals(videoParticipants.get(newItemPosition));
                    }
                    int oldItemPosition2 = oldItemPosition - oldVideoParticipants.size();
                    int newItemPosition2 = newItemPosition - videoParticipants.size();
                    if (newItemPosition2 >= 0 && newItemPosition2 < participants.size() && oldItemPosition2 >= 0 && oldItemPosition2 < oldParticipants.size()) {
                        return MessageObject.getPeerId(oldParticipants.get(oldItemPosition2).peer) == MessageObject.getPeerId(participants.get(newItemPosition2).peer);
                    }

                    TLRPC.TL_groupCallParticipant oldParticipant;
                    TLRPC.TL_groupCallParticipant newParticipant;
                    if (oldItemPosition < oldVideoParticipants.size()) {
                        oldParticipant = oldVideoParticipants.get(oldItemPosition).participant;
                    } else {
                        oldParticipant = oldParticipants.get(oldItemPosition2);
                    }

                    if (newItemPosition < videoParticipants.size()) {
                        newParticipant = videoParticipants.get(newItemPosition).participant;
                    } else {
                        newParticipant = participants.get(newItemPosition2);
                    }
                    if (MessageObject.getPeerId(oldParticipant.peer) == MessageObject.getPeerId(newParticipant.peer)) {
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }).dispatchUpdatesTo(this);
            AndroidUtilities.updateVisibleRows(listView);
        } else {
            participants.clear();
            if (!groupCall.call.rtmp_stream) {
                participants.addAll(groupCall.visibleParticipants);
            }

            videoParticipants.clear();
            if (!groupCall.call.rtmp_stream) {
                videoParticipants.addAll(groupCall.visibleVideoParticipants);
            }
            notifyDataSetChanged();
        }
    }
}
