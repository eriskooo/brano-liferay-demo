package com.example.crm.frontend.base.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

class ReadSourceFlagTest {

    private final JedisPool pool = mock(JedisPool.class);
    private final Jedis jedis = mock(Jedis.class);

    @BeforeEach
    void wirePool() {
        when(pool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("Chýbajúci flag = PORTLET (stará session je zdrojom pravdy)")
    void should_returnPortlet_when_flagMissing() {
        assertThat(flag().current()).isEqualTo(ReadSource.PORTLET);
    }

    @Test
    @DisplayName("Hodnota „redis“ (bez ohľadu na veľkosť písmen) prepne čítanie na Redis")
    void should_returnRedis_when_flagSet() {
        when(jedis.get("crm:flags:session-read-source")).thenReturn(" redis ");

        assertThat(flag().current()).isEqualTo(ReadSource.REDIS);
    }

    @Test
    @DisplayName("Neznáma hodnota = PORTLET")
    void should_returnPortlet_when_valueUnknown() {
        when(jedis.get("crm:flags:session-read-source")).thenReturn("oboje");

        assertThat(flag().current()).isEqualTo(ReadSource.PORTLET);
    }

    @Test
    @DisplayName("Nedostupný Redis = PORTLET, request nespadne")
    void should_returnPortlet_when_redisDown() {
        when(pool.getResource()).thenThrow(new JedisConnectionException("down"));

        assertThat(flag().current()).isEqualTo(ReadSource.PORTLET);
    }

    @Test
    @DisplayName("V rámci jedného requestu sa flag číta z Redisu len raz")
    void should_readRedisOnce_when_askedRepeatedlyInOneRequest() {
        ReadSourceFlag flag = flag();

        flag.current();
        flag.current();

        verify(pool, times(1)).getResource();
    }

    private ReadSourceFlag flag() {
        return new ReadSourceFlag(pool, new FakeRequestAwareManager());
    }
}
