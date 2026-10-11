# Sync runs automatically while it is on, under a bounded schedule

ADR-0023 settled where sync's configuration lives, and ADR-0027 made a steady
run cheap — one `PROPFIND` plus a `GET` per new or changed item. What neither
settled is **when** a run happens. Until now sync ran only on an explicit act:
`SyncController.enable()` → `syncNow()` and the **Sync now** button. A learner
on two devices had to remember to sync by hand, which defeats the feature on
exactly the devices it exists for. This is issue #121.

The tension is ADR-0009 / D9's promise that sync is an **explicit, visible act**:
the user turns it on, and its status and last outcome stay shown. Automatic
running must not turn sync into a hidden background process. So the decision is
narrow: **once sync is on, it runs on its own**; it is never on without the
user's act, and it never stops reporting.

## The trigger set

Automatic sync arms only when the user has turned sync on, and it has four
triggers:

- **App start** — one run as the app composes, so a return to the app reconciles
  before the user reads.
- **Periodic** — a run at a bounded interval for as long as the app is open.
- **Resume** — a run when the app returns to the foreground, raised by the
  platform through the composition root's `onResume` signal (desktop: window
  focus; a platform with no lifecycle leaves it empty and the periodic trigger
  still applies).
- **Local change (push)** — a run after the user changes the vault: a book
  imported, a word saved or deleted, or a reading position moved. This is the
  trigger that makes sync feel immediate on one device instead of waiting for the
  timer.

A manual **Sync now** is the same gate, so it can never race the automatic runs.

## Push on a local change, with a settle

The local writers — the library's import, the vocabulary's save and delete, and
the reading-progress writer — raise a shared `LocalChanges` signal (content
changes and reading-progress changes are two flows, because their tempos differ),
and the sync controller subscribes. The signal is deliberately **not** emitted by
the vault: the sync engine writes to the vault during a run, so a vault-level
signal would make every sync trigger the next one.

A change pushes a run only after it has settled, so a burst is one run, not one
per edit or per page:

- a **content** change (book, word) settles for `contentChangeDelay` (5 seconds)
  — long enough to coalesce a save and a follow-up mastery tweak;
- a **reading-progress** change settles for `progressChangeDelay` (30 seconds) —
  a page turn must not sync on its own, and a reader who keeps turning pages
  gives the timer's fallback the job of catching up.

A change raised while sync is off is ignored, and turning sync off stops the
subscribers as well as the timer.

## Runs never overlap; triggers coalesce

Every trigger funnels through one gate that holds a single run at a time. A
trigger raised while a run is in flight sets one pending flag, and the run's
completion starts **at most one** follow-up. A burst of triggers is therefore
one extra run, never a storm, and the section's `syncing` state is honest because
there is only ever one run.

## The interval and its backoff are bounded

The periodic wait starts at `SyncSchedule.baseInterval` (15 minutes) and doubles
after each **consecutive failure**, capped at `maxInterval` (1 hour); a success
resets it. Fifteen minutes bounds the load a large vault puts on its target —
ADR-0027 already made a no-op run one listing, but a listing a second would
still be abuse — and the doubling means a target that is reachable but broken is
polled *less*, not more, until it recovers. A failure is retried on the next
trigger and its plain-language line is kept; it is never silently swallowed. The
same `SyncSchedule` carries the two change settles above, so every timing policy
is one pure value.

## The last outcome is device-local

The last outcome is persisted as one JSON document, `sync-outcome.json`, beside
`sync-settings.json` and `tombstones.json`, through a `SyncOutcomeStore` seam in
`core` (the `SyncSettingsStore` / `TombstoneStore` pattern). It is **not** the
**Vault** (ADR-0005): it is bookkeeping, it must not appear in an export, and it
must not travel to another device. An absent or unreadable document reads as "no
outcome yet", so a corrupt cache costs a blank line, never a crash. Turning sync
off stops every trigger; disconnecting also clears the stored outcome.

## Consequences

- With sync on, a device stays reconciled without a manual press — pushed by the
  user's own changes and pulled by the timer — and the last outcome is visible
  from the first frame after a restart.
- Provider load is bounded by the interval, the cap and the change settles, so a
  vault of thousands of records is not hammered and a page turn is not a request;
  a failing target is backed off.
- The scheduling stays in the sync state holder (`SyncController`), which owns
  the coroutine scope and subscribes to `LocalChanges`; the timing policy is a
  pure `SyncSchedule` in `core`, so the backoff and the settles are tested as a
  function and the state holder is tested under `runTest`'s virtual time.
- **Android** is desktop-first: the platform's background-execution limits need
  WorkManager and a wakeup policy, which is left to #116's follow-up. The
  in-process triggers (start, resume, periodic-while-open and local changes) are
  shared, so the Android client gets them when it is foregrounded without new
  platform work.

## Rejected

- **A background service or OS scheduler as the mechanism** — platform-specific,
  battery-expensive, and unnecessary while the app is open; it would also make
  the run invisible, against ADR-0009's explicit-act promise.
- **An unbounded or very short interval** — hammering the target is the failure
  this feature would otherwise introduce; the cap and the doubling bound it.
- **Syncing on every local change with no settle** — a page turn would be a
  request, and a run per keystroke of a mastery slider a burst; the settles and
  the coalescing gate bound it.
- **Emitting the change signal from the vault** — the sync engine writes to the
  vault during a run, so that would make every sync trigger the next one; the
  signal is raised by the user-facing writers instead.
- **Overlapping runs (no coalescing)** — two writers on the same vault defeats
  the merge and doubles the load; one run plus one follow-up is enough.
- **Storing the last outcome in the Vault or the `SecretStore`** — it is neither
  user data (ADR-0005) nor a secret (ADR-0021); it belongs beside the sync
  settings, device-locally.
- **An in-memory-only last outcome** — the section would go blank on every
  launch, which is worse than the manual status it replaces.
