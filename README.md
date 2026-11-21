# Practice Tracker V1 — Installable PWA

Phone-first Progressive Web App. No Android Studio or laptop required for development.

## Features
- Firebase email/password authentication
- Firestore personal practice sessions
- Practice timer and session history
- Audible BPM metronome (40–240)
- Trinity Grade 1–8 progress
- Weekly/monthly reports and CSV export
- PWA manifest, service worker and install prompt
- Daily notification reminder while the app is open (background delivery is not guaranteed)

## Firebase
Firebase web config is in app.js. Enable Email/Password Authentication and Firestore in project practice-tracker-5c663. Apply firestore.rules. Each user's records are stored under users/{uid}.

## Cloudflare Pages
Connect this GitHub repository. Framework preset: None. Build command: blank. Build output directory: / . Deploy over HTTPS for installation and service workers.

Cloud data requires internet. The app shell is cached after first visit.
