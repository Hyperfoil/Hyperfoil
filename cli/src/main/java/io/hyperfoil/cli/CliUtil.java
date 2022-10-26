/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.hyperfoil.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.io.FileResource;
import org.aesh.io.Resource;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

public final class CliUtil {
   private CliUtil() {
   }

   public static String sanitize(String path) {
      if (path.startsWith("~/")) {
         return System.getProperty("user.home") + path.substring(1);
      }
      return path;
   }

   public static Resource sanitize(Resource resource) {
      if (resource instanceof FileResource) {
         File file = ((FileResource) resource).getFile();
         if (file.getPath().startsWith("~/")) {
            return new FileResource(System.getProperty("user.home") + file.getPath().substring(1));
         }
      }
      return resource;
   }

   public static boolean isPortListening(String hostname, int port) {
      try (ServerSocket serverSocket = new ServerSocket()) {
         serverSocket.setReuseAddress(false);
         serverSocket.bind(new InetSocketAddress(InetAddress.getByName(hostname), port), 1);
         return false;
      } catch (Exception ex) {
         return true;
      }
   }

   public static String fromCommand(String... command) {
      String result = null;
      try {
         Process process = new ProcessBuilder(command).start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            result = reader.readLine();
         }
         process.destroy();
      } catch (IOException e) {
         // ignore error
      }
      return result;
   }

   public static void execProcess(HyperfoilCommandInvocation invocation, boolean expectNewWindow, String command, String... params) throws IOException {
      Process process = null;
      try {
         if (expectNewWindow) {
            invocation.println("Press Ctrl+C when done...");
         }
         ArrayList<String> cmdline = new ArrayList<>();
         cmdline.addAll(Arrays.asList(command.split("[\t \n]+", 0)));
         cmdline.addAll(Arrays.asList(params));
         process = new ProcessBuilder(cmdline.toArray(new String[0])).inheritIO().start();
         process.waitFor();
      } catch (InterruptedException e) {
         process.destroy();
      }
   }

   public static Map<String, Stream<Map.Entry<String, Client.MinMax>>> toMapOfStreams(Map<String, Map<String, Client.MinMax>> stats) {
      return stats.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, CliUtil::sortedEntries, throwingMerger(), TreeMap::new));
   }

   private static Stream<Map.Entry<String, Client.MinMax>> sortedEntries(Map.Entry<String, Map<String, Client.MinMax>> e) {
      return e.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey());
   }

   private static BinaryOperator<Stream<Map.Entry<String, Client.MinMax>>> throwingMerger() {
      return (u, v) -> { throw new IllegalStateException(); };
   }

   public static Path getLocalFileForUpload(HyperfoilCommandInvocation invocation, String file) throws InterruptedException {
      File ff = new File(file);
      if (ff.exists()) {
         if (!ff.canRead()) {
            invocation.print("File " + file + " cannot be read, provide another path or leave empty to skip: ");
            return askForFile(invocation, file);
         } else {
            invocation.print("Upload file " + file + "? [y/N] ");
            switch (invocation.inputLine().trim().toLowerCase()) {
               case "y":
               case "yes":
                  return ff.toPath();
               default:
                  return null;
            }
         }
      } else if (!ff.isAbsolute()) {
         invocation.print("Non-absolute path " + file + ", provide absolute path or leave empty to skip: ");
         return askForFile(invocation, file);
      } else {
         invocation.println("Ignoring file " + file + " as it doesn't exist on local file system.");
      }
      return null;
   }

   private static Path askForFile(HyperfoilCommandInvocation invocation, String file) throws InterruptedException {
      File ff;
      for (; ; ) {
         String path = invocation.inputLine().trim();
         if (path.isEmpty()) {
            invocation.println("Ignoring file " + file + ".");
            return null;
         }
         ff = new File(path);
         if (!ff.exists()) {
            invocation.println("Invalid path " + path + ", retry or leave empty to skip: ");
         } else if (!ff.canRead()) {
            invocation.println("Cannot read file " + path + ", retry or leave empty to skip: ");
         } else {
            return ff.toPath();
         }
      }
   }
}
