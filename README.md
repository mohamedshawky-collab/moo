# BSM MARKET Android

Independent Android shell for BSM MARKET, using the existing Google Play package and the canonical BSM Market web experience at https://www.bsm-properties.com/.

Build target: Android 16 / API 36. Current app version: 3.0.0.

The existing `android-build.yml` produces an unsigned build artifact only. It does not publish an app update.

Use `Publish BSM MARKET Android Production` with the next unused version code and version name for the existing Play application. This workflow keeps the package ID and verifies the original upload certificate before signing. It does not generate or reset a signing key.

Bind the existing upload keystore through repository secrets `BSM_MARKET_KEYSTORE_BASE64`, `BSM_MARKET_STORE_PASSWORD`, `BSM_MARKET_KEY_ALIAS`, and `BSM_MARKET_KEY_PASSWORD`. Bind the existing authorized Play identity as `BSM_PLAY_SERVICE_ACCOUNT_JSON`. Set repository variable `BSM_MARKET_UPLOAD_CERT_SHA256` to the existing upload certificate SHA-256 shown under Google Play Console → App integrity. Supply these through secure settings; do not commit credential files or paste values into reports.

The public policy pages already belong to the existing MARKET web application:

- Privacy: https://www.bsm-properties.com/privacy
- Data-deletion requests: https://www.bsm-properties.com/delete-account

If upload or commit has an unknown result, keep the `bsm-market-production-VERSION_CODE` artifact and rerun with `resume_run_id` set to that run. The workflow restores the same signed bundle and edit state, reconciles Google Play state, and blocks a duplicate unresolved upload. A successful result means the release was submitted to Google Play; it does not establish that the update is available to users. Confirm actual availability from the existing Play Console release status.
