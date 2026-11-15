# Android app: Irish Whistle Tabs

Irish Whistle Tabs is an open-source tool to learn and improve your tin whistle skills. It features hundreds of traditional Celtic tunes (jigs, reels, polkas, ballads, etc.).

![Promo banner](pictures/banner.png)

**Google play link**: https://play.google.com/store/apps/details?id=fr.charleslabs.tinwhistletabs

**Technical article link**: https://charleslabs.fr/en/project-Irish+Whistle+Tab+-+Android+app

This repo contains the Android Studio project and the database generator.

## Fork maintenance

This fork preserves the original project attribution and is maintained by Guillaume Jambet.

Every push to `master` automatically increments the patch component of the application version, increments `versionCode`, builds the debug APK, and publishes a GitHub prerelease named after that version (for example, `1.9.13`) with a matching tag (for example, `v1.9.13`).

## Tune editor

Tunes can be added directly from the app: tap **+** in the main screen, type the tune in [ABC notation](https://abcnotation.com/wiki/abc:standard:v2.1) (with the keyboard or the note palette) and save it. The score and the tin whistle tabs are updated live, the required whistle is chosen automatically, and *Try the tabs* opens the tune in the player before saving. Any ABC tune can be pasted (from thesession.org for example).

To modify a saved tune, open it and tap **Edit** (pencil), or long press it in the list (edit or delete). Built-in tunes can be copied into the editor the same way. User tunes are stored on the device (`files/user_tunes.json`).

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
* Tin Whistle Tab font (© Blayne Chastain, www.blaynechastain.com)
* Uncial Antiqua font (SIL Open Font License, Version 1.1)
