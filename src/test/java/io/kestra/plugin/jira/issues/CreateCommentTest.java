package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateCommentTest extends AbstractJiraTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void addsCommentAndExposesOutputs() throws Exception {
        // projectKey/summary/description/labels/issueTypeId are deprecated and ignored on this task;
        // set here to prove old flow configs still deserialize, run, and produce an unchanged request.
        CreateComment task = CreateComment.builder()
            .id(IdUtils.create())
            .type(CreateComment.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Ignored summary"))
            .description("Ignored description")
            .labels(Property.ofValue(List.of("ignored-label")))
            .issueTypeId(Property.ofValue("99999"))
            .issueIdOrKey("TEST-1")
            .body("This ticket is not moving")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        CreateComment.Output output = task.run(runContext);

        assertThat(output.getId(), is("20000"));
        assertThat(output.getIssueIdOrKey(), is("TEST-1"));
        assertThat(output.getSelf(), is("http://mock-jira/rest/api/2/issue/TEST-1/comment/20000"));
        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/TEST-1?focusedCommentId=20000"));

        assertThat(mockController.requests, hasSize(1));
        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.method(), is("POST"));
        assertThat(request.path(), is("/rest/api/2/issue/TEST-1/comment"));
        assertThat(request.body(), containsString("This ticket is not moving"));
        assertThat(request.body(), not(containsString("Ignored")));
        assertThat(request.body(), not(containsString("PROJ")));
        assertThat(request.body(), not(containsString("99999")));
    }

    @Test
    void supportsBaseUrlWithContextPathAndTrailingSlash() throws Exception {
        CreateComment task = CreateComment.builder()
            .id(IdUtils.create())
            .type(CreateComment.class.getName())
            .baseUrl(getApiBaseUrl() + "/jira/")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .body("Comment via context path")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        CreateComment.Output output = task.run(runContext);

        assertThat(output.getUrl(), is(getApiBaseUrl() + "/jira/browse/TEST-1?focusedCommentId=20000"));
        assertThat(mockController.requests.getFirst().path(), is("/jira/rest/api/2/issue/TEST-1/comment"));
    }

    @Test
    void failsWithClearMessageWhenCommentIdMissingFrom2xxResponse() {
        CreateComment task = CreateComment.builder()
            .id(IdUtils.create())
            .type(CreateComment.class.getName())
            .baseUrl(getApiBaseUrl() + "/missing-key")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .body("Comment body")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 201 without a comment id"));
    }

    @Test
    void encodesIssueIdOrKeyPathSegment() throws Exception {
        CreateComment task = CreateComment.builder()
            .id(IdUtils.create())
            .type(CreateComment.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("OPS 123")
            .body("Comment body")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        CreateComment.Output output = task.run(runContext);

        assertThat(output.getIssueIdOrKey(), is("OPS 123"));
        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/OPS%20123?focusedCommentId=20000"));
        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/OPS%20123/comment"));
    }
}
