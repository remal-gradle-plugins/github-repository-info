package name.remal.gradle_plugins.github_repository_info;

import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.toolkit.testkit.functional.GradleProject;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class GitHubRepositoryInfoPluginAppliedViaSettingsFunctionalTest {

    final GradleProject project;

    @Test
    void appliedViaSettingsIsAppliedToProject() {
        project.forSettingsFile(settings -> settings.applyPlugin("name.remal.github-repository-info"));

        // The plugin must NOT be applied via the project's build file: it should reach the project
        // solely through the Settings-level application propagating via GradleLifecycle.beforeProject.
        // The assertion runs at configuration time (not inside doLast): accessing Task.project at
        // execution time is unsupported with the configuration cache.
        project.getBuildFile().line(
            "assert pluginManager.hasPlugin('name.remal.github-repository-info')"
        );
        project.getBuildFile().line("tasks.register('assertPluginApplied')");

        project.assertBuildSuccessfully("assertPluginApplied");
    }

}
