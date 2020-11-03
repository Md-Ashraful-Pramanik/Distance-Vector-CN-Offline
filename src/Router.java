//Work needed
import java.io.Serializable;
import java.util.*;

public class Router implements Serializable {
    private int routerId;
    private int numberOfInterfaces;
    private Vector<IPAddress> interfaceAddresses;//list of IP address of all interfaces of the router
    private Vector<RoutingTableEntry> routingTable;//used to implement DVR
    private Vector<Integer> neighborRouterIDs;//Contains both "UP" and "DOWN" state routers
    private Boolean state;//true represents "UP" state and false is for "DOWN" state
    private HashMap<Integer, IPAddress> gatewayIDtoIP;

    public Router() {
        interfaceAddresses = new Vector<>();
        routingTable = new Vector<>();
        neighborRouterIDs = new Vector<>();

        /**
         * 80% Probability that the router is up
         */
        Random random = new Random();
        double p = random.nextDouble();
        if(p < 0.80) state = true;
        else state = false;

        numberOfInterfaces = 0;
    }

    public Router(int routerId, Vector<Integer> neighborRouters,
                  Vector<IPAddress> interfaceAddresses,
                  HashMap<Integer, IPAddress> gatewayIDtoIP) {
        this.routerId = routerId;
        this.interfaceAddresses = interfaceAddresses;
        this.neighborRouterIDs = neighborRouters;
        this.gatewayIDtoIP = gatewayIDtoIP;
        this.routingTable = new Vector<>();

        /**
         * 80% Probability that the router is up
         */
        Random random = new Random();
        double p = random.nextDouble();
        if(p < 0.80) state = true;
        else state = false;

        numberOfInterfaces = interfaceAddresses.size();
    }

    @Override
    public String toString() {
        String string = "";
        string += "Router ID: " + routerId + "\n" + "Interfaces: \n";
        for (int i = 0; i < numberOfInterfaces; i++) {
            string += interfaceAddresses.get(i).getString() + "\t";
        }
        string += "\n" + "Neighbors: \n";
        for(int i = 0; i < neighborRouterIDs.size(); i++) {
            string += neighborRouterIDs.get(i) + "\t";
        }
        return string;
    }

    /**
     * Initialize the distance(hop count) for each router.
     * for itself, distance=0; for any connected router with state=true, distance=1; otherwise distance=Constants.INFTY;
     */
    public void initiateRoutingTable() {
        if (routingTable.size() > 0)
            clearRoutingTable();
        for (Router r: NetworkLayerServer.routers) {
            if(r.getRouterId() == routerId)
                routingTable.add(new RoutingTableEntry(r.getRouterId(), 0, r.getRouterId()));
            else if(neighborRouterIDs.contains(r.getRouterId())){
                if(r.state)
                    routingTable.add(new RoutingTableEntry(r.getRouterId(), 1, r.getRouterId()));
                else
                    routingTable.add(new RoutingTableEntry(r.getRouterId(), Constants.INFINITY, r.getRouterId()));
            }
            else
                routingTable.add(new RoutingTableEntry(r.getRouterId(), Constants.INFINITY, -1));
        }
    }

    /**
     * Delete all the routingTableEntry
     */
    public void clearRoutingTable() {
        routingTable.clear();
    }

    /**
     * Update the routing table for this router using the entries of Router neighbor
     */
    public boolean updateRoutingTable(Router neighbour) {
        boolean change = false;
        if(routingTable.size() == 0 || neighbour.routingTable.size() == 0)
            initiateRoutingTable();

//        for (int i=0; i< NetworkLayerServer.routers.size(); i++) {
//            RoutingTableEntry ownEntry = routingTable.get(i);
//            if(ownEntry.getDistance() <= 1)
//                continue;
//
//            RoutingTableEntry tempEntry = new RoutingTableEntry(ownEntry.getRouterId(), Constants.INFINITY, -1);
//            for (int neighbourRouterID:neighborRouterIDs) {
//                Router neighbourRouter = NetworkLayerServer.getRouterByID(neighbourRouterID);
//                if(neighbourRouter.getRoutingTable().size() == 0)
//                    continue;
//
//                RoutingTableEntry neighbourEntry = neighbourRouter.getRoutingTable().get(i);
//
//                if (neighbourEntry.getDistance() + 1 < Constants.INFINITY &&
//                        neighbourEntry.getDistance() + 1 < tempEntry.getDistance())
//                {
//                    tempEntry.setDistance(neighbourEntry.getDistance() + 1);
//                    tempEntry.setGatewayRouterId(neighbourRouterID);
//                }
//            }
//
//            if(ownEntry.getDistance() != tempEntry.getDistance() ||
//                    ownEntry.getGatewayRouterId() != tempEntry.getGatewayRouterId()){
//                ownEntry.setDistance(tempEntry.getDistance());
//                ownEntry.setGatewayRouterId(tempEntry.getGatewayRouterId());
//                change = true;
//            }
//        }
        for (int i=0;i<routingTable.size();i++) {
            if(routingTable.get(i).getDistance()<=1)
                continue;
            if(neighbour.routingTable.size() <= i)
                continue;
            if(neighbour.routingTable.get(i).getDistance() == Constants.INFINITY)
                continue;

            if(routingTable.get(i).getDistance() > neighbour.routingTable.get(i).getDistance() + 1){
                routingTable.get(i).setDistance(neighbour.routingTable.get(i).getDistance() + 1);
                routingTable.get(i).setGatewayRouterId(neighbour.getRouterId());
                change = true;
            }
        }

        return change;
    }

