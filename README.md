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
- Validate an email address
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

- Check how many credits you have left on your account
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
