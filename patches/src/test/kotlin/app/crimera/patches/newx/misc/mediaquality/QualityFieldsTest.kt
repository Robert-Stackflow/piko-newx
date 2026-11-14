package app.crimera.patches.newx.misc.mediaquality

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import kotlin.test.*

class QualityFieldsTest {
    private fun fixture(bundle: String = "BaseBundle", missing: Boolean = false, duplicate: Boolean = false): Pair<ImmutableClassDef, ImmutableMethod> {
        val owner = "Landroidx/media3/common/RenamedParameters;"
        val specs = listOf("9" to "I", "j" to "I", "l" to "Z", "m" to "Z")
        fun method(name: String, returnType: String, lines: List<String>): ImmutableMethod {
            val builder = MethodImplementationBuilder(5)
            lines.forEach { builder.addInstruction(it.toInstruction()) }
            return ImmutableMethod(owner, name, emptyList(), returnType, 1, emptySet(), emptySet(), builder.methodImplementation)
        }
        val init = method("<clinit>", "V", specs.flatMapIndexed { i, (key, _) -> listOf(
            "const-string v0, \"$key\"", "sput-object v0, $owner->key$i:Ljava/lang/String;"
        ) } + "return-void")
        fun write(i: Int, type: String) = listOf(
            "sget-object v1, $owner->key$i:Ljava/lang/String;",
            "iget${if(type == "Z") "-boolean" else ""} v2, v4, $owner->value$i:$type",
            "invoke-virtual {v0, v1, v2}, Landroid/os/$bundle;->put${if(type == "Z") "Boolean" else "Int"}(Ljava/lang/String;$type)V"
        )
        val serializer = method("renamedToBundle", "Landroid/os/Bundle;", specs.flatMapIndexed { i, (_, type) ->
            if(missing && i == 3) emptyList() else write(i,type)
        } + (if(duplicate) write(3,"Z") else emptyList()) + "return-object v0")
        val cls = ImmutableClassDef(owner, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(init,serializer))
        return cls to serializer
    }
    @Test fun `resolves renamed fields through BaseBundle wire keys`() {
        val (owner, method) = fixture()
        assertEquals(setOf("9","j","l","m"), resolveQualityFields(owner,method)!!.keys)
        assertEquals("value3", resolveQualityFields(owner,method)!!["m"]!!.name)
    }
    @Test fun `also accepts Bundle declared methods`() {
        val (owner, method) = fixture(bundle="Bundle")
        assertNotNull(resolveQualityFields(owner,method))
    }
    @Test fun `rejects incomplete capability`() {
        val (owner, method) = fixture(missing=true)
        assertNull(resolveQualityFields(owner,method))
    }
    @Test fun `rejects duplicate semantic fields`() {
        val (owner, method) = fixture(duplicate=true)
        assertFailsWith<PatchException> { resolveQualityFields(owner,method) }
    }
}
