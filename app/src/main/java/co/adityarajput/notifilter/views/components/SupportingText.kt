package co.adityarajput.notifilter.views.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.RegexTarget
import co.adityarajput.notifilter.utils.generateRegex
import co.adityarajput.notifilter.utils.getFirst
import co.adityarajput.notifilter.viewmodels.UpsertFilterViewModel

@Composable
fun SupportingText(viewModel: UpsertFilterViewModel, isPrimaryPattern: Boolean) {
    val notification = viewModel.state.values.notification ?: return

    if (viewModel.state.values.regexTarget == RegexTarget.EXPRESSION ||
        viewModel.state.values.regexTarget == RegexTarget.ALL
    ) return

    val title = notification.title
    val content = notification.content
    val context = notification.contextualData
    val channel = notification.channel

    val target = when (viewModel.state.values.regexTarget) {
        RegexTarget.TITLE -> title.getFirst(20)
        RegexTarget.CONTENT -> content.getFirst(20)
        RegexTarget.OR -> "${title.getFirst(20)}' ${stringResource(R.string.or)} '${content.getFirst(20)}"
        RegexTarget.AND -> if (isPrimaryPattern) title.getFirst(20) else content.getFirst(20)
        RegexTarget.CONTEXT -> context.getFirst(40)
        RegexTarget.CHANNEL -> channel.getFirst(40)
        else -> ""
    }

    if (target.isBlank()) return

    val pattern = when (viewModel.state.values.regexTarget) {
        RegexTarget.TITLE -> title.generateRegex()
        RegexTarget.CONTENT -> content.generateRegex()
        RegexTarget.OR -> "${title.generateRegex()}|${content.generateRegex()}"
        RegexTarget.AND -> if (isPrimaryPattern) title.generateRegex() else content.generateRegex()
        RegexTarget.CONTEXT -> context.generateRegex()
        RegexTarget.CHANNEL -> channel.generateRegex()
        else -> ""
    }

    Text(
        buildAnnotatedString {
            append(stringResource(R.string.pattern_supporting, target))
            withLink(
                LinkAnnotation.Clickable(
                    "generate",
                    TextLinkStyles(
                        SpanStyle(
                            MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ) {
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.run {
                            if (isPrimaryPattern) copy(queryPattern = pattern)
                            else copy(secondaryQueryPattern = pattern)
                        },
                    )
                },
            ) {
                append(stringResource(R.string.generate))
            }
        },
    )
}
