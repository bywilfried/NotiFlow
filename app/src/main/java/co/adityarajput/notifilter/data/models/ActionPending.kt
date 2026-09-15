package co.adityarajput.notifilter.data.models

/** True only when the action's user-facing purpose is to hold a notification for later delivery. */
val Action.hasMeaningfulPendingNotifications: Boolean
    get() = this is Action.DELAY || this is Action.BATCH
