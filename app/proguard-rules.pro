# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.tweeter042.alarmclock.**$$serializer { *; }
-keepclassmembers class io.github.tweeter042.alarmclock.** {
    *** Companion;
}
# Room
-keep class * extends androidx.room.RoomDatabase
# OkHttp / osmdroid
-dontwarn okhttp3.**
-dontwarn org.osmdroid.**
