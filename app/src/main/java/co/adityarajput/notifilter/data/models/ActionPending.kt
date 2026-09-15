package co.adityarajput.notifilter.data.models

/** True only for actions whose user-facing purpose is to hold a notification for later delivery. */
val Action.hasMeaningfulPendingNotifications: Boolean
    get() = this is Action.DELAY || this is Action.BATCH
