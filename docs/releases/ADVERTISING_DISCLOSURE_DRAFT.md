# Advertising privacy and Play declaration draft

18 October 2026. Preparation for an eventual ad-enabled candidate. **Not deployed, not submitted to Play, and not the policy for the frozen 0.1.11 package.** Debug-only AdMob testing is manually activated; release activation and placements remain disabled. Owner has an AdMob account; Birlevo registration, app-specific messages and production units are pending.

## Actual integration inventory

- AdMob GMA Next-Gen 1.5.0 / UMP 4.0.0; no mediation, Firebase Analytics, account login, cloud sync or media upload API.
- SDK manifest declares `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_BASIC_PHONE_STATE`, `com.google.android.gms.permission.AD_ID`; application foreground-service/notification/wake-lock permissions remain. Permission presence and actual SDK activation/data transmission are different facts.
- Application passes only Android host/application context and Google's sample ID to UMP/SDK initialization. It does not pass file bytes, filenames, URIs, document text, project images, output sizes or custom targeting/content URLs.
- Google documents possible collection/sharing of IP address (including approximate-location inference), product interactions, diagnostic information and device/account identifiers such as advertising ID and app-set ID. TLS is documented for transmission. This does not justify declaring that the whole app never collects data or never uses the network.
- No Birlevo server, support export, analytics service or new crash-reporting product is added. SDK crash reporting is disabled through initialization configuration, but that does not eliminate all provider diagnostics.
- UMP messages must be configured in AdMob and rechecked per launch once production is enabled. Its `canRequestAds` gate is not a personalized-ad opt-in flag. Do not substitute an app checkbox for a configured regional message.

## Candidate privacy text: English

### Files and local processing

Birlevo processes the files and presentation projects you select on your Android device. Birlevo does not upload those files, document contents or project images to its own servers or supply them as advertising targeting data. Saving and sharing through Android can send a selected result to another application or destination that you choose.

### Advertising and privacy choices

The ad-enabled version uses Google AdMob and Google's User Messaging Platform. When advertising services are activated, Google may process device and account identifiers, IP address and approximate location inferred from it, app/ad interactions and diagnostic information for advertising, analytics and fraud prevention. Google describes encrypted transmission using TLS. Your advertising experience and data handling depend on your region, device settings and applicable privacy choices. Where required, a privacy message is shown before ads can be requested. Use the app's Ad privacy entry to manage choices when the provider requires it. If the privacy service is unavailable, you can continue using the file tools without advertising requests.

You can manage or delete your Android advertising ID in system settings. Google provides information about its practices and controls in its [privacy policy](https://policies.google.com/privacy) and [advertising controls](https://myadcenter.google.com/). Contact the developer at support@birlevo.com about Birlevo. Do not send private files unless you choose to provide them for a specific support request.

## Candidate privacy text: Turkish

### Dosyalar ve cihazda işleme

Birlevo, seçtiğiniz dosyaları ve sunum projelerini Android cihazınızda işler. Dosyalarınızı, belge içeriklerinizi veya proje görsellerinizi kendi sunucularına yüklemez ve reklam hedefleme verisi olarak iletmez. Android üzerinden kaydetme ve paylaşma işlemleri, seçtiğiniz sonucu sizin belirlediğiniz başka bir uygulamaya veya hedefe gönderebilir.

### Reklamlar ve gizlilik tercihleri

Reklamlı sürüm Google AdMob ve Google User Messaging Platform kullanır. Reklam hizmetleri etkinleştiğinde Google; reklam, analiz ve sahtekârlığı önleme amaçlarıyla cihaz ve hesap tanımlayıcılarını, IP adresini ve bundan çıkarılan yaklaşık konumu, uygulama/reklam etkileşimlerini ve tanılama bilgilerini işleyebilir. Google, iletimin TLS ile şifrelendiğini belirtir. Reklam deneyimi ve veri işleme; bölgenize, cihaz ayarlarınıza ve geçerli gizlilik tercihlerinize bağlıdır. Gereken durumlarda reklam istenmeden önce gizlilik mesajı gösterilir. Sağlayıcı gerektiriyorsa uygulamadaki reklam gizliliği bölümünden tercihlerinizi yönetebilirsiniz. Gizlilik hizmeti kullanılamıyorsa reklam isteği yapılmadan dosya araçlarını kullanmaya devam edebilirsiniz.

Android reklam kimliğinizi sistem ayarlarından yönetebilir veya silebilirsiniz. Google'ın uygulamaları ve kontrolleri hakkında [gizlilik politikasını](https://policies.google.com/privacy) ve [reklam kontrollerini](https://myadcenter.google.com/) inceleyebilirsiniz. Birlevo hakkında support@birlevo.com adresine yazabilirsiniz. Belirli bir destek talebi için paylaşmayı seçmediğiniz sürece özel dosyalarınızı göndermeyin.

## Console and account checklist before activation/distribution

If Birlevo is not publicly discoverable in a supported store, register it as unpublished for testing rather than treating a private/closed-test listing as a public store link. App registration does not itself authorize full ad serving; the actual account's app readiness and app-ads.txt verification remain separate gates. See [Google's current app setup guide](https://support.google.com/admob/answer/9989980?hl=en).

1. In AdMob **Apps → Add app**, register Android package `studio.birlevo.android` and existing Play listing if available. Record the app ID and create the chosen test/production ad units. An app ID is public configuration, not a secret API key.
2. Configure applicable Privacy & messaging regional messages and their language/withdrawal behavior. Verify with a registered test device/EEA debug geography and the actual app ID. The sample ID cannot certify your account-hosted forms.
3. Resolve target audience, child/teen and under-age-of-consent treatment before production requests. Do not infer age from UI language, region or the existence of an AdMob account. Any child-directed/age-screen design requires a separate implementation and policy review; current debug testing is not approval for serving ads to children.
4. Complete **Contains ads** according to the actual distributed candidate and Google Play's current definition. For an ad-enabled candidate, declare ads even if some eligible users do not see one.
5. Reconcile the advertising-ID form with the actual merged `AD_ID` permission and SDK use. For an activated SDK using the ID, declare use and the actual relevant purposes; do not leave the old no-ID answer merely because Birlevo's own media code never reads it. Removing the ID is a code/configuration change requiring another review, not a form-only answer.
6. Complete Data safety for the exact candidate: review approximate location, device/other IDs, app interactions and diagnostics, applicable collection/sharing definitions, purposes, optionality, retention/deletion and encryption against the current provider and account setup. Do not copy every checkbox from this draft or claim the developer controls Google's retention.
7. Publish the finalized policy before distributing the affected candidate and use its correct URL in Play/AdMob/in-app privacy entry. Replace broad ad-free/no-network/no-collection claims only for the new version; keep an explicit old-version boundary where appropriate. Production UI must make required privacy choices reachable beyond the current debug panel.
8. Qualify actual test ad rendering, lifecycle destruction, request suppression after choices change, offline/slow failure, placement spacing and frequency before enabling live identifiers. Test ads must not be mistaken for monetization or policy approval. Release/R8 and disclosure checks remain separate gates.
9. Verify app readiness and app-ads.txt using the actual publisher/account details and the website linked from the public store listing. Do not deploy a sample publisher entry or infer verification/approval merely from creating the app. Account-specific status must be checked before serving live ads.

References: [provider data disclosure](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure), [UMP flow](https://developers.google.com/admob/android/next-gen/privacy), [Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [AdMob terms](https://developers.google.com/admob/terms). Recheck them and the real account/candidate before submission.
