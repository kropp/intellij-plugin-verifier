package com.jetbrains.intellij.feature.extractor

/**
 * Extension point used by plugins to specify
 * additional supported features.
 */
enum class ExtensionPoint(val extensionPointName: String) {
  CONFIGURATION_TYPE("com.intellij.configurationType"),
  FACET_TYPE("com.intellij.facetType"),
  FILE_TYPE("com.intellij.fileTypeFactory"),
  ARTIFACT_TYPE("com.intellij.packaging.artifactType")
  //TODO: module type: see https://plugins.jetbrains.com/plugin/9238 for example
}