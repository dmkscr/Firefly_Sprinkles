package com.zigzag.android.bachelorproject;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getName();

    private AudioRecord recorder = null;
    private AudioTrack track = null;

    private static final int SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int TRACK_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private int minBufferSizeRec;
    short[] bufferRec;

    private Thread myThread;
    private boolean isRunning = false;

    private AudioManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonHandlers();

        enableButton(R.id.btnStartRecording,true);
        enableButton(R.id.btnStopRecording,false);

        minBufferSizeRec = AudioRecord.getMinBufferSize(SAMPLERATE,RECORDER_CHANNELS,AUDIO_ENCODING);
        bufferRec = new short[minBufferSizeRec/2];

        manager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
        manager.setMode(AudioManager.MODE_NORMAL);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    }

    private void enableButton(int id,boolean isEnable){
        ((Button)findViewById(id)).setEnabled(isEnable);
    }

    private void setButtonHandlers() {
        ((Button)findViewById(R.id.btnStartRecording)).setOnClickListener(btnClick);
        ((Button)findViewById(R.id.btnStopRecording)).setOnClickListener(btnClick);
    }

    // das mit den buttons ist falsch gebaut, da der stop button einen neuen, zweiten thread laufen laesst anstatt den ersten zu beenden,
    // aber das recording und live play back funktioniert (spaeter gibt es ja eh keinen stop button, sondern es wird intervall-weise recorded)
    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()){
                case R.id.btnStartRecording:{
                    isRunning = true;
                    runThread(isRunning);
                    enableButton(R.id.btnStartRecording,false);
                    enableButton(R.id.btnStopRecording, true);
                    break;
                }
                case R.id.btnStopRecording:{
                    isRunning = false;
                    runThread(isRunning);
                    enableButton(R.id.btnStartRecording,true);
                    enableButton(R.id.btnStopRecording, false);
                    break;
                }
            }
        }
    };

    private void runThread(final boolean flag){
        myThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runRunnable(flag);
            }
        });
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        myThread.start();
    }

    public AudioTrack findAudioTrack (AudioTrack track) {
        int myBufferSize = AudioTrack.getMinBufferSize(SAMPLERATE, TRACK_CHANNELS, AUDIO_ENCODING);

        if (myBufferSize != AudioTrack.ERROR_BAD_VALUE) {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLERATE, TRACK_CHANNELS, AUDIO_ENCODING, myBufferSize, AudioTrack.MODE_STREAM);

            track.setPlaybackRate(SAMPLERATE);

            if (track.getState() == AudioTrack.STATE_UNINITIALIZED) {
                Log.e(TAG, "AudioTrack Uninitialized");
                return null;
            }
        }
        return track;
    }

    public void runRunnable(boolean isRunning){

        Log.d(TAG,"isRunning " + isRunning);

        if (isRunning == false) {

            if (AudioRecord.STATE_INITIALIZED == recorder.getState()) {
                recorder.stop();
                recorder.release();
            }

            if (track != null && AudioTrack.STATE_INITIALIZED == track.getState()) {

                if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {

                    try{
                        track.stop();
                    }catch (IllegalStateException e)
                    {
                        e.printStackTrace();
                    }

                }

                track.release();
                manager.setMode(AudioManager.MODE_NORMAL);

            }
            return;

        } else if (isRunning == true) {

            recorder = findAudioRecord();
            if (recorder == null) {
                Log.e(TAG, "findAudioRecord error");
                return;
            }

            track = findAudioTrack(track);
            if (track == null) {
                Log.e(TAG, "findAudioTrack error");
                return;
            }
            track.setPlaybackRate(SAMPLERATE);

            if ((AudioRecord.STATE_INITIALIZED == recorder.getState()) && (AudioTrack.STATE_INITIALIZED == track.getState())) {

                short[] data = new short[minBufferSizeRec/2];

                recorder.startRecording();
                track.play();

                while (isRunning == true) {

                    Log.d(TAG, "----- Running -----");

                    recorder.read(bufferRec, 0, (minBufferSizeRec/2));
                    for (int i = 0; i < data.length; i++) {
                        data[i] = bufferRec[i];
                    }
                    track.write(data, 0, data.length);
                    bufferRec = new short[minBufferSizeRec/2];
                    data = new short[minBufferSizeRec/2];

                }

            } else {
                Log.d(TAG, "Init for Recorder and Track failed");
                return;
            }
            return;

        }
    }

    private static int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100 };
    public AudioRecord findAudioRecord() {

        for (int rate : mSampleRates) {
            for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
                for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
                    try {
                        Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
                                + channelConfig);
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            Log.d(TAG, "Found rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
                                    + channelConfig);

                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                                return recorder;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, rate + "Exception, keep trying.",e);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
