# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

`zero-bounce-android-sdk-setup` is the ZeroBounce Android SDK. `ZeroBounceSDK` is a Kotlin `object` (one shared client). `ZeroBounceClient` is a separate object per API key. HTTP goes through OkHttp. Releases are published to Maven Central as `com.zerobounce.android:zerobouncesdk`.

## Layout

- `zero_bounce_sdk/src/main/java/com/zerobounce/android/ZeroBounceSDK.kt`: shared client and request helpers.
- `zero_bounce_sdk/src/main/java/com/zerobounce/android/ZeroBounceClient.kt`: one API key. It delegates through a thread-local `SdkState`.
- `zero_bounce_sdk/src/main/java/com/zerobounce/android/Priority3Api.kt`: bulk email finder, domain search, list evaluator, filters, the S3 / SFTP bulk jobs, and the Inbox Placement Tester transport. The six file jobs share `ZBBulkFileJobOptions` and `bulkRootFileJob`.
- `zero_bounce_sdk/src/main/java/com/zerobounce/android/HttpMessages.kt`: turns HTML, empty bodies, and parser failures into a short `ErrorResponse`.
- `zero_bounce_sdk/src/test/java/com/zerobounce/android/OfflineApiTest.kt`: offline suite. A fake OkHttp interceptor records every call. No network and no real API key.
- `zero_bounce_sdk/build.gradle`: `PUBLISH_VERSION` is the only version to bump.

## Commands

```bash
./gradlew :zero_bounce_sdk:testDebugUnitTest
./gradlew :zero_bounce_sdk:assembleDebug
```

The Kotlin compile inside `testDebugUnitTest` is the typecheck. CI (`.github/workflows/sdk_ci.yml`) runs those two tasks. There is no separate lint step that CI runs.

The live API suite is excluded from the default test task unless `-PzbLive` is
passed, so neither `testDebugUnitTest` nor CI runs it:

```bash
export ZEROBOUNCE_API_KEY=... ZEROBOUNCE_WARMUP_API_KEY=...
./gradlew :zero_bounce_sdk:testDebugUnitTest -PzbLive --tests "com.zerobounce.android.LiveApiTest"
ZEROBOUNCE_LIVE_SPEND=1 ./gradlew :zero_bounce_sdk:testDebugUnitTest -PzbLive --tests "com.zerobounce.android.LiveApiTest"
ZEROBOUNCE_LIVE_CHANGES=1 ./gradlew :zero_bounce_sdk:testDebugUnitTest -PzbLive --tests "com.zerobounce.android.LiveApiTest"
```

Keys come from the environment only. Never write one into a file, a commit, a
log, a fixture or a message.

## Conventions

