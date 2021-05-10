package com.pekochu.lynx.commands.telegram;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.DriveScopes;
import com.pekochu.lynx.bots.TelegramBot;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Locality;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.abilitybots.api.objects.Privacy;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.sender.SilentSender;
import org.telegram.abilitybots.api.util.AbilityExtension;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

public class GoogleOauthFlow implements AbilityExtension {

    private final static Logger LOGGER = LoggerFactory.getLogger(GoogleOauthFlow.class.getCanonicalName());

    private final DBContext db;
    private MessageSender sender;
    private SilentSender silent;

    private final Map<Long, Map<String, String>> driveCredentials;
    private Map<String, String> storeCredentials;
    private final Map<Long, String> driveStates;

    private final String GOOGLE_CLIENT;
    private final String GOOGLE_SECRET;

    public GoogleOauthFlow(DBContext db, MessageSender sender, SilentSender silent, String client, String secret) {
        this.db = db;
        this.sender = sender;
        this.silent = silent;
        // Maps
        driveCredentials = db.getMap("DRIVE_CREDENTIALS");
        driveStates = db.getMap("DRIVE_AUTH_STATES");
        // Google secrets
        this.GOOGLE_CLIENT = client;
        this.GOOGLE_SECRET = secret;
    }

    public Ability googleOauthAbility(){
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
                    sendChatAction.setChatId(String.valueOf(ctx.chatId()));
                    silent.execute(sendChatAction);
                    replyToOauth(ctx);
                }).build();
    }

    public void replyToOauth(MessageContext ctx) {
        StringBuilder text = new StringBuilder();
        String currentState = driveStates.get(ctx.chatId());
        String[] args = ctx.arguments();
        SendMessage snd = null;
        LOGGER.info("ChatId: {}\tEstado: {}", ctx.chatId(), currentState);

        try {
            if (args.length < 1) {
                snd = new SendMessage();
                snd.enableHtml(true);
                snd.setText(EmojiParser.parseToUnicode("Argumentos disponibles para este comando:\n\n" +
                        "<code>/googleauth url</code>\n<i>Te genera un URL para otorgar permisos " +
                        "de Google Drive al bot.</i>\n\n" +
                        "<code>/googleauth code &lt;tu codigo&gt;</code>\n<i>Regitramos tu código " +
                        "para obtener un token e interactuar con tu cuenta de Google Drive</i>\n\n" +
                        "Usa <code>/commands</code> para más información. :relaxed:"));
                snd.setChatId(String.valueOf(ctx.chatId()));

                sender.execute(snd);
                driveStates.put(ctx.chatId(), "TRYING");
            }else if(args[0].equals("url")){
                if(currentState == null){
                    requestUrl(ctx);
                    driveStates.put(ctx.chatId(), "URL_GENERATED");
                }else{
                    if(currentState.equals("USER_SAVED")){
                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText("Parece que ya te has autenticado. ¿Quieres una nueva URL de autenticación? " +
                                "Vuelve a enviar este comando (<code>/googleauth url</code>) " +
                                EmojiParser.parseToUnicode("si quieres otra URL. :smile:"));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
                        driveStates.put(ctx.chatId(), "RETRYING");
                    }else if(currentState.equals("TRYING") || currentState.equals("RETRYING")){
                        requestUrl(ctx);
                        driveStates.put(ctx.chatId(), "URL_GENERATED");
                    }
                }
            }else if(args[0].equals("code")){
                if(currentState == null){
                    snd = new SendMessage();
                    snd.enableHtml(true);
                    snd.setText(EmojiParser.parseToUnicode("Parece que no has " +
                            "solicitado un URL de autenticación. Por favor, solicitalo usando el comando " +
                            "<code>/googleauth url</code>. :thinking:"));
                    snd.setChatId(String.valueOf(ctx.chatId()));

                    sender.execute(snd);
                }else{
                    if(currentState.equals("URL_GENERATED")){
                        storeCode(ctx, args[1]);
                    }else{
                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode("Parece que no has " +
                                "solicitado un URL de autenticación. Por favor, solicitalo usando el comando " +
                                "<code>/googleauth url</code>. :thinking:"));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
                    }
                }
            }
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.error(e.getMessage());

            try{
                snd = new SendMessage();
                snd.enableHtml(true);
                snd.setText(EmojiParser.parseToUnicode("Ha ocurrido un error inesperado. :disappointed:" +
                        "\n:thinking: Detalles:\n\n<pre>"+e.getMessage()+"</pre>" +
                        "\n\nNo te preocupes, el admin ya ha sido notificado del error. :hugs:"));
                snd.setChatId(String.valueOf(ctx.chatId()));

                sender.execute(snd);
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

        InlineKeyboardButton inlineUrlButton = new InlineKeyboardButton();
        inlineUrlButton.setText("URL de autorización");
        inlineUrlButton.setUrl(url);
        rowInline.add(inlineUrlButton);

        Buttons.add(rowInline);
        inlineKeyboard.setKeyboard(Buttons);

        SendMessage snd = new SendMessage();
        snd.enableHtml(true);
        snd.setText("Da click en el enlace para obtener tu código de autorización." +
                " Después vuelve aquí y escribe <code>/gogoleauth code &lt;codigo&gt;</code> para" +
                " poder utilizarlo aquí.");
        snd.setChatId(String.valueOf(ctx.chatId()));
        snd.setReplyMarkup(inlineKeyboard);

        LOGGER.info("URL generado: {}", url);

        sender.execute(snd);
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
                        new GsonFactory(), GOOGLE_CLIENT, GOOGLE_SECRET, code,
                        "https://pekochu.com/domotics/google/code").execute();

        storeCredentials.put("AUTH_CODE", code);
        storeCredentials.put("ACCESS_TOKEN", response.getAccessToken());
        storeCredentials.put("REFRESH_TOKEN", response.getRefreshToken());
        storeCredentials.put("TOKEN_TYPE", response.getTokenType());
        storeCredentials.put("EXPIRY", response.getExpiresInSeconds().toString());
        driveCredentials.put(ctx.chatId(), storeCredentials);

        SendMessage snd = new SendMessage();
        snd.enableHtml(true);
        snd.setText(EmojiParser.parseToUnicode("Código guardado con éxito. :wink:"));
        snd.setChatId(String.valueOf(ctx.chatId()));

        driveStates.put(ctx.chatId(), "USER_SAVED");
        sender.execute(snd);
    }
}
