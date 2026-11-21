# Practice Tracker V1

Android starter project for personal keyboard/music practice tracking.

## Included
- Email/password Firebase Authentication
- Firestore-backed practice-session storage
- Practice timer and session history
- Audible BPM metronome (40–240 BPM)
- Trinity Grade 1–8 progress tracking
- Weekly and monthly practice summaries
- CSV session export
- Daily local practice reminders

## Setup
1. Open this folder in Android Studio with JDK 17.
2. Create a Firebase project and register Android package `com.sanjaimenan.practice_tracker`.
3. Enable Email/Password in Firebase Authentication and create Cloud Firestore.
4. Download `google-services.json` into `app/`.
5. Apply the rules in `firestore.rules`.
6. Sync Gradle and run on an Android device.

`google-services.json` is intentionally not included. Never commit service-account private keys.

## Testing status
The source is prepared but has not yet been built with Android SDK or tested against a live Firebase project. Test reminders and metronome timing on-device.
