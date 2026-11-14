package app.crimera.patches.newx.misc.mediaquality

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.settings.injectReadWithDefault
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.p0Register
import app.morphe.util.cloneMutable
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Media3's Bundle field IDs are its stable wire schema, NOT obfuscated field names.
// 9=maxVideoBitrate, 19=maxAudioBitrate, 21=forceLowest, 22=forceHighestSupported.
private val requiredKeys = mapOf("9" to "I", "j" to "I", "l" to "Z", "m" to "Z")

internal fun resolveQualityFields(owner: ClassDef, serializer: Method): Map<String, FieldReference>? {
    val keys = mutableMapOf<String, String>()
    val constants = mutableMapOf<Int, String>()
    owner.methods.singleOrNull { it.name == "<clinit>" }?.implementation?.instructions?.forEach { op ->
        val reg = (op as? OneRegisterInstruction)?.registerA
        if (op.opcode == Opcode.CONST_STRING || op.opcode == Opcode.CONST_STRING_JUMBO) {
            constants[reg!!] = op.getReference<StringReference>()!!.string
        } else if (op.opcode == Opcode.SPUT_OBJECT) {
            constants[reg]?.let { keys[op.getReference<FieldReference>().toString()] = it }
        } else if (reg != null) constants.remove(reg)
    }
    val keyRegisters = mutableMapOf<Int, String>()
    val valueRegisters = mutableMapOf<Int, FieldReference>()
    val result = mutableMapOf<String, FieldReference>()
    serializer.implementation!!.instructions.forEach { op ->
        val ref = op.getReference<FieldReference>()
        val regs = op.registersUsed
        if (op.opcode == Opcode.SGET_OBJECT && ref != null) {
            keys[ref.toString()]?.let { keyRegisters[regs[0]] = it }
        } else if (op.opcode in setOf(Opcode.IGET, Opcode.IGET_BOOLEAN) && ref != null) {
            valueRegisters[regs[0]] = ref
        } else {
            val call = op.getReference<MethodReference>()
            if (call?.definingClass in setOf("Landroid/os/Bundle;", "Landroid/os/BaseBundle;") && call?.name in setOf("putInt", "putBoolean") && regs.size == 3) {
                val key = keyRegisters[regs[1]]
                val field = valueRegisters[regs[2]]
                if (key in requiredKeys && field != null && field.definingClass == owner.type && field.type == requiredKeys[key]) {
                    if (result.put(key!!, field) != null) throw PatchException("Duplicate quality Bundle key $key")
                }
            }
        }
    }
    return result.takeIf { it.keys == requiredKeys.keys }
}

@Suppress("unused")
val newXForceHighestVideoQualityPatch = bytecodePatch(
    name = "NewX: Force highest video/audio quality",
    description = "Selects the highest supported playback bitrate and removes audio/video bitrate caps. May increase data use and buffering.",
) {
    unsupportedOnMount()
    compatibleWith(COMPATIBILITY_NEW_X)
    val setting = newXToggle(
        id = "newx.post_actions_media.force_highest_video_quality",
        category = Categories.POST_ACTIONS_MEDIA,
        strings = settingStrings("piko_newx_force_highest_video_quality"),
        order = 150, defaultValue = true,
    )
    execute {
        val candidates = Fingerprint(
            definingClass = "Landroidx/media3/common/", returnType = "Landroid/os/Bundle;", parameters = emptyList(),
            filters = listOf(methodCall(name = "putBoolean", parameters = listOf("Ljava/lang/String;", "Z"))),
        ).scopedMatchAllOrNull().orEmpty().mapNotNull { match ->
            resolveQualityFields(match.originalClassDef, match.method)?.let { match.originalClassDef to it }
        }
        if (candidates.size != 1) throw PatchException("Expected unique Media3 quality wire schema, found ${candidates.size}")
        val (owner, fields) = candidates.single()
        val mutable = mutableClassDefBy(owner.type)
        val constructors = mutable.methods.filter { it.name == "<init>" && it.parameterTypes.size == 1 && it.parameterTypes[0] != "Landroid/os/Bundle;" }
        if (constructors.size != 1) throw PatchException("Expected one Media3 parameter-builder constructor")
        val original = constructors.single()
        // cloneMutable shifts incoming parameters above the new locals. Scratch
        // starts at the OLD p0, not old registerCount (which overlaps NEW p0/p1).
        val scratch = original.p0Register
        val method = original.cloneMutable(additionalRegisters = 3)
        mutable.methods.remove(original); mutable.methods.add(method)
        val returns = method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
        if (returns.size != 1) throw PatchException("Unexpected Media3 constructor exits")
        val read = setting.injectReadWithDefault(method, returns.single(), true, scratch..scratch + 1)
        val receiver = method.p0Register
        if (receiver > 15 || scratch + 2 >= receiver) throw PatchException("Quality constructor needs register-safe short field writes")
        method.addInstructionsWithLabels(read.nextIndex, """
            if-eqz v${read.register}, :native_quality
            const v${scratch + 2}, 0x7fffffff
            iput v${scratch + 2}, v$receiver, ${fields.getValue("9")}
            iput v${scratch + 2}, v$receiver, ${fields.getValue("j")}
            const/4 v${scratch + 2}, 0x0
            iput-boolean v${scratch + 2}, v$receiver, ${fields.getValue("l")}
            const/4 v${scratch + 2}, 0x1
            iput-boolean v${scratch + 2}, v$receiver, ${fields.getValue("m")}
            :native_quality
            nop
        """.trimIndent())
    }
}
