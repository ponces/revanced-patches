package app.revanced.patches.youtube.ad.general.bytecode.patch

import app.revanced.extensions.findMutableMethodOf
import app.revanced.extensions.toErrorResult
import app.revanced.patcher.annotation.Description
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.instruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patcher.patch.annotations.DependsOn
import app.revanced.patcher.patch.annotations.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.shared.misc.fix.verticalscroll.patch.VerticalScrollPatch
import app.revanced.patches.youtube.ad.general.annotation.HideAdsCompatibility
import app.revanced.patches.youtube.ad.general.bytecode.fingerprints.ReelConstructorFingerprint
import app.revanced.patches.youtube.ad.general.resource.patch.HideAdsResourcePatch
import app.revanced.patches.youtube.misc.fix.backtoexitgesture.patch.FixBackToExitGesturePatch
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction
import org.jf.dexlib2.iface.instruction.formats.Instruction31i
import org.jf.dexlib2.iface.instruction.formats.Instruction35c


@Patch
@DependsOn([HideAdsResourcePatch::class, VerticalScrollPatch::class, FixBackToExitGesturePatch::class])
@Name("hide-ads")
@Description("Removes general ads.")
@HideAdsCompatibility
@Version("0.0.1")
class HideAdsPatch : BytecodePatch(
    listOf(ReelConstructorFingerprint)
) {
    override fun execute(context: BytecodeContext): PatchResult {
        fun String.buildHideCall(viewRegister: Int) = "invoke-static { v$viewRegister }, " +
                "Lapp/revanced/integrations/patches/litho/AdsFilter;" +
                "->" +
                "$this(Landroid/view/View;)V"

        fun MutableMethod.injectHideCall(insertIndex: Int, viewRegister: Int, method: String) =
            this.addInstruction(insertIndex, method.buildHideCall(viewRegister))

        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                with(method.implementation) {
                    this?.instructions?.forEachIndexed { index, instruction ->
                        if (instruction.opcode != org.jf.dexlib2.Opcode.CONST)
                            return@forEachIndexed
                        // Instruction to store the id adAttribution into a register
                        if ((instruction as Instruction31i).wideLiteral != HideAdsResourcePatch.adAttributionId)
                            return@forEachIndexed

                        val insertIndex = index + 1

                        // Call to get the view with the id adAttribution
                        with(instructions.elementAt(insertIndex)) {
                            if (opcode != org.jf.dexlib2.Opcode.INVOKE_VIRTUAL)
                                return@forEachIndexed

                            // Hide the view
                            val viewRegister = (this as Instruction35c).registerC
                            context.proxy(classDef)
                                .mutableClass
                                .findMutableMethodOf(method)
                                .injectHideCall(insertIndex, viewRegister, "hideAdAttributionView")
                        }
                    }
                }
            }
        }

        ReelConstructorFingerprint.result?.let {
            // iput-object v$viewRegister, ...
            val insertIndex = it.scanResult.patternScanResult!!.startIndex + 2

            it.mutableMethod.apply {
                val viewRegister = instruction<TwoRegisterInstruction>(insertIndex).registerA

                injectHideCall(insertIndex, viewRegister, "hideReelView")
            }
        } ?: return ReelConstructorFingerprint.toErrorResult()

        return PatchResultSuccess()
    }

}