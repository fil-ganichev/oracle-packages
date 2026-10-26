package oracle.packages.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.calcite.CalciteQueryEngineConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.SqlConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;

/**
 * Один embedded server node Ignite для тестов (SQL / {@link IgniteSqlScriptRunner}).
 * SQL — только Calcite ({@code ignite-calcite}), без H2 / {@code ignite-indexing}.
 */
public final class IgniteTestServer {

    static final String SQL_CACHE_NAME = "oracle_packages_ignite_sql";

    private static Ignite ignite;

    private IgniteTestServer() {
    }

    public static synchronized Ignite start() {
        if (ignite != null) {
            return ignite;
        }
        int discoveryPort = freeLocalPort();
        TcpDiscoverySpi discovery = new TcpDiscoverySpi();
        discovery.setLocalPort(discoveryPort);
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:" + discoveryPort));
        discovery.setIpFinder(ipFinder);

        CacheConfiguration<Object, Object> sqlCache = new CacheConfiguration<>(SQL_CACHE_NAME);
        sqlCache.setCacheMode(CacheMode.PARTITIONED);
        sqlCache.setSqlSchema("PUBLIC");

        IgniteConfiguration configuration = new IgniteConfiguration();
        configuration.setIgniteInstanceName("oracle-packages-ignite-test-" + UUID.randomUUID());
        configuration.setDiscoverySpi(discovery);
        configuration.setClientMode(false);
        configuration.setCacheConfiguration(sqlCache);
        configuration.setSqlConfiguration(
                new SqlConfiguration().setQueryEnginesConfiguration(
                        new CalciteQueryEngineConfiguration().setDefault(true)));

        ignite = Ignition.start(configuration);
        return ignite;
    }

    public static synchronized void stop() {
        if (ignite != null) {
            ignite.close();
            ignite = null;
        }
    }

    static IgniteCache<Object, Object> sqlCache(Ignite ignite) {
        return ignite.cache(SQL_CACHE_NAME);
    }

    private static int freeLocalPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate local port for Ignite discovery", e);
        }
    }
}
