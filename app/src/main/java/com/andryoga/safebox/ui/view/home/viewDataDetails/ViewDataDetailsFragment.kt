package com.andryoga.safebox.ui.view.home.viewDataDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils.logResource
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ViewDataDetailsFragment : Fragment() {
    private val viewModel: ViewDataDetailsViewModel by viewModels()
    private val args: ViewDataDetailsFragmentArgs by navArgs()
    private val tagLocal = "view data fragment for"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val id = args.id
                val dataType = args.userDataType
                var map = emptyMap<Int, String?>()

                when (dataType) {
                    UserDataType.LOGIN_DATA -> TODO()
                    UserDataType.BANK_ACCOUNT -> {
                        val data by viewModel.getBankAccountData(id)
                            .observeAsState(initial = Resource.loading(null))
                        logResource("$tagLocal ${dataType.name}", data)
                        val viewData = data.data
                        if (viewData != null) {
                            map = mapOf(
                                R.string.account_number to viewData.accountNumber,
                                R.string.customer_name to viewData.customerName,
                                R.string.customer_id to viewData.customerId,
                                R.string.branch_code to viewData.branchCode,
                                R.string.branch_name to viewData.branchName,
                                R.string.branch_address to viewData.branchAddress,
                                R.string.ifsc_code to viewData.ifscCode,
                                R.string.micr_code to viewData.micrCode,
                                R.string.notes to viewData.notes,
                                R.string.created_on to viewData.creationDate,
                                R.string.updated_on to viewData.updateDate,
                            )
                        }
                        BasicSafeBoxTheme {
                            UserDataView(map, viewData?.title ?: "", data.status)
                        }
                    }
                    UserDataType.BANK_CARD -> TODO()
                    UserDataType.SECURE_NOTE -> TODO()
                }
            }
        }
    }

    @Composable
    fun UserDataField(
        fieldLabelResourceId: Int,
        field: String?
    ) {
        if (field != null) {
            val label = stringResource(id = fieldLabelResourceId).replace("̽", "")
            Text(
                text = label,
                color = MaterialTheme.colors.secondaryVariant,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = field,
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    @Composable
    fun ActionIcon(
        imageVector: ImageVector,
        labelResId: Int,
        contentDescriptionResId: Int,
        iconSize: Dp = 32.dp,
        tint: Color = MaterialTheme.colors.primary
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = imageVector,
                contentDescription = stringResource(id = contentDescriptionResId),
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = stringResource(id = labelResId),
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.body1
            )
        }
    }

    @Composable
    fun UserDataView(
        viewDataMap: Map<Int, String?>,
        title: String,
        status: Status
    ) {
        when (status) {
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
            Status.ERROR -> {
                // FUTURE
            }
            Status.SUCCESS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(state = ScrollState(0))
                ) {
                    // title
                    Text(
                        text = title,
                        color = MaterialTheme.colors.primaryVariant,
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // action row
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        ActionIcon(
                            imageVector = Icons.Filled.Edit,
                            labelResId = R.string.edit,
                            contentDescriptionResId = R.string.cd_action_edit
                        )
                        ActionIcon(
                            imageVector = MaterialIconsCopy.ContentCopyFilled,
                            labelResId = R.string.copy,
                            contentDescriptionResId = R.string.cd_action_copy
                        )
                        ActionIcon(
                            imageVector = MaterialIconsCopy.DeleteForeverFilled,
                            labelResId = R.string.delete,
                            contentDescriptionResId = R.string.cd_action_delete
                        )
                        ActionIcon(
                            imageVector = Icons.Filled.Share,
                            labelResId = R.string.share,
                            contentDescriptionResId = R.string.cd_action_share
                        )
                    }
                    Divider(
                        color = MaterialTheme.colors.primary,
                        startIndent = 8.dp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )

                    // user data in view only mode
                    viewDataMap.forEach { (titleResourceId, value) ->
                        UserDataField(
                            fieldLabelResourceId = titleResourceId,
                            field = value
                        )
                    }
                }
            }
        }
    }
}
