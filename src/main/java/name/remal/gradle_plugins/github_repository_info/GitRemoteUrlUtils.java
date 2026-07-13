package name.remal.gradle_plugins.github_repository_info;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.nullIfEmpty;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor(access = PRIVATE)
class GitRemoteUrlUtils {

    private static final String DOT_GIT_EXT = ".git";

    /**
     * Extracts the host from a git remote URL.
     *
     * <p>Standard scheme URLs (like {@code https://}, {@code ssh://}, or {@code git://})
     * and the scp-like syntax ({@code [user@]host:path}) are supported.
     * Local paths produce no host.
     */
    @Nullable
    public static String getRemoteUrlHost(String remoteUrl) {
        if (hasScheme(remoteUrl)) {
            try {
                return nullIfEmpty(new URI(remoteUrl).getHost());
            } catch (URISyntaxException ignored) {
                return null;
            }
        }

        var scpDelimPos = getScpLikeSyntaxDelimPos(remoteUrl);
        if (scpDelimPos >= 0) {
            var authority = remoteUrl.substring(0, scpDelimPos);
            var userDelimPos = authority.lastIndexOf('@');
            return nullIfEmpty(authority.substring(userDelimPos + 1));
        }

        return null;
    }

    /**
     * Extracts the repository path from a git remote URL.
     *
     * <p>The leading slash and the trailing {@code .git} extension are stripped.
     * An empty path produces null.
     */
    @Nullable
    public static String getRemoteUrlPath(String remoteUrl) {
        var rawPath = getRemoteUrlRawPath(remoteUrl);
        if (rawPath == null) {
            return null;
        }

        var path = rawPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith(DOT_GIT_EXT)) {
            path = path.substring(0, path.length() - DOT_GIT_EXT.length());
        }
        return nullIfEmpty(path);
    }


    @Nullable
    private static String getRemoteUrlRawPath(String remoteUrl) {
        if (hasScheme(remoteUrl)) {
            try {
                return new URI(remoteUrl).getPath();
            } catch (URISyntaxException ignored) {
                return null;
            }
        }

        var scpDelimPos = getScpLikeSyntaxDelimPos(remoteUrl);
        if (scpDelimPos >= 0) {
            return remoteUrl.substring(scpDelimPos + 1);
        }

        return remoteUrl;
    }

    private static boolean hasScheme(String remoteUrl) {
        return remoteUrl.contains("://");
    }

    /**
     * Returns the position of the colon delimiting the scp-like syntax ({@code [user@]host:path}),
     * or a negative value for other remote URL formats.
     */
    private static int getScpLikeSyntaxDelimPos(String remoteUrl) {
        var colonPos = remoteUrl.indexOf(':');
        if (colonPos <= 0) {
            return -1;
        }

        var slashPos = remoteUrl.indexOf('/');
        if (slashPos >= 0 && slashPos < colonPos) {
            return -1;
        }

        var userDelimPos = remoteUrl.lastIndexOf('@', colonPos - 1);
        if (userDelimPos < 0 && colonPos == 1) {
            // a single character before the colon is a Windows drive letter, not a host
            return -1;
        }

        return colonPos;
    }

}
