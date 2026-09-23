package io.kestra.plugin.jira.issues;

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

class UpdateFieldsTest extends AbstractJiraTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void updatesFieldsWithPutAndExposesOutputs() throws Exception {
        UpdateFields task = UpdateFields.builder()
            .id(IdUtils.create())
            .type(UpdateFields.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .issueIdOrKey("TEST-1")
            .fields(Property.ofValue(Map.of("description", "Updated description")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        UpdateFields.Output output = task.run(runContext);

        assertThat(output.getIssueIdOrKey(), is("TEST-1"));
        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/TEST-1"));

        assertThat(mockController.requests, hasSize(1));
        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        // Jira REST v2 rejects POST on the edit-issue route; only PUT is accepted.
        assertThat(request.method(), is("PUT"));
        assertThat(request.path(), is("/rest/api/2/issue/TEST-1"));
        assertThat(request.body(), containsString("Updated description"));
    }

    @Test
    void supportsBaseUrlWithContextPath() throws Exception {
        UpdateFields task = UpdateFields.builder()
            .id(IdUtils.create())
            .type(UpdateFields.class.getName())
            .baseUrl(getApiBaseUrl() + "/jira")
            .accessToken(Property.ofValue("oauth-token"))
            .projectKey("PROJ")
            .issueIdOrKey("TEST-1")
            .fields(Property.ofValue(Map.of("summary", "Updated summary")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        UpdateFields.Output output = task.run(runContext);

        assertThat(output.getUrl(), is(getApiBaseUrl() + "/jira/browse/TEST-1"));
        assertThat(mockController.requests.getFirst().path(), is("/jira/rest/api/2/issue/TEST-1"));
        assertThat(mockController.requests.getFirst().method(), is("PUT"));
    }

    @Test
    void encodesIssueIdOrKeyPathSegment() throws Exception {
        UpdateFields task = UpdateFields.builder()
            .id(IdUtils.create())
            .type(UpdateFields.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .issueIdOrKey("OPS 123")
            .fields(Property.ofValue(Map.of("summary", "Updated summary")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        UpdateFields.Output output = task.run(runContext);

        assertThat(output.getIssueIdOrKey(), is("OPS 123"));
        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/OPS%20123"));
        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/OPS%20123"));
    }
}
