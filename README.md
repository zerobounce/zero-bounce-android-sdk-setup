## ZeroBounce Android SDK

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.android/zerobouncesdk/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.android/zerobouncesdk) [![Build Status](https://github.com/zerobounce/zero-bounce-android-sdk-setup/actions/workflows/publish.yml/badge.svg?branch=master)](https://github.com/zerobounce/zero-bounce-android-sdk-setup/actions/workflows/publish.yml)

This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/). \
This SDK is built using the Java 11 version.

### Installation
You can install ZeroBounceSDK by adding the dependency to your gradle file:

```gradle
implementation 'com.zerobounce.android:zerobouncesdk:1.1.5'
```

## USAGE
Import the sdk in your file:
```kotlin
import com.zerobounce.android.ZeroBounceSDK
```

Initialize the sdk with your api key:
```kotlin
ZeroBounceSDK.initialize("<YOUR_API_KEY>")
```

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
* ##### Guess the format of an email
    ```kotlin
    ZeroBounceSDK.guessFormat(
        email = email,
        domain = domain,
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
    val firstNameColumn = 4           // The column index of first name in the file
    val lastNameColumn = 5            // The column index of last name in the file
    val genderColumn = 6              // The column index of gender in the file
    val ipAddressColumn = 7           // The column index of IP address in the file
    val hasHeaderRow = true           // If this is `true` the first row is considered as table headers 
    val returnUrl = "https://domain.com/called/after/processing/request"

    ZeroBounceSDK.sendFile(
        context,
        file,
        returnUrl, 
        firstNameColumn, 
        lastNameColumn,
        genderColumn, 
        ipAddressColumn, 
        hasHeaderRow,
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
        }
    )
    ```

* ##### Check the status of a file uploaded via *sendFile* method
    ```kotlin
    val fileId = "<FILE_ID>"    // The returned file ID when calling sendfile API

    ZeroBounceSDK.fileStatus(
        context, 
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

* ##### Delete the file that was submitted using *sendFile* API. File can be deleted only when its status is `Complete`
    ```kotlin
    val fileId = "<FILE_ID>"   // The returned file ID when calling sendfile API

    ZeroBounceSDK.deleteFile(
        context,
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

* ##### The *scoringSendFile* API allows user to send a file for bulk email validation
    ```kotlin
    // import java.io.File
    val myFile = File("<FILE_PATH>")  // The csv or txt file
    val emailAddressColumn = 3        // The column index of email address in the file. Index starts at 1
    val hasHeaderRow = true           // If this is `true` the first row is considered as table headers 
    val returnUrl = "https://domain.com/called/after/processing/request"

    ZeroBounceSDK.scoringSendFile(
        context,
        file,
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
        context, 
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
        context, 
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

## Documentation
The documentation of the SDK can be generated through a *Gradle* task. Open the *Gradle* tab (on the default layout, it should be at the right side of the Android Studio), then go to *zero_bounce_sdk > Tasks > documentation* and double click on the ***dokkaHtml*** task. After it is generated, you can find it in *zero_bounce_sdk/build/dokka/html*. From there you only have to open the ```index.html``` file.


## Publication
Every time a new release is created, the CI/CD pipeline will execute and a new artifact will be released on Maven Central. Don't forget to update the version before doing a release!
If you ever change the OSSRH login credentials, you'll need to also update the repository variables on Github.


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
    ossrhUsername=<YOUR_SONATYPE_JIRA_USERNAME>
    ossrhPassword=<YOUR_SONATYPE_JIRA_PASSWORD>
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

Alternatively, you can only execute the first command, then then go to the [Nexus Sonatype](https://s01.oss.sonatype.org/), login and then open *Staging Repositories* and click on *Refresh*. Here you'll see the artifact you just uploaded. In order to publish it, you have to **close** it and then **release** it. These actions will take a few minutes to complete. After **releasing** the artifact, it will take:
- a few hours before you can see it on the [Maven Repository](https://repo1.maven.org/maven2/com/zerobounce/android/zerobouncesdk/) and on the [Sonatype Search](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk/1.1.1)
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
