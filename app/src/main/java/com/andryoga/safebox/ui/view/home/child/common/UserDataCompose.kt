package com.andryoga.safebox.ui.view.home.child.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
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
fun UserDataList(list: List<UserDataAdapterEntity>) {
    LazyColumn() {
        items(items = list, key = { it.id }) {
            UserDataListItem(item = it)
        }
    }
}

@Composable
fun UserDataListItem(item: UserDataAdapterEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically
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
                    .size(60.dp)
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
                    style = MaterialTheme.typography.body2,
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
            item = UserDataAdapterEntity(
                1,
                "HDFC Bank ajsfnsajfaslfnalfnaasnflasnfasljnflasnflasnflkansflkansflkasnflnalf",
                "xxxxxxxx123",
                UserDataType.BANK_ACCOUNT
            )
        )
    }
}
