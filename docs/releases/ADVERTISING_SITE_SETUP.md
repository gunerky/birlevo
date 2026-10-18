# Advertising website setup

18 October 2026. The owner supplied the exact AdMob publisher declaration while completing the advertising setup. The reviewed static site is published on the existing Cloudflare Pages project `birlevo`; this does not enable advertising in an Android package or submit any Play declaration.

## Published content

- [app-ads.txt](https://birlevo.com/app-ads.txt) serves the owner-provided line as plain text: `google.com, pub-5967660342681325, DIRECT, f08c47fec0942fa0`.
- [English privacy policy](https://birlevo.com/en/privacy/) and [Turkish privacy policy](https://birlevo.com/privacy/) distinguish previously prepared advertising-free releases from builds with advertising enabled and manually activated development testing. They disclose Google advertising/identifier/interaction/diagnostic handling and sharing, regional choices, possible limited ads after rejection, local file handling, clearing/export behavior, website hosting and support contacts.
- The English homepage, blue header styling, five transparent favicon assets, language links and footer layout are preserved. No app capability, SDK dependency, media engine, version or signed package changes.

The actual sources are [publisher text](../../web/app-ads.txt), [English policy](../../web/en/privacy/index.html) and [Turkish policy](../../web/privacy/index.html). The [provider data disclosure](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure) and [app-ads.txt guide](https://support.google.com/admob/answer/9363762?hl=en) were rechecked. The disclosure reflects the reviewed integration; website publication is not an assertion of complete legal or Play/account approval.

## Deployment and verification

- Cloudflare production deployment: `4bc9815d-91a3-4618-99fa-cb0bf47a1bdc`, API deploy stage succeeded at **2026-10-18 07:42:27 UTC**.
- Prior site before this task: `b6a5867d-b89d-4e96-9ae6-4fffaf257470`. The project and existing custom domain were reused; source/deployment settings and other Cloudflare resources were not changed.
- Preview deployment `cfb78ca7-43e9-47e1-af6d-c2c3be46a25c` succeeded through the API, but its pages.dev HTTP address timed out from the verification environment. Preview HTTP validation is not reported as passed. Local reviewed content was then deployed to the reachable custom domain and fully verified there.
- All **9 production URLs returned HTTP 200**: homepage, both policies, publisher text and five icons. Publisher text and icons match the source bytes; the publisher response is `text/plain`. HTML matches after the existing narrow normalization for Cloudflare email protection and automatic website analytics scripts. All other content/styles are compared. No TLS verification bypass was used.
- Public site evidence and final asset hashes are in ignored `output/development/advertising-site/final-production/site-deployment.json` and `output/development/advertising-site/final-manifest.json`. The manual deployment records the baseline Git head with `commit_dirty=true`; the exact published asset hashes define the content.
- The uploader explicitly includes the publisher file alongside the original eight reviewed assets. A temporary Pages upload credential is passed through a hidden stdin prompt, never source files, arguments, reports or Git. `.gitattributes` keeps the publisher file in LF text form.

Reproduce the read-only public verification from the repository root:

```powershell
python tools/testing/verify_live_site.py output/development/advertising-site/recheck 4bc9815d-91a3-4618-99fa-cb0bf47a1bdc --fetch
```

## Remaining account and release gates

The Play listing's developer website must point to the matching domain (`https://birlevo.com`), and AdMob must be able to find the supported public store listing before account verification can be assumed. Hosting the correct file does not itself complete crawling, app verification or readiness review. Check the actual account rather than treating an HTTP pass as Google approval.

The owner subsequently confirmed English European/US message publication, and actual-account emulator checks verified the returned forms. The later [pre-release readiness record](PRE_RELEASE_READINESS.md) publishes the age-aware policy additions with a separate verified deployment. Actual-account UMP forms, audience/age treatment, Play Ads/advertising-ID/Data safety answers, production activation and the signed candidate still require their agreed checks. The [Home placement report](../development/ADVERTISING_PLACEMENTS.md) records the tested debug sample-banner boundary; publishing these pages does not activate the owner's ad unit.
