package name.remal.gradle_plugins.github_repository_info;

import static java.lang.String.join;
import static java.lang.System.identityHashCode;
import static name.remal.gradle_plugins.toolkit.CiUtils.getCiSystem;
import static name.remal.gradle_plugins.toolkit.GradleManagedObjectsUtils.copyManagedProperties;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.doNotInline;
import static name.remal.gradle_plugins.toolkit.git.GitUtils.findGitRepositoryRootFor;

import java.util.Optional;
import name.remal.gradle_plugins.toolkit.AbstractSettingsAwarePlugin;
import name.remal.gradle_plugins.toolkit.CiSystem;
import org.gradle.api.Project;
import org.gradle.api.services.BuildService;

public abstract class GitHubRepositoryInfoPlugin extends AbstractSettingsAwarePlugin {

    public static final String GITHUB_REPOSITORY_INFO_EXTENSION_NAME = doNotInline("githubRepositoryInfo");

    @Override
    @SuppressWarnings("unchecked")
    protected void applyToProject(Project project) {
        var dataFetcher = project.getGradle().getSharedServices().registerIfAbsent(
            getBuildServiceName(GitHubDataFetcher.class),
            GitHubDataFetcher.class,
            __ -> { }
        );
        dataFetcher.get().registerProject(project);

        var extension = project.getExtensions().create(
            GITHUB_REPOSITORY_INFO_EXTENSION_NAME,
            GitHubRepositoryInfoExtension.class
        );
        extension.getRepositoryRootDir().fileProvider(project.provider(() -> {
            var buildDir = getCiSystem()
                .flatMap(CiSystem::getBuildDir)
                .orElse(null);
            if (buildDir != null) {
                return buildDir;
            }
            return findGitRepositoryRootFor(project.getRootDir());
        })).finalizeValueOnRead();
        extension.getGitHubDataFetcher().value(dataFetcher).finalizeValueOnRead();

        project.getTasks().withType(AbstractRetrieveGitHubRepositoryInfoTask.class).configureEach(task -> {
            copyManagedProperties(GitHubRepositoryInfoSettings.class, extension, task);

            task.getGitHubDataFetcher().set(dataFetcher);
            task.usesService(dataFetcher);
        });
    }

    private static String getBuildServiceName(Class<? extends BuildService<?>> serviceClass) {
        return join(
            "|",
            serviceClass.getName(),
            String.valueOf(identityHashCode(serviceClass)),
            Optional.ofNullable(serviceClass.getClassLoader())
                .map(System::identityHashCode)
                .map(Object::toString)
                .orElse("")
        );
    }

}
