package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val REPOST_FILTER = "Lapp/morphe/extension/newx/timeline/TimelineRepostFilter;"
private const val HO_OBJECT = "Ljava/lang/Object;"
private const val HO_STRING = "Ljava/lang/String;"
private val HOME_OPTIONS_COMPATIBILITY = Compatibility(name = "NewX", packageName = "com.twitter.android",
    apkFileType = ApkFileType.APKM, appIconColor = 0x000000,
    targets = listOf(AppTarget(version = "12.22.0-prod.01")))

@Suppress("unused")
val homeTimelineRepostsPatch = bytecodePatch(
    name = "NewX: Per-timeline repost visibility",
    description = "Show or hide reposts separately in For You, Following and Lists.",
) {
    compatibleWith(HOME_OPTIONS_COMPATIBILITY)
    dependsOn(newXTimelineModelAdapterPatch)
    for ((order, suffix) in listOf("for_you", "following", "lists").withIndex()) {
        newXToggle(id = "newx.timeline.show_reposts_$suffix", category = Categories.TIMELINE,
            strings = settingStrings("piko_newx_show_reposts_$suffix"), order = 210 + order,
            defaultValue = true, rebootApp = true)
    }
    execute {
        val postMatch = Fingerprint(definingClass = "Lcom/x/models/timelines/items/", name = "toString", returnType = HO_STRING,
            strings = listOf("UrtTimelinePost(postResult=", ", socialContext="))
            .requireSingle("post social context")
        val social = postMatch.fieldForToStringLabel(", socialContext=")
        mutableClassDefBy(social.definingClass).requirePublicFields(listOf(social))
        val repost = Fingerprint(definingClass = "Lcom/x/models/", name = "toString", returnType = HO_STRING,
            strings = listOf("Repost(repostAuthor=", ", isRepostedByCurrentUser="))
            .requireSingle("repost social-context variant").originalClassDef
        if (repost.superclass != social.type) throw PatchException("Repost is not a post social-context subtype")
        val original = mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
        val target = mutableClassDefBy(REPOST_FILTER)
        val adapters = listOf("isTimelinePost", "isTimelineModule", "isTimelineModuleItem", "getModuleItem",
            "isModuleItemDispensable", "copyModuleItem", "getModuleInnerContent", "getModuleDisplayType",
            "getModuleHeader", "getModuleFooter", "getModuleSortIndex", "getModuleEntryId", "getModuleClientEventInfo",
            "getPostId", "isVerticalConversation", "getVerticalConversationPostIds", "copyVerticalConversation",
            "copyModule", "immutableList")
        for (name in adapters) {
            val from = original.methods.singleOrNull { it.name == name }
                ?: throw PatchException("Repost model adapter missing or ambiguous: $name")
            val to = target.methods.singleOrNull { it.name == name && it.parameterTypes == from.parameterTypes && it.returnType == from.returnType }
                ?: throw PatchException("Repost model adapter signature changed: $name")
            val replacement = MutableMethod(ImmutableMethod(target.type, name, to.parameters, to.returnType,
                to.accessFlags, to.annotations, to.hiddenApiRestrictions, from.implementation))
            target.methods.remove(to)
            target.methods.add(replacement)
        }
        target.patchBridge("getPostSocialContext", HO_OBJECT, HO_OBJECT, """
            check-cast p0, ${social.definingClass}
            iget-object p0, p0, $social
            return-object p0
        """)
        target.patchBridge("isRepostContext", HO_OBJECT, "Z", "instance-of p0, p0, ${repost.type}\nreturn p0")
        val success = NewXTimelineSuccessFingerprint.requireSingle("timeline success constructor").method
        val timeline = success.parameterTypes[0].toString()
        if (mutableClassDefBy(timeline).superclass != "Ljava/lang/Enum;") throw PatchException("Success timeline type is not an enum")
        success.addInstructions(0, """
            invoke-static {p2, p1}, $REPOST_FILTER->filter(${HO_OBJECT}Ljava/lang/Enum;)$HO_OBJECT
            move-result-object p2
            check-cast p2, ${success.parameterTypes[1]}
        """.trimIndent())
    }
}

@Suppress("unused")
val homeListTextOnlyTabsPatch = bytecodePatch(
    name = "NewX: Text-only pinned List tabs",
    description = "Hide only List tab logos in both home tab renderers, keeping titles and other tabs unchanged.",
) {
    compatibleWith(HOME_OPTIONS_COMPATIBILITY)
    execute {
        val tab = Fingerprint(definingClass = "Lcom/x/home/", name = "toString", returnType = HO_STRING,
            strings = listOf("Tab(homeTabType=", ", title=", ", logoUrl="))
            .requireSingle("home tab model")
        val homeType = tab.fieldForToStringLabel("Tab(homeTabType=")
        val logo = tab.fieldForToStringLabel(", logoUrl=")
        if (logo.type != HO_STRING) throw PatchException("Home tab logo is not a String")
        val generic = Fingerprint(definingClass = "Lcom/x/home/", name = "toString", returnType = HO_STRING,
            strings = listOf("Generic(pinnedTimeline="))
            .requireSingle("generic pinned home tab")
        val pinned = generic.fieldForToStringLabel("Generic(pinnedTimeline=")
        if (generic.originalClassDef.superclass != homeType.type) throw PatchException("Generic home-tab hierarchy changed")
        val list = Fingerprint(definingClass = "Lcom/x/models/pinnedtimelines/", name = "toString", returnType = HO_STRING,
            strings = listOf("ListPinnedTimeline(list="))
            .requireSingle("pinned List type").originalClassDef
        if (list.superclass != pinned.type) throw PatchException("Pinned List hierarchy changed")
        val owner = mutableClassDefBy(tab.originalClassDef.type)
        owner.requirePublicFields(listOf(homeType, logo))
        mutableClassDefBy(generic.originalClassDef.type).requirePublicFields(listOf(pinned))
        val bridge = listBridge(owner.type, "pikoTextOnlyListTabLogo", emptyList(), HO_STRING, 4, """
            iget-object v0, p0, $homeType
            instance-of v1, v0, ${generic.originalClassDef.type}
            if-eqz v1, :original
            check-cast v0, ${generic.originalClassDef.type}
            iget-object v0, v0, $pinned
            instance-of v0, v0, ${list.type}
            if-eqz v0, :original
            const/4 v0, 0x0
            return-object v0
            :original
            iget-object v0, p0, $logo
            return-object v0
        """)
        owner.methods.add(bridge)
        val sites = mutableListOf<Pair<MutableMethod, Int>>()
        for (match in Fingerprint(definingClass = "Lcom/x/home/tabbed/").scopedMatchAll()) {
            for ((i, instruction) in match.method.instructions.withIndex()) {
                if (instruction.opcode == Opcode.IGET_OBJECT && instruction.getReference<FieldReference>()?.toString() == logo.toString())
                    sites += match.method to i
            }
        }
        if (sites.size != 2 || sites.map { it.first.definingClass }.distinct().size != 2)
            throw PatchException("Expected two home logo readers, found: $sites")
        for ((method, index) in sites) {
            val load = method.instructions[index] as TwoRegisterInstruction
            method.removeInstruction(index)
            method.addInstructions(index, """
                invoke-virtual {v${load.registerB}}, $bridge
                move-result-object v${load.registerA}
            """.trimIndent())
        }
    }
}
