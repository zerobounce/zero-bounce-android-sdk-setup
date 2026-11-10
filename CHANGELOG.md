# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.3.0] - 2026-11-11

### Added

- `ZeroBounceClient` holds one API key. `ZeroBounceSDK` remains a shared object and must not be reused across keys.
- `validate` accepts `ZBValidateOptions`: `timeout`, `activityData`, `verifyPlus`, `domainInfo`, `greylistProcessing`, `callbackUrl`, and `usePost` (form-encoded `POST /v2/validate`). A `timeout` outside 3–60 seconds is rejected before a request is sent. `callbackUrl` needs `greylistProcessing = true` and is rejected without it, and when blank, before a request is sent.
- `validateBatch` accepts `ZBValidateOptions` too, but sends only `timeout`, `activityData` and `verifyPlus`. `timeout` goes out as a JSON string, because the live API rejects a number with an account-level error. `domainInfo`, `greylistProcessing`, `callbackUrl` and `usePost` are ignored, and not checked, by `validateBatch`, which always sends a JSON `POST /v2/validatebatch`. A `timeout` outside 10–120 seconds is rejected before a request is sent.
- `ZBValidateResponse` reads `active_in_days`, `active_first_seen`, `domain_website_exists`, `domain_registrant_company_name` and `Message`. `Message` is returned instead of a result while a greylist retry is still running.
- `ZBValidateSubStatus.AI_AGENT_MAILBOX` (`ai_agent_mailbox`). See the breaking change under Changed.
- `scoreEmail` (`GET /v2/scoring`) and `scoreEmailPost` (form-encoded `POST /v2/scoring`).
- Bulk email finder, bulk domain search, list evaluator, filters, and S3 bulk (`listS3Files`, `validateS3File`, `scoreS3File`, `exportS3File`, `emailFinderS3File`, `domainSearchS3File`). `has_header_row` is `true` or `false` when set. An S3 email column of `0` is sent when set.
- The bulk email finder (`sendEmailFinderFile`, `sendEmailFinderFileStream`) requires `firstNameColumn` or `fullNameColumn` next to `domainColumn`, as the docs do, and rejects a call without either before sending it. A file sent without a name column has nothing to build an address from.
- The list evaluator's upload, `getListEvaluatorResult` and `sendEvaluatedFile` all use the list evaluator base URL. The result's file id is encoded as a single path segment. `sendEvaluatedFile(fileId)` posts the evaluator's `file_id` as a form field: the endpoint rejects the file upload the docs describe with "file_id: This field is required".
- The filters report an invalid or drained key, which the API answers with an HTTP 200 and `{"Error": ...}`, through the error callback with status 200. A bare `{"Message": ...}`, which is how a filter change answers, is a result.
- SFTP/FTP bulk (`validateSFTPFile`, `scoreSFTPFile`, `emailFinderSFTPFile`, `domainSearchSFTPFile`, `exportSFTPFile`), sharing the new `ZBBulkFileJobOptions`. The server, path and protocol come from your ZeroBounce settings. `exportSFTPFile` takes `export_path` rather than the `export_bucket` `exportS3File` sends.
- All eight S3 and SFTP/FTP file jobs require a file name and a return URL, and reject a call without either before sending it. The docs list both as optional, and `file_name` as optional for `validateSFTPFile`, but the live API rejects a job without them: without a return URL it answers 400 `Provided return URL is not permitted: None`.
- `exportS3File` and `exportSFTPFile` take a nullable destination: `null` uses the export bucket or path from your ZeroBounce settings. A blank file id or a blank destination is rejected before a request is sent.
- Inbox Placement Tester on `https://bulkapi.zerobounce.net/api/ipt`: `getInboxPlacementProviders`, `getInboxPlacementTestTypes`, `createInboxPlacementTest`, `getInboxPlacementTest`, `listInboxPlacementTests`, `deleteInboxPlacementTest`, `getInboxPlacementWebhook`, `setInboxPlacementWebhook`. It authenticates with an `Authorization: Bearer` header, so the key is never put in a query string. Bodies are camelCase JSON and unset fields are left out. A test id is encoded as a single path segment. `listInboxPlacementTests` calls `GET /tests`, takes `createdAfter` and `createdBefore`, and sends the page size as `page_size`: the live API ignores the documented `pageSize`. `deleteInboxPlacementTest` reports the API's `204 No Content` as a success with a `null` response. The documented limits are rejected before a request is sent: a test name over 64 characters, a `testType` other than 1, 2 or 3, a type 1 test without a `senderEmail`, and a `pageSize` outside 1-100.
- `setListEvaluatorBaseUrl` and `setInboxPlacementBaseUrl`. The list evaluator and the Inbox Placement Tester are separate hosts, not paths built from the bulk base, so `initialize` alone does not change them.
- `ZeroBounceWarmup`, a client for the Warmup API. It is a separate product with its own host (`https://warmup-api.zerobounce.net`), its own team-scoped key sent in an `API-Key` header, and all 26 documented endpoints, including the seed-list and statistics CSV downloads.
- `ErrorResponse.statusCode` carries the HTTP status of the response that failed, including an HTTP 200 whose body is an error, so callers can tell a Cloudflare 403 from an API 400/404 and a Warmup 429. It is 0 only when there was no response: the call was rejected before it was sent, the connection failed, or saving a downloaded file failed.
- An opt-in live API suite (`LiveApiTest`). The default test task excludes it unless `-PzbLive` is passed, so `gradle test` and CI never run it; it needs keys in the environment, and only spends credits or changes the account behind `ZEROBOUNCE_LIVE_SPEND=1` and `ZEROBOUNCE_LIVE_CHANGES=1`.

