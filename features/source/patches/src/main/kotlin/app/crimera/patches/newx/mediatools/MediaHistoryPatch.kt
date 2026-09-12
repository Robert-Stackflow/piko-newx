package app.crimera.patches.newx.mediatools

import app.crimera.patches.newx.models.*
import app.crimera.patches.newx.settings.*
import app.crimera.patches.newx.timeline.postfilter.newXTimelineTextModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val RUNTIME = "Lapp/morphe/extension/newx/mediatools/MediaHistoryRuntime;"
private const val OBJ = "Ljava/lang/Object;"
private const val STR = "Ljava/lang/String;"
private fun <T> List<T>.one(label: String): T = singleOrNull()
    ?: throw PatchException("Media history: expected one $label, got $size: ${joinToString()}")
private fun MutableMethod.refs() = instructions.mapNotNull { it.getReference<MethodReference>() }
private fun MutableMethod.fields() = instructions.mapNotNull { it.getReference<FieldReference>() }.distinctBy { it.toString() }

@Suppress("unused")
val mediaHistoryPatch = bytecodePatch(
    name = "NewX: Local post and video history",
    description = "Private history of opened posts and foreground video pages, including video swipes.",
) {
    compatibleWith(Compatibility(name = "NewX", packageName = "com.twitter.android", apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000, targets = listOf(AppTarget(version = "12.22.0-prod.01"))))
    dependsOn(newXTimelineTextModelAdapterPatch, newXPostMediaModelResolutionPatch)
    newXSettings {
        category(Categories.POST_ACTIONS_MEDIA) {
            customScreen(id = "newx.media_tools.downloads", strings = settingStrings("piko_newx_tools_downloads"), order = 509,
                fragmentClassDescriptor = "Lapp/morphe/extension/newx/mediatools/DownloadsFragment;")
            customScreen(id = "newx.media_tools.history", strings = settingStrings("piko_newx_tools_history"), order = 510,
                fragmentClassDescriptor = "Lapp/morphe/extension/newx/mediatools/HistoryFragment;")
            toggle(id = "newx.media_tools.history_enabled", strings = settingStrings("piko_newx_tools_history_enabled"),
                order = 511, defaultValue = false)
            toggle(id = "newx.media_tools.resume_video", strings = settingStrings("piko_newx_tools_resume_video"),
                order = 513, defaultValue = false)
            toggle(id = "newx.media_tools.touch_lock", strings = settingStrings("piko_newx_tools_touch_lock"),
                order = 514, defaultValue = false)
            singleChoice(id = "newx.media_tools.history_days", strings = settingStrings("piko_newx_tools_history_days"),
                order = 512, defaultValue = "30", options = listOf(choice("7", "piko_newx_tools_days_7"),
                    choice("30", "piko_newx_tools_days_30"), choice("90", "piko_newx_tools_days_90")))
        }
    }
    execute {
        val runtime = mutableClassDefBy(RUNTIME)
        fun bridge(name: String, registers: Int, body: String) {
            val old = runtime.methods.filter { it.name == name }.one("bridge $name")
            val method = MutableMethod(ImmutableMethod(runtime.type, name, old.parameters, old.returnType, old.accessFlags,
                old.annotations, old.hiddenApiRestrictions, MethodImplementationBuilder(registers).methodImplementation))
            method.addInstructionsWithLabels(0, body.trimIndent())
            runtime.methods.remove(old); runtime.methods.add(method)
        }
        fun expose(field: FieldReference) { mutableClassDefBy(field.definingClass).requirePublicFields(listOf(field)) }
        val models = resolvedNewXTimelineModels()
        val mediaModels = resolvedNewXPostMediaModels()
        val postModels = mediaModels.postModels
        val original = mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
        for (name in listOf("isTimelinePost", "isTimelineModule", "isTimelineModuleItem", "getModuleItem",
            "getModuleInnerContent", "getPostText", "getPostAuthorScreenName")) {
            val from = original.methods.filter { it.name == name }.one("model $name")
            val to = runtime.methods.filter { it.name == name &&
                it.parameterTypes.map(CharSequence::toString) == from.parameterTypes.map(CharSequence::toString) &&
                it.returnType == from.returnType }.one("model target $name (${from.parameterTypes})${from.returnType}")
            runtime.methods.remove(to)
            runtime.methods.add(MutableMethod(ImmutableMethod(runtime.type, name, to.parameters, to.returnType, to.accessFlags,
                to.annotations, to.hiddenApiRestrictions, from.implementation)))
        }
        val focal = Fingerprint(definingClass = "Lcom/x/postdetail/", returnType = models.postIdGetter.returnType,
            strings = listOf("Focal post id read before the timeline exists")).requireSingle("post detail focal ID")
        val detail = mutableClassDefBy(focal.originalClassDef.type)
        val focalField = focal.method.fields().filter { it.type == models.postIdGetter.returnType }.one("focal ID field")
        val idField = mutableClassDefBy(focalField.type).fields.filter { it.type == "J" && !AccessFlags.STATIC.isSet(it.accessFlags) }.one("numeric post ID")
        expose(focalField); expose(idField)
        bridge("postId", 3, """
            check-cast p0, ${models.postDescriptor}
            invoke-virtual {p0}, ${models.postIdGetter}
            move-result-object p0
            if-eqz p0, :none
            iget-wide v0, p0, $idField
            invoke-static {v0, v1}, Ljava/lang/Long;->toString(J)$STR
            move-result-object p0
            return-object p0
            :none
            const/4 p0, 0x0
            return-object p0
        """)
        bridge("focalPostId", 3, """
            check-cast p0, ${detail.type}
            iget-object p0, p0, $focalField
            if-eqz p0, :none
            iget-wide v0, p0, $idField
            invoke-static {v0, v1}, Ljava/lang/Long;->toString(J)$STR
            move-result-object p0
            return-object p0
            :none
            const/4 p0, 0x0
            return-object p0
        """)

        val selected = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString",
            strings = listOf("CurrentMediaChanged(mediaId=")).requireSingle("current-media selection")
        val selectionField = selected.originalClassDef.fields.filter { it.type == STR }.one("selected media ID")
        expose(selectionField)
        val eventInterface = selected.originalClassDef.interfaces.toList().one("video event interface")
        val page = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString", strings = listOf("PageChanged(page="))
            .scopedMatchAll().filter { eventInterface in it.originalClassDef.interfaces }.one("current viewer page event")
        val pageField = page.originalClassDef.fields.filter { it.type == "I" }.one("page event index")
        val dispatcher = Fingerprint(definingClass = "Lcom/x/video/tab/", returnType = "V", parameters = listOf(eventInterface))
            .scopedMatchAll().map { it.method }.filter { m -> m.fields().any { it.toString() == selectionField.toString() } &&
                m.fields().any { it.toString() == pageField.toString() } }.one("viewer event dispatcher")
        val video = mutableClassDefBy(dispatcher.definingClass)
        val items = video.methods.filter { it.parameterTypes.isEmpty() && it.returnType == "Ljava/util/ArrayList;" }.one("displayed video items")
        val pageWrites = dispatcher.instructions.withIndex().mapNotNull { (i, op) ->
            if (op.opcode != Opcode.IGET || op.getReference<FieldReference>()?.toString() != pageField.toString()) return@mapNotNull null
            val pair = dispatcher.instructions.drop(i + 1).take(2)
            if (pair.size != 2 || pair.any { it.opcode != Opcode.IPUT || (it as TwoRegisterInstruction).registerA != (op as TwoRegisterInstruction).registerA }) return@mapNotNull null
            pair.first().getReference<FieldReference>()
        }.one("current page write followed by scroll-page write")
        if (pageWrites.definingClass != video.type || pageWrites.type != "I") throw PatchException("Media history: invalid current-page owner")
        expose(pageWrites)
        bridge("videoPage", 1, "check-cast p0, ${video.type}\niget p0, p0, $pageWrites\nreturn p0")
        bridge("videoItems", 1, "check-cast p0, ${video.type}\ninvoke-virtual {p0}, $items\nmove-result-object p0\nreturn-object p0")
        bridge("isVideoPageEvent", 1, "instance-of p0, p0, ${page.originalClassDef.type}\nreturn p0")
        bridge("isMediaSelectionEvent", 1, "instance-of p0, p0, ${selected.originalClassDef.type}\nreturn p0")
        bridge("selectionMediaId", 1, "check-cast p0, ${selected.originalClassDef.type}\niget-object p0, p0, $selectionField\nreturn-object p0")

        val successItems = items.fields().filter { it.definingClass.startsWith("Lcom/x/urt/") && it.type.startsWith("Lkotlinx/collections/immutable/") }.one("success items")
        val detailSuccess = detail.fields.filter { it.type == successItems.definingClass }.one("detail success state")
        expose(successItems); expose(detailSuccess)
        bridge("detailItems", 1, """
            check-cast p0, ${detail.type}
            iget-object p0, p0, $detailSuccess
            if-eqz p0, :none
            iget-object p0, p0, $successItems
            :none
            return-object p0
        """)
        fun accountField(owner: String): FieldReference = mutableClassDefBy(owner).fields.filter { field ->
            field.type.startsWith("Lcom/x/models/") && runCatching {
                mutableClassDefBy(field.type).methods.any { m -> m.name == "<init>" && m.parameterTypes.map(CharSequence::toString) == listOf(STR) &&
                    m.refs().any { it.toString() == "Ljava/lang/Long;->parseLong(Ljava/lang/String;)J" } }
            }.getOrDefault(false)
        }.one("account in $owner")
        val detailAccount = accountField(detail.type)
        val accountId = mutableClassDefBy(detailAccount.type).fields.filter { it.type == "J" && !AccessFlags.STATIC.isSet(it.accessFlags) }.one("numeric account ID")
        val wrapper = items.fields().filter { it.definingClass == video.type && it.type.startsWith("Lcom/x/video/tab/") }.one("video timeline wrapper")
        val timeline = mutableClassDefBy(wrapper.type).fields.filter { it.type.startsWith("Lcom/x/urt/") &&
            runCatching { accountField(it.type) }.isSuccess }.one("video account owner")
        val videoAccount = accountField(timeline.type)
        if (videoAccount.type != detailAccount.type) throw PatchException("Media history: account identity types disagree")
        listOf(detailAccount, accountId, wrapper, timeline, videoAccount).forEach(::expose)
        bridge("postAccount", 3, """
            check-cast p0, ${detail.type}
            iget-object p0, p0, $detailAccount
            iget-wide v0, p0, $accountId
            return-wide v0
        """)
        bridge("videoAccount", 3, """
            check-cast p0, ${video.type}
            iget-object p0, p0, $wrapper
            iget-object p0, p0, $timeline
            iget-object p0, p0, $videoAccount
            iget-wide v0, p0, $accountId
            return-wide v0
        """)
        val videoLife = video.methods.filter { it.name == "getLifecycle" && it.parameterTypes.isEmpty() }.one("video lifecycle")
        val detailLife = detail.methods.filter { it.name == "getLifecycle" && it.parameterTypes.isEmpty() }.one("detail lifecycle")
        if (videoLife.returnType != detailLife.returnType) throw PatchException("Media history: lifecycle contracts disagree")
        val lifeState = mutableClassDefBy(videoLife.returnType).methods.filter { it.name == "getState" && it.parameterTypes.isEmpty() }.one("lifecycle state")
        val state = mutableClassDefBy(lifeState.returnType)
        if (state.superclass != "Ljava/lang/Enum;" || !state.fields.any { it.name == "RESUMED" } || !state.fields.any { it.name == "DESTROYED" })
            throw PatchException("Media history: unsupported lifecycle states")
        bridge("lifecycle", 2, """
            instance-of v0, p0, ${video.type}
            if-eqz v0, :detail
            check-cast p0, ${video.type}
            invoke-virtual {p0}, $videoLife
            move-result-object p0
            goto :state
            :detail
            check-cast p0, ${detail.type}
            invoke-virtual {p0}, $detailLife
            move-result-object p0
            :state
            invoke-interface {p0}, $lifeState
            move-result-object p0
            return-object p0
        """)
        val media = Fingerprint(definingClass = "Lcom/x/models/", name = "toString",
            strings = listOf("MediaContentVideo(mediaId=", ", variants=")).requireSingle("video media model").originalClassDef
        val mediaInterface = media.interfaces.toList().one("video media interface")
        val emitter = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "emit").scopedMatchAll().map { it.method }
            .filter { m -> m.refs().any { it.definingClass == selected.originalClassDef.type && it.name == "<init>" } }.one("current media emitter")
        val mediaId = emitter.refs().filter { it.definingClass == mediaInterface && it.returnType == STR && it.parameterTypes.isEmpty() }.one("selected media ID getter")
        bridge("isVideo", 1, "instance-of p0, p0, ${media.type}\nreturn p0")
        bridge("mediaId", 1, "check-cast p0, $mediaInterface\ninvoke-interface {p0}, $mediaId\nmove-result-object p0\nreturn-object p0")
        val playback = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString",
            strings = listOf("ImmersivePlaybackState(progress=", ", isSeekable=")).requireSingle("viewer playback state").originalClassDef
        val playbackConstructor = mutableClassDefBy(playback.type).methods.filter {
            it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf("F", "J", "J", "Z", "Z", "F")
        }.one("playback state constructor")
        val parameterStart = playbackConstructor.implementation!!.registerCount - 9
        fun stateField(offset: Int, type: String): FieldReference = playbackConstructor.instructions.filter { op ->
            op is TwoRegisterInstruction && op.opcode in listOf(Opcode.IPUT_WIDE, Opcode.IPUT_BOOLEAN) &&
                op.registerA == parameterStart + offset && op.registerB == parameterStart
        }.mapNotNull { it.getReference<FieldReference>() }.filter { it.type == type }.one("playback parameter $offset")
        val positionField = stateField(2, "J")
        val durationField = stateField(4, "J")
        val seekableField = stateField(7, "Z")
        listOf(positionField, durationField, seekableField).forEach(::expose)
        val stateFlows = dispatcher.instructions.withIndex().mapNotNull { (index, op) ->
            if (op.opcode != Opcode.CHECK_CAST || op.getReference<com.android.tools.smali.dexlib2.iface.reference.TypeReference>()?.type != playback.type) return@mapNotNull null
            dispatcher.instructions.take(index).takeLast(5).mapNotNull { it.getReference<FieldReference>() }
                .lastOrNull { it.definingClass == video.type && it.type.startsWith("Lkotlinx/coroutines/flow/") }
        }.distinctBy { it.toString() }
        val stateFlow = stateFlows.one("playback state flow")
        expose(stateFlow)
        val stateGet = mutableClassDefBy(stateFlow.type).methods.filter { it.name == "getValue" && it.parameterTypes.isEmpty() }.one("playback flow getter")
        bridge("playbackState", 1, "check-cast p0, ${video.type}\niget-object p0, p0, $stateFlow\ninvoke-virtual {p0}, $stateGet\nmove-result-object p0\nreturn-object p0")
        val millis = Fingerprint(definingClass = "Lkotlin/time/Duration;", parameters = listOf("J"), returnType = "J")
            .scopedMatchAll().map { it.method }.filter { AccessFlags.STATIC.isSet(it.accessFlags) && it.fields().any { field -> field.name == "MILLISECONDS" } }.one("duration milliseconds decoder")
        for ((name, field) in listOf("playbackPosition" to positionField, "playbackDuration" to durationField))
            bridge(name, 3, "check-cast p0, ${playback.type}\niget-wide v0, p0, $field\ninvoke-static {v0, v1}, $millis\nmove-result-wide v0\nreturn-wide v0")
        bridge("playbackSeekable", 1, "check-cast p0, ${playback.type}\niget-boolean p0, p0, $seekableField\nreturn p0")
        val progress = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString", strings = listOf("MediaProgress(index="))
            .scopedMatchAll().filter { eventInterface in it.originalClassDef.interfaces }.one("current viewer progress event").originalClassDef
        val envelope = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString", strings = listOf("ProgressEvent(event="))
            .scopedMatchAll().filter { m -> m.originalClassDef.fields.any { it.type == progress.type } }.one("current progress envelope").originalClassDef
        val progressField = video.fields.filter { it.type == envelope.type }.one("current native progress")
        val envelopePost = envelope.fields.filter { it.type == models.postDescriptor }.one("progress post")
        val envelopeEvent = envelope.fields.filter { it.type == progress.type }.one("progress event")
        val progressMedia = progress.fields.filter { it.type == mediaInterface }.one("progress media")
        listOf(progressField, envelopePost, envelopeEvent, progressMedia).forEach(::expose)
        bridge("progressPostId", 1, """
            check-cast p0, ${video.type}
            iget-object p0, p0, $progressField
            if-eqz p0, :none
            iget-object p0, p0, $envelopePost
            invoke-static {p0}, $RUNTIME->postId($OBJ)$STR
            move-result-object p0
            :none
            return-object p0
        """)
        bridge("progressMediaId", 1, """
            check-cast p0, ${video.type}
            iget-object p0, p0, $progressField
            if-eqz p0, :none
            iget-object p0, p0, $envelopeEvent
            iget-object p0, p0, $progressMedia
            invoke-interface {p0}, $mediaId
            move-result-object p0
            :none
            return-object p0
        """)
        val seek = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "toString", strings = listOf("BarSeekTo(progress="))
            .scopedMatchAll().filter { eventInterface in it.originalClassDef.interfaces }.one("native viewer seek event").originalClassDef
        val seekConstructor = mutableClassDefBy(seek.type).methods.filter { it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf("F") }.one("seek constructor")
        bridge("isSeekEvent", 1, "instance-of p0, p0, ${seek.type}\nreturn p0")
        bridge("seekVideo", 3, """
            new-instance v0, ${seek.type}
            invoke-direct {v0, p1}, $seekConstructor
            check-cast p0, ${video.type}
            invoke-virtual {p0, v0}, $dispatcher
            return-void
        """)
        val result = models.postResultField
        val canonical = postModels.contextualCanonicalPostField
        val repost = postModels.contextualRepostedPostField
        val repostCanonical = postModels.repostedCanonicalPostField
        val mediaField = mediaModels.canonicalPostMediaField
        listOf(result, canonical, repost, repostCanonical, mediaField).forEach(::expose)
        for ((name, path) in listOf("postMedia" to "iget-object p0, p0, $canonical",
            "repostedMedia" to "iget-object p0, p0, $repost\nif-eqz p0, :none\niget-object p0, p0, $repostCanonical")) {
            bridge(name, 2, """
                check-cast p0, ${models.postDescriptor}
                iget-object p0, p0, $result
                instance-of v0, p0, ${postModels.contextualPostDescriptor}
                if-eqz v0, :none
                check-cast p0, ${postModels.contextualPostDescriptor}
                $path
                if-eqz p0, :none
                check-cast p0, ${postModels.canonicalPostDescriptor}
                iget-object p0, p0, $mediaField
                return-object p0
                :none
                const/4 p0, 0x0
                return-object p0
            """)
        }
        // No repository/cursor mutation: observe only detail presentation and viewer page events.
        val detailGetter = detail.methods.filter { it.name == "getState" && it.parameterTypes.isEmpty() }.one("detail state getter")
        detailGetter.addInstructions(0, "invoke-static/range {p0 .. p0}, $RUNTIME->bindPost($OBJ)V")
        dispatcher.addInstructions(0, "invoke-static/range {p0 .. p1}, $RUNTIME->videoEvent($OBJ$OBJ)V")
        println("Media history: verified foreground detail/video observers; repository, playback and pagination unchanged")
    }
}
