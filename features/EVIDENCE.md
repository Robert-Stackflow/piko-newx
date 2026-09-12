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
- Runtime acceptance is **not yet complete**. Do not label these previews as stable or overwrite the installed listfix.6.2 before signature, final DEX and isolated ART checks pass.

Planned device controls: history disabled, opened post, paused video, multiple video swipes, preload exclusion, background/foreground, search/filter, clearing, same-video resume after reopening/restarting, user seek override, lock/unlock/system Back, download completion/open/source/retry, and existing home List switching/position restoration.
