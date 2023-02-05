package bobby;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;

public class Utils {

    // Precondition: !set.isEmpty()
    public static <E> E pickRandom(Set<E> set, Random rng) {
        Iterator<E> iter = set.iterator();
        int num = rng.nextInt(set.size());
        int count = 0;
        E ret = iter.next();
        while (count < num && iter.hasNext()) {
            ret = iter.next();
        }
        return ret;
    }
}
