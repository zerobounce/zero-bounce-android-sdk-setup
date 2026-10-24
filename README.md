## ZeroBounce Android SDK


## Security

- Keep API keys on a trusted server. Do not embed them in mobile apps or browser JavaScript that untrusted users can inspect.
- Custom API base URLs (when supported) must use `https://`. Do not pass end-user-controlled hosts into those settings.
- Request URLs include `api_key` as a query parameter (ZeroBounce API contract). Do not log full request URLs or enable payload debug logging in production.

[![Maven Central](https://img.shields.io/maven-central/v/com.zerobounce.android/zerobouncesdk?label=Latest%20release)](https://mvnrepository.com/artifact/com.zerobounce.android/zerobouncesdk)

This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/). \
This SDK is built using the Java 11 version.

### Installation
You can install ZeroBounceSDK by adding the dependency to your gradle file:

```gradle
implementation 'com.zerobounce.android:zerobouncesdk:<latest_version>'
```

## USAGE
Import the sdk in your file:
```kotlin
import com.zerobounce.android.ZeroBounceSDK
```

Initialize the SDK with your API key. You can optionally specify a base URL to use a different API region or a custom endpoint:

**Default**: Uses the default ZeroBounce API endpoint
```kotlin
ZeroBounceSDK.initialize("<YOUR_API_KEY>")
```

**Using predefined API regions**: Use one of the available API regions
```kotlin
import com.zerobounce.android.ZBConstants

// Use USA region
ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = ZBConstants.API_USA_URL)

// Use EU region
ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = ZBConstants.API_EU_URL)

// Use default region (explicit)
ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = ZBConstants.API_DEFAULT_URL)
```

**Using a custom URL string**: Provide your own base URL
```kotlin
ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = "https://custom-api.example.com/v2")
```

**Available API regions:**
- `ZBConstants.API_DEFAULT_URL` - Default ZeroBounce API (https://api.zerobounce.net/v2/)
- `ZBConstants.API_USA_URL` - USA region API (https://api-us.zerobounce.net/v2/)
- `ZBConstants.API_EU_URL` - EU region API (https://api-eu.zerobounce.net/v2/)

## Validation statuses and sub-statuses

The `validate` and `validateBatch` responses include `status` ([ZBValidateStatus](https://github.com/zerobounce/zero-bounce-android-sdk-setup/blob/main/zero_bounce_sdk/src/main/java/com/zerobounce/android/ZBValidateStatus.kt)) and `subStatus` ([ZBValidateSubStatus](https://github.com/zerobounce/zero-bounce-android-sdk-setup/blob/main/zero_bounce_sdk/src/main/java/com/zerobounce/android/ZBValidateSubStatus.kt)).

**ZBValidateStatus** values: `NONE`, `VALID`, `INVALID`, `CATCH_ALL`, `UNKNOWN`, `SPAMTRAP`, `ABUSE`, `DO_NOT_MAIL`

**ZBValidateSubStatus** values (examples): `ANTISPAM_SYSTEM`, `GREYLISTED`, `MAIL_SERVER_TEMPORARY_ERROR`, `FORCIBLE_DISCONNECT`, `MAIL_SERVER_DID_NOT_RESPOND`, `TIMEOUT_EXCEEDED`, `FAILED_SMTP_CONNECTION`, `MAILBOX_QUOTA_EXCEEDED`, `EXCEPTION_OCCURRED`, `POSSIBLE_TRAP`, `ROLE_BASED`, `GLOBAL_SUPPRESSION`, `MAILBOX_NOT_FOUND`, `NO_DNS_ENTRIES`, `FAILED_SYNTAX_CHECK`, `POSSIBLE_TYPO`, `UNROUTABLE_IP_ADDRESS`, `LEADING_PERIOD_REMOVED`, `DOES_NOT_ACCEPT_MAIL`, `ALIAS_ADDRESS`, `ROLE_BASED_CATCH_ALL`, `DISPOSABLE`, `TOXIC`, `ALTERNATE`, `MX_FORWARD`, `BLOCKED`, `ALLOWED`, `ACCEPT_ALL`, `ROLE_BASED_ACCEPT_ALL`, `GOLD`

## Examples
Then you can use any of the SDK methods, for example:

* ##### Validate an email address
    ```kotlin
    ZeroBounceSDK.validate(
        "<EMAIL_TO_TEST>",
        "<OPTIONAL_IP_ADDRESS>",
        { rsp ->
            Log.d("MainActivity", "validate rsp: $rsp")
            // your implementation
        },
        { error ->
            Log.e("MainActivity", "validate error: $error")
            // your implementation
        }
    )
    ```

* ##### Validate batch a list of email addresses
    ```kotlin
    val emailsData = listOf(
        ZBValidateBatchData(email = "valid@example.com", ip = "1.1.1.1"),
        ZBValidateBatchData(email = "invalid@example.com", ip = "1.1.1.1"),
        ZBValidateBatchData(email = "disposable@example.com", ip = null)
    )
    ZeroBounceSDK.validateBatch(
        emailsData,
        { rsp ->
            Log.d("MainActivity", "validateBatch rsp: $rsp")
            // your implementation
        },
        { error ->
            Log.e("MainActivity", "validateBatch error: $error")
            // your implementation
        }
    )
    ```

* ##### Find the email and other domain formats based on a given domain name
    ```kotlin
    ZeroBounceSDK.guessFormat(
        domain = "<DOMAIN_TO_TEST>",
        responseCallback = { rsp ->
            Log.d("MainActivity", "guessFormat rsp: $rsp")
            // your implementation
        },
        errorCallback = { error ->
            Log.e("MainActivity", "guessFormat error: $error")
            // your implementation
        }
    )
    ```

* ##### Check how many credits you have left on your account
    ```kotlin
    ZeroBounceSDK.getCredits(
        { rsp -> 
            Log.d("MainActivity", "getCredits rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "getCredits error: $error") 
            // your implementation
        }
    )
    ```

* ##### Check your API usage for a given period of time
    ```kotlin
    // import java.time.LocalDate
    val startDate = LocalDate.now()    // The start date of when you want to view API usage
    val endDate = LocalDate.now()      // The end date of when you want to view API usage

    ZeroBounceSDK.getApiUsage(
        startDate, 
        endDate,
        { rsp -> 
            Log.d("MainActivity", "getApiUsage rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "getApiUsage error: $error") 
            // your implementation
        }
    )
    ```

* ##### The *sendFile* API allows user to send a file for bulk email validation
    ```kotlin
    // import java.io.File
    val myFile = File("<FILE_PATH>")  // The csv or txt file
    val emailAddressColumn = 3        // The column index of email address in the file. Index starts at 1
    val returnUrl = "https://domain.com/called/after/processing/request"
    val firstNameColumn = 4           // The column index of first name in the file
    val lastNameColumn = 5            // The column index of last name in the file
    val genderColumn = 6              // The column index of gender in the file
    val ipAddressColumn = 7           // The column index of IP address in the file
    val hasHeaderRow = true           // If this is `true` the first row is considered as table headers
    val removeDuplicate = true        // If you want the system to remove duplicate emails
    val allowPhase2 = true            // optional: API `allow_phase_2` — Verify+ and catch-all rules apply

    ZeroBounceSDK.sendFile(
        context,
        myFile,
        emailAddressColumn,
        firstNameColumn,
        lastNameColumn,
        genderColumn,
        ipAddressColumn,
        returnUrl,
        hasHeaderRow,
        removeDuplicate,
        allowPhase2,
        { rsp ->
            Log.d("MainActivity", "sendFile rsp: $rsp")
            // your implementation
        },
        { error ->
            Log.e("MainActivity", "sendFile error: $error")
            // your implementation
        },
    )
    ```
    See ZeroBounce [sendfile parameters](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-send-file): `allow_phase_2` requests catch-all (phase 2) validation after phase 1 when **Verify+** is enabled and the file meets API rules (e.g. more than 10 catch-all emails). Otherwise `file_phase_2_status` may stay `N/A`.

* ##### The *getFile* API allows users to get the validation results file for the file been submitted using *sendFile* API
    ```kotlin
    val fileId = "<FILE_ID>"    // The returned file ID when calling sendfile API

    ZeroBounceSDK.getFile(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "getfile rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "getfile error: $error") 
            // your implementation
        },
        downloadType = null,
        activityData = null, // optional: true → `activity_data` (validation only)
    )
    ```
    ```kotlin
    ZeroBounceSDK.getFile(
        context,
        fileId,
        { rsp -> Log.d("MainActivity", "getfile combined rsp: $rsp") },
        { error -> Log.e("MainActivity", "getfile combined error: $error") },
        downloadType = ZBDownloadType.COMBINED,
    )
    ```
    See [v2 getfile](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-get-file): `phase_2` / `combined` only when `file_phase_2_status = Complete`. Optional `activity_data` → [activity data](https://www.zerobounce.net/docs/activity-data). JSON errors may use HTTP 200; the SDK returns them via `errorCallback`.

    Pass `downloadType` / `activityData` as **named** arguments after the two callbacks (avoids ambiguity with Kotlin overloads).

* ##### Check the status of a file uploaded via *sendFile* method
    ```kotlin
    val fileId = "<FILE_ID>"    // The returned file ID when calling sendfile API

    ZeroBounceSDK.fileStatus(
        fileId,
        { rsp -> 
            Log.d("MainActivity", "fileStatus rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "fileStatus error: $error") 
            // your implementation
        }
    )
    ```
    The file status response includes `file_phase_2_status` between `file_status` and
    `complete_percentage`.
    - Successful response - phase 2 disabled: `file_phase_2_status = "N/A"`
    - Successful response - phase 2 enabled: `file_phase_2_status = "Complete"`
    - Other possible values:
      - `file_status=Queued` -> `file_phase_2_status="Queued"`
      - `file_status=Processing` -> `file_phase_2_status="Processing"`
      - `file_status=CatchAllProcessing` -> `file_phase_2_status="Processing"`
      - `file_status=Complete` -> `file_phase_2_status="Complete"`
      - for all other statuses, `file_phase_2_status` copies `file_status`
    The `return_url` callback behavior remains unchanged.

* ##### Delete the file that was submitted using *sendFile* API. File can be deleted only when its status is `Complete`
    ```kotlin
    val fileId = "<FILE_ID>"   // The returned file ID when calling sendfile API

    ZeroBounceSDK.deleteFile(
        fileId,
        { rsp -> 
            Log.d("MainActivity", "deleteFile rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "deleteFile error: $error") 
            // your implementation
        }
    )
    ```
    [v2 deletefile](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-delete-file): allowed when `file_status` is `Complete`.

* ##### Gather insights into your subscribers’ overall email engagement. The request returns data regarding opens, clicks, forwards and unsubscribes that have taken place in the past 30, 90, 180 or 365 days.
    ```kotlin
    ZeroBounceSDK.getActivityData(
        "<EMAIL_TO_TEST>",
        { rsp -> 
            Log.d("MainActivity", "validate rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "validate error: $error") 
            // your implementation
        }
    )
    ```

### AI Scoring API

**Live API behavior (verified against production):** The scoring bulk base URL (`.../v2/scoring`) differs from validation bulk. In our checks, `filestatus` did not include `file_phase_2_status`, and `getfile` accepted `phase_1`, `phase_2`, and `combined` and returned the same scoring CSV each time. Validation bulk returns `file_phase_2_status` and may return a JSON error (HTTP 200) for `phase_2`/`combined` when phase 2 is not enabled.

* ##### The *scoringSendFile* API allows user to send a file for bulk email validation
    ```kotlin
    // import java.io.File
    val myFile = File("<FILE_PATH>")  // The csv or txt file
    val emailAddressColumn = 3        // The column index of email address in the file. Index starts at 1
    val hasHeaderRow = true           // If this is `true` the first row is considered as table headers 
    val returnUrl = "https://domain.com/called/after/processing/request"

    ZeroBounceSDK.scoringSendFile(
        context,
        myFile,
        emailAddressColumn,
        returnUrl, 
        hasHeaderRow,
        { rsp ->
            Log.d("MainActivity", "scoringSendFile rsp: $rsp")
            // your implementation
        },
        { error ->
            Log.e("MainActivity", "scoringSendFile error: $error")
            // your implementation
        }
    )
    ```

* ##### The *scoringGetFile* API allows users to get the validation results file for the file been submitted using *scoringSendFile* API
    ```kotlin
    val fileId = "<FILE_ID>"    // The returned file ID when calling scoringSendFile API

    ZeroBounceSDK.scoringGetFile(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringGetFile rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "scoringGetFile error: $error")
            // your implementation
        }
    )
    ```

* ##### Check the status of a file uploaded via *scoringSendFile* method
    ```kotlin
    val fileId = "<FILE_ID>"    // The returned file ID when calling scoringSendFile API

    ZeroBounceSDK.scoringFileStatus(
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringFileStatus rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "scoringFileStatus error: $error") 
            // your implementation
        }
    )
    ```

* ##### Delete the file that was submitted using scoring *scoringSendFile* API. File can be deleted only when its status is `Complete`
    ```kotlin
    val fileId = "<FILE_ID>"   // The returned file ID when calling scoringSendFile API

    ZeroBounceSDK.scoringDeleteFile(
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringDeleteFile rsp: $rsp")
            // your implementation
        },
        { error -> 
            Log.e("MainActivity", "scoringDeleteFile error: $error") 
            // your implementation
        }
    )
    ```

* ##### Find the email formats based on a given first name and domain
    ```kotlin
    ZeroBounceSDK.findEmail(
        firstName = "<FIRST_NAME_TO_TEST>",
        domain = "<DOMAIN_TO_TEST>",
        responseCallback = { rsp ->
            Log.d("MainActivity", "findEmail rsp: $rsp")
            // your implementation
        },
        errorCallback = { error ->
            Log.e("MainActivity", "findEmail error: $error")
            // your implementation
        }
    )
    ```

* ##### Find the email formats based on a given first name and company name
    ```kotlin
    ZeroBounceSDK.findEmail(
        firstName = "<FIRST_NAME_TO_TEST>",
        companyName = "<COMPANY_NAME_TO_TEST>",
        responseCallback = { rsp ->
            Log.d("MainActivity", "findEmail rsp: $rsp")
            // your implementation
        },
        errorCallback = { error ->
            Log.e("MainActivity", "findEmail error: $error")
            // your implementation
        }
    )
    ```

* ##### Find other domain formats based on a given domain
    ```kotlin
    ZeroBounceSDK.findDomain(
        domain = "<DOMAIN_TO_TEST>",
        responseCallback = { rsp ->
            Log.d("MainActivity", "findDomain rsp: $rsp")
            // your implementation
        },
        errorCallback = { error ->
            Log.e("MainActivity", "findDomain error: $error")
            // your implementation
        }
    )
    ```

* ##### Find other domain formats based on a given company name
   ```kotlin
   ZeroBounceSDK.findDomain(
       companyName = "COMPANY_NAME_TO_TEST",
       responseCallback = { rsp ->
           Log.d("MainActivity", "findDomain rsp: $rsp")
           // your implementation
       },
       errorCallback = { error ->
           Log.e("MainActivity", "findDomain error: $error")
           // your implementation
       }
   )
   ``` 

## Validate options

`validate` takes a `ZBValidateOptions`. An option left `null` is not sent, and
booleans go on the wire as the strings `"true"`/`"false"`.

```kotlin
ZeroBounceSDK.validate(
    "<EMAIL>",
    null,
    ZBValidateOptions(
        timeout = 30,                 // 3-60 seconds
        activityData = true,          // fills activeInDays / activeFirstSeen
        verifyPlus = true,
        domainInfo = true,            // fills domainWebsiteExists / domainRegistrantCompanyName
        greylistProcessing = true,
        callbackUrl = "https://example.com/greylist",
        usePost = true,               // form-encoded POST /v2/validate instead of GET
    ),
    { response -> println(response?.status) },
    { error -> println(error?.errors) },
)
```

`callbackUrl` is where the greylist retry posts its result, so it is rejected
without `greylistProcessing = true` — before any request is sent. While that
retry is still running the API answers with a `Message` instead of a result,
which lands on `response.message`.

## S3 and SFTP/FTP bulk

Queue a job on a file that already sits in your Amazon S3 bucket, or on your
SFTP/FTP server. See the [S3](https://www.zerobounce.net/docs/api-integrations/amazon-s3-bulk-api)
and [SFTP](https://www.zerobounce.net/docs/api-integrations/sftp_bulk_api) docs.
These live on the bulkapi host *without* the `/v2` path.

```kotlin
// Amazon S3. fileName is the full path, as returned by listS3Files.
// fileName and returnUrl are required on every one of these jobs.
val s3 = ZBBulkFileJobOptions(
    fileName = "lists/leads.csv",
    hasHeaderRow = true,
    returnUrl = "https://example.com/zerobounce-done",
)
ZeroBounceSDK.emailFinderS3File(s3, ok, error)
ZeroBounceSDK.domainSearchS3File(s3.copy(removeDuplicates = true), ok, error)

// SFTP/FTP. The server, path and protocol come from your ZeroBounce settings.
// The docs list fileName as optional for validateSFTPFile, but the live API
// requires it there too.
val sftp = ZBBulkFileJobOptions(
    fileName = "my_file.txt",
    hasHeaderRow = true,
    returnUrl = "https://example.com/zerobounce-done",
)
ZeroBounceSDK.validateSFTPFile(sftp, ok, error)
ZeroBounceSDK.scoreSFTPFile(sftp, ok, error)
ZeroBounceSDK.emailFinderSFTPFile(sftp, ok, error)
ZeroBounceSDK.domainSearchSFTPFile(sftp, ok, error)

// Note the field: this one takes export_path, not export_bucket.
// null uses the export path from your settings.
ZeroBounceSDK.exportSFTPFile("<FILE_ID>", "/exports", ok, error)
```

## Inbox Placement Tester

Runs on its own host, authenticates with an `Authorization: Bearer` header, and
sends camelCase JSON bodies. It spends the account's inbox-test credits.

```kotlin
ZeroBounceSDK.getInboxPlacementProviders(ok, error)
ZeroBounceSDK.getInboxPlacementTestTypes(ok, error)

ZeroBounceSDK.createInboxPlacementTest(
    "Launch",
    ZBCreateInboxPlacementTestOptions(
        testType = 1,
        senderEmail = "news@example.com",
        webhookUrl = "https://example.com/ipt-webhook",
    ),
    ok, error)

// Send your campaign to the seeds you got back, then read the results.
ZeroBounceSDK.getInboxPlacementTest("<TEST_ID>", ok, error)

ZeroBounceSDK.listInboxPlacementTests(
    ZBListInboxPlacementTestsOptions(status = "finished", page = 1), ok, error)
ZeroBounceSDK.setInboxPlacementWebhook("https://example.com/ipt-webhook", ok, error)
ZeroBounceSDK.getInboxPlacementWebhook(ok, error)
ZeroBounceSDK.deleteInboxPlacementTest("<TEST_ID>", ok, error)
```

## Per-API base URLs

The list evaluator and the Inbox Placement Tester are separate hosts, so
`initialize` alone leaves them pointing at the live API.

```kotlin
ZeroBounceSDK.setListEvaluatorBaseUrl("https://bulkapi.zerobounce.net/v2/listevaluator")
ZeroBounceSDK.setInboxPlacementBaseUrl("https://bulkapi.zerobounce.net/api/ipt")
```

## Warmup API

The Warmup API is a separate product. It has its own host, its own team-scoped key, and its own client; your validation key will not work with it.

```kotlin
val warmup = ZeroBounceWarmup()
warmup.initialize("<YOUR_WARMUP_API_KEY>")

warmup.listSendingAccounts(ok, error)
warmup.getProgressToday(ok, error)
warmup.downloadSeeds({ csv -> println(csv) }, error)   // columns seed,provider
```

Add a from-address you run yourself, with one of the five standard engagement rules (0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect):

```kotlin
warmup.addSendingAccount(
    "sender@example.com", "self_managed",
    ZBWarmupAddSendingAccountOptions(engagementRuleId = 0), ok, error)
```

A custom engagement rule can be changed only once every seven days; inside that window the API answers 429.

```kotlin
warmup.createCustomEngagement("sender@example.com", 20, 20, 35, ok, error)  // reply, important, click %
```

`checkDomainDmarc` takes an **email address**, not a domain — the live API rejects a bare domain even though the docs describe one:

```kotlin
warmup.checkDomainDmarc("sender@example.com", null, ok, error)
```

Every documented empty success (204, or an empty 200) arrives as a `null` payload, and a payload that is not a JSON object — `/seeds` is an array, `/seed-count` a bare number — arrives as that `JsonElement`. Failures carry the HTTP status on `ErrorResponse.statusCode`. Nothing is stored by `testSmtpConfiguration`, and the credentials you pass it are masked out of any error it raises.

## Live API suite

`gradle test` never touches the network: the live suite is excluded from the default test task unless you pass `-PzbLive`, so CI never runs it. It needs real keys, which come from the environment and are never written anywhere:

```bash
export ZEROBOUNCE_API_KEY=...          # validation API
export ZEROBOUNCE_WARMUP_API_KEY=...   # Warmup API
./gradlew :zero_bounce_sdk:testDebugUnitTest -PzbLive \
  --tests "com.zerobounce.android.LiveApiTest"
```

By default it only makes free calls: sandbox addresses, reads, and requests that are expected to fail but still prove the API accepts the payload. It checks that the credit balance did not move. Two flags opt in to more:

| Flag | What it adds |
| --- | --- |
| `ZEROBOUNCE_LIVE_SPEND=1` | billable calls (scoring, activity, email finder) and a bulk round trip that deletes its own job |
| `ZEROBOUNCE_LIVE_CHANGES=1` | reversible account changes — a test filter and a self-managed Warmup sending account — each undone in cleanup even when a check fails |

It never creates an Inbox Placement test, sets the Inbox Placement webhook, or starts ZB-managed Warmup onboarding, because the API cannot undo any of those.

Run it by hand before every release: the offline suite proves the SDK sends what we think it sends, and only the live suite proves the API accepts it.

## Testing

Unit tests (JVM only, no emulator) can be run locally or via Docker.

**Local:** from this directory run:
```bash
./gradlew :zero_bounce_sdk:test
```

**Docker:** from the **`sdk-docs/`** folder in the SDKs monorepo:
```bash
cd sdk-docs
docker compose build android
docker compose run --rm android
```

See [TESTING.md](../sdk-docs/TESTING.md) for all SDK test commands.

## Documentation

The documentation of the SDK can be generated through a *Gradle* task. Open the *Gradle* tab (on the default layout, it should be at the right side of the Android Studio), then go to *zero_bounce_sdk > Tasks > documentation* and double click on the ***dokkaHtml*** task. After it is generated, you can find it in *zero_bounce_sdk/build/dokka/html*. From there you only have to open the ```index.html``` file.

## Publication

Publishing to Maven Central is done via the **GitHub Actions `publish` workflow**, triggered **manually per tag**. It does not run automatically on release creation.

1. **Version the project** and create a **git tag** (e.g. `v1.5.1`), then push the tag.
2. In the repo go to **Actions → Publish**, click **Run workflow**, enter the tag (e.g. `v1.5.1`), and run.
3. The workflow runs tests, builds, and publishes to Maven Central.

If you change the OSSRH/Sonatype or signing credentials, update the repository **Secrets** on GitHub.

### Local setup for manual release
In order to be able to publish to the Nexus repository from you local machine, you'll need to do a few steps:
1. Create a/Update the `local.properties` file, in the project root, like this:
    ```gradle
    ## This file must *NOT* be checked into Version Control Systems,
    # as it contains information specific to your local configuration.
    #
    # Location of the SDK. This is only used by Gradle.
    # For customization when using a Version Control System, please read the
    # header note.
    #Mon Mar 20 10:30:40 EET 2023
    sdk.dir=<YOUR_ANDROID_SDK_DIR>
    signing.keyId=<THE_LAST_8_DIGITS_OF_YOUR_GPG_KEY>
    signing.password=<YOUR_GPG_PASSWORD>
    signing.key=<YOUR_GPG_PRIVATE_KEY>  # newlines must be replaced with the newline character '\n'
    centralTokenUsername=<YOUR_SONATYPE_CENTRAL_TOKEN_USERNAME>
    centralTokenPassword=<YOUR_SONATYPE_CENTRAL_TOKEN_PASSWORD>
    sonatypeStagingProfileId=<YOUR_SONATYPE_STAGING_PROFILE_ID>
    ```
2. Import the GPG key to your local machine (see below)

If you want to manually publish to the Nexus repository (and then release it to Maven Central), you can use the following commands:
```shell
# For publishing to the staging repository
./gradlew publishReleasePublicationToSonatypeRepository

# For closing and releasing the artifact.
./gradlew closeAndReleaseSonatypeStagingRepository
```

Alternatively, you can only execute the first command, then then go to the [Nexus Sonatype] (https://s01.oss.sonatype.org/), login and then open *Staging Repositories* and click on *Refresh*. Here you'll see the artifact you just uploaded. In order to publish it, you have to **close** it and then **release** it. These actions will take a few minutes to complete. After * *releasing** the artifact, it will take:
- a few hours before you can see it on the [Maven Repository](https://repo1.maven.org/maven2/com/zerobounce/android/zerobouncesdk/) and on the [Sonatype Search](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk)
- 1-3 days before you can see it on the [MVN Repository](https://mvnrepository.com/artifact/com.zerobounce.android/zerobouncesdk)

## Exporting and importing PGP keys
1. Export the keys:
    ```shell
    gpg --list-keys  # In order to obtain the key hash for the next step
    gpg --export -a <LAST_8_DIGITS> > public.key
    gpg --export-secret-key -a <LAST_8_DIGITS> > private key
    ```
2. Import the keys:
    ```shell
    gpg --import public.key
    gpg --import private.key
    ```
3. Check that the new keys are imported:
    ```shell
    gpg --list-keys
    gpg --list-secret-keys
    ```
   
## Breaking Changes:
**`guessFormat`** has been deprecated. To continue using your existing code, you must migrate to **`findEmail`** or **`findDomain`** .
The change is not a simple one-to-one replacement, as the functionality has been split:
- **If you were finding a person's email format**, use the new **`findEmail()`** method.
- **If you were only determining the domain's general email pattern**, use the new **`findDomain()`** method.
### Migration Example:
- #### Old (Deprecated)
    ```kotlin
    ZeroBounceSDK.guessFormat(
        domain = "<DOMAIN_TO_TEST>",
        responseCallback = { rsp ->
            Log.d("MainActivity", "guessFormat rsp: $rsp")
            // your implementation
        },
        errorCallback = { error ->
            Log.e("MainActivity", "guessFormat error: $error")
            // your implementation
        }
    )
    ```
- #### New Methods
  - ##### Find the email formats based on a given first name and domain
      ```kotlin
      ZeroBounceSDK.findEmail(
          firstName = "<FIRST_NAME_TO_TEST>",
          domain = "<DOMAIN_TO_TEST>",
          responseCallback = { rsp ->
              Log.d("MainActivity", "findEmail rsp: $rsp")
              // your implementation
          },
          errorCallback = { error ->
              Log.e("MainActivity", "findEmail error: $error")
              // your implementation
          }
      )
      ```
  - ##### Find the email formats based on a given first name and company name
      ```kotlin
      ZeroBounceSDK.findEmail(
          firstName = "<FIRST_NAME_TO_TEST>",
          companyName = "<COMPANY_NAME_TO_TEST>",
          responseCallback = { rsp ->
              Log.d("MainActivity", "findEmail rsp: $rsp")
              // your implementation
          },
          errorCallback = { error ->
              Log.e("MainActivity", "findEmail error: $error")
              // your implementation
          }
      )
      ```
  - ##### Find other domain formats based on a given domain
      ```kotlin
      ZeroBounceSDK.findDomain(
          domain = "<DOMAIN_TO_TEST>",
          responseCallback = { rsp ->
              Log.d("MainActivity", "findDomain rsp: $rsp")
              // your implementation
          },
          errorCallback = { error ->
              Log.e("MainActivity", "findDomain error: $error")
              // your implementation
          }
      )
      ```
  - ##### Find other domain formats based on a given company name
     ```kotlin
     ZeroBounceSDK.findDomain(
         companyName = "COMPANY_NAME_TO_TEST",
         responseCallback = { rsp ->
             Log.d("MainActivity", "findDomain rsp: $rsp")
             // your implementation
         },
         errorCallback = { error ->
             Log.e("MainActivity", "findDomain error: $error")
             // your implementation
         }
     )
     ```
## New Features and Enhancements
### Custom API URL Support
The `ZeroBounceSDK.initialize()` method now accepts an **optional** `apiBaseUrl` parameter.
This allows you to specify a custom base URL for the ZeroBounce API.
#### Migration / Usage
The existing way of initializing the SDK is still valid.
- Default Usage (No Change Required). If you don't provide a URL, the SDK will continue to use the standard ZeroBounce API endpoint:
    ```kotlin
    ZeroBounceSDK.initialize("<YOUR_API_KEY>")
    ```
- Initialize the SDK with your API key and URL:
    ```kotlin
    ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = "<YOUR_URL>")
    ```
The SDK now exposes a set of predefined constants for different geographical API endpoints,
allowing for more precise network routing.
### Available API Endpoints
You can specify a custom API base URL during initialization by using the new optional `apiBaseUrl`
parameter in `initialize()`. For convenience, the following constants are available in the
**`ZBConstants`** class:
| Constant              | URL Value                                   | Description                                                          |
|:----------------------|:--------------------------------------------|:---------------------------------------------------------------------|
| **`API_DEFAULT_URL`** | `https://api.zerobounce.net/v2/`    | The global default endpoint.                                         |
| **`API_USA_URL`**     | `https://api-us.zerobounce.net/v2/` | The US-specific endpoint for lower latency in the Americas.          |
| **`API_EU_URL`**      | `https://api-eu.zerobounce.net/v2/` | The EU-specific endpoint for compliance and lower latency in Europe. |
### Usage Example:
To use the EU endpoint for initialization:
```kotlin
ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>", apiBaseUrl = ZBConstants.API_EU_URL)
```

## Publish

1. Bump `PUBLISH_VERSION` in `zero_bounce_sdk/build.gradle`, commit, tag (`vX.Y.Z`), push tag.
2. **Actions → Publish → Run workflow** with that tag.

Registry: [com.zerobounce.android:zerobouncesdk on Maven Central](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk)