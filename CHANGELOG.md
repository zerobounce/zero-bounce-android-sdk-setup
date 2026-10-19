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
- Bulk email finder, bulk domain search, list evaluator, filters, and S3 bulk (`listS3Files`, `validateS3File`, `scoreS3File`, `exportS3File`). `has_header_row` is `true` or `false` when set. An S3 email column of `0` is sent when set.

### Fixed

- A large HTML "Access denied" page is a short error. An HTML page with status 200 is an error as well, including `getFile`, `emailFinderGetFile`, and `domainSearchGetFile`, instead of a saved file.
- Empty and non-JSON bodies are a short error. The message is no longer a parser exception, and a repeated `Error:` / `[Error]:` prefix is removed.
- A Cloudflare JSON 403 (`error_code` 1020) is the title "Error 1020: Access denied", not the raw body.
- `validateBatch` reports an account-level failure (HTTP 200, error for address `all`) through the error callback. An error for one address stays on the successful result.
- A blank API key, a blank email, and a blank file id are rejected before a request is sent.

### Security

- Error callbacks no longer include the raw HTML access-denied page.

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
