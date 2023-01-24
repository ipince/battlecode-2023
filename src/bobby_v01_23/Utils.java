package bobby_v01_23;

public class Utils {

    public static String padLeft(String in, int length) {
        if (in.length() >= length) {
            return in;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length - in.length()) {
            sb.append('0');
        }
        sb.append(in);

        return sb.toString();
    }
}
