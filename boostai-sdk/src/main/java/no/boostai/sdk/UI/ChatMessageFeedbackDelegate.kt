package no.boostai.sdk.UI

/**
 * Lets a message part show the thumbs up/down row for its message.
 *
 * [ChatMessagePartView] implements it. A host-supplied part view returned from
 * [ChatViewFragmentDelegate.getChatMessagePartView] can implement it too: when the view is the
 * last element of a message that allows feedback, [ChatMessageView] calls [showFeedbackButtons]
 * on it once the element has been revealed — the same contract the deprecated
 * [IChatMessagePartFragment.showFeedbackButtons] hook offered on the fragment path.
 */
interface ChatMessageFeedbackDelegate {
    fun showFeedbackButtons()
}
