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
import org.telegram.abilitybots.api.util.AbilityExtension;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;

@Service
public class TelegramBot extends AbilityBot {

    // Variables
    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());

    private final BasicCommands basicCommands;
    private final GoogleOauthFlow googleOauthFlow;
    private final DriveCommands driveCommands;

    @Autowired
    public TelegramBot(BotPropertiesProvider botPropertiesProvider){
        super(botPropertiesProvider.getTelegramToken(), botPropertiesProvider.getTelegramUsername());

        //Basic commands
        basicCommands = new BasicCommands(silent);

        // Google OAuth2.0 commands flow
        googleOauthFlow = new GoogleOauthFlow(db, sender, silent,
                botPropertiesProvider.getGoogleClientId(),
                botPropertiesProvider.getGoogleClientSecret());

        // Google Drive API commands flow
        driveCommands = new DriveCommands(db, sender, silent,
                botPropertiesProvider.getGoogleClientId(),
                botPropertiesProvider.getGoogleClientSecret());
    }

    @PostConstruct
    public void registerBot(){
        try {
            LOGGER.info("Registering Telegram bot...");
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public long creatorId() {
        return 218168915;
    }

    // Basic commands
    public AbilityExtension defaultCommand(){
        return basicCommands;
    }

    // Google Auth commands
    public AbilityExtension googleOauthCommand(){
        return googleOauthFlow;
    }

    // Drive commands
    public AbilityExtension driveCommand() { return driveCommands; }
}
