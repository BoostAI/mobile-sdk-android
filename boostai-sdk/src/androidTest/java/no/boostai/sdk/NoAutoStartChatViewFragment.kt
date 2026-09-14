package no.boostai.sdk

import no.boostai.sdk.UI.ChatViewFragment

/**
 * A ChatViewFragment that never auto-starts the conversation — including after the
 * FragmentManager re-instantiates it on a configuration change, which a plain
 * `ChatViewFragment().apply { startConversationOnLoad = false }` cannot guarantee.
 */
class NoAutoStartChatViewFragment : ChatViewFragment() {
    init {
        startConversationOnLoad = false
    }
}
