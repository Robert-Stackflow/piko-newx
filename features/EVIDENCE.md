# Media tools — development evidence

Target remains `com.twitter.android`, X `12.22.0-prod.01`, original arm64 APK SHA-256
`51d43aec02604d097677d0cfc68c7639046ceed6b007610aee143c007979b48f`.
Piko source remains `bc03fce4352bcb7ee89299722d0e900af3fb8a82`; upstream builder rebase does not change that pin.

## Native contracts

- Detail component: unique `Focal post id read before the timeline exists` getter; history only uses the focal post found in the displayed success state, with account identity and RESUMED lifecycle. Replies fetched with it are not recorded.
- Current video dispatcher: common event interface linking `PageChanged(page=` and `CurrentMediaChanged(mediaId=`. Current page is derived from the paired index writes, not a literal obfuscated field name.
- Displayed video items are the native viewer's filtered items; media ID must belong to that post. A sole video can be recorded before playback starts. Multi-video posts wait for matching native media selection.
- Progress is independently guarded by native progress post ID and media ID. Adjacent preloaded progress cannot supply another video's resume position.
- Kotlin Duration is decoded through the APK's verified MILLISECONDS conversion, never treated as raw milliseconds.
- Resume dispatches the existing `BarSeekTo(progress=` event once. Manual seeking cancels it; late/mismatched/near-finished saved positions are rejected.
- Speed is already written to `persistent_video_settings_playback_speed` by the native speed-selection handler, which commits Android preferences. Native long-press acceleration is preserved. These are reused, not claimed as newly implemented patches.

Obfuscated `com.x.video.tab.v`, `com.x.postdetail.n`, and `com.x.video.tab.g0` appear only in the isolated ART probe for this frozen APK. Production injection resolves semantic contracts and fails closed.

## Privacy and lifecycle

History defaults off. SQLite files use the app's private no-backup directory; no history export, network sync, DM hook, body logging or token logging. Recording depends on foreground activity/window, interactive device and native component lifecycle. Video is recorded immediately upon foreground selection, including paused videos, per the user's choice.

History clearing invalidates older queued writes. The UI's queue monitor is never held during disk I/O. Playback bookmarks are separately bounded and excluded from history clearing. Download tasks track only explicit Piko task IDs, not unrelated system downloads. Clearing completed records does not delete media files.

## Validation state

- 23 local tests passed, including 24 immediate-view checks and 20 resume guards, plus existing list restoration/repost regression tests.
- dev1 compiled in Actions but failed patch loading on an invalid resource prefix. Fixed without weakening upstream validation.
- dev2 failed Java compilation due to a package-name/local-variable collision. Fixed.
- dev3 compiled but stopped during APK patching on a cloned model adapter signature comparison. Descriptor comparisons now use string values.
- dev4 applied all 37 selected patches to the frozen X APK, including every new semantic bridge; final resource/DEX/ART validation follows.
- dev5 also fixes explicit retry after a missing system download and persists terminal task states for bounded retention.
- dev4 passed signature, 413 translated resources and the 14-class list ART probe, but the new-module probe caught incompatible null-branch type merges in progress ID getters. Neither dev4 nor dev5 was installed. dev6 uses separate explicit null returns (also for detail items) and adds a regression check.
- Runtime acceptance is **not yet complete**. Do not label these previews as stable or overwrite the installed listfix.6.2 before signature, final DEX and isolated ART checks pass.

Planned device controls: history disabled, opened post, paused video, multiple video swipes, preload exclusion, background/foreground, search/filter, clearing, same-video resume after reopening/restarting, user seek override, lock/unlock/system Back, download completion/open/source/retry, and existing home List switching/position restoration.

## Media UI / header revision (dev7)

