import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//Work needed
public class NetworkLayerServer {

    static int clientCount = 0;
    static Vector<Router> routers = new Vector<>();
    static RouterStateChanger stateChanger = null;
    static HashMap<IPAddress,Integer> clientInterfaces = new HashMap<>(); //Each map entry represents number of client end devices connected to the interface
    static HashMap<IPAddress, EndDevice> endDeviceMap = new HashMap<>();
    static Vector<EndDevice> endDevices = new Vector<>();
    static HashMap<Integer, Integer> deviceIDtoRouterID = new HashMap<>();
    static HashMap<IPAddress, Integer> interfacetoRouterID = new HashMap<>();
    static HashMap<Integer, Router> routerMap = new HashMap<>();

    public static void main(String[] args) {

        //Task: Maintain an active client list

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(4444);
        } catch (IOException ex) {
            Logger.getLogger(NetworkLayerServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.println("Server Ready: " + serverSocket.getInetAddress().getHostAddress());
        System.out.println("Creating router topology");

        readTopology();
        //printRouters();

        initRoutingTables(); //Initialize routing tables for all routers

        //DVR(1); //Update routing table using distance vector routing until convergence
        simpleDVR(1);
        //printRouters();
        stateChanger = new RouterStateChanger();//Starts a new thread which turns on/off routers randomly depending on parameter Constants.LAMBDA

        System.out.println("Starting taking client");

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("Client" + (clientCount + 1) + " attempted to connect");
                EndDevice endDevice = getClientDeviceSetup();
                clientCount++;
                endDevices.add(endDevice);
                endDeviceMap.put(endDevice.getIpAddress(),endDevice);
                //System.out.println(endDeviceMap.containsKey(new IPAddress(endDevice.getIpAddress().getString())));
                new ServerThread(new NetworkUtility(socket), endDevice);
            } catch (Exception ex) {
                Logger.getLogger(NetworkLayerServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void initRoutingTables() {
        for (Router router : routers) {
            router.initiateRoutingTable();
        }
    }

    public static synchronized void DVR(int startingRouterId) {
        boolean convergence = false;
        boolean firstTime = true;

        Router t;

        while(!convergence)
        {
            convergence = true;
            for (Router r: routers) {
                if(!r.getState())
                    continue;
                if(firstTime && r.getRouterId()!=startingRouterId)
                    continue;
                for (int neighbourID : r.getNeighborRouterIDs()) {
                    t = getRouterByID(neighbourID);
                    if (t.getState() && r.sfupdateRoutingTable(t))
                        convergence = false;
                }
            }
            if(firstTime){
                convergence = false;
                firstTime = false;
            }
        }
    }

    public static synchronized void simpleDVR(int startingRouterId) {
        boolean convergence = false;
        boolean firstTime = true;

        Router t;

        while(!convergence)
        {
            convergence = true;
            for (Router r: routers) {
                if(!r.getState())
                    continue;
                if(firstTime && r.getRouterId()!=startingRouterId)
                    continue;
                for (int neighbourID : r.getNeighborRouterIDs()) {
                    t = getRouterByID(neighbourID);
                    if (t.getState() && r.updateRoutingTable(t))
                        convergence = false;
                }
            }
            if(firstTime){
                convergence = false;
                firstTime = false;
            }
        }
    }

    public static EndDevice getClientDeviceSetup() {
        Random random = new Random(System.currentTimeMillis());
        int r = Math.abs(random.nextInt(clientInterfaces.size()));

        System.out.println("Size: " + clientInterfaces.size() + "\n" + r);

        IPAddress ip = null;
        IPAddress gateway = null;

        int i = 0;
        for (Map.Entry<IPAddress, Integer> entry : clientInterfaces.entrySet()) {
            IPAddress key = entry.getKey();
            Integer value = entry.getValue();
            if(i == r) {
                gateway = key;
                ip = new IPAddress(gateway.getBytes()[0] + "." + gateway.getBytes()[1] + "." + gateway.getBytes()[2] + "." + (value+2));
                value++;
                clientInterfaces.put(key, value);
                deviceIDtoRouterID.put(endDevices.size(), interfacetoRouterID.get(key));
                break;
            }
            i++;
        }

        EndDevice device = new EndDevice(ip, gateway, endDevices.size());

        System.out.println("Device : " + ip + "::::" + gateway);
        return device;
    }

    public static void printRouters() {
        for(int i = 0; i < routers.size(); i++) {
            System.out.println("------------------\n" + routers.get(i));
        }
    }

    public static String strrouters() {
        String string = "";
        for (int i = 0; i < routers.size(); i++) {
            string += "\n------------------\n" + routers.get(i).strRoutingTable();
        }
        string += "\n\n";
        return string;
    }

    public static void readTopology() {
        Scanner inputFile = null;
        try {
            inputFile = new Scanner(new File("topology.txt"));
            //skip first 27 lines
            int skipLines = 27;
            for(int i = 0; i < skipLines; i++) {
                inputFile.nextLine();
            }

            //start reading contents
            while(inputFile.hasNext()) {
                inputFile.nextLine();
                int routerId;
                Vector<Integer> neighborRouters = new Vector<>();
                Vector<IPAddress> interfaceAddrs = new Vector<>();
                HashMap<Integer, IPAddress> interfaceIDtoIP = new HashMap<>();

                routerId = inputFile.nextInt();

                int count = inputFile.nextInt();
                for(int i = 0; i < count; i++) {
                    neighborRouters.add(inputFile.nextInt());
                }
                count = inputFile.nextInt();
                inputFile.nextLine();

                for(int i = 0; i < count; i++) {
                    String string = inputFile.nextLine();
                    IPAddress ipAddress = new IPAddress(string);
                    interfaceAddrs.add(ipAddress);
                    interfacetoRouterID.put(ipAddress, routerId);

                    /**
                     * First interface is always client interface
                     */
                    if(i == 0) {
                        //client interface is not connected to any end device yet
                        clientInterfaces.put(ipAddress, 0);
                    }
                    else {
                        interfaceIDtoIP.put(neighborRouters.get(i - 1), ipAddress);
                    }
                }
                Router router = new Router(routerId, neighborRouters, interfaceAddrs, interfaceIDtoIP);
                routers.add(router);
                routerMap.put(routerId, router);
            }


        } catch (FileNotFoundException ex) {
            Logger.getLogger(NetworkLayerServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static Router getRouterByID(int id){
        return routerMap.get(id);
    }

}
