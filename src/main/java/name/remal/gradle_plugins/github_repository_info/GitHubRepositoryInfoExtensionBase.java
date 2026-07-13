package name.remal.gradle_plugins.github_repository_info;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static name.remal.gradle_plugins.github_repository_info.GitHubApiTokenUtils.extractGitHubApiTokenFromGitConfigEntries;
import static name.remal.gradle_plugins.toolkit.ConfigurationCacheSafeSystem.getConfigurationCacheSafeOptionalEnv;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.initialization.BuildCancellationToken;

abstract class GitHubRepositoryInfoExtensionBase implements GitHubRepositoryInfoSettings {

    private static final String DEFAULT_REMOTE_NAME = "origin";

    private static final String REMOTE = "remote";
    private static final String URL = "url";


    @Internal
    protected abstract Property<GitHubDataFetcher> getGitHubDataFetcher();


    @Internal
    public abstract DirectoryProperty getRepositoryRootDir();

    @Internal
    public abstract Property<String> getGithubServerUrl();

    private final Provider<GitConfigEntries> repositoryGitConfigEntries = getProviders().of(
        GitConfigValueSource.class,
        spec -> spec.getParameters().getRepositoryRootDir().set(getRepositoryRootDir())
    );

    {
        getGithubApiUrl().convention(
            getProviders().environmentVariable("GITHUB_API_URL")
                .orElse(getGitRemoteHost().map(host -> "https://api." + host))
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.api-url"))
                .orElse("https://api.github.com")
        );
        getRepositoryFullName().convention(
            getProviders().environmentVariable("GITHUB_REPOSITORY")
                .orElse(getGitRemoteRepositoryFullName())
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.repository"))
        );
        getGithubApiToken().convention(
            getProviders().provider(() -> getConfigurationCacheSafeOptionalEnv("GITHUB_TOKEN"))
                .orElse(getProviders().provider(() -> getConfigurationCacheSafeOptionalEnv("GITHUB_ACTIONS_TOKEN")))
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.api-token"))
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.api.token"))
                .orElse(getGitHubApiTokenFromGitConfig())
        );
        getGithubServerUrl().convention(
            getProviders().environmentVariable("GITHUB_SERVER_URL")
                .orElse(getGitRemoteHost().map(host -> "https://" + host))
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.server-url"))
                .orElse("https://github.com")
        );
    }

    @VisibleForTesting
    Provider<String> getGitHubApiTokenFromGitConfig() {
        return getProviders().provider(() -> {
            var gitConfigEntries = repositoryGitConfigEntries.getOrNull();
            if (gitConfigEntries == null) {
                return null;
            }

            return extractGitHubApiTokenFromGitConfigEntries(
                gitConfigEntries.getEntries(),
                getGithubServerUrl().getOrNull()
            );
        });
    }


    @Internal
    protected abstract Property<String> getGitRemoteHost();

    @Internal
    protected abstract Property<String> getGitRemoteRepositoryFullName();

    {
        var gitRemoteUrl = getObjects().property(String.class);
        gitRemoteUrl.value(getProviders().provider(() -> {
            var gitConfigEntries = repositoryGitConfigEntries.getOrNull();
            if (gitConfigEntries == null) {
                return null;
            }

            var remoteNamesComparator = comparing(
                (GitConfigEntry entry) -> requireNonNull(entry.getSubsection()),
                (name1, name2) -> {
                    if (Objects.equals(name1, name2)) {
                        return 0;
                    } else if (Objects.equals(name1, DEFAULT_REMOTE_NAME)) {
                        return -1;
                    } else if (Objects.equals(name2, DEFAULT_REMOTE_NAME)) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            );
            return gitConfigEntries.getEntries().stream()
                .filter(entry -> entry.getSection().equals(REMOTE) && entry.getName().equals(URL))
                .filter(entry -> entry.getSubsection() != null && entry.getValue() != null)
                .sorted(remoteNamesComparator)
                .map(GitConfigEntry::getValue)
                .findFirst()
                .orElse(null);
        })).finalizeValueOnRead();

        getGitRemoteHost().value(
            gitRemoteUrl.map(GitRemoteUrlUtils::getRemoteUrlHost)
        ).finalizeValueOnRead();

        getGitRemoteRepositoryFullName().value(
            gitRemoteUrl.map(GitRemoteUrlUtils::getRemoteUrlPath)
        ).finalizeValueOnRead();
    }


    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract BuildCancellationToken getCancellationToken();

}
