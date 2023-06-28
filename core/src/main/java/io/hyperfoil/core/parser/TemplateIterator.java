package io.hyperfoil.core.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionEndEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;


public class TemplateIterator implements Iterator<Event> {
   private final Iterator<Event> delegate;
   private final Map<String, AnchorInfo> anchors = new HashMap<>();
   private final List<List<Event>> anchorStack = new ArrayList<>();
   private final Map<String, Stack<String>> params;
   private final Stack<Integer> depthStack = new Stack<>();
   private final Stack<Iterator<Event>> replaying = new Stack<>();
   private int depth;

   public TemplateIterator(Iterator<Event> delegate, Map<String, String> params) {
      this.delegate = delegate;
      this.params = params.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
         Stack<String> stack = new Stack<>();
         stack.push(entry.getValue());
         return stack;
      }));
   }

   @Override
   public boolean hasNext() {
      while (!replaying.isEmpty()) {
         Iterator<Event> iterator = replaying.peek();
         if (iterator.hasNext()) {
            return true;
         }
         replaying.pop();
      }
      return delegate.hasNext();
   }

   @Override
   public Event next() {
      Event event = nextRawEvent();
      String tag = null;
      if (event instanceof ScalarEvent) {
         tag = ((ScalarEvent) event).getTag();
      } else if (event instanceof CollectionStartEvent) {
         tag = ((CollectionStartEvent) event).getTag();
      }
      if (tag != null) {
         switch (tag.toLowerCase(Locale.ENGLISH)) {
            case "!param":
               return paramEvent(event);
            case "!concat":
               return concatEvent(event);
            case "!foreach":
               return forEachEvent(event);
         }
      }
      return event;
   }

   private Event nextRawEvent() {
      Event event = nextEventFromStacks();
      if (event instanceof NodeEvent) {
         NodeEvent nodeEvent = (NodeEvent) event;
         if (nodeEvent instanceof AliasEvent) {
            AnchorInfo anchorInfo = anchors.get(nodeEvent.getAnchor());
            if (anchorInfo == null) {
               throw new RuntimeException(new ParserException(nodeEvent, "No anchor for alias '" + nodeEvent.getAnchor() + "'"));
            } else {
               Iterator<Event> iterator = anchorInfo.events.iterator();
               replaying.push(iterator);
               assert iterator.hasNext();
               event = iterator.next();
            }
         } else if (nodeEvent.getAnchor() != null) {
            List<Event> newReplayList = new ArrayList<>();
            AnchorInfo prev = anchors.put(nodeEvent.getAnchor(), new AnchorInfo(nodeEvent, newReplayList));
            if (prev != null) {
               throw new RuntimeException(new ParserException(nodeEvent, "Anchor '" + nodeEvent.getAnchor() +
                     "' was already defined before on line " + (prev.anchorEvent.getStartMark().getLine() + 1)));
            }
            if (nodeEvent instanceof CollectionStartEvent) {
               anchorStack.add(newReplayList);
               depthStack.push(depth);
               // sanitize anchors
               if (nodeEvent instanceof MappingStartEvent) {
                  MappingStartEvent mse = (MappingStartEvent) nodeEvent;
                  event = new MappingStartEvent(null, mse.getTag(), mse.getImplicit(),
                        mse.getStartMark(), mse.getEndMark(), mse.getFlowStyle());
               } else if (nodeEvent instanceof SequenceStartEvent) {
                  SequenceStartEvent sse = (SequenceStartEvent) nodeEvent;
                  event = new SequenceStartEvent(null, sse.getTag(), sse.getImplicit(),
                        sse.getStartMark(), sse.getEndMark(), sse.getFlowStyle());
               }
            } else {
               ScalarEvent se = (ScalarEvent) nodeEvent;
               event = new ScalarEvent(null, se.getTag(), se.getImplicit(),
                     se.getValue(), se.getStartMark(), se.getEndMark(), se.getScalarStyle());
               // it this is single scalar item we won't add it through anchorStack
               newReplayList.add(event);
            }
         }
         if (event instanceof CollectionStartEvent) {
            ++depth;
         }
      }
      for (List<Event> list : anchorStack) {
         list.add(event);
      }
      if (event instanceof CollectionEndEvent) {
         --depth;
         if (!depthStack.isEmpty() && depth == depthStack.peek()) {
            depthStack.pop();
            anchorStack.remove(anchorStack.size() - 1);
         }
      }
      return event;
   }

   private Event nextEventFromStacks() {
      while (!replaying.isEmpty()) {
         Iterator<Event> iterator = replaying.peek();
         if (iterator.hasNext()) {
            return iterator.next();
         } else {
            replaying.pop();
         }
      }
      return delegate.next();
   }

   private Event paramEvent(Event event) {
      if (event instanceof ScalarEvent) {
         ScalarEvent scalar = (ScalarEvent) event;
         String value = scalar.getValue();
         if (value.isEmpty()) {
            throw new RuntimeException(new ParserException(event, "Cannot use !param with empty value!"));
         }
         String[] parts = value.split(" +", 2);
         Stack<String> paramStack = params.get(parts[0]);
         String paramValue;
         if (paramStack == null || paramStack.isEmpty()) {
            if (parts.length > 1) {
               String defaultValue = parts[1];
               if (defaultValue.startsWith("\"") && defaultValue.endsWith("\"") || defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
                  defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
               }
               paramValue = defaultValue;
            } else {
               throw new RuntimeException(new ParserException(event, "Cannot replace parameter '" + parts[0] + "': not defined and no default value"));
            }
         } else {
            paramValue = paramStack.peek();
         }
         return new ScalarEvent(null, null, scalar.getImplicit(), paramValue, scalar.getStartMark(), scalar.getEndMark(), scalar.getScalarStyle());
      } else {
         throw new RuntimeException(new ParserException(event, "Parameter in template assumes scalar - example: 'foo: !param FOO optional default"));
      }
   }

   private Event concatEvent(Event event) {
      if (event instanceof SequenceStartEvent) {
         StringBuilder sb = new StringBuilder();
         Event itemEvent;
         while (hasNext() && !((itemEvent = next()) instanceof SequenceEndEvent)) {
            if (itemEvent instanceof ScalarEvent) {
               sb.append(((ScalarEvent) itemEvent).getValue());
            } else {
               throw new RuntimeException(new ParserException(itemEvent, "Concatenation expects only scalar events in the list."));
            }
         }
         return new ScalarEvent(null, null, new ImplicitTuple(true, true), sb.toString(), event.getStartMark(), event.getEndMark(), DumperOptions.ScalarStyle.PLAIN);
      } else {
         throw new RuntimeException(new ParserException(event, "Concatenation expects a sequence - example: 'foo: !concat [ \"http://\", !param SERVER ]'"));
      }
   }

   private Event forEachEvent(Event event) {
      String scalarItems = null;
      List<String> items = null;
      String separator = null;
      String param = null;
      List<Event> rawEvents = null;
      if (event instanceof MappingStartEvent) {
         Event mappingEvent = null;
         while (hasNext() && !((mappingEvent = next()) instanceof MappingEndEvent)) {
            if (mappingEvent instanceof ScalarEvent) {
               String property = ((ScalarEvent) mappingEvent).getValue();
               switch (property) {
                  case "items":
                     if (scalarItems != null || items != null) {
                        throw new RuntimeException(new ParserException(mappingEvent, "ForEach.items set twice?"));
                     }
                     Event itemsEvent = next();
                     if (itemsEvent instanceof ScalarEvent) {
                        scalarItems = ((ScalarEvent) itemsEvent).getValue();
                     } else if (itemsEvent instanceof SequenceStartEvent) {
                        Event itemEvent;
                        items = new ArrayList<>();
                        while (hasNext() && !((itemEvent = next()) instanceof SequenceEndEvent)) {
                           if (itemEvent instanceof ScalarEvent) {
                              items.add(((ScalarEvent) itemEvent).getValue());
                           } else {
                              throw new RuntimeException(new ParserException(itemsEvent, "ForEach.items as a sequence must only contain scalars!"));
                           }
                        }
                     } else {
                        throw new RuntimeException(new ParserException(itemsEvent, "ForEach.items must be scalar or sequence!"));
                     }
                     break;
                  case "separator":
                     if (separator != null) {
                        throw new RuntimeException(new ParserException(mappingEvent, "Separator set twice?"));
                     }
                     Event separatorEvent = next();
                     if (separatorEvent instanceof ScalarEvent) {
                        separator = ((ScalarEvent) separatorEvent).getValue();
                     } else {
                        throw new RuntimeException(new ParserException(separatorEvent, "ForEach.separator must be scalar!"));
                     }
                     break;
                  case "param":
                     if (param != null) {
                        throw new RuntimeException(new ParserException(mappingEvent, "Param set twice?"));
                     }
                     Event paramEvent = next();
                     if (paramEvent instanceof ScalarEvent) {
                        param = ((ScalarEvent) paramEvent).getValue();
                     } else {
                        throw new RuntimeException(new ParserException(paramEvent, "ForEach.param must be scalar!"));
                     }
                     break;
                  case "do":
                     int depth = 0;
                     rawEvents = new ArrayList<>();
                     do {
                        Event raw = nextRawEvent();
                        rawEvents.add(raw);
                        if (raw instanceof CollectionStartEvent) {
                           ++depth;
                        } else if (raw instanceof CollectionEndEvent) {
                           --depth;
                        }
                     } while (depth > 0);
                     break;
                  default:
                     throw new RuntimeException(new ParserException(mappingEvent, "Unknown ForEach property: '" + property + "', valid options are 'items', 'param', 'separator' and 'do'"));
               }
            } else {
               throw new RuntimeException(new ParserException(mappingEvent, "ForEach expect scalar property names"));
            }
         }
         if (separator == null) {
            separator = ",";
         }
         if (param == null) {
            param = "ITEM";
         }
         if (items == null) {
            if (scalarItems != null) {
               items = Stream.of(scalarItems.split(separator)).map(String::trim).collect(Collectors.toList());
               // drop empty elements at end - an empty string results in an empty list
               for (ListIterator<String> it = items.listIterator(items.size()); it.hasPrevious(); ) {
                  if (it.previous().isEmpty()) {
                     it.remove();
                  }
               }
            } else {
               throw new RuntimeException(new ParserException("ForEach.items must be defined (set to empty string if you want an empty list)"));
            }
         }
         if (rawEvents == null) {
            throw new RuntimeException(new ParserException("ForEach.do must be defined: that's what you want repeat."));
         }
         replaying.push(new ForeachIterator(items, rawEvents, param, new SequenceEndEvent(mappingEvent.getStartMark(), mappingEvent.getEndMark())));
         return new SequenceStartEvent(null, null, true, event.getStartMark(), event.getEndMark(), DumperOptions.FlowStyle.BLOCK);
      } else {
         throw new RuntimeException(new ParserException(event, "ForEach expects a mapping with 'items', 'do' and optional 'param' and 'separator' properties."));
      }
   }

   private static class AnchorInfo {
      final NodeEvent anchorEvent;
      final List<Event> events;

      private AnchorInfo(NodeEvent anchorEvent, List<Event> events) {
         this.anchorEvent = anchorEvent;
         this.events = events;
      }
   }

   private class ForeachIterator implements Iterator<Event> {
      private final Iterator<String> itemsIterator;
      private final List<Event> events;
      private final SequenceEndEvent endEvent;
      private final Stack<String> paramStack;
      private Iterator<Event> currentEventsIterator;
      private boolean started = false, atEnd = false;

      ForeachIterator(List<String> items, List<Event> events, String param, SequenceEndEvent endEvent) {
         this.itemsIterator = items.iterator();
         this.events = events;
         this.endEvent = endEvent;
         this.paramStack = params.computeIfAbsent(param, p -> new Stack<>());
         advanceCurrentIterator();
      }

      private void advanceCurrentIterator() {
         if (itemsIterator.hasNext()) {
            if (started) {
               paramStack.pop();
            } else {
               started = true;
            }
            paramStack.push(itemsIterator.next());
            currentEventsIterator = events.iterator();
         } else if (!atEnd) {
            if (started) {
               paramStack.pop();
            }
            currentEventsIterator = Collections.<Event>singletonList(endEvent).iterator();
            atEnd = true;
         }
      }

      @Override
      public boolean hasNext() {
         if (!currentEventsIterator.hasNext()) {
            return itemsIterator.hasNext() || !atEnd;
         }
         return true;
      }

      @Override
      public Event next() {
         if (!currentEventsIterator.hasNext()) {
            advanceCurrentIterator();
         }
         return currentEventsIterator.next();
      }
   }
}
