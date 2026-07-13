package name.remal.gradle_plugins.github_repository_info;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.packageNameOf;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.unwrapGeneratedSubclass;
import static name.remal.gradle_plugins.toolkit.testkit.ProjectValidations.executeAfterEvaluateActions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.github_repository_info.info.GitHubContributor;
import name.remal.gradle_plugins.github_repository_info.info.GitHubFullRepository;
import name.remal.gradle_plugins.github_repository_info.info.GitHubLicenseContent;
import name.remal.gradle_plugins.toolkit.testkit.TaskValidations;
import org.gradle.api.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@RequiredArgsConstructor
@SuppressWarnings("java:S5778")
class GitHubRepositoryInfoPluginTest {

    final Project project;

    @BeforeEach
    void beforeEach() {
        project.getPluginManager().apply(GitHubRepositoryInfoPlugin.class);
    }

    @Test
    void tasksCanBeCreated() {
        var tasks = project.getTasks();
        assertDoesNotThrow(() -> tasks.register("repository", RetrieveGitHubRepositoryInfo.class).get());
        assertDoesNotThrow(() -> tasks.register("license", RetrieveGitHubRepositoryLicenseFileInfo.class).get());
        assertDoesNotThrow(() -> tasks.register("contributors", RetrieveGitHubRepositoryContributorsInfo.class).get());
        assertDoesNotThrow(() -> tasks.register("languages", RetrieveGitHubRepositoryLanguagesInfo.class).get());
    }

    @Test
    void extensionPropertiesAreReadOnly() {
        var extension = project.getExtensions().getByType(GitHubRepositoryInfoExtension.class);

        assertThrows(IllegalStateException.class, () ->
            extension.getRepository().set(mock(GitHubFullRepository.class))
        );
        assertThrows(IllegalStateException.class, () ->
            extension.getLicenseFile().set(mock(GitHubLicenseContent.class))
        );
        assertThrows(IllegalStateException.class, () ->
            extension.getContributors().set(List.of(mock(GitHubContributor.class)))
        );
        assertThrows(IllegalStateException.class, () ->
            extension.getLanguages().set(Map.of("C++", 0))
        );
    }

    @Test
    void pluginTasksDoNotHavePropertyProblems() {
        executeAfterEvaluateActions(project);

        var taskClassNamePrefix = packageNameOf(GitHubRepositoryInfoPlugin.class) + '.';
        project.getTasks().stream().filter(task -> {
            var taskClass = unwrapGeneratedSubclass(task.getClass());
            return taskClass.getName().startsWith(taskClassNamePrefix);
        }).map(TaskValidations::markTaskDependenciesAsSkipped).forEach(TaskValidations::assertNoTaskPropertiesProblems);
    }

    @Nested
    class GithubApiTokenFromGitConfig {

        private static final String SYNTHETIC_TOKEN = "synthetic-git-config-token";

        GitHubRepositoryInfoExtension extension;

        @BeforeEach
        void beforeEach() {
            extension = project.getExtensions().getByType(GitHubRepositoryInfoExtension.class);
            extension.getRepositoryRootDir().fileValue(project.getProjectDir());
            extension.getGithubServerUrl().set("https://github.com");
        }

        @Test
        @DisabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
        @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS_TOKEN", matches = ".+")
        void tokenIsReadFromRepositoryGitConfig() throws Throwable {
            writeProjectGitConfig(
                "[http \"https://github.com/\"]",
                "\textraheader = AUTHORIZATION: basic " + base64("x-access-token:" + SYNTHETIC_TOKEN)
            );

            assertEquals(SYNTHETIC_TOKEN, extension.getGithubApiToken().getOrNull());
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "GITHUB_ACTIONS_TOKEN", matches = ".+")
        void environmentVariableWinsOverGitConfig() throws Throwable {
            writeProjectGitConfig(
                "[http \"https://github.com/\"]",
                "\textraheader = AUTHORIZATION: basic " + base64("x-access-token:" + SYNTHETIC_TOKEN)
            );

            var expectedToken = System.getenv("GITHUB_TOKEN") != null
                ? System.getenv("GITHUB_TOKEN")
                : System.getenv("GITHUB_ACTIONS_TOKEN");
            var token = extension.getGithubApiToken().getOrNull();
            assertNotEquals(SYNTHETIC_TOKEN, token, "git config token must not win over environment variables");
            assertEquals(expectedToken, token);
        }

        @Test
        @DisabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
        @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS_TOKEN", matches = ".+")
        void tokenIsAbsentWhenGitConfigHasNoExtraHeader() throws Throwable {
            writeProjectGitConfig(
                "[remote \"origin\"]",
                "\turl = https://github.com/remal-gradle-plugins/github-repository-info"
            );

            assertNull(
                extension.getGithubApiToken().getOrNull(),
                "githubApiToken resolved without any token source"
            );
        }


        private void writeProjectGitConfig(String... lines) throws IOException {
            var gitConfigPath = project.getProjectDir().toPath().resolve(".git").resolve("config");
            createDirectories(gitConfigPath.getParent());
            writeString(gitConfigPath, String.join("\n", lines) + "\n");
        }

        private String base64(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
        }

    }

}
