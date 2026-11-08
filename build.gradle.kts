plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.diffplug.spotless") version "7.0.2"
}

// IntelliJ's style, to match kotlin.code.style=official, with short signatures and one-line
// expression bodies left on one line. Set here because Spotless overrides .editorconfig's style.
val ktlintRules =
    mapOf(
        "ktlint_code_style" to "intellij_idea",
        "max_line_length" to "120",
        "ktlint_standard_function-signature" to "disabled",
        "ktlint_standard_class-signature" to "disabled",
        "ktlint_standard_multiline-expression-wrapping" to "disabled",
    )

// gradlew spotlessApply formats, gradlew spotlessCheck fails on anything unformatted.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.5.0").editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint("1.5.0").editorConfigOverride(ktlintRules)
    }
}
