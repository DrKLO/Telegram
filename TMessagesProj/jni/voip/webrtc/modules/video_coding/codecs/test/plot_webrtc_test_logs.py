#  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
#
#  Use of this source code is governed by a BSD-style license
#  that can be found in the LICENSE file in the root of the source
#  tree. An additional intellectual property rights grant can be found
#  in the file PATENTS.  All contributing project authors may
#  be found in the AUTHORS file in the root of the source tree.
"""Plots statistics from WebRTC integration test logs.

Usage: $ python plot_webrtc_test_logs.py filename.txt
"""

import numpy
import sys
import re

import matplotlib.pyplot as plt

# Log events.
EVENT_START = 'RUN      ] CodecSettings/VideoCodecTestParameterized.'
EVENT_END = 'OK ] CodecSettings/VideoCodecTestParameterized.'

# Metrics to plot, tuple: (name to parse in file, label to use when plotting).
WIDTH = ('width', 'width')
HEIGHT = ('height', 'height')
FILENAME = ('filename', 'clip')
CODEC_TYPE = ('codec_type', 'Codec')
ENCODER_IMPLEMENTATION_NAME = ('enc_impl_name', 'enc name')
DECODER_IMPLEMENTATION_NAME = ('dec_impl_name', 'dec name')
CODEC_IMPLEMENTATION_NAME = ('codec_impl_name', 'codec name')
CORES = ('num_cores', 'CPU cores used')
DENOISING = ('denoising', 'denoising')
RESILIENCE = ('resilience', 'resilience')
ERROR_CONCEALMENT = ('error_concealment', 'error concealment')
CPU_USAGE = ('cpu_usage_percent', 'CPU usage (%)')
BITRATE = ('target_bitrate_kbps', 'target bitrate (kbps)')
FRAMERATE = ('input_framerate_fps', 'fps')
QP = ('avg_qp', 'QP avg')
PSNR = ('avg_psnr', 'PSNR (dB)')
SSIM = ('avg_ssim', 'SSIM')
ENC_BITRATE = ('bitrate_kbps', 'encoded bitrate (kbps)')
NUM_FRAMES = ('num_input_frames', 'num frames')
NUM_DROPPED_FRAMES = ('num_dropped_frames', 'num dropped frames')
TIME_TO_TARGET = ('time_to_reach_target_bitrate_sec',
                  'time to reach target rate (sec)')
ENCODE_SPEED_FPS = ('enc_speed_fps', 'encode speed (fps)')
DECODE_SPEED_FPS = ('dec_speed_fps', 'decode speed (fps)')
AVG_KEY_FRAME_SIZE = ('avg_key_frame_size_bytes', 'avg key frame size (bytes)')
AVG_DELTA_FRAME_SIZE = ('avg_delta_frame_size_bytes',
                        'avg delta frame size (bytes)')

# Settings.
SETTINGS = [
    WIDTH,
    HEIGHT,
    FILENAME,
    NUM_FRAMES,
]

# Settings, options for x-axis.
X_SETTINGS = [
    CORES,
    FRAMERATE,
    DENOISING,
    RESILIENCE,
    ERROR_CONCEALMENT,
    BITRATE,  # TODO(asapersson): Needs to be last.
]

# Settings, options for subplots.
SUBPLOT_SETTINGS = [
    CODEC_TYPE,
    ENCODER_IMPLEMENTATION_NAME,
    DECODER_IMPLEMENTATION_NAME,
    CODEC_IMPLEMENTATION_NAME,
] + X_SETTINGS

# Results.
RESULTS = [
    PSNR,
    SSIM,
    ENC_BITRATE,
    NUM_DROPPED_FRAMES,
    TIME_TO_TARGET,
    ENCODE_SPEED_FPS,
    DECODE_SPEED_FPS,
    QP,
    CPU_USAGE,
    AVG_KEY_FRAME_SIZE,
    AVG_DELTA_FRAME_SIZE,
]

