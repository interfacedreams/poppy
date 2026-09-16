# Releasing Poppy

Distribute installable APKs as assets in this repository's GitHub Releases. Keep the asset name `Poppy.apk` consistent across releases. Leave `README.md` unchanged when preparing or publishing a release; keep installation and setup instructions in release notes or this document.

## Private signing setup

The original release signing key is stored locally at `.local/poppy-release.jks`. Its configuration and passwords are in `keystore.properties`. Both are ignored by Git. Back up both files securely outside this checkout before relying on them for future releases. Never upload them as release assets or commit them.

Future updates must use the same signing key. Losing it means existing users cannot install an update over the original app.

For a separate fork, generate your own Android release keystore and create a private `keystore.properties` file with these properties:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_PASSWORD
```

Without this file, release builds are unsigned; debug builds still work normally.

## Build and verify

1. Increase `versionCode` and `versionName` in `app/build.gradle` for each subsequent release.
2. Run:

   ```sh
   ./gradlew assembleDebug lintDebug assembleRelease lintRelease
   ```

3. Verify the signature using the Android SDK's `build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`.
4. Test the signed APK on a device: installation, accessibility setup, microphone permission, key entry, a question, cancellation, and reopening. The first release may require uninstalling a previous debug build; this clears its settings. Do not uninstall a user's build without their consent.
5. Copy `app/build/outputs/apk/release/app-release.apk` to `Poppy.apk` in an ignored build directory and generate a SHA-256 checksum file.

## Publish

Commit and push the release source, create a matching version tag such as `v0.4`, and create a GitHub Release with that tag. Attach only `Poppy.apk` and its checksum file. Use a normal release if it should be served by GitHub's `/releases/latest/` link; GitHub excludes prereleases from that link.

Release notes should describe tested devices, setup instructions, known limitations, and whether the APK itself was tested on hardware. Fresh installations default to the DC-1 orange button (F12) once accessibility is enabled; existing saved button choices are preserved. Verify the default on a fresh installation before marking that path as tested.
