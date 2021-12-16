# BoostAI SDK

## Table of Contents
* [License](#license)
* [Installation](#installation)
* [Frontend/UI](#frontendui)
    * [ChatBackend](#chatbackend)
    * [Config](#config)
    * [Display the chat](#display-the-chat)
        * [Floating avatar](#floating-avatar)
        * [Docked chat view (in a tab bar)](#docked-chat-view-in-a-tab-bar)
        * [Modal chat view](#modal-chat-view)
    * [ChatViewFragment](#chatviewfragment)
       * [Colors](#colors)
    * [Customize responses (i.e. handle custom JSON responses)](#customize-responses-ie-handle-custom-json-responses)
* [Backend](#backend)
    * [Subscribe to messages](#subscribe-to-messages)
    * [Subscribe to config changes](#subscribe-to-config-changes)
    * [Commands](#commands)
    * [Post](#post)
    * [Send](#send)

## License

The SDK is licensed under the [GNU General Public License (GPLv3)](https://www.gnu.org/licenses/gpl-3.0.html).

A commercial license will be granted to any Boost AI clients that want to use the SDK.

## Installation

### Gradle

Add the JitPack repository to the list of repositories in your root `build.gradle` file:

```kotlin
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
```

Add the boost.ai SDK library as a dependency in your app `build.gradle` file:

```kotlin
dependencies { 
  implementation 'com.github.BoostAI:mobile-sdk-android:1.0.4'
}
```

### Maven

Add the JitPack repository to your build file:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>com.github.BoostAI</groupId>
    <artifactId>mobile-sdk-android</artifactId>
    <version>1.0.4</version>
</dependency>
```


## Frontend/UI

The UI library is developed with two main goals:

1. Make it "plug and play" for normal use cases
2. Make it easy to extend and customize by exposing configurable variables and opening up for subclassing

See the `app` folder for a demo of how to set up chat view via a floating avatar or a chat view under a tab in a tab bar.

### ChatBackend

To start off, we need to configure the ChatBackend singleton object:

```kotlin
ChatBackend.domain = "sdk.boost.ai"
ChatBackend.languageCode = "no-NO" // Default value – will potentially be overridden by the backend config
```

### Config

Almost all of colors, string and other customization is available in a `Config` object that comes from the server. The config object can be accessed at any point later through the `ChatBackend.config` property. Before we display the chat view, we should wait for the config to be ready. This can be done by calling `ChatBackend.onReady` with a listener:

```kotlin
ChatBackend.onReady(object : ChatBackend.ConfigReadyListener {
    override fun onFailure(exception: Exception) {
        // Handle exception
    }

    override fun onReady(config: ChatConfig) {
        // Start our conversation
    }
})
```

### Display the chat

#### Floating avatar

To set up a floating avatar, you can do something along the lines of:

`floating_avatar_fragment.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/agent_avatar_fragment"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```

```kotlin
class FloatingAvatarFragment : Fragment(R.layout.floating_avatar_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ChatBackend.onReady(object : ChatBackend.ConfigReadyListener {
            override fun onFailure(exception: Exception) {
                // Handle exception
            }

            override fun onReady(config: ChatConfig) {
                childFragmentManager
                    .beginTransaction()
                    .add(
                        R.id.agent_avatar_fragment,
                        AgentAvatarFragment(R.mipmap.agent, customConfig)
                    )
                    .commitAllowingStateLoss()
            }
        }
    }

}
```

The `AgentAvatarView` class handles tapping on the icon and displaying the default `ChatViewController`. You may configure the `AgentAvatarView` by passing an `UIImage` to the `avatarImage` property, and optionally override the default `avatarSize` (which is 60 points).

#### Docked chat view (in a tab bar)

To display the `ChatViewFragment` in a custom activity or in a tab bar:

```kotlin
val chatViewFragment = ChatViewFragment()
// Add to childFragmentManager or to a view pager
```

#### Modal chat view

To present the chat in a new activity:

```kotlin
Intent(mContext, ChatViewActivity::class.java).let { intent ->
    intent.putExtra(ChatViewActivity.IS_DIALOG, true)
    
    // Pass custom config if needed
    /*customConfig?.let {
        config -> intent.putExtra(ChatViewActivity.CUSTOM_CONFIG, config)
    }*/
    
    startActivity(intent)
}
```

### ChatViewFragment

The `ChatViewFragment` is the main entry point for the chat view. It can be subclassed for fine-grained control, or you can set and override properties and assign yourself as a delegate to configure most of the normal use cases.

#### Colors

Colors will normally be configured server-side, and all of the Boost UI view will use these colors by default. We encourage you to configure colors server-side to get a consistent color palette across platforms, but if you want to override these colors in your app, you can pass a custom config object to the `ChatViewFragment`.

The order of precedence for colors is (1) custom config color, (2) server config color, and (3) default embedded SDK colors. Example of precedence: (1) custom config `primaryColor`, (2) server config `primaryColor` and (3) `R.color.primaryColor` from `colors.xml`.

Please note that all colors are `ColorInt`s, and resource colors must be converted to color integers (i.e. `ContextCompat.getColor(requireContext(), R.color.primaryColor)`).

```kotlin
val customConfig = ChatConfig()
customConfig.primaryColor = getColor(R.color.red)
val chatViewFragment = ChatViewFragment(customConfig = customConfig)
```

### Customize responses (i.e. handle custom JSON responses)

If you want to override the display of responses from the server, you can assign yourself as a `ChatViewFragmentDelegate`:

```kotlin
val chatViewFragment = ChatViewFragment(delegate = this)
```

In order to display a view for a custom JSON object, return a view for the `json` type (return nil will lead the `ChatResponseView` to handle it).

Use the `JSONDecoder` to parse the JSON with a custom class describing the JSON object, i.e. for the "genericCard" default template, where the object looks like: 


```json
{
  "body": {
    "text": "This is the logo for the worlds best football club."
  },
  "heading": {
    "text": "UNITED"
  },
  "image": {
    "alt": "Photo of product",
    "position": "top",
    "url": "https://cdn.united.no/uploads/2020/09/kenilworthroad220720.jpg"
  },
  "link": {
    "text": "More information",
    "url": "https://united.no"
  },
  "template": "genericCard"
}
```

Define a `Serializable` class that matches the data:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
class GenericCard {

    @Serializable
    data class TextContent (
        val text: String
    )

    @Serializable
    data class Image (
        val url: String?,
        val alt: String? = null,
        val position: String? = null,
    )

    @Serializable
    data class Link (
        val text: String?,
        val url: String
    )

    val body: TextContent? = null
    val heading: TextContent? = null
    val image: Image? = null
    val link: Link? = null
    val template: String? = null
}
```

Return a view that displays the data:

```kotlin
class MyChatViewFragmentDelegate : ChatViewFragmentDelegate {
    override fun getChatMessagePartFragment(
        element: Element,
        responseId: String?,
        animated: Boolean
    ): Fragment? {
        when (element.type) {
            ElementType.json -> {
                element.payload.json?.let {
                    try {
                        val genericCard =
                            Json.decodeFromJsonElement<GenericCard>(it)
                        return MyCustomJsonFragment(genericCard)
                    } catch (e: SerializationException) {
                        return null
                    }
                }
            }
        }

        return null
    }

    ...
}
```

If you want more control, you can return a `Fragment` subclass from the `getChatMessageFragment(response: Response, animated: Boolean)` method or the `getChatMessagePartFragment(element: Element, responseId: String?, animated: Boolean = true)` method, which will be called for each message that arrives from the server or the user:

```kotlin
class MyClass: ChatViewFragmentDelegate {

    fun getChatMessageFragment(response: Response,
                               animated: Boolean): Fragment? {
        ...
    }

    fun getChatMessagePartFragment(element: Element,
                                   responseId: String?,
                                   animated: Boolean = true): Fragment? {
        ...
    }
    
    ...
}
```

## Backend

The `ChatBackend` class is the main entry point for everything backend/API related. As a minimum, it needs a domain to point to:

```swift
ChatBackend.domain = "sdk.boost.ai" // Replace with a possible custom domain
```

If you use the `ChatBackend` outside of the provided UI classes, always start by calling `ChatBackend.getConfig(configReadyListener: ConfigReadyListener? = null)` to get the server config object, which has colors and string etc. that is needed for the UI.

### Subscribe to messages

The easiest way to use the backend for your own frontend needs is to subscribe to messages:

```kotlin
class MyFragment : Fragment, ChatBackend.MessageObserver {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ChatBackend.addMessageObserver(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeMessageObserver(this)
    }
    
    // Implement observer methods
    override fun onMessageReceived(backend: ChatBackend, message: APIMessage) {
        handleReceivedMessage(message)
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
        showStatusMessage(error.localizedMessage ?: getString(R.string.unknown_error), true)
    }
```

### Subscribe to config changes

The server config might be updated/changed based on the user chat. If the user is transferred to another virtual agent in a VAN (Virtual Agent Network), i.e. the user is transferred to a virtual agent specialized in insurance cases after the user asks a question regarding insurance, the virtual agent avatar might change, and the dialog colors change etc.

Subscribe to notifications about config changes and update UI styling accordingly:

```kotlin
class MyFragment : Fragment, ChatBackend.ConfigObserver {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ChatBackend.addConfigObserver(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
    }
    
    // Implement observer methods
    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        updateStyling(config)
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
        showStatusMessage(error.localizedMessage ?: getString(R.string.unknown_error), true)
    }
```

### Commands

All commands available in the API is accessible in the `Commands.kt` file, with the `Command` enum describing them all:

```kotlin
@Serializable
public enum Command: String, Codable {
    enum class Command {
    START,
    POST,
    DOWNLOAD,
    RESUME,
    DELETE,
    FEEDBACK,
    TYPING,
    POLL,
    POLLSTOP,
    STOP,
    LOGINEVENT,
    SMARTREPLY,
    HUMANCHATPOST,
    CONFIG
}
```

You'll find all the commands on the `ChatBackend` object. Almost all of them can be called without a parameter, which will use the
default value, or you can add the message definition from `Commands.kt`.

```kotlin
ChatBackend.start()
ChatBackend.start(CommandStart())
ChatBackend.start(CommandStart(filterValues = asList("test")))

ChatBackend.stop()
ChatBackend.resume()
ChatBackend.delete()
ChatBackend.poll()
ChatBackend.pollStop()
ChatBackend.smartReply(CommandSmartReply(value = "test"))
ChatBackend.humanChatPost(CommandHumanChatPost(value = "test"))
ChatBackend.typing()
ChatBackend.conversationFeedback(CommandFeedback(value = CommandFeedbackValue(1, "Feedback")))
ChatBackend.download()
ChatBackend.loginEvent(CommandLoginEvent())
```

### Post

Post is a bit different. You should try to avoid using the internal `ChatBackend.post(data: JsonElement, url: URL? = null, listener: APIMessageResponseListener? = null, responseHandler: APIMessageResponseHandler? = null)` command, and instead use the predefined commands:

```kotlin
ChatBackend.actionButton(id = "action_id")
ChatBackend.message(value = "My message")
ChatBackend.feedback(id = "message_id", value = FeedbackValue.positive)
ChatBackend.urlButton(id = "message_id")
ChatBackend.sendFiles(files = asList(File( ...)))
ChatBackend.triggerAction(id = "action_id")
ChatBackend.smartReply(value = "reply_id")
ChatBackend.humanChatPost(value = "Human chat message")
ChatBackend.clientTyping(value = "text")
ChatBackend.conversationFeedback(rating = 1, text = "My feedback")
ChatBackend.loginEvent(userToken = "user_token")
```

### Send

You can also send a command with `send()`. You can use the predefined commands, which should include all the ones the server support,
or define your own if you have a server which support more. The command must then conform to the `ICommand` interface and
`@Serializable`.

```kotlin
@Serializable
sealed class ICommand {
    @Required
    abstract val command: Command
}
```

To send a command:

```kotlin
ChatBackend.send(CommandStart())
```

The result will be received throught the regular publish/subscribe methods described above.

Or if you want to directly take control over the result, use the callback.

```kotlin
ChatBackend.send(CommandStart(), object : ChatBackend.APIMessageResponseListener {
    override fun onFailure(exception: Exception) {
        TODO("Not yet implemented")
    }

    override fun onResponse(apiMessage: APIMessage) {
        // Handle the response
    }

})
```
