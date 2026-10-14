package com.electricwealth.api.error;

import java.io.IOException;

import com.electricwealth.api.web.TraceIds;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Writes an error envelope directly to the response, for filters that run before Spring MVC. */
@Component
public class ErrorResponses {

    private final JsonMapper jsonMapper;

    public ErrorResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ErrorEnvelope.of(code, message, TraceIds.get(request)));
    }
}
