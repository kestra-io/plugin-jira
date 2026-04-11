package io.kestra.plugin.jira.issues;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@KestraTest
@WireMockTest(httpPort = 5083)
class CreateCommentTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(any(urlPathEqualTo("/rest/api/2/issue/TEST-123/comment"))
            .willReturn(okJson("{\"id\":\"12345\",\"body\":\"Test comment\"}")));

        RunContext runContext = runContextFactory.of(Map.of());

        CreateComment task = CreateComment.builder()
            .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .username(Property.ofValue("test@test.com"))
            .password(Property.ofValue("api-token"))
            .projectKey("TEST")
            .issueIdOrKey("TEST-123")
            .body("Test comment body")
            .build();

        task.run(runContext);

        verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue/TEST-123/comment"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Authorization", matching("Basic .+")));
    }
}