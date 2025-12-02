package fr.epita.assistants.ping.domain.executor;

import java.io.File;

public interface FeatureExecutor {
    /**
     * unique tool name, e.g. "git"
     */
    String name();

    /**
     * execute sub-command on the project root directory
     */
    void execute(File projectRoot, Object request);
}
