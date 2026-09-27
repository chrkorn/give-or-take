# Android core app quality audit

Date: 2026-09-28

Source: [Android core app quality guidelines](https://developer.android.com/docs/quality-guidelines/core-app-quality),
reviewed 2026-09-27 against the version updated 2026-09-21.

Status meanings:

- **Satisfies** — repository evidence or an executed test supports the claim.
- **Does not satisfy** — the implementation or required evidence is missing.
- **Not demonstrated** — it may work, but this review did not perform the official test strongly
  enough to claim compliance.
- **N/A** — the app does not provide the feature to which the item applies.

## User experience

| ID | Status | Evidence and finding |
| --- | --- | --- |
| Consistent_UX | **Not demonstrated** | All app flows were exercised on one phone AVD, but not with calls/notifications, transient system changes, foldables, tablets or physical devices. “All form factors” is not established. |
| App_Switcher | **Not demonstrated** | Activity recreation has tests, but every screen was not backgrounded and restored through Recents. |
| Sleep_Resume | **Not demonstrated** | No full sleep/wake matrix was run. |
| Lock_Resume | **Not demonstrated** | No configured-lock-screen test was run. |
| Display_State_Parity | **Not demonstrated** | Portrait phone was checked; landscape and fold states were not. |
| Fullscreen_Display | **Not demonstrated** | Edge-to-edge fills the tested portrait phone, but rotation, folding and resizable-window letterboxing were not checked. |
| Orientation_Transitions | **Not demonstrated** | Several Activities have recreation tests, but the official rapid rotate/fold test on every screen was not run. |
| Graphic_Quality | **Satisfies on tested phone only** | Material/vector UI and custom canvas views were sharp on the API 35 AVD. Other densities and form factors remain unverified. |
| Line_Length | **Does not satisfy** | Phone text wraps without clipping, but there is no wide-screen content-width cap guaranteeing 45–75 characters on tablets or desktop windows. |
| Theme_Support | **Satisfies** | Every Activity and both quiz modes were checked in explicit light and dark palettes. Source pages open in the user's external browser and are not app-owned web content. |
| Back_Button_Nav | **Satisfies** | Activities use normal framework back navigation; toolbars finish the secondary Activity and no custom “press back” prompt exists. Espresso covers the main back flows. |
| Back_Gesture_Nav | **Not demonstrated** | The app does not intercept back gestures, but an end-to-end gesture-navigation pass on every screen was not run. |
| State_Preservation | **Does not satisfy** | Instance-state coverage is substantial, but ADR 0024 accepts loss of queued history writes on process death and a non-atomic SQLite/SharedPreferences reset. Full process-death restoration is therefore not guaranteed. |
| Notification_Quality | **N/A** | The app emits no notifications. |
| Conversation_Quality | **N/A** | This is not a messaging or social app. |
| Touch_Target_Size | **Satisfies** | Shared styles enforce 56 dp buttons and 48 dp text actions; radio/checkbox rows, toolbar navigation and the custom dial meet or exceed 48 dp. |
| Visual_Contrast | **Satisfies** | Theme text pairs meet 4.5:1 and graphic/action pairs meet 3:1 in the checked combinations. Semantic-band text is 8.07–9.06:1 light and 8.60–8.88:1 dark using the supplied measurements. Shape plus text prevents colour-only meaning. |
| Content_Description | **Satisfies structurally; scanner not run** | Custom chart/dial controls, source/share actions and toolbar navigation have descriptions. Decorative result symbols are intentionally hidden because adjacent text carries the meaning. A TalkBack and Accessibility Scanner pass is still needed. |

## Functionality

| ID | Status | Evidence and finding |
| --- | --- | --- |
| Audio_Playback_Start | **N/A** | No audio playback. |
| Audio_Focus_Request | **N/A** | No audio playback. |
| Audio_Focus_Change | **N/A** | No audio playback. |
| Audio_Playback_Background | **N/A** | No audio playback. |
| Audio_Notification_Style | **N/A** | No audio playback. |
| Audio_Playback_Resume | **N/A** | No audio playback. |
| Video_PiP | **N/A** | No video playback. |
| Video_Encoding | **N/A** | No video encoding. |
| Video_Playback_Background | **N/A** | No video playback. |
| System_Sharesheet | **Satisfies** | Result sharing uses an `ACTION_SEND` chooser rather than a custom target UI. |
| Background_Service_Optimization | **Satisfies** | The manifest and source define no background or foreground service. Database work uses a bounded application executor. |

## Performance and stability

| ID | Status | Evidence and finding |
| --- | --- | --- |
| App_Startup_Time | **Not demonstrated** | Startup was visually immediate, but no cold/warm startup benchmark proves the two-second requirement. |
| Rendering_Performance | **Not demonstrated** | No frame-timing, jank or Macrobenchmark run exists. |
| StrictMode_Compliance | **Not demonstrated** | StrictMode with `detectAll()` and `penaltyFlashScreen()` was not enabled and exercised. |
| Stability_ANR | **Not demonstrated** | 195 JVM tests and the 35-test instrumented suite pass, but there is no Play pre-launch report, ANR stress run or Android Vitals evidence. |
| Android_Platform_Compatibility | **Does not satisfy as evidence** | The app compiles/targets API 37, but the device suite is deliberately capped at API 35 because the pinned Espresso 3.5.1 runner does not initialise on API 37. Latest-public-platform runtime compatibility is unproven. |
| Target_SDK_Version | **Satisfies** | `targetSdk` is 37. |
| Compile_SDK_Version | **Satisfies** | `compileSdk` is 37. |
| SDK_Maintenance | **Does not satisfy** | Lint reports newer Gradle, AGP, AndroidX Test, Espresso, AppCompat, Material, Activity and ConstraintLayout releases. Updating needs a deliberate compatibility change, not silence. |
| Non_SDK_Interfaces | **Satisfies** | No non-SDK interface use was found and lint reports none. |
| Production_Build_Quality | **Satisfies the checklist item** | Debug/test libraries are confined to test configurations and `bundleRelease` succeeds. Separate concern: release optimisation is explicitly disabled, which is poor release hardening even though it is not the wording of this item. |
| Power_Management | **N/A** | No background work, alarms, location, media or network service requires Doze handling. |

## Privacy and security

| ID | Status | Evidence and finding |
| --- | --- | --- |
| Minimize_Permissions | **Satisfies** | The manifest requests no permissions. |
| Sensitive_Permissions | **N/A** | No sensitive or paid permission is requested. |
| Runtime_Permissions | **N/A** | No runtime permission is requested. |
| Permission_Rationale | **N/A** | No permission prompt exists. |
| Graceful_Degradation | **N/A** | There is no permission denial path. |
| Sensitive_Data_Storage / Sensitive_Data_Handling | **Satisfies** | Quiz history and settings remain in app-private SQLite and SharedPreferences storage; the app collects no credentials or special-category personal data. |
| Sensitive_Data_Logging | **Satisfies by inspection** | No answer/history payload is written to logs; logging is limited to failure diagnostics. |
| Hardware_IDs | **Satisfies** | No hardware identifier API is used. |
| App_Data_Backup | **Does not satisfy** | `allowBackup` is true, but both backup XML files are untouched templates with TODOs and restore has not been tested. This is configuration, not a completed backup strategy. |
| App_Logins_Restoration | **N/A** | The app has no accounts or sign-in. |
| Autofill_Hints | **N/A** | Inputs are numerical quiz answers, not identity, credential, address or payment fields. |
| Credential_Manager | **N/A** | No sign-in. |
| Biometric_Authentication | **N/A** | No sensitive document or financial action. |
| Component_Export | **Satisfies** | Every Activity declares `android:exported`; only the launcher is exported. There are no services, receivers or providers. |
| Component_Permissions | **Satisfies** | Internal navigation uses explicit Intents and validates primitive extras. Sharing/source viewing deliberately uses system implicit Intents. No nested or mutable pending Intent exists. |
| Component_Protection | **N/A** | No component shares protected content and no custom permission is defined. |
| Network_Security_Traffic | **N/A for in-process traffic** | The app has no Internet permission or network client. Question source URLs are delegated to the user's browser; several bundled source URLs are HTTP and therefore inherit the browser/site risk rather than being app traffic. |
| Network_Security_Configuration | **Does not satisfy** | No network security configuration is declared. Current exposure is low because the app does no networking, but the checklist item is still absent and any future client would need this before shipping. |
| Security_Provider_Initialization | **N/A** | The app does not use Google Play services or its security provider. |
| WebView_Asset_Loader | **N/A** | No WebView. |
| WebView_JavaScript | **N/A** | No WebView or JavaScript bridge. |
| WebView_Navigation | **N/A** | No WebView. |
| App_Bundles | **Satisfies** | No dynamic code loading exists and a release Android App Bundle builds successfully. |
| Cryptographic_Algorithms | **N/A** | The app performs no cryptographic operation and implements no custom crypto. |

## Google Play

| ID | Status | Evidence and finding |
| --- | --- | --- |
| Play_Content_Policies | **Not demonstrated** | The educational content appears benign, but there is no completed Play policy review, Data safety declaration or rights/source audit for the final listing. |
| Play_Content_Rating | **Does not satisfy** | No Play Console content-rating questionnaire or resulting rating is recorded. |
| Play_Feature_Graphic | **Does not satisfy** | The repository has no feature graphic and the README still contains a screenshot placeholder. |
| Play_Device_References | **N/A until listing assets exist** | There are no final listing screenshots/videos to inspect. |
| Play_Misleading_Content | **N/A until listing assets exist** | There are no final listing screenshots/videos to compare with the app. |
| Play_User_Reviews | **N/A** | The app is not published and has no review history. |

## Test-environment gap

The official checklist asks for representative phone, foldable and tablet emulators, some real
hardware and the latest Android version. This project currently has one verified Pixel_9 AVD on
API 35. It has no physical-device, tablet, foldable, desktop-window, Play pre-launch,
Accessibility Scanner or API 37 device evidence. That is the largest gap in any claim that the app
meets the complete core checklist.

## Fix first

1. Upgrade the Android test stack and execute the full suite on API 37.
2. Define and test real backup/restore rules, including SQLite and both preference stores.
3. Add tablet/foldable/landscape/resizable-window layouts or constraints, including a 45–75
   character content width, then run the official transition tests.
4. Run TalkBack, Accessibility Scanner, StrictMode, startup and frame-timing checks.
5. Stop allowing CI lint failures (`continue-on-error: true`) and decide how to handle the ten
   remaining lint warnings rather than normalising them.
6. Enable release optimisation after verifying keep rules.
7. Produce honest Play screenshots and a feature graphic, complete the content rating, policy and
   Data safety reviews, and obtain a Play pre-launch report.
