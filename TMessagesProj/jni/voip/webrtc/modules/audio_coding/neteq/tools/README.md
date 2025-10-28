# NetEQ RTP Play tool

## Testing of the command line arguments
The command line tool `neteq_rtpplay` can be tested by running `neteq_rtpplay_test.sh`, which is not use on try bots, but it can be used before submitting any CLs that may break the behavior of the command line arguments of `neteq_rtpplay`.

Run `neteq_rtpplay_test.sh` as follows from the `src/` folder:
```
src$ ./modules/audio_coding/neteq/tools/neteq_rtpplay_test.sh  \
  out/Default/neteq_rtpplay  \
  resources/audio_coding/neteq_opus.rtp  \
  resources/short_mixed_mono_48.pcm
```

You can replace the RTP and PCM files with any other compatible files.
If you get an error using the files indicated above, try running `gclient sync`.

Requirements: `awk` and `md5sum`.
