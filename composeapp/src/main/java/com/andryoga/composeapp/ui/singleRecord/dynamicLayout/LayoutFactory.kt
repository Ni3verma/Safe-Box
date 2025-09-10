package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R

object LayoutFactory {
    fun getLoginRecordLayout(): Layout {
        var mapIndex = 0
        return Layout(
            id = LayoutId.LOGIN,
            rows = mapOf(
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.title,
                                isMandatory = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.url,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.user_id,
                                isMandatory = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.password,
                                isPasswordField = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.notes,
                                singleLine = false,
                                minLines = 5
                            )
                        )
                    )
                )
            )
        )
    }

    fun getBankAccountRecordLayout(): Layout {
        var mapIndex = 0
        return Layout(
            id = LayoutId.BANK_ACCOUNT,
            rows = mapOf(
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.title,
                                isMandatory = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.account_number,
                                isMandatory = true,
                                keyboardType = KeyboardType.Number
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.customer_name,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.customer_id,
                            )
                        ),
                        weight = 0.5f
                    ),
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.branch_code,
                            )
                        ),
                        weight = 0.5f
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.branch_name,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.branch_address,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.ifsc_code,
                            )
                        ),
                        weight = 0.5f
                    ),
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.micr_code,
                            )
                        ),
                        weight = 0.5f
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.notes,
                                singleLine = false,
                                minLines = 5
                            )
                        )
                    )
                )
            )
        )
    }

    fun getCardRecordLayout(): Layout {
        var mapIndex = 0
        return Layout(
            id = LayoutId.CARD,
            rows = mapOf(
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.title,
                                isMandatory = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.name,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.number,
                                isMandatory = true,
                                keyboardType = KeyboardType.Number,
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.pin,
                                keyboardType = KeyboardType.Number,
                            )
                        ),
                        weight = 0.5f
                    ),
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.cvv,
                                isPasswordField = true,
                                keyboardType = KeyboardType.Number,
                            )
                        ),
                        weight = 0.5f
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.expiryDate,
                                keyboardType = KeyboardType.Number
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.notes,
                                singleLine = false,
                                minLines = 5
                            )
                        )
                    )
                )
            )
        )
    }

    fun getNoteRecordLayout(): Layout {
        var mapIndex = 0
        return Layout(
            id = LayoutId.NOTE,
            rows = mapOf(
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.title,
                                isMandatory = true
                            )
                        )
                    )
                ),
                mapIndex++ to listOf(
                    Layout.Field(
                        uiState = Layout.Field.UiState(
                            cell = Layout.Field.UiState.Cell(
                                label = R.string.notes,
                                isMandatory = true,
                                singleLine = false,
                                minLines = 5
                            )
                        )
                    )
                )
            )
        )
    }

}