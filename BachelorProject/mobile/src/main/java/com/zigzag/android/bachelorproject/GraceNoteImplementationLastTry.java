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

import com.gracenote.gnsdk.GnError;
import com.gracenote.gnsdk.GnException;
import com.gracenote.gnsdk.GnLicenseInputMode;
import com.gracenote.gnsdk.GnManager;
import com.gracenote.gnsdk.GnMusicIdStream;
import com.gracenote.gnsdk.GnMusicIdStreamIdentifyingStatus;
import com.gracenote.gnsdk.GnMusicIdStreamPreset;
import com.gracenote.gnsdk.GnMusicIdStreamProcessingStatus;
import com.gracenote.gnsdk.GnResponseAlbums;
import com.gracenote.gnsdk.GnStatus;
import com.gracenote.gnsdk.GnUser;
import com.gracenote.gnsdk.GnUserStore;
import com.gracenote.gnsdk.IGnCancellable;
import com.gracenote.gnsdk.IGnMusicIdStreamEvents;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getName();

    private AudioRecord recorder = null;
    private AudioTrack track = null;
    private AudioManager manager                                             = null;

    //    private static final int SAMPLERATE = 44100;
    private static final int SAMPLERATE = 8000; // at least 22050 for gracenote
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int TRACK_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private int minBufferSizeRec;
    byte[] bufferRec;

    private Thread myThread;
    private boolean isRunning = false;

    /**
     * GNSDK
     */
    static final String gnsdkClientId                                   = "9148416";
    static final String gnsdkClientTag         = "EA1C43BD1FFE51ED7ECF272A2F04DA45";
    static final String gnsdkLicenseFilename                        = "license.txt";


    private GnManager gnManager;
    private GnUser gnUser;
    private GnMusicIdStream gnMusicIdStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context  = this.getApplicationContext();

        setButtonHandlers();

        enableButton(R.id.btnStartRecording,true);
        enableButton(R.id.btnStopRecording,false);

        minBufferSizeRec = AudioRecord.getMinBufferSize(SAMPLERATE,RECORDER_CHANNELS,AUDIO_ENCODING);
        bufferRec = new byte[minBufferSizeRec/2];

        manager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
        manager.setMode(AudioManager.MODE_NORMAL);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        String gnsdkLicense = null;

        if ( (gnsdkLicenseFilename == null) || (gnsdkLicenseFilename.length() == 0) )
        {
            Log.d( TAG, "License filename not set" );
        }
        else
        {
            gnsdkLicense = getAssetAsString( gnsdkLicenseFilename );

            if ( gnsdkLicense == null )
            {
                Log.d(TAG, "License file not found: " + gnsdkLicenseFilename );
                return;
            }
        }

        try
        {
            gnManager = new GnManager( context, gnsdkLicense, GnLicenseInputMode.kLicenseInputModeString );
            gnUser = new GnUser( new GnUserStore(context), gnsdkClientId, gnsdkClientTag, TAG );
        }
        catch ( GnException e )
        {
            Log.e(TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            Log.d( TAG, " " + e.errorAPI() + ": " + e.errorDescription() );
            return;
        }
        catch ( Exception e )
        {
            if(e.getMessage() != null)
            {
                Log.e(TAG, e.getMessage() );
                Log.d( TAG, " " + e.getMessage() );
            }
            else
            {
                e.printStackTrace();
            }
            return;
        }

        try
        {
            gnMusicIdStream = new GnMusicIdStream(gnUser, GnMusicIdStreamPreset.kPresetMicrophone,new IGnMusicIdStreamEvents() {
                @Override
                public void musicIdStreamProcessingStatusEvent(GnMusicIdStreamProcessingStatus gnMusicIdStreamProcessingStatus, IGnCancellable iGnCancellable) {
                    Log.d(TAG, "----- musicIdStreamProcessingStatusEvent -----");
                }

                @Override
                public void musicIdStreamIdentifyingStatusEvent(GnMusicIdStreamIdentifyingStatus gnMusicIdStreamIdentifyingStatus, IGnCancellable iGnCancellable) {
                    Log.d(TAG, "----- musicIdStreamIdentifyingStatusEvent -----"+gnMusicIdStreamIdentifyingStatus.toString());
                }

                @Override
                public void musicIdStreamAlbumResult(GnResponseAlbums gnResponseAlbums, IGnCancellable iGnCancellable) {
                    Log.d(TAG, "----- musicIdStreamAlbumResult -----");
                    Log.d("MainActivity","Album result" + gnResponseAlbums.toString());
                }

                @Override
                public void musicIdStreamIdentifyCompletedWithError(GnError gnError) {
                    Log.d(TAG, "----- musicIdStreamIdentifyCompletedWithError -----");
                }

                @Override
                public void statusEvent(GnStatus gnStatus, long l, long l2, long l3, IGnCancellable iGnCancellable) {
                    Log.d(TAG, "----- statusEvent -----"+gnStatus.toString());
                }
            });
        }
        catch (GnException e)
        {
            e.printStackTrace();
        }


    }

    private void enableButton(int id,boolean isEnable){
        ((Button)findViewById(id)).setEnabled(isEnable);
    }

    private void setButtonHandlers() {
        ((Button)findViewById(R.id.btnStartRecording)).setOnClickListener(btnClick);
        ((Button)findViewById(R.id.btnStopRecording)).setOnClickListener(btnClick);
    }


    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()){
                case R.id.btnStartRecording:{
                    isRunning = true;
                    runThread();
                    enableButton(R.id.btnStartRecording,false);
                    enableButton(R.id.btnStopRecording, true);
                    break;
                }
                case R.id.btnStopRecording:{
                    isRunning = false;
                    //runThread(isRunning);
                    enableButton(R.id.btnStartRecording,true);
                    enableButton(R.id.btnStopRecording, false);
                    break;
                }
            }
        }
    };

    private void runThread(){
        myThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runRunnable();
                } catch (GnException e) {
                    e.printStackTrace();
                }
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

    public void runRunnable() throws GnException {

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

                byte[] data = new byte[minBufferSizeRec/2];

                recorder.startRecording();
                track.play();
                int bytesRead = 0;

                Log.d(TAG, "----- gnMusicIdStream init -----");
                gnMusicIdStream.audioProcessStart(
                        8000,
                        16,
                        2);
                Log.d(TAG, "----- gnMusicIdStream iit after -----");

                while (isRunning == true) {

                    Log.d(TAG, "----- Running -----");

                    bytesRead = recorder.read(bufferRec, 0, (minBufferSizeRec/2));
                    for (int i = 0; i < data.length; i++) {
                        data[i] =  bufferRec[i];
                    }
                    track.write(data, 0, data.length);

                    try
                    {
                        // Log.d(TAG, "----- before -----");
                        gnMusicIdStream.audioProcess(data);
                        // Log.d(TAG, "----- after -----");
                    }
                    catch (GnException e)
                    {
                        e.printStackTrace();
                    }


                    bufferRec = new byte[minBufferSizeRec/2];
                    data = new byte[minBufferSizeRec/2];

                }

                Log.d(TAG, "----- identifyskjsabfkhf -----");
                gnMusicIdStream.identifyAlbumAsync();

                // kill/destroy/interrupt thread
                // null thread
                // empty/clear buffer

            } else {
                Log.d(TAG, "Init for Recorder and Track failed");
                return;
            }
