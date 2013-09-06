package ochaloup;

import ochaloup.util.Utils;

import org.apache.log4j.Logger;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * Let's test some operations
 */
public class OperationsMain {
	private static final Logger log = Logger.getLogger(OperationsMain.class);
	
	// private static final String propertyName = "jboss.bind.address";

	public static void main(String[] args) {
		try {
			// ModelNode addPropertyResult = Utils.addSystemProperty(propertyName, "localhost");
			// log.info("system property " + propertyName + " add - result: " + addPropertyResult);

			// ModelNode result = readAttr("/interface=unsecure", "inet-address");
			// ModelNode result = readAttr("/interface=public", "inet-address");
			// showMeResults(result);

			// ModelNode result = readAttr("/system-property=" + propertyName, "value");
			// log.info("system property foo result: " + result);
			// log.info("system property foo result type: " +
			// result.getType());
			// log.info("system property foo resolved: " +
			// result.resolve());

			// read-resource data check
			String address = "/subsystem=datasources";
			String oper = "read-resource";
			ModelNode readResource = Utils.runOperation(address, oper);
			ModelNode readResourceResult = Utils.getResult(readResource);
			showMeResults(readResourceResult);
			// log.info("resourceees: " + readResource.get("result").get("data-source").get("ExampleDS").isDefined()); // hmm... returning false - why?
			// log.info("resourceees: " + readResource.get("result").get("data-source").isDefined());
			log.info("resourceees: " + readResourceResult.get("data-source").get("ExampleDS").isDefined());
			
			log.info(Utils.isDefined("/subsystem=datasources", new String[]{"data-source", "ExampleDS"}));
			
			CLI cli = CLI.newInstance();
			cli.connect();
			// do st.
			cli.disconnect();
			
		} finally {
			Utils.closeClient();
		}
	}

	
	@SuppressWarnings("unused")
	private static ModelNode readAttr(String addr, String attr) {
		ModelNode result = Utils.readAttr(addr, attr);
		return Utils.getResult(result);
	}

	@SuppressWarnings("unused")
	private static ModelNode writeAttr(String addr, String attrName,
			String attrValue) {
		ModelNode result = Utils.writeAttr(addr, attrName, attrValue);
		return Utils.getResult(result);
	}

	/**
	 * Receiving the
	 */
	@SuppressWarnings("unused")
	private static void showMeResults(ModelNode result) {
		log.info("we are going out with: " + result);
		log.info("result type: " + result.getType());
		log.info("result as string: " + result.asString());
		log.info("result resolved: " + result.resolve());
	}

}
