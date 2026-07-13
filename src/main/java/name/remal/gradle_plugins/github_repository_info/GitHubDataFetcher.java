package name.remal.gradle_plugins.github_repository_info;

import static com.google.common.hash.Hashing.sha512;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.google.common.net.HttpHeaders.ACCEPT_LANGUAGE;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PUBLIC;
import static name.remal.gradle_plugins.github_repository_info.GitHubRestApiHttpClientUtils.sendGitHubRestApiHttpRequest;
import static name.remal.gradle_plugins.github_repository_info.HttpClientUtils.getHttpResponseCharset;
import static name.remal.gradle_plugins.github_repository_info.HttpClientUtils.getPlainHttpResponseBody;
import static name.remal.gradle_plugins.github_repository_info.JsonUtils.GSON;
import static name.remal.gradle_plugins.toolkit.ConfigurationCacheSafeSystem.getConfigurationCacheSafeBooleanEnv;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.PathUtils.normalizePath;

import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.github_repository_info.GitHubDataFetcher.GitHubDataFetcherParams;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.initialization.BuildCancellationToken;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class GitHubDataFetcher implements BuildService<GitHubDataFetcherParams> {

    protected interface GitHubDataFetcherParams extends BuildServiceParameters {
        MapProperty<String, DirectoryProperty> getProjectPathToBuildDirectory();
    }

    public synchronized void registerProject(Project project) {
        getParameters().getProjectPathToBuildDirectory().put(
            project.getPath(),
            project.getLayout().getBuildDirectory()
        );
    }

    @Nullable
    private synchronized Path getCacheDirectory() {
        var projectPathToBuildDirectory = new TreeMap<>(getParameters().getProjectPathToBuildDirectory().get());
        if (projectPathToBuildDirectory.isEmpty()) {
            return null;
        }

        var projectPath = projectPathToBuildDirectory.firstKey();
        var buildDirectory = requireNonNull(projectPathToBuildDirectory.get(projectPath));

        return normalizePath(
            buildDirectory
                .dir("tmp/.cache/name.remal.github-repository-info")
                .get()
                .getAsFile()
                .toPath()
        );
    }

    @Nullable
    public Path getCacheFile(String apiUrl, String relativeUrl) {
        var fullUrl = createFullUrl(apiUrl, relativeUrl);
        return getCacheFile(fullUrl);
    }

    @Nullable
    private Path getCacheFile(String fullUrl) {
        var cacheDir = getCacheDirectory();
        if (cacheDir == null) {
            return null;
        }

        var hash = sha512().hashBytes(fullUrl.getBytes(UTF_8)).toString();
        return cacheDir.resolve(hash + ".json");
    }


    public <T> T fetch(
        String apiUrl,
        String relativeUrl,
        @Nullable String apiToken,
        Class<T> type,
        @Nullable BuildCancellationToken cancellationToken
    ) {
        return fetch(
            apiUrl,
            relativeUrl,
            apiToken,
            TypeToken.get(type),
            cancellationToken
        );
    }

    public <T> T fetch(
        String apiUrl,
        String relativeUrl,
        @Nullable String apiToken,
        TypeToken<T> type,
        @Nullable BuildCancellationToken cancellationToken
    ) {
        if (apiToken != null && apiToken.isEmpty()) {
            apiToken = null;
        }
        if (apiToken == null && isInTestOnCI()) {
            throw new IllegalStateException("GitHub REST API requests must be authenticated when running tests on CI");
        }

        var fullUrl = createFullUrl(apiUrl, relativeUrl);

        var content = getContentFromInMemoryCacheOrFetch(fullUrl, apiToken, cancellationToken);
        var result = GSON.fromJson(content, type);
        return requireNonNull(result);
    }

    private static String createFullUrl(String apiUrl, String relativeUrl) {
        while (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        while (relativeUrl.startsWith("/")) {
            relativeUrl = relativeUrl.substring(1);
        }

        return apiUrl + '/' + relativeUrl;
    }

    private static boolean isInTestOnCI() {
        if (!isInTest()) {
            return false;
        }

        return getConfigurationCacheSafeBooleanEnv("CI")
            || getConfigurationCacheSafeBooleanEnv("GITHUB_ACTIONS");
    }


    private static final ConcurrentMap<String, String> IN_MEMORY_CACHE = new ConcurrentHashMap<>();

    private String getContentFromInMemoryCacheOrFetch(
        String fullUrl,
        @Nullable String apiToken,
        @Nullable BuildCancellationToken cancellationToken
    ) {
        return IN_MEMORY_CACHE.computeIfAbsent(fullUrl, __ ->
            getContentFromFileCacheOrFetch(fullUrl, apiToken, cancellationToken)
        );
    }


    private String getContentFromFileCacheOrFetch(
        String fullUrl,
        @Nullable String apiToken,
        @Nullable BuildCancellationToken cancellationToken
    ) {
        var cacheFile = getCacheFile(fullUrl);
        if (cacheFile == null) {
            return fetchContent(fullUrl, apiToken, cancellationToken);
        }

        var fileCache = new FileCache(cacheFile);
        var bytes = fileCache.getOrCreateContent(() -> {
            var content = fetchContent(fullUrl, apiToken, cancellationToken);
            return content.getBytes(UTF_8);
        });
        var content = new String(bytes, UTF_8);
        return content;
    }


    private String fetchContent(
        String fullUrl,
        @Nullable String apiToken,
        @Nullable BuildCancellationToken cancellationToken
    ) {
        var requestUri = URI.create(fullUrl);
        var requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(requestUri)
            .setHeader(ACCEPT, "application/json")
            .setHeader(ACCEPT_LANGUAGE, "en-US")
            .setHeader(ACCEPT_ENCODING, "gzip");
        if (apiToken != null) {
            requestBuilder.header(AUTHORIZATION, "token " + apiToken);
        }
        var request = requestBuilder.build();

        var response = sendGitHubRestApiHttpRequest(request, cancellationToken);
        var bytes = getPlainHttpResponseBody(response);
        var charset = getHttpResponseCharset(response);
        return new String(bytes, charset);
    }

}
