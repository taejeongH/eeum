# Firebase Android Setup

`mobile/app/google-services.json` is now treated as a local-only file and must not be committed.

Use one of these options:

1. Place your Firebase config directly at `mobile/app/google-services.json`.
2. Store the source file elsewhere on your machine and point to it with `GOOGLE_SERVICES_JSON_PATH`.

You can set `GOOGLE_SERVICES_JSON_PATH` in `mobile/local.properties`:

```properties
GOOGLE_SERVICES_JSON_PATH=C:\\secure\\firebase\\google-services.json
WEBVIEW_URL=https://i14a105.p.ssafy.io
API_BASE_URL=https://i14a105.p.ssafy.io
```

Or export it as an environment variable before building:

```powershell
$env:GOOGLE_SERVICES_JSON_PATH="C:\secure\firebase\google-services.json"
```

If the file is available, the Gradle build copies it into `mobile/app/google-services.json` locally and applies the Firebase Google Services plugin. If it is missing, the app still builds without that plugin, but Firebase messaging will not be configured.

Because the previous file contents were exposed in git history, rotate or restrict the Firebase/Google API key in the Firebase Console or Google Cloud Console after the history rewrite and force-push.
