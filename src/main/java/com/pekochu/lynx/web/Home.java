package com.pekochu.lynx.web;


import com.pekochu.lynx.utilities.TelegramServiceImpl;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
