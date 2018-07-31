package io.sailrocket.api;


import java.util.List;

public interface Sequence {

    Sequence step(Step step);

     List<Step> getSteps();
}
