package com.andryoga.safebox.ui.view.home.child.common

import android.content.res.Resources
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import timber.log.Timber
import kotlin.math.roundToInt

private val typeToIconMap = mapOf(
    UserDataType.LOGIN_DATA to R.drawable.ic_person_24,
    UserDataType.BANK_ACCOUNT to R.drawable.ic_bank_24,
    UserDataType.BANK_CARD to R.drawable.ic_card_24,
    UserDataType.SECURE_NOTE to R.drawable.ic_key_24
)

private val typeToTextMap = mapOf(
    UserDataType.LOGIN_DATA to R.string.login,
    UserDataType.BANK_ACCOUNT to R.string.bank,
    UserDataType.BANK_CARD to R.string.card,
    UserDataType.SECURE_NOTE to R.string.note
)

const val ACTION_ITEM_SIZE = 56
const val CARD_HEIGHT = 56
const val CARD_OFFSET = 56f

@ExperimentalMaterialApi
@Composable
fun UserDataList(
    listResource: Resource<List<UserListItemData>>,
    onItemClick: (item: UserListItemData) -> Unit
) {
    when (listResource.status) {
        Status.LOADING -> {
            // In loading state, just show a indefinite circular progress in center of screen
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
        Status.SUCCESS -> {
            // In success state, if there was no data then show empty view other show list of data
            val list = listResource.data
            if (list.isNullOrEmpty()) {
                EmptyUserData()
            } else {
                LazyColumn() {
                    items(
                        items = list,
                        key = {
                            it.type.name + it.id
                        }
                    ) { item ->
                        val isRevealed = remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth()) {
                            ActionsRow(
                                actionIconSize = ACTION_ITEM_SIZE.dp,
                                onDelete = {}
                            )
                            DraggableCard(
                                item = item,
                                isRevealed = isRevealed.value,
                                cardHeight = CARD_HEIGHT.dp,
                                cardOffset = CARD_OFFSET.dp(),
                                onExpand = {
                                    isRevealed.value = true
                                },
                                onCollapse = {
                                    isRevealed.value = false
                                },
                                onClick = onItemClick
                            )
                        }
                    }
                }
            }
        }
        Status.ERROR -> {
            // In error state, show a error snackbar : Future feature
        }
    }
}

fun test(revealedCards: MutableSet<String>, s: String): Boolean {
    Timber.i("${revealedCards.contains(s)}")
    return revealedCards.contains(s)
}

@Composable
@Preview(name = "empty view")
fun EmptyUserData() {
    Image(
        painter = painterResource(id = R.drawable.no_result),
        contentDescription = "No result found",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxSize()
    )

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.common_no_result),
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(top = 180.dp)
        )
        Text(
            text = stringResource(id = R.string.common_click_on_plus_to_add_data),
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UserDataListItem(item: UserListItemData, onClick: (item: UserListItemData) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onClick(item) }
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = typeToIconMap.getValue(item.type)),
                contentDescription = "icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colors.secondary, CircleShape)
                    .padding(8.dp)
            )
            Text(
                text = stringResource(id = typeToTextMap.getValue(item.type)),
                color = MaterialTheme.colors.primary
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.h5,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subTitle != null) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = item.subTitle,
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsRow(
    actionIconSize: Dp,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Red)
    ) {
        IconButton(
            modifier = Modifier.size(actionIconSize),
            onClick = {
                onDelete()
            },
            content = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        )
    }
}

const val ANIMATION_DURATION = 500
const val MIN_DRAG = 10

@Composable
fun DraggableCard(
    item: UserListItemData,
    cardHeight: Dp,
    isRevealed: Boolean,
    cardOffset: Float,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onClick: (item: UserListItemData) -> Unit,
) {
    Timber.i("composing ${item.type.name + item.id} as revealed = $isRevealed")
    val offsetX = remember { mutableStateOf(0f) }
    val transitionState = remember {
        MutableTransitionState(isRevealed).apply {
            targetState = !isRevealed
        }
    }
    val transition = updateTransition(transitionState, "cardTransition")
    val cardBgColor by transition.animateColor(
        label = "cardBgColorTransition",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = {
            if (isRevealed) Color.LightGray else Color.White
        }
    )
    val offsetTransition by transition.animateFloat(
        label = "cardOffsetTransition",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = { if (isRevealed) cardOffset - offsetX.value else -offsetX.value },

    )
    val cardElevation by transition.animateDp(
        label = "cardElevation",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = { if (isRevealed) 20.dp else 2.dp }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(cardHeight)
            .offset { IntOffset((offsetX.value + offsetTransition).roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    val original = Offset(offsetX.value, 0f)
                    val summed = original + Offset(x = dragAmount, y = 0f)
                    val newValue = Offset(x = summed.x.coerceIn(0f, cardOffset), y = 0f)
                    if (newValue.x >= MIN_DRAG) {
                        onExpand()
                        return@detectHorizontalDragGestures
                    } else if (newValue.x <= 0) {
                        onCollapse()
                        return@detectHorizontalDragGestures
                    }
                    change.consumePositionChange()
                    offsetX.value = newValue.x
                }
            },
        backgroundColor = cardBgColor,
        shape = RoundedCornerShape(0.dp),
        elevation = cardElevation,
        content = { UserDataListItem(item, onClick) }
    )
}

fun Float.dp(): Float = this * density + 1f

val density: Float
    get() = Resources.getSystem().displayMetrics.density

@Composable
@Preview(name = "item")
private fun item() {
    BasicSafeBoxTheme {
        UserDataListItem(
            item = UserListItemData(
                1,
                "HDFC Bank ajsfnsajfaslfnalfnaasnflasnfasljnflasnflasnflkansflkansflkasnflnalf",
                "xxxxxxxx123",
                UserDataType.BANK_ACCOUNT
            ),
            {}
        )
    }
}
