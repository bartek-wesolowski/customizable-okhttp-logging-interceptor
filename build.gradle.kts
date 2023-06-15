import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.okhttp)
}

// https://vanniktech.github.io/gradle-maven-publish-plugin/central/
mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01, true)
    signAllPublications()

    coordinates("io.github.bartek-wesolowski", "mockito-kotlin-rx", "2.0.0")

    pom {
        name.set("Customizable OkHttp Logging Interceptor")
        description.set("An OkHttp interceptor which logs HTTP request and response data and allows output customization.")
        inceptionYear.set("2021")
        url.set("https://github.com/bartek-wesolowski/customizable-okhttp-logging-interceptor")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("bartek-wesolowski")
                name.set("Bartosz Wesołowski")
                url.set("https://github.com/bartek-wesolowski/")
            }
        }
        scm {
            url.set("https://github.com/bartek-wesolowski/customizable-okhttp-logging-interceptor")
            connection.set("scm:git:git://github.com/bartek-wesolowski/customizable-okhttp-logging-interceptor.git")
            developerConnection.set("scm:git:ssh://git@github.com:bartek-wesolowski/customizable-okhttp-logging-interceptor.git")
        }
    }
}