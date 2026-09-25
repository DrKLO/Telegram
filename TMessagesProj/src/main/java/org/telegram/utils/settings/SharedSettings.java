package org.telegram.utils.settings;

import org.telegram.messenger.BuildConfig;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;

public final class SharedSettings {

    public static final BooleanSetting experimentalSettingsAllowed =
        BooleanSetting.of("experimental_settings_allowed", BuildConfig.DEBUG_VERSION);

    public static final BooleanSetting roundVideoCamera2Enabled =
        BooleanSetting.of("round_video_camera2_enabled", BuildConfig.DEBUG_VERSION);

    public static final EnumSetting<RoundVideoSession.OutputResolution> roundVideoOutputResolution =
        EnumSetting.of("round_video_output_resolution", RoundVideoSession.OutputResolution.P480);

    public static final EnumSetting<RoundVideoSession.CameraResolution> roundVideoCameraResolution =
        EnumSetting.of("round_video_camera_resolution", RoundVideoSession.CameraResolution.HIGH);

    public static final EnumSetting<RoundVideoSession.FrameRate> roundVideoFrameRate =
        EnumSetting.of("round_video_frame_rate", RoundVideoSession.FrameRate.FPS_30);

    public static final IntSetting roundVideoVideoBitrate =
        IntSetting.of("round_video_video_bitrate", 1_000_000);

    public static final BooleanSetting roundVideoComposition =
        BooleanSetting.of("round_video_composition", true);

    public static final EnumSetting<RoundVideoSession.CameraFacing> roundVideoLastCamera =
        EnumSetting.of("round_video_last_camera", RoundVideoSession.CameraFacing.FRONT);

    private SharedSettings() {
    }
}
