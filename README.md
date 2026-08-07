**Tested on Java LTS versions from <!--property:java-runtime.min-version-->11<!--/property--> to <!--property:java-runtime.max-version-->26<!--/property-->.**

**Tested on Gradle versions from <!--property:gradle-api.min-version-->8.0<!--/property--> to <!--property:gradle-api.max-version-->9.7.0<!--/property-->.**

# `name.remal.github-repository-info` plugin

[![configuration cache: supported](https://img.shields.io/static/v1?label=configuration%20cache&message=supported&color=success)](https://docs.gradle.org/current/userguide/configuration_cache.html)

<!--plugin-usage:name.remal.github-repository-info-->
```groovy
plugins {
    id 'name.remal.github-repository-info' version '1.1.1'
}
```
<!--/plugin-usage-->

&nbsp;

This Gradle plugin that provides functionality to access GitHub repository information
directly from the build environment.

It can be useful for projects that need to load some GitHub repository information dynamically.
This is how you can add Maven POM license using this plugin:

```groovy
apply plugin: 'maven-publish'

publishing.publications.withType(MavenPublication).configureEach {
  pom {
    licenses {
      license {
        name = githubRepositoryInfo.licenseFile.map { it.license.name }
        url = githubRepositoryInfo.licenseFile.map { it.htmlUrl }
      }
    }
  }
}
```

All the information is loaded lazily and cached during the build.

## `githubRepositoryInfo` extension

This plugin creates `githubRepositoryInfo` extension that provides the following
[read-only](https://docs.gradle.org/current/javadoc/org/gradle/api/provider/HasConfigurableValue.html#disallowChanges())
[properties](https://docs.gradle.org/current/javadoc/org/gradle/api/provider/Property.html).

* `githubRepositoryInfo.repository` - provides information about the repository itself ([example](https://api.github.com/repos/remal-gradle-plugins/github-repository-info))
* `githubRepositoryInfo.licenseFile` - provides information about the repository license file ([example](https://api.github.com/repos/remal-gradle-plugins/github-repository-info/license))
* `githubRepositoryInfo.contributors` - provides a list of repository contributors ([example](https://api.github.com/repos/remal-gradle-plugins/github-repository-info/contributors))
* `githubRepositoryInfo.languages` - provides a map of programming languages used in the repository with their byte size ([example](https://api.github.com/repos/remal-gradle-plugins/github-repository-info/languages))

All these properties load data lazily
and **cache the result** in `./build/tmp/.cache/name.remal.github-repository-info` directory.
To get updated data, you need to delete this cache directory.

Also, you can get or configure general GitHub connection settings via the following `Property<String>` properties.
These properties are automatically configured from GitHub Actions environment variables or remote URL in the `.git/config` file.

* `githubRepositoryInfo.githubApiUrl` - GitHub API URL (default: `https://api.github.com`).
* `githubRepositoryInfo.repositoryFullName` - repository full name (e.g., `owner/repo`).
* `githubRepositoryInfo.githubApiToken` - GitHub API authentication token.
  See the [Configuration](#configuration) section.
* `githubRepositoryInfo.githubServerUrl` - GitHub URL (default: `https://github.com`).
  Not used by this plugin directly, but can be useful for constructing links.

<!--
## Loading data via `RetrieveGitHubRepository*Info` tasks

You can register tasks to retrieve GitHub repository information explicitly.

All these tasks emit a JSON file with the retrieved data. This data can be deserialized using the `GitHubJsonDeserializer` class.

These tasks do **not** override the result JSON file, so to get an updated result, you need to delete the previous result file.

```groovy
import static name.remal.gradle_plugins.github_repository_info.GitHubJsonDeserializer.deserializerGitHubRepositoryContributorsInfo
import static name.remal.gradle_plugins.github_repository_info.GitHubJsonDeserializer.deserializerGitHubRepositoryInfo
import static name.remal.gradle_plugins.github_repository_info.GitHubJsonDeserializer.deserializerGitHubRepositoryLanguagesInfo
import static name.remal.gradle_plugins.github_repository_info.GitHubJsonDeserializer.deserializerGitHubRepositoryLicenseFileInfo

import name.remal.gradle_plugins.github_repository_info.RetrieveGitHubRepositoryContributorsInfo
import name.remal.gradle_plugins.github_repository_info.RetrieveGitHubRepositoryInfo
import name.remal.gradle_plugins.github_repository_info.RetrieveGitHubRepositoryLanguagesInfo
import name.remal.gradle_plugins.github_repository_info.RetrieveGitHubRepositoryLicenseFileInfo
import name.remal.gradle_plugins.github_repository_info.info.GitHubContributor
import name.remal.gradle_plugins.github_repository_info.info.GitHubFullRepository
import name.remal.gradle_plugins.github_repository_info.info.GitHubLicenseContent

def repositoryInfoTask = tasks.register('repositoryInfo', RetrieveGitHubRepositoryInfo) { // provides information about the repository itself
  outputJsonFile = file('...')
}

Provider<GitHubFullRepository> repositoryInfo = repositoryInfoTask.flatMap { it.outputJsonFile }.map { deserializerGitHubRepositoryInfo(it) }


def repositoryLicenseFileInfoTask = tasks.register('repositoryLicenseFileInfo', RetrieveGitHubRepositoryLicenseFileInfo) { // provides information about the repository license file
  outputJsonFile = file('...')
}

Provider<GitHubLicenseContent> repositoryLicenseFileInfo = repositoryLicenseFileInfoTask.flatMap { it.outputJsonFile }.map { deserializerGitHubRepositoryLicenseFileInfo(it) }


def repositoryContributorsInfoTask = tasks.register('repositoryContributorsInfo', RetrieveGitHubRepositoryContributorsInfo) { // provides information about the repository contributors
  outputJsonFile = file('...')
}

Provider<List<GitHubContributor>> repositoryContributorsInfo = repositoryContributorsInfoTask.flatMap { it.outputJsonFile }.map { deserializerGitHubRepositoryContributorsInfo(it) }


def repositoryLanguagesInfoTask = tasks.register('repositoryLanguagesInfo', RetrieveGitHubRepositoryLanguagesInfo) { // provides a map of programming languages used in the repository with their byte size
  outputJsonFile = file('...')
}

Provider<Map<String, Integer>> repositoryLanguagesInfo = repositoryLanguagesInfoTask.flatMap { it.outputJsonFile }.map { deserializerGitHubRepositoryLanguagesInfo(it) }
```
-->

## Configuration

**For public GitHub repositories, the plugin should work without any additional configuration.**

However, for **private** repositories or to **increase the rate limits**, you need to provide a GitHub token.

To configure a GitHub token for GitHub Actions,
set `GITHUB_TOKEN` environment variable for your GitHub Actions job:

```yaml
- name: Run Gradle build
  env:
    GITHUB_TOKEN: ${{secrets.GITHUB_TOKEN}}
  run: ./gradlew build
```

`GITHUB_ACTIONS_TOKEN` environment variable can be used instead of `GITHUB_TOKEN`.

In GitHub Actions, the plugin usually works even without any token configuration.
If no token is configured, the plugin falls back to the credentials
persisted in the repository git config by [`actions/checkout`](https://github.com/actions/checkout)
(unless its `persist-credentials` input is disabled).
The plugin invokes the `git` CLI (`git config --local --includes --list`) to read the repository config,
so the credentials are found for all `actions/checkout` layouts:
both the direct `extraheader` value and the newer credentials file referenced via `includeIf`.
The `git` executable is required on `PATH` when these values are needed
(the build fails with a clear error otherwise; `includeIf` resolution requires git 2.13+).

To configure a GitHub token for local development,
add `name.remal.github-repository-info.api-token` property to your `~/.gradle/gradle.properties` file:

```properties
name.remal.github-repository-info.api-token = <your github token here>
```

This file is in [the Gradle User Home directory](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home),
so it won't be committed.

The token sources in priority order:

1. `GITHUB_TOKEN` environment variable
2. `GITHUB_ACTIONS_TOKEN` environment variable
3. `name.remal.github-repository-info.api-token` Gradle property
4. `name.remal.github-repository-info.api.token` Gradle property
5. Credentials persisted in the repository git config by [`actions/checkout`](https://github.com/actions/checkout) (read via the git CLI, following `include`/`includeIf`)
