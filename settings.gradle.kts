pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories {
 exclusiveContent { forRepository { google() }; filter { includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google\\.android.*") } }
 mavenCentral()
} }
rootProject.name="BillProgressImporter"
include(":app")
