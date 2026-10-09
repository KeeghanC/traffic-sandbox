package traffic.engine;

import java.util.ArrayDeque;
import java.util.Deque;

final class Metrics {
    long completed;
    double delaySum;
    final Deque<Double> recent = new ArrayDeque<>();

    void complete(double time, double delay) {
        completed++;
        delaySum += delay;
        recent.addLast(time);
    }

    void expire(double time) {
        while (!recent.isEmpty() && recent.peekFirst() <= time - 60) {
            recent.removeFirst();
        }
    }

    void clear() {
        completed = 0;
        delaySum = 0;
        recent.clear();
    }
}
