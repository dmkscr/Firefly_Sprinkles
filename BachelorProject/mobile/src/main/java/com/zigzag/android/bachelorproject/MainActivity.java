package com.zigzag.android.bachelorproject;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.gracenote.gnsdk.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getName();

    /**
     * AudioRecording
     */
    private AudioRecord recorder                                             = null;
    private AudioTrack track                                                 = null;
    private AudioManager manager                                             = null;

    private static final int SAMPLERATE =                                     44100;
    private static final int RECORDER_CHANNELS =        AudioFormat.CHANNEL_IN_MONO;
    private static final int TRACK_CHANNELS =          AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_ENCODING =        AudioFormat.ENCODING_PCM_16BIT;

    private Thread myThread;
    private boolean isRunning                                               = false;

    /**
     * GNSDK
     */
    static final String gnsdkClientId                                   = "9148416";
    static final String gnsdkClientTag         = "EA1C43BD1FFE51ED7ECF272A2F04DA45";
    static final String gnsdkLicenseFilename                        = "license.txt";

    private Context context;

    private GnManager gnManager;
    private GnUser gnUser;
    private GnMusicIdStream gnMusicIdStream;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context  = this.getApplicationContext();

        setButtonHandlers();

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
            gnMusicIdStream = new GnMusicIdStream(gnUser,GnMusicIdStreamPreset.kPresetMicrophone,new MusicIDStreamEvents());
        }
        catch (GnException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ( gnMusicIdStream != null ) {

//            // Create a thread to process the data pulled from GnMic
//            // Internally pulling data is a blocking call, repeatedly called until
//            // audio processing is stopped. This cannot be called on the main thread.
//            Thread audioProcessThread = new Thread(new AudioProcessRunnable());
//            audioProcessThread.start();
//
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if ( gnMusicIdStream != null ) {

            try {

                // to ensure no pending identifications deliver results while your app is
                // paused it is good practice to call cancel
                // it is safe to call identifyCancel if no identify is pending
                gnMusicIdStream.identifyCancel();

                // stopping audio processing stops the audio processing thread started
                // in onResume
                gnMusicIdStream.audioProcessStop();

            } catch (GnException e) {

                Log.e( TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
                Log.d( TAG, " " + e.errorAPI() + ": " +  e.errorDescription() );

            }

        }
    }

    /**
     * Recording Thread
     */
    private void runThread(final boolean isRunning)
    {
        myThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                runRunnable(isRunning);
            }
        });

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        myThread.start();
    }

    public void runRunnable(boolean isRunning)
    {

        if (isRunning == true) {

            recorder = findAudioRecord();
            if (recorder == null)
            {
                Log.e(TAG, "findAudioRecord error");
                return;
            }

            track = findAudioTrack(track);
            if (track == null)
            {
                Log.e(TAG, "findAudioTrack error");
                return;
            }
            track.setPlaybackRate(SAMPLERATE);

            if ( (AudioRecord.STATE_INITIALIZED == recorder.getState()) && (AudioTrack.STATE_INITIALIZED == track.getState()) ) // delete track @the end
            {

                int minBufferSize = AudioRecord.getMinBufferSize(SAMPLERATE, RECORDER_CHANNELS, AUDIO_ENCODING);
                byte[] recordingBuffer = new byte[minBufferSize * 2];
                long bytesRead = 0;

                recorder.startRecording();

                while (isRunning == true) {

                    recorder.read(recordingBuffer, 0, recordingBuffer.length);

                    ByteBuffer byteBuffer = ByteBuffer.wrap(recordingBuffer);

                    bytesRead = gnMicrophone.getData(byteBuffer, byteBuffer.capacity());

                    try
                    {
                        gnMusicIdStream.audioProcess(byteBuffer, bytesRead);
                    }
                    catch (GnException e)
                    {
                        e.printStackTrace();
                    }
                }

            }
            else
            {
                Log.d(TAG, "Init for Recorder and Track failed");
                return;
            }
            return;
        }
        else
        {
            if (AudioRecord.STATE_INITIALIZED == recorder.getState())
            {
                recorder.stop();
                recorder.release();
            }

            if (track != null && AudioTrack.STATE_INITIALIZED == track.getState())
            {
                if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED)
                {
                    try
                    {
                        track.stop();
                    }
                    catch (IllegalStateException e)
                    {
                        e.printStackTrace();
                    }
                }
                track.release();
                manager.setMode(AudioManager.MODE_NORMAL);
            }
            return;
        }
    }

    /**
     * GNSDK MusicID-Stream event delegate
     */
    private class MusicIDStreamEvents implements IGnMusicIdStreamEvents
    {
        HashMap<String, String> gnStatus_to_displayStatus;

        public MusicIDStreamEvents()
        {
            gnStatus_to_displayStatus = new HashMap<String,String>();
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingStarted.toString(), "Identification started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingFpGenerated.toString(), "Fingerprinting complete");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted.toString(), "Lookup started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted.toString(), "Lookup started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingEnded.toString(), "Identification complete");
        }

        @Override
        public void statusEvent( GnStatus status, long percentComplete, long bytesTotalSent, long bytesTotalReceived, IGnCancellable cancellable )
        {

        }

        @Override
        public void musicIdStreamProcessingStatusEvent( GnMusicIdStreamProcessingStatus status, IGnCancellable canceller )
        {
            if(GnMusicIdStreamProcessingStatus.kStatusProcessingAudioStarted.compareTo(status) == 0)
            {
//                audioProcessingStarted = true;
                /*runOnUiThread(new Runnable()
                {
                    public void run()
                    {
                        btnStartRecording.setEnabled(true);
                    }
                });*/
            }
        }

        @Override
        public void musicIdStreamIdentifyingStatusEvent( GnMusicIdStreamIdentifyingStatus status, IGnCancellable canceller )
        {
            if(gnStatus_to_displayStatus.containsKey(status.toString()))
            {
//                setStatus( String.format("%s", gnStatus_to_displayStatus.get(status.toString())), true );
                Log.v("TAG", "musicIdStreamIdentifyingStatusEvent: " + String.format("%s", gnStatus_to_displayStatus.get(status.toString())));
            }

            if(status.compareTo( GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted ) == 0 )
            {
//                lastLookup_local = true;
            }
            else if(status.compareTo( GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted ) == 0)
            {
//                lastLookup_local = false;
            }

            if ( status == GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingEnded )
            {
//                setUIState( UIState.READY );
                Log.v("TAG", "musicIdStreamIdentifyingStatusEvent: ENDED" + String.format("%s", gnStatus_to_displayStatus.get(status.toString())));
            }
        }

        @Override
        public void musicIdStreamAlbumResult( GnResponseAlbums result, IGnCancellable canceller )
        {
            Log.v("TAG", "musicIdStreamIdentifyingStatusEvent: RESULT" + result.toString());
            /*lastLookup_matchTime = SystemClock.elapsedRealtime() - lastLookup_startTime;
            activity.runOnUiThread(new UpdateResultsRunnable( result ));*/
        }

        @Override
        public void musicIdStreamIdentifyCompletedWithError(GnError error)
        {
            /*if ( error.isCancelled() )
                setStatus( "Cancelled", true );
            else
                setStatus( error.errorDescription(), true );
            setUIState( UIState.READY );*/
        }
    }

    /**
     * OnClickListener Buttons Start and Stop Recording
     */
    private View.OnClickListener btnClick = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            switch(v.getId())
            {
                case R.id.btnStartRecording:
                {
                    isRunning = true;
                    runThread(isRunning);
                    break;
                }
                case R.id.btnStopRecording:
                {
                    isRunning = false;
                    runThread(isRunning);
                    break;
                }
            }
        }
    };

    /**
     * Button Handling and Enabling
     */
    Button btnStartRecording, btnStopRecording;

    private void setButtonHandlers() {
        btnStartRecording = ((Button)findViewById(R.id.btnStartRecording));
        btnStartRecording.setOnClickListener(btnClick);
        btnStopRecording = ((Button)findViewById(R.id.btnStopRecording));
        btnStopRecording.setOnClickListener(btnClick);
    }

    private void enableButton(int id,boolean isEnable){
        ((Button)findViewById(id)).setEnabled(isEnable);
    }

    /**
     * Helpers to find right setup for AudioTrack
     */
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

    /**
     * Helpers to find right setup for AudioRecord (depending on device)
     */
    private static int[] mSampleRates = new int[] { 44100, 22050, 11025, 8000 };
    public AudioRecord findAudioRecord() {

        for (int rate : mSampleRates) {
            for (byte audioFormat : new byte[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
                for (byte channelConfig : new byte[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
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

    /**
     * Helpers to read license file from assets as string
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