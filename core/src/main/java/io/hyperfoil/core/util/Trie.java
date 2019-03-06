package io.hyperfoil.core.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Trie {
   private final Node[] firstNodes;

   public Trie(String... strings) {
      firstNodes = getNodes(Stream.of(strings).map(s -> s.getBytes(StandardCharsets.UTF_8)).collect(Collectors.toList()), 0);
   }

   private Node[] getNodes(List<byte[]> strings, int index) {
      // Quick and dirty impl...
      Set<Byte> bytes = strings.stream().filter(Objects::nonNull).map(s -> s[index]).distinct().collect(Collectors.toSet());
      List<Node> nodes = new ArrayList<>();
      for (byte b : bytes) {
         List<byte[]> matching = new ArrayList<>();
         int terminal = -1;
         for (int i = 0; i < strings.size(); i++) {
            byte[] s = strings.get(i);
            if (s != null && s[index] == b) {
               if (s.length == index + 1) {
                  assert terminal < 0 : "Duplicate strings";
                  terminal = i;
               } else {
                  matching.add(s);
               }
            } else {
               // to keep terminal indices
               matching.add(null);
            }
         }
         nodes.add(new Node(b, terminal, getNodes(matching, index + 1)));
      }
      return nodes.isEmpty() ? null : nodes.toArray(new Node[0]);
   }

   public State newState() {
      return new State();
   }

   public class State {
      Node[] current = firstNodes;

      public int next(byte b) {
         if (current == null) {
            // prefix does not match, ignore
            return -1;
         }
         for (Node n : current) {
            if (n.b == b) {
               current = n.nextNodes;
               return n.terminal;
            }
         }
         // no match
         current = null;
         return -1;
      }

      public void reset() {
         current = firstNodes;
      }
   }

   private static class Node {
      final byte b;
      final int terminal;
      final Node[] nextNodes;

      private Node(byte b, int terminal, Node[] nextNodes) {
         this.b = b;
         this.terminal = terminal;
         this.nextNodes = nextNodes;
      }
   }
}
