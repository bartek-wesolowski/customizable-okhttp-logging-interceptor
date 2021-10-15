Customizable OkHttp Logging Interceptor
===================

An [OkHttp interceptor][interceptors] which logs HTTP request and response data and allows output customization.
The implementation is based on the [Logging Interceptor from OkHttp][okhttp-logging-interceptor].

```kotlin
val loggingInterceptor = CustomizableHttpLoggingInterceptor(
    logHeaders = true,
    logBody = true,
    maxBodyLength = 1000L
)
val client = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .build()
```

To log to a custom location, pass a `Logger` instance to the constructor.
```kotlin
val loggingInterceptor = CustomizableHttpLoggingInterceptor(
    logger = { message -> Timber.tag("OkHttp").d(message) }
)
```

**Warning**: The logs generated by this interceptor when using the `HEADERS` or `BODY` levels have
the potential to leak sensitive information such as "Authorization" or "Cookie" headers and the
contents of request and response bodies. This data should only be logged in a controlled way or in
a non-production environment.

You can redact headers that may contain sensitive information by calling `redactHeader()`.
```kotlin
loggingInterceptor.redactHeader("Authorization")
loggingInterceptor.redactHeader("Cookie")
```

Download
--------
Add JitPack to the repository list:

```groovy
allprojects {
  repositories {
    ...

    maven { url 'https://jitpack.io' }
}
```

```kotlin
implementation("com.github.bartek-wesolowski:customizable-okhttp-logging-interceptor:1.0.0")
```


[interceptors]: https://square.github.io/okhttp/interceptors/
[okhttp-logging-interceptor]: https://github.com/square/okhttp/tree/master/okhttp-logging-interceptor
