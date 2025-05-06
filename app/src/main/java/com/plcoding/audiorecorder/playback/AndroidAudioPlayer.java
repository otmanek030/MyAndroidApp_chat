package com.plcoding.audiorecorder.playback;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import java.io.File;

public class AndroidAudioPlayer implements AudioPlayer {
    private final Context context;
    private MediaPlayer player;

    public AndroidAudioPlayer(Context context) {
        this.context = context;
    }

    @Override
    public void playFile(File file) {
        MediaPlayer mediaPlayer = MediaPlayer.create(context, Uri.fromFile(file));
        if (mediaPlayer != null) {
            player = mediaPlayer;
            player.start();
        }
    }

    @Override
    public void stop() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
}