//            myThread.interrupt();
//            myThread = null;
            return;

        }
    }

    //private static int[] mSampleRates = new int[] { 44100, 22050, 11025, 8000 };
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


// MusicID
// MusicID-Stream
/*

// Initialize mic
gnMicrophone = new GnMic(44100, 16, 1);
        gnMicrophone.sourceInit();

// Initialize music id stream
        gnMusicIdStream = new GnMusicIdStream(gnMusicUser, new GnMusicIdStreamEvents());
        gnMusicIdStream.audioProcessStart(
        gnMicrophone.samplesPerSecond(),
        gnMicrophone.sampleSizeInBits(),
        gnMicrophone.numberOfChannels());

// Create thread to process audio data from the mic
        Thread audioProcessThread = new Thread(new Runnable()
        {
@Override
public void run()
        {
        try
        {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024*4);
        long bytesRead = 0;
        while(isListening)
        {
        bytesRead = gnMicrophone.getData(byteBuffer, byteBuffer.capacity());
        gnMusicIdStream.audioProcess(byteBuffer.array(), bytesRead);
        }
        }
        }
        });

// Call this method when user requests identification
        gnMusicIdStream.identifyAlbumAsync();

// Handle results in callback
 */



    /*

    MusicIDStreamEvents musicIDStreamEvents = new MusicIDStreamEvents();

    private class MusicIDStreamEvents extends GnMusicIdStreamEventsListener {

        HashMap<String, String> gnStatus_to_displayStatus;

        public MusicIDStreamEvents(){
            gnStatus_to_displayStatus = new HashMap<String,String>();
            gnStatus_to_displayStatus.put("kStatusStarted", "Identification started");
            gnStatus_to_displayStatus.put("kStatusFpGenerated", "Fingerprinting complete");

            //gnStatus_to_displayStatus.put("kStatusIdentifyingOnlineQueryStarted", "Online query started");
            gnStatus_to_displayStatus.put("kStatusIdentifyingEnded", "Identification complete");
        }

        @Override
        public void statusEvent( GnStatus status, long percentComplete, long bytesTotalSent, long bytesTotalReceived, IGnCancellable cancellable ) {
            //setStatus( String.format("%d%%",percentComplete), true );
        }

        @Override
        public void musicIdStreamStatusEvent( GnMusicIdStreamStatus status, IGnCancellable cancellable ) {
            if(gnStatus_to_displayStatus.containsKey(status.toString())){
                setStatus( String.format("%s", gnStatus_to_displayStatus.get(status.toString())), true );
            }
        }

        @Override
        public void musicIdStreamResultAvailable( GnResponseAlbums result, IGnCancellable cancellable ) {
            activity.runOnUiThread(new UpdateResultsRunnable( result ));
            setStatus( "Success", true );
            setUIState( UIState.READY );
        }
    }
    */
    private String getAssetAsString( String assetName ){

        String 		assetString = null;
        InputStream assetStream;

        try {

            assetStream = this.getApplicationContext().getAssets().open(assetName);
            if(assetStream != null){

                java.util.Scanner s = new java.util.Scanner(assetStream).useDelimiter("\\A");

                assetString = s.hasNext() ? s.next() : "";
                assetStream.close();

            }else{
                Log.e(TAG, "Asset not found:" + assetName);
            }

        } catch (IOException e) {


            Log.e( TAG, "Error getting asset as string: " + e.getMessage() );

        }

        return assetString;
    }
}

