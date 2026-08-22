package org.telegram.lottie

import org.gradle.api.provider.Property

abstract class LottieMetaExtension {
    // Target package for the generated class. Defaults to the variant namespace.
    abstract val packageName: Property<String>

    // Simple name of the generated class.
    abstract val className: Property<String>
}
