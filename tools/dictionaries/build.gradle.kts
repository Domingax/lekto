plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

application {
    mainClass.set("app.lekto.tools.dictionaries.MainKt")
}
