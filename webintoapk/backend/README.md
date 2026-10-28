# Connect the WebIntoAPK backend

This guide connects the Android app to the Supabase and GitHub Actions backend in this repository. You do this setup once for your own accounts. You do not need to write backend code.

## 1. Create a Supabase project

Create a project in the Supabase dashboard and keep its project URL, anon/public key, and service-role/secret key available. The project URL and keys are in **Project Settings → API**.

## 2. Create the backend database tables

In Supabase, open **SQL Editor → New query**. From the repository root, open and run these SQL files in this order:

1. [`supabase/app-settings.sql`](../../supabase/app-settings.sql) creates the app-admin table.
2. [`supabase/app-builder.sql`](../../supabase/app-builder.sql) creates website projects, build records, access policies, and private APK storage.

Run each file's complete contents and make sure Supabase reports success.

## 3. Create your admin login

In Supabase, open **Authentication → Users** and add a user with your email and a password. Then return to SQL Editor and run this query, replacing the example email with that exact email:

```sql
insert into public.app_admins (user_id)
select id
from auth.users
where email = 'you@example.com'
on conflict (user_id) do nothing;
```

Only users listed in `app_admins` can use the builder.

## 4. Add the build workflow to GitHub

Push this repository to GitHub, including [the build workflow](../../.github/workflows/build-webview-app.yml). The workflow must be on the repository's default branch, and GitHub Actions must be enabled.

Create an Android signing key on a trusted computer. Keep a backup: losing this key means you cannot publish updates signed as the same app.

```sh
keytool -genkeypair -v -keystore webintoapk-release.jks -alias webintoapk \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 webintoapk-release.jks
```

In GitHub, open **Repository → Settings → Secrets and variables → Actions** and add these repository secrets:

| Secret name | Value |
|---|---|
| `SUPABASE_URL` | Your Supabase project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase service-role/secret key |
| `ANDROID_KEYSTORE_BASE64` | The single-line output from the `base64` command |
| `ANDROID_KEY_ALIAS` | `webintoapk` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | The key password chosen by `keytool` |
| `ANDROID_STORE_PASSWORD` | The keystore password chosen by `keytool` |

Never commit the keystore or these secret values to Git.

## 5. Deploy the build-request function

Install the Supabase CLI if needed, then from the repository root deploy the function:

```sh
supabase functions deploy dispatch-build --project-ref YOUR_PROJECT_REF
```

`YOUR_PROJECT_REF` is the project identifier in the Supabase project URL (the part before `.supabase.co`).

In Supabase **Edge Functions → dispatch-build → Secrets**, set:

| Secret name | Value |
|---|---|
| `SUPABASE_URL` | Your Supabase project URL |
| `SUPABASE_ANON_KEY` | Your Supabase anon/public key |
| `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase service-role/secret key |
| `GITHUB_TOKEN` | A GitHub token for this repository with permission to dispatch Actions workflows |
| `GITHUB_OWNER` | The GitHub account or organization that owns the repository |
| `GITHUB_REPO` | The repository name |
| `GITHUB_REF` | The branch containing the workflow, usually the default branch |
| `GITHUB_WORKFLOW_FILE` | `build-webview-app.yml` |

Keep the service-role key and GitHub token in Supabase secrets only. Do not put them in the Android app or commit them to the repository.

The function implementation is [`supabase/functions/dispatch-build/index.ts`](../../supabase/functions/dispatch-build/index.ts). It checks that the signed-in user is an admin, records the build request, and asks GitHub Actions to build it.

## 6. Connect the Android app

Create `webintoapk/local.properties` with the project URL and anon/public key:

```properties
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.anonKey=YOUR_SUPABASE_ANON_KEY
```

Build the Android app from the repository root:

```sh
./gradlew -p webintoapk :app:assembleDebug
```

Install the generated `webintoapk/app/build/outputs/apk/debug/app-debug.apk`, sign in with the admin account from step 3, create a project with an HTTPS website address, and tap **Build APK**.

## Troubleshooting

- **Sign-in works but app creation fails:** confirm you ran both SQL files and added your user to `app_admins`.
- **“Build service is not configured”:** check the Edge Function secrets in step 5.
- **Build request fails:** check the function logs in Supabase and make sure the GitHub token can dispatch workflows.
- **Build fails in GitHub:** open **Actions**, select the failed run, and check its log. Confirm all six GitHub Actions secrets in step 4 are present.
- **APK build succeeds but the app cannot connect:** confirm `webintoapk/local.properties` contains the correct project URL and anon key, then rebuild the Android app.
