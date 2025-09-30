package no.boostai.sdk.UI

interface ChatMessageButtonDelegate {
    fun didTapActionButton(fragment: ChatMessageButtonFragment)
    fun enableActionButtons(makeInteractable: Boolean = false)
    fun disableActionButtons()
}