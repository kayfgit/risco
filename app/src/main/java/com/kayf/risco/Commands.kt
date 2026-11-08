package com.kayf.risco

/** What a command's argument is picked from: nothing, a set of apps, or the command's own choices. */
enum class Arg { NONE, APP, PINNED, HIDDEN, CHOICE }

enum class Command(val word: String, val arg: Arg, val help: String, val choices: List<String> = emptyList()) {
    PIN("pin", Arg.APP, "add an app to home"),
    UNPIN("unpin", Arg.PINNED, "remove an app from home"),
    HIDE("hide", Arg.APP, "take an app off the rail and search"),
    UNHIDE("unhide", Arg.HIDDEN, "bring a hidden app back"),
    INFO("info", Arg.APP, "open an app's system page"),
    RM("rm", Arg.APP, "uninstall an app"),
    CLOCK("clock", Arg.CHOICE, "where the clock goes, or off", listOf("top", "middle", "bottom", "off")),
    SET("set", Arg.NONE, "open Android settings"),
    DEFAULT("default", Arg.NONE, "choose the default launcher"),
    Q("q", Arg.NONE, "close this"),
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
