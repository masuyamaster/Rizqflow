// Plugin dideklarasikan di sini (apply false) supaya versinya satu tempat; tiap modul menerapkan sendiri.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
