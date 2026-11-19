package com.kayf.risco

/**
 * What a command's argument is picked from: nothing, a set of apps (ITEM adds pinned shortcuts),
 * the installed widgets, or the command's own choices.
 */
enum class Arg { NONE, APP, ITEM, PINNED, HIDDEN, WIDGET, CHOICE }

/** [word] is typed and stays English; [help] is shown beside it, in risco's language. */
enum class Command(val word: String, val arg: Arg, val help: Int, val choices: List<String> = emptyList()) {
    PIN("pin", Arg.APP, R.string.help_pin),
    UNPIN("unpin", Arg.PINNED, R.string.help_unpin),
    RENAME("rename", Arg.ITEM, R.string.help_rename),
    HIDE("hide", Arg.APP, R.string.help_hide),
    UNHIDE("unhide", Arg.HIDDEN, R.string.help_unhide),
    INFO("info", Arg.APP, R.string.help_info),
    RM("rm", Arg.APP, R.string.help_rm),
    ICONS("icons", Arg.CHOICE, R.string.help_icons, listOf("on", "off")),
    ALPHABET("alphabet", Arg.CHOICE, R.string.help_alphabet, AlphabetChoice.entries.map { it.word }),
    LANGUAGE("language", Arg.NONE, R.string.help_language),
    MENU("menu", Arg.NONE, R.string.help_menu),
    WALLPAPER("wallpaper", Arg.NONE, R.string.help_wallpaper),
    WIDGET("widget", Arg.WIDGET, R.string.help_widget),
    SET("set", Arg.NONE, R.string.help_set),
    DEFAULT("default", Arg.NONE, R.string.help_default),
    Q("q", Arg.NONE, R.string.help_q),
}

sealed class Input {
    data class Search(val query: String) : Input()
    data class Partial(val matches: List<Command>) : Input()
    data class Full(val command: Command, val argument: String) : Input()
}

/** The choices that start with what's been typed so far. */
fun Command.choicesFor(argument: String) = choices.filter { it.startsWith(argument.trim().lowercase()) }

fun parseInput(text: String): Input {
    if (!text.startsWith(":")) return Input.Search(text)
    val body = text.drop(1).trimStart()
    val word = body.substringBefore(' ')
    val exact = Command.entries.firstOrNull { it.word == word }
    if (exact != null && (' ' in body || exact.arg == Arg.NONE)) {
        return Input.Full(exact, body.substringAfter(' ', "").trim())
    }
    return Input.Partial(Command.entries.filter { it.word.startsWith(word) })
}
