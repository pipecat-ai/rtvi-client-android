package ai.rtvi.client.types

import ai.rtvi.client.helper.RTVIClientHelper

internal class RegisteredHelper(
    val helper: RTVIClientHelper,
    val supportedMessages: Set<String>
)