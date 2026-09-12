package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val LIST_FIX = "Lapp/morphe/extension/newx/timeline/ListReadingPosition;"
private const val LF_STRING = "Ljava/lang/String;"
private const val LF_ENUM = "Ljava/lang/Enum;"

private fun <T> List<T>.one(label: String): T = singleOrNull()
    ?: throw PatchException("List-position fix: expected one $label, found $size: ${joinToString()}")

private fun Method.refs() = implementation?.instructions?.mapNotNull { it.getReference<FieldReference>() }.orEmpty()

private fun listBridge(owner: String, name: String, params: List<String>, result: String, registers: Int, body: String): MutableMethod {
    val placeholder = MethodImplementationBuilder(registers).apply {
        addInstruction("return-void".toInstruction())
    }.methodImplementation
    return MutableMethod(ImmutableMethod(owner, name, params.map { ImmutableMethodParameter(it, emptySet(), null) },
        result, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, emptySet(), emptySet(), placeholder)).apply {
        implementation!!.removeInstruction(0)
        addInstructionsWithLabels(0, body.trimIndent())
    }
}

@Suppress("unused")
val preserveListReadingPositionPatch = bytecodePatch(
    name = "NewX: Preserve list reading position",
    description = "Experimental per-list position storage and viewport-aware refresh merging.",
) {
    compatibleWith(Compatibility(name = "NewX", packageName = "com.twitter.android",
        apkFileType = ApkFileType.APKM, appIconColor = 0x000000,
        targets = listOf(AppTarget(version = "12.22.0-prod.01"))))
    dependsOn(restoreTimelinePositionPatch, disableTimelineRefreshPatch)
    newXToggle(id = "newx.timeline.list_reading_position", category = Categories.TIMELINE,
        strings = settingStrings("piko_newx_list_reading_position"), order = 155,
        defaultValue = true, rebootApp = true)

    execute {
        val holder = Fingerprint(definingClass = "Lcom/x/urt/", name = "toString", returnType = LF_STRING,
            strings = listOf("ScrollPositionHolder(firstVisibleItemIndex=", ", firstVisibleItemScrollOffset="))
            .scopedMatchAll().one("scroll holder").originalClassDef.type
        val holderCtor = mutableClassDefBy(holder).methods.filter {
            it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf("I", "I")
        }.one("two-int holder constructor")
        val intWrites = holderCtor.instructions.filter { it.opcode == Opcode.IPUT }
        if (intWrites.size != 2) throw PatchException("List-position fix: holder constructor changed")
        val indexField = intWrites[0].getReference<FieldReference>()!!
        val offsetField = intWrites[1].getReference<FieldReference>()!!
        if ((intWrites[0] as TwoRegisterInstruction).registerA != 1 ||
            (intWrites[1] as TwoRegisterInstruction).registerA != 2 ||
            holderCtor.implementation!!.registerCount != 3) {
            throw PatchException("List-position fix: holder index/offset mapping is not proven")
        }

        val getter = Fingerprint(definingClass = "Lcom/x/urt/", parameters = emptyList(), returnType = holder,
            strings = listOf("Restoring scrolling position for ")).scopedMatchAll().one("position getter").method
        val component = mutableClassDefBy(getter.definingClass)
        val timelineGet = getter.instructions.mapNotNull { it.getReference<MethodReference>() }.filter {
            it.parameterTypes.isEmpty() && it.returnType.startsWith("L") &&
                runCatching { mutableClassDefBy(it.returnType).superclass == LF_ENUM }.getOrDefault(false)
        }.distinctBy { it.toString() }.one("repository timeline getter")
        val timeline = timelineGet.returnType
        mutableClassDefBy(timeline).fields.filter { it.name == "LIST_POSTS" && it.type == timeline }.one("LIST_POSTS enum")
        val repoField = component.fields.filter { it.type == timelineGet.definingClass }.one("component repository")
        val identity = Fingerprint(definingClass = "Lcom/x/models/timelines/", name = "toString", returnType = LF_STRING,
            strings = listOf("TimelineIdentifier(value="))
            .scopedMatchAll().one("timeline identifier").originalClassDef.type
        val identityField = mutableClassDefBy(identity).fields.filter {
            it.type == LF_STRING && !AccessFlags.STATIC.isSet(it.accessFlags)
        }.one("identifier value")
        val identityGet = mutableClassDefBy(timelineGet.definingClass).methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == identity
        }.one("repository identifier getter")

        val restoreBridge = listBridge(component.type, "pikoRestoreListPosition", emptyList(), holder, 7, """
            iget-object v0, p0, $repoField
            invoke-interface {v0}, $timelineGet
            move-result-object v1
            invoke-interface {v0}, $identityGet
            move-result-object v0
            iget-object v0, v0, $identityField
            invoke-static {v1, v0}, $LIST_FIX->restore(${LF_ENUM}${LF_STRING})[I
            move-result-object v0
            if-eqz v0, :done
            const/4 v1, 0x0
            aget v1, v0, v1
            const/4 v2, 0x1
            aget v2, v0, v2
            new-instance v0, $holder
            invoke-direct {v0, v1, v2}, $holder-><init>(II)V
            :done
            return-object v0
        """)
        val saveBridge = listBridge(component.type, "pikoSaveListPosition", listOf(holder), "Z", 8, """
            iget-object v0, p0, $repoField
            invoke-interface {v0}, $timelineGet
            move-result-object v1
            invoke-interface {v0}, $identityGet
            move-result-object v0
            iget-object v0, v0, $identityField
            iget v2, p1, $indexField
            iget v3, p1, $offsetField
            invoke-static {v1, v0, v2, v3}, $LIST_FIX->save(${LF_ENUM}${LF_STRING}II)Z
            move-result v0
            return v0
        """)
        val scrollBridge = listBridge(component.type, "pikoListScrollToTop", listOf("Z"), "Z", 4, """
            iget-object v0, p0, $repoField
            invoke-interface {v0}, $timelineGet
            move-result-object v0
            invoke-static {v0}, $LIST_FIX->enabled($LF_ENUM)Z
            move-result v0
            if-eqz v0, :original
            const/4 p1, 0x0
            :original
            return p1
        """)
        for (bridge in listOf(restoreBridge, saveBridge, scrollBridge)) {
            if (component.methods.any { it.name == bridge.name }) throw PatchException("List-position bridge already exists")
            component.methods.add(bridge)
        }
        val getterStart = getter.instructions.first()
        val getterTemp = getter.getFreeRegisterProvider(0, 1).getFreeRegister4Bit()
        getter.addInstructionsWithLabels(0, """
            invoke-virtual {p0}, $restoreBridge
            move-result-object v$getterTemp
            if-eqz v$getterTemp, :native
            return-object v$getterTemp
        """.trimIndent(), ExternalLabel("native", getterStart))

        val save = Fingerprint(definingClass = component.type, returnType = "V",
            strings = listOf("Saving scrolling positions for ")).scopedMatchAll().one("save event dispatcher").method
        val extraction = save.instructions.withIndex().filter {
            it.value.opcode == Opcode.IGET_OBJECT && it.value.getReference<FieldReference>()?.type == holder
        }.one("save-event holder extraction")
        val saveAt = extraction.index + 1
        val continuation = save.instructions[saveAt]
        // The first native policy load overwrites its destination: safe scratch register.
        if (continuation.opcode != Opcode.IGET_OBJECT || save.instructions[saveAt + 1].opcode != Opcode.IGET_BOOLEAN ||
            save.instructions[saveAt + 2].opcode != Opcode.IF_EQZ) throw PatchException("List-position save policy shape changed")
        val policyLoad = continuation as TwoRegisterInstruction
        val receiver = policyLoad.registerB
        val scratch = policyLoad.registerA
        val holderReg = (extraction.value as TwoRegisterInstruction).registerA
        if (setOf(receiver, scratch, holderReg).size != 3 || listOf(receiver, scratch, holderReg).any { it > 15 })
            throw PatchException("List-position save registers changed")
        save.addInstructionsWithLabels(saveAt, """
            invoke-virtual {v$receiver, v$holderReg}, $saveBridge
            move-result v$scratch
            if-eqz v$scratch, :native
            return-void
        """.trimIndent(), ExternalLabel("native", continuation))

        val repositoryRequest = Fingerprint(definingClass = "Lcom/x/repositories/urt/", returnType = "V",
            parameters = listOf("L", "L"), strings = listOf("requestType"))
            .scopedMatchAll().one("repository request").method
        val repository = mutableClassDefBy(repositoryRequest.definingClass)
        val requestType = repositoryRequest.parameterTypes[0].toString()
        val cursorType = repositoryRequest.parameterTypes[1].toString()
        val viewport = mutableClassDefBy(requestType).fields.filter {
            it.name == "VIEWPORT_AWARE_AUTO_REFRESH" && it.type == requestType
        }.one("viewport request enum")
        val repoTimeline = repository.fields.filter { it.type == timeline }.one("repository timeline field")
        // Resolve the data-merge coroutine by both viewport comparisons and its captured inputs.
        val mergeCandidates = mutableListOf<Pair<MutableMethod, Pair<Int, MethodReference>>>()
        for (method in repository.methods) {
            for ((i, instruction) in method.instructions.withIndex()) {
                val ref = instruction.getReference<MethodReference>() ?: continue
                if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE || ref.name != "<init>") continue
                val parameters = ref.parameterTypes.map(CharSequence::toString)
                if (parameters.size != 7 || parameters[0] != cursorType || parameters[1] != "Ljava/util/ArrayList;" ||
                    parameters[3] != "Z" || parameters[5] != requestType || parameters[6] != "Lkotlin/coroutines/Continuation;") continue
                val coroutine = mutableClassDefBy(ref.definingClass)
                if (coroutine.methods.any { m -> m.name == "invokeSuspend" && m.refs().count { it.toString() == viewport.toString() } == 2 })
                    mergeCandidates += method to (i to ref)
            }
        }
        val (mergeCaller, mergeSite) = mergeCandidates.one("data-merge constructor call")
        val (mergeAt, mergeCtor) = mergeSite
        val processor = mergeCtor.parameterTypes[2].toString()
        val cachedItems = mutableClassDefBy(processor).fields.filter {
            it.type == "Ljava/util/List;" && AccessFlags.VOLATILE.isSet(it.accessFlags)
        }.one("native current-item cache")
        val processorLoad = mergeCaller.instructions.take(mergeAt).withIndex().filter {
            it.value.opcode == Opcode.IGET_OBJECT && it.value.getReference<FieldReference>()?.let { f ->
                f.definingClass == repository.type && f.type == processor
            } == true
        }.one("merge repository receiver")
        val repoReg = (processorLoad.value as TwoRegisterInstruction).registerB
        val mergeRange = mergeCaller.instructions[mergeAt] as RegisterRangeInstruction
        val start = mergeRange.startRegister
        if (mergeRange.registerCount != 8 || listOf(repoReg, start + 1, start + 3, start + 6).any { it > 15 })
            throw PatchException("List-position merge register contract changed")
        val mergeBridge = listBridge(repository.type, "pikoListMergeMode", listOf(requestType, cursorType, processor), requestType, 8, """
            iget-object v0, p0, $repoTimeline
            iget-object v1, p3, $cachedItems
            invoke-static {v0, p1, p2, v1}, $LIST_FIX->preserveMerge(${LF_ENUM}${LF_ENUM}Ljava/lang/Object;Ljava/util/List;)Z
            move-result v0
            if-eqz v0, :original
            sget-object p1, $viewport
            :original
            return-object p1
        """)
        repository.methods.add(mergeBridge)
        mergeCaller.addInstructions(mergeAt, """
            invoke-virtual {v$repoReg, v${start + 6}, v${start + 1}, v${start + 3}}, $mergeBridge
            move-result-object v${start + 6}
        """.trimIndent())

        // Only neutralize the automatic jump on a successful pull result; no request or result
        // enum is changed here, so spinner completion, errors, paging and explicit top taps survive.
        val resultHandlers = mutableListOf<Pair<MutableMethod, Int>>()
        for (match in Fingerprint(definingClass = "Lcom/x/urt/", name = "emit",
            parameters = listOf("Ljava/lang/Object;", "Lkotlin/coroutines/Continuation;"), returnType = "Ljava/lang/Object;").scopedMatchAll()) {
            val method = match.method
            for ((i, instruction) in method.instructions.withIndex()) {
                val f = instruction.getReference<FieldReference>() ?: continue
                if (instruction.opcode != Opcode.SGET_OBJECT || f.name != "PULL_TO_REFRESH" || f.type != requestType || i < 4) continue
                if (method.instructions[i - 2].opcode == Opcode.IF_EQZ && method.instructions[i - 3].opcode == Opcode.IGET_BOOLEAN &&
                    method.instructions[i - 4].opcode == Opcode.IGET_OBJECT &&
                    method.instructions[i - 4].getReference<FieldReference>()?.definingClass == component.type)
                    resultHandlers += method to (i - 2)
            }
        }
        val (resultHandler, resultAt) = resultHandlers.one("pull-result scroll policy")
        val resultComponent = (resultHandler.instructions[resultAt - 2] as TwoRegisterInstruction).registerB
        val resultBoolean = (resultHandler.instructions[resultAt - 1] as OneRegisterInstruction).registerA
        if (resultComponent > 15 || resultBoolean > 15) throw PatchException("List-position result registers changed")
        resultHandler.addInstructions(resultAt, """
            invoke-virtual {v$resultComponent, v$resultBoolean}, $scrollBridge
            move-result v$resultBoolean
        """.trimIndent())
        println("List-position fix: per-identifier restore/save, cached-list-only merge, pull-result jump guard installed")
    }
}
