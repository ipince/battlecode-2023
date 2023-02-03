package bobby_v2;

public class MovingAvgLastN {
    int maxTotal;
    int total;
    double lastN[];
    double avg;
    int head;

    public MovingAvgLastN(int N) {
        maxTotal = N;
        lastN = new double[N];
        avg = 0;
        head = 0;
        total = 0;
    }

    public void add(double num) {
        double prevSum = total * avg;

        if (total == maxTotal) {
            prevSum -= lastN[head];
            total--;
        }

        head = (head + 1) % maxTotal;
        int emptyPos = (maxTotal + head - 1) % maxTotal;
        lastN[emptyPos] = num;

        double newSum = prevSum + num;
        total++;
        avg = newSum / total;
    }

    public double getAvg() {
        return avg;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (double i : lastN) {
            sb.append(String.valueOf(i) + ", ");
        }
        return String.format("[avg=%.2f, seen=%d, sum=%.2f, all=%s]", avg, total, total * avg, sb.toString());
    }
}