- **One client per API key.** `ZeroBounceSDK` shares one key. Do not call `initialize` on it for two users. Construct `ZeroBounceClient` per key. The offline concurrency test does this.
- **Column indexes are 1-based** for `sendFile`, scoring send, email finder, domain search, and list evaluator. `0` means the optional name, gender, or IP column is left out. `ValidateS3FileOptions.emailAddressColumn` sends `0` when set. `sendFile`'s email column rejects `0`.
- **Optional parameters are omitted when unset.** Explicit `false` is sent as `false`. Validate `timeout` must be 3–60 seconds. Batch `timeout` must be 10–120. `0` is outside those ranges and is rejected before a request. `0` on an optional name column means the column is omitted. `0` on an S3 email column is sent.
- **Booleans are the strings `true` and `false`.** Do not send `1` or `0`.
- **`allow_phase_2` is validation send and `validateS3File` only.** `activity_data` on get-file is validation only.
- **`validateBatch` uses `api.zerobounce.net`**, not `bulkapi.zerobounce.net`.
- **Failures use the error callback.** The message must be short: no raw HTML, no megabyte body, no parser exception text, and no `Error: Error:` prefix. An account-level `validateBatch` error (`email_address` `all`) is an error callback. A per-address error stays on the success result.
- **Tests set `transportInterceptor` before `initialize`.** Do not call the live API from the default test run; `LiveApiTest` is the separate, opt-in suite behind `-PzbLive`.
- **Errors carry the HTTP status.** `ErrorResponse.statusCode` is set on every failed response, so callers can tell a Cloudflare 403 from an API 400/404 and a Warmup 429. A failure that never reached the API leaves it 0.
- **`BuildGradleValidationTest` fails when `PUBLISH_VERSION` appears on a dependency line.** A real dependency may share that number. Add its coordinate to `COORDINATES_ALLOWED_TO_MATCH` only after you confirm the version is that dependency's own. Do not delete or weaken the test.
- `http://localhost` is allowed as a base URL. Any other non-HTTPS base URL is rejected.
- **Five base URLs on `SdkState`.** `apiBaseUrl`, `bulkApiBaseUrl` (ends in `/v2`), `bulkApiScoringBaseUrl`, `bulkApiRootUrl` (the S3/SFTP jobs, **no** `/v2`), plus `listEvaluatorBaseUrl` and `inboxPlacementBaseUrl`. The last two are separate hosts rather than paths built from the bulk base, so `initialize` alone leaves them on the live API — use `setListEvaluatorBaseUrl` / `setInboxPlacementBaseUrl`, and reset them in `resetSharedUrls`.
- **`enc` is form encoding.** It turns a space into `+`, which is correct in a query string but a literal plus in a path. Use `encPathSegment` for anything that goes into a path, such as the list evaluator file id and the Inbox Placement test id.
- **Where the live API and the docs disagree, follow the API and say so in a comment at the call site.** The ones found so far: `validatebatch` takes `timeout` as a JSON *string*; the Inbox Placement Tester authenticates with `Authorization: Bearer`, not `api_key`; its listing is `GET /tests`, not `/test`; `send-evaluated-file` takes `file_id` as a form field, not a file upload; the S3 and SFTP jobs require `return_url` and `file_name`, `validateSFTPFile` included; bulk `filestatus` answers with an ISO-8601 `upload_date`; the Warmup `/seed-count` answers with a bare number and `check-domain-dmarc` takes an email address, not a domain; and a failed SMTP login in `test-smtp-configuration` comes back as an HTTP 500.
- **`exportSFTPFile` sends `export_path`**, not the `export_bucket` that `exportS3File` sends.
- **`{"success": false}` on an HTTP 200 is a failure.** The API answers an unknown bulk file id that way. `successMeansFailure` is the check, applied in `jsonIsUsable` and `deliverJson`.
- **`GsonDateDeserializer` must accept every shape the API sends.** It parses the two legacy formats and ISO-8601. A format it does not know throws, and the whole response then comes back as an error with the record's values as the message.
- **`ZeroBounceWarmup` is a separate client for the Warmup API**, a different product with its own host and its own team-scoped key. It is not part of `ZeroBounceSDK`.
- **Only endpoints documented at https://www.zerobounce.net/docs/.** Do not add a method for an endpoint the public docs do not describe. Check before concluding something is undocumented: the S3 and SFTP bulk endpoints live under https://www.zerobounce.net/docs/api-integrations, not the main API reference.
- Match the surrounding style. Do not reformat unrelated code.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

- Add every user-visible change to `## [Unreleased]` in the same commit as the change.
- Group entries under these headings, in this order, omitting empty ones: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.
- Write for SDK users. Leave out CI-only changes, internal refactors, and fixes to code that was never released.
- At 2.y.z, a breaking change bumps the major version, a new method or option bumps the minor version, and fixes alone bump the patch version. A `compileSdk` increase that raises the AAR `minCompileSdk` is not a patch.

## Releasing

Publishing and pushing tags need a maintainer's explicit approval. Do not bump the version, tag, or publish unless they asked.

1. Move `[Unreleased]` entries under `## [x.y.z] - YYYY-MM-DD`, leave an empty `[Unreleased]` section, and update the compare links at the bottom of `CHANGELOG.md`.
2. Set `PUBLISH_VERSION` in `zero_bounce_sdk/build.gradle`. That is the only version field. If it matches a dependency version, update `COORDINATES_ALLOWED_TO_MATCH` in `BuildGradleValidationTest` instead of removing the guard.
3. `./gradlew :zero_bounce_sdk:testDebugUnitTest` and `./gradlew :zero_bounce_sdk:assembleDebug`.
   **Run the live suite by hand too.** The offline suite proves the SDK sends what we think it sends; only the live suite proves the API accepts it. Run it with real keys, including `ZEROBOUNCE_LIVE_SPEND=1` and `ZEROBOUNCE_LIVE_CHANGES=1`, and do not release on a failure.
4. Tag `vx.y.z` on that commit and run the Publish workflow with that tag. It uploads a bundle to the Sonatype Central Portal.
