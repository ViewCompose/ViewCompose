# Third-Party Notices

This project depends on third-party software. Their licenses remain the
property of their respective owners.

## Dependency Sources

Primary dependency groups used by this project include:

1. AndroidX libraries
2. Google Android/Material libraries
3. Kotlin and KotlinX libraries
4. Paparazzi and related testing tooling

## License Notes

Most dependencies in this project are distributed under permissive licenses
(commonly Apache-2.0, MIT, BSD-style). Please refer to each dependency's
official source and license text for exact terms.

### AndroidX Media3 1.10.1

The optional `viewcompose-media3-androidx` integration directly uses:

- `androidx.media3:media3-common:1.10.1`
- `androidx.media3:media3-ui:1.10.1`

The Demo application additionally uses `androidx.media3:media3-exoplayer:1.10.1`. These artifacts
are provided by the Android Open Source Project under the Apache License 2.0. Their upstream source,
license, and release terms are available from the
[AndroidX Media repository](https://github.com/androidx/media) and the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## How to Verify

Check declared dependencies in Gradle files and version catalogs, then map to
their upstream license pages.
