package io.sailrocket.core.client;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

//TODO:: tidy this up, just simple for POC
public class WorkerStats {
    public final Histogram histogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 2);
    public LongAdder connectFailureCount = new LongAdder();
    public LongAdder requestCount = new LongAdder();
    public LongAdder responseCount = new LongAdder();
    public LongAdder status_2xx = new LongAdder();
    public LongAdder status_3xx = new LongAdder();
    public LongAdder status_4xx = new LongAdder();
    public LongAdder status_5xx = new LongAdder();
    public LongAdder status_other = new LongAdder();
    public LongAdder[] statuses = {status_2xx, status_3xx, status_4xx, status_5xx, status_other};
    public LongAdder resetCount = new LongAdder();


}
