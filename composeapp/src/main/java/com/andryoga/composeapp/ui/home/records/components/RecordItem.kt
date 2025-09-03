package com.andryoga.composeapp.ui.home.records.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.home.records.RecordListItem
import com.andryoga.composeapp.ui.previewHelper.getBankAccountRecordItem
import com.andryoga.composeapp.ui.previewHelper.getCardRecordItem
import com.andryoga.composeapp.ui.previewHelper.getLoginRecordItem
import com.andryoga.composeapp.ui.previewHelper.getNoteRecordItem

@Composable
fun RecordItem(item: RecordListItem) {
    val icon: ImageVector = when (item.type) {
        RecordListItem.Type.LOGIN -> Icons.AutoMirrored.Filled.Login
        RecordListItem.Type.CARD -> Icons.Filled.CreditCard
        RecordListItem.Type.BANK_ACCOUNT -> Icons.Filled.AccountBalance
        RecordListItem.Type.NOTE -> Icons.AutoMirrored.Filled.Note
    }

    val typeText: String = when (item.type) {
        RecordListItem.Type.LOGIN -> stringResource(R.string.login)
        RecordListItem.Type.CARD -> stringResource(R.string.card)
        RecordListItem.Type.BANK_ACCOUNT -> stringResource(R.string.bank)
        RecordListItem.Type.NOTE -> stringResource(R.string.note)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon, contentDescription = null, modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary, shape = CircleShape
                        )
                        .padding(8.dp)
                        .size(40.dp), tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.size(16.dp))
            Column(
                verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Text(
                    text = item.subTitle ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }

            Text(
                text = typeText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(top = 4.dp, start = 30.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(start = 8.dp, end = 8.dp)
            )
        }
    }
}


@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoginRecordItemPreview() {
    RecordItem(item = getLoginRecordItem())
}

@Preview
@Composable
private fun BankAccountRecordItemPreview() {
    RecordItem(item = getBankAccountRecordItem())
}

@Preview
@Composable
private fun CardRecordItemPreview() {
    RecordItem(item = getCardRecordItem())
}

@Preview
@Composable
private fun NoteRecordItemPreview() {
    RecordItem(item = getNoteRecordItem())
}

@Preview
@Composable
private fun LongRecordItemPreview() {
    RecordItem(
        item = RecordListItem(
            1,
            "long titletitletitletitletitletitle titletitletitletitle",
            "long subtitle subtitle subtitle subtitle  subtitle",
            RecordListItem.Type.LOGIN
        )
    )
}