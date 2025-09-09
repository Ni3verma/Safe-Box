package com.andryoga.composeapp.ui.record.dynamicLayout

import org.junit.Before
import org.junit.Test


// todo: validate all scenarios for layout here
class LayoutFactoryTest {
    @Before
    fun setUp() {

    }

    @Test
    fun getLoginRecordLayout() {
        val layout = LayoutFactory.getLoginRecordLayout()
        assert(layout.id == LayoutId.LOGIN)
    }
}