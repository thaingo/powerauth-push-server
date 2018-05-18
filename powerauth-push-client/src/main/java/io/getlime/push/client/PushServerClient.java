/*
 * Copyright 2016 Lime - HighTech Solutions s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getlime.push.client;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.getlime.core.rest.model.base.entity.Error;
import io.getlime.core.rest.model.base.request.ObjectRequest;
import io.getlime.core.rest.model.base.response.ErrorResponse;
import io.getlime.core.rest.model.base.response.ObjectResponse;
import io.getlime.core.rest.model.base.response.Response;
import io.getlime.push.model.base.PagedResponse;
import io.getlime.push.model.entity.ListOfUsers;
import io.getlime.push.model.entity.PushMessage;
import io.getlime.push.model.entity.PushMessageBody;
import io.getlime.push.model.entity.PushMessageSendResult;
import io.getlime.push.model.request.*;
import io.getlime.push.model.response.*;
import io.getlime.push.model.validator.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class for interacting with the push server RESTful API.
 *
 * @author Petr Dvorak, petr@lime-company.eu
 * @author Martin Tupy, martin.tupy.work@gmail.com
 */
public class PushServerClient {

    private com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper = null;
    private String serviceBaseUrl;

    /**
     * Default constructor.
     */
    public PushServerClient() {

        jacksonObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        Unirest.setObjectMapper(new ObjectMapper() {

            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object value) {
                try {
                    return jacksonObjectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * Constructor with a push server base URL.
     *
     * @param serviceBaseUrl URL pointing to the running push server instance, for example "http://localhost:8080/powerauth-push-server".
     */
    public PushServerClient(String serviceBaseUrl) {
        this();
        this.serviceBaseUrl = serviceBaseUrl;
    }

    /**
     * Set the service base URL.
     *
     * @param serviceBaseUrl Base URL.
     */
    public void setServiceBaseUrl(String serviceBaseUrl) {
        this.serviceBaseUrl = serviceBaseUrl;
    }

    /**
     * Returns service information
     *
     * @return True if service is running.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<ServiceStatusResponse> getServiceStatus() throws PushServerClientException {
        TypeReference<ObjectResponse<ServiceStatusResponse>> typeReference = new TypeReference<ObjectResponse<ServiceStatusResponse>>() {};

        log("Calling push server status service - start");
        final ObjectResponse<ServiceStatusResponse> result = getObjectImpl("/push/service/status", null, typeReference);
        log("Calling push server status service - finish");

        return result;
    }

    /**
     * Register anonymous device to the push server.
     *
     * @param appId PowerAuth 2.0 application app ID.
     * @param token Token received from the push service provider (APNs, FCM).
     * @param platform Mobile platform (iOS, Android).
     * @return True if device registration was successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean createDevice(Long appId, String token, MobilePlatform platform) throws PushServerClientException {
        return createDevice(appId, token, platform, null);
    }

    /**
     * Register device associated with activation ID to the push server.
     *
     * @param appId PowerAuth 2.0 application app ID.
     * @param token Token received from the push service provider (APNs, FCM).
     * @param platform Mobile platform (iOS, Android).
     * @param activationId PowerAuth 2.0 activation ID.
     * @return True if device registration was successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean createDevice(Long appId, String token, MobilePlatform platform, String activationId) throws PushServerClientException {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setAppId(appId);
        request.setToken(token);
        request.setPlatform(platform.value());
        request.setActivationId(activationId);

        // Validate request on the client side.
        String error = CreateDeviceRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        log("Calling create device service, appId: {}, token: {}, platform: {} - start", new Object[] { appId, token, platform.value() });
        Response response = postObjectImpl("/push/device/create", new ObjectRequest<>(request));
        log("Calling create device service, appId: {}, token: {}, platform: {} - finish", new Object[] { appId, token, platform.value() });

        return response.getStatus().equals(Response.Status.OK);
    }

    /**
     * Remove device from the push server
     *
     * @param appId PowerAuth 2.0 application app ID.
     * @param token Token received from the push service provider.
     * @return True if device removal was successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean deleteDevice(Long appId, String token) throws PushServerClientException {
        DeleteDeviceRequest request = new DeleteDeviceRequest();
        request.setAppId(appId);
        request.setToken(token);

        // Validate request on the client side.
        String error = DeleteDeviceRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        log("Calling push server delete device service, appId: {}, token: {} - start", new Object[] { appId, token });
        Response response = postObjectImpl("/push/device/delete", new ObjectRequest<>(request));
        log("Calling push server delete device service, appId: {}, token: {} - finish", new Object[] { appId, token });

        return response.getStatus().equals(Response.Status.OK);
    }

    /**
     * Update activation status for given device registration.
     *
     * @param activationId Identifier of activation
     * @return True if updating went successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean updateDeviceStatus(String activationId) throws PushServerClientException {
        UpdateDeviceStatusRequest request = new UpdateDeviceStatusRequest();
        request.setActivationId(activationId);

        // Validate request on the client side.
        String error = UpdateDeviceStatusRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        log("Calling push server update device status, activation ID: {} - start", new Object[] { activationId });
        // Note that there is just plain 'request' in the request, not 'new ObjectRequest<>(request)'.
        // This is due to the fact that standard PowerAuth Server callback format is used here.
        Response response = postObjectImpl("/push/device/status/update", request);
        log("Calling push server update device status, activation ID: {} - finish", new Object[] { activationId });

        return response.getStatus().equals(Response.Status.OK);
    }

    /**
     * Send a single push message to application with given ID.
     *
     * @param appId PowerAuth 2.0 application app ID.
     * @param pushMessage Push message to be sent.
     * @return SendMessageResponse in case everything went OK, ErrorResponse in case of an error.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<PushMessageSendResult> sendPushMessage(Long appId, PushMessage pushMessage) throws PushServerClientException {
        SendPushMessageRequest request = new SendPushMessageRequest();
        request.setAppId(appId);
        request.setMessage(pushMessage);

        // Validate request on the client side.
        String error = SendPushMessageRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        TypeReference<ObjectResponse<PushMessageSendResult>> typeReference = new TypeReference<ObjectResponse<PushMessageSendResult>>() {};

        log("Calling push server to send a push message, app ID: {}, user ID: {} - start", new Object[] { appId, pushMessage.getUserId() });
        final ObjectResponse<PushMessageSendResult> result = postObjectImpl("/push/message/send", new ObjectRequest<>(request), typeReference);
        log("Calling push server to send a push message, app ID: {}, user ID: {} - finish", new Object[] { appId, pushMessage.getUserId() });

        return result;
    }

    /**
     * Send a push message batch to application with given ID.
     *
     * @param appId PowerAuth 2.0 application app ID.
     * @param batch Push message batch to be sent.
     * @return SendMessageResponse in case everything went OK, ErrorResponse in case of an error.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<PushMessageSendResult> sendPushMessageBatch(Long appId, List<PushMessage> batch) throws PushServerClientException {
        SendPushMessageBatchRequest request = new SendPushMessageBatchRequest();
        request.setAppId(appId);
        request.setBatch(batch);

        // Validate request on the client side.
        String error = SendPushMessageBatchRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        TypeReference<ObjectResponse<PushMessageSendResult>> typeReference = new TypeReference<ObjectResponse<PushMessageSendResult>>() {};

        log("Calling push server to send a push message batch, app ID: {} - start", new Object[] { appId });
        final ObjectResponse<PushMessageSendResult> result = postObjectImpl("/push/message/batch/send", new ObjectRequest<>(request), typeReference);
        log("Calling push server to send a push message batch, app ID: {} - finish", new Object[] { appId });

        return result;
    }

    /**
     * Create a campaign.
     *
     * @param appId Application ID.
     * @param message Message which attributes are defined in PushMessageBody.
     * @return ID of new created campaign.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<CreateCampaignResponse> createCampaign(Long appId, PushMessageBody message) throws PushServerClientException {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setAppId(appId);
        request.setMessage(message);

        // Validate request on the client side.
        String error = CreateCampaignRequestValidator.validate(request);
        if (error != null) {
            throw new PushServerClientException(error);
        }

        TypeReference<ObjectResponse<CreateCampaignResponse>> typeReference = new TypeReference<ObjectResponse<CreateCampaignResponse>>() {};

        log("Calling push server to create a push campaign, app ID: {} - start", new Object[] { appId });
        final ObjectResponse<CreateCampaignResponse> result = postObjectImpl("/push/campaign/create", new ObjectRequest<>(request), typeReference);
        log("Calling push server to create a push campaign, app ID: {} - finish", new Object[] { appId });

        return result;
    }

    /**
     * Delete a campaign specified with campaignId.
     *
     * @param campaignId Campaign ID.
     * @return True if campaign is removed, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean deleteCampaign(Long campaignId) throws PushServerClientException {
        try {
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");

            TypeReference<ObjectResponse<DeleteCampaignResponse>> typeReference = new TypeReference<ObjectResponse<DeleteCampaignResponse>>() {};

            log("Calling push server to delete a push campaign, campaign ID: {} - start", new Object[] { campaignId });
            ObjectResponse<DeleteCampaignResponse> response = postObjectImpl("/push/campaign/" + campaignIdSanitized + "/delete", null, typeReference);
            log("Calling push server to delete a push campaign, campaign ID: {} - finish", new Object[] { campaignId });

            return response.getStatus().equals(Response.Status.OK);
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Get list of campaigns, dependent on all param
     *
     * @param all true to get whole list, false to get campaigns that are only sent
     * @return List of campaigns.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<ListOfCampaignsResponse> getListOfCampaigns(boolean all) throws PushServerClientException {
        Map<String, Object> params = new HashMap<>();
        params.put("all", all);

        TypeReference<ObjectResponse<ListOfCampaignsResponse>> typeReference = new TypeReference<ObjectResponse<ListOfCampaignsResponse>>() {};

        log("Calling push server to obtain a push campaign list - start");
        final ObjectResponse<ListOfCampaignsResponse> result = getObjectImpl("/push/campaign/list", params, typeReference);
        log("Calling push server to obtain a push campaign list - finish");

        return result;
    }

    /**
     * Get a campaign specified with campaignID.
     *
     * @param campaignId ID of campaign to get.
     * @return Details of campaign, defined in CampaignResponse
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public ObjectResponse<CampaignResponse> getCampaign(Long campaignId) throws PushServerClientException {
        try {
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");

            TypeReference<ObjectResponse<CampaignResponse>> typeReference = new TypeReference<ObjectResponse<CampaignResponse>>() {};

            log("Calling push server to obtain a push campaign detail, campaign ID: {} - start", new Object[] { campaignId });
            final ObjectResponse<CampaignResponse> result = getObjectImpl("/push/campaign/" + campaignIdSanitized + "/detail", null, typeReference);
            log("Calling push server to obtain a push campaign detail, campaign ID: {} - finish", new Object[] { campaignId });

            return result;
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Add a list of users to a specific campaign
     *
     * @param campaignId Identifier of campaign.
     * @param users List of users to add.
     * @return True if adding was successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean addUsersToCampaign(Long campaignId, List<String> users) throws PushServerClientException {
        try {
            ListOfUsers listOfUsers = new ListOfUsers(users);
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");

            log("Calling push server to add users to campaign, campaign ID: {} - start", new Object[] { campaignId });
            Response response = putObjectImpl("/push/campaign/" + campaignIdSanitized + "/user/add", new ObjectRequest<>(listOfUsers));
            log("Calling push server to add users to campaign, campaign ID: {} - finish", new Object[] { campaignId });

            return response.getStatus().equals(Response.Status.OK);
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Get a list of users in paged format from specific campaign
     *
     * @param campaignId Identifier of campaign.
     * @param page Page number.
     * @param size Size of elements per page.
     * @return Page of users specified with params.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public PagedResponse<ListOfUsersFromCampaignResponse> getListOfUsersFromCampaign(Long campaignId, int page, int size) throws PushServerClientException {
        try {
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);

            TypeReference<PagedResponse<ListOfUsersFromCampaignResponse>> typeReference = new TypeReference<PagedResponse<ListOfUsersFromCampaignResponse>>() {};

            log("Calling push server to get users from the campaign, campaign ID: {} - start", new Object[] { campaignId });
            final PagedResponse<ListOfUsersFromCampaignResponse> result = getObjectImpl("/push/campaign/" + campaignIdSanitized + "/user/list", params, typeReference);
            log("Calling push server to get users from the campaign, campaign ID: {} - finish", new Object[] { campaignId });

            return result;
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Delete a list of users from specific campaign.
     *
     * @param campaignId Identifier of campaign.
     * @param users List of users' Identifiers to delete.
     * @return True if deletion was successful, false otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean deleteUsersFromCampaign(Long campaignId, List<String> users) throws PushServerClientException {
        try {
            ListOfUsers listOfUsers = new ListOfUsers(users);
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");

            log("Calling push server to remove users from the campaign, campaign ID: {} - start", new Object[] { campaignId });
            Response response = postObjectImpl("/push/campaign/" + campaignIdSanitized + "/user/delete", new ObjectRequest<>(listOfUsers));
            log("Calling push server to remove users from the campaign, campaign ID: {} - finish", new Object[] { campaignId });

            return response.getStatus().equals(Response.Status.OK);
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Send a campaign on test user for trying its correctness.
     *
     * @param campaignId Identifier of campaign.
     * @param userId Identifier of test user.
     * @return True if sent, else otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean sendTestCampaign(Long campaignId, String userId) throws PushServerClientException {
        try {
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");
            TestCampaignRequest request = new TestCampaignRequest();
            request.setUserId(userId);

            // Validate request on the client side.
            String error = TestCampaignRequestValidator.validate(request);
            if (error != null) {
                throw new PushServerClientException(error);
            }

            log("Calling push server to send test campaign, campaign ID: {}, user ID: {} - start", new Object[] { campaignId, userId });
            Response response = postObjectImpl("/push/campaign/send/test/" + campaignIdSanitized, new ObjectRequest<>(request));
            log("Calling push server to send test campaign, campaign ID: {}, user ID: {} - finish", new Object[] { campaignId, userId });

            return response.getStatus().equals(Response.Status.OK);
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    /**
     * Send a specific campaign to users carrying this campaignID in PushCampaignUser model, but only once per device identified by token.
     *
     * @param campaignId Identifier of campaign.
     * @return True if sent, else otherwise.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    public boolean sendCampaign(Long campaignId) throws PushServerClientException {
        try {
            String campaignIdSanitized = URLEncoder.encode(String.valueOf(campaignId), "UTF-8");

            log("Calling push server to send a production campaign, campaign ID: {} - start", new Object[] { campaignId });
            Response response = postObjectImpl("/push/campaign/send/live/" + campaignIdSanitized, null);
            log("Calling push server to send a production campaign, campaign ID: {} - finish", new Object[] { campaignId });

            return response.getStatus().equals(Response.Status.OK);
        } catch (UnsupportedEncodingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", e.getMessage()));
        }
    }

    // Generic HTTP client methods

    /**
     * Prepare GET object response. Uses default {@link Response} type reference for response.
     *
     * @param url specific url of method.
     * @param params params to pass to url path, optional.
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     *
     */
    private <T> T getObjectImpl(String url, Map<String, Object> params) throws PushServerClientException {
        return getObjectImpl(url, params, new TypeReference<Response>() {});
    }

    /**
     * Prepare GET object response.
     *
     * @param url specific url of method.
     * @param params params to pass to url path, optional.
     * @param typeReference reference on type for parsing into JSON.
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     *
     */
    private <T> T getObjectImpl(String url, Map<String, Object> params, TypeReference typeReference) throws PushServerClientException {
        try {
            HttpResponse response = Unirest.get(serviceBaseUrl + url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .queryString(params)
                    .asString();
            return checkHttpStatus(typeReference, response);
        } catch (UnirestException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Network communication has failed."));
        } catch (JsonParseException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON parsing has failed."));
        } catch (JsonMappingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON mapping has failed."));
        } catch (IOException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Unknown IO error."));
        }

    }

    /**
     * Prepare POST object response. Uses default {@link Response} type reference for response.
     *
     * @param url specific url of method
     * @param request request body
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    private <T> T postObjectImpl(String url, Object request) throws PushServerClientException {
        return postObjectImpl(url, request, new TypeReference<Response>() {});
    }

    /**
     * Prepare POST object response.
     *
     * @param url specific url of method
     * @param request request body
     * @param typeReference reference on type for parsing into JSON
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    private <T> T postObjectImpl(String url, Object request, TypeReference typeReference) throws PushServerClientException {
        try {
            // Fetch post response from given URL and for provided request object
            HttpResponse response = Unirest.post(serviceBaseUrl + url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(request)
                    .asString();
            return checkHttpStatus(typeReference, response);
        } catch (UnirestException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Network communication has failed."));
        } catch (JsonParseException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON parsing has failed."));
        } catch (JsonMappingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON mapping has failed."));
        } catch (IOException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Unknown IO error."));
        }
    }

    /**
     * Prepare PUT object response. Uses default {@link Response} type reference for response.
     *
     * @param url specific url of method
     * @param request request body
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    private <T> T putObjectImpl(String url, Object request) throws PushServerClientException {
        return putObjectImpl(url, request, new TypeReference<Response>() {});
    }

    /**
     * Prepare PUT object response.
     *
     * @param url specific url of method
     * @param request request body
     * @param typeReference reference on type for parsing into JSON
     * @return Object obtained after processing the response JSON.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     */
    private <T> T putObjectImpl(String url, Object request, TypeReference typeReference) throws PushServerClientException {
        try {
            HttpResponse response = Unirest.put(serviceBaseUrl + url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(request)
                    .asString();
            return checkHttpStatus(typeReference, response);
        } catch (UnirestException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Network communication has failed."));
        } catch (JsonParseException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON parsing has failed."));
        } catch (JsonMappingException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "JSON mapping has failed."));
        } catch (IOException e) {
            throw new PushServerClientException(new Error("PUSH_SERVER_CLIENT_ERROR", "Unknown IO error."));
        }
    }

    /**
     * Checks response status
     *
     * @param typeReference reference on type of response body from which map into JSON
     * @param response prepared http response
     * @return In case response code is 200, returns instance of expected response type. Otherwise, it attempts to
     * reconstruct error response and returns the error response.
     * @throws PushServerClientException In case of network, response / JSON processing, or other IO error.
     * @throws IOException In case JSON processing fails.
     */
    private <T> T checkHttpStatus(TypeReference typeReference, HttpResponse response) throws IOException, PushServerClientException {
        if (response.getStatus() == 200) {
            return jacksonObjectMapper.readValue(response.getRawBody(), typeReference);
        } else {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            ErrorResponse errorResponse = mapper.readValue(response.getRawBody(), ErrorResponse.class);
            throw new PushServerClientException(response.getStatusText(), errorResponse.getResponseObject());
        }
    }

    // Logging helpers

    /**
     * Log a provided message.
     * @param msg Message.
     */
    private void log(String msg) {
        Logger.getLogger(PushServerClient.class.getName()).log(Level.INFO, msg);
    }

    /**
     * Log a provided message and substitute parameters.
     * @param msg Message.
     * @param params Message parameters.
     */
    private void log(String msg, Object[] params) {
        Logger.getLogger(PushServerClient.class.getName()).log(Level.INFO, msg, params);
    }


}
