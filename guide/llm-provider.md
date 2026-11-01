# LLM provider and privacy

You can connect your own **LLM provider** to translate a selected phrase or
sentence in context, directly from the device. Lekto asks it for your
**Translation language** (chosen on the **Languages** surface).

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

## Choosing the model

Open the **LLM provider** screen from Settings. Pick your provider first: Lekto
asks it for the models it offers and lists them, so you never type a name from
memory. Then open the **Model** field — its list drops straight out of the field,
and you can still type a name yourself. The model applies as you choose it. If you
have just entered your API key, opening the field fetches the list with that key.
The list is device-local and is not stored in your Vault.

The key is the one thing the screen does not write as you type: press
**Connection**. Lekto tests the provider first and stores the key only when it
answers, so a wrong key never replaces a working one. Once a key is stored, the
field shows masked dots instead of staying empty; type a new key to replace it.

OpenCode Zen and OpenCode Go publish their list, so it loads without a key. A
provider that has no model list — or answers in a way Lekto cannot read — is
reported inline, and the **Model** field stays for you to type the name instead. If
your provider needs a key, a discreet note under the field reminds you to enter it.

## Testing the connection

**Connection** checks that a provider works before you rely on it. A rejected key
is reported inline, and nothing is stored.

## When no provider is connected

You do not need a key to look a phrase up. With no provider connected, the panel
offers a **Translation shortcut**: an outbound link that opens an external
translation service with your selected phrase already filled in. Like a
**Dictionary shortcut**, Lekto never embeds or scrapes the service. See
[Settings and FAQ](/settings-and-faq) for where to connect a provider.

The shortcut translates into your **Translation language**, so it appears once you
have chosen one on the **Languages** surface.

## Closing the panel

Close the translation panel with its close icon, or by tapping the page outside
it. A tap outside the panel only closes it: it does not look up the word under
your finger or turn the page.
