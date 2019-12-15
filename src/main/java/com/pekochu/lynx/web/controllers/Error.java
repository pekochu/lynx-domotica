package com.pekochu.lynx.web.controllers;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

@RestController
public class Error implements ErrorController {

    private final static Logger LOGGER = LoggerFactory.getLogger(Error.class.getCanonicalName());

    @RequestMapping(value = "/error",
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public @ResponseBody
    ResponseEntity<String> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorException = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object errorExceptionType = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
        String errorServletName = (String)request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
        String errorMessage =  (String)request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        HttpStatus finalStatus;
        JSONObject error = new JSONObject();

        if(statusCode != null){
            finalStatus = HttpStatus.valueOf((Integer) statusCode);
            error.put("header", finalStatus);
            error.put("content", errorMessage);
        }else{
            finalStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            error.put("header", finalStatus);
            error.put("content", "Error desconocido. ".concat(errorMessage));
        }

        LOGGER.warn("Error handler has been called");
        return ResponseEntity.status(finalStatus.value()).contentType(MediaType.APPLICATION_JSON).body(error.toString());
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }
}
