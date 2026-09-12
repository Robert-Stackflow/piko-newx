# Experimental List reading-position repair

Target: original X `12.22.0-prod.01`, base APK SHA-256
`51d43aec02604d097677d0cfc68c7639046ceed6b007610aee143c007979b48f`.
Piko source remains pinned to `bc03fce4352bcb7ee89299722d0e900af3fb8a82`.
No X upgrade, video-setting change, Following/profile behavior change, or stable release replacement.

Additional requested features: three independently persisted, default-on repost visibility toggles
for For You, Following (including ranked Following), and Lists. They affect display only and preserve
quote posts, cursors, repository data, module metadata and conversation ID consistency. Search,
profiles, bookmarks and other timeline types are controls and remain unchanged.

Home pinned List tab logos are hidden in both home renderers. This is scoped by the actual
ListPinnedTimeline subtype; names, routes, sorting controls, community/topic logos, For You and
Following remain untouched. A native tab logo bridge returns null only for a pinned List.

## Observed bytecode contract

Reconnaissance names below document this APK, not patch-time identity anchors:

- `com.x.urt.y.d` returns a two-int ScrollPositionHolder after policy gates and an enum-only native map lookup.
- `y.i` extracts the holder before a native save policy gate. The native map key does not distinguish Lists.
- `n1.d` returns TimelineIdentifier; its String value includes LIST_POSTS and the actual List ID.
- Repository `v.l` constructs the data-merge coroutine `f1` with request mode at argument 6.
  The original request is already saved in the outer continuation; after construction it is reloaded
  from the original parameter/continuation. Changing only the constructor argument preserves request
  completion/error classification. The coroutine emits viewport-aware merge metadata and retains old items.
- `g1.c` is the native volatile viewport-items cache, not an automatically populated timeline cache.
  Its sole producer is repository `v.h(above, visible, below)`, originally gated to FOR_YOU only.
  This missing producer made the listfix.2/.3 merge eligibility check always false on Lists.
  listfix.4 opts enabled LIST_POSTS into that existing producer; unrelated types retain the exact native path.
  Empty/null viewport content still uses the original merge mode.
- Result collector `urt.i.emit` consults a boolean policy then checks PULL_TO_REFRESH before sending
  an automatic scroll-to-top command. Only that policy result is overridden for Lists.

## Patch constraints

All class, field and method references are resolved from semantic strings, interfaces, field types,
constructor parameters, enum names and verified instruction sequences. Required anchors must be unique.
Register layouts are asserted. There is no runtime reflection of obfuscated classes.

- Store each List's index and pixel offset by native identifier in a separate preferences file.
- Bypass the native type-only map for handled List save/restore events, including cache misses.
- Only populated LIST_POSTS head-refresh merges (AUTO_REFRESH / PULL_TO_REFRESH with null cursor)
  use VIEWPORT_AWARE_AUTO_REFRESH. Outer request type, spinner completion, errors and paging stay native.
- Non-List timelines, initial empty loads, cursor pagination and explicit scroll-to-top actions keep
  their original behavior. Existing home patches remain unchanged.
- A dedicated Chinese/English Timeline toggle defaults on. Turn it off and restart for rollback.
- Existing Restore timeline position toggle is additionally respected for persistent save/restore.

## Limits and device acceptance checklist

### listfix.4 follow-up

The user reported that listfix.2 position retention was ineffective. A diagnostic-only listfix.3
showed successful per-List save/restore but `content=false` for populated List refreshes. Exact DEX
inspection identified the FOR_YOU-only producer described above. listfix.4 adds a three-instruction
List opt-in before that gate, preserving the original producer bytecode and its branch behavior.

Source: `e6e3a40`, successful Actions run `34686575530`; version `3.10.6-zh.2-listfix.4`.
Final APK SHA-256: `ddb5ea2c614c05105140be99f13b1dd90701656c909b070e9d2caa43c0f582de`.
Installed over the previous app without clearing data. 36 patches, 364 Chinese strings, same signer,
16 KiB alignment, 12 unit-test cases, 93 final-DEX assertions and all 9 isolated ART checks passed.

Observed on device:
- List AUTO_REFRESH now reports populated viewport content and an eligible preservation merge.
- A controlled List switch away/back showed the same post text fingerprint at identical bounds.
- Another List, saved at index 10 / offset 909, restored after process restart to the same text
  fingerprints at identical bounds. An additional List's restored offset stayed unchanged while
  its index increased after head content was merged.
- No new app crash was recorded for the two tested listfix.4 processes.
- Some earlier/later UI sequences overlapped with user operation and are not acceptance evidence.
  The manual-pull attempt did not reach a confirmed PULL_TO_REFRESH event and is not a passed test.
  Large refreshes, error recovery and cache eviction still require further usage validation.

Local diagnostics log only position integers, refresh policy facts and hashed List identifiers.
Device dumps, screenshots and logs stay outside the repository and must not be uploaded.

### Earlier candidate history

The first `listfix.1` candidate was rejected by Android 16 at startup: the position bridge joined
an int-array-typed null path with a holder path at one return, which ART inferred as Object.
`listfix.2` uses a separate explicit null return before constructing the holder. The previous
stable APK was restored immediately without clearing data. Do not install `listfix.1`.

The corrected `listfix.2` APK was installed on the connected Android 16 phone with the existing
signature and without clearing data. The isolated ART probe passed all nine target classes (and
rejected the known-bad `listfix.1` negative control). Startup and populated home content were observed;
both pinned List labels were present as text-only. No new APP CRASH exit was recorded after the
corrected installation. The device was then being operated by the user, so controlled refresh,
multi-list switching, error recovery and repost-toggle UI tests are not claimed as completed.

This remains an experimental reading-position fix. Persistent restoration stores index/offset,
not a tweet ID; it cannot guarantee the same
post after cache eviction, deletions, a large content replacement or process death with changed data.
List IDs are separate; the same shared List uses the same position across accounts on the device.

Build source: `2e53156`, Actions run `34685290581`. Final APK SHA-256:
`1c5f958c3f540ffca4211e1abb702b5ee904da660cd0f806decb134777fb49bb`.
36 applied patches, 364 verified Simplified Chinese strings, original signer and 16 KiB alignment.
87 final-DEX assertions passed. Local helper tests cover 170 position-policy/persistence checks and
73 repost filtering checks. `tests/test_list_fix.py` additionally locks the separate typed-null return.

Before promoting: test two pinned Lists with different positions, pull refresh with new posts, no new
posts, network errors, tab switches, background/foreground and process restart; first entry into empty
and new Lists; older pagination; explicit top action; Following/profile controls; and the existing
video-swiping setting. Refresh indicators must stop and old items must not disappear or duplicate.
