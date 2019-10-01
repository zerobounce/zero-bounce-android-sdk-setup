## ZeroBounce Android SDK
This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).

### Installation
You can install ZeroBounceSDK by adding the dependency to your gradle file:

```
implementation 'com.zerobounce.android:zerobouncesdk:1.0.0'
```

## USAGE
Import the sdk in your file:
```kotlin
import com.zerobounce.android.ZeroBounceSDK
```

Initialize the sdk with your api key:
```kotlin 
ZeroBounceSDK.initialize(this.applicationContext, "<YOUR_API_KEY>")
```

## Examples
Then you can use any of the SDK methods, for example:
* ##### Validate an email address
```kotlin
ZeroBounceSDK.validate("<EMAIL_TO_TEST>", "<OPTIONAL_IP_ADDRESS>",
    { rsp -> 
        Log.d("MainActivity", "validate rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "validate error: $error") 
        // your implementation
    })
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
    })
```

* ##### Check your API usage for a given period of time
```java
Date startDate = new Date();    // The start date of when you want to view API usage
Date endDate = new Date();      // The end date of when you want to view API usage

ZeroBounceSDK.getApiUsage(startDate, endDate,
    { rsp -> 
        Log.d("MainActivity", "getApiUsage rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "getApiUsage error: $error") 
        // your implementation
    })
```

* ##### The sendfile API allows user to send a file for bulk email validation
```java
File myFile = new File("<FILE_PATH>");  // The csv or txt file
Int emailAddressColumn = 3;             // The column index of email address in the file. Index starts at 1
Int firstNameColumn = 4;                // The column index of first name in the file
Int lastNameColumn = 5;                 // The column index of last name in the file
Int genderColumn = 6;                   // The column index of gender in the file
Int ipAddressColumn = 7;                // The column index of IP address in the file
Boolean hasHeaderRow = true;            // If this is `true` the first row is considered as table headers 
String returnUrl = 'https://domain.com/called/after/processing/request';

ZeroBounceSDK.sendFile(
    this.context,
    file,
    { rsp -> 
        Log.d("MainActivity", "sendFile rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "sendFile error: $error") 
        // your implementation
    },
    returnUrl, 
    firstNameColumn, 
    lastNameColumn,
    genderColumn, 
    ipAddressColumn, 
    hasHeaderRow
)
```

* ##### The getfile API allows users to get the validation results file for the file been submitted using sendfile API
```java
String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API

ZeroBounceSDK.getfile(this.context, fileId,
    { rsp -> 
        Log.d("MainActivity", "getfile rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "getfile error: $error") 
        // your implementation
    })
```

* ##### Check the status of a file uploaded via "sendFile" method
```java
String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API

ZeroBounceSDK.fileStatus(this.context, fileId,
    { rsp -> 
        Log.d("MainActivity", "fileStatus rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "fileStatus error: $error") 
        // your implementation
    })
```

* ##### Deletes the file that was submitted using scoring sendfile API. File can be deleted only when its status is _`Complete`_
```java
String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API

ZeroBounceSDK.deleteFile(this.context, fileId,
    { rsp -> 
        Log.d("MainActivity", "deleteFile rsp: $rsp")
        // your implementation
    },
    { error -> 
        Log.e("MainActivity", "deleteFile error: $error") 
        // your implementation
    })
```
