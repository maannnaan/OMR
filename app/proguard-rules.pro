# OpenCV calls into native code by name
-keep class org.opencv.** { *; }

# Question bank and Lecturer.bd JSON models (parsed with Gson by field name)
-keep class com.omrscanner.app.data.bank.** { *; }
-keep class com.omrscanner.app.data.lecturer.** { *; }
-keepattributes Signature, *Annotation*

# Retrofit
-keepattributes Exceptions, InnerClasses, EnclosingMethod
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
