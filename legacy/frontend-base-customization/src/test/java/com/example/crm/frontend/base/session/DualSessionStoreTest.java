package com.example.crm.frontend.base.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DualSessionStoreTest {

    private final SessionStore primary = mock(SessionStore.class);
    private final SessionStore shared = mock(SessionStore.class);
    private final ReadSourceFlag flag = mock(ReadSourceFlag.class);
    private final DualSessionStore store = new DualSessionStore(primary, shared, flag);

    @Test
    @DisplayName("Zápis ide do oboch úložísk")
    void should_writeToBoth_when_put() {
        store.put("APP_X", "1");

        verify(primary).put("APP_X", "1");
        verify(shared).put("APP_X", "1");
    }

    @Test
    @DisplayName("Zlyhanie Redisu pri zápise request nezhodí, stará session je zapísaná")
    void should_keepPrimaryWrite_when_sharedWriteFails() {
        doThrow(new RuntimeException("redis down")).when(shared).put("APP_X", "1");

        store.put("APP_X", "1");

        verify(primary).put("APP_X", "1");
    }

    @Test
    @DisplayName("Flag PORTLET: číta sa zo starej session, Redis sa nepýta")
    void should_readPrimary_when_flagPortlet() {
        when(flag.current()).thenReturn(ReadSource.PORTLET);
        when(primary.get("APP_X")).thenReturn("stará");

        assertThat(store.get("APP_X")).isEqualTo("stará");
        verify(shared, never()).get("APP_X");
    }

    @Test
    @DisplayName("Flag REDIS: číta sa z Redisu")
    void should_readShared_when_flagRedis() {
        when(flag.current()).thenReturn(ReadSource.REDIS);
        when(shared.get("APP_X")).thenReturn("redis");

        assertThat(store.get("APP_X")).isEqualTo("redis");
    }

    @Test
    @DisplayName("Flag REDIS a Redis dole: fallback na starú session")
    void should_fallBackToPrimary_when_sharedReadFails() {
        when(flag.current()).thenReturn(ReadSource.REDIS);
        when(shared.get("APP_X")).thenThrow(new RuntimeException("redis down"));
        when(primary.get("APP_X")).thenReturn("stará");

        assertThat(store.get("APP_X")).isEqualTo("stará");
    }

    @Test
    @DisplayName("remove a clear idú do oboch, zlyhanie Redisu sa len zaloguje")
    void should_removeAndClearBoth_when_called() {
        doThrow(new RuntimeException("redis down")).when(shared).clear();

        store.remove("APP_X");
        store.clear();

        verify(primary).remove("APP_X");
        verify(shared).remove("APP_X");
        verify(primary).clear();
    }
}
