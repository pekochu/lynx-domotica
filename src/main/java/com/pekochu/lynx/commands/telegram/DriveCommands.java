package com.pekochu.lynx.commands.telegram;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.User;
import com.pekochu.lynx.bots.TelegramBot;
import com.pekochu.lynx.utilities.Common;
import com.vdurmont.emoji.EmojiParser;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.sender.SilentSender;
import org.telegram.abilitybots.api.util.AbilityExtension;
import org.telegram.abilitybots.api.util.AbilityUtils;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DriveCommands implements AbilityExtension {

    private final static Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class.getCanonicalName());
    private final static String DRIVE_FIELDS = "name, id, mimeType, kind, parents, owners, capabilities, size";

    private final DBContext db;
    private MessageSender sender;
    private SilentSender silent;

    private final Map<Long, Map<String, String>> driveCredentials;
    private final Map<Long, Map<Long, String>> drivePendings;
    private final Map<Long, String> driveStates;

    private final String GOOGLE_CLIENT;
    private final String GOOGLE_SECRET;

    // Constructor with all the the vars
    public DriveCommands(DBContext db, MessageSender sender, SilentSender silent, String client, String secret) {
        this.db = db;
        this.sender = sender;
        this.silent = silent;
        // Maps
        driveCredentials = this.db.getMap("DRIVE_CREDENTIALS");
        drivePendings = this.db.getMap("DRIVE_PENDINGS");
        driveStates = this.db.getMap("DRIVE_STATES");
        // Google secrets
        this.GOOGLE_CLIENT = client;
        this.GOOGLE_SECRET = secret;
    }

    // Ability function for AbilityExtension implementation
    public Ability driveAbility(){
        return Ability.builder()
                .name("drive")
                .info("Interactuar con la API de Google Drive con el bot.")
                .privacy(Privacy.PUBLIC)
                .locality(Locality.ALL)
                .action(ctx -> {
                    //
                    SendChatAction sendChatAction = new SendChatAction();
                    sendChatAction.setAction(ActionType.TYPING);
                    sendChatAction.setChatId(String.valueOf(ctx.chatId()));
                    silent.execute(sendChatAction);
                    replyToDrive(ctx);
                }).build();
    }

    public void replyToDrive(MessageContext ctx){
        StringBuilder text;
        SendMessage snd;
        String pageToken = null;
        String accessToken;
        String refreshToken;
        String[] args = ctx.arguments();
        HttpTransport httpTransport;
        JacksonFactory jacksonFactory;

        Pattern driveFileId = Pattern.compile("https://drive\\.google\\.com/file/d/(.*?)/.*?\\?usp=sharing");
        Matcher matcher;

        List<File> list;
        GoogleCredential credential;
        Drive service;

        InlineKeyboardMarkup inlineKeyboardMarkup;
        List<List<InlineKeyboardButton>> keyboardAllRows;
        List<InlineKeyboardButton> keyboardRow;

        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' y 'a las' HH:mm:ss",
                new Locale("es", "mx"));

        try {
            if (driveCredentials.get(ctx.chatId()) == null) {
                snd = new SendMessage();
                snd.enableHtml(true);
                snd.setText("No has iniciado sesión. Por favor, usa el comando" +
                        "<code>/googleauth</code> para obtener más información.");
                snd.setChatId(String.valueOf(ctx.chatId()));

                sender.execute(snd);
                driveStates.put(ctx.chatId(), "TRYING");
            }else{

                if(args.length == 0) {
                    text = new StringBuilder();
                    text.append("Los siguientes argumentos están disponibles para este comando:\n\n");
                    text.append("<code>/drive account</code>\n");
                    text.append("<code>/drive list</code>\n");
                    text.append("<code>/drive copy</code>\n");
                    text.append("<code>/drive fcopy</code>\n\n");
                    text.append("<code>/drive pending</code>\n\n");
                    text.append("Los argumentos son auto-explicativos. :grin:");

                    snd = new SendMessage();
                    snd.enableHtml(true);
                    snd.setText(EmojiParser.parseToUnicode(text.toString()));
                    snd.setChatId(String.valueOf(ctx.chatId()));

                    sender.execute(snd);
                    return;
                }

                // now the user is authenticated
                httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                jacksonFactory = JacksonFactory.getDefaultInstance();

                // getting user tokens
                Map<String, String> storeCredentials = driveCredentials.get(ctx.chatId());
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
                        JacksonFactory.getDefaultInstance(),credential).setApplicationName("Lynx Domotics Bot").build();

                switch (args[0]) {
                    case "pending":
                        // init needed vars
                        text = new StringBuilder();
                        snd = new SendMessage();
                        snd.enableHtml(true);

                        Map<Long, String> storePendings;
                        storePendings = drivePendings.get(ctx.chatId()) == null ?
                                new HashMap<>() : drivePendings.get(ctx.chatId());

                        if(storePendings.size() == 0){
                            text.append("No tienes archivos pendientes para copiar. :alien:");
                        }else{
                            text.append("<b>Lista de los archivos que están pendientes</b>:\n");
                            for (Map.Entry<Long, String> entry : storePendings.entrySet()) {
                                Date fileDate = new Date(entry.getKey() * 1000L);
                                File filePending = service.files()
                                        .get(entry.getValue())
                                        .setFields(DRIVE_FIELDS)
                                        .execute();
                                text.append(String.format("\n:pushpin: Archivo <b>\"%s\" (%s)</b> creado el <b>%s</b>",
                                        filePending.getName(), Common.humanReadableByteCountBin(filePending.getSize()),
                                        sdf.format(fileDate)));
                            }

                            inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            keyboardAllRows = new ArrayList<>();

                            keyboardRow = new ArrayList<>();
                            InlineKeyboardButton keyButton = new InlineKeyboardButton();
                            keyButton.setText("Intentar copiar los archivos");
                            keyButton.setCallbackData("DRIVE:PENDINGS_COPY");
                            keyboardRow.add(keyButton);

                            keyButton = new InlineKeyboardButton();
                            keyButton.setText("Eliminar archivos");
                            keyButton.setCallbackData("DRIVE:PENDINGS_DELETE");
                            keyboardRow.add(keyButton);
                            keyboardAllRows.add(keyboardRow);
                            inlineKeyboardMarkup.setKeyboard(keyboardAllRows);
                            snd.setReplyMarkup(inlineKeyboardMarkup);
                        }

                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);

                        break;
                    case "account":
                        // init needed vars
                        text = new StringBuilder();
                        About aboutUser = service.about().get().setFields("user, storageQuota, maxUploadSize").execute();

                        text.append("<b>Cuenta con la que iniciaste sesión</b>:\n\n");
                        text.append(String.format("Correo: <b>%s</b>\n", aboutUser.getUser().getEmailAddress()));
                        text.append(String.format("Nombre: <b>%s</b>\n", aboutUser.getUser().getDisplayName()));
                        text.append(String.format("Tipo de cuenta: <b>%s</b>\n", aboutUser.getUser().getKind()));
                        text.append("\n<b>Almacenamiento</b>:\n\n");

                        long totalStorage = aboutUser.getStorageQuota().getLimit();
                        long totalUsed = aboutUser.getStorageQuota().getUsage();
                        long totalFree = totalStorage - totalUsed;

                        text.append(String.format("Total: <b>%s</b>\n", Common.humanReadableByteCountBin(totalStorage)));
                        text.append(String.format("Usado: <b>%s</b>\n", Common.humanReadableByteCountBin(totalUsed)));
                        text.append(String.format("Libre: <b>%s</b>\n", Common.humanReadableByteCountBin(totalFree)));
                        text.append(String.format("Limite de tamaño de archivos: <b>%s</b>\n",
                                Common.humanReadableByteCountBin(aboutUser.getMaxUploadSize())));
                        /*
                        SendPhoto photoLink = new SendPhoto();
                        photoLink.setPhoto(new InputFile(aboutUser.getUser().getPhotoLink()));
                        photoLink.setChatId(String.valueOf(ctx.chatId())); */

                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        silent.execute(snd);
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

                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
                        break;
                    case "info":
                        // init needed vars
                        if(!(args.length > 1)){
                            text = new StringBuilder("Argumentos insuficientes. :x:");
                            snd = new SendMessage();
                            snd.enableHtml(true);
                            snd.setText(EmojiParser.parseToUnicode(text.toString()));
                            snd.setChatId(String.valueOf(ctx.chatId()));

                            sender.execute(snd);
                            return;
                        }

                        matcher = driveFileId.matcher(args[1]);
                        if (matcher.find()) {
                            text = fileInfo(service, matcher.group(1));
                        } else {
                            text = fileInfo(service, args[1]);
                        }

                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
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
                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
                        break;
                    case "fcopy":
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
                            service.files().copy(f.getId(), dstFile).execute();

                            text.append(String.format("El archivo \"<b>%s</b>\" ha sido copiado con éxito.\n",
                                    f.getName()));
                        }

                        text.append("\n<b>")
                                .append(list.size() + 1)
                                .append(" archivos</b> han sido examinados. Los archivos copiados se encuentran en ")
                                .append("la carpeta raíz de tu Drive. :partying_face:");

                        snd = new SendMessage();
                        snd.enableHtml(true);
                        snd.setText(EmojiParser.parseToUnicode(text.toString()));
                        snd.setChatId(String.valueOf(ctx.chatId()));

                        sender.execute(snd);
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
                JSONObject jsonDriveReasons = jsonDriveError.getJSONArray("errors").getJSONObject(0);
                LOGGER.info(String.format("Drive error JSON: %s", jsonDriveError.toString()));
                LOGGER.info(String.format("Reasons error JSON: %s", jsonDriveReasons.toString()));

                snd = new SendMessage();
                snd.enableHtml(true);
                snd.setText(EmojiParser.parseToUnicode("Ha ocurrido un error inesperado. :disappointed:" +
                        "\n:thinking: Detalles:\n\n<pre>"+jsonDriveError.getString("message")+"</pre>"));
                snd.setChatId(String.valueOf(ctx.chatId()));

                if(jsonDriveReasons.getString("reason").equals("userRateLimitExceeded")){
                    matcher = driveFileId.matcher(args[1]);

                    Map<Long, String> storePendings;
                    storePendings = drivePendings.get(ctx.chatId()) == null ?
                            new HashMap<>() : drivePendings.get(ctx.chatId());

                    String idPending = matcher.find() ? matcher.group(1) : args[1];
                    if(storePendings.size() == 0){
                        storePendings.put(System.currentTimeMillis() / 1000L, idPending);
                    }else if(!storePendings.containsValue(idPending)){
                        storePendings.put(System.currentTimeMillis() / 1000L, idPending);
                    }

                    drivePendings.put(ctx.chatId(), storePendings);
                }

                sender.execute(snd);
            }catch (TelegramApiException | JSONException ex){
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    // DRIVE API
    public StringBuilder fileCopy(@NotNull Drive service, String id, String @NotNull [] args)
            throws SecurityException, IOException{
        StringBuilder text = new StringBuilder();
        StringBuilder message = new StringBuilder();
        List<String> idList = Arrays.asList(id.split(","));

        // Get name
        if (args.length > 2) {
            File parentFile = service.files()
                    .get(id)
                    .setFields(DRIVE_FIELDS)
                    .execute();

            LOGGER.info(String.format("File ID: %s", parentFile.getId()));
            LOGGER.info(String.format("Name: %s", parentFile.getName()));

            for (int i = 2; i < args.length; i++) {
                text.append(args[i]);
                if (i < args.length - 1) text.append(" ");
            }

            File dstFile = new File();
            dstFile.setName(text.toString());
            dstFile.setParents(Collections.singletonList("root"));
            File finalFile = service.files().copy(parentFile.getId(), dstFile).execute();

            message.append(String.format("El archivo \"<b>%s</b>\" ha sido copiado con éxito. ",
                    finalFile.getName()));
            message.append(":white_check_mark:");
        } else {
            // Multi copy
            for (String sid: idList) {
                File parentFile = service.files()
                        .get(sid)
                        .setFields(DRIVE_FIELDS)
                        .execute();

                File dstFile = new File();
                dstFile.setName(parentFile.getName());
                dstFile.setParents(Collections.singletonList("root"));
                File finalFile = service.files().copy(parentFile.getId(), dstFile).execute();

                message.append(String.format("El archivo \"<b>%s</b>\" ha sido copiado con éxito. ",
                        finalFile.getName()));
                message.append(":white_check_mark:\n\n");
            }

            message.append(String.format("\n¡%d archivos copiados con éxito! ", idList.size()));
            message.append(":partying_face:");
        }

        return message;
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

    public void replyToButtons(Update update){
        long chatId = AbilityUtils.getChatId(update);
        // init vars
        String accessToken;
        String refreshToken;
        HttpTransport httpTransport;
        JacksonFactory jacksonFactory;
        GoogleCredential credential;
        Drive service;

        try {
            String replyData = update.getCallbackQuery().getData();
            String[] dataSplitted = replyData.split(":");
            StringBuilder message = new StringBuilder();
            if(dataSplitted[0].equals("DRIVE")){
                // now the user is authenticated
                httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                jacksonFactory = JacksonFactory.getDefaultInstance();

                // getting user tokens
                Map<String, String> storeCredentials = driveCredentials.get(chatId);
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
                        JacksonFactory.getDefaultInstance(),credential).setApplicationName("Lynx Domotics Bot").build();

                Map<Long, String> storePendings;
                storePendings = drivePendings.get(chatId) == null ?
                        new HashMap<>() : drivePendings.get(chatId);

                EditMessageText edited = new EditMessageText();
                edited.setChatId(Long.toString(update.getCallbackQuery().getMessage().getChatId()));
                edited.setMessageId(update.getCallbackQuery().getMessage().getMessageId());

                SendMessage snd = new SendMessage();
                snd.enableHtml(true);

                if(dataSplitted[1].equals("PENDINGS_COPY")){
                    edited.setText(EmojiParser.parseToUnicode("Copiando... :clock130:"));
                    edited.setReplyMarkup(null);
                    sender.execute(edited);

                    for (Map.Entry<Long, String> entry : storePendings.entrySet()) {
                        File filePending = service.files()
                                .get(entry.getValue())
                                .setFields(DRIVE_FIELDS)
                                .execute();
                        try{
                            message.append(fileCopy(
                                    service, filePending.getId(), new String[]{"", filePending.getId()}).toString());
                            storePendings.remove(entry.getKey());
                        }catch(SecurityException | IOException d){
                            message.append(String.format("Archivo <b>\"%s\" (%s)</b> aún no puede copiarse porque ",
                                    filePending.getName(), Common.humanReadableByteCountBin(filePending.getSize())));
                            message.append("se ha excedido la cuota del archivo o no tienes permisos para copiarlo.");
                            message.append(" :no_entry_sign:\n\n");
                        }
                    }

                    message.append("Proceso de copiado terminado. :relieved:");
                    snd.setText(EmojiParser.parseToUnicode(message.toString()));
                    snd.setChatId(String.valueOf(chatId));
                    snd.setReplyToMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    sender.execute(snd);
                }else if(dataSplitted[1].equals("PENDINGS_DELETE")){
                    edited.setText(EmojiParser.parseToUnicode("Eliminando... :clock130:"));
                    edited.setReplyMarkup(null);
                    sender.execute(edited);

                    message.append(String.format("Proceso de limpieza terminado. :relieved:\nEliminados <b>%d archivos</b>.",
                            storePendings.size()));
                    storePendings.clear();
                    message.append(" :wastebasket:");

                    snd.setText(EmojiParser.parseToUnicode(message.toString()));
                    snd.setChatId(String.valueOf(chatId));
                    snd.setReplyToMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    sender.execute(snd);
                }
            }
        } catch (TelegramApiException | GeneralSecurityException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public Reply replys() {
        Consumer<Update> action = upd -> {
            replyToButtons(upd);
        };
        return Reply.of(action, Flag.CALLBACK_QUERY);
    }
}
