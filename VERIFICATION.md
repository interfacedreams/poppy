# Verification — 2026-09-15

## Public release 0.5 — 2026-09-16

- Included the fresh-install F12 orange-button default and bumped version to 0.5 (code 5).
- assembleDebug, lintDebug, assembleRelease, and lintRelease passed. APK version and signature verified; signing certificate matches the 0.4 distribution APK.
- Fresh-install physical-button testing remains unverified. No tablet data was cleared or installation changed.

## Fresh-install orange button — 2026-09-16

- Default the accessibility service to KEYCODE_F12, the orange-button code previously verified on the DC-1, when no saved button exists. Existing saved mappings remain unchanged.
- Updated installation and release documentation. assembleDebug and lintDebug pass. A fresh installation and physical button press still need device verification; existing user data was not cleared.

## Saved key indicator — 2026-09-16

- An unfocused, empty key field shows masked dots and Saved when a stored key exists. Focusing shows Enter a replacement key with an empty input. The saved credential is never loaded into the field. Leaving it empty, including pressing Save key, preserves the existing key; Remove key explicitly deletes it.
- Build and lint pass; installed successfully on DC-1. No credentials were read or changed during verification. Focus behavior has not been manually exercised on-device.

## Rounded vertical selectors — 2026-09-16

- Restored Model and Thinking to separate full-width rows. Kept native Spinner behavior, replacing the default underline/arrow background with rounded fields and one chevron in the selected-value layout. Rounded popup menus are positioned over the original field and use checked option rows.
- assembleDebug and lintDebug pass. Installed the updated APK on DC-1. The requested tablet screenshot failed; Settings retains FLAG_SECURE protection. Appearance and popup coverage still require visual confirmation on the tablet.

## Compact settings — 2026-09-16

- Placed Model and Thinking in equal-width columns on one row, using native dropdowns. Removed the automatic-save explanation and the Choose orange button control; existing button configuration is preserved.
- assembleDebug and lintDebug pass. Installed successfully on the connected DC-1. Layout has not been visually inspected on the device.

## Model and thinking dropdowns — 2026-09-16

- Added native Android dropdowns at the top of Settings for Luna, Terra, Sol, and Astra, plus supported thinking levels. Selections save immediately and are read when submitting the next question; defaults are Luna / Low. Astra excludes None and normalizes it to Low.
- Requests now send the selected API model ID and `reasoning.effort`. Increased the output budget to 25,000 tokens for thinking (1,000 for None) and the read timeout to five minutes. Answers remain instructed to be concise. Model access failures direct users to Settings.
- Verified model IDs and supported reasoning efforts against the official OpenAI model pages on 2026-09-16. Build and lint pass. Standalone ModelOptionsTest passes for every model/effort combination, unsupported and stale preferences, defaults, and reasoning budget selection.
- No tablet was attached to ADB. The updated APK is built, but installation, dropdown interaction/persistence on the device, and live API requests remain unverified. No key was read and no paid request was made.
- Follow-up: installed the updated APK successfully on the USB-connected DC-1 using an in-place update. Verified Poppy is enabled and bound in Android accessibility, with no crashed services. Dropdown interaction and live API requests still require device testing.

Earlier sections below are historical records of the retired server-based version. Current setup uses Settings and a direct OpenAI connection; no Mac, USB forwarding, or local server is required.

## Confirmed on the physical DC-1

- Android 13 / API 33, ARM64; screenshot size 1184 × 1584.
- User enabled accessibility and selected the orange button: KEYCODE_F12, scan code 88.
- User confirmed screenshot and floating panel work in v0.1; app logs confirm successful capture, overlay display, and dismissal.
- v0.2 APK built and installed successfully. Accessibility remains enabled and the F12 binding survived the update. Service reconnects without a startup crash in inspected logs.
- USB reverse port 3817 configured. Backend connection credentials copied into the app's private files directory.

## v0.2 automated checks

