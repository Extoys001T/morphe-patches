package app.morphe.patches.youtube.interaction.copyvideourl

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.youtube.layout.buttons.overlay.hidePlayerOverlayButtonsPatch
import app.morphe.patches.youtube.layout.player.buttons.addPlayerBottomButton
import app.morphe.patches.youtube.layout.player.buttons.playerOverlayButtonsHookPatch
import app.morphe.patches.youtube.misc.playercontrols.addLegacyBottomControl
import app.morphe.patches.youtube.misc.playercontrols.initializeLegacyBottomControl
import app.morphe.patches.youtube.misc.playercontrols.injectVisibilityCheckCall
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

private const val EXTENSION_BUTTON = "Lapp/morphe/extension/youtube/videoplayer/CopyVideoURLButton;"

private val copyVideoURLButtonResourcePatch = resourcePatch {
    dependsOn(
        settingsPatch,
        legacyPlayerControlsPatch
    )

    execute {

        copyResources(
            "copyvideourlbutton",
            ResourceGroup(
                resourceDirectoryName = "drawable",
                "morphe_yt_copy.xml",
                "morphe_yt_copy_timestamp.xml",
                "morphe_yt_copy_bold.xml",
                "morphe_yt_copy_timestamp_bold.xml"
            )
        )

        addLegacyBottomControl("copyvideourlbutton")
    }
}

@Suppress("unused")
val copyVideoURLButtonPatch = bytecodePatch(
    name = "Copy video URL",
    description = "Adds options to display buttons in the video player to copy video URLs.",
) {
    dependsOn(
        copyVideoURLButtonResourcePatch,
        hidePlayerOverlayButtonsPatch,
        playerOverlayButtonsHookPatch,
        legacyPlayerControlsPatch,
        videoInformationPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        addPlayerBottomButton(EXTENSION_BUTTON)

        initializeLegacyBottomControl(EXTENSION_BUTTON)
        injectVisibilityCheckCall(EXTENSION_BUTTON)
    }
}
