import java.io.Serializable;
import java.util.BitSet;

public class BloomFilter implements Serializable {
    private BitSet bitset;
    private int size = 512;

    public BloomFilter() {
        bitset = new BitSet(size);
    }

    public void add(String value) {
        int h1 = Math.abs(value.hashCode()) % size;
        int h2 = Math.abs((value + "salt").hashCode()) % size;
        bitset.set(h1);
        bitset.set(h2);
    }

    public boolean mightContain(String value) {
        int h1 = Math.abs(value.hashCode()) % size;
        int h2 = Math.abs((value + "salt").hashCode()) % size;
        return bitset.get(h1) && bitset.get(h2);
    }

    public BitSet getBitSet() {
        return bitset;
    }
}