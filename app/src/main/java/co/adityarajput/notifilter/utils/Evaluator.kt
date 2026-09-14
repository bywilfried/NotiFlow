package co.adityarajput.notifilter.utils

import co.adityarajput.evaluator.Evaluator
import co.adityarajput.evaluator.Function
import co.adityarajput.notifilter.data.models.Notification

fun String.evaluateAgainst(notification: Notification?) =
    Evaluator(
        object : Function("titleMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.title ?: "")).toString()
        },
        object : Function("contentMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.content ?: "")).toString()
        },
        object : Function("contextMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.contextualData ?: "")).toString()
        },
        object : Function("channelMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.channel ?: "")).toString()
        },
        object : Function("tagMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.tag ?: "")).toString()
        },
        object : Function("groupMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.group ?: "")).toString()
        },
        object : Function("categoryMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.category ?: "")).toString()
        },
        object : Function("shortcutMatches", 1) {
            override fun evaluate(arguments: List<String>) =
                (arguments[0].containsMatchIn(notification?.shortcut ?: "")).toString()
        },
    ).evaluate(this).toBooleanStrict()
