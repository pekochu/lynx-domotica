package com.pekochu.lynx.commands.telegram;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.User;
import com.pekochu.lynx.bots.TelegramBot;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DriveCommands {

    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());

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
        Pattern driveFolderId = Pattern.compile("https://drive\\.google\\.com/drive/folders/(.*?)");
        Matcher matcher = null;

        List<File> list = null;
        GoogleCredential credential = null;
        Drive service = null;

        try {
            if (driveCredentials.get(ctx.chatId()) == null) {
                sender.execute(new SendMessage()
                        .setText("No has iniciado sesión. Por favor, usa el comando" +
                                "<code>/googleauth</code> para obtener más información.")
                        .enableHtml(true)
                        .setChatId(ctx.chatId()));

                driveStates.put(ctx.chatId(), "TRYING");
            }else{
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

                if(args.length == 0) {
                    text = new StringBuilder();
                    text.append("Los siguientes argumentos están disponibles para este comando:\n\n");
                    text.append("<code>/drive list</code>\n");
                    text.append("<code>/drive copy</code>\n");
                    text.append("<code>/drive folderCopy</code>\n\n");
                    text.append("Los argumentos son auto-explicativos. Gracias. :grin:");
                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));

                    return;
                }

                if ("list".equals(args[0])) {
                    // init needed vars
                    text = new StringBuilder();
                    list = new ArrayList<>();

                    text.append("Archivos:\n\n");
                    do {
                        FileList result = service.files().list().setQ("'root' in parents and trashed=false")
                                .setSpaces("drive")
                                .setFields("nextPageToken, files(name, id, mimeType, kind, parents, owners, capabilities)")
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
                } else if ("info".equals(args[0])) {
                    // init needed vars
                    text = new StringBuilder();
                    matcher = driveFileId.matcher(args[1]);

                    if(matcher.find()){
                        File parentFile = service.files()
                                .get(matcher.group(1))
                                .setFields("name, id, mimeType, kind, parents, owners, capabilities")
                                .execute();

                        text = new StringBuilder();

                        text.append(String.format("Nombre del archivo: <b>%s</b>\n", parentFile.getName()));
                        text.append(String.format("Tipo MIME: <b>%s</b>\n", parentFile.getMimeType()));

                        text.append("\n<i>Parents</i>\n");
                        for (String s: parentFile.getParents()){
                            text.append(String.format("ID: <b>%s</b>\n", s));
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


                        text.append("\nServido.");

                    }else{
                        text = new StringBuilder();
                        text.append("La URL del archivo de Drive tiene que tener el formato como sigue:\n<code>");
                        text.append("https://drive.google.com/file/d/&lt;1XXXXXXXXXXXXX_XXXXXXXXXXXXXXXXX&gt;")
                                .append("</code>\n\nPor favor. Gracias. :pleading_face:");
                    }

                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                } else if ("copy".equals(args[0])) {
                    // init needed vars
                    text = new StringBuilder();
                    matcher = driveFileId.matcher(args[1]);

                    if(matcher.find()){
                        File parentFile = service.files()
                                .get(matcher.group(1))
                                .setFields("name, id, mimeType, kind, parents, owners, capabilities")
                                .execute();

                        if(args.length > 2){
                            for (int i = 2; i < args.length; i++) {
                                text.append(args[i]);
                                if(i < args.length -1) text.append(" ");
                            }
                        }else{
                            text.append(parentFile.getName());
                        }


                        File dstFile = new File();
                        dstFile.setName(text.toString());
                        dstFile.setParents(Collections.singletonList("root"));

                        File finalFile = service.files()
                                .copy(parentFile.getId(), dstFile)
                                .execute();

                        text = new StringBuilder();
                        text.append(finalFile.getId())
                                .append(String.format("<i>%s</i> ha sido copiado con éxito en la raiz del " +
                                                "Drive como <b>%s</b>", finalFile.getId(), finalFile.getName()))
                                .append("\n¡Saludos! :partying_face:");
                    }else{
                        text = new StringBuilder();
                        text.append("La URL del archivo de Drive tiene que tener el formato como sigue:\n<code>");
                        text.append("https://drive.google.com/file/d/&lt;1XXXXXXXXXXXXX_XXXXXXXXXXXXXXXXX&gt;")
                                .append("</code>\n\nPor favor. Gracias. :pleading_face:");
                    }

                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                } else if ("folderCopy".equals(args[0])) {
                    // init needed vars
                    list = new ArrayList<>();
                    text = new StringBuilder();
                    File dstFile;

                    LOGGER.info(String.format("ID: %s", args[1]));
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

                        if(f.getCapabilities().getCanCopy()){
                            service.files()
                                    .copy(f.getId(), dstFile)
                                    .execute();

                            text.append(String.format("<i>%s</i> copiado con éxito.\n"));
                        }else{
                            text.append(String.format("<i>%s</i> <b>no pudo copiarse.</b>\n"));
                        }

                    }

                    text.append("\n<b>")
                            .append(list.size() + 1)
                            .append(" archivos</b> han sido examinados. Los archivos copiados se encuentran en " +
                                    "la carpeta raíz del Drive.")
                            .append("\n¡Saludos! :partying_face:");

                    sender.execute(new SendMessage()
                            .setText(EmojiParser.parseToUnicode(text.toString()))
                            .enableHtml(true)
                            .setChatId(ctx.chatId()));
                } // end-if arguments
            } //end-if
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

}
