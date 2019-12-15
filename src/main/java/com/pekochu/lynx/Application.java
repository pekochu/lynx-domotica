package com.pekochu.lynx;

import com.pekochu.lynx.bots.Telegram;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

@SpringBootApplication
public class Application {

    private final static Logger LOGGER = LoggerFactory.getLogger(Application.class.getCanonicalName());

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
        ApiContextInitializer.init();
        LOGGER.info("Alessandra Bot Server initiated");

        // Telegram API
        try {
            final TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
            telegramBotsApi.registerBot(new Telegram());
        } catch (TelegramApiRequestException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


}
