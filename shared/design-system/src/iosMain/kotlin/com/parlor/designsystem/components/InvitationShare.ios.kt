package com.parlor.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@Composable
internal actual fun rememberInvitationSharer(): (String) -> Unit = remember { ::showInvitationActivities }

@OptIn(ExperimentalForeignApi::class)
private fun showInvitationActivities(invite: String) {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: return
    val root = scene.keyWindow?.rootViewController ?: return
    val presenter = generateSequence(root) { it.presentedViewController }.last()
    val sheet = UIActivityViewController(activityItems = listOf(invite), applicationActivities = null)
    sheet.popoverPresentationController?.apply {
        sourceView = presenter.view
        sourceRect = presenter.view.bounds
    }
    presenter.presentViewController(sheet, animated = true, completion = null)
}
