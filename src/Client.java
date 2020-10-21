import java.lang.reflect.Array;
import java.util.*;

//Work needed
public class Client {
    static Random random = new Random();
    static NetworkUtility networkUtility;
    static EndDevice deviceConfig;
    static IPAddress deviceIP;
    static IPAddress[] endDeviceIPs;

    public static void main(String[] args) throws InterruptedException {
        /**
         *          1. Receive EndDevice configuration from server
         *          2. Receive active client list from server
         *          3. for(int i=0;i<100;i++)
         *          4. {
         *          5.      Generate a random message
         *          6.      Assign a random receiver from active client list
         *          7.      if(i==20)
         *          8.      {
         *          9.            Send the message and recipient IP address to server and a special request "SHOW_ROUTE"
         *          10.           Display routing path, hop count and routing table of each router [You need to receive
         *          all the required info from the server in response to "SHOW_ROUTE" request]
         *          11.     }
         *          12.     else
         *          13.     {
         *          14.           Simply send the message and recipient IP address to server.
         *          15.     }
         *          16.     If server can successfully send the message, client will get an acknowledgement along with hop count
         *          Otherwise, client will get a failure message [dropped packet]
         *          17. }
         *          18. Report average number of hops and drop rate
         */
        Scanner scanner = new Scanner(System.in);
        random.setSeed(System.currentTimeMillis());

        int dropCount = 0;
        int successCount = 0;
        int hopCount = 0;

        networkUtility = new NetworkUtility("localhost", 4444);
        System.out.println("Connected to server");

        deviceConfig = (EndDevice) networkUtility.read();
        deviceIP = deviceConfig.getIpAddress();

        System.out.print("Press Y to start sending packet: ");
        scanner.next();

        networkUtility.write(Constants.SEND_ACTIVE_CLIENT);
        HashSet<IPAddress> endDeviceSet = (HashSet<IPAddress>) networkUtility.read();
        endDeviceSet.remove(deviceIP); /// Removing it's own entry.
        endDeviceIPs = new IPAddress[endDeviceSet.size()];
        endDeviceSet.toArray(endDeviceIPs);

        //System.out.println(endDeviceIPs.length);
        //            if(destinationAddress == null){
//                Thread.sleep(5000);
//                System.out.println("Awaking from sleep.");
//                continue;
//            }
        for(int i=0;i<100;i++) {
            IPAddress destinationAddress = getRandomClientIP();
            Packet packet = new Packet("HI", "", deviceIP, destinationAddress);

            if(i % 20 == 0) {
                //System.out.println("Asking for showing route");
                packet.setSpecialMessage("SHOW_ROUTE");
            }

            networkUtility.write(packet);
            Object obj = networkUtility.read();

            if(obj instanceof Packet) {
                Packet ack = (Packet) obj;
                hopCount += ack.hopcount;
                if (ack.getSpecialMessage().equals(Constants.DROP_MESSAGE)) {
                    dropCount++;
                } else {
                    successCount++;
                }
            }
            else if(i % 20 == 0) {
                //System.out.println("Other");
                Vector<Integer> routePath = (Vector<Integer>) obj;
                if(routePath.size() == 0) {
                    System.out.println("No Route finds.");
                    continue;
                }

                System.out.println("Route");
                for (int r: routePath) {
                    System.out.print(r + " -> ");
                }
                System.out.println();
                networkUtility.write(Constants.REQUEST_ROUTING_TABLE);
                Vector<Vector<RoutingTableEntry>> routingTables = (Vector<Vector<RoutingTableEntry>>)networkUtility.read();
                successCount++;
            }
        }

        System.out.println("Total packet sent: " + (dropCount + successCount));
        System.out.println("Average hop count: " + ((double)hopCount) / (dropCount + successCount));
        System.out.println("Average drop count: " + ((double)dropCount) / (dropCount + successCount));
    }

    public static IPAddress getRandomClientIP(){
        if(endDeviceIPs.length == 0) {
            System.out.println("There is no active client to sent packet.");
            System.exit(0);
        }

        return endDeviceIPs[random.nextInt(endDeviceIPs.length)];
    }
}
