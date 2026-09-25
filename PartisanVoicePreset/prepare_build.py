"""Apply the preset to a pinned checkout and reuse its unmodified release natives."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import re
import subprocess
import zipfile

UPSTREAM = '513865a0b8a1e521eb58403c1b787c5845c28ffc'
APK_SHA256 = '1938d03b7ef1d5f420639703c9cfdfcb2011c1821baaae2534435c28cb68581b'

def remove_blocks(text, name):
    pattern = re.compile(r'\b' + re.escape(name) + r'\s*\{')
    while match := pattern.search(text):
        end = match.end()
        depth = 1
        while depth and end < len(text):
            if text[end] == '{':
                depth += 1
            elif text[end] == '}':
                depth -= 1
            end += 1
        if depth:
            raise ValueError('Unbalanced Gradle block: ' + name)
        text = text[:match.start()] + text[end:]
    return text

def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Pinned build file changed: ' + old)
    return text.replace(old, new, 1)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    package = Path(__file__).resolve().parent
    revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != UPSTREAM:
        raise ValueError('Unexpected upstream revision: ' + revision)
    actual = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if actual != APK_SHA256:
        raise ValueError('Upstream release APK checksum mismatch')
    subprocess.run(['git', '-C', str(source), 'apply', '--check', str(package / 'female-voice.patch')], check=True)
    subprocess.run(['git', '-C', str(source), 'apply', str(package / 'female-voice.patch')], check=True)

    natives = source / 'TMessagesProj/src/ovozJniLibs'
    found = set()
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            parts = PurePosixPath(name).parts
            # Third-party AARs supply their own natives (for example ML Kit).
            # Only Telegram's CMake target is replaced by the verified release binary.
            if len(parts) != 3 or parts[0] != 'lib' or parts[1] not in ('arm64-v8a', 'armeabi-v7a') or parts[2] != 'libtmessages.49.so':
                continue
            destination = natives / parts[1] / parts[2]
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(apk.read(name))
            found.add((parts[1], parts[2]))
    for abi in ('arm64-v8a', 'armeabi-v7a'):
        if (abi, 'libtmessages.49.so') not in found:
            raise ValueError('Required release native library missing for ' + abi)

    library_file = source / 'TMessagesProj/build.gradle'
    library = remove_blocks(library_file.read_text(), 'externalNativeBuild')
    library = replace_once(library, "sourceSets.main.jniLibs.srcDirs = ['./jni/']", "sourceSets.main.jniLibs.srcDirs = ['src/ovozJniLibs']")
    library_file.write_text(library)
    app_file = source / 'TMessagesProj_App/build.gradle'
    app = remove_blocks(app_file.read_text(), 'externalNativeBuild')
    app = replace_once(app, "sourceSets.main.jniLibs.srcDirs = ['../TMessagesProj/jni/']", 'sourceSets.main.jniLibs.srcDirs = []')
    app = app.replace('abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"', 'abiFilters "armeabi-v7a", "arm64-v8a"')
    debug_key = re.compile(r'debug\s*\{\s*storeFile file\("\.\./TMessagesProj/config/debug\.keystore"\).*?\n\s*\}', re.S)
    app, count = debug_key.subn('', app, count=1)
    if count != 1:
        raise ValueError('Expected upstream debug signing configuration')
    app_file.write_text(app)
    (source / 'settings.gradle').write_text("include ':TMessagesProj', ':TMessagesProj_App', ':jlatexmath'\nproject(':jlatexmath').projectDir = file('TMessagesProj/lib/jlatexmath/jlatexmath')\n")
    for name in ('AndroidManifest.xml', 'AndroidManifest_SDK23.xml'):
        manifest = source / 'TMessagesProj/config/debug' / name
        manifest.write_text(replace_once(manifest.read_text(), 'android:label="@string/AppNameBeta"', 'android:label="Ovoz Telegram"'))
    print('Pinned source and release verified. Applied female preset; reused unmodified ARM release libraries.')
    print('Debug application ID remains org.telegram.messenger.beta; standard Android debug signing is used.')

if __name__ == '__main__':
    main()
