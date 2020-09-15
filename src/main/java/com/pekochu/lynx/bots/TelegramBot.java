package com.pekochu.lynx.bots;

import com.pekochu.lynx.utilities.TelegramService;
import com.vdurmont.emoji.EmojiParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    // DI
    @Autowired
    private TelegramService telegramService;

    // Variables
    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());

    // Static Api Context Initializer
    static{
        ApiContextInitializer.init();
    }

    public TelegramBot(){
        // Empty constructor
    }

    @PostConstruct
    public void registerBot(){
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    // Commands
    public String commandDefault(Update update){
        StringBuilder defaultInfo = new StringBuilder();
        defaultInfo.append(String.format("Hola, %s. :relieved:", update.getMessage().getChat().getUserName()));

        return EmojiParser.parseToUnicode(defaultInfo.toString());
    }

    public String commandWhoami(){
        StringBuilder defaultInfo = new StringBuilder();
        defaultInfo.append(String.format("Respuesta del terminal:\n%s\n:hushed:", responseFromTerminal("whoami")));

        return EmojiParser.parseToUnicode(defaultInfo.toString());
    }

    // Handler
    @Override
    public void onUpdateReceived(final Update update) {
        LOGGER.info("Request from {}", update.getMessage().getChat().getUserName());
        String messageTextReceived = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        // Se crea un objeto mensaje
        SendMessage message = new SendMessage().setChatId(chatId);
        if(messageTextReceived.startsWith("/")){
            int endOfCommand = (messageTextReceived.indexOf(" ") == -1) ? messageTextReceived.length() :
                    messageTextReceived.indexOf(" ");
            String command = messageTextReceived.substring(1, endOfCommand);

            LOGGER.info("Comando: {}", command);
            switch(command){
                case "start":
                    message.setText(commandDefault(update));
                    break;
                case "whoami":
                    message.setText(commandWhoami());
                    break;
            }
        }else{
            message.setText(commandDefault(update));
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return telegramService.getUsername();
    }

    @Override
    public String getBotToken() {
        return telegramService.getToken();
    }

    @NotNull
    private String responseFromTerminal(String cmd){
        StringBuffer output = new StringBuffer();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null) {
                output.append(line + "\n");
            }
            // your output that you can use to build your json response:
            output.toString();
        } catch (IOException | InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return output.toString();
    }

}
