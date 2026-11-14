import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.io.File;
import java.util.*;

/** Frozen X 12.43 final-bytecode checks, NOT portable patch fingerprints.
 * Java 21 source mode; classpath requires smali-dexlib2 and Guava.
 * Run against the final patched APK, not the MPP or an unpatched input.
 */
class QualityDexCheck {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        int checked = 0;
        for (var entry : container.getDexEntryNames()) {
            for (var cls : container.getEntry(entry).getDexFile().getClasses()) {
                if (!cls.getType().equals("Landroidx/media3/common/d3;")) continue;
                for (var method : cls.getMethods()) {
                    if (!method.getName().equals("<init>")) continue;
                    var ops = new ArrayList<Instruction>();
                    method.getImplementation().getInstructions().forEach(ops::add);
                    int marker = -1;
                    for (int i = 0; i < ops.size(); i++) {
                        if (ops.get(i) instanceof ReferenceInstruction r &&
                            r.getReference() instanceof StringReference s &&
                            s.getString().equals("newx.post_actions_media.force_highest_video_quality")) marker = i;
                    }
                    require(marker >= 0, "setting read missing");
                    int receiver = method.getImplementation().getRegisterCount() - 2;
                    int branch = -1, address = 0;
                    var addresses = new ArrayList<Integer>();
                    for (var op : ops) { addresses.add(address); address += op.getCodeUnits(); }
                    var values = new HashMap<Integer, Long>();
                    var writes = new HashMap<String, Long>();
                    for (int i = marker; i < ops.size(); i++) {
                        var op = ops.get(i);
                        if (op instanceof OneRegisterInstruction r && op.getOpcode().setsRegister())
                            require(r.getRegisterA() < receiver, "hook overwrites incoming object/builder");
                        if (op.getOpcode() == Opcode.IF_EQZ) {
                            require(branch == -1, "multiple guards"); branch = i;
                        }
                        if (op instanceof NarrowLiteralInstruction n && op instanceof OneRegisterInstruction r)
                            values.put(r.getRegisterA(), (long)n.getNarrowLiteral());
                        if (op instanceof ReferenceInstruction r && r.getReference() instanceof FieldReference f &&
                            (op.getOpcode() == Opcode.IPUT || op.getOpcode() == Opcode.IPUT_BOOLEAN)) {
                            require(branch >= 0, "unguarded write");
                            require(((TwoRegisterInstruction)op).getRegisterB() == receiver, "wrong receiver");
                            writes.put(f.getName(), values.get(((TwoRegisterInstruction)op).getRegisterA()));
                        }
                    }
                    require(writes.equals(Map.of("d", 2147483647L, "u", 2147483647L, "F", 0L, "G", 1L)), "wrong overrides: " + writes);
                    require(branch >= 0, "disabled guard missing");
                    int target = addresses.get(branch) + ((OffsetInstruction)ops.get(branch)).getCodeOffset();
                    int targetIndex = addresses.indexOf(target);
                    require(targetIndex > branch, "invalid disabled branch");
                    require(ops.subList(targetIndex, ops.size()).stream().allMatch(op -> op.getOpcode() == Opcode.NOP || op.getOpcode() == Opcode.RETURN_VOID), "disabled branch fails to bypass writes");
                    checked++;
                }
            }
        }
        require(checked == 1, "unexpected constructor count: " + checked);
        System.out.println("PASS: quality values, disabled branch, and receiver-register safety");
    }
}
