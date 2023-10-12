package org.telegram.messenger.video;

import com.googlecode.mp4parser.h264.read.CAVLCReader;

import java.io.IOException;
import java.io.InputStream;


public class SequenceParameterSetRbsp {
    public int pic_width_in_luma_samples;
    public int pic_height_in_luma_samples;
    public int general_profile_space;
    public boolean general_tier_flag;
    public int general_profile_idc;
    public long general_profile_compatibility_flags;
    public long general_constraint_indicator_flags;
    public byte general_level_idc;
    public int chroma_format_idc;
    public int bit_depth_luma_minus8;
    public int bit_depth_chroma_minus8;
    public int sps_max_sub_layers_minus1;
    public boolean sps_temporal_id_nesting_flag;

    public SequenceParameterSetRbsp(InputStream is) throws IOException {
        CAVLCReader bsr = new CAVLCReader(is);

        int sps_video_parameter_set_id = (int) bsr.readNBit(4, "sps_video_parameter_set_id");
        sps_max_sub_layers_minus1 = (int) bsr.readNBit(3, "sps_max_sub_layers_minus1");
        boolean sps_temporal_id_nesting_flag = bsr.readBool("sps_temporal_id_nesting_flag");
        profile_tier_level(sps_max_sub_layers_minus1, bsr);
        int sps_seq_parameter_set_id = bsr.readUE("sps_seq_parameter_set_id");
        chroma_format_idc = bsr.readUE("chroma_format_idc");
        if (chroma_format_idc == 3) {
            int separate_colour_plane_flag = bsr.read1Bit();
        }
        pic_width_in_luma_samples = bsr.readUE("pic_width_in_luma_samples");
        pic_height_in_luma_samples = bsr.readUE("pic_width_in_luma_samples");
        boolean conformance_window_flag = bsr.readBool("conformance_window_flag");
        if (conformance_window_flag) {
            int conf_win_left_offset = bsr.readUE("conf_win_left_offset");
            int conf_win_right_offset = bsr.readUE("conf_win_right_offset");
            int conf_win_top_offset = bsr.readUE("conf_win_top_offset");
            int conf_win_bottom_offset = bsr.readUE("conf_win_bottom_offset");
        }

        bit_depth_luma_minus8 = bsr.readUE("bit_depth_luma_minus8");
        bit_depth_chroma_minus8 = bsr.readUE("bit_depth_chroma_minus8");
        int log2_max_pic_order_cnt_lsb_minus4 = bsr.readUE("log2_max_pic_order_cnt_lsb_minus4");
        boolean sps_sub_layer_ordering_info_present_flag = bsr.readBool("sps_sub_layer_ordering_info_present_flag");

        int j = sps_max_sub_layers_minus1 - (sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1) + 1;
        int sps_max_dec_pic_buffering_minus1[] = new int[j];
        int sps_max_num_reorder_pics[] = new int[j];
        int sps_max_latency_increase_plus1[] = new int[j];

        for (int i = (sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1); i <= sps_max_sub_layers_minus1; i++) {
            sps_max_dec_pic_buffering_minus1[i] = bsr.readUE("sps_max_dec_pic_buffering_minus1[" + i + "]");
            sps_max_num_reorder_pics[i] = bsr.readUE("sps_max_num_reorder_pics[" + i + "]");
            sps_max_latency_increase_plus1[i] = bsr.readUE("sps_max_latency_increase_plus1[" + i + "]");
        }

        int log2_min_luma_coding_block_size_minus3 = bsr.readUE("log2_min_luma_coding_block_size_minus3");
        int log2_diff_max_min_luma_coding_block_size = bsr.readUE("log2_diff_max_min_luma_coding_block_size");
        int log2_min_transform_block_size_minus2 = bsr.readUE("log2_min_transform_block_size_minus2");
        int log2_diff_max_min_transform_block_size = bsr.readUE("log2_diff_max_min_transform_block_size");
        int max_transform_hierarchy_depth_inter = bsr.readUE("max_transform_hierarchy_depth_inter");
        int max_transform_hierarchy_depth_intra = bsr.readUE("max_transform_hierarchy_depth_intra");

        boolean scaling_list_enabled_flag = bsr.readBool("scaling_list_enabled_flag");
        if (scaling_list_enabled_flag) {
            boolean sps_scaling_list_data_present_flag = bsr.readBool("sps_scaling_list_data_present_flag");
            if (sps_scaling_list_data_present_flag) {
                skip_scaling_list_data(bsr);
            }
        }
        boolean amp_enabled_flag = bsr.readBool("amp_enabled_flag");
        boolean sample_adaptive_offset_enabled_flag = bsr.readBool("sample_adaptive_offset_enabled_flag");
        boolean pcm_enabled_flag = bsr.readBool("pcm_enabled_flag");

        if (pcm_enabled_flag) {
            int pcm_sample_bit_depth_luma_minus1 = (int) bsr.readNBit(4, "pcm_sample_bit_depth_luma_minus1");
            int pcm_sample_bit_depth_chroma_minus1 = (int) bsr.readNBit(4, "pcm_sample_bit_depth_chroma_minus1");
            int log2_min_pcm_luma_coding_block_size_minus3 = bsr.readUE("log2_min_pcm_luma_coding_block_size_minus3");
            int log2_diff_max_min_pcm_luma_coding_block_size = bsr.readUE("log2_diff_max_min_pcm_luma_coding_block_size");
            boolean pcm_loop_filter_disabled_flag = bsr.readBool("pcm_loop_filter_disabled_flag");
        }
        int num_short_term_ref_pic_sets = bsr.readUE("num_short_term_ref_pic_sets");

        parse_short_term_ref_pic_sets(num_short_term_ref_pic_sets, bsr);

        boolean long_term_ref_pics_present_flag = bsr.readBool("long_term_ref_pics_present_flag");
        if (long_term_ref_pics_present_flag) {
            int num_long_term_ref_pics_sps = bsr.readUE("num_long_term_ref_pics_sps");
            int lt_ref_pic_poc_lsb_sps[] = new int[num_long_term_ref_pics_sps];
            boolean used_by_curr_pic_lt_sps_flag[] = new boolean[num_long_term_ref_pics_sps];
            for (int i = 0; i < num_long_term_ref_pics_sps; i++) {
                lt_ref_pic_poc_lsb_sps[i] = bsr.readU(log2_max_pic_order_cnt_lsb_minus4 + 4, "lt_ref_pic_poc_lsb_sps[" + i + "]");
                used_by_curr_pic_lt_sps_flag[i] = bsr.readBool("used_by_curr_pic_lt_sps_flag[" + i + "]");
            }
        }
        boolean sps_temporal_mvp_enabled_flag = bsr.readBool("sps_temporal_mvp_enabled_flag");
        boolean strong_intra_smoothing_enabled_flag = bsr.readBool("strong_intra_smoothing_enabled_flag");
//        boolean vui_parameters_present_flag = bsr.readBool("vui_parameters_present_flag");
//        if (vui_parameters_present_flag) {
//            vuiParameters = new VuiParameters(sps_max_sub_layers_minus1, bsr);
//        }
    }

