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
- `g1.c` is the native volatile current-items list. Empty/null content must use the original merge mode.
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

This is an experimental patch, not a verified runtime fix. No Android device was connected during
development. Persistent restoration stores index/offset, not a tweet ID; it cannot guarantee the same
post after cache eviction, deletions, a large content replacement or process death with changed data.
List IDs are separate; the same shared List uses the same position across accounts on the device.

Before promoting: test two pinned Lists with different positions, pull refresh with new posts, no new
posts, network errors, tab switches, background/foreground and process restart; first entry into empty
and new Lists; older pagination; explicit top action; Following/profile controls; and the existing
video-swiping setting. Refresh indicators must stop and old items must not disappear or duplicate.
