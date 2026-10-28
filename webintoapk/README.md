# WebIntoAPK

WebIntoAPK is an Android app for asking a cloud service to package a website you control as an Android app. The cloud service uses Supabase for accounts and project data, and GitHub Actions to build and sign the APK.

**The Android app is ready, but the cloud service needs to be connected to your own Supabase and GitHub accounts before sign-in or APK builds will work.** Follow [the backend setup guide](./backend/README.md).

## What you need

- A Supabase account and project
- A GitHub repository containing this workspace
- JDK 17 and Android SDK API 36 to build the Android app
- An Android signing key for cloud-generated APKs

## Build the Android app

After connecting Supabase, put its project URL and public anon key in `webintoapk/local.properties`:

```properties
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.anonKey=YOUR_SUPABASE_ANON_KEY
```

This local file is ignored by Git. Never put a Supabase service-role key or GitHub token in the Android app.

From the repository root, run:

```sh
./gradlew -p webintoapk :app:assembleDebug
```

The APK will be at `webintoapk/app/build/outputs/apk/debug/app-debug.apk`.

The website-to-APK build runs on GitHub Actions using this repository's Android website-wrapper template. After signing in with an authorized admin account, enter an HTTPS website you own, create the app, request a build, and refresh its status to download the APK.
