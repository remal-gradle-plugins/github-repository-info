package name.remal.gradle_plugins.github_repository_info;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.nullIfEmpty;
import static name.remal.gradle_plugins.toolkit.StringUtils.trimRightWith;

import java.util.Base64;
import java.util.List;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor(access = PRIVATE)
class GitHubApiTokenUtils {

    private static final String HTTP = "http";
    private static final String EXTRA_HEADER = "extraheader";

    /**
     * Extracts a GitHub API token from resolved git config entries with credentials persisted by
     * <a href="https://github.com/actions/checkout">{@code actions/checkout}</a>.
     *
     * <p>Only {@code extraheader} values of {@code [http "<url>"]} sections matching {@code githubServerUrl}
     * are considered (trailing slashes and character case are ignored).
     * A value must be an {@code Authorization} header with the {@code basic} scheme,
     * and the token is the part of the Base64-decoded credentials after the first colon.
     * Subsection-less {@code [http]} sections are deliberately ignored.
     *
     * <p>If multiple values match, the last successfully parsed one wins.
     */
    @Nullable
    public static String extractGitHubApiTokenFromGitConfigEntries(
        List<GitConfigEntry> gitConfigEntries,
        @Nullable String githubServerUrl
    ) {
        if (githubServerUrl == null || githubServerUrl.isEmpty()) {
            return null;
        }

        var expectedSubsection = trimRightWith(githubServerUrl, '/');

        String token = null;
        for (var entry : gitConfigEntries) {
            if (!entry.getSection().equals(HTTP)
                || !entry.getName().equals(EXTRA_HEADER)
            ) {
                continue;
            }

            var subsection = entry.getSubsection();
            if (subsection == null || !trimRightWith(subsection, '/').equalsIgnoreCase(expectedSubsection)) {
                continue;
            }

            var extraHeader = entry.getValue();
            if (extraHeader == null) {
                continue;
            }

            // Git semantics: the last value wins. The entries come in git's own resolution order.
            var parsedToken = parseGitHubApiTokenFromExtraHeader(extraHeader);
            if (parsedToken != null) {
                token = parsedToken;
            }
        }
        return token;
    }

    @Nullable
    private static String parseGitHubApiTokenFromExtraHeader(String extraHeader) {
        var headerNameDelimPos = extraHeader.indexOf(':');
        if (headerNameDelimPos < 0) {
            return null;
        }

        var headerName = extraHeader.substring(0, headerNameDelimPos).trim();
        if (!headerName.equalsIgnoreCase(AUTHORIZATION)) {
            return null;
        }

        var headerValue = extraHeader.substring(headerNameDelimPos + 1).trim();
        var schemeDelimPos = headerValue.indexOf(' ');
        if (schemeDelimPos < 0) {
            return null;
        }

        var scheme = headerValue.substring(0, schemeDelimPos);
        if (!scheme.equalsIgnoreCase("basic")) {
            return null;
        }

        final byte[] credentialsBytes;
        try {
            credentialsBytes = Base64.getDecoder().decode(headerValue.substring(schemeDelimPos + 1).trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        var credentials = new String(credentialsBytes, UTF_8);
        var credentialsDelimPos = credentials.indexOf(':');
        if (credentialsDelimPos < 0) {
            return null;
        }

        return nullIfEmpty(credentials.substring(credentialsDelimPos + 1));
    }

}
