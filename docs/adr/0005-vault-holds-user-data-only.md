# The vault holds user-authored data only

The vault contains what the user authored or imported: vocabulary, reading progress,
book originals. **Derived assets** — parsed book text, the dictionary pack — live in a
device-local store excluded from sync, and are re-derived or re-downloaded on demand.

We rejected syncing everything: a dictionary pack is hundreds of megabytes and
identical on every device, and a parsed-text cache is reproducible from the original.
Keeping the vault small keeps syncing cheap, and it makes the "no folder available"
fallback natural, since a device-local cache needs no folder at all.