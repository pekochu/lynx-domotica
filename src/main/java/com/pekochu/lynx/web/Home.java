package com.pekochu.lynx.web;


import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Home {

    @RequestMapping(value = "/",
            produces = {MediaType.APPLICATION_JSON_VALUE},
            method = RequestMethod.GET)
    public @ResponseBody
    ResponseEntity<String> index() {
        JSONObject greet = new JSONObject();
        greet.put("header", HttpStatus.OK);
        greet.put("content", "Hello world");

        return ResponseEntity.status(HttpStatus.OK.value()).contentType(MediaType.APPLICATION_JSON).body(greet.toString());
    }

}
