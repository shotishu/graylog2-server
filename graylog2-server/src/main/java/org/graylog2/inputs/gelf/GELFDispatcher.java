/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.graylog2.inputs.gelf;

import com.codahale.metrics.Meter;
import org.graylog2.gelf.GELFProcessor;
import org.graylog2.gelf.GELFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graylog2.Core;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import static com.codahale.metrics.MetricRegistry.name;


/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class GELFDispatcher extends SimpleChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GELFDispatcher.class);

    private GELFProcessor processor;
    private Core server;

    private final Meter receivedMessages;
    private final Meter dispatchedChunkedMessages;
    private final Meter dispatchedUnchunkedMessages;

    public GELFDispatcher(Core server) {
        this.server = server;
        this.processor = new GELFProcessor(server);

        this.receivedMessages = server.metrics().meter(name(GELFDispatcher.class, "receivedMessages"));
        this.dispatchedChunkedMessages = server.metrics().meter(name(GELFDispatcher.class, "dispatchedChunkedMessages"));
        this.dispatchedUnchunkedMessages = server.metrics().meter(name(GELFDispatcher.class, "dispatchedUnchunkedMessages"));
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        receivedMessages.mark();
        
        ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

        byte[] readable = new byte[buffer.readableBytes()];
        buffer.toByteBuffer().get(readable, buffer.readerIndex(), buffer.readableBytes());

        GELFMessage msg = new GELFMessage(readable);

        switch(msg.getGELFType()) {
        case CHUNKED:
            dispatchedChunkedMessages.mark();
            server.getGELFChunkManager().insert(msg);
            break;
        case ZLIB:
        case GZIP:
        case UNCOMPRESSED:
        case UNSUPPORTED:
            dispatchedUnchunkedMessages.mark();
            processor.messageReceived(msg);
            break;
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        LOG.warn("Could not handle GELF message.", e.getCause());
    }

}
