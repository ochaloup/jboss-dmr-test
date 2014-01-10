package ochaloup.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

import org.apache.log4j.Logger;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Some client model controller things for invocation - for testing.
 */
public class Utils {
  private static final Logger log = Logger.getLogger(Utils.class);
  private static ModelControllerClient client;

  // composite operations
  private static boolean IS_BATCH_MODE = false;
  private static ModelNode BATCH = null;

  private static final String systemPropertyPath = "/system-property=";

  public static synchronized ModelControllerClient getClient() {
    if (client == null) {
      client = createClient();
    }
    return client;
  }

  public static synchronized void closeClient() {
    if (client != null) {
      try {
        client.close();
      } catch (IOException ioe) {
        log.error("Not able to close client!");
      }
    }
  }

  private static ModelControllerClient createClient() {
    // TODO: not hardcoded address and port :)
    try {
      ModelControllerClient client = ModelControllerClient.Factory.create(
          InetAddress.getByName("127.0.0.1"), 9999);
      return client;
    } catch (UnknownHostException uhe) {
      throw new RuntimeException(uhe);
    }
  }

  /**
   * Address in "standart" format: /subsystem=web/something=something/...
   * 
   * @return list of PathElements which could be passed to PathAddress class
   */
  public static List<PathElement> parseAddress(String address) {
    String[] splitAddrParts = address.split("/");
    List<PathElement> elements = new ArrayList<PathElement>();

    for (String addrPart : splitAddrParts) {
      addrPart = addrPart.trim();
      if (!addrPart.isEmpty()) {
        String[] nameVal = addrPart.split("=");
        if (nameVal.length == 1) {
          elements.add(PathElement.pathElement(addrPart));
        } else if (nameVal.length == 2) {
          elements.add(PathElement.pathElement(nameVal[0], nameVal[1]));
        } else {
          throw new RuntimeException("There is problem with element "
              + addrPart + " for address: " + address + ". Not able to parse.");
        }
      }
    }

    return elements;
  }

  public static ModelNode runOperation(String address, String operationName) {
    PathAddress pa = PathAddress.pathAddress(parseAddress(address));
    ModelNode mdAddress = pa.toModelNode();

    ModelNode operation = new ModelNode();
    operation.get(OP_ADDR).set(mdAddress);
    operation.get(OP).set(operationName);

    operation.get(INCLUDE_DEFAULTS).set(true);
    // workaround for isDefined would work
    operation.get(RECURSIVE).set(true);

    return executeOperation(operation);
  }

  /**
   * Checking existence of something on the specified address
   * 
   * @param address
   *          - use like /subsystem=datasources
   * @param checkPath
   *          use like [data-source, ExampleDS]
   */
  public static boolean isDefined(String address, String[] checkPath) {
    PathAddress pa = PathAddress.pathAddress(parseAddress(address));
    ModelNode mdAddress = pa.toModelNode();

    ModelNode operation = new ModelNode();
    operation.get(OP_ADDR).set(mdAddress);
    operation.get(OP).set("read-resource");

    operation.get(INCLUDE_DEFAULTS).set(true);
    // workaround for isDefined would work (bz1005131)
    operation.get(RECURSIVE).set(true);

    ModelNode resultCheck = executeOperation(operation);
    resultCheck = getResult(resultCheck);

    for (String checkStr : checkPath) {
      if (!resultCheck.isDefined()) {
        return false;
      }
      resultCheck.get(checkStr);
    }
    return true;
  }

  // op.get("recursive").set(true);
  // op.get("operations").set(true);
  public static ModelNode readAttr(String address, String attrName) {
    PathAddress pa = PathAddress.pathAddress(parseAddress(address));
    ModelNode mdAddress = pa.toModelNode();

    ModelNode operation = new ModelNode();
    operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
    operation.get(NAME).set(attrName);
    operation.get(OP_ADDR).set(mdAddress);
    operation.get(RECURSIVE).set(true);

    return executeOperation(operation);
  }

  public static ModelNode writeAttr(String address, String attrName,
      String attrValue) {
    PathAddress pa = PathAddress.pathAddress(parseAddress(address));
    ModelNode mdAddress = pa.toModelNode();

    ModelNode operation = new ModelNode();
    operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
    operation.get(NAME).set(attrName);
    operation.get(VALUE).set(attrValue);
    operation.get(OP_ADDR).set(mdAddress);

    return executeOperation(operation);
  }

