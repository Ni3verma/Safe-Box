package com.andryoga.safebox.domain.mappers.record

import com.andryoga.safebox.domain.DomainTestFixtures
import com.andryoga.safebox.domain.models.record.RecordType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

class SearchRecordMappersTest {

    @Test
    fun searchSecureNoteData_toRecordListItem_mapsCorrectlyWithNullSubtitle() {
        val searchNote = DomainTestFixtures.createSearchSecureNoteData(
            key = 101,
            title = "Secret Server Keys",
            creationDate = Date(1690000000000L)
        )

        val recordItem = searchNote.toRecordListItem()

        assertThat(recordItem.id).isEqualTo(101)
        assertThat(recordItem.title).isEqualTo("Secret Server Keys")
        assertThat(recordItem.subTitle).isNull()
        assertThat(recordItem.recordType).isEqualTo(RecordType.NOTE)
    }

    @Test
    fun searchBankAccountData_toRecordListItem_mapsAccountNumberToSubtitle() {
        val searchAccount = DomainTestFixtures.createSearchBankAccountData(
            key = 202,
            title = "Global Checking",
            accountNumber = "XXXX5678",
            creationDate = Date(1690000000000L)
        )

        val recordItem = searchAccount.toRecordListItem()

        assertThat(recordItem.id).isEqualTo(202)
        assertThat(recordItem.title).isEqualTo("Global Checking")
        assertThat(recordItem.subTitle).isEqualTo("XXXX5678")
        assertThat(recordItem.recordType).isEqualTo(RecordType.BANK_ACCOUNT)
    }

    @Test
    fun searchBankCardData_toRecordListItem_mapsCardNumberToSubtitle() {
        val searchCard = DomainTestFixtures.createSearchBankCardData(
            key = 303,
            title = "Platinum Credit",
            number = "XXXX4321",
            creationDate = Date(1690000000000L)
        )

        val recordItem = searchCard.toRecordListItem()

        assertThat(recordItem.id).isEqualTo(303)
        assertThat(recordItem.title).isEqualTo("Platinum Credit")
        assertThat(recordItem.subTitle).isEqualTo("XXXX4321")
        assertThat(recordItem.recordType).isEqualTo(RecordType.CARD)
    }

    @Test
    fun searchLoginData_toRecordListItem_mapsUserIdToSubtitle() {
        val searchLogin = DomainTestFixtures.createSearchLoginData(
            key = 404,
            title = "Company Portal",
            userId = "admin@workplace.internal",
            creationDate = Date(1690000000000L)
        )

        val recordItem = searchLogin.toRecordListItem()

        assertThat(recordItem.id).isEqualTo(404)
        assertThat(recordItem.title).isEqualTo("Company Portal")
        assertThat(recordItem.subTitle).isEqualTo("admin@workplace.internal")
        assertThat(recordItem.recordType).isEqualTo(RecordType.LOGIN)
    }
}
