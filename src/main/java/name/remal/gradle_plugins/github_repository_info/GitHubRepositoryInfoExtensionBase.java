package name.remal.gradle_plugins.github_repository_info;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static java.util.Comparator.comparing;
import static name.remal.gradle_plugins.toolkit.ConfigurationCacheSafeSystem.getConfigurationCacheSafeOptionalEnv;
import static name.remal.gradle_plugins.toolkit.StringUtils.substringBefore;
import static org.eclipse.jgit.lib.Constants.CONFIG;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;
import static org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;
import static org.eclipse.jgit.transport.RemoteConfig.getAllRemoteConfigs;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import javax.inject.Inject;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.initialization.BuildCancellationToken;

abstract class GitHubRepositoryInfoExtensionBase implements GitHubRepositoryInfoSettings {

    @Internal
    protected abstract Property<GitHubDataFetcher> getGitHubDataFetcher();


    @Internal
    public abstract DirectoryProperty getRepositoryRootDir();

    @Internal
    public abstract Property<String> getGithubServerUrl();

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
        );
        getGithubServerUrl().convention(
            getProviders().environmentVariable("GITHUB_SERVER_URL")
                .orElse(getGitRemoteHost().map(host -> "https://" + host))
                .orElse(getProviders().gradleProperty("name.remal.github-repository-info.server-url"))
                .orElse("https://github.com")
        );
    }


    @Internal
    protected abstract Property<String> getGitRemoteHost();

    @Internal
    protected abstract Property<String> getGitRemoteRepositoryFullName();

    {
        var gitRemoteUri = getObjects().property(URIish.class);
        gitRemoteUri.value(getProviders().provider(() -> {
            var gitConfigPath = getRepositoryRootDir()
                .map(Directory::getAsFile)
                .map(File::toPath)
                .map(path -> path.resolve(DOT_GIT).resolve(CONFIG))
                .getOrNull();
            if (gitConfigPath == null) {
                return null;
            }

            final Config config;
            try {
                var bytes = readAllBytes(gitConfigPath);
                var text = new String(bytes, UTF_8);
                config = new Config();
                config.fromText(text);
            } catch (NoSuchFileException ignored) {
                return null;
            }

            var remotes = getAllRemoteConfigs(config);
            var remotesComparator = comparing(RemoteConfig::getName, (name1, name2) -> {
                if (name1.equals(name2)) {
                    return 0;
                } else if (name1.equals(DEFAULT_REMOTE_NAME)) {
                    return -1;
                } else if (name2.equals(DEFAULT_REMOTE_NAME)) {
                    return 1;
                } else {
                    return 0;
                }
            });
            return remotes.stream()
                .sorted(remotesComparator)
                .map(RemoteConfig::getURIs)
                .flatMap(Collection::stream)
                .findFirst()
                .orElse(null);
        })).finalizeValueOnRead();

        getGitRemoteHost().value(
            gitRemoteUri
                .map(URIish::getHost)
                .map(ObjectUtils::nullIfEmpty)
        ).finalizeValueOnRead();

        getGitRemoteRepositoryFullName().value(
            gitRemoteUri
                .map(URIish::getRawPath)
                .map(path -> path.startsWith("/") ? path.substring(1) : path)
                .map(path -> substringBefore(path, DOT_GIT_EXT))
                .map(ObjectUtils::nullIfEmpty)
        ).finalizeValueOnRead();
    }


    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract BuildCancellationToken getCancellationToken();

}
