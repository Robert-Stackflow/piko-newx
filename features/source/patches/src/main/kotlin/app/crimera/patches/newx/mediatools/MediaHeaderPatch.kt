package app.crimera.patches.newx.mediatools

import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val HEADER = "Lapp/morphe/extension/newx/mediatools/HeaderToolsRuntime;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION3 = "Lkotlin/jvm/functions/Function3;"
private fun <T> List<T>.exact(label: String): T = singleOrNull()
    ?: throw PatchException("Media header: expected one $label, got $size: ${joinToString()}")
private fun MethodReference.parameters() = parameterTypes.map(CharSequence::toString)

/** Add children to proven native home/video action slots, preserving Compose lifecycle and layout. */
internal val mediaHeaderPatch = bytecodePatch(default = false) {
    dependsOn(newXSettingsPatch)
    execute {
        val home = Fingerprint(definingClass = "Lcom/x/home/tabbed/", returnType = "V",
            filters = listOf(literal(getResourceId(ResourceType.STRING, "home_logo_scroll_to_top"))))
            .scopedMatchAll().map { it.method }.exact("home logo/header renderer")
        val modifier = home.instructions.filter { it.opcode == Opcode.SGET_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }.distinctBy { it.toString() }
            .filter { classDefByOrNull(it.type)?.interfaces?.contains(MODIFIER) == true }.exact("home empty Modifier")
        val androidView = Fingerprint(definingClass = "Landroidx/compose/ui/viewinterop/", returnType = "V",
            parameters = listOf(FUNCTION1, MODIFIER, FUNCTION1, COMPOSER, "I", "I"))
            .scopedMatchAll().map { it.method }.exact("AndroidView factory/update renderer")
        val runtime = mutableClassDefBy(HEADER)
        val old = runtime.methods.filter { it.name == "render" }.exact("header render stub")
        val render = MutableMethod(ImmutableMethod(runtime.type, old.name, old.parameters, old.returnType, old.accessFlags,
            old.annotations, old.hiddenApiRestrictions, MethodImplementationBuilder(8).methodImplementation))
        render.addInstructions(0, """
            invoke-static {p1}, $HEADER->factory(Z)$FUNCTION1
            move-result-object v0
            sget-object v1, $modifier
            invoke-static {}, $HEADER->updater()$FUNCTION1
            move-result-object v2
            move-object v3, p0
            check-cast v3, $COMPOSER
            const/4 v4, 0x0
            const/4 v5, 0x0
            invoke-static/range {v0 .. v5}, $androidView
            return-void
        """.trimIndent())
        runtime.methods.remove(old); runtime.methods.add(render)

        // The header owns left/right weighted rows around the centered logo. Insert before the
        // consecutive right-row and outer-row closes, not at method entry or the skip branch.
        val ops = home.instructions.toList()
        val rowEnd = (0 until ops.lastIndex).filter { i ->
            val a = ops[i]; val b = ops[i + 1]
            val ref = a.getReference<MethodReference>()
            a.opcode == Opcode.INVOKE_VIRTUAL && b.opcode == Opcode.INVOKE_VIRTUAL && ref != null &&
                ref.definingClass.startsWith("Landroidx/compose/runtime/") && ref.parameters() == listOf("Z") && ref.returnType == "V" &&
                ref.toString() == b.getReference<MethodReference>()?.toString() &&
                a is FiveRegisterInstruction && b is FiveRegisterInstruction && a.registerCount == 2 && b.registerCount == 2 &&
                a.registerC == b.registerC && a.registerD == b.registerD
        }.exact("home right action row close pair")
        val composerRegister = (ops[rowEnd] as FiveRegisterInstruction).registerC
        home.addInstructions(rowEnd, "invoke-static/range {v$composerRegister .. v$composerRegister}, $HEADER->home(Ljava/lang/Object;)V")

        // The portrait header's More action identifies its parent. That parent's shared appbar
        // adapter is also used by the landscape branch, so both wrap the same action contract.
        val videoActions = Fingerprint(definingClass = "Lcom/x/video/tab/", name = "invoke", returnType = "Ljava/lang/Object;",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            filters = listOf(literal(getResourceId(ResourceType.STRING, "more_options"))))
            .scopedMatchAll().filter { match -> match.originalClassDef.fields.any { it.type.startsWith("Ldev/chrisbanes/haze/") } }
            .exact("video header More actions")
        val owner = videoActions.originalClassDef.type
        val parent = Fingerprint(definingClass = "Lcom/x/video/tab/", returnType = "V")
            .scopedMatchAll().map { it.method }.filter { method -> method.instructions.any {
                val ref = it.getReference<MethodReference>(); ref?.definingClass == owner && ref.name == "<init>"
            } }.exact("video header actions owner")
        val header = parent.instructions.mapNotNull { it.getReference<MethodReference>() }.distinctBy { it.toString() }
            .filter { ref -> ref.definingClass.startsWith("Lcom/x/video/tab/") && ref.returnType == "V" &&
                ref.parameters().let { p -> p.size == 5 && p[0] == "I" && p[1] == COMPOSER && p[4] == MODIFIER &&
                    p[2] == p[3] && p[2].startsWith("Landroidx/compose/runtime/internal/") } }.exact("shared video appbar")
        val headerMethod = mutableClassDefBy(header.definingClass).methods.filter { it.toString() == header.toString() }.exact("mutable video appbar")
        val actionType = header.parameters()[3]
        val wrapCall = headerMethod.instructions.withIndex().filter { (_, op) ->
            val ref = op.getReference<MethodReference>()
            ref != null && ref.name == "<init>" && ref.parameters() == listOf(actionType, "I") &&
                classDefByOrNull(ref.definingClass)?.interfaces?.contains(FUNCTION3) == true
        }.exact("video RowScope actions wrapper constructor")
        val op = wrapCall.value
        val registers = when (op) {
            is FiveRegisterInstruction -> listOf(op.registerC, op.registerD, op.registerE).take(op.registerCount)
            is RegisterRangeInstruction -> (op.startRegister until op.startRegister + op.registerCount).toList()
            else -> throw PatchException("Media header: unsupported wrapper invoke")
        }
        if (registers.size != 3) throw PatchException("Media header: unexpected wrapper constructor arguments")
        // Both registers are already local scratch for the native wrapper; preserve all parameters.
        val out = registers[0]; val action = registers[1]
        if (out > 255) throw PatchException("Media header: wrapper destination exceeds move-result encoding")
        headerMethod.addInstructions(wrapCall.index + 1, """
            invoke-static/range {v$action .. v$action}, $HEADER->wrapVideoActions(Ljava/lang/Object;)$FUNCTION3
            move-result-object v$out
        """.trimIndent())
    }
}
