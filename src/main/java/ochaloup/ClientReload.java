package ochaloup;

import java.net.InetAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Testing reloading client
 */
public class ClientReload 
{
    public static void main( String[] args ) throws Exception {

    	ModelControllerClient client = ModelControllerClient.Factory.create(InetAddress.getByName("localhost"), 9999);
    	
try {
    	ModelNode op = new ModelNode();
    	op.get("operation").set("reload");
    	op.get("admin-only").set(true);
    	ModelNode status = client.execute(op);
    	System.out.println("We are getting: " + status);
    	
/*
        Thread.sleep(6000);

        // client = ModelControllerClient.Factory.create(InetAddress.getByName("localhost"), 9999);
        ModelNode rop = new ModelNode();
        rop.get("operation").set("read-attribute");
        rop.get("name").set("server-state");
        ModelNode status2 = client.execute(rop);
        System.out.println("Server state: " + status2);
*/
        
} finally {
        client.close();
}
    }
}
