# LLM provider and privacy

You can connect your own **LLM provider** to translate a selected phrase or
sentence in context, directly from the device.

## Bring your own key

Lekto is bring-your-own-key: you connect one provider with your own **API key**.
There is no Lekto server in between — Lekto calls the provider directly. Any
service that speaks the OpenAI chat-completions API works through the **Custom**
preset.

## Your key stays secret

Your API key is kept in the platform's **Secret store** (the keychain or keystore
on your device). It never enters the **Vault**, never appears in an export, and
never appears in a log.

The provider's name, model and base URL are app-private settings outside the Vault.
Because the key is device-local, you configure the provider again on each new
device.

## Testing the connection

Settings offers a connection test, so you can check that a provider works before you
rely on it. A rejected key is reported inline.

## When no provider is connected

You do not need a key to look a phrase up. With no provider connected, the panel
offers a **Translation shortcut**: an outbound link that opens an external
translation service with your selected phrase already filled in. Like a
**Dictionary shortcut**, Lekto never embeds or scrapes the service. See
[Settings and FAQ](/settings-and-faq) for where to connect a provider.
