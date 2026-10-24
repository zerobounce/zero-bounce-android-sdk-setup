# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `ZeroBounceClient` holds one API key. `ZeroBounceSDK` remains a shared object and must not be reused across keys.
- `validate` accepts `ZBValidateOptions`: `timeout`, `activityData`, `verifyPlus`, and `usePost` (form-encoded `POST /v2/validate`).
- `validateBatch` accepts the same options on `POST /v2/validatebatch`. A `timeout` outside 3–60 seconds (validate) or 10–120 seconds (batch) is rejected before a request is sent.
- `ZBValidateResponse` reads `active_in_days` and `active_first_seen`.
- `scoreEmail` (`GET /v2/scoring`) and a form-encoded `POST /v2/scoring`.
- Bulk email finder, bulk domain search, list evaluator, filters, and S3 bulk (`listS3Files`, `validateS3File`, `scoreS3File`, `exportS3File`, `emailFinderS3File`, `domainSearchS3File`). `has_header_row` is `true` or `false` when set. An S3 email column of `0` is sent when set.
- SFTP/FTP bulk (`validateSFTPFile`, `scoreSFTPFile`, `emailFinderSFTPFile`, `domainSearchSFTPFile`, `exportSFTPFile`), sharing the new `ZBBulkFileJobOptions`. The server, path and protocol come from your ZeroBounce settings. `validateSFTPFile`'s `fileName` is optional; `exportSFTPFile` takes `export_path` rather than the `export_bucket` `exportS3File` sends.
- `ZBValidateOptions` accepts `domainInfo`, `greylistProcessing` and `callbackUrl`. `callbackUrl` needs `greylistProcessing = true` and is rejected without it, and when blank, before a request is sent.
- `ZBValidateResponse` reads `domain_website_exists`, `domain_registrant_company_name` and `Message`. `Message` is returned instead of a result while a greylist retry is still running.
- Inbox Placement Tester on `https://bulkapi.zerobounce.net/api/ipt`: `getInboxPlacementProviders`, `getInboxPlacementTestTypes`, `createInboxPlacementTest`, `getInboxPlacementTest`, `listInboxPlacementTests`, `deleteInboxPlacementTest`, `getInboxPlacementWebhook`, `setInboxPlacementWebhook`. The API key goes on the query string, bodies are camelCase JSON, and unset fields are left out.
- `setListEvaluatorBaseUrl` and `setInboxPlacementBaseUrl`. The list evaluator and the Inbox Placement Tester are separate hosts, not paths built from the bulk base, so `initialize` alone does not change them.
- `ZeroBounceWarmup`, a client for the Warmup API. It is a separate product with its own host (`https://warmup-api.zerobounce.net`), its own team-scoped key sent in an `API-Key` header, and all 26 documented endpoints, including the seed-list and statistics CSV downloads.
- `ErrorResponse.statusCode` carries the HTTP status of a failed response, so callers can tell a Cloudflare 403 from an API 400/404 and a Warmup 429.
- An opt-in live API suite (`LiveApiTest`). The default test task excludes it unless `-PzbLive` is passed, so `gradle test` and CI never run it; it needs keys in the environment, and only spends credits or changes the account behind `ZEROBOUNCE_LIVE_SPEND=1` and `ZEROBOUNCE_LIVE_CHANGES=1`.

### Changed

- The list evaluator uses its own base URL instead of building the path from the bulk base. `sendEvaluatedFile` stays on the bulk base.
- `validateBatch` sends `timeout` as a JSON string. Sent as a number, the live API rejects the whole batch with an account-level error.
- The Inbox Placement Tester authenticates with an `Authorization: Bearer` header instead of an `api_key` parameter, so the key no longer reaches a query string that gets logged, and `listInboxPlacementTests` calls `GET /tests`. `GET /test` answers `Method "GET" not allowed`.
- `sendEvaluatedFile(fileId)` posts the list evaluator's `file_id` as a form field. The endpoint rejects a file upload with "file_id: This field is required", so it no longer takes a `File`.
- The S3 and SFTP/FTP jobs require `file_name` and `return_url`, `validateSFTPFile` included, although the docs list `file_name` as optional for that one.
- `exportS3File`'s `exportBucket` is nullable, matching the documented endpoint: `null` uses the export bucket from your ZeroBounce settings. A blank string is still rejected.

### Fixed

- `fileStatus` and `scoringFileStatus` work against the live API. It answers with an ISO-8601 `upload_date` (`2026-10-24T05:24:13Z`), which `GsonDateDeserializer` could not parse, so the whole response was reported as an error carrying the record's field values as its message.
- `{"success": false}` on an HTTP 200 reaches the error callback instead of the success one. The API answers an unknown bulk file id that way.
- A file id and an Inbox Placement test id are encoded as a single path segment. They previously went through the form encoder, which turns a space into `+` — correct in a query string, but a literal plus in a path.
- A large HTML "Access denied" page is a short error. An HTML page with status 200 is an error as well, including `getFile`, `emailFinderGetFile`, and `domainSearchGetFile`, instead of a saved file.
- Empty and non-JSON bodies are a short error. The message is no longer a parser exception, and a repeated `Error:` / `[Error]:` prefix is removed.
- A Cloudflare JSON 403 (`error_code` 1020) is the title "Error 1020: Access denied", not the raw body.
- `validateBatch` reports an account-level failure (HTTP 200, error for address `all`) through the error callback. An error for one address stays on the successful result.
- A blank API key, a blank email, and a blank file id are rejected before a request is sent.

### Security

- Error callbacks no longer include the raw HTML access-denied page.
- A Warmup API key with surrounding whitespace or control characters is rejected, because the transport would otherwise quote it unredacted in its own error, and a Warmup base URL must be `https://`.
- The Warmup SMTP test masks the username and password out of every error it raises.

## [2.2.0] - 2026-09-04

### Changed

- The published AAR is built with `compileSdk` 37. `minSdk` stays 21.
- Publishing uploads a bundle to the Sonatype Central Portal.
- A custom API base URL must be `https://`, except `http://localhost` in tests.
- HTTP calls time out (connect 30 seconds, read and write 120 seconds).

## [2.1.2] - 2026-06-09

### Added

- `ZBValidateResponse.catchallDomain`, read from `catchall_domain`.

## [2.0.17] - 2026-03-03

### Changed

- License the project under MIT.

## [1.5.1] - 2026-02-18

### Added

- `ZBValidateSubStatus.NONE` and `sub_status_accept_all`, aligned with the C# SDK.

## [1.2.1] - 2025-07-21

### Fixed

- `sendFile` rejects an email column index below 1.

### Changed

- Publishing uses the Maven Central portal.

## [1.1.5] - 2023-08-24

### Added

- Email finder.

## [1.1.3] - 2023-05-04

### Added

- `validateBatch` includes the per-address `errors` list.

## [1.1.2] - 2023-04-05

### Added

- `validateBatch`.

## [1.0.0] - 2023-03-22

### Added

- First tagged release: validation, credits, usage, bulk file, and AI scoring.

[Unreleased]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.2.0...HEAD
[2.2.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.4...v2.2.0
[2.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.1...v2.1.2
[2.0.17]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.0.0...v2.0.17
[1.5.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.5.0...v1.5.1
[1.2.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.2.0...v1.2.1
[1.1.5]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.4...v1.1.5
[1.1.3]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.1...v1.1.2
[1.0.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/releases/tag/v1.0.0
