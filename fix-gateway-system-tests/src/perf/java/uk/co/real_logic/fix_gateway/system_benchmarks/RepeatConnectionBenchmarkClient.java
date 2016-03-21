/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.system_benchmarks;

import uk.co.real_logic.fix_gateway.builder.TestRequestEncoder;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import static uk.co.real_logic.fix_gateway.system_benchmarks.Configuration.INITIATOR_ID;

public final class RepeatConnectionBenchmarkClient extends AbstractBenchmarkClient
{

    public static void main(String[] args) throws IOException
    {
        new RepeatConnectionBenchmarkClient().runBenchmark();
    }

    public static final int NUMBER_OF_CONNECTIONS = 10_000;

    public void runBenchmark() throws IOException
    {
        for (int i = 0; i < NUMBER_OF_CONNECTIONS; i++)
        {
            final String initiatorId = INITIATOR_ID;

            try (final SocketChannel socketChannel = open())
            {
                logon(socketChannel, initiatorId);

                final TestRequestEncoder testRequest = setupTestRequest(initiatorId);
                testRequest.header().msgSeqNum(3);

                timestampEncoder.encode(System.currentTimeMillis());

                final int length = testRequest.encode(writeFlyweight, 0);

                write(socketChannel, length);

                read(socketChannel);
            }

            System.out.printf("Finished Connection: %d\n", i + 1);
        }
    }

}