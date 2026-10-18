# Home banner development

Follow-up: [account privacy message QA](ADVERTISING_ACCOUNT_QA.md) adds an explicit debug-only account profile and validates Birlevo's returned European/US forms. The sample placement results below are the original artifact-specific record; ordinary debug still defaults to sample identifiers and release activation remains closed.

## Scope and account configuration

The owner chose a restrained first placement: one adaptive banner at the bottom of Home, separated from the file controls. No full-screen, app-open, rewarded or per-file advertisement is introduced. Existing formats, quality, resolution, saving and sharing remain available without watching an advertisement.

On 18 October 2026 the owner registered Birlevo in AdMob and reported saving European and US state privacy message drafts. The supplied public configuration is:

| Item | Account value |
| --- | --- |
| Android package | `studio.birlevo.android` |
| AdMob application | `ca-app-pub-5967660342681325~8485054661` |
| Home banner unit | `ca-app-pub-5967660342681325/5970571966` |

These are public configuration identifiers, not credentials. They are recorded here for the later reviewed activation; this patch does **not** put them into an application's request path. The debug implementation continues to use Google's public Publisher Test Ads application and banner identifiers. Release still hardcodes advertising testing off. Message drafts, app readiness, app-ads.txt, audience treatment and actual production eligibility have not been independently verified in the account.

## Interaction and ownership

- Ordinary startup and opening Ad privacy do not contact the provider. The user must first run Check privacy, then explicitly choose Show test banner after the existing UMP/initialization gate succeeds.
- Home owns the placement. The main list receives the remaining height; the native advertisement does not overlay file controls. Width is measured from the available content, capped at 720 dp, using the ordinary anchored adaptive size rather than a large banner. Short windows omit the placement if its height would exceed a quarter of the available height; this does not reduce media output dimensions.
- Navigating out of Home, stopping/recreating its Activity or opening another Home panel removes the banner. The banner choice is deliberately not saved; there is no automatic reload on returning or recreation. Opening privacy choices removes the previous banner and requires a fresh consent check.
- Active application-owned processing jobs or unresolved job recovery prevent the test placement. This guard does not claim that all possible synchronous/non-job work is globally measured; the placement is absent from every work screen.
- The controller rechecks current eligibility before requesting and before revealing the native view. Callback generations reject late success/failure from an old view. Provider callbacks are marshalled to the UI coroutine scope. Native destruction is idempotent.
- A 15-second load deadline removes the view on slow loading. Failure is non-blocking and does not schedule a retry. App-initiated banner attempts, including failures, are separated by at least 60 seconds using a persisted timestamp; a backward wall-clock adjustment rebases the timestamp and denies that attempt. This applies to application-issued requests, not the provider's own configured automatic refresh. Confirm the final unit's refresh settings before production activation.
- No selected media, project title, filename, URI, document contents, content URL or custom targeting is supplied to the ad request.

## Qualification and remaining gates

The frozen debug APK is **40,674,921 bytes**, SHA-256 `60d95a9ccc0f124bcc836ae96aa3beac838bdbe757a6e7e4b75b5bb3146ae61d`. It is 6,564 bytes larger than the prior lint-maintenance debug artifact; unshrunk debug size does not predict a release download or retained heap. Evidence is under ignored `output/development/home-banner/`.

| Check | Result |
| --- | --- |
| Debug app and instrumentation build | Passed; no signed release or version change |
| JVM suite | 176 tests, no failures/errors; includes 9 banner ownership/timeout/cooldown/dispatcher cases |
| Lint | 0 errors, 7 existing warnings, 0 hints; none suppressed |
| API 24 / Android 7 | 7 checks: 4 startup/privacy/manifest checks and 3 real sample-banner lifecycle cases |
| API 36 | 20 passing checks: the same 7, 5 workspace flows, 8 native accessibility/RTL flows |
| API 36, 200% text / dark / Arabic RTL | 8 additional native accessibility/workspace checks |
| Combined emulator result | 35 passed; 1 separate TalkBack-speech case skipped by its existing explicit opt-in |
| Runtime and native license coverage | 132 exact reviewed runtime artifacts plus native encoder; no dependencies changed |
| Native package | 8 encoder/bridge libraries across 4 ABIs; exact source/notices, dynamic linkage, no decoder, 16 KiB LOAD/RELRO passed |
| Development-tool isolation | Source and exact APK guard passed; no Jev API call |
| Localization | Three added strings present in all 22 locale resources; English/Turkish directly reviewed |

