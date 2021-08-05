package com.rokid.voiceai;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initHandlers();
        initVoiceAI();
        initTtsPlayer();
        dialogueButton = (Button)findViewById(R.id.btnToggleRecord);
        updateButtonText();
        dialogueButton.setOnClickListener((v)-> {
            if (recording) {
                recording = false;
            } else {
                recording = true;
                recordThreadHandler.post(speechTask);
            }
            updateButtonText();
        });
    }

    private void updateButtonText() {
        if (recording)
            dialogueButton.setText(R.string.end_dialogue);
        else
            dialogueButton.setText(R.string.start_dialogue);
    }

    private void initHandlers() {
        mainHandler = new Handler(getMainLooper());
        recordThread = new HandlerThread("audioRecord");
        recordThread.start();
        recordThreadHandler = new Handler(recordThread.getLooper());
    }

    private void initVoiceAI() {
        voiceAI = new VoiceAI.Builder()
                .setSpeechUri(SPEECH_URI)
                .setReportUri(REPORT_URI)
                .setAuthInfo(ROKID_KEY, ROKID_SECRET, ROKID_DEVICE_TYPE_ID, ROKID_DEVICE_ID)
                .setVoiceCallback(voiceCallback)
                .setActionCallback(actionCallback)
                .build();
    }

    private AudioRecord createAudioRecord() {
        AudioFormat af = new AudioFormat.Builder()
                .setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        int bufsize = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        bufsize *= 2;
        AudioRecord.Builder builder = new AudioRecord.Builder();
        return builder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(af)
                .setBufferSizeInBytes(bufsize)
                .build();
    }

    private void endRecording() {
        mainHandler.post(() -> {
            recording = false;
            updateButtonText();
        });
    }

    private void initTtsPlayer() {
        AudioFormat af = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(24000)
                .build();
        AudioAttributes aa = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        int bufsize = AudioTrack.getMinBufferSize(24000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        bufsize *= 2;
        ttsPlayer = new AudioTrack.Builder()
                .setAudioFormat(af)
                .setAudioAttributes(aa)
                .setBufferSizeInBytes(bufsize)
                .build();
        ttsPlayer.play();
    }

    private boolean recording = false;
    private Button dialogueButton;
    private HandlerThread recordThread;
    private Handler recordThreadHandler;
    private Handler mainHandler;
    private VoiceAI voiceAI;
    private Runnable speechTask = ()-> {
        AudioRecord record = createAudioRecord();
        byte[] data = new byte[RECORD_BUFSIZE];
        record.startRecording();
        voiceAI.startVoice(null);
        while (recording) {
            int c = record.read(data, 0, RECORD_BUFSIZE);
            if (c < 0)
                break;
            if (c > 0) {
                Log.d(TAG, "录音数据字节: " + c);
                voiceAI.writeVoice(data, 0, c);
            }
        }
        record.stop();
        record.release();
        if (recording) {
            endRecording();
        }
    };

    private VoiceCallback voiceCallback = new VoiceCallback() {
        @Override
        public void onAccept(String status) {
            if (status.equals("fake") || status.equals("reject"))
                endRecording();
        }

        @Override
        public void onAsr(String asr, boolean inter) {
            if (!inter)
                endRecording();
        }

        @Override
        public void onResult(String nlp, String action) {
        }

        @Override
        public void onError(int code) {
            endRecording();
        }
    };
    private ActionCallback actionCallback = new ActionCallback() {
        @Override
        public void onSessionStart(Session sess) {
        }

        @Override
        public void onSessionActionCompleted(Session sess) {
        }

        @Override
        public void onSessionEnd(Session sess) {
        }

        @Override
        public void onPlayTts(Session sess, String text) {
            Log.d(TAG, "play tts: " + text);
            voiceAI.textToSpeech(text, ttsCallback);
        }

        @Override
        public void onStopTts(Session sess) {
        }

        @Override
        public void onPlayMedia(Session sess, String url, int position) {
        }

        @Override
        public int onStopMedia(Session sess) {
            return 0;
        }

        @Override
        public void onPickup(Session sess, boolean on, int dur) {
        }

        @Override
        public void onNativeAction(Session sess, String nlp) {
        }

        @Override
        public void onNativeExit(Session sess) {
        }

        @Override
        public void onNativePause(Session sess) {
        }

        @Override
        public void onNativeResume(Session sess) {
        }
    };
    private TtsCallback ttsCallback = new TtsCallback() {
        @Override
        public void onAudioData(byte[] data, int offset, int length) {
            ttsPlayer.write(data, offset, length);
        }

        @Override
        public void onCompleted() {
            Log.d(TAG, "tts completed");
        }

        @Override
        public void onError(int code) {
            Log.d(TAG, "tts error: " + code);
        }
    };
    private AudioTrack ttsPlayer;
    private static final int RECORD_BUFSIZE = 4000;
    private static final String TAG = "VoiceAI.Demo";
    private static final String SPEECH_URI = "wss://cloudapigw.open.rokid.com:443/api";
    private static final String REPORT_URI = "https://apigwrest.open.rokid.com:443/v1/skill/dispatch/sendEvent";
//    private static final String ROKID_KEY = "6DDECE40ED024837AC9BDC4039DC3245";
//    private static final String ROKID_SECRET = "F2A1FDC667A042F3A44E516282C3E1D7";
//    private static final String ROKID_DEVICE_TYPE_ID = "B16B2DFB5A004DCBAFD0C0291C211CE1";
//    private static final String ROKID_DEVICE_ID = "rokid.voiceai.demo";
    private static final String ROKID_KEY = "CF7FE812A9D24A88879C5EB56178A26B";
    private static final String ROKID_SECRET = "1B59E55E56B848EFAA6910D7E67A567D";
    private static final String ROKID_DEVICE_TYPE_ID = "A2BDBA5C696E4D78ABC95D557729EAF6";
    private static final String ROKID_DEVICE_ID = "181561978000002";
}