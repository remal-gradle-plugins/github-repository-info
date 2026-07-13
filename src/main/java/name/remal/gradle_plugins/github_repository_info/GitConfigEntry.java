package name.remal.gradle_plugins.github_repository_info;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A single resolved git config entry.
 *
 * <p>Git normalizes the {@link #section} and the {@link #name} to lower case,
 * while the {@link #subsection} keeps its original character case.
 *
 * <p>A null {@link #value} means a value-less entry (a config name without {@code =}).
 */
@Value
@AllArgsConstructor
@NoArgsConstructor(force = true)
class GitConfigEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    String section;

    @Nullable
    String subsection;

    String name;

    @Nullable
    String value;

}
