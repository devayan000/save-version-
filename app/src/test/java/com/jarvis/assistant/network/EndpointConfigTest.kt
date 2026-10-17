package com.jarvis.assistant.network

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class EndpointConfigTest {
    @Test fun noDefaultEndpointExists() { assertNull(EndpointConfig.httpEndpoint("", "")); assertNull(EndpointConfig.httpEndpoint("own-key", "")) }
    @Test fun customEndpointWorksWithOrWithoutKey() {
        val url = "https://example.com/chat?prompt={prompt}"
        assertEquals(url, EndpointConfig.httpEndpoint("", url))
        assertEquals(url, EndpointConfig.httpEndpoint("personal-key", url))
        assertTrue(EndpointConfig.usesGet(url))
        assertFalse(EndpointConfig.usesGet("https://example.com/v1/chat/completions"))
    }
    @Test fun unicodeAndQueryCharactersAreEscaped() {
        val text = "বাংলায় বলুন & x=1? # + hello"
        val url = EndpointConfig.promptUrl("https://example.com/chat?prompt=YOUR_PROMPT_HERE", text)
        assertEquals(text, URLDecoder.decode(url.substringAfter("prompt="), "UTF-8"))
        assertFalse(url.substringAfter("prompt=").contains('&'))
    }
    @Test fun preservesQueryAndRejectsUnsafeUrls() {
        assertEquals("https://example.com/chat?model=chat&prompt=hello%20there", EndpointConfig.promptUrl("https://example.com/chat?model=chat&prompt=old", "hello there"))
        assertNotNull(EndpointConfig.validationError("http://example.com/chat"))
        assertNotNull(EndpointConfig.validationError("https://user:password@example.com/chat"))
        assertNull(EndpointConfig.validationError(""))
    }
}
