package org.telegram.messenger.partisan.voicechange;

import java.util.*;
import org.telegram.messenger.partisan.settings.*;

public final class PresetRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual);
    }
    private static void reset() {
        for (Setting<?> setting : SettingUtils.getAllSettings(VoiceChangeSettings.class)) setting.resetForTest();
    }
    private static void quality(int index) {
        VoiceChangeSettings.useSpectrumDistortion.set(index == 0);
        VoiceChangeSettings.formantShiftingHarvest.set(index == 2);
    }
    private static List<Object> audioSettings() {
        return Arrays.asList(
                VoiceChangeSettings.f0Shift.getOrDefault(),
                VoiceChangeSettings.lowRatio.getOrDefault(),
                VoiceChangeSettings.midRatio.getOrDefault(),
                VoiceChangeSettings.highRatio.getOrDefault(),
                VoiceChangeSettings.maxFormantSpread.getOrDefault(),
                VoiceChangeSettings.badSCutoff.getOrDefault(),
                VoiceChangeSettings.badShCutoff.getOrDefault(),
                VoiceChangeSettings.spectrumDistortionParams.getOrDefault(),
                VoiceChangeSettings.useSpectrumDistortion.getOrDefault(),
                VoiceChangeSettings.formantShiftingHarvest.getOrDefault());
    }
    private static void setup(long seed, int quality, boolean aggressive) {
        reset();
        VoiceChangeSettings.settingsSeed.set(seed);
        VoiceChangeSettings.aggressiveChangeLevel.set(aggressive);
        quality(quality);
    }
    private static void legacyModesRemainUnchanged() {
        for (long seed = 1; seed <= 200; seed++) {
            for (int quality = 0; quality <= 2; quality++) {
                for (boolean aggressive : new boolean[]{false, true}) {
                    setup(seed, quality, aggressive);
                    new LegacyVoiceChangeSettingsGenerator().generateParameters(false);
                    List<Object> expected = audioSettings();
                    setup(seed, quality, aggressive);
                    new VoiceChangeSettingsGenerator().generateParameters(false);
                    check(audioSettings().equals(expected), "Legacy output changed for seed=" + seed + " quality=" + quality + " aggressive=" + aggressive);
                }
            }
        }
    }
    private static void stableFemalePreset() {
        for (long seed = 1; seed <= 100; seed++) {
            for (int quality = 0; quality <= 2; quality++) {
                for (boolean aggressive : new boolean[]{false, true}) {
                    setup(seed, quality, aggressive);
                    new LegacyVoiceChangeSettingsGenerator().generateParameters(false);
                    VoiceChangeSettings.femaleVoiceEnabled.set(true);
                    new VoiceChangeSettingsGenerator().generateParameters(true);
                    close(VoiceChangeSettings.f0Shift.getOrDefault(), 1.6, "Female pitch");
                    close(VoiceChangeSettings.lowRatio.getOrDefault(), 1.2, "Low formants");
                    close(VoiceChangeSettings.midRatio.getOrDefault(), 1.2, "Mid formants");
                    close(VoiceChangeSettings.highRatio.getOrDefault(), 1.2, "High formants");
                    CachedVoiceChangerSettingsParametersProvider provider = new CachedVoiceChangerSettingsParametersProvider();
                    check(provider.formantShiftingEnabled(), "Preset must reach formant processor");
                    check(!provider.spectrumDistortionEnabled() && !provider.badSEnabled() && !provider.badShEnabled(), "Preset retained distortion");
                    close(provider.getMaxFormantSpread(), 0, "Preset retained random spread");
                    check(!provider.shiftFormantsWithHarvest(), "Expected medium-quality WORLD");
                    check(!VoiceChangeSettings.useSpectrumDistortion.getOrDefault(), "Low quality still enabled");
                    check(VoiceChangeSettings.settingsSeed.getOrDefault() == seed, "Female mode changed legacy seed");
                    check(!VoiceChangeSettings.areSettingsEmpty(), "Preset incorrectly treated as empty");
                }
            }
        }
    }
    private static void switchingRestoresPriorMode() {
        for (int quality = 0; quality <= 2; quality++) {
            for (boolean aggressive : new boolean[]{false, true}) {
                setup(7394L, quality, aggressive);
                new VoiceChangeSettingsGenerator().generateParameters(false);
                List<Object> original = audioSettings();
                check(VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(true), "Enable must change mode");
                check(!VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(true), "Re-enable must be idempotent");
                check(VoiceChangeSettings.qualityBeforeFemaleVoice.getOrDefault() == quality, "Saved quality lost");
                VoiceChangeSettings.femaleVoicePitch.set(1.8f);
                VoiceChangeSettings.femaleVoiceFormantRatio.set(1.35f);
                new VoiceChangeSettingsGenerator().generateParameters(false);
                close(VoiceChangeSettings.f0Shift.getOrDefault(), 1.8, "Pitch control ignored");
                close(VoiceChangeSettings.lowRatio.getOrDefault(), 1.35, "Formant control ignored");
                check(VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(false), "Disable must change mode");
                check(audioSettings().equals(original), "Original mode not restored for quality=" + quality);
                check(!VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(false), "Re-disable must be idempotent");
                check(VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(true), "Second enable failed");
                close(VoiceChangeSettings.f0Shift.getOrDefault(), 1.8, "Pitch preference not retained");
                close(VoiceChangeSettings.lowRatio.getOrDefault(), 1.35, "Tone preference not retained");
            }
        }
    }
    private static void invalidValuesAndCachedCalls() {
        reset();
        VoiceChangeSettingsGenerator.setFemaleVoiceEnabled(true);
        CachedVoiceChangerSettingsParametersProvider inProgressCall = new CachedVoiceChangerSettingsParametersProvider();
        for (float value : new float[]{-1f, 0f, 50f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            VoiceChangeSettings.femaleVoicePitch.set(value);
            VoiceChangeSettings.femaleVoiceFormantRatio.set(value);
            new VoiceChangeSettingsGenerator().generateParameters(false);
            float pitch = VoiceChangeSettings.f0Shift.getOrDefault();
            float formants = VoiceChangeSettings.lowRatio.getOrDefault();
            check(Float.isFinite(pitch) && pitch >= 1.25f && pitch <= 2f, "Unsafe pitch: " + value);
            check(Float.isFinite(formants) && formants >= 1.05f && formants <= 1.4f, "Unsafe formant: " + value);
        }
        close(inProgressCall.getF0Shift(), 1.6, "Active call snapshot changed");
        close(inProgressCall.getLowRatio(), 1.2, "Active call tone changed");
    }
    public static void main(String[] args) {
        legacyModesRemainUnchanged();
        stableFemalePreset();
        switchingRestoresPriorMode();
        invalidValuesAndCachedCalls();
        System.out.println("PASS: " + checks + " preset, seeded regression, restoration, bounds and cached-call checks.");
        System.out.println("Android settings persistence, UI, native audio, APK build and real calls are NOT covered.");
    }
}
