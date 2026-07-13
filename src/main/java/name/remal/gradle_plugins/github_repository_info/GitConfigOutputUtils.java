package name.remal.gradle_plugins.github_repository_info;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
class GitConfigOutputUtils {

    private static final Splitter NUL_SPLITTER = Splitter.on('\0');

    /**
     * Parses the output of {@code git config --list --null} into entries in their original order.
     *
     * <p>Every entry consists of a config key and an optional value delimited with the first {@code \n} character
     * and is terminated with a NUL character.
     * The value may contain further {@code \n} characters.
     */
    public static List<GitConfigEntry> parseGitConfigNullOutput(String output) {
        var entries = new ArrayList<GitConfigEntry>();
        for (var chunk : NUL_SPLITTER.split(output)) {
            if (chunk.isEmpty()) {
                continue;
            }

            final String key;
            final String value;
            var keyDelimPos = chunk.indexOf('\n');
            if (keyDelimPos >= 0) {
                key = chunk.substring(0, keyDelimPos);
                value = chunk.substring(keyDelimPos + 1);
            } else {
                key = chunk;
                value = null;
            }

            var sectionDelimPos = key.indexOf('.');
            if (sectionDelimPos < 0) {
                // a git config key always consists of at least a section and a name
                continue;
            }
            var nameDelimPos = key.lastIndexOf('.');

            var section = key.substring(0, sectionDelimPos);
            var subsection = sectionDelimPos < nameDelimPos
                ? key.substring(sectionDelimPos + 1, nameDelimPos)
                : null;
            var name = key.substring(nameDelimPos + 1);
            entries.add(new GitConfigEntry(section, subsection, name, value));
        }
        return entries;
    }

}
