package com.andryoga.safebox.service

import android.R
import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.sqlite.db.SimpleSQLiteQuery
import com.andryoga.safebox.common.Utils.isOneOf
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class SafeBoxAutoFillService : AutofillService() {
    companion object Constants {
        const val userId = "userId"
        const val password = "password"
    }

    @Inject
    lateinit var loginDataDaoSecure: LoginDataDaoSecure

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        // Get the structure from the request
        val context: List<FillContext> = request.fillContexts
        val structure: AssistStructure = context[context.size - 1].structure

        val windowNodes: List<AssistStructure.WindowNode> = structure.run {
            (0 until windowNodeCount).map { getWindowNodeAt(it) }
        }

        windowNodes.forEach { windowNode: AssistStructure.WindowNode ->
            val viewNode: AssistStructure.ViewNode? = windowNode.rootViewNode
            val data = parseStructure(viewNode)
            Timber.d("data = $data")

            prepareAutoFillData(data, windowNode, callback)
        }
    }

    private fun prepareAutoFillData(
        data: Map<String, AutofillId?>,
        windowNode: AssistStructure.WindowNode,
        callback: FillCallback
    ) {
        val userIdFillField = data[userId]
        val passwordFillField = data[password]

        if (userIdFillField != null || passwordFillField != null) {
            prepareLoginAutoFillData(userIdFillField, passwordFillField, windowNode, callback)
        }
    }

    private fun prepareLoginAutoFillData(
        userIdFillField: AutofillId?,
        passwordFillField: AutofillId?,
        windowNode: AssistStructure.WindowNode,
        callback: FillCallback
    ) {

        // e.g. windowNode title = com.andryoga.safebox.debug/com.andryoga.safebox.ui.view.MainActivity
        val windowTitle = windowNode.title.toString()
        val dbSearchQuery = getDbSearchQuery(windowTitle)

        CoroutineScope(Dispatchers.IO).launch {
            val loginDataSet =
                loginDataDaoSecure.getDataForAutoFillService(
                    SimpleSQLiteQuery(
                        dbSearchQuery
                    )
                )
            Timber.d("$loginDataSet")

            if (loginDataSet.isNotEmpty()) {
                val fillResponseBuilder = FillResponse.Builder()
                populateFillResponse(
                    loginDataSet,
                    userIdFillField,
                    fillResponseBuilder,
                    passwordFillField
                )
                callback.onSuccess(fillResponseBuilder.build())
            }
        }
    }

    private fun populateFillResponse(
        loginDataSet: List<LoginDataEntity>,
        userIdFillField: AutofillId?,
        fillResponseBuilder: FillResponse.Builder,
        passwordFillField: AutofillId?
    ) {
        loginDataSet.forEach {
            if (userIdFillField != null) {
                val remoteView =
                    RemoteViews(packageName, R.layout.simple_list_item_1)
                remoteView.setTextViewText(R.id.text1, it.title + " user id")

                fillResponseBuilder.addDataset(
                    Dataset.Builder()
                        .setValue(
                            userIdFillField,
                            AutofillValue.forText(it.userId),
                            remoteView
                        ).build()
                )
            }
            if (passwordFillField != null) {
                val remoteView =
                    RemoteViews(packageName, R.layout.simple_list_item_1)
                remoteView.setTextViewText(R.id.text1, it.title + " password")

                fillResponseBuilder.addDataset(
                    Dataset.Builder()
                        .setValue(
                            passwordFillField,
                            AutofillValue.forText(it.password),
                            remoteView
                        ).build()
                )
            }
        }
    }

    private fun getDbSearchQuery(windowTitle: String): String {
        val possibleTitles = windowTitle.split("/")[0]
            .replace("com.", "")
            .replace("android.", "")
            .split(".")
            .filterNot { it.isEmpty() }

        Timber.d("possible titles = $possibleTitles")
        val query = StringBuilder("select * from login_data where ")
        possibleTitles.forEach {
            query.append("title like '%$it%' OR ")
        }

        // remove last extra OR
        val finalQuery =
            query.removeRange(query.length - " OR ".length, query.length).toString() + ";"
        Timber.d("final query = $finalQuery")
        return finalQuery
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        TODO("Not yet implemented")
    }

    private fun parseStructure(viewNode: AssistStructure.ViewNode?): Map<String, AutofillId?> {
        val data = HashMap<String, AutofillId?>()
        traverseNode(viewNode, data)
        return data
    }

    private fun traverseNode(
        viewNode: AssistStructure.ViewNode?,
        data: HashMap<String, AutofillId?>
    ) {
        if (viewNode?.autofillHints?.isNotEmpty() == true) {
            val autoFillHints = viewNode.autofillHints!!
            matchWithAutoFillHints(autoFillHints, data, viewNode)
        } else if (viewNode != null && viewNode.className?.contains("EditText", true) == true) {
            matchWithViewNodeProperties(viewNode, data)
        }

        val children: List<AssistStructure.ViewNode>? =
            viewNode?.run {
                (0 until childCount).map { getChildAt(it) }
            }

        children?.forEach { childNode: AssistStructure.ViewNode ->
            traverseNode(childNode, data)
        }
    }

    private fun matchWithViewNodeProperties(
        viewNode: AssistStructure.ViewNode,
        data: HashMap<String, AutofillId?>
    ) {
        val searchData = viewNode.hint + viewNode.id + viewNode.text + viewNode.idEntry
        if (searchData.isOneOf(
                "username",
                "user_name",
                "userid",
                "user_id"
            )
        ) {
            data[userId] = viewNode.autofillId
            Timber.i("found user id bcz of custom logic")
        } else if (searchData.isOneOf(
                "password", "userPassword", "user_password", "pswrd"
            )
        ) {
            data[password] = viewNode.autofillId
            Timber.i("found pswrd bcz of custom logic")
        }
    }

    private fun matchWithAutoFillHints(
        autoFillHints: Array<out String>,
        data: HashMap<String, AutofillId?>,
        viewNode: AssistStructure.ViewNode
    ) {
        autoFillHints.forEach {
            if (it == View.AUTOFILL_HINT_EMAIL_ADDRESS || it == View.AUTOFILL_HINT_USERNAME) {
                data[userId] = viewNode.autofillId
                Timber.i("found user id bcz of autofill hint")
            } else if (it == View.AUTOFILL_HINT_PASSWORD) {
                data[password] = viewNode.autofillId
                Timber.i("found pswrd bcz of autofill hint")
            }
        }
    }
}
