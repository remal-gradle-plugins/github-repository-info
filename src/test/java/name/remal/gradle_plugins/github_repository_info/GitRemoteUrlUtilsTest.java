package name.remal.gradle_plugins.github_repository_info;

import static name.remal.gradle_plugins.github_repository_info.GitRemoteUrlUtils.getRemoteUrlHost;
import static name.remal.gradle_plugins.github_repository_info.GitRemoteUrlUtils.getRemoteUrlPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GitRemoteUrlUtilsTest {

    @Test
    void httpsUrl() {
        assertEquals("github.com", getRemoteUrlHost("https://github.com/owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("https://github.com/owner/repo.git"));
    }

    @Test
    void httpsUrlWithoutDotGitExtension() {
        assertEquals("github.com", getRemoteUrlHost("https://github.com/owner/repo"));
        assertEquals("owner/repo", getRemoteUrlPath("https://github.com/owner/repo"));
    }

    @Test
    void scpLikeSyntax() {
        assertEquals("github.com", getRemoteUrlHost("git@github.com:owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("git@github.com:owner/repo.git"));
    }

    @Test
    void scpLikeSyntaxWithoutUser() {
        assertEquals("github.com", getRemoteUrlHost("github.com:owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("github.com:owner/repo.git"));
    }

    @Test
    void sshUrl() {
        assertEquals("github.com", getRemoteUrlHost("ssh://git@github.com/owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("ssh://git@github.com/owner/repo.git"));
    }

    @Test
    void gitUrl() {
        assertEquals("github.com", getRemoteUrlHost("git://github.com/owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("git://github.com/owner/repo.git"));
    }

    @Test
    void urlWithPort() {
        assertEquals("github.example.com", getRemoteUrlHost("https://github.example.com:8443/owner/repo.git"));
        assertEquals("owner/repo", getRemoteUrlPath("https://github.example.com:8443/owner/repo.git"));
    }

    @Test
    void urlWithoutPath() {
        assertEquals("github.com", getRemoteUrlHost("https://github.com"));
        assertNull(
            getRemoteUrlPath("https://github.com"),
            "a path extracted from a URL without a path"
        );
    }

    @Test
    void dotGitExtensionIsStrippedOnlyAtTheEnd() {
        assertEquals(
            "owner/owner.github.io",
            getRemoteUrlPath("https://github.com/owner/owner.github.io.git")
        );
        assertEquals(
            "owner/owner.github.io",
            getRemoteUrlPath("https://github.com/owner/owner.github.io")
        );
    }

    @Test
    void emptyPathProducesNull() {
        assertNull(
            getRemoteUrlPath("https://github.com/"),
            "a path extracted from a URL with an empty path"
        );
        assertNull(
            getRemoteUrlPath("https://github.com/.git"),
            "a path extracted from a URL whose path is only the .git extension"
        );
    }

    @Test
    void localPathsProduceNoHost() {
        assertNull(
            getRemoteUrlHost("/srv/git/repo.git"),
            "a host extracted from a local absolute path"
        );
        assertNull(
            getRemoteUrlHost("C:/projects/repo.git"),
            "a host extracted from a Windows drive letter path"
        );
        assertNull(
            getRemoteUrlHost("C:\\projects\\repo.git"),
            "a host extracted from a Windows drive letter path with backslashes"
        );
    }

}
