package org.telegram.utils.settings;

import org.telegram.messenger.BuildConfig;
import org.telegram.ui.ActionBar.SimpleActionBarDoneButton;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.recorder.audio.AudioRecordingSession;

public final class SharedSettings {

    public static final BooleanSetting experimentalSettingsAllowed =
        BooleanSetting.of("experimental_settings_allowed", BuildConfig.DEBUG_VERSION);

    public static final BooleanSetting audioRecorderV2Enabled =
        BooleanSetting.of("audio_recorder_v2_enabled", BuildConfig.DEBUG_VERSION);

    public static final EnumSetting<AudioRecordingSession.NoiseSuppression> audioRecorderNoiseSuppression =
        EnumSetting.of("audio_recorder_noise_suppression", AudioRecordingSession.NoiseSuppression.DISABLED);

    public static final BooleanSetting roundVideoCamera2Enabled =
        BooleanSetting.of("round_video_camera2_enabled", BuildConfig.DEBUG_VERSION);

    public static final EnumSetting<RoundVideoSession.OutputResolution> roundVideoOutputResolution =
        EnumSetting.of("round_video_output_resolution", RoundVideoSession.OutputResolution.P480);

    public static final EnumSetting<RoundVideoSession.CameraResolution> roundVideoCameraResolution =
        EnumSetting.of("round_video_camera_resolution", RoundVideoSession.CameraResolution.HIGH);

    public static final EnumSetting<RoundVideoSession.FrameRate> roundVideoFrameRate =
        EnumSetting.of("round_video_frame_rate", RoundVideoSession.FrameRate.FPS_30);

    public static final IntSetting roundVideoVideoBitrate =
        IntSetting.of("round_video_video_bitrate", 1_200_000);

    public static final BooleanSetting roundVideoComposition =
        BooleanSetting.of("round_video_composition", true);

    public static final EnumSetting<RoundVideoSession.CameraFacing> roundVideoLastCamera =
        EnumSetting.of("round_video_last_camera", RoundVideoSession.CameraFacing.FRONT);

    public static final EnumSetting<SimpleActionBarDoneButton.Style> headerDoneButtonStyle =
        EnumSetting.of("header_done_button_style", SimpleActionBarDoneButton.Style.GLASS);

    public static final EnumSetting<SimpleActionBarDoneButton.Shape> headerDoneButtonShape =
        EnumSetting.of("header_done_button_shape", SimpleActionBarDoneButton.Shape.TEXT);

    // ButterGram: ghost mode
    public static final BooleanSetting buttergramGhostRead =
        BooleanSetting.of("buttergram_ghost_read", false);

    public static final BooleanSetting buttergramGhostTyping =
        BooleanSetting.of("buttergram_ghost_typing", false);

    public static final BooleanSetting buttergramGhostOnline =
        BooleanSetting.of("buttergram_ghost_online", false);

    // ButterGram: keep messages deleted remotely
    public static final BooleanSetting buttergramSaveDeleted =
        BooleanSetting.of("buttergram_save_deleted", false);

    // ButterGram: interface customization
    public static final BooleanSetting buttergramHidePremiumRows =
        BooleanSetting.of("buttergram_hide_premium_rows", false);

    private SharedSettings() {
    }
}
