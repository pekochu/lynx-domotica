package com.pekochu.lynx.utilities;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BotPropertiesProviderImpl implements BotPropertiesProvider {

    @Value("${env.telegram.token}")
    private String telegramToken;

    @Value("${env.telegram.username}")
    private String telegramUsername;

    @Value("${env.google.client}")
    private String googleClientId;

    @Value("${env.google.secret}")
    private String googleClientSecret;

    // Getters
    public String getTelegramToken() {
        return telegramToken;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getGoogleClientSecret() {
        return googleClientSecret;
    }
}
