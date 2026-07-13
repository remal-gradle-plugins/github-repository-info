package name.remal.gradle_plugins.github_repository_info;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.packageNameOf;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.unwrapGeneratedSubclass;
import static name.remal.gradle_plugins.toolkit.testkit.ProjectValidations.executeAfterEvaluateActions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.junit.jupiter.api.io.TempDir;

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
        void tokenIsExtractedFromDirectExtraHeader() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            executeGit(
                project.getProjectDir(),
                "config",
                "--local",
                "http.https://github.com/.extraheader",
                basicAuthorizationExtraHeader(SYNTHETIC_TOKEN)
            );

            assertEquals(SYNTHETIC_TOKEN, extension.getGitHubApiTokenFromGitConfig().getOrNull());
        }

        @Test
        void tokenIsExtractedFromIncludeIfReferencedCredentialsFile(@TempDir Path credentialsDir) throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            var credentialsFile = writeCredentialsFile(credentialsDir);

            var gitDirPattern = toGitPathPattern(project.getProjectDir().toPath().resolve(".git").toRealPath());
            executeGit(
                project.getProjectDir(),
                "config",
                "--local",
                "includeIf.gitdir:" + gitDirPattern + ".path",
                credentialsFile.toString()
            );

            assertEquals(SYNTHETIC_TOKEN, extension.getGitHubApiTokenFromGitConfig().getOrNull());
        }

        @Test
        void tokenIsAbsentWhenIncludeIfGitDirDoesNotMatch(
            @TempDir Path credentialsDir,
            @TempDir Path notMatchingDir
        ) throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            var credentialsFile = writeCredentialsFile(credentialsDir);

            var notMatchingGitDirPattern = toGitPathPattern(notMatchingDir.toRealPath().resolve(".git"));
            executeGit(
                project.getProjectDir(),
                "config",
                "--local",
                "includeIf.gitdir:" + notMatchingGitDirPattern + ".path",
                credentialsFile.toString()
            );

            assertNull(
                extension.getGitHubApiTokenFromGitConfig().getOrNull(),
                "token extracted from a credentials file behind a not matching includeIf condition"
            );
        }

        @Test
        void tokenIsAbsentWhenGitConfigHasNoExtraHeader() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            executeGit(
                project.getProjectDir(),
                "remote",
                "add",
                "origin",
                "https://github.com/remal-gradle-plugins/github-repository-info"
            );

            assertNull(
                extension.getGitHubApiTokenFromGitConfig().getOrNull(),
                "token extracted from a git config without extraheader entries"
            );
        }

        @Test
        void notGitRepositoryProducesActionableError() {
            // the project directory is deliberately not initialized as a git repository
            var tokenProvider = extension.getGitHubApiTokenFromGitConfig();
            var exception = assertThrows(Exception.class, tokenProvider::getOrNull);

            var messages = getAllMessages(exception);
            assertTrue(
                messages.contains("git config"),
                () -> "error messages must mention the executed git command:\n" + messages
            );
            assertTrue(
                messages.contains("exit code 128"),
                () -> "error messages must include the git exit code:\n" + messages
            );
            assertTrue(
                messages.contains(project.getProjectDir().toString()),
                () -> "error messages must include the working directory:\n" + messages
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "GITHUB_ACTIONS_TOKEN", matches = ".+")
        void environmentVariableWinsOverGitConfig() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            executeGit(
                project.getProjectDir(),
                "config",
                "--local",
                "http.https://github.com/.extraheader",
                basicAuthorizationExtraHeader(SYNTHETIC_TOKEN)
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
        void tokenIsReadFromRepositoryGitConfig() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            executeGit(
                project.getProjectDir(),
                "config",
                "--local",
                "http.https://github.com/.extraheader",
                basicAuthorizationExtraHeader(SYNTHETIC_TOKEN)
            );

            assertEquals(SYNTHETIC_TOKEN, extension.getGithubApiToken().getOrNull());
        }


        private Path writeCredentialsFile(Path credentialsDir) throws Exception {
            var credentialsFile = credentialsDir.resolve("git-credentials-test.config");
            executeGit(
                project.getProjectDir(),
                "config",
                "--file",
                credentialsFile.toString(),
                "http.https://github.com/.extraheader",
                basicAuthorizationExtraHeader(SYNTHETIC_TOKEN)
            );
            return credentialsFile;
        }

        private String basicAuthorizationExtraHeader(String token) {
            var credentials = "x-access-token:" + token;
            return "AUTHORIZATION: basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
        }

    }

    @Nested
    class GitRemoteDetection {

        GitHubRepositoryInfoExtension extension;

        @BeforeEach
        void beforeEach() {
            extension = project.getExtensions().getByType(GitHubRepositoryInfoExtension.class);
            extension.getRepositoryRootDir().fileValue(project.getProjectDir());
        }

        @Test
        void originRemoteIsPreferredForRemoteInfo() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");
            executeGit(
                project.getProjectDir(),
                "remote",
                "add",
                "aaa-remote",
                "https://example.com/other/repository.git"
            );
            executeGit(
                project.getProjectDir(),
                "remote",
                "add",
                "origin",
                "https://github.example.com/owner/repo.git"
            );

            assertEquals("github.example.com", extension.getGitRemoteHost().getOrNull());
            assertEquals("owner/repo", extension.getGitRemoteRepositoryFullName().getOrNull());
        }

        @Test
        void remoteInfoIsAbsentWithoutRemotes() throws Throwable {
            executeGit(project.getProjectDir(), "init", "--quiet");

            assertNull(
                extension.getGitRemoteHost().getOrNull(),
                "git remote host detected in a repository without remotes"
            );
            assertNull(
                extension.getGitRemoteRepositoryFullName().getOrNull(),
                "git remote repository full name detected in a repository without remotes"
            );
        }

    }


    private static void executeGit(File workingDir, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(asList(args));

        var process = new ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start();
        var output = new String(process.getInputStream().readAllBytes(), UTF_8);
        var exitCode = process.waitFor();
        assertEquals(
            0,
            exitCode,
            () -> format("Command %s failed with exit code %d:%n%s", command, exitCode, output)
        );
    }

    /**
     * Git matches {@code gitdir:} patterns against paths with forward slashes on all OSes.
     */
    private static String toGitPathPattern(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    private static String getAllMessages(Throwable rootThrowable) {
        var messages = new StringBuilder();
        for (var throwable = rootThrowable; throwable != null; throwable = throwable.getCause()) {
            if (messages.length() > 0) {
                messages.append('\n');
            }
            messages.append(throwable);
        }
        return messages.toString();
    }

}
