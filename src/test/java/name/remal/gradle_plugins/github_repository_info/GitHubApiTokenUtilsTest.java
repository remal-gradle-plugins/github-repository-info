package name.remal.gradle_plugins.github_repository_info;

import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.github_repository_info.GitHubApiTokenUtils.extractGitHubApiTokenFromGitConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class GitHubApiTokenUtilsTest {

    private static final String GITHUB_SERVER_URL = "https://github.com";

    @Test
    void tokenPersistedByCheckoutActionIsExtracted() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void trailingSlashesOfSubsectionAndServerUrlAreIgnored() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, "https://github.com/"));
    }

    @Test
    void subsectionIsMatchedCaseInsensitively() throws Throwable {
        var config = gitConfig(
            "[http \"https://GitHub.COM/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = authorization: basic " + base64("x-access-token:token-value")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void schemeIsMatchedCaseInsensitively() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = AUTHORIZATION: Basic " + base64("x-access-token:token-value")
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void colonsInsideTokenArePreserved() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token:with:colons")
        );

        assertEquals("token:with:colons", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void lastValueWins() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:first-token"),
            "\textraheader = " + basicExtraHeader("x-access-token:second-token")
        );

        assertEquals("second-token", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void unparseableValueDoesNotOverridePrecedingParsedValue() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value"),
            "\textraheader = AUTHORIZATION: bearer some-token"
        );

        assertEquals("token-value", extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL));
    }

    @Test
    void notMatchingSubsectionProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.example.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from a section of another server URL"
        );
    }

    @Test
    void notAuthorizationHeaderProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = X-Custom-Header: basic " + base64("x-access-token:token-value")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from a header other than Authorization"
        );
    }

    @Test
    void notBasicSchemeProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = AUTHORIZATION: bearer some-token"
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from a scheme other than basic"
        );
    }

    @Test
    void invalidBase64ProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = AUTHORIZATION: basic !!!not-base64!!!"
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from invalid Base64 credentials"
        );
    }

    @Test
    void credentialsWithoutColonProduceNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("credentials-without-colon")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from credentials without a colon"
        );
    }

    @Test
    void emptyTokenProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "empty token extracted"
        );
    }

    @Test
    void subsectionLessHttpSectionIsIgnored() throws Throwable {
        var config = gitConfig(
            "[http]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from a subsection-less [http] section"
        );
    }

    @Test
    void configWithoutHttpSectionProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[remote \"origin\"]",
            "\turl = https://github.com/remal-gradle-plugins/github-repository-info"
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, GITHUB_SERVER_URL),
            "token extracted from a config without [http] sections"
        );
    }

    @Test
    void nullOrEmptyServerUrlProducesNoToken() throws Throwable {
        var config = gitConfig(
            "[http \"https://github.com/\"]",
            "\textraheader = " + basicExtraHeader("x-access-token:token-value")
        );

        assertNull(
            extractGitHubApiTokenFromGitConfig(config, null),
            "token extracted for a null server URL"
        );
        assertNull(
            extractGitHubApiTokenFromGitConfig(config, ""),
            "token extracted for an empty server URL"
        );
    }


    private static String gitConfig(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static String basicExtraHeader(String rawCredentials) {
        return "AUTHORIZATION: basic " + base64(rawCredentials);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

}
