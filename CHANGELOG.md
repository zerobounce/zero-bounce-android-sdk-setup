# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Deprecated

- `ZBWarmupEmailContent.industry` (not an API field; not sent), `ZBWarmupEmailContent.website` (use `companyWebsite`), `ZBWarmupManagedSending.dailyLimit` (the API does not apply a daily limit; not sent) and `ZBWarmupManagedSending.timezoneOffset` (whole hours only; use `timezoneOffsetHours`). They still compile and work as described under Fixed, with a deprecation warning.

### Fixed

- `ZeroBounceWarmup.addSendingAccount` sends the documented ZeroBounce Managed body (https://www.zerobounce.net/docs/warmup-api/adding-a-sending-account), as the Ruby, Python, C, iOS and Pascal SDKs do: `email_content` carries `company_name`, `company_website`, `company_description`, `types_of_emails` and `example_email`, and `managed_sending` carries `weekdays_only`, `timezone`, `timezone_offset` and `warmup_start_date`. Up to 3.0.1 `ZBWarmupEmailContent` and `ZBWarmupManagedSending` could not set `company_website`, `company_description`, `example_email`, `weekdays_only` or `timezone`, and sent `industry`, `website` and `daily_limit`, which the API does not read. New properties (added after the existing ones, with defaults): `ZBWarmupEmailContent.companyWebsite`, `companyDescription`, `exampleEmail`, and `ZBWarmupManagedSending.weekdaysOnly`, `timezone` and `timezoneOffsetHours` (a `Double?`, -11.0 to 14.0, e.g. `5.5`). A `website` is now sent as `company_website` (unless `companyWebsite` is set too); `industry` and `dailyLimit` are no longer sent. The whole-hour `timezoneOffset` still works and goes out as `timezone_offset` (`-5` as `-5.0`); setting both it and `timezoneOffsetHours` is an error. NaN, infinities and anything outside -11 to 14 reach the error callback as "timezone_offset must be -11 to 14" before a request is sent. Calls that compiled against 3.0.1 still compile, from Kotlin (named or positional arguments) and from Java (the primary constructors are `@JvmOverloads`, so the 3.0.1 constructor signatures are still there). Kotlin code compiled against 3.0.1 that calls `copy` or relies on default arguments must be recompiled.
- `ZeroBounceWarmup.downloadSeeds` and `downloadStatistics` report an HTML page answered with HTTP 200 (a proxy's or Cloudflare's block page) as an error with status 200, "ZeroBounce returned an HTML error page ("<title>") instead of an API response.", with the API key redacted from the title, as the C#, JavaScript, Ruby and Python SDKs do. Up to 3.0.1 the page reached the success callback as if it were the CSV. A body is taken for HTML when its content type is `text/html` or its first 2000 characters contain `<!doctype html`, `<html`, `<head` or `<body`.
- The Inbox Placement Tester treats an empty 2xx as a success only on `deleteInboxPlacementTest`, where the API answers 204 No Content: the response callback gets `null`. Up to 3.0.1 an empty 204 was a success with `null` on every Inbox Placement call (`getInboxPlacementProviders`, `getInboxPlacementTestTypes`, `createInboxPlacementTest`, `getInboxPlacementTest`, `listInboxPlacementTests`, `getInboxPlacementWebhook`, `setInboxPlacementWebhook`), while an empty 200 or 202 to `deleteInboxPlacementTest` reached the error callback. **Behaviour change:** an empty 2xx to any Inbox Placement call other than the delete now reaches the error callback with its status ("Empty response from ZeroBounce (HTTP 204)"), and an empty 200 or 202 to the delete is a success. An empty non-2xx is an error with its status, as before. This is the rule of the C#, Rust, Python, PHP and iOS SDKs. Both `ZeroBounceSDK` and `ZeroBounceClient` are covered.
- Repository only: a test makes every public request method of `ZeroBounceClient` whose name mentions `InboxPlacement`, found by reflection, answer an empty 200, 202 and 204 (and 404 and 500) and checks the rule above in both directions.

- A response body cut short (fewer bytes than its `Content-Length`, or the connection closed mid-body) reaches the error callback with status 0. In 3.0.1 OkHttp threw while the SDK read the body inside its response callback, where OkHttp only logs an exception, so neither callback ran: every call that reads a JSON body (every `ZeroBounceSDK` and `ZeroBounceClient` call except the file downloads) and every `ZeroBounceWarmup` call waited forever. The downloads already reported it and deleted the partial file.
- A transport error never carries the API key. In 3.0.1 the error callback got the exception's message as it was, so a message that quoted the request URL, or the key itself, passed it on (the key was redacted only in debug logs). The key is now replaced with `***` wherever it appears, whether the request carried it in the URL, a form or multipart field, the JSON body or the Inbox Placement Tester's `Authorization` header.
- `initialize(apiKey, apiBaseUrl)` changes nothing when it throws `IllegalArgumentException` for a base URL that is not https. In 3.0.1 it stored the new key and client first, so a caller that caught the exception sent the new key to the old base URL.
- Repository only: tests send every public request method of `ZeroBounceClient` and `ZeroBounceWarmup`, found by reflection, to a real HTTP server on a loopback socket and fail if a body cut short does not reach the error callback with status 0 (or a download leaves a partial file), if a 301, 302, 303, 307 or 308 with a `Location` is followed or not reported with its status, if any request goes out on a client without connect, read and write timeouts or one that follows redirects, or if a transport error carries the key. The request-method list is shared with the HTTPS sweep and covers `ZeroBounceWarmup` too.

- Gson is published as a compile (`api`) dependency. Since 2.3.0 the public API has taken `(JsonElement?) -> Unit` callbacks (the Inbox Placement Tester and filter calls, `getListEvaluatorResult`, `listS3Files` and the `ZeroBounceWarmup` JSON calls) and `GsonDateDeserializer` is a public Gson `JsonDeserializer`, but 2.3.0, 3.0.0 and 3.0.1 listed Gson with `runtime` scope in the POM (and only in the runtime variant of the Gradle module metadata), so an app that did not declare Gson itself could not compile those calls: Kotlin reported "Cannot access class 'com.google.gson.JsonElement'" and Java "cannot access JsonElement" for `ZeroBounceSDK.getInboxPlacementProviders(...)`. OkHttp stays a runtime dependency: no public declaration uses an OkHttp type.
- Repository only: the minSdk consumer app built by `ci/check_min_sdk_consumer.sh` now also compiles a call whose callback takes a `JsonElement`, without declaring Gson, so it fails if Gson leaves the published compile scope. A test sets each base-URL entry point of `ZeroBounceClient` and `ZeroBounceWarmup` (`initialize` with a base URL, `setListEvaluatorBaseUrl`, `setInboxPlacementBaseUrl`, found by reflection and checked against `ZeroBounceSDK`) to a URL ending in `///` and fails if any request of either client has an empty path segment such as `/v2//validate`; another fails unless `setInboxPlacementBaseUrl` and `setListEvaluatorBaseUrl` refuse an http URL and keep the https one set before.
- A results download (`getFile`, `scoringGetFile`, `emailFinderGetFile`, `domainSearchGetFile`) that fails part-way no longer destroys a file already at its target. In 3.0.1 the file was opened for writing (truncating it) before the body was read, and deleted when the read failed, so a dropped connection lost the previous results. The body is now written to a temporary file in the same directory and moved over the target only when it is complete.
- Repository only: tests now pin the default timeouts of both clients (connect 30 s, read and write 120 s; OkHttp's own 10 s passed the old non-zero check), check that every download, Warmup's CSV downloads included, refuses an empty 200 and a JSON content type, that `remove_duplicate` is left out of all four `sendFile`/`scoringSendFile` uploads unless set, and that `testSmtpConfiguration` and `changeFromAddress` send exactly their documented fields.
- A boolean field that holds something else is quoted in the error cut at 40 characters without splitting a character: a cut through a surrogate pair (an emoji) left half of it in the message.

### Security

- The API key is redacted (`***`) from every error message built from a response body, as it is from transport errors (above) and was from Warmup errors, and as the C#, JavaScript and Python SDKs do. In 3.0.1 every `ZeroBounceClient` and `ZeroBounceSDK` request method passed the body's text to the error callback unchanged, so a server that quoted the request back put the key in front of the caller: a `400` `Bad request: https://api.zerobounce.net/v2/getcredits?api_key=<key>` reached the error callback as is, and so did the key in an HTTP 200 `{"error": "Invalid API key <key>"}`. This covered the JSON calls, the bulk and email finder / domain search downloads, the multipart uploads, the filters, the S3 and SFTP jobs and the Inbox Placement Tester, and the account-level `validateBatch` error. The key each request carried is now replaced as sent, URL-encoded and JSON-escaped; `ZeroBounceWarmup` replaces those forms of its key and of the SMTP password too (it replaced only the key and password as sent).
- Repository only: tests send every public request method of `ZeroBounceWarmup`, found by reflection, to a fake server that answers HTTP 200 with a body Warmup must refuse and that quotes the key and the request (text that is not JSON to the JSON calls, a JSON error to the CSV downloads, and an HTML page whose title holds the key), and fail unless each call reports one error with status 200 that carries neither the key nor the SMTP password. The redaction of messages built from a 200 body had no test before.
- Repository only: a test sends every public request method of `ZeroBounceClient` and `ZeroBounceWarmup`, found by reflection, to a fake server whose error bodies (a plain-text 400, a JSON 400 and an HTTP 200 carrying an error) quote the request URL, the `Authorization` and `API-Key` headers, the body and the key, and fails if the key or the Warmup SMTP password reaches any error callback in any form; another checks the account-level batch error.
- The key and the SMTP password are redacted in every JSON-escaped spelling, including without Gson's HTML escapes and with `/` written as `\/` (PHP's `json_encode`), and a transport error is redacted with the same rules as an error body: it masked only the key as written and URL-encoded. Tests use a key and a password containing `"`, `/`, `+` and `%`, quoted by an error body, a transport error and a body that fails while it is read (Warmup included).

## [3.0.1] - 2026-11-11

### Changed

- Repository only: the HTTPS test sweeps every public request method of `ZeroBounceClient`, found and invoked by reflection, instead of 22 chosen calls, and fails if a public request method is missing from the sweep or if `ZeroBounceSDK` and `ZeroBounceClient` offer different request methods.

## [3.0.0] - 2026-11-12

### Breaking

- An exception thrown by your own response or error callback now propagates instead of being reported through the error callback. Callbacks run on OkHttp's dispatcher thread, so the exception reaches that thread's uncaught-exception handler, which on Android ends the app by default. In 2.3.0 an exception thrown by the response callback of any call that decodes a JSON response (every call except the file downloads), and of the `ZeroBounceWarmup` JSON calls, was caught and handed to the error callback as if the request had failed, and an exception thrown by the error callback for a `validateBatch` account-level failure made the SDK call the error callback a second time. The error callback now receives only failed requests: transport failures, error responses, and bodies the SDK cannot read.

#### Upgrading from 2.3

- If a response or error callback can throw, catch the exception inside the callback.

### Added

- The library manifest declares `android.permission.INTERNET`, so the manifest merger adds it to your app. Every SDK call needs it.

### Changed

- `initialize` (and `ZeroBounceClient.initialize`) refuses an API key with surrounding whitespace or control characters, such as a key read from a file with its trailing newline, as `ZeroBounceWarmup.initialize` already did. The key is not stored or sent, and every call reports "api_key has surrounding whitespace or control characters" through its error callback, with status 0, until `initialize` is called with a clean key. In 2.3.0 such a key was sent as given. A key with spaces inside it is unchanged.
- Booleans in responses (`freeEmail`, `mxFound`, `catchallDomain`, `found`, `success`) are read strictly: `true`/`false`, the strings `"true"`/`"false"` in any case, and `1`/`0` as a number or a string. In 2.3.0 the strings `"yes"` and `"1"` were read as `false`, and a bare `1` or `0` reached the error callback with every value of the response as its message. Any other value now reaches the error callback with a short message naming the field, such as `ZeroBounce returned "yes" for free_email, which is not a boolean.`

### Fixed

- In an app minified with R8 or ProGuard, every response field without `@SerializedName`, such as `ZBValidateResponse.address`, `status` and `domain`, decoded as `null`, `false` or `0`, because minification renamed the fields Gson matches by name. The AAR now ships consumer rules (`consumer-rules.pro`, through `consumerProguardFiles`) that keep the response models, `DomainFormat` and `ZBValidateBatchData` intact. Checked by running R8 9.2.1 (the version AGP 9.2.1 bundles) over the SDK classes and decoding a validate response with the SDK's Gson settings: without the rules `address`, `status` and `domain` came back `null`; with them every field decoded.
- `getFile`, `scoringGetFile`, `emailFinderGetFile` and `domainSearchGetFile` saved a JSON error body whose only message was under `Error`, `Message` or `detail` as the results file when it did not come as `application/json`, and handed it to the response callback. Those bodies are now errors with the HTTP status, whatever the content type, and no file is written. A body that is a JSON object with a `success` field, or with an `error`, `message`, `error_message`, `Error`, `Message` or `detail` field, is an error, as in the C++ and Rust SDKs; a CSV is saved as before. An error body with no message, `{"success": false}`, now carries one instead of an empty error list (from all four calls, on an HTTP 200 or 400).

## [2.3.0] - 2026-11-11

### Breaking

The details are under Changed and Fixed below.

#### Upgrading from 2.2

- Errors you used to receive as successes now arrive in the error callback: an invalid or drained API key (HTTP 200 with an error body), `{"success": false}`, an empty or non-JSON body, an HTML page, and a `validateBatch` account-level failure (error for address `all`). Move that handling from the response callback to the error callback. `getCredits` still returns `-1` for an invalid key.
- Recompile if you construct or destructure `ZBValidateResponse`, `ZBGetApiUsageResponse` or `ErrorResponse` positionally, or call their `copy`: new constructor parameters were inserted. Named arguments and property access are unaffected.
- A Kotlin `when` over `ZBValidateSubStatus` with no `else` branch needs a branch for `AI_AGENT_MAILBOX`.
- Redirects are no longer followed; a 3xx reaches the error callback with its status.
- A blank API key, email or file id is rejected through the error callback before a request is sent.

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

[Unreleased]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v3.0.1...HEAD
[3.0.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.3.0...v3.0.0
[2.3.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.4...v2.2.0
[2.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.1.1...v2.1.2
[2.0.17]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v2.0.0...v2.0.17
[1.5.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.5.0...v1.5.1
[1.2.1]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.6...v1.2.1
[1.1.5]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.4...v1.1.5
[1.1.3]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/compare/v1.1.1...v1.1.2
[1.0.0]: https://github.com/zerobounce/zero-bounce-android-sdk-setup/releases/tag/v1.0.0
