package name.remal.gradle_plugins.github_repository_info;

class GitConfigReadException extends RuntimeException {

    GitConfigReadException(String message) {
        super(message);
    }

    GitConfigReadException(String message, Throwable cause) {
        super(message, cause);
    }

}
