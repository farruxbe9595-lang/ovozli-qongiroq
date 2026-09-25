"""Compile the real preset/settings/provider classes with in-memory setting storage.

This checks preset and regression logic, not Android UI, native audio, or calls.
"""
from pathlib import Path
import argparse
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--drafts', type=Path, required=True)
parser.add_argument('--runtime', type=Path, required=True)
args = parser.parse_args()
runtime = args.runtime.resolve()
runtime.mkdir(parents=True, exist_ok=True)
sources = runtime / 'src'
classes = runtime / 'classes'
classes.mkdir(exist_ok=True)

def write(name, text):
    path = sources / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')

pkg = 'org/telegram/messenger/partisan/voicechange/'
production = Path('TMessagesProj/src/main/java') / pkg
for name in ['VoiceChangeSettings.java', 'VoiceChangeSettingsGenerator.java']:
    write(pkg + name, (args.drafts / 'modified' / production / name).read_text(encoding='utf-8'))
legacy = (args.drafts / 'original' / production / 'VoiceChangeSettingsGenerator.java').read_text(encoding='utf-8')
write(pkg + 'LegacyVoiceChangeSettingsGenerator.java', legacy.replace('VoiceChangeSettingsGenerator', 'LegacyVoiceChangeSettingsGenerator'))
for source in (Path(__file__).parent / 'upstream').glob('*.java'):
    write(pkg + source.name, source.read_text(encoding='utf-8'))
write('androidx/annotation/NonNull.java', 'package androidx.annotation; public @interface NonNull {}')
write('com/google/common/base/Strings.java', 'package com.google.common.base; public class Strings { public static boolean isNullOrEmpty(String s) { return s == null || s.isEmpty(); } }')
settings = 'org/telegram/messenger/partisan/settings/'
write(settings + 'Setting.java', '''package org.telegram.messenger.partisan.settings;
import java.util.Optional;
public class Setting<T> {
    private T value;
    private final T defaultValue;
    public Setting(String name, T value) { this.value = value; this.defaultValue = value; }
    public Optional<T> get() { return Optional.ofNullable(value); }
    public T getOrDefault() { return value; }
    public void set(T value) { this.value = value; }
    public void load() {}
    public void resetForTest() { value = defaultValue; }
}''')
for name, type_name in [('Boolean', 'Boolean'), ('Float', 'Float'), ('Int', 'Integer'), ('Long', 'Long'), ('String', 'String'), ('StringSet', 'java.util.Set<String>')]:
    write(settings + name + 'Setting.java', 'package org.telegram.messenger.partisan.settings; public class ' + name + 'Setting extends Setting<' + type_name + '> { public ' + name + 'Setting(String name, ' + type_name + ' value) { super(name, value); } }')
write(settings + 'SettingUtils.java', '''package org.telegram.messenger.partisan.settings;
import java.util.*;
import java.lang.reflect.*;
public class SettingUtils {
    public static List<Setting<?>> getAllSettings(Class<?> cls) {
        List<Setting<?>> result = new ArrayList<>();
        try {
            for (Field field : cls.getFields()) {
                if (Setting.class.isAssignableFrom(field.getType())) result.add((Setting<?>) field.get(null));
            }
        } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
        return result;
    }
}''')
write(pkg + 'PresetRegressionTest.java', (Path(__file__).parent / 'PresetRegressionTest.java').read_text(encoding='utf-8'))
java_sources = sorted(str(p) for p in sources.rglob('*.java'))
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes), *java_sources], check=True)
subprocess.run(['java', '-ea', '-cp', str(classes), 'org.telegram.messenger.partisan.voicechange.PresetRegressionTest'], check=True)
