package name.remal.gradle_plugins.github_repository_info;

import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.github_repository_info.GitHubApiTokenUtils.extractGitHubApiTokenFromGitConfigEntries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubApiTokenUtilsTest {

    private static final String GITHUB_SERVER_URL = "https://github.com";

    @Test
    void tokenPersistedByCheckoutActionIsExtracted() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:token-value"))
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void trailingSlashesOfSubsectionAndServerUrlAreIgnored() {
        var entries = List.of(
            extraHeaderEntry("https://github.com", basicExtraHeader("x-access-token:token-value"))
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, "https://github.com/"));
    }

    @Test
    void subsectionIsMatchedCaseInsensitively() {
        var entries = List.of(
            extraHeaderEntry("https://GitHub.COM/", basicExtraHeader("x-access-token:token-value"))
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", "authorization: basic " + base64("x-access-token:token-value"))
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void schemeIsMatchedCaseInsensitively() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", "AUTHORIZATION: Basic " + base64("x-access-token:token-value"))
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void colonsInsideTokenArePreserved() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:token:with:colons"))
        );

        assertEquals("token:with:colons", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void lastValueWins() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:first-token")),
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:second-token"))
        );

        assertEquals("second-token", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void unparseableValueDoesNotOverridePrecedingParsedValue() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:token-value")),
            extraHeaderEntry("https://github.com/", "AUTHORIZATION: bearer some-token")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL));
    }

    @Test
    void notMatchingSubsectionProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.example.com/", basicExtraHeader("x-access-token:token-value"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from a section of another server URL"
        );
    }

    @Test
    void notAuthorizationHeaderProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", "X-Custom-Header: basic " + base64("x-access-token:token-value"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from a header other than Authorization"
        );
    }

    @Test
    void notBasicSchemeProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", "AUTHORIZATION: bearer some-token")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from a scheme other than basic"
        );
    }

    @Test
    void invalidBase64ProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", "AUTHORIZATION: basic !!!not-base64!!!")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from invalid Base64 credentials"
        );
    }

    @Test
    void credentialsWithoutColonProduceNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("credentials-without-colon"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from credentials without a colon"
        );
    }

    @Test
    void emptyTokenProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "empty token extracted"
        );
    }

    @Test
    void subsectionLessHttpEntryIsIgnored() {
        var entries = List.of(
            new GitConfigEntry("http", null, "extraheader", basicExtraHeader("x-access-token:token-value"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from a subsection-less [http] section"
        );
    }

    @Test
    void valueLessEntryProducesNoToken() {
        var entries = List.of(
            new GitConfigEntry("http", "https://github.com/", "extraheader", null)
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from a value-less entry"
        );
    }

    @Test
    void entriesWithoutHttpSectionProduceNoToken() {
        var entries = List.of(
            new GitConfigEntry("remote", "origin", "url", "https://github.com/remal-gradle-plugins/github-repository-info")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, GITHUB_SERVER_URL),
            "token extracted from entries without [http] sections"
        );
    }

    @Test
    void nullOrEmptyServerUrlProducesNoToken() {
        var entries = List.of(
            extraHeaderEntry("https://github.com/", basicExtraHeader("x-access-token:token-value"))
        );

        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, null),
            "token extracted for a null server URL"
        );
        assertNull(
            extractGitHubApiTokenFromGitConfigEntries(entries, ""),
            "token extracted for an empty server URL"
        );
    }


    private static GitConfigEntry extraHeaderEntry(String subsection, String extraHeader) {
        return new GitConfigEntry("http", subsection, "extraheader", extraHeader);
    }

    private static String basicExtraHeader(String rawCredentials) {
        return "AUTHORIZATION: basic " + base64(rawCredentials);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

}
