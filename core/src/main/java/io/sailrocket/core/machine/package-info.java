/**
 * <h1>Design</h1>
 *
 * There are two main components:
 * <ul>
 * <li>State machine (automaton) is a stateless model of the operation
 * <li>Session (execution context) holds any state, including current state of the state machine and variables
 * </ul>
 * <h2>Memory allocation</h2>
 *
 * In order to keep object allocations at minimum we're expected to know all variables in advance and pre-allocate
 * these in the Session object. During consecutive repetitions of the user scenario
 * the {@link io.sailrocket.core.machine.Session} is {@link io.sailrocket.core.machine.Session#reset(State)}
 * which does not release the memory.
 * <p>
 * Any memory required by validators/extractors must be known ahead and these should implement
 * the {@link io.sailrocket.core.machine.ResourceUtilizer} interface to register itselves.
 * <p>
 * States may need to reserve some memory as well (for handlers that contain session reference), these are cached
 * in Session upon the {@link io.sailrocket.core.machine.State#register(io.sailrocket.core.machine.Session)} call.
 *
 * <h2>State machine operation</h2>
 *
 * The session is constructed with an initial state and then the {@link io.sailrocket.core.machine.Session#run()}
 * method should be invoked. Each state has several {@link io.sailrocket.core.machine.Transition transitions}
 * to other states; the condition for each transition is evaluated and first matching transition is followed.
 * When the transition is followed, an action associated with this transition is invoked and state is set
 * to the transition target. The next behaviour depends whether the transition is blocking or not:
 * <ul>
 * <li>When the transition is blocking, the execution does not continue in this thread; we wait for an async operation.
 *     This is used when we need to wait for a response to previous request (e.g. fired as the action of transition).
 * <li>When the transition is non-blocking, we immediately start evaluating conditions on the new state's transitions
 *     and follow the first matching.
 * </ul>
 * When the state is set to null, the execution of state machine is stopped. The session should be
 * {@link io.sailrocket.core.machine.Session#reset(State)} before starting new user scenario.
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
 */
package io.sailrocket.core.machine;