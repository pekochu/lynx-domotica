package com.pekochu.lynx.bots;

import com.pekochu.lynx.utilities.Constants;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Telegram extends TelegramLongPollingBot {


    private final static Logger LOGGER = LoggerFactory.getLogger(Telegram.class.getCanonicalName());
    private final static String BOTNAME = "Alessandra";

    public Telegram(){
    }

    // Comandos
    public String commandDefault(Update update){
        StringBuilder defaultInfo = new StringBuilder();
        defaultInfo.append(String.format("Hola, %s.", update.getMessage().getChat().getUserName()));
        defaultInfo.append(String.format("\nMi nombre es %s y soy un bot.\nExplora los comandos para ", BOTNAME));
        defaultInfo.append("averiguar qué es lo que puedo hacer.");

        return defaultInfo.toString();
    }

    public String commandWhoami(){
        StringBuilder defaultInfo = new StringBuilder();
        defaultInfo.append(String.format("Respuesta del terminal: %s", responseFromTerminal("whoami")));

        return defaultInfo.toString();
    }


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
        return Constants.TELEGRAM_USERNAME;
    }

    @Override
    public String getBotToken() {
        return Constants.TELEGRAM_TOKEN;
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
