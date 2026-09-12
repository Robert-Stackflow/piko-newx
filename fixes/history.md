# Historical List repair and verification records

Archived during repository cleanup on 2026-09-12. Descriptions below belong to the named build,
not necessarily the current implementation. In particular, ordinal persistence and refresh merging
describe earlier candidates. See [README.md](README.md) for current behavior and module ownership.
Commit IDs are the original build revisions before rebase; they remain artifact provenance.

## listfix.6.2 identity-based UI restoration: original build and follow-up

Source `40184c3`, successful Actions run `34689191257`.
Version `3.10.6-zh.2-listfix.6.2`; APK SHA-256:
`c825e24b10b3b0296b513814deff843658cf2c03e8c7e0b5ec32a2f6b43f4c27`.

The identity-based implementation is now present: account + List scoped persistence, final
RegularItem UI key + offset, unique matching only, gesture/top cancellation and no old-item reinsertion.
Final provider traversal and all snapshot reads run outside Compose's provider calculation.
Unknown keys and missing/deleted targets are not converted into guessed ordinal positions.

Verification: 14 unit-test cases (225 identity-state checks, 15 observer/lifecycle/race checks,
15 obsolete-ordinal policy checks, 73 repost checks), 226 final-DEX assertions, all 14 initialized
ART class probes, 364 Chinese resources, 36 patches, original signer and 16 KiB alignment passed.
Repository merge methods and the native pull-result collector remain instruction-identical to stable.

Installed over listfix.5 without clearing data. Cold startup succeeded. During initial device use,
multiple List scopes bound and processed data; the process remained alive and no new crash/ANR was
recorded in the checked interval. Unexpected page/drag activity made those sequences unsuitable as
controlled acceptance evidence; its source was not established. ADB then disconnected before a
fresh controlled run could complete. That initial run was INCOMPLETE.

Controlled follow-up on 2026-09-12, 20:01-20:05 local time: three pinned List tabs were exercised
without a new ANR or crash in the checked processes. Seven fresh-XML comparisons matched visible
text and bounds, covering List return, two process restarts at different reading positions,
manual scrolling without snapback, and an at-top pull whose settled visible posts were unchanged.
The pull was followed by a new provider generation. This is basic device acceptance, not proof of
preservation when new posts arrive ahead of a target. Initial data replacements logged missing full
UI keys; those logs cannot distinguish removed posts from changed keys. No fallback request was
issued for those missing-key generations. The last system ANR remained the older candidate's event.
Large refreshes, pagination, network failures, explicit-top cancellation, account/filter changes
and new-head-content preservation still require device acceptance. Keep this experimental;
do not promote it as fully verified. Known safe fallback is listfix.5. Device content stays local.

Rejected intermediate candidates:

- listfix.6: R8-inlined List state getters bypassed scope registration. listfix.6.1 binds through the
  delegated, non-inlined position getter instead.
- listfix.6.1: user reproduced List-switch ANR (input dispatch timeout). Rolled back to listfix.5.
  Reading LazyList snapshots inside provider calculation created a dangerous cyclic dependency;
  listfix.6.2 defers those reads and has a regression test that fails on reads during construction.
  ANR Java stacks were not obtained, so this source-level finding is not proof all ANR causes are gone.

See [position-restoration-design.md](position-restoration-design.md) for contracts and remaining limits.

## Historical baseline: listfix.5 rollback

The user rejected listfix.4 after observing old posts mixed into refreshed content. Its earlier
limited viewport tests below are historical evidence, not functional acceptance. Do not promote it.

listfix.5 removes the refresh-mode override, List viewport-cache producer opt-in, and pull-result
scroll-to-top guard. Native refresh is restored; localization, per-timeline repost visibility,
text-only List tabs, and the limited per-List ordinal store remain. Existing data is not cleared.

Source: `c742eff`; Actions run `34687319570`; version `3.10.6-zh.2-listfix.5`.
The replacement identity-based UI-only approach is documented in
[position-restoration-design.md](position-restoration-design.md). It is not shipped in listfix.5.

Rollback APK SHA-256: `c6cc4eb336df5d25ca40ba11cc2cc8dde5c9e711128be5f79c9a1159a62bf75c`.
36 applied patches, 364 Chinese resources, matching signer and 16 KiB alignment verified.
12 unit-test cases, 173 final-DEX assertions and 9 initialized ART class checks passed.
The native merge constructor path, viewport producer, pull-result collector and merge engine
match the stable APK's instruction streams. Repost and text-only tab bindings remain present.
Installed over the previous build without clearing app data; cold startup succeeded.
This is rollback/startup verification, not acceptance of the future identity-based restoration.

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
  Historical listfix.4 opted enabled LIST_POSTS into that existing producer; listfix.5 removes this.
  Empty/null viewport content still uses the original merge mode.
- Result collector `urt.i.emit` consults a boolean policy then checks PULL_TO_REFRESH before sending
  an automatic scroll-to-top command. listfix.5 no longer overrides this policy result.

## Patch constraints

All class, field and method references are resolved from semantic strings, interfaces, field types,
constructor parameters, enum names and verified instruction sequences. Required anchors must be unique.
Register layouts are asserted. There is no runtime reflection of obfuscated classes.

- Store each List's index and pixel offset by native identifier in a separate preferences file.
- Bypass the native type-only map for handled List save/restore events, including cache misses.
- All refresh merge modes, viewport producer gates and pull-result top actions remain native.
- A dedicated Chinese/English Timeline toggle defaults on for the per-List ordinal store only.
  It does not promise identity preservation after content changes. Turn it off and restart to disable it.
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
