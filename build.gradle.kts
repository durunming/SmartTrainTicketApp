// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // 添加对 Google 服务 Gradle 插件的依赖
    id("com.google.gms.google-services") version "4.4.4" apply false
}