import java.io.Serializable;

//Done!
public class EndDevice implements Serializable {

    private IPAddress ipAddress;
    private IPAddress gateway;
    private Integer deviceID;

    public EndDevice(IPAddress ipAddress, IPAddress gateway, Integer deviceID) {
        this.ipAddress = ipAddress;
        this.gateway = gateway;
        this.deviceID = deviceID;
    }

    public IPAddress getIpAddress() {
        return ipAddress;
    }

    public IPAddress getGateway() { return gateway; }

    public Integer getDeviceID() { return deviceID; }

    @Override
    public boolean equals(Object obj) {
        EndDevice endDevice = (EndDevice) obj;
        return deviceID.equals(endDevice.deviceID) &&
                gateway.equals(endDevice.gateway) &&
                ipAddress.equals(endDevice.ipAddress);
    }

    @Override
    public int hashCode() {
        return ipAddress.hashCode() + gateway.hashCode() + deviceID.hashCode();
    }
}
