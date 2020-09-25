package com.pekochu.lynx.commands.telegram;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.User;
import com.pekochu.lynx.bots.TelegramBot;
import com.vdurmont.emoji.EmojiParser;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.util.AbilityUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DriveCommands {

    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());
    private final static String DRIVE_FIELDS = "name, id, mimeType, kind, parents, owners, capabilities";

    private final MessageSender sender;
    private final Map<Long, Map<String, String>> driveCredentials;
    private Map<String, String> storeCredentials;
    private final Map<Long, String> driveStates;

    private final String GOOGLE_CLIENT;
    private final String GOOGLE_SECRET;

    public DriveCommands(MessageSender sender, DBContext db, String client, String secret) {
        this.sender = sender;
        driveCredentials = db.getMap("DRIVE_CREDENTIALS");
        driveStates = db.getMap("DRIVE_STATES");
        this.GOOGLE_CLIENT = client;
        this.GOOGLE_SECRET = secret;
    }

    public void replyToDrive(MessageContext ctx){
        StringBuilder text;
        String pageToken = null;
        String accessToken = null;
        String refreshToken = null;
        String[] args = ctx.arguments();
        HttpTransport httpTransport = null;
        JacksonFactory jacksonFactory = null;

        Pattern driveFileId = Pattern.compile("https://drive\\.google\\.com/file/d/(.*?)/.*?\\?usp=sharing");
        Matcher matcher = null;

        List<File> list = null;
        GoogleCredential credential = null;
        Drive service = null;

        InlineKeyboardMarkup inlineKeyboard;
        List<InlineKeyboardButton> buttonsRow;
        List<List<InlineKeyboardButton>> buttonsKeyboard;

        try {
            if (driveCredentials.get(ctx.chatId()) == null) {
                sender.execute(new SendMessage()
                        .setText("No has iniciado sesión. Por favor, usa el comando" +
                                "<code>/googleauth</code> para obtener más información.")
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));

                driveStates.put(ctx.chatId(), "TRYING");
            }else{

                if(args.length == 0) {
                    text = new StringBuilder();
                    text.append("Los siguientes argumentos están disponibles para este comando:\n\n");
                    text.append("<code>/drive list</code>\n");
                    text.append("<code>/drive copy</code>\n");
                    text.append("<code>/drive folderCopy</code>\n\n");
                    text.append("Los argumentos son auto-explicativos. :grin:");
                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));

                    return;
                }

                // now the user is authenticated
                httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                jacksonFactory = JacksonFactory.getDefaultInstance();

                // getting user tokens
                storeCredentials = driveCredentials.get(ctx.chatId());
                accessToken = storeCredentials.get("ACCESS_TOKEN");
                refreshToken = storeCredentials.get("REFRESH_TOKEN");

                // creating credentials
                credential = new GoogleCredential.Builder()
                        .setTransport(httpTransport)
                        .setJsonFactory(jacksonFactory)
                        .setClientSecrets(GOOGLE_CLIENT, GOOGLE_SECRET).build();
                credential.setAccessToken(accessToken);
                credential.setRefreshToken(refreshToken);

                service = new Drive.Builder(httpTransport,
                        JacksonFactory.getDefaultInstance(),
                        credential).setApplicationName("Lynx Domotics Bot").build();

                switch (args[0]) {
                    case "addParent":
                        // init needed vars
                        text = new StringBuilder();
                        inlineKeyboard = new InlineKeyboardMarkup();
                        buttonsRow = new ArrayList<>();
                        buttonsKeyboard = new ArrayList<>();

                        buttonsRow.add(new InlineKeyboardButton()
                                .setText("Si, agregar")
                                .setCallbackData("YES_IAM"));
                        buttonsRow.add(new InlineKeyboardButton()
                                .setText("No, terminar")
                                .setCallbackData("NO_IAM_NOT"));
                        buttonsKeyboard.add(buttonsRow);
                        inlineKeyboard.setKeyboard(buttonsKeyboard);

                        text.append("Información del parent: ");

                        sender.execute(new SendMessage()
                                .setText(EmojiParser.parseToUnicode(text.toString()))
                                .enableHtml(true)
                                .setReplyMarkup(inlineKeyboard)
                                .setChatId(ctx.chatId()));

                        break;
                    case "list":
                        // init needed vars
                        text = new StringBuilder();
                        list = new ArrayList<>();

                        text.append("Archivos:\n\n");
                        do {
                            FileList result = service.files().list().setQ("'root' in parents and trashed=false")
                                    .setSpaces("drive")
                                    .setFields("nextPageToken, files(" + DRIVE_FIELDS + ")")
                                    .setPageToken(pageToken).execute();
                            list.addAll(result.getFiles());

                            pageToken = result.getNextPageToken();
                        } while (pageToken != null);

                        for (File f : list) {
                            if (f.getMimeType().equals("application/vnd.google-apps.folder"))
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
                    case "info":
                        // init needed vars
                        matcher = driveFileId.matcher(args[1]);

                        if (matcher.find()) {
                            text = fileInfo(service, matcher.group(1));
                        } else {
                            text = fileInfo(service, args[1]);
                        }

                        sender.execute(new SendMessage()
                                .setText(EmojiParser.parseToUnicode(text.toString()))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));
                        break;
                    case "copy":
                        // init needed vars
                        matcher = driveFileId.matcher(args[1]);

                        // get file id
                        if (matcher.find()) {
                            text = fileCopy(service, matcher.group(1), args);
                        } else {
                            text = fileCopy(service, args[1], args);
                        }

                        // send message
                        sender.execute(new SendMessage()
                                .setText(EmojiParser.parseToUnicode(text.toString()))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));
                        break;
                    case "folderCopy":
                        // init needed vars
                        list = new ArrayList<>();
                        text = new StringBuilder();
                        File dstFile;

                        LOGGER.info(String.format("Folder ID: %s", args[1]));
                        do {
                            FileList result = service.files().list()
                                    .setQ(String.format("'%s' in parents", args[1]))
                                    .setSpaces("drive")
                                    .setFields("nextPageToken, files(name, id, mimeType, kind, parents, owners, capabilities)")
                                    .setPageToken(pageToken)
                                    .execute();

                            list.addAll(result.getFiles());

                            pageToken = result.getNextPageToken();
                        } while (pageToken != null);

                        for (File f : list) {
                            dstFile = new File();
                            dstFile.setName(f.getName());
                            dstFile.setParents(Collections.singletonList("root"));

                            if (f.getCapabilities().getCanCopy()) {
                                service.files()
                                        .copy(f.getId(), dstFile)
                                        .execute();

                                text.append(String.format("<i>%s</i> <b>copiado con éxito</b>.\n", f.getName()));
                            } else {
                                text.append(String.format("<i>%s</i> <b>no pudo copiarse.</b>\n", f.getName()));
                            }

                        }

                        text.append("\n<b>")
                                .append(list.size() + 1)
                                .append(" archivos</b> han sido examinados. Los archivos copiados se encuentran en ")
                                .append("la carpeta raíz de tu Drive. :partying_face:");

                        sender.execute(new SendMessage()
                                .setText(EmojiParser.parseToUnicode(text.toString()))
                                .enableHtml(true)
                                .setChatId(ctx.chatId()));
                        break;
                }
            } //end-if
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.warn(e.getMessage());

            try{
                // get code point of {
                int jsonStartsAt = e.getMessage().indexOf(123);
                JSONObject jsonDriveError = new JSONObject(e.getMessage().substring(jsonStartsAt));
                LOGGER.info(String.format("JSON Object: %s", jsonDriveError.toString()));

                sender.execute(new SendMessage()
                        .setText(EmojiParser.parseToUnicode("Ha ocurrido un error inesperado. :disappointed:" +
                                "\n:thinking: Detalles:\n\n<pre>"+jsonDriveError.getString("message")+"</pre>"))
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));
            }catch (TelegramApiException | JSONException ex){
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    public void replyToButtons(Update update){
        long chatId = AbilityUtils.getChatId(update);
        try {
            sender.execute(new SendMessage()
                    .setText(String.format("Callback Query: <b>%d</b>", update.getUpdateId()))
                    .enableHtml(true)
                    .setChatId(chatId));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // DRIVE API

    public StringBuilder fileCopy(@NotNull Drive service, String id, String @NotNull [] args)
            throws SecurityException, IOException{
        StringBuilder text = new StringBuilder();
        File parentFile = service.files()
                .get(id)
                .setFields(DRIVE_FIELDS)
                .execute();

        LOGGER.info(String.format("File ID: %s", parentFile.getId()));
        LOGGER.info(String.format("Name: %s", parentFile.getName()));

        // Get name
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                text.append(args[i]);
                if (i < args.length - 1) text.append(" ");
            }
        } else {
            text.append(parentFile.getName());
        }

        File dstFile = new File();
        dstFile.setName(text.toString());
        dstFile.setParents(Collections.singletonList("root"));

        File finalFile = service.files()
                .copy(parentFile.getId(), dstFile)
                .execute();

        text = new StringBuilder();
        text.append(String.format("<i>%s</i> ha sido <b>copiado con éxito</b> en la raiz de tu Drive. :partying_face:",
                finalFile.getName()));

        return text;
    }

    public StringBuilder fileInfo(@NotNull Drive service, String id)
            throws SecurityException, IOException{
        StringBuilder text = new StringBuilder();

        File parentFile = service.files()
                .get(id)
                .setFields(DRIVE_FIELDS)
                .execute();

        text.append(String.format("Nombre del archivo: <b>%s</b>\n", parentFile.getName()));
        text.append(String.format("Tipo MIME: <b>%s</b>\n", parentFile.getMimeType()));

        if(parentFile.getParents() != null){
            text.append("\n<i>Parents</i>\n");
            for (String s: parentFile.getParents()){
                text.append(String.format("ID: <b>%s</b>\n", s));
            }
        }

        text.append("\n<i>Capacidades</i>\n");
        for (Map.Entry<String, Object> entry: parentFile.getCapabilities().entrySet()){
            text.append(String.format("%s: <b>%s</b>\n", entry.getKey(), entry.getValue().toString()));
        }

        text.append("\n<i>Dueños</i>\n");
        for (User entry: parentFile.getOwners()){
            text.append(String.format("Nombre: <b>%s</b>\n", entry.getDisplayName()));
            text.append(String.format("ID de permiso: <b>%s</b>\n", entry.getPermissionId()));
            text.append(String.format("Email: <b>%s</b>\n", entry.getEmailAddress()));
        }

        return text;
    }
}
