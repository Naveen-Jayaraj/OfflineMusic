# BitTune — quiet listening

The new identity uses a record with a single open wave, warm charcoal, oatmeal,
sage and clay. Production artwork is a clean Android vector, shared by the header,
welcome screen, About dialog, launch animation, adaptive launcher and themed icon.
The SVG is an editable export of the same geometry. The raster concept is retained
as the design reference; it is not loaded by the app.

## Palette

| Role | Dark | Light |
| --- | --- | --- |
| Background | `#191C19` | `#F5F2E9` |
| Surface | `#20241F` | `#FCFAF4` |
| Sage / primary | `#A8B89A` | `#4F6546` |
| Clay / tertiary | `#DBAE98` | `#86533D` |
| Text | `#F1EEE5` | `#252B23` |

Manrope weights 400–800 are bundled in `app/src/main/res/font/manrope.ttf`.
Source: https://github.com/google/fonts/tree/main/ofl/manrope
The SIL Open Font License is included in the APK under `assets/licenses/Manrope-OFL.txt`.

## Motion

650 ms eased fade and 10 dp rise, with an 8 degree settling rotation of the record,
followed by a 250 ms dissolve. No sound, haptics, particles or looping motion.
Tap or Back skips. Android's animation setting is respected. Completion is saved
across activity recreation. System launch colors follow light/dark mode.

## Image generation provenance

Concept generated with the built-in image_gen tool, then translated to native
vector geometry for crisp edges, predictable tinting and Android adaptive masks.

Initial prompt:
> Use case: logo-brand. Asset type: production Android music app logo, square transparent PNG. Create a simple, understated record-inspired symbol for BitTune, an offline music player with a chill independent record-store aesthetic. One solid muted sage green (#A8B89A) round vinyl-like disc with a small transparent center hole, crossed on the lower third by one gently undulating negative-space groove, suggesting a relaxed sound wave. Confident, slightly organic curves, very minimal, flat two-dimensional graphic, clear silhouette at 24px. Center the single symbol with generous equal padding, symbol about 65% of image width. Background genuinely transparent. No lettering, no wordmark, no mascot, no headphones, no sparkle, no gradients, no texture, no glow, no shadows, no mockup, no border, no extra objects. Calm and human, like a tiny independent record label stamp.

Refinement prompt:
> Refine this logo for production. Preserve exactly the simple record disc, small round center hole, and one open wavy groove near the bottom. Make it a perfectly flat single-color solid #A8B89A silhouette with smooth crisp vector-like edges. Remove every trace of gradient, shading, noise, surface texture, white debris, colored edge artifacts, and speckles. The center hole and entire wavy groove must be completely transparent and clean. Transparent background. Center the symbol with equal padding. No text, no other marks. This is a tiny Android icon, so pristine flat geometry is essential.
