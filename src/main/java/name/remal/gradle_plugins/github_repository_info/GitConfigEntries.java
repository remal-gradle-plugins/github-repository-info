package name.remal.gradle_plugins.github_repository_info;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * Git config entries relevant to the plugin, resolved by {@link GitConfigValueSource}.
 */
@Value
@AllArgsConstructor
@NoArgsConstructor(force = true)
class GitConfigEntries implements Serializable {

    private static final long serialVersionUID = 1L;

    List<GitConfigEntry> entries;

}
