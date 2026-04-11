package io.kestra.plugin.jira.issues;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.tenant.TenantService;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@KestraTest
class CreateCommentTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(CreateCommentTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            TenantService.MAIN_TENANT,
            "io.kestra.tests",
            "comment-jira",
            null,
            null
        );

        assertThat(execution.getTaskRunList(), hasSize(1));
    }
}