- Android assembleDebug and lintDebug pass with zero errors. Remaining warnings concern English-only UI strings.
- TypeScript compilation passes.
- Six backend HTTP tests pass: chunk delivery before completion, authentication, missing key, input validation, early stream termination, and upstream cancellation on client disconnect.
- Local backend started and health endpoint responds: ok=true, ready=false (API key not yet supplied).

## Historical v0.2 verification gaps

- At v0.2, a real OpenAI request had not been tested. For current testing, add a key in Poppy Settings.
- Unlock tablet, press the orange button, grant microphone permission, and speak a question.
- Verify Android transcription, live streamed answer, close/cancel, keyboard fallback, rotation, and reconnect behavior.
- Attempted v0.2 ADB visual inspection returned a black screen; new question-dialog layout has not yet been visually verified on the tablet.

## Development paths

- SDK: /Users/tommyjoseph/Library/Android/sdk
- Java 17: /Users/tommyjoseph/Library/Java/JavaVirtualMachines/daylight-jdk17.jdk/Contents/Home
- Gradle 8.11.1; Android Gradle Plugin 8.9.2; SDK/build tools 35.
- The retired server used Mac loopback 127.0.0.1:3817 with USB forwarding.

## SQLite quota update

- Added a persistent default allowance of 200 total attempts in backend/.local/quota.sqlite.
- TypeScript build and all 11 tests pass, including persistence after reopening, concurrent reservations from four separate processes, quota exhaustion, database failure, and charging failed upstream attempts.
- Restarted the local backend with quota enforcement enabled. No Android update is required; existing error handling displays exhaustion messages.

## Poppy v0.3 recording interface

- Rebranded app label and setup UI to Poppy; Android package remains the same to preserve device configuration.
- Recording panel now shows microphone-level bars and one large Ask Poppy button. Transcript is hidden. Recognition segments are accumulated without automatically submitting; only an explicit send tap enables submission. Small × and existing hardware toggle dismiss.
- Android build and lint pass. Installed on DC-1; screenshot confirms recording panel fits and microphone bars respond. Closed the panel after inspection without submitting.
- Live manual-send behavior and pauses between recognition segments still need user testing. Android's recognition service may briefly stop/restart between segments.
- Backend restarted from renamed ~/dreams/projects/Poppy/backend so its .env resolves correctly.

## Recording meter restored

Restored the original WaveBars noise-floor tracking, thresholds, square-root level mapping, and smoothing at the user’s request. Removed both experimental SpeechMeter implementations and their tests. Bar size and spacing remain as previously adjusted.

## Direct API connection and Settings

- Added Settings immediately before Ask Poppy in the recording panel and on the setup screen.
- Added encrypted, non-backed-up user API-key storage with save, replacement, and removal.
- Android now sends HTTPS Responses requests directly to OpenAI and handles streamed deltas, completion, incomplete responses, failures, and cancellation.
- Build and Android lint pass. Live API calls with a user-supplied key remain unverified.
- Tablet screenshot inspection was blocked by automatic approval review; placement was checked from layout code only.

## Three-stage Poppy loading animation

- Added a centered flower that unfolds through bud, half-open, and full-bloom poses using the existing Poppy artwork. The cycle repeats while waiting, with a static full flower when system animations are disabled.
- Appears immediately during final transcription after Ask Poppy, then in the answer panel until the first nonempty response arrives. Completion and errors reveal the answer area; closing/detaching stops the animation.
- Android assembleDebug and lintDebug pass. Updated APK installed successfully on the connected tablet. The complete speech-to-answer animation still needs a live spoken question for visual verification.

## Retired server removal

- Removed the server source, tests, dependencies, generated output, local environment credentials, and quota database.
- Stopped the local server process on port 3817.
- Renamed the Android direct API client to OpenAIClient and removed the localhost cleartext network exception.
- Updated accessibility text, README, and agent setup instructions for client-side API keys.
- Android assembleDebug and lintDebug pass. Live requests with a user-supplied key remain unverified.

## Settings as a question-panel state

