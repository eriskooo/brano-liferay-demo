package com.example.crm.frontend.base.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class RedisSessionStoreTest {

    private final JedisPool pool = mock(JedisPool.class);
    private final Jedis jedis = mock(Jedis.class);
    private final SharedKeys keys = new SharedKeys();

    @BeforeEach
    void wirePool() {
        when(pool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("Zdieľaný kľúč zapíše ako JSON do hashu podľa SSO_ID a predĺži TTL")
    void should_writeJsonUnderSsoHash_when_sharedKeyPut() {
        store("s-1").put(SharedKeys.MESSAGES, Collections.singletonList(new UiMessage(UiMessage.Level.INFO, "k")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(jedis).hset(eq("crm:session:s-1"), eq("APP_MESSAGES"), json.capture());
        assertThat(json.getValue()).contains("\"level\":\"INFO\"").contains("\"key\":\"k\"");
        verify(jedis).expire("crm:session:s-1", 1800);
    }

    @Test
    @DisplayName("Prečíta JSON späť do typu z registra kľúčov")
    void should_readTypedValue_when_jsonStored() {
        when(jedis.hget("crm:session:s-1", "APP_MESSAGES")).thenReturn("[{\"level\":\"ERROR\",\"key\":\"x\"}]");

        Object value = store("s-1").get(SharedKeys.MESSAGES);

        assertThat(value).isEqualTo(Arrays.asList(new UiMessage(UiMessage.Level.ERROR, "x")));
    }

    @Test
    @DisplayName("Kľúč mimo registra sa do Redisu nezapíše (zdieľa sa len to, čo je dohodnuté)")
    void should_skipRedis_when_keyNotShared() {
        store("s-1").put("APP_LOCAL_ONLY", "x");

        verifyNoInteractions(pool);
    }

    @Test
    @DisplayName("Bez SSO session sa Redis nevolá")
    void should_doNothing_when_noSsoSession() {
        RedisSessionStore store = store(null);

        store.put(SharedKeys.MESSAGES, Collections.emptyList());

        assertThat(store.get(SharedKeys.MESSAGES)).isNull();
        verifyNoInteractions(pool);
    }

    @Test
    @DisplayName("Typy ďalších kľúčov sa registrujú konfiguráciou (Spring XML)")
    void should_useConfiguredType_when_keyRegistered() {
        keys.setTypes(Collections.<String, Class<?>>singletonMap("APP_LAST", String.class));
        when(jedis.hget("crm:session:s-1", "APP_LAST")).thenReturn("\"abc\"");

        assertThat(store("s-1").get("APP_LAST")).isEqualTo("abc");
        assertThat(keys.isShared("APP_LAST")).isTrue();
    }

    @Test
    @DisplayName("null hodnota kľúč z hashu odstráni, clear() zmaže celý hash")
    void should_deleteFieldAndHash_when_nullPutAndClear() {
        RedisSessionStore store = store("s-1");

        store.put(SharedKeys.MESSAGES, null);
        store.clear();

        verify(jedis).hdel("crm:session:s-1", "APP_MESSAGES");
        verify(jedis).del("crm:session:s-1");
    }

    private RedisSessionStore store(String ssoId) {
        FakeRequestAwareManager request = new FakeRequestAwareManager();
        if (ssoId != null) {
            request.withSsoSessionId(ssoId);
        }
        return new RedisSessionStore(pool, request, keys);
    }
}
