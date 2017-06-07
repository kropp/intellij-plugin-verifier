package com.jetbrains.pluginverifier.verifiers.clazz

import com.jetbrains.pluginverifier.problems.InheritFromFinalClassProblem
import com.jetbrains.pluginverifier.utils.BytecodeUtil
import com.jetbrains.pluginverifier.utils.resolveClassOrProblem
import com.jetbrains.pluginverifier.verifiers.VerificationContext
import org.objectweb.asm.tree.ClassNode

class InheritFromFinalClassVerifier : ClassVerifier {
  override fun verify(clazz: ClassNode, ctx: VerificationContext) {
    val superClassName = clazz.superName ?: "java/lang/Object"
    val supClass = ctx.resolveClassOrProblem(superClassName, clazz, { ctx.fromClass(clazz) }) ?: return
    if (BytecodeUtil.isFinal(supClass)) {
      val child = ctx.fromClass(clazz)
      val finalClass = ctx.fromClass(supClass)
      ctx.registerProblem(InheritFromFinalClassProblem(child, finalClass))
    }
  }
}
