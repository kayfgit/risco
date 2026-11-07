package com.kayf.dedo

/** Which apps a command's argument is picked from. */
enum class Arg { NONE, APP, PINNED, HIDDEN }

enum class Command(val word: String, val arg: Arg, val help: String) {
    PIN("pin", Arg.APP, "add an app to home"),
    UNPIN("unpin", Arg.PINNED, "remove an app from home"),
    HIDE("hide", Arg.APP, "take an app off the rail and search"),
    UNHIDE("unhide", Arg.HIDDEN, "bring a hidden app back"),
    INFO("info", Arg.APP, "open an app's system page"),
    RM("rm", Arg.APP, "uninstall an app"),
    SET("set", Arg.NONE, "open Android settings"),
    DEFAULT("default", Arg.NONE, "choose the default launcher"),
    Q("q", Arg.NONE, "close this"),
}

sealed class Input {
    data class Search(val query: String) : Input()
    data class Partial(val matches: List<Command>) : Input()
    data class Full(val command: Command, val argument: String) : Input()
}

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
