/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.rest.action.apikey;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.rest.AbstractRestChannel;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.FakeRestRequest;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.security.action.ApiKey;
import org.elasticsearch.xpack.core.security.action.GetApiKeyRequest;
import org.elasticsearch.xpack.core.security.action.GetApiKeyResponse;
import org.elasticsearch.xpack.security.rest.action.apikey.RestGetApiKeyAction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestGetApiKeyActionTests extends ESTestCase {
    private final XPackLicenseState mockLicenseState = mock(XPackLicenseState.class);
    private final RestController mockRestController = mock(RestController.class);
    private Settings settings = null;
    private ThreadPool threadPool = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        settings = Settings.builder().put("path.home", createTempDir().toString()).put("node.name", "test-" + getTestName())
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString()).build();
        threadPool = new ThreadPool(settings);
        when(mockLicenseState.isSecurityAvailable()).thenReturn(true);
        when(mockLicenseState.isApiKeyServiceAllowed()).thenReturn(true);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        terminate(threadPool);
    }

    public void testGetApiKey() throws Exception {
        final Map<String, String> param1 = mapBuilder().put("realm_name", "realm-1").put("username","user-x").map();
        final Map<String, String> param2 = mapBuilder().put("realm_name", "realm-1").map();
        final Map<String, String> param3 = mapBuilder().put("username", "user-x").map();
        final Map<String, String> param4 = mapBuilder().put("id", "api-key-id-1").map();
        final Map<String, String> param5 = mapBuilder().put("name", "api-key-name-1").map();
        final Map<String, String> params = randomFrom(param1, param2, param3, param4, param5);
        final boolean replyEmptyResponse = rarely();
        final FakeRestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withParams(params).build();

        final SetOnce<RestResponse> responseSetOnce = new SetOnce<>();
        final RestChannel restChannel = new AbstractRestChannel(restRequest, randomBoolean()) {
            @Override
            public void sendResponse(RestResponse restResponse) {
                responseSetOnce.set(restResponse);
            }
        };
        final Instant creation = Instant.now();
        final Instant expiration = randomFrom(Arrays.asList(null, Instant.now().plus(10, ChronoUnit.DAYS)));
        final GetApiKeyResponse getApiKeyResponseExpected = new GetApiKeyResponse(
                Collections.singletonList(new ApiKey("api-key-name-1", "api-key-id-1", creation, expiration, false, "user-x", "realm-1")));

        try (NodeClient client = new NodeClient(Settings.EMPTY, threadPool) {
            @SuppressWarnings("unchecked")
            public <    Request extends ActionRequest,
                        Response extends ActionResponse,
                        RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>>
            void doExecute(Action<Request, Response, RequestBuilder> action,
                           Request request,
                           ActionListener<Response> listener) {
                GetApiKeyRequest getApiKeyRequest = (GetApiKeyRequest) request;
                ActionRequestValidationException validationException = getApiKeyRequest.validate();
                if (validationException != null) {
                    listener.onFailure(validationException);
                    return;
                }
                if (getApiKeyRequest.getApiKeyName() != null && getApiKeyRequest.getApiKeyName().equals("api-key-name-1")
                        || getApiKeyRequest.getApiKeyId() != null && getApiKeyRequest.getApiKeyId().equals("api-key-id-1")
                        || getApiKeyRequest.getRealmName() != null && getApiKeyRequest.getRealmName().equals("realm-1")
                        || getApiKeyRequest.getUserName() != null && getApiKeyRequest.getUserName().equals("user-x")) {
                    if (replyEmptyResponse) {
                        listener.onResponse((Response) GetApiKeyResponse.emptyResponse());
                    } else {
                        listener.onResponse((Response) getApiKeyResponseExpected);
                    }
                } else {
                    listener.onFailure(new ElasticsearchSecurityException("encountered an error while creating API key"));
                }
            }
        }) {
            final RestGetApiKeyAction restGetApiKeyAction = new RestGetApiKeyAction(Settings.EMPTY, mockRestController, mockLicenseState);

            restGetApiKeyAction.handleRequest(restRequest, restChannel, client);

            final RestResponse restResponse = responseSetOnce.get();
            assertNotNull(restResponse);
            assertThat(restResponse.status(),
                    (replyEmptyResponse && params.get("id") != null) ? is(RestStatus.NOT_FOUND) : is(RestStatus.OK));
            final GetApiKeyResponse actual = GetApiKeyResponse
                    .fromXContent(createParser(XContentType.JSON.xContent(), restResponse.content()));
            if (replyEmptyResponse) {
                assertThat(actual.getApiKeyInfos().length, is(0));
            } else {
                assertThat(actual.getApiKeyInfos(),
                        arrayContaining(new ApiKey("api-key-name-1", "api-key-id-1", creation, expiration, false, "user-x", "realm-1")));
            }
        }

    }

    private static MapBuilder<String, String> mapBuilder() {
        return MapBuilder.newMapBuilder();
    }
}
