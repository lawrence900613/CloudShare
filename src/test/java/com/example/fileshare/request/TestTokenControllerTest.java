package com.example.fileshare.request;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class TestTokenControllerTest {

    @Test
    void me_returnsGreetingWithAuthenticatedUserName() {
        TestTokenController controller = new TestTokenController();
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice@example.com");

        String result = controller.me(auth);
        Assertions.assertEquals("hello alice@example.com", result);
    }
}