METRICS_TO_PARSE = SETTINGS + SUBPLOT_SETTINGS + RESULTS

Y_METRICS = [res[1] for res in RESULTS]

# Parameters for plotting.
FIG_SIZE_SCALE_FACTOR_X = 1.6
FIG_SIZE_SCALE_FACTOR_Y = 1.8
GRID_COLOR = [0.45, 0.45, 0.45]


def ParseSetting(filename, setting):
    """Parses setting from file.

  Args:
    filename: The name of the file.
    setting: Name of setting to parse (e.g. width).

  Returns:
    A list holding parsed settings, e.g. ['width: 128.0', 'width: 160.0'] """

    settings = []

    settings_file = open(filename)
    while True:
        line = settings_file.readline()
        if not line:
            break
        if re.search(r'%s' % EVENT_START, line):
            # Parse event.
            parsed = {}
            while True:
                line = settings_file.readline()
                if not line:
                    break
                if re.search(r'%s' % EVENT_END, line):
                    # Add parsed setting to list.
                    if setting in parsed:
                        s = setting + ': ' + str(parsed[setting])
                        if s not in settings:
                            settings.append(s)
                    break

                TryFindMetric(parsed, line)

    settings_file.close()
    return settings


def ParseMetrics(filename, setting1, setting2):
    """Parses metrics from file.

  Args:
    filename: The name of the file.
    setting1: First setting for sorting metrics (e.g. width).
    setting2: Second setting for sorting metrics (e.g. CPU cores used).

  Returns:
    A dictionary holding parsed metrics.

  For example:
    metrics[key1][key2][measurement]

  metrics = {
  "width: 352": {
    "CPU cores used: 1.0": {
      "encode time (us)": [0.718005, 0.806925, 0.909726, 0.931835, 0.953642],
      "PSNR (dB)": [25.546029, 29.465518, 34.723535, 36.428493, 38.686551],
      "bitrate (kbps)": [50, 100, 300, 500, 1000]
    },
    "CPU cores used: 2.0": {
      "encode time (us)": [0.718005, 0.806925, 0.909726, 0.931835, 0.953642],
      "PSNR (dB)": [25.546029, 29.465518, 34.723535, 36.428493, 38.686551],
      "bitrate (kbps)": [50, 100, 300, 500, 1000]
    },
  },
  "width: 176": {
    "CPU cores used: 1.0": {
      "encode time (us)": [0.857897, 0.91608, 0.959173, 0.971116, 0.980961],
      "PSNR (dB)": [30.243646, 33.375592, 37.574387, 39.42184, 41.437897],
      "bitrate (kbps)": [50, 100, 300, 500, 1000]
    },
  }
  } """

    metrics = {}

    # Parse events.
    settings_file = open(filename)
    while True:
        line = settings_file.readline()
        if not line:
            break
        if re.search(r'%s' % EVENT_START, line):
            # Parse event.
            parsed = {}
            while True:
                line = settings_file.readline()
                if not line:
                    break
                if re.search(r'%s' % EVENT_END, line):
                    # Add parsed values to metrics.
                    key1 = setting1 + ': ' + str(parsed[setting1])
                    key2 = setting2 + ': ' + str(parsed[setting2])
                    if key1 not in metrics:
                        metrics[key1] = {}
                    if key2 not in metrics[key1]:
                        metrics[key1][key2] = {}

                    for label in parsed:
                        if label not in metrics[key1][key2]:
                            metrics[key1][key2][label] = []
                        metrics[key1][key2][label].append(parsed[label])

                    break

                TryFindMetric(parsed, line)

    settings_file.close()
    return metrics


def TryFindMetric(parsed, line):
    for metric in METRICS_TO_PARSE:
        name = metric[0]
        label = metric[1]
        if re.search(r'%s' % name, line):
            found, value = GetMetric(name, line)
            if found:
                parsed[label] = value
            return


