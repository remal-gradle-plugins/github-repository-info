package name.remal.gradle_plugins.github_repository_info;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toUnmodifiableList;
import static lombok.AccessLevel.PUBLIC;
import static name.remal.gradle_plugins.github_repository_info.GitConfigOutputUtils.parseGitConfigNullOutput;

import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.Nullable;

/**
 * Reads the repository git config by executing the {@code git} CLI.
 *
 * <p>Delegating to the CLI makes git itself resolve {@code include} and {@code includeIf} directives,
 * so credentials persisted by all {@code actions/checkout} versions are found
 * (new versions persist credentials in a separate file referenced via {@code includeIf}).
 *
 * <p>Only the entries the plugin consumes are returned,
 * which keeps the configuration cache input of this value source small.
 */
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class GitConfigValueSource implements ValueSource<GitConfigEntries, GitConfigValueSource.Parameters> {

    interface Parameters extends ValueSourceParameters {
        DirectoryProperty getRepositoryRootDir();
    }


    private static final List<String> GIT_CONFIG_COMMAND = List.of(
        "git", "config", "--local", "--includes", "--list", "--null"
    );

    private static final String EXPLICIT_VALUES_HINT =
        "Alternatively, provide the GitHub repository information explicitly"
            + " via the GITHUB_TOKEN / GITHUB_REPOSITORY / GITHUB_SERVER_URL environment variables"
            + " or the `name.remal.github-repository-info.*` Gradle properties.";

    private static final String HTTP = "http";
    private static final String EXTRA_HEADER = "extraheader";
    private static final String REMOTE = "remote";
    private static final String URL = "url";


    @Inject
    protected abstract ExecOperations getExecOperations();

    @Nullable
    @Override
    public GitConfigEntries obtain() {
        var repositoryRootDir = getParameters().getRepositoryRootDir().getAsFile().getOrNull();
        if (repositoryRootDir == null) {
            return null;
        }

        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        final ExecResult execResult;
        try {
            execResult = getExecOperations().exec(spec -> {
                spec.commandLine(GIT_CONFIG_COMMAND);
                spec.setWorkingDir(repositoryRootDir);
                spec.setStandardOutput(stdout);
                spec.setErrorOutput(stderr);
                spec.setIgnoreExitValue(true);
            });
        } catch (Exception exception) {
            throw new GitConfigReadException(
                format(
                    "Failed to execute `%s` in `%s` to read the repository git config."
                        + " Install git and make sure the `git` executable is available on PATH. %s",
                    join(" ", GIT_CONFIG_COMMAND),
                    repositoryRootDir,
                    EXPLICIT_VALUES_HINT
                ),
                exception
            );
        }

        var exitValue = execResult.getExitValue();
        if (exitValue != 0) {
            throw new GitConfigReadException(format(
                "`%s` executed in `%s` failed with exit code %d. %s%nGit error output:%n%s",
                join(" ", GIT_CONFIG_COMMAND),
                repositoryRootDir,
                exitValue,
                EXPLICIT_VALUES_HINT,
                stderr.toString(UTF_8)
            ));
        }

        var entries = parseGitConfigNullOutput(stdout.toString(UTF_8)).stream()
            .filter(GitConfigValueSource::isRelevantEntry)
            .collect(toUnmodifiableList());
        return new GitConfigEntries(entries);
    }

    private static boolean isRelevantEntry(GitConfigEntry entry) {
        return (entry.getSection().equals(HTTP) && entry.getName().equals(EXTRA_HEADER))
            || (entry.getSection().equals(REMOTE) && entry.getName().equals(URL));
    }

}
