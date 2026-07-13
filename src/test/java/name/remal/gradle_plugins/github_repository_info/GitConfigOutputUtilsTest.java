package name.remal.gradle_plugins.github_repository_info;

import static name.remal.gradle_plugins.github_repository_info.GitConfigOutputUtils.parseGitConfigNullOutput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class GitConfigOutputUtilsTest {

    @Test
    void entriesAreSplitOnNulCharacters() {
        var entries = parseGitConfigNullOutput(
            "core.bare\nfalse\0remote.origin.url\nhttps://github.com/owner/repo\0"
        );

        assertEquals(
            List.of(
                new GitConfigEntry("core", null, "bare", "false"),
                new GitConfigEntry("remote", "origin", "url", "https://github.com/owner/repo")
            ),
            entries
        );
    }

    @Test
    void subsectionMayContainDots() {
        var entries = parseGitConfigNullOutput(
            "http.https://github.com/.extraheader\nAUTHORIZATION: basic dG9rZW4=\0"
        );

        assertEquals(
            List.of(new GitConfigEntry(
                "http",
                "https://github.com/",
                "extraheader",
                "AUTHORIZATION: basic dG9rZW4="
            )),
            entries
        );
    }

    @Test
    void twoSegmentKeyHasNoSubsection() {
        var entries = parseGitConfigNullOutput("http.extraheader\nheader-value\0");

        assertEquals(
            List.of(new GitConfigEntry("http", null, "extraheader", "header-value")),
            entries
        );
    }

    @Test
    void valueMayContainNewLines() {
        var entries = parseGitConfigNullOutput("alias.log-graph\nlog\n--graph\n--oneline\0");

        assertEquals(
            List.of(new GitConfigEntry("alias", null, "log-graph", "log\n--graph\n--oneline")),
            entries
        );
    }

    @Test
    void emptyOutputProducesNoEntries() {
        assertEquals(List.of(), parseGitConfigNullOutput(""));
    }

    @Test
    void duplicateKeysArePreservedInOrder() {
        var entries = parseGitConfigNullOutput(
            "http.https://github.com/.extraheader\nfirst\0http.https://github.com/.extraheader\nsecond\0"
        );

        assertEquals(
            List.of(
                new GitConfigEntry("http", "https://github.com/", "extraheader", "first"),
                new GitConfigEntry("http", "https://github.com/", "extraheader", "second")
            ),
            entries
        );
    }

    @Test
    void valueLessEntryHasNullValue() {
        var entries = parseGitConfigNullOutput("http.https://github.com/.extraheader\0");

        assertEquals(1, entries.size());
        var entry = entries.get(0);
        assertEquals("http", entry.getSection());
        assertEquals("https://github.com/", entry.getSubsection());
        assertEquals("extraheader", entry.getName());
        assertNull(entry.getValue(), "a value-less entry must have a null value");
    }

}
