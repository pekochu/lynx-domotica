package com.pekochu.lynx.commands.telegram;

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.pekochu.lynx.bots.TelegramBot;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

public class GoogleOauthFlow {

    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());

    private final MessageSender sender;
    private final Map<Long, Map<String, String>> driveCredentials;
    private Map<String, String> storeCredentials;
    private final Map<Long, String> driveStates;

    private final String GOOGLE_CLIENT;
    private final String GOOGLE_SECRET;

    public GoogleOauthFlow(MessageSender sender, DBContext db, String client, String secret) {
        this.sender = sender;
        driveCredentials = db.getMap("DRIVE_CREDENTIALS");
        driveStates = db.getMap("DRIVE_AUTH_STATES");
        this.GOOGLE_CLIENT = client;
        this.GOOGLE_SECRET = secret;
    }

    public void replyToOauth(MessageContext ctx) {
        StringBuilder text = new StringBuilder();
        String currentState = driveStates.get(ctx.chatId());
        String[] args = ctx.arguments();

        SendChatAction sendChatAction = new SendChatAction();
        sendChatAction.setChatId(ctx.chatId());
        sendChatAction.setAction(ActionType.TYPING);

        try {
            sender.execute(sendChatAction);

            if (args.length < 1) {
                sender.execute(new SendMessage()
                        .setText(EmojiParser.parseToUnicode("Argumentos disponibles para este comando:\n\n" +
                                "<code>/googleauth url</code>\n<i>Te genera un URL para otorgar permisos " +
                                "de Google Drive al bot.</i>\n\n" +
                                "<code>/googleauth code &lt;tu codigo&gt;</code>\n<i>Regitramos tu código " +
                                "para obtener un token e interactuar con tu cuenta de Google Drive</i>\n\n" +
                                "Usa <code>/commands</code> para más información. :relaxed:"))
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));

                driveStates.put(ctx.chatId(), "TRYING");
            }else if(args[0].equals("url")){
                if(currentState == null){
                    requestUrl(ctx);
                    driveStates.put(ctx.chatId(), "URL_GENERATED");
                }else{
                    if(currentState.equals("USER_SAVED")){
                        sender.execute(new SendMessage()
                                .setText("Parece que ya te has autenticado. ¿Quieres una nueva URL de autenticación? " +
                                        "Vuelve a enviar este comando (<code>/googleauth url</code>) " +
                                        EmojiParser.parseToUnicode("si quieres otra URL. :smile:"))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));
                        driveStates.put(ctx.chatId(), "MAYBE_TRYING");
                    }else if(currentState.equals("MAYBE_TRYING")){
                        requestUrl(ctx);
                        driveStates.put(ctx.chatId(), "URL_GENERATED");
                    }
                }
            }else if(args[0].equals("code")){
                if(currentState == null){
                    sender.execute(new SendMessage().setText(EmojiParser.parseToUnicode("Parece que no has " +
                            "solicitado un URL de autenticación. Por favor, solicitalo usando el comando " +
                            "<code>/googleauth url</code>. :thinking:"))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                }else{
                    if(currentState.equals("URL_GENERATED") ||
                            currentState.equals("MAYBE_TRYING")){
                        storeCode(ctx, args[1]);
                    }else{
                        sender.execute(new SendMessage().setText(EmojiParser.parseToUnicode("Parece que no has " +
                                "solicitado un URL de autenticación. Por favor, solicitalo usando el comando " +
                                "<code>/googleauth url</code>. :thinking:"))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));
                    }
                }
            }
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.error(e.getMessage());

            try{
                sender.execute(new SendMessage()
                        .setText(EmojiParser.parseToUnicode("Ha ocurrido un error inesperado. :disappointed:" +
                                "\n:thinking: Detalles:\n\n<pre>"+e.getMessage()+"</pre>" +
                                "\n\nNo te preocupes, el admin ya ha sido notificado del error. :hugs:"))
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));
            }catch (TelegramApiException ex){
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    private void requestUrl(MessageContext ctx) throws TelegramApiException{
        String url = new GoogleAuthorizationCodeRequestUrl(
                GOOGLE_CLIENT, "https://pekochu.com/domotics/google/code",
                Collections.singleton(DriveScopes.DRIVE))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        List<List<InlineKeyboardButton>> Buttons = new ArrayList<>();
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(new InlineKeyboardButton().setText("URL de autorización").setUrl(url));
        Buttons.add(rowInline);
        inlineKeyboard.setKeyboard(Buttons);

        sender.execute(new SendMessage()
                .setText("Da click en el enlace para obtener tu código de autorización." +
                        " Después vuelve aquí y escribe <code>/gogoleauth code &lt;codigo&gt;</code> para" +
                        " poder utilizarlo aquí.")
                .enableHtml(true)
                .setReplyMarkup(inlineKeyboard)
                .setChatId(ctx.chatId()));
    }

    private void storeCode(MessageContext ctx, String code) throws TelegramApiException,
            IOException, GeneralSecurityException{
        if (driveCredentials.get(ctx.chatId()) == null) {
            storeCredentials = new HashMap<>();
        } else {
            storeCredentials = driveCredentials.get(ctx.chatId());
        }

        GoogleTokenResponse response =
                new GoogleAuthorizationCodeTokenRequest(GoogleNetHttpTransport.newTrustedTransport(),
                        JacksonFactory.getDefaultInstance(), GOOGLE_CLIENT, GOOGLE_SECRET, code,
                        "https://pekochu.com/domotics/google/code").execute();

        storeCredentials.put("AUTH_CODE", code);
        storeCredentials.put("ACCESS_TOKEN", response.getAccessToken());
        storeCredentials.put("REFRESH_TOKEN", response.getRefreshToken());
        storeCredentials.put("TOKEN_TYPE", response.getTokenType());
        storeCredentials.put("EXPIRY", response.getExpiresInSeconds().toString());
        driveCredentials.put(ctx.chatId(), storeCredentials);

        sender.execute(new SendMessage()
                .setText(EmojiParser.parseToUnicode("Código guardado con éxito. :wink:"))
                .enableHtml(true)
                .setChatId(ctx.chatId()));
    }
}