- The recording panel now swaps to shared SettingsPanel content in the same QuestionActivity. Back returns to recording; the existing screenshot and completed transcript segments remain available. Recording pauses while settings is open.
- Settings uses the panel’s cream background, rounded corners, width, top alignment, back/close controls, and paired Save / Remove pills. Removed the initial saved/unsaved status line; feedback appears only after actions. Key input remains masked, excluded from saved state/autofill, and protected from screenshots.
- assembleDebug and lintDebug pass; APK installed on the tablet. Verified settings stays in the same QuestionActivity with SECURE enabled and the back arrow returns to the recording screen. Closed the test question without sending or changing credentials.
- UI Automator inspection temporarily interrupts this accessibility service and closes its panel; window inspection and screenshots of the recording state were used to finish navigation verification.

## Transcription recovery

- Previously, the app requested but ignored partial speech results, recreated the recognizer after every terminal result, and failed an 8-second send timeout even when words were available. Existing diagnostics did not record the specific callback/error causing the reported failure.
- Keep partial revisions separately from committed segments; final words replace provisional words. Empty final results, errors, and send timeouts can recover the latest partial segment without duplication. The former missing-part prompt has since been removed; see below.
- Reuse the speech connection after results/silence. Defer stop until ready and avoid another stop after end-of-speech. Retry transient provider errors up to twice. Added content-free error codes and recovery diagnostics.
- Standalone RecordingTranscriptTest assertions pass for partial revisions, final replacement, timeout/error recovery, silence, missing words, repeated recovery, restoration, and the length cap. Run by compiling RecordingTranscript.java and RecordingTranscriptTest.java with Java 17, then executing dev.tommy.daylightpilot.RecordingTranscriptTest with -ea.
- Android assembleDebug and lintDebug pass. Updated APK installed on the tablet; real spoken-question/provider recovery still requires user verification.

## Missing-part state removed

- Removed the speech-without-words state and its repeat-part prompts. Empty recognition results now resume recording; recoverable recognition errors use the existing automatic retries.
- Ask Poppy sends the captured final or partial words even if the latest segment has no text. The existing no-words prompt remains when the entire question is empty.
- Updated transcript regression checks pass. Android assembleDebug and lintDebug pass. Updated APK installed successfully on the connected tablet; live speech behavior remains to be checked.

## Vector flower lifecycle loading state

- Replaced the bitmap deformation with native vector paths: grass, a growing curved stem, unfurling leaves, six independently opening petals, sepals, and a detailed flower center.
- Three states across a 6.6-second loop: emergence, full bloom, then withering. The last state bends the stem, folds petals downward, changes their color, and releases two petals before the plant fades back into the grass. Disabled system animations show a static full bloom.
- assembleDebug, assembleDebugAndroidTest, and lintDebug pass. LoadingPreview rendered all three poses at the recording and answer-panel sizes on the tablet; inspected the resulting contact sheet and refined the withering silhouette. Final app installed; temporary preview package removed.
- Reproduce the contact sheet by installing the debug Android test APK and running dev.tommy.daylightpilot.test/dev.tommy.daylightpilot.LoadingPreview. Output is the target app cache/loading-states.png; no question, screenshot, or credentials are used by this preview.

## Simplified recovery flows

- Removed all bar-tapping instructions and interactions. Empty sends resume listening; recording startup retries automatically, with one Retry recording action if recovery fails.
- Removed the 4,000-character transcript truncation and recording-full state. Updated transcript regression checks pass, including preservation of a 5,000-character question.
- Screenshot failures now open voice-only recording with an unavailable screenshot option. Session validity and New question no longer depend on screenshot bytes. The saved screenshot preference remains unchanged.
- Invalid sessions offer Restart Poppy. Answer failures keep a consistent heading and use friendly messages instead of raw exception details. Empty answers direct users to New question.
- Android assembleDebug and lintDebug pass. Live microphone and protected-screen fallback behavior still need device verification.

## Bloom once, then sway

