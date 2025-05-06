package com.plcoding.audiorecorder.playback;

import java.io.File;

public interface AudioPlayer {
    void playFile(File file);
    void stop();
}