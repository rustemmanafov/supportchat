package io.supportchat.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportChatUserTest {
    @Test
    fun userJsonContainsOnlyProvidedFields() {
        val json = SupportChatUser(id = "42", name = "Aysel", attributes = mapOf("plan" to "pro", "orders" to 3)).toJson()
        assertEquals("42", json.getString("id"))
        assertEquals("Aysel", json.getString("name"))
        assertFalse(json.has("email"))
        assertEquals(3, json.getJSONObject("attributes").getInt("orders"))
    }

    @Test
    fun parsesServerTimestamps() {
        assertEquals(0L, Api.parseIso(null))
        assertTrue(Api.parseIso("2030-01-01T00:00:00.000Z") > System.currentTimeMillis())
    }

    @Test
    fun recognisesSupportChatPushPayloads() {
        assertTrue(SupportChat.isSupportChatPush(mapOf("supportchat" to "1", "title" to "Leyla")))
        assertFalse(SupportChat.isSupportChatPush(mapOf("title" to "Other app push")))
    }
}
