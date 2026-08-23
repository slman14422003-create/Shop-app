# Added together with enabling isMinifyEnabled/isShrinkResources on the
# release build (see the comment in app/build.gradle.kts). This app maps
# every Firestore document manually (doc.getString()/getDouble()/etc. — see
# DebtsRepository.kt and MaterialsRepository.kt), never toObject(), so no
# app data class needs a keep rule. What still needs protecting is
# Firestore's own gRPC/protobuf transport, which does its own reflection
# internally regardless of how the app reads documents.

# Firebase Firestore — gRPC + protobuf reflection used internally for the
# wire protocol, independent of whether the app calls toObject().
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class io.grpc.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn io.grpc.**

# Firebase — keep the annotation classes so no false "onCall" style
# reflection targets get stripped.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Kotlin coroutines — the play-services bridge (Task.await()) and Firestore
# callback flows use reflection to inspect suspend function continuations.
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
