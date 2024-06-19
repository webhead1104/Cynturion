package net.cytonic.cynturion.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * A class that holds utils for formatting messages
 */
public final class MessageUtils {
    /**
     * Defaut constructor
     */
    private MessageUtils() {
    }

    /**
     * Gets a formatted ban message
     *
     * @param banData the data to format
     * @return the formatted message in Component format
     */
    public static Component formatBanMessage(BanData banData) {
        if (!banData.isBanned()) return Component.empty();

        return Component.empty()
                .append(MiniMessage.miniMessage().deserialize("<red>You are currently banned from the Cytonic Network!").appendNewline().appendNewline())
                .append(MiniMessage.miniMessage().deserialize("<gray>Reason:<gray> <white>"+banData.reason()+"</white>").appendNewline())
                .append(MiniMessage.miniMessage().deserialize("<gray>Expires:</gray> <white>"+DurationParser.unparse(banData.expiry(), " ")+"</white>").appendNewline().appendNewline())
                .append(MiniMessage.miniMessage().deserialize("<gray>Appeal at: </gray><aqua><underlined>https://cytonic.net</underlined></aqua>").appendNewline());
    }
}
