package com.pekochu.app.bots;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.permission.Permissions;
import org.javacord.api.event.message.MessageCreateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class Discord {

    private final static Logger LOGGER = LoggerFactory.getLogger(Discord.class.getCanonicalName());
    private final static String DISCORD_ME = "227971372712853504";
    private final static String BOT_TOKEN = "NTk5NDc2NTcwNDEwODQ0MjA3.XZPrdQ.Nxs3e6hW4YIGbGkAFGul-TmSP_0";
    private DiscordApi discordApi;

    public Discord(){
    }

    public Discord(DiscordApi discordApi){
        this.discordApi = discordApi;
    }

    public static DiscordApi createInstance(){
        return new DiscordApiBuilder().setToken(BOT_TOKEN).login().join();
    }

    public void listen(){
        // Imprimir invitación
        LOGGER.info("Link de invitación del bot de Discord: {}",
                discordApi.createBotInvite(Permissions.fromBitmask(1047648)));
        // Escuchar
        discordApi.addMessageCreateListener(event -> {
            discordCommands(event);
        });
    }

    public void sendMessageToAdmin(String message){
        try{
            discordApi.getUserById(DISCORD_ME).get().sendMessage(message);
            LOGGER.info("A message has been sent to admin");
        }catch(InterruptedException | ExecutionException e){
            LOGGER.error(e.getMessage(), e);
        }

    }

    public void discordCommands(MessageCreateEvent mce){
        if(!mce.getMessageContent().startsWith("!peko")) return;
        LOGGER.info("Discord Bot | {}",
                mce.getMessageAuthor().getId() + ": "+mce.getMessageContent());

        if(!(mce.getMessageContent().length() > 4)) return;
        String command = mce.getMessageContent().substring(6);
        LOGGER.info("Command used: {}", command);

        if (command.equalsIgnoreCase("ping")) {
            mce.getChannel().sendMessage("pong!");
        }else if(command.equalsIgnoreCase("marco")){
            mce.getChannel().sendMessage("polo xd");
        }
    }
}
