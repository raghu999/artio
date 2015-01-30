/*
 * Copyright 2015 Real Logic Ltd.
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
package uk.co.real_logic.framer;

import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.fix_gateway.framer.MessageSource;
import uk.co.real_logic.fix_gateway.framer.Multiplexer;
import uk.co.real_logic.fix_gateway.framer.SenderEndPoint;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MultiplexerTest
{

    private SenderEndPoint mockSenderEndPoint = mock(SenderEndPoint.class);
    private MessageSource mockSource = mock(MessageSource.class);
    private Multiplexer multiplexer = new Multiplexer(mockSource);
    private DirectBuffer buffer = mock(DirectBuffer.class);

    private int messagesSent;

    @Before
    public void setUp()
    {
        doAnswer(inv ->
        {
            multiplexer.onMessage(buffer, 1, 1, 1L);
            return 1;
        }).when(mockSource).drainTo(multiplexer);
    }

    @Test
    public void messagesAreSentToCorrectEndPoint()
    {
        given:
        multiplexer.onNewConnection(1L, mockSenderEndPoint);

        when:
        messagesSent = multiplexer.scanBuffers();

        then:
        assertEquals(1, messagesSent);
        verify(mockSenderEndPoint).onFramedMessage(buffer, 1, 1);
    }

    @Test
    public void messagesAreNotSentToOtherEndPoints()
    {
        given:
        multiplexer.onNewConnection(2L, mockSenderEndPoint);

        when:
        multiplexer.scanBuffers();

        then:
        verify(mockSenderEndPoint, never()).onFramedMessage(any(), anyInt(), anyInt());
    }
}
