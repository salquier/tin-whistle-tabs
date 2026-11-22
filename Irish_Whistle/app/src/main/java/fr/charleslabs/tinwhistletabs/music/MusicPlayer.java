package fr.charleslabs.tinwhistletabs.music;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.PresetReverb;
import android.os.Handler;

import java.util.List;

import fr.charleslabs.tinwhistletabs.music.synth.TinWhistleSynth;

public class MusicPlayer {
    private static final int SAMPLE_RATE = 22050;

    // Internal states
    private AudioTrack audioTrack = null;
    private final Handler handler = new Handler();

    //Singleton
    private static MusicPlayer instance;
    public static MusicPlayer getInstance(){
        if(instance == null){
            instance = new MusicPlayer();
        }
        return instance;
    }
    private MusicPlayer(){}

    public static byte[] genMusic(List<MusicNote> notes, float tempoModifier){
        final float[] frequencies = new float[notes.size()];
        final int[] lengths = new int[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            final MusicNote note = notes.get(i);
            frequencies[i] = note.isRest() ? 0f : note.getFrequency();
            lengths[i] = (int) (note.getLengthInS(tempoModifier) * SAMPLE_RATE);
        }
        return toneToBytePCM(TinWhistleSynth.render(frequencies, lengths, SAMPLE_RATE));
    }

    // Media controls: set, play, pause, stop, clear
    public void setAudioTrack(byte[] generatedSnd){
        if(audioTrack != null){
            audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
        }
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);

        // PRESET
        try {
            final PresetReverb reverb = new PresetReverb(0, audioTrack.getAudioSessionId());
            reverb.setPreset(PresetReverb.PRESET_SMALLROOM);
            reverb.setEnabled(true);
            audioTrack.setAuxEffectSendLevel(1.0f);
        }catch (Exception ignored){}

        audioTrack.write(generatedSnd, 0, generatedSnd.length);
    }

    public void play() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        if( audioTrack != null) audioTrack.play();
                    }
                });
            }
        });
        thread.start();
    }

    public void pause() {
        if (audioTrack != null){
            audioTrack.pause();
            audioTrack.flush();
        }
    }

    public void stop() {
        // @TODO Fade volume
        if (audioTrack != null)
            //fadeOutAndStop();
            audioTrack.stop();
    }

    public void move(float time) {
        if (audioTrack != null)
            audioTrack.setPlaybackHeadPosition((int)(time*SAMPLE_RATE));
    }

    private static byte[] toneToBytePCM(double[] tone){
        final byte[] generatedSnd = new byte[tone.length * 2];
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (double dVal : tone) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        return generatedSnd;
    }

    private static byte[] toneToBytePCM(float[] tone){
        final byte[] generatedSnd = new byte[tone.length * 2];
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (float dVal : tone) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        return generatedSnd;
    }

    /*float volume;
    final private static int FADE_DURATION = 300; //The duration of the fade
    final private static  int FADE_INTERVAL = 150; //The amount of time between volume changes.
    private void fadeOutAndStop(){
        volume = AudioTrack.getMaxVolume();
        final float deltaVolume = volume / (float)(FADE_DURATION/FADE_INTERVAL);
        final Timer timer = new Timer(true);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                volume -= deltaVolume;
                System.out.println(volume);
                if (volume >= AudioTrack.getMinVolume())
                    audioTrack.setStereoVolume(volume,volume);
                //Cancel and Purge the Timer if the desired volume has been reached
                else{
                    timer.cancel();
                    timer.purge();
                    audioTrack.stop();
                    audioTrack.setStereoVolume(1,1);
                }
            }
        };
        timer.schedule(timerTask,FADE_INTERVAL,FADE_INTERVAL);
    }*/
}
