package com.plcoding.audiorecorder.record;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AndroidAudioRecorder implements AudioRecorder {
    private final Context context;
    private MediaRecorder recorder;

    public AndroidAudioRecorder(Context context) {
        this.context = context;
    }

    private MediaRecorder createRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new MediaRecorder(context);
        } else {
            // Use old constructor for backward compatibility
            return new MediaRecorder();
        }
    }

    @Override
    public void start(File outputFile) {
        try {
            MediaRecorder mediaRecorder = createRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            FileOutputStream fos = new FileOutputStream(outputFile);
            mediaRecorder.setOutputFile(fos.getFD());

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                recorder = mediaRecorder;
            } catch (Exception e) {
                // Log error and release resources
                e.printStackTrace();
                mediaRecorder.release();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exception - maybe notify user
        }
    }

    @Override
    public void stop() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.reset();
                recorder.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            recorder = null;
        }
    }
}