package com.pekochu.lynx.utilities;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramServiceImpl implements TelegramService{

    @Value("${env.telegram.token}")
    private String token;

    @Value("${env.telegram.username}")
    private String username;

    public String getToken() {
        return this.token;
    }

    public String getUsername() {
        return this.username;
    }
}