- User requested themed cards, media previews, correct appbar titles, removal of the duplicate in-history recording switch, an in-header touch-lock icon, and a home-header More menu with direct settings/downloads/history destinations.
- The setting-level recording switch remains default-off. History schema 2 adds a `previews` JSON column transactionally; existing rows remain intact with `[]`. A subsequent foreground visit fills cover URLs. No retroactive network/API crawl is attempted.
- Covers use Piko's existing media-picker parser on individual native media objects, once per deduplicated foreground visit. At most four image/selected-video-cover HTTPS twimg URLs are retained; never an entire post dump or signed playback URL.
- Screen-scoped thumbnail work uses two workers, queue 32, 8 MiB memory cache, bounded image decoding and no persistent cache or URL logs. Redirects are disabled. Completed downloads use their own published content URI and Android thumbnail API; remote videos are never fetched for a cover. Network preview requests are made only for visible rows in these user-opened screens.
- History uses a rounded search field, segmented All/Posts/Videos filters, a confirmed clear icon, themed cards and on-demand covers. Downloads use local preview cards, status/progress and the existing action menu. Stable download IDs and scroll anchors preserve position on progress updates; replacements are deferred during drag/fling.
- Each custom fragment explicitly sets its appbar title on resume. Home shortcuts accept only the fixed downloads/history extra values; no class-name reflection or arbitrary destination.

### Header bytecode evidence

Frozen APK remains the SHA recorded above. Inspection helper is local-only `analysis/NativeHeaderInspect.java`; production resolvers contain no obfuscated owner names.

- Home header: `home_logo_scroll_to_top` resource selects one renderer, observed as `com.x.home.tabbed.d0.i`. The unique consecutive Compose `endNode` calls close the trailing weighted action row and then the outer row. Add the view inside this trailing row, preserving avatar, centered logo and existing optional actions. Device XML confirms avatar at x12–180, centered logo at x528–672 and an empty top-right slot at width1200.
- AndroidView: uniquely resolve factory `(Function1, Modifier, Function1, Composer, int, int)void` within `androidx.compose.ui.viewinterop`. The actual descriptor is `l.a` on this APK. Pass explicit empty Modifier and factory/update callbacks; no Compose internals in extension Java.
- Video: `more_options` resource plus haze-bearing action owner identifies `video.tab.i4.invoke`. Its parent `video.tab.k6.A` supplies this action to the shared header adapter `k6.F` in both header branches. That adapter wraps its action in a `Function3` RowScope lambda via a constructor `(native-composable-lambda, int)`. Replace only the wrapper object with our Function3, rendering the lock child before invoking the original action. Existing native callbacks and Compose composition boundary remain in place.
- Unlocked: the lock is an actual header child, with a 48dp hit target. Locked: the touch shield retains only an unlock icon at that child's measured bounds, even if native controls auto-hide. No right-edge coordinates are guessed; foreground exit removes the shield. Runtime overlap/rotation/auto-hide acceptance remains pending.

29 local checks passed before dev7 CI, including migration retention, resource format parity, UI contract regression, bounded preview behavior and native header/direct destination guards. These static checks are not substitutes for final patching, ART and device interaction.

### dev7 rejected at actual header creation

CI 34699561466 succeeded, all 37 patches applied, 420 resources/signature/16KiB alignment and ART class loading passed. APK SHA256 `1f0cc12eee8704aa10677b2de90997b9f5b9319ef80482eb3464817e9099a656`. Actual startup crashed in AndroidView: `AbstractMethodError Function1.invoke(Object)` on `HeaderToolsRuntime$$ExternalSyntheticLambda3` (PID6199). Final DEX showed a constructor-only synthetic class shared by all three Kotlin Function1 callbacks; invoke implementations were eliminated before native injection made their callsites reachable. Class loading alone did not catch an absent interface implementation.

Device immediately rolled back to media.dev6 without uninstalling or clearing data. dev7 is rejected, never present as usable. dev8 uses explicitly named callback classes with Object-erased override signatures retained by the extension's app.morphe keep rule; the patch now refuses missing/concrete invoke methods, and the device preflight checks each retained override and actually invokes the update callback. Also hide the settings-version footer for direct custom-screen entry and use the requested short history title.
