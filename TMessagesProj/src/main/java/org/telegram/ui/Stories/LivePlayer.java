package org.telegram.ui.Stories;

import static org.telegram.messenger.MessagesController.findUpdates;
import static org.telegram.messenger.MessagesController.findUpdatesAndRemove;
import static org.telegram.messenger.voip.VoIPService.QUALITY_FULL;
import static org.telegram.messenger.voip.VoIPService.QUALITY_MEDIUM;

import android.Manifest;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.messenger.voip.GroupCallMessagesController;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.NativeInstance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.messenger.voip.VoipAudioManager;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.voiceengine.WebRtcAudioTrack;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LivePlayer implements NotificationCenter.NotificationCenterDelegate, AudioManager.OnAudioFocusChangeListener {

    public static LivePlayer recording;
    public static final int QUALITY = QUALITY_FULL;

    public TL_stories.StoryItem storyItem;
    public final long dialogId;
    public final int storyId;
    public final Context context;
    public final int currentAccount;
    public final TLRPC.InputGroupCall inputCall;
    public final boolean isRtmpStream;
    public boolean outgoing;

    private boolean isMuted = false;
    public boolean isMuted() {
        return outgoing && isMuted;
    }
    public void setMuted(boolean muted) {
        if (!outgoing || isMuted == muted)
            return;
        isMuted = muted;
        if (instance != null) {
            instance.setMuteMicrophone(isMuted);
        }
    }

    private boolean emptyStream = false;
    public void setEmptyStream(boolean isEmpty) {
        if (destroyed) return;
        if (emptyStream == isEmpty) return;
        if (outgoing && isEmpty) return;

        emptyStream = isEmpty;
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, getCallId());
    }
    public boolean isEmptyStream() {
        return !destroyed && emptyStream;
    }
    public boolean canContinueEmptyStream() {
        return !(
            destroyed ||
            outgoing ||
            !emptyStream ||
            LivePlayer.recording != null ||
            call == null ||
            call.rtmp_stream ||
            !call.creator
        );
    }

    public void continueStreaming() {
        if (outgoing) return;

        PermissionRequest.ensureAllPermissions(R.raw.permission_request_camera, R.string.PermissionNoCameraMicVideo, new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO }, granted -> {
            if (!granted) return;

            if (destroyed) return;

            outgoing = true;
            isFront = true;
            setPolling(false);

            LivePlayer.recording = this;

            recordingVideoCapturer = NativeInstance.createVideoCapturer(instanceSink, isFront ? 1 : 0);
            if (instance != null) {
                Utilities.globalQueue.postRunnable(instance::stopGroup);
                srcs.clear();
                instance = null;
            }
            configureAudio();
            init();

            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, inputCall.id);
        });
    }

    private TLRPC.GroupCall call;

    public boolean destroyed = false;
    private boolean joined;

    private int connectionState;
    private NativeInstance instance;
    private final HashMap<String, Integer> currentStreamRequestTimestamp = new HashMap<>();

    private TLRPC.GroupCallParticipant participant;

    private VoIPService.ProxyVideoSink instanceSink;
    private boolean isFront = false;
    private long recordingVideoCapturer;

    private int mySource;

    public LivePlayer(
        Context context,
        int currentAccount,
        TL_stories.StoryItem storyItem,
        long dialogId,
        int storyId,
        boolean isRtmpStream,
        TLRPC.InputGroupCall inputCall
    ) {
        this(context, currentAccount, storyItem, dialogId, storyId, isRtmpStream, inputCall, false, false);
    }
    public LivePlayer(
        Context context,
        int currentAccount,
        TL_stories.StoryItem storyItem,
        long dialogId,
        int storyId,
        boolean isRtmpStream,
        TLRPC.InputGroupCall inputCall,

        boolean outgoing,
        boolean isFront
    ) {
        this.context = context;
        this.currentAccount = currentAccount;
        this.inputCall = inputCall;
        this.storyItem = storyItem;
        this.dialogId = dialogId;
        this.storyId = storyId;
        this.isRtmpStream = isRtmpStream;
        this.outgoing = outgoing;
        this.isFront = isFront;

        instanceSink = new VoIPService.ProxyVideoSink() {
            @Override
            public synchronized void onFrame(VideoFrame frame) {
                super.onFrame(frame);
                if (emptyStream) {
                    AndroidUtilities.runOnUIThread(() -> setEmptyStream(false));
                }
            }
        };

        FileLog.d("[LivePlayer] setup to call " + inputCall.id);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storyGroupCallUpdated);

        if (outgoing) {
            recordingVideoCapturer = NativeInstance.createVideoCapturer(instanceSink, isFront ? 1 : 0);
        }

        configureAudio();
        init();
    }

    private boolean listeningToAudioFocus;
    private boolean hasAudioFocus;

    private void configureAudio() {
        if (Build.VERSION.SDK_INT >= 21) {
            WebRtcAudioTrack.setAudioTrackUsageAttribute(AudioAttributes.USAGE_MEDIA);
            WebRtcAudioTrack.setAudioStreamType(AudioManager.USE_DEFAULT_STREAM_TYPE);
        }

        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (isRtmpStream) {
            am.setMode(AudioManager.MODE_NORMAL);
            am.setBluetoothScoOn(false);
        } else if (outgoing) {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);

            int focusResult = am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            listeningToAudioFocus = hasAudioFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            final VoipAudioManager vam = VoipAudioManager.get();
            am.setBluetoothScoOn(false);
            vam.setSpeakerphoneOn(true);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        hasAudioFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN;
    }

    private void init() {
        if (destroyed) return;

        instance = NativeInstance.makeGroup(
                VoIPHelper.getLogFilePath("live_" + inputCall.id),
                0,
                false,
                SharedConfig.noiseSupression,
                (ssrc, json) -> {
                    mySource = ssrc;

                    final TL_phone.joinGroupCall req = new TL_phone.joinGroupCall();
                    req.muted = !outgoing;
                    req.video_stopped = !outgoing;
                    req.call = inputCall;
                    req.params = new TLRPC.TL_dataJSON();
                    req.params.data = json;
                    req.join_as = new TLRPC.TL_inputPeerUser();
                    req.join_as.user_id = AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                        if (res instanceof TLRPC.Updates) {
                            final TLRPC.Updates updates = (TLRPC.Updates) res;
                            MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                            MessagesController.getInstance(currentAccount).putChats(updates.chats, false);
                            for (TLRPC.TL_updateGroupCall upd : findUpdates(updates, TLRPC.TL_updateGroupCall.class)) {
                                this.call = upd.call;
                            }
                            final ArrayList<TLRPC.TL_updateGroupCallMessage> history = findUpdatesAndRemove(updates, TLRPC.TL_updateGroupCallMessage.class);
                            AndroidUtilities.runOnUIThread(() -> {
                                for (TLRPC.TL_updateGroupCallMessage u : history) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryMessageUpdate, u.call.id, u, true);
                                }
                            });
                            MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                            final boolean isRtmpStream = call != null && call.rtmp_stream;
                            for (TLRPC.TL_updateGroupCallParticipants upd : findUpdates(updates, TLRPC.TL_updateGroupCallParticipants.class)) {
                                if (upd.call.id == getCallId() && !isRtmpStream) {
                                    for (int i = 0; i < upd.participants.size(); ++i) {
                                        if (DialogObject.getPeerDialogId(upd.participants.get(i).peer) == dialogId) {
                                            participant = upd.participants.get(i);
                                            break;
                                        }
                                    }
                                    if (participant != null)
                                        break;
                                }
                            }
                            TLRPC.TL_dataJSON params = null;
                            for (TLRPC.TL_updateGroupCallConnection upd : findUpdates(updates, TLRPC.TL_updateGroupCallConnection.class)) {
                                params = upd.params;
                            }

                            FileLog.d("[LivePlayer] joined call " + inputCall.id);

                            joined = true;

                            if (destroyed || instance == null) {
                                final TL_phone.leaveGroupCall leaveReq = new TL_phone.leaveGroupCall();
                                leaveReq.call = inputCall;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(leaveReq, (res2, err2) -> {
                                    if (res2 instanceof TLRPC.Updates) {
                                        MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                                    }
                                });
                                return;
                            }

                            final boolean isChannelStreaming;
                            if (params != null && !params.data.startsWith("{\"stream\":true")) {
                                isChannelStreaming = false;
                                instance.setJoinResponsePayload(params.data);
                            } else {
                                isChannelStreaming = !isRtmpStream;
                                instance.prepareForStream(isRtmpStream);
                            }
                            if (outgoing) {
                                instance.setMuteMicrophone(isMuted);
                                instance.activateVideoCapturer(recordingVideoCapturer);
                                instance.setupOutgoingVideoCreated(recordingVideoCapturer);
                            } else {
                                if (participant == null) {
                                    if (isRtmpStream) {
                                        final TL_phone.getGroupCallStreamChannels req3 = new TL_phone.getGroupCallStreamChannels();
                                        req3.call = inputCall;
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(req3, (response, error, responseTime) -> {
                                            long currentTime = 0;
                                            if (error == null) {
                                                if (instance == null || destroyed) {
                                                    return;
                                                }
                                                final TL_phone.groupCallStreamChannels res3 = (TL_phone.groupCallStreamChannels) response;
                                                if (!res3.channels.isEmpty()) {
                                                    currentTime = res3.channels.get(0).last_timestamp_ms;
                                                }
                                                if (res3.channels.isEmpty()) {
                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        setEmptyStream(true);
                                                    });
                                                }
                                                if (participant == null) {
                                                    participant = new TLRPC.TL_groupCallParticipant();
                                                    participant.peer = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                                                    participant.video = new TLRPC.TL_groupCallParticipantVideo();
                                                    TLRPC.TL_groupCallParticipantVideoSourceGroup sourceGroup = new TLRPC.TL_groupCallParticipantVideoSourceGroup();
                                                    sourceGroup.semantics = "SIM";
                                                    for (TL_phone.TL_groupCallStreamChannel channel : res3.channels) {
                                                        sourceGroup.sources.add(channel.channel);
                                                    }
                                                    participant.video.source_groups.add(sourceGroup);
                                                    participant.video.endpoint = "unified";
                                                    participant.videoEndpoint = "unified";

                                                    instance.addIncomingVideoOutput(
                                                        QUALITY,
                                                        "unified",
                                                        pushSources(createSsrcGroups(participant.video)),
                                                        instanceSink,
                                                        DialogObject.getPeerDialogId(participant.peer)
                                                    );
                                                }
                                            }
                                        }, ConnectionsManager.RequestFlagFailOnServerErrorsExceptFloodWait, ConnectionsManager.ConnectionTypeDownload, getCallStreamDatacenterId());
                                    } else {
                                        final TL_phone.getGroupCall req3 = new TL_phone.getGroupCall();
                                        req3.call = inputCall;
                                        req3.limit = 10;
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(req3, (response, error) -> {
                                            if (response instanceof TL_phone.groupCall) {
                                                final TL_phone.groupCall r = (TL_phone.groupCall) response;

                                                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                                                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                                                if (instance == null || destroyed) {
                                                    return;
                                                }
                                                for (int i = 0; i < r.participants.size(); ++i) {
                                                    if (DialogObject.getPeerDialogId(r.participants.get(i).peer) == dialogId) {
                                                        participant = r.participants.get(i);
                                                        break;
                                                    }
                                                }

                                                if (participant != null && participant.video != null) {
                                                    instance.addIncomingVideoOutput(
                                                        QUALITY,
                                                        participant.video.endpoint,
                                                        pushSources(createSsrcGroups(participant.video)),
                                                        instanceSink,
                                                        DialogObject.getPeerDialogId(participant.peer)
                                                    );
                                                } else {
                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        setEmptyStream(true);
                                                    });
                                                }
                                            }
                                        });
                                    }
                                } else if (participant.video != null) {
                                    instance.addIncomingVideoOutput(
                                        QUALITY,
                                        participant.video.endpoint,
                                        pushSources(createSsrcGroups(participant.video)),
                                        instanceSink,
                                        DialogObject.getPeerDialogId(participant.peer)
                                    );
                                } else {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        setEmptyStream(true);
                                    });
                                }
                            }

                            AndroidUtilities.runOnUIThread(() -> {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, call.id);
                                setPolling(true);
                            });
                        } else if (err != null) {
                            if ("GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
                                AndroidUtilities.runOnUIThread(this::storyDeleted);
                            }
                        }
                    });
                },
                (uids, levels, voice) -> {

                },
                (taskPtr, unknown) -> {
                    if (instance == null) {
                        return;
                    }

                    final TL_phone.getGroupParticipants req = new TL_phone.getGroupParticipants();
                    req.call = inputCall;
                    req.offset = "";
                    for (int a = 0; a < unknown.length; a++) {
                        req.sources.add(unknown[a]);
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                        if (res instanceof TL_phone.groupParticipants) {
                            final TL_phone.groupParticipants r = (TL_phone.groupParticipants) res;
                            MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                            MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                            if (instance == null) {
                                return;
                            }
                            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                            final ArrayList<VoIPService.RequestedParticipant> loaded = new ArrayList<>();
                            for (int i = 0; i < unknown.length; ++i) {
                                final int src = unknown[i];

                                for (TLRPC.GroupCallParticipant p : r.participants) {
                                    if (p.source == src) {
                                        loaded.add(new VoIPService.RequestedParticipant(p, src));
                                        break;
                                    }
                                }
                            }

                            instance.onMediaDescriptionAvailable(taskPtr, loaded.toArray(new VoIPService.RequestedParticipant[0]));
                        }
                    });
                },
                (timestamp, duration, videoChannel, quality) -> {
                    if (call == null) return;
                    FileLog.d("[LivePlayer] sending getFile time_ms=" + timestamp + (duration == 500 ? ", scale = 1" : "") + ", video_channel = " + videoChannel + ", video_quality = " + quality);
                    long startTime = System.currentTimeMillis();
                    final TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
                    req.limit = 128 * 1024;
                    final TLRPC.TL_inputGroupCallStream inputGroupCallStream = new TLRPC.TL_inputGroupCallStream();
                    inputGroupCallStream.call = inputCall;
                    inputGroupCallStream.time_ms = timestamp;
                    if (duration == 500) {
                        inputGroupCallStream.scale = 1;
                    }
                    if (videoChannel != 0) {
                        inputGroupCallStream.flags |= 1;
                        inputGroupCallStream.video_channel = videoChannel;
                        inputGroupCallStream.video_quality = quality;
                    }
                    req.location = inputGroupCallStream;
                    String key = videoChannel == 0 ? ("" + timestamp) : (videoChannel + "_" + timestamp + "_" + quality);
                    int reqId = AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(req, (response, error, responseTime) -> {
                        if (destroyed) return;
                        AndroidUtilities.runOnUIThread(() -> currentStreamRequestTimestamp.remove(key));

                        if (response != null) {
                            TLRPC.TL_upload_file res = (TLRPC.TL_upload_file) response;
                            FileLog.d("[LivePlayer] received in "+(System.currentTimeMillis() - startTime)+"ms getFile{time_ms=" + timestamp + (duration == 500 ? ", scale = 1" : "") + ", video_channel = " + videoChannel + ", video_quality = " + quality+ "}: " + res.bytes.limit() + " bytes");
                            instance.onStreamPartAvailable(timestamp, res.bytes.buffer, res.bytes.limit(), responseTime, videoChannel, quality);
                        } else {
                            if ("GROUPCALL_INVALID".equalsIgnoreCase(error.text)) {
                                instance.onStreamPartAvailable(timestamp, null, -1, responseTime, videoChannel, quality);
                                AndroidUtilities.runOnUIThread(this::storyDeleted);
                            } else if ("GROUPCALL_JOIN_MISSING".equals(error.text)) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (instance != null) {
                                        Utilities.globalQueue.postRunnable(instance::stopGroup);
                                        srcs.clear();
                                        instance = null;
                                    }
                                    init();
                                });
                                FileLog.d("[LivePlayer] received in "+(System.currentTimeMillis() - startTime)+"ms getFile{time_ms=" + timestamp + (duration == 500 ? ", scale = 1" : "") + ", video_channel = " + videoChannel + ", video_quality = " + quality + "}: " + error.text + " => rejoining");
                            } else {
                                int status;
                                if ("TIME_TOO_BIG".equals(error.text) || error.text.startsWith("FLOOD_WAIT")) {
                                    status = 0;
                                } else {
                                    status = -1;
                                }
                                FileLog.d("[LivePlayer] received in "+(System.currentTimeMillis() - startTime)+"ms getFile{time_ms=" + timestamp + (duration == 500 ? ", scale = 1" : "") + ", video_channel = " + videoChannel + ", video_quality = " + quality + "}: " + error.text + " => " + status);
                                instance.onStreamPartAvailable(timestamp, null, status, responseTime, videoChannel, quality);
                            }
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, getCallStreamDatacenterId());
                    AndroidUtilities.runOnUIThread(() -> currentStreamRequestTimestamp.put(key, reqId));
                },
                (timestamp, duration, videoChannel, quality) -> {
                    FileLog.d("[LivePlayer] cancelling getFile time_ms=" + timestamp + (duration == 500 ? ", scale = 1" : "") + (videoChannel != 0 ? ", video_channel = " + videoChannel + ", video_quality = " + quality : ""));
                    AndroidUtilities.runOnUIThread(() -> {
                        String key = videoChannel == 0 ? ("" + timestamp) : (videoChannel + "_" + timestamp + "_" + quality);
                        Integer reqId = currentStreamRequestTimestamp.get(key);
                        if (reqId != null) {
                            AccountInstance.getInstance(currentAccount).getConnectionsManager().cancelRequest(reqId, true);
                            currentStreamRequestTimestamp.remove(key);
                        }
                    });
                },
                taskPtr -> {
                    if (call != null && call.rtmp_stream) {
                        final TL_phone.getGroupCallStreamChannels req = new TL_phone.getGroupCallStreamChannels();
                        req.call = inputCall;
                        if (call == null || instance == null) {
                            return;
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error, responseTime) -> {
                            long currentTime = 0;
                            if (error == null) {
                                if (instance == null || destroyed) {
                                    return;
                                }
                                final TL_phone.groupCallStreamChannels res = (TL_phone.groupCallStreamChannels) response;
                                if (!res.channels.isEmpty()) {
                                    currentTime = res.channels.get(0).last_timestamp_ms;
                                }
                                if (res.channels.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        setEmptyStream(true);
                                    });
                                }
                                if (participant == null && !res.channels.isEmpty()) {
                                    participant = new TLRPC.TL_groupCallParticipant();
                                    participant.peer = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                                    participant.video = new TLRPC.TL_groupCallParticipantVideo();
                                    TLRPC.TL_groupCallParticipantVideoSourceGroup sourceGroup = new TLRPC.TL_groupCallParticipantVideoSourceGroup();
                                    sourceGroup.semantics = "SIM";
                                    for (TL_phone.TL_groupCallStreamChannel channel : res.channels) {
                                        sourceGroup.sources.add(channel.channel);
                                    }
                                    participant.video.source_groups.add(sourceGroup);
                                    participant.video.endpoint = "unified";
                                    participant.videoEndpoint = "unified";

                                    instance.addIncomingVideoOutput(
                                        QUALITY,
                                        "unified",
                                        pushSources(createSsrcGroups(participant.video)),
                                        instanceSink,
                                        DialogObject.getPeerDialogId(participant.peer)
                                    );
                                }
                            }
                            if (instance != null) {
                                instance.onRequestTimeComplete(taskPtr, currentTime);
                            }
                        }, ConnectionsManager.RequestFlagFailOnServerErrorsExceptFloodWait, ConnectionsManager.ConnectionTypeDownload, getCallStreamDatacenterId());
                    } else {
                        if (instance != null) {
                            instance.onRequestTimeComplete(taskPtr, ConnectionsManager.getInstance(currentAccount).getCurrentTimeMillis());
                        }
                    }
                },
                false
        );
        instance.setOnStateUpdatedListener(new Instance.OnStateUpdatedListener() {
            @Override
            public void onStateUpdated(int state, boolean inTransition) {
                final boolean wasConnected = isConnected();
                connectionState = state;
                FileLog.d("[LivePlayer] connectionState = " + state);
                if (wasConnected != isConnected()) {
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, getCallId());
                    });
                }
            }
        });
        instance.resetGroupInstance(false, false);
    }

    public boolean isConnected() {
        return (
            connectionState == Instance.STATE_ESTABLISHED ||
            connectionState == Instance.STATE_WAIT_INIT ||
            connectionState == Instance.STATE_WAIT_INIT_ACK
        );
    }

    private final HashSet<Integer> srcs = new HashSet<>();
    private float volume = 1.0f;
    public void setVolume(float volume) {
        volume = Utilities.clamp01(volume);
        if (LiveStoryPipOverlay.isVisible(this))
            volume = 1.0f;
        FileLog.d("setVolume(" + volume + ")");
        if (Math.abs(volume - this.volume) < 0.01f) return;
        this.volume = volume;
        updateVolumes();
    }

    private void updateVolumes() {
        if (destroyed || instance == null) return;
        for (int src : srcs)
            instance.setVolume(src, volume);
    }

    private NativeInstance.SsrcGroup[] pushSources(NativeInstance.SsrcGroup[] ssrc) {
        for (int i = 0; i < (ssrc == null ? 0 : ssrc.length); ++i) {
            for (int j = 0; j < ssrc[i].ssrcs.length; ++j) {
                srcs.add(ssrc[i].ssrcs[j]);
            }
        }
        updateVolumes();
        return ssrc;
    }

    private VideoSink displaySink;
    public VideoSink getDisplaySink() {
        return displaySink;
    }
    public void setDisplaySink(VideoSink sink) {
        if (displaySink == sink) return;
        instanceSink.setTarget(displaySink = sink);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storyGroupCallUpdated) {
            final long dialogId = (long) args[0];
            final TLRPC.GroupCall call = (TLRPC.GroupCall) args[1];
            if (this.dialogId == dialogId) {
                this.call = TlUtils.applyGroupCallUpdate(this.call, call);

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, call.id);
            }
        }
    }

    public void storyDeleted() {
        final TL_stories.TL_updateStory upd = new TL_stories.TL_updateStory();
        upd.peer = MessagesController.getInstance(currentAccount).getPeer(dialogId);
        upd.story = new TL_stories.TL_storyItemDeleted();
        upd.story.id = storyId;
        MessagesController.getInstance(currentAccount).getStoriesController().processUpdate(upd);
        destroy();
    }

    public boolean equals(TLRPC.InputGroupCall call) {
        return (
            this.inputCall == call ||
            this.inputCall.id == call.id
        );
    }

    public void destroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        setPolling(false);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storyGroupCallUpdated);

        FileLog.d("[LivePlayer] destroyed");

        if (joined) {
            final TL_phone.leaveGroupCall req = new TL_phone.leaveGroupCall();
            req.call = inputCall;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                }
            });
        }

        if (outgoing) {
            instanceSink.setTarget(null);
            NativeInstance.destroyVideoCapturer(recordingVideoCapturer);
        }
        if (instance != null) {
            Utilities.globalQueue.postRunnable(instance::stopGroup);
            srcs.clear();
            instance = null;
        }

        if (listeningToAudioFocus) {
            final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(this);
            hasAudioFocus = false;
            listeningToAudioFocus = false;
        }
        if (outgoing) {
            final VoipAudioManager vam = VoipAudioManager.get();
            vam.setSpeakerphoneOn(false);
        }

        if (recording == this) {
            recording = null;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, getCallId());
        }
    }

    public int getWatchersCount() {
        return Math.max(1, call == null ? 0 : call.participants_count);
    }

    public boolean areMessagesEnabled() {
        if (call == null) return true;
        return call.messages_enabled;
    }

    public long getSendPaidMessagesStars() {
        if (call == null) return 0;
        return call.send_paid_messages_stars;
    }

    private int getCallStreamDatacenterId() {
        if (call == null)
            return Integer.MAX_VALUE;
        if ((call.flags & 16) != 0)
            return call.stream_dc_id;
        return Integer.MAX_VALUE;
    }

    private NativeInstance.SsrcGroup[] createSsrcGroups(TLRPC.TL_groupCallParticipantVideo video) {
        if (video.source_groups.isEmpty()) {
            return null;
        }
        final NativeInstance.SsrcGroup[] result = new NativeInstance.SsrcGroup[video.source_groups.size()];
        for (int a = 0; a < result.length; a++) {
            result[a] = new NativeInstance.SsrcGroup();
            final TLRPC.TL_groupCallParticipantVideoSourceGroup group = video.source_groups.get(a);
            result[a].semantics = group.semantics;
            result[a].ssrcs = new int[group.sources.size()];
            for (int b = 0; b < result[a].ssrcs.length; b++) {
                result[a].ssrcs[b] = group.sources.get(b);
            }
        }
        return result;
    }

    private boolean polling;
    private Runnable pollRunnable;
    private Runnable poll2Runnable;
    private int pollingRequestId = -1;
    private int polling2RequestId = -1;
    private void setPolling(boolean poll) {
        if (destroyed) poll = false;
        if (this.polling == poll) return;

        this.polling = poll;

        if (!poll) {
            if (pollingRequestId != -1) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(pollingRequestId, true);
                pollingRequestId = -1;
            }
            if (polling2RequestId != -1) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(polling2RequestId, true);
                polling2RequestId = -1;
            }
            if (pollRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(pollRunnable);
                pollRunnable = null;
            }
            if (poll2Runnable != null) {
                AndroidUtilities.cancelRunOnUIThread(poll2Runnable);
                poll2Runnable = null;
            }
            return;
        }

        if (pollRunnable != null) AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        AndroidUtilities.runOnUIThread(pollRunnable = () -> this.poll(), 4000);

        if (poll2Runnable != null) AndroidUtilities.cancelRunOnUIThread(poll2Runnable);
        AndroidUtilities.runOnUIThread(poll2Runnable = () -> this.poll2(), pollingGroupCallInterval());
    }

    private int pollingGroupCallInterval() {
        return isAdmin() ? 5_000 : 20_000;
    }
    private void poll2() {
        poll2Runnable = null;
        if (destroyed) return;

        final TL_phone.getGroupCall req = new TL_phone.getGroupCall();
        req.call = inputCall;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (destroyed) return;

            if (res instanceof TL_phone.groupCall) {
                final TL_phone.groupCall r = (TL_phone.groupCall) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                call = r.call;

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, call.id);
            } else if (err != null) {
                if ("GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
                    AndroidUtilities.runOnUIThread(this::storyDeleted);
                }
            }

            if (this.polling) {
                if (poll2Runnable != null) AndroidUtilities.cancelRunOnUIThread(poll2Runnable);
                AndroidUtilities.runOnUIThread(poll2Runnable = () -> this.poll2(), pollingGroupCallInterval());
            }
        }));
    }

    private void poll() {
        pollRunnable = null;
        if (destroyed) return;

        final TL_phone.checkGroupCall req = new TL_phone.checkGroupCall();
        req.call = inputCall;
        req.sources.add(mySource);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (destroyed) return;

            if (res instanceof Vector) {
                Vector vector = (Vector) res;
                if (!vector.toIntArray().contains(mySource)) {
                    if (instance != null) {
                        Utilities.globalQueue.postRunnable(instance::stopGroup);
                        srcs.clear();
                        instance = null;
                    }
                    init();
                }
            } else if (res instanceof TL_phone.groupCall) {
                final TL_phone.groupCall r = (TL_phone.groupCall) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                call = r.call;

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.liveStoryUpdated, call.id);
            } else if (err != null) {
                if ("GROUPCALL_JOIN_MISSING".equals(err.text)) {
                    FileLog.d("[LivePlayer] received GROUPCALL_JOIN_MISSING on checkGroupCall => rejoining");
                    AndroidUtilities.runOnUIThread(() -> {
                        if (instance != null) {
                            Utilities.globalQueue.postRunnable(instance::stopGroup);
                            srcs.clear();
                            instance = null;
                        }
                        init();
                    });
                } else if ("GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
                    AndroidUtilities.runOnUIThread(this::storyDeleted);
                }
            }

            if (this.polling) {
                if (pollRunnable != null) AndroidUtilities.cancelRunOnUIThread(pollRunnable);
                AndroidUtilities.runOnUIThread(pollRunnable = () -> this.poll(), 4000);
            }
        }));
    }

    public void switchCamera() {
        if (!outgoing) return;
        NativeInstance.switchCameraCapturer(recordingVideoCapturer, isFront = !isFront);
    }

    public long getCallId() {
        if (call != null) {
            return call.id;
        }
        if (inputCall != null) {
            return inputCall.id;
        }
        return 0;
    }

    public boolean commentsDisabled() {
        if (call == null) return false;
        if (isAdmin()) return false;
        return !call.messages_enabled;
    }

    public boolean sendAsDisabled() {
        if (call == null) return false;
        return !call.messages_enabled;
    }

    public boolean isCreator() {
        return call != null && call.creator;
    }

    public boolean isAdmin() {
        return isAdmin(ChatObject.ACTION_MANAGE_CALLS);
    }

    public boolean isAdmin(int action) {
        if (isCreator()) return true;
        if (dialogId >= 0) {
            return UserConfig.getInstance(currentAccount).getClientUserId() == dialogId;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return ChatObject.canUserDoAction(chat, action);
        }
    }

    public void end() {
        if (destroyed) return;
        final TL_phone.discardGroupCall req = new TL_phone.discardGroupCall();
        req.call = inputCall;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.Updates) {
                final TLRPC.Updates updates = (TLRPC.Updates) res;
                MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                MessagesController.getInstance(currentAccount).putChats(updates.chats, false);
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            } else if (err != null) {
                if ("GROUPCALL_ALREADY_DISCARDED".equalsIgnoreCase(err.text)) {
                    AndroidUtilities.runOnUIThread(this::storyDeleted);
                }
            }
        });
        destroy();
    }

    public TLRPC.Peer getDefaultSendAs() {
        if (call == null) return null;
        return call.default_send_as;
    }

    public void setDefaultSendAs(TLRPC.Peer peer) {
        if (call == null) return;
        call.flags = TLObject.setFlag(call.flags, TLObject.FLAG_21, peer != null);
        call.default_send_as = peer;
    }

    public ArrayList<LiveCommentsView.Message> messages;
    public ArrayList<LiveCommentsView.TopSender> topMessages;

}
