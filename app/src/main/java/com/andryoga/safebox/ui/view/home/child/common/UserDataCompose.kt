package com.andryoga.safebox.ui.view.home.child.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme

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
            if (listResource.data.isNullOrEmpty()) {
                EmptyUserData()
            } else {
                val list = listResource.data
                LazyColumn() {
                    items(items = list, key = { it.id }) {
                        UserDataListItem(item = it, onItemClick)
                    }
                }
            }
        }
        Status.ERROR -> {
            // In error state, show a error snackbar : Future feature
        }
    }
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
