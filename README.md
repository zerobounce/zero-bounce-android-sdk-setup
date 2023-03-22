## ZeroBounce Android SDK

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.android/zerobouncesdk/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.android/zerobouncesdk) [![Build Status](https://github.com/zerobounce-llc/zero-bounce-android-sdk-setup/actions/workflows/publish.yml/badge.svg?branch=master)](https://github.com/zerobounce-llc/zero-bounce-android-sdk-setup/actions/workflows/publish.yml)

This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/). \
This SDK is built using the Java 11 version.

### Installation
You can install ZeroBounceSDK by adding the dependency to your gradle file:

```gradle
implementation 'com.zerobounce.android:zerobouncesdk:1.1.1'
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

ZeroBounceSDK.getfile(
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

ZeroBounceSDK.scoringGetfile(
    context, 
    fileId,
    { rsp -> 
        Log.d("MainActivity", "scoringGetfile rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "scoringGetfile error: $error") 
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
