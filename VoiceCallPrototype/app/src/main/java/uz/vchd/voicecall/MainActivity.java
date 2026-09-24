package uz.vchd.voicecall;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.text.InputType;

public class MainActivity extends Activity {
    private final int rate = 16000;
    private volatile boolean running;
    private volatile int effect = 0;
    private Thread audioThread;
    private AudioRecord recorder;
    private AudioTrack speaker;
    private TextView status;
    private Button preview;
    private EditText number;
    private int phase;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(Color.rgb(245, 248, 252));
        TextView title = label("Ovozli qo‘ng‘iroq", 26);
        root.addView(title);
        root.addView(label("Telefoningizda ovoz effektlarini sinang", 16));
        number = new EditText(this);
        number.setHint("+998 XX XXX XX XX");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(number);
        RadioGroup voices = new RadioGroup(this);
        String[] names = {"Original", "Yumshoq", "Kuchli", "Robot"};
        for (int i = 0; i < names.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setText(names[i]); button.setId(i + 100);
            voices.addView(button);
        }
        voices.check(100);
        voices.setOnCheckedChangeListener((g, id) -> effect = id - 100);
        root.addView(voices);
        preview = new Button(this);
        preview.setText("🎧 OVOZNI SINASH");
        preview.setOnClickListener(v -> { if (running) stopAudio(); else requestPreview(); });
        root.addView(preview);
        Button call = new Button(this);
        call.setText("📞 QO‘NG‘IROQ — GATEWAY KERAK");
        call.setEnabled(false);
        root.addView(call);
        status = label("Quloqchin ulang. Ovoz sinovi telefonda ishlaydi. Oddiy raqamga chiqish uchun alohida GSM/SIP gateway kerak.", 14);
        root.addView(status);
        setContentView(root);
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(24, 39, 58));
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(0, 12, 0, 12);
        return view;
    }

    private void requestPreview() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return;
        }
        startAudio();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 10) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startAudio();
            else status.setText("Mikrofonga ruxsat berilmadi.");
        }
    }

    private void startAudio() {
        if (running) return;
        int inputMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int outputMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (inputMin <= 0 || outputMin <= 0) { status.setText("Audio qurilma ishlamadi."); return; }
        try {
            int size = Math.max(4096, Math.max(inputMin, outputMin));
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            speaker = new AudioTrack.Builder().setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED || speaker.getState() != AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("Audio qurilma tayyor emas");
            recorder.startRecording(); speaker.play(); running = true;
            preview.setText("■ SINOVNI TO‘XTATISH");
            status.setText("Mikrofon → effekt → quloqchin. Bu AI ovoz modeli emas, mahalliy audio effekt sinovi.");
            audioThread = new Thread(this::audioLoop, "audio-preview"); audioThread.start();
        } catch (Exception ex) { stopAudio(); status.setText("Audio xatosi: " + ex.getMessage()); }
    }

    private void audioLoop() {
        short[] data = new short[320];
        AudioRecord input = recorder;
        AudioTrack output = speaker;
        while (running) {
            int count = input.read(data, 0, data.length);
            if (count <= 0) continue;
            int mode = effect;
            for (int i = 0; i < count; i++) {
                double x = data[i];
                // Local DSP preview; formant-preserving AI conversion is a later gateway component.
                if (mode == 1) x = Math.tanh(x / 12000.0) * 18500;
                if (mode == 2) x = Math.tanh(x / 8000.0) * 24000;
                if (mode == 3) x *= Math.sin(2 * Math.PI * 75 * (phase++ / (double) rate));
                data[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x)));
            }
            int written = 0;
            while (running && written < count) {
                int n = output.write(data, written, count - written);
                if (n <= 0) break;
                written += n;
            }
        }
    }

    private void stopAudio() {
        running = false;
        AudioRecord input = recorder;
        AudioTrack output = speaker;
        if (input != null) { try { input.stop(); } catch (Exception ignored) {} }
        if (output != null) { try { output.pause(); output.flush(); } catch (Exception ignored) {} }
        Thread worker = audioThread;
        if (worker != null && worker != Thread.currentThread()) {
            try { worker.join(500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        if (input != null) { input.release(); recorder = null; }
        if (output != null) { output.release(); speaker = null; }
        audioThread = null;
        if (preview != null) preview.setText("🎧 OVOZNI SINASH");
    }
    @Override protected void onStop() { stopAudio(); super.onStop(); }
}
