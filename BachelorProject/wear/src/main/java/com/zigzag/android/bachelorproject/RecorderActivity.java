package com.zigzag.android.bachelorproject;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import java.nio.ByteBuffer;

public class RecorderActivity extends Activity {

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private int minBufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        minBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING);

        Log.v("BachelorProject", "AudioRecord recorder minBufferSize" + minBufferSize);

        startRecording();

//        if () {
//            startRecording();
//        }
//        else {
//            stopRecording();
//        }
    }

    private void startRecording(){

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, minBufferSize);

        recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeAudioDataToBuffer();
            }
        },"AudioRecorder Thread");

        recordingThread.start();
    }

    private void writeAudioDataToBuffer(){
        byte data[] = new byte[minBufferSize];

        Log.d("write before", " " + data.length);

        recorder.read(data, 0, minBufferSize);

        Log.d("write after", " " + data.length);

    }

    private void stopRecording(){
        if(null != recorder){
            isRecording = false;

            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

    }

}



/*

byte[] data = new byte[minBufferSizeRec/2];

ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024 * 4);

Log.d(TAG, "byteBuffer: " + byteBuffer);
        Log.d(TAG, "bufferRec.length: " + bufferRec.length);
        Log.d(TAG, "bufferRec: " + bufferRec.length);

        recorder.startRecording();
        track.play();

        while (isRunning == true) {

        Log.d(TAG, "----- Running -----");

        recorder.read(bufferRec, 0, (minBufferSizeRec/2));
        for (int i = 0; i < data.length; i++) {
        data[i] = bufferRec[i];
        }

        try {
        gnMusicIdStream.audioProcess(bufferRec);
        } catch (GnException e) {
        e.printStackTrace();
        }

        track.write(data, 0, data.length);
        bufferRec = new byte[minBufferSizeRec/2];
        data = new byte[minBufferSizeRec/2];
        }*/