### Changed

- **Breaking:** `ZBValidateResponse` and `ZBGetApiUsageResponse` have new properties in the middle of their primary constructors: `activeInDays` and `activeFirstSeen` after `domainAgeDays`, and `domainWebsiteExists`, `domainRegistrantCompanyName` and `message` before `error`, in `ZBValidateResponse`; `subStatusLeadingPeriodRemoved` and `subStatusAliasAddress` before `startDate` in `ZBGetApiUsageResponse`. Calling their constructors or `copy` with positional arguments, or destructuring them, either no longer compiles or binds different properties. Code compiled against 2.2.0 that calls those constructors with arguments, `copy`, or a moved `componentN` fails with `NoSuchMethodError` or reads a different property. Named arguments and property access are unaffected.
- **Breaking:** `ErrorResponse` has a third constructor parameter, `statusCode`. Source that constructs it positionally still compiles, but its constructor and `copy` signatures changed, so code compiled against 2.2.0 that calls its constructor with arguments, or `copy`, fails with `NoSuchMethodError`.
- **Breaking:** `ZBValidateSubStatus` has a new constant, `AI_AGENT_MAILBOX`. A Kotlin `when` over it that lists every constant and has no `else` branch no longer compiles.
- The HTTP client no longer follows redirects. A 3xx answer reaches the error callback with its status, so a redirect cannot carry the API key in the query string to another host.
- `getFile` and `scoringGetFile` no longer require `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`. They write to the app's own external files directory, which needs neither.
- `minSdk` stays 21, so the range of devices the SDK runs on is unchanged. The AAR's `minCompileSdk` of 37 is unchanged as well; it shipped in 2.2.0, not here.

### Fixed

