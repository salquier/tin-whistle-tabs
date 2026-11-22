# Android app: Irish Whistle Tabs

Irish Whistle Tabs is an open-source tool to learn and improve your tin whistle skills. It features hundreds of traditional Celtic tunes (jigs, reels, polkas, ballads, etc.).

![Promo banner](pictures/banner.png)

**Google play link**: https://play.google.com/store/apps/details?id=fr.charleslabs.tinwhistletabs

**Technical article link**: https://charleslabs.fr/en/project-Irish+Whistle+Tab+-+Android+app

This repo contains the Android Studio project and the database generator.

## Fork maintenance

This fork preserves the original project attribution and is maintained by Guillaume Jambet.

Every push to `master` automatically increments the patch component of the application version, increments `versionCode`, builds the release APK (signed with the stable key, see below), and publishes a GitHub release named after that version (for example, `1.9.13`) with a matching tag (for example, `v1.9.13`) and the APK `TinWhistleTabs-<version>.apk`.


The fork uses its own application id, `fr.salquier.tinwhistletabs`, so it can be installed next to the original Play Store app.

### Signing key

Android only installs an update signed with the same key as the installed app. The workflow signs APKs with a stable key stored in four repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the keystore file, base64 encoded |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Create the key once (keep the keystore and passwords safe: losing them means users must uninstall to update):

```sh
keytool -genkeypair -v -keystore tinwhistletabs.keystore -alias tinwhistletabs \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Tin Whistle Tabs"
base64 -w0 tinwhistletabs.keystore   # value of SIGNING_KEYSTORE_BASE64 (macOS: base64 -i tinwhistletabs.keystore)
```

Builds on `master` fail if the secrets are missing; pull request builds without them use a temporary key.

### Backup

Tunes created in the app and favourites are stored in the app's private storage and are deleted when the app is uninstalled. Use the menu (⋮) of the main screen: *Export my tunes and favourites* saves a JSON file, *Restore a backup* reads it back.
## Tune editor

Tunes can be added directly from the app: tap **+** in the main screen, type the tune in [ABC notation](https://abcnotation.com/wiki/abc:standard:v2.1) (with the keyboard or the note palette) and save it. The score and the tin whistle tabs are updated live, the required whistle is chosen automatically, and *Try the tabs* opens the tune in the player before saving. Any ABC tune can be pasted (from thesession.org for example).

To modify a saved tune, open it and tap **Edit** (pencil), or long press it in the list (edit or delete). Built-in tunes can be copied into the editor the same way. User tunes are stored on the device (`files/user_tunes.json`).

## Playing with another whistle

The **Whistle** menu of a tune sets the whistle you play (remembered for all tunes). By default the tabs are for the whistle recommended for the tune (badge in the list). With another whistle, the fingerings are transposed so that the tune sounds at the same pitch, for example a C whistle and a D whistle playing a duet; the octave is chosen to stay in the range of the whistle and avoid half-holed notes. Notes out of range are shown as `?`. Playback uses the sound of the chosen whistle, and the score with fingerings uses the same fingerings. See `WhistleTransposer`.

## Score with fingerings

In a tune, the **Score** button shows the sheet music. Its menu option *Fingerings under the notes* writes the tin whistle fingering under each note (same fingerings as the tab view). The fingerings are added to the ABC as lyrics (`w:` lines) drawn with the tab font, see `AbcParser.annotateWithTabs`. The choice is remembered.

## Sharing tunes

Open a tune and choose **Share (QR code)** in the menu (or long press it in the list): the app shows a QR code that another phone can scan, and can send it as an image or send the tune as ABC text. To import, tap **+** in the main screen: *Scan a QR code* (camera), *Import a QR code image* (photo or screenshot) or *Paste a tune* (ABC text or tune code from the clipboard). Text and QR images shared from other apps can also be sent directly to Tin Whistle Tabs. Imported tunes open in the editor to be checked and saved. New tunes (created, copied or imported) are added to the favourites.

The QR code contains `TWT1:` followed by the compressed title, type and ABC (deflate + base64url), see `TuneShareCodec`. QR codes use [ZXing](https://github.com/zxing/zxing) and [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) (Apache 2.0). The scanner requires Android 4.4 (API 19) or later.

The conversion from ABC to notes is done in the app by `AbcParser` (a Java port of the `abc2midi` + `abc2db.py` pipeline). Its unit test checks it against the pre-generated database: run `./gradlew test` in `Irish_Whistle`.

## ToDo

* Improve flute synth
* Add more tunes
* Add tunes request form
* Add sorting and filtering functions
* Add instructions (how to read tabs)

## Technical details

The app uses the ABC music format.

To print the tabs, the ABC files are pre-processed by the "abc2db.py" script that does and intermediate conversion to MIDI, and then to text. This makes it easy to synthesise the tunes and show the tabs. The ABC format is also used in the app to show the sheet music.

## Libraries

The database generator uses:
* Python3 (with json, os, mido and re)
* abc2midi (GNU GPL)
* TheSession.org tunes (ODbL v1.0, https://github.com/adactio/TheSession-data)

The app uses:
* abcjs (MIT, https://github.com/paulrosen/abcjs)
* ZXing and zxing-android-embedded (Apache 2.0)
* Piccolo samples from the FluidR3_GM SoundFont by Frank Wen (MIT, see `assets/licenses/FluidR3_GM.txt`), exported with `database_generator/export_samples.py`
* Tin Whistle Tab font (© Blayne Chastain, www.blaynechastain.com)
* Uncial Antiqua font (SIL Open Font License, Version 1.1)
