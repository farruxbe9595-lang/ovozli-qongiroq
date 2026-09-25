# Ovoz Telegram APK build

The `Build Ovoz Telegram test APK` workflow builds a real Telegram client with the female preset patch. It runs on the `codex/ovoz-female-voice` branch. The original `VoiceCallPrototype` remains a separate microphone demo.

Source is pinned to Partisan Telegram `513865a0b8a1e521eb58403c1b787c5845c28ffc` (`ptg-4.4.5`). This patch changes Java and resources only. The build reuses the unmodified ARM native libraries extracted from the official release APK, verified against GitHub's published SHA-256 `1938d03b7ef1d5f420639703c9cfdfcb2011c1821baaae2534435c28cb68581b`. It does not rebuild native code.

The output is a debug-signed testing client named `Ovoz Telegram`, package `org.telegram.messenger.beta`, with 32-bit and 64-bit ARM support. Its application ID differs from ordinary Telegram and the Partisan standalone client. It may still conflict with an existing Telegram Beta installation. No installed app is removed by this workflow.

The GitHub Actions artifact contains the APK, checksum, package information, applied patch and GPL license. No app-store release is published. Upstream sample service configuration remains in use for this test build; a production distribution needs its own app credentials and signing setup as described by upstream.

Successful compilation is not evidence of natural voice quality or successful Telegram calls. Test the recorded preview first, then individual and group calls, checking audio in both directions and after mute/unmute. Existing calls retain their settings until a new call starts.

The earlier README describes the initial patch-only checkpoint. Current build results should be read from the Actions run and delivered build-info file.
