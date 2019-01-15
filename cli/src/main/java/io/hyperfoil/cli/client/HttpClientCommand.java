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

package io.hyperfoil.cli.client;

import io.vertx.core.http.HttpVersion;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.validator.OptionValidatorException;

import java.util.List;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@CommandDefinition(name = "http-clientPool", description = "")
public class HttpClientCommand implements Command<CommandInvocation> {

    @Option(name = "protocol", shortName = 'p', converter = HttpVersionConverter.class, defaultValue = "HTTP_2")
    public HttpVersion protocol;

    @Option(name = "duration", shortName = 'd', defaultValue = "30s")
    public String durationParam;

    @Option(name = "connection", shortName = 'c', defaultValue = "1")
    public int connections;

    @Option(name = "out", shortName = 'o')
    public String out;

    @Option(name = "body", shortName = 'b')
    public String bodyParam;

    @Option(name = "concurrency", shortName = 'q', defaultValue = "1",
            description = "The concurrency per connection: number of pipelined requests for HTTP/1.1, max concurrent streams for HTTP/2")
    public int concurrency;

    @Arguments
    public List<String> uriParam;

    @Option(name = "rate", shortName = 'r', defaultValue = "100")
    public int rates; // rate per second

    @Option(name = "warmup", shortName = 'w', defaultValue = "0")
    public String warmupParam;

    @Option(name = "threads", shortName = 't', defaultValue = "1", description = "Number of threads to use")
    public int threads;

    @Option(name = "tags")
    public String tagString;

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) {
        return CommandResult.SUCCESS;
    }

    public static class HttpVersionConverter implements Converter<HttpVersion, ConverterInvocation> {

        @Override
        public HttpVersion convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
            if(converterInvocation.getInput() == null)
                return HttpVersion.HTTP_2;
            for(HttpVersion version : HttpVersion.values())
                if(version.name().equals(converterInvocation.getInput()))
                    return version;
            return HttpVersion.HTTP_2;
        }
    }
}