def GetMetric(name, string):
    # Float (e.g. bitrate = 98.8253).
    pattern = r'%s\s*[:=]\s*([+-]?\d+\.*\d*)' % name
    m = re.search(r'%s' % pattern, string)
    if m is not None:
        return StringToFloat(m.group(1))

    # Alphanumeric characters (e.g. codec type : VP8).
    pattern = r'%s\s*[:=]\s*(\w+)' % name
    m = re.search(r'%s' % pattern, string)
    if m is not None:
        return True, m.group(1)

    return False, -1


def StringToFloat(value):
    try:
        value = float(value)
    except ValueError:
        print "Not a float, skipped %s" % value
        return False, -1

    return True, value


def Plot(y_metric, x_metric, metrics):
    """Plots y_metric vs x_metric per key in metrics.

  For example:
    y_metric = 'PSNR (dB)'
    x_metric = 'bitrate (kbps)'
    metrics = {
      "CPU cores used: 1.0": {
        "PSNR (dB)": [25.546029, 29.465518, 34.723535, 36.428493, 38.686551],
        "bitrate (kbps)": [50, 100, 300, 500, 1000]
      },
      "CPU cores used: 2.0": {
        "PSNR (dB)": [25.546029, 29.465518, 34.723535, 36.428493, 38.686551],
        "bitrate (kbps)": [50, 100, 300, 500, 1000]
      },
    }
    """
    for key in sorted(metrics):
        data = metrics[key]
        if y_metric not in data:
            print "Failed to find metric: %s" % y_metric
            continue

        y = numpy.array(data[y_metric])
        x = numpy.array(data[x_metric])
        if len(y) != len(x):
            print "Length mismatch for %s, %s" % (y, x)
            continue

        label = y_metric + ' - ' + str(key)

        plt.plot(x,
                 y,
                 label=label,
                 linewidth=1.5,
                 marker='o',
                 markersize=5,
                 markeredgewidth=0.0)


def PlotFigure(settings, y_metrics, x_metric, metrics, title):
    """Plots metrics in y_metrics list. One figure is plotted and each entry
  in the list is plotted in a subplot (and sorted per settings).

  For example:
    settings = ['width: 128.0', 'width: 160.0']. Sort subplot per setting.
    y_metrics = ['PSNR (dB)', 'PSNR (dB)']. Metric to plot per subplot.
    x_metric = 'bitrate (kbps)'

  """

    plt.figure()
    plt.suptitle(title, fontsize='large', fontweight='bold')
    settings.sort()
    rows = len(settings)
    cols = 1
    pos = 1
    while pos <= rows:
        plt.rc('grid', color=GRID_COLOR)
        ax = plt.subplot(rows, cols, pos)
        plt.grid()
        plt.setp(ax.get_xticklabels(), visible=(pos == rows), fontsize='large')
        plt.setp(ax.get_yticklabels(), fontsize='large')
        setting = settings[pos - 1]
        Plot(y_metrics[pos - 1], x_metric, metrics[setting])
        if setting.startswith(WIDTH[1]):
            plt.title(setting, fontsize='medium')
        plt.legend(fontsize='large', loc='best')
        pos += 1

    plt.xlabel(x_metric, fontsize='large')
    plt.subplots_adjust(left=0.06,
                        right=0.98,
                        bottom=0.05,
                        top=0.94,
                        hspace=0.08)


