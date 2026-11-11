# R8/ProGuard rules for apps that use the ZeroBounce SDK. They ship inside the
# AAR (consumerProguardFiles) and apply when the app is minified.
#
# Gson maps the response models by field name (lower_case_with_underscores), and
# most fields carry no @SerializedName, so the field names, the classes and their
# constructors must survive minification. Without these rules a minified app
# reads every un-annotated field as null, false or 0.
-keep class com.zerobounce.android.** implements com.zerobounce.android.JSONConvertable {
    <fields>;
    <init>(...);
}
-keep class com.zerobounce.android.DomainFormat {
    <fields>;
    <init>(...);
}
-keep class com.zerobounce.android.ZBValidateBatchData {
    <fields>;
    <init>(...);
}
# ZBValidateStatus and ZBValidateSubStatus are matched by @SerializedName on
# their constants.
-keepclassmembers enum com.zerobounce.android.** {
    <fields>;
}
# Gson reads generic field types (List<DomainFormat>) and annotations at run time.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