    private void parse_short_term_ref_pic_sets(int num_short_term_ref_pic_sets, CAVLCReader bsr) throws IOException
    {
        // Based on FFMPEG implementation -- see hevc.c "parse_rps"
        long[] num_delta_pocs = new long[num_short_term_ref_pic_sets];
        for (int rpsIdx = 0; rpsIdx < num_short_term_ref_pic_sets; rpsIdx++) {
            if (rpsIdx != 0 && bsr.readBool()) {
                bsr.readBool("delta_rps_sign");
                bsr.readUE("abs_delta_rps_minus1");
                num_delta_pocs[rpsIdx] = 0;
                for (int i = 0; i <= num_delta_pocs[rpsIdx - 1]; i++) {
                    boolean use_delta_flag = false;
                    boolean used_by_curr_pic_flag = bsr.readBool();
                    if (!used_by_curr_pic_flag) {
                        use_delta_flag = bsr.readBool();
                    }
                    if (used_by_curr_pic_flag || use_delta_flag) {
                        num_delta_pocs[rpsIdx]++;
                    }
                }
            }
            else {
                long delta_pocs = bsr.readUE("num_negative_pics") + bsr.readUE("num_positive_pics");
                num_delta_pocs[rpsIdx] = delta_pocs;
                for (long i = 0; i < delta_pocs; ++i) {
                    bsr.readUE("delta_poc_s0/1_minus1");
                    bsr.readBool("used_by_curr_pic_s0/1_flag");
                }
            }
        }
    }

