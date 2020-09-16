package com.pekochu.lynx.bots;

import com.pekochu.lynx.commands.telegram.BasicCommands;
import com.pekochu.lynx.commands.telegram.DriveOauthFlow;
import com.pekochu.lynx.utilities.GoogleDriveService;
import com.pekochu.lynx.utilities.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Locality;
import org.telegram.abilitybots.api.objects.Privacy;
import org.telegram.abilitybots.api.util.AbilityExtension;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;

import static org.telegram.abilitybots.api.objects.Privacy.PUBLIC;

@Component
public class TelegramBot extends AbilityBot {

    // Variables
    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());
    private final DriveOauthFlow driveOauthFlow;

    static{
        ApiContextInitializer.init();
    }

    @Autowired
    public TelegramBot(TelegramService telegramService, GoogleDriveService googleDriveService){
        super(telegramService.getToken(), telegramService.getUsername());
        driveOauthFlow = new DriveOauthFlow(sender, db,
                googleDriveService.getCliendId(), googleDriveService.getClientSecret());
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
    public Ability driveCommand(){
        return Ability
                .builder()
                .name("drive")
                .info("")
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .action(ctx -> driveOauthFlow.replyToDrive(ctx))
                .build();
    }

}