  public static ModelNode executeOperation(final ModelNode op, boolean unwrapResult) {

    if (IS_BATCH_MODE) {
      // adding to batch
      addToBatch(op);
      log.info("Operation " + op.asString()
          + " was added to batch (was not executed)");
      ModelNode retCode = new ModelNode();
      retCode.get(OUTCOME).set(SUCCESS);
      retCode.get(RESULT).set("NO EXECUTION! The op was added to batch");
      return retCode;
    } else {
      ModelNode ret = null;
      try {
        // operation execution
        ret = getClient().execute(op);
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
      if (!unwrapResult)
        return ret;

      if (SUCCESS.equals(ret.get(OUTCOME).asString())) {
        log.info("Succesful management operation " + op + " with result " + ret);
      }

      if (!SUCCESS.equals(ret.get(OUTCOME).asString())) {
        log.error("Management operation " + op + " failed: " + ret);
        
        // LET's ignore duplicated resource
        if(!ret.get(FAILURE_DESCRIPTION).asString().contains("Duplicate resource")) {
          throw new RuntimeException("Our! management operation failed: "
              + ret.get(FAILURE_DESCRIPTION) + op + ret);
        } else {
          log.error("Error occured but was ignored ;)");
        }
      }

      return ret.get(RESULT);
    }
  }

  public static ModelNode executeOperation(final ModelNode op) {
    return executeOperation(op, true);
  }

  public static ModelNode setAttribute(ModelNode addr, String attrName,
      String attrValue) throws Exception {
    return setAttribute(addr, attrName, attrValue, false);
  }

  public static ModelNode setAttribute(ModelNode addr, String attrName, String attrValue, boolean isLog) {
    ModelNode op = new ModelNode();
    op = new ModelNode();
    op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
    op.get(OP_ADDR).set(addr);
    op.get(NAME).set(attrName);
    op.get(VALUE).set(attrValue);

    if (isLog) {
      log.info("Operation: " + op);
    }

    ModelNode result = executeOperation(op, true);

    if (isLog) {
      String logstring = "";
      if (result.has("outcome")) {
        logstring += "outcome: " + result.get("outcome") + ", ";
      }
      log.info("Operation: " + logstring + "result: " + result);
    }
    return result;
  }

  // ----------------------------------------------------------------
  // --------------------------- BATCH ------------------------------
  // ----------------------------------------------------------------
  public static void startBatch() {
    if (IS_BATCH_MODE) {
      throw new RuntimeException(
          "Batch is already running. First cancel the current batch and then create a new one");
    }

    BATCH = new ModelNode();
    BATCH.get(OP).set(COMPOSITE);
    BATCH.get(OP_ADDR).setEmptyList();
    IS_BATCH_MODE = true;
  }

  public static void addToBatch(ModelNode operationToBatch) {
    if (!IS_BATCH_MODE || BATCH == null) {
      throw new RuntimeException(
          "No active batch. First start a batch then add operations");
    }

    BATCH.get(STEPS).add(operationToBatch);
  }

  public static void runBatch() {
    if (!IS_BATCH_MODE || BATCH == null) {
      throw new RuntimeException(
          "No active batch. First start a batch then you can run it");
    }

    IS_BATCH_MODE = false;
    executeOperation(BATCH);
    BATCH = null;
  }

  public static void cancelBatch() {
    IS_BATCH_MODE = false;
    BATCH = null;
  }

  public static ModelNode addSystemProperty(String name, String value) {
    // /system-property=foo:add(value=bar)
    String addressString = systemPropertyPath + name;
    PathAddress pa = PathAddress.pathAddress(parseAddress(addressString));
    ModelNode mdAddress = pa.toModelNode();

    ModelNode operation = new ModelNode();
    operation.get(OP).set(ADD);
    operation.get(NAME).set("value");
    operation.get(VALUE).set(value);
    operation.get(OP_ADDR).set(mdAddress);

    return executeOperation(operation);
  }

  public static ModelNode getResult(ModelNode modelNodeResult) {
    if (!SUCCESS.equals(modelNodeResult.get(OUTCOME).asString())) {
      log.error("The outcome of the result is was not succesful: "
          + modelNodeResult.toString());
    }
    return modelNodeResult.get(RESULT);
  }

  public static boolean reload() throws IOException {
    /* :reload() */
    final ModelNode operation = new ModelNode();
    operation.get(OP).set("reload");
    log.info("operation=" + operation);

    return executeServerReininitalization(operation);
  }

  public static boolean restart() throws IOException {
    /* :shutdwon(restart=true) */
    final ModelNode operation = new ModelNode();
    operation.get(OP).set("shutdown");
    operation.get("restart").set("true");
    log.info("operation=" + operation);

    return executeServerReininitalization(operation);
  }

  private static boolean executeServerReininitalization(ModelNode operation)
      throws IOException {
    try {
      executeOperation(operation);
    } catch (Exception e) {
      log.error(
          "Exception applying shutdown operation. This is probably fine, as the server probably shut down before the response was sent",
          e);
    }
    boolean reloaded = false;
    int i = 0;
    while (!reloaded) {
      try {
        Thread.sleep(2000);
        if (isServerInRunningState()) {
          reloaded = true;
          log.info("Server was sucessfully restarted/reloaded");
        }
      } catch (Throwable t) {
        // nothing to do, just waiting
      } finally {
        if (!reloaded && i++ > 20) {
          throw new RuntimeException("Server reloading failed");
        }
      }
    }
    return reloaded;
  }

  public static boolean isServerInRunningState() {
    try {
      ModelNode op = new ModelNode();
      op.get(OP).set(READ_ATTRIBUTE_OPERATION);
      op.get(OP_ADDR).setEmptyList();
      op.get(NAME).set("server-state");

      ModelNode rsp = client.execute(op);
      return SUCCESS.equals(rsp.get(OUTCOME).asString())
          && !"starting".equals(rsp.get(RESULT).asString())
          && !"stopping".equals(rsp.get(RESULT).asString());
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static ModelNode getOutboundSocketBindingAddr(String name) {
    final ModelNode addr = new ModelNode();
    addr.add(SOCKET_BINDING_GROUP, "standard-sockets");
    addr.add("remote-destination-outbound-socket-binding", name);
    return addr;
  }

  public static ModelNode addOutboundSocketBinding(String name, String host,
      String port) {
    ModelNode addr = getOutboundSocketBindingAddr(name);

    ModelNode op = new ModelNode();
    op = new ModelNode();
    op.get(OP).set(ADD);
    op.get(OP_ADDR).set(addr);

    op.get("host").set(host);
    op.get("port").set(port);

    return executeOperation(op);
  }

  private static ModelNode getRemoteNettyConnectorAddr(String connectorName) {
    final ModelNode addr = new ModelNode();
    addr.add(SUBSYSTEM, "messaging");
    addr.add("hornetq-server", "default");
    addr.add("remote-connector", connectorName);
    return addr;
  }

  public static ModelNode addRemoteNettyConnector(String connectorName,
      String socketBindingName) {
    ModelNode op = new ModelNode();
    op.get(OP).set(ADD);
    op.get(OP_ADDR).set(getRemoteNettyConnectorAddr(connectorName));

    op.get("socket-binding").set(socketBindingName);

    return executeOperation(op);
  }

  private static ModelNode getPooledConnectionFactoryAddr(String connectionFactoryName) {
    final ModelNode addr = new ModelNode();
    addr.add("subsystem", "messaging");
    addr.add("hornetq-server", "default");
    addr.add("pooled-connection-factory", connectionFactoryName);
    return addr;
  }

  /**
   * Sets connector on pooled connection factory transaction=xa,
   * entries={{java:jmsXA3}}, connector={["netty"]}, ha=true)
   * 
   * /subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra-2:add(entries={[java:/jmsXAA]}, connector={"netty" => undefined})
   * 
   * @param connectionFactoryName
   *          name of the pooled connection factory like "hornetq-ra"
   * @param connectorName
   *          name of the connector like "remote-connector"
   * @throws MgmtOperationException
   * @throws IOException
   */
  public static void addPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
    ModelNode op = new ModelNode();   
    op.get(OP_ADDR).set(getPooledConnectionFactoryAddr(connectionFactoryName));
    op.get(OP).set(ADD);
    /*
     * or address definition could be written like
     * model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
     * model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
     * model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
     */

    op.get("transaction").set("xa");
    op.get("entries").add(jndiName);

    op.get("name").set("connector");
    ModelNode opnew = new ModelNode();
    opnew.get(connectorName).clear();
    op.get("connector").set(opnew);

    executeOperation(op);
  }
}
