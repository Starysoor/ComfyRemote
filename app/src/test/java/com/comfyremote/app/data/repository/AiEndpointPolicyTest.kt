package com.comfyremote.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointPolicyTest {

    @Test
    fun httpsAndLocalHttpAreNotFlagged() {
        assertFalse(AiEndpointPolicy.isInsecureRemote("https://api.example.com/v1"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://localhost:11434/v1"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://127.0.0.1:11434/v1"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://192.168.1.20:11434/v1"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://10.0.2.2:8188"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://172.20.0.5:8080"))
        assertFalse(AiEndpointPolicy.isInsecureRemote("http://desktop.local:11434"))
    }

    @Test
    fun remoteHttpIsFlagged() {
        assertTrue(AiEndpointPolicy.isInsecureRemote("http://api.example.com/v1"))
        assertTrue(AiEndpointPolicy.isInsecureRemote("http://172.32.0.1:8080"))
        assertTrue(AiEndpointPolicy.isInsecureRemote("http://8.8.8.8"))
    }
}
