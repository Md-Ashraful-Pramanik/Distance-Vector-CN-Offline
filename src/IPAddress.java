import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;

//Done!
public class IPAddress implements Serializable {

    private Short bytes[];
    private String string;

    public IPAddress(String string) {
        bytes = new Short[4];
        this.string = string;
        String[] temp = string.split("\\.");
        for (int i = 0; i < 4; i++) {
            bytes[i] = Short.parseShort(temp[i]);
        }

    }

    public Short[] getBytes()
    {
        return bytes;
    }

    public String getString()
    {
        return string;
    }

    @Override
    public String toString() { return string; }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof IPAddress))
            return false;

        return ((IPAddress)obj).string.equals(string);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }
}