    public boolean sfupdateRoutingTable(Router neighbour) {
        boolean change = false;
        if(routingTable.size() == 0 || neighbour.routingTable.size() == 0)
            initiateRoutingTable();

        for (int i=0;i<routingTable.size();i++) {
            if(routingTable.get(i).getDistance()<=1)
                continue;
            if(neighbour.routingTable.size() <= i)
                continue;
            if(neighbour.routingTable.get(i).getDistance() == Constants.INFINITY)
                continue;

            /// Split horizon rule
            if(neighbour.routingTable.get(i).getGatewayRouterId() == routerId)
                continue;

            /// force update
            if(routingTable.get(i).getGatewayRouterId() == neighbour.getRouterId()) {
                if(routingTable.get(i).getDistance() != neighbour.routingTable.get(i).getDistance() + 1) {
                    routingTable.get(i).setDistance(neighbour.routingTable.get(i).getDistance() + 1);
                    change = true;
                }
                continue;
            }

            if(routingTable.get(i).getDistance() > neighbour.routingTable.get(i).getDistance() + 1){
                routingTable.get(i).setDistance(neighbour.routingTable.get(i).getDistance() + 1);
                routingTable.get(i).setGatewayRouterId(neighbour.getRouterId());
                change = true;
            }
        }

        return change;
    }

    /**
     * If the state was up, down it; if state was down, up it
     */
    public void revertState() {
        state = !state;
        if(state) { initiateRoutingTable(); }
        else { clearRoutingTable(); }
    }

    public int getRouterId() {
        return routerId;
    }

    public void setRouterId(int routerId) {
        this.routerId = routerId;
    }

    public int getNumberOfInterfaces() {
        return numberOfInterfaces;
    }

    public void setNumberOfInterfaces(int numberOfInterfaces) {
        this.numberOfInterfaces = numberOfInterfaces;
    }

    public Vector<IPAddress> getInterfaceAddresses() {
        return interfaceAddresses;
    }

    public void setInterfaceAddresses(Vector<IPAddress> interfaceAddresses) {
        this.interfaceAddresses = interfaceAddresses;
        numberOfInterfaces = interfaceAddresses.size();
    }

    public Vector<RoutingTableEntry> getRoutingTable() {
        return routingTable;
    }

    public void addRoutingTableEntry(RoutingTableEntry entry) {
        this.routingTable.add(entry);
    }

    public Vector<Integer> getNeighborRouterIDs() {
        return neighborRouterIDs;
    }

    public void setNeighborRouterIDs(Vector<Integer> neighborRouterIDs) { this.neighborRouterIDs = neighborRouterIDs; }

    public Boolean getState() {
        return state;
    }

    public void setState(Boolean state) {
        this.state = state;
    }

    public RoutingTableEntry getRoutingEntryForRouterID(int id){
        return routingTable.get(id - 1);
    }

    public Map<Integer, IPAddress> getGatewayIDtoIP() { return gatewayIDtoIP; }

    public void printRoutingTable() {
        System.out.println("Router " + routerId);
        System.out.println("DestID Distance Nexthop");
        for (RoutingTableEntry routingTableEntry : routingTable) {
            System.out.println(routingTableEntry.getRouterId() + " " + routingTableEntry.getDistance() + " " + routingTableEntry.getGatewayRouterId());
        }
        System.out.println("-----------------------");
    }

    public String strRoutingTable() {
        String string = "Router" + routerId + "\n";
        string += "DestID Distance Nexthop\n";
        for (RoutingTableEntry routingTableEntry : routingTable) {
            string += routingTableEntry.getRouterId() + " " + routingTableEntry.getDistance() + " " + routingTableEntry.getGatewayRouterId() + "\n";
        }

        string += "-----------------------\n";
        return string;
    }

}
