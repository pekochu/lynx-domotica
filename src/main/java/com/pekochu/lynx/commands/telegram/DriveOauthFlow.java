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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

public class DriveOauthFlow {

    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());

    private final MessageSender sender;
    private final Map<Long, Map<String, String>> driveCredentials;
    private Map<String, String> storeCredentials;
    private final Map<Long, String> driveStates;

    private final String GOOGLE_CLIENT;
    private final String GOOGLE_SECRET;

    public DriveOauthFlow(MessageSender sender, DBContext db, String client, String secret) {
        this.sender = sender;
        driveCredentials = db.getMap("DRIVE_CREDENTIALS");
        driveStates = db.getMap("DRIVE_STATES");
        this.GOOGLE_CLIENT = client;
        this.GOOGLE_SECRET = secret;
    }

    public void replyToDrive(MessageContext ctx) {
        StringBuilder text = new StringBuilder();
        String[] args = ctx.arguments();

        try {
            if (driveStates.get(ctx.chatId()) == null) {
                sender.execute(new SendMessage()
                        .setText("No has iniciado sesión")
                        .setChatId(ctx.chatId()));

                driveStates.put(ctx.chatId(), "TRYING");
            }else if(driveStates.get(ctx.chatId()).equals("TRYING")){
                String url = new GoogleAuthorizationCodeRequestUrl(
                        GOOGLE_CLIENT, "https://angelbrv.com/google/redirect",
                        Collections.singleton(DriveScopes.DRIVE))
                        .setAccessType("offline")
                        .setApprovalPrompt("force")
                        .build();

                if (driveCredentials.get(ctx.chatId()) == null) {
                    storeCredentials = new HashMap<>();
                } else {
                    storeCredentials = driveCredentials.get(ctx.chatId());
                }
                storeCredentials.put("AUTH_URL", url);
                driveCredentials.put(ctx.chatId(), storeCredentials);

                List<List<InlineKeyboardButton>> Buttons = new ArrayList<>();
                InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
                List<InlineKeyboardButton> rowInline = new ArrayList<>();
                rowInline.add(new InlineKeyboardButton().setText("URL de autorización").setUrl(url));
                Buttons.add(rowInline);
                inlineKeyboard.setKeyboard(Buttons);

                sender.execute(new SendMessage()
                        .setText("Da click en el enlace para obtener tu código de autorización." +
                                " Después vuelve aquí y escribe <code>/drive code [codigo]</code> para" +
                                " poder utilizarlo aquí. También puedes usar <code>/drive retry</code>" +
                                " para obtene un nuevo URL de autorización.")
                        .enableHtml(true)
                        .setReplyMarkup(inlineKeyboard)
                        .setChatId(ctx.chatId()));

                driveStates.put(ctx.chatId(), "URL_GENERATED");
            }else if(driveStates.get(ctx.chatId()).equals("URL_GENERATED")) {
                text = new StringBuilder();

                if (args.length > 0) {
                    if (args[0].equals("retry")) {
                        driveStates.put(ctx.chatId(), "TRYING");

                        sender.execute(new SendMessage()
                                .setText("Generando nuevo URL...")
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));

                        replyToDrive(ctx);
                    } else if (args[0].equals("code")) {
                        if (driveCredentials.get(ctx.chatId()) == null) {
                            storeCredentials = new HashMap<>();
                        } else {
                            storeCredentials = driveCredentials.get(ctx.chatId());
                        }

                        GoogleTokenResponse response =
                                new GoogleAuthorizationCodeTokenRequest(GoogleNetHttpTransport.newTrustedTransport(),
                                        JacksonFactory.getDefaultInstance(),
                                        GOOGLE_CLIENT, GOOGLE_SECRET, args[1],
                                        "https://angelbrv.com/google/redirect").execute();

                        text.append("Código guardado con éxito. :wink:");
                        storeCredentials.put("AUTH_CODE", args[1]);
                        storeCredentials.put("ACCESS_TOKEN", response.getAccessToken());
                        storeCredentials.put("REFRESH_TOKEN", response.getRefreshToken());
                        storeCredentials.put("TOKEN_TYPE", response.getTokenType());
                        storeCredentials.put("EXPIRY", response.getExpiresInSeconds().toString());
                        driveCredentials.put(ctx.chatId(), storeCredentials);

                        sender.execute(new SendMessage()
                                .setText(EmojiParser.parseToUnicode(text.toString()))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));

                        driveStates.put(ctx.chatId(), "USER_SAVED");
                    }
                }else{
                    text.append("Ingresa un argumento como <code>retry</code> o <code>code [codigo]</code> después");
                    text.append("del comando /drive. :no_mouth:");
                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                }
            }else if(driveStates.get(ctx.chatId()).equals("USER_SAVED")){
                // Acciones ahora que el usuario está autenticado
                HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
                String pageToken = null;
                List<File> list = new ArrayList<>();
                GoogleCredential credential = null;
                Drive service = null;

                storeCredentials = driveCredentials.get(ctx.chatId());
                String accessToken = storeCredentials.get("ACCESS_TOKEN");
                String refreshToken = storeCredentials.get("REFRESH_TOKEN");

                if(args.length > 0){
                    switch (args[0]) {
                        case "retry":
                            driveStates.put(ctx.chatId(), "TRYING");

                            sender.execute(new SendMessage()
                                    .setText("Generando nuevo URL...")
                                    .enableHtml(true)
                                    .setChatId(ctx.chatId()));

                            replyToDrive(ctx);
                            break;
                        case "code":
                            if (driveCredentials.get(ctx.chatId()) == null) {
                                storeCredentials = new HashMap<>();
                            } else {
                                storeCredentials = driveCredentials.get(ctx.chatId());
                            }

                            GoogleTokenResponse response =
                                    new GoogleAuthorizationCodeTokenRequest(GoogleNetHttpTransport.newTrustedTransport(),
                                            JacksonFactory.getDefaultInstance(),
                                            GOOGLE_CLIENT, GOOGLE_SECRET, args[1],
                                            "https://angelbrv.com/google/redirect").execute();

                            text.append("Código guardado con éxito. :wink:");
                            storeCredentials.put("AUTH_CODE", args[1]);
                            storeCredentials.put("ACCESS_TOKEN", response.getAccessToken());
                            storeCredentials.put("REFRESH_TOKEN", response.getRefreshToken());
                            storeCredentials.put("TOKEN_TYPE", response.getTokenType());
                            storeCredentials.put("EXPIRY", response.getExpiresInSeconds().toString());
                            driveCredentials.put(ctx.chatId(), storeCredentials);

                            sender.execute(new SendMessage()
                                    .setText(EmojiParser.parseToUnicode(text.toString()))
                                    .enableHtml(true)
                                    .setChatId(ctx.chatId()));

                            driveStates.put(ctx.chatId(), "USER_SAVED");
                            break;
                        case "lista":
                            credential = new GoogleCredential.Builder()
                                    .setTransport(httpTransport)
                                    .setJsonFactory(jacksonFactory)
                                    .setClientSecrets(GOOGLE_CLIENT, GOOGLE_SECRET).build();
                            credential.setAccessToken(accessToken);
                            credential.setRefreshToken(refreshToken);

                            service = new Drive.Builder(httpTransport,
                                    JacksonFactory.getDefaultInstance(),
                                    credential).setApplicationName("Lynx Domotics Bot").build();

                            text.append("Archivos:\n\n");
                            do {
                                FileList result = service.files().list().setQ("'root' in parents and trashed=false")
                                        .setSpaces("drive")
                                        .setFields("nextPageToken, files(id, name, parents, mimeType)")
                                        .setPageToken(pageToken).execute();
                                list.addAll(result.getFiles());

                                pageToken = result.getNextPageToken();
                            } while (pageToken != null);

                            for (File f : list) {
                                if(f.getMimeType().equals("application/vnd.google-apps.folder"))
                                    text.append("Folder: ");
                                else
                                    text.append("Archivo: ");
                                text.append(f.getName());
                                text.append("\n");
                            }

                            text.append("\nTodo esto en tu Drive. :wink:");

                            sender.execute(new SendMessage()
                                    .setText(EmojiParser.parseToUnicode(text.toString()))
                                    .enableHtml(true)
                                    .setChatId(ctx.chatId()));

                            driveStates.put(ctx.chatId(), "USER_SAVED");
                            break;
                        case "copiar":
                            credential = new GoogleCredential.Builder()
                                    .setTransport(httpTransport)
                                    .setJsonFactory(jacksonFactory)
                                    .setClientSecrets(GOOGLE_CLIENT, GOOGLE_SECRET).build();
                            credential.setAccessToken(accessToken);
                            credential.setRefreshToken(refreshToken);

                            service = new Drive.Builder(httpTransport,
                                    JacksonFactory.getDefaultInstance(),
                                    credential).setApplicationName("Lynx Domotics Bot").build();

                            File parentFile = service.files().get(args[1]).execute();
                            text = new StringBuilder();

                            for(int i = 1; i < args.length; i++){
                                  text.append(args[i]);
                            }

                            File dstFile = new File();
                            dstFile.setName(text.toString());
                            dstFile.setParents(parentFile.getParents());
                            File finalFile = service.files().copy(parentFile.getId(), dstFile).execute();

                            text = new StringBuilder();
                            text.append(finalFile.getId())
                                    .append(" ha sido copiado con éxito en la raíz de tu Drive como: <b>")
                                    .append(finalFile.getName())
                                    .append("</b>\n¡Saludos! :partying_face:");

                            sender.execute(new SendMessage()
                                    .setText(EmojiParser.parseToUnicode(text.toString()))
                                    .enableHtml(true)
                                    .setChatId(ctx.chatId()));
                            break;
                        case "copiarFolder":
                            credential = new GoogleCredential.Builder()
                                    .setTransport(httpTransport)
                                    .setJsonFactory(jacksonFactory)
                                    .setClientSecrets(GOOGLE_CLIENT, GOOGLE_SECRET).build();
                            credential.setAccessToken(accessToken);
                            credential.setRefreshToken(refreshToken);

                            service = new Drive.Builder(httpTransport,
                                    JacksonFactory.getDefaultInstance(),
                                    credential).setApplicationName("Lynx Domotics Bot").build();

                            list = new ArrayList<>();
                            do {
                                FileList result = service.files().list()
                                        .setQ(String.format("'%s' in parents", args[1]))
                                        .setSpaces("drive")
                                        .setFields("nextPageToken, files(id, name, parents, mimeType)")
                                        .setPageToken(pageToken).execute();

                                list.addAll(result.getFiles());

                                pageToken = result.getNextPageToken();
                            } while (pageToken != null);

                            text = new StringBuilder();

                            for (File f : list) {
                                dstFile = new File();
                                dstFile.setName(f.getName());
                                dstFile.setParents(f.getParents());

                                finalFile = service.files().copy(f.getId(), dstFile).execute();
                                text.append(finalFile.getName())
                                        .append(" copiado con éxito.\n");
                            }

                            text.append("\n<b>")
                                    .append(list.size() + 1)
                                    .append(" archivos</b> han sido copiado con éxito en la raíz de tu Drive.")
                                    .append("\n¡Saludos! :partying_face:");

                            sender.execute(new SendMessage()
                                    .setText(EmojiParser.parseToUnicode(text.toString()))
                                    .enableHtml(true)
                                    .setChatId(ctx.chatId()));
                            break;
                        default:
                            break;
                    }
                }else{
                    text.append("Los siguientes argumentos están disponibles para este comando:\n\n");
                    text.append("<code>lista</code>\n");
                    text.append("<code>copiar</code>\n");
                    text.append("<code>copiarFolder</code>\n");
                    text.append("<code>retry</code>\n");
                    text.append("<code>code</code>\n");
                    text.append("\n");
                    text.append("Gracias. :grin:");
                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                    //
                }
            }

        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.error(e.getMessage(), e);

            try{
                sender.execute(new SendMessage()
                        .setText(EmojiParser.parseToUnicode("Ha ocurrido un error inesperado. :disappointed:" +
                                "\n:thinking: Detalles:\n<code>"+e.getMessage()+"</code>" +
                                "\n\nNo te preocupes, el admin ya ha sido notificado del error. :hugs:"))
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));
            }catch (TelegramApiException ex){
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }
}