- A base URL passed to `initialize` with a trailing slash produced request URLs with a double slash. Every `ZBConstants` value ends in `/v2/`, so `initialize(apiKey, ZBConstants.API_EU_URL)`, as the README shows, sent `validate` to `https://api-eu.zerobounce.net/v2//validate`. This was so in 2.2.0 and in every release since `ZBConstants` was added in 1.3.0, for `API_DEFAULT_URL` and `API_USA_URL` too, and for any custom base URL ending in `/`. It affected every call on the API base URL: in 2.2.0 `validate`, `validateBatch`, `guessFormat`, `findEmail`, `findDomain`, `getApiUsage`, `getCredits` and `getActivityData`. `initialize` (and `ZeroBounceClient.initialize`) now removes trailing slashes, so those calls, and the new scoring and filter calls, go to the documented path (`https://api-eu.zerobounce.net/v2/validate`). Calling `initialize` without a base URL was not affected, and neither were the bulk file calls (`sendFile`, `fileStatus`, `getFile`, `deleteFile` and their `scoring` versions), which use a separate bulk base URL that `initialize` does not change.
- The published POM's `scm` section is valid. 2.2.0's pointed `url` at `https://github.com/zerobounce/zero-bounce-android-sdk-setup/tree/main`, a branch that does not exist, and had a malformed `developerConnection` (`scm:git:ssh:/github.com/...`) and a `connection` without a protocol. They are now `https://github.com/zerobounce/zero-bounce-android-sdk-setup`, `scm:git:ssh://git@github.com/zerobounce/zero-bounce-android-sdk-setup.git` and `scm:git:https://github.com/zerobounce/zero-bounce-android-sdk-setup.git`.
- An invalid or drained API key was a silent success. The API answers one with an HTTP 200 and no `success` field: `{"error": "Invalid API key or your account ran out of credits"}` from `validate` and `getActivityData`, `{"error": "Invalid API key"}` from `getApiUsage`, and `{"Message": ...}` from `findEmail`, `findDomain` and `guessFormat`. Each was parsed into an empty response, with the message at most on an `error` field nobody had to read, and passed to the response callback. Each now reaches the error callback with the API's own message and status 200. A bare `Message` is treated as a failure only for the email finder and domain search, whose results never consist of one. `getCredits` still returns `-1` for an invalid key, as documented.
- The api-usage response now reads `sub_status_leading_period_removed` and `sub_status_alias_address`, two counters the API returns that were silently discarded.
- `fileStatus` and `scoringFileStatus` work against the live API. It answers with an ISO-8601 `upload_date` (`2026-10-24T05:24:13Z`), which `GsonDateDeserializer` could not parse, so the whole response was reported as an error carrying the record's field values as its message. ISO-8601 dates with or without fractional seconds, and with `Z`, a numeric offset or no offset, now parse.
- `{"success": false}` on an HTTP 200 reaches the error callback instead of the success one. The API answers an unknown bulk file id that way.
- Query parameters are percent-encoded. 2.2.0 put the API key, the email address, the file id, names and the domain into the URL as they were, so a `+` in an address arrived as a space, and an `&` or `#` cut the query short.
- A large HTML "Access denied" page is a short error. An HTML page with status 200 is an error as well, including from `getFile` and `scoringGetFile`, which used to save it as the results file. They also report a JSON error body as an error when it is not labelled `application/json`, instead of saving it.
- Empty and non-JSON bodies are a short error. An empty HTTP 200 body used to reach the response callback as `null`, and a non-JSON one reached the error callback as its raw text. A repeated `Error:` / `[Error]:` prefix is removed.
- A Cloudflare JSON error that has `"cloudflare_error": true` and an "Access denied" or 1020 `title`, as Cloudflare's 1020 block does, is reported as that title, "Error 1020: Access denied", not the raw body. Without `cloudflare_error`, the title is used only when no key contains `error` or `message`; otherwise the body is parsed like any other JSON error.
- `validateBatch` reports an account-level failure (HTTP 200, error for address `all`) through the error callback, with status 200. An error for one address stays on the successful result.
- A blank API key, and a blank or whitespace-only email address or file id, are rejected through the error callback before a request is sent (`Empty parameter: email`, `Empty parameter: file_id`). The email check covers `validate` and `getActivityData`, and the new `scoreEmail` and `scoreEmailPost`. The file id check covers `fileStatus`, `getFile`, `deleteFile` and their `scoring` versions, and every new method that takes a file id.
- Apps with `minSdk` 21 or 22 can use the SDK again. Since 2.2.0 it depended on `androidx.appcompat:appcompat:1.8.0`, which it never used and which declares `minSdkVersion` 23, so those apps failed manifest merging ("minSdkVersion 21 cannot be smaller than version 23"); the dependency is removed.

### Security

- Error callbacks no longer include the raw HTML access-denied page.
- `getFile` and `scoringGetFile` keep only the last path component of the file name in the server's `Content-Disposition` header. 2.2.0 joined that name to the app's files directory as given, so a name containing `../` could write outside it.
- A plain `http://` base URL is accepted only when its host is `localhost`, `127.0.0.1` or `::1`. 2.2.0 checked only the prefix, so a URL such as `http://localhost.example.com` was accepted.
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

[Unreleased]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.3.0...HEAD
[2.3.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.4...v2.2.0
[2.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.1...v2.1.2
[2.0.17]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.0.0...v2.0.17
[1.5.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.5.0...v1.5.1
[1.2.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.2.0...v1.2.1
[1.1.5]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.4...v1.1.5
[1.1.3]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.1...v1.1.2
[1.0.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/releases/tag/v1.0.0
