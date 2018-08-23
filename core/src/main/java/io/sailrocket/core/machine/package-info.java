/**
 * <h1>Design</h1>
 *
 * There are two main components:
 * <ul>
 * <li>{@link io.sailrocket.core.machine.SequenceTemplate Sequence templates} - instructions 'what to do'
 * <li>Session (execution context) holds any state, including current state of the state machine and variables
 * </ul>
 * <h2>Memory allocation</h2>
 *
 * In order to keep object allocations at minimum we're expected to know all variables in advance and pre-allocate
 * these in the Session object. During consecutive repetitions of the user scenario
 * the {@link io.sailrocket.core.machine.Session} is {@link io.sailrocket.core.machine.Session#reset()}
 * which does not release the memory.
 * <p>
 * Any memory required by validators/extractors must be known ahead and these should implement
 * the {@link io.sailrocket.core.machine.ResourceUtilizer} interface to register itselves. The reservation is invoked
 * once when the session is created through {@link io.sailrocket.core.machine.SequenceTemplate#reserve(io.sailrocket.core.machine.Session)}
 * which in turn calls this on all {@link io.sailrocket.core.machine.Step steps} and these call the
 * {@link io.sailrocket.api.Session.Processor processors} or any other handlers.
 *
 * <h2>Execution</h2>
 *
 * After the session is constructed or reset you should create {@link io.sailrocket.core.machine.SequenceInstance sequence instances}
 * from the {@link io.sailrocket.core.machine.SequenceTemplate templates} and subsequently
 * {@link io.sailrocket.core.machine.Session#enableSequence(io.sailrocket.core.machine.SequenceInstance) enable}
 * them in the session. Upon {@link io.sailrocket.core.machine.Session#run()} the session tries to invoke all enabled
 * sequence instances; some of the enabled sequences may be blocked because of missing data dependency.
 * <p>
 * The sequence consists of several {@link io.sailrocket.core.machine.Step steps}, each of which may have some
 * data dependency. Therefore the sequence may be blocked in the middle. Other enabled sequence may be still invoked
 * as soon as its dependencies are satisfied. Each step can enable further sequences.
 * <p>
 * The execution of sequence cannot be forked but it can be terminated by calling
 * {@link io.sailrocket.core.machine.Session#currentSequence(io.sailrocket.core.machine.SequenceInstance)}
 * with <code>null</code> argument - {@link io.sailrocket.core.machine.BreakSequenceStep} is an example of that.
 * <p>
 * Execution is terminated when there are no enabled sequences in the session.
 *
 * <h2>Variables</h2>
 *
 * The {@link io.sailrocket.api.Session} is provided as a parameter to most calls and stores all state of the scenario.
 * The state is operated using {@link io.sailrocket.core.machine.Session#getObject(java.lang.Object)} and
 * {@link io.sailrocket.core.machine.Session#setObject(java.lang.Object, java.lang.Object)} methods or their integer
 * counterparts.
 * <p>
 * Initially all variables are in undefined state; reading such variable is considered an error. The unset/set state
 * forms the basis of data-dependencies mentioned earlier: when a step requires the variable to be defined, you should
 * declare that with {@link io.sailrocket.core.machine.BaseStep#addDependency(io.sailrocket.core.machine.VarReference)}.
 * <p>
 * It is possible to find out if the variable is set calling {@link io.sailrocket.core.machine.Session#isSet(java.lang.Object)}.
 * <p>
 * Simple variables are scalar, these are useful for scenario-scoped data. Other variables are scoped for particular
 * {@link io.sailrocket.core.machine.SequenceInstance}; these should be implemented as arrays (or collections) with
 * a limited size equal to the number of instances. When a Step/Processor needs to address sequence-scoped data
 * it fetches its index through {@link io.sailrocket.core.machine.Session#currentSequence()}.{@link io.sailrocket.core.machine.SequenceInstance#index() index()}.
 * <p>
 * The choice of index is up to the Step that creates the new sequences. Two concurrently enabled sequences may share
 * the same index, but in that case these should not use the same variable names for sequence-scoped data.
 *
 * <h2>Recording statistics</h2>
 *
 * When processing a response the original request would not be available as the handler is stateless, and we can have
 * several requests in-flight. We need to find out a time when the request started, though, and that's why the session
 * holds a {@link io.sailrocket.core.machine.RequestQueue}. Since all requests are sent over the same connection (ATM)
 * and responses are received FIFO we can push the {@link io.sailrocket.core.machine.RequestQueue.Request} with
 * the start timestamp and retrieve it when processing the response. Since this queue is bounded, this effectively
 * limits the number of concurrent requests.
 *
 * <h2>Threading model</h2>
 *
 * There's no internal synchronization of anything; we rely on the event-loop model. It is expected that at any moment
 * the {@link io.sailrocket.api.Session} will be accessed (read or write) by at most one thread. It does not need to be
 * a single thread, therefore the use of thread-locals is strongly discouraged.
 */
package io.sailrocket.core.machine;