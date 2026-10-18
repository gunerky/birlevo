# Larger Google Play store icon

19 October 2026. Store-only artwork update requested after the file/play mark appeared small on the Play listing. The current store asset was exported from the launcher-safe artwork, leaving the mark at about 46% of the square's height.

The replacement [512 px store icon](assets/birlevo-play-icon-512.png) retains the navy, white, cyan and coral file/play identity with a larger centered mark. The measured foreground is 295 × 394 px, versus 181 × 237 px in the previous store PNG: about **66% taller**. Bounds are measured from pixels whose red or green channel exceeds 130, excluding the navy background and most antialiasing.

## Upload asset and scope

- Final: **512 × 512**, **32-bit RGBA PNG**, tagged sRGB, fully opaque, **242,983 bytes**.
- SHA-256: `72caa0677b39a16e7c123f86ad61f34d8f2770bc74c901a0807de0e53a1c71f2`.
- Square full-bleed background; no baked outer rounding, border, badge, text or drop shadow. Google Play applies its own mask and outer shadow.
- Upload copy: `output/branding/store-icon-2026-10-19/app-icon-512.png`. This is the file for the store's **App icon** field, not the comparison preview.
- The original source, former store asset, Android launcher/adaptive/monochrome resources, website icons and signed APK/AAB files remain unchanged. No version increment or app build is performed.
- This does not upload or publish the store entry. The owner updates the icon in Play Console.

In Play Console, open Birlevo's store listings, edit the main/default listing and replace its **App icon** under graphics with this PNG. Check localized/custom listings for explicitly overridden graphics before submitting the store-entry update. A store-listing icon is separate from the APK launcher icon.

The output directory also retains the generated 1254 px master, prompt, mechanical size/format validation and a labeled small-size comparison. The built-in image generator edits the existing owned artwork; GDI+ only downsamples the selected result and exports the required PNG. No font, SDK or third-party image package is added to the application.

Measured small-size previews are visually inspected for the full file silhouette, play cutout, cyan corner and coral ribbon. Masked previews are illustrations of store rendering, not captured live Google Play screenshots; they are not upload assets.

Google's current [icon design specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) require 512 × 512, 32-bit PNG, sRGB and a maximum of 1024 KB, with the outer mask/shadow applied by Play. The [graphic asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151) describes the store-listing assets.

## Exact built-in edit prompt

Edit target: the original owned `docs/design/assets/birlevo_logo.png`. Built-in tool mode; no CLI/API-key fallback.

```text
Use case: precise-object-edit
Asset type: Google Play store icon for Birlevo, full square.
Input image 1 is the edit target: Birlevo's original owned artwork. Preserve the same exact logo identity, proportions, silhouette, rounded file-page corners, dark-navy right-pointing rounded play cutout, cyan folded top-right corner, and coral diagonal ribbon curving around the bottom.
Change only the size and positioning of that existing foreground mark: enlarge it uniformly to occupy about 74% of the square canvas height, with about 13% navy breathing room above and below; center the entire mark optically and horizontally. Keep its natural aspect ratio (roughly 57% of canvas width). It must look substantially larger and clearer at tiny mobile store sizes. Do not stretch, rotate, crop, change the geometry, redraw the logo concept, or change the relative sizes of its internal pieces.
Keep the full-bleed deep navy background and the original white/cyan/coral brand colors and subtle smooth shading. Produce a polished crisp high-resolution square raster asset, 1024x1024 if possible. Entire canvas is opaque.
Do not bake rounded outer icon corners, external drop shadow, frame, outline, badge, words, letters, captions, phone UI, or transparency into the asset. Google Play supplies the outer mask. Output only the single square icon image.
```
