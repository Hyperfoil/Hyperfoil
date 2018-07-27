package io.sailrocket.distributed;

import io.sailrocket.core.BenchmarkImpl;

//TODO:: REMOVE!! and create from definition
// this is only here until we can dynamically generate benchmark definitions
public class SimpleBenchmark extends BenchmarkImpl {


    public SimpleBenchmark(String name) {
        super(name);
        agents("localhost").users(5).endpoint("http://localhost:8080/");
    }
}