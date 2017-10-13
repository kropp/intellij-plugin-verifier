package com.jetbrains.pluginverifier.verifiers

import com.jetbrains.pluginverifier.misc.singletonOrEmpty
import com.jetbrains.pluginverifier.results.access.AccessType
import com.jetbrains.pluginverifier.results.deprecated.DeprecatedClassUsage
import com.jetbrains.pluginverifier.results.location.Location
import com.jetbrains.pluginverifier.results.problems.ClassNotFoundProblem
import com.jetbrains.pluginverifier.results.problems.IllegalClassAccessProblem
import com.jetbrains.pluginverifier.results.problems.InvalidClassFileProblem
import com.jetbrains.pluginverifier.results.reference.ClassReference
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

sealed class ClsResolution {
  object NotFound : ClsResolution()
  object ExternalClass : ClsResolution()
  data class InvalidClassFile(val reason: String) : ClsResolution()
  data class IllegalAccess(val resolvedNode: ClassNode, val accessType: AccessType) : ClsResolution()
  data class FoundDeprecated(val node: ClassNode) : ClsResolution()
  data class Found(val node: ClassNode) : ClsResolution()
}

private val ClassFileLogger: Logger = LoggerFactory.getLogger("plugin.verifier.class.file.reader")

/**
 * To resolve an unresolved symbolic reference from D to a class or interface C denoted by N, the following steps are performed:
 * ...<JVM-related stuff>...
 *  3) Finally, access permissions to C are checked.
 *  If C is not accessible (§5.4.4) to D, class or interface resolution throws an IllegalAccessError.
 */
fun VerificationContext.resolveClass(className: String, lookup: ClassNode): ClsResolution {
  if (isExternalClass(className)) {
    return ClsResolution.ExternalClass
  }
  val node = try {
    classLoader.findClass(className)
  } catch (e: Exception) {
    ClassFileLogger.debug("Unable to read class $className", e)
    return ClsResolution.InvalidClassFile("Unable to read class-file $className using ASM Java Bytecode engineering library. Internal error: ${e.message}")
  }
  if (node != null) {
    if (!isClassAccessibleToOtherClass(node, lookup)) {
      return ClsResolution.IllegalAccess(node, node.access.getAccessType())
    }
    return if (node.isDeprecated()) {
      ClsResolution.FoundDeprecated(node)
    } else {
      ClsResolution.Found(node)
    }
  }
  return ClsResolution.NotFound
}


fun VerificationContext.resolveClassOrProblem(className: String,
                                              lookup: ClassNode,
                                              lookupLocation: () -> Location): ClassNode? {
  val resolution = resolveClass(className, lookup)
  return with(resolution) {
    when (this) {
      is ClsResolution.Found -> node
      is ClsResolution.FoundDeprecated -> {
        registerDeprecatedUsage(DeprecatedClassUsage(fromClass(node), lookupLocation()))
        node
      }
      ClsResolution.ExternalClass -> null
      ClsResolution.NotFound -> {
        registerProblem(ClassNotFoundProblem(ClassReference(className), lookupLocation()))
        null
      }
      is ClsResolution.IllegalAccess -> {
        registerProblem(IllegalClassAccessProblem(fromClass(resolvedNode), accessType, lookupLocation()))
        null
      }
      is ClsResolution.InvalidClassFile -> {
        registerProblem(InvalidClassFileProblem(ClassReference(className), lookupLocation(), reason))
        null
      }
    }
  }
}

fun VerificationContext.checkClassExistsOrExternal(className: String, lookup: ClassNode, registerMissing: () -> Location) {
  resolveClassOrProblem(className, lookup, registerMissing)
}

@Suppress("UNCHECKED_CAST")
private fun VerificationContext.resolveAllDirectParents(classNode: ClassNode): List<ClassNode> {
  val parents = classNode.superName.singletonOrEmpty() + (classNode.interfaces as? List<String>).orEmpty()
  return parents.mapNotNull { resolveClassOrProblem(it, classNode, { fromClass(classNode) }) }
}

fun VerificationContext.isSubclassOf(child: ClassNode, possibleParent: ClassNode): Boolean {
  if (possibleParent.name == "java/lang/Object") {
    return true
  }

  val directParents = resolveAllDirectParents(child)

  val queue = LinkedList<ClassNode>()
  queue.addAll(directParents)

  val visited = hashSetOf<String>()
  visited.addAll(directParents.map { it.name })

  while (queue.isNotEmpty()) {
    val node = queue.poll()
    if (node.name == possibleParent.name) {
      return true
    }

    resolveAllDirectParents(node).filterNot { it.name in visited }.forEach {
      visited.add(it.name)
      queue.addLast(it)
    }
  }

  return false
}
