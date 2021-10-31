package com.andryoga.safebox.ui.view.home.viewDataDetails

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast.LENGTH_SHORT
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Constants.APP_PLAYSTORE_LINK
import com.andryoga.safebox.common.Utils.logResource
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ViewDataDetailsFragment : Fragment() {
    private val viewModel: ViewDataDetailsViewModel by viewModels()
    private val args: ViewDataDetailsFragmentArgs by navArgs()
    private var tagLocal = "view data fragment for"
    private lateinit var clipboardManager: ClipboardManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        clipboardManager =
            requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return ComposeView(requireContext()).apply {
            setContent {
                val id = args.id
                val dataType = args.userDataType
                tagLocal += dataType.name
                Timber.i("in data view fragment, id=$id, type=${dataType.name}")

                when (dataType) {
                    UserDataType.LOGIN_DATA -> {
                        handleLoginDataType(id = id)
                    }
                    UserDataType.BANK_ACCOUNT -> {
                        handleBankAccountDataType(id = id)
                    }
                    UserDataType.BANK_CARD -> {
                        handleBankCardDataType(id = id)
                    }
                    UserDataType.SECURE_NOTE -> {
                        handleSecureNoteDataType(id = id)
                    }
                }
            }
        }
    }

    @Composable
    private fun handleLoginDataType(id: Int) {
        var map = emptyMap<Int, ViewDataProperties>()
        val data by viewModel.getLoginData(id)
            .observeAsState(initial = Resource.loading(null))
        logResource(tagLocal, data)
        val viewData = data.data
        if (viewData != null) {
            map = mapOf(
                R.string.url to ViewDataProperties(viewData.url),
                R.string.password to ViewDataProperties(viewData.password, false),
                R.string.user_id to ViewDataProperties(viewData.userId),
                R.string.notes to ViewDataProperties(viewData.notes, false),
                R.string.created_on to ViewDataProperties(viewData.creationDate, false),
                R.string.updated_on to ViewDataProperties(viewData.updateDate, false),
            )
        }
        BasicSafeBoxTheme {
            UserDataView(map, viewData?.title ?: "", data.status, args.userDataType)
        }
    }

    @Composable
    private fun handleBankAccountDataType(id: Int) {
        var map = emptyMap<Int, ViewDataProperties>()
        val data by viewModel.getBankAccountData(id)
            .observeAsState(initial = Resource.loading(null))
        logResource(tagLocal, data)
        val viewData = data.data
        if (viewData != null) {
            map = mapOf(
                R.string.account_number to ViewDataProperties(viewData.accountNumber),
                R.string.customer_name to ViewDataProperties(viewData.customerName),
                R.string.customer_id to ViewDataProperties(viewData.customerId, false),
                R.string.branch_code to ViewDataProperties(viewData.branchCode),
                R.string.branch_name to ViewDataProperties(viewData.branchName),
                R.string.branch_address to ViewDataProperties(viewData.branchAddress),
                R.string.ifsc_code to ViewDataProperties(viewData.ifscCode),
                R.string.micr_code to ViewDataProperties(viewData.micrCode, false),
                R.string.notes to ViewDataProperties(viewData.notes, false),
                R.string.created_on to ViewDataProperties(viewData.creationDate, false),
                R.string.updated_on to ViewDataProperties(viewData.updateDate, false),
            )
        }
        BasicSafeBoxTheme {
            UserDataView(map, viewData?.title ?: "", data.status, args.userDataType)
        }
    }

    @Composable
    private fun handleBankCardDataType(id: Int) {
        var map = emptyMap<Int, ViewDataProperties>()
        val data by viewModel.getBankCardData(id)
            .observeAsState(initial = Resource.loading(null))
        logResource(tagLocal, data)
        val viewData = data.data
        if (viewData != null) {
            map = mapOf(
                R.string.name to ViewDataProperties(viewData.name),
                R.string.number to ViewDataProperties(viewData.number),
                R.string.pin to ViewDataProperties(viewData.pin, false),
                R.string.cvv to ViewDataProperties(viewData.cvv),
                R.string.expiryDate to ViewDataProperties(viewData.expiryDate),
                R.string.notes to ViewDataProperties(viewData.notes, false),
                R.string.created_on to ViewDataProperties(viewData.creationDate, false),
                R.string.updated_on to ViewDataProperties(viewData.updateDate, false),
            )
        }
        BasicSafeBoxTheme {
            UserDataView(map, viewData?.title ?: "", data.status, args.userDataType)
        }
    }

    @Composable
    private fun handleSecureNoteDataType(id: Int) {
        var map = emptyMap<Int, ViewDataProperties>()
        val data by viewModel.getSecureNoteData(id)
            .observeAsState(initial = Resource.loading(null))
        logResource(tagLocal, data)
        val viewData = data.data
        if (viewData != null) {
            map = mapOf(
                R.string.notes to ViewDataProperties(viewData.notes),
                R.string.created_on to ViewDataProperties(viewData.creationDate, false),
                R.string.updated_on to ViewDataProperties(viewData.updateDate, false),
            )
        }
        BasicSafeBoxTheme {
            UserDataView(map, viewData?.title ?: "", data.status, args.userDataType)
        }
    }

    @Composable
    fun UserDataField(
        fieldLabelResourceId: Int,
        fieldProperties: ViewDataProperties
    ) {
        if (fieldProperties.value != null) {
            val label = stringResource(id = fieldLabelResourceId).replace("̽", "")
            Text(
                text = label,
                color = MaterialTheme.colors.secondaryVariant,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = fieldProperties.value,
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable(
                        indication = rememberRipple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        Timber.i("$label clicked for copy")
                        copyContentToClipboard(label, fieldProperties.value)
                    }
            )
        }
    }

    @Composable
    fun ActionIcon(
        imageVector: ImageVector,
        labelResId: Int,
        contentDescriptionResId: Int,
        iconSize: Dp = 32.dp,
        tint: Color = MaterialTheme.colors.primary,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
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
        viewDataMap: Map<Int, ViewDataProperties>,
        title: String,
        status: Status,
        dataType: UserDataType
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
                    ActionRow(title, dataType, viewDataMap)

                    // user data in view only mode
                    viewDataMap.forEach { (titleResourceId, properties) ->
                        UserDataField(
                            fieldLabelResourceId = titleResourceId,
                            fieldProperties = properties
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionRow(
        title: String,
        dataType: UserDataType,
        viewDataMap: Map<Int, ViewDataProperties>
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ActionIcon(
                imageVector = Icons.Filled.Edit,
                labelResId = R.string.edit,
                contentDescriptionResId = R.string.cd_action_edit
            ) {
                Timber.i("edit action clicked")
                navigateToEditScreen(dataType)
            }
            ActionIcon(
                imageVector = MaterialIconsCopy.ContentCopyFilled,
                labelResId = R.string.copy,
                contentDescriptionResId = R.string.cd_action_copy
            ) {
                Timber.i("copy action clicked")
                copyContent(title, viewDataMap)
            }
            ActionIcon(
                imageVector = MaterialIconsCopy.DeleteForeverFilled,
                labelResId = R.string.delete,
                contentDescriptionResId = R.string.cd_action_delete
            ) {
                Timber.i("delete action clicked")
            }
            ActionIcon(
                imageVector = Icons.Filled.Share,
                labelResId = R.string.share,
                contentDescriptionResId = R.string.cd_action_share
            ) {
                Timber.i("share action clicked")
            }
        }
        Divider(
            color = MaterialTheme.colors.primary,
            startIndent = 8.dp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }

    private fun navigateToEditScreen(dataType: UserDataType) {
        Timber.i("navigating to edit screen for ${dataType.name}")
        findNavController().navigate(
            when (dataType) {
                UserDataType.LOGIN_DATA -> {
                    ViewDataDetailsFragmentDirections.actionViewDataDetailsFragmentToLoginDataFragment(
                        args.id
                    )
                }
                UserDataType.BANK_ACCOUNT -> {
                    ViewDataDetailsFragmentDirections.actionViewDataDetailsFragmentToBankAccountDataFragment(
                        args.id
                    )
                }
                UserDataType.BANK_CARD -> {
                    ViewDataDetailsFragmentDirections.actionViewDataDetailsFragmentToBankCardDataFragment(
                        args.id
                    )
                }
                UserDataType.SECURE_NOTE -> {
                    ViewDataDetailsFragmentDirections.actionViewDataDetailsFragmentToSecureNoteDataFragment(
                        args.id
                    )
                }
            }
        )
    }

    private fun copyContent(userDataTitle: String, viewDataMap: Map<Int, ViewDataProperties>) {
        Timber.i("copying content")
        val content = getCopyableContent(userDataTitle, viewDataMap)
        copyContentToClipboard(getString(R.string.common_data), content)
    }

    private fun getCopyableContent(
        userDataTitle: String,
        viewDataMap: Map<Int, ViewDataProperties>
    ): String {
        Timber.i("making copyable content")
        val dataStringBuffer =
            StringBuffer(userDataTitle).append(":\n---------------\n---------------\n")
        viewDataMap
            .filter { it.value.isCopyable && it.value.value != null }
            .forEach { (titleResourceId, properties) ->
                val propertyTitle = getString(titleResourceId)
                val propertyValue = properties.value
                dataStringBuffer.append("$propertyTitle : $propertyValue\n")
            }
        dataStringBuffer.append(
            "---------------\n---------------\n${
            getString(
                R.string.common_app_playstore_download,
                APP_PLAYSTORE_LINK
            )
            }"
        )
        return dataStringBuffer.toString()
    }

    private fun copyContentToClipboard(contentLabel: String, contentValue: String) {
        val clip = ClipData.newPlainText(contentLabel, contentValue)
        Timber.i("setting primary clip")
        Timber.d("setting primary clip\n label = $contentLabel\n value=$contentValue")
        clipboardManager.setPrimaryClip(clip)
        Timber.i("showing snackbar after setting primary clip")
        Snackbar
            .make(
                requireView(),
                getString(R.string.snackbar_common_copied_to_clipboard, contentLabel),
                LENGTH_SHORT
            )
            .show()
    }
}
