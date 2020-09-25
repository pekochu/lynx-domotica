package com.pekochu.lynx.bots;

import com.pekochu.lynx.commands.telegram.BasicCommands;
import com.pekochu.lynx.commands.telegram.DriveCommands;
import com.pekochu.lynx.commands.telegram.GoogleOauthFlow;
import com.pekochu.lynx.utilities.BotPropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.util.AbilityExtension;
import org.telegram.abilitybots.api.util.AbilityUtils;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;
import java.util.function.Consumer;

@Service
public class TelegramBot extends AbilityBot {

    // Variables
    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());
    private final GoogleOauthFlow googleOauthFlow;
    private final DriveCommands driveCommands;

    static{
        ApiContextInitializer.init();
    }

    @Autowired
    public TelegramBot(BotPropertiesProvider botPropertiesProvider){
        super(botPropertiesProvider.getTelegramToken(), botPropertiesProvider.getTelegramUsername());
        // Google OAuth2.0 commands flow
        googleOauthFlow = new GoogleOauthFlow(sender, db,
                botPropertiesProvider.getGoogleClientId(),
                botPropertiesProvider.getGoogleClientSecret());

        // Google Drive API commands flow
        driveCommands = new DriveCommands(sender, db,
                botPropertiesProvider.getGoogleClientId(),
                botPropertiesProvider.getGoogleClientSecret());
    }

    @PostConstruct
    public void registerBot(){
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            LOGGER.info("Registering Telegram bot...");
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public int creatorId() {
        return 218168915;
    }

    // Basic commands
    public AbilityExtension defaultCommand(){
        return new BasicCommands(silent);
    }

    // Google Drive
    public Ability googleAuthCommand(){
        return Ability
                .builder()
                .name("googleauth")
                .info("Autentíficarse con la API de Google.")
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .action(ctx -> {
                    //
                    SendChatAction sendChatAction = new SendChatAction();
                    sendChatAction.setAction(ActionType.TYPING);
                    sendChatAction.setChatId(ctx.chatId());
                    silent.execute(sendChatAction);
                    googleOauthFlow.replyToOauth(ctx);
                })
                .build();
    }

    // Google Drive
    public Ability driveCommand(){
        return Ability
                .builder()
                .name("drive")
                .info("Interactuar con la API de Google Drive con el bot.")
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .action(ctx -> {
                    //
                    SendChatAction sendChatAction = new SendChatAction();
                    sendChatAction.setAction(ActionType.TYPING);
                    sendChatAction.setChatId(ctx.chatId());
                    silent.execute(sendChatAction);
                    driveCommands.replyToDrive(ctx);
                })
                // .reply(update -> driveCommands.replyToButtons(update), Flag.CALLBACK_QUERY, Flag.REPLY)
                .build();
    }

}
