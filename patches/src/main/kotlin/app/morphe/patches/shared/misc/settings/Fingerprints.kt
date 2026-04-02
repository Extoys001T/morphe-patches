package app.morphe.patches.shared.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.extension.UTILS_EXTENSION_CLASS_DESCRIPTOR
import com.android.tools.smali.dexlib2.AccessFlags

// TODO: Refactor these out of Utils?
internal object ThemeLightColorResourceNameFingerprint : Fingerprint(
    definingClass = UTILS_EXTENSION_CLASS_DESCRIPTOR,
    name = "getThemeLightColorResourceName",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf()
)

internal object ThemeDarkColorResourceNameFingerprint : Fingerprint(
    definingClass = UTILS_EXTENSION_CLASS_DESCRIPTOR,
    name = "getThemeDarkColorResourceName",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)

internal object RecommendedAppVersionUtilsFingerprint : Fingerprint(
    definingClass = UTILS_EXTENSION_CLASS_DESCRIPTOR,
    name = "getRecommendedAppVersion",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf()
)
