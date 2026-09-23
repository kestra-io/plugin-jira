package io.kestra.plugin.jira.issues;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.tenant.TenantService;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Runs the Jira tasks as real flows against {@link JiraMockController}, covering YAML
 * deserialization, flow validation, plugin registration by {@code type}, and output rendering
 * across tasks — coverage the builder-based unit tests in this package don't provide.
 */
class JiraFlowsTest extends AbstractJiraTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void loadFlows() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            repositoryLoader.load(Objects.requireNonNull(JiraFlowsTest.class.getClassLoader().getResource("flows")));
            runner.run();
        }
    }

    @Test
    void createsIssueThenCommentsOnIt() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            TenantService.MAIN_TENANT,
            "io.kestra.tests",
            "jira",
            null,
            (f, e) -> Map.of("url", getApiBaseUrl())
        );

        assertThat(execution.getState().isSuccess(), is(true));

        var createOutputs = execution.findTaskRunsByTaskId("create_issue").getFirst().getOutputs();
        assertThat(createOutputs.get("id"), is("10000"));
        assertThat(createOutputs.get("key"), is("TEST-1"));
        assertThat((String) createOutputs.get("url"), endsWith("/browse/TEST-1"));

        var commentOutputs = execution.findTaskRunsByTaskId("create_comment").getFirst().getOutputs();
        assertThat(commentOutputs.get("id"), is("20000"));
        assertThat(commentOutputs.get("issueIdOrKey"), is("TEST-1"));

        JiraMockController.CapturedRequest commentRequest = mockController.requests.stream()
            .filter(request -> request.path().endsWith("/comment"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No comment request captured"));
        assertThat(commentRequest.method(), is("POST"));
        assertThat(commentRequest.path(), is("/rest/api/2/issue/TEST-1/comment"));
    }

    @Test
    void commentsOnAnExistingIssue() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            TenantService.MAIN_TENANT,
            "io.kestra.tests",
            "comment-jira",
            null,
            (f, e) -> Map.of("url", getApiBaseUrl())
        );

        assertThat(execution.getState().isSuccess(), is(true));

        var commentOutputs = execution.findTaskRunsByTaskId("create_comment").getFirst().getOutputs();
        assertThat(commentOutputs.get("id"), is("20000"));
        assertThat(commentOutputs.get("issueIdOrKey"), is("issuekey"));
        assertThat((String) commentOutputs.get("url"), startsWith(getApiBaseUrl() + "/browse/issuekey"));

        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/issuekey/comment"));
    }

    @Test
    void updatesFieldsOnAnExistingIssue() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            TenantService.MAIN_TENANT,
            "io.kestra.tests",
            "update-fields-jira",
            null,
            (f, e) -> Map.of("url", getApiBaseUrl())
        );

        assertThat(execution.getState().isSuccess(), is(true));

        var updateOutputs = execution.findTaskRunsByTaskId("update_fields").getFirst().getOutputs();
        assertThat(updateOutputs.get("issueIdOrKey"), is("issuekey"));
        assertThat((String) updateOutputs.get("url"), is(getApiBaseUrl() + "/browse/issuekey"));

        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.method(), is("PUT"));
        assertThat(request.path(), is("/rest/api/2/issue/issuekey"));
    }
}
