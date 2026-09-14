package no.boostai.sdk.UI.Events

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object BoostUIEvents {
    // CopyOnWriteArrayList so a host observer reacting to an event (say, chatPanelClosed) can
    // remove itself from inside the callback without a ConcurrentModificationException
    private val observers = CopyOnWriteArrayList<WeakReference<Observer>>()

    fun addObserver(observer: Observer) {
        // Deduped and swept: the chat re-registers its observer on every start() (retry
        // included), and collected references were never pruned
        observers.removeAll { it.get() == null || it.get() === observer }
        observers.add(WeakReference(observer))
    }

    fun removeObserver(observer: Observer) {
        observers.removeAll { it.get() == null || it.get() === observer }
    }

    fun notifyObservers(event: Event, detail: Any? = null) {
        observers.forEach { observer ->
            observer.get()?.onUIEventReceived(event, detail)
        }
    }
    
    interface Observer {
        fun onUIEventReceived(event: Event, detail: Any?)
    }

    enum class Event {
        chatPanelOpened,
        chatPanelClosed,
        chatPanelMinimized,
        conversationIdChanged,
        messageSent,
        menuOpened,
        menuClosed,
        privacyPolicyOpened,
        conversationDownloaded,
        conversationDeleted,
        positiveMessageFeedbackGiven,
        negativeMessageFeedbackGiven,
        positiveConversationFeedbackGiven,
        negativeConversationFeedbackGiven,
        conversationFeedbackTextGiven,
        actionLinkClicked,
        externalLinkClicked,
        conversationReferenceChanged,
        filterValuesChanged,
    }
}