- Replaced the repeating growth/withering cycle with a two-second emergence and bloom, followed by a gentle 4.8-second sway. The flower keeps its full petals and color throughout the wait, with a Thinking… label.
- Carry animation progress from final transcription into the answer panel so the bloom does not restart at that transition. A new send starts a fresh bloom; hiding/detaching stops animation work. Disabled system animations show a static full bloom.
- assembleDebug, assembleDebugAndroidTest, and lintDebug pass. Installed the updated app on the connected tablet. LoadingPreview rendered growth, full bloom, both sway directions, and a 20-second wait at both panel sizes; visually inspected the contact sheet. Removed the temporary preview test package.
- A live spoken question and the animated transition between panels were not exercised in this check.

## Accessibility reconnection after loading preview

- User reported the orange button stopped working after installation/preview. Android listed Poppy as enabled but crashed, with no bound service or running app process; the available diagnostic buffer contained no crash stack trace.
- Toggled only Poppy's existing accessibility-service setting off and back on. Verified Poppy is bound, the crashed-services list is empty, and the service reports connected with its saved hardware key.
- After future instrumentation previews, verify accessibility has rebound before declaring the tablet ready; instrumentation can interrupt the target app's service. The exact cause of this disconnection was not established.

## Shared icon artwork for loading

- Loading now draws the same ic_poppy drawable used by both panel headers, replacing the separate six-petal vector flower and center. The existing stem, leaves, grass, Thinking… label, two-second growth, and sustained gentle sway remain. Opening scales the original artwork uniformly to preserve its proportions.
- assembleDebug, assembleDebugAndroidTest, and lintDebug pass. Updated APK installed on the tablet; inspected the device-rendered contact sheet at both panel sizes. Removed the preview test package and reconnected the accessibility service after instrumentation interrupted it.

## v0.4 — home/setup screen removed (2026-09-16)

- Removed the full-screen setup UI. MainActivity is now an invisible launcher that opens recording when the accessibility service is connected, or Android accessibility settings when it is not.
- Gave QuestionActivity an empty task affinity and excluded its temporary task from Recents, so launching it from the hardware button does not bring Poppy’s former background task forward.
- Kept API-key settings inside recording; moved hardware-button selection there and removed the unused standalone SettingsActivity. Existing saved keys and button preferences are preserved.
- assembleDebug and lintDebug pass. Checked the merged manifest for the invisible launcher, isolated question task, and removed activity. Version is 0.4 (code 4).
- Tablet is disconnected: APK built but not installed. Verify opening from another app, microphone permission, send/answer transition, New question, dismissal, app-icon launch, and button selection on the tablet when available.

## Simple recording status (2026-09-16)

- Replaced the microphone bars with a centered text status: Starting microphone…, Listening…, Finishing transcription…, or Microphone paused. Permission requests show a microphone-access prompt.
- Removed adaptive noise-floor tracking, thresholds, smoothing, decorative bar animation, and microphone-level diagnostics. Audio levels no longer drive the interface; recognition and submission behavior are unchanged.
- assembleDebug and lintDebug pass. Installed the updated APK on the connected tablet. Live spoken-question behavior remains to be checked by the user.

## Decorative listening bars restored (2026-09-16)

- Replaced the text-only recording status with eleven rounded bars in the original recording area. A simple gentle wave runs while the speech provider is ready, independently of sound volume, and rests on pause/end/error. Disabled system animations show stationary bars.
- No adaptive thresholds, audio-level diagnostics, sample history, or smoothing. Hidden/detached bars do not schedule ongoing redraws.
- assembleDebug and lintDebug pass. Updated APK installed on the tablet; live appearance and speech remain to be checked.

## Original voice-responsive bars restored (2026-09-16)

- Reverted both recording-indicator experiments at the user's request. Restored the original WaveBars implementation, noise-floor tracking, thresholds, smoothing, dimensions, RMS callbacks, and level diagnostics from before this conversation's edits.
- assembleDebug and lintDebug pass. Installed the restored APK on the connected tablet.

## Loading caption removed (2026-09-16)

- Removed the visible Thinking… caption beneath the shared flower loading animation in both recording and answer panels.
- assembleDebug and lintDebug pass. Installed the updated APK on the tablet.
