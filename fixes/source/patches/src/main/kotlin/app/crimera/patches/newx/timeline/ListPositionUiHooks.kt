package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val UI_RUNTIME = "Lapp/morphe/extension/newx/timeline/ListPositionRuntime;"
private const val OBJ = "Ljava/lang/Object;"
private const val STR = "Ljava/lang/String;"
private fun <T> List<T>.unique(label: String): T = singleOrNull()
    ?: throw PatchException("List anchor: expected one $label; found $size: ${joinToString()}")
private fun MutableMethod.calls() = instructions.mapNotNull { it.getReference<MethodReference>() }
private fun MutableMethod.fields() = instructions.mapNotNull { it.getReference<FieldReference>() }.distinctBy { it.toString() }

context(context: BytecodePatchContext)
internal fun installListAnchorUi(componentType: String, holder: String, timelineGet: MethodReference,
    repoField: FieldReference, identityGet: MethodReference, identityField: FieldReference) {
    val component = context.mutableClassDefBy(componentType)
    val runtime = context.mutableClassDefBy(UI_RUNTIME)
    fun adapter(name: String, registers: Int, body: String) {
        val old = runtime.methods.filter { it.name == name }.unique("adapter $name")
        val replacement = MutableMethod(ImmutableMethod(runtime.type,name,old.parameters,old.returnType,old.accessFlags,
            old.annotations,old.hiddenApiRestrictions,MethodImplementationBuilder(registers).methodImplementation))
        replacement.addInstructionsWithLabels(0,body.trimIndent())
        runtime.methods.remove(old)
        runtime.methods.add(replacement)
    }
    val flowGetter = component.methods.filter { method ->
        method.parameterTypes.isEmpty() && method.returnType.startsWith("Lkotlinx/coroutines/flow/") &&
            method.instructions.map { it.opcode } == listOf(Opcode.IGET_OBJECT, Opcode.RETURN_OBJECT) &&
            runCatching { context.mutableClassDefBy(method.returnType).methods.any {
                it.name == "getValue" && it.parameterTypes.isEmpty()
            } }.getOrDefault(false)
    }.unique("component state getter")
    if (flowGetter.instructions.map { it.opcode } != listOf(Opcode.IGET_OBJECT,Opcode.RETURN_OBJECT))
        throw PatchException("List anchor: state getter is not a direct read")
    val flowField = flowGetter.fields().unique("state flow field")
    val ownerFields = component.fields.filter { field ->
        field.type.startsWith("Lcom/x/models/") && runCatching {
            context.mutableClassDefBy(field.type).methods.any { m ->
                m.name == "<init>" && m.parameterTypes.map(CharSequence::toString) == listOf(STR) &&
                    m.instructions.any { it.getReference<MethodReference>()?.toString() == "Ljava/lang/Long;->parseLong(Ljava/lang/String;)J" }
            }
        }.getOrDefault(false)
    }
    val owner = ownerFields.unique("account identity field")
    val accountId = context.mutableClassDefBy(owner.type).fields.filter { it.type == "J" && !AccessFlags.STATIC.isSet(it.accessFlags) }.unique("account ID")
    val register = listBridge(componentType,"pikoRegisterListScope",emptyList(),"V",7,"""
        iget-object v0, p0, $flowField
        iget-object v1, p0, $repoField
        invoke-interface {v1}, $identityGet
        move-result-object v2
        iget-object v2, v2, $identityField
        invoke-interface {v1}, $timelineGet
        move-result-object v1
        iget-object v3, p0, $owner
        iget-wide v3, v3, $accountId
        invoke-static {v0, v1, v2, v3, v4}, $UI_RUNTIME->register(${OBJ}Ljava/lang/Enum;${STR}J)V
        return-void
    """)
    component.methods.add(register)
    flowGetter.addInstructions(0,"invoke-virtual {p0}, $register")
    // R8 inlined getState into List delegates, but the position getter remains delegated.
    component.methods.filter { it.name=="pikoRestoreListPosition" }.unique("List position bridge")
        .addInstructions(0,"invoke-virtual {p0}, $register")

    val disposal = Fingerprint(definingClass="Lcom/x/urt/ui/",returnType="V",parameters=emptyList()).scopedMatchAll()
        .map { it.method }.filter { m -> m.calls().any { it.definingClass == holder && it.name == "<init>" } }.unique("lifecycle position save")
    val life = context.mutableClassDefBy(disposal.definingClass)
    val lazyField = life.fields.filter { it.type.startsWith("Landroidx/compose/foundation/lazy/") }.unique("lifecycle LazyListState")
    val lazy = context.mutableClassDefBy(lazyField.type)
    val interfaceField = life.fields.filter { it.type != lazy.type }.unique("lifecycle timeline component")
    val flowInterface = context.mutableClassDefBy(interfaceField.type).methods.filter {
        it.name == flowGetter.name && it.parameterTypes.isEmpty() && it.returnType == flowGetter.returnType
    }.unique("delegating state interface")
    val positionInterface = context.mutableClassDefBy(interfaceField.type).methods.filter {
        it.returnType==holder && it.parameterTypes.isEmpty()
    }.unique("delegating position interface")
    adapter("nativeFlow",2,"""
        check-cast p0, ${interfaceField.type}
        invoke-interface {p0}, $positionInterface
        invoke-interface {p0}, $flowInterface
        move-result-object v0
        return-object v0
    """)
    val lifeCtor = life.methods.filter { it.name == "<init>" }.unique("lifecycle constructor")
    if (lifeCtor.parameterTypes.drop(1).map(CharSequence::toString) != listOf(interfaceField.type,lazy.type) ||
        lifeCtor.implementation!!.registerCount != 4) throw PatchException("List anchor: lifecycle constructor contract changed")
    lifeCtor.addInstructions(lifeCtor.instructions.size-1,"invoke-static {p2, p3}, $UI_RUNTIME->bind($OBJ$OBJ)V")
    disposal.addInstructions(0,"iget-object v0, p0, $lazyField\ninvoke-static {v0}, $UI_RUNTIME->pause($OBJ)V")

    val renderer = Fingerprint(definingClass="Lcom/x/urt/ui/",name="invoke",returnType=OBJ,
        strings=listOf("timeline_header_key","timeline_footer_key")).requireSingle("final lazy items builder").method
    val rendererClass = context.mutableClassDefBy(renderer.definingClass)
    val rendererState = rendererClass.fields.filter { it.type == lazy.type }.unique("builder lazy state")
    val scopeType = renderer.instructions.mapNotNull { it.getReference<MethodReference>() }.filter {
        it.name != "<init>" && it.definingClass.startsWith("Landroidx/compose/foundation/lazy/") &&
            it.parameterTypes.map(CharSequence::toString) in listOf(
                listOf(OBJ,OBJ,"Lkotlin/jvm/functions/Function3;"),
                listOf(it.definingClass,OBJ,"Lkotlin/jvm/functions/Function3;","I")
            )
    }.distinctBy { it.definingClass }.unique("final lazy scope").definingClass
    val scope = context.mutableClassDefBy(scopeType)
    val intervals = scope.fields.filter { it.type.startsWith("Landroidx/appcompat/widget/") }.unique("interval storage")
    val sticky = scope.methods.filter { m -> AccessFlags.STATIC.isSet(m.accessFlags) &&
        m.parameterTypes.map(CharSequence::toString).take(2)==listOf(scopeType,STR) }.unique("sticky item registration")
    val count = sticky.fields().filter { it.definingClass==intervals.type && it.type=="I" }.unique("final item count")
    val keyGet = context.mutableClassDefBy(scope.superclass!!).methods.filter { m ->
        !AccessFlags.STATIC.isSet(m.accessFlags) && m.parameterTypes.map(CharSequence::toString)==listOf("I") &&
            m.returnType==OBJ && m.calls().any { it.name=="getKey" && it.returnType=="Lkotlin/jvm/functions/Function1;" }
    }.unique("final item key lookup")
    adapter("nativeCount",2,"check-cast p0, $scopeType\niget-object v0, p0, $intervals\niget v0, v0, $count\nreturn v0")
    adapter("nativeKeyAt",3,"check-cast p0, $scopeType\ninvoke-virtual {p0, p1}, $keyGet\nmove-result-object v0\nreturn-object v0")
    val renderReturn = renderer.instructions.withIndex().filter { item ->
        item.value.opcode==Opcode.RETURN_OBJECT && item.index>0 &&
            renderer.instructions[item.index-1].getReference<FieldReference>()?.definingClass=="Lkotlin/Unit;"
    }.unique("builder Unit return")
    // Insert before the terminal Unit load so the return register is preserved across R8 layouts.
    val previous = renderer.instructions[renderReturn.index-1].getReference<FieldReference>()
    if(previous?.definingClass!="Lkotlin/Unit;" ||
        (renderer.instructions[renderReturn.index-1] as OneRegisterInstruction).registerA !=
            (renderReturn.value as OneRegisterInstruction).registerA)
        throw PatchException("List anchor: builder terminal contract changed")
    renderer.addInstructions(renderReturn.index-1,"""
        move-object/from16 v0, p0
        iget-object v0, v0, $rendererState
        move-object/from16 v2, p1
        invoke-static {v0, v2}, $UI_RUNTIME->render($OBJ$OBJ)V
    """.trimIndent())

    val interaction = lazy.fields.filter { it.type.startsWith("Landroidx/compose/foundation/interaction/") }.unique("drag interaction source")
    adapter("nativeInteraction",2,"check-cast p0, ${lazy.type}\niget-object v0, p0, $interaction\nreturn-object v0")
    val emitters = context.mutableClassDefBy(interaction.type).methods.filter { m ->
        m.parameterTypes.firstOrNull()?.toString()?.startsWith("Landroidx/compose/foundation/interaction/")==true &&
            m.calls().any { it.definingClass.startsWith("Lkotlinx/coroutines/flow/") }
    }
    if(emitters.size!=2) throw PatchException("List anchor: expected suspending and non-suspending interaction emitters")
    for(m in emitters) m.addInstructions(0,"invoke-static {p0}, $UI_RUNTIME->interaction($OBJ)V")

    val layoutGet = lazy.methods.filter { m -> m.parameterTypes.isEmpty() && m.returnType.startsWith("Landroidx/compose/foundation/lazy/") &&
        runCatching { context.mutableClassDefBy(m.returnType).fields.any { it.type=="Ljava/util/List;" } }.getOrDefault(false) }.unique("measured layout getter")
    val layout = context.mutableClassDefBy(layoutGet.returnType)
    val first = layout.fields.filter { field -> field.type.startsWith("Landroidx/compose/foundation/lazy/") &&
        context.mutableClassDefBy(field.type).methods.any { it.name=="getKey" && it.returnType==OBJ } }.unique("first measured item")
    val measuredKey = context.mutableClassDefBy(first.type).methods.filter { it.name=="getKey" && it.parameterTypes.isEmpty() }.unique("measured item key")
    adapter("nativeLayout",2,"check-cast p0, ${lazy.type}\ninvoke-virtual {p0}, $layoutGet\nmove-result-object v0\nreturn-object v0")
    adapter("nativeMeasuredKey",2,"""
        check-cast p0, ${lazy.type}
        invoke-virtual {p0}, $layoutGet
        move-result-object v0
        iget-object v0, v0, $first
        if-eqz v0, :empty
        invoke-virtual {v0}, $measuredKey
        move-result-object v0
        return-object v0
        :empty
        const/4 v0, 0x0
        return-object v0
    """)
    val positionField = disposal.fields().filter { it.definingClass==lazy.type && it.type.startsWith("Landroidx/compose/foundation/lazy/") }.unique("lazy position state")
    val ints = disposal.fields().filter { it.definingClass==positionField.type }
    if(ints.size!=2 || ints[0].type!=ints[1].type) throw PatchException("List anchor: first-index/offset access changed")
    val readInt = disposal.calls().filter { it.definingClass==ints[0].type && it.returnType=="I" && it.parameterTypes.isEmpty() }.distinctBy { it.toString() }.unique("snapshot integer reader")
    // The mutable scroll position can contain a requested offset before measuring.
    // Read the actual measure result instead. Prove its offset by tracing the value
    // consumed by the native measured-result -> scroll-position offset setter.
    val applyMeasure=lazy.methods.filter { m -> m.parameterTypes.map(CharSequence::toString)==listOf(layout.type,"Z","Z") &&
        m.returnType=="V" && m.fields().any { it.toString()==ints[1].toString() }
    }.unique("native measure result consumer")
    val offsetStore=applyMeasure.instructions.withIndex().filter { item ->
        val next=applyMeasure.instructions.getOrNull(item.index+1)
        val call=next?.getReference<MethodReference>()
        item.value.opcode==Opcode.IGET_OBJECT && item.value.getReference<FieldReference>()?.toString()==ints[1].toString() &&
            next?.opcode==Opcode.INVOKE_VIRTUAL && call?.definingClass==readInt.definingClass &&
            call.parameterTypes.map(CharSequence::toString)==listOf("I") && call.returnType=="V"
    }.unique("measured offset setter")
    val offsetArgs=applyMeasure.instructions[offsetStore.index+1] as FiveRegisterInstruction
    if(offsetArgs.registerCount!=2 || offsetArgs.registerC!=(offsetStore.value as TwoRegisterInstruction).registerA)
        throw PatchException("List anchor: measured offset setter receiver changed")
    val offsetInput=applyMeasure.instructions.take(offsetStore.index).withIndex().filter { item ->
        item.value.opcode==Opcode.IGET && (item.value as TwoRegisterInstruction).registerA==offsetArgs.registerD &&
            item.value.getReference<FieldReference>()?.definingClass==layout.type && item.value.getReference<FieldReference>()?.type=="I"
    }.unique("measured offset value producer")
    if(applyMeasure.instructions.subList(offsetInput.index+1,offsetStore.index+1).any { i ->
        i.opcode.setsRegister() && i is OneRegisterInstruction &&
            (i.registerA==offsetArgs.registerD || (i.opcode.setsWideRegister() && i.registerA+1==offsetArgs.registerD))
    }) throw PatchException("List anchor: measured offset value is overwritten before consumption")
    val measuredOffset=offsetInput.value.getReference<FieldReference>()!!
    val measuredIndex=context.mutableClassDefBy(first.type).methods.filter {
        it.name=="getIndex" && it.parameterTypes.isEmpty() && it.returnType=="I"
    }.unique("measured first item index")
    val rawDelta = lazy.methods.filter { it.parameterTypes.map(CharSequence::toString)==listOf("F") && it.returnType=="F" }.unique("scrollable delegate")
    val gestureOwner = rawDelta.calls().unique("scroll delegate call").definingClass
    val scrolling = lazy.methods.filter { it.returnType=="Z" && it.parameterTypes.isEmpty() && it.calls().any { r -> r.definingClass==gestureOwner } }.unique("scroll in progress")
    adapter("nativeSnapshot",6,"""
        check-cast p0, ${lazy.type}
        invoke-virtual {p0}, $layoutGet
        move-result-object v1
        iget-object v2, v1, $first
        if-eqz v2, :empty
        const/4 v0, 0x3
        new-array v0, v0, [I
        invoke-virtual {v2}, $measuredIndex
        move-result v2
        const/4 v3, 0x0
        aput v2, v0, v3
        iget v1, v1, $measuredOffset
        const/4 v3, 0x1
        aput v1, v0, v3
        invoke-virtual {p0}, $scrolling
        move-result v1
        const/4 v3, 0x2
        aput v1, v0, v3
        return-object v0
        :empty
        const/4 v0, 0x0
        return-object v0
    """)
    val request = lazy.methods.filter { it.parameterTypes.map(CharSequence::toString)==listOf("I","I") && it.returnType=="V" && it.name!="<init>" }.unique("requestScrollToItem")
    if(request.calls().none { it.definingClass==lazy.type && it.parameterTypes.map(CharSequence::toString)==listOf("I","I","Z") })
        throw PatchException("List anchor: requestScrollToItem no longer schedules a non-suspending position")
    adapter("nativeRequest",3,"check-cast p0, ${lazy.type}\ninvoke-virtual {p0, p1, p2}, $request\nreturn-void")

    val regular = Fingerprint(definingClass="Lcom/x/urt/ui/",name="toString",strings=listOf("RegularItem(entryId=",", sortIndex=")).requireSingle("regular UI key")
    val regularId = regular.fieldForToStringLabel("RegularItem(entryId=")
    // R8 fused the string builder helper: bind the single long through its constructor instead.
    val keyCtor = context.mutableClassDefBy(regular.originalClassDef.type).methods.filter {
        it.name=="<init>" && it.parameterTypes.map(CharSequence::toString)==listOf(STR,"J")
    }.unique("regular key identity constructor")
    val sortWrite = keyCtor.instructions.filter { it.opcode==Opcode.IPUT_WIDE }.unique("sort identity write")
    val regularSort = sortWrite.getReference<FieldReference>()!!
    if(keyCtor.implementation!!.registerCount!=5 || (sortWrite as TwoRegisterInstruction).registerA!=3 ||
        sortWrite.registerB!=1 || regular.method.fields().none { it.toString()==regularSort.toString() })
        throw PatchException("List anchor: constructor sort identity mapping is unproven")
    // Unknown key variants (headers/loading/modules) are not persisted in this first implementation.
    // RegularItem is also used for each vertically expanded module item, preserving exact UI indices.
    if(regularId.type!=STR || regularSort.type!="J") throw PatchException("List anchor: regular key representation changed")
    adapter("nativeKey",6,"""
        instance-of v0, p0, ${regular.originalClassDef.type}
        if-eqz v0, :unknown
        check-cast p0, ${regular.originalClassDef.type}
        iget-object v1, p0, $regularId
        iget-wide v2, p0, $regularSort
        invoke-static {v2, v3}, Ljava/lang/Long;->toString(J)$STR
        move-result-object v2
        const-string v0, "regular"
        invoke-static {v0, v1, v2}, $UI_RUNTIME->pack($STR$STR$STR)$STR
        move-result-object v0
        return-object v0
        :unknown
        const/4 v0, 0x0
        return-object v0
    """)
    val dispatcher = component.methods.filter { m -> m.instructions.any { it.getReference<FieldReference>()?.type=="Lkotlin/jvm/functions/Function0;" } &&
        m.calls().any { it.name=="invoke" && it.definingClass=="Lkotlin/jvm/functions/Function0;" } && m.returnType=="V" && m.name!="<init>" }.unique("shared jump dispatcher")
    val callbackLoad = dispatcher.instructions.withIndex().filter { it.value.opcode==Opcode.IGET_OBJECT &&
        it.value.getReference<FieldReference>()?.type=="Lkotlin/jvm/functions/Function0;" }.unique("shared jump callback")
    val receiver = (callbackLoad.value as TwoRegisterInstruction).registerB
    val topBridge = listBridge(componentType,"pikoCancelListAnchor",emptyList(),"V",2,"""
        iget-object v0, p0, $flowField
        invoke-static {v0}, $UI_RUNTIME->top($OBJ)V
        return-void
    """)
    component.methods.add(topBridge)
    dispatcher.addInstructions(callbackLoad.index,"invoke-virtual {v$receiver}, $topBridge")
    // This dispatcher is shared by users and the server. Intercept ONLY the
    // NavigateToTop consumer; user actions must still reach the original callback.
    val navigation = Fingerprint(definingClass="Lcom/x/models/timelines/", name="toString",
        returnType=STR, strings=listOf("NavigateToTop")).requireSingle("server top instruction").originalClassDef.type
    val consumer = Fingerprint(definingClass="Lcom/x/urt/instructions/", returnType=OBJ)
        .scopedMatchAll().map { it.method }.filter { m -> m.instructions.any {
            it.opcode==Opcode.INSTANCE_OF && it.getReference<TypeReference>()?.type==navigation
        } }.unique("server top instruction consumer")
    val branch = consumer.instructions.withIndex().filter {
        it.value.opcode==Opcode.INSTANCE_OF && it.value.getReference<TypeReference>()?.type==navigation
    }.unique("server top instruction branch").index
    val shape = consumer.instructions.drop(branch).take(5)
    if(shape.map { it.opcode } != listOf(Opcode.INSTANCE_OF,Opcode.IF_EQZ,Opcode.IGET_OBJECT,Opcode.INVOKE_VIRTUAL,Opcode.GOTO))
        throw PatchException("List anchor: server navigation consumer control flow changed")
    if((shape[0] as TwoRegisterInstruction).registerA!=(shape[1] as OneRegisterInstruction).registerA ||
        (shape[1] as OffsetInstruction).codeOffset!=shape.drop(1).sumOf { it.codeUnits })
        throw PatchException("List anchor: server navigation branch does not guard only its callback")
    val serverCall=shape[3].getReference<MethodReference>()!!
    if(serverCall.name!="invoke" || serverCall.parameterTypes.isNotEmpty() || serverCall.returnType!=OBJ)
        throw PatchException("List anchor: server navigation callback contract changed")
    val callback=context.mutableClassDefBy(serverCall.definingClass)
    val targetField=callback.fields.filter { it.type==componentType }.unique("server callback component")
    val original=callback.methods.filter { it.toString()==serverCall.toString() }.unique("server callback invoke")
    if(original.calls().none { it.toString()==dispatcher.toString() })
        throw PatchException("List anchor: navigation callback does not reach the verified jump dispatcher")
    val unit=original.fields().filter { it.definingClass=="Lkotlin/Unit;" && it.type=="Lkotlin/Unit;" }.unique("callback Unit result")
    val serverBridge=listBridge(callback.type,"pikoDispatchServerTop",emptyList(),OBJ,5,"""
        iget-object v0, p0, $targetField
        iget-object v0, v0, $repoField
        invoke-interface {v0}, $timelineGet
        move-result-object v1
        invoke-interface {v0}, $identityGet
        move-result-object v0
        iget-object v0, v0, $identityField
        invoke-static {v1, v0}, Lapp/morphe/extension/newx/timeline/ListReadingPosition;->active(Ljava/lang/Enum;$STR)Z
        move-result v0
        if-eqz v0, :native
        sget-object v0, $unit
        return-object v0
        :native
        invoke-virtual {p0}, $serverCall
        move-result-object v0
        return-object v0
    """)
    callback.methods.add(serverBridge)
    val callRegs=shape[3] as FiveRegisterInstruction
    val callbackReg=(shape[2] as TwoRegisterInstruction).registerA
    if(callRegs.registerCount!=1 || callRegs.registerC!=callbackReg || shape[2].getReference<FieldReference>()?.type!=callback.type)
        throw PatchException("List anchor: server callback receiver unproven")
    consumer.replaceInstruction(branch+3,"invoke-virtual {v$callbackReg}, $serverBridge")

    // Reuse the native Top-cursor lookup for opted-in List pull-to-refresh only.
    // Do not change AUTO_REFRESH, server instructions, the DB merge, or inject old data.
    val cursorResolver=component.methods.filter { m -> AccessFlags.STATIC.isSet(m.accessFlags) &&
        m.parameterTypes.size==3 && m.parameterTypes[0].toString()==componentType &&
        m.parameterTypes[1].toString().startsWith("Lcom/x/urt/refresh/") && m.returnType==OBJ &&
        m.fields().any { it.name=="Top" && it.definingClass.startsWith("Lcom/x/models/timelines/") }
    }.unique("native refresh cursor resolver")
    val policyType=cursorResolver.parameterTypes[1].toString()
    val topAt=cursorResolver.instructions.withIndex().filter {
        it.value.opcode==Opcode.SGET_OBJECT && it.value.getReference<FieldReference>()?.name=="Top"
    }.unique("Top cursor enum comparison").index
    val topPolicyLoad=cursorResolver.instructions.take(topAt).withIndex().filter { item ->
        val field=item.value.getReference<FieldReference>()
        item.value.opcode==Opcode.SGET_OBJECT && field!=null &&
            runCatching { context.mutableClassDefBy(field.type).interfaces.contains(policyType) }.getOrDefault(false)
    }.lastOrNull() ?: throw PatchException("List anchor: Top cursor policy not found")
    val topPolicy=topPolicyLoad.value.getReference<FieldReference>()!!
    val policyBranch=cursorResolver.instructions.drop(topPolicyLoad.index).take(4)
    if(policyBranch.map { it.opcode } != listOf(Opcode.SGET_OBJECT,Opcode.INVOKE_STATIC,Opcode.MOVE_RESULT,Opcode.IF_EQZ) ||
        policyBranch[1].getReference<MethodReference>()?.toString()!=
            "Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z")
        throw PatchException("List anchor: Top cursor policy selection is unproven")
    val comparison=policyBranch[1] as FiveRegisterInstruction
    val policyParameter=cursorResolver.implementation!!.registerCount-2
    if(comparison.registerCount!=2 || setOf(comparison.registerC,comparison.registerD)!=
        setOf(policyParameter,(policyBranch[0] as OneRegisterInstruction).registerA) ||
        (policyBranch[2] as OneRegisterInstruction).registerA!=(policyBranch[3] as OneRegisterInstruction).registerA)
        throw PatchException("List anchor: Top policy is not compared with the resolver input")
    val cursorBridge=listBridge(componentType,"pikoListRefreshCursor",listOf(policyType),policyType,5,"""
        iget-object v0, p0, $repoField
        invoke-interface {v0}, $timelineGet
        move-result-object v1
        invoke-interface {v0}, $identityGet
        move-result-object v0
        iget-object v0, v0, $identityField
        invoke-static {v1, v0}, Lapp/morphe/extension/newx/timeline/ListReadingPosition;->active(Ljava/lang/Enum;$STR)Z
        move-result v0
        if-eqz v0, :native
        sget-object v0, $topPolicy
        return-object v0
        :native
        return-object p1
    """)
    val refreshCandidates=dispatcher.calls().filter { it.name=="<init>" }.map { it.definingClass }.distinct()
        // Platform constructors also occur in the dispatcher but have no ClassDef
        // in the APK. Only in-APK SuspendLambda owners are candidates; the required
        // pull-refresh callsite is still checked for exact cardinality below.
        .mapNotNull { runCatching { context.mutableClassDefBy(it) }.getOrNull() }
        .filter { it.superclass=="Lkotlin/coroutines/jvm/internal/SuspendLambda;" }
        .flatMap { it.methods }.filter { m -> m.name=="invokeSuspend" &&
            m.fields().any { it.name=="PULL_TO_REFRESH" } && m.calls().any { it.toString()==cursorResolver.toString() }
        }
    val manual=refreshCandidates.unique("pull refresh coroutine")
    val pull=manual.instructions.withIndex().filter {
        it.value.opcode==Opcode.SGET_OBJECT && it.value.getReference<FieldReference>()?.name=="PULL_TO_REFRESH"
    }.unique("pull refresh request constant").index
    val lookup=manual.instructions.withIndex().drop(pull).take(20).filter {
        it.value.opcode==Opcode.INVOKE_STATIC && it.value.getReference<MethodReference>()?.toString()==cursorResolver.toString()
    }.unique("pull refresh cursor callsite")
    val lookupRegs=lookup.value as FiveRegisterInstruction
    if(lookupRegs.registerCount!=3 || lookupRegs.registerC==lookupRegs.registerD)
        throw PatchException("List anchor: pull refresh cursor registers changed")
    component.methods.add(cursorBridge)
    manual.addInstructions(lookup.index,"""
        invoke-virtual {v${lookupRegs.registerC}, v${lookupRegs.registerD}}, $cursorBridge
        move-result-object v${lookupRegs.registerD}
    """.trimIndent())
    println("List anchor: confirmed keys; server/user top separated; native Top cursor for List pull refresh; no data injection")
}
