# OkHttp/Okio/Coroutines ship their own consumer rules.
# org.json is used directly (no reflection) - no keep rules needed.
-dontwarn okio.**
-dontwarn javax.annotation.**