All six network cases load a real Google sample banner after interacting with the Publisher Test Ads **Do not consent** button. They then verify removal on recreation, Home panel/edit navigation or backgrounding, with no automatic resumption. Both API screenshots were inspected for separate banner/control bounds. The sample rejection path is not a universal no-ad signal, a production-account form test or evidence of personalized-ad consent. The large-text/dark suite checks application controls; it does not certify provider-form speech or simultaneous heavy encoding with a resident SDK. The existing [foundation report](ADVERTISING_FOUNDATION.md) and [lint/dependency review](LINT_WARNING_REVIEW.md) retain their earlier artifact-specific results. Translation population is not independent linguistic review of all 22 languages.

An initial live test caught a wrong-thread native visibility update: a Compose test coroutine interceptor did not provide the required main-thread dispatch for the SDK's background callback. The controller now uses an explicit main dispatcher; a dedicated-executor JVM regression proves callback dispatch, and the real banner tests pass on both APIs. Another initial test diagnostic involved a disappearing UMP WebView and the underlying Compose dialog; the final helper retains actual provider-form interaction, tolerates only the specific missing-view race and still requires current SDK eligibility plus a visible sample banner. Earlier failed logs are retained separately. Native form-node probing did not complete that provider form and is not reported as accessibility qualification.

Reproduce on dedicated QA emulators after building the debug targets:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./tools/testing/home-banner-qa.ps1 -Serial emulator-5580 -Api 36 -Network -Workflows
./tools/testing/home-banner-qa.ps1 -Serial emulator-5582 -Api 24 -Network
```

`-Network` explicitly contacts the public test provider and never substitutes the owner's live unit. The runner validates AVD name/API and uses fresh instrumentation processes. It does not enable TalkBack speech. The additional large-text/dark run uses the same app APK with font scale 2.0 and night mode; the environment was restored and both dedicated emulators were stopped afterward. The frozen signed 0.1.11 (12) APK remains SHA-256 `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`.

Before any production activation:

1. The [website policies and publisher file](../releases/ADVERTISING_SITE_SETUP.md) are now published, preserving the older ad-free/development boundaries. Reconcile the [Play disclosure draft](../releases/ADVERTISING_DISCLOSURE_DRAFT.md) with the exact final candidate before distributing it.
2. The owner chose English-only messages; [actual-account tests](ADVERTISING_ACCOUNT_QA.md) now verify returned European/US forms on recognized QA emulators. Resolve target-audience/age treatment and retain regional/privacy regression checks when the final placement changes. Neither sample nor account-form success certifies full production readiness.
3. Verify app-ads.txt with the actual account entry and public store-linked developer website; inspect readiness rather than inferring approval from registration.
4. Add the production privacy entry and activation configuration only after these gates are resolved. Check banner spacing, provider refresh, consent withdrawal, background work/resource coexistence, offline failure and lifecycle behavior on the exact candidate.
5. Run the integrated and signed/R8 release gates only when the owner requests a release. This patch does not increment the version or create a signed APK/AAB.

## References

- [Next-Gen adaptive banner integration and cleanup](https://developers.google.com/admob/android/next-gen/banner)
- [Test advertisements](https://developers.google.com/admob/android/next-gen/test-ads)
- [UMP setup](https://developers.google.com/admob/android/next-gen/privacy)
- [European messages](https://support.google.com/admob/answer/10113207?hl=en)
- [US state messages](https://support.google.com/admob/answer/10860309?hl=en)
