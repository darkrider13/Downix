# Downix release (R8) rules. Library consumer rules (Room, OkHttp, WorkManager, Compose) are applied automatically.

# Readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room database is created by reflection
-keep class * extends androidx.room.RoomDatabase

# OkHttp optional platform providers that are not on Android
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Torrent engine (libtorrent4j talks to native code through JNI)
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
