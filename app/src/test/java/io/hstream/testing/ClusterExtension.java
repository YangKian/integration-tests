package io.hstream.testing;

import static io.hstream.testing.TestUtils.makeHServer;
import static io.hstream.testing.TestUtils.makeHStore;
import static io.hstream.testing.TestUtils.makeZooKeeper;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame.OutputType;

public class ClusterExtension implements BeforeEachCallback, AfterEachCallback {

  private final GenericContainer<?>[] hservers = new GenericContainer[5];
  private Path dataDir;
  private GenericContainer<?> zk;
  private GenericContainer<?> hstore;

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    dataDir = Files.createTempDirectory("hstream");
    Network.NetworkImpl network = Network.builder().build();

    zk = makeZooKeeper(network);
    zk.start();
    String zkHost =
        zk.getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .get(network.getName())
            .getIpAddress();
    System.out.println("[DEBUG]: zkHost: " + zkHost);

    hstore = makeHStore(network, dataDir);
    hstore.start();
    String hstoreHost =
        hstore
            .getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .get(network.getName())
            .getIpAddress();
    System.out.println("[DEBUG]: hstoreHost: " + hstoreHost);

    for (int i = 0; i < 5; i++) {
      hservers[i] = makeHServer(network, dataDir, zkHost, hstoreHost, i);
      hservers[i].start();

      Thread.sleep(1000);
      String logs = hservers[0].getLogs();
      System.out.println(logs.substring(logs.indexOf("rank")));
    }

    Object testInstance = context.getRequiredTestInstance();
    testInstance
        .getClass()
        .getMethod("setHStreamDBUrl", String.class)
        .invoke(testInstance, "127.0.0.1:6570");
    testInstance
        .getClass()
        .getMethod("setHServers", GenericContainer[].class)
        .invoke(testInstance, (Object) hservers);
    Thread.sleep(5000);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    for (int i = 0; i < 5; i++) {
      System.out.println(hservers[i].getLogs(OutputType.STDOUT));
      hservers[i].close();
    }
    hstore.close();
    zk.close();

    for (int i = 0; i < 5; i++) {
      hservers[i] = null;
    }
    hstore = null;
    System.out.println(zk.getLogs(OutputType.STDOUT));
    zk = null;
    dataDir = null;
  }
}