package com.kayf.risco

/**
 * What a command's argument is picked from: nothing, a set of apps, the installed widgets, or the
 * command's own choices.
 */
enum class Arg { NONE, APP, PINNED, HIDDEN, WIDGET, CHOICE }

enum class Command(val word: String, val arg: Arg, val help: String, val choices: List<String> = emptyList()) {
    PIN("pin", Arg.APP, "Add an app to home"),
    UNPIN("unpin", Arg.PINNED, "Remove an app or shortcut from home"),
    HIDE("hide", Arg.APP, "Hide an app from the rail and search"),
    UNHIDE("unhide", Arg.HIDDEN, "Bring back a hidden app"),
    INFO("info", Arg.APP, "Open an app's info page"),
    RM("rm", Arg.APP, "Uninstall an app"),
    ICONS("icons", Arg.CHOICE, "Show or hide app icons", listOf("on", "off")),
    MENU("menu", Arg.NONE, "Open the home menu"),
    WALLPAPER("wallpaper", Arg.NONE, "Change the wallpaper"),
    WIDGET("widget", Arg.WIDGET, "Add a widget to home"),
    SET("set", Arg.NONE, "open Android settings"),
    DEFAULT("default", Arg.NONE, "Choose your home app"),
    Q("q", Arg.NONE, "Close search"),
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
