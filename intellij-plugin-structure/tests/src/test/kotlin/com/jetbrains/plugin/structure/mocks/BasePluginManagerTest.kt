package com.jetbrains.plugin.structure.mocks

import com.jetbrains.plugin.structure.base.plugin.*
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.rules.FileSystemType
import org.junit.Assert
import java.nio.file.Path

abstract class BasePluginManagerTest<P : Plugin, M : PluginManager<P>>(fileSystemType: FileSystemType) : BaseFileSystemAwareTest(fileSystemType) {

  abstract fun createManager(extractDirectory: Path): M

  fun createPluginSuccessfully(pluginFile: Path): PluginCreationSuccess<P> {
    val pluginCreationResult = createManager(temporaryFolder.newFolder("extract")).createPlugin(pluginFile)
    if (pluginCreationResult is PluginCreationFail) {
      Assert.fail(pluginCreationResult.errorsAndWarnings.joinToString())
    }
    return pluginCreationResult as PluginCreationSuccess<P>
  }

  fun assertProblematicPlugin(pluginFile: Path, expectedProblems: List<PluginProblem>): PluginCreationFail<P> {
    val pluginCreationResult = createManager(temporaryFolder.newFolder("extract")).createPlugin(pluginFile)
    if (pluginCreationResult is PluginCreationSuccess) {
      Assert.fail("must have failed, but warnings: [${pluginCreationResult.warnings.joinToString()}]")
    }
    val creationFail = pluginCreationResult as PluginCreationFail
    val actualProblems = creationFail.errorsAndWarnings
    Assert.assertEquals(expectedProblems.toSet(), actualProblems.toSet())
    return creationFail
  }
}