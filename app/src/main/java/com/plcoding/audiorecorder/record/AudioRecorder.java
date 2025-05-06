package com.plcoding.audiorecorder.record;

import java.io.File;

public interface AudioRecorder {
    void start(File outputFile);
    void stop();
}