package org.telegram.messenger.partisan.voicechange;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.settings.TesterSettings;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.ButtonWithIconItem;
import org.telegram.messenger.partisan.ui.items.DelimiterItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.HeaderItem;
import org.telegram.messenger.partisan.ui.PartisanBaseFragment;
import org.telegram.messenger.partisan.ui.items.RadioButtonItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.messenger.partisan.ui.items.ValuesSlideChooseItem;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AlertsCreator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VoiceChangeSettingsFragment extends PartisanBaseFragment {
    private static final float[] FEMALE_PITCH_VALUES = {1.25f, 1.4f, 1.6f, 1.8f, 2.0f};
    private static final float[] FEMALE_FORMANT_VALUES = {1.05f, 1.1f, 1.2f, 1.3f, 1.4f};

    private AbstractViewItem aggressiveChangeLevelItem;
    private AbstractViewItem moderateChangeLevelItem;
    private AbstractViewItem recordItem;
    private AbstractViewItem playOriginalItem;
    private AbstractViewItem playChangedItem;
    private AbstractViewItem enableForIndividualAccountsItem;
    private AbstractViewItem benchmarkItem;

    private AudioRecord audioRecorder = null;
    private VoiceChanger voiceChanger = null;
    private final DispatchQueue recordQueue = new DispatchQueue("recordQueue");
    private ByteArrayOutputStream changedOutputAudioBuffer = null;
    private ByteArrayOutputStream originalOutputAudioBuffer = null;
    private Runnable voiceChangingFinishedCallback = null;

    private final VoiceChangeExamplePlayer changedPlayer = new VoiceChangeExamplePlayer();
    private final VoiceChangeExamplePlayer originalPlayer = new VoiceChangeExamplePlayer();

    private static final int sampleRate = 48000;
    private static final int recordBufferSize = 1280;

    private String benchmarkRatioText = "";

    @Override
    protected String getTitle() {
        return getString(R.string.VoiceChange);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new ToggleItem(this, getString(R.string.Enable), VoiceChangeSettings.voiceChangeEnabled::getOrDefault, value -> {
                    VoiceChangeSettings.voiceChangeEnabled.set(value);
                    if (VoiceChangeSettings.areSettingsEmpty()) {
                        new VoiceChangeSettingsGenerator().generateParameters(true);
                    }
                    listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
                    showCurrentCallWillNotBeAffectedDialogIfNeeded();
                }),
                new DelimiterItem(this),


                new HeaderItem(this, getString(R.string.VoicePreset)),
                new RadioButtonItem(this, getString(R.string.RandomVoicePreset),
                        () -> !VoiceChangeSettings.femaleVoiceEnabled.getOrDefault(),
                        () -> selectFemaleVoice(false))
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new RadioButtonItem(this, getString(R.string.FemaleVoicePreset),
                        VoiceChangeSettings.femaleVoiceEnabled::getOrDefault,
                        () -> selectFemaleVoice(true))
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.FemaleVoicePresetDescription)),

                new HeaderItem(this, getString(R.string.FemaleVoicePitch)),
                new ValuesSlideChooseItem(this, new String[]{"1.25×", "1.40×", "1.60×", "1.80×", "2.00×"},
                        () -> closestValueIndex(FEMALE_PITCH_VALUES, VoiceChangeSettings.femaleVoicePitch.getOrDefault()),
                        index -> {
                            if (!isFemaleVoiceEnabled() || index < 0 || index >= FEMALE_PITCH_VALUES.length) {
                                return;
                            }
                            VoiceChangeSettings.femaleVoicePitch.set(FEMALE_PITCH_VALUES[index]);
                            femaleVoiceParametersChanged();
                        })
                        .addEnabledCondition(this::isFemaleVoiceEnabled),
                new HeaderItem(this, getString(R.string.FemaleVoiceTone)),
                new ValuesSlideChooseItem(this, new String[]{"1.05×", "1.10×", "1.20×", "1.30×", "1.40×"},
                        () -> closestValueIndex(FEMALE_FORMANT_VALUES, VoiceChangeSettings.femaleVoiceFormantRatio.getOrDefault()),
                        index -> {
                            if (!isFemaleVoiceEnabled() || index < 0 || index >= FEMALE_FORMANT_VALUES.length) {
                                return;
                            }
                            VoiceChangeSettings.femaleVoiceFormantRatio.set(FEMALE_FORMANT_VALUES[index]);
                            femaleVoiceParametersChanged();
                        })
                        .addEnabledCondition(this::isFemaleVoiceEnabled),
                new DescriptionItem(this, getString(R.string.FemaleVoiceNextCall)),

                new HeaderItem(this, getString(R.string.VoiceChangeLevel)),
                aggressiveChangeLevelItem = new RadioButtonItem(this, getString(R.string.AggressiveVoiceChange),
                        VoiceChangeSettings.aggressiveChangeLevel::getOrDefault,
                        () -> changeAggressiveness(true))
                        .addEnabledCondition(this::isRandomVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.AggressiveVoiceChangeDescription)),
                moderateChangeLevelItem = new RadioButtonItem(this, getString(R.string.ModerateVoiceChange),
                        () -> !VoiceChangeSettings.aggressiveChangeLevel.getOrDefault(),
                        () -> changeAggressiveness(false))
                        .addEnabledCondition(this::isRandomVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.ModerateVoiceChangeDescription)),


                new HeaderItem(this, getString(R.string.Quality)),
                new ValuesSlideChooseItem(this,
                        new String[]{getString(R.string.Quality480) /*Low*/, getString(R.string.Quality720) /*Medium*/, getString(R.string.Quality1080) /*High*/},
                        VoiceChangeSettingsFragment::getQualityIndex,
                        this::setQuality)
                        .addEnabledCondition(this::isRandomVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.VoiceChangeQualityDescription)),


                new HeaderItem(this, getString(R.string.CheckVoiceChanging)),
                recordItem = new RecordItem(this, () -> audioRecorder != null, this::onRecordClicked)
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                playChangedItem = new ButtonWithIconItem(this, getString(R.string.PlayChangedVoice), R.drawable.quantum_ic_play_arrow_white_24,
                        view -> onPlayerButtonClicked(view, true))
                        .addEnabledCondition(() -> isVoiceChangeEnabled() && changedOutputAudioBuffer != null),
                playOriginalItem = new ButtonWithIconItem(this, getString(R.string.PlayNormalVoice), R.drawable.quantum_ic_play_arrow_white_24,
                        view -> onPlayerButtonClicked(view, false))
                        .addEnabledCondition(() -> isVoiceChangeEnabled() && originalOutputAudioBuffer != null),
                new DescriptionItem(this, getString(R.string.CheckVoiceChangingDescription)),


                new ButtonWithIconItem(this, getString(R.string.GenerateNewVoiceChangeParameters), R.drawable.quantum_ic_refresh_white_24,
                        view -> {
                            if (!isRandomVoiceChangeEnabled()) {
                                return;
                            }
                            new VoiceChangeSettingsGenerator().generateParameters(true);
                            voiceChangingParametersChanged();
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            Toast.makeText(getContext(), getString(R.string.VoiceChanged), Toast.LENGTH_SHORT).show();
                            showCurrentCallWillNotBeAffectedDialogIfNeeded();
                        })
                        .addEnabledCondition(this::isRandomVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.GenerateNewVoiceChangeParametersDescription)),


                enableForIndividualAccountsItem = new ButtonItem(this, getString(R.string.EnableForIndividualAccounts),
                        () -> {
                            int enabledCount = getVoiceChangeEnabledAccounts().size();
                            return enabledCount == UserConfig.getActivatedAccountsCount()
                                    ? getString(R.string.FilterAllChatsShort)
                                    : enabledCount + "/" + UserConfig.getActivatedAccountsCount();
                        },
                        view -> showEnableForIndividualAccountsDialog())
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new DelimiterItem(this),


                new ToggleItem(this, getString(R.string.EnableForVoiceMessages),
                        () -> VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.VOICE_MESSAGE),
                        value -> VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.VOICE_MESSAGE))
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new ToggleItem(this, getString(R.string.EnableForVideoMessages),
                        () -> VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.VIDEO_MESSAGE),
                        value -> VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.VIDEO_MESSAGE))
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new ToggleItem(this, getString(R.string.EnableForVideoCalls),
                        () -> VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.CALL),
                        value -> {
                            VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.CALL);
                            showCurrentCallWillNotBeAffectedDialogIfNeeded();
                        })
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new DelimiterItem(this),


                new ToggleItem(this, getString(R.string.ShowVoiceChangedNotification), VoiceChangeSettings.showVoiceChangedNotification)
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new DescriptionItem(this, getString(R.string.ShowVoiceChangedNotificationDescription)),


                new ToggleItem(this, getString(R.string.WorksWithFakePasscodes), VoiceChangeSettings.voiceChangeWorksWithFakePasscode)
                        .addEnabledCondition(this::isVoiceChangeEnabled),
                new DelimiterItem(this).addCondition(TesterSettings::areTesterSettingsActivated),

                benchmarkItem = new ButtonItem(this, "Benchmark", () -> benchmarkRatioText, view -> runBenchmark())
                        .addCondition(TesterSettings::areVoiceChangingSettingsVisible)
                        .addEnabledCondition(() -> isVoiceChangeEnabled() && originalOutputAudioBuffer != null),
        };
    }

    private boolean isVoiceChangeEnabled() {
        return VoiceChangeSettings.voiceChangeEnabled.get().orElse(false);
    }

    private boolean isFemaleVoiceEnabled() {
        return isVoiceChangeEnabled() && VoiceChangeSettings.femaleVoiceEnabled.getOrDefault();
    }

    private static int closestValueIndex(float[] values, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return values.length / 2;
        }
        int closest = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - value) < Math.abs(values[closest] - value)) {
                closest = i;
            }
        }
        return closest;
    }

    private boolean isRandomVoiceChangeEnabled() {
        return isVoiceChangeEnabled() && !VoiceChangeSettings.femaleVoiceEnabled.getOrDefault();
    }

    private void selectFemaleVoice(boolean female) {
        if (!isVoiceChangeEnabled() || !VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(female)) {
            return;
        }
        voiceChangingParametersChanged();
        listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
        showCurrentCallWillNotBeAffectedDialogIfNeeded();
    }

    private void femaleVoiceParametersChanged() {
        if (!isFemaleVoiceEnabled()) {
            return;
        }
        new VoiceChangeSettingsGenerator().generateParameters(false);
        voiceChangingParametersChanged();
    }

    private void showCurrentCallWillNotBeAffectedDialogIfNeeded() {
        if (isInCall()) {
            AlertDialog dialog = AlertsCreator
                    .createSimpleAlert(getContext(), getString(R.string.ChangesWillBeAppliedToTheNextCall))
                    .create();
            showDialog(dialog);
        }
    }

    private static boolean isInCall() {
        return VoIPService.getSharedInstance() != null;
    }

    private void changeAggressiveness(boolean aggressive) {
        if (!isRandomVoiceChangeEnabled()) {
            return;
        }
        if (VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true) != aggressive) {
            VoiceChangeSettings.aggressiveChangeLevel.set(aggressive);
            new VoiceChangeSettingsGenerator().generateParameters(false);
            listAdapter.notifyItemChanged(aggressiveChangeLevelItem.getPosition());
            listAdapter.notifyItemChanged(moderateChangeLevelItem.getPosition());
            voiceChangingParametersChanged();
        }
    }

    private static int getQualityIndex() {
        if (VoiceChangeSettings.useSpectrumDistortion.get().orElse(false)) {
            return 0;
        } else if (VoiceChangeSettings.formantShiftingHarvest.get().orElse(false)) {
            return 2;
        } else {
            return 1;
        }
    }

    private void setQuality(Integer value) {
        if (!isRandomVoiceChangeEnabled()) {
            return;
        }
        if (value == 0) {
            VoiceChangeSettings.useSpectrumDistortion.set(true);
            VoiceChangeSettings.formantShiftingHarvest.set(false);
            new VoiceChangeSettingsGenerator().generateParameters(false);
        } else if (value == 1) {
            boolean spectrumDistortionWasEnabled = VoiceChangeSettings.useSpectrumDistortion.get().orElse(false);
            VoiceChangeSettings.useSpectrumDistortion.set(false);
            VoiceChangeSettings.formantShiftingHarvest.set(false);
            if (spectrumDistortionWasEnabled) {
                new VoiceChangeSettingsGenerator().generateParameters(false);
            }
        } else if (value == 2) {
            VoiceChangeSettings.useSpectrumDistortion.set(false);
            VoiceChangeSettings.formantShiftingHarvest.set(true);
        }
        voiceChangingParametersChanged();
    }

    private void onRecordClicked(View view) {
        if (audioRecorder == null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            stopPlaying();
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                return;
            }
            startRecording();
        } else {
            stopRecording();
        }
        listAdapter.notifyItemChanged(recordItem.getPosition());
    }

    private void onPlayerButtonClicked(View view, boolean changed) {
        if (voiceChanger != null) {
            voiceChangingFinishedCallback = () -> onPlayerButtonClicked(view, changed);
            stopRecording();
        }
        VoiceChangeExamplePlayer player = changed ? changedPlayer : originalPlayer;
        ByteArrayOutputStream buffer = changed ? changedOutputAudioBuffer : originalOutputAudioBuffer;
        if (!player.isPlaying()) {
            if (changed) {
                originalPlayer.stopPlaying();
            } else {
                changedPlayer.stopPlaying();
            }

            if (buffer != null) {
                byte[] audioBytes = buffer.toByteArray();
                player.startPlaying(audioBytes, () -> onPlayingFinished(changed ? playChangedItem.getPosition() : playOriginalItem.getPosition()));
                ((TextCell)view).setTextAndIcon(getString(R.string.Stop), R.drawable.quantum_ic_stop_white_24, true);
            }
        } else {
            player.stopPlaying();
        }
    }

    private void onPlayingFinished(int row) {
        AndroidUtilities.runOnUIThread(() -> listAdapter.notifyItemChanged(row));
    }

    private void showEnableForIndividualAccountsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final Set<Integer> selectedAccounts = new HashSet<>(getVoiceChangeEnabledAccounts());
        LinearLayout accountsLayout = Utils.createAccountsCheckboxLayout(getContext(), selectedAccounts::contains, (acc, enabled) -> {
            if (enabled) {
                selectedAccounts.add(acc);
            } else {
                selectedAccounts.remove(acc);
            }
        });

        builder.setTitle(LocaleController.getString(R.string.EnableForIndividualAccounts));
        builder.setView(accountsLayout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            for (int accountNum : Utils.getActivatedAccountsSortedByLoginTime()) {
                UserConfig.getInstance(accountNum).voiceChangeEnabledForAccount = selectedAccounts.contains(accountNum);
                UserConfig.getInstance(accountNum).saveConfig(false);
            }
            listAdapter.notifyItemChanged(enableForIndividualAccountsItem.getPosition());
        });
        showDialog(builder.create());
    }

    private List<Integer> getVoiceChangeEnabledAccounts() {
        return Utils.getActivatedAccountsSortedByLoginTime().stream()
                .filter(a -> UserConfig.getInstance(a).voiceChangeEnabledForAccount)
                .collect(Collectors.toList());
    }

    private void runBenchmark() {
        if (originalOutputAudioBuffer == null || originalOutputAudioBuffer.size() == 0) {
            return;
        }
        listView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        final long startTime = System.currentTimeMillis();
        VoiceChanger benchmarkVoiceChanger = new VoiceChanger(new CachedVoiceChangerSettingsParametersProvider(), sampleRate);
        benchmarkVoiceChanger.setFinishedCallback(() -> AndroidUtilities.runOnUIThread(() -> onBenchmarkFinished(startTime)));
        benchmarkVoiceChanger.write(originalOutputAudioBuffer.toByteArray());
        benchmarkVoiceChanger.notifyWritingFinished();
    }

    private void onBenchmarkFinished(long startTime) {
        if (getContext() == null) {
            return;
        }
        long duration = System.currentTimeMillis() - startTime;
        benchmarkRatioText = getBenchmarkRatioText(duration);
        Toast.makeText(getContext(), "Ratio: " + benchmarkRatioText, Toast.LENGTH_LONG).show();
        listAdapter.notifyItemChanged(benchmarkItem.getPosition());
        listView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private String getBenchmarkRatioText(long duration) {
        final int sampleSize = 2;
        double originalLength = ((double)originalOutputAudioBuffer.size() / sampleSize / sampleRate);
        double formantRatio = duration / (originalLength * 1E3);
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(formantRatio * 100) + "%";
    }

    private final Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecorder != null) {
                VoiceChanger voiceChanger = VoiceChangeSettingsFragment.this.voiceChanger;
                ByteBuffer buffer = allocateByteBuffer(recordBufferSize);
                int len = audioRecorder.read(buffer, buffer.capacity());

                if (len > 0) {
                    writeToOutputBuffer(originalOutputAudioBuffer, buffer, len);
                }

                ByteBuffer changedBuffer = null;
                if (voiceChanger != null) {
                    if (len > 0) {
                        voiceChanger.write(VoiceChangerUtils.getBytesFromByteBuffer(buffer, len));
                    }
                    byte[] changedVoice = voiceChanger.readAll();
                    if (changedVoice.length == 0) {
                        recordQueue.postRunnable(recordRunnable);
                        return;
                    }
                    len = changedVoice.length;
                    changedBuffer = allocateByteBuffer(len);
                    changedBuffer.put(changedVoice, 0, len);
                    changedBuffer.rewind();
                }
                if (changedBuffer != null) {
                    writeToOutputBuffer(changedOutputAudioBuffer, changedBuffer, len);
                    recordQueue.postRunnable(recordRunnable);
                }
                if (originalOutputAudioBuffer.size() >= 60 * sampleRate) {
                    stopRecording();
                }
            }
        }

        private ByteBuffer allocateByteBuffer(int length) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(length);
            buffer.order(ByteOrder.nativeOrder());
            buffer.rewind();
            return buffer;
        }

        private void writeToOutputBuffer(ByteArrayOutputStream outputBuffer, ByteBuffer buffer, int len) {
            try {
                outputBuffer.write(VoiceChangerUtils.getBytesFromByteBuffer(buffer, len));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (audioRecorder != null) {
            stopRecording();
        }
        stopPlaying();
    }

    private void startRecording() {
        try {
            changedOutputAudioBuffer = new ByteArrayOutputStream();
            originalOutputAudioBuffer = new ByteArrayOutputStream();
            audioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize);
            audioRecorder.startRecording();
            voiceChanger = new VoiceChanger(new CachedVoiceChangerSettingsParametersProvider(), audioRecorder.getSampleRate());
            voiceChanger.setFinishedCallback(() -> recordQueue.postRunnable(this::stopRecordingInternal));
            recordQueue.postRunnable(recordRunnable);
        } catch (Exception e) {
            PartisanLog.e(e);
            try {
                audioRecorder.release();
                audioRecorder = null;
            } catch (Exception e2) {
                PartisanLog.e(e2);
            }
        }
    }

    private void stopRecording() {
        stopRecording(false);
    }

    private void stopRecording(boolean force) {
        if (force && voiceChanger != null) {
            voiceChanger.forceStop();
        }
        recordQueue.postRunnable(() -> {
            try {
                if (audioRecorder != null) {
                    if (audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecorder.stop();
                    }
                }
                if (voiceChanger != null) {
                    voiceChanger.notifyWritingFinished();
                    if (force) {
                        voiceChanger.forceStop();
                    }
                }
            } catch (Exception e) {
                PartisanLog.e(e);
            }
        });
    }

    private void stopRecordingInternal() {
        try {
            if (audioRecorder != null) {
                audioRecorder.release();
                audioRecorder = null;
                AndroidUtilities.runOnUIThread(() -> {
                    listView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    listAdapter.notifyItemChanged(recordItem.getPosition());
                    notifyPlayButtonsChanged();
                });
            }
            if (voiceChanger != null) {
                voiceChanger = null;
            }
            if (voiceChangingFinishedCallback != null) {
                AndroidUtilities.runOnUIThread(voiceChangingFinishedCallback);
                voiceChangingFinishedCallback = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void notifyPlayButtonsChanged() {
        listAdapter.notifyItemChanged(playChangedItem.getPosition());
        listAdapter.notifyItemChanged(playOriginalItem.getPosition());
        listAdapter.notifyItemChanged(benchmarkItem.getPosition());
    }

    private void voiceChangingParametersChanged() {
        stopPlaying();
        if (voiceChanger != null) {
            voiceChangingFinishedCallback = VoiceChangeSettingsFragment.this::resetBuffers;
            stopRecording(true);
        } else if (changedOutputAudioBuffer != null || originalOutputAudioBuffer != null) {
            AndroidUtilities.runOnUIThread(VoiceChangeSettingsFragment.this::resetBuffers);
        }
    }

    private void stopPlaying() {
        originalPlayer.stopPlaying();
        changedPlayer.stopPlaying();
    }

    private void resetBuffers() {
        changedOutputAudioBuffer = null;
        originalOutputAudioBuffer = null;
        notifyPlayButtonsChanged();
    }
}