    private static void skip_scaling_list_data(CAVLCReader bsr) throws IOException
    {
        // Based on FFMPEG implementation see hevc.c "skip_scaling_list_data"
        for (int i = 0; i < 4; i++)
        {
            for (int j = 0; j < (i == 3 ? 2 : 6); j++)
            {
                if (bsr.readBool())
                {
                    bsr.readUE("scaling_list_pred_matrix_id_delta");
                }
                else
                {
                    int coef_num = Math.min(64, (1 << (4 + (i << 1))));
                    if (i > 1)
                    {
                        bsr.readUE("scaling_list_dc_coef_minus8");
                    }
                    for (int k = 0; k < coef_num; k++)
                    {
                        bsr.readUE("scaling_list_delta_coef");
                    }
                }
            }
        }
    }


    private void profile_tier_level(int maxNumSubLayersMinus1, CAVLCReader bsr) throws IOException {
        general_profile_space = bsr.readU(2, "general_profile_space");
        general_tier_flag = bsr.readBool("general_tier_flag");
        general_profile_idc = bsr.readU(5, "general_profile_idc");
        general_profile_compatibility_flags = bsr.readNBit(32);
        general_constraint_indicator_flags = bsr.readNBit(48);
        general_level_idc = (byte) bsr.readByte();
        boolean[] sub_layer_profile_present_flag = new boolean[maxNumSubLayersMinus1];
        boolean[] sub_layer_level_present_flag = new boolean[maxNumSubLayersMinus1];
        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            sub_layer_profile_present_flag[i] = bsr.readBool("sub_layer_profile_present_flag[" + i + "]");
            sub_layer_level_present_flag[i] = bsr.readBool("sub_layer_level_present_flag[" + i + "]");
        }

        if (maxNumSubLayersMinus1 > 0) {
            int[] reserved_zero_2bits = new int[8];

            for (int i = maxNumSubLayersMinus1; i < 8; i++) {
                reserved_zero_2bits[i] = bsr.readU(2, "reserved_zero_2bits[" + i + "]");
            }
        }
        int[] sub_layer_profile_space = new int[maxNumSubLayersMinus1];
        boolean[] sub_layer_tier_flag = new boolean[maxNumSubLayersMinus1];
        int[] sub_layer_profile_idc = new int[maxNumSubLayersMinus1];
        boolean[][] sub_layer_profile_compatibility_flag = new boolean[maxNumSubLayersMinus1][32];
        boolean[] sub_layer_progressive_source_flag = new boolean[maxNumSubLayersMinus1];
        boolean[] sub_layer_interlaced_source_flag = new boolean[maxNumSubLayersMinus1];
        boolean[] sub_layer_non_packed_constraint_flag = new boolean[maxNumSubLayersMinus1];
        boolean[] sub_layer_frame_only_constraint_flag = new boolean[maxNumSubLayersMinus1];
        long[] sub_layer_reserved_zero_44bits = new long[maxNumSubLayersMinus1];
        int[] sub_layer_level_idc = new int[maxNumSubLayersMinus1];


        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            if (sub_layer_profile_present_flag[i]) {
                sub_layer_profile_space[i] = bsr.readU(2, "sub_layer_profile_space[" + i + "]");
                sub_layer_tier_flag[i] = bsr.readBool("sub_layer_tier_flag[" + i + "]");
                sub_layer_profile_idc[i] = bsr.readU(5, "sub_layer_profile_idc[" + i + "]");
                for (int j = 0; j < 32; j++) {
                    sub_layer_profile_compatibility_flag[i][j] = bsr.readBool("sub_layer_profile_compatibility_flag[" + i + "][" + j + "]");
                }
                sub_layer_progressive_source_flag[i] = bsr.readBool("sub_layer_progressive_source_flag[" + i + "]");
                sub_layer_interlaced_source_flag[i] = bsr.readBool("sub_layer_interlaced_source_flag[" + i + "]");
                sub_layer_non_packed_constraint_flag[i] = bsr.readBool("sub_layer_non_packed_constraint_flag[" + i + "]");
                sub_layer_frame_only_constraint_flag[i] = bsr.readBool("sub_layer_frame_only_constraint_flag[" + i + "]");
                sub_layer_reserved_zero_44bits[i] = bsr.readNBit(44);
            }
            if (sub_layer_level_present_flag[i]) {
                sub_layer_level_idc[i] = bsr.readU(8, "sub_layer_level_idc[" + i + "]");
            }
        }
    }


}