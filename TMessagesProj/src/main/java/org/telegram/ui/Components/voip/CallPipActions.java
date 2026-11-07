package org.telegram.ui.Components.voip;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.GroupCallActivity;
import org.telegram.utils.pip.PipAction;

import java.util.ArrayList;
import java.util.List;

import static org.telegram.messenger.LocaleController.getString;

/**
 * System PiP buttons of a call (one-to-one and group). The system shows only a few of them
 * (getMaxNumPictureInPictureActions, 3 on phones), so each has a priority and the most useful
 * ones stay:
 * <ol>
 *   <li>microphone (mute / unmute, or raise hand when muted by an admin);</li>
 *   <li>camera while it is on: turning one's own camera off quickly matters most;</li>
 *   <li>end the call (leave a group call);</li>
 *   <li>camera to turn it back on, only if it was on when the call went to PiP or is paused.</li>
 * </ol>
 * On screen they always go in the same order: microphone, camera, end.
 */
final class CallPipActions {
    static final int ACTION_MIC = 1;
    static final int ACTION_CAMERA = 2;
    static final int ACTION_END = 3;

    private static final int PRIORITY_MIC = 100;
    private static final int PRIORITY_CAMERA_ON = 90;
    private static final int PRIORITY_END = 80;
    private static final int PRIORITY_CAMERA_OFF = 60;

    /** Own camera was on (or paused) when the call went to system PiP: offer to turn it back on. */
    private static boolean cameraWasOn;

    private CallPipActions() {}

    static void onSystemEnter() {
        final VoIPService service = VoIPService.getSharedInstance();
        cameraWasOn = service != null && service.getVideoState(false) != Instance.VIDEO_STATE_INACTIVE;
    }

    static void onSystemExit() {
        cameraWasOn = false;
    }

    private static final class Candidate {
        final PipAction action;
        final int priority;
        final int order;

        Candidate(PipAction action, int priority, int order) {
            this.action = action;
            this.priority = priority;
            this.order = order;
        }
    }

    static void build(@NonNull List<PipAction> out, int maxActions, boolean group) {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service == null || maxActions <= 0) {
            return;
        }
        final ArrayList<Candidate> candidates = new ArrayList<>(3);

        if (group && service.mutedByAdmin()) {
            final TLRPC.GroupCallParticipant self = service.groupCall != null ? service.groupCall.participants.get(service.getSelfId()) : null;
            final boolean raised = self != null && self.raise_hand_rating != 0;
            candidates.add(new Candidate(new PipAction(ACTION_MIC, R.drawable.msg_voice_muted, getString(raised ? R.string.VoipGroupCancelRaiseHand : R.string.VoipMutedTapForSpeak), !raised), PRIORITY_MIC, 0));
        } else if (service.isMicMute()) {
            candidates.add(new Candidate(new PipAction(ACTION_MIC, R.drawable.msg_voice_muted, getString(R.string.VoipUnmute), true), PRIORITY_MIC, 0));
        } else {
            candidates.add(new Candidate(new PipAction(ACTION_MIC, R.drawable.msg_voice_unmuted, getString(R.string.VoipMute), true), PRIORITY_MIC, 0));
        }

        final int videoState = service.getVideoState(false);
        if (videoState == Instance.VIDEO_STATE_ACTIVE) {
            candidates.add(new Candidate(new PipAction(ACTION_CAMERA, R.drawable.pip_camera_on, getString(R.string.VoipStopVideo), true), PRIORITY_CAMERA_ON, 1));
        } else if (cameraWasOn || videoState == Instance.VIDEO_STATE_PAUSED) {
            candidates.add(new Candidate(new PipAction(ACTION_CAMERA, R.drawable.pip_camera_off, getString(R.string.VoipStartVideo), canStartCamera(service, group)), PRIORITY_CAMERA_OFF, 1));
        }

        candidates.add(new Candidate(new PipAction(ACTION_END, R.drawable.msg_endcall, getString(group ? R.string.VoipGroupLeave : R.string.VoipEndCall), true), PRIORITY_END, 2));

        java.util.Collections.sort(candidates, (a, b) -> Integer.compare(b.priority, a.priority));
        while (candidates.size() > maxActions) {
            candidates.remove(candidates.size() - 1);
        }
        java.util.Collections.sort(candidates, (a, b) -> Integer.compare(a.order, b.order));
        for (Candidate candidate : candidates) {
            out.add(candidate.action);
        }
    }

    private static boolean canStartCamera(@NonNull VoIPService service, boolean group) {
        if (Build.VERSION.SDK_INT >= 23 && ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return !group || service.groupCall != null && service.groupCall.canRecordVideo();
    }

    static void perform(int actionId, boolean group) {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("call pip: action " + actionId + ", camera " + service.getVideoState(false) + ", mic muted " + service.isMicMute());
        }
        if (actionId == ACTION_MIC) {
            if (group && service.mutedByAdmin()) {
                final TLObject self = UserConfig.getInstance(service.getAccount()).getCurrentUser();
                service.editCallMember(self, null, null, null, true, null);
            } else {
                service.setMicMute(!service.isMicMute(), false, true);
            }
        } else if (actionId == ACTION_CAMERA) {
            final int videoState = service.getVideoState(false);
            if (videoState == Instance.VIDEO_STATE_ACTIVE) {
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
            } else if (canStartCamera(service, group)) {
                if (videoState == Instance.VIDEO_STATE_INACTIVE) {
                    service.createCaptureDevice(false);
                    service.requestVideoCall(false);
                }
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
        } else if (actionId == ACTION_END) {
            if (group) {
                // an explicit action of the user, no dialog is possible in PiP
                GroupCallActivity.onLeaveClick(ApplicationLoader.applicationContext, null, false, true);
            } else {
                service.hangUp();
            }
        }
    }

    /** What the buttons depend on: refresh them when it changes. */
    static long stateKey() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return 0;
        }
        long key = service.getVideoState(false);
        key = key * 2 + (service.isMicMute() ? 1 : 0);
        key = key * 2 + (service.mutedByAdmin() ? 1 : 0);
        return key;
    }
}
