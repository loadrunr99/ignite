/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.junits.common.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.events.GridEventType.*;

/**
 * Client-based discovery tests.
 */
public class GridTcpClientDiscoverySelfTest extends GridCommonAbstractTest {
    /** */
    private static final GridTcpDiscoveryIpFinder IP_FINDER = new GridTcpDiscoveryVmIpFinder(true);

    /** */
    private static final AtomicInteger srvIdx = new AtomicInteger();

    /** */
    private static final AtomicInteger clientIdx = new AtomicInteger();

    /** */
    private static Collection<UUID> srvNodeIds;

    /** */
    private static Collection<UUID> clientNodeIds;

    /** */
    private static int clientsPerSrv;

    /** */
    private static CountDownLatch srvJoinedLatch;

    /** */
    private static CountDownLatch srvLeftLatch;

    /** */
    private static CountDownLatch srvFailedLatch;

    /** */
    private static CountDownLatch clientJoinedLatch;

    /** */
    private static CountDownLatch clientLeftLatch;

    /** */
    private static CountDownLatch clientFailedLatch;

    /** {@inheritDoc} */
    @Override protected GridConfiguration getConfiguration(String gridName) throws Exception {
        GridConfiguration cfg = super.getConfiguration(gridName);

        cfg.setLocalHost("127.0.0.1");

        if (gridName.startsWith("server")) {
            GridTcpDiscoverySpi disco = new GridTcpDiscoverySpi();

            disco.setIpFinder(IP_FINDER);

            cfg.setDiscoverySpi(disco);
        }
        else if (gridName.startsWith("client")) {
            GridTcpClientDiscoverySpi disco = new GridTcpClientDiscoverySpi();

            GridTcpDiscoveryVmIpFinder ipFinder = new GridTcpDiscoveryVmIpFinder();

            String addr = new ArrayList<>(IP_FINDER.getRegisteredAddresses()).
                get((clientIdx.get() - 1) / clientsPerSrv).toString();

            if (addr.startsWith("/"))
                addr = addr.substring(1);

            ipFinder.setAddresses(Arrays.asList(addr));

            disco.setIpFinder(ipFinder);

            cfg.setDiscoverySpi(disco);
        }

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        Collection<InetSocketAddress> addrs = IP_FINDER.getRegisteredAddresses();

        if (!F.isEmpty(addrs))
            IP_FINDER.unregisterAddresses(addrs);

        srvIdx.set(0);
        clientIdx.set(0);

        srvNodeIds = new GridConcurrentHashSet<>();
        clientNodeIds = new GridConcurrentHashSet<>();

        clientsPerSrv = 2;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllClients(true);
        stopAllServers(true);

        assert G.allGrids().isEmpty();
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeJoin() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvJoinedLatch = new CountDownLatch(3);
        clientJoinedLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        startClientNodes(1);

        await(srvJoinedLatch);
        await(clientJoinedLatch);

        checkNodes(3, 4);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeLeave() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvLeftLatch = new CountDownLatch(3);
        clientLeftLatch = new CountDownLatch(2);

        attachListeners(3, 3);

        stopGrid("client-2");

        await(srvLeftLatch);
        await(clientLeftLatch);

        checkNodes(3, 2);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeFail() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvFailedLatch = new CountDownLatch(3);
        clientFailedLatch = new CountDownLatch(2);

        attachListeners(3, 3);

        failClient(2);

        await(srvFailedLatch);
        await(clientFailedLatch);

        checkNodes(3, 2);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeJoin() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvJoinedLatch = new CountDownLatch(3);
        clientJoinedLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        startServerNodes(1);

        await(srvJoinedLatch);
        await(clientJoinedLatch);

        checkNodes(4, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeLeave() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvLeftLatch = new CountDownLatch(2);
        clientLeftLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        stopGrid("server-2");

        await(srvLeftLatch);
        await(clientLeftLatch);

        checkNodes(2, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeFail() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvFailedLatch = new CountDownLatch(2);
        clientFailedLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        assert U.<Map>field(G.grid("server-2").configuration().getDiscoverySpi(), "clientMsgWorkers").isEmpty();

        failServer(2);

        await(srvFailedLatch);
        await(clientFailedLatch);

        checkNodes(2, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientReconnect() throws Exception {
        clientsPerSrv = 1;

        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        resetClientIpFinder(2);

        srvFailedLatch = new CountDownLatch(4);
        clientFailedLatch = new CountDownLatch(4);
        srvJoinedLatch = new CountDownLatch(2);
        clientJoinedLatch = new CountDownLatch(2);

        attachListeners(2, 2);

        failServer(2);

        await(srvFailedLatch);
        await(clientFailedLatch);
        await(srvJoinedLatch);
        await(clientJoinedLatch);

        checkNodes(2, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeJoinOneServer() throws Exception {
        startServerNodes(1);

        srvJoinedLatch = new CountDownLatch(1);

        attachListeners(1, 0);

        startClientNodes(1);

        await(srvJoinedLatch);

        checkNodes(1, 1);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeLeaveOneServer() throws Exception {
        startServerNodes(1);
        startClientNodes(1);

        checkNodes(1, 1);

        srvLeftLatch = new CountDownLatch(1);

        attachListeners(1, 0);

        stopGrid("client-0");

        await(srvLeftLatch);

        checkNodes(1, 0);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeFailOneServer() throws Exception {
        startServerNodes(1);
        startClientNodes(1);

        checkNodes(1, 1);

        srvFailedLatch = new CountDownLatch(1);

        attachListeners(1, 0);

        failClient(0);

        await(srvFailedLatch);

        checkNodes(1, 0);
    }

    /**
     * @param idx Index.
     * @throws Exception In case of error.
     */
    private void resetClientIpFinder(int idx) throws Exception {
        GridTcpClientDiscoverySpi disco =
            (GridTcpClientDiscoverySpi)G.grid("client-" + idx).configuration().getDiscoverySpi();

        GridTcpDiscoveryVmIpFinder ipFinder = (GridTcpDiscoveryVmIpFinder)disco.getIpFinder();

        String addr = IP_FINDER.getRegisteredAddresses().iterator().next().toString();

        if (addr.startsWith("/"))
            addr = addr.substring(1);

        ipFinder.setAddresses(Arrays.asList(addr));
    }

    /**
     * @param cnt Number of nodes.
     * @throws Exception In case of error.
     */
    private void startServerNodes(int cnt) throws Exception {
        for (int i = 0; i < cnt; i++) {
            Grid g = startGrid("server-" + srvIdx.getAndIncrement());

            srvNodeIds.add(g.localNode().id());
        }
    }

    /**
     * @param cnt Number of nodes.
     * @throws Exception In case of error.
     */
    private void startClientNodes(int cnt) throws Exception {
        for (int i = 0; i < cnt; i++) {
            Grid g = startGrid("client-" + clientIdx.getAndIncrement());

            clientNodeIds.add(g.localNode().id());
        }
    }

    /**
     * @param idx Index.
     */
    private void failServer(int idx) {
        ((GridTcpDiscoverySpi)G.grid("server-" + idx).configuration().getDiscoverySpi()).simulateNodeFailure();
    }

    /**
     * @param idx Index.
     */
    private void failClient(int idx) {
        ((GridTcpClientDiscoverySpi)G.grid("client-" + idx).configuration().getDiscoverySpi()).simulateNodeFailure();
    }

    /**
     * @param srvCnt Number of server nodes.
     * @param clientCnt Number of client nodes.
     */
    private void attachListeners(int srvCnt, int clientCnt) throws Exception {
        if (srvJoinedLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Joined event fired on server: " + evt);

                        srvJoinedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_JOINED);
            }
        }

        if (srvLeftLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Left event fired on server: " + evt);

                        srvLeftLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_LEFT);
            }
        }

        if (srvFailedLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Failed event fired on server: " + evt);

                        srvFailedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_FAILED);
            }
        }

        if (clientJoinedLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Joined event fired on client: " + evt);

                        clientJoinedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_JOINED);
            }
        }

        if (clientLeftLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Left event fired on client: " + evt);

                        clientLeftLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_LEFT);
            }
        }

        if (clientFailedLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Failed event fired on client: " + evt);

                        clientFailedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_FAILED);
            }
        }
    }

    /**
     * @param srvCnt Number of server nodes.
     * @param clientCnt Number of client nodes.
     */
    private void checkNodes(int srvCnt, int clientCnt) {
        for (int i = 0; i < srvCnt; i++) {
            Grid g = G.grid("server-" + i);

            assertTrue(srvNodeIds.contains(g.localNode().id()));

            assertFalse(g.localNode().isClient());

            checkRemoteNodes(g, srvCnt + clientCnt - 1);
        }

        for (int i = 0; i < clientCnt; i++) {
            Grid g = G.grid("client-" + i);

            assertTrue(clientNodeIds.contains(g.localNode().id()));

            assertTrue(g.localNode().isClient());

            checkRemoteNodes(g, srvCnt + clientCnt - 1);
        }
    }

    /**
     * @param grid Grid.
     * @param expCnt Expected nodes count.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    private void checkRemoteNodes(Grid grid, int expCnt) {
        Collection<GridNode> nodes = grid.forRemotes().nodes();

        assertEquals(expCnt, nodes.size());

        for (GridNode node : nodes) {
            UUID id = node.id();

            if (clientNodeIds.contains(id))
                assertTrue(node.isClient());
            else if (srvNodeIds.contains(id))
                assertFalse(node.isClient());
            else
                assert false : "Unexpected node ID: " + id;
        }
    }

    /**
     * @param latch Latch.
     * @throws InterruptedException If interrupted.
     */
    private void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5000, MILLISECONDS));
    }
}
