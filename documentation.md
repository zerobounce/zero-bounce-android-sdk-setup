##### Installation
You can install ZeroBounceSDK by adding the dependency to your gradle file:

```gradle
implementation 'com.zerobounce.android:zerobouncesdk:<latest_version>'
```

#### USAGE
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

#### Validation statuses and sub-statuses

The `validate` and `validateBatch` responses include `status` (ZBValidateStatus) and `subStatus` (ZBValidateSubStatus).

**ZBValidateStatus** values: `NONE`, `VALID`, `INVALID`, `CATCH_ALL`, `UNKNOWN`, `SPAMTRAP`, `ABUSE`, `DO_NOT_MAIL`

**ZBValidateSubStatus** values (examples): `ANTISPAM_SYSTEM`, `GREYLISTED`, `MAIL_SERVER_TEMPORARY_ERROR`, `FORCIBLE_DISCONNECT`, `MAIL_SERVER_DID_NOT_RESPOND`, `TIMEOUT_EXCEEDED`, `FAILED_SMTP_CONNECTION`, `MAILBOX_QUOTA_EXCEEDED`, `EXCEPTION_OCCURRED`, `POSSIBLE_TRAP`, `ROLE_BASED`, `GLOBAL_SUPPRESSION`, `MAILBOX_NOT_FOUND`, `NO_DNS_ENTRIES`, `FAILED_SYNTAX_CHECK`, `POSSIBLE_TYPO`, `UNROUTABLE_IP_ADDRESS`, `LEADING_PERIOD_REMOVED`, `DOES_NOT_ACCEPT_MAIL`, `ALIAS_ADDRESS`, `ROLE_BASED_CATCH_ALL`, `DISPOSABLE`, `TOXIC`, `ALTERNATE`, `MX_FORWARD`, `BLOCKED`, `ALLOWED`, `ACCEPT_ALL`, `ROLE_BASED_ACCEPT_ALL`, `GOLD`

#### Examples
Then you can use any of the SDK methods, for example:

* ####### Validate an email address
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

* ####### Validate batch a list of email addresses
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

* ####### Find the email and other domain formats based on a given domain name
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

* ####### Check how many credits you have left on your account
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

* ####### Check your API usage for a given period of time
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

* ####### The *sendFile* API allows user to send a file for bulk email validation
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

* ####### The *getFile* API allows users to get the validation results file for the file been submitted using *sendFile* API
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
        downloadType = null, // optional: ZBDownloadType.PHASE_1, PHASE_2, COMBINED
        activityData = null, // optional: true/false → query `activity_data` (validation only)
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
    Matches [v2 getfile](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-get-file): `phase_2` and `combined` only when `file_phase_2_status = Complete`. Optional `activity_data` appends activity columns when `true` ([activity data](https://www.zerobounce.net/docs/activity-data)). The API may return JSON errors with HTTP 200; the SDK surfaces those via `errorCallback`.

* ####### Check the status of a file uploaded via *sendFile* method
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
    The `return_url` callback behavior remains unchanged. See [v2 file status](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-file-status).

* ####### Delete the file that was submitted using *sendFile* API. File can be deleted only when its status is `Complete`
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
    Per [v2 deletefile](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-delete-file), delete is only allowed when `file_status` is `Complete`.

* ####### Gather insights into your subscribers’ overall email engagement. The request returns data regarding opens, clicks, forwards and unsubscribes that have taken place in the past 30, 90, 180 or 365 days.
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


##### AI Scoring API

**Live API behavior (verified against production):** The scoring bulk base URL (`.../v2/scoring`) differs from validation bulk. In our checks, `filestatus` did not include `file_phase_2_status`, and `getfile` accepted `phase_1`, `phase_2`, and `combined` and returned the same scoring CSV each time. Validation bulk returns `file_phase_2_status` and may return a JSON error (HTTP 200) for `phase_2`/`combined` when phase 2 is not enabled.

* ####### The *scoringSendFile* API allows user to send a file for bulk email validation
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

* ####### The *scoringGetFile* API allows users to get the validation results file for the file been submitted using *scoringSendFile* API
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

* ####### Check the status of a file uploaded via *scoringSendFile* method
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

* ####### Delete the file that was submitted using scoring *scoringSendFile* API. File can be deleted only when its status is `Complete`
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

#### Documentation
The documentation of the SDK can be generated through a *Gradle* task. Open the *Gradle* tab (on the default layout, it should be at the right side of the Android Studio), then go to *zero_bounce_sdk > Tasks > documentation* and double click on the ***dokkaHtml*** task. After it is generated, you can find it in *zero_bounce_sdk/build/dokka/html*. From there you only have to open the ```index.html``` file.


#### Publication
Every time a new release is created, the CI/CD pipeline will execute and a new artifact will be released on Maven Central. **The pipeline updates the version automatically!**
If you ever change the OSSRH login credentials, you'll need to also update the repository variables on Github.


##### Local setup for manual release
In order to be able to publish to the Nexus repository from you local machine, you'll need to do a few steps:
1. Create a/Update the `local.properties` file, in the project root, like this:
    ```gradle
    #### This file must *NOT* be checked into Version Control Systems,
    ### as it contains information specific to your local configuration.
    ###
    ### Location of the SDK. This is only used by Gradle.
    ### For customization when using a Version Control System, please read the
    ### header note.
    ###Mon Mar 20 10:30:40 EET 2023
    sdk.dir=<YOUR_ANDROID_SDK_DIR>
    signing.keyId=<THE_LAST_8_DIGITS_OF_YOUR_GPG_KEY>
    signing.password=<YOUR_GPG_PASSWORD>
    signing.key=<YOUR_GPG_PRIVATE_KEY>  ### newlines must be replaced with the newline character '\n'
    centralTokenUsername=<YOUR_SONATYPE_CENTRAL_TOKEN_USERNAME>
    centralTokenPassword=<YOUR_SONATYPE_CENTRAL_TOKEN_PASSWORD>
    sonatypeStagingProfileId=<YOUR_SONATYPE_STAGING_PROFILE_ID>
    ```
2. Import the GPG key to your local machine (see below)


If you want to manually publish to the Nexus repository (and then release it to Maven Central), you can use the following commands:
```shell
### For publishing to the staging repository
./gradlew publishReleasePublicationToSonatypeRepository

### For closing and releasing the artifact.
./gradlew closeAndReleaseSonatypeStagingRepository
```

Alternatively, you can only execute the first command, then then go to the [Nexus Sonatype](https://s01.oss.sonatype.org/), login and then open *Staging Repositories* and click on *Refresh*. Here you'll see the artifact you just uploaded. In order to publish it, you have to **close** it and then **release** it. These actions will take a few minutes to complete. After **releasing** the artifact, it will take:
- a few hours before you can see it on the [Maven Repository](https://repo1.maven.org/maven2/com/zerobounce/in/android/zerobouncesdk/) and on the [Sonatype Search](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk)
- 1-3 days before you can see it on the [MVN Repository](https://mvnrepository.com/artifact/com.zerobounce.android/zerobouncesdk)


#### Exporting and importing PGP keys
1. Export the keys:
    ```shell
    gpg --list-keys  ### In order to obtain the key hash for the next step
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
