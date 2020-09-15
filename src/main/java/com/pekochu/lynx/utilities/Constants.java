package com.pekochu.lynx.utilities;

import org.springframework.beans.factory.annotation.Autowired;

public class Constants {

    @Autowired
    private TelegramServiceImpl telegramService;

    public Constants(){

    }

    public String getTelegramToken(){
        return telegramService.getToken();
    }

    public String getTelegramUsername(){
        return telegramService.getUsername();
    }

}
