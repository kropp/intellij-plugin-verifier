/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.intellij.problems

import com.jetbrains.plugin.structure.base.problems.InvalidDescriptorProblem
import com.jetbrains.plugin.structure.base.problems.ProblemSolutionHint
import com.jetbrains.plugin.structure.intellij.version.IdeVersion

class PropertyWithDefaultValue(
  descriptorPath: String,
  property: DefaultProperty,
  value: String
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "One of the parameters matches the default value. Please ensure that ${property.propertyName} " +
                    "is not equal to the default value '$value'."
) {
  enum class DefaultProperty(val propertyName: String) {
    ID("<id>"),
    NAME("<name>"),
    VENDOR("<vendor>"),
    VENDOR_URL("<vendor url>"),
    VENDOR_EMAIL("<vendor email>"),
    DESCRIPTION("<description>")
  }

  override val level
    get() = Level.ERROR
}

class InvalidDependencyId(descriptorPath: String, invalidPluginId: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The dependency ID is invalid. '${invalidPluginId.trim()}' cannot be empty and must not contain " +
                    "newline characters."
) {
  override val level
    get() = Level.ERROR
}

class InvalidModuleBean(descriptorPath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <module value> parameter is empty. It must be specified as <module value=\"my.module\"/>."
) {
  override val level
    get() = Level.ERROR
}

class SinceBuildNotSpecified(descriptorPath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <since-build> parameter is not specified in the plugin.xml file."
) {
  override val level
    get() = Level.ERROR
}

class InvalidSinceBuild(
  descriptorPath: String,
  sinceBuild: String
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <since-build> parameter ($sinceBuild) format is invalid. Ensure it is greater than <130>, " +
                    "it doesn't end with a dot star suffix <.*> and represents the actual build numbers."
) {
  override val level
    get() = Level.ERROR
}

class InvalidUntilBuild(
  descriptorPath: String,
  untilBuild: String
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <until-build> parameter ($untilBuild) format is invalid. Ensure it represents the actual build numbers."
) {
  override val level
    get() = Level.ERROR
}

class SinceBuildGreaterThanUntilBuild(
  descriptorPath: String,
  sinceBuild: IdeVersion,
  untilBuild: IdeVersion
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <since-build> parameter ($sinceBuild) must not be greater than the <until-build> parameter ($untilBuild)."
) {
  override val level
    get() = Level.ERROR
}

class ErroneousSinceBuild(
  descriptorPath: String,
  sinceBuild: IdeVersion
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <since-build> parameter ($sinceBuild) does not match the multi-part build number format " +
                    "<branch>.<build_number>.<version>, for example, '182.4132.789'."
) {
  override val hint = ProblemSolutionHint(
    example = "since-build=\"182.4132.789\"",
    documentationUrl = "https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html"
  )

  override val level: Level
    get() = Level.ERROR
}

class ErroneousUntilBuild(
  descriptorPath: String,
  untilBuild: IdeVersion
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <until-build> parameter ($untilBuild) does not match the multi-part build number format " +
                    "<branch>.<build_number>.<version>, for example, '182.4132.789'."
) {
  override val hint = ProblemSolutionHint(
    example = "until-build=\"182.4132.789\"",
    documentationUrl = "https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html"
  )

  override val level: Level
    get() = Level.ERROR
}

class ProductCodePrefixInBuild(descriptorPath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <since-build> and <until-build> parameters must not contain product code prefix."
) {
  override val level: Level
    get() = Level.ERROR
}

class XIncludeResolutionErrors(descriptorPath: String, error: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "Failed to resolve <xi:include> statement in the plugin.xml file. ${error.capitalize()}."
) {
  override val level
    get() = Level.ERROR
}

class ReleaseDateWrongFormat(descriptorPath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The <release-date> parameter must be of YYYYMMDD format (type: integer)."
) {
  override val level
    get() = Level.ERROR
}

class UnableToFindTheme(descriptorPath: String, themePath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The theme description file cannot be found by the path '$themePath'. Ensure the theme description " +
                    "file is present and follows the JSON open-standard file format of key-value pairs."
) {
  override val level
    get() = Level.ERROR
}

class UnableToReadTheme(descriptorPath: String, themePath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The theme description file cannot be read from the path '$themePath'. Ensure the theme description " +
                    "file is present and follows the JSON open-standard file format of key-value pairs."
) {
  override val level
    get() = Level.ERROR
}

class OptionalDependencyDescriptorCycleProblem(
  descriptorPath: String,
  cyclicPath: List<String>
) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "The declared optional dependencies configuration files contain a cycle: " +
          cyclicPath.joinToString(separator = " -> ", postfix = ".")
) {
  override val level
    get() = Level.ERROR
}

/**
 * Indicates optional dependency with empty config file.
 *
 * Example violation:
 * ```
 * <depends optional="true" config-file="">
 *   com.intellij.optional.plugin.id
 * </depends>
 * ```
 *
 */
class OptionalDependencyConfigFileIsEmpty(optionalDependencyId: String, descriptorPath: String) : InvalidDescriptorProblem(
  descriptorPath = descriptorPath,
  detailedMessage = "Optional dependency declaration on '$optionalDependencyId' cannot have empty \"config-file\"."
) {
  override val level
    get() = Level.ERROR
}