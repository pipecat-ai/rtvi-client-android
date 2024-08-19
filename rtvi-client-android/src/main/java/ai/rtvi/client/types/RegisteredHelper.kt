package ai.rtvi.client.types

import ai.rtvi.client.helper.VoiceClientHelper

internal class RegisteredHelper(
    val helper: VoiceClientHelper,
    val supportedMessages: Set<String>
)