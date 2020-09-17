package com.pekochu.lynx.commands.telegram;

import com.vdurmont.emoji.EmojiParser;
import org.jetbrains.annotations.NotNull;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Locality;
import org.telegram.abilitybots.api.objects.Privacy;
import org.telegram.abilitybots.api.sender.SilentSender;
import org.telegram.abilitybots.api.util.AbilityExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class BasicCommands implements AbilityExtension {

    private final SilentSender silent;

    public BasicCommands(SilentSender silent){
        this.silent = silent;
    }

    public Ability start(){
        return Ability.builder()
                .name("start")
                .info("¡Hola, mundo!")
                .privacy(Privacy.PUBLIC)
                .locality(Locality.ALL)
                .action(ctx -> {
                    StringBuilder text = new StringBuilder();
                    text.append(String.format("Hola, %s. :relieved:", ctx.user().getUserName()));

                    silent.send(EmojiParser.parseToUnicode(text.toString()), ctx.chatId());
                })
                .build();
    }

    public Ability whoami(){
        return Ability.builder()
                .name("whoami")
                .info("Devuelve la respuesta del comando \"whoami\".")
                .privacy(Privacy.PUBLIC)
                .locality(Locality.ALL)
                .action(ctx -> {
                    StringBuilder text = new StringBuilder();
                    text.append(String.format("Respuesta del terminal:\n%s\n:hushed:", responseFromTerminal("whoami")));

                    silent.send(EmojiParser.parseToUnicode(text.toString()), ctx.chatId());
                })
                .build();
    }

    @NotNull
    private String responseFromTerminal(String cmd){
        StringBuffer output = new StringBuffer();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null) {
                output.append(line + "\n");
            }
            // your output that you can use to build your json response:
            output.toString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return output.toString();
    }

    public Ability telegramId() {
        return Ability.builder()
                .name("id")
                .info("Te muestra tu Telegram ID.")
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .action(ctx -> {
                    StringBuilder reply = new StringBuilder();
                    reply.append(String.format("Tu Telegram ID es: %d :stuck_out_tongue:", ctx.user().getId()));
                    silent.send(EmojiParser.parseToUnicode(reply.toString()), ctx.chatId());
                })
                .build();
    }


}