def GetTitle(filename, setting):
    title = ''
    if setting != CODEC_IMPLEMENTATION_NAME[1] and setting != CODEC_TYPE[1]:
        codec_types = ParseSetting(filename, CODEC_TYPE[1])
        for i in range(0, len(codec_types)):
            title += codec_types[i] + ', '

    if setting != CORES[1]:
        cores = ParseSetting(filename, CORES[1])
        for i in range(0, len(cores)):
            title += cores[i].split('.')[0] + ', '

    if setting != FRAMERATE[1]:
        framerate = ParseSetting(filename, FRAMERATE[1])
        for i in range(0, len(framerate)):
            title += framerate[i].split('.')[0] + ', '

    if (setting != CODEC_IMPLEMENTATION_NAME[1]
            and setting != ENCODER_IMPLEMENTATION_NAME[1]):
        enc_names = ParseSetting(filename, ENCODER_IMPLEMENTATION_NAME[1])
        for i in range(0, len(enc_names)):
            title += enc_names[i] + ', '

    if (setting != CODEC_IMPLEMENTATION_NAME[1]
            and setting != DECODER_IMPLEMENTATION_NAME[1]):
        dec_names = ParseSetting(filename, DECODER_IMPLEMENTATION_NAME[1])
        for i in range(0, len(dec_names)):
            title += dec_names[i] + ', '

    filenames = ParseSetting(filename, FILENAME[1])
    title += filenames[0].split('_')[0]

    num_frames = ParseSetting(filename, NUM_FRAMES[1])
    for i in range(0, len(num_frames)):
        title += ' (' + num_frames[i].split('.')[0] + ')'

    return title


def ToString(input_list):
    return ToStringWithoutMetric(input_list, ('', ''))


def ToStringWithoutMetric(input_list, metric):
    i = 1
    output_str = ""
    for m in input_list:
        if m != metric:
            output_str = output_str + ("%s. %s\n" % (i, m[1]))
            i += 1
    return output_str


def GetIdx(text_list):
    return int(raw_input(text_list)) - 1


def main():
    filename = sys.argv[1]

    # Setup.
    idx_metric = GetIdx("Choose metric:\n0. All\n%s" % ToString(RESULTS))
    if idx_metric == -1:
        # Plot all metrics. One subplot for each metric.
        # Per subplot: metric vs bitrate (per resolution).
        cores = ParseSetting(filename, CORES[1])
        setting1 = CORES[1]
        setting2 = WIDTH[1]
        sub_keys = [cores[0]] * len(Y_METRICS)
        y_metrics = Y_METRICS
        x_metric = BITRATE[1]
    else:
        resolutions = ParseSetting(filename, WIDTH[1])
        idx = GetIdx("Select metric for x-axis:\n%s" % ToString(X_SETTINGS))
        if X_SETTINGS[idx] == BITRATE:
            idx = GetIdx("Plot per:\n%s" %
                         ToStringWithoutMetric(SUBPLOT_SETTINGS, BITRATE))
            idx_setting = METRICS_TO_PARSE.index(SUBPLOT_SETTINGS[idx])
            # Plot one metric. One subplot for each resolution.
            # Per subplot: metric vs bitrate (per setting).
            setting1 = WIDTH[1]
            setting2 = METRICS_TO_PARSE[idx_setting][1]
            sub_keys = resolutions
            y_metrics = [RESULTS[idx_metric][1]] * len(sub_keys)
            x_metric = BITRATE[1]
        else:
            # Plot one metric. One subplot for each resolution.
            # Per subplot: metric vs setting (per bitrate).
            setting1 = WIDTH[1]
            setting2 = BITRATE[1]
            sub_keys = resolutions
            y_metrics = [RESULTS[idx_metric][1]] * len(sub_keys)
            x_metric = X_SETTINGS[idx][1]

    metrics = ParseMetrics(filename, setting1, setting2)

    # Stretch fig size.
    figsize = plt.rcParams["figure.figsize"]
    figsize[0] *= FIG_SIZE_SCALE_FACTOR_X
    figsize[1] *= FIG_SIZE_SCALE_FACTOR_Y
    plt.rcParams["figure.figsize"] = figsize

    PlotFigure(sub_keys, y_metrics, x_metric, metrics,
               GetTitle(filename, setting2))

    plt.show()


if __name__ == '__main__':
    main()
