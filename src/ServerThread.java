

import java.util.*;

public class ServerThread implements Runnable {

    NetworkUtility networkUtility;
    EndDevice endDevice;
    Vector<Router> route;

    ServerThread(NetworkUtility networkUtility, EndDevice endDevice) {
        this.networkUtility = networkUtility;
        this.endDevice = endDevice;
        System.out.println("Server Ready for client " + NetworkLayerServer.clientCount);
        NetworkLayerServer.clientCount++;
        new Thread(this).start();
    }

    @Override
    public void run() {
        /*
//        if (getActiveClientIPs().size() <= 1) {
//            try {
//                wait();
//                networkUtility.write(getActiveClientIPs());
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                return;
//            }
//        }


        Tasks:
        1. Upon receiving a packet and recipient, call deliverPacket(packet)
        2. If the packet contains "SHOW_ROUTE" request, then fetch the required information
                and send back to client
        3. Either send acknowledgement with number of hops or send failure message back to client
        */
        networkUtility.write(endDevice); /// sending endDevice configuration

        String msg = (String) networkUtility.read();
        if(msg == null || !msg.equals(Constants.SEND_ACTIVE_CLIENT))
            return;

        HashSet<IPAddress> activeClientIPs = getActiveClientIPs();
        networkUtility.write(activeClientIPs);

        for(int i=0;i<100;i++) {
//            if(activeClientIPs.size() <= 1){
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                System.out.println("Sending packet again");
//                continue;
//            }
            Object obj = networkUtility.read();
            if(obj == null) {
                freeClientResourse();
                return;
            }

            Packet packet = (Packet) obj;

            if (!deliverPacket(packet)) {
                packet.hopcount = Constants.INFINITY;
                packet.setSpecialMessage(Constants.DROP_MESSAGE);
                networkUtility.write(packet);
                continue;
            }

            packet.hopcount = route.size();
            packet.setMessage("acknowledgement");

            if(packet.getSpecialMessage().equals(Constants.SHOW_ROUTE)) {
                System.out.println("**************Sending route to : " + endDevice.getDeviceID());
                if (route != null || route.size() != 0) {
                    System.out.println("Route length " + route.size());
                    System.out.println("Route");
                    for (Router r: route) {
                        System.out.print(r.getRouterId() + " -> ");
                    }
                    System.out.println();
                }
                else
                    System.out.println("No route finds");

                networkUtility.write(getRoutePath());
                if(Constants.REQUEST_ROUTING_TABLE.equals(networkUtility.read()))
                    networkUtility.write(getRouteTables());
            }
            else
                networkUtility.write(packet);
        }

        freeClientResourse(); /// Deleting this client from every list and updating client
    }

    private Vector<Integer> getRoutePath(){
        Vector<Integer> routingPath = new Vector<>(route.size());
        for (Router r: route)
            routingPath.add(r.getRouterId());

        return routingPath;
    }

    private Vector<Vector<RoutingTableEntry>> getRouteTables(){
        Vector<Vector<RoutingTableEntry>> routingTables = new Vector<>(route.size());
        for (Router r: route)
            routingTables.add(r.getRoutingTable());

        return routingTables;
    }

    private void freeClientResourse() {
        int prevCount = NetworkLayerServer.clientInterfaces.get(endDevice.getGateway());
        NetworkLayerServer.clientInterfaces.put(endDevice.getGateway(), --prevCount);
        NetworkLayerServer.deviceIDtoRouterID.remove(endDevice.getDeviceID());
        NetworkLayerServer.endDevices.remove(endDevice);
        NetworkLayerServer.endDeviceMap.remove(endDevice.getIpAddress());
    }


    public Boolean deliverPacket(Packet p) {
        /// 1. Find the router s which has an interface
        ///    such that the interface and source end device have same network address.
        /// 2. Find the router d which has an interface
        /// such that the interface and destination end device have same network address.

        route = new Vector<>();
        
        Router sourceRouter = getRouterFromEndDeviceIP(p.getSourceIP());
        Router destinationRouter = getRouterFromEndDeviceIP(p.getDestinationIP());

        /*
            3. Implement forwarding, i.e., s forwards to its gateway router x considering d as the destination.
            similarly, x forwards to the next gateway router y considering d as the destination,
            and eventually the packet reaches to destination router d.

            3(a) If, while forwarding, any gateway x, found from routingTable of router r is in down state[x.state==FALSE]
                (i) Drop packet
                (ii) Update the entry with distance Constants.INFTY
                (iii) Block NetworkLayerServer.stateChanger.t
                (iv) Apply DVR starting from router r.
                (v) Resume NetworkLayerServer.stateChanger.t

            3(b) If, while forwarding, a router x receives the packet from router y,
                but routingTableEntry shows Constants.INFTY distance from x to y,
                (i) Update the entry with distance 1
                (ii) Block NetworkLayerServer.stateChanger.t
                (iii) Apply DVR starting from router x.
                (iv) Resume NetworkLayerServer.stateChanger.t
        */

        route.add(sourceRouter);
        
        while (sourceRouter.getRouterId() != destinationRouter.getRouterId()){
            if(sourceRouter.getRoutingTable().size() == 0)
                return false;

            RoutingTableEntry routingEntry = sourceRouter.getRoutingEntryForRouterID(destinationRouter.getRouterId());

            if(routingEntry.getDistance() == Constants.INFINITY)
                return false;

            Router nextRouter = NetworkLayerServer.getRouterByID(routingEntry.getGatewayRouterId());

            if(!nextRouter.getState()){
                routingEntry.setDistance(Constants.INFINITY);
                routingEntry.setGatewayRouterId(-1);
                RouterStateChanger.islocked = true;
                NetworkLayerServer.simpleDVR(sourceRouter.getRouterId());
                RouterStateChanger.islocked = false;
                return false;
            }
            else if (nextRouter.getRoutingEntryForRouterID(sourceRouter.getRouterId()).getDistance() == Constants.INFINITY){
                nextRouter.getRoutingEntryForRouterID(sourceRouter.getRouterId()).setDistance(1);
                nextRouter.getRoutingEntryForRouterID(sourceRouter.getRouterId()).setGatewayRouterId(sourceRouter.getRouterId());
                RouterStateChanger.islocked = true;
                NetworkLayerServer.simpleDVR(nextRouter.getRouterId());
                RouterStateChanger.islocked = false;
            }
            sourceRouter = nextRouter;
            route.add(sourceRouter);
        }

        return true;

    }

    public Router getRouterFromEndDeviceIP(IPAddress ipAddress) {
        //System.out.println(NetworkLayerServer.endDeviceMap.containsKey(ipAddress));
        EndDevice endDevice = NetworkLayerServer.endDeviceMap.get(ipAddress);
        int routerID = NetworkLayerServer.deviceIDtoRouterID.get(endDevice.getDeviceID());

        return NetworkLayerServer.getRouterByID(routerID);
    }

    public HashSet<IPAddress> getActiveClientIPs(){
        HashSet<IPAddress> activeClients = new HashSet<>(NetworkLayerServer.endDeviceMap.keySet());
//        for (Router r:NetworkLayerServer.routers) {
//            if(r.getState())
//                activeClients.addAll(r.getInterfaceAddresses());
//        }
        //System.out.println("Sending " + endDevice.getDeviceID() + " => " + (activeClients.size() -1) + " Client");
        return activeClients;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj); //To change body of generated methods, choose Tools | Templates.
    